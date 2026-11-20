package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MAM-37 on the real application and database: the search filters (exact NPI, exact Medicaid ID,
 * name contains, status, screen flag), paging, the refusals (400), the roles, and the literal
 * treatment of % _ and the backslash in a name (D-022, D-023).
 */
class ProviderSearchApiTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    // stored out of NPI order on purpose
    ProviderRows.individual("1003000444")
        .medicaidId("MCD000444")
        .name("OKAFOR", "JAMES")
        .status("P")
        .flag("F")
        .insert(jdbc);
    ProviderRows.individual("1003000111")
        .medicaidId("MCD000111")
        .name("RIVERA", "MARIA")
        .status("P")
        .flag("P")
        .insert(jdbc);
    ProviderRows.group("1003000777")
        .type("FA")
        .medicaidId("MCD000777")
        .legalName("SAMPLE HOSPITAL 01")
        .dbaName("SAMPLE HOSP")
        .status("A")
        .flag("P")
        .insert(jdbc);
    ProviderRows.individual("1003000222")
        .medicaidId("MCD000222")
        .name("LINDQVIST", "ASTRID")
        .status("T")
        .flag("P")
        .end(LocalDate.of(2026, 3, 31))
        .insert(jdbc);
  }

  private MvcResult search(String... paramsAsNameValue) throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/providers").with(PemJwt.analyst());
    for (int i = 0; i < paramsAsNameValue.length; i += 2) {
      req = req.param(paramsAsNameValue[i], paramsAsNameValue[i + 1]);
    }
    return mvc.perform(req).andReturn();
  }

  private JsonNode ok(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("npi").asText()));
    return out;
  }

  // ---- AC-5: NPI and Medicaid ID are exact ------------------------------------------------------

  @Test
  @DisplayName("[MAM-37 AC-5] the Medicaid ID is matched exactly: a prefix or another case finds nothing")
  void medicaidIdIsExact() throws Exception {
    assertThat(npis(ok(search("medicaidId", "MCD000111")))).containsExactly("1003000111");
    assertThat(npis(ok(search("medicaidId", "MCD00011")))).isEmpty();
    assertThat(npis(ok(search("medicaidId", "mcd000111")))).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-5] a Medicaid ID shorter than its 9-character column is found by its own text (padding is ignored)")
  void shortMedicaidId() throws Exception {
    ProviderRows.individual("1003000555").medicaidId("MCD5").insert(jdbc);

    assertThat(npis(ok(search("medicaidId", "MCD5")))).containsExactly("1003000555");
  }

  @Test
  @DisplayName("[MAM-37 AC-5] the NPI is matched exactly and an NPI that is not on file gives an empty page")
  void npiExact() throws Exception {
    JsonNode found = ok(search("npi", "1003000444"));
    JsonNode none = ok(search("npi", "1003000999"));

    assertThat(npis(found)).containsExactly("1003000444");
    assertThat(found.path("total").asInt()).isEqualTo(1);
    assertThat(none.path("items").isArray()).isTrue();
    assertThat(none.path("items").size()).isZero();
    assertThat(none.path("total").asInt()).isZero();
  }

  @Test
  @DisplayName("[MAM-37 AC-5] a search answer keeps to the ProviderPage contract, item by item")
  void searchAnswerMatchesContract() throws Exception {
    MvcResult r = search("status", "P");

    OpenApiContract.of("provider-service")
        .assertResponse("searchProviders", 200, r.getResponse().getContentAsString());
  }

  @ParameterizedTest(name = "npi={0} is refused")
  @ValueSource(strings = {"12AB", "100300011", "10030001111", "1003 00111", " "})
  @DisplayName("[MAM-37 AC-5] an NPI that is not 10 digits is a 400 problem")
  void malformedNpi(String npi) throws Exception {
    mvc.perform(get("/api/v1/providers").param("npi", npi).with(PemJwt.analyst()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @ParameterizedTest(name = "medicaidId=[{0}] is refused")
  @ValueSource(strings = {"", " ", "ABCDEFGHIJ"})
  @DisplayName("[MAM-37 AC-5] a Medicaid ID that is empty or longer than 9 characters is a 400 problem")
  void malformedMedicaidId(String id) throws Exception {
    mvc.perform(get("/api/v1/providers").param("medicaidId", id).with(PemJwt.analyst()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("[MAM-37 AC-5] a Medicaid ID of exactly 9 characters is accepted")
  void nineCharacterMedicaidId() throws Exception {
    assertThat(npis(ok(search("medicaidId", "MCD000999")))).isEmpty();
  }

  // ---- AC-6: name, status, screen flag, paging ----------------------------------------------------

  @Test
  @DisplayName("[MAM-37 AC-6] status and screen flag are exact filters that combine with AND")
  void statusAndScreenFlag() throws Exception {
    assertThat(npis(ok(search("status", "P", "screenFlag", "P")))).containsExactly("1003000111");
    assertThat(npis(ok(search("status", "P", "screenFlag", "F")))).containsExactly("1003000444");
    assertThat(npis(ok(search("status", "P")))).containsExactly("1003000111", "1003000444");
    assertThat(npis(ok(search("screenFlag", "P"))))
        .containsExactly("1003000111", "1003000222", "1003000777");
    assertThat(npis(ok(search("status", "T")))).containsExactly("1003000222");
    assertThat(npis(ok(search("status", "S")))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-37 AC-6] a name matches any part of the last, first, legal or DBA name, ignoring case")
  void nameContains() throws Exception {
    assertThat(npis(ok(search("name", "RIVERA")))).containsExactly("1003000111");
    assertThat(npis(ok(search("name", "river")))).containsExactly("1003000111");
    assertThat(npis(ok(search("name", "maria")))).containsExactly("1003000111");
    assertThat(npis(ok(search("name", "hospital")))).containsExactly("1003000777");
    assertThat(npis(ok(search("name", "samp hosp")))).isEmpty();
    assertThat(npis(ok(search("name", "SAMPLE HOSP")))).containsExactly("1003000777");
    assertThat(npis(ok(search("name", "  rivera  ")))).containsExactly("1003000111");
  }

  @Test
  @DisplayName("[MAM-37 AC-6] a name filter and a status filter must both match")
  void nameAndStatus() throws Exception {
    assertThat(npis(ok(search("name", "RIVERA", "status", "P")))).containsExactly("1003000111");
    assertThat(npis(ok(search("name", "RIVERA", "status", "A")))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-37 AC-6] an empty or blank name is no name filter")
  void blankNameIsNoFilter() throws Exception {
    assertThat(ok(search("name", "")).path("total").asInt()).isEqualTo(4);
    assertThat(ok(search("name", "   ")).path("total").asInt()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-6] % and _ in a name are plain characters, not wildcards: they find only a name that holds them")
  void wildcardsAreLiteral() throws Exception {
    ProviderRows.group("1003000511").legalName("50% OFF CLINIC").dbaName("HALF PRICE").insert(jdbc);
    ProviderRows.group("1003000522").legalName("A_B HEALTH").dbaName("AB HEALTH").insert(jdbc);
    ProviderRows.group("1003000533").legalName("AXB HEALTH").dbaName("AXB").insert(jdbc);

    assertThat(npis(ok(search("name", "%")))).containsExactly("1003000511");
    assertThat(npis(ok(search("name", "50%")))).containsExactly("1003000511");
    assertThat(npis(ok(search("name", "_")))).containsExactly("1003000522");
    assertThat(npis(ok(search("name", "A_B")))).containsExactly("1003000522");
    assertThat(npis(ok(search("name", "R_VERA")))).isEmpty();
    assertThat(npis(ok(search("name", "%RIVERA")))).isEmpty();
    assertThat(npis(ok(search("name", "RIV%")))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-37 AC-6] a backslash in a name is a plain character, and cannot be used to escape a wildcard")
  void backslashIsLiteral() throws Exception {
    ProviderRows.group("1003000544").legalName("C\\D CLINIC").dbaName("CD").insert(jdbc);
    ProviderRows.group("1003000555").legalName("100% FREE").dbaName("FREE").insert(jdbc);

    assertThat(npis(ok(search("name", "\\")))).containsExactly("1003000544");
    assertThat(npis(ok(search("name", "C\\D")))).containsExactly("1003000544");
    assertThat(npis(ok(search("name", "\\%")))).isEmpty();
    assertThat(npis(ok(search("name", "100\\%")))).isEmpty();
    assertThat(npis(ok(search("name", "100%")))).containsExactly("1003000555");
  }

  @ParameterizedTest(name = "status={0} screenFlag={1} is refused")
  @CsvSource({"X,", "p,", "'',", ",Z", ",u", "PP,"})
  @DisplayName("[MAM-37 AC-6] a status or screen flag that is not one of the contract codes is a 400 problem")
  void unknownCodes(String statusCode, String flagCode) throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/providers").with(PemJwt.analyst());
    if (statusCode != null) {
      req = req.param("status", statusCode);
    }
    if (flagCode != null) {
      req = req.param("screenFlag", flagCode);
    }
    mvc.perform(req).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("[MAM-37 AC-6] with no filter and no paging the answer is page 0 of size 20, in ascending NPI order")
  void defaultsAndOrder() throws Exception {
    JsonNode page = ok(search());

    assertThat(page.path("page").asInt()).isZero();
    assertThat(page.path("size").asInt()).isEqualTo(20);
    assertThat(page.path("total").asInt()).isEqualTo(4);
    assertThat(npis(page))
        .containsExactly("1003000111", "1003000222", "1003000444", "1003000777");
  }

  @Test
  @DisplayName("[MAM-37 AC-6] pages are cut from the NPI order and the total counts every match")
  void pagesFollowTheOrder() throws Exception {
    JsonNode first = ok(search("page", "0", "size", "3"));
    JsonNode second = ok(search("page", "1", "size", "3"));
    JsonNode beyond = ok(search("page", "5", "size", "3"));

    assertThat(npis(first)).containsExactly("1003000111", "1003000222", "1003000444");
    assertThat(npis(second)).containsExactly("1003000777");
    assertThat(second.path("page").asInt()).isEqualTo(1);
    assertThat(second.path("size").asInt()).isEqualTo(3);
    assertThat(second.path("total").asInt()).isEqualTo(4);
    assertThat(npis(beyond)).isEmpty();
    assertThat(beyond.path("total").asInt()).isEqualTo(4);
  }

  @Test
  @DisplayName("[MAM-37 AC-6] the largest page size is 200; 0, 201, a negative page or text is a 400 problem")
  void pagingLimits() throws Exception {
    assertThat(ok(search("size", "200")).path("size").asInt()).isEqualTo(200);
    assertThat(ok(search("size", "1")).path("items").size()).isEqualTo(1);
    for (String[] bad :
        new String[][] {
          {"size", "0"}, {"size", "201"}, {"size", "-1"}, {"page", "-1"}, {"size", "abc"}, {"page", "x"}
        }) {
      MvcResult r = search(bad);
      assertThat(r.getResponse().getStatus()).as(bad[0] + "=" + bad[1]).isEqualTo(400);
      assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    }
  }

  // ---- AC-1, AC-4: what a record shows ------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-37 AC-1] an organisation shows its legal name, DBA name and owner NPI, and no individual part")
  void organisationView() throws Exception {
    mvc.perform(get("/api/v1/providers/1003000777").with(PemJwt.analyst()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.providerType").value("FA"))
        .andExpect(jsonPath("$.organization.legalName").value("SAMPLE HOSPITAL 01"))
        .andExpect(jsonPath("$.organization.dbaName").value("SAMPLE HOSP"))
        .andExpect(jsonPath("$.organization.ownerNpi").value("1990000111"))
        .andExpect(jsonPath("$.individual").doesNotExist());
  }

  @Test
  @DisplayName("[MAM-37 AC-1] the end date of a terminated provider is returned, and no end date is not shown")
  void endDate() throws Exception {
    mvc.perform(get("/api/v1/providers/1003000222").with(PemJwt.analyst()))
        .andExpect(jsonPath("$.endDate").value("2026-03-31"))
        .andExpect(jsonPath("$.enrollStatus").value("T"));
    mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.analyst()))
        .andExpect(jsonPath("$.endDate").doesNotExist())
        .andExpect(jsonPath("$.riskTier").doesNotExist());
  }

  @Test
  @DisplayName("[MAM-37 AC-1] a single record keeps to the Provider contract, field by field")
  void recordMatchesContract() throws Exception {
    for (String npi : new String[] {"1003000111", "1003000222", "1003000777"}) {
      MvcResult r = mvc.perform(get("/api/v1/providers/" + npi).with(PemJwt.analyst())).andReturn();
      OpenApiContract.of("provider-service")
          .assertResponse("getProvider", 200, r.getResponse().getContentAsString());
    }
  }

  // ---- AC-3: nothing is carried between requests ----------------------------------------------------

  @Test
  @DisplayName("[MAM-37 AC-3] a lookup creates no session and sets no cookie: nothing is carried to the next request")
  void noSessionIsCreated() throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.analyst())).andReturn();

    assertThat(r.getRequest().getSession(false)).isNull();
    assertThat(r.getResponse().getHeaders("Set-Cookie")).isEmpty();
  }

  @Test
  @DisplayName("[MAM-37 AC-3] the same caller reading A, then B, then an unknown NPI gets A, B and 404, in any order")
  void noCarryOver() throws Exception {
    String[] order = {"1003000444", "1003000999", "1003000111", "1003000999", "1003000444"};
    for (String npi : order) {
      MvcResult r = mvc.perform(get("/api/v1/providers/" + npi).with(PemJwt.analyst())).andReturn();
      if (npi.equals("1003000999")) {
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(r.getResponse().getContentAsString()).doesNotContain("1003000444").doesNotContain("1003000111");
      } else {
        assertThat(json.readTree(r.getResponse().getContentAsString()).path("npi").asText())
            .isEqualTo(npi);
      }
    }
  }

  // ---- AC-7: roles ------------------------------------------------------------------------------------

  @ParameterizedTest(name = "{0} may search")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "ADMIN", "SYSTEM"})
  @DisplayName("[MAM-37 AC-7] staff roles and SYSTEM may search")
  void staffMaySearch(String role) throws Exception {
    mvc.perform(get("/api/v1/providers").with(PemJwt.user("x", null, role)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(4));
  }

  @Test
  @DisplayName("[MAM-37 AC-7] a PROVIDER cannot search, even with a token that carries their own NPI")
  void providerCannotSearch() throws Exception {
    mvc.perform(get("/api/v1/providers").param("npi", "1003000111").with(PemJwt.provider("1003000111")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403));
  }

  @Test
  @DisplayName("[MAM-37 AC-7] a user with no PEM role gets 403 on search and on read")
  void noRole() throws Exception {
    mvc.perform(get("/api/v1/providers").with(PemJwt.user("nobody", null)))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.user("nobody", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("[MAM-37 AC-7] no token gives a 401 problem+json on search and on read")
  void noToken() throws Exception {
    for (String url : new String[] {"/api/v1/providers", "/api/v1/providers/1003000111"}) {
      MvcResult r = mvc.perform(get(url)).andReturn();
      assertThat(r.getResponse().getStatus()).as(url).isEqualTo(401);
      assertThat(r.getResponse().getContentType()).as(url).startsWith("application/problem+json");
    }
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-7] a PROVIDER whose token has no npi claim cannot read any provider, and is not told whether one exists")
  void providerWithoutNpiClaim() throws Exception {
    mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.user("p", null, "PROVIDER")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/providers/1003000999").with(PemJwt.user("p", null, "PROVIDER")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-7] a PROVIDER gets 403 for another NPI whether or not it exists, and 200 for their own")
  void providerOwnRecordOnly() throws Exception {
    mvc.perform(get("/api/v1/providers/1003000111").with(PemJwt.provider("1003000111")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enrollStatus").value("P"));
    mvc.perform(get("/api/v1/providers/1003000444").with(PemJwt.provider("1003000111")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/providers/1003000999").with(PemJwt.provider("1003000111")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("[MAM-37 AC-2] an unknown NPI is a 404 problem+json that names only the NPI asked for")
  void unknownNpiProblem() throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/1003000999").with(PemJwt.analyst())).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(404);
    assertThat(problem.path("detail").asText()).contains("1003000999");
    assertThat(problem.has("npi")).isFalse();
    assertThat(r.getResponse().getContentAsString()).doesNotContain("RIVERA").doesNotContain("OKAFOR");
  }
}
