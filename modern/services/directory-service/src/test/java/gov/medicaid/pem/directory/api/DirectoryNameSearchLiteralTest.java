package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-63 AC-3 ("name contains", any case) on the public search GET /api/v1/directory/providers: the
 * searched name is plain text. A percent sign, an underscore or a backslash in it is a literal
 * character, never an SQL LIKE wildcard or escape, as in the FHIR search (JdbcFhirDirectory escapes
 * them). So {@code name=%} does not list every provider, and a name that contains a literal % or _
 * is found by that character and only by it. Real Postgres (Testcontainers).
 */
class DirectoryNameSearchLiteralTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    // the two ACTIVE names of MAM-63 AC-3; none of them holds a %, an _ or a backslash
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", null, null),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"));
  }

  /** Two more ACTIVE names, one with a literal % and one with a literal _ (synthetic). */
  void addNamesWithLiterals() {
    DirectoryFixtures.insert(
        jdbc,
        Row.organization(
            "1003000777", "M00000777", "GR", null, "A", "251E00000X", "PLAN 100% CARE"));
    DirectoryFixtures.insert(
        jdbc,
        Row.organization(
            "1003000888", "M00000888", "GR", null, "A", "251E00000X", "NORTH_SIDE CLINIC"));
  }

  JsonNode search(String name) throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/directory/providers").param("name", name)).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  static List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("npi").asText()));
    return out;
  }

  @ParameterizedTest(
      name =
          "[MAM-63 AC-3] the wildcard text <{0}> is taken literally and matches no name that holds it")
  @ValueSource(strings = {"%", "_", "R_VERA", "RIV%"})
  void wildcardTextMatchesNothing(String text) throws Exception {
    JsonNode page = search(text);
    assertThat(npis(page)).isEmpty();
    assertThat(page.path("total").asInt()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-63 AC-3] name=% finds only the name that holds a literal percent sign, not every name")
  void percentFindsOnlyTheNameWithAPercent() throws Exception {
    addNamesWithLiterals();
    JsonNode page = search("%");
    assertThat(npis(page)).containsExactly("1003000777");
    assertThat(page.path("total").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-63 AC-3] name=_ finds only the name that holds a literal underscore, not every name")
  void underscoreFindsOnlyTheNameWithAnUnderscore() throws Exception {
    addNamesWithLiterals();
    JsonNode page = search("_");
    assertThat(npis(page)).containsExactly("1003000888");
    assertThat(page.path("total").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-63 AC-3] name=\\ finds only the name that holds a literal backslash, the escape character")
  void backslashFindsOnlyTheNameWithABackslash() throws Exception {
    DirectoryFixtures.insert(
        jdbc,
        Row.organization(
            "1003000999", "M00000999", "GR", null, "A", "251E00000X", "ACME\\WEST CLINIC"));
    JsonNode page = search("\\");
    assertThat(npis(page)).containsExactly("1003000999");
    assertThat(page.path("total").asInt()).isEqualTo(1);
  }
}
