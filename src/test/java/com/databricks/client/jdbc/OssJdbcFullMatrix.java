import java.sql.*;

/**
 * OSS JDBC — Full SPOG Matrix Test Usage: cd databricks-jdbc mvn package -DskipTests
 * -Djacoco.skip=true -Ddependency-check.skip=true -q mvn dependency:copy-dependencies -q
 * JAR=target/databricks-jdbc-*.jar; DEP=target/dependency javac -cp "$JAR"
 * src/test/java/com/databricks/client/jdbc/OssJdbcFullMatrix.java java -cp
 * "src/test/java/com/databricks/client/jdbc:$JAR:$(echo $DEP/*.jar | tr ' ' ':')" OssJdbcFullMatrix
 */
public class OssJdbcFullMatrix {
  static final String STG_SPOG = "dogfood-spog.staging.azuredatabricks.net";
  static final String STG_LEGACY = "adb-7064161269814046.2.staging.azuredatabricks.net";
  static final String STG_WH = "/sql/1.0/warehouses/e256699345d1ac74";
  static final String STG_SPOG_PATH = STG_WH + "?o=7064161269814046";
  static final String STG_TENANT = "e3fe3f22-4b98-4c04-82cc-d8817d1b17da";

  static final String PROD_SPOG = "peco.azuredatabricks.net";
  static final String PROD_LEGACY = "adb-6436897454825492.12.azuredatabricks.net";
  static final String PROD_WH = "/sql/1.0/warehouses/00adc7b6c00429b8";
  static final String PROD_SPOG_PATH = PROD_WH + "?o=6436897454825492";
  static final String PROD_TENANT = "9f37a392-f0ae-4280-9796-f1864a10effc";

  static int pass = 0, fail = 0, skip = 0;

  public static void main(String[] args) {
    System.out.println("================================================================");
    System.out.println("  OSS JDBC — Full SPOG Matrix Test");
    System.out.println("================================================================");
    try {
      Class.forName("com.databricks.client.jdbc.Driver");
    } catch (Exception e) {
      System.err.println("FATAL: driver not found");
      System.exit(1);
    }

    // ============ STAGING ============
    section("STAGING");

    String stgPat = env("DATABRICKS_DOGFOOD_WESTUS_STAGING_TOKEN");
    if (stgPat != null) {
      String auth = "AuthMech=3;UID=token;PWD=" + stgPat;
      run("Stg | PAT | SPOG", STG_SPOG, STG_SPOG_PATH, auth);
      run("Stg | PAT | Legacy", STG_LEGACY, STG_WH, auth);
    } else skip("Stg | PAT");

    String stgM2mId = env("DATABRICKS_DOGFOOD_AZURE_CLIENT_ID");
    String stgM2mSec = env("DATABRICKS_DOGFOOD_AZURE_CLIENT_SECRET");
    if (stgM2mId != null && stgM2mSec != null) {
      String auth =
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId=" + stgM2mId + ";OAuth2Secret=" + stgM2mSec;
      run("Stg | DB M2M (dc8dd813) | SPOG", STG_SPOG, STG_SPOG_PATH, auth);
      run("Stg | DB M2M (dc8dd813) | Legacy", STG_LEGACY, STG_WH, auth);
    } else skip("Stg | DB M2M");

    // Azure AD d7f11108 with AzureTenantId (staging tenant)
    String stgEntraId = env("DATABRICKS_SPOG_ENTRA_TEST_CLIENT_ID");
    String stgEntraSec = env("DATABRICKS_SPOG_ENTRA_TEST_CLIENT_SECRET");
    if (stgEntraId != null && stgEntraSec != null) {
      String auth =
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId="
              + stgEntraId
              + ";OAuth2Secret="
              + stgEntraSec
              + ";AzureTenantId="
              + STG_TENANT;
      run("Stg | Azure AD (d7f11108 + AzureTenantId) | SPOG", STG_SPOG, STG_SPOG_PATH, auth);
      run("Stg | Azure AD (d7f11108 + AzureTenantId) | Legacy", STG_LEGACY, STG_WH, auth);

      // Without AzureTenantId — goes to Databricks OIDC, expected FAIL
      String authNoTenant =
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId=" + stgEntraId + ";OAuth2Secret=" + stgEntraSec;
      run(
          "Stg | Azure AD (d7f11108, NO AzureTenantId) | SPOG",
          STG_SPOG,
          STG_SPOG_PATH,
          authNoTenant);
      run("Stg | Azure AD (d7f11108, NO AzureTenantId) | Legacy", STG_LEGACY, STG_WH, authNoTenant);
    } else skip("Stg | Azure AD d7f11108");

    // ============ PROD ============
    section("PROD");

    String prodPat = env("DATABRICKS_PECOTESTING_TOKEN_PERSONAL");
    if (prodPat != null) {
      run("Prod | PAT | Legacy", PROD_LEGACY, PROD_WH, "AuthMech=3;UID=token;PWD=" + prodPat);
      run("Prod | PAT | SPOG", PROD_SPOG, PROD_SPOG_PATH, "AuthMech=3;UID=token;PWD=" + prodPat);
    } else skip("Prod | PAT");

    String prodM2mId = env("DATABRICKS_PECOTESTING_DATABRICKS_CLIENT_ID_MSR_SPN");
    String prodM2mSec = env("DATABRICKS_PECOTESTING_DATABRICKS_CLIENT_SECRET_MSR_SPN");
    if (prodM2mId != null && prodM2mSec != null) {
      run(
          "Prod | Entra SP (a6f72159, dose) | Legacy",
          PROD_LEGACY,
          PROD_WH,
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId=" + prodM2mId + ";OAuth2Secret=" + prodM2mSec);
    } else skip("Prod | Entra SP a6f72159");

    // Azure AD d154b9ed with AzureTenantId (prod tenant)
    String prodEntraId = env("DATABRICKS_AAD_CLIENT_ID");
    String prodEntraSec = env("DATABRICKS_AAD_CLIENT_SECRET");
    if (prodEntraId != null && prodEntraSec != null) {
      String auth =
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId="
              + prodEntraId
              + ";OAuth2Secret="
              + prodEntraSec
              + ";AzureTenantId="
              + PROD_TENANT;
      run("Prod | Azure AD (d154b9ed + AzureTenantId) | Legacy", PROD_LEGACY, PROD_WH, auth);
      run("Prod | Azure AD (d154b9ed + AzureTenantId) | SPOG", PROD_SPOG, PROD_SPOG_PATH, auth);

      // Without AzureTenantId — goes to Databricks OIDC, expected FAIL
      String authNoTenant =
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId=" + prodEntraId + ";OAuth2Secret=" + prodEntraSec;
      run(
          "Prod | Azure AD (d154b9ed, NO AzureTenantId) | Legacy",
          PROD_LEGACY,
          PROD_WH,
          authNoTenant);
    } else skip("Prod | Azure AD d154b9ed");

    // Same SP d154b9ed with dose secret on prod
    String prodEntraDbId = env("DATABRICKS_PECOTESTING_MSR_ENTRA_SPN_CLIENT_ID");
    String prodEntraDbSec = env("DATABRICKS_PECOTESTING_MSR_ENTRA_SPN_CLIENT_SECRET");
    if (prodEntraDbId != null && prodEntraDbSec != null) {
      run(
          "Prod | Same SP d154b9ed (dose) | Legacy",
          PROD_LEGACY,
          PROD_WH,
          "AuthMech=11;Auth_Flow=1;OAuth2ClientId="
              + prodEntraDbId
              + ";OAuth2Secret="
              + prodEntraDbSec);
    } else skip("Prod | d154b9ed dose");

    System.out.println("\n================================================================");
    System.out.printf("  SUMMARY: PASS=%d  FAIL=%d  SKIP=%d%n", pass, fail, skip);
    System.out.println("================================================================");
    System.exit(fail > 0 ? 1 : 0);
  }

  static String url(String host, String path, String auth) {
    return String.format(
        "jdbc:databricks://%s/default;ssl=1;%s;httpPath=%s;UseThriftClient=1", host, auth, path);
  }

  static void run(String name, String host, String path, String auth) {
    System.out.printf("  %-62s", name);
    try (Connection c = DriverManager.getConnection(url(host, path, auth));
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT 1")) {
      if (r.next() && r.getInt(1) == 1) {
        System.out.println("PASS");
        pass++;
      } else {
        System.out.println("FAIL (wrong result)");
        fail++;
      }
    } catch (Exception e) {
      String m = e.getMessage();
      if (m != null && m.length() > 180) m = m.substring(0, 180);
      System.out.println("FAIL");
      System.out.println("    -> " + m);
      fail++;
    }
  }

  static void skip(String name) {
    System.out.printf("  %-62sSKIP%n", name);
    skip++;
  }

  static void section(String t) {
    System.out.println("\n--- " + t + " ---");
  }

  static String env(String k) {
    String v = System.getenv(k);
    return (v != null && !v.isEmpty()) ? v : null;
  }
}
