package gov.medicaid.pem.tmsis;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base for integration tests: the real application with Postgres and Kafka from Testcontainers, the
 * fake provider-service of {@link TestProviderService} and the test Clock (2026-01-15, 10:00 UTC).
 * Before each test the fake and the clock are reset and the extract tables are emptied (so an
 * archive starts empty); after each test every run has ended (a run is asynchronous, ADR-004), the
 * triggers a test made are lifted, and the clock is reset.
 *
 * <p>Nothing here can wait for ever: a run is awaited for {@link TmsisApi#RUN_BOUND} at most.
 */
@SpringBootTest(properties = {"spring.config.name=tmsis-service", "PEM_LOG_FORMAT="})
@AutoConfigureMockMvc
@ImportTestcontainers(PemContainers.class)
@Import(TestClock.Config.class)
public abstract class IntegrationTest {

  /** The one provider the very first run of the JVM reads, to warm the HTTP client up. */
  private static final String WARM_UP_NPI = "1003000001";

  private static boolean warm;

  @Autowired protected MockMvc mvc;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected ObjectMapper json;

  protected TmsisApi api;

  private final List<String[]> rejections = new ArrayList<>();

  @DynamicPropertySource
  static void sources(DynamicPropertyRegistry r) {
    TestProviderService.register(r);
  }

  @BeforeEach
  void prepareTmsisTest() {
    api = new TmsisApi(mvc, json);
    TestProviderService.reset();
    TestClock.reset();
    warmUp();
    cleanExtractData();
  }

  /**
   * The very first call of a fresh context (the token, the client, the JSON mapping) is slower than
   * the next ones: one run of one provider is made before any test, and its result is not used.
   */
  private void warmUp() {
    if (warm) {
      return;
    }
    warm = true;
    try {
      TestProviderService.holds(Prov.active(WARM_UP_NPI));
      api.runToEnd("2026-01-15");
    } catch (Throwable e) {
      // the tests that follow show what is wrong; the warm-up proves nothing
    } finally {
      TestProviderService.reset();
      backgroundRunsHaveEnded();
    }
  }

  /** Forgets every run, row, archive generation and event of the extract tables. */
  protected void cleanExtractData() {
    jdbc.update("DELETE FROM outbox_event");
    jdbc.update("DELETE FROM extract_archive");
    jdbc.update("DELETE FROM extract_row");
    jdbc.update("DELETE FROM extract_run");
  }

  @AfterEach
  void finishTmsisTest() {
    backgroundRunsHaveEnded();
    for (String name : rejections.stream().map(r -> r[0]).toList()) {
      lift(name);
    }
    TestProviderService.reset();
    TestClock.reset();
  }

  /**
   * Waits for the runs of the test to leave RUNNING, so that none of them writes while the next
   * test empties the tables. A run that is still RUNNING after the limit (the test that started it
   * has failed on that) is marked FAILED, so it cannot disturb the next test.
   */
  protected void backgroundRunsHaveEnded() {
    try {
      Awaitility.await()
          .atMost(Duration.ofSeconds(30))
          .pollInterval(Duration.ofMillis(100))
          .ignoreExceptions()
          .until(
              () ->
                  jdbc.queryForObject(
                          "SELECT count(*) FROM extract_run WHERE status = 'RUNNING'",
                          Integer.class)
                      == 0);
    } catch (RuntimeException e) {
      jdbc.update("UPDATE extract_run SET status = 'FAILED' WHERE status = 'RUNNING'");
    }
  }

  /**
   * Makes the database reject every write of {@code events} (for example "INSERT" or "INSERT OR
   * UPDATE") on {@code table} for which the SQL condition {@code when} holds. The message of the
   * rejection says nothing about SQL; the trigger is lifted after the test.
   */
  protected void reject(String name, String events, String table, String when) {
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
    rejections.add(new String[] {name, table});
  }

  /** Lets the writes through again. */
  protected void lift(String name) {
    for (String[] r : rejections) {
      if (r[0].equals(name)) {
        jdbc.execute("DROP TRIGGER IF EXISTS " + name + " ON " + r[1]);
        jdbc.execute("DROP FUNCTION IF EXISTS " + name + "()");
      }
    }
    rejections.removeIf(r -> r[0].equals(name));
  }

  /** The archive generations kept, ascending. */
  protected List<Integer> generations() {
    return jdbc.queryForList(
        "SELECT generation FROM extract_archive ORDER BY generation", Integer.class);
  }

  /** The ExtractProduced events in the outbox (all of them, whoever they are for). */
  protected List<String> extractProducedEnvelopes() {
    return jdbc.queryForList(
        "SELECT payload::text FROM outbox_event WHERE type = 'ExtractProduced' ORDER BY created_at",
        String.class);
  }
}
