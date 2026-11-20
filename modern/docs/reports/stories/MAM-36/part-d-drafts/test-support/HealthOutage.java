package gov.medicaid.pem.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.DockerClientFactory;

/**
 * MAM-36 AC-5 per service (Q-099 answer B): the test's own Testcontainers Postgres is taken away
 * mid-test (paused, so it can come back on the same port); the service's /actuator/health must then
 * answer 503 DOWN without a token and 200 UP again once the database is back. Only the
 * Testcontainers database of the test JVM is touched, never a container of the PEM stack.
 *
 * <p>After the pause the helper waits 1.5 s, so the connection pool validates its idle connections
 * instead of handing one out unchecked (Hikari skips the check for 500 ms after a use, and a query
 * on a paused database has no timeout). The outage check is bounded: if health does not answer
 * within 90 s the database is unpaused and the test fails, visibly, instead of hanging.
 */
public final class HealthOutage {

  static final Duration POOL_VALIDATION_WAIT = Duration.ofMillis(1500);
  static final Duration OUTAGE_ANSWER_LIMIT = Duration.ofSeconds(90);

  private HealthOutage() {}

  public static void assertVisibleAndRecovers(MockMvc mvc) throws Exception {
    assertHealth(mvc, 200, "UP");
    var docker = DockerClientFactory.instance().client();
    String postgres = PemContainers.POSTGRES.getContainerId();
    ExecutorService probe = Executors.newSingleThreadExecutor();
    docker.pauseContainerCmd(postgres).exec();
    try {
      Thread.sleep(POOL_VALIDATION_WAIT.toMillis());
      Future<?> outage =
          probe.submit(
              () -> {
                assertHealth(mvc, 503, "DOWN");
                return null;
              });
      try {
        outage.get(OUTAGE_ANSWER_LIMIT.toSeconds(), TimeUnit.SECONDS);
      } catch (TimeoutException e) {
        throw new AssertionError(
            "/actuator/health gave no answer within "
                + OUTAGE_ANSWER_LIMIT.toSeconds()
                + " s while the database was down",
            e);
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
    long end = System.nanoTime() + Duration.ofSeconds(60).toNanos();
    while (System.nanoTime() < end) {
      try {
        assertHealth(mvc, 200, "UP");
        return;
      } catch (AssertionError notYet) {
        Thread.sleep(500);
      }
    }
    assertHealth(mvc, 200, "UP");
  }

  private static void assertHealth(MockMvc mvc, int code, String state) throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().is(code))
        .andExpect(jsonPath("$.status").value(state));
  }
}
