package gov.medicaid.pem.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * The three endpoints of the APPINPUT intake (contract enrollment-service.yaml: startIntakeRun,
 * getIntakeRun, getIntakeErrorFile) as the tests call them, and the wait for a run that goes on in
 * the background. A wait is always bounded: a run that does not end fails the test, it never hangs
 * the build.
 */
public final class IntakeApi {

  public static final String BASE = "/api/v1/batch/intake-runs";
  public static final Duration LONG = Duration.ofSeconds(60);

  private IntakeApi() {}

  /**
   * POST with a multipart request: the file, the business date and the batch id, each only when not
   * null.
   */
  public static MvcResult start(
      MockMvc mvc, RequestPostProcessor who, byte[] file, String businessDate, String batchId)
      throws Exception {
    return mvc.perform(request(who, file, businessDate, batchId)).andReturn();
  }

  /** The POST request itself, for tests that add a header or a part. */
  public static MockMultipartHttpServletRequestBuilder request(
      RequestPostProcessor who, byte[] file, String businessDate, String batchId) {
    MockMultipartHttpServletRequestBuilder request = multipart(BASE);
    if (file != null) {
      request.file(new MockMultipartFile("file", "appinput.dat", "application/octet-stream", file));
    }
    if (businessDate != null) {
      request.param("businessDate", businessDate);
    }
    if (batchId != null) {
      request.param("batchId", batchId);
    }
    if (who != null) {
      request.with(who);
    }
    return request;
  }

  /** POST without a batch id. */
  public static MvcResult start(
      MockMvc mvc, RequestPostProcessor who, byte[] file, String businessDate) throws Exception {
    return start(mvc, who, file, businessDate, null);
  }

  /** GET of one run. */
  public static MvcResult read(MockMvc mvc, String runId, RequestPostProcessor who)
      throws Exception {
    MockHttpServletRequestBuilder request = get(BASE + "/" + runId);
    if (who != null) {
      request.with(who);
    }
    return mvc.perform(request).andReturn();
  }

  /** GET of the APPERROR file of one run. */
  public static MvcResult errorFile(MockMvc mvc, String runId, RequestPostProcessor who)
      throws Exception {
    MockHttpServletRequestBuilder request = get(BASE + "/" + runId + "/error-file");
    if (who != null) {
      request.with(who);
    }
    return mvc.perform(request).andReturn();
  }

  /** Starts a run as ADMIN, expects 202 and returns the run id. */
  public static String startAsAdmin(MockMvc mvc, ObjectMapper json, byte[] file, String date)
      throws Exception {
    return startAsAdmin(mvc, json, file, date, null);
  }

  /** Starts a run as ADMIN with a batch id, expects 202 and returns the run id. */
  public static String startAsAdmin(
      MockMvc mvc, ObjectMapper json, byte[] file, String date, String batchId) throws Exception {
    MvcResult r = start(mvc, PemJwt.admin(), file, date, batchId);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return json.readTree(r.getResponse().getContentAsString()).path("runId").asText();
  }

  /** The run as ADMIN reads it now. */
  public static JsonNode run(MockMvc mvc, ObjectMapper json, String runId) throws Exception {
    MvcResult r = read(mvc, runId, PemJwt.admin());
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  /** Waits until the run is no longer RUNNING (at most {@link #LONG}) and returns it. */
  public static JsonNode await(MockMvc mvc, ObjectMapper json, String runId) throws Exception {
    Awaitility.await("intake run " + runId + " has ended")
        .atMost(LONG)
        .pollInterval(Duration.ofMillis(100))
        .until(() -> !"RUNNING".equals(run(mvc, json, runId).path("status").asText()));
    return run(mvc, json, runId);
  }

  /** Starts a run as ADMIN and waits for its end. */
  public static JsonNode runToEnd(MockMvc mvc, ObjectMapper json, byte[] file, String date)
      throws Exception {
    return await(mvc, json, startAsAdmin(mvc, json, file, date));
  }

  /** The APPERROR file of the run as ADMIN downloads it (200 expected). */
  public static String errorFileOf(MockMvc mvc, String runId) throws Exception {
    MvcResult r = errorFile(mvc, runId, PemJwt.admin());
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.ISO_8859_1);
  }
}
