package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.ContractFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-56 AC-7 and MAM-57 AC-7: only ADMIN and SYSTEM may start a run, only ADMIN may read its
 * result and its error file (D-023, ADR-007). The other roles get 403, no token 401, an unknown run
 * 404 for ADMIN, all as application/problem+json. The roles of each operation are also compared
 * with the x-roles of the contract, so the code and the contract cannot drift apart.
 */
class IntakeRunRolesTest extends IntakeIntegrationTest {

  static final String UNKNOWN = "00000000-0000-0000-0000-000000000000";

  static RequestPostProcessor as(String role) {
    return switch (role) {
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      default -> PemJwt.provider("1003000111");
    };
  }

  byte[] file() {
    return AppInputFile.lines(AppInputFile.valid("1003000111"));
  }

  int runCount() {
    return count("SELECT count(*) FROM intake_run");
  }

  /** A run that ended, started by ADMIN. */
  String adminRun() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, file(), DATE);
    IntakeApi.await(mvc, json, id);
    return id;
  }

  int status(MvcResult r) {
    return r.getResponse().getStatus();
  }

  void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asInt())
        .isEqualTo(status);
  }

  // ---- start (MAM-56 AC-7) --------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-7] ADMIN starts a run: 202")
  void adminStarts() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), file(), DATE);
    assertThat(status(r)).isEqualTo(202);
    IntakeApi.await(
        mvc, json, json.readTree(r.getResponse().getContentAsString()).path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-56 AC-7] SYSTEM starts a run: 202")
  void systemStarts() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.system(), file(), DATE);
    assertThat(status(r)).isEqualTo(202);
    IntakeApi.await(
        mvc, json, json.readTree(r.getResponse().getContentAsString()).path("runId").asText());
  }

  @ParameterizedTest(
      name = "[MAM-56 AC-7] {0} gets 403 problem+json on the start and no run is created")
  @ValueSource(strings = {"PROVIDER", "ANALYST", "SUPERVISOR"})
  void otherRolesRefusedOnStart(String role) throws Exception {
    MvcResult r = IntakeApi.start(mvc, as(role), file(), DATE);
    assertThat(status(r)).isEqualTo(403);
    assertProblem(r, 403);
    assertThat(runCount()).isZero();
    assertThat(count("SELECT count(*) FROM application")).isZero();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] a start without a token is 401 problem+json and no run is created")
  void startWithoutToken() throws Exception {
    MvcResult r = IntakeApi.start(mvc, null, file(), DATE);
    assertThat(status(r)).isEqualTo(401);
    assertProblem(r, 401);
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-7] the role is checked before the parameters: a PROVIDER without a file gets 403, not 400")
  void roleBeforeParameters() throws Exception {
    assertThat(status(IntakeApi.start(mvc, PemJwt.provider("1003000111"), null, null)))
        .isEqualTo(403);
    assertThat(status(IntakeApi.start(mvc, null, null, null))).isEqualTo(401);
  }

  // ---- the error file (MAM-56 AC-7) -------------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-7] ADMIN downloads the error file: 200 text/plain")
  void adminDownloads() throws Exception {
    MvcResult r = IntakeApi.errorFile(mvc, adminRun(), PemJwt.admin());
    assertThat(status(r)).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
  }

  @ParameterizedTest(name = "[MAM-56 AC-7] {0} gets 403 problem+json on the error file")
  @ValueSource(strings = {"SYSTEM", "PROVIDER", "ANALYST", "SUPERVISOR"})
  void otherRolesRefusedOnErrorFile(String role) throws Exception {
    MvcResult r = IntakeApi.errorFile(mvc, adminRun(), as(role));
    assertThat(status(r)).isEqualTo(403);
    assertProblem(r, 403);
  }

  @Test
  @DisplayName("[MAM-56 AC-7] the error file without a token is 401 problem+json")
  void errorFileWithoutToken() throws Exception {
    MvcResult r = IntakeApi.errorFile(mvc, adminRun(), null);
    assertThat(status(r)).isEqualTo(401);
    assertProblem(r, 401);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-7] an unknown run id has no error file: 404 problem+json that names the id")
  void unknownRunHasNoErrorFile() throws Exception {
    MvcResult r = IntakeApi.errorFile(mvc, UNKNOWN, PemJwt.admin());
    assertThat(status(r)).isEqualTo(404);
    assertProblem(r, 404);
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("detail").asText())
        .contains(UNKNOWN);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-7] the roles are checked before the lookup: a refused download of an unknown run is 403, not 404")
  void errorFileRolesBeforeLookup() throws Exception {
    assertThat(status(IntakeApi.errorFile(mvc, UNKNOWN, PemJwt.analyst()))).isEqualTo(403);
    assertThat(status(IntakeApi.errorFile(mvc, UNKNOWN, PemJwt.system()))).isEqualTo(403);
    assertThat(status(IntakeApi.errorFile(mvc, UNKNOWN, null))).isEqualTo(401);
  }

  // ---- the run result (MAM-57 AC-7) -------------------------------------------------------------

  @Test
  @DisplayName("[MAM-57 AC-7] ADMIN reads a run: 200")
  void adminReads() throws Exception {
    assertThat(status(IntakeApi.read(mvc, adminRun(), PemJwt.admin()))).isEqualTo(200);
  }

  @ParameterizedTest(name = "[MAM-57 AC-7] {0} gets 403 problem+json on reading a run")
  @ValueSource(strings = {"SYSTEM", "PROVIDER", "ANALYST", "SUPERVISOR"})
  void otherRolesRefusedOnRead(String role) throws Exception {
    MvcResult r = IntakeApi.read(mvc, adminRun(), as(role));
    assertThat(status(r)).isEqualTo(403);
    assertProblem(r, 403);
  }

  @Test
  @DisplayName("[MAM-57 AC-7] reading a run without a token is 401 problem+json")
  void readWithoutToken() throws Exception {
    MvcResult r = IntakeApi.read(mvc, adminRun(), null);
    assertThat(status(r)).isEqualTo(401);
    assertProblem(r, 401);
  }

  @Test
  @DisplayName("[MAM-57 AC-7] an unknown run id is 404 problem+json for ADMIN and names the id")
  void unknownRun() throws Exception {
    MvcResult r = IntakeApi.read(mvc, UNKNOWN, PemJwt.admin());
    assertThat(status(r)).isEqualTo(404);
    assertProblem(r, 404);
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("detail").asText()).contains(UNKNOWN);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-7] the roles are checked before the lookup: a refused read of an unknown run is 403, not 404")
  void rolesBeforeLookup() throws Exception {
    assertThat(status(IntakeApi.read(mvc, UNKNOWN, PemJwt.analyst()))).isEqualTo(403);
    assertThat(status(IntakeApi.read(mvc, UNKNOWN, PemJwt.system()))).isEqualTo(403);
    assertThat(status(IntakeApi.read(mvc, UNKNOWN, null))).isEqualTo(401);
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-7] a run id that is no UUID is 400 problem+json for ADMIN, on both reads")
  void malformedRunId() throws Exception {
    MvcResult run = IntakeApi.read(mvc, "not-a-uuid", PemJwt.admin());
    MvcResult file = IntakeApi.errorFile(mvc, "not-a-uuid", PemJwt.admin());
    assertThat(status(run)).isEqualTo(400);
    assertThat(status(file)).isEqualTo(400);
    assertProblem(run, 400);
    assertProblem(file, 400);
  }

  // ---- the code against the contract's x-roles --------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-56 AC-7] startIntakeRun: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void startMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("startIntakeRun").contains(role);
    MvcResult r = IntakeApi.start(mvc, as(role), file(), DATE);
    assertThat(status(r)).isEqualTo(allowed ? 202 : 403);
    if (allowed) {
      IntakeApi.await(
          mvc, json, json.readTree(r.getResponse().getContentAsString()).path("runId").asText());
    }
  }

  @ParameterizedTest(
      name = "[MAM-57 AC-7] getIntakeRun: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void readMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("getIntakeRun").contains(role);
    assertThat(status(IntakeApi.read(mvc, adminRun(), as(role)))).isEqualTo(allowed ? 200 : 403);
  }

  @ParameterizedTest(
      name =
          "[MAM-56 AC-7] getIntakeErrorFile: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void errorFileMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("getIntakeErrorFile").contains(role);
    assertThat(status(IntakeApi.errorFile(mvc, adminRun(), as(role))))
        .isEqualTo(allowed ? 200 : 403);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-7] the contract lists ADMIN and SYSTEM to start, and only ADMIN to read a run and its error file")
  void contractRoles() {
    assertThat(ContractFile.roles("startIntakeRun")).containsExactlyInAnyOrder("ADMIN", "SYSTEM");
    assertThat(ContractFile.roles("getIntakeRun")).containsExactly("ADMIN");
    assertThat(ContractFile.roles("getIntakeErrorFile")).containsExactly("ADMIN");
  }
}
