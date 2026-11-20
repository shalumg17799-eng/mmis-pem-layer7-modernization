package gov.medicaid.pem.sanctions.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.ReviewFixesTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-55 code review 2026-11-19, MUST-FIX 2 (D-024): when a run fails on a database error, the
 * reason in BatchRun.error (the 202 answer and the GET) and in the stored failure_reason is a short
 * reason, not Spring's whole wrapped text with the SQL statement. It holds none of "select",
 * "insert", "update" or "sql" (any case), is at most 200 characters, and keeps the "record n NPI x"
 * prefix where the record is known.
 *
 * <p>Five failures of the real database are made, each at the second record of the usual file: the
 * insert of the history row and the write of the counts and of the completion (triggers that reject
 * that write), the failure to start the job (a trigger on the job repository's instance table), and
 * the check whether the record was already applied (a SELECT, which no trigger can reject: the run
 * table is a spy that throws Spring's own exception, with the statement text, for record 2).
 */
class SanctionIntakeFailureReasonTest extends ReviewFixesTest {

  /** The database operation that fails. */
  enum Failure {
    /** insert of the history row of record 2 */
    HISTORY_INSERT,
    /** runs.alreadyApplied for record 2 */
    ALREADY_APPLIED_CHECK,
    /** runs.counts after record 2 */
    RUN_COUNTS,
    /** runs.complete after the last record */
    RUN_COMPLETE,
    /** the job repository when the job is started */
    JOB_LAUNCH
  }

  /** What the caller can see of a failed run. */
  record Seen(JsonNode answer, JsonNode read, String stored) {

    /** The error texts shown to a caller: the 202 answer (when it already has one) and the GET. */
    List<String> shown() {
      List<String> texts = new ArrayList<>();
      if (!answer.path("error").isMissingNode() && !answer.path("error").isNull()) {
        texts.add(answer.path("error").asText());
      }
      texts.add(read.path("error").asText());
      return texts;
    }
  }

  static final String[] SQL_WORDS = {"select", "insert", "update", "sql"};
  static final int SHORT = 200;

  void inject(Failure failure) {
    switch (failure) {
      case HISTORY_INSERT ->
          reject("reject_history", "INSERT", "sanction", "NEW.npi = '" + NPI_2 + "'");
      case ALREADY_APPLIED_CHECK ->
          doThrow(
                  new UncategorizedSQLException(
                      "PreparedStatementCallback",
                      "SELECT count(*) FROM sanction s JOIN sanction_intake_run r ON r.id ="
                          + " s.intake_run_id WHERE r.business_date = ? AND s.npi = ?",
                      new SQLException("the test database lost the connection", "08006")))
              .when(runs)
              .alreadyApplied(any(), any(), eq(NPI_2), any(), any(), any());
      case RUN_COUNTS ->
          reject(
              "reject_counts",
              "UPDATE",
              "sanction_intake_run",
              "NEW.status = 'RUNNING' AND NEW.read_count >= 2");
      case RUN_COMPLETE ->
          reject("reject_complete", "UPDATE", "sanction_intake_run", "NEW.status = 'COMPLETED'");
      case JOB_LAUNCH -> reject("reject_job", "INSERT", "batch_job_instance", "true");
    }
  }

  /** Makes the failure, runs the usual file as ADMIN and reads the failed run back. */
  Seen failedRun(Failure failure) throws Exception {
    inject(failure);
    MvcResult started = IntakeApi.start(mvc, PemJwt.admin(), threeRecords(), BUSINESS_DATE);
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
            "SELECT failure_reason FROM sanction_intake_run WHERE public_id = ?::uuid",
            String.class,
            runId);
    return new Seen(answer, read, stored);
  }

  // ---- what a caller is shown -----------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-55 AC-6] a database error at {0}: the error of the run has no SQL statement text (code review 2026-11-19)")
  @EnumSource(Failure.class)
  void errorHasNoSqlText(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.shown())
        .allSatisfy(text -> assertThat(text.toLowerCase()).doesNotContain(SQL_WORDS));
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-6] a database error at {0}: the error of the run is a short reason, not blank and at most 200 characters (code review 2026-11-19)")
  @EnumSource(Failure.class)
  void errorIsShort(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.shown())
        .allSatisfy(text -> assertThat(text).isNotBlank().hasSizeLessThanOrEqualTo(SHORT));
  }

  // ---- what is stored -------------------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-55 AC-6] a database error at {0}: the failure_reason stored with the run has no SQL statement text (code review 2026-11-19)")
  @EnumSource(Failure.class)
  void storedReasonHasNoSqlText(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.stored()).isNotBlank();
    assertThat(seen.stored().toLowerCase()).doesNotContain(SQL_WORDS);
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-6] a database error at {0}: the failure_reason stored with the run is at most 200 characters (code review 2026-11-19)")
  @EnumSource(Failure.class)
  void storedReasonIsShort(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.stored()).isNotBlank().hasSizeLessThanOrEqualTo(SHORT);
  }

  // ---- the record stays named -----------------------------------------------------------------

  /**
   * The record is known when it is applied or checked. HISTORY_INSERT names it today (a guard that
   * the shorter reason keeps it); ALREADY_APPLIED_CHECK does not yet.
   */
  @ParameterizedTest(
      name =
          "[MAM-55 AC-6] a database error at {0}: the short reason keeps the prefix \"record 2 NPI 1003000333\" (code review 2026-11-19)")
  @EnumSource(
      value = Failure.class,
      names = {"HISTORY_INSERT", "ALREADY_APPLIED_CHECK"})
  void errorNamesTheRecord(Failure failure) throws Exception {
    Seen seen = failedRun(failure);
    assertThat(seen.read().path("error").asText()).startsWith("record 2 NPI " + NPI_2);
  }
}
