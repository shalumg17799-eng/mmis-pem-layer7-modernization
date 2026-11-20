package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.enrollment.application.IntakeRuns;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * intake_run and the run key of an application (MAM-56, MAM-57; V1): what the run table keeps, the
 * error rows in the order they were added, the end of a run (COMPLETED or FAILED, and a completed
 * run never becomes FAILED), and the check that decides whether a record was already processed on
 * the same business date by another run (AC-8).
 */
class JdbcIntakeRunsTest extends IntakeIntegrationTest {

  static final Instant START = Instant.parse("2026-03-31T08:00:00Z").truncatedTo(ChronoUnit.MICROS);
  static final Instant END = Instant.parse("2026-03-31T08:05:00Z");
  static final LocalDate D1 = LocalDate.parse("2026-03-31");
  static final LocalDate D2 = LocalDate.parse("2026-04-01");

  UUID newRun(LocalDate date) {
    UUID id = UUID.randomUUID();
    runs.create(id, date, "B0331001", START);
    return id;
  }

  /** An application row of the run with this raw record (the run key of MAM-57 AC-8). */
  void application(UUID run, String raw) {
    UUID application = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO application (public_id, npi, status, source, submitted_at) VALUES (?, '1003000111', 'REJECTED',"
            + " 'LEGACY_FILE', now())",
        application);
    runs.attach(application, run, raw);
  }

  static String raw(String text) {
    return String.format("%-200s", text);
  }

  // ---- create and find --------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-57 AC-1] a new run is RUNNING with every count 0, no error and no end time")
  void newRun() {
    UUID id = newRun(D1);
    IntakeRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.runId()).isEqualTo(id);
    assertThat(run.businessDate()).isEqualTo(D1);
    assertThat(run.batchId()).isEqualTo("B0331001");
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.read()).isZero();
    assertThat(run.written()).isZero();
    assertThat(run.errored()).isZero();
    assertThat(run.skipped()).isZero();
    assertThat(run.error()).isNull();
    assertThat(run.startedAt()).isEqualTo(START);
    assertThat(run.endedAt()).isNull();
  }

  @Test
  @DisplayName("[MAM-57 AC-1] an unknown run is not found, and has no error file")
  void unknownRun() {
    assertThat(runs.find(UUID.randomUUID())).isEmpty();
    assertThat(runs.errorFile(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-57 AC-1] counts are kept as given: read, written, errored, skipped")
  void counts() {
    UUID id = newRun(D1);
    runs.counts(id, 9, 4, 3, 2);
    IntakeRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.read()).isEqualTo(9);
    assertThat(run.written()).isEqualTo(4);
    assertThat(run.errored()).isEqualTo(3);
    assertThat(run.skipped()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-57 AC-1] the counts of one run do not touch another run")
  void countsAreForTheRunOnly() {
    UUID first = newRun(D1);
    UUID second = newRun(D2);
    runs.counts(first, 3, 3, 0, 0);
    assertThat(runs.find(second).orElseThrow().read()).isZero();
  }

  // ---- the error file ---------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-6] a run with no error row has an empty error file, not a missing one")
  void emptyErrorFile() {
    assertThat(runs.errorFile(newRun(D1))).contains("");
  }

  @Test
  @DisplayName("[MAM-56 AC-6] error rows are kept one after the other, in the order they were added")
  void errorRowsInOrder() {
    UUID id = newRun(D1);
    String first = "1003000222 TYPE:XX ERR:VALIDATION-FAILED".concat(" ".repeat(210));
    String second = "1003000333 TYPE:IN ERR:VALIDATION-FAILED".concat(" ".repeat(210));
    runs.addErrorRow(id, first);
    runs.addErrorRow(id, second);
    assertThat(runs.errorFile(id)).contains(first + second);
  }

  @Test
  @DisplayName("[MAM-56 AC-6] the error rows of one run are not in another run's file")
  void errorRowsAreForTheRunOnly() {
    UUID first = newRun(D1);
    UUID second = newRun(D1);
    runs.addErrorRow(first, "ROW");
    assertThat(runs.errorFile(second)).contains("");
    assertThat(runs.errorFile(first)).contains("ROW");
  }

  // ---- the end of a run -------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-57 AC-4] complete makes the run COMPLETED with its end time and no error")
  void complete() {
    UUID id = newRun(D1);
    runs.complete(id, END);
    IntakeRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.endedAt()).isEqualTo(END);
    assertThat(run.error()).isNull();
  }

  @Test
  @DisplayName("[MAM-57 AC-5] fail makes the run FAILED with its reason and end time")
  void fail() {
    UUID id = newRun(D1);
    runs.fail(id, "record 3 is 150 bytes, not 200", END);
    IntakeRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("record 3 is 150 bytes, not 200");
    assertThat(run.endedAt()).isEqualTo(END);
  }

  @Test
  @DisplayName("[MAM-57 AC-6] a run that is already COMPLETED is never turned into FAILED afterwards")
  void completedRunStaysCompleted() {
    UUID id = newRun(D1);
    runs.complete(id, END);
    runs.fail(id, "late failure", END.plusSeconds(60));
    IntakeRuns.Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.error()).isNull();
    assertThat(run.endedAt()).isEqualTo(END);
  }

  @Test
  @DisplayName("[MAM-57 AC-6] a failure with no reason is stored with the reason 'failed', never null")
  void failWithoutReason() {
    UUID first = newRun(D1);
    UUID second = newRun(D1);
    runs.fail(first, null, END);
    runs.fail(second, "   ", END);
    assertThat(runs.find(first).orElseThrow().error()).isEqualTo("failed");
    assertThat(runs.find(second).orElseThrow().error()).isEqualTo("failed");
  }

  // ---- the run key and the re-run check (AC-8) -----------------------------------------------------

  @Test
  @DisplayName("[MAM-57 AC-8] a record that another run of the same business date stored is already processed")
  void processedByAnotherRunOfTheSameDate() {
    UUID earlier = newRun(D1);
    UUID now = newRun(D1);
    application(earlier, raw("RECORD A"));
    assertThat(runs.alreadyProcessed(now, D1, raw("RECORD A"))).isTrue();
  }

  @Test
  @DisplayName("[MAM-57 AC-8] a record the same run stored is not 'already processed' by that run (duplicates in one file)")
  void notProcessedBySameRun() {
    UUID run = newRun(D1);
    application(run, raw("RECORD A"));
    assertThat(runs.alreadyProcessed(run, D1, raw("RECORD A"))).isFalse();
  }

  @Test
  @DisplayName("[MAM-57 AC-8] a record stored under another business date is not 'already processed'")
  void otherBusinessDate() {
    UUID earlier = newRun(D1);
    UUID now = newRun(D2);
    application(earlier, raw("RECORD A"));
    assertThat(runs.alreadyProcessed(now, D2, raw("RECORD A"))).isFalse();
  }

  @Test
  @DisplayName("[MAM-57 AC-8] another record of the same business date is not 'already processed'")
  void otherRecord() {
    UUID earlier = newRun(D1);
    UUID now = newRun(D1);
    application(earlier, raw("RECORD A"));
    assertThat(runs.alreadyProcessed(now, D1, raw("RECORD B"))).isFalse();
  }

  @Test
  @DisplayName("[MAM-57 AC-8] nothing is processed before any run stored anything")
  void nothingProcessedYet() {
    UUID now = newRun(D1);
    assertThat(runs.alreadyProcessed(now, D1, raw("RECORD A"))).isFalse();
  }

  @Test
  @DisplayName("[MAM-57 AC-8] attach ties the application to the run and keeps its raw 200-byte record")
  void attach() {
    UUID run = newRun(D1);
    application(run, raw("RECORD A"));
    assertThat(
            jdbc.queryForObject(
                "SELECT raw_record FROM application a JOIN intake_run r ON r.id = a.intake_run_id"
                    + " WHERE r.public_id = ?",
                String.class,
                run))
        .isEqualTo(raw("RECORD A"));
  }
}
