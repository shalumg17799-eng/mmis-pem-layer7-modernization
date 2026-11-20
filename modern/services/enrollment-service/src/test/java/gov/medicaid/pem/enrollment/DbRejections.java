package gov.medicaid.pem.enrollment;

import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Failures of the real database, made on purpose: a trigger that rejects one kind of write on one
 * table for the rows a SQL condition selects. The message of the rejection says nothing about SQL,
 * so a reason that mentions SQL can only have come from the service's own wrapping of the failure.
 */
public final class DbRejections {

  private final JdbcTemplate jdbc;
  private final List<String[]> active = new ArrayList<>();

  public DbRejections(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * Makes the database reject every write of {@code events} (for example "INSERT" or "INSERT OR
   * UPDATE") on {@code table} for which the SQL condition {@code when} holds.
   */
  public void reject(String name, String events, String table, String when) {
    jdbc.execute(
        "CREATE FUNCTION "
            + name
            + "() RETURNS trigger AS $$ BEGIN"
            + " RAISE EXCEPTION 'the test database rejected this write'; END; $$"
            + " LANGUAGE plpgsql");
    jdbc.execute(
        "CREATE TRIGGER "
            + name
            + " BEFORE "
            + events
            + " ON "
            + table
            + " FOR EACH ROW WHEN ("
            + when
            + ") EXECUTE FUNCTION "
            + name
            + "()");
    active.add(new String[] {name, table});
  }

  /** Lets the writes through again. */
  public void lift(String name) {
    for (String[] r : active) {
      if (r[0].equals(name)) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + name + " ON " + r[1]);
        jdbc.execute("DROP FUNCTION IF EXISTS " + name + "()");
      }
    }
    active.removeIf(r -> r[0].equals(name));
  }

  /** Lets every rejected write through again. */
  public void liftAll() {
    for (String name : active.stream().map(r -> r[0]).toList()) {
      lift(name);
    }
  }
}
