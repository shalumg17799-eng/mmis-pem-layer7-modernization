package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.ContractFile;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.IntegrationTest;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.testsupport.PemJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-55 contract tests against modern/contracts/openapi/sanctions-service.yaml (1.0.1): the
 * answers of startSanctionIntakeRun and getSanctionIntakeRun are the contract's BatchRun and
 * Problem, and the operations accept and answer what the contract says (multipart request, the
 * documented status codes, application/json and application/problem+json).
 */
class SanctionIntakeContractTest extends IntegrationTest {

  static final String REASON = "SYNTHETIC TEST REASON";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
    TestProviderService.holds("1003000111", "A");
    TestProviderService.holds("1003000333", "A");
  }

  byte[] file() {
    return SancinFile.lines(
        SancinFile.record("1003000111", "T", REASON, "20260331"),
        SancinFile.record("1003000333", "S", REASON, "20260331"));
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- the operations as the contract describes them ------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the contract's request is multipart/form-data with the required file and businessDate")
  void requestShape() {
    assertThat(ContractFile.requestMediaTypes("startSanctionIntakeRun"))
        .containsExactly("multipart/form-data");
    assertThat(ContractFile.required("FileRunRequest"))
        .containsExactlyInAnyOrder("file", "businessDate");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the contract documents 202, 400, 401, 403 and 422 for the start and 200, 401, 403 and 404 for the read")
  void documentedStatuses() {
    assertThat(ContractFile.statuses("startSanctionIntakeRun"))
        .containsExactlyInAnyOrder("202", "400", "401", "403", "422");
    assertThat(ContractFile.statuses("getSanctionIntakeRun"))
        .containsExactlyInAnyOrder("200", "401", "403", "404");
  }

  // ---- BatchRun --------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-1] the 202 of startSanctionIntakeRun is a BatchRun of the contract")
  void startAnswer() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), file(), "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(ContractFile.mediaTypes("startSanctionIntakeRun", 202))
        .containsExactly("application/json");
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    assertThat(ContractFile.violations("BatchRun", body(r))).isEmpty();
    IntakeApi.await(mvc, json, body(r).path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] the 200 of getSanctionIntakeRun for a COMPLETED run is a BatchRun of the contract")
  void completedRun() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, file(), "2026-03-31");
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(ContractFile.mediaTypes("getSanctionIntakeRun", 200))
        .containsExactly("application/json");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the 200 of getSanctionIntakeRun for a FAILED run is a BatchRun of the contract, with its error")
  void failedRun() throws Exception {
    byte[] broken =
        SancinFile.lines(
            SancinFile.record("1003000111", "T", REASON, "20260331"),
            SancinFile.cut(SancinFile.record("1003000333", "S", REASON, "20260331"), 60));
    JsonNode run = IntakeApi.runToEnd(mvc, json, broken, "2026-03-31");
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("error").isTextual()).isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] the contract check itself rejects a status other than RUNNING, COMPLETED or FAILED")
  void statusValues() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, file(), "2026-03-31");
    assertThat(run.path("status").asText()).isIn("RUNNING", "COMPLETED", "FAILED");
    JsonNode bad = json.readTree(run.toString().replace("COMPLETED", "DONE"));
    assertThat(ContractFile.violations("BatchRun", bad)).isNotEmpty();
  }

  // ---- Problem ---------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-1] the 400 is a Problem of the contract, as application/problem+json")
  void badRequest() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), file(), null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(ContractFile.mediaTypes("startSanctionIntakeRun", 400))
        .containsExactly("application/problem+json");
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] the 403 of the start is a Problem of the contract")
  void forbiddenStart() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.analyst(), file(), "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(ContractFile.mediaTypes("startSanctionIntakeRun", 403))
        .containsExactly("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] the 403 of the read is a Problem of the contract")
  void forbiddenRead() throws Exception {
    MvcResult r = IntakeApi.read(mvc, "00000000-0000-0000-0000-000000000000", PemJwt.system());
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(ContractFile.mediaTypes("getSanctionIntakeRun", 403))
        .containsExactly("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] the 404 of the read is a Problem of the contract")
  void notFound() throws Exception {
    MvcResult r = IntakeApi.read(mvc, "00000000-0000-0000-0000-000000000000", PemJwt.admin());
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(ContractFile.mediaTypes("getSanctionIntakeRun", 404))
        .containsExactly("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] a 401 is documented as application/problem+json for both operations")
  void unauthorizedIsDocumented() {
    assertThat(ContractFile.mediaTypes("startSanctionIntakeRun", 401))
        .containsExactly("application/problem+json");
    assertThat(ContractFile.mediaTypes("getSanctionIntakeRun", 401))
        .containsExactly("application/problem+json");
  }
}
