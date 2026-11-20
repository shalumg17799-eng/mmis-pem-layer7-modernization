package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import gov.medicaid.pem.tmsis.Tmsisout;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-75 AC-1 to AC-3: an administrator starts an extract for a business date and gets 202 with a
 * BatchRun, the run is listed, reports its result and row count, and uses the business date given
 * and not the system date. The run is asynchronous (ADR-004), so every test waits for its end with
 * a time limit.
 */
@Timeout(120)
class ExtractRunApiTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  static void twoActive() {
    TestProviderService.holds(
        Prov.active("1003000333"),
        Prov.active("1003000111"),
        Prov.of("1003000444", "D"),
        Prov.of("1003000555", "P"));
  }

  // ---- AC-1: start and list ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the ADMIN starts a run: 202 with a BatchRun that has a runId, job PRVTMS01, businessDate 2026-03-31 and status RUNNING or COMPLETED")
  void startAnswersABatchRun() throws Exception {
    twoActive();
    MvcResult r = api.postBody(PemJwt.admin(), TmsisApi.body(DATE));
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode run = api.read(r);
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo("PRVTMS01");
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(run.path("status").asText()).isIn("RUNNING", "COMPLETED");
    assertThat(run.path("startedAt").asText()).isNotBlank();
    api.await(run.path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-75 AC-1] GET /api/v1/batch/tmsis-extracts then lists that runId")
  void listShowsTheRun() throws Exception {
    twoActive();
    String runId = api.start(DATE).path("runId").asText();
    api.await(runId);
    List<String> ids = new ArrayList<>();
    api.list(0, 20).path("items").forEach(i -> ids.add(i.path("runId").asText()));
    assertThat(ids).contains(runId);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the answer comes before a slow provider-service has answered: the run is RUNNING, has no end, no generation and no file")
  void runIsRunningWhileProviderServiceIsSlow() throws Exception {
    twoActive();
    TestProviderService.answerSlowlyBy(1500);
    JsonNode started = api.start(DATE);
    String runId = started.path("runId").asText();
    assertThat(started.path("status").asText()).isEqualTo("RUNNING");
    JsonNode listed = api.listed(runId);
    assertThat(listed.path("status").asText()).isEqualTo("RUNNING");
    assertThat(listed.has("endedAt")).isFalse();
    assertThat(listed.path("counts").has("archiveGeneration")).isFalse();
    assertThat(api.fileStatus(runId)).isEqualTo(404);
    JsonNode ended = api.await(runId);
    assertThat(ended.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(api.fileStatus(runId)).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the runs are listed newest first, with page, size and total, and a page beyond the last is empty")
  void listIsNewestFirstAndPaged() throws Exception {
    twoActive();
    List<String> started = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      TestClock.set("2026-01-15T10:0" + i + ":00Z");
      started.add(api.runToEnd(DATE).path("runId").asText());
    }
    JsonNode all = api.list(0, 20);
    assertThat(all.path("total").asInt()).isEqualTo(5);
    assertThat(all.path("page").asInt()).isZero();
    assertThat(all.path("size").asInt()).isEqualTo(20);
    List<String> listed = new ArrayList<>();
    all.path("items").forEach(i -> listed.add(i.path("runId").asText()));
    List<String> newestFirst = new ArrayList<>(started);
    Collections.reverse(newestFirst);
    assertThat(listed).containsExactlyElementsOf(newestFirst);

    JsonNode second = api.list(1, 2);
    assertThat(second.path("total").asInt()).isEqualTo(5);
    assertThat(second.path("page").asInt()).isEqualTo(1);
    assertThat(second.path("size").asInt()).isEqualTo(2);
    assertThat(second.path("items")).hasSize(2);
    assertThat(second.path("items").get(0).path("runId").asText()).isEqualTo(newestFirst.get(2));
    assertThat(api.list(9, 2).path("items")).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-1] the list without page and size shows the first page of 20 runs")
  void defaultPage() throws Exception {
    twoActive();
    api.runToEnd(DATE);
    MvcResult r = api.getPath(PemJwt.admin(), TmsisApi.BASE);
    JsonNode page = api.read(r);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(page.path("page").asInt()).isZero();
    assertThat(page.path("size").asInt()).isEqualTo(20);
    assertThat(page.path("items")).hasSize(1);
  }

  // ---- AC-2: result and row count ---------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-2] a run over 2 ACTIVE providers is COMPLETED with endedAt set and counts.rows 2, equal to the records of its file")
  void completedWithRowCount() throws Exception {
    twoActive();
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(2);
    assertThat(Tmsisout.records(api.file(run.path("runId").asText())))
        .hasSize(run.path("counts").path("rows").asInt());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-2] a run over no providers is COMPLETED with counts.rows 0 and an empty file")
  void zeroRows() throws Exception {
    TestProviderService.holds();
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("counts").path("rows").asInt()).isZero();
    assertThat(api.file(run.path("runId").asText())).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-2] the run started before it ended, and its times are the injected clock's")
  void timesAreTheClocks() throws Exception {
    twoActive();
    TestClock.set("2026-02-01T08:00:00Z");
    JsonNode run = api.runToEnd(DATE);
    Instant started = Instant.parse(run.path("startedAt").asText());
    Instant ended = Instant.parse(run.path("endedAt").asText());
    assertThat(started).isEqualTo(Instant.parse("2026-02-01T08:00:00Z"));
    assertThat(ended).isAfterOrEqualTo(started);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-2] the counts of a run are integers: rows, futureEnrolments and archiveGeneration")
  void countsAreIntegers() throws Exception {
    twoActive();
    JsonNode counts = api.runToEnd(DATE).path("counts");
    assertThat(counts.path("rows").isInt()).isTrue();
    assertThat(counts.path("futureEnrolments").isInt()).isTrue();
    assertThat(counts.path("archiveGeneration").isInt()).isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-2] the non-active providers are not counted: counts.rows is the number of records written, not the number read")
  void rowsAreRecordsWrittenNotProvidersRead() throws Exception {
    twoActive();
    TestProviderService.ignoreStatusFilter();
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(2);
  }

  // ---- AC-3: the business date, not the system date ---------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-3] with the system clock at 2026-05-10 a run for 2026-03-31 shows businessDate 2026-03-31 and every record holds 26090, not 26130")
  void businessDateNotSystemDate() throws Exception {
    twoActive();
    TestClock.set("2026-05-10T09:00:00Z");
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    List<String> records = Tmsisout.records(api.file(run.path("runId").asText()));
    assertThat(records).hasSize(2);
    assertThat(records)
        .allSatisfy(r -> assertThat(Tmsisout.period(r)).isEqualTo("26090").isNotEqualTo("26130"));
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-3] a business date in the future of the clock is used as it is (the run does not move it to today)")
  void futureBusinessDateIsKept() throws Exception {
    twoActive();
    JsonNode run = api.runToEnd("2026-12-31");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-12-31");
    assertThat(Tmsisout.period(Tmsisout.records(api.file(run.path("runId").asText())).get(0)))
        .isEqualTo("26365");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-3] two runs for different business dates on the same system day carry their own date and period")
  void eachRunHasItsOwnDate() throws Exception {
    twoActive();
    JsonNode a = api.runToEnd("2026-02-28");
    JsonNode b = api.runToEnd(DATE);
    assertThat(a.path("businessDate").asText()).isEqualTo("2026-02-28");
    assertThat(b.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(Tmsisout.period(Tmsisout.records(api.file(a.path("runId").asText())).get(0)))
        .isEqualTo("26059");
    assertThat(Tmsisout.period(Tmsisout.records(api.file(b.path("runId").asText())).get(0)))
        .isEqualTo("26090");
  }

  @Test
  @DisplayName("[MAM-75 AC-2] a run stays COMPLETED and unchanged when the list is read again")
  void completedRunIsStable() throws Exception {
    twoActive();
    JsonNode first = api.runToEnd(DATE);
    assertThat(api.listed(first.path("runId").asText())).isEqualTo(first);
    assertThat(api.listed(first.path("runId").asText())).isEqualTo(first);
  }
}
