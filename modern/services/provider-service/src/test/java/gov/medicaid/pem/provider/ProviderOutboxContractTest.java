package gov.medicaid.pem.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.infrastructure.EnrollmentEventsListener;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

/**
 * The events provider-service writes to its outbox keep to modern/contracts/asyncapi/events.yaml:
 * every required field, no undeclared field, and no null where the contract says string (lesson of
 * the reviews of the first three services; D-024, ADR-003). The events are read from outbox_event as
 * the relay would publish them.
 */
class ProviderOutboxContractTest extends IntegrationTest {

  static final String NPI = "1003000111";
  static final String CORRELATION = "5a1e7b20-3c4d-4e5f-9a6b-000000000456";
  static final EventContract CONTRACT = EventContract.load();

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired EnrollmentEventsListener enrollment;

  @BeforeEach
  void clean() {
    ProviderRows.wipe(jdbc);
    jdbc.update("DELETE FROM outbox_event WHERE subject = ?", NPI);
  }

  private List<String> outbox(String type) {
    return jdbc.queryForList(
        "SELECT payload::text FROM outbox_event WHERE type = ? AND subject = ? ORDER BY created_at, id",
        String.class,
        type,
        NPI);
  }

  private String the(String type) {
    List<String> events = outbox(type);
    assertThat(events).as("%s events of %s", type, NPI).hasSize(1);
    return events.get(0);
  }

  private void send(String url, String body, String correlationId) throws Exception {
    var req =
        post(url, NPI)
            .with(PemJwt.supervisor())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    if (correlationId != null) {
      req = req.header("X-Correlation-Id", correlationId);
    }
    MvcResult r = mvc.perform(req).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
  }

  // ---- ProviderRegistered (MAM-17) -------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-17 AC-2] ProviderRegistered for an individual and for a group keeps to the contract (events.yaml ProviderRegisteredEnvelope)")
  void providerRegistered() {
    Map<String, Object> group = new LinkedHashMap<>();
    group.put("applicationId", UUID.randomUUID().toString());
    group.put("npi", "1003000444");
    group.put("medicaidId", "MCD000444");
    group.put("providerType", "GR");
    group.put("taxonomy", "261QM0801X");
    group.put(
        "organization",
        Map.of("legalName", "EXCLUDED CLINIC LLC", "dbaName", "EXCLUDED CLINIC", "ownerNpi", "1990000111"));
    jdbc.update("DELETE FROM outbox_event WHERE subject = '1003000444'");

    enrollment.onEvent(
        TestEvents.envelope(
            json,
            UUID.randomUUID(),
            "ApplicationValidated",
            "enrollment-service",
            NPI,
            UUID.randomUUID().toString(),
            TestEvents.individualApplication(NPI, "MCD000111", "RIVERA", "MARIA")));
    enrollment.onEvent(
        TestEvents.envelope(
            json,
            UUID.randomUUID(),
            "ApplicationValidated",
            "enrollment-service",
            "1003000444",
            UUID.randomUUID().toString(),
            group));

    assertThat(CONTRACT.violations("ProviderRegisteredEnvelope", the("ProviderRegistered")))
        .isEmpty();
    List<String> groupEvents =
        jdbc.queryForList(
            "SELECT payload::text FROM outbox_event WHERE type = 'ProviderRegistered' AND subject = '1003000444'",
            String.class);
    assertThat(groupEvents).hasSize(1);
    assertThat(CONTRACT.violations("ProviderRegisteredEnvelope", groupEvents.get(0))).isEmpty();
  }

  // ---- ProviderStatusChanged from the analyst's decision (MAM-27) ------------------------------

  private void pendingWithPassedScreening() {
    ProviderRows.individual(NPI).status("P").flag("P").riskTier("L").insert(jdbc);
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-2] an approval with a reason announces ProviderStatusChanged that keeps to the contract")
  void approvalWithReason() throws Exception {
    pendingWithPassedScreening();

    send(
        "/api/v1/providers/{npi}/approval",
        "{\"decision\":\"APPROVE\",\"reason\":\"Documents complete\"}",
        CORRELATION);

    assertThat(CONTRACT.violations("ProviderStatusChangedEnvelope", the("ProviderStatusChanged")))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-2] an approval without a reason (the reason is optional in the contract) announces ProviderStatusChanged with no null where the contract says string")
  void approvalWithoutReason() throws Exception {
    pendingWithPassedScreening();

    send("/api/v1/providers/{npi}/approval", "{\"decision\":\"APPROVE\"}", CORRELATION);

    assertThat(CONTRACT.violations("ProviderStatusChangedEnvelope", the("ProviderStatusChanged")))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-3] a denial (no due date, a null the contract allows) keeps to the contract")
  void denial() throws Exception {
    ProviderRows.individual(NPI).status("P").flag("F").riskTier("H").insert(jdbc);

    send(
        "/api/v1/providers/{npi}/approval",
        "{\"decision\":\"DENY\",\"reason\":\"Owner is on the LEIE list\"}",
        CORRELATION);

    assertThat(CONTRACT.violations("ProviderStatusChangedEnvelope", the("ProviderStatusChanged")))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-2] the correlation id of the request is in the event; an unsafe one is replaced by a generated uuid and never reaches the event")
  void correlationIdInTheEvent() throws Exception {
    pendingWithPassedScreening();
    send("/api/v1/providers/{npi}/approval", "{\"decision\":\"APPROVE\"}", CORRELATION);
    assertThat(json.readTree(the("ProviderStatusChanged")).path("correlationId").asText())
        .isEqualTo(CORRELATION);

    clean();
    pendingWithPassedScreening();
    send(
        "/api/v1/providers/{npi}/approval",
        "{\"decision\":\"APPROVE\"}",
        "abc\nERROR forged line from the caller");

    String id = json.readTree(the("ProviderStatusChanged")).path("correlationId").asText();
    assertThat(id).doesNotContain("forged").doesNotContain("\n").hasSize(36);
    assertThat(UUID.fromString(id)).isNotNull();
  }

  // ---- ProviderStatusChanged from a sanction status change (MAM-33) ----------------------------

  @ParameterizedTest(name = "{0} with a reason keeps to the contract")
  @ValueSource(strings = {"TERMINATE", "SUSPEND", "REINSTATE"})
  @DisplayName(
      "[MAM-33 AC-5] a status change with a reason announces ProviderStatusChanged that keeps to the contract")
  void statusChangeWithReason(String action) throws Exception {
    ProviderRows.individual(NPI).status("A").flag("P").riskTier("L").insert(jdbc);

    send(
        "/api/v1/providers/{npi}/status-changes",
        "{\"action\":\"" + action + "\",\"reason\":\"LEIE exclusion\",\"effectiveDate\":\"2026-03-31\"}",
        CORRELATION);

    assertThat(CONTRACT.violations("ProviderStatusChangedEnvelope", the("ProviderStatusChanged")))
        .isEmpty();
  }

  @ParameterizedTest(name = "{0} without a reason keeps to the contract")
  @ValueSource(strings = {"TERMINATE", "SUSPEND", "REINSTATE"})
  @DisplayName(
      "[MAM-33 AC-5] a status change without a reason (optional in the contract) announces ProviderStatusChanged with no null where the contract says string")
  void statusChangeWithoutReason(String action) throws Exception {
    ProviderRows.individual(NPI).status("A").flag("P").riskTier("L").insert(jdbc);

    send(
        "/api/v1/providers/{npi}/status-changes",
        "{\"action\":\"" + action + "\",\"effectiveDate\":\"2026-03-31\"}",
        CORRELATION);

    assertThat(CONTRACT.violations("ProviderStatusChangedEnvelope", the("ProviderStatusChanged")))
        .isEmpty();
  }
}
