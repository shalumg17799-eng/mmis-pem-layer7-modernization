package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.Tmsisout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-75 AC-4: every run's file is archived as a new generation (PRVMTH01 STEP030, IEBGENER to
 * MMPRV.TMSIS.ARCHIVE(+1)). Nothing is overwritten or deleted (Q-012); earlier files stay
 * downloadable byte for byte; a re-run for the same date gives the same bytes. The archive starts
 * empty before each test.
 */
@Timeout(180)
class ArchiveGenerationApiTest extends IntegrationTest {

  static void twoActive() {
    TestProviderService.holds(
        Prov.active("1003000333"), Prov.active("1003000111"), Prov.of("1003000444", "D"));
  }

  static int generation(JsonNode run) {
    return run.path("counts").path("archiveGeneration").asInt(-1);
  }

  // ---- the scenario of the AC -------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-4] from an empty archive the runs for 2026-02-28, 2026-03-31 and 2026-03-31 again show archiveGeneration 1, 2 and 3")
  void threeRunsThreeGenerations() throws Exception {
    twoActive();
    assertThat(generations()).isEmpty();
    JsonNode first = api.runToEnd("2026-02-28");
    JsonNode second = api.runToEnd("2026-03-31");
    JsonNode third = api.runToEnd("2026-03-31");
    assertThat(generation(first)).isEqualTo(1);
    assertThat(generation(second)).isEqualTo(2);
    assertThat(generation(third)).isEqualTo(3);
    assertThat(generations()).containsExactly(1, 2, 3);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] the list shows the archive generation of every run, as the run answered it")
  void listShowsTheGenerations() throws Exception {
    twoActive();
    JsonNode first = api.runToEnd("2026-02-28");
    JsonNode second = api.runToEnd("2026-03-31");
    assertThat(generation(api.listed(first.path("runId").asText()))).isEqualTo(1);
    assertThat(generation(api.listed(second.path("runId").asText()))).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] every earlier run's file can still be downloaded byte for byte unchanged after the later runs")
  void earlierFilesAreUnchanged() throws Exception {
    twoActive();
    JsonNode first = api.runToEnd("2026-02-28");
    byte[] firstFile = api.file(first.path("runId").asText());
    JsonNode second = api.runToEnd("2026-03-31");
    byte[] secondFile = api.file(second.path("runId").asText());
    JsonNode third = api.runToEnd("2026-03-31");
    api.runToEnd("2026-04-30");
    assertThat(api.file(first.path("runId").asText())).isEqualTo(firstFile);
    assertThat(api.file(second.path("runId").asText())).isEqualTo(secondFile);
    assertThat(api.file(third.path("runId").asText())).isNotEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] the third file (a second run for 2026-03-31 with no data change) is byte-identical to the second")
  void rerunGivesTheSameBytes() throws Exception {
    twoActive();
    api.runToEnd("2026-02-28");
    byte[] second = api.file(api.runToEnd("2026-03-31").path("runId").asText());
    byte[] third = api.file(api.runToEnd("2026-03-31").path("runId").asText());
    assertThat(third).isNotEmpty().isEqualTo(second);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] the files of different business dates differ in the period only: 26059 and 26090")
  void differentDatesDifferentPeriods() throws Exception {
    twoActive();
    byte[] first = api.file(api.runToEnd("2026-02-28").path("runId").asText());
    byte[] second = api.file(api.runToEnd("2026-03-31").path("runId").asText());
    assertThat(first).isNotEqualTo(second);
    assertThat(Tmsisout.npis(first)).isEqualTo(Tmsisout.npis(second));
    assertThat(Tmsisout.period(Tmsisout.records(first).get(0))).isEqualTo("26059");
    assertThat(Tmsisout.period(Tmsisout.records(second).get(0))).isEqualTo("26090");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] nothing is overwritten or deleted: each run adds one archive row and the earlier rows stay")
  void archiveOnlyGrows() throws Exception {
    twoActive();
    api.runToEnd("2026-02-28");
    List<String> contents =
        jdbc.queryForList("SELECT content FROM extract_archive ORDER BY generation", String.class);
    api.runToEnd("2026-03-31");
    api.runToEnd("2026-03-31");
    List<String> after =
        jdbc.queryForList("SELECT content FROM extract_archive ORDER BY generation", String.class);
    assertThat(after).hasSize(3);
    assertThat(after.get(0)).isEqualTo(contents.get(0));
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-4] a run with no providers is archived too, as an empty generation of its own")
  void emptyRunIsArchived() throws Exception {
    twoActive();
    api.runToEnd("2026-02-28");
    TestProviderService.holds();
    JsonNode empty = api.runToEnd("2026-03-31");
    assertThat(generation(empty)).isEqualTo(2);
    assertThat(api.file(empty.path("runId").asText())).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a failed run takes no archive generation (AC-4): the next completed run follows the last archived one without a gap")
  void failedRunTakesNoGeneration() throws Exception {
    twoActive();
    api.runToEnd("2026-02-28");
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    JsonNode failed = api.runToEnd("2026-03-31");
    assertThat(failed.path("status").asText()).isEqualTo("FAILED");
    assertThat(generations()).containsExactly(1);
    TestProviderService.providerRecovers();
    JsonNode next = api.runToEnd("2026-03-31");
    assertThat(generation(next)).isEqualTo(2);
    assertThat(generations()).containsExactly(1, 2);
  }

  // ---- parallel runs ----------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-4] five runs started at the same time get five different generations, 1 to 5, and each file can be downloaded")
  void parallelRunsGetDistinctGenerations() throws Exception {
    twoActive();
    ExecutorService pool = Executors.newFixedThreadPool(5);
    try {
      List<Future<String>> started = new ArrayList<>();
      for (int i = 0; i < 5; i++) {
        Callable<String> start = () -> api.start("2026-03-31").path("runId").asText();
        started.add(pool.submit(start));
      }
      List<Integer> found = new ArrayList<>();
      for (Future<String> f : started) {
        String runId = f.get(30, TimeUnit.SECONDS);
        JsonNode run = api.await(runId);
        assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
        found.add(generation(run));
        assertThat(api.file(runId)).hasSize(240);
      }
      assertThat(found).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
      assertThat(generations()).containsExactly(1, 2, 3, 4, 5);
    } finally {
      pool.shutdownNow();
    }
  }
}
