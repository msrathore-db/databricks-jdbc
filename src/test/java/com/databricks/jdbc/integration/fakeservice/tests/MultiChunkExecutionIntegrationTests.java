package com.databricks.jdbc.integration.fakeservice.tests;

import static com.databricks.jdbc.dbclient.impl.sqlexec.PathConstants.RESULT_CHUNK_PATH;
import static com.databricks.jdbc.integration.IntegrationTestUtil.getValidJDBCConnection;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.*;

import com.databricks.jdbc.api.impl.DatabricksResultSet;
import com.databricks.jdbc.api.impl.DatabricksResultSetMetaData;
import com.databricks.jdbc.common.DatabricksJdbcConstants;
import com.databricks.jdbc.integration.fakeservice.AbstractFakeServiceIntegrationTests;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Test SQL execution with results spanning multiple chunks. */
public class MultiChunkExecutionIntegrationTests extends AbstractFakeServiceIntegrationTests {

  /**
   * Extracts unique chunk indices from chunk link fetch requests.
   *
   * @param requests List of logged requests matching the chunk path pattern
   * @return Set of unique chunk indices that were requested
   */
  private Set<Long> extractUniqueChunkIndices(List<LoggedRequest> requests) {
    Set<Long> chunkIndices = new HashSet<>();
    // Pattern to extract chunk index from URL: /result/chunks/{chunkIndex}
    Pattern pattern = Pattern.compile("/result/chunks/(\\d+)");

    for (LoggedRequest request : requests) {
      Matcher matcher = pattern.matcher(request.getUrl());
      if (matcher.find()) {
        chunkIndices.add(Long.parseLong(matcher.group(1)));
      }
    }
    return chunkIndices;
  }

  @Test
  void testMultiChunkSelect() throws SQLException, InterruptedException {
    // Ensure fake service property is set to skip link expiry checks in tests
    System.setProperty(DatabricksJdbcConstants.IS_FAKE_SERVICE_TEST_PROP, "true");

    final String table = "samples.tpch.lineitem";

    // To save on the size of stub mappings, the test uses just enough rows to span multiple chunks.
    // That minimum threshold is different for SQL Exec and SQL Gateway clients.
    final int maxRows = isSqlExecSdkClient() ? 122900 : 147500;
    final String sql = "SELECT * FROM " + table + " limit " + maxRows;

    Properties properties = new Properties();
    properties.setProperty("RowsFetchedPerBlock", String.valueOf(maxRows));
    properties.setProperty("EnableSQLExecHybridResults", "0");
    Connection connection = getValidJDBCConnection(properties);

    final Statement statement = connection.createStatement();
    statement.setMaxRows(maxRows);

    final AtomicReference<Throwable> threadException = new AtomicReference<>();

    // Iterate through the result set in a different thread to surface any 1st-level thread-safety
    // issues
    Thread thread =
        new Thread(
            () -> {
              try (ResultSet rs = statement.executeQuery(sql)) {
                DatabricksResultSetMetaData metaData =
                    (DatabricksResultSetMetaData) rs.getMetaData();

                int rowCount = 0;
                while (rs.next()) {
                  rowCount++;
                }

                // The result should have the same number of rows as the limit
                assertEquals(maxRows, rowCount);
                assertEquals(maxRows, metaData.getTotalRows());

                // The result should be split into multiple chunks
                assertTrue(metaData.getChunkCount() > 1, "Chunk count should be greater than 1");

                // The number of cloud fetch calls should be equal to the number of chunks
                final int cloudFetchCalls =
                    getCloudFetchApiExtension()
                        .countRequestsMatching(getRequestedFor(urlPathMatching(".*")).build())
                        .getCount();
                // cloud fetch calls can be retried
                assertTrue(cloudFetchCalls >= metaData.getChunkCount());

                if (isSqlExecSdkClient()) {
                  // Verify chunk link fetching behavior: first chunk is inline, remaining chunks
                  // need explicit link fetch calls
                  final String statementId = ((DatabricksResultSet) rs).getStatementId();
                  final String resultChunkPathRegex =
                      String.format(RESULT_CHUNK_PATH, statementId, ".*");

                  // Get all requests matching the chunk path pattern
                  List<LoggedRequest> chunkLinkRequests =
                      getDatabricksApiExtension()
                          .findRequestsMatching(
                              getRequestedFor(urlPathMatching(resultChunkPathRegex)).build())
                          .getRequests();

                  // Count unique chunk indices (actual chunk fetches)
                  Set<Long> uniqueChunkIndices = extractUniqueChunkIndices(chunkLinkRequests);
                  int totalRequests = chunkLinkRequests.size();
                  int retryRequests = totalRequests - uniqueChunkIndices.size();

                  // Expected: chunkCount - 1 (first chunk is inline)
                  long expectedChunkFetches = metaData.getChunkCount() - 1;

                  // Verify that we fetched the correct number of unique chunks
                  assertEquals(
                      expectedChunkFetches,
                      uniqueChunkIndices.size(),
                      String.format(
                          "Expected %d unique chunk fetches (chunkCount - 1), but got %d. "
                              + "Total requests: %d, Retries: %d",
                          expectedChunkFetches,
                          uniqueChunkIndices.size(),
                          totalRequests,
                          retryRequests));

                  // Log retry information for debugging
                  if (retryRequests > 0) {
                    System.out.println(
                        String.format(
                            "Note: %d retry request(s) occurred for chunk link fetching",
                            retryRequests));
                  }
                }
              } catch (Throwable e) {
                threadException.set(e);
              }
            });

    thread.start();
    thread.join(10_000);

    // Check if the thread had an exception
    if (threadException.get() != null) {
      if (threadException.get() instanceof AssertionError) {
        throw (AssertionError) threadException.get();
      } else {
        fail("Test thread failed with exception: " + threadException.get());
      }
    }

    connection.close();
  }
}
