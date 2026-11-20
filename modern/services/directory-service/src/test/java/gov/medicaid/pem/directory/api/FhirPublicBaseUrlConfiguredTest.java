package gov.medicaid.pem.directory.api;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

/**
 * MAM-65 AC-7, public FHIR fullUrl, the base URL is configured: with {@code
 * pem.fhir.public-base-url=https://pem.example.test} every Bundle entry's fullUrl starts with that
 * base followed by /fhir/&lt;Type&gt;/&lt;id&gt;, whatever host or forwarded header the request
 * carries. The tests are tagged AC-7 (and not AC-2) because the fullUrl is the R4 validity rule of
 * a searchset Bundle, next to the other fullUrl tests of this story.
 */
@TestPropertySource(properties = "pem.fhir.public-base-url=https://pem.example.test")
class FhirPublicBaseUrlConfiguredTest extends FhirFullUrlChecks {

  static final String BASE = "https://pem.example.test";

  @ParameterizedTest(
      name =
          "[MAM-65 AC-7] with pem.fhir.public-base-url set, every fullUrl of the {0} Bundle is that base + /fhir/{0}/<id>")
  @ValueSource(strings = {"Practitioner", "Organization", "PractitionerRole"})
  void configuredBase(String type) throws Exception {
    assertFullUrls(BASE, type, search(type));
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-7] a request with {0} still gets the configured base in every fullUrl")
  @MethodSource(FACTORY)
  void forwardedHeadersAreIgnored(String label, Map<String, String> headers) throws Exception {
    assertFullUrls(BASE, "Practitioner", searchWith("Practitioner", headers));
    assertFullUrls(BASE, "Organization", searchWith("Organization", headers));
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] a request that arrives at another host and port still gets the configured base")
  void requestHostIsIgnored() throws Exception {
    assertFullUrls(BASE, "Practitioner", searchAt("http://attacker.example:7070", "Practitioner"));
  }
}
