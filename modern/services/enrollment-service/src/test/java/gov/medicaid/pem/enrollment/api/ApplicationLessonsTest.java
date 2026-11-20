package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.EventsContractFile;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The lessons of the code reviews of the other services, applied to the online endpoints of MAM-46
 * and MAM-54 (Lane 3 tests, additive): what the online submit writes to the outbox is the
 * contract's envelope (no null where the contract says string), a correlation id from a header
 * never reaches the log or an event unsanitised and an event always carries a uuid, an event that
 * cannot be written leaves no application behind (one transaction), and the 401, 403 and 400
 * answers of the read endpoints are application/problem+json.
 */
class ApplicationLessonsTest extends IntakeIntegrationTest {

  static final String UNKNOWN = "00000000-0000-0000-0000-000000000000";

  String individual(String npi, String type, String taxonomy) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "npi",
            npi,
            "medicaidId",
            "M00000" + npi.substring(7),
            "providerType",
            type,
            "taxonomy",
            taxonomy,
            "individual",
            Map.of(
                "lastName",
                "RIVERA",
                "firstName",
                "MARIA",
                "licenseNo",
                "MD12345",
                "licenseState",
                "TX")));
  }

  MockHttpServletRequestBuilder submit(RequestPostProcessor who, String body) {
    MockHttpServletRequestBuilder request =
        post("/api/v1/applications").contentType(MediaType.APPLICATION_JSON).content(body);
    return who == null ? request : request.with(who);
  }

  List<String> violations(String schema, String type) throws Exception {
    List<String> all = new java.util.ArrayList<>();
    for (JsonNode event : events(type)) {
      all.addAll(EventsContractFile.violations(schema, event));
    }
    return all;
  }

  // ---- events conform to events.yaml (MAM-46)
  // -------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-46 AC-1] the ApplicationValidated event of an online application is a valid ApplicationValidatedEnvelope")
  void validatedEventConforms() throws Exception {
    mvc.perform(submit(PemJwt.provider("1003000111"), individual("1003000111", "IN", "207Q00000X")))
        .andReturn();
    assertThat(events("ApplicationValidated")).hasSize(1);
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-3] the ApplicationRejected event of an online application is a valid ApplicationRejectedEnvelope")
  void rejectedEventConforms() throws Exception {
    mvc.perform(submit(PemJwt.provider("1003000222"), individual("1003000222", "XX", "")))
        .andReturn();
    assertThat(events("ApplicationRejected")).hasSize(1);
    assertThat(violations("ApplicationRejectedEnvelope", "ApplicationRejected")).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-3] a group application with only a legal name gives an event with no null member")
  void organizationEventHasNoNulls() throws Exception {
    String body =
        json.writeValueAsString(
            Map.of(
                "npi",
                "1003000444",
                "medicaidId",
                "M00000444",
                "providerType",
                "GR",
                "taxonomy",
                "207Q00000X",
                "organization",
                Map.of("legalName", "SAMPLE CLINIC 01")));
    MvcResult r = mvc.perform(submit(PemJwt.analyst(), body)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    assertThat(events("ApplicationValidated").get(0).path("data").toString())
        .doesNotContain("null");
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  // ---- the correlation id (MAM-46)
  // ------------------------------------------------------------------

  /**
   * Correlation ids that are not safe to log or forward and can still travel in an HTTP header. A
   * header value cannot hold a line break (the server ends the header line there), and Spring
   * Security's firewall refuses any control character, so those never reach the application.
   */
  static Stream<Arguments> unsafeIds() {
    return Stream.of(
        Arguments.of("a forged log line after a space", "abc FORGED LOG LINE level=ERROR"),
        Arguments.of(
            "a forged JSON log line",
            "{\"@timestamp\":\"2026-01-15T00:00:00Z\",\"log.level\":\"ERROR\",\"message\":\"FORGED\"}"),
        Arguments.of("65 characters (one over the limit)", "FORGED" + "a".repeat(59)),
        Arguments.of("a path and a quote", "../../FORGED/x\"y"),
        Arguments.of("markup", "<script>FORGED</script>"),
        Arguments.of("an empty string", ""));
  }

  @ParameterizedTest(
      name =
          "[MAM-46 AC-1] an X-Correlation-Id with {0} never reaches the log line or the ApplicationValidated event")
  @MethodSource("unsafeIds")
  void unsafeCorrelationIdIsReplaced(String what, String evil) throws Exception {
    MvcResult r =
        mvc.perform(
                submit(PemJwt.provider("1003000111"), individual("1003000111", "IN", "207Q00000X"))
                    .header("X-Correlation-Id", evil))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(201);
    assertThat(r.getResponse().getHeader("X-Correlation-Id"))
        .isNotEqualTo(evil)
        .matches("[A-Za-z0-9._:-]{1,64}");
    assertThat(log().lines)
        .allSatisfy(
            line -> {
              if (line.correlationId() != null) {
                assertThat(line.correlationId()).doesNotContain("\n").doesNotContain("FORGED");
              }
              assertThat(line.message()).doesNotContain("FORGED");
            });
    String payload = jdbc.queryForObject("SELECT payload::text FROM outbox_event", String.class);
    assertThat(payload).doesNotContain("FORGED");
    assertThat(UUID.fromString(json.readTree(payload).path("correlationId").asText())).isNotNull();
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-1] an X-Correlation-Id with a tab (legal in HTTP, refused by Spring Security's firewall) never leaves an application stored behind a 400 answer")
  void tabInTheCorrelationIdIsAnsweredConsistently() throws Exception {
    MvcResult r =
        mvc.perform(
                submit(PemJwt.provider("1003000111"), individual("1003000111", "IN", "207Q00000X"))
                    .header("X-Correlation-Id", "abc\tdef"))
            .andReturn();
    int status = r.getResponse().getStatus();
    assertThat(status).isIn(201, 400);
    assertThat(applications("1003000111"))
        .as("applications stored while the answer was %s", status)
        .isEqualTo(status == 201 ? 1 : 0);
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-1] an X-Correlation-Id that is safe to log but no uuid still gives an event whose correlationId is a uuid (events.yaml)")
  void eventCorrelationIdIsAlwaysAUuid() throws Exception {
    mvc.perform(
            submit(PemJwt.provider("1003000111"), individual("1003000111", "IN", "207Q00000X"))
                .header("X-Correlation-Id", "trace-42"))
        .andReturn();
    assertThat(violations("ApplicationValidatedEnvelope", "ApplicationValidated")).isEmpty();
  }

  // ---- one transaction (MAM-46)
  // ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-46 AC-6] when the event cannot be written the application is not stored either: both are one transaction")
  void applicationAndEventAreOneTransaction() throws Exception {
    reject("reject_outbox", "INSERT", "outbox_event", "true");
    int status;
    try {
      status =
          mvc.perform(
                  submit(
                      PemJwt.provider("1003000111"), individual("1003000111", "IN", "207Q00000X")))
              .andReturn()
              .getResponse()
              .getStatus();
    } catch (Exception e) {
      status = 500;
    }
    assertThat(status).isGreaterThanOrEqualTo(500);
    assertThat(count("SELECT count(*) FROM application")).isZero();
    assertThat(count("SELECT count(*) FROM application_error")).isZero();
  }

  // ---- problem+json on the read endpoints (MAM-54)
  // ----------------------------------------------------

  @ParameterizedTest(name = "[MAM-54 AC-8] {0} without a token is 401 problem+json")
  @ValueSource(strings = {"/api/v1/applications", "/api/v1/applications/" + UNKNOWN})
  void readWithoutTokenIsProblem(String uri) throws Exception {
    MvcResult r = mvc.perform(get(uri)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @ParameterizedTest(name = "[MAM-54 AC-8] {0} as ADMIN is 403 problem+json")
  @ValueSource(strings = {"/api/v1/applications", "/api/v1/applications/" + UNKNOWN})
  void readAsAdminIsProblem(String uri) throws Exception {
    MvcResult r = mvc.perform(get(uri).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName("[MAM-54 AC-7] an application id that is no UUID is 400 problem+json")
  void malformedApplicationId() throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/applications/not-a-uuid").with(PemJwt.analyst())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  // ---- % and _ are never wildcards (MAM-54)
  // -------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-54 AC-6] the npi filter [{0}] is refused: % and _ are no wildcards, 400 problem+json")
  @ValueSource(strings = {"100300011%", "100300011_", "%003000111", "%", "_", "\\", "1003000111%"})
  void wildcardsInTheNpiFilter(String npi) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/applications").param("npi", npi).with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @ParameterizedTest(
      name =
          "[MAM-54 AC-6] the status filter [{0}] is refused: % and _ are no wildcards, 400 problem+json")
  @ValueSource(strings = {"VAL%", "_ALIDATED", "%", "REJECTED%"})
  void wildcardsInTheStatusFilter(String status) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/applications").param("status", status).with(PemJwt.analyst()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }
}
