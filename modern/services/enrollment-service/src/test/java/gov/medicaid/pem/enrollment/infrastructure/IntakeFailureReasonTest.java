package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-57 AC-6 and MAM-56 AC-7 with the code-review lesson of the other services (D-024): when a run
 * fails on a database error, the reason in BatchRun.error (the 202 answer and the GET) and in the
 * stored failure_reason is a short reason, not Spring's whole wrapped text with the SQL statement.
 * It holds none of "select", "insert", "update" or "sql" (any case), is at most 200 characters, and
 * keeps the "record n NPI x" prefix where the record is known; the full exception is logged.
 *
 * <p>Seven failures of the real database are made: the insert of the application, the write of its
 * run key and the write of the APPERROR row (triggers that reject that write), the check whether
 * the record was already processed (a SELECT, which no trigger can reject: the column it reads is
 * renamed for the test), the write of the counts and of the completion, and the start of the job (a
 * trigger on the job repository's instance table). All but the check fail at the second record of
 * the usual file; the check fails at the first.
 */
class IntakeFailureReasonTest extends IntakeIntegrationTest {

  /** The database operation that fails. */
  enum Failure {
    /** insert of the application of record 2 */
    APPLICATION_INSERT,
    /** the write of the run key (intake_run_id, raw_record) of record 2 */
    RUN_KEY,
    /** the write of the APPERROR row of the rejected record 2 */
    ERROR_ROW,
    /** the check whether record 1 was already processed (its column is missing) */
    ALREADY_PROCESSED_CHECK,
    /** runs.counts after record 2 */
    RUN_COUNTS,
    /** runs.complete after the last record */
    RUN_COMPLETE,
    /** the job repository when the job is started */
    JOB_LAUNCH
  }

  static final String NPI_1 = "1003000111";
  static final String NPI_2 = "1003000333";
  static final String NPI_3 = "1003000777";
  static final String[] SQL_WORDS = {"select", "insert", "update", "sql"};
  static final int SHORT = 200;

  /** What a caller can see of a failed run, and what is stored. */
  record Seen(JsonNode answer, JsonNode read, String stored) {

    /** The error texts shown to a caller (the 202 answer when it has one, the GET) and stored. */
    List<String> everything() {
      List<String> texts = new ArrayList<>();
      if (!answer.path("error").isMissingNode() && !answer.path("error").isNull()) {
        texts.add(answer.path("error").asText());
      }
      texts.add(read.path("error").asText());
      texts.add(stored);
      return texts;
    }
  }

  /** The file: record 2 is rejected for the ERROR_ROW failure, valid for the others. */
  static byte[] file(Failure failure) {
    return AppInputFile.lines(
        AppInputFile.valid(NPI_1),
        failure == Failure.ERROR_ROW ? AppInputFile.noTaxonomy(NPI_2) : AppInputFile.valid(NPI_2),
        AppInputFile.valid(NPI_3));
  }

  void inject(Failure failure) {
    switch (failure) {
      case APPLICATION_INSERT ->
          reject("reject_app", "INSERT", "application", "NEW.npi = '" + NPI_2 + "'");
      case RUN_KEY ->
          reject(
              "reject_key",
              "UPDATE",
              "application",
              "NEW.intake_run_id IS NOT NULL AND NEW.npi = '" + NPI_2 + "'");
      case ERROR_ROW -> reject("reject_row", "UPDATE", "intake_run", "NEW.error_file IS NOT NULL");
      case ALREADY_PROCESSED_CHECK -> {
        // a SELECT cannot be rejected by a trigger: the column it reads is renamed for the test
        jdbc.execute("ALTER TABLE application RENAME COLUMN raw_record TO raw_record_off");
        afterTest(
            () ->
                jdbc.execute("ALTER TABLE application RENAME COLUMN raw_record_off TO raw_record"));
      }
      case RUN_COUNTS ->
          reject(
              "reject_counts",
              "UPDATE",
              "intake_run",
              "NEW.status = 'RUNNING' AND NEW.read_count >= 2");
      case RUN_COMPLETE ->
          reject("reject_complete", "UPDATE", "intake_run", "NEW.status = 'COMPLETED'");
      case JOB_LAUNCH -> reject("reject_job", "INSERT", "batch_job_instance", "true");
    }
  }

  /** What the injected failure says in the database or driver: the full exception holds this. */
  static String causeOf(Failure failure) {
    return failure == Failure.ALREADY_PROCESSED_CHECK
        ? "does not exist"
        : "the test database rejected this write";
  }

  /** Makes the failure, runs the file as ADMIN and reads the failed run back. */
  Seen failedRun(Failure failure) throws Exception {
    inject(failure);
    MvcResult started = IntakeApi.start(mvc, PemJwt.admin(), file(failure), DATE);
    assertThat(started.getResponse().getStatus())
        .as(started.getResponse().getContentAsString())
        .isEqualTo(202);
    JsonNode answer = json.readTree(started.getResponse().getContentAsString());
    String runId = answer.path("runId").asText();
    JsonNode read = IntakeApi.await(mvc, json, runId);
    assertThat(read.path("status").asText())
        .as(failure + " must end the run FAILED, run: " + read)
        .isEqualTo("FAILED");
    String stored =
        jdbc.queryForObject(
            "SELECT failure_reason FROM intake_run WHERE public_id = ?::uuid", String.class, runId);
    return new Seen(answer, read, stored);
  }

  // ---- what a caller is shown and what is stored -----------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-57 AC-6] a database error at {0}: the error of the run and the stored reason have no SQL statement text (D-024)")
  @EnumSource(Failure.class)
  void errorHasNoSqlText(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.everything())
        .allSatisfy(text -> assertThat(text.toLowerCase()).doesNotContain(SQL_WORDS));
  }

  @ParameterizedTest(
      name =
          "[MAM-57 AC-6] a database error at {0}: the error of the run and the stored reason are short, not blank and at most 200 characters (D-024)")
  @EnumSource(Failure.class)
  void errorIsShort(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.everything())
        .allSatisfy(text -> assertThat(text).isNotBlank().hasSizeLessThanOrEqualTo(SHORT));
  }

  @ParameterizedTest(
      name =
          "[MAM-57 AC-6] a database error at {0}: the full exception is logged for the operator (D-024)")
  @EnumSource(Failure.class)
  void fullExceptionIsLogged(Failure failure) throws Exception {
    failedRun(failure);
    assertThat(log().lines)
        .filteredOn(line -> line.level().equals("ERROR") || line.level().equals("WARN"))
        .anySatisfy(line -> assertThat(line.everything()).contains(causeOf(failure)));
  }

  // ---- the record stays named -------------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-57 AC-6] a storage failure at {0}: the short reason keeps the prefix \"record 2 NPI 1003000333\"")
  @EnumSource(
      value = Failure.class,
      names = {"APPLICATION_INSERT", "RUN_KEY"})
  void errorNamesTheRecordAndItsNpi(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.read().path("error").asText()).startsWith("record 2 NPI " + NPI_2);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-6] a failure of the check whether record 1 was processed names record 1 (the NPI is not read yet)")
  void checkFailureNamesTheRecord() throws Exception {
    Seen seen = failedRun(Failure.ALREADY_PROCESSED_CHECK);
    assertThat(seen.read().path("error").asText()).startsWith("record 1");
  }

  @Test
  @DisplayName("[MAM-57 AC-6] a failed run has an end time and the earlier results are kept")
  void failedRunHasAnEndTime() throws Exception {
    Seen seen = failedRun(Failure.APPLICATION_INSERT);
    assertThat(seen.read().path("endedAt").asText()).isNotBlank();
    assertThat(statusOf(NPI_1)).isEqualTo("VALIDATED");
    assertThat(applications(NPI_2)).isZero();
    assertThat(applications(NPI_3)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-7] when the job cannot be started the 202 answer already shows the run FAILED with a short reason")
  void launchFailureShowsInTheAnswer() throws Exception {
    Seen seen = failedRun(Failure.JOB_LAUNCH);
    assertThat(seen.answer().path("status").asText()).isEqualTo("FAILED");
    assertThat(seen.answer().path("error").asText()).isNotBlank();
  }

  // ---- a failure nobody injected: a record that is not text
  // ----------------------------------------------

  /**
   * Record 2 has a NUL byte in its taxonomy: PostgreSQL cannot store it, whatever the service does.
   */
  static byte[] fileWithANulByte() {
    String nul = AppInputFile.with(AppInputFile.valid(NPI_2), 22, "207Q" + (char) 0 + "0000X");
    return AppInputFile.lines(AppInputFile.valid(NPI_1), nul, AppInputFile.valid(NPI_3));
  }

  JsonNode runWithANulByte() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, fileWithANulByte(), DATE);
    assertThat(run.path("status").asText()).as("the run: " + run).isEqualTo("FAILED");
    return run;
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-6] a record that the database cannot store (a NUL byte) ends the run FAILED, never COMPLETED, naming record 2 and its NPI")
  void nulByteEndsTheRunFailed() throws Exception {
    JsonNode run = runWithANulByte();
    assertThat(run.path("error").asText()).startsWith("record 2 NPI " + NPI_2);
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(2);
    assertThat(run.path("counts").path("errored").asInt()).isEqualTo(1);
    assertThat(statusOf(NPI_1)).isEqualTo("VALIDATED");
    assertThat(applications(NPI_3)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-6] the reason of a run that failed on a NUL byte is short and has no SQL statement text (D-024)")
  void nulByteReasonIsClean() throws Exception {
    JsonNode run = runWithANulByte();
    String stored =
        jdbc.queryForObject(
            "SELECT failure_reason FROM intake_run WHERE public_id = ?::uuid",
            String.class,
            run.path("runId").asText());
    assertThat(List.of(run.path("error").asText(), stored))
        .allSatisfy(
            text ->
                assertThat(text)
                    .hasSizeLessThanOrEqualTo(SHORT)
                    .satisfies(t -> assertThat(t.toLowerCase()).doesNotContain(SQL_WORDS)));
  }
}
