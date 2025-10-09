package com.databricks.jdbc;

import com.databricks.jdbc.api.impl.ImmutableSessionInfo;
import com.databricks.jdbc.common.AllPurposeCluster;
import com.databricks.jdbc.common.IDatabricksComputeResource;
import com.databricks.jdbc.common.Warehouse;
import com.databricks.jdbc.dbclient.impl.common.StatementId;
import com.databricks.jdbc.model.client.thrift.generated.*;
import com.databricks.sdk.core.DatabricksException;
import com.databricks.sdk.core.oauth.OpenIDConnectEndpoints;
import java.net.MalformedURLException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** TestConstants class contains all the constants that are used in the test classes. */
public class TestConstants {
  public static final String WAREHOUSE_ID = "warehouse_id";
  public static final String SESSION_ID = "12345678";
  public static final IDatabricksComputeResource CLUSTER_COMPUTE =
      new AllPurposeCluster("9999999999999999", "9999999999999999999");
  public static final Warehouse WAREHOUSE_COMPUTE = new Warehouse(WAREHOUSE_ID);

  public static final String TEST_SCHEMA = "testSchema";
  public static final String TEST_TABLE = "testTable";
  public static final Map<String, String> EMPTY_MAP = Collections.emptyMap();
  public static final String TEST_COLUMN = "testColumn";
  public static final String TEST_CATALOG = "catalog1";
  public static final String TEST_FOREIGN_CATALOG = "foreignCatalog";
  public static final String TEST_FOREIGN_SCHEMA = "foreignSchema";
  public static final String TEST_FOREIGN_TABLE = "foreignTable";
  public static final String TEST_FUNCTION_PATTERN = "functionPattern";
  public static final String TEST_STRING = "test";
  public static final String TEST_STRING_2 = "test2";
  public static final String TEST_USER = "testUser";
  public static final String TEST_PASSWORD = "testPassword";
  public static final StatementId TEST_STATEMENT_ID = new StatementId("statement_id");
  public static final String UC_VOLUME_CATALOG = "uc_volume_test_catalog";
  public static final String UC_VOLUME_SCHEMA = "uc_volume_test_schema";
  public static final String TEST_SCOPE_STRING = "URI:RS:000001:000999:DatabricksOAuth:TEST";

  public static final String DEFAULT_BENCHFOOD_HOST = "e2-benchfood.cloud.databricks.com";
  public static final String DEFAULT_BENCHFOOD_HTTP_PATH = "/sql/1.0/warehouses/6e681b20741e4674";

  public static final String DEFAULT_BENCHFOOD_HOST = "e2-benchfood.cloud.databricks.com";
  public static final String DEFAULT_BENCHFOOD_HTTP_PATH = "/sql/1.0/warehouses/6e681b20741e4674";

  public static final TSessionHandle SESSION_HANDLE =
      new TSessionHandle().setSessionId(new THandleIdentifier().setGuid(SESSION_ID.getBytes()));

  public static final ImmutableSessionInfo SESSION_INFO =
      ImmutableSessionInfo.builder()
          .sessionHandle(SESSION_HANDLE)
          .sessionId(SESSION_ID)
          .computeResource(CLUSTER_COMPUTE)
          .build();

  public static final String WAREHOUSE_JDBC_URL =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;transportMode=http;ssl=1;"
          + "AuthMech=3;httpPath=/sql/1.0/warehouses/warehouse_id;UserAgentEntry=MyApp";

  public static final String WAREHOUSE_JDBC_URL_WITH_THRIFT =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;transportMode=http;ssl=1;"
          + "AuthMech=3;httpPath=/sql/1.0/warehouses/warehouse_id;UseThriftClient=1;";

  public static final String WAREHOUSE_JDBC_URL_WITH_SEA =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;transportMode=http;ssl=1;"
          + "AuthMech=3;httpPath=/sql/1.0/warehouses/warehouse_id;UseThriftClient=0;";

  public static final String USER_AGENT_URL =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;transportMode=http;"
          + "ssl=1;httpPath=sql/protocolv1/o/9999999999999999/9999999999999999999;AuthMech=3;"
          + "UserAgentEntry=TEST/24.2.0.2712019";

  public static final String CLUSTER_JDBC_URL =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;transportMode=http;"
          + "ssl=1;httpPath=sql/protocolv1/o/9999999999999999/9999999999999999999;AuthMech=3;"
          + "UserAgentEntry=MyApp";

  public static final List<ByteBuffer> BINARY_ROW_SET_VALUES =
      List.of(ByteBuffer.wrap(TEST_STRING.getBytes()));
  public static final List<Boolean> BOOL_ROW_SET_VALUES = List.of(false, true, false, true);
  public static final List<Byte> BYTE_ROW_SET_VALUES =
      List.of((byte) 5, (byte) 4, (byte) 3, (byte) 2, (byte) 1);
  public static final List<Double> DOUBLE_ROW_SET_VALUES = List.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
  public static final List<Short> SHORT_ROW_SET_VALUES =
      List.of((short) 1, (short) 2, (short) 3, (short) 4);
  public static final List<Integer> INT_ROW_SET_VALUES = List.of(143, 243, 343, 443);
  public static final List<Long> LONG_ROW_SET_VALUES =
      List.of(1344343433L, 243433343443L, 3434343433443L, 443434343434L);
  public static final List<String> STRING_ROW_SET_VALUES =
      List.of(TEST_STRING, TEST_STRING, TEST_STRING);

  public static final TRowSet BINARY_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.binaryVal(new TBinaryColumn().setValues(BINARY_ROW_SET_VALUES))));
  public static final TRowSet BOOL_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.boolVal(new TBoolColumn().setValues(BOOL_ROW_SET_VALUES))));
  public static final TRowSet BYTE_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.byteVal(new TByteColumn().setValues(BYTE_ROW_SET_VALUES))));
  public static final TRowSet DOUBLE_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.doubleVal(new TDoubleColumn().setValues(DOUBLE_ROW_SET_VALUES))));
  public static final TRowSet I16_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.i16Val(new TI16Column().setValues(SHORT_ROW_SET_VALUES))));
  public static final TRowSet I32_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.i32Val(new TI32Column().setValues(INT_ROW_SET_VALUES))));
  public static final TRowSet I64_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.i64Val(new TI64Column().setValues(LONG_ROW_SET_VALUES))));
  public static final TRowSet STRING_ROW_SET =
      new TRowSet()
          .setColumns(
              Collections.singletonList(
                  TColumn.stringVal(new TStringColumn().setValues(STRING_ROW_SET_VALUES))));

  public static final int MIXED_ROW_SET_COUNT =
      Collections.min(
          List.of(
              BYTE_ROW_SET_VALUES.size(),
              DOUBLE_ROW_SET_VALUES.size(),
              STRING_ROW_SET_VALUES.size()));

  public static final TRowSet MIXED_ROW_SET =
      new TRowSet()
          .setColumns(
              List.of(
                  TColumn.byteVal(
                      new TByteColumn()
                          .setValues(BYTE_ROW_SET_VALUES.subList(0, MIXED_ROW_SET_COUNT))),
                  TColumn.doubleVal(
                      new TDoubleColumn()
                          .setValues(DOUBLE_ROW_SET_VALUES.subList(0, MIXED_ROW_SET_COUNT))),
                  TColumn.stringVal(
                      new TStringColumn()
                          .setValues(STRING_ROW_SET_VALUES.subList(0, MIXED_ROW_SET_COUNT)))));

  private static final TColumnDesc TEST_COLUMN_DESCRIPTION =
      new TColumnDesc().setColumnName("testCol");
  public static final TTableSchema TEST_TABLE_SCHEMA =
      new TTableSchema().setColumns(Collections.singletonList(TEST_COLUMN_DESCRIPTION));
  public static final byte[] TEST_BYTES =
      ByteBuffer.allocate(Long.BYTES).putLong(123456789L).array();

  public static final String TEST_CLIENT_ID = "test-client-id";
  public static final String TEST_TOKEN_URL = "https://sample-host.token.url";
  public static final String TEST_AUTH_URL = "https://sample-host.auth.url";
  public static final String TEST_DISCOVERY_URL = "https://sample-host.discovery.url";
  public static final String TEST_JWT_KID = "test-kid";
  public static final String TEST_SCOPE = "test-scope";
  public static final String TEST_JWT_ALGORITHM = "RS256";
  public static final String TEST_JWT_KEY_FILE = "src/test/resources/private_key.pem";
  public static final String TEST_ACCESS_TOKEN = "test-access-token";

  public static OpenIDConnectEndpoints TEST_OIDC_ENDPOINTS;

  static {
    try {
      TEST_OIDC_ENDPOINTS =
          new OpenIDConnectEndpoints(
              "https://sample-host.token.url", "https://sample-host.auth.url");
    } catch (MalformedURLException e) {
      throw new DatabricksException("Can't initiate test constant for OIDC. Error: " + e);
    }
  }

  public static final String TEST_OAUTH_RESPONSE =
      "{\n"
          + "  \"expires_in\": 3600,\n"
          + "  \"access_token\": \"test-access-token\",\n"
          + "  \"token_type\": \"Bearer\"\n"
          + "}";

  public static final String GCP_TEST_URL =
      "jdbc:databricks://sample-host.7.gcp.databricks.com:9999/default;transportMode=http;AuthMech=11;"
          + "Auth_Flow=1;httpPath=/sql/1.0/warehouses/9999999999999999;";

  public static final String VALID_URL_1 =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2";

  public static final String VALID_URL_2 =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999;LogLevel=invalid;EnableQueryResultLZ4Compression=1;"
          + "UseThriftClient=0";

  public static final String VALID_URL_3 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;transportMode=http;"
          + "ssl=0;AuthMech=3;httpPath=/sql/1.0/warehouses/9999999999999999;EnableQueryResultLZ4Compression=0;"
          + "UseThriftClient=1;LogLevel=1234;Auth_scope=URI:RS:000001:000999:DatabricksOAuth:TEST";

  public static final String VALID_URL_4 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999999999;QueryResultCompressionType=1;"
          + "EnableDirectResults=1;";

  public static final String VALID_URL_5 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999;ssl=0;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999999999;QueryResultCompressionType=1;"
          + "EnableDirectResults=0";

  public static final String VALID_URL_6 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/schemaName;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999999999;ConnCatalog=catalogName;ConnSchema=schemaName;"
          + "QueryResultCompressionType=1";

  public static final String VALID_URL_7 =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;"
          + "AuthMech=3;httpPath=/sql/1.0/endpoints/999999999;LogLevel=debug;LogPath=./test1;"
          + "auth_flow=2;enablearrow=0";

  public static final String VALID_URL_8 =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;port=123;AuthMech=3;"
          + "httpPath=/sql/1.0/endpoints/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;enablearrow=0;"
          + "OAuth2AuthorizationEndPoint=authEndpoint;OAuth2TokenEndpoint=tokenEndpoint;"
          + "OAuthDiscoveryURL=testDiscovery;discovery_mode=1;UseJWTAssertion=1;auth_scope=test_scope;"
          + "auth_kid=test_kid;Auth_JWT_Key_Passphrase=test_phrase;Auth_JWT_Key_File=test_key_file;"
          + "Auth_JWT_Alg=test_algo;enableTelemetry=1";

  public static final String VALID_URL_WITH_INVALID_COMPRESSION_TYPE =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999999999;QueryResultCompressionType=234";

  public static final String INVALID_URL_1 =
      "jdbc:oracle://sample-host.net/default;ssl=1;AuthMech=3;httpPath=/sql/1.0/warehouses/9999999999;";

  public static final String INVALID_URL_2 =
      "http:databricks://sample-host.net/default;ssl=1;AuthMech=3;httpPath=/sql/1.0/warehouses/9999999999;";

  public static final String INVALID_URL_3 =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;port=alphabetical;"
          + "AuthMech=3;httpPath=/sql/1.0/endpoints/999999999;LogLevel=debug;LogPath=./test1;"
          + "auth_flow=2;enablearrow=0";

  public static final String VALID_TEST_URL = "jdbc:databricks://test";

  public static final String VALID_CLUSTER_URL =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;ssl=1;"
          + "httpPath=sql/protocolv1/o/9999999999999999/9999999999999999999;AuthMech=3;loglevel=3";

  public static final String VALID_BASE_URL_1 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;";

  public static final String VALID_BASE_URL_2 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default";

  public static final String VALID_BASE_URL_3 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999";

  public static final String VALID_BASE_URL_4 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999;AuthMech=3";

  public static final String VALID_BASE_URL_5 =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999;EnableArrow=0;ConnCatalog=test";

  public static final String VALID_URL_WITH_PROXY =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999999999;UseProxy=1;ProxyHost=127.0.0.1;"
          + "ProxyPort=8080;ProxyAuth=1;ProxyUID=proxyUser;ProxyPwd=proxyPassword;";

  public static final String VALID_URL_WITH_PROXY_AND_CF_PROXY =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/9999999999999999;UseSystemProxy=1;UseProxy=1;ProxyHost=127.0.0.1;"
          + "ProxyPort=8080;ProxyAuth=1;ProxyUID=proxyUser;ProxyPwd=proxyPassword;UseCFProxy=1;"
          + "CFProxyHost=127.0.1.2;CFProxyPort=8081;CFProxyAuth=2;CFProxyUID=cfProxyUser;"
          + "CFProxyPwd=cfProxyPassword;enableTelemetry=1";

  public static final String VALID_URL_POLLING =
      "jdbc:databricks://sample-host.cloud.databricks.com:9999;ssl=1;asyncexecpollinterval=500;"
          + "AuthMech=3;httpPath=/sql/1.0/warehouses/9999999999999999;QueryResultCompressionType=1";

  public static final String VALID_URL_WITH_STAGING_ALLOWED_PATH =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;"
          + "StagingAllowedLocalPaths=/tmp;UCIngestionRetriableHttpCode=503,504;UCIngestionRetryTimeout=12";

  public static final String VALID_URL_WITH_VOLUME_ALLOWED_PATH =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;"
          + "VolumeOperationAllowedLocalPaths=/tmp2;VolumeOperationRetryableHttpCode=429,503,504;"
          + "VolumeOperationRetryTimeout=10";

  public static final String VALID_URL_WITH_CONN_CATALOG_CONN_SCHEMA_PROVIDED =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;"
          + "VolumeOperationAllowedLocalPaths=/tmp2;connCatalog=sampleCatalog;connSchema=sampleSchema";

  public static final String VALID_URL_WITH_CONN_CATALOG_CONN_SCHEMA_NOT_PROVIDED =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;"
          + "VolumeOperationAllowedLocalPaths=/tmp2;";

  public static final String VALID_URL_WITH_CONN_CATALOG_CONN_SCHEMA_NOT_PROVIDED_WITHOUT_SCHEMA =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;"
          + "VolumeOperationAllowedLocalPaths=/tmp2;";

  public static final String VALID_URL_WITH_CUSTOM_HEADERS =
      "jdbc:databricks://sample-host.18.azuredatabricks.net:9999/default;ssl=1;AuthMech=3;"
          + "httpPath=/sql/1.0/warehouses/999999999;LogLevel=debug;LogPath=./test1;auth_flow=2;"
          + "http.header.HEADER_KEY_1=headerValue1;http.header.headerKey2=headerValue2;";

  public static final List<TSparkArrowBatch> ARROW_BATCH_LIST =
      Collections.singletonList(
          new TSparkArrowBatch().setRowCount(0).setBatch(new byte[] {65, 66, 67}));
}
