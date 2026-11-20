package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.Dirout;
import gov.medicaid.pem.directory.IntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-67: the directory extract API against the real application with Postgres (Testcontainers):
 * start a run for an explicit business date, read the run, download the DIROUT file; the file is
 * checked byte by byte.
 */
class DirectoryExtractApiTest extends IntegrationTest {

  static final String BASE = "/api/v1/batch/directory-extracts";
  static final String DATE = "2026-03-31";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    DirectoryFixtures.replaceAll(
        jdbc,
        // inserted out of NPI order, with entries that must not be written
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"),
        Row.organization(
            "1003000777", "M00000777", "FA", "H", "S", "251G00000X", "SUSPENDED HOSPICE"),
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", "MD12345", "TX"),
        Row.organization(
            "1003000444", "M00000444", "GR", null, "D", "251E00000X", "EXCLUDED CLINIC LLC"),
        Row.individual(
            "1003000555", "M00000555", null, "P", "225100000X", "PAT", "SAMPLE", null, null));
  }

  String startBody(String date) {
    return "{\"businessDate\":\"" + date + "\"}";
  }

  MvcResult start(RequestPostProcessor who, String body) throws Exception {
    return mvc.perform(post(BASE).with(who).contentType(MediaType.APPLICATION_JSON).content(body))
        .andReturn();
  }

  JsonNode startAdmin(String date) throws Exception {
    MvcResult r = start(PemJwt.admin(), startBody(date));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return json.readTree(r.getResponse().getContentAsString());
  }

  byte[] file(JsonNode run) throws Exception {
    MvcResult r =
        mvc.perform(get(BASE + "/" + run.path("runId").asText() + "/file").with(PemJwt.admin()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return r.getResponse().getContentAsByteArray();
  }

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class);
  }

  // ---- AC-1 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-67 AC-1] the ADMIN starts a run and gets 202 with the finished BatchRun: PRVDIR01, 2026-03-31, COMPLETED, rows 2")
  void adminRuns() throws Exception {
    MvcResult r = start(PemJwt.admin(), startBody(DATE));
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    assertThat(r.getResponse().getContentType()).startsWith("application/json");
    JsonNode run = json.readTree(r.getResponse().getContentAsString());
    assertThat(UUID.fromString(run.path("runId").asText())).isNotNull();
    assertThat(run.path("job").asText()).isEqualTo("PRVDIR01");
    assertThat(run.path("businessDate").asText()).isEqualTo(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(2);
    assertThat(run.has("error")).isFalse();
  }

  @Test
  @DisplayName("[MAM-67 AC-1] startedAt and endedAt are set, and the run ended after it started")
  void timesSet() throws Exception {
    JsonNode run = startAdmin(DATE);
    Instant started = Instant.parse(run.path("startedAt").asText());
    Instant ended = Instant.parse(run.path("endedAt").asText());
    assertThat(ended).isAfterOrEqualTo(started);
  }

  @Test
  @DisplayName("[MAM-67 AC-1] counts.rows equals the number of records in the file")
  void rowsEqualRecords() throws Exception {
    JsonNode run = startAdmin(DATE);
    assertThat(Dirout.records(file(run))).hasSize(run.path("counts").path("rows").asInt());
  }

  @ParameterizedTest(name = "[MAM-67 AC-1] the business date {0} is echoed in the run")
  @ValueSource(strings = {"2026-03-31", "2026-01-15", "2024-02-29", "2026-12-31"})
  void dateEchoed(String date) throws Exception {
    assertThat(startAdmin(date).path("businessDate").asText()).isEqualTo(date);
  }

  @Test
  @DisplayName("[MAM-67 AC-1] the run can be read again: GET by id gives the same BatchRun")
  void statusGet() throws Exception {
    JsonNode started = startAdmin(DATE);
    MvcResult r =
        mvc.perform(get(BASE + "/" + started.path("runId").asText()).with(PemJwt.admin()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString())).isEqualTo(started);
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-1] a run id that does not exist is 404 problem+json for the status and for the file")
  void unknownRun() throws Exception {
    String unknown = UUID.randomUUID().toString();
    for (String path : List.of(BASE + "/" + unknown, BASE + "/" + unknown + "/file")) {
      MvcResult r = mvc.perform(get(path).with(PemJwt.admin())).andReturn();
      assertThat(r.getResponse().getStatus()).as(path).isEqualTo(404);
      assertThat(r.getResponse().getContentType()).as(path).startsWith("application/problem+json");
    }
  }

  @Test
  @DisplayName("[MAM-67 AC-1] a run id that is not a UUID is 400 problem+json")
  void notAUuid() throws Exception {
    MvcResult r = mvc.perform(get(BASE + "/not-a-uuid").with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  // ---- AC-2 -----------------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-67 AC-2] the body <{0}> is a 400 problem+json and creates no run")
  @ValueSource(
      strings = {
        "",
        "{}",
        "{\"businessDate\":null}",
        "{\"businessDate\":\"\"}",
        "{\"businessDate\":\"31/03/2026\"}",
        "{\"businessDate\":\"2026-02-30\"}",
        "{\"businessDate\":\"20260331\"}",
        "not json"
      })
  void businessDateRequired(String body) throws Exception {
    int before = runCount();
    MvcResult r = start(PemJwt.admin(), body);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(runCount()).isEqualTo(before);
  }

  @ParameterizedTest(
      name =
          "[MAM-67 AC-2] the date-time <{0}> is not a date (contract: format date): 400 problem+json, no run")
  @ValueSource(strings = {"2026-03-31T00:00:00", "2026-03-31T00:00:00Z"})
  void dateTimeIsNotADate(String value) throws Exception {
    int before = runCount();
    MvcResult r = start(PemJwt.admin(), "{\"businessDate\":\"" + value + "\"}");
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(runCount()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-67 AC-2] a start without any body is a 400 problem+json and creates no run")
  void noBody() throws Exception {
    int before = runCount();
    MvcResult r = mvc.perform(post(BASE).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(runCount()).isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-67 AC-2] the 400 says that the business date is the problem")
  void problemNamesTheDate() throws Exception {
    MvcResult r = start(PemJwt.admin(), "{\"businessDate\":\"31/03/2026\"}");
    JsonNode problem = json.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(400);
    assertThat(problem.path("detail").asText()).contains("businessDate");
  }

  // ---- AC-3 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-67 AC-3] only the two ACTIVE entries are written, NPI 1003000111 first, each 100 bytes, no header or trailer")
  void activeOnlyInNpiOrder() throws Exception {
    byte[] file = file(startAdmin(DATE));
    assertThat(file).hasSize(200);
    List<String> records = Dirout.records(file);
    assertThat(records).extracting(Dirout::npi).containsExactly("1003000111", "1003000333");
  }

  @ParameterizedTest(
      name =
          "[MAM-67 AC-3] with every entry in status {0} the run is COMPLETED with 0 rows and an empty file")
  @ValueSource(strings = {"P", "S", "T", "D"})
  void noActiveEntry(String status) throws Exception {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111", "M00000111", "L", status, "207Q00000X", "MARIA", "RIVERA", null, null),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", status, "251G00000X", "SUNSET HOSPICE INC"));
    JsonNode run = startAdmin(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("rows").asInt()).isZero();
    assertThat(file(run)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-3] an empty directory gives a COMPLETED run with 0 rows and an empty file")
  void emptyDirectory() throws Exception {
    DirectoryFixtures.clear(jdbc);
    JsonNode run = startAdmin(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("rows").asInt()).isZero();
    assertThat(file(run)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-3] more than one page of ACTIVE entries are all written in ascending NPI order")
  void moreThanOnePage() throws Exception {
    DirectoryFixtures.clear(jdbc);
    for (int i = 250; i >= 1; i--) {
      String npi = String.format("10031%05d", i);
      DirectoryFixtures.insert(
          jdbc,
          Row.individual(
              npi,
              "M" + npi.substring(2),
              "L",
              "A",
              "207Q00000X",
              "FIRST" + i,
              "LAST" + i,
              null,
              null));
    }
    JsonNode run = startAdmin(DATE);
    List<String> records = Dirout.records(file(run));
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(250);
    assertThat(records).hasSize(250);
    assertThat(records).extracting(Dirout::npi).isSorted().doesNotHaveDuplicates();
    assertThat(Dirout.npi(records.get(0))).isEqualTo("1003100001");
    assertThat(Dirout.npi(records.get(249))).isEqualTo("1003100250");
  }

  // ---- AC-4 and AC-5 --------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-67 AC-4] the record of NPI 1003000111: M00000111, 1003000111, IN, L, the name, 18 spaces")
  void recordLayout() throws Exception {
    String record = Dirout.records(file(startAdmin(DATE))).get(0);
    assertThat(record).hasSize(100);
    assertThat(Dirout.bytes(record, 1, 9)).isEqualTo("M00000111");
    assertThat(Dirout.bytes(record, 10, 19)).isEqualTo("1003000111");
    assertThat(Dirout.bytes(record, 20, 21)).isEqualTo("IN");
    assertThat(Dirout.bytes(record, 22, 22)).isEqualTo("L");
    assertThat(Dirout.bytes(record, 23, 82)).hasSize(60);
    assertThat(Dirout.bytes(record, 83, 100)).isEqualTo(" ".repeat(18));
  }

  @Test
  @DisplayName("[MAM-67 AC-4] the facility record has type FA and tier H")
  void facilityLayout() throws Exception {
    String record = Dirout.records(file(startAdmin(DATE))).get(1);
    assertThat(Dirout.medicaidId(record)).isEqualTo("M00000333");
    assertThat(Dirout.type(record)).isEqualTo("FA");
    assertThat(Dirout.tier(record)).isEqualTo("H");
  }

  @Test
  @DisplayName("[MAM-67 AC-4] an entry without a risk tier has a space at byte 22")
  void noTier() throws Exception {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000555", "M00000555", null, "A", "225100000X", "PAT", "SAMPLE", null, null));
    String record = Dirout.records(file(startAdmin(DATE))).get(0);
    assertThat(Dirout.tier(record)).isEqualTo(" ");
    assertThat(Dirout.type(record)).isEqualTo("IN");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-5] the names are MARIA RIVERA and SUNSET HOSPICE INC, padded with spaces to 60 bytes, and no licence number")
  void names() throws Exception {
    List<String> records = Dirout.records(file(startAdmin(DATE)));
    assertThat(Dirout.name(records.get(0))).isEqualTo(Dirout.padded("MARIA RIVERA", 60));
    assertThat(Dirout.name(records.get(1))).isEqualTo(Dirout.padded("SUNSET HOSPICE INC", 60));
    assertThat(String.join("", records)).doesNotContain("MD12345");
  }

  @Test
  @DisplayName("[MAM-67 AC-3] the file download is text/plain and has no line ends")
  void downloadFormat() throws Exception {
    JsonNode run = startAdmin(DATE);
    MvcResult r =
        mvc.perform(get(BASE + "/" + run.path("runId").asText() + "/file").with(PemJwt.admin()))
            .andReturn();
    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
    String body = r.getResponse().getContentAsString();
    assertThat(body).doesNotContain("\n").doesNotContain("\r");
  }

  // ---- AC-6 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-67 AC-6] running again for the same date gives a new runId and a byte-identical file")
  void rerunIdentical() throws Exception {
    JsonNode a = startAdmin(DATE);
    JsonNode b = startAdmin(DATE);
    assertThat(b.path("runId").asText()).isNotEqualTo(a.path("runId").asText());
    assertThat(file(b)).isEqualTo(file(a));
    assertThat(b.path("status").asText()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-67 AC-6] the file of the earlier run can still be downloaded after a later run")
  void earlierFileKept() throws Exception {
    JsonNode a = startAdmin(DATE);
    byte[] first = file(a);
    startAdmin(DATE);
    assertThat(file(a)).isEqualTo(first);
  }

  @Test
  @DisplayName("[MAM-67 AC-6] a run keeps the file it wrote when the directory changes later")
  void fileFrozenAtTheRun() throws Exception {
    JsonNode a = startAdmin(DATE);
    DirectoryFixtures.insert(
        jdbc,
        Row.individual(
            "1003000222", "M00000222", "M", "A", "207Q00000X", "ANA", "LOPEZ", null, null));
    JsonNode b = startAdmin(DATE);
    assertThat(file(a)).hasSize(200);
    assertThat(file(b)).hasSize(300);
  }

  @Test
  @DisplayName("[MAM-67 AC-6] each run is a row of its own")
  void runsAccumulate() throws Exception {
    int before = runCount();
    startAdmin(DATE);
    startAdmin(DATE);
    assertThat(runCount()).isEqualTo(before + 2);
  }
}
