package gov.medicaid.pem.sanctions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.ReviewFixesTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-55 code review 2026-11-19, MUST-FIX 1: a record is applied once, all or nothing. The key a
 * re-run uses to recognise an applied record (sanction.intake_run_id) must be written in the same
 * transaction as the history row and the SanctionApplied event. The real database rejects the write
 * of that key for record 2 (a trigger on the sanction table), so the write of the key fails after
 * the history row and the event were written: nothing of record 2 may remain, and a re-run of the
 * file for the same business date then applies record 2 exactly once (AC-6, AC-7).
 */
class SanctionIntakeAtomicityTest extends ReviewFixesTest {

  static final String RUN_KEY = "reject_run_key";

  @BeforeEach
  void theRunKeyOfRecordTwoCannotBeWritten() {
    reject(
        RUN_KEY,
        "INSERT OR UPDATE",
        "sanction",
        "NEW.intake_run_id IS NOT NULL AND NEW.npi = '" + NPI_2 + "'");
  }

  /** Runs the file; the run must end FAILED, or the failure was not injected. */
  JsonNode failedRun() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, threeRecords(), BUSINESS_DATE);
    assertThat(run.path("status").asText())
        .as("the run, error: " + run.path("error"))
        .isEqualTo("FAILED");
    return run;
  }

  /** The failed run, the write let through again, and the same file run again. */
  JsonNode rerunAfterTheFailure() throws Exception {
    failedRun();
    lift(RUN_KEY);
    JsonNode again = IntakeApi.runToEnd(mvc, json, threeRecords(), BUSINESS_DATE);
    assertThat(again.path("status").asText()).as("the re-run").isEqualTo("COMPLETED");
    return again;
  }

  // ---- AC-6: when the key cannot be written, nothing of the record remains --------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when the run key of record 2 cannot be written, no history row of record 2 remains (code review 2026-11-19)")
  void noHistoryRowRemains() throws Exception {
    failedRun();
    assertThat(historyRows(NPI_2)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when the run key of record 2 cannot be written, no SanctionApplied event of record 2 remains (code review 2026-11-19)")
  void noEventRemains() throws Exception {
    failedRun();
    assertThat(sanctionAppliedEvents(NPI_2)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when the run key of record 2 cannot be written, the run ends FAILED naming record 2 and its NPI (code review 2026-11-19)")
  void failedRunNamesTheRecord() throws Exception {
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).contains("record 2").contains(NPI_2);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when the run key of record 2 cannot be written, record 1 keeps its history row, its run key and its event (code review 2026-11-19)")
  void recordOneKeepsItsResult() throws Exception {
    failedRun();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sanction WHERE npi = ? AND intake_run_id IS NOT NULL",
                Integer.class,
                NPI_1))
        .isEqualTo(1);
    assertThat(sanctionAppliedEvents(NPI_1)).isEqualTo(1);
  }

  // ---- AC-7: the re-run applies the record exactly once ---------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-7] after the failed run the re-run of the same business date leaves exactly one history row per record (code review 2026-11-19)")
  void rerunLeavesOneHistoryRowPerRecord() throws Exception {
    rerunAfterTheFailure();
    assertThat(
            jdbc.queryForList("SELECT npi, count(*) AS n FROM sanction GROUP BY npi ORDER BY npi"))
        .extracting(row -> row.get("npi"), row -> row.get("n"))
        .containsExactly(tuple(NPI_1, 1L), tuple(NPI_3, 1L), tuple(NPI_2, 1L));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] after the failed run the re-run of the same business date publishes exactly one SanctionApplied per record (code review 2026-11-19)")
  void rerunPublishesOneEventPerRecord() throws Exception {
    rerunAfterTheFailure();
    assertThat(
            jdbc.queryForList(
                "SELECT subject, count(*) AS n FROM outbox_event WHERE type = 'SanctionApplied'"
                    + " GROUP BY subject ORDER BY subject"))
        .extracting(row -> row.get("subject"), row -> row.get("n"))
        .containsExactly(tuple(NPI_1, 1L), tuple(NPI_3, 1L), tuple(NPI_2, 1L));
  }
}
