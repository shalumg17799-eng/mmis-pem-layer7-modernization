package gov.medicaid.pem.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.risk.IntegrationTest;
import gov.medicaid.pem.risk.RiskFixtures;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-38 through the real application (Postgres and Kafka from Testcontainers): GET and PUT
 * /api/v1/risk-rules with signed-in users: roles (401/403), validation (400/422), versioning and
 * the contract risk-service.yaml (getRiskRules, replaceRiskRules).
 */
class RiskRulesApiTest extends IntegrationTest {

  static final String RULES = "/api/v1/risk-rules";
  static final OpenApiContract CONTRACT = OpenApiContract.of("risk-service");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void startFromTheLegacyTable() {
    RiskFixtures.resetToLegacyTable(jdbc);
  }

  @AfterEach
  void restoreTheLegacyTable() {
    RiskFixtures.resetToLegacyTable(jdbc);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private MvcResult read(RequestPostProcessor who) throws Exception {
    MockHttpServletRequestBuilder req = get(RULES);
    return mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  private MvcResult save(RequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        put(RULES).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private JsonNode tableInForce() throws Exception {
    MvcResult r = read(PemJwt.admin());
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return body(r);
  }

  private void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode p = body(r);
    assertThat(p.path("status").asInt()).isEqualTo(status);
    assertThat(p.path("title").asText()).isNotBlank();
  }

  private void assertLegacyTableUnchanged() throws Exception {
    JsonNode t = tableInForce();
    assertThat(t.path("version").asInt()).isEqualTo(1);
    assertThat(t.path("defaultTier").asText()).isEqualTo("L");
    assertThat(RiskFixtures.rulesOf(t)).containsExactlyInAnyOrderElementsOf(RiskFixtures.LEGACY);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_rule_set", Integer.class))
        .isEqualTo(1);
  }

  // ---- AC-1: staff read the table --------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-38 AC-1] {0} reads version 1: default L, rules 332 H, 251 H, 341 M, 291 M, 225 M")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "ADMIN"})
  @DisplayName("[MAM-38 AC-1] staff read the current table")
  void staffRead(String role) throws Exception {
    mvc.perform(get(RULES).with(PemJwt.user("x", null, role)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(1))
        .andExpect(jsonPath("$.defaultTier").value("L"))
        .andExpect(jsonPath("$.rules.length()").value(5))
        .andExpect(jsonPath("$.effectiveFrom").value("2000-01-01"));
    assertThat(RiskFixtures.rulesOf(tableInForce()))
        .containsExactlyInAnyOrderElementsOf(RiskFixtures.LEGACY);
  }

  @Test
  @DisplayName("[MAM-38 AC-1] the rules come back in the order PRVRSK01 lists them")
  void rulesInSavedOrder() throws Exception {
    assertThat(RiskFixtures.rulesOf(tableInForce())).containsExactlyElementsOf(RiskFixtures.LEGACY);
  }

  @Test
  @DisplayName("[MAM-38 AC-1] the GET response matches the contract (getRiskRules 200)")
  void getMatchesContract() throws Exception {
    MvcResult r = read(PemJwt.analyst());

    CONTRACT.assertResponse("getRiskRules", 200, r.getResponse().getContentAsString());
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
  }

  // ---- AC-2: an administrator saves a new version ----------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-2] an ADMIN saves the legacy rules plus 207 as M: version 2, and GET returns it")
  void adminSaves() throws Exception {
    LocalDate before = LocalDate.now(ZoneOffset.UTC);

    MvcResult saved =
        save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    LocalDate after = LocalDate.now(ZoneOffset.UTC);
    assertThat(saved.getResponse().getStatus()).isEqualTo(200);
    JsonNode s = body(saved);
    assertThat(s.path("version").asInt()).isEqualTo(2);
    assertThat(s.path("defaultTier").asText()).isEqualTo("L");
    assertThat(LocalDate.parse(s.path("effectiveFrom").asText())).isBetween(before, after);
    assertThat(RiskFixtures.rulesOf(s)).containsExactlyElementsOf(RiskFixtures.legacyPlus("207=M"));

    JsonNode now = tableInForce();
    assertThat(now.path("version").asInt()).isEqualTo(2);
    assertThat(RiskFixtures.rulesOf(now))
        .containsExactlyElementsOf(RiskFixtures.legacyPlus("207=M"));
  }

  @Test
  @DisplayName("[MAM-38 AC-2] the PUT response matches the contract (replaceRiskRules 200)")
  void putMatchesContract() throws Exception {
    MvcResult r =
        save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    CONTRACT.assertResponse("replaceRiskRules", 200, r.getResponse().getContentAsString());
  }

  @Test
  @DisplayName("[MAM-38 AC-2] version 1 is kept: both versions are stored, each with its own rules")
  void oldVersionKept() throws Exception {
    save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    assertThat(
            jdbc.queryForList(
                "SELECT rule_set_version FROM risk_rule_set ORDER BY rule_set_version",
                Integer.class))
        .containsExactly(1, 2);
    assertThat(
            jdbc.queryForList(
                "SELECT r.taxonomy_prefix || '=' || r.tier FROM risk_rule r JOIN risk_rule_set s ON s.id = r.rule_set_id"
                    + " WHERE s.rule_set_version = 1",
                String.class))
        .containsExactlyInAnyOrderElementsOf(RiskFixtures.LEGACY);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM risk_rule r JOIN risk_rule_set s ON s.id = r.rule_set_id WHERE s.rule_set_version = 2",
                Integer.class))
        .isEqualTo(6);
  }

  @Test
  @DisplayName("[MAM-38 AC-2] the new version takes the default tier and the rules of the request")
  void takesTheRequest() throws Exception {
    save(PemJwt.admin(), RiskFixtures.ruleSet(1, "M", List.of("207=H", "332=L")));

    JsonNode t = tableInForce();
    assertThat(t.path("version").asInt()).isEqualTo(2);
    assertThat(t.path("defaultTier").asText()).isEqualTo("M");
    assertThat(RiskFixtures.rulesOf(t)).containsExactly("207=H", "332=L");
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-2] two saves in a row give versions 2 and 3, the second based on version 2")
  void twoSaves() throws Exception {
    assertThat(
            save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")))
                .getResponse()
                .getStatus())
        .isEqualTo(200);

    MvcResult second =
        save(PemJwt.admin(), RiskFixtures.ruleSet(2, "L", RiskFixtures.legacyPlus("208=H")));

    assertThat(second.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(second).path("version").asInt()).isEqualTo(3);
    assertThat(tableInForce().path("version").asInt()).isEqualTo(3);
  }

  // ---- AC-3: stale or inconsistent -------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-3] a table based on version 1 when version 2 is in force is a 422 problem; GET still returns version 2")
  void staleVersion() throws Exception {
    save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    MvcResult stale =
        save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("208=H")));

    assertProblem(stale, 422);
    assertThat(body(stale).path("detail").asText()).contains("version 1").contains("version 2");
    JsonNode t = tableInForce();
    assertThat(t.path("version").asInt()).isEqualTo(2);
    assertThat(RiskFixtures.rulesOf(t)).containsExactlyElementsOf(RiskFixtures.legacyPlus("207=M"));
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-3] a table that lists prefix 332 twice is a 422 problem; GET still returns version 2 unchanged")
  void duplicatePrefix() throws Exception {
    save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    MvcResult dup =
        save(PemJwt.admin(), RiskFixtures.ruleSet(2, "L", RiskFixtures.legacyPlus("332=M")));

    assertProblem(dup, 422);
    assertThat(body(dup).path("detail").asText()).contains("332");
    JsonNode t = tableInForce();
    assertThat(t.path("version").asInt()).isEqualTo(2);
    assertThat(RiskFixtures.rulesOf(t)).containsExactlyElementsOf(RiskFixtures.legacyPlus("207=M"));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_rule_set", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a table based on a version that does not exist yet is a 422 too")
  void versionAhead() throws Exception {
    assertProblem(save(PemJwt.admin(), RiskFixtures.ruleSet(5, "L", RiskFixtures.LEGACY)), 422);
    assertLegacyTableUnchanged();
  }

  @Test
  @DisplayName("[MAM-38 AC-3] the 422 and 400 responses match the contract (Problem)")
  void problemsMatchContract() throws Exception {
    MvcResult stale = save(PemJwt.admin(), RiskFixtures.ruleSet(9, "L", RiskFixtures.LEGACY));
    MvcResult bad = save(PemJwt.admin(), RiskFixtures.ruleSet(1, "X", RiskFixtures.LEGACY));

    CONTRACT.assertResponse(
        "replaceRiskRules",
        422,
        RiskFixtures.withoutInstance(stale.getResponse().getContentAsString()));
    CONTRACT.assertResponse(
        "replaceRiskRules",
        400,
        RiskFixtures.withoutInstance(bad.getResponse().getContentAsString()));
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-3] two administrators saving on version 1 at the same time: one wins with 200, the other gets 422")
  void twoAtOnce() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Integer>> calls = new ArrayList<>();
      for (String extra : new String[] {"207=M", "208=H"}) {
        calls.add(
            () -> {
              start.await();
              return save(
                      PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus(extra)))
                  .getResponse()
                  .getStatus();
            });
      }
      List<Future<Integer>> futures = new ArrayList<>();
      for (Callable<Integer> c : calls) {
        futures.add(pool.submit(c));
      }
      start.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> f : futures) {
        statuses.add(f.get());
      }

      assertThat(statuses).containsExactlyInAnyOrder(200, 422);
    } finally {
      pool.shutdownNow();
    }
    assertThat(tableInForce().path("version").asInt()).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_rule_set", Integer.class))
        .isEqualTo(2);
  }

  // ---- AC-4: malformed rules -------------------------------------------------------------------

  static Stream<Arguments> malformedBodies() {
    return Stream.of(
        Arguments.of("a 2-character prefix", RiskFixtures.ruleSet(1, "L", List.of("20=M"))),
        Arguments.of("a lower-case prefix", RiskFixtures.ruleSet(1, "L", List.of("20q=M"))),
        Arguments.of("a 4-character prefix", RiskFixtures.ruleSet(1, "L", List.of("2077=M"))),
        Arguments.of("a prefix with a symbol", RiskFixtures.ruleSet(1, "L", List.of("20-=M"))),
        Arguments.of("a tier X", RiskFixtures.ruleSet(1, "L", List.of("207=X"))),
        Arguments.of("a lower-case tier", RiskFixtures.ruleSet(1, "L", List.of("207=m"))),
        Arguments.of("a rule without a tier", RiskFixtures.ruleSet(1, "L", List.of("207=-"))),
        Arguments.of("no defaultTier", RiskFixtures.ruleSet(1, null, RiskFixtures.LEGACY)),
        Arguments.of("a defaultTier X", RiskFixtures.ruleSet(1, "X", RiskFixtures.LEGACY)),
        Arguments.of("no rules list", RiskFixtures.ruleSet(1, "L", null)),
        Arguments.of("no version", RiskFixtures.ruleSet(null, "L", RiskFixtures.LEGACY)),
        Arguments.of(
            "an empty entry in the rules",
            "{\"version\":1,\"defaultTier\":\"L\",\"rules\":[null]}"),
        Arguments.of(
            "an empty rule object", "{\"version\":1,\"defaultTier\":\"L\",\"rules\":[{}]}"));
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] {0} is a 400 problem and the table is unchanged")
  @MethodSource("malformedBodies")
  void malformed(String what, String body) throws Exception {
    assertProblem(save(PemJwt.admin(), body), 400);

    assertLegacyTableUnchanged();
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a request without a body is a 400 problem")
  void noBody() throws Exception {
    MvcResult r =
        mvc.perform(put(RULES).with(PemJwt.admin()).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    assertProblem(r, 400);
    assertLegacyTableUnchanged();
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] the body {0} is a 400 and the table is unchanged")
  @ValueSource(
      strings = {
        "not json",
        "{\"version\":\"one\",\"defaultTier\":\"L\",\"rules\":[]}",
        "{\"version\":1,\"defaultTier\":\"L\",\"rules\":\"332=H\"}",
        "[]"
      })
  void unreadableBody(String body) throws Exception {
    MvcResult r = save(PemJwt.admin(), body);

    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertLegacyTableUnchanged();
  }

  @ParameterizedTest(
      name = "[MAM-38 AC-4] the unreadable body {0} is answered with a 400 problem+json body")
  @ValueSource(
      strings = {"not json", "{\"version\":\"one\",\"defaultTier\":\"L\",\"rules\":[]}", "[]"})
  void unreadableBodyIsAProblem(String body) throws Exception {
    assertProblem(save(PemJwt.admin(), body), 400);
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a malformed table is a 400 even when its version is stale")
  void malformedBeforeStale() throws Exception {
    assertProblem(save(PemJwt.admin(), RiskFixtures.ruleSet(7, "L", List.of("20=M"))), 400);
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-4] a well-formed table with only the default tier and no rules is accepted as version 2")
  void noRulesButDefaultTier() throws Exception {
    MvcResult r = save(PemJwt.admin(), RiskFixtures.ruleSet(1, "M", List.of()));

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("rules")).isEmpty();
    assertThat(body(r).path("defaultTier").asText()).isEqualTo("M");
  }

  // ---- AC-5: nobody is re-tiered ---------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-5] saving version 2 writes no event and no assessment: nobody is re-tiered")
  void savingReTiersNobody() throws Exception {
    Integer events = jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
    Integer assessments =
        jdbc.queryForObject("SELECT count(*) FROM risk_assessment", Integer.class);

    MvcResult r =
        save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class))
        .isEqualTo(events);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_assessment", Integer.class))
        .isEqualTo(assessments);
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-5] an assessment stored under version 1 keeps tier L and version 1 after version 2 is saved")
  void storedAssessmentKeepsItsVersion() throws Exception {
    jdbc.update("DELETE FROM risk_assessment WHERE npi = '1003000111'");
    jdbc.update(
        "INSERT INTO risk_assessment (npi, taxonomy, tier, rule_set_version, assessed_at)"
            + " VALUES ('1003000111', '207Q00000X', 'L', 1, now())");
    try {
      save(PemJwt.admin(), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

      assertThat(
              jdbc.queryForList(
                  "SELECT tier || '@' || rule_set_version FROM risk_assessment WHERE npi = '1003000111'",
                  String.class))
          .containsExactly("L@1");
    } finally {
      jdbc.update("DELETE FROM risk_assessment WHERE npi = '1003000111'");
    }
  }

  // ---- AC-6: roles -----------------------------------------------------------------------------

  static RequestPostProcessor token(String role) {
    return switch (role) {
      case "NONE" -> null;
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "NO_ROLE" -> PemJwt.user("nobody", null);
      default -> PemJwt.user("x", null, role);
    };
  }

  @ParameterizedTest(name = "[MAM-38 AC-6] PUT by {0} gives {1}")
  @CsvSource({
    "ADMIN,200",
    "SUPERVISOR,403",
    "ANALYST,403",
    "PROVIDER,403",
    "SYSTEM,403",
    "NO_ROLE,403",
    "NONE,401"
  })
  void putRoles(String role, int expected) throws Exception {
    MvcResult r = save(token(role), RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M")));

    assertThat(r.getResponse().getStatus()).isEqualTo(expected);
    if (expected == 403) {
      assertProblem(r, 403);
    }
    assertThat(tableInForce().path("version").asInt()).isEqualTo(expected == 200 ? 2 : 1);
  }

  @ParameterizedTest(name = "[MAM-38 AC-6] GET by {0} gives {1}")
  @CsvSource({
    "ANALYST,200",
    "SUPERVISOR,200",
    "ADMIN,200",
    "PROVIDER,403",
    "SYSTEM,403",
    "NO_ROLE,403",
    "NONE,401"
  })
  void getRoles(String role, int expected) throws Exception {
    MvcResult r = read(token(role));

    assertThat(r.getResponse().getStatus()).isEqualTo(expected);
    if (expected == 403) {
      assertProblem(r, 403);
    }
    if (expected == 200) {
      assertThat(body(r).path("version").asInt()).isEqualTo(1);
    }
  }
}
