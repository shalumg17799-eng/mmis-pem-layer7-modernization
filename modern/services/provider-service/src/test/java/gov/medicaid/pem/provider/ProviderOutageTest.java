package gov.medicaid.pem.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemContainers;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.DockerClientFactory;

/**
 * The real outage of provider-service's database, injected inside the test by pausing the test's
 * own Testcontainers Postgres (never a container of the PEM stack), as Q-099 answer B asks:
 *
 * <ul>
 *   <li>MAM-37 AC-8: a read while the database is down is a 503 problem that carries the request's
 *       X-Correlation-Id, within a time limit, never a 404 and never a hang;
 *   <li>MAM-36 AC-5: /actuator/health answers 503 DOWN while the database is down (so the compose
 *       health check turns unhealthy) and 200 UP again once it is back.
 * </ul>
 *
 * Every wait is bounded: after the pause the test waits 1.5 s so that the connection pool validates
 * its idle connections instead of handing one out unchecked, the answer must come within 90 s, and
 * the database is always un-paused in a finally block. A defect makes the test fail, never hang.
 */
class ProviderOutageTest extends IntegrationTest {

  static final Duration POOL_VALIDATION_WAIT = Duration.ofMillis(1500);
  static final Duration ANSWER_LIMIT = Duration.ofSeconds(90);
  static final Duration RECOVERY_LIMIT = Duration.ofSeconds(60);
  static final String CORRELATION = "7d2f9c4e-1b3a-4c5d-8e6f-000000000123";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual("1003000111").insert(jdbc);
  }

  /**
   * Runs the work while the test database is paused; the work gets 90 s, the database comes back.
   */
  private <T> T whileTheDatabaseIsDown(Callable<T> work) throws Exception {
    var docker = DockerClientFactory.instance().client();
    String postgres = PemContainers.POSTGRES.getContainerId();
    ExecutorService probe = Executors.newSingleThreadExecutor();
    docker.pauseContainerCmd(postgres).exec();
    try {
      Thread.sleep(POOL_VALIDATION_WAIT.toMillis());
      Future<T> answer = probe.submit(work);
      try {
        return answer.get(ANSWER_LIMIT.toSeconds(), TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new AssertionError(
            "no answer within " + ANSWER_LIMIT.toSeconds() + " s while the database was down", e);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof AssertionError a) {
          throw a;
        }
        throw e;
      }
    } finally {
      docker.unpauseContainerCmd(postgres).exec();
      probe.shutdownNow();
    }
  }

  /** Health is UP again within 60 s of the database coming back. */
  private void awaitHealthUp() throws Exception {
    long end = System.nanoTime() + RECOVERY_LIMIT.toNanos();
    int status = 0;
    while (System.nanoTime() < end && status != 200) {
      status = mvc.perform(get("/actuator/health")).andReturn().getResponse().getStatus();
      if (status != 200) {
        Thread.sleep(500);
      }
    }
    assertThat(status).as("health after the database is back").isEqualTo(200);
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-8] with the database stopped a read is a 503 problem, not a 404, with the request's X-Correlation-Id, within the limit; it works again when the database is back")
  void readFailsVisiblyAndRecovers() throws Exception {
    MvcResult down =
        whileTheDatabaseIsDown(
            () ->
                mvc.perform(
                        get("/api/v1/providers/1003000111")
                            .header("X-Correlation-Id", CORRELATION)
                            .with(PemJwt.analyst()))
                    .andReturn());

    assertThat(down.getResponse().getStatus()).isEqualTo(503);
    assertThat(down.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(down.getResponse().getHeader("X-Correlation-Id")).isEqualTo(CORRELATION);
    assertThat(json.readTree(down.getResponse().getContentAsString()).path("detail").asText())
        .isNotBlank()
        .hasSizeLessThanOrEqualTo(200);

    awaitHealthUp();
    MvcResult up =
        mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.analyst())).andReturn();
    assertThat(up.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(up.getResponse().getContentAsString()).path("npi").asText())
        .isEqualTo("1003000111");
  }

  @Test
  @DisplayName(
      "[MAM-36 AC-5] provider-service: while its test database is stopped /actuator/health answers 503 DOWN, and 200 UP again once the database is back")
  void healthFollowsTheDatabase() throws Exception {
    MvcResult before = mvc.perform(get("/actuator/health")).andReturn();
    assertThat(before.getResponse().getStatus())
        .as(before.getResponse().getContentAsString())
        .isEqualTo(200);

    MvcResult down = whileTheDatabaseIsDown(() -> mvc.perform(get("/actuator/health")).andReturn());

    assertThat(down.getResponse().getStatus())
        .as(down.getResponse().getContentAsString())
        .isEqualTo(503);
    JsonNode body = json.readTree(down.getResponse().getContentAsString());
    assertThat(body.path("status").asText()).isEqualTo("DOWN");

    awaitHealthUp();
    JsonNode after =
        json.readTree(
            mvc.perform(get("/actuator/health")).andReturn().getResponse().getContentAsString());
    assertThat(after.path("status").asText()).isEqualTo("UP");
  }
}
