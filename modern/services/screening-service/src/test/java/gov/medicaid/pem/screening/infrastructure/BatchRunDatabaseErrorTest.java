package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.LogCapture;
import gov.medicaid.pem.screening.ScreeningWorld;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * MAM-32 AC-6 and AC-7, lessons a and c of the reviews of 2026-11-19 (decision D-024): a run that
 * ends on a database error is FAILED with a short reason that has no SQL text, the full exception
 * is logged, and what the run had written before is not left half done, so the next run for the
 * business date screens everybody exactly once. Every failure point of the run is tried: the insert
 * of a screening, the link of a screening to its run, the ScreeningCompleted written to the outbox,
 * the completion of the run with its file, and the start of the job itself (the batch repository).
 */
@Timeout(value = 150, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class BatchRunDatabaseErrorTest extends Lane3Test {

  static final String DATE = "2026-03-31";
  static final String P_111 = "1003000111";
  static final String P_555 = "1003000555";
  static final String[] SQL_WORDS = {"select", "insert", "update", "sql"};

  /** Where the database rejects a write. */
  enum Failure {
    /** the insert of the screening of the second provider */
    SCREENING_INSERT,
    /** the link of the screening of the second provider to the run */
    RUN_LINK,
    /** the ScreeningCompleted of the second provider in the outbox */
    OUTBOX_INSERT,
    /** the write of the file and the completion of the run */
    RUN_COMPLETE,
    /** the batch repository, when the job is started */
    JOB_LAUNCH
  }

  /** What a caller can see of a run that failed on a database error. */
  record Seen(JsonNode answer, JsonNode listed, String stored, String runId, String trigger) {

    /** The error texts shown to a caller: the 202 answer (when it has one) and the read-back. */
    List<String> shown() {
      List<String> texts = new ArrayList<>();
      if (!answer.path("error").isMissingNode() && !answer.path("error").isNull()) {
        texts.add(answer.path("error").asText());
      }
      texts.add(listed.path("error").asText());
      return texts;
    }
  }

  void twoUnscreened() {
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "MD12345", "TX"), "U"),
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(P_555, "TESTPROV", "ECHO", "MD12345", "TX"), "U")));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
  }

  String inject(Failure failure) {
    switch (failure) {
      case SCREENING_INSERT -> {
        reject("reject_screening", "INSERT", "screening", "NEW.npi = '" + P_555 + "'");
        return "reject_screening";
      }
      case RUN_LINK -> {
        reject(
            "reject_link",
            "UPDATE",
            "screening",
            "NEW.run_id IS NOT NULL AND NEW.npi = '" + P_555 + "'");
        return "reject_link";
      }
      case OUTBOX_INSERT -> {
        reject("reject_outbox", "INSERT", "outbox_event", "NEW.subject = '" + P_555 + "'");
        return "reject_outbox";
      }
      case RUN_COMPLETE -> {
        reject("reject_complete", "UPDATE", "screening_run", "NEW.status = 'COMPLETED'");
        return "reject_complete";
      }
      default -> {
        reject("reject_job", "INSERT", "batch_job_instance", "true");
        return "reject_job";
      }
    }
  }

  /** Makes the failure, starts a run as ADMIN and reads the failed run back. */
  Seen failedOnDatabaseError(Failure failure) throws Exception {
    twoUnscreened();
    String trigger = inject(failure);
    JsonNode answer = startRun("ADMIN", DATE);
    String runId = answer.path("runId").asText();
    JsonNode listed = awaitRun(runId);
    assertThat(listed.path("status").asText())
        .as(failure + " must end the run FAILED, run: " + listed)
        .isEqualTo("FAILED");
    String stored =
        jdbc.queryForObject(
            "SELECT failure_reason FROM screening_run WHERE public_id = ?::uuid",
            String.class,
            runId);
    return new Seen(answer, listed, stored, runId, trigger);
  }

  // ---- AC-6, lesson a ---------------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: every error text a caller sees has no SQL statement text (D-024, lesson a)")
  @EnumSource(Failure.class)
  void shownErrorHasNoSqlText(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.shown())
        .allSatisfy(text -> assertThat(text.toLowerCase()).doesNotContain(SQL_WORDS));
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: every error text a caller sees is short, not blank and at most 200 characters (D-024, lesson a)")
  @EnumSource(Failure.class)
  void shownErrorIsShort(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.shown())
        .allSatisfy(text -> assertThat(text).isNotBlank().hasSizeLessThanOrEqualTo(200));
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the failure_reason stored with the run is short and has no SQL statement text (lesson a)")
  @EnumSource(Failure.class)
  void storedReasonIsShortAndHasNoSql(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.stored()).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(seen.stored().toLowerCase()).doesNotContain(SQL_WORDS);
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the full exception is logged at ERROR, with the message of the database itself (D-024, lesson a)")
  @EnumSource(Failure.class)
  void fullExceptionIsLogged(Failure failure) throws Exception {
    try (LogCapture log = LogCapture.open()) {
      failedOnDatabaseError(failure);
      assertThat(log.lines())
          .filteredOn(l -> l.level() == Level.ERROR)
          .anySatisfy(
              l -> assertThat(l.everything()).contains("the test database rejected this write"));
      if (failure != Failure.JOB_LAUNCH) {
        assertThat(log.lines())
            .filteredOn(l -> l.level() == Level.ERROR && l.hasThrowable())
            .as("an ERROR event with the exception itself, not only its text")
            .isNotEmpty();
      }
    }
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the run is never reported COMPLETED and has no results file")
  @EnumSource(Failure.class)
  void failedRunHasNoFile(Failure failure) throws Exception {
    Seen seen = failedOnDatabaseError(failure);
    assertThat(seen.listed().path("status").asText()).isEqualTo("FAILED");
    assertThat(seen.listed().path("endedAt").asText()).isNotBlank();
    assertProblem(get(RUNS + "/" + seen.runId() + "/results-file", token("ADMIN")), 404);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening_run WHERE public_id = ?::uuid"
                    + " AND (status = 'COMPLETED' OR results_file IS NOT NULL)",
                Integer.class,
                seen.runId()))
        .isZero();
  }

  // ---- AC-7, lesson c ---------------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-32 AC-7] a database error at {0}: nothing is left half done for the provider whose write failed (lesson c)")
  @EnumSource(
      value = Failure.class,
      names = {"SCREENING_INSERT", "RUN_LINK", "OUTBOX_INSERT"})
  void nothingIsLeftHalfDone(Failure failure) throws Exception {
    failedOnDatabaseError(failure);
    assertThat(screeningCount(P_555))
        .as("a screening kept although a write that belongs to it failed")
        .isZero();
    assertThat(completedEvents(P_555))
        .as("a ScreeningCompleted announced although the screening was not stored")
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND run_id IS NULL",
                Integer.class,
                P_555))
        .as("a screening without its run")
        .isZero();
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-7] a database error at {0}: after the failure the next run for the date is COMPLETED and nobody has two INITIAL screenings or two events (lesson c)")
  @EnumSource(Failure.class)
  void nextRunAfterTheDatabaseError(Failure failure) throws Exception {
    lift(failedOnDatabaseError(failure).trigger());
    JsonNode next = runToEnd(DATE);
    assertThat(next.path("status").asText()).as(next.toString()).isEqualTo("COMPLETED");
    for (String npi : List.of(P_111, P_555)) {
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM screening WHERE npi = ? AND reason = 'INITIAL'",
                  Integer.class,
                  npi))
          .as("INITIAL screenings of " + npi)
          .isEqualTo(1);
      assertThat(completedEvents(npi)).as("ScreeningCompleted events of " + npi).isEqualTo(1);
    }
  }
}
