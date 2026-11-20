package gov.medicaid.pem.screening;

import static org.assertj.core.api.Assertions.fail;

import java.time.Duration;
import java.util.concurrent.Callable;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;

/**
 * Waiting with a bound: a test that waits for something in the background never waits for ever.
 * When the bound is reached the test fails with a message that says what did not happen.
 */
public final class Bounded {

  /** The longest a test waits for something that runs in the background. */
  public static final Duration BOUND = Duration.ofSeconds(60);

  private Bounded() {}

  public static void until(String what, Callable<Boolean> condition) {
    until(what, condition, BOUND);
  }

  public static void until(String what, Callable<Boolean> condition, Duration bound) {
    try {
      Awaitility.await(what).atMost(bound).pollInterval(Duration.ofMillis(100)).until(condition);
    } catch (ConditionTimeoutException e) {
      fail("not reached within %s: %s", bound, what);
    }
  }
}
