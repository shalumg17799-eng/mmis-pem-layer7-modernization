package gov.medicaid.pem.sanctions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.application.SanctionIntakeRuns;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * Base of the tests added after the code review of 2026-11-19 (Q-097 C, additive). They share one
 * Spring context that differs from IntegrationTest's in two ways: the call to provider-service has
 * a read timeout of one second ({@code pem.services.provider-timeout}), and the run table is a spy
 * that stays real unless a test makes one operation fail. Failures of the real database are made
 * with triggers that reject one kind of write (the way DirectoryExtractJobTest does it), lifted
 * after every test.
 *
 * <p>Nothing here can wait for ever: a run is awaited for {@link #RUN_BOUND} at most, and the
 * classes that make provider-service answer slowly carry their own time limit (JUnit's {@code
 * Timeout}, and a preemptive limit around the online call), so a missing timeout in the service
 * fails the test instead of hanging the build.
 */
@TestPropertySource(properties = "pem.services.provider-timeout=1s")
public abstract class ReviewFixesTest extends IntegrationTest {

  protected static final String REASON = "SYNTHETIC TEST REASON";
  protected static final String BUSINESS_DATE = "2026-03-31";

  /** The longest a test waits for a run to end. A run of three records takes seconds. */
  protected static final Duration RUN_BOUND = Duration.ofSeconds(20);

  /** Record 1 of the three-record file: T, ACTIVE provider. */
  protected static final String NPI_1 = "1003000111";

  /** Record 2: S, ACTIVE provider; the record every failure of these tests is injected at. */
  protected static final String NPI_2 = "1003000333";

  /** Record 3: R, suspended provider. */
  protected static final String NPI_3 = "1003000222";

  /** An NPI no provider has: the first call of the context goes there to warm the client up. */
  private static final String WARM_UP_NPI = "1003000999";

  private static boolean providerClientIsWarm;

  @MockitoSpyBean protected SanctionIntakeRuns runs;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected MockMvc mvc;
  @Autowired protected ObjectMapper json;

  private final List<String[]> rejections = new ArrayList<>();

  @BeforeEach
  protected void cleanHistoryAndProviders() throws Exception {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
    TestProviderService.holds(NPI_1, "A");
    TestProviderService.holds(NPI_2, "A");
    TestProviderService.holds(NPI_3, "S");
    warmUpTheProviderClient();
  }

  /**
   * With a read timeout of one second the very first call of a fresh context (the token, the
   * client, the JSON mapping) must not be the one that is slow. One sanction for an unknown NPI
   * (404, nothing is kept or announced) makes that call before any test does.
   */
  private void warmUpTheProviderClient() throws Exception {
    if (providerClientIsWarm) {
      return;
    }
    TestProviderService.unknown(WARM_UP_NPI);
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post("/api/v1/sanctions")
                    .with(PemJwt.supervisor())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"npi\":\""
                            + WARM_UP_NPI
                            + "\",\"action\":\"T\",\"effectiveDate\":\""
                            + BUSINESS_DATE
                            + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus())
        .as("warm-up call, answer: " + r.getResponse().getContentAsString())
        .isEqualTo(404);
    providerClientIsWarm = true;
  }

  /** The records of the usual file: T for record 1, S for record 2, R for record 3. */
  protected static byte[] threeRecords() {
    return SancinFile.lines(
        SancinFile.record(NPI_1, "T", REASON, "20260331"),
        SancinFile.record(NPI_2, "S", REASON, "20260331"),
        SancinFile.record(NPI_3, "R", REASON, "20260331"));
  }

  /**
   * Starts a run as ADMIN and waits for its end for {@code bound} at most. A run that is still
   * RUNNING then fails the test with a message; it is never waited for any longer.
   */
  protected JsonNode runToEndWithin(byte[] file, Duration bound) throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, file, BUSINESS_DATE);
    try {
      Awaitility.await("sanction intake run " + runId + " has ended")
          .atMost(bound)
          .pollInterval(Duration.ofMillis(100))
          .until(() -> !"RUNNING".equals(IntakeApi.run(mvc, json, runId).path("status").asText()));
    } catch (ConditionTimeoutException e) {
      fail(
          "run %s is still RUNNING after %s: the call to provider-service is not cut off",
          runId, bound);
    }
    return IntakeApi.run(mvc, json, runId);
  }

  /**
   * Makes the database reject every write of {@code events} (for example "INSERT" or "INSERT OR
   * UPDATE") on {@code table} for which the SQL condition {@code when} holds. The message of the
   * rejection says nothing about SQL.
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

  /**
   * provider-service answers the status change of this NPI only after {@code millis} milliseconds
   * (a hang): with a timeout of one second, an answer that takes three is no answer.
   */
  protected static void providerAnswersSlowly(String npi, int millis) {
    TestProviderService.MOCK.stubFor(
        post(urlPathEqualTo("/api/v1/providers/" + npi + "/status-changes"))
            .atPriority(1)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody("{}")
                    .withFixedDelay(millis)));
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

  /** Lifts every rejection once the background jobs of the test have ended. */
  @AfterEach
  protected void liftEveryRejection() {
    backgroundJobsHaveEnded();
    for (String name : rejections.stream().map(r -> r[0]).toList()) {
      lift(name);
    }
  }

  /** History rows kept for the NPI. */
  protected int historyRows(String npi) {
    return jdbc.queryForObject("SELECT count(*) FROM sanction WHERE npi = ?", Integer.class, npi);
  }

  /** SanctionApplied events written for the NPI (the outbox subject). */
  protected int sanctionAppliedEvents(String npi) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM outbox_event WHERE type = 'SanctionApplied' AND subject = ?",
        Integer.class,
        npi);
  }
}
