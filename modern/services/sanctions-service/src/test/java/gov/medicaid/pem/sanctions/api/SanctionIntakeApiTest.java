package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.IntegrationTest;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.TestProviderService;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-55 AC-1 and AC-2 at the endpoint: POST /api/v1/batch/sanction-intake-runs with the file and
 * the business date answers 202 with a BatchRun; a call without the business date or without the
 * file (or with a business date that is not a date) is a 400 problem+json and creates no run
 * (D-020).
 */
class SanctionIntakeApiTest extends IntegrationTest {

  static final String REASON = "SYNTHETIC TEST REASON";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    jdbc.update("DELETE FROM sanction");
    jdbc.update("DELETE FROM sanction_intake_run");
    jdbc.update("DELETE FROM outbox_event");
    TestProviderService.reset();
    TestProviderService.holds("1003000111", "A");
  }

  byte[] oneRecord() {
    return SancinFile.lines(SancinFile.record("1003000111", "T", REASON, "20260331"));
  }

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM sanction_intake_run", Integer.class);
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- AC-1: the 202 answer -------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the start answers 202 with the run id, the job PRVSAN01 and the business date")
  void startAnswer() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), oneRecord(), "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode run = body(r);
    assertThat(run.path("runId").asText())
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    assertThat(run.path("job").asText()).isEqualTo("PRVSAN01");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
    assertThat(run.path("status").asText()).isIn("RUNNING", "COMPLETED");
    assertThat(run.path("startedAt").asText()).isNotBlank();
    IntakeApi.await(mvc, json, run.path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the answer carries the six counts, read, applied, notFound, rejected, skipped and terminated")
  void countNames() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), oneRecord(), "2026-03-31");
    JsonNode counts = body(r).path("counts");
    assertThat(counts.fieldNames())
        .toIterable()
        .containsExactlyInAnyOrder(
            "read", "applied", "notFound", "rejected", "skipped", "terminated");
    counts.forEach(c -> assertThat(c.isInt()).isTrue());
    IntakeApi.await(mvc, json, body(r).path("runId").asText());
  }

  @Test
  @DisplayName("[MAM-55 AC-1] reading the run gives the same business date and run id")
  void readSameBusinessDate() throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, oneRecord(), "2026-03-31");
    JsonNode run = IntakeApi.await(mvc, json, runId);
    assertThat(run.path("runId").asText()).isEqualTo(runId);
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
    assertThat(run.path("job").asText()).isEqualTo("PRVSAN01");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the business date is the explicit one, not today's: 2025-12-31 stays 2025-12-31")
  void explicitDate() throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, oneRecord(), "2025-12-31");
    assertThat(IntakeApi.await(mvc, json, runId).path("businessDate").asText())
        .isEqualTo("2025-12-31");
    assertThat(
            jdbc.queryForObject(
                "SELECT business_date::text FROM sanction_intake_run WHERE public_id = ?::uuid",
                String.class,
                runId))
        .isEqualTo("2025-12-31");
  }

  // ---- AC-1: what is refused with a 400 -------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] a call without the business date is a 400 problem+json that names it, and creates no run")
  void noBusinessDate() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), oneRecord(), null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("businessDate");
    assertThat(body(r).path("status").asInt()).isEqualTo(400);
    assertThat(runCount()).isZero();
  }

  @ParameterizedTest(name = "[MAM-55 AC-1] the business date \"{0}\" is not a date: 400 and no run")
  @ValueSource(
      strings = {
        "",
        "   ",
        "31/03/2026",
        "20260331",
        "2026-3-31",
        "2026-02-30",
        "2026-13-01",
        "today"
      })
  void businessDateIsNotADate(String date) throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), oneRecord(), date);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("businessDate");
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] a call without the file is a 400 problem+json that names it, and creates no run")
  void noFile() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), null, "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("file");
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] a call with neither the file nor the business date is a 400 and creates no run")
  void neither() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.admin(), null, null);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(runCount()).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-1] a refused call does not reach provider-service")
  void refusedCallSendsNothing() throws Exception {
    IntakeApi.start(mvc, PemJwt.admin(), oneRecord(), null);
    IntakeApi.start(mvc, PemJwt.admin(), null, "2026-03-31");
    assertThat(TestProviderService.MOCK.getAllServeEvents()).isEmpty();
  }

  // ---- AC-2: an empty file --------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-2] a file with no record completes with nothing read")
  void emptyFile() throws Exception {
    JsonNode run = IntakeApi.runToEnd(mvc, json, new byte[0], "2026-03-31");
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("read").asInt()).isZero();
    assertThat(run.path("counts").path("applied").asInt()).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM sanction", Integer.class)).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-2] counts.read is the number of records, whatever their kind")
  void readIsTheNumberOfRecords() throws Exception {
    TestProviderService.holds("1003000333", "A");
    TestProviderService.unknown("1003000999");
    byte[] file =
        SancinFile.lines(
            SancinFile.record("1003000111", "T", REASON, "20260331"),
            SancinFile.record("1003000999", "T", REASON, "20260331"),
            SancinFile.record("1003000333", "X", REASON, "20260331"),
            SancinFile.record("1003000333", "S", REASON, "20261331"));
    JsonNode run = IntakeApi.runToEnd(mvc, json, file, "2026-03-31");
    JsonNode counts = run.path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(4);
    List<Integer> parts =
        List.of(
            counts.path("applied").asInt(),
            counts.path("notFound").asInt(),
            counts.path("rejected").asInt(),
            counts.path("skipped").asInt());
    assertThat(parts).containsExactly(1, 1, 2, 0);
  }

  @Test
  @DisplayName("[MAM-55 AC-3] recordedBy is the login of the account that started the run")
  void recordedByIsTheLogin() throws Exception {
    String runId = IntakeApi.startAsAdmin(mvc, json, oneRecord(), "2026-03-31");
    IntakeApi.await(mvc, json, runId);
    assertThat(jdbc.queryForObject("SELECT recorded_by FROM sanction", String.class))
        .isEqualTo("admin1");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] a run started by the SYSTEM account is recorded under its service account")
  void recordedBySystem() throws Exception {
    MvcResult r = IntakeApi.start(mvc, PemJwt.system(), oneRecord(), "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    IntakeApi.await(mvc, json, body(r).path("runId").asText());
    assertThat(jdbc.queryForObject("SELECT recorded_by FROM sanction", String.class))
        .isEqualTo("service-account-pem-services");
  }
}
