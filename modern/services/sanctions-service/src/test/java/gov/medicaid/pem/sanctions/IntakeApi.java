package gov.medicaid.pem.sanctions;

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
 * The two endpoints of the SANCIN intake (contract sanctions-service.yaml: startSanctionIntakeRun,
 * getSanctionIntakeRun) as the tests call them, and the wait for a run that goes on in the
 * background.
 */
public final class IntakeApi {

  public static final String BASE = "/api/v1/batch/sanction-intake-runs";
  public static final Duration LONG = Duration.ofSeconds(60);

  private IntakeApi() {}

  /** POST with a multipart request: the file and the business date, each only when not null. */
  public static MvcResult start(
      MockMvc mvc, RequestPostProcessor who, byte[] file, String businessDate) throws Exception {
    MockMultipartHttpServletRequestBuilder request = multipart(BASE);
    if (file != null) {
      request.file(new MockMultipartFile("file", "sancin.dat", "application/octet-stream", file));
    }
    if (businessDate != null) {
      request.param("businessDate", businessDate);
    }
    if (who != null) {
      request.with(who);
    }
    return mvc.perform(request).andReturn();
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

  /** Starts a run as ADMIN, expects 202 and returns the run id. */
  public static String startAsAdmin(MockMvc mvc, ObjectMapper json, byte[] file, String date)
      throws Exception {
    MvcResult r = start(mvc, PemJwt.admin(), file, date);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(202);
    return json.readTree(r.getResponse().getContentAsString()).path("runId").asText();
  }

  /** The run as ADMIN reads it now. */
  public static JsonNode run(MockMvc mvc, ObjectMapper json, String runId) throws Exception {
    MvcResult r = read(mvc, runId, PemJwt.admin());
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  /** Waits until the run is no longer RUNNING and returns it. */
  public static JsonNode await(MockMvc mvc, ObjectMapper json, String runId) throws Exception {
    Awaitility.await("sanction intake run " + runId + " has ended")
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
}
