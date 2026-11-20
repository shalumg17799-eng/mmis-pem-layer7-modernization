package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

/**
 * requestScreening (MAM-28): a fresh MANUAL screening on request. The provider must exist in
 * provider-service (404 otherwise, nothing created; a provider-service that gives no answer is
 * passed on as {@link ProviderFactsSource.Unavailable}); the answer is the RUNNING screening, which
 * then runs in the background with the provider's current data and the same four sources (D-005),
 * under the correlation id of the request; earlier screenings are kept (D-018).
 */
@Service
public class RequestScreening {

  private static final Logger LOG = LoggerFactory.getLogger(RequestScreening.class);
  static final String MANUAL = "MANUAL";

  private final ProviderFactsSource providers;
  private final ScreenProvider screen;
  private final Executor executor;
  private final Clock clock;

  public RequestScreening(
      ProviderFactsSource providers,
      ScreenProvider screen,
      @Qualifier("screeningExecutor") Executor executor,
      Clock clock) {
    this.providers = providers;
    this.screen = screen;
    this.executor = executor;
    this.clock = clock;
  }

  public Screening request(String npi) {
    try {
      providers.facts(npi);
    } catch (HttpClientErrorException.NotFound e) {
      throw ApiException.notFound("No provider found for NPI " + npi);
    }
    UUID id = UUID.randomUUID();
    Screening running =
        new Screening(id, npi, MANUAL, "RUNNING", null, List.of(), clock.instant(), null);
    String correlationId = CorrelationId.currentOrNew();
    executor.execute(() -> CorrelationId.with(correlationId, () -> screenInBackground(id, npi)));
    return running;
  }

  /** The background run; a failure is logged with the exception itself (D-024). */
  private Screening screenInBackground(UUID id, String npi) {
    try {
      return screen.screen(id, npi, MANUAL);
    } catch (RuntimeException e) {
      LOG.error("MANUAL screening {} of NPI {} FAILED", id, npi, e);
      return null;
    }
  }
}
