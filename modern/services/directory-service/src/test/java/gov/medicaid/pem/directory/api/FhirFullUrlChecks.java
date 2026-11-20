package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MAM-65 AC-7 (R4: every entry of a searchset Bundle has an absolute fullUrl, the server base plus
 * /fhir/&lt;Type&gt;/&lt;id&gt;): what the fullUrl tests of this story share. The base is the
 * public base URL that is configured, property {@code pem.fhir.public-base-url} (default {@code
 * http://localhost:8080}). It is never the host of the request nor any forwarded header, because
 * those come from the caller and the public FHIR API must not echo them into the URLs it publishes.
 *
 * <p>Abstract: the two concrete classes differ only in how the property is set, so each runs in its
 * own Spring context.
 */
abstract class FhirFullUrlChecks extends IntegrationTest {

  static final String FACTORY =
      "gov.medicaid.pem.directory.api.FhirFullUrlChecks#forwardedHeaderSets";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    // one ACTIVE individual (a Practitioner and a PractitionerRole), one group and one facility
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", "MD12345", "TX"),
        Row.organization(
            "1003000444", "M00000444", "GR", null, "A", "251E00000X", "EXCLUDED CLINIC LLC"),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"));
  }

  /** What a caller (or a gateway) can put on a request to try to steer the published URLs. */
  static Stream<Arguments> forwardedHeaderSets() {
    Map<String, String> xForwarded = new LinkedHashMap<>();
    xForwarded.put("X-Forwarded-Host", "evil.example");
    xForwarded.put("X-Forwarded-Proto", "https");
    xForwarded.put("X-Forwarded-Port", "9999");
    return Stream.of(
        Arguments.of("X-Forwarded-Host, X-Forwarded-Proto and X-Forwarded-Port", xForwarded),
        Arguments.of("X-Forwarded-Prefix", Map.of("X-Forwarded-Prefix", "/evil-prefix")),
        Arguments.of(
            "the standard Forwarded header",
            Map.of("Forwarded", "for=192.0.2.60;proto=https;host=evil.example:9999")));
  }

  static MockHttpServletRequestBuilder search(String type) {
    return get("/fhir/" + type);
  }

  static MockHttpServletRequestBuilder searchWith(String type, Map<String, String> headers) {
    MockHttpServletRequestBuilder request = search(type);
    headers.forEach(request::header);
    return request;
  }

  /** A request that arrives at a host name and port of its own (an absolute URL in MockMvc). */
  static MockHttpServletRequestBuilder searchAt(String origin, String type) {
    return get(URI.create(origin + "/fhir/" + type));
  }

  /**
   * Performs the search and requires, for every entry of the Bundle, the fullUrl {@code base +
   * /fhir/<type>/<id>}, in entry order. The Bundle must have entries, so the check is not vacuous.
   */
  void assertFullUrls(String base, String type, MockHttpServletRequestBuilder request)
      throws Exception {
    MvcResult r = mvc.perform(request).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    JsonNode entries = json.readTree(r.getResponse().getContentAsString()).path("entry");
    assertThat(entries.size()).as("entries of the %s search", type).isPositive();
    List<String> actual = new ArrayList<>();
    List<String> expected = new ArrayList<>();
    for (JsonNode entry : entries) {
      actual.add(entry.path("fullUrl").asText());
      expected.add(base + "/fhir/" + type + "/" + entry.path("resource").path("id").asText());
    }
    assertThat(actual).containsExactlyElementsOf(expected);
  }
}
