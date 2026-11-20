package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * How the two write endpoints answer a bad request (lessons of the earlier reviews): a date must be
 * an ISO date, an unreadable body is a 400 problem, a missing token a 401 problem and a missing role
 * a 403 problem, and nothing is changed by a refused request.
 */
class ProviderWriteInputTest extends IntegrationTest {

  static final String NPI = "1003000111";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual(NPI).status("A").flag("P").riskTier("L").insert(jdbc);
  }

  private MvcResult send(String url, String body, RequestPostProcessor token) throws Exception {
    var req = post(url, NPI).contentType(MediaType.APPLICATION_JSON).content(body);
    if (token != null) {
      req = req.with(token);
    }
    return mvc.perform(req).andReturn();
  }

  private Map<String, Object> record() {
    return jdbc.queryForMap("SELECT enroll_status, version, end_date FROM provider WHERE npi = ?", NPI);
  }

  private static void assertProblem(MvcResult r, int status) {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @ParameterizedTest(name = "effectiveDate {0} is refused")
  @ValueSource(
      strings = {"2026-03-31T00:00:00", "2026-03-31T00:00:00Z", "2026-03-31 00:00:00", "31/03/2026", "2026-02-30"})
  @DisplayName(
      "[MAM-33 AC-6] an effective date that is not an ISO date (a time part, another format, an impossible day) is a 400 problem and changes nothing")
  void onlyAnIsoDateIsAccepted(String date) throws Exception {
    Map<String, Object> before = record();

    MvcResult r =
        send(
            "/api/v1/providers/{npi}/status-changes",
            "{\"action\":\"SUSPEND\",\"effectiveDate\":\"" + date + "\"}",
            PemJwt.system());

    assertProblem(r, 400);
    assertThat(record()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-33 AC-6] a status change whose body is not JSON is a 400 problem and changes nothing")
  void unreadableStatusChange() throws Exception {
    Map<String, Object> before = record();

    assertProblem(send("/api/v1/providers/{npi}/status-changes", "not json", PemJwt.system()), 400);
    assertProblem(send("/api/v1/providers/{npi}/status-changes", "", PemJwt.system()), 400);
    assertThat(record()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-33 AC-8] a status change with no token is a 401 problem, with a role that may not is a 403 problem")
  void statusChangeNeedsToken() throws Exception {
    String body = "{\"action\":\"SUSPEND\",\"effectiveDate\":\"2026-03-31\"}";

    assertProblem(send("/api/v1/providers/{npi}/status-changes", body, null), 401);
    assertProblem(send("/api/v1/providers/{npi}/status-changes", body, PemJwt.analyst()), 403);
  }

  @Test
  @DisplayName("[MAM-27 AC-7] a decision with no token is a 401 problem, with a role that may not is a 403 problem")
  void decisionNeedsToken() throws Exception {
    String body = "{\"decision\":\"APPROVE\"}";

    assertProblem(send("/api/v1/providers/{npi}/approval", body, null), 401);
    assertProblem(send("/api/v1/providers/{npi}/approval", body, PemJwt.admin()), 403);
  }

  @ParameterizedTest(name = "decision body {0} is refused")
  @ValueSource(strings = {"not json", "", "{\"decision\":\"MAYBE\"}", "{\"decision\":42}", "{}"})
  @DisplayName("[MAM-27 AC-4] a decision whose body is not JSON or names no valid decision is a 400 problem and changes nothing")
  void unreadableDecision(String body) throws Exception {
    Map<String, Object> before = record();

    assertProblem(send("/api/v1/providers/{npi}/approval", body, PemJwt.analyst()), 400);
    assertThat(record()).isEqualTo(before);
  }
}
