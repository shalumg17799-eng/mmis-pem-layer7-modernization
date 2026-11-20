package gov.medicaid.pem.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.risk.IntegrationTest;
import gov.medicaid.pem.risk.RiskFixtures;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-41 through the real application (Postgres and Kafka from Testcontainers): POST
 * /api/v1/risk-assessments with signed-in users: the tier of the table in force, the first three
 * characters only, blank or short gets the default, 400 for a missing or too long taxonomy, no side
 * effect, roles (401/403) and the contract risk-service.yaml (assessRisk).
 */
class RiskAssessmentsApiTest extends IntegrationTest {

  static final String ASSESS = "/api/v1/risk-assessments";
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

  private MvcResult ask(RequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder req =
        post(ASSESS).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(who == null ? req : req.with(who)).andReturn();
  }

  private MvcResult assess(String taxonomy) throws Exception {
    return ask(PemJwt.analyst(), RiskFixtures.assessmentRequest(taxonomy, null));
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode p = body(r);
    assertThat(p.path("status").asInt()).isEqualTo(status);
    assertThat(p.path("title").asText()).isNotBlank();
  }

  private void saveVersion2With207AsM() throws Exception {
    MvcResult r =
        mvc.perform(
                put("/api/v1/risk-rules")
                    .with(PemJwt.admin())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(RiskFixtures.ruleSet(1, "L", RiskFixtures.legacyPlus("207=M"))))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  // ---- AC-1: prefixes in the table -------------------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-41 AC-1] taxonomy {0} is assessed as {1} under rule set version 1")
  @CsvSource({
    "332B00000X,H",
    "251E00000X,H",
    "341600000X,M",
    "291U00000X,M",
    "225100000X,M",
    "207Q00000X,L"
  })
  void tableTiers(String taxonomy, String tier) throws Exception {
    MvcResult r = assess(taxonomy);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode a = body(r);
    assertThat(a.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(a.path("tier").asText()).isEqualTo(tier);
    assertThat(a.path("ruleSetVersion").asInt()).isEqualTo(1);
    assertThat(a.has("npi")).as("no npi was asked").isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-1] the response matches the contract (assessRisk 200) and is stamped with a time")
  void matchesContract() throws Exception {
    Instant before = Instant.now().minusSeconds(1);

    MvcResult r = assess("332B00000X");

    Instant after = Instant.now().plusSeconds(1);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    CONTRACT.assertResponse("assessRisk", 200, r.getResponse().getContentAsString());
    assertThat(Instant.parse(body(r).path("assessedAt").asText())).isBetween(before, after);
  }

  // ---- AC-2: only the first three characters count ---------------------------------------------

  @ParameterizedTest(name = "[MAM-41 AC-2] taxonomy {0} is assessed as H")
  @ValueSource(strings = {"3320000000", "332ZZZZZZZ", "332"})
  void firstThreeOnly(String taxonomy) throws Exception {
    MvcResult r = assess(taxonomy);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("tier").asText()).isEqualTo("H");
  }

  @Test
  @DisplayName("[MAM-41 AC-2] 332 elsewhere in the code does not count: X332000000 is Limited")
  void notAtTheStart() throws Exception {
    assertThat(body(assess("X332000000")).path("tier").asText()).isEqualTo("L");
  }

  // ---- AC-3: blank or too short ----------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-41 AC-3] taxonomy \"{0}\" gets 200 and the default tier L")
  @ValueSource(strings = {"", "   ", "          ", "33", "3"})
  void blankOrShort(String taxonomy) throws Exception {
    MvcResult r = assess(taxonomy);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode a = body(r);
    assertThat(a.path("tier").asText()).isEqualTo("L");
    assertThat(a.path("taxonomy").asText()).isEqualTo(taxonomy);
    assertThat(a.path("ruleSetVersion").asInt()).isEqualTo(1);
  }

  // ---- AC-4: missing or too long ---------------------------------------------------------------

  @Test
  @DisplayName("[MAM-41 AC-4] no taxonomy at all is a 400 problem")
  void noTaxonomy() throws Exception {
    assertProblem(ask(PemJwt.analyst(), RiskFixtures.assessmentRequest(null, null)), 400);
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a taxonomy that is null is a 400 problem")
  void nullTaxonomy() throws Exception {
    assertProblem(ask(PemJwt.analyst(), "{\"taxonomy\":null}"), 400);
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a request without a body is a 400 problem")
  void noBody() throws Exception {
    MvcResult r =
        mvc.perform(post(ASSESS).with(PemJwt.analyst()).contentType(MediaType.APPLICATION_JSON))
            .andReturn();

    assertProblem(r, 400);
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a taxonomy of 11 characters is a 400 problem")
  void tooLong() throws Exception {
    MvcResult r = assess("332B00000XY");

    assertProblem(r, 400);
    CONTRACT.assertResponse(
        "assessRisk", 400, RiskFixtures.withoutInstance(r.getResponse().getContentAsString()));
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a taxonomy of exactly 10 characters is accepted")
  void tenCharacters() throws Exception {
    assertThat(assess("332B00000X").getResponse().getStatus()).isEqualTo(200);
  }

  @ParameterizedTest(
      name = "[MAM-41 AC-4] the NPI \"{0}\" is a 400 problem (10 digits are required)")
  @ValueSource(strings = {"123", "100300011", "10030001111", "100300011A", ""})
  void badNpi(String npi) throws Exception {
    assertProblem(ask(PemJwt.analyst(), RiskFixtures.assessmentRequest("332B00000X", npi)), 400);
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a body that is not JSON is a 400")
  void notJson() throws Exception {
    assertThat(ask(PemJwt.analyst(), "not json").getResponse().getStatus()).isEqualTo(400);
  }

  @ParameterizedTest(
      name = "[MAM-41 AC-4] the unreadable body {0} is answered with a 400 problem+json body")
  @ValueSource(strings = {"not json", "{\"taxonomy\":[\"332\"]}", "[]"})
  void unreadableBodyIsAProblem(String body) throws Exception {
    assertProblem(ask(PemJwt.analyst(), body), 400);
  }

  // ---- AC-5: no side effect --------------------------------------------------------------------

  private double tiersAssigned() throws Exception {
    MvcResult r =
        mvc.perform(get("/actuator/metrics/pem.risk.tiers.assigned").with(PemJwt.admin()))
            .andReturn();
    if (r.getResponse().getStatus() == 404) {
      return 0;
    }
    return json.readTree(r.getResponse().getContentAsString())
        .path("measurements")
        .get(0)
        .path("value")
        .asDouble();
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-5] with and without an NPI the tier is H and nothing is stored, written to the outbox or counted")
  void noSideEffect() throws Exception {
    Integer outbox = jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class);
    Integer stored = jdbc.queryForObject("SELECT count(*) FROM risk_assessment", Integer.class);
    Integer processed = jdbc.queryForObject("SELECT count(*) FROM processed_event", Integer.class);
    double counted = tiersAssigned();

    MvcResult without = assess("332B00000X");
    MvcResult with =
        ask(PemJwt.analyst(), RiskFixtures.assessmentRequest("332B00000X", "1003000111"));

    assertThat(body(without).path("tier").asText()).isEqualTo("H");
    assertThat(body(with).path("tier").asText()).isEqualTo("H");
    assertThat(body(with).path("npi").asText()).isEqualTo("1003000111");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event", Integer.class))
        .isEqualTo(outbox);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_assessment", Integer.class))
        .isEqualTo(stored);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM processed_event", Integer.class))
        .isEqualTo(processed);
    assertThat(tiersAssigned()).isEqualTo(counted);
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-5] a question about NPI 1003000111 leaves what is stored for that NPI as it was")
  void storedTierOfTheNpiUntouched() throws Exception {
    jdbc.update("DELETE FROM risk_assessment WHERE npi = '1003000111'");
    jdbc.update(
        "INSERT INTO risk_assessment (npi, taxonomy, tier, rule_set_version, assessed_at)"
            + " VALUES ('1003000111', '207Q00000X', 'L', 1, now())");
    try {
      ask(PemJwt.analyst(), RiskFixtures.assessmentRequest("332B00000X", "1003000111"));

      assertThat(
              jdbc.queryForList(
                  "SELECT tier || '@' || rule_set_version FROM risk_assessment WHERE npi = '1003000111'",
                  String.class))
          .containsExactly("L@1");
    } finally {
      jdbc.update("DELETE FROM risk_assessment WHERE npi = '1003000111'");
    }
  }

  // ---- AC-6: the table in force ----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-41 AC-6] with version 2 in force and 207 as M, 207Q00000X is M under version 2")
  void usesVersion2() throws Exception {
    assertThat(body(assess("207Q00000X")).path("tier").asText()).isEqualTo("L");
    saveVersion2With207AsM();

    JsonNode a = body(assess("207Q00000X"));

    assertThat(a.path("tier").asText()).isEqualTo("M");
    assertThat(a.path("ruleSetVersion").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-41 AC-6] the legacy prefixes keep their tiers under version 2")
  void legacyPrefixesUnderVersion2() throws Exception {
    saveVersion2With207AsM();

    assertThat(body(assess("332B00000X")).path("tier").asText()).isEqualTo("H");
    assertThat(body(assess("341600000X")).path("tier").asText()).isEqualTo("M");
    assertThat(body(assess("999Z00000X")).path("tier").asText()).isEqualTo("L");
    assertThat(body(assess("999Z00000X")).path("ruleSetVersion").asInt()).isEqualTo(2);
  }

  // ---- AC-7: roles -----------------------------------------------------------------------------

  static RequestPostProcessor token(String role) {
    return switch (role) {
      case "NONE" -> null;
      case "PROVIDER" -> PemJwt.provider("1003000111");
      case "NO_ROLE" -> PemJwt.user("nobody", null);
      default -> PemJwt.user("x", null, role);
    };
  }

  @ParameterizedTest(name = "[MAM-41 AC-7] {0} asking for 207Q00000X gets {1}")
  @CsvSource({
    "ANALYST,200",
    "SUPERVISOR,200",
    "SYSTEM,200",
    "PROVIDER,403",
    "ADMIN,403",
    "NO_ROLE,403",
    "NONE,401"
  })
  void roles(String role, int expected) throws Exception {
    MvcResult r = ask(token(role), RiskFixtures.assessmentRequest("207Q00000X", null));

    assertThat(r.getResponse().getStatus()).isEqualTo(expected);
    if (expected == 200) {
      assertThat(body(r).path("tier").asText()).isEqualTo("L");
    }
    if (expected == 403) {
      assertProblem(r, 403);
      CONTRACT.assertResponse(
          "assessRisk", 403, RiskFixtures.withoutInstance(r.getResponse().getContentAsString()));
    }
  }
}
