package gov.medicaid.pem.screening.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.tomakehurst.wiremock.http.Fault;
import gov.medicaid.pem.screening.ContractFile;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * MAM-28 on the API: POST /api/v1/screenings (requestScreening) against the contract: the 202
 * answer, the refusals (400, 404, 401, 403 as problem+json), the roles, and what the lessons of
 * the earlier reviews ask of this endpoint (a provider-service that does not answer ends in a
 * visible 503 within its limit, the NPI filter takes no wildcards, the correlation id is never
 * taken unsanitised).
 */
class RescreenApiTest extends Lane3Test {

  static final String NPI = "1003000555";
  static final String PATH = "/api/v1/screenings";

  void provider() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "TESTPROV", "ECHO", "MD55555", "TX"));
    ScreeningWorld.licence("MD55555", "TX", "ACTIVE", "2029-01", 3);
  }

  // ---- AC-1: the 202 answer ---------------------------------------------------------------

  @Test
  @DisplayName("[MAM-28 AC-1] the 202 answer is a Screening of the contract: RUNNING, MANUAL, no result yet")
  void acceptedAnswerIsTheContractScreening() throws Exception {
    provider();
    MvcResult r = post(PATH, PemJwt.analyst(), "{\"npi\":\"" + NPI + "\"}");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode s = body(r);
    assertThat(ContractFile.violations("Screening", s)).isEmpty();
    assertThat(s.path("npi").asText()).isEqualTo(NPI);
    assertThat(s.path("reason").asText()).isEqualTo("MANUAL");
    assertThat(s.path("status").asText()).isEqualTo("RUNNING");
    assertThat(s.path("result").isNull()).isTrue();
    assertThat(s.path("results")).isEmpty();
    assertThat(s.path("completedAt").isNull()).isTrue();
    assertThat(s.path("startedAt").asText()).isNotBlank();
    assertThat(UUID.fromString(s.path("screeningId").asText())).isNotNull();
    awaitCompleted(NPI, 1);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] the finished screening read by id is a Screening of the contract with four results")
  void finishedScreeningIsTheContractScreening() throws Exception {
    provider();
    JsonNode s = screen(NPI);
    assertThat(ContractFile.violations("Screening", s)).isEmpty();
    assertThat(s.path("results")).hasSize(4);
    assertThat(s.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(s.path("reason").asText()).isEqualTo("MANUAL");
    assertThat(s.path("completedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName("[MAM-28 AC-1] two requests for the same provider make two screenings, both kept (D-018)")
  void historyIsKept() throws Exception {
    provider();
    String first = screen(NPI).path("screeningId").asText();
    String second = screen(NPI).path("screeningId").asText();
    assertThat(first).isNotEqualTo(second);
    MvcResult list = get(PATH + "?npi=" + NPI, PemJwt.analyst());
    assertThat(body(list).path("total").asInt()).isEqualTo(2);
    assertThat(screeningCount(NPI)).isEqualTo(2);
    assertThat(completedEvents(NPI)).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] the content type of the 202 is application/json, as the contract says")
  void contentType() {
    assertThat(ContractFile.mediaTypes("requestScreening", 202)).containsExactly("application/json");
  }

  // ---- AC-2: refused requests -------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-28 AC-2] the body <{0}> is refused with 400 problem+json and starts nothing")
  @ValueSource(
      strings = {
        "{}",
        "{\"npi\":null}",
        "{\"npi\":\"\"}",
        "{\"npi\":\"123\"}",
        "{\"npi\":\"12345678901\"}",
        "{\"npi\":\"100300055X\"}",
        "{\"npi\":\" 1003000555\"}",
        "{\"npi\":\"1003000555 \"}",
        "{\"npi\":\"1003000555\\n\"}",
        "{\"npi\":[\"1003000555\"]}",
        "{\"npi\":{\"a\":1}}",
        "[]",
        "not json",
        "{\"npi\":"
      })
  void badBodyIsRefused(String body) throws Exception {
    provider();
    MvcResult r = post(PATH, PemJwt.analyst(), body);
    assertProblem(r, 400);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
    assertThat(screeningCount(NPI)).isZero();
    assertThat(ScreeningWorld.providerReads(NPI)).isZero();
    assertThat(ScreeningWorld.allLookups()).isZero();
  }

  @Test
  @DisplayName("[MAM-28 AC-2] a request without any body is refused with 400 problem+json")
  void noBody() throws Exception {
    MvcResult r =
        mvc.perform(MockMvcRequestBuilders.post(PATH).with(PemJwt.analyst())).andReturn();
    assertProblem(r, 400);
  }

  @Test
  @DisplayName("[MAM-28 AC-2] a body of the wrong media type is not accepted as a screening request")
  void wrongMediaType() throws Exception {
    provider();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(PATH)
                    .with(PemJwt.analyst())
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\"npi\":\"" + NPI + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isIn(400, 415);
    assertThat(screeningCount(NPI)).isZero();
  }

  @Test
  @DisplayName("[MAM-28 AC-2] an NPI provider-service does not know is 404 problem+json of the contract, nothing is created")
  void unknownNpi() throws Exception {
    ScreeningWorld.providerUnknown("1003000999");
    MvcResult r = post(PATH, PemJwt.analyst(), "{\"npi\":\"1003000999\"}");
    assertProblem(r, 404);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
    assertThat(screeningCount("1003000999")).isZero();
    assertThat(completedEvents("1003000999")).isZero();
    assertThat(ScreeningWorld.allLookups()).isZero();
  }

  // ---- AC-6: roles ------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-28 AC-6] {0} gets {1}")
  @CsvSource({
    "ANALYST,202",
    "SUPERVISOR,202",
    "SYSTEM,202",
    "PROVIDER,403",
    "ADMIN,403",
    "NONE,401"
  })
  void roles(String role, int status) throws Exception {
    provider();
    MvcResult r = post(PATH, token(role), "{\"npi\":\"" + NPI + "\"}");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status);
    if (status != 202) {
      assertProblem(r, status);
      assertThat(ContractFile.problemViolations(body(r))).isEmpty();
      assertThat(screeningCount(NPI)).isZero();
    } else {
      awaitCompleted(NPI, 1);
    }
  }

  @Test
  @DisplayName("[MAM-28 AC-6] a forbidden role with a malformed body gets the 403 or 400, never a 202")
  void forbiddenWithBadBody() throws Exception {
    MvcResult r = post(PATH, PemJwt.provider(NPI), "{\"npi\":\"123\"}");
    assertThat(r.getResponse().getStatus()).isIn(400, 403);
    assertThat(screeningCount("123")).isZero();
  }

  @Test
  @DisplayName("[MAM-28 AC-6] the contract lists the same roles as the code: ANALYST, SUPERVISOR, SYSTEM")
  void contractRoles() {
    assertThat(ContractFile.roles("requestScreening"))
        .containsExactlyInAnyOrder("ANALYST", "SUPERVISOR", "SYSTEM");
  }

  // ---- lesson d: provider-service does not answer -----------------------------------------

  @Test
  @DisplayName("[MAM-28 AC-2] provider-service answering 500 ends in a visible 503, not a 500 or a hang (lesson d)")
  @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void providerDownIsServiceUnavailable() throws Exception {
    ScreeningWorld.providerAnswers(NPI, 500);
    assertThat(statusOfRequest()).isEqualTo(503);
    assertThat(screeningCount(NPI)).isZero();
  }

  @Test
  @DisplayName("[MAM-28 AC-2] provider-service that resets the connection ends in a visible 503 (lesson d)")
  @Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void providerResetIsServiceUnavailable() throws Exception {
    ScreeningWorld.providerFault(NPI, Fault.CONNECTION_RESET_BY_PEER);
    assertThat(statusOfRequest()).isEqualTo(503);
  }

  @Test
  @DisplayName("[MAM-28 AC-2] provider-service that does not answer within the limit ends in a visible 503 within the limit, never a hang (lesson d)")
  @Timeout(value = 40, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
  void providerHangIsServiceUnavailableInTime() throws Exception {
    ScreeningWorld.providerSlow(
        ScreeningWorld.individual(NPI, "TESTPROV", "ECHO", "MD55555", "TX"), 20_000);
    long start = System.nanoTime();
    int status = statusOfRequest();
    Duration took = Duration.ofNanos(System.nanoTime() - start);
    assertThat(took).as("the call is cut off at the read limit (5 s) plus a margin").isLessThan(Duration.ofSeconds(12));
    assertThat(status).isEqualTo(503);
    assertThat(screeningCount(NPI)).isZero();
  }

  /** The status of the request; an exception that escapes the controller counts as 500. */
  private int statusOfRequest() {
    try {
      return post(PATH, PemJwt.analyst(), "{\"npi\":\"" + NPI + "\"}").getResponse().getStatus();
    } catch (Exception e) {
      return 500;
    }
  }

  // ---- lesson e: wildcards in the filters -------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-28 AC-1] the npi filter <{0}> is refused, % and _ are no wildcards (lesson e)")
  @ValueSource(strings = {"%", "100300055%", "10030005_5", "%%%%%%%%%%", "_________", "\\"})
  void npiFilterTakesNoWildcards(String npi) throws Exception {
    provider();
    screen(NPI);
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get(PATH).queryParam("npi", npi).with(PemJwt.analyst()))
            .andReturn();
    assertProblem(r, 400);
  }

  @ParameterizedTest(
      name =
          "[MAM-28 AC-1] the result filter <{0}> is refused, only PASS and FAIL exist (lesson e)")
  @ValueSource(strings = {"%", "PA%", "PAS_", "pass", "FAIL%", "_"})
  void resultFilterTakesNoWildcards(String result) throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get(PATH)
                    .queryParam("result", result)
                    .with(PemJwt.analyst()))
            .andReturn();
    assertProblem(r, 400);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] the screening list answers 400 problem+json for a size or page out of range")
  void pagingOutOfRange() throws Exception {
    assertProblem(get(PATH + "?size=0", PemJwt.analyst()), 400);
    assertProblem(get(PATH + "?size=201", PemJwt.analyst()), 400);
    assertProblem(get(PATH + "?page=-1", PemJwt.analyst()), 400);
    assertProblem(get(PATH + "?page=abc", PemJwt.analyst()), 400);
  }

  // ---- lesson h: problem+json for the refusals --------------------------------------------

  @Test
  @DisplayName("[MAM-28 AC-6] a request without a token gets 401 problem+json, also for the read endpoints (lesson h)")
  void unauthenticatedIsProblemJson() throws Exception {
    assertProblem(get(PATH, null), 401);
    assertProblem(get(PATH + "/" + UUID.randomUUID(), null), 401);
    assertProblem(post(PATH, null, "{\"npi\":\"" + NPI + "\"}"), 401);
  }

  @Test
  @DisplayName("[MAM-28 AC-6] a role that may not read gets 403 problem+json (lesson h)")
  void forbiddenIsProblemJson() throws Exception {
    assertProblem(get(PATH, PemJwt.provider(NPI)), 403);
    assertProblem(get(PATH + "/" + UUID.randomUUID(), PemJwt.system()), 403);
    assertProblem(post(PATH, PemJwt.admin(), "{\"npi\":\"" + NPI + "\"}"), 403);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] a screening id that is not a UUID is 400 problem+json")
  void idNotAUuid() throws Exception {
    assertProblem(get(PATH + "/not-a-uuid", PemJwt.analyst()), 400);
  }

  // ---- lesson g: the correlation id -------------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-28 AC-1] the hostile correlation id header number {index} is never taken over (lesson g)")
  @ValueSource(
      strings = {
        "bad id with spaces",
        "x<script>alert(1)</script>",
        "semi;colon",
        "line\nbreak",
        "FORGED-LOG-LINE ERROR something terrible happened",
        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
      })
  void hostileCorrelationId(String hostile) throws Exception {
    provider();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(PATH)
                    .with(PemJwt.analyst())
                    .header("X-Correlation-Id", hostile)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"npi\":\"" + NPI + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    String echoed = r.getResponse().getHeader("X-Correlation-Id");
    assertThat(echoed).isNotNull().isNotEqualTo(hostile).matches("[A-Za-z0-9._:-]{1,64}");
    awaitCompleted(NPI, 1);
    String eventId = completedEvent(NPI).path("correlationId").asText();
    assertThat(eventId).isNotEqualTo(hostile).matches("[A-Za-z0-9._:-]{1,64}");
  }

  @Test
  @DisplayName("[MAM-28 AC-1] a valid correlation id of the request is echoed in the response header")
  void validCorrelationIdIsEchoed() throws Exception {
    provider();
    String id = UUID.randomUUID().toString();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(PATH)
                    .with(PemJwt.analyst())
                    .header("X-Correlation-Id", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"npi\":\"" + NPI + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo(id);
    awaitCompleted(NPI, 1);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] the ScreeningCompleted event of a requested screening carries the correlation id of the request (java-microservices 11)")
  void correlationIdFollowsTheRequestIntoTheEvent() throws Exception {
    provider();
    String id = UUID.randomUUID().toString();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(PATH)
                    .with(PemJwt.analyst())
                    .header("X-Correlation-Id", id)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"npi\":\"" + NPI + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    awaitCompleted(NPI, 1);
    assertThat(completedEvent(NPI).path("correlationId").asText()).isEqualTo(id);
  }
}
