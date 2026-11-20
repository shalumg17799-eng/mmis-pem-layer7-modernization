package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * A change, its history entry and its event are one transaction (D-018, ADR-003, lessons of the
 * earlier reviews): when the history entry or the event cannot be written, the provider row stays
 * as it was, nothing is announced, the caller gets a 503 problem with a short plain reason (no SQL
 * text, no table name, at most 200 characters) and the request's correlation id, and once the fault
 * is gone the same request applies the change once. The fault is a trigger in the test database
 * that is removed again in every case.
 */
class ProviderWriteAtomicityTest extends IntegrationTest {

  static final String NPI = "1003000111";
  static final String CORRELATION = "7d2f9c4e-1b3a-4c5d-8e6f-000000000123";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", NPI);
    allowWrites("audit_log");
    allowWrites("outbox_event");
  }

  private void failWrites(String table) {
    jdbc.execute(
        "CREATE OR REPLACE FUNCTION fail_atomic_write() RETURNS trigger LANGUAGE plpgsql AS $$"
            + " BEGIN RAISE EXCEPTION 'injected failure in %', TG_TABLE_NAME; END $$");
    jdbc.execute(
        "CREATE TRIGGER fail_atomic_write BEFORE INSERT ON "
            + table
            + " FOR EACH ROW EXECUTE FUNCTION fail_atomic_write()");
  }

  private void allowWrites(String table) {
    jdbc.execute("DROP TRIGGER IF EXISTS fail_atomic_write ON " + table);
  }

  private Map<String, Object> provider() {
    return jdbc.queryForMap(
        "SELECT enroll_status, version, reval_due_date, end_date FROM provider WHERE npi = ?", NPI);
  }

  private int count(String sql) {
    Integer n = jdbc.queryForObject(sql, Integer.class, NPI);
    return n == null ? 0 : n;
  }

  private int announced() {
    return count(
        "SELECT count(*) FROM outbox_event WHERE type = 'ProviderStatusChanged' AND subject = ?");
  }

  private int history() {
    return count("SELECT count(*) FROM audit_log WHERE npi = ?");
  }

  private MvcResult send(String url, String body, RequestPostProcessor token) throws Exception {
    return mvc.perform(
            post(url, NPI)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("X-Correlation-Id", CORRELATION)
                .with(token))
        .andReturn();
  }

  private void assertShortPlain503(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(503);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo(CORRELATION);
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("detail").asText()).isNotBlank().hasSizeLessThanOrEqualTo(200);
    assertThat(r.getResponse().getContentAsString().toLowerCase())
        .doesNotContain("injected")
        .doesNotContain("audit_log")
        .doesNotContain("outbox_event")
        .doesNotContain("trigger")
        .doesNotContain("sql")
        .doesNotContain("postgres")
        .doesNotContain("jdbc");
  }

  @ParameterizedTest(name = "[MAM-27 AC-2] with {0} failing the approval is all or nothing")
  @ValueSource(strings = {"audit_log", "outbox_event"})
  @DisplayName(
      "[MAM-27 AC-2] when the history entry or the event of an approval cannot be written, the provider stays PENDING, nothing is announced, the answer is a plain 503, and the retry applies it once")
  void approvalIsAllOrNothing(String failingTable) throws Exception {
    ProviderRows.individual(NPI).status("P").flag("P").riskTier("L").insert(jdbc);
    Map<String, Object> before = provider();
    failWrites(failingTable);
    MvcResult refused;
    try {
      refused =
          send("/api/v1/providers/{npi}/approval", "{\"decision\":\"APPROVE\"}", PemJwt.analyst());
    } finally {
      allowWrites(failingTable);
    }

    assertShortPlain503(refused);
    assertThat(provider()).as("provider row after the failed approval").isEqualTo(before);
    assertThat(announced()).as("events after the failed approval").isZero();
    assertThat(history()).as("history after the failed approval").isZero();

    MvcResult retried =
        send("/api/v1/providers/{npi}/approval", "{\"decision\":\"APPROVE\"}", PemJwt.analyst());

    assertThat(retried.getResponse().getStatus()).isEqualTo(200);
    assertThat(provider().get("enroll_status")).isEqualTo("A");
    assertThat(announced()).as("events after the retry").isEqualTo(1);
    assertThat(history()).as("history after the retry").isEqualTo(1);
  }

  @ParameterizedTest(name = "[MAM-33 AC-5] with {0} failing the status change is all or nothing")
  @ValueSource(strings = {"audit_log", "outbox_event"})
  @DisplayName(
      "[MAM-33 AC-5] when the history entry or the event of a status change cannot be written, the status stays as it was, nothing is announced, the answer is a plain 503, and the retry applies it once")
  void statusChangeIsAllOrNothing(String failingTable) throws Exception {
    ProviderRows.individual(NPI).status("A").flag("P").riskTier("L").insert(jdbc);
    Map<String, Object> before = provider();
    String body =
        "{\"action\":\"SUSPEND\",\"reason\":\"LEIE exclusion\",\"effectiveDate\":\"2026-03-31\"}";
    failWrites(failingTable);
    MvcResult refused;
    try {
      refused = send("/api/v1/providers/{npi}/status-changes", body, PemJwt.system());
    } finally {
      allowWrites(failingTable);
    }

    assertShortPlain503(refused);
    assertThat(provider()).as("provider row after the failed status change").isEqualTo(before);
    assertThat(announced()).as("events after the failed status change").isZero();
    assertThat(history()).as("history after the failed status change").isZero();

    MvcResult retried = send("/api/v1/providers/{npi}/status-changes", body, PemJwt.system());

    assertThat(retried.getResponse().getStatus()).isEqualTo(200);
    assertThat(provider().get("enroll_status")).isEqualTo("S");
    assertThat(announced()).as("events after the retry").isEqualTo(1);
    assertThat(history()).as("history after the retry").isEqualTo(1);
  }
}
