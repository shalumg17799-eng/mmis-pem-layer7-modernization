package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.ContractFile;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contract tests against modern/contracts/openapi/tmsis-service.yaml (1.0.0): the answers of
 * startTmsisExtract, listTmsisExtracts and getTmsisExtractFile are the contract's BatchRun,
 * BatchRunPage, Problem and text/plain, with the status codes and media types it documents.
 */
@Timeout(120)
class ExtractContractTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  @BeforeEach
  void providers() {
    TestProviderService.holds(Prov.active("1003000333"), Prov.active("1003000111"));
  }

  // ---- the contract itself ----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the contract has POST and GET on /api/v1/batch/tmsis-extracts and GET on the file, and nothing else")
  void contractPaths() {
    var paths = ContractFile.methodsOfPathsStartingWith("/api/v1/batch/tmsis-extracts");
    assertThat(paths)
        .containsOnlyKeys(
            "/api/v1/batch/tmsis-extracts", "/api/v1/batch/tmsis-extracts/{runId}/file");
    assertThat(paths.get("/api/v1/batch/tmsis-extracts")).containsExactlyInAnyOrder("post", "get");
    assertThat(paths.get("/api/v1/batch/tmsis-extracts/{runId}/file")).containsExactly("get");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-1] the contract documents 202 for the start, 200 for the list and the file, and problem+json for 400, 401, 403 and 404")
  void contractMediaTypes() {
    assertThat(ContractFile.mediaTypes("startTmsisExtract", 202))
        .containsExactly("application/json");
    assertThat(ContractFile.mediaTypes("listTmsisExtracts", 200))
        .containsExactly("application/json");
    assertThat(ContractFile.mediaTypes("getTmsisExtractFile", 200)).containsExactly("text/plain");
    for (int status : Set.of(400, 401, 403)) {
      assertThat(ContractFile.mediaTypes("startTmsisExtract", status))
          .containsExactly("application/problem+json");
    }
    assertThat(ContractFile.mediaTypes("getTmsisExtractFile", 404))
        .containsExactly("application/problem+json");
  }

  // ---- the answers ------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-75 AC-1] the 202 of startTmsisExtract is a BatchRun of the contract")
  void startAnswer() throws Exception {
    MvcResult r = api.postBody(PemJwt.admin(), TmsisApi.body(DATE));
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode run = api.read(r);
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    api.await(run.path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-75 AC-2] the COMPLETED BatchRun of the list is a BatchRun of the contract")
  void completedRunConforms() throws Exception {
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("counts").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("rows", "futureEnrolments", "archiveGeneration");
  }

  @Test
  @DisplayName("[MAM-75 AC-1] the 200 of listTmsisExtracts is a BatchRunPage of the contract")
  void listAnswer() throws Exception {
    api.runToEnd(DATE);
    MvcResult r = api.getPath(PemJwt.admin(), TmsisApi.BASE);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode page = api.read(r);
    assertThat(ContractFile.violations("BatchRunPage", page)).isEmpty();
    assertThat(page.path("items")).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-75 AC-1] an empty list is a BatchRunPage of the contract too")
  void emptyListAnswer() throws Exception {
    JsonNode page = api.read(api.getPath(PemJwt.admin(), TmsisApi.BASE));
    assertThat(ContractFile.violations("BatchRunPage", page)).isEmpty();
    assertThat(page.path("items")).isEmpty();
    assertThat(page.path("total").asInt()).isZero();
  }

  @Test
  @DisplayName("[MAM-75 AC-4] the 200 of getTmsisExtractFile is text/plain as the contract says")
  void fileAnswer() throws Exception {
    String runId = api.runToEnd(DATE).path("runId").asText();
    MvcResult r = api.getPath(PemJwt.admin(), TmsisApi.BASE + "/" + runId + "/file");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the BatchRun of a failed run is a BatchRun of the contract, with its error")
  void failedRunConforms() throws Exception {
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(run.path("error").isTextual()).isTrue();
  }

  @Test
  @DisplayName("[MAM-75 AC-8] the 400 of startTmsisExtract is a Problem of the contract")
  void badRequestAnswer() throws Exception {
    MvcResult r = api.postBody(PemJwt.admin(), "{}");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(api.read(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-8] the 400 for a body that cannot be read is a Problem of the contract")
  void unreadableBodyAnswer() throws Exception {
    MvcResult r = api.postBody(PemJwt.admin(), "{\"businessDate\":\"31/03/2026\"}");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(ContractFile.problemViolations(api.read(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-8] the 401 of startTmsisExtract is a Problem of the contract")
  void unauthorizedAnswer() throws Exception {
    MvcResult r = api.postBody(null, TmsisApi.body(DATE));
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(api.read(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-8] the 403 of listTmsisExtracts is a Problem of the contract")
  void forbiddenAnswer() throws Exception {
    MvcResult r = api.getPath(PemJwt.system(), TmsisApi.BASE);
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(api.read(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-6] the 404 of getTmsisExtractFile is a Problem of the contract")
  void notFoundAnswer() throws Exception {
    MvcResult r = api.getPath(PemJwt.admin(), TmsisApi.BASE + "/" + UUID.randomUUID() + "/file");
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(api.read(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-75 AC-1] the BatchRun job is the legacy program id PRVTMS01")
  void jobName() throws Exception {
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("job").asText()).isEqualTo("PRVTMS01");
    assertThat(ContractFile.operation("startTmsisExtract").get("x-legacy").toString())
        .contains("PRVMTH01");
  }
}
