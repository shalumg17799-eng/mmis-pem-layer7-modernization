package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.SweepDecision;
import gov.medicaid.pem.revalidation.domain.SweepRule;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The work of one revalidation sweep run (MAM-45, PRVREV01), called by the step of its Spring Batch
 * job (ADR-004, Q-102 A): every tracked provider is evaluated; each due ACTIVE provider without an
 * open case gets one DUE case and one RevalidationDue, and the run records its counts and its
 * REVALQ file, all in the step's one transaction (D-029, ADR-003). A run whose job does not
 * complete is recorded FAILED with the reason (D-024).
 */
@Service
public class ExecuteSweep {

  private static final Logger LOG = LoggerFactory.getLogger(ExecuteSweep.class);

  /** What one sweep did: providers evaluated, cases opened, providers without a due date. */
  public record Result(int evaluated, int queued, int noDueDate) {}

  private final SweepRuns runs;
  private final CaseStore cases;
  private final QueueFile queueFile;
  private final EventOutbox outbox;
  private final Clock clock;

  public ExecuteSweep(
      SweepRuns runs, CaseStore cases, QueueFile queueFile, EventOutbox outbox, Clock clock) {
    this.runs = runs;
    this.cases = cases;
    this.queueFile = queueFile;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public Result execute(UUID runId, LocalDate businessDate) {
    List<RevalidationCase> opened = new ArrayList<>();
    int evaluated = 0;
    int noDueDate = 0;
    for (TrackedProvider p : cases.trackedProviders()) {
      evaluated++;
      SweepDecision d = SweepRule.decide(p, businessDate);
      if (d == SweepDecision.NO_DUE_DATE) {
        noDueDate++;
      } else if (d == SweepDecision.QUEUE && !cases.hasOpenCase(p.npi())) {
        RevalidationCase c =
            new RevalidationCase(
                UUID.randomUUID(), p.npi(), p.dueDate(), CaseStatus.DUE, clock.instant(), null);
        cases.open(c, runId);
        outbox.enqueue(
            "RevalidationDue", c.npi(), new RevalidationDueData(c.caseId(), c.npi(), c.dueDate()));
        opened.add(c);
      }
    }
    runs.complete(runId, opened.size(), noDueDate, queueFile.write(opened), clock.instant());
    LOG.info(
        "Revalidation sweep {} for {}: QUEUED={} NO-DUE-DATE={}",
        runId,
        businessDate,
        opened.size(),
        noDueDate);
    return new Result(evaluated, opened.size(), noDueDate);
  }

  /** A run whose job did not complete: FAILED with the short reason (D-024). */
  public void failed(UUID runId, LocalDate businessDate, String reason) {
    failed(runId, businessDate, reason, null);
  }

  /** The same; the whole exception of the failure, if there is one, goes to the log. */
  public void failed(UUID runId, LocalDate businessDate, String reason, Throwable cause) {
    LOG.error("Revalidation sweep {} for {} FAILED: {}", runId, businessDate, reason, cause);
    runs.fail(runId, reason, clock.instant());
  }
}
