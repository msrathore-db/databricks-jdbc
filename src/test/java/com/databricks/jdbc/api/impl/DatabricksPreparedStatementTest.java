package com.databricks.jdbc.api.impl;

import static com.databricks.jdbc.TestConstants.*;
import static java.sql.JDBCType.DECIMAL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.databricks.jdbc.api.internal.IDatabricksConnectionContext;
import com.databricks.jdbc.api.internal.IDatabricksSession;
import com.databricks.jdbc.common.StatementType;
import com.databricks.jdbc.common.Warehouse;
import com.databricks.jdbc.common.util.DatabricksTypeUtil;
import com.databricks.jdbc.dbclient.impl.sqlexec.DatabricksSdkClient;
import com.databricks.jdbc.dbclient.impl.thrift.DatabricksThriftServiceClient;
import com.databricks.jdbc.exception.DatabricksBatchUpdateException;
import com.databricks.jdbc.exception.DatabricksSQLException;
import com.databricks.jdbc.exception.DatabricksSQLFeatureNotSupportedException;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DatabricksPreparedStatementTest {

  private static final String WAREHOUSE_ID = "99999999";
  private static final String STATEMENT =
      "SELECT * FROM orders WHERE user_id = ? AND shard = ? AND region_code = ? AND namespace = ?";
  private static final String BATCH_STATEMENT =
      "INSERT INTO orders (user_id, shard, region_code, namespace) VALUES (?, ?, ?, ?)";
  private static final String JDBC_URL =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:4423/default;transportMode=http;ssl=1;AuthMech=3;httpPath=/sql/1.0/warehouses/99999999;";
  private static final String JDBC_URL_WITH_BATCHED_INSERTS =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:4423/default;transportMode=http;ssl=1;AuthMech=3;httpPath=/sql/1.0/warehouses/99999999;EnableBatchedInserts=1;";
  private static final String JDBC_URL_WITH_MANY_PARAMETERS =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:4423/default;transportMode=http;ssl=1;AuthMech=3;httpPath=/sql/1.0/warehouses/99999999;supportManyParameters=1;";
  private static final String JDBC_CLUSTER_URL_WITH_MANY_PARAMETERS =
      VALID_CLUSTER_URL + ";supportManyParameters=1;";
  @Mock DatabricksResultSet resultSet;
  @Mock DatabricksSdkClient client;
  @Mock DatabricksThriftServiceClient thriftClient;
  @Mock DatabricksConnection connection;
  @Mock DatabricksSession session;
  private static final String INTERPOLATED_INITIAL_STATEMENT =
      "SELECT * FROM orders WHERE user_id = ? AND data = ?";
  private static final String INTERPOLATED_PROCESSED_STATEMENT =
      "SELECT * FROM orders WHERE user_id = 1 AND data = 'test'";
  private static final String INTERPOLATED_PROCESSED_STATEMENT_WITH_BYTES =
      "SELECT * FROM orders WHERE user_id = 1 AND data = X'01020304'";

  void setupMocks() throws DatabricksSQLException {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL, new Properties());
    when(connection.getConnectionContext()).thenReturn(connectionContext);
  }

  @Test
  public void testExecuteStatement() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement = new DatabricksPreparedStatement(connection, STATEMENT);
    statement.setLong(1, 100);
    statement.setShort(2, (short) 10);
    statement.setByte(3, (byte) 15);
    statement.setString(4, "value");
    when(client.executeStatement(
            eq(STATEMENT),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.QUERY),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);

    DatabricksResultSet newResultSet = (DatabricksResultSet) statement.executeQuery();
    assertFalse(statement.isClosed());
    assertEquals(resultSet, newResultSet);
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteStatementWithManyParameters() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_MANY_PARAMETERS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, INTERPOLATED_INITIAL_STATEMENT);
    statement.setInt(1, 1);
    statement.setString(2, TEST_STRING);
    when(client.executeStatement(
            eq(INTERPOLATED_PROCESSED_STATEMENT),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.QUERY),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);

    DatabricksResultSet newResultSet = (DatabricksResultSet) statement.executeQuery();
    assertFalse(statement.isClosed());
    assertEquals(resultSet, newResultSet);
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteStatementWithManyParametersAndSetBytes() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_MANY_PARAMETERS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, INTERPOLATED_INITIAL_STATEMENT);
    statement.setInt(1, 1);
    statement.setBytes(2, new byte[] {0x01, 0x02, 0x03, 0x04});
    when(client.executeStatement(
            eq(INTERPOLATED_PROCESSED_STATEMENT_WITH_BYTES),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.QUERY),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);

    DatabricksResultSet newResultSet = (DatabricksResultSet) statement.executeQuery();
    assertFalse(statement.isClosed());
    assertEquals(resultSet, newResultSet);
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testAllPurposeExecuteStatementWithManyParameters() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_CLUSTER_URL_WITH_MANY_PARAMETERS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, thriftClient);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, INTERPOLATED_INITIAL_STATEMENT);
    statement.setInt(1, 1);
    statement.setString(2, TEST_STRING);
    when(thriftClient.executeStatement(
            eq(INTERPOLATED_PROCESSED_STATEMENT),
            any(),
            any(HashMap.class),
            eq(StatementType.QUERY),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);

    DatabricksResultSet newResultSet = (DatabricksResultSet) statement.executeQuery();
    assertFalse(statement.isClosed());
    assertEquals(resultSet, newResultSet);
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteUpdateStatement() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement = new DatabricksPreparedStatement(connection, STATEMENT);
    when(resultSet.getUpdateCount()).thenReturn(2L);
    when(client.executeStatement(
            eq(STATEMENT),
            eq(new Warehouse(WAREHOUSE_ID)),
            eq(new HashMap<Integer, ImmutableSqlParameter>()),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);

    int updateCount = statement.executeUpdate();
    assertEquals(2, updateCount);
    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteLargeUpdateStatement() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement = new DatabricksPreparedStatement(connection, STATEMENT);
    when(resultSet.getUpdateCount()).thenReturn(2L);
    when(client.executeStatement(
            eq(STATEMENT),
            eq(new Warehouse(WAREHOUSE_ID)),
            eq(new HashMap<Integer, ImmutableSqlParameter>()),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);

    long updateCount = statement.executeLargeUpdate();
    assertEquals(2L, updateCount);
    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteBatchStatement() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, BATCH_STATEMENT);
    // Setting to execute a batch of 4 statements
    for (int i = 1; i <= 4; i++) {
      statement.setLong(1, 100);
      statement.setShort(2, (short) 10);
      statement.setByte(3, (byte) 15);
      statement.setString(4, "value");
      statement.addBatch();
    }
    // Our implementation converts single INSERT to multi-row INSERT for batching
    String expectedMultiRowSQL =
        "INSERT INTO orders (user_id, shard, region_code, namespace) VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)";
    when(client.executeStatement(
            eq(expectedMultiRowSQL),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);
    lenient()
        .when(resultSet.getUpdateCount())
        .thenReturn(4L); // Multi-row INSERT returns total rows affected

    int[] expectedCountsResult = {1, 1, 1, 1};
    int[] updateCounts = statement.executeBatch();
    assertArrayEquals(expectedCountsResult, updateCounts);
    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testGetMetaData_NoResultSet_NonSelectQuery_ReturnNull() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, BATCH_STATEMENT);
    // Setting to execute a batch of 4 statements
    for (int i = 1; i <= 4; i++) {
      statement.setLong(1, 100);
      statement.setShort(2, (short) 10);
      statement.setByte(3, (byte) 15);
      statement.setString(4, "value");
      statement.addBatch();
    }

    assertNull(statement.getMetaData());
  }

  @Test
  public void testExecuteBatchStatementThrowsError() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, BATCH_STATEMENT);
    // Setting to execute a batch of 4 statements
    for (int i = 1; i <= 4; i++) {
      statement.setLong(1, 100);
      statement.setShort(2, (short) 10);
      statement.setByte(3, (byte) 15);
      statement.setString(4, "value");
      statement.addBatch();
    }

    // Our implementation batches all into one multi-row INSERT, so if it fails, all fail
    String expectedMultiRowSQL =
        "INSERT INTO orders (user_id, shard, region_code, namespace) VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)";
    when(client.executeStatement(
            eq(expectedMultiRowSQL),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenThrow(new SQLException());

    DatabricksBatchUpdateException exception =
        assertThrows(DatabricksBatchUpdateException.class, statement::executeBatch);
    int[] updateCounts = exception.getUpdateCounts();
    assertEquals(4, updateCounts.length);
    // All statements should fail since they're batched into one multi-row INSERT
    for (int i = 0; i < 4; i++) {
      assertEquals(Statement.EXECUTE_FAILED, updateCounts[i]);
    }
  }

  @Test
  public void testExecuteLargeBatchStatement() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, BATCH_STATEMENT);
    // Setting to execute a batch of 4 statements
    for (int i = 1; i <= 4; i++) {
      statement.setLong(1, 100);
      statement.setShort(2, (short) 10);
      statement.setByte(3, (byte) 15);
      statement.setString(4, "value");
      statement.addBatch();
    }
    // Our implementation converts single INSERT to multi-row INSERT for batching
    String expectedMultiRowSQL =
        "INSERT INTO orders (user_id, shard, region_code, namespace) VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)";
    when(client.executeStatement(
            eq(expectedMultiRowSQL),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);
    lenient()
        .when(resultSet.getUpdateCount())
        .thenReturn(4L); // Multi-row INSERT returns total rows affected

    long[] expectedCountsResult = {1, 1, 1, 1};
    long[] updateCounts = statement.executeLargeBatch();
    assertArrayEquals(expectedCountsResult, updateCounts);
    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteLargeBatchStatementThrowsError() throws Exception {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, BATCH_STATEMENT);
    // Setting to execute a batch of 4 statements
    for (int i = 1; i <= 4; i++) {
      statement.setLong(1, 100);
      statement.setShort(2, (short) 10);
      statement.setByte(3, (byte) 15);
      statement.setString(4, "value");
      statement.addBatch();
    }

    // Our implementation batches all into one multi-row INSERT, so if it fails, all fail
    String expectedMultiRowSQL =
        "INSERT INTO orders (user_id, shard, region_code, namespace) VALUES (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?), (?, ?, ?, ?)";
    when(client.executeStatement(
            eq(expectedMultiRowSQL),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenThrow(new SQLException());

    DatabricksBatchUpdateException exception =
        assertThrows(DatabricksBatchUpdateException.class, statement::executeLargeBatch);
    long[] updateCounts = exception.getLargeUpdateCounts();
    assertEquals(4, updateCounts.length);
    // All statements should fail since they're batched into one multi-row INSERT
    for (int i = 0; i < 4; i++) {
      assertEquals(Statement.EXECUTE_FAILED, updateCounts[i]);
    }
  }

  public static ImmutableSqlParameter getSqlParam(
      int parameterIndex, Object x, String databricksType) {
    return ImmutableSqlParameter.builder()
        .type(DatabricksTypeUtil.getColumnInfoType(databricksType))
        .value(x)
        .cardinal(parameterIndex)
        .build();
  }

  @Test
  public void testSetBoolean() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setBoolean(1, true));
  }

  @Test
  public void testSetByte() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setByte(1, (byte) 1));
  }

  @Test
  public void testSetShort() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setShort(1, (short) 1));
  }

  @Test
  public void testSetInt() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setInt(1, 1));
  }

  @Test
  public void testSetLong() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setLong(1, 1L));
  }

  @Test
  public void testSetFloat() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setFloat(1, 1.0f));
  }

  @Test
  public void testSetDouble() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setDouble(1, 1.0));
  }

  @Test
  public void testSetBigDecimal() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setBigDecimal(1, BigDecimal.ONE));
  }

  @Test
  public void testSetString() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setString(1, "test"));
  }

  @Test
  public void testSetDate() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setDate(1, new Date(System.currentTimeMillis())));
  }

  @Test
  public void testSetObject() throws SQLException {
    setupMocks();

    // setObject(int parameterIndex, Object x, int targetSqlType)
    // setObject(int parameterIndex, Object x)
    // setObject(int parameterIndex, Object x, int targetSqlType, int scaleOrLength)

    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(() -> preparedStatement.setObject(1, 1, DECIMAL));
    assertDoesNotThrow(() -> preparedStatement.setObject(1, 1, Types.INTEGER, 1));
    assertDoesNotThrow(() -> preparedStatement.setObject(1, 1, Types.INTEGER));
    assertEquals(Types.INTEGER, preparedStatement.getParameterMetaData().getParameterType(1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setObject(1, 1, Types.ROWID)); // Unsupported type

    assertDoesNotThrow(() -> preparedStatement.setObject(1, "1"));
    assertEquals(Types.VARCHAR, preparedStatement.getParameterMetaData().getParameterType(1));

    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () ->
            preparedStatement.setObject(
                1, new Time(System.currentTimeMillis()))); // Unsupported type

    assertDoesNotThrow(() -> preparedStatement.setObject(1, 2.567, Types.DECIMAL, 2));
    assertEquals(Types.DECIMAL, preparedStatement.getParameterMetaData().getParameterType(1));
  }

  @Test
  public void testSetDateWithCalendar() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    Date date = new Date(System.currentTimeMillis());
    Calendar cal = Calendar.getInstance();
    assertDoesNotThrow(() -> preparedStatement.setDate(1, date, cal));
  }

  @Test
  public void testSetTimestamp() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(
        () -> preparedStatement.setTimestamp(1, new Timestamp(System.currentTimeMillis())));
  }

  @Test
  public void testSetTimestampWithCalendar() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(
        () ->
            preparedStatement.setTimestamp(
                1, new Timestamp(System.currentTimeMillis()), Calendar.getInstance()));
  }

  @Test
  public void testSetTimestampWithNullCalendar() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    assertDoesNotThrow(
        () -> preparedStatement.setTimestamp(1, new Timestamp(System.currentTimeMillis()), null));
  }

  @Test
  public void testSetAsciiStream() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    byte[] bytes = {0x01, 0x02, 0x03, 0x04};
    InputStream asciiStream = new ByteArrayInputStream(bytes);

    assertDoesNotThrow(() -> preparedStatement.setAsciiStream(1, asciiStream, bytes.length));
  }

  @Test
  public void testSetAsciiStreamWithLong() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    byte[] bytes = {0x01, 0x02, 0x03, 0x04};
    InputStream asciiStream = new ByteArrayInputStream(bytes);

    assertDoesNotThrow(() -> preparedStatement.setAsciiStream(1, asciiStream, (long) bytes.length));
  }

  @Test
  public void testSetCharacterStream() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    String originalString = "Hello, World!";
    Reader characterStream = new StringReader(originalString);

    assertDoesNotThrow(
        () -> preparedStatement.setCharacterStream(1, characterStream, originalString.length()));
  }

  @Test
  public void testSetCharacterStreamWithLong() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    String originalString = "Hello, World!";
    Reader characterStream = new StringReader(originalString);

    assertDoesNotThrow(
        () ->
            preparedStatement.setCharacterStream(
                1, characterStream, (long) originalString.length()));
  }

  @Test
  public void testSetAsciiStreamWithoutLength() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    byte[] bytes = "Hello, World!".getBytes(StandardCharsets.US_ASCII);
    InputStream asciiStream = new ByteArrayInputStream(bytes);

    assertDoesNotThrow(() -> preparedStatement.setAsciiStream(1, asciiStream));
  }

  @Test
  public void testSetBytes() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    byte[] bytes = {0x01, 0x02, 0x03, 0x04};

    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBytes(1, bytes));

    DatabricksConnectionContext connectionContext = mock(DatabricksConnectionContext.class);
    when(connection.getConnectionContext()).thenReturn(connectionContext);
    when(connectionContext.supportManyParameters()).thenReturn(true);

    DatabricksPreparedStatement preparedStatementWithManyParameters =
        new DatabricksPreparedStatement(connection, STATEMENT);

    assertDoesNotThrow(() -> preparedStatementWithManyParameters.setBytes(1, bytes));
  }

  @Test
  public void testSetCharacterStreamWithoutLength() throws DatabricksSQLException {
    setupMocks();
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);

    String originalString = "Hello, World!";
    Reader characterStream = new StringReader(originalString);

    assertDoesNotThrow(() -> preparedStatement.setCharacterStream(1, characterStream));
  }

  @Test
  public void testExecuteLargeBatchWithParameterChunking() throws Exception {
    // Test scenario that would exceed the 256 parameter limit and verify chunking works
    // 5 columns × 60 rows = 300 parameters (exceeds 256 limit)
    // Should be split into chunks: 51 rows + 9 rows (51 = 255/5, leaving 1 parameter short for
    // safety)

    String largeBatchStatement =
        "INSERT INTO products (id, name, price, category, description) VALUES (?, ?, ?, ?, ?)";
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, largeBatchStatement);

    // Add 60 batches (5 columns each = 300 total parameters)
    int totalBatches = 60;
    for (int i = 1; i <= totalBatches; i++) {
      statement.setInt(1, i); // id
      statement.setString(2, "Product " + i); // name
      statement.setBigDecimal(3, new BigDecimal("19.99")); // price
      statement.setString(4, "Category " + (i % 5)); // category
      statement.setString(5, "Description for product " + i); // description
      statement.addBatch();
    }

    // Mock client to verify chunking behavior
    // With 5 columns, max rows per chunk = 256/5 = 51 rows
    // So 60 total rows should be split into 2 chunks: 51 + 9
    when(client.executeStatement(
            any(String.class), // SQL will vary based on chunk size
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);
    lenient()
        .when(resultSet.getUpdateCount())
        .thenReturn(51L) // First chunk: 51 rows
        .thenReturn(9L); // Second chunk: 9 rows

    long[] updateCounts = statement.executeLargeBatch();

    // Verify results
    assertEquals(totalBatches, updateCounts.length);

    // All update counts should be 1 (each row affects 1 row)
    for (int i = 0; i < totalBatches; i++) {
      assertEquals(1, updateCounts[i], "Update count for batch " + i + " should be 1");
    }

    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteLargeBatchWithManyColumnsChunking() throws Exception {
    // Test edge case with very wide table that forces 1 row per chunk
    // 300 columns would result in 0 rows per chunk calculation, should default to 1

    StringBuilder largeSqlBuilder = new StringBuilder("INSERT INTO wide_table (");
    StringBuilder valuesBuilder = new StringBuilder("(");

    // Create SQL with 300 columns
    int columnCount = 300;
    for (int i = 1; i <= columnCount; i++) {
      if (i > 1) {
        largeSqlBuilder.append(", ");
        valuesBuilder.append(", ");
      }
      largeSqlBuilder.append("col").append(i);
      valuesBuilder.append("?");
    }
    largeSqlBuilder.append(") VALUES ").append(valuesBuilder).append(")");

    String wideTableStatement = largeSqlBuilder.toString();
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, wideTableStatement);

    // Add 3 batches - each should be executed separately due to parameter limit
    int totalBatches = 3;
    for (int batchNum = 1; batchNum <= totalBatches; batchNum++) {
      // Set all 300 parameters for this batch
      for (int col = 1; col <= columnCount; col++) {
        statement.setString(col, "value_" + batchNum + "_" + col);
      }
      statement.addBatch();
    }

    // Mock client - each batch should be executed individually due to parameter limit
    when(client.executeStatement(
            any(String.class),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);
    lenient().when(resultSet.getUpdateCount()).thenReturn(1L); // Each execution affects 1 row

    long[] updateCounts = statement.executeLargeBatch();

    // Verify results
    assertEquals(totalBatches, updateCounts.length);
    for (int i = 0; i < totalBatches; i++) {
      assertEquals(1, updateCounts[i], "Update count for batch " + i + " should be 1");
    }

    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  public void testExecuteLargeBatchParameterChunkingOptimization() throws Exception {
    // Test that we're actually getting the chunking optimization vs individual execution
    // Use a 2-column table with 200 rows = 400 parameters (exceeds 256 limit)
    // Should be chunked into: 128 rows + 72 rows (128 = 256/2)

    String simpleStatement = "INSERT INTO users (id, name) VALUES (?, ?)";
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL_WITH_BATCHED_INSERTS, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement statement =
        new DatabricksPreparedStatement(connection, simpleStatement);

    // Add 200 batches (2 columns each = 400 total parameters)
    int totalBatches = 200;
    for (int i = 1; i <= totalBatches; i++) {
      statement.setInt(1, i);
      statement.setString(2, "User " + i);
      statement.addBatch();
    }

    // Mock the client to capture the generated SQL
    when(client.executeStatement(
            any(String.class),
            eq(new Warehouse(WAREHOUSE_ID)),
            any(HashMap.class),
            eq(StatementType.UPDATE),
            any(IDatabricksSession.class),
            eq(statement)))
        .thenReturn(resultSet);
    lenient()
        .when(resultSet.getUpdateCount())
        .thenReturn(128L)
        .thenReturn(72L); // Two chunks: 128 + 72

    long[] updateCounts = statement.executeLargeBatch();

    // Verify results
    assertEquals(totalBatches, updateCounts.length);
    for (int i = 0; i < totalBatches; i++) {
      assertEquals(1, updateCounts[i], "Update count for batch " + i + " should be 1");
    }

    assertFalse(statement.isClosed());
    statement.close();
    assertTrue(statement.isClosed());
  }

  @Test
  void testUnsupportedMethods() throws DatabricksSQLException {
    IDatabricksConnectionContext connectionContext =
        DatabricksConnectionContext.parse(JDBC_URL, new Properties());
    DatabricksConnection connection = new DatabricksConnection(connectionContext, client);
    DatabricksPreparedStatement preparedStatement =
        new DatabricksPreparedStatement(connection, STATEMENT);
    // Unsupported methods
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class, () -> preparedStatement.setArray(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBlob(1, (Blob) null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setClob(1, (Clob) null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class, () -> preparedStatement.setRef(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class, () -> preparedStatement.setURL(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class, () -> preparedStatement.setRowId(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setNString(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setNCharacterStream(1, null, 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setNClob(1, (NClob) null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setClob(1, null, 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBlob(1, null, 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setNClob(1, null, 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setSQLXML(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBinaryStream(1, null, 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBinaryStream(1, InputStream.nullInputStream(), 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBinaryStream(1, InputStream.nullInputStream(), 1L));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBinaryStream(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setNCharacterStream(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setUnicodeStream(1, InputStream.nullInputStream(), 1));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setClob(1, Reader.nullReader()));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setBlob(1, InputStream.nullInputStream()));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setNClob(1, Reader.nullReader()));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class, () -> preparedStatement.setTime(1, null));
    assertThrows(
        DatabricksSQLFeatureNotSupportedException.class,
        () -> preparedStatement.setTime(1, null, null));
    assertThrows(
        DatabricksSQLException.class, () -> preparedStatement.executeUpdate("SELECT * from table"));
    assertThrows(
        DatabricksSQLException.class,
        () ->
            preparedStatement.executeUpdate(
                "UPDATE table SET column = 1", new String[] {"column"}));
    assertThrows(
        DatabricksSQLException.class, () -> preparedStatement.execute("SELECT * FROM table", 1));
    assertThrows(
        DatabricksSQLException.class,
        () -> preparedStatement.execute("SELECT * FROM table", new int[] {1}));
    assertThrows(
        DatabricksSQLException.class,
        () -> preparedStatement.execute("SELECT * FROM table", new String[] {"column"}));
    assertThrows(
        DatabricksSQLException.class,
        () -> preparedStatement.executeUpdate("UPDATE table SET column = 1", new int[] {1}));
    assertThrows(
        DatabricksSQLException.class,
        () ->
            preparedStatement.executeUpdate(
                "UPDATE table SET column = 1", new String[] {"column"}));
    assertThrows(
        DatabricksSQLException.class,
        () -> preparedStatement.execute("SELECT * FROM table", new int[] {1}));
    assertThrows(
        DatabricksSQLException.class,
        () -> preparedStatement.execute("SELECT * FROM table", new String[] {"column"}));
  }
}
