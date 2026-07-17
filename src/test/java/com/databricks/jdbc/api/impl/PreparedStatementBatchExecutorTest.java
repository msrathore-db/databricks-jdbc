package com.databricks.jdbc.api.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import com.databricks.jdbc.api.internal.IDatabricksConnectionContext;
import com.databricks.jdbc.common.DatabricksJdbcConstants;
import com.databricks.jdbc.common.StatementType;
import com.databricks.jdbc.exception.DatabricksBatchUpdateException;
import com.databricks.jdbc.exception.DatabricksSQLException;
import com.databricks.jdbc.model.core.ColumnInfoTypeName;
import com.databricks.jdbc.model.telemetry.enums.DatabricksDriverErrorCode;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Unit tests for {@link PreparedStatementBatchExecutor} routing, update counts and fallback. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PreparedStatementBatchExecutorTest {

  private static final String INSERT_SQL = "INSERT INTO t VALUES (?, ?)";

  @Mock DatabricksConnection connection;
  @Mock IDatabricksConnectionContext connectionContext;
  @Mock DatabricksResultSet batchResultSet;

  @BeforeEach
  void setUp() {
    when(connection.getConnectionContext()).thenReturn(connectionContext);
  }

  private List<DatabricksParameterMetaData> buildBatch(int rows) {
    List<DatabricksParameterMetaData> batch = new ArrayList<>();
    for (int i = 0; i < rows; i++) {
      DatabricksParameterMetaData md = new DatabricksParameterMetaData();
      md.put(
          1,
          ImmutableSqlParameter.builder()
              .cardinal(1)
              .type(ColumnInfoTypeName.INT)
              .value(i)
              .build());
      md.put(
          2,
          ImmutableSqlParameter.builder()
              .cardinal(2)
              .type(ColumnInfoTypeName.STRING)
              .value("v" + i)
              .build());
      batch.add(md);
    }
    return batch;
  }

  @Test
  void emptyBatchReturnsEmptyCounts() throws Exception {
    PreparedStatementBatchExecutor executor =
        new PreparedStatementBatchExecutor(
            INSERT_SQL, connection, false, (sql, params, type, close) -> null);
    assertArrayEquals(new long[0], executor.executeBatch(new ArrayList<>()));
  }

  @Test
  void serverSidePathReadsPerRowAffectedCounts() throws Exception {
    when(connectionContext.isBatchedInsertsEnabled()).thenReturn(true);
    // Three parameter sets -> server returns three result rows, each with num_affected_rows=1.
    when(batchResultSet.next()).thenReturn(true, true, true, false);
    when(batchResultSet.getLong("num_affected_rows")).thenReturn(1L, 1L, 1L);

    boolean[] batchCalled = {false};
    PreparedStatementBatchExecutor executor =
        new PreparedStatementBatchExecutor(
            INSERT_SQL,
            connection,
            false,
            (sql, params, type, close) -> null,
            (sql, batchParams, type) -> {
              batchCalled[0] = true;
              assertEquals(3, batchParams.size());
              assertEquals(StatementType.UPDATE, type);
              return batchResultSet;
            });

    long[] counts = executor.executeBatch(buildBatch(3));

    assertTrue(batchCalled[0], "server-side batch executor should have been invoked");
    assertArrayEquals(new long[] {1L, 1L, 1L}, counts);
  }

  @Test
  void unbound42P02FallsBackToClientSide() throws Exception {
    when(connectionContext.isBatchedInsertsEnabled()).thenReturn(true);
    // Not a batched-insert-eligible INSERT parse -> client-side falls through to individual exec.
    when(connectionContext.getBatchInsertSize()).thenReturn(1000);

    boolean[] individualCalled = {false};
    PreparedStatementBatchExecutor executor =
        new PreparedStatementBatchExecutor(
            "UPDATE t SET a = ?",
            connection,
            false,
            (sql, params, type, close) -> {
              individualCalled[0] = true;
              return individualResult();
            },
            (sql, batchParams, type) -> {
              // Simulate an old DBR ignoring batchParameters -> unbound parameter error.
              throw new DatabricksSQLException(
                  "[UNBOUND_SQL_PARAMETER] Found the unbound parameter",
                  DatabricksJdbcConstants.UNBOUND_SQL_PARAMETER_SQLSTATE);
            });

    long[] counts = executor.executeBatch(buildBatch(2));

    assertTrue(individualCalled[0], "should fall back to client-side execution on 42P02");
    assertEquals(2, counts.length);
  }

  @Test
  void unsupportedProtocolFallsBackToClientSide() throws Exception {
    when(connectionContext.isBatchedInsertsEnabled()).thenReturn(true);

    boolean[] individualCalled = {false};
    PreparedStatementBatchExecutor executor =
        new PreparedStatementBatchExecutor(
            "UPDATE t SET a = ?",
            connection,
            false,
            (sql, params, type, close) -> {
              individualCalled[0] = true;
              return individualResult();
            },
            (sql, batchParams, type) -> {
              // Thrift client below V10 signals unsupported via UNSUPPORTED_OPERATION SQLState.
              throw new DatabricksSQLException(
                  "requires V10", DatabricksDriverErrorCode.UNSUPPORTED_OPERATION);
            });

    executor.executeBatch(buildBatch(2));
    assertTrue(individualCalled[0], "should fall back when server protocol < V10");
  }

  @Test
  void noBatchExecutorSkipsServerSidePath() throws Exception {
    // When only the single-statement executor is wired, server-side path is never attempted even
    // if the property is on.
    boolean[] individualCalled = {false};
    PreparedStatementBatchExecutor executor =
        new PreparedStatementBatchExecutor(
            "UPDATE t SET a = ?",
            connection,
            false,
            (sql, params, type, close) -> {
              individualCalled[0] = true;
              return individualResult();
            });

    executor.executeBatch(buildBatch(1));
    // With no batch executor wired, the server-side path is skipped and execution falls to the
    // client-side individual path regardless of the property.
    assertTrue(individualCalled[0]);
  }

  @Test
  void genuineServerErrorSurfacesAsBatchUpdateException() {
    when(connectionContext.isBatchedInsertsEnabled()).thenReturn(true);
    PreparedStatementBatchExecutor executor =
        new PreparedStatementBatchExecutor(
            INSERT_SQL,
            connection,
            false,
            (sql, params, type, close) -> null,
            (sql, batchParams, type) -> {
              throw new DatabricksSQLException(
                  "real failure", DatabricksDriverErrorCode.BATCH_EXECUTE_EXCEPTION);
            });

    DatabricksBatchUpdateException e =
        assertThrows(
            DatabricksBatchUpdateException.class, () -> executor.executeBatch(buildBatch(2)));
    long[] counts = e.getLargeUpdateCounts();
    assertEquals(2, counts.length);
    assertEquals(java.sql.Statement.EXECUTE_FAILED, counts[0]);
  }

  // A fresh mock result set for the client-side individual path.
  private DatabricksResultSet individualResultCache;

  private DatabricksResultSet individualResult() throws SQLException {
    if (individualResultCache == null) {
      individualResultCache = org.mockito.Mockito.mock(DatabricksResultSet.class);
      when(individualResultCache.getUpdateCount()).thenReturn(1L);
    }
    return individualResultCache;
  }
}
