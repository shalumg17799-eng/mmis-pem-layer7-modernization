package gov.medicaid.pem.screening.infrastructure;

import gov.medicaid.pem.screening.application.ProviderFactsSource;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * MAM-20 AC-5 (D-024, ADR-003): an event whose provider cannot be read from provider-service (5xx
 * or no answer) is not parked on the DLT after the usual retries; it is retried, with a growing
 * pause capped at {@code pem.screening.provider-retry-max-interval}, until provider-service
 * answers. The event's processed_event row is rolled back with the failed attempt, so the screening
 * runs exactly once when it succeeds. Every other failure keeps the kit's retries and DLT.
 */
@Component
public class ProviderReadRetry implements SmartInitializingSingleton {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderReadRetry.class);

  private final ObjectProvider<DefaultErrorHandler> handler;
  private final Duration maxInterval;

  public ProviderReadRetry(
      ObjectProvider<DefaultErrorHandler> handler,
      @Value("${pem.screening.provider-retry-max-interval:60s}") Duration maxInterval) {
    this.handler = handler;
    this.maxInterval = maxInterval;
  }

  @Override
  public void afterSingletonsInstantiated() {
    handler.ifAvailable(
        h ->
            h.setBackOffFunction(
                (rec, ex) -> {
                  if (!ProviderFactsSource.Unavailable.in(ex)) {
                    return null;
                  }
                  LOG.warn(
                      "provider-service unavailable; event at {}-{}@{} will be retried until it"
                          + " answers",
                      rec.topic(),
                      rec.partition(),
                      rec.offset());
                  return untilRestored();
                }));
  }

  private ExponentialBackOff untilRestored() {
    ExponentialBackOff b = new ExponentialBackOff(1000L, 2.0);
    b.setMaxInterval(maxInterval.toMillis());
    return b;
  }
}
