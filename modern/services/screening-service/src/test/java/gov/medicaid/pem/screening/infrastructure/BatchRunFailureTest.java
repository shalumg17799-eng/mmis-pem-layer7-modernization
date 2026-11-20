package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.Fault;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * MAM-32 AC-6 and AC-7: a failure is reported and never silent (D-024), a run never reads for ever,
 * and running again does not screen anyone twice (ADR-004). Also the lessons of the earlier
 * reviews: a failing run on a database error shows a short reason without SQL text (a), the write
 * that marks work as done is in the transaction of the work (c), and a provider-service that does
 * not answer ends the run FAILED within its limit (d).
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class BatchRunFailureTest extends Lane3Test {

  static final String DATE = "2026-03-31";
  static final String P_111 = "1003000111";
  static final String P_555 = "1003000555";
  static final List<String> SQL_WORDS = List.of("select", "insert", "update", "sql");

  void twoUnscreened() {
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "MD12345", "TX"), "U"),
            ScreeningWorld.withFlag(
                ScreeningWorld.individual(P_555, "TESTPROV", "ECHO", "MD12345", "TX"), "U")));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
  }

  JsonNode failedRun() throws Exception {
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("FAILED");
    return run;
  }

  // ---- AC-6: provider-service ------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] provider-service answers HTTP {0} on the list: the run is FAILED and reads no further")
  @CsvSource({"500", "502", "503", "504", "400", "401", "403", "404"})
  void listError(int status) throws Exception {
    ScreeningWorld.listAnswers(status);
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(ScreeningWorld.listedPages()).hasSizeLessThanOrEqualTo(3);
    assertThat(screeningCount(P_111)).isZero();
  }

  @ParameterizedTest(
      name = "[MAM-32 AC-6] provider-service ends the list call with a {0}: the run is FAILED")
  @EnumSource(
      value = Fault.class,
      names = {"CONNECTION_RESET_BY_PEER", "EMPTY_RESPONSE"})
  void listFault(Fault fault) throws Exception {
    ScreeningWorld.listFault(fault);
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] provider-service answers the list with something that is no provider page: the run is FAILED")
  void listWithoutItems() throws Exception {
    ScreeningWorld.listBody("{\"hello\":1}");
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] provider-service fails on the second page: FAILED, the first page was read, the second asked once, nothing retried for ever")
  void secondPageFails() throws Exception {
    List<ObjectNode> providers = new ArrayList<>();
    for (int i = 1; i <= 250; i++) {
      providers.add(
          ScreeningWorld.withFlag(
              ScreeningWorld.individual(
                  String.format("1004%06d", i), "TESTPROV", "N" + i, "LIC" + i, "TX"),
              "P"));
    }
    ScreeningWorld.lists(providers);
    ScreeningWorld.listPageAnswers(1, 503);
    JsonNode run = failedRun();
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(200);
    assertThat(ScreeningWorld.listedPages()).containsExactly("0", "1");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] provider-service fails on a provider of the run: FAILED, never COMPLETED, and the run stops there")
  void providerReadFails() throws Exception {
    twoUnscreened();
    ScreeningWorld.providerAnswers(P_555, 503);
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(screeningCount(P_111))
        .as("the first provider was screened before the failure")
        .isEqualTo(1);
    assertThat(screeningCount(P_555)).isZero();
  }

  // ---- lesson d: provider-service does not answer -----------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-6] provider-service does not answer the list within its limit: the run is FAILED within the limit, not RUNNING for ever (lesson d)")
  @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void listHangs() throws Exception {
    ScreeningWorld.listSlow(30_000);
    long start = System.nanoTime();
    JsonNode run = failedRun();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(25));
    assertThat(run.path("error").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] provider-service does not answer for a provider of the run within its limit: the run is FAILED within the limit (lesson d)")
  @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void providerHangs() throws Exception {
    twoUnscreened();
    ScreeningWorld.providerSlow(
        ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "MD12345", "TX"), 30_000);
    long start = System.nanoTime();
    JsonNode run = failedRun();
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(25));
    assertThat(run.path("error").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a source that does not answer does not fail the run: its provider is screened with C and the run is COMPLETED")
  void sourceHangDoesNotFailTheRun() throws Exception {
    twoUnscreened();
    ScreeningWorld.sourceSlow("SAMGOV", P_111, 3500);
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("failed").asInt()).isEqualTo(1);
  }

  // ---- AC-6 and lesson a: a database error ------------------------------------------------

  /** The writes that fail. */
  enum Failure {
    /** the insert of the screening of the second provider */
    SCREENING_INSERT,
    /** the link of the screening of the second provider to the run */
    RUN_LINK,
    /** the write of the file and the completion of the run */
    RUN_COMPLETE
  }

  void inject(Failure failure) {
    switch (failure) {
      case SCREENING_INSERT ->
          reject("reject_screening", "INSERT", "screening", "NEW.npi = '" + P_555 + "'");
      case RUN_LINK ->
          reject(
              "reject_link",
              "UPDATE",
              "screening",
              "NEW.run_id IS NOT NULL AND NEW.npi = '" + P_555 + "'");
      case RUN_COMPLETE ->
          reject("reject_complete", "UPDATE", "screening_run", "NEW.status = 'COMPLETED'");
    }
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the run is FAILED, with a reason, and never COMPLETED")
  @EnumSource(Failure.class)
  void databaseErrorFailsTheRun(Failure failure) throws Exception {
    twoUnscreened();
    inject(failure);
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isNotBlank();
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertProblem(
        get(RUNS + "/" + run.path("runId").asText() + "/results-file", token("ADMIN")), 404);
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the error of the run has no SQL statement text (lesson a, decision D-024)")
  @EnumSource(Failure.class)
  void errorHasNoSqlText(Failure failure) throws Exception {
    twoUnscreened();
    inject(failure);
    JsonNode run = failedRun();
    assertThat(run.path("error").asText().toLowerCase())
        .doesNotContain(SQL_WORDS.toArray(new String[0]));
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the error of the run is a short reason of at most 200 characters (lesson a, decision D-024)")
  @EnumSource(Failure.class)
  void errorIsShort(Failure failure) throws Exception {
    twoUnscreened();
    inject(failure);
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isNotBlank().hasSizeLessThanOrEqualTo(200);
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error at {0}: the failure_reason stored with the run is short and has no SQL text (lesson a)")
  @EnumSource(Failure.class)
  void storedReasonIsShortWithoutSql(Failure failure) throws Exception {
    twoUnscreened();
    inject(failure);
    JsonNode run = failedRun();
    String stored =
        jdbc.queryForObject(
            "SELECT failure_reason FROM screening_run WHERE public_id = ?::uuid",
            String.class,
            run.path("runId").asText());
    assertThat(stored).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(stored.toLowerCase()).doesNotContain(SQL_WORDS.toArray(new String[0]));
  }

  // ---- AC-7 and lesson c: nobody is screened twice ----------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-7] a second run for the same date screens nobody again and writes an empty file")
  void secondRunScreensNobody() throws Exception {
    twoUnscreened();
    JsonNode first = runToEnd(DATE);
    JsonNode second = runToEnd(DATE);
    assertThat(second.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(second.path("counts").path("screened").asInt()).isZero();
    assertThat(second.path("counts").path("skipped").asInt()).isEqualTo(2);
    assertThat(screeningCount(P_111)).isEqualTo(1);
    assertThat(screeningCount(P_555)).isEqualTo(1);
    assertThat(completedEvents(P_111)).isEqualTo(1);
    assertThat(completedEvents(P_555)).isEqualTo(1);
    assertThat(resultsFile(second.path("runId").asText())).isEmpty();
    assertThat(resultsFile(first.path("runId").asText())).hasSize(8 * 67);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] an INITIAL screening made by the registration event also stops the run: the provider is not screened again")
  void initialFromTheEventCounts() throws Exception {
    twoUnscreened();
    awaitProcessed(providerRegistered(P_111));
    JsonNode run = runToEnd(DATE);
    assertThat(screeningCount(P_111)).isEqualTo(1);
    assertThat(screeningCount(P_555)).isEqualTo(1);
    assertThat(run.path("counts").path("screened").asInt()).isEqualTo(1);
    assertThat(completedEvents(P_111)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] a MANUAL or REVALIDATION screening is no INITIAL screening: the run still screens the provider")
  void otherReasonsDoNotStopTheRun() throws Exception {
    twoUnscreened();
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
    screen(P_111);
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("counts").path("screened").asInt()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening WHERE npi = ? AND reason = 'INITIAL'",
                Integer.class,
                P_111))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] after a failure between the screening and its link to the run, a second run does not screen that provider twice (lesson c)")
  void noDoubleScreeningAfterALinkFailure() throws Exception {
    twoUnscreened();
    inject(Failure.RUN_LINK);
    failedRun();
    lift("reject_link");
    runToEnd(DATE);
    assertThat(screeningCount(P_555))
        .as("screenings of the provider whose link failed")
        .isEqualTo(1);
    assertThat(completedEvents(P_555)).isEqualTo(1);
    assertThat(screeningCount(P_111)).isEqualTo(1);
    assertThat(completedEvents(P_111)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] the screening and its link to the run are written in one transaction: when the link fails, no screening is kept without its run (lesson c)")
  void screeningAndLinkAreOneTransaction() throws Exception {
    twoUnscreened();
    inject(Failure.RUN_LINK);
    failedRun();
    assertThat(screeningCount(P_555))
        .as("a screening kept although its link to the run could not be written")
        .isZero();
    assertThat(completedEvents(P_555))
        .as("a ScreeningCompleted announced although the screening is not linked to its run")
        .isZero();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-7] after a failed run the next run for the date screens the provider that was not reached, once")
  void rerunAfterAFailedRun() throws Exception {
    twoUnscreened();
    inject(Failure.SCREENING_INSERT);
    failedRun();
    lift("reject_screening");
    JsonNode again = runToEnd(DATE);
    assertThat(again.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(screeningCount(P_111)).isEqualTo(1);
    assertThat(screeningCount(P_555)).isEqualTo(1);
    assertThat(completedEvents(P_555)).isEqualTo(1);
    assertThat(again.path("counts").path("screened").asInt()).isEqualTo(1);
  }
}
