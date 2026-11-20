package gov.medicaid.pem.enrollment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-57 AC-6 and MAM-56 AC-7 with the code-review lesson of the other services (D-024): when a run
 * fails on a database error, the reason in BatchRun.error (the 202 answer and the GET) and in the
 * stored failure_reason is a short reason, not Spring's whole wrapped text with the SQL statement.
 * It holds none of "select", "insert", "update" or "sql" (any case), is at most 200 characters, and
 * keeps the "record n NPI x" prefix where the record is known; the full exception is logged.
 *
 * <p>Seven failures of the real database are made, each at the second record of the usual file: the
 * insert of the application, the write of its run key, the write of the APPERROR row, the check
 * whether the record was already processed (a SELECT, which no trigger can reject: the run table is
 * a spy that throws Spring's own exception, with the statement text), the write of the counts and
 * of the completion, and the start of the job (a trigger on the job repository's instance table).
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
    /** runs.alreadyProcessed for record 2 */
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
      case ALREADY_PROCESSED_CHECK ->
          doThrow(
                  new UncategorizedSQLException(
                      "PreparedStatementCallback",
                      "SELECT count(*) FROM application a JOIN intake_run r ON r.id = a.intake_run_id"
                          + " WHERE r.business_date = ? AND r.public_id <> ? AND a.raw_record = ?",
                      new SQLException("the test database lost the connection", "08006")))
              .when(runs)
              .alreadyProcessed(any(), any(), argThat(raw -> raw != null && raw.startsWith(NPI_2)));
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
        ? "the test database lost the connection"
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
      "[MAM-57 AC-6] a failure of the check whether record 2 was processed names record 2 (the NPI is not read yet)")
  void checkFailureNamesTheRecord() throws Exception {
    Seen seen = failedRun(Failure.ALREADY_PROCESSED_CHECK);
    assertThat(seen.read().path("error").asText()).startsWith("record 2");
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
}
