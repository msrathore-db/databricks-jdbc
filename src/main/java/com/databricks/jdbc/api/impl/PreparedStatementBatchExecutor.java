package com.databricks.jdbc.api.impl;

import com.databricks.jdbc.common.DatabricksJdbcConstants;
import com.databricks.jdbc.common.StatementType;
import com.databricks.jdbc.common.util.InsertStatementParser;
import com.databricks.jdbc.exception.DatabricksBatchUpdateException;
import com.databricks.jdbc.exception.DatabricksSQLException;
import com.databricks.jdbc.log.JdbcLogger;
import com.databricks.jdbc.log.JdbcLoggerFactory;
import com.databricks.jdbc.model.telemetry.enums.DatabricksDriverErrorCode;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class PreparedStatementBatchExecutor {

  private static final JdbcLogger LOGGER =
      JdbcLoggerFactory.getLogger(PreparedStatementBatchExecutor.class);

  /** Result-set column carrying the per-parameter-set affected row count in the batch response. */
  private static final String AFFECTED_ROWS_COUNT_COLUMN = "num_affected_rows";

  private final String sql;
  private final DatabricksConnection connection;
  private final boolean interpolateParameters;
  private final StatementExecutor statementExecutor;
  private final BatchStatementExecutor batchStatementExecutor;

  @FunctionalInterface
  interface StatementExecutor {
    DatabricksResultSet execute(
        String sql,
        Map<Integer, ImmutableSqlParameter> params,
        StatementType statementType,
        boolean closeStatement)
        throws SQLException;
  }

  @FunctionalInterface
  interface BatchStatementExecutor {
    DatabricksResultSet execute(
        String sql,
        List<Map<Integer, ImmutableSqlParameter>> batchParameters,
        StatementType statementType)
        throws SQLException;
  }

  PreparedStatementBatchExecutor(
      String sql,
      DatabricksConnection connection,
      boolean interpolateParameters,
      StatementExecutor statementExecutor) {
    this.sql = sql;
    this.connection = connection;
    this.interpolateParameters = interpolateParameters;
    this.statementExecutor = statementExecutor;
    this.batchStatementExecutor = null;
  }

  PreparedStatementBatchExecutor(
      String sql,
      DatabricksConnection connection,
      boolean interpolateParameters,
      StatementExecutor statementExecutor,
      BatchStatementExecutor batchStatementExecutor) {
    this.sql = sql;
    this.connection = connection;
    this.interpolateParameters = interpolateParameters;
    this.statementExecutor = statementExecutor;
    this.batchStatementExecutor = batchStatementExecutor;
  }

  long[] executeBatch(List<DatabricksParameterMetaData> batchParameterMetaData)
      throws DatabricksBatchUpdateException {
    if (batchParameterMetaData.isEmpty()) {
      return new long[0];
    }

    // Preferred path: submit the whole batch to the server in a single request via the
    // batchParameters/parameter_sets field, letting DBR combine them into one performant execution.
    if (canUseServerSideBatchParameters()) {
      LOGGER.debug(
          "Attempting server-side batch parameters for {} rows", batchParameterMetaData.size());
      try {
        return executeWithBatchParameters(batchParameterMetaData);
      } catch (BatchParametersUnsupportedException e) {
        // The negotiated protocol/DBR version does not support server-side batch parameters
        // (either detected up front as < V10, or surfaced at runtime as an UNBOUND_SQL_PARAMETER
        // 42P02 error from an old DBR that ignored the field). Fall back to the previous approach
        // so batch insertion keeps working, per the graceful-rollout design.
        LOGGER.info(
            "Server-side batch parameters unsupported ({}); falling back to client-side batching",
            e.getMessage());
      }
    }

    return executeClientSideBatch(batchParameterMetaData);
  }

  /** Client-side batching used both when server-side is disabled and as the graceful fallback. */
  private long[] executeClientSideBatch(List<DatabricksParameterMetaData> batchParameterMetaData)
      throws DatabricksBatchUpdateException {
    if (canUseBatchedInsert()) {
      LOGGER.debug(
          "Using client-side multi-row INSERT expansion for {} rows",
          batchParameterMetaData.size());
      return executeBatchedInsert(batchParameterMetaData);
    }

    LOGGER.debug("Using individual statement execution for {} rows", batchParameterMetaData.size());
    return executeIndividualStatements(batchParameterMetaData);
  }

  private boolean canUseServerSideBatchParameters() {
    // A single connection property (EnableBatchedInserts) opts into batching; when a batch executor
    // is wired (SEA or Thrift client supports it), we prefer the server-side path.
    return batchStatementExecutor != null
        && connection.getConnectionContext().isBatchedInsertsEnabled();
  }

  private long[] executeWithBatchParameters(
      List<DatabricksParameterMetaData> batchParameterMetaData)
      throws DatabricksBatchUpdateException, BatchParametersUnsupportedException {
    LOGGER.debug(
        "Executing INSERT with server-side batch parameters, {} rows",
        batchParameterMetaData.size());

    List<Map<Integer, ImmutableSqlParameter>> batchParams = new ArrayList<>();
    for (DatabricksParameterMetaData metaData : batchParameterMetaData) {
      batchParams.add(metaData.getParameterBindings());
    }

    DatabricksResultSet resultSet;
    try {
      resultSet = batchStatementExecutor.execute(sql, batchParams, StatementType.UPDATE);
    } catch (SQLException e) {
      if (isBatchParametersUnsupported(e)) {
        // Signal executeBatch to retry via the client-side path. No rows were inserted (DBR fails
        // the whole batch atomically), so falling back is safe.
        throw new BatchParametersUnsupportedException(e.getMessage());
      }
      LOGGER.error("Error executing with server-side batch parameters: {}", e.getMessage(), e);
      long[] failedCounts = new long[batchParameterMetaData.size()];
      Arrays.fill(failedCounts, Statement.EXECUTE_FAILED);
      throw new DatabricksBatchUpdateException(
          e.getMessage(), DatabricksDriverErrorCode.BATCH_EXECUTE_EXCEPTION, failedCounts);
    }

    return extractUpdateCounts(resultSet, batchParameterMetaData.size());
  }

  /**
   * The server returns one result row per parameter set, each carrying its {@code
   * num_affected_rows} count. Read them in order into the update-count array. If the result set
   * does not expose per-row counts (older/unexpected shape), default each entry to {@link
   * Statement#SUCCESS_NO_INFO}.
   */
  private long[] extractUpdateCounts(DatabricksResultSet resultSet, int batchSize)
      throws DatabricksBatchUpdateException {
    long[] updateCounts = new long[batchSize];
    try {
      int index = 0;
      while (index < batchSize && resultSet.next()) {
        updateCounts[index++] = resultSet.getLong(AFFECTED_ROWS_COUNT_COLUMN);
      }
      // If the server did not return a row per parameter set, fill the remainder with
      // SUCCESS_NO_INFO rather than claiming a specific count we do not have.
      while (index < batchSize) {
        updateCounts[index++] = Statement.SUCCESS_NO_INFO;
      }
      return updateCounts;
    } catch (SQLException e) {
      LOGGER.error("Error reading batch update counts from result set: {}", e.getMessage(), e);
      long[] failedCounts = new long[batchSize];
      Arrays.fill(failedCounts, Statement.EXECUTE_FAILED);
      throw new DatabricksBatchUpdateException(
          e.getMessage(), DatabricksDriverErrorCode.BATCH_EXECUTE_EXCEPTION, failedCounts);
    }
  }

  /**
   * Detects whether the failure indicates the server does not support server-side batch parameters,
   * meaning we should gracefully fall back to client-side batching. This covers both the up-front
   * capability check (Thrift protocol < V10, raised as UNSUPPORTED_OPERATION) and old DBR versions
   * that ignore the batchParameters field and report the placeholders as unbound (SQLState 42P02).
   */
  private boolean isBatchParametersUnsupported(SQLException e) {
    String sqlState = e.getSQLState();
    return DatabricksJdbcConstants.UNBOUND_SQL_PARAMETER_SQLSTATE.equals(sqlState)
        || DatabricksDriverErrorCode.UNSUPPORTED_OPERATION.name().equals(sqlState);
  }

  /** Internal signal that the server cannot handle batch parameters and we should fall back. */
  private static class BatchParametersUnsupportedException extends Exception {
    BatchParametersUnsupportedException(String message) {
      super(message);
    }
  }

  private boolean canUseBatchedInsert() {
    // Check if batched inserts are enabled via connection property
    if (!connection.getConnectionContext().isBatchedInsertsEnabled()) {
      return false;
    }

    // Use strict exception-based parsing for better error handling
    try {
      InsertStatementParser.parseInsertStrict(sql);
      return true;
    } catch (Exception e) {
      // Not a valid INSERT statement suitable for batching
      return false;
    }
  }

  private long[] executeBatchedInsert(List<DatabricksParameterMetaData> batchParameterMetaData)
      throws DatabricksBatchUpdateException {
    LOGGER.debug("Executing batched INSERT with {} rows", batchParameterMetaData.size());

    try {
      InsertStatementParser.InsertInfo insertInfo = InsertStatementParser.parseInsertStrict(sql);

      // Calculate how many rows we can fit in one chunk
      int parametersPerRow = insertInfo.getColumnCount();
      int maxRowsPerChunk;

      if (interpolateParameters) {
        // When parameter interpolation is enabled (supportManyParameters=1), there is no
        // parameter limit since values are interpolated directly into the SQL string.
        // Try to execute all rows in a single batch, only limited by configured BatchInsertSize
        // which users can set based on their data to avoid exceeding the 16MB statement limit.
        int configuredBatchSize = connection.getConnectionContext().getBatchInsertSize();
        if (configuredBatchSize < 1) {
          throw new DatabricksSQLException(
              "BatchInsertSize must be at least 1, got: " + configuredBatchSize,
              DatabricksDriverErrorCode.INVALID_STATE);
        }
        maxRowsPerChunk = Math.min(configuredBatchSize, batchParameterMetaData.size());
      } else {
        // When using parameterized queries, respect the 256 parameter limit from Databricks
        // backend
        int maxRowsByParameterLimit =
            DatabricksJdbcConstants.MAX_QUERY_PARAMETERS / parametersPerRow;

        // Ensure we have at least 1 row per chunk
        if (maxRowsByParameterLimit < 1) {
          maxRowsPerChunk = 1;
        } else {
          maxRowsPerChunk = maxRowsByParameterLimit;
        }
      }

      long[] allUpdateCounts = new long[batchParameterMetaData.size()];

      // Process batches in chunks
      for (int startIndex = 0;
          startIndex < batchParameterMetaData.size();
          startIndex += maxRowsPerChunk) {
        int endIndex = Math.min(startIndex + maxRowsPerChunk, batchParameterMetaData.size());
        int chunkSize = endIndex - startIndex;

        // Build multi-row INSERT for this chunk
        String multiRowSql = InsertStatementParser.generateMultiRowInsert(insertInfo, chunkSize);
        Map<Integer, ImmutableSqlParameter> chunkParams = new HashMap<>();
        int paramIndex = 1;

        for (int i = startIndex; i < endIndex; i++) {
          DatabricksParameterMetaData batchParams = batchParameterMetaData.get(i);
          Map<Integer, ImmutableSqlParameter> rowParams = batchParams.getParameterBindings();
          for (int j = 1; j <= rowParams.size(); j++) {
            if (rowParams.containsKey(j)) {
              chunkParams.put(paramIndex++, rowParams.get(j));
            }
          }
        }

        // Execute this chunk
        String sqlToExecute =
            interpolateParameters
                ? com.databricks.jdbc.common.util.SQLInterpolator.interpolateSQL(
                    multiRowSql, chunkParams)
                : multiRowSql;
        Map<Integer, ImmutableSqlParameter> paramsToSend =
            interpolateParameters ? new HashMap<>() : chunkParams;
        statementExecutor.execute(sqlToExecute, paramsToSend, StatementType.UPDATE, false);

        // Set update counts for this chunk (each row typically affects 1 row)
        for (int i = startIndex; i < endIndex; i++) {
          allUpdateCounts[i] = 1;
        }
      }

      return allUpdateCounts;

    } catch (DatabricksBatchUpdateException e) {
      // Re-throw batch update exceptions (these already have proper update counts)
      throw e;
    } catch (Exception e) {
      // Unexpected exception - mark all as failed
      LOGGER.error("Unexpected error executing batched INSERT: {}", e.getMessage(), e);
      long[] failedCounts = new long[batchParameterMetaData.size()];
      for (int i = 0; i < failedCounts.length; i++) {
        failedCounts[i] = Statement.EXECUTE_FAILED;
      }
      throw new DatabricksBatchUpdateException(
          e.getMessage(), DatabricksDriverErrorCode.BATCH_EXECUTE_EXCEPTION, failedCounts);
    }
  }

  private long[] executeIndividualStatements(
      List<DatabricksParameterMetaData> batchParameterMetaData)
      throws DatabricksBatchUpdateException {
    LOGGER.debug("Executing batch individually with {} statements", batchParameterMetaData.size());
    long[] largeUpdateCount = new long[batchParameterMetaData.size()];

    for (int sqlQueryIndex = 0; sqlQueryIndex < batchParameterMetaData.size(); sqlQueryIndex++) {
      DatabricksParameterMetaData databricksParameterMetaData =
          batchParameterMetaData.get(sqlQueryIndex);
      try {
        DatabricksResultSet resultSet =
            statementExecutor.execute(
                sql,
                databricksParameterMetaData.getParameterBindings(),
                StatementType.UPDATE,
                false);
        largeUpdateCount[sqlQueryIndex] = resultSet.getUpdateCount();
      } catch (Exception e) {
        LOGGER.error(
            "Error executing batch update for index {}: {}", sqlQueryIndex, e.getMessage(), e);
        // Set the current failed statement's count
        largeUpdateCount[sqlQueryIndex] = Statement.EXECUTE_FAILED;
        // Set all remaining statements as failed
        for (int i = sqlQueryIndex + 1; i < largeUpdateCount.length; i++) {
          largeUpdateCount[i] = Statement.EXECUTE_FAILED;
        }
        // WARNING: Due to lack of transaction support, any successfully executed statements
        // before this failure have already been committed and cannot be rolled back
        throw new DatabricksBatchUpdateException(
            e.getMessage(), DatabricksDriverErrorCode.BATCH_EXECUTE_EXCEPTION, largeUpdateCount);
      }
    }
    return largeUpdateCount;
  }
}
