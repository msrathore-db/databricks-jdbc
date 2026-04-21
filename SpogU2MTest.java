import java.sql.*;

public class SpogU2MTest {
  public static void main(String[] args) throws Exception {
    Class.forName("com.databricks.client.jdbc.Driver");
    String host = args.length > 0 ? args[0] : "dogfood-spog.staging.azuredatabricks.net";
    String httpPath =
        args.length > 1
            ? args[1]
            : "/sql/1.0/warehouses/e256699345d1ac74?o=7064161269814046";
    int port = args.length > 2 ? Integer.parseInt(args[2]) : 8030;
    String url =
        "jdbc:databricks://" + host + "/default"
            + ";ssl=1"
            + ";AuthMech=11;Auth_Flow=2"
            + ";httpPath=" + httpPath
            + ";UseThriftClient=1"
            + ";EnableTokenCache=0"
            + ";OAuth2RedirectUrlPort=" + port
            + ";LogLevel=DEBUG"
            + ";LogPath=/tmp/jdbc-u2m-log-" + host;
    System.out.println("URL: " + url);
    System.out.println("Connecting... browser will open for login.");
    long t0 = System.currentTimeMillis();
    try (Connection c = DriverManager.getConnection(url);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT 1")) {
      if (r.next() && r.getInt(1) == 1) {
        System.out.printf(
            "RESULT=%d  (elapsed %d ms)%n", r.getInt(1), System.currentTimeMillis() - t0);
        System.out.println("U2M " + host + ": PASS");
      } else {
        System.out.println("U2M " + host + ": FAIL (wrong result)");
      }
    } catch (Exception e) {
      System.out.println("U2M " + host + ": FAIL");
      String m = e.getMessage();
      if (m != null && m.length() > 300) m = m.substring(0, 300);
      System.out.println("  -> " + m);
    }
  }
}
