package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.IntegrationTest;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.testsupport.PemJwt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-55 with the synthetic SANCIN datasets of MAM-44 (modern/testdata): the files as the legacy
 * layout writes them, run through the endpoint with provider-service stubbed. The baseline file has
 * records that apply, an unknown NPI and an unknown action; the malformed file has an impossible
 * date (rejected, the run goes on) and a record of 79 bytes (the run fails and names it).
 */
class SanctionIntakeDatasetsTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
  }

  static byte[] dataset(String name) throws IOException {
    Path rel = Path.of("testdata", name);
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isRegularFile(c)) {
          return Files.readAllBytes(c);
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("synthetic dataset not found: " + rel);
  }

  JsonNode history(String npi) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/sanctions").param("npi", npi).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- sancin-baseline.dat ---------------------------------------------------------------------

  void baselineProviders() {
    TestProviderService.holds("1005943141", "A");
    TestProviderService.holds("1005992312", "A");
    TestProviderService.holds("1005919109", "S");
    TestProviderService.unknown("1006143592");
    TestProviderService.holds("1005912765", "A");
    TestProviderService.holds("1005933068", "P");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] the baseline file: read 6, applied 4, notFound 1, rejected 1, terminated 1, COMPLETED")
  void baselineCounts() throws Exception {
    baselineProviders();
    JsonNode run =
        IntakeApi.runToEnd(mvc, json, dataset("fixed-width/sancin-baseline.dat"), "2026-01-15");
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    JsonNode counts = run.path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(6);
    assertThat(counts.path("applied").asInt()).isEqualTo(4);
    assertThat(counts.path("notFound").asInt()).isEqualTo(1);
    assertThat(counts.path("rejected").asInt()).isEqualTo(1);
    assertThat(counts.path("skipped").asInt()).isZero();
    assertThat(counts.path("terminated").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-2] the baseline file: reason from bytes 12-51 and the date 20260115 as 2026-01-15")
  void baselineFields() throws Exception {
    baselineProviders();
    IntakeApi.runToEnd(mvc, json, dataset("fixed-width/sancin-baseline.dat"), "2026-01-15");
    JsonNode terminated = history("1005943141").path("items").get(0);
    assertThat(terminated.path("reason").asText())
        .isEqualTo("SAMPLE TERMINATION: PROGRAM VIOLATION");
    assertThat(terminated.path("effectiveDate").asText()).isEqualTo("2026-01-15");
    assertThat(terminated.path("action").asText()).isEqualTo("T");
    assertThat(terminated.path("sourceFeed").asText()).isEqualTo("SANCIN");
    JsonNode suspended = history("1005992312").path("items").get(0);
    assertThat(suspended.path("reason").asText()).isEqualTo("SAMPLE SUSPENSION: PENDING REVIEW");
    assertThat(suspended.path("resultingStatus").asText()).isEqualTo("S");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] the baseline file: a reinstatement of a pending provider is applied whatever its status (D-027)")
  void baselineReinstatesAPendingProvider() throws Exception {
    baselineProviders();
    IntakeApi.runToEnd(mvc, json, dataset("fixed-width/sancin-baseline.dat"), "2026-01-15");
    JsonNode reinstated = history("1005933068").path("items").get(0);
    assertThat(reinstated.path("action").asText()).isEqualTo("R");
    assertThat(reinstated.path("resultingStatus").asText()).isEqualTo("A");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] the baseline file: nothing is kept for the unknown NPI or the action X")
  void baselineKeepsNothingForSkippedRecords() throws Exception {
    baselineProviders();
    IntakeApi.runToEnd(mvc, json, dataset("fixed-width/sancin-baseline.dat"), "2026-01-15");
    assertThat(history("1006143592").path("total").asInt()).isZero();
    assertThat(history("1005912765").path("total").asInt()).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-7] the baseline file again for the same business date applies nothing")
  void baselineTwice() throws Exception {
    baselineProviders();
    IntakeApi.runToEnd(mvc, json, dataset("fixed-width/sancin-baseline.dat"), "2026-01-15");
    JsonNode again =
        IntakeApi.runToEnd(mvc, json, dataset("fixed-width/sancin-baseline.dat"), "2026-01-15");
    JsonNode counts = again.path("counts");
    assertThat(again.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(counts.path("read").asInt()).isEqualTo(6);
    assertThat(counts.path("applied").asInt()).isZero();
    assertThat(counts.path("skipped").asInt()).isEqualTo(4);
    assertThat(counts.path("notFound").asInt()).isEqualTo(1);
    assertThat(counts.path("rejected").asInt()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sanction", Integer.class)).isEqualTo(4);
  }

  // ---- sancin-malformed.dat --------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the malformed file: the impossible date of record 2 is rejected, the 79-byte record 3 fails the run")
  void malformed() throws Exception {
    TestProviderService.holds("1005943141", "A");
    TestProviderService.holds("1005992312", "A");
    TestProviderService.holds("1005919109", "S");
    MvcResult started =
        IntakeApi.start(
            mvc, PemJwt.admin(), dataset("negative/sancin-malformed.dat"), "2026-01-15");
    assertThat(started.getResponse().getStatus()).isEqualTo(202);
    String runId = json.readTree(started.getResponse().getContentAsString()).path("runId").asText();
    JsonNode run = IntakeApi.await(mvc, json, runId);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("endedAt").asText()).isNotBlank();
    assertThat(run.path("error").asText()).contains("record 3").contains("79 bytes");
    JsonNode counts = run.path("counts");
    assertThat(counts.path("applied").asInt()).isEqualTo(1);
    assertThat(counts.path("rejected").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the malformed file: record 1 keeps its result and record 4 is never processed")
  void malformedKeepsRecordOneOnly() throws Exception {
    TestProviderService.holds("1005943141", "A");
    TestProviderService.holds("1005992312", "A");
    TestProviderService.holds("1005919109", "S");
    IntakeApi.runToEnd(mvc, json, dataset("negative/sancin-malformed.dat"), "2026-01-15");
    // record 1 and record 4 are both T for 1005943141: only record 1 was applied
    assertThat(history("1005943141").path("total").asInt()).isEqualTo(1);
    assertThat(history("1005992312").path("total").asInt()).isZero();
    assertThat(history("1005919109").path("total").asInt()).isZero();
  }
}
