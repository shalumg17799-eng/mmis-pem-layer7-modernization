package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Steps for MAM-30 (the revalidation due date moves at approval and at a passed revalidation). */
public class RevalidationDueDateSteps {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;
  @Autowired private Clock clock;

  @Given("provider {string} is ACTIVE with screen flag {string} and revalidation due date {string}")
  public void active(String npi, String flag, String due) {
    jdbc.update(
        "DELETE FROM provider_owner WHERE provider_id IN (SELECT id FROM provider WHERE npi = ?)",
        npi);
    jdbc.update("DELETE FROM provider WHERE npi = ?", npi);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", npi);
    jdbc.update(
        "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, risk_tier, enroll_status, screen_flag,"
            + " effective_date, reval_due_date, last_name, first_name, license_no, license_state, version)"
            + " VALUES (?, ?, 'MCD000111', 'IN', '207Q00000X', 'L', 'A', ?, DATE '2021-03-31', ?, 'RIVERA', 'MARIA',"
            + " 'MD12345', 'TX', 0)",
        UUID.randomUUID(),
        npi,
        flag,
        java.sql.Date.valueOf(due));
  }

  @Then("provider {string} now has screen flag {string}")
  public void screenFlag(String npi, String flag) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.system())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("screenFlag").asText())
        .isEqualTo(flag);
  }

  @Then(
      "{int} ProviderStatusChanged event for {string} goes from {string} to {string} with the service clock date plus 5 years as due date")
  public void announced(int count, String npi, String from, String to) throws Exception {
    List<JsonNode> events = new ArrayList<>();
    for (String p :
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ProviderStatusChanged' AND subject = ?",
            String.class,
            npi)) {
      events.add(json.readTree(p));
    }
    assertThat(events).hasSize(count);
    JsonNode d = events.get(0).path("data");
    assertThat(d.path("npi").asText()).isEqualTo(npi);
    assertThat(d.path("from").asText()).isEqualTo(from);
    assertThat(d.path("to").asText()).isEqualTo(to);
    assertThat(d.path("revalidationDueDate").asText())
        .isEqualTo(LocalDate.now(clock).plusYears(5).toString());
  }
}
