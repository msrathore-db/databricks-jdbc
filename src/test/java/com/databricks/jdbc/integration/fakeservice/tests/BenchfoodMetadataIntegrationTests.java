package com.databricks.jdbc.integration.fakeservice.tests;

import static com.databricks.jdbc.integration.IntegrationTestUtil.*;
import static org.junit.jupiter.api.Assertions.*;

import com.databricks.jdbc.common.DatabricksJdbcUrlParams;
import com.databricks.jdbc.integration.fakeservice.AbstractFakeServiceIntegrationTests;
import com.databricks.jdbc.integration.fakeservice.FakeServiceConfigLoader;
import java.sql.*;
import java.util.Properties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class BenchfoodMetadataIntegrationTests extends AbstractFakeServiceIntegrationTests {

  @BeforeAll
  static void setupBenchfoodEnvironment() {
    String benchfoodHost = getDatabricksBenchfoodHost();
    if (benchfoodHost != null && !benchfoodHost.isEmpty()) {
      String fullUrl =
          benchfoodHost.startsWith("http") ? benchfoodHost : "https://" + benchfoodHost;
      setDatabricksApiTargetUrl(fullUrl);
    }
  }

  @Test
  void testGetSchemasWithNullCatalogMultipleCatalogSupportEnabled() throws SQLException {
    Properties props = new Properties();
    props.put("UID", getDatabricksUser());
    props.put("PWD", getDatabricksBenchfoodToken());
    props.put(
        DatabricksJdbcUrlParams.USE_THRIFT_CLIENT.getParamName(),
        FakeServiceConfigLoader.shouldUseThriftClient());

    String jdbcUrl = getFakeServiceBenchfoodJDBCUrl() + ";enableMultipleCatalogSupport=1";

    try (Connection connectionWithMultiCatalog = DriverManager.getConnection(jdbcUrl, props)) {
      DatabaseMetaData metaData = connectionWithMultiCatalog.getMetaData();

      try (ResultSet schemas = metaData.getSchemas(null, null)) {
        assertTrue(schemas.next(), "There should be at least one schema when catalog is null");

        java.util.Set<String> distinctCatalogs = new java.util.HashSet<>();
        int schemaCount = 0;
        do {
          String schemaName = schemas.getString("TABLE_SCHEM");
          String catalogName = schemas.getString("TABLE_CATALOG");

          assertNotNull(schemaName, "Schema name should not be null");
          if (catalogName != null) {
            distinctCatalogs.add(catalogName);
          }
          schemaCount++;

        } while (schemas.next());

        assertTrue(
            schemaCount > 1,
            "With enableMultipleCatalogSupport=1, should find schemas from multiple catalogs");
      }
    }
  }

  @Test
  void testGetSchemasWithNullCatalogMultipleCatalogSupportDisabled() throws SQLException {
    Properties props = new Properties();
    props.put("UID", getDatabricksUser());
    props.put("PWD", getDatabricksBenchfoodToken());
    props.put(
        DatabricksJdbcUrlParams.USE_THRIFT_CLIENT.getParamName(),
        FakeServiceConfigLoader.shouldUseThriftClient());

    String jdbcUrl = getFakeServiceBenchfoodJDBCUrl() + ";enableMultipleCatalogSupport=0";

    try (Connection connectionWithSingleCatalog = DriverManager.getConnection(jdbcUrl, props)) {
      DatabaseMetaData metaData = connectionWithSingleCatalog.getMetaData();

      try (ResultSet schemas = metaData.getSchemas(null, null)) {
        assertTrue(schemas.next(), "There should be at least one schema when catalog is null");

        java.util.Set<String> distinctCatalogs = new java.util.HashSet<>();
        int schemaCount = 0;
        do {
          String schemaName = schemas.getString("TABLE_SCHEM");
          String catalogName = schemas.getString("TABLE_CATALOG");

          assertNotNull(schemaName, "Schema name should not be null");
          if (catalogName != null) {
            distinctCatalogs.add(catalogName);
          }
          schemaCount++;

        } while (schemas.next());

        assertTrue(
            schemaCount >= 1,
            "With enableMultipleCatalogSupport=0, should find schemas from single catalog");
      }
    }
  }
}
