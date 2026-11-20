package gov.medicaid.pem.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.enrollment.AppInputFile;
import gov.medicaid.pem.enrollment.IntakeApi;
import gov.medicaid.pem.enrollment.IntakeIntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-56 AC-1, AC-2 and the run parameters of AC-7: the start of an intake run. The business date
 * is an explicit parameter (D-020) and an ISO date only; the batch id is a real job parameter with
 * the default DEFBATCH (D-017). A refused start creates no run.
 */
class IntakeRunStartApiTest extends IntakeIntegrationTest {

  byte[] threeValid() {
    return AppInputFile.lines(
        AppInputFile.valid("1003000111"),
        AppInputFile.valid("1003000222"),
        AppInputFile.valid("1003000333"));
  }

  int runCount() {
    return count("SELECT count(*) FROM intake_run");
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- AC-1 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-56 AC-1] ADMIN starts a run with a business date and batch id: 202 with runId, job PRVENR01, businessDate and status RUNNING or COMPLETED")
  void adminStartsARun() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), threeValid(), DATE, "B0331001");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    JsonNode run = body(r);
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo("PRVENR01");
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(run.path("status").asText()).isIn("RUNNING", "COMPLETED");
    assertThat(run.path("startedAt").asText()).isNotBlank();
    IntakeApi.await(mvc, json, run.path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-56 AC-1] the run read back by its id shows the same business date and the job")
  void readBackShowsTheBusinessDate() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "B0331001");
    JsonNode run = IntakeApi.await(mvc, json, id);
    assertThat(run.path("runId").asText()).isEqualTo(id);
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(run.path("job").asText()).isEqualTo("PRVENR01");
  }

  @Test
  @DisplayName("[MAM-56 AC-1] the run keeps its batch id and business date in intake_run")
  void runIsStored() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "B0331001");
    IntakeApi.await(mvc, json, id);
    assertThat(
            jdbc.queryForMap(
                "SELECT batch_id, business_date::text AS business_date FROM intake_run WHERE public_id = ?::uuid",
                id))
        .containsEntry("batch_id", "B0331001")
        .containsEntry("business_date", DATE);
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-1] the service log contains the line 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001'")
  void logLine() throws Exception {
    IntakeApi.await(
        mvc, json, IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "B0331001"));
    assertThat(log().messages())
        .contains("PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001");
  }

  @Test
  @DisplayName("[MAM-56 AC-1] each run logs its own batch id")
  void eachRunLogsItsBatchId() throws Exception {
    IntakeApi.await(mvc, json, IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "B0331001"));
    IntakeApi.await(
        mvc,
        json,
        IntakeApi.startAsAdmin(mvc, json, AppInputFile.lines(AppInputFile.valid("1003000777")), "2026-04-01", "B0401001"));
    assertThat(log().messages())
        .contains(
            "PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001",
            "PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0401001");
  }

  @Test
  @DisplayName("[MAM-56 AC-1] the run is ended by the job: COMPLETED with startedAt before endedAt")
  void runEnds() throws Exception {
    JsonNode run =
        IntakeApi.await(
            mvc, json, IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "B0331001"));
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(java.time.Instant.parse(run.path("endedAt").asText()))
        .isAfterOrEqualTo(java.time.Instant.parse(run.path("startedAt").asText()));
  }

  // ---- AC-2 -------------------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-56 AC-2] a batch id of [{0}] defaults to DEFBATCH: log line and intake_run")
  @NullSource
  @ValueSource(strings = {"", " ", "      ", "        "})
  void missingBatchIdIsDefbatch(String batchId) throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, batchId);
    IntakeApi.await(mvc, json, id);
    assertThat(log().messages()).contains("PRVENR01: STARTING ENROLLMENT LOAD - BATCH DEFBATCH");
    assertThat(
            jdbc.queryForObject(
                "SELECT batch_id FROM intake_run WHERE public_id = ?::uuid", String.class, id))
        .isEqualTo("DEFBATCH");
  }

  @Test
  @DisplayName("[MAM-56 AC-2] a batch id with blanks around it is kept without them")
  void batchIdIsTrimmed() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "  B0331001 ");
    IntakeApi.await(mvc, json, id);
    assertThat(
            jdbc.queryForObject(
                "SELECT batch_id FROM intake_run WHERE public_id = ?::uuid", String.class, id))
        .isEqualTo("B0331001");
  }

  @Test
  @DisplayName("[MAM-56 AC-2] a batch id of exactly 8 characters (the contract's maxLength) is accepted")
  void eightCharactersAreAccepted() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, threeValid(), DATE, "ABCDEFGH");
    IntakeApi.await(mvc, json, id);
    assertThat(log().messages()).contains("PRVENR01: STARTING ENROLLMENT LOAD - BATCH ABCDEFGH");
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-2] a batch id of 9 characters is 400 problem+json and no run is created")
  void nineCharactersAreRefused() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), threeValid(), DATE, "ABCDEFGHI");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(runCount()).isZero();
  }

  // ---- AC-7: the run parameters ---------------------------------------------------------------

  @Test
  @DisplayName("[MAM-56 AC-7] a start without businessDate is 400 problem+json that names it, and no run is created")
  void withoutBusinessDate() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), threeValid(), null, null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("businessDate");
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] a start without a file is 400 problem+json that names it, and no run is created")
  void withoutFile() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), null, DATE, null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("file");
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] a start with neither a file nor a business date is 400 and no run is created")
  void withNeither() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), null, null, null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(runCount()).isZero();
  }

  @ParameterizedTest(
      name = "[MAM-56 AC-7] businessDate [{0}] is not an ISO date: 400 problem+json and no run is created")
  @ValueSource(
      strings = {
        "",
        "   ",
        "2026-03-31T00:00:00",
        "2026-03-31T10:15:30Z",
        "2026-03-31 00:00:00",
        "20260331",
        "2026-3-31",
        "31.03.2026",
        "03/31/2026",
        "2026-02-30",
        "2026-13-01",
        "2026-00-10",
        "not-a-date"
      })
  void businessDateMustBeAnIsoDate(String businessDate) throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), threeValid(), businessDate, "B0331001");
    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName("[MAM-56 AC-7] a leap day is a valid business date")
  void leapDay() throws Exception {
    String id = IntakeApi.startAsAdmin(mvc, json, threeValid(), "2028-02-29", "B0229001");
    assertThat(IntakeApi.await(mvc, json, id).path("businessDate").asText()).isEqualTo("2028-02-29");
  }

  @Test
  @DisplayName("[MAM-56 AC-7] a refused start leaves no file behind: the next run is not affected")
  void refusedStartLeavesNothing() throws Exception {
    IntakeApi.start(mvc, PemJwt.admin(), threeValid(), "2026-03-31T00:00:00", null);
    JsonNode run = IntakeApi.runToEnd(mvc, json, threeValid(), DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(3);
  }

  // ---- the correlation id (code review lesson) ----------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-56 AC-1] an X-Correlation-Id with a line break is replaced by a safe id: it is not echoed, logged or put in an event")
  void unsafeCorrelationIdNeverReachesTheLogOrAnEvent() throws Exception {
    String evil = "abc\nFORGED LOG LINE level=ERROR";
    MvcResult r =
        mvc.perform(
                IntakeApi.request(PemJwt.admin(), threeValid(), DATE, "B0331001")
                    .header("X-Correlation-Id", evil))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    IntakeApi.await(mvc, json, body(r).path("runId").asText());
    String echoed = r.getResponse().getHeader("X-Correlation-Id");
    assertThat(echoed).isNotEqualTo(evil).matches("[A-Za-z0-9._:-]{1,64}");
    assertThat(log().lines)
        .allSatisfy(
            line -> {
              if (line.correlationId() != null) {
                assertThat(line.correlationId()).doesNotContain("\n").doesNotContain("FORGED");
              }
              assertThat(line.message()).doesNotContain("FORGED");
            });
    List<String> payloads = jdbc.queryForList("SELECT payload::text FROM outbox_event", String.class);
    assertThat(payloads).hasSize(3);
    for (String payload : payloads) {
      JsonNode event = json.readTree(payload);
      assertThat(payload).doesNotContain("FORGED");
      assertThat(UUID.fromString(event.path("correlationId").asText())).isNotNull();
    }
  }

  @Test
  @DisplayName("[MAM-56 AC-1] a safe X-Correlation-Id is echoed on the 202 answer")
  void safeCorrelationIdIsEchoed() throws Exception {
    MvcResult r =
        mvc.perform(
                IntakeApi.request(PemJwt.admin(), threeValid(), DATE, "B0331001")
                    .header("X-Correlation-Id", "trace-42"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getHeader("X-Correlation-Id")).isEqualTo("trace-42");
    IntakeApi.await(mvc, json, body(r).path("runId").asText());
  }
}
