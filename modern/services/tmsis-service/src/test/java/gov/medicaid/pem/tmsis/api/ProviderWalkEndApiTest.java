package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TestProviderService.Request;
import gov.medicaid.pem.tmsis.Tmsisout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-71 AC-5 ("no provider is missed or repeated"), code review of tmsis-service 2026-11-21,
 * MUST-FIX 1: the walk over the provider data ended when the rows read had reached the total, so a
 * pair of changes during the run that keeps the total (one provider already read leaves ACTIVE, one
 * enters after the key) cost the file its last provider while the run was COMPLETED, archived and
 * announced. The cases here run the whole application (the real Spring Batch job, Postgres, Kafka)
 * against the fake provider-service of contract 1.2.0, whose data changes between two answers.
 *
 * <p>The reviewer's case: 400 ACTIVE providers with every second NPI (1003002002 to 1003002800);
 * after the first answer 1003002010 (already read) is terminated and 1003002501 approved. The run
 * must be COMPLETED with every provider that stayed ACTIVE once, 1003002800 included, in 3
 * requests. And an exact multiple of 200 providers ends with the empty answer, one request more
 * than the count of full answers (contract 1.2.0: the walk ends at the first answer whose items
 * list is empty).
 *
 * <p>Nothing here can hang the build: a run is awaited for 25 seconds at most and the class is
 * limited by JUnit.
 */
@Timeout(300)
class ProviderWalkEndApiTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  /**
   * 400 ACTIVE providers with every second NPI, 1003002002 to 1003002800, and PENDING 1003002501.
   */
  static List<Prov> reviewersProviders() {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 400; i++) {
      all.add(Prov.active(String.format("1003%06d", 2000 + 2 * i)));
    }
    all.add(Prov.of("1003002501", "P"));
    return all;
  }

  /** The data of the reviewer's case, and its change after the first answer. */
  void reviewersCase() {
    TestProviderService.holds(reviewersProviders());
    TestProviderService.afterSearch(
        1,
        () -> {
          TestProviderService.changeStatus("1003002010", "T");
          TestProviderService.changeStatus("1003002501", "A");
        });
  }

  /** The NPIs the run of the reviewer's case must report: the 400 that started ACTIVE and 501. */
  static List<String> expectedNpis() {
    List<String> expected = new ArrayList<>();
    for (int i = 1; i <= 400; i++) {
      expected.add(String.format("1003%06d", 2000 + 2 * i));
    }
    expected.add("1003002501");
    Collections.sort(expected);
    return expected;
  }

  JsonNode completedRun() throws Exception {
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).as("the run: %s", run).isEqualTo("COMPLETED");
    return run;
  }

  // ---- the reviewer's case
  // ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] one provider leaves and one enters after the first answer (the total stays 400): the run is COMPLETED and the file holds every provider once, 1003002800 and 1003002501 included, ascending")
  void fileHoldsEveryProviderOnce() throws Exception {
    reviewersCase();
    JsonNode run = completedRun();
    assertThat(Tmsisout.npis(api.file(run.path("runId").asText())))
        .containsExactlyElementsOf(expectedNpis());
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the run of that case counts 401 rows, as many as its file, and archives them as generation 1")
  void runCountsTheRowsOfTheFile() throws Exception {
    reviewersCase();
    JsonNode run = completedRun();
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(401);
    assertThat(Tmsisout.records(api.file(run.path("runId").asText()))).hasSize(401);
    assertThat(generations()).containsExactly(1);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the ExtractProduced event of that case says 401 rows: the file, the run and the event agree")
  void eventCountsTheRowsOfTheFile() throws Exception {
    reviewersCase();
    completedRun();
    List<String> events = extractProducedEnvelopes();
    assertThat(events).hasSize(1);
    assertThat(json.readTree(events.get(0)).path("data").path("rowCount").asInt()).isEqualTo(401);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the run of that case reads in 3 requests of size 200: no key, after 1003002400, after 1003002798 (the last, short answer holds 1003002800)")
  void searchesOfTheReviewersCase() throws Exception {
    reviewersCase();
    completedRun();
    List<Request> searches = TestProviderService.searches();
    assertThat(searches)
        .extracting(r -> r.query().get("afterNpi"))
        .containsExactly(null, "1003002400", "1003002798");
    assertThat(searches).allSatisfy(r -> assertThat(r.query()).containsEntry("size", "200"));
  }

  // ---- an exact multiple of the answer size
  // ---------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-71 AC-5] {0} ACTIVE providers (an exact multiple of 200) are read in {0}/200 + 1"
              + " requests: the last one, after the last NPI, gets the empty answer that ends the walk")
  @ValueSource(ints = {200, 400})
  void exactMultipleEndsWithTheEmptyAnswer(int count) throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      all.add(Prov.active(String.format("1003%06d", 1000 + i)));
    }
    TestProviderService.holds(all);
    JsonNode run = completedRun();
    assertThat(Tmsisout.npis(api.file(run.path("runId").asText())))
        .hasSize(count)
        .doesNotHaveDuplicates()
        .isSorted();
    List<Request> searches = TestProviderService.searches();
    assertThat(searches).hasSize(count / 200 + 1);
    assertThat(searches.get(searches.size() - 1).query())
        .containsEntry("afterNpi", String.format("1003%06d", 1000 + count));
  }
}
