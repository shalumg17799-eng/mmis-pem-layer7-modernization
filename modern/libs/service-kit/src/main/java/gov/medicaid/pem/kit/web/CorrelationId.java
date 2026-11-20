package gov.medicaid.pem.kit.web;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * The X-Correlation-Id carried through REST calls, events and every log line (java-microservices
 * 11).
 */
public final class CorrelationId {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";
  private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  private CorrelationId() {}

  public static String currentOrNew() {
    String id = MDC.get(MDC_KEY);
    return id != null ? id : UUID.randomUUID().toString();
  }

  /** The given value if it is safe to log and forward, otherwise a new id. */
  public static String sanitize(String candidate) {
    return candidate != null && SAFE.matcher(candidate).matches()
        ? candidate
        : UUID.randomUUID().toString();
  }

  /** Runs work with the correlation id of an incoming event in the MDC. */
  public static <T> T with(String id, Supplier<T> work) {
    String previous = MDC.get(MDC_KEY);
    MDC.put(MDC_KEY, sanitize(id));
    try {
      return work.get();
    } finally {
      if (previous == null) {
        MDC.remove(MDC_KEY);
      } else {
        MDC.put(MDC_KEY, previous);
      }
    }
  }
}
