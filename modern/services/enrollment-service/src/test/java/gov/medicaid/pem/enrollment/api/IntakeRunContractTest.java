package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.ContractFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contract tests of the three intake endpoints against modern/contracts/openapi/
 * enrollment-service.yaml: the BatchRun answers (202, 200 COMPLETED, 200 FAILED), the Problem
 * answers (400, 401, 403, 404), the media types, the documented status codes and the request
 * schema.
 */
class IntakeRunContractTest extends IntakeIntegrationTest {

  static final String UNKNOWN = "00000000-0000-0000-0000-000000000000";

  byte[] valid() {
    return AppInputFile.lines(AppInputFile.valid("1003000111"), AppInputFile.valid("1003000222"));
  }

  byte[] cutShort() {
    return AppInputFile.lines(
        AppInputFile.valid("1003000111"),
        AppInputFile.cut(AppInputFile.valid("1003000222"), 150),
        AppInputFile.valid("1003000333"));
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- BatchRun -------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-1] the 202 answer of startIntakeRun is a BatchRun of the contract")
  void startAnswerIsABatchRun() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), valid(), DATE, "B0331001");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    JsonNode run = body(r);
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    IntakeApi.await(mvc, json, run.path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-57 AC-1] the 200 answer of getIntakeRun for a COMPLETED run is a BatchRun with the four counts")
  void completedRunIsABatchRun() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, valid(), DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("counts").fieldNames()).toIterable()
        .containsExactly("read", "written", "errored", "skipped");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-5] the 200 answer of getIntakeRun for a FAILED run is a BatchRun with error text and endedAt")
  void failedRunIsABatchRun() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, cutShort(), DATE);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("error").isTextual()).isTrue();
    assertThat(run.path("endedAt").isTextual()).isTrue();
  }

  @Test
  @DisplayName("[MAM-57 AC-1] a COMPLETED run has no error member or a null one, never an empty text")
  void completedRunHasNoError() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, valid(), DATE);
    assertThat(run.path("error").isMissingNode() || run.path("error").isNull()).isTrue();
  }

  @Test
  @DisplayName("[MAM-57 AC-1] the status of a run is one of the contract's RUNNING, COMPLETED, FAILED")
  void statusIsInTheEnum() throws Exception {
    JsonNode completed = IntakeApi.runToEnd(mvc, json, valid(), DATE);
    JsonNode failed = IntakeApi.runToEnd(mvc, json, cutShort(), "2026-04-01");
    assertThat(List.of(completed.path("status").asText(), failed.path("status").asText()))
        .isSubsetOf("RUNNING", "COMPLETED", "FAILED");
  }

  // ---- Problem ------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-7] the 400 answer of startIntakeRun is a Problem of the contract")
  void badRequestIsAProblem() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), valid(), null, null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] the 401 answer is a Problem of the contract")
  void unauthorizedIsAProblem() throws Exception {
    MvcResult r = IntakeApi.start(mvc, null, valid(), DATE);
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] the 403 answer is a Problem of the contract")
  void forbiddenIsAProblem() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.analyst(), valid(), DATE);
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-57 AC-7] the 404 answer of getIntakeRun is a Problem of the contract")
  void notFoundIsAProblem() throws Exception {
    MvcResult r = IntakeApi.read(mvc, UNKNOWN, PemJwt.admin());
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] the 404 answer of getIntakeErrorFile is a Problem of the contract")
  void errorFileNotFoundIsAProblem() throws Exception {
    MvcResult r = IntakeApi.errorFile(mvc, UNKNOWN, PemJwt.admin());
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  // ---- what the contract documents ----------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-7] the contract documents 202, 400, 401, 403 for startIntakeRun")
  void startStatuses() {
    assertThat(ContractFile.statuses("startIntakeRun")).contains("202", "400", "401", "403");
  }

  @Test
  @DisplayName("[MAM-57 AC-7] the contract documents 200, 401, 403, 404 for getIntakeRun and getIntakeErrorFile")
  void readStatuses() {
    assertThat(ContractFile.statuses("getIntakeRun")).contains("200", "401", "403", "404");
    assertThat(ContractFile.statuses("getIntakeErrorFile")).contains("200", "401", "403", "404");
  }

  @Test
  @DisplayName("[MAM-56 AC-6] the error file is text/plain as the contract says")
  void errorFileMediaType() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, valid(), DATE);
    IntakeApi.await(mvc, json, id);
    MvcResult r = IntakeApi.errorFile(mvc, id, PemJwt.admin());
    assertThat(ContractFile.mediaTypes("getIntakeErrorFile", 200)).contains("text/plain");
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
  }

  @Test
  @DisplayName("[MAM-56 AC-1] the request of startIntakeRun is multipart/form-data with file and businessDate required")
  void requestSchema() {
    assertThat(ContractFile.requestMediaTypes("startIntakeRun")).containsExactly("multipart/form-data");
    assertThat(ContractFile.required("FileRunRequest")).contains("file", "businessDate");
    assertThat(ContractFile.required("FileRunRequest")).doesNotContain("batchId");
  }

  @Test
  @DisplayName("[MAM-57 AC-1] a BatchRun needs runId, job, businessDate and status")
  void batchRunRequired() {
    assertThat(ContractFile.required("BatchRun"))
        .containsExactlyInAnyOrder("runId", "job", "businessDate", "status");
  }
}
