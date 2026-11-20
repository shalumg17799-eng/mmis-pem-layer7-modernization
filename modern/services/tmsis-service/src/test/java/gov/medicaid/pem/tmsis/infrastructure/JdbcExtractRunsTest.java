package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.Tmsisout;
import gov.medicaid.pem.tmsis.application.ExtractRun;
import gov.medicaid.pem.tmsis.application.ExtractRuns;
import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MAM-71 AC-1, AC-2, MAM-75 AC-2, AC-4, AC-6 and MAM-78 AC-1: the runs, their rows and the archive
 * generations on the real database (extract_run, extract_row, extract_archive): rows come back in
 * ascending NPI order; an archived file is added as the next generation and never overwritten or
 * deleted (Q-012); only a COMPLETED run has a file; a run that is not RUNNING cannot be failed
 * afterwards; the counts of the run are kept.
 */
class JdbcExtractRunsTest extends IntegrationTest {

  static final Instant START = Instant.parse("2026-01-15T10:00:00Z");
  static final Instant END = Instant.parse("2026-01-15T10:00:07Z");

  @Autowired ExtractRuns runs;

  /**
   * The runs of this class are made by hand and have no job behind them, so they stay RUNNING for
   * ever; they are ended here, before the base class waits for the end of every run.
   */
  @AfterEach
  void endTheRunsMadeByHand() {
    jdbc.update("UPDATE extract_run SET status = 'FAILED' WHERE status = 'RUNNING'");
  }

  UUID newRun(String businessDate, String period, Instant started) {
    UUID id = UUID.randomUUID();
    runs.create(
        new ExtractRun(
            id,
            LocalDate.parse(businessDate),
            period,
            ExtractRun.RUNNING,
            0,
            0,
            null,
            null,
            started,
            null));
    return id;
  }

  UUID newRun() {
    return newRun("2026-03-31", "26090", START);
  }

  static TmsisRecord rec(String npi) {
    return new TmsisRecord("M00000" + npi.substring(7), npi, "A", "26090");
  }

  static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.ISO_8859_1);
  }

  // ---- create and find --------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-1] a created run is found RUNNING with its business date, period, zero counts and start time, and no end")
  void createdRun() {
    UUID id = newRun();
    ExtractRun run = runs.find(id).orElseThrow();
    assertThat(run.runId()).isEqualTo(id);
    assertThat(run.businessDate()).isEqualTo(LocalDate.parse("2026-03-31"));
    assertThat(run.periodYyddd()).isEqualTo("26090");
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.rows()).isZero();
    assertThat(run.futureEnrolments()).isZero();
    assertThat(run.archiveGeneration()).isNull();
    assertThat(run.error()).isNull();
    assertThat(run.startedAt()).isEqualTo(START);
    assertThat(run.endedAt()).isNull();
  }

  @Test
  @DisplayName("[MAM-75 AC-1] a run id that does not exist is found as nothing")
  void unknownRun() {
    assertThat(runs.find(UUID.randomUUID())).isEmpty();
    assertThat(runs.file(UUID.randomUUID())).isEmpty();
  }

  // ---- rows -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-1] the rows of a run come back in ascending NPI order, whatever order they were added in, as the same records")
  void rowsInNpiOrder() {
    UUID id = newRun();
    runs.addRows(id, List.of(rec("1003000333"), rec("1003000111")));
    runs.addRows(id, List.of(rec("1003000222")));
    assertThat(runs.rows(id))
        .containsExactly(rec("1003000111"), rec("1003000222"), rec("1003000333"));
  }

  @Test
  @DisplayName("[MAM-71 AC-2] a Medicaid ID shorter than 9 characters comes back without padding")
  void shortMedicaidIdIsNotPadded() {
    UUID id = newRun();
    runs.addRows(id, List.of(new TmsisRecord("M123", "1003000111", "A", "26090")));
    assertThat(runs.rows(id).get(0).medicaidId()).isEqualTo("M123");
    assertThat(runs.rows(id).get(0).npi()).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("[MAM-71 AC-1] the rows of one run are not mixed with the rows of another")
  void rowsBelongToTheirRun() {
    UUID a = newRun();
    UUID b = newRun();
    runs.addRows(a, List.of(rec("1003000111")));
    runs.addRows(b, List.of(rec("1003000333"), rec("1003000444")));
    assertThat(runs.rows(a)).containsExactly(rec("1003000111"));
    assertThat(runs.rows(b)).containsExactly(rec("1003000333"), rec("1003000444"));
  }

  @Test
  @DisplayName("[MAM-71 AC-1] adding no rows changes nothing; a run without rows has none")
  void noRows() {
    UUID id = newRun();
    runs.addRows(id, List.of());
    assertThat(runs.rows(id)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] more than one chunk of rows are all kept: 450 rows come back in order")
  void manyRows() {
    UUID id = newRun();
    List<TmsisRecord> all = new ArrayList<>();
    for (int i = 450; i >= 1; i--) {
      all.add(rec(String.format("1003%06d", 1000 + i)));
    }
    runs.addRows(id, all);
    List<TmsisRecord> read = runs.rows(id);
    assertThat(read).hasSize(450);
    assertThat(read).extracting(TmsisRecord::npi).isSorted().doesNotHaveDuplicates();
  }

  // ---- future enrolments (MAM-78) ---------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-78 AC-1] flagged future enrolments are added to the run's count and accumulate")
  void futureEnrolmentsAccumulate() {
    UUID id = newRun();
    runs.addFutureEnrolments(id, 1);
    runs.addFutureEnrolments(id, 2);
    assertThat(runs.find(id).orElseThrow().futureEnrolments()).isEqualTo(3);
  }

  @Test
  @DisplayName("[MAM-78 AC-4] adding zero future enrolments leaves the count at 0")
  void zeroFutureEnrolments() {
    UUID id = newRun();
    runs.addFutureEnrolments(id, 0);
    assertThat(runs.find(id).orElseThrow().futureEnrolments()).isZero();
  }

  @Test
  @DisplayName("[MAM-78 AC-3] the count of one run is not added to another run")
  void futureEnrolmentsPerRun() {
    UUID a = newRun();
    UUID b = newRun();
    runs.addFutureEnrolments(a, 4);
    assertThat(runs.find(a).orElseThrow().futureEnrolments()).isEqualTo(4);
    assertThat(runs.find(b).orElseThrow().futureEnrolments()).isZero();
  }

  // ---- complete and the archive -----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-4] completing a run archives its file as generation 1, COMPLETED with the row count and end time; the file is the same bytes")
  void complete() {
    UUID id = newRun();
    byte[] file = bytes(Tmsisout.expected("M00000111", "1003000111", "26090"));
    int generation = runs.complete(id, 1, file, END);
    assertThat(generation).isEqualTo(1);
    ExtractRun run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.rows()).isEqualTo(1);
    assertThat(run.archiveGeneration()).isEqualTo(1);
    assertThat(run.endedAt()).isEqualTo(END);
    assertThat(runs.file(id).orElseThrow()).isEqualTo(file);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] each completed run is the next generation: 1, 2, 3, and the earlier files are not overwritten or deleted")
  void generationsGrow() {
    UUID a = newRun();
    UUID b = newRun();
    UUID c = newRun();
    assertThat(runs.complete(a, 1, bytes("AAAA"), END)).isEqualTo(1);
    assertThat(runs.complete(b, 2, bytes("BBBB"), END)).isEqualTo(2);
    assertThat(runs.complete(c, 0, bytes(""), END)).isEqualTo(3);
    assertThat(generations()).containsExactly(1, 2, 3);
    assertThat(runs.file(a).orElseThrow()).isEqualTo(bytes("AAAA"));
    assertThat(runs.file(b).orElseThrow()).isEqualTo(bytes("BBBB"));
    assertThat(runs.file(c).orElseThrow()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-4] the next generation follows the last archived one")
  void generationFollowsTheLast() {
    UUID a = newRun();
    runs.complete(a, 1, bytes("AAAA"), END);
    UUID b = newRun();
    assertThat(runs.complete(b, 1, bytes("BBBB"), END)).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a RUNNING run has no file, and neither has a FAILED one")
  void onlyCompletedRunsHaveAFile() {
    UUID running = newRun();
    UUID failed = newRun();
    runs.fail(failed, "provider-service answered 500", END);
    assertThat(runs.file(running)).isEmpty();
    assertThat(runs.file(failed)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a failed run is FAILED with its reason and end time, and has no generation")
  void fail() {
    UUID id = newRun();
    runs.fail(id, "provider-service answered 500", END);
    ExtractRun run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("provider-service answered 500");
    assertThat(run.endedAt()).isEqualTo(END);
    assertThat(run.archiveGeneration()).isNull();
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a blank reason is stored as 'failed', so the error is never empty")
  void blankReason() {
    UUID a = newRun();
    UUID b = newRun();
    runs.fail(a, "   ", END);
    runs.fail(b, null, END);
    assertThat(runs.find(a).orElseThrow().error()).isEqualTo("failed");
    assertThat(runs.find(b).orElseThrow().error()).isEqualTo("failed");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a very long reason is stored without failing the write of the failure itself")
  void longReasonIsStored() {
    UUID id = newRun();
    runs.fail(id, "x".repeat(5000), END);
    ExtractRun run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isNotBlank().hasSizeLessThanOrEqualTo(500);
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a COMPLETED run cannot be turned into a FAILED one afterwards")
  void completedRunStaysCompleted() {
    UUID id = newRun();
    runs.complete(id, 1, bytes("AAAA"), END);
    runs.fail(id, "late failure", END);
    ExtractRun run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.error()).isNull();
    assertThat(runs.file(id)).isPresent();
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a FAILED run keeps its first reason when it is failed again")
  void firstFailureWins() {
    UUID id = newRun();
    runs.fail(id, "first", END);
    runs.fail(id, "second", END);
    assertThat(runs.find(id).orElseThrow().error()).isEqualTo("first");
  }

  // ---- the list ---------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the page lists the runs newest first with the total, and offsets by page and size")
  void pageNewestFirst() {
    UUID a = newRun("2026-03-31", "26090", Instant.parse("2026-01-15T10:00:00Z"));
    UUID b = newRun("2026-03-31", "26090", Instant.parse("2026-01-15T11:00:00Z"));
    UUID c = newRun("2026-03-31", "26090", Instant.parse("2026-01-15T12:00:00Z"));
    ExtractRuns.RunPage all = runs.page(0, 10);
    assertThat(all.total()).isEqualTo(3);
    assertThat(all.items()).extracting(ExtractRun::runId).containsExactly(c, b, a);
    assertThat(runs.page(1, 1).items()).extracting(ExtractRun::runId).containsExactly(b);
    assertThat(runs.page(1, 2).items()).extracting(ExtractRun::runId).containsExactly(a);
    assertThat(runs.page(5, 2).items()).isEmpty();
    assertThat(runs.page(5, 2).total()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] the page shows each run's archive generation, null for a run without one")
  void pageShowsGenerations() {
    UUID a = newRun("2026-03-31", "26090", Instant.parse("2026-01-15T10:00:00Z"));
    UUID b = newRun("2026-03-31", "26090", Instant.parse("2026-01-15T11:00:00Z"));
    runs.complete(a, 0, bytes(""), END);
    ExtractRuns.RunPage page = runs.page(0, 10);
    assertThat(page.items().get(0).runId()).isEqualTo(b);
    assertThat(page.items().get(0).archiveGeneration()).isNull();
    assertThat(page.items().get(1).archiveGeneration()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-3] the business date and period of a run are stored as given, not from the clock")
  void businessDateIsStoredAsGiven() {
    TestClock.set("2026-05-10T09:00:00Z");
    UUID id = newRun("2026-03-31", "26090", Instant.parse("2026-05-10T09:00:00Z"));
    ExtractRun run = runs.find(id).orElseThrow();
    assertThat(run.businessDate()).isEqualTo(LocalDate.parse("2026-03-31"));
    assertThat(run.periodYyddd()).isEqualTo("26090");
  }
}
