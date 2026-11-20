package gov.medicaid.pem.screening.application;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The batch screening (MAM-32; PRVDAY01 STEP030 PRVSCR01) as the steps of its Spring Batch job
 * (ADR-004): every provider is read from provider-service; a provider with screen flag U and no
 * INITIAL screening yet (AC-7) is screened now with reason INITIAL (each screening commits on its
 * own, as ScreenProvider stores and announces it); at the end the SCRNOUT file is written, four rows
 * per screened provider, and the run is COMPLETED with its counts. A failure ends the run FAILED
 * with the reason (AC-6, D-024).
 */
@Service
public class RunScreeningBatch {

  private static final Logger LOG = LoggerFactory.getLogger(RunScreeningBatch.class);
  static final String UNSCREENED = "U";
  static final String INITIAL = "INITIAL";

  private final ScreeningRuns runs;
  private final ScreenProvider screen;
  private final ScrnoutFile file;
  private final Clock clock;

  public RunScreeningBatch(
      ScreeningRuns runs, ScreenProvider screen, ScrnoutFile file, Clock clock) {
    this.runs = runs;
    this.screen = screen;
    this.file = file;
    this.clock = clock;
  }

  public ScreeningRuns.Run open(LocalDate businessDate) {
    UUID runId = UUID.randomUUID();
    runs.create(runId, businessDate, clock.instant());
    return runs.find(runId).orElseThrow();
  }

  /** One provider read by the job: screened when unscreened and not screened before. */
  public boolean screenIfDue(UUID runId, ProviderListing.Row p) {
    if (!UNSCREENED.equals(p.screenFlag()) || runs.hasInitialScreening(p.npi())) {
      return false;
    }
    Screening s = screen.screen(p.npi(), INITIAL);
    runs.link(s.screeningId(), runId);
    return true;
  }

  /** The last step: the SCRNOUT file and the counts. */
  public void finish(UUID runId, LocalDate businessDate, int read) {
    List<ScreeningRuns.Screened> screened = runs.screened(runId);
    int failed =
        (int)
            screened.stream()
                .filter(s -> s.results().stream().anyMatch(r -> !"P".equals(r.result())))
                .count();
    runs.complete(runId, read, failed, file.write(screened, businessDate), clock.instant());
    LOG.info(
        "PRVSCR01 run {} for {}: READ={} SCREENED={} FAILED={}",
        runId,
        businessDate,
        read,
        screened.size(),
        failed);
  }

  public void failed(UUID runId, LocalDate businessDate, int read, String reason) {
    LOG.error("PRVSCR01 run {} for {} FAILED: {}", runId, businessDate, reason);
    runs.fail(runId, read, reason, clock.instant());
  }
}
