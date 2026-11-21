package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.ContractFile;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.Tmsisout;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-71 AC-5 ("no provider is missed or repeated") and MAM-75 AC-6 (a failed run is visible and
 * leaves nothing behind), code review of tmsis-service 2026-11-20, MUST-FIX 1, user decision Q-132:
 * the ACTIVE providers of a run are read from provider-service by key (contract 1.2.0) and the run
 * does not trust data that changed while it was reading. The fake provider-service of the tests
 * answers as the contract says and its provider list changes between two answers (an analyst
 * terminates or approves a provider at month end), as the reviewer described it:
 *
 * <ul>
 *   <li>401 ACTIVE providers, the provider #5 terminated after the first page: with offset paging
 *       the second page starts at the old #202, so #201 is missing, the total 400 ends the read and
 *       the run is COMPLETED, archived and announced with a wrong file;
 *   <li>a provider approved before the boundary: the boundary provider is written twice;
 * </ul>
 *
 * By key neither can happen. The safety net ends the run FAILED when the total of the answers
 * differs (a provider entered or left ACTIVE during the run: the safer default of the user's order,
 * the file would mix two moments) or when the NPIs are not strictly ascending, with a short reason,
 * no file, no archive generation and no ExtractProduced event (D-024). One entering and one leaving
 * provider that keep the total are not seen as a change, and by key they do no harm.
 *
 * <p>Nothing here can hang the build: a run is awaited for 25 seconds at most and the class is
 * limited by JUnit.
 */
@Timeout(300)
class ProviderDataChangeApiTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  /** The reason of a run whose provider data changed while it read (the user's order, Q-132). */
  static final String CHANGED = "provider data changed during the extract; run it again";

  /** The NPI of the i-th provider (1-based): 1003001001, 1003001002, ... */
  static String npi(int i) {
    return String.format("1003%06d", 1000 + i);
  }

  static List<Prov> active(int count) {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      all.add(Prov.active(npi(i)));
    }
    return all;
  }

  /** The run ended FAILED: the BatchRun as listed; fails the test if it is not FAILED. */
  JsonNode failedRun() throws Exception {
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText())
        .as(
            "the run must be FAILED, but it is %s with %s rows and generation %s",
            run.path("status").asText(),
            run.path("counts").path("rows"),
            run.path("counts").path("archiveGeneration"))
        .isEqualTo("FAILED");
    return run;
  }

  void assertNothingLeftBehind(String runId) throws Exception {
    assertThat(api.fileStatus(runId)).as("file of the failed run").isEqualTo(404);
    assertThat(generations()).as("archive generations").isEmpty();
    assertThat(extractProducedEnvelopes()).as("ExtractProduced events").isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM extract_run WHERE status = 'COMPLETED'", Integer.class))
        .isZero();
  }

  void assertShortClearReason(JsonNode run) {
    String reason = run.path("error").asText();
    assertThat(reason).isNotBlank().contains(" ").doesNotContain("Exception").doesNotContain("\n");
    assertThat(reason.length()).isLessThanOrEqualTo(200);
  }

  // ---- the reviewer's cases: the data changes between page 0 and page 1 -------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 401 ACTIVE providers, #5 is terminated after the first page: the run is FAILED with 'provider data changed during the extract; run it again', not COMPLETED with 400 rows and no #201")
  void terminatedAfterTheFirstPage() throws Exception {
    TestProviderService.holds(active(401));
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus(npi(5), "T"));
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isEqualTo(CHANGED);
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("counts").path("rows").asInt()).isZero();
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the job execution of the run is FAILED too (ADR-004), its extract step is FAILED and its archive step never ran")
  void jobExecutionIsFailed() throws Exception {
    TestProviderService.holds(active(401));
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus(npi(5), "T"));
    String runId = failedRun().path("runId").asText();
    // the run is FAILED when the job listener writes it, a moment before the job repository records
    // the end
    Awaitility.await("job execution of run " + runId + " has ended")
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> !List.of("STARTING", "STARTED").contains(jobStatus(runId)));
    assertThat(jobStatus(runId)).isEqualTo("FAILED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM batch_step_execution s JOIN batch_job_execution_params p ON"
                    + " p.job_execution_id = s.job_execution_id WHERE p.parameter_name = 'runId'"
                    + " AND p.parameter_value = ? AND s.step_name = 'archive'",
                Integer.class,
                runId))
        .isZero();
  }

  String jobStatus(String runId) {
    return jdbc.queryForObject(
        "SELECT e.status FROM batch_job_execution e JOIN batch_job_execution_params p ON"
            + " p.job_execution_id = e.job_execution_id WHERE p.parameter_name = 'runId' AND"
            + " p.parameter_value = ?",
        String.class,
        runId);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider is approved before the boundary after the first page (400 ACTIVE and one PENDING between): the run is FAILED with the reason of a changed total, the boundary provider is not written twice")
  void approvedBeforeTheBoundary() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 400; i++) {
      all.add(Prov.active(String.format("1003%06d", 2000 + 2 * i)));
    }
    all.add(Prov.of("1003002101", "P"));
    TestProviderService.holds(all);
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus("1003002101", "A"));
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isEqualTo(CHANGED);
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider is approved after the boundary after the first page: the total is 402, not 401, so the run is FAILED with the reason of a changed total (the safer default)")
  void approvedAfterTheBoundary() throws Exception {
    List<Prov> all = active(401);
    all.add(Prov.of("1003009999", "P"));
    TestProviderService.holds(all);
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus("1003009999", "A"));
    JsonNode run = failedRun();
    assertThat(run.path("error").asText()).isEqualTo(CHANGED);
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] one provider is terminated before the boundary and one approved after it (the total stays 401): by key the run is COMPLETED with every provider once, #201 included, ascending")
  void pairedChangeIsReadRight() throws Exception {
    List<Prov> all = active(401);
    all.add(Prov.of("1003009999", "P"));
    TestProviderService.holds(all);
    TestProviderService.afterSearch(
        1,
        () -> {
          TestProviderService.changeStatus(npi(5), "T");
          TestProviderService.changeStatus("1003009999", "A");
        });
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("COMPLETED");
    List<String> expected = new ArrayList<>();
    for (int i = 1; i <= 401; i++) {
      expected.add(npi(i)); // #5 was read before it was terminated
    }
    expected.add("1003009999");
    assertThat(Tmsisout.npis(api.file(run.path("runId").asText())))
        .containsExactlyElementsOf(expected);
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(402);
    assertThat(generations()).containsExactly(1);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] after a run failed because the data changed, the same run again works: COMPLETED, 400 records, generation 1")
  void theRunCanBeRepeated() throws Exception {
    TestProviderService.holds(active(401));
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus(npi(5), "T"));
    failedRun();
    JsonNode again = api.runToEnd(DATE);
    assertThat(again.path("status").asText())
        .as("the second run: %s", again)
        .isEqualTo("COMPLETED");
    List<String> npis = Tmsisout.npis(api.file(again.path("runId").asText()));
    assertThat(npis).hasSize(400).doesNotContain(npi(5)).doesNotHaveDuplicates().isSorted();
    assertThat(generations()).containsExactly(1);
  }

  // ---- a provider-service that breaks its contract ----------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider-service older than 1.2.0 ignores afterNpi (the first page again): the run is FAILED with a short reason after the second answer, it does not loop and does not write 400 rows twice")
  void providerIgnoresTheKey() throws Exception {
    TestProviderService.holds(active(450));
    TestProviderService.answerIgnoringTheKey();
    JsonNode run = failedRun();
    assertShortClearReason(run);
    assertThat(TestProviderService.searchCount()).isLessThanOrEqualTo(2);
    assertNothingLeftBehind(run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider-service that lists in descending NPI order (a breach of its contract) gives a FAILED run with a short reason, not a file")
  void providerAnswersDescending() throws Exception {
    TestProviderService.holds(Prov.active("1003000333"), Prov.active("1003000111"));
    TestProviderService.answerInDescendingOrder();
    JsonNode run = failedRun();
    assertShortClearReason(run);
    assertNothingLeftBehind(run.path("runId").asText());
  }
}
