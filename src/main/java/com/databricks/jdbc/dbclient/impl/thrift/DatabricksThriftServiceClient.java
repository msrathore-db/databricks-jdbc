package com.databricks.jdbc.dbclient.impl.thrift;

import static com.databricks.jdbc.common.EnvironmentVariables.DEFAULT_STATEMENT_TIMEOUT_SECONDS;
import static com.databricks.jdbc.common.EnvironmentVariables.JDBC_THRIFT_VERSION;
import static com.databricks.jdbc.common.util.DatabricksThriftUtil.*;
import static com.databricks.jdbc.common.util.DatabricksTypeUtil.DECIMAL;
import static com.databricks.jdbc.common.util.DatabricksTypeUtil.getDecimalTypeString;
import static com.databricks.jdbc.dbclient.impl.sqlexec.CommandName.LIST_FUNCTIONS;
import static com.databricks.jdbc.dbclient.impl.sqlexec.ResultConstants.TYPE_INFO_RESULT;

import com.databricks.jdbc.api.impl.*;
import com.databricks.jdbc.api.internal.IDatabricksConnectionContext;
import com.databricks.jdbc.api.internal.IDatabricksSession;
import com.databricks.jdbc.api.internal.IDatabricksStatementInternal;
import com.databricks.jdbc.common.IDatabricksComputeResource;
import com.databricks.jdbc.common.StatementType;
import com.databricks.jdbc.common.util.DatabricksThreadContextHolder;
import com.databricks.jdbc.common.util.DriverUtil;
import com.databricks.jdbc.common.util.ProtocolFeatureUtil;
import com.databricks.jdbc.dbclient.IDatabricksClient;
import com.databricks.jdbc.dbclient.IDatabricksMetadataClient;
import com.databricks.jdbc.dbclient.impl.common.MetadataResultSetBuilder;
import com.databricks.jdbc.dbclient.impl.common.StatementId;
import com.databricks.jdbc.dbclient.impl.sqlexec.CommandBuilder;
import com.databricks.jdbc.exception.DatabricksParsingException;
import com.databricks.jdbc.exception.DatabricksSQLException;
import com.databricks.jdbc.log.JdbcLogger;
import com.databricks.jdbc.log.JdbcLoggerFactory;
import com.databricks.jdbc.model.client.thrift.generated.*;
import com.databricks.jdbc.model.core.ExternalLink;
import com.databricks.jdbc.model.telemetry.enums.DatabricksDriverErrorCode;
import com.databricks.sdk.core.DatabricksConfig;
import com.google.common.annotations.VisibleForTesting;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class DatabricksThriftServiceClient implements IDatabricksClient, IDatabricksMetadataClient {

  private static final JdbcLogger LOGGER =
      JdbcLoggerFactory.getLogger(DatabricksThriftServiceClient.class);
  private final DatabricksThriftAccessor thriftAccessor;
  private final IDatabricksConnectionContext connectionContext;
  private TProtocolVersion serverProtocolVersion = JDBC_THRIFT_VERSION;
  private final MetadataResultSetBuilder metadataResultSetBuilder;

  public DatabricksThriftServiceClient(IDatabricksConnectionContext connectionContext)
      throws DatabricksParsingException {
    this.connectionContext = connectionContext;
    this.thriftAccessor = new DatabricksThriftAccessor(connectionContext);
    this.metadataResultSetBuilder = new MetadataResultSetBuilder(connectionContext);
  }

  @VisibleForTesting
  DatabricksThriftServiceClient(
      DatabricksThriftAccessor thriftAccessor, IDatabricksConnectionContext connectionContext) {
    this.thriftAccessor = thriftAccessor;
    this.connectionContext = connectionContext;
    this.metadataResultSetBuilder = new MetadataResultSetBuilder(connectionContext);
  }

  @VisibleForTesting
  void setServerProtocolVersion(TProtocolVersion serverProtocolVersion) {
    this.serverProtocolVersion = serverProtocolVersion;
  }

  private boolean isMultipleCatalogSupportEnabled() {
    return connectionContext == null || connectionContext.getEnableMultipleCatalogSupport();
  }

  @Override
  public IDatabricksConnectionContext getConnectionContext() {
    return connectionContext;
  }

  @Override
  public void resetAccessToken(String newAccessToken) {
    ((DatabricksHttpTTransport) thriftAccessor.getThriftClient().getInputProtocol().getTransport())
        .resetAccessToken(newAccessToken);
  }

  @Override
  public ImmutableSessionInfo createSession(
      IDatabricksComputeResource cluster,
      String catalog,
      String schema,
      Map<String, String> sessionConf)
      throws DatabricksSQLException {
    LOGGER.debug(
        String.format(
            "public Session createSession(Compute cluster = {%s}, String catalog = {%s}, String schema = {%s}, Map<String, String> sessionConf = {%s})",
            cluster.toString(), catalog, schema, sessionConf));
    TOpenSessionReq openSessionReq =
        new TOpenSessionReq()
            .setConfiguration(sessionConf)
            .setCanUseMultipleCatalogs(isMultipleCatalogSupportEnabled())
            .setClient_protocol_i64(JDBC_THRIFT_VERSION.getValue());
    if (catalog != null || schema != null) {
      openSessionReq.setInitialNamespace(getNamespace(catalog, schema));
    }
    TOpenSessionResp response = (TOpenSessionResp) thriftAccessor.getThriftResponse(openSessionReq);
    verifySuccessStatus(response.status, response.toString());

    // cache the server protocol version
    serverProtocolVersion = response.getServerProtocolVersion();
    thriftAccessor.setServerProtocolVersion(
        serverProtocolVersion); // save protocol version in thriftAccessor

    if (ProtocolFeatureUtil.isNonDatabricksCompute(serverProtocolVersion)) {
      throw new DatabricksSQLException(
          "Attempting to connect to a non Databricks compute using the Databricks driver.",
          DatabricksDriverErrorCode.UNSUPPORTED_OPERATION);
    }

    String sessionId = byteBufferToString(response.sessionHandle.getSessionId().guid);
    DatabricksThreadContextHolder.setSessionId(sessionId);
    LOGGER.debug("Session created with ID {}", sessionId);
    return ImmutableSessionInfo.builder()
        .sessionId(sessionId)
        .sessionHandle(response.sessionHandle)
        .computeResource(cluster)
        .build();
  }

  @Override
  public void deleteSession(ImmutableSessionInfo sessionInfo) throws DatabricksSQLException {
    LOGGER.debug(
        String.format(
            "public void deleteSession(Session session = {%s}))", sessionInfo.toString()));
    DatabricksThreadContextHolder.setSessionId(sessionInfo.sessionId());
    TCloseSessionReq closeSessionReq =
        new TCloseSessionReq().setSessionHandle(sessionInfo.sessionHandle());
    TCloseSessionResp response =
        (TCloseSessionResp) thriftAccessor.getThriftResponse(closeSessionReq);
    verifySuccessStatus(response.status, response.toString());
  }

  @Override
  public DatabricksResultSet executeStatement(
      String sql,
      IDatabricksComputeResource computeResource,
      Map<Integer, ImmutableSqlParameter> parameters,
      StatementType statementType,
      IDatabricksSession session,
      IDatabricksStatementInternal parentStatement)
      throws SQLException {

    LOGGER.debug(
        String.format(
            "public DatabricksResultSet executeStatement(String sql = {%s}, Compute cluster = {%s}, Map<Integer, ImmutableSqlParameter> parameters = {%s}, StatementType statementType = {%s}, IDatabricksSession session)",
            sql, computeResource, parameters.toString(), statementType));

    DatabricksThreadContextHolder.setStatementType(statementType);

    TExecuteStatementReq request = getRequest(sql, parameters, session, parentStatement, false);

    return thriftAccessor.execute(request, parentStatement, session, statementType);
  }

  @Override
  public DatabricksResultSet executeStatementAsync(
      String sql,
      IDatabricksComputeResource computeResource,
      Map<Integer, ImmutableSqlParameter> parameters,
      IDatabricksSession session,
      IDatabricksStatementInternal parentStatement)
      throws SQLException {

    LOGGER.debug(
        String.format(
            "public DatabricksResultSet executeStatementAsync(String sql = {%s}, Compute cluster = {%s}, Map<Integer, ImmutableSqlParameter> parameters = {%s})",
            sql, computeResource.toString(), parameters.toString()));

    TExecuteStatementReq request = getRequest(sql, parameters, session, parentStatement, true);

    return thriftAccessor.executeAsync(request, parentStatement, session, StatementType.SQL);
  }

  @VisibleForTesting
  TSparkParameter mapToSparkParameterListItem(ImmutableSqlParameter parameter) {
    Object value = parameter.value();
    String typeString = parameter.type().name();
    if (typeString.equals(DECIMAL) && value instanceof BigDecimal) {
      typeString = getDecimalTypeString((BigDecimal) value);
    }
    return new TSparkParameter()
        .setOrdinal(parameter.cardinal())
        .setType(typeString)
        .setValue(value != null ? TSparkParameterValue.stringValue(value.toString()) : null);
  }

  private TExecuteStatementReq getRequest(
      String sql,
      Map<Integer, ImmutableSqlParameter> parameters,
      IDatabricksSession session,
      IDatabricksStatementInternal parentStatement,
      boolean runAsync)
      throws SQLException {
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    TSparkArrowTypes arrowNativeTypes = new TSparkArrowTypes().setTimestampAsArrow(true);

    // Convert the parameters to a list of TSparkParameter objects.
    List<TSparkParameter> sparkParameters =
        parameters.values().stream()
            .map(this::mapToSparkParameterListItem)
            .collect(Collectors.toList());
    int timeout = DEFAULT_STATEMENT_TIMEOUT_SECONDS;
    if (parentStatement != null && parentStatement.getStatement() != null) {
      timeout = parentStatement.getStatement().getQueryTimeout();
    }
    TExecuteStatementReq request =
        new TExecuteStatementReq()
            .setStatement(sql)
            .setQueryTimeout(timeout)
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setCanReadArrowResult(this.connectionContext.shouldEnableArrow())
            .setUseArrowNativeTypes(arrowNativeTypes);

    // Conditionally set parameters based on server protocol version
    if (ProtocolFeatureUtil.supportsParameterizedQueries(serverProtocolVersion)) {
      request.setParameters(sparkParameters);
    }
    if (ProtocolFeatureUtil.supportsCompressedArrowBatches(serverProtocolVersion)) {
      request.setCanDecompressLZ4Result(true);
    }
    if (ProtocolFeatureUtil.supportsCloudFetch(serverProtocolVersion)) {
      request.setCanDownloadResult(true);
    }
    if (ProtocolFeatureUtil.supportsAdvancedArrowTypes(serverProtocolVersion)) {
      arrowNativeTypes
          .setComplexTypesAsArrow(true)
          .setIntervalTypesAsArrow(true)
          .setNullTypeAsArrow(true)
          .setDecimalAsArrow(true);
      request.setUseArrowNativeTypes(arrowNativeTypes);
    }

    int maxRows = (parentStatement == null) ? 0 : parentStatement.getMaxRows();
    if (maxRows > 0) { // set request param only if user has set maxRows.
      // Similar
      // behavior
      // to SEA flow
      request.setResultRowLimit(maxRows);
    }

    if (runAsync || !DriverUtil.isRunningAgainstFake()) {
      request.setRunAsync(true);
    }

    return request;
  }

  @Override
  public void closeStatement(StatementId statementId) throws DatabricksSQLException {
    LOGGER.debug(
        String.format(
            "public void closeStatement(String statementId = {%s}) using Thrift client",
            statementId));
    DatabricksThreadContextHolder.setStatementId(statementId);
    TCloseOperationReq request =
        new TCloseOperationReq().setOperationHandle(getOperationHandle(statementId));
    TCloseOperationResp resp = thriftAccessor.closeOperation(request);
    LOGGER.debug("Statement {} closed with status {}", statementId, resp.getStatus());
  }

  @Override
  public void cancelStatement(StatementId statementId) throws DatabricksSQLException {
    LOGGER.debug(
        String.format(
            "public void cancelStatement(String statementId = {%s}) using Thrift client",
            statementId));
    DatabricksThreadContextHolder.setStatementId(statementId);
    TCancelOperationReq request =
        new TCancelOperationReq().setOperationHandle(getOperationHandle(statementId));
    TCancelOperationResp resp = thriftAccessor.cancelOperation(request);
    LOGGER.debug("Statement {} cancelled with status {}", statementId, resp.getStatus());
  }

  @Override
  public DatabricksResultSet getStatementResult(
      StatementId statementId,
      IDatabricksSession session,
      IDatabricksStatementInternal parentStatement)
      throws SQLException {
    LOGGER.debug(
        String.format(
            "public DatabricksResultSet getStatementResult(String statementId = {%s}) using Thrift client",
            statementId));
    DatabricksThreadContextHolder.setStatementId(statementId);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    return thriftAccessor.getStatementResult(
        getOperationHandle(statementId), parentStatement, session);
  }

  @Override
  public Collection<ExternalLink> getResultChunks(StatementId statementId, long chunkIndex)
      throws DatabricksSQLException {
    String context =
        String.format(
            "public Optional<ExternalLink> getResultChunk(String statementId = {%s}, long chunkIndex = {%s}) using Thrift client",
            statementId, chunkIndex);
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setStatementId(statementId);
    TFetchResultsResp fetchResultsResp;
    List<ExternalLink> externalLinks = new ArrayList<>();
    AtomicInteger index = new AtomicInteger(0);
    do {
      fetchResultsResp = thriftAccessor.getResultSetResp(getOperationHandle(statementId), context);
      fetchResultsResp
          .getResults()
          .getResultLinks()
          .forEach(
              resultLink ->
                  externalLinks.add(createExternalLink(resultLink, index.getAndIncrement())));
    } while (fetchResultsResp.hasMoreRows);
    if (chunkIndex < 0 || externalLinks.size() <= chunkIndex) {
      String error = String.format("Out of bounds error for chunkIndex. Context: %s", context);
      LOGGER.error(error);
      throw new DatabricksSQLException(error, DatabricksDriverErrorCode.INVALID_STATE);
    }
    return externalLinks;
  }

  @Override
  public DatabricksResultSet listTypeInfo(IDatabricksSession session) {
    LOGGER.debug("public ResultSet getTypeInfo()");
    return TYPE_INFO_RESULT;
  }

  @Override
  public DatabricksResultSet listCatalogs(IDatabricksSession session) throws SQLException {
    String context =
        String.format("Fetching catalogs using Thrift client. Session {%s}", session.toString());
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());

    // If multiple catalog support is disabled, return only the current catalog
    if (!isMultipleCatalogSupportEnabled()) {
      String currentCatalog = session.getCurrentCatalog();
      if (currentCatalog == null || currentCatalog.isEmpty()) {
        currentCatalog = "";
      }
      List<List<Object>> singleCatalogRows = new ArrayList<>();
      List<Object> catalogRow = new ArrayList<>();
      catalogRow.add(currentCatalog);
      singleCatalogRows.add(catalogRow);
      return metadataResultSetBuilder.getCatalogsResult(singleCatalogRows);
    }

    TGetCatalogsReq request =
        new TGetCatalogsReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle());
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true); // support async metadata execution if supported
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getCatalogsResult(
        extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listSchemas(
      IDatabricksSession session, String catalog, String schemaNamePattern) throws SQLException {
    String context =
        String.format(
            "Fetching schemas using Thrift client. Session {%s}, catalog {%s}, schemaNamePattern {%s}",
            session.toString(), catalog, schemaNamePattern);
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    TGetSchemasReq request =
        new TGetSchemasReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setCatalogName(catalog);
    if (schemaNamePattern != null) {
      request.setSchemaName(schemaNamePattern);
    }
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getSchemasResult(
        extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listTables(
      IDatabricksSession session,
      String catalog,
      String schemaNamePattern,
      String tableNamePattern,
      String[] tableTypes)
      throws SQLException {
    String context =
        String.format(
            "Fetching tables using Thrift client. Session {%s}, catalog {%s}, schemaNamePattern {%s}, tableNamePattern {%s}",
            session.toString(), catalog, schemaNamePattern, tableNamePattern);
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    TGetTablesReq request =
        new TGetTablesReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setCatalogName(catalog)
            .setSchemaName(schemaNamePattern)
            .setTableName(tableNamePattern);
    if (tableTypes != null) {
      request.setTableTypes(Arrays.asList(tableTypes));
    }
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getTablesResult(
        catalog, tableTypes, extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listTableTypes(IDatabricksSession session) {
    LOGGER.debug(
        String.format(
            "Fetching table types using Thrift client. Session {%s}", session.toString()));
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    return metadataResultSetBuilder.getTableTypesResult();
  }

  @Override
  public DatabricksResultSet listColumns(
      IDatabricksSession session,
      String catalog,
      String schemaNamePattern,
      String tableNamePattern,
      String columnNamePattern)
      throws DatabricksSQLException {
    String context =
        String.format(
            "Fetching columns using Thrift client. Session {%s}, catalog {%s}, schemaNamePattern {%s}, tableNamePattern {%s}, columnNamePattern {%s}",
            session.toString(), catalog, schemaNamePattern, tableNamePattern, columnNamePattern);
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    TGetColumnsReq request =
        new TGetColumnsReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setCatalogName(catalog)
            .setSchemaName(schemaNamePattern)
            .setTableName(tableNamePattern)
            .setColumnName(columnNamePattern);
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getColumnsResult(
        extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listFunctions(
      IDatabricksSession session,
      String catalog,
      String schemaNamePattern,
      String functionNamePattern)
      throws SQLException {
    String context =
        String.format(
            "Fetching functions using Thrift client. Session {%s}, catalog {%s}, schemaNamePattern {%s}, functionNamePattern {%s}.",
            session.toString(), catalog, schemaNamePattern, functionNamePattern);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    LOGGER.debug(context);
    if (connectionContext.enableShowCommandsForGetFunctions()) {
      String showFunctionsSqlCommand =
          new CommandBuilder(catalog, session)
              .setSchemaPattern(schemaNamePattern)
              .setFunctionPattern(functionNamePattern)
              .getSQLString(LIST_FUNCTIONS);
      LOGGER.debug(
          "Fetching functions using SQL Command {{}}. Session {{}}",
          showFunctionsSqlCommand,
          session.toString());
      try (DatabricksResultSet rs =
          executeStatement(
              showFunctionsSqlCommand,
              session.getComputeResource(),
              Collections.emptyMap(),
              StatementType.METADATA,
              session,
              null)) {
        return metadataResultSetBuilder.getFunctionsResult(rs, catalog);
      }
    }
    TGetFunctionsReq request =
        new TGetFunctionsReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setCatalogName(catalog)
            .setSchemaName(schemaNamePattern)
            .setFunctionName(functionNamePattern);
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getFunctionsResult(
        catalog, extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listPrimaryKeys(
      IDatabricksSession session, String catalog, String schema, String table) throws SQLException {
    String context =
        String.format(
            "Fetching primary keys using Thrift client. session {%s}, catalog {%s}, schema {%s}, table {%s}",
            session.toString(), catalog, schema, table);
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    TGetPrimaryKeysReq request =
        new TGetPrimaryKeysReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setCatalogName(catalog)
            .setSchemaName(schema)
            .setTableName(table);
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getPrimaryKeysResult(
        extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listImportedKeys(
      IDatabricksSession session, String catalog, String schema, String table) throws SQLException {
    String context =
        String.format(
            "Fetching imported keys using Thrift client for session {%s}, catalog {%s}, schema {%s}, table {%s}",
            session.toString(), catalog, schema, table);
    LOGGER.debug(context);
    DatabricksThreadContextHolder.setSessionId(session.getSessionId());
    // GetImportedKeys is implemented using GetCrossReferences
    // When only foreign table name is provided, we get imported keys
    TGetCrossReferenceReq request =
        new TGetCrossReferenceReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setForeignCatalogName(catalog)
            .setForeignSchemaName(schema)
            .setForeignTableName(table);
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getImportedKeys(extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listExportedKeys(
      IDatabricksSession session, String catalog, String schema, String table) throws SQLException {
    String context =
        String.format(
            "Fetching exported keys using Thrift client for session {%s}, catalog {%s}, schema {%s}, table {%s}",
            session.toString(), catalog, schema, table);
    LOGGER.debug(context);
    // GetImportedKeys is implemented using GetCrossReferences
    // When only parent table name is provided, we get exported keys
    TGetCrossReferenceReq request =
        new TGetCrossReferenceReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setParentCatalogName(catalog)
            .setParentSchemaName(schema)
            .setParentTableName(table);
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getExportedKeys(extractRowsFromColumnar(response.getResults()));
  }

  @Override
  public DatabricksResultSet listCrossReferences(
      IDatabricksSession session,
      String parentCatalog,
      String parentSchema,
      String parentTable,
      String foreignCatalog,
      String foreignSchema,
      String foreignTable)
      throws SQLException {
    String context =
        String.format(
            "Fetching cross references using Thrift client for session {%s}, catalog {%s}, schema {%s}, table {%s}, foreign catalog {%s}, foreign schema {%s}, foreign table {%s}",
            session.toString(),
            parentCatalog,
            parentSchema,
            parentTable,
            foreignCatalog,
            foreignSchema,
            foreignTable);
    LOGGER.debug(context);
    TGetCrossReferenceReq request =
        new TGetCrossReferenceReq()
            .setSessionHandle(Objects.requireNonNull(session.getSessionInfo()).sessionHandle())
            .setParentCatalogName(parentCatalog)
            .setParentSchemaName(parentSchema)
            .setParentTableName(parentTable)
            .setForeignCatalogName(foreignCatalog)
            .setForeignSchemaName(foreignSchema)
            .setForeignTableName(foreignTable);
    if (ProtocolFeatureUtil.supportsAsyncMetadataExecution(serverProtocolVersion)) {
      request.setRunAsync(true);
    }
    TFetchResultsResp response = (TFetchResultsResp) thriftAccessor.getThriftResponse(request);
    return metadataResultSetBuilder.getCrossRefsResult(
        extractRowsFromColumnar(response.getResults()));
  }

  public TFetchResultsResp getMoreResults(IDatabricksStatementInternal parentStatement)
      throws DatabricksSQLException {
    return thriftAccessor.getMoreResults(parentStatement);
  }

  @Override
  public DatabricksConfig getDatabricksConfig() {
    return thriftAccessor.getDatabricksConfig();
  }

  private TNamespace getNamespace(String catalog, String schema) {
    final TNamespace namespace = new TNamespace();
    if (catalog != null) {
      namespace.setCatalogName(catalog);
    }
    if (schema != null) {
      namespace.setSchemaName(schema);
    }

    return namespace;
  }
}
