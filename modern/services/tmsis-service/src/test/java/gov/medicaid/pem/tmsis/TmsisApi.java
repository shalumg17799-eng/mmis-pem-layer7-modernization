package gov.medicaid.pem.tmsis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The T-MSIS extract API as the tests call it (POST, GET list, GET file) with MockMvc. A run is
 * asynchronous (ADR-004), so a test that needs its end waits for it with a time limit: a run that
 * is still RUNNING after {@link #RUN_BOUND} fails the test with a message, it never hangs it.
 */
public final class TmsisApi {

  public static final String BASE = "/api/v1/batch/tmsis-extracts";

  /** The longest a test waits for a run to end; a run of a few providers takes a second or two. */
  public static final Duration RUN_BOUND = Duration.ofSeconds(25);

  private final MockMvc mvc;
  private final ObjectMapper json;

  public TmsisApi(MockMvc mvc, ObjectMapper json) {
    this.mvc = mvc;
    this.json = json;
  }

  public static String body(String businessDate) {
    return "{\"businessDate\":\"" + businessDate + "\"}";
  }

  /** POST with this caller (null = no token) and this raw body. */
  public MvcResult postBody(RequestPostProcessor who, String body) throws Exception {
    MockHttpServletRequestBuilder request =
        post(BASE).contentType(MediaType.APPLICATION_JSON).content(body);
    return mvc.perform(who == null ? request : request.with(who)).andReturn();
  }

  /** GET of a path under the base path with this caller (null = no token). */
  public MvcResult getPath(RequestPostProcessor who, String path) throws Exception {
    MockHttpServletRequestBuilder request = get(path);
    return mvc.perform(who == null ? request : request.with(who)).andReturn();
  }

  public JsonNode read(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  /** The ADMIN starts a run for the date; asserts the 202 and returns the BatchRun answered. */
  public JsonNode start(String businessDate) throws Exception {
    MvcResult r = postBody(PemJwt.admin(), body(businessDate));
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return read(r);
  }

  /** The ADMIN starts a run and waits for its end; returns the run as the list shows it. */
  public JsonNode runToEnd(String businessDate) throws Exception {
    return await(start(businessDate).path("runId").asText());
  }

  /** Waits for the run to leave RUNNING (at most {@link #RUN_BOUND}); returns it as listed. */
  public JsonNode await(String runId) throws Exception {
    try {
      Awaitility.await("T-MSIS extract run " + runId + " has ended")
          .atMost(RUN_BOUND)
          .pollInterval(Duration.ofMillis(100))
          .until(() -> !"RUNNING".equals(listed(runId).path("status").asText()));
    } catch (ConditionTimeoutException e) {
      fail("run %s is still RUNNING after %s", runId, RUN_BOUND);
    }
    return listed(runId);
  }

  /** The BatchRun of the run as GET /api/v1/batch/tmsis-extracts lists it (as ADMIN). */
  public JsonNode listed(String runId) throws Exception {
    JsonNode page = list(0, 200);
    for (JsonNode item : page.path("items")) {
      if (runId.equals(item.path("runId").asText())) {
        return item;
      }
    }
    return fail("run %s is not in the list of runs: %s", runId, page);
  }

  /** The page of runs, as ADMIN. */
  public JsonNode list(int page, int size) throws Exception {
    MvcResult r = getPath(PemJwt.admin(), BASE + "?page=" + page + "&size=" + size);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return read(r);
  }

  /** The file of the run as ADMIN; asserts the 200. */
  public byte[] file(String runId) throws Exception {
    MvcResult r = getPath(PemJwt.admin(), BASE + "/" + runId + "/file");
    assertThat(r.getResponse().getStatus()).as("file of run " + runId).isEqualTo(200);
    return r.getResponse().getContentAsByteArray();
  }

  /** The status the file download of the run answers to the ADMIN. */
  public int fileStatus(String runId) throws Exception {
    return getPath(PemJwt.admin(), BASE + "/" + runId + "/file").getResponse().getStatus();
  }
}
