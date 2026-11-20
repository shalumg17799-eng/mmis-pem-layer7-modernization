package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Code-review lesson (MAM-57 AC-6, AC-8; MAM-56 AC-6): a record is stored once, all or nothing. What
 * marks a record as done for a re-run (its link to the run, intake_run_id with raw_record) is written
 * in the same transaction as the application, the error and the event; so is the APPERROR row of a
 * rejected application. The real database rejects one of these writes for record 2, so the write
 * fails after the others were made: nothing of record 2 may remain, and a re-run of the file for the
 * same business date then stores record 2 exactly once.
 */
class IntakeAtomicityTest extends IntakeIntegrationTest {

  static final String NPI_1 = "1003000111";
  static final String NPI_2 = "1003000333";
  static final String NPI_3 = "1003000777";

  /** What fails at record 2. */
  enum Break {
    /** the write of intake_run_id and raw_record, after the application row was inserted */
    RUN_KEY,
    /** the insert of the application row itself */
    APPLICATION_INSERT
  }

  void breakIt(Break what) {
    switch (what) {
      case RUN_KEY ->
          reject(
              "reject_key",
              "UPDATE",
              "application",
              "NEW.intake_run_id IS NOT NULL AND NEW.npi = '" + NPI_2 + "'");
      case APPLICATION_INSERT ->
          reject("reject_app", "INSERT", "application", "NEW.npi = '" + NPI_2 + "'");
    }
  }

  byte[] threeRecords() {
    return AppInputFile.lines(
        AppInputFile.valid(NPI_1), AppInputFile.valid(NPI_2), AppInputFile.valid(NPI_3));
  }

  /** Runs the file; the run must end FAILED, or the failure was not injected. */
  JsonNode failedRun(Break what) throws Exception {
    breakIt(what);
    JsonNode run = IntakeApi.runToEnd(mvc, json, threeRecords(), DATE);
    assertThat(run.path("status").asText())
        .as("the run, error: " + run.path("error"))
        .isEqualTo("FAILED");
    return run;
  }

  /** The failed run, the write let through again, and the same file run again. */
  JsonNode rerunAfterTheFailure(Break what) throws Exception {
    failedRun(what);
    lift(what == Break.RUN_KEY ? "reject_key" : "reject_app");
    JsonNode again = IntakeApi.runToEnd(mvc, json, threeRecords(), DATE);
    assertThat(again.path("status").asText()).as("the re-run: " + again).isEqualTo("COMPLETED");
    return again;
  }

  // ---- when the write fails, nothing of the record remains (AC-6) ---------------------------------

  @ParameterizedTest(name = "[MAM-57 AC-6] when {0} fails for record 2, no application of record 2 remains")
  @EnumSource(Break.class)
  void noApplicationRemains(Break what) throws Exception {
    failedRun(what);
    assertThat(applications(NPI_2)).isZero();
  }

  @ParameterizedTest(name = "[MAM-57 AC-6] when {0} fails for record 2, no event of record 2 remains")
  @EnumSource(Break.class)
  void noEventRemains(Break what) throws Exception {
    failedRun(what);
    assertThat(
            count("SELECT count(*) FROM outbox_event WHERE subject = ?", NPI_2))
        .isZero();
  }

  @ParameterizedTest(
      name = "[MAM-57 AC-6] when {0} fails for record 2, the run ends FAILED naming record 2 and its NPI with an end time")
  @EnumSource(Break.class)
  void failedRunNamesTheRecord(Break what) throws Exception {
    JsonNode run = failedRun(what);
    assertThat(run.path("error").asText()).contains("record 2").contains(NPI_2);
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @ParameterizedTest(
      name = "[MAM-57 AC-6] when {0} fails for record 2, record 1 keeps its application, its run key and its event")
  @EnumSource(Break.class)
  void recordOneKeepsItsResult(Break what) throws Exception {
    failedRun(what);
    assertThat(
            count(
                "SELECT count(*) FROM application WHERE npi = ? AND intake_run_id IS NOT NULL"
                    + " AND status = 'VALIDATED'",
                NPI_1))
        .isEqualTo(1);
    assertThat(subjects("ApplicationValidated")).containsExactly(NPI_1);
  }

  @ParameterizedTest(
      name = "[MAM-57 AC-6] when {0} fails for record 2, record 3 is not processed")
  @EnumSource(Break.class)
  void recordThreeIsNotProcessed(Break what) throws Exception {
    failedRun(what);
    assertThat(applications(NPI_3)).isZero();
  }

  // ---- the re-run stores the record exactly once (AC-8) ---------------------------------------------

  @ParameterizedTest(
      name = "[MAM-57 AC-8] after {0} failed, the re-run of the same business date leaves exactly one application per record")
  @EnumSource(Break.class)
  void rerunLeavesOneApplicationPerRecord(Break what) throws Exception {
    rerunAfterTheFailure(what);
    assertThat(
            jdbc.queryForList(
                "SELECT trim(npi) AS npi, count(*) AS n FROM application GROUP BY npi ORDER BY npi"))
        .extracting(row -> row.get("npi"), row -> row.get("n"))
        .containsExactly(tuple(NPI_1, 1L), tuple(NPI_2, 1L), tuple(NPI_3, 1L));
  }

  @ParameterizedTest(
      name = "[MAM-57 AC-8] after {0} failed, the re-run of the same business date publishes exactly one ApplicationValidated per record")
  @EnumSource(Break.class)
  void rerunPublishesOneEventPerRecord(Break what) throws Exception {
    rerunAfterTheFailure(what);
    assertThat(
            jdbc.queryForList(
                "SELECT trim(subject) AS subject, count(*) AS n FROM outbox_event"
                    + " WHERE type = 'ApplicationValidated' GROUP BY subject ORDER BY subject"))
        .extracting(row -> row.get("subject"), row -> row.get("n"))
        .containsExactly(tuple(NPI_1, 1L), tuple(NPI_2, 1L), tuple(NPI_3, 1L));
  }

  @ParameterizedTest(
      name = "[MAM-57 AC-8] after {0} failed, the re-run reads 3, writes 2 (records 2 and 3) and skips record 1")
  @EnumSource(Break.class)
  void rerunCounts(Break what) throws Exception {
    JsonNode counts = rerunAfterTheFailure(what).path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(3);
    assertThat(counts.path("written").asInt()).isEqualTo(2);
    assertThat(counts.path("errored").asInt()).isZero();
    assertThat(counts.path("skipped").asInt()).isEqualTo(1);
  }

  // ---- the APPERROR row of a rejected application is written with it (AC-6) --------------------------

  static final String ROW_REJECTION = "reject_row";

  byte[] withARejectedRecord() {
    return AppInputFile.lines(
        AppInputFile.valid(NPI_1), AppInputFile.noTaxonomy(NPI_2), AppInputFile.valid(NPI_3));
  }

  /** The run fails when the APPERROR row of the rejected record 2 is written. */
  JsonNode runThatCannotWriteTheErrorRow() throws Exception {
    reject(ROW_REJECTION, "UPDATE", "intake_run", "NEW.error_file IS NOT NULL");
    JsonNode run = IntakeApi.runToEnd(mvc, json, withARejectedRecord(), DATE);
    assertThat(run.path("status").asText()).as("the run: " + run).isEqualTo("FAILED");
    return run;
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-6] when the APPERROR row of a rejected application cannot be written, the application is not kept without it")
  void rejectedApplicationAndItsRowAreWrittenTogether() throws Exception {
    JsonNode run = runThatCannotWriteTheErrorRow();
    int rows = AppInputFile.rows(IntakeApi.errorFileOf(mvc, run.path("runId").asText())).size();
    int stored = applications(NPI_2);
    assertThat(rows).as("APPERROR rows of the run, applications stored for record 2: %s", stored).isEqualTo(stored);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-6] after the failed run and a re-run, the rejected application has exactly one APPERROR row in all")
  void rerunLeavesExactlyOneErrorRowForTheRejectedApplication() throws Exception {
    JsonNode failed = runThatCannotWriteTheErrorRow();
    lift(ROW_REJECTION);
    JsonNode again = IntakeApi.runToEnd(mvc, json, withARejectedRecord(), DATE);
    assertThat(again.path("status").asText()).as("the re-run: " + again).isEqualTo("COMPLETED");
    List<String> rows = new java.util.ArrayList<>();
    rows.addAll(AppInputFile.rows(IntakeApi.errorFileOf(mvc, failed.path("runId").asText())));
    rows.addAll(AppInputFile.rows(IntakeApi.errorFileOf(mvc, again.path("runId").asText())));
    assertThat(rows).containsExactly(AppInputFile.errorRow(NPI_2, "IN"));
  }
}
