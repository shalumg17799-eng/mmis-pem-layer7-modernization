package gov.medicaid.pem.tmsis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-75 AC-1, AC-3, AC-6 and AC-7: starting an extract records a RUNNING run for the explicit
 * business date (D-020) and starts its job in the background; a job that cannot even be started is
 * a FAILED run with a short reason (D-024); the scheduled start takes the business date from the
 * injected Clock (ADR-004).
 */
class StartTmsisExtractTest {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-03-31");

  final FakeExtractRuns runs = new FakeExtractRuns();
  final List<String> launched = new ArrayList<>();

  StartTmsisExtract start(ExtractJobLauncher launcher, Clock clock) {
    return new StartTmsisExtract(runs, launcher, clock);
  }

  StartTmsisExtract start(ExtractJobLauncher launcher) {
    return start(launcher, CLOCK);
  }

  // ---- AC-1: the run is recorded and its job started ---------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-1] a new run is RUNNING for the given business date, with its period, no rows, no generation and no end")
  void newRunIsRunning() {
    ExtractRun run = start((id, date) -> {}).start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo(ExtractRun.RUNNING);
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.periodYyddd()).isEqualTo("26090");
    assertThat(run.rows()).isZero();
    assertThat(run.futureEnrolments()).isZero();
    assertThat(run.archiveGeneration()).isNull();
    assertThat(run.error()).isNull();
    assertThat(run.endedAt()).isNull();
    assertThat(run.runId()).isNotNull();
  }

  @Test
  @DisplayName("[MAM-75 AC-1] the run's start time is the clock's, not the system time")
  void startedAtIsTheClocks() {
    assertThat(start((id, date) -> {}).start(BUSINESS_DATE).startedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the run is stored before its job is started, and the job is started once for that run and date")
  void runStoredThenJobStarted() {
    ExtractRun run =
        start(
                (id, date) -> {
                  launched.add(id + "@" + date);
                  assertThat(runs.runs).containsKey(id);
                })
            .start(BUSINESS_DATE);
    assertThat(launched).containsExactly(run.runId() + "@" + BUSINESS_DATE);
    assertThat(runs.calls.get(0)).isEqualTo("create");
  }

  @Test
  @DisplayName("[MAM-75 AC-1] every start is a run of its own with a new run id")
  void newRunIdEachTime() {
    StartTmsisExtract start = start((id, date) -> {});
    UUID a = start.start(BUSINESS_DATE).runId();
    UUID b = start.start(BUSINESS_DATE).runId();
    assertThat(a).isNotEqualTo(b);
    assertThat(runs.runs).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the answer is the run as the job left it when it has already finished (COMPLETED)")
  void answerIsTheStoredRun() {
    ExtractRun run =
        start(
                (id, date) ->
                    runs.complete(id, 2, new byte[240], Instant.parse("2026-01-15T10:00:01Z")))
            .start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo(ExtractRun.COMPLETED);
    assertThat(run.rows()).isEqualTo(2);
    assertThat(run.archiveGeneration()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-75 AC-1] if the stored run cannot be read back, the run that was opened is answered")
  void storedRunMissing() {
    runs.findReturnsNothing = true;
    ExtractRun run = start((id, date) -> {}).start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo(ExtractRun.RUNNING);
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
  }

  // ---- AC-6: a job that cannot be started ---------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a job that cannot be started leaves a FAILED run with the reason and the clock's end time")
  void launchFailureIsRecorded() {
    ExtractRun run =
        start(
                (id, date) -> {
                  throw new IllegalStateException("job PRVTMS01 not started: no job repository");
                })
            .start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo(ExtractRun.FAILED);
    assertThat(run.error()).contains("no job repository");
    assertThat(run.endedAt()).isEqualTo(NOW);
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.archiveGeneration()).isNull();
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a failed start is answered, not thrown: the caller sees the FAILED run")
  void launchFailureIsNotThrown() {
    ExtractRun run =
        start(
                (id, date) -> {
                  throw new IllegalStateException("no thread available");
                })
            .start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo(ExtractRun.FAILED);
    assertThat(runs.calls).contains("fail");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the reason of a start that failed on a database error is short and has no SQL statement text (D-024)")
  void launchFailureReasonIsShortAndHasNoSql() {
    String sql =
        "PreparedStatementCallback; SQL [INSERT INTO BATCH_JOB_INSTANCE(JOB_INSTANCE_ID, JOB_NAME,"
            + " JOB_KEY, VERSION) values (?, ?, ?, ?)]; ERROR: the test database rejected this"
            + " write Where: PL/pgSQL function reject_job() line 1 at RAISE";
    ExtractRun run =
        start(
                (id, date) -> {
                  throw new IllegalStateException("job PRVTMS01 not started: " + sql);
                })
            .start(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo(ExtractRun.FAILED);
    assertThat(run.error()).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(run.error().toLowerCase()).doesNotContain("select", "insert", "update", "sql");
  }

  // ---- AC-7: the schedule uses the Clock's date ---------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the scheduled start with the Clock at 2026-04-30 runs for the business date 2026-04-30 (period 26120)")
  void scheduledStartUsesTheClocksDate() {
    Clock clock = Clock.fixed(Instant.parse("2026-04-30T22:00:00Z"), ZoneOffset.UTC);
    ExtractRun run = start((id, date) -> launched.add(id + "@" + date), clock).startForToday();
    assertThat(run.businessDate()).isEqualTo(LocalDate.parse("2026-04-30"));
    assertThat(run.periodYyddd()).isEqualTo("26120");
    assertThat(launched).containsExactly(run.runId() + "@2026-04-30");
    assertThat(run.startedAt()).isEqualTo(Instant.parse("2026-04-30T22:00:00Z"));
  }

  @ParameterizedTest(
      name = "[MAM-75 AC-7] the Clock at {0} gives the business date {1} and the period {2}")
  @CsvSource({
    "2026-12-31T23:59:59Z, 2026-12-31, 26365",
    "2027-01-01T00:00:00Z, 2027-01-01, 27001",
    "2028-12-31T12:00:00Z, 2028-12-31, 28366",
    "2026-03-31T00:00:00Z, 2026-03-31, 26090"
  })
  void clockDateEdges(String instant, String date, String period) {
    Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
    ExtractRun run = start((id, d) -> {}, clock).startForToday();
    assertThat(run.businessDate()).isEqualTo(LocalDate.parse(date));
    assertThat(run.periodYyddd()).isEqualTo(period);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-3] an explicit business date is used as it is, whatever the clock shows (2026-01-15)")
  void explicitDateBeatsTheClock() {
    ExtractRun run = start((id, date) -> {}).start(LocalDate.parse("2026-03-31"));
    assertThat(run.businessDate()).isEqualTo(LocalDate.parse("2026-03-31"));
    assertThat(run.periodYyddd()).isEqualTo("26090").isNotEqualTo("26015");
  }
}
