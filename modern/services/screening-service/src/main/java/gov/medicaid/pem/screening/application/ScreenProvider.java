package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.screening.domain.SourceResult;
import gov.medicaid.pem.screening.domain.Verdict;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Screens one provider (PRVSCR01 SCREEN-ONE-PROVIDER, D-005): read the provider from
 * provider-service, ask the four sources, decide the verdict (any result that is not P fails),
 * store the screening with its four results and announce ScreeningCompleted, all in one
 * transaction.
 */
@Service
public class ScreenProvider {

  private final ProviderFactsSource providers;
  private final ScreeningSources sources;
  private final ScreeningStore store;
  private final EventOutbox outbox;
  private final Clock clock;

  public ScreenProvider(
      ProviderFactsSource providers,
      ScreeningSources sources,
      ScreeningStore store,
      EventOutbox outbox,
      Clock clock) {
    this.providers = providers;
    this.sources = sources;
    this.store = store;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public Screening screen(String npi, String reason) {
    return screen(UUID.randomUUID(), npi, reason);
  }

  /** MAM-28: a requested screening keeps the id its 202 answer announced. */
  @Transactional
  public Screening screen(UUID screeningId, String npi, String reason) {
    Instant started = clock.instant();
    ProviderFacts facts = providers.facts(npi);
    List<SourceResult> results = sources.check(facts);
    Screening s =
        new Screening(
            screeningId,
            npi,
            reason,
            "COMPLETED",
            Verdict.of(results),
            results,
            started,
            clock.instant());
    store.save(s);
    outbox.enqueue(
        "ScreeningCompleted",
        npi,
        new ScreeningCompletedData(
            s.screeningId(),
            npi,
            reason,
            s.result(),
            facts.taxonomy(),
            results.stream()
                .map(
                    r ->
                        new ScreeningCompletedData.SourceSummary(
                            r.source().name(), r.result(), r.detail()))
                .toList()));
    return s;
  }
}
