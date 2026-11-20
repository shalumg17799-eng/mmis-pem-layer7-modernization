package gov.medicaid.pem.directory.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Steps for MAM-63 (public provider directory search). */
public class DirectorySearchSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private MvcResult result;

  private void insert(
      String npi,
      String medicaidId,
      String type,
      String tier,
      String status,
      String name,
      String taxonomy) {
    jdbc.update(
        "INSERT INTO directory_entry (npi, medicaid_id, provider_type, risk_tier, enroll_status, display_name, taxonomy)"
            + " VALUES (?, ?, ?, ?, ?, ?, ?)",
        npi,
        medicaidId,
        type,
        tier == null || tier.isBlank() ? null : tier,
        status,
        name,
        taxonomy);
  }

  @Given("the directory holds:")
  public void holds(DataTable table) {
    jdbc.update("DELETE FROM directory_entry");
    for (Map<String, String> r : table.asMaps()) {
      insert(
          r.get("npi"),
          r.get("medicaidId"),
          r.get("type"),
          r.get("tier"),
          r.get("status"),
          r.get("name"),
          r.get("taxonomy"));
    }
  }

  @Given("the directory holds 25 ACTIVE entries with NPI 1003001001 to 1003001025 and no others")
  public void twentyFive() {
    jdbc.update("DELETE FROM directory_entry");
    // inserted in reverse so that the order of the answer cannot come from the insertion order
    for (int i = 25; i >= 1; i--) {
      String npi = String.format("10030010%02d", i);
      insert(
          npi, "M" + npi.substring(2), "IN", "L", "A", "SAMPLE PROVIDER " + (26 - i), "207Q00000X");
    }
  }

  @When("anyone searches the directory with {string}")
  public void search(String query) throws Exception {
    result =
        mvc.perform(
                MockMvcRequestBuilders.get(
                    "/api/v1/directory/providers" + (query.isEmpty() ? "" : "?" + query)))
            .andReturn();
  }

  private JsonNode page() throws Exception {
    assertThat(result.getResponse().getStatus())
        .as(result.getResponse().getContentAsString())
        .isEqualTo(200);
    assertThat(result.getResponse().getContentType()).startsWith("application/json");
    return json.readTree(result.getResponse().getContentAsString());
  }

  private static List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("npi").asText()));
    return out;
  }

  @Then("the search answers 200 with the NPIs {string}, page {int}, size {int} and total {int}")
  public void answers(String npis, int page, int size, int total) throws Exception {
    JsonNode p = page();
    List<String> expected = npis.isEmpty() ? List.of() : Arrays.asList(npis.split(","));
    assertThat(npis(p)).containsExactlyElementsOf(expected);
    assertThat(p.path("page").asInt()).isEqualTo(page);
    assertThat(p.path("size").asInt()).isEqualTo(size);
    assertThat(p.path("total").asInt()).isEqualTo(total);
  }

  @Then(
      "the search answers 200 with the NPIs {long} to {long}, page {int}, size {int} and total {int}")
  public void range(long from, long to, int page, int size, int total) throws Exception {
    JsonNode p = page();
    List<String> expected =
        IntStream.rangeClosed(0, (int) (to - from))
            .mapToObj(i -> String.valueOf(from + i))
            .toList();
    assertThat(npis(p)).containsExactlyElementsOf(expected);
    assertThat(p.path("page").asInt()).isEqualTo(page);
    assertThat(p.path("size").asInt()).isEqualTo(size);
    assertThat(p.path("total").asInt()).isEqualTo(total);
  }

  @Then("the search answers 400 problem+json")
  public void badRequest() {
    assertThat(result.getResponse().getStatus()).isEqualTo(400);
    assertThat(result.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Then(
      "every item has exactly the fields npi, medicaidId, providerType, riskTier, displayName and taxonomy")
  public void fields() throws Exception {
    JsonNode p = page();
    assertThat(p.path("items")).isNotEmpty();
    p.path("items")
        .forEach(
            i -> {
              List<String> names = new ArrayList<>();
              i.fieldNames().forEachRemaining(names::add);
              assertThat(names)
                  .containsExactlyInAnyOrder(
                      "npi", "medicaidId", "providerType", "riskTier", "displayName", "taxonomy");
            });
  }
}
