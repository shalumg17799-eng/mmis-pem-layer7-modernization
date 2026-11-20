package gov.medicaid.pem.sanctions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.sanctions.IntegrationTest;
import gov.medicaid.pem.sanctions.application.SanctionHistory;
import gov.medicaid.pem.sanctions.application.SanctionIntakeRuns;
import gov.medicaid.pem.sanctions.application.SanctionIntakeRuns.Run;
import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MAM-55: sanction_intake_run and sanction.intake_run_id of sanctions_db in a real Postgres. The
 * run's counts (AC-4), its terminated count (AC-5), its end or failure (AC-6) and the check that
 * decides a re-run skips a record (AC-7).
 */
class JdbcSanctionIntakeRunsTest extends IntegrationTest {

  static final Instant STARTED = Instant.parse("2026-11-19T10:00:00Z");
  static final Instant ENDED = Instant.parse("2026-11-19T10:00:05Z");
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);
  static final LocalDate EFFECTIVE = LocalDate.of(2026, 3, 31);
  static final String REASON = "SYNTHETIC TEST REASON";

  @Autowired SanctionIntakeRuns runs;
  @Autowired SanctionHistory history;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
  }

  UUID open(LocalDate businessDate) {
    UUID id = UUID.randomUUID();
    runs.create(id, businessDate, STARTED);
    return id;
  }

  UUID applied(UUID run, String npi, SanctionAction action, String reason, LocalDate date) {
    Sanction s =
        new Sanction(UUID.randomUUID(), npi, action, reason, date, "SANCIN", "admin1", STARTED);
    history.add(s);
    runs.link(s.sanctionId(), run);
    return s.sanctionId();
  }

  // ---- AC-1: a run is created for its business date -----------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] a new run is RUNNING for its business date, with no end, no error and zero counts")
  void created() {
    UUID id = open(BUSINESS_DATE);
    Run run = runs.find(id).orElseThrow();
    assertThat(run.runId()).isEqualTo(id);
    assertThat(run.businessDate()).isEqualTo(BUSINESS_DATE);
    assertThat(run.status()).isEqualTo("RUNNING");
    assertThat(run.startedAt()).isEqualTo(STARTED);
    assertThat(run.endedAt()).isNull();
    assertThat(run.error()).isNull();
    assertThat(run.read()).isZero();
    assertThat(run.applied()).isZero();
    assertThat(run.notFound()).isZero();
    assertThat(run.rejected()).isZero();
    assertThat(run.terminated()).isZero();
    assertThat(run.skipped()).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] a run id that does not exist is not found")
  void unknownRun() {
    assertThat(runs.find(UUID.fromString("00000000-0000-0000-0000-000000000000"))).isEmpty();
  }

  // ---- AC-4: counts --------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-4] the counts of a run are written as given and read back")
  void counts() {
    UUID id = open(BUSINESS_DATE);
    runs.counts(id, 5, 2, 1, 1);
    Run run = runs.find(id).orElseThrow();
    assertThat(run.read()).isEqualTo(5);
    assertThat(run.applied()).isEqualTo(2);
    assertThat(run.notFound()).isEqualTo(1);
    assertThat(run.rejected()).isEqualTo(1);
    assertThat(run.skipped()).isEqualTo(1);
    assertThat(run.status()).isEqualTo("RUNNING");
  }

  @Test
  @DisplayName("[MAM-55 AC-4] the counts of one run do not touch another run")
  void countsOfOtherRun() {
    UUID a = open(BUSINESS_DATE);
    UUID b = open(BUSINESS_DATE);
    runs.counts(a, 3, 3, 0, 0);
    assertThat(runs.find(b).orElseThrow().read()).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-4] a completed run is COMPLETED with its end time and no error")
  void complete() {
    UUID id = open(BUSINESS_DATE);
    runs.complete(id, ENDED);
    Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.endedAt()).isEqualTo(ENDED);
    assertThat(run.error()).isNull();
  }

  // ---- AC-5: terminated ----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-5] terminated is the number of T sanctions the run applied; S and R do not count")
  void terminated() {
    UUID id = open(BUSINESS_DATE);
    applied(id, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    applied(id, "1003000333", SanctionAction.S, REASON, EFFECTIVE);
    applied(id, "1003000222", SanctionAction.R, REASON, EFFECTIVE);
    applied(id, "1003000555", SanctionAction.T, REASON, EFFECTIVE);
    assertThat(runs.find(id).orElseThrow().terminated()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-5] a T sanction of another run, or an online one, is not counted for this run")
  void terminatedOnlyThisRun() {
    UUID mine = open(BUSINESS_DATE);
    UUID other = open(BUSINESS_DATE);
    applied(mine, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    applied(other, "1003000333", SanctionAction.T, REASON, EFFECTIVE);
    history.add(
        new Sanction(
            UUID.randomUUID(),
            "1003000222",
            SanctionAction.T,
            REASON,
            EFFECTIVE,
            null,
            "supervisor1",
            STARTED));
    assertThat(runs.find(mine).orElseThrow().terminated()).isEqualTo(1);
    assertThat(runs.find(other).orElseThrow().terminated()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-55 AC-3] link ties the sanction to its run through sanction.intake_run_id")
  void link() {
    UUID id = open(BUSINESS_DATE);
    UUID sanction = applied(id, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT r.public_id AS run FROM sanction s JOIN sanction_intake_run r"
                + " ON r.id = s.intake_run_id WHERE s.public_id = ?",
            sanction);
    assertThat(row.get("run")).isEqualTo(id);
  }

  // ---- AC-6: failure --------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-6] a failed run is FAILED with the error text and its end time")
  void fail() {
    UUID id = open(BUSINESS_DATE);
    runs.fail(id, "record 2 is 60 bytes, not 80", ENDED);
    Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("FAILED");
    assertThat(run.error()).isEqualTo("record 2 is 60 bytes, not 80");
    assertThat(run.endedAt()).isEqualTo(ENDED);
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a failure keeps the counts of the records done before it")
  void failKeepsCounts() {
    UUID id = open(BUSINESS_DATE);
    runs.counts(id, 1, 1, 0, 0);
    runs.fail(id, "record 2 is 60 bytes, not 80", ENDED);
    assertThat(runs.find(id).orElseThrow().applied()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-55 AC-6] an error text longer than 500 characters is cut to 500")
  void longError() {
    UUID id = open(BUSINESS_DATE);
    runs.fail(id, "x".repeat(501), ENDED);
    assertThat(runs.find(id).orElseThrow().error()).hasSize(500);
    UUID exact = open(BUSINESS_DATE);
    runs.fail(exact, "y".repeat(500), ENDED);
    assertThat(runs.find(exact).orElseThrow().error()).hasSize(500).startsWith("yyy");
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a failure without a reason still says that the run failed")
  void failWithoutReason() {
    UUID blank = open(BUSINESS_DATE);
    runs.fail(blank, "  ", ENDED);
    assertThat(runs.find(blank).orElseThrow().error()).isEqualTo("failed");
    UUID none = open(BUSINESS_DATE);
    runs.fail(none, null, ENDED);
    assertThat(runs.find(none).orElseThrow().error()).isEqualTo("failed");
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a run that is not RUNNING any more is not changed by a later failure")
  void failDoesNotOverwriteAnEndedRun() {
    UUID id = open(BUSINESS_DATE);
    runs.complete(id, ENDED);
    runs.fail(id, "too late", Instant.parse("2026-11-19T11:00:00Z"));
    Run run = runs.find(id).orElseThrow();
    assertThat(run.status()).isEqualTo("COMPLETED");
    assertThat(run.error()).isNull();
    assertThat(run.endedAt()).isEqualTo(ENDED);
  }

  // ---- AC-7: re-run ----------------------------------------------------------------------------

  boolean skipped(
      UUID thisRun,
      LocalDate businessDate,
      String npi,
      String action,
      String reason,
      LocalDate date) {
    return runs.alreadyApplied(thisRun, businessDate, npi, action, reason, date);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] a record an earlier run of the same business date applied is already applied")
  void alreadyApplied() {
    UUID first = open(BUSINESS_DATE);
    applied(first, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    UUID second = open(BUSINESS_DATE);
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE)).isTrue();
  }

  @Test
  @DisplayName("[MAM-55 AC-7] the same record is not 'already applied' by the run that applied it")
  void sameRunIsNotARerun() {
    UUID first = open(BUSINESS_DATE);
    applied(first, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    assertThat(skipped(first, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE)).isFalse();
  }

  @Test
  @DisplayName("[MAM-55 AC-7] another business date is not a re-run: the record applies again")
  void anotherBusinessDate() {
    UUID first = open(BUSINESS_DATE);
    applied(first, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    LocalDate next = BUSINESS_DATE.plusDays(1);
    UUID second = open(next);
    assertThat(skipped(second, next, "1003000111", "T", REASON, EFFECTIVE)).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] a record that differs in NPI, action, reason or effective date is not skipped")
  void differentRecord() {
    UUID first = open(BUSINESS_DATE);
    applied(first, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    UUID second = open(BUSINESS_DATE);
    assertThat(skipped(second, BUSINESS_DATE, "1003000333", "T", REASON, EFFECTIVE)).isFalse();
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "S", REASON, EFFECTIVE)).isFalse();
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", "OTHER REASON", EFFECTIVE))
        .isFalse();
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE.plusDays(1)))
        .isFalse();
  }

  @Test
  @DisplayName("[MAM-55 AC-7] a sanction recorded online (no run) never makes a record skipped")
  void onlineSanctionIsNotARun() {
    history.add(
        new Sanction(
            UUID.randomUUID(),
            "1003000111",
            SanctionAction.T,
            REASON,
            EFFECTIVE,
            null,
            "supervisor1",
            STARTED));
    UUID run = open(BUSINESS_DATE);
    assertThat(skipped(run, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE)).isFalse();
  }

  @Test
  @DisplayName("[MAM-55 AC-7] a record without a reason matches a sanction kept without one")
  void noReason() {
    UUID first = open(BUSINESS_DATE);
    applied(first, "1003000111", SanctionAction.T, null, EFFECTIVE);
    UUID second = open(BUSINESS_DATE);
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", "", EFFECTIVE)).isTrue();
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", null, EFFECTIVE)).isTrue();
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE)).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] the records a FAILED run applied are skipped when the date is run again")
  void failedRunsRecordsAreSkipped() {
    UUID first = open(BUSINESS_DATE);
    applied(first, "1003000111", SanctionAction.T, REASON, EFFECTIVE);
    runs.fail(first, "record 2 is 60 bytes, not 80", ENDED);
    UUID second = open(BUSINESS_DATE);
    assertThat(skipped(second, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE)).isTrue();
  }
}
