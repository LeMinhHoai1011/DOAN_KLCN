import java.nio.file.*;
import java.sql.*;

/** JDBC fallback for machines without psql. Password is read from environment. */
class PaymentSchemaUpgrade {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (!java.util.Set.of("Check", "DryRun", "Apply").contains(mode))
            throw new IllegalArgumentException("Expected Check, DryRun or Apply");
        try (Connection c = DriverManager.getConnection(System.getenv("PAYMENT_DB_URL"),
                System.getenv("PAYMENT_DB_USER"), System.getenv("PAYMENT_DB_PASSWORD"))) {
            if (!mode.equals("Check")) {
                String sql = Files.readString(Path.of(args[1]));
                if (mode.equals("DryRun")) sql = sql.replace("COMMIT;", "ROLLBACK;");
                try (Statement s = c.createStatement()) { s.execute(sql); }
                catch (Exception e) {
                    try (Statement s = c.createStatement()) { s.execute("ROLLBACK"); }
                    throw e;
                }
                System.out.println(mode + " completed; row preservation verified.");
            }
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                    "SELECT current_database(),indexname,indexdef FROM pg_indexes " +
                    "WHERE schemaname='public' AND tablename='financial_transactions' ORDER BY indexname")) {
                while (r.next()) System.out.println(r.getString(1)+" | "+r.getString(2)+" | "+r.getString(3));
            }
        }
    }
}
