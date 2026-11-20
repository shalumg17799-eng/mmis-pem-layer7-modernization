package gov.medicaid.pem.revalidation.api;

import static gov.medicaid.pem.revalidation.RevalidationFixtures.SWEEPS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.PemJwt;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The sweep endpoints (MAM-45 AC-1, AC-7, AC-8) on the real application: the answer of the start
 * call, the bodies it refuses with a problem+json, the run read back and the REVALQ file as bytes.
 * Business-date strictness is MAM-45 AC-1 in the feature file (lesson b of the earlier reviews).
 */
@Timeout(180)
class SweepApiTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private MvcResult adminGet(String url, Object... vars) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.get(url, vars).with(PemJwt.admin())).andReturn();
  }

  private static void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    JsonNode p = RevalidationFixtures.read(r.getResponse().getContentAsString());
    assertThat(p.path("title").asText()).isNotBlank();
    assertThat(p.path("status").asInt()).isEqualTo(status);
  }

  // ---- AC-1: the start call --------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-1] the start answer is 202 with runId, job, businessDate, status RUNNING, counts and startedAt")
  void startAnswer() throws Exception {
    MvcResult r =
        RevalidationFixtures.startSweep(mvc, PemJwt.admin(), "{\"businessDate\":\"2026-03-31\"}");

    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).contains("application/json");
    JsonNode run = RevalidationFixtures.read(r.getResponse().getContentAsString());
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo("revalidation-sweep");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
    assertThat(run.path("status").asText()).isEqualTo("RUNNING");
    assertThat(run.path("counts").path("queued").asInt()).isZero();
    assertThat(run.path("counts").path("noDueDate").asInt()).isZero();
    assertThat(run.path("startedAt").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*");
    assertThat(run.has("endedAt")).as("a RUNNING run has no end yet").isFalse();
    RevalidationFixtures.awaitRun(mvc, UUID.fromString(run.path("runId").asText()));
  }

  @Test
  @DisplayName("[MAM-45 AC-1] the run started is recorded with its business date")
  void startRecordsTheRun() throws Exception {
    UUID run = RevalidationFixtures.startAsAdmin(mvc, "2031-03-31");
    RevalidationFixtures.awaitRun(mvc, run);

    assertThat(
            jdbc.queryForObject(
                "SELECT business_date::text FROM sweep_run WHERE public_id = ?",
                String.class,
                run))
        .isEqualTo("2031-03-31");
  }

  @ParameterizedTest(name = "[MAM-45 AC-1] the body {0} is refused with a 400 problem and starts no run")
  @ValueSource(
      strings = {
        "",
        "{",
        "not json",
        "[]",
        "null",
        "{}",
        "{\"businessDate\":null}",
        "{\"businessDate\":}",
        "{\"date\":\"2026-03-31\"}",
        "{\"businessDate\":\"tomorrow\"}",
        "{\"businessDate\":\"2026-13-45\"}",
        "{\"businessDate\":\"2026-02-30\"}",
        "{\"businessDate\":true}",
        "{\"businessDate\":{}}"
      })
  void refusedBodies(String body) throws Exception {
    MvcResult r = RevalidationFixtures.startSweep(mvc, PemJwt.admin(), body);

    assertProblem(r, 400);
    assertThat(RevalidationFixtures.count(jdbc, "SELECT count(*) FROM sweep_run")).isZero();
  }

  // ---- AC-8: the run status --------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-45 AC-8] a COMPLETED run shows its counts, its business date and both times")
  void completedRun() throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "COMPLETED", LocalDate.of(2026, 3, 31), 2, 1, "", null);

    JsonNode body = RevalidationFixtures.read(adminGet(SWEEPS + "/{runId}", run).getResponse().getContentAsString());

    assertThat(body.path("runId").asText()).isEqualTo(run.toString());
    assertThat(body.path("job").asText()).isEqualTo("revalidation-sweep");
    assertThat(body.path("businessDate").asText()).isEqualTo("2026-03-31");
    assertThat(body.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(body.path("counts").path("queued").asInt()).isEqualTo(2);
    assertThat(body.path("counts").path("noDueDate").asInt()).isEqualTo(1);
    assertThat(body.path("startedAt").asText()).isEqualTo("2026-03-31T08:00:00Z");
    assertThat(body.path("endedAt").asText()).isEqualTo("2026-03-31T08:00:05Z");
    assertThat(body.has("error")).isFalse();
  }

  @Test
  @DisplayName("[MAM-45 AC-8] a FAILED run shows its reason in error and is never shown as COMPLETED")
  void failedRun() throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "FAILED", LocalDate.of(2026, 3, 31), 0, 0, null, "SYNTHETIC REASON");

    JsonNode body = RevalidationFixtures.read(adminGet(SWEEPS + "/{runId}", run).getResponse().getContentAsString());

    assertThat(body.path("status").asText()).isEqualTo("FAILED");
    assertThat(body.path("error").asText()).isEqualTo("SYNTHETIC REASON");
    assertThat(body.path("endedAt").asText()).isNotBlank();
  }

  @Test
  @DisplayName("[MAM-45 AC-8] a RUNNING run has no end time and no error")
  void runningRun() throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(jdbc, "RUNNING", LocalDate.of(2026, 3, 31), 0, 0, null, null);

    JsonNode body = RevalidationFixtures.read(adminGet(SWEEPS + "/{runId}", run).getResponse().getContentAsString());

    assertThat(body.path("status").asText()).isEqualTo("RUNNING");
    assertThat(body.has("endedAt")).isFalse();
    assertThat(body.has("error")).isFalse();
  }

  @Test
  @DisplayName("[MAM-45 AC-8] an unknown run is a 404 problem")
  void unknownRun() throws Exception {
    assertProblem(adminGet(SWEEPS + "/{runId}", UUID.randomUUID()), 404);
  }

  @Test
  @DisplayName("[MAM-45 AC-8] a run id that is not a UUID is a 400 problem")
  void runIdIsNotAUuid() throws Exception {
    assertProblem(adminGet(SWEEPS + "/{runId}", "not-a-uuid"), 400);
    assertProblem(adminGet(SWEEPS + "/{runId}/queue-file", "1003000222"), 400);
  }

  // ---- AC-7: the queue file --------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-7] the file of a COMPLETED run is returned byte for byte as text/plain, 80 bytes per record")
  void queueFileBytes() throws Exception {
    String record1 = "1003000222" + "20260228" + "RESCREEN-REQUIRED   " + " ".repeat(42);
    String record2 = "1003000333" + "20310331" + "RESCREEN-REQUIRED   " + " ".repeat(42);
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "COMPLETED", LocalDate.of(2026, 3, 31), 2, 0, record1 + record2, null);

    MvcResult r = adminGet(SWEEPS + "/{runId}/queue-file", run);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
    assertThat(r.getResponse().getContentAsByteArray())
        .hasSize(160)
        .isEqualTo((record1 + record2).getBytes(StandardCharsets.ISO_8859_1));
  }

  @Test
  @DisplayName("[MAM-45 AC-7] a COMPLETED run that opened no case has an empty file, not a 404")
  void emptyQueueFile() throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(jdbc, "COMPLETED", LocalDate.of(2026, 3, 31), 0, 0, "", null);

    MvcResult r = adminGet(SWEEPS + "/{runId}/queue-file", run);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-45 AC-7] a COMPLETED run whose file was never written also has an empty file")
  void nullQueueFile() throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "COMPLETED", LocalDate.of(2026, 3, 31), 0, 0, null, null);

    MvcResult r = adminGet(SWEEPS + "/{runId}/queue-file", run);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentAsByteArray()).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-45 AC-7] a {0} run has no file: 404 problem")
  @ValueSource(strings = {"RUNNING", "FAILED"})
  void noFileUntilCompleted(String status) throws Exception {
    UUID run =
        RevalidationFixtures.insertRun(jdbc, status, LocalDate.of(2026, 3, 31), 0, 0, null, null);

    assertProblem(adminGet(SWEEPS + "/{runId}/queue-file", run), 404);
  }

  @Test
  @DisplayName("[MAM-45 AC-7] an unknown run has no file: 404 problem")
  void unknownRunHasNoFile() throws Exception {
    assertProblem(adminGet(SWEEPS + "/{runId}/queue-file", UUID.randomUUID()), 404);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] the file of a run that really ran is the REVALQ of its cases (end to end through the job)")
  void fileOfARealRun() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000333", "A", LocalDate.of(2026, 3, 31));
    RevalidationFixtures.track(jdbc, "1003000222", "A", LocalDate.of(2026, 2, 28));
    RevalidationFixtures.track(jdbc, "1003000777", "A", LocalDate.of(2026, 4, 1));

    UUID run = RevalidationFixtures.sweep(mvc, "2026-03-31");

    MvcResult r = adminGet(SWEEPS + "/{runId}/queue-file", run);
    assertThat(new String(r.getResponse().getContentAsByteArray(), StandardCharsets.ISO_8859_1))
        .isEqualTo(
            "1003000222"
                + "20260228"
                + "RESCREEN-REQUIRED   "
                + " ".repeat(42)
                + "1003000333"
                + "20260331"
                + "RESCREEN-REQUIRED   "
                + " ".repeat(42));
  }
}
