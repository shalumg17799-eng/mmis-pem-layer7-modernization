package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.ContractFile;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-75 AC-8 (D-023, ADR-007): only the right roles can run, list and download. ADMIN and SYSTEM
 * may start a run (202); only ADMIN may list runs and download files (SYSTEM gets 403 on both);
 * ANALYST, SUPERVISOR and PROVIDER get 403 everywhere; no token gets 401; a 401 and a 403 are
 * application/problem+json. The roles of each operation are also compared with the x-roles of the
 * contract, so the code and the contract cannot drift apart.
 */
@Timeout(120)
class ExtractRolesTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  @BeforeEach
  void providers() {
    TestProviderService.holds(Prov.active("1003000333"), Prov.active("1003000111"));
  }

  static RequestPostProcessor as(String role) {
    return switch (role) {
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "PROVIDER" -> PemJwt.provider("1003000111");
      default -> null; // no token
    };
  }

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class);
  }

  MvcResult start(String role) throws Exception {
    return api.postBody(as(role), TmsisApi.body(DATE));
  }

  /** A run of the ADMIN that has ended, so its file exists. */
  String adminRunId() throws Exception {
    String runId = api.start(DATE).path("runId").asText();
    api.await(runId);
    return runId;
  }

  // ---- the AC as written ------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-75 AC-8] ADMIN and SYSTEM may start a run: 202")
  void adminAndSystemStart() throws Exception {
    assertThat(start("ADMIN").getResponse().getStatus()).isEqualTo(202);
    assertThat(start("SYSTEM").getResponse().getStatus()).isEqualTo(202);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] a run started by SYSTEM is a run like any other: it runs to COMPLETED")
  void systemRunCompletes() throws Exception {
    JsonNode run = api.read(start("SYSTEM"));
    assertThat(api.await(run.path("runId").asText()).path("status").asText())
        .isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName("[MAM-75 AC-8] only ADMIN may list the runs: 200; SYSTEM gets 403")
  void onlyAdminLists() throws Exception {
    assertThat(api.getPath(as("ADMIN"), TmsisApi.BASE).getResponse().getStatus()).isEqualTo(200);
    assertThat(api.getPath(as("SYSTEM"), TmsisApi.BASE).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("[MAM-75 AC-8] only ADMIN may download the file: 200; SYSTEM gets 403")
  void onlyAdminDownloads() throws Exception {
    String file = TmsisApi.BASE + "/" + adminRunId() + "/file";
    assertThat(api.getPath(as("ADMIN"), file).getResponse().getStatus()).isEqualTo(200);
    assertThat(api.getPath(as("SYSTEM"), file).getResponse().getStatus()).isEqualTo(403);
  }

  @ParameterizedTest(
      name = "[MAM-75 AC-8] {0} gets 403 on the start, the list and the file download")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "PROVIDER"})
  void otherRolesAreRefused(String role) throws Exception {
    String file = TmsisApi.BASE + "/" + adminRunId() + "/file";
    assertThat(start(role).getResponse().getStatus()).isEqualTo(403);
    assertThat(api.getPath(as(role), TmsisApi.BASE).getResponse().getStatus()).isEqualTo(403);
    assertThat(api.getPath(as(role), file).getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("[MAM-75 AC-8] no token gets 401 on the start, the list and the file download")
  void noToken() throws Exception {
    String file = TmsisApi.BASE + "/" + adminRunId() + "/file";
    assertThat(start("none").getResponse().getStatus()).isEqualTo(401);
    assertThat(api.getPath(null, TmsisApi.BASE).getResponse().getStatus()).isEqualTo(401);
    assertThat(api.getPath(null, file).getResponse().getStatus()).isEqualTo(401);
  }

  // ---- nothing happens for a refused caller -----------------------------------------------------

  @ParameterizedTest(name = "[MAM-75 AC-8] a refused start by {0} creates no run and starts no job")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "PROVIDER", "none"})
  void refusedStartCreatesNothing(String role) throws Exception {
    int runs = runCount();
    int jobs = jdbc.queryForObject("SELECT count(*) FROM batch_job_instance", Integer.class);
    start(role);
    assertThat(runCount()).isEqualTo(runs);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM batch_job_instance", Integer.class))
        .isEqualTo(jobs);
    assertThat(TestProviderService.searchCount()).isZero();
  }

  // ---- problem+json -----------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-75 AC-8] a 403 is application/problem+json with status 403")
  void forbiddenIsProblemJson() throws Exception {
    MvcResult r = start("ANALYST");
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(api.read(r).path("status").asInt()).isEqualTo(403);
    assertThat(api.read(r).path("title").asText()).isNotBlank();
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] a 401 is application/problem+json with status 401 on the start, the list and the file (code review, lesson h)")
  void unauthorizedIsProblemJson() throws Exception {
    String file = TmsisApi.BASE + "/" + UUID.randomUUID() + "/file";
    for (MvcResult r :
        new MvcResult[] {
          start("none"), api.getPath(null, TmsisApi.BASE), api.getPath(null, file)
        }) {
      assertThat(r.getResponse().getStatus()).isEqualTo(401);
      assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
      assertThat(api.read(r).path("status").asInt()).isEqualTo(401);
    }
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] a start without a token and with a body that cannot be read is a 401, not a 400: the caller is checked first")
  void tokenBeforeBody() throws Exception {
    MvcResult r = api.postBody(null, "not json");
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] a refused file download of a run that does not exist is 403, not 404: the role is checked first")
  void roleBeforeLookup() throws Exception {
    String unknown = TmsisApi.BASE + "/" + UUID.randomUUID() + "/file";
    assertThat(api.getPath(PemJwt.analyst(), unknown).getResponse().getStatus()).isEqualTo(403);
    assertThat(api.getPath(PemJwt.system(), unknown).getResponse().getStatus()).isEqualTo(403);
    assertThat(api.getPath(PemJwt.admin(), unknown).getResponse().getStatus()).isEqualTo(404);
  }

  // ---- the code against the contract's x-roles --------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] startTmsisExtract: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void startMatchesTheContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("startTmsisExtract").contains(role);
    MvcResult r = start(role);
    assertThat(r.getResponse().getStatus()).isEqualTo(allowed ? 202 : 403);
    if (allowed) {
      api.await(api.read(r).path("runId").asText());
    }
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] listTmsisExtracts: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void listMatchesTheContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("listTmsisExtracts").contains(role);
    assertThat(api.getPath(as(role), TmsisApi.BASE).getResponse().getStatus())
        .isEqualTo(allowed ? 200 : 403);
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] getTmsisExtractFile: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void fileMatchesTheContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("getTmsisExtractFile").contains(role);
    String file = TmsisApi.BASE + "/" + adminRunId() + "/file";
    assertThat(api.getPath(as(role), file).getResponse().getStatus())
        .isEqualTo(allowed ? 200 : 403);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] the contract lists ADMIN and SYSTEM to start, and only ADMIN to list and to download")
  void contractRoles() {
    assertThat(ContractFile.roles("startTmsisExtract"))
        .containsExactlyInAnyOrder("ADMIN", "SYSTEM");
    assertThat(ContractFile.roles("listTmsisExtracts")).containsExactly("ADMIN");
    assertThat(ContractFile.roles("getTmsisExtractFile")).containsExactly("ADMIN");
  }
}
