package gov.medicaid.pem.revalidation.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Then;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Steps for MAM-49 (the states IN_SCREENING and CANCELLED of a revalidation case). */
public class StateSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private List<JsonNode> casesOf(String npi) throws Exception {
    MvcResult r =
        mvc.perform(
                get("/api/v1/revalidations")
                    .param("npi", npi)
                    .param("size", "200")
                    .with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    List<JsonNode> out = new ArrayList<>();
    json.readTree(r.getResponse().getContentAsString()).path("items").forEach(out::add);
    return out;
  }

  @Then(
      "the case of {string} opened by the sweep reaches status {string} once its RevalidationDue is published")
  public void reaches(String npi, String status) throws Exception {
    long end = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    String seen = null;
    while (System.nanoTime() < end) {
      List<JsonNode> cases = casesOf(npi);
      assertThat(cases).hasSize(1);
      seen = cases.get(0).path("status").asText();
      if (seen.equals(status)) {
        break;
      }
      Thread.sleep(250);
    }
    assertThat(seen).isEqualTo(status);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE type = 'RevalidationDue' AND subject = ?"
                    + " AND published_at IS NOT NULL",
                Integer.class,
                npi))
        .isEqualTo(1);
  }

  @Then("the case of {string} is CANCELLED with closedAt set")
  public void cancelled(String npi) throws Exception {
    List<JsonNode> cases = casesOf(npi);
    assertThat(cases).hasSize(1);
    assertThat(cases.get(0).path("status").asText()).isEqualTo("CANCELLED");
    assertThat(cases.get(0).path("closedAt").asText()).isNotBlank();
  }

  @Then("the cases of {string} are 1 open case due {string} and 1 CANCELLED case")
  public void reopened(String npi, String due) throws Exception {
    List<JsonNode> cases = casesOf(npi);
    assertThat(cases).hasSize(2);
    List<JsonNode> open =
        cases.stream()
            .filter(c -> List.of("DUE", "IN_SCREENING").contains(c.path("status").asText()))
            .toList();
    assertThat(open).hasSize(1);
    assertThat(open.get(0).path("dueDate").asText()).isEqualTo(due);
    assertThat(cases).filteredOn(c -> c.path("status").asText().equals("CANCELLED")).hasSize(1);
  }
}
