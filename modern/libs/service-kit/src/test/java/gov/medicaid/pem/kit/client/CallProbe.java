package gov.medicaid.pem.kit.client;

import static org.assertj.core.api.Assertions.catchThrowable;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.web.client.RestClient;

/**
 * Makes a call that is expected to fail and measures how long the caller waited, so a test can say
 * "it failed, and it failed within the limit" instead of hanging when a limit is missing.
 */
public final class CallProbe {

  private CallProbe() {}

  /** What a call threw (null when it did not throw) and how long it took to throw it. */
  public record Failure(Throwable error, Duration elapsed) {

    /** True when the exception or one of its causes is a read or connect timeout. */
    public boolean causedByTimeout() {
      for (Throwable t = error; t != null; t = t.getCause()) {
        if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
          return true;
        }
      }
      return false;
    }
  }

  public static Failure failureOf(ThrowingCallable call) {
    long start = System.nanoTime();
    Throwable error = catchThrowable(call);
    return new Failure(error, Duration.ofNanos(System.nanoTime() - start));
  }

  /** A GET of a provider through the client: the call of a service to provider-service. */
  public static Failure failureOf(RestClient client) {
    return failureOf(
        () -> client.get().uri("/api/v1/providers/1003000111").retrieve().body(String.class));
  }
}
