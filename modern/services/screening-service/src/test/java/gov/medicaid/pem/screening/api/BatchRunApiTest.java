package gov.medicaid.pem.screening.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.screening.ContractFile;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * MAM-32 on the API: startScreeningRun, getScreeningRun and getScreeningResultsFile against
 * contract 1.1.0 (ADR-004, D-020, D-023, question C-8): the answers, the roles, the refusals as
 * problem+json and the business date that accepts nothing but an ISO date (lesson b).
 */
class BatchRunApiTest extends Lane3Test {

  static final String DATE = "2026-03-31";

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM screening_run", Integer.class);
  }

  void emptyWorld() {
    ScreeningWorld.lists(List.of());
  }

  // ---- AC-1: starting ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-1] the 202 answer is a BatchRun of the contract: PRVSCR01, the date, RUNNING")
  void acceptedAnswer() throws Exception {
    emptyWorld();
    MvcResult r = post(RUNS, PemJwt.admin(), "{\"businessDate\":\"" + DATE + "\"}");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode run = body(r);
    assertThat(ContractFile.violations("BatchRun", run)).isEmpty();
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo("PRVSCR01");
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(run.path("status").asText()).isEqualTo("RUNNING");
    assertThat(run.has("error")).isFalse();
    assertThat(Instant.parse(run.path("startedAt").asText())).isNotNull();
    awaitRun(run.path("runId").asText());
  }

  @ParameterizedTest(name = "[MAM-32 AC-1] {0} starting a run gets {1}")
  @CsvSource({
    "ADMIN,202",
    "SYSTEM,202",
    "ANALYST,403",
    "SUPERVISOR,403",
    "PROVIDER,403",
    "NONE,401"
  })
  void roles(String role, int status) throws Exception {
    emptyWorld();
    int before = runCount();
    MvcResult r = post(RUNS, token(role), "{\"businessDate\":\"" + DATE + "\"}");
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(status);
    if (status == 202) {
      awaitRun(body(r).path("runId").asText());
      assertThat(runCount()).isEqualTo(before + 1);
    } else {
      assertProblem(r, status);
      assertThat(ContractFile.problemViolations(body(r))).isEmpty();
      assertThat(runCount()).isEqualTo(before);
      assertThat(ScreeningWorld.listedPages()).isEmpty();
    }
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-1] the contract lists ADMIN and SYSTEM for the start, ADMIN for status and file")
  void contractRoles() {
    assertThat(ContractFile.roles("startScreeningRun"))
        .containsExactlyInAnyOrder("ADMIN", "SYSTEM");
    assertThat(ContractFile.roles("getScreeningRun")).containsExactly("ADMIN");
    assertThat(ContractFile.roles("getScreeningResultsFile")).containsExactly("ADMIN");
  }

  @ParameterizedTest(
      name = "[MAM-32 AC-1] the body <{0}> is refused with 400 problem+json and starts no run")
  @ValueSource(
      strings = {
        "",
        "{}",
        "{\"businessDate\":null}",
        "{\"businessDate\":\"\"}",
        "{\"businessDate\":\"   \"}",
        "{\"businessDate\":\"31/03/2026\"}",
        "{\"businessDate\":\"03-31-2026\"}",
        "{\"businessDate\":\"2026-02-30\"}",
        "{\"businessDate\":\"2026-13-01\"}",
        "{\"businessDate\":\"2026-3-31\"}",
        "{\"businessDate\":\"20260331\"}",
        "{\"businessDate\":\"March 31, 2026\"}",
        "{\"businessDate\":\"tomorrow\"}",
        "{\"businessDate\":20260331}",
        "{\"businessDate\":[2026,3,31]}",
        "{\"businessDate\":true}",
        "{\"businessDate\":{\"year\":2026}}",
        "[]",
        "not json",
        "{\"businessDate\":"
      })
  void badBusinessDateIsRefused(String body) throws Exception {
    emptyWorld();
    int before = runCount();
    MvcResult r = post(RUNS, PemJwt.admin(), body);
    assertProblem(r, 400);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
    assertThat(runCount()).isEqualTo(before);
    assertThat(ScreeningWorld.listedPages()).isEmpty();
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-1] the business date <{0}> has a time part or other padding: 400, not a date (lesson b)")
  @ValueSource(
      strings = {
        "2026-03-31T00:00:00",
        "2026-03-31T00:00:00Z",
        "2026-03-31T10:15:30+01:00",
        "2026-03-31T23:59:59.999",
        "2026-03-31 00:00:00",
        " 2026-03-31",
        "2026-03-31 ",
        "2026-03-31\n"
      })
  void dateWithTimePartIsRefused(String value) throws Exception {
    emptyWorld();
    int before = runCount();
    MvcResult r =
        post(
            RUNS, PemJwt.admin(), json.writeValueAsString(java.util.Map.of("businessDate", value)));
    assertThat(r.getResponse().getStatus())
        .as("the answer for the date <%s>: %s", value, r.getResponse().getContentAsString())
        .isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(runCount()).isEqualTo(before);
  }

  @ParameterizedTest(name = "[MAM-32 AC-1] the business date {0} is echoed in the run")
  @ValueSource(strings = {"2026-03-31", "2026-01-15", "2024-02-29", "2026-12-31", "1999-01-01"})
  void businessDateIsEchoed(String date) throws Exception {
    emptyWorld();
    JsonNode run = startRun("ADMIN", date);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    JsonNode ended = awaitRun(run.path("runId").asText());
    assertThat(ended.path("businessDate").asText()).isEqualTo(date);
  }

  @Test
  @DisplayName("[MAM-32 AC-1] a start without any body is 400 problem+json")
  void noBody() throws Exception {
    MvcResult r = mvc.perform(MockMvcRequestBuilders.post(RUNS).with(PemJwt.admin())).andReturn();
    assertProblem(r, 400);
  }

  @Test
  @DisplayName("[MAM-32 AC-1] a body of the wrong media type starts no run")
  void wrongMediaType() throws Exception {
    emptyWorld();
    int before = runCount();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(RUNS)
                    .with(PemJwt.admin())
                    .contentType(MediaType.TEXT_PLAIN)
                    .content("{\"businessDate\":\"" + DATE + "\"}"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isIn(400, 415);
    assertThat(runCount()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-32 AC-1] two starts for the same business date are two runs with two ids")
  void twoRuns() throws Exception {
    emptyWorld();
    String a = startRun("ADMIN", DATE).path("runId").asText();
    String b = startRun("SYSTEM", DATE).path("runId").asText();
    assertThat(a).isNotEqualTo(b);
    awaitRun(a);
    awaitRun(b);
    assertThat(runCount()).isEqualTo(2);
  }

  // ---- AC-5: reading a run ----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-5] the 200 of getScreeningRun is a BatchRun of the contract with the four counts")
  void statusAnswer() throws Exception {
    emptyWorld();
    JsonNode ended = runToEnd(DATE);
    assertThat(ContractFile.violations("BatchRun", ended)).isEmpty();
    assertThat(ended.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(ended.path("counts").fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder("read", "screened", "skipped", "failed");
    assertThat(Instant.parse(ended.path("endedAt").asText()))
        .isAfterOrEqualTo(Instant.parse(ended.path("startedAt").asText()));
  }

  @ParameterizedTest(name = "[MAM-32 AC-5] {0} reading the run gets {1}")
  @CsvSource({
    "ADMIN,200",
    "SYSTEM,403",
    "ANALYST,403",
    "SUPERVISOR,403",
    "PROVIDER,403",
    "NONE,401"
  })
  void statusRoles(String role, int status) throws Exception {
    emptyWorld();
    String runId = runToEnd(DATE).path("runId").asText();
    MvcResult r = get(RUNS + "/" + runId, token(role));
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    if (status != 200) {
      assertProblem(r, status);
    }
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-5] an unknown run id is 404 problem+json, one that is no UUID is 400 problem+json")
  void statusUnknown() throws Exception {
    assertProblem(get(RUNS + "/" + UUID.randomUUID(), PemJwt.admin()), 404);
    assertProblem(get(RUNS + "/not-a-uuid", PemJwt.admin()), 400);
  }

  // ---- AC-8: the file ---------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-32 AC-8] {0} downloading the file gets {1}")
  @CsvSource({
    "ADMIN,200",
    "SYSTEM,403",
    "ANALYST,403",
    "SUPERVISOR,403",
    "PROVIDER,403",
    "NONE,401"
  })
  void fileRoles(String role, int status) throws Exception {
    emptyWorld();
    String runId = runToEnd(DATE).path("runId").asText();
    MvcResult r = get(RUNS + "/" + runId + "/results-file", token(role));
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    if (status == 200) {
      assertThat(r.getResponse().getContentType()).startsWith("text/plain");
      assertThat(ContractFile.mediaTypes("getScreeningResultsFile", 200))
          .containsExactly("text/plain");
    } else {
      assertProblem(r, status);
      assertThat(ContractFile.problemViolations(body(r))).isEmpty();
    }
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-8] an unknown run id is 404 problem+json of the contract, one that is no UUID is 400")
  void fileUnknown() throws Exception {
    MvcResult r = get(RUNS + "/" + UUID.randomUUID() + "/results-file", PemJwt.admin());
    assertProblem(r, 404);
    assertThat(ContractFile.problemViolations(body(r))).isEmpty();
    assertProblem(get(RUNS + "/not-a-uuid/results-file", PemJwt.admin()), 400);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a FAILED run has no file: 404 problem+json, the file is only for a COMPLETED run")
  void failedRunHasNoFile() throws Exception {
    ScreeningWorld.listAnswers(503);
    JsonNode ended = runToEnd(DATE);
    assertThat(ended.path("status").asText()).isEqualTo("FAILED");
    assertProblem(
        get(RUNS + "/" + ended.path("runId").asText() + "/results-file", PemJwt.admin()), 404);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] the FAILED run is a BatchRun of the contract with a reason in error and an end time")
  void failedRunIsAContractBatchRun() throws Exception {
    ScreeningWorld.listAnswers(503);
    JsonNode ended = runToEnd(DATE);
    assertThat(ContractFile.violations("BatchRun", ended)).isEmpty();
    assertThat(ended.path("status").asText()).isEqualTo("FAILED");
    assertThat(ended.path("error").asText()).isNotBlank();
    assertThat(ended.path("endedAt").asText()).isNotBlank();
  }
}
