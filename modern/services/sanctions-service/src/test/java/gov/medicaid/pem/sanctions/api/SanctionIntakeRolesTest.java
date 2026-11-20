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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-55 AC-8: only ADMIN and SYSTEM may start a run and only ADMIN may read its result (D-023,
 * ADR-007). ANALYST, SUPERVISOR and PROVIDER get 403, no token gets 401, an unknown run id gets 404
 * for ADMIN. The roles of each operation are also compared with the x-roles of the contract, so the
 * code and the contract cannot drift apart.
 */
class SanctionIntakeRolesTest extends IntegrationTest {

  static final String UNKNOWN = "00000000-0000-0000-0000-000000000000";

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

  byte[] file() {
    return SancinFile.lines(
        SancinFile.record("1003000111", "T", "SYNTHETIC TEST REASON", "20260331"));
  }

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM sanction_intake_run", Integer.class);
  }

  MvcResult start(RequestPostProcessor who) throws Exception {
    return IntakeApi.start(mvc, who, file(), "2026-03-31");
  }

  String adminRun() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, file(), "2026-03-31");
    IntakeApi.await(mvc, json, id);
    return id;
  }

  int status(MvcResult r) {
    return r.getResponse().getStatus();
  }

  // ---- the AC as written ----------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-8] ADMIN and SYSTEM may start a run: 202")
  void adminAndSystemStart() throws Exception {
    MvcResult admin = start(PemJwt.admin());
    MvcResult system = start(PemJwt.system());
    assertThat(status(admin)).isEqualTo(202);
    assertThat(status(system)).isEqualTo(202);
    IntakeApi.await(
        mvc, json, json.readTree(admin.getResponse().getContentAsString()).path("runId").asText());
    IntakeApi.await(
        mvc, json, json.readTree(system.getResponse().getContentAsString()).path("runId").asText());
  }

  @ParameterizedTest(
      name = "[MAM-55 AC-8] {0} gets 403 problem+json on the start and no run is created")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "PROVIDER"})
  void otherRolesRefusedOnStart(String role) throws Exception {
    MvcResult r = start(as(role));
    assertThat(status(r)).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(403);
    assertThat(runCount()).isZero();
    assertThat(TestProviderService.MOCK.getAllServeEvents()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] a start without a token is 401 and no run is created")
  void startWithoutToken() throws Exception {
    assertThat(status(start(null))).isEqualTo(401);
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-8] only ADMIN may read a run: 200; SYSTEM gets 403")
  void onlyAdminReads() throws Exception {
    String id = adminRun();
    assertThat(status(IntakeApi.read(mvc, id, PemJwt.admin()))).isEqualTo(200);
    assertThat(status(IntakeApi.read(mvc, id, PemJwt.system()))).isEqualTo(403);
  }

  @ParameterizedTest(name = "[MAM-55 AC-8] {0} gets 403 problem+json on reading a run")
  @ValueSource(strings = {"SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void otherRolesRefusedOnRead(String role) throws Exception {
    MvcResult r = IntakeApi.read(mvc, adminRun(), as(role));
    assertThat(status(r)).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName("[MAM-55 AC-8] reading a run without a token is 401")
  void readWithoutToken() throws Exception {
    assertThat(status(IntakeApi.read(mvc, adminRun(), null))).isEqualTo(401);
  }

  @Test
  @DisplayName("[MAM-55 AC-8] an unknown run id is 404 problem+json for ADMIN and names the id")
  void unknownRun() throws Exception {
    MvcResult r = IntakeApi.read(mvc, UNKNOWN, PemJwt.admin());
    assertThat(status(r)).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(404);
    assertThat(problem.path("detail").asText()).contains(UNKNOWN);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-8] the roles are checked before the lookup: a refused read of an unknown run is 403, not 404")
  void rolesBeforeLookup() throws Exception {
    assertThat(status(IntakeApi.read(mvc, UNKNOWN, PemJwt.analyst()))).isEqualTo(403);
    assertThat(status(IntakeApi.read(mvc, UNKNOWN, PemJwt.system()))).isEqualTo(403);
    assertThat(status(IntakeApi.read(mvc, UNKNOWN, null))).isEqualTo(401);
  }

  // ---- the code against the contract's x-roles ------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-55 AC-8] startSanctionIntakeRun: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void startMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("startSanctionIntakeRun").contains(role);
    MvcResult r = start(as(role));
    assertThat(status(r)).isEqualTo(allowed ? 202 : 403);
    if (allowed) {
      IntakeApi.await(
          mvc, json, json.readTree(r.getResponse().getContentAsString()).path("runId").asText());
    }
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-8] getSanctionIntakeRun: {0} is allowed exactly as the contract's x-roles say")
  @ValueSource(strings = {"ADMIN", "SYSTEM", "ANALYST", "SUPERVISOR", "PROVIDER"})
  void readMatchesContract(String role) throws Exception {
    boolean allowed = ContractFile.roles("getSanctionIntakeRun").contains(role);
    assertThat(status(IntakeApi.read(mvc, adminRun(), as(role)))).isEqualTo(allowed ? 200 : 403);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-8] the contract lists ADMIN and SYSTEM to start, and only ADMIN to read a run")
  void contractRoles() {
    assertThat(ContractFile.roles("startSanctionIntakeRun"))
        .containsExactlyInAnyOrder("ADMIN", "SYSTEM");
    assertThat(ContractFile.roles("getSanctionIntakeRun")).containsExactly("ADMIN");
  }
}
