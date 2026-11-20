package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TmsisApi;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
 * MAM-75 AC-8 (D-020): the business date is an explicit, required parameter. A start without a
 * businessDate, with a body that cannot be read, or with anything but an ISO date (yyyy-mm-dd) is a
 * 400 application/problem+json and starts no run. A value with a time part such as
 * 2026-03-31T00:00:00 is not a date (contract: format date; code review 2026-11-19, lesson b). The
 * list and the file download check their own parameters the same way.
 */
@Timeout(120)
class StartRequestInputTest extends IntegrationTest {

  int runCount() {
    return jdbc.queryForObject("SELECT count(*) FROM extract_run", Integer.class);
  }

  int jobCount() {
    return jdbc.queryForObject("SELECT count(*) FROM batch_job_instance", Integer.class);
  }

  void assertRejectedWithAProblem(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  // ---- AC-8: a start without a business date ----------------------------------------------------

  @ParameterizedTest(name = "[MAM-75 AC-8] the body <{0}> is a 400 problem+json and starts no run")
  @ValueSource(
      strings = {
        "",
        "{}",
        "{\"businessDate\":null}",
        "{\"businessDate\":\"\"}",
        "{\"businessDate\":\"   \"}",
        "{\"other\":\"2026-03-31\"}",
        "not json",
        "[]",
        "{\"businessDate\":"
      })
  void businessDateRequired(String body) throws Exception {
    int runs = runCount();
    int jobs = jobCount();
    assertRejectedWithAProblem(api.postBody(PemJwt.admin(), body));
    assertThat(runCount()).isEqualTo(runs);
    assertThat(jobCount()).isEqualTo(jobs);
  }

  @Test
  @DisplayName("[MAM-75 AC-8] a start without any body is a 400 problem+json and starts no run")
  void noBodyAtAll() throws Exception {
    int runs = runCount();
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(TmsisApi.BASE)
                    .with(PemJwt.admin())
                    .contentType(MediaType.APPLICATION_JSON))
            .andReturn();
    assertRejectedWithAProblem(r);
    assertThat(runCount()).isEqualTo(runs);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] the 400 of a missing business date says that businessDate is the problem")
  void problemNamesTheDate() throws Exception {
    MvcResult r = api.postBody(PemJwt.admin(), "{}");
    JsonNode problem = api.read(r);
    assertThat(problem.path("status").asInt()).isEqualTo(400);
    assertThat(problem.path("title").asText()).isNotBlank();
    assertThat(problem.path("detail").asText()).contains("businessDate");
  }

  // ---- lesson b: only an ISO date ---------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] the businessDate <{0}> is not an ISO date (yyyy-mm-dd): 400 problem+json and no run (code review 2026-11-19, lesson b)")
  @ValueSource(
      strings = {
        "31/03/2026",
        "03/31/2026",
        "2026-02-30",
        "2026-13-01",
        "2026-3-31",
        "20260331",
        "2026-03-31T00:00:00",
        "2026-03-31T00:00:00Z",
        "2026-03-31T23:59:59+01:00",
        "2026-03-31 00:00:00",
        "March 31, 2026",
        "tomorrow"
      })
  void onlyAnIsoDateIsAccepted(String value) throws Exception {
    int runs = runCount();
    int jobs = jobCount();
    assertRejectedWithAProblem(
        api.postBody(PemJwt.admin(), "{\"businessDate\":\"" + value + "\"}"));
    assertThat(runCount()).isEqualTo(runs);
    assertThat(jobCount()).isEqualTo(jobs);
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] the businessDate given as the JSON value {0} is not a date string: 400 problem+json and no run (lesson b)")
  @ValueSource(strings = {"20260331", "1774915200", "[2026,3,31]", "true", "{}", "2026.5"})
  void onlyAStringIsAccepted(String jsonValue) throws Exception {
    int runs = runCount();
    assertRejectedWithAProblem(
        api.postBody(PemJwt.admin(), "{\"businessDate\":" + jsonValue + "}"));
    assertThat(runCount()).isEqualTo(runs);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-8] the 400 for a date with a time part says that businessDate is the problem")
  void dateTimeProblemNamesTheDate() throws Exception {
    MvcResult r = api.postBody(PemJwt.admin(), "{\"businessDate\":\"2026-03-31T00:00:00\"}");
    assertRejectedWithAProblem(r);
    assertThat(api.read(r).path("detail").asText()).contains("businessDate");
  }

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] the ISO date {0} is accepted: 202 with that business date and period {1}")
  @CsvSource({
    "2026-03-31, 26090",
    "2026-01-01, 26001",
    "2026-12-31, 26365",
    "2028-02-29, 28060",
    "2024-02-29, 24060"
  })
  void isoDatesAreAccepted(String date, String period) throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    JsonNode run = api.start(date);
    assertThat(run.path("businessDate").asText()).isEqualTo(date);
    String runId = run.path("runId").asText();
    api.await(runId);
    byte[] file = api.file(runId);
    assertThat(new String(file, StandardCharsets.ISO_8859_1).substring(25, 30)).isEqualTo(period);
  }

  // ---- the other parameters ---------------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-75 AC-8] the list with the query <{0}> is a 400 problem+json (contract: page >= 0, size 1 to 200)")
  @ValueSource(
      strings = {
        "page=-1",
        "size=0",
        "size=-5",
        "size=201",
        "size=1000",
        "page=abc",
        "size=abc",
        "page=1.5"
      })
  void invalidListParameters(String query) throws Exception {
    assertRejectedWithAProblem(api.getPath(PemJwt.admin(), TmsisApi.BASE + "?" + query));
  }

  @ParameterizedTest(name = "[MAM-75 AC-8] the list with the query <{0}> is accepted: 200")
  @ValueSource(strings = {"page=0&size=1", "page=0&size=200", "size=20", "page=3"})
  void validListParameters(String query) throws Exception {
    MvcResult r = api.getPath(PemJwt.admin(), TmsisApi.BASE + "?" + query);
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("[MAM-75 AC-8] a run id that is not a UUID is a 400 problem+json for the file")
  void fileOfANonUuid() throws Exception {
    assertRejectedWithAProblem(api.getPath(PemJwt.admin(), TmsisApi.BASE + "/not-a-uuid/file"));
  }

  @Test
  @DisplayName("[MAM-75 AC-6] the file of a run id that does not exist is a 404 problem+json")
  void fileOfAnUnknownRun() throws Exception {
    MvcResult r = api.getPath(PemJwt.admin(), TmsisApi.BASE + "/" + UUID.randomUUID() + "/file");
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(api.read(r).path("status").asInt()).isEqualTo(404);
  }
}
