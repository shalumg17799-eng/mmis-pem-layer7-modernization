package gov.medicaid.pem.tmsis.application;

import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The last step of a run (PRVMTH01 STEP030, MAM-75): the run's records in ascending NPI order are
 * written as the TMSISOUT file, archived as the next generation, the run is COMPLETED with its row
 * count (WS-WRITE-COUNT) and ExtractProduced is put in the outbox, all in one transaction, so the
 * event is published only for a COMPLETED, archived run (AC-5). Its subject and key are the run id
 * (events.yaml 1.1.0, Q-061 B). A run that failed before this step is never archived and announces
 * nothing (D-024; the legacy archived whatever file was there).
 */
@Service
public class ArchiveExtract {

  private static final Logger LOG = LoggerFactory.getLogger(ArchiveExtract.class);
  static final String EXTRACT_PRODUCED = "ExtractProduced";

  private final ExtractRuns runs;
  private final TmsisFile file;
  private final EventOutbox outbox;
  private final Clock clock;

  public ArchiveExtract(ExtractRuns runs, TmsisFile file, EventOutbox outbox, Clock clock) {
    this.runs = runs;
    this.file = file;
    this.outbox = outbox;
    this.clock = clock;
  }

  @Transactional
  public int archive(UUID runId) {
    ExtractRun run =
        runs.find(runId).orElseThrow(() -> new IllegalStateException("no extract run " + runId));
    List<TmsisRecord> records = runs.rows(runId);
    int generation = runs.complete(runId, records.size(), file.write(records), clock.instant());
    outbox.enqueue(
        EXTRACT_PRODUCED,
        runId.toString(),
        new ExtractProducedData(runId, run.businessDate(), run.periodYyddd(), records.size()));
    LOG.info(
        "PRVTMS01 run {} COMPLETED: {} records written, archive generation {}",
        runId,
        records.size(),
        generation);
    return generation;
  }
}
