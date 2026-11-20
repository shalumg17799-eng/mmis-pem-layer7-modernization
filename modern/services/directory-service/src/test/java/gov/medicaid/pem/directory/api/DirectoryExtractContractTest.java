package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.ContractFile;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-67 contract tests against modern/contracts/openapi/directory-service.yaml (1.1.0): the
 * answers of startDirectoryExtract, getDirectoryExtract and getDirectoryExtractFile are the
 * contract's BatchRun, Problem and text/plain.
 */
class DirectoryExtractContractTest extends IntegrationTest {

  static final String BASE = "/api/v1/batch/directory-extracts";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", null, null),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"));
  }

  MvcResult start(String body) throws Exception {
    return mvc.perform(
            post(BASE).with(PemJwt.admin()).contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn();
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("[MAM-67 AC-1] the 202 of startDirectoryExtract is a BatchRun of the contract")
  void startAnswer() throws Exception {
    MvcResult r = start("{\"businessDate\":\"2026-03-31\"}");
    assertThat(ContractFile.mediaTypes("startDirectoryExtract", 202))
        .containsExactly("application/json");
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    assertThat(ContractFile.violations("BatchRun", body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-1] the 200 of getDirectoryExtract is a BatchRun of the contract")
  void statusAnswer() throws Exception {
    String runId = body(start("{\"businessDate\":\"2026-03-31\"}")).path("runId").asText();
    MvcResult r = mvc.perform(get(BASE + "/" + runId).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(ContractFile.mediaTypes("getDirectoryExtract", 200))
        .containsExactly("application/json");
    assertThat(ContractFile.violations("BatchRun", body(r))).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] the 200 of getDirectoryExtractFile is text/plain as the contract says")
  void fileAnswer() throws Exception {
    String runId = body(start("{\"businessDate\":\"2026-03-31\"}")).path("runId").asText();
    MvcResult r = mvc.perform(get(BASE + "/" + runId + "/file").with(PemJwt.admin())).andReturn();
    assertThat(ContractFile.mediaTypes("getDirectoryExtractFile", 200))
        .containsExactly("text/plain");
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
  }

  @Test
  @DisplayName("[MAM-67 AC-2] the 400 of startDirectoryExtract is a Problem of the contract")
  void badRequestAnswer() throws Exception {
    MvcResult r = start("{}");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(ContractFile.mediaTypes("startDirectoryExtract", 400))
        .containsExactly("application/problem+json");
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-2] the 400 for a date that is not ISO is a Problem of the contract")
  void unreadableDateAnswer() throws Exception {
    MvcResult r = start("{\"businessDate\":\"31/03/2026\"}");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-8] the 403 of startDirectoryExtract is a Problem of the contract")
  void forbiddenAnswer() throws Exception {
    MvcResult r =
        mvc.perform(
                post(BASE)
                    .with(PemJwt.analyst())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"businessDate\":\"2026-03-31\"}"))
            .andReturn();
    assertThat(ContractFile.mediaTypes("startDirectoryExtract", 403))
        .containsExactly("application/problem+json");
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-7] the 404 of getDirectoryExtractFile is a Problem of the contract")
  void notFoundAnswer() throws Exception {
    MvcResult r =
        mvc.perform(get(BASE + "/00000000-0000-0000-0000-000000000000/file").with(PemJwt.admin()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(ContractFile.mediaTypes("getDirectoryExtractFile", 404))
        .containsExactly("application/problem+json");
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-67 AC-1] the BatchRun job in the contract is the legacy program id PRVDIR01")
  void jobName() throws Exception {
    MvcResult r = start("{\"businessDate\":\"2026-03-31\"}");
    assertThat(body(r).path("job").asText()).isEqualTo("PRVDIR01");
    assertThat(ContractFile.operation("startDirectoryExtract").get("x-legacy"))
        .isEqualTo("PRVDIR01");
  }
}
