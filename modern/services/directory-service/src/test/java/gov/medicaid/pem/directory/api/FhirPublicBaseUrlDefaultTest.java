package gov.medicaid.pem.directory.api;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-65 AC-7, public FHIR fullUrl, the base URL is not configured: with {@code
 * pem.fhir.public-base-url} left unset the default {@code http://localhost:8080} is the base of
 * every Bundle entry's fullUrl (the local stack's gateway), again whatever host or forwarded header
 * the request carries. This class sets no property, so it runs in the shared application context.
 */
class FhirPublicBaseUrlDefaultTest extends FhirFullUrlChecks {

  static final String DEFAULT_BASE = "http://localhost:8080";

  @ParameterizedTest(
      name =
          "[MAM-65 AC-7] with pem.fhir.public-base-url unset, every fullUrl of the {0} Bundle is http://localhost:8080/fhir/{0}/<id>")
  @ValueSource(strings = {"Practitioner", "Organization", "PractitionerRole"})
  void defaultBase(String type) throws Exception {
    assertFullUrls(DEFAULT_BASE, type, search(type));
  }

  @ParameterizedTest(
      name =
          "[MAM-65 AC-7] with the property unset, a request with {0} still gets the default base in every fullUrl")
  @MethodSource(FACTORY)
  void forwardedHeadersAreIgnored(String label, Map<String, String> headers) throws Exception {
    assertFullUrls(DEFAULT_BASE, "Practitioner", searchWith("Practitioner", headers));
    assertFullUrls(DEFAULT_BASE, "Organization", searchWith("Organization", headers));
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] with the property unset, a request that arrives at another host and port still gets the default base")
  void requestHostIsIgnored() throws Exception {
    assertFullUrls(
        DEFAULT_BASE, "Practitioner", searchAt("http://attacker.example:7070", "Practitioner"));
  }
}
