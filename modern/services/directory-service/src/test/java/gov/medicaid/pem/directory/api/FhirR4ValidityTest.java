package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.Fhir4Shape;
import gov.medicaid.pem.directory.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * MAM-65 AC-7: every Bundle and resource the live API returns for ACTIVE providers of type IN, GR
 * and FA is valid FHIR R4. This class checks the R4 structure with Fhir4Shape (see its
 * description); the fullUrl rule of R4 is its own test. The HAPI FHIR validator check of the same
 * Bundles (decision Q-112) is FhirR4HapiValidatorTest, and both checks stay.
 */
class FhirR4ValidityTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", "MD12345", "TX"),
        Row.organization(
            "1003000444", "M00000444", "GR", null, "A", "251E00000X", "EXCLUDED CLINIC LLC"),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"),
        // a minimal individual: no names, no licence, no taxonomy, no Medicaid ID
        new Row("1003000777", null, "IN", null, "A", "", null, null, null, null, null, null));
  }

  JsonNode bundle(String path) throws Exception {
    String body = mvc.perform(get(path)).andReturn().getResponse().getContentAsString();
    return json.readTree(body);
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-7] the {0} Bundle and its resources are valid R4 in structure")
  @ValueSource(strings = {"Practitioner", "Organization", "PractitionerRole"})
  void validStructure(String type) throws Exception {
    JsonNode b = bundle("/fhir/" + type);
    assertThat(b.path("total").asInt()).isPositive();
    assertThat(Fhir4Shape.violations(b)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] the searches for IN, GR and FA together return all three provider types")
  void allThreeTypesCovered() throws Exception {
    assertThat(bundle("/fhir/Practitioner").path("entry").findValuesAsText("id"))
        .contains("1003000111");
    assertThat(bundle("/fhir/Organization").path("entry").findValuesAsText("id"))
        .containsExactly("1003000333", "1003000444");
  }

  @ParameterizedTest(
      name =
          "[MAM-65 AC-7] every entry of the {0} Bundle has a fullUrl, as R4 requires outside transactions and batches")
  @ValueSource(strings = {"Practitioner", "Organization", "PractitionerRole"})
  void fullUrl(String type) throws Exception {
    assertThat(Fhir4Shape.entriesWithoutFullUrl(bundle("/fhir/" + type))).isEmpty();
  }
}
