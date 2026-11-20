package gov.medicaid.pem.directory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.directory.IntegrationTest;
import gov.medicaid.pem.directory.application.DirectoryExtracts;
import gov.medicaid.pem.directory.application.DirectoryExtracts.Run;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MAM-67: directory_db.extract_run on a real Postgres: a run with its count, its DIROUT file or its
 * failure (D-024); the file is only given for a COMPLETED run (AC-7: no partial file).
 */
class JdbcDirectoryExtractsTest extends IntegrationTest {

  static final Instant STARTED = Instant.parse("2026-01-15T10:00:00Z");
  static final Instant ENDED = Instant.parse("2026-01-15T10:00:05Z");
  static final LocalDate BUSINESS_DATE = LocalDate.parse("2026-03-31");

  @Autowired DirectoryExtracts extracts;
  @Autowired JdbcTemplate jdbc;

  Run open() {
    Run run = new Run(UUID.randomUUID(), BUSINESS_DATE, Run.RUNNING, 0, null, STARTED, null);
    extracts.create(run);
    return run;
  }

  static byte[] file(int records) {
    return ("M00000111" + "1003000111" + "IN" + "L" + " ".repeat(78))
        .repeat(records)
        .getBytes(StandardCharsets.US_ASCII);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] a created run is found RUNNING with its date, 0 rows, start time and no end or error")
  void created() {
    Run run = open();
    assertThat(extracts.find(run.runId())).contains(run);
  }

  @Test
  @DisplayName("[MAM-67 AC-1] a run that is not there is not found")
  void unknown() {
    assertThat(extracts.find(UUID.randomUUID())).isEmpty();
    assertThat(extracts.file(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-1] a completed run has status COMPLETED, its row count and its end time")
  void completed() {
    Run run = open();
    extracts.complete(run.runId(), 2, file(2), ENDED);
    assertThat(extracts.find(run.runId()))
        .contains(new Run(run.runId(), BUSINESS_DATE, "COMPLETED", 2, null, STARTED, ENDED));
  }

  @Test
  @DisplayName("[MAM-67 AC-3] the file of a completed run comes back byte for byte")
  void fileRoundTrip() {
    Run run = open();
    byte[] written = file(3);
    extracts.complete(run.runId(), 3, written, ENDED);
    assertThat(extracts.file(run.runId()))
        .hasValueSatisfying(f -> assertThat(f).isEqualTo(written));
  }

  @Test
  @DisplayName("[MAM-67 AC-3] an empty file of a completed run is an empty file, not a missing one")
  void emptyFile() {
    Run run = open();
    extracts.complete(run.runId(), 0, new byte[0], ENDED);
    assertThat(extracts.file(run.runId())).hasValueSatisfying(f -> assertThat(f).isEmpty());
    assertThat(extracts.find(run.runId()).orElseThrow().rows()).isZero();
  }

  @Test
  @DisplayName("[MAM-67 AC-7] a RUNNING run has no file yet")
  void runningHasNoFile() {
    assertThat(extracts.file(open().runId())).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] a failed run has status FAILED, the reason and the end time, and no file")
  void failed() {
    Run run = open();
    extracts.fail(run.runId(), "the database rejected the write", ENDED);
    assertThat(extracts.find(run.runId()))
        .contains(
            new Run(
                run.runId(),
                BUSINESS_DATE,
                "FAILED",
                0,
                "the database rejected the write",
                STARTED,
                ENDED));
    assertThat(extracts.file(run.runId())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-7] a failure removes any file the run had")
  void failureRemovesFile() {
    Run run = open();
    jdbc.update("UPDATE extract_run SET extract_file = 'PARTIAL' WHERE public_id = ?", run.runId());
    extracts.fail(run.runId(), "stopped", ENDED);
    String stored =
        jdbc.queryForObject(
            "SELECT extract_file FROM extract_run WHERE public_id = ?", String.class, run.runId());
    assertThat(stored).isNull();
  }

  @Test
  @DisplayName("[MAM-67 AC-7] a run that is already COMPLETED is not turned into FAILED afterwards")
  void completedStaysCompleted() {
    Run run = open();
    extracts.complete(run.runId(), 1, file(1), ENDED);
    extracts.fail(run.runId(), "late failure", ENDED.plusSeconds(60));
    Run found = extracts.find(run.runId()).orElseThrow();
    assertThat(found.status()).isEqualTo("COMPLETED");
    assertThat(found.error()).isNull();
    assertThat(found.endedAt()).isEqualTo(ENDED);
    assertThat(extracts.file(run.runId())).isPresent();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] a failure without a reason is recorded as 'failed', so the error text is never empty")
  void blankReason() {
    Run first = open();
    extracts.fail(first.runId(), null, ENDED);
    Run second = open();
    extracts.fail(second.runId(), "  ", ENDED);
    assertThat(extracts.find(first.runId()).orElseThrow().error()).isEqualTo("failed");
    assertThat(extracts.find(second.runId()).orElseThrow().error()).isEqualTo("failed");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-7] a reason longer than 500 characters is cut to 500; one of 500 is kept whole")
  void longReason() {
    Run exact = open();
    extracts.fail(exact.runId(), "e".repeat(500), ENDED);
    Run longer = open();
    extracts.fail(longer.runId(), "e".repeat(501), ENDED);
    assertThat(extracts.find(exact.runId()).orElseThrow().error()).hasSize(500);
    assertThat(extracts.find(longer.runId()).orElseThrow().error()).hasSize(500);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-6] two runs for the same business date are two rows with their own files")
  void sameDateTwoRuns() {
    Run a = open();
    Run b = open();
    extracts.complete(a.runId(), 1, file(1), ENDED);
    extracts.complete(b.runId(), 2, file(2), ENDED);
    assertThat(extracts.file(a.runId()).orElseThrow()).hasSize(100);
    assertThat(extracts.file(b.runId()).orElseThrow()).hasSize(200);
    assertThat(a.runId()).isNotEqualTo(b.runId());
  }
}
