package gov.medicaid.pem.directory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-65 AC-7: the structural R4 check that stands in for the HAPI validator catches what it is
 * meant to catch, so that a green AC-7 test means something.
 */
class Fhir4ShapeTest {

  static final ObjectMapper JSON = new ObjectMapper();

  static final String VALID =
      """
      {"resourceType":"Bundle","type":"searchset","total":1,"entry":[
        {"fullUrl":"http://localhost/fhir/Practitioner/1003000111",
         "resource":{"resourceType":"Practitioner","id":"1003000111","active":true,
           "identifier":[{"system":"http://hl7.org/fhir/sid/us-npi","value":"1003000111"},
                         {"type":{"text":"Medicaid ID"},"value":"M00000111"}],
           "name":[{"family":"RIVERA","given":["MARIA"]}],
           "qualification":[{"identifier":[{"value":"MD12345"}],
                             "code":{"text":"State licence"},"issuer":{"display":"TX"}}]},
         "search":{"mode":"match"}}]}
      """;

  static JsonNode json(String text) throws Exception {
    return JSON.readTree(text);
  }

  @Test
  @DisplayName("[MAM-65 AC-7] a well-formed searchset Bundle has no violations")
  void valid() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID))).isEmpty();
    assertThat(Fhir4Shape.entriesWithoutFullUrl(json(VALID))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-65 AC-7] a Bundle without type is reported")
  void noType() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID.replace("\"type\":\"searchset\",", ""))))
        .anyMatch(s -> s.contains("Bundle.type (1..1)"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] a Bundle that is not a searchset is reported")
  void notSearchset() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID.replace("searchset", "collection"))))
        .anyMatch(s -> s.contains("must be searchset"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] a total smaller than the number of entries is reported")
  void totalTooSmall() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID.replace("\"total\":1", "\"total\":0"))))
        .anyMatch(s -> s.contains("Bundle.total"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] an element that R4 does not define on the resource is reported")
  void unknownElement() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID.replace("\"active\":true,", "\"network\":[{}],"))))
        .anyMatch(s -> s.contains("'network'"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] a resource id outside [A-Za-z0-9-.]{1,64} is reported")
  void badId() throws Exception {
    assertThat(
            Fhir4Shape.violations(json(VALID.replace("\"id\":\"1003000111\"", "\"id\":\"a b\""))))
        .anyMatch(s -> s.contains(".id must match"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] a search mode that is not match, include or outcome is reported")
  void badSearchMode() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID.replace("\"mode\":\"match\"", "\"mode\":\"hit\""))))
        .anyMatch(s -> s.contains("search.mode"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] an empty array is reported (FHIR JSON never has one)")
  void emptyArray() throws Exception {
    assertThat(Fhir4Shape.violations(json(VALID.replace("[\"MARIA\"]", "[]"))))
        .anyMatch(s -> s.contains("empty array"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] an identifier system that is not an absolute URI is reported")
  void badSystem() throws Exception {
    assertThat(
            Fhir4Shape.violations(
                json(VALID.replace("http://hl7.org/fhir/sid/us-npi", "us-npi-without-scheme"))))
        .anyMatch(s -> s.contains("absolute URI"));
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] a qualification without a code is reported (Practitioner.qualification.code 1..1)")
  void qualificationWithoutCode() throws Exception {
    assertThat(
            Fhir4Shape.violations(
                json(VALID.replace("\"code\":{\"text\":\"State licence\"},", ""))))
        .anyMatch(s -> s.contains("qualification[0].code is required"));
  }

  @Test
  @DisplayName("[MAM-65 AC-7] an entry without a fullUrl is reported")
  void noFullUrl() throws Exception {
    assertThat(
            Fhir4Shape.entriesWithoutFullUrl(
                json(
                    VALID.replace(
                        "\"fullUrl\":\"http://localhost/fhir/Practitioner/1003000111\",", ""))))
        .hasSize(1);
  }
}
