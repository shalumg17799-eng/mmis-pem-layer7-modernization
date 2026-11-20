package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.ContractFile;
import gov.medicaid.pem.sanctions.IntegrationTest;
import gov.medicaid.pem.sanctions.application.SanctionHistory;
import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/**
 * Lane 3 tests for MAM-52 AC-8 (the sanction history is readable), which the SANCIN intake of
 * MAM-55 fills: GET /api/v1/sanctions as the contract's listSanctions defines it. The history lists
 * every provider's sanctions when no NPI is given, newest first, one page at a time (page from 0,
 * size 1 to 200, default 20); an NPI that is not 10 digits, a negative page or a size outside 1 to
 * 200 is a 400 problem+json.
 */
class SanctionListApiTest extends IntegrationTest {

  static final String BASE = "/api/v1/sanctions";
  static final Instant T0 = Instant.parse("2026-03-31T09:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired SanctionHistory history;

  @BeforeEach
  void threeSanctions() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    add("1003000111", SanctionAction.T, T0);
    add("1003000333", SanctionAction.S, T0.plusSeconds(60));
    add("1003000222", SanctionAction.R, T0.plusSeconds(120));
  }

  void add(String npi, SanctionAction action, Instant at) {
    history.add(
        new Sanction(
            UUID.randomUUID(),
            npi,
            action,
            "SYNTHETIC TEST REASON",
            LocalDate.of(2026, 3, 31),
            "SANCIN",
            "admin1",
            at));
  }

  MvcResult list(String query) throws Exception {
    return mvc.perform(get(BASE + query).with(PemJwt.analyst())).andReturn();
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("npi").asText()));
    return out;
  }

  // ---- what is listed ---------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-52 AC-8] without an NPI the history of every provider is listed, newest first")
  void everyProviderNewestFirst() throws Exception {
    MvcResult r = list("");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode page = body(r);
    assertThat(npis(page)).containsExactly("1003000222", "1003000333", "1003000111");
    assertThat(page.path("total").asInt()).isEqualTo(3);
    assertThat(page.path("page").asInt()).isZero();
    assertThat(page.path("size").asInt()).isEqualTo(20);
  }

  @Test
  @DisplayName("[MAM-52 AC-8] an NPI lists only that provider's sanctions")
  void oneProvider() throws Exception {
    JsonNode page = body(list("?npi=1003000333"));
    assertThat(npis(page)).containsExactly("1003000333");
    assertThat(page.path("total").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-52 AC-8] an NPI with no sanction lists an empty page with total 0")
  void noSanctions() throws Exception {
    JsonNode page = body(list("?npi=1003000999"));
    assertThat(page.path("items")).isEmpty();
    assertThat(page.path("total").asInt()).isZero();
  }

  @ParameterizedTest(name = "[MAM-52 AC-8] page {0} of size {1} holds {2}")
  @CsvSource({
    "0, 1, 1003000222",
    "1, 1, 1003000333",
    "2, 1, 1003000111",
    "0, 2, '1003000222 1003000333'",
    "1, 2, 1003000111"
  })
  void paging(int page, int size, String expected) throws Exception {
    JsonNode body = body(list("?page=" + page + "&size=" + size));
    assertThat(npis(body)).containsExactly(expected.split(" "));
    assertThat(body.path("page").asInt()).isEqualTo(page);
    assertThat(body.path("size").asInt()).isEqualTo(size);
    assertThat(body.path("total").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("[MAM-52 AC-8] a page beyond the last is empty but still reports the total")
  void pageBeyondTheEnd() throws Exception {
    JsonNode page = body(list("?page=5&size=20"));
    assertThat(page.path("items")).isEmpty();
    assertThat(page.path("total").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName("[MAM-52 AC-8] the answer is a SanctionPage of the contract")
  void contractPage() throws Exception {
    assertThat(ContractFile.violations("SanctionPage", body(list("")))).isEmpty();
  }

  // ---- what is refused --------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-52 AC-8] the NPI \"{0}\" is not 10 digits: 400 problem+json")
  @ValueSource(strings = {"ABC", "100300011", "10030001111", "100300011A"})
  void invalidNpi(String npi) throws Exception {
    MvcResult r = list("?npi=" + npi);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("NPI");
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-52 AC-8] {0} is outside the contract's limits: 400 problem+json")
  @ValueSource(strings = {"page=-1", "size=0", "size=201", "size=-5", "page=abc", "size=abc"})
  void invalidPaging(String query) throws Exception {
    MvcResult r = list("?" + query);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @ParameterizedTest(name = "[MAM-52 AC-8] {0} is inside the contract's limits: 200")
  @ValueSource(strings = {"page=0", "size=1", "size=200", "page=0&size=200"})
  void validPaging(String query) throws Exception {
    assertThat(list("?" + query).getResponse().getStatus()).isEqualTo(200);
  }
}
