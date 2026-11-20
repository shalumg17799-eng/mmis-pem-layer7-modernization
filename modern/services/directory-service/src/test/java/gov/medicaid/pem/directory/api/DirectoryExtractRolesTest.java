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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-67 AC-8: only the right roles can run and download (D-023, ADR-007). ADMIN and SYSTEM may
 * start a run; only ADMIN may read a run and download its file; ANALYST, SUPERVISOR and PROVIDER
 * get 403; no token gets 401. The roles of each operation are also compared with the x-roles of the
 * contract, so the code and the contract cannot drift apart.
 */
class DirectoryExtractRolesTest extends IntegrationTest {

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

  static RequestPostProcessor as(String role) {
    return switch (role) {
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      default -> PemJwt.provider("1003000111");
    };
  }

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class);
  }

  MvcResult start(RequestPostProcessor who) throws Exception {
    var request =
        post(BASE)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"businessDate\":\"2026-03-31\"}");
    return mvc.perform(who == null ? request : request.with(who)).andReturn();
  }

  String adminRunId() throws Exception {
    MvcResult r = start(PemJwt.admin());
    return json.readTree(r.getResponse().getContentAsString()).path("runId").asText();
  }

  MvcResult read(String path, RequestPostProcessor who) throws Exception {
    var request = get(path);
    return mvc.perform(who == null ? request : request.with(who)).andReturn();
  }

  // ---- the AC as written ----------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-67 AC-8] ADMIN and SYSTEM may start a run: 202")
  void adminAndSystemStart() throws Exception {
    assertThat(start(PemJwt.admin()).getResponse().getStatus()).isEqualTo(202);
    assertThat(start(PemJwt.system()).getResponse().getStatus()).isEqualTo(202);
  }

  @Test
  @DisplayName("[MAM-67 AC-8] only ADMIN may download the file: 200; SYSTEM gets 403")
  void onlyAdminDownloads() throws Exception {
    String file = BASE + "/" + adminRunId() + "/file";
    assertThat(read(file, PemJwt.admin()).getResponse().getStatus()).isEqualTo(200);
    assertThat(read(file, PemJwt.system()).getResponse().getStatus()).isEqualTo(403);
  }

  @ParameterizedTest(name = "[MAM-67 AC-8] {0} gets 403 on the start and on the file download")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "PROVIDER"})
  void otherRolesRefused(String role) throws Exception {
    String file = BASE + "/" + adminRunId() + "/file";
    assertThat(start(as(role)).getResponse().getStatus()).isEqualTo(403);
    assertThat(read(file, as(role)).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("[MAM-67 AC-8] no token gets 401 on the start, on the run and on the file download")
  void noToken() throws Exception {
    String run = BASE + "/" + adminRunId();
    assertThat(start(null).getResponse().getStatus()).isEqualTo(401);
    assertThat(read(run, null).getResponse().getStatus()).isEqualTo(401);
    assertThat(read(run + "/file", null).getResponse().getStatus()).isEqualTo(401);
  }

  @ParameterizedTest(name = "[MAM-67 AC-8] a refused start by {0} runs no job and creates no run")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "PROVIDER"})
  void refusedStartCreatesNothing(String role) throws Exception {
    int before = runCount();
    start(as(role));
    assertThat(runCount()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-67 AC-8] a start with no token creates no run")
  void unauthenticatedStartCreatesNothing() throws Exception {
    int before = runCount();
    start(null);
    assertThat(runCount()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-67 AC-8] a 403 is problem+json")
  void forbiddenIsProblem() throws Exception {
    MvcResult r = start(PemJwt.analyst());
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(403);
  }

  // ---- the code against the contract's x-roles ------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-67 AC-8] startDirectoryExtract: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void startMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("startDirectoryExtract").contains(role);
    assertThat(start(as(role)).getResponse().getStatus()).isEqualTo(allowed ? 202 : 403);
  }

  @ParameterizedTest(
      name =
          "[MAM-67 AC-8] getDirectoryExtract: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void statusMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("getDirectoryExtract").contains(role);
    int status = read(BASE + "/" + adminRunId(), as(role)).getResponse().getStatus();
    assertThat(status).isEqualTo(allowed ? 200 : 403);
  }

  @ParameterizedTest(
      name =
          "[MAM-67 AC-8] getDirectoryExtractFile: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void fileMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("getDirectoryExtractFile").contains(role);
    int status = read(BASE + "/" + adminRunId() + "/file", as(role)).getResponse().getStatus();
    assertThat(status).isEqualTo(allowed ? 200 : 403);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-8] the contract lists ADMIN and SYSTEM to start, and only ADMIN for the run and the file")
  void contractRoles() {
    assertThat(ContractFile.roles("startDirectoryExtract"))
        .containsExactlyInAnyOrder("ADMIN", "SYSTEM");
    assertThat(ContractFile.roles("getDirectoryExtract")).containsExactly("ADMIN");
    assertThat(ContractFile.roles("getDirectoryExtractFile")).containsExactly("ADMIN");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-8] a refused status read of a run that does not exist is 403, not 404: roles are checked first")
  void rolesBeforeLookup() throws Exception {
    String unknown = BASE + "/00000000-0000-0000-0000-000000000000";
    assertThat(read(unknown, PemJwt.analyst()).getResponse().getStatus()).isEqualTo(403);
    assertThat(read(unknown + "/file", PemJwt.system()).getResponse().getStatus()).isEqualTo(403);
  }
}
