package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.ContractFile;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-65 contract tests against modern/contracts/openapi/directory-service.yaml (1.1.0): the FHIR
 * operations answer what the contract declares (media types, FhirBundle, Problem, roles) and define
 * GET only.
 */
class FhirContractTest extends IntegrationTest {

  static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";

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
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"));
  }

  @Test
  @DisplayName("[MAM-65 AC-6] the contract defines only GET under /fhir")
  void contractIsReadOnly() {
    Map<String, Set<String>> methods = ContractFile.methodsOfPathsStartingWith("/fhir/");
    assertThat(methods)
        .containsOnlyKeys("/fhir/Practitioner", "/fhir/Organization", "/fhir/PractitionerRole");
    assertThat(methods.values()).allSatisfy(m -> assertThat(m).containsExactly("get"));
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-1] {0} is PUBLIC in the contract and answers 200 without a token")
  @CsvSource({
    "searchPractitioners,/fhir/Practitioner",
    "searchOrganizations,/fhir/Organization",
    "searchPractitionerRoles,/fhir/PractitionerRole"
  })
  void publicInTheContract(String operationId, String path) throws Exception {
    assertThat(ContractFile.roles(operationId)).containsExactly("PUBLIC");
    assertThat(ContractFile.operation(operationId).get("security")).isEqualTo(List.of());
    assertThat(mvc.perform(get(path)).andReturn().getResponse().getStatus()).isEqualTo(200);
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-1] {0} answers with a media type the contract declares for 200")
  @CsvSource({
    "searchPractitioners,/fhir/Practitioner",
    "searchOrganizations,/fhir/Organization",
    "searchPractitionerRoles,/fhir/PractitionerRole"
  })
  void declaredMediaType(String operationId, String path) throws Exception {
    MvcResult r = mvc.perform(get(path)).andReturn();
    assertThat(ContractFile.mediaTypes(operationId, 200)).containsExactly("application/fhir+json");
    assertThat(r.getResponse().getContentType()).startsWith("application/fhir+json");
  }

  @ParameterizedTest(name = "[MAM-65 AC-1] the {0} answer is a FhirBundle of the contract")
  @CsvSource({
    "Practitioner,/fhir/Practitioner",
    "Organization,/fhir/Organization",
    "PractitionerRole,/fhir/PractitionerRole"
  })
  void bundleMatchesSchema(String type, String path) throws Exception {
    JsonNode body =
        json.readTree(mvc.perform(get(path)).andReturn().getResponse().getContentAsString());
    assertThat(body.path("total").asInt()).isPositive();
    assertThat(ContractFile.violations("FhirBundle", body)).isEmpty();
  }

  @Test
  @DisplayName("[MAM-65 AC-1] an empty search answer is a FhirBundle of the contract too")
  void emptyBundleMatchesSchema() throws Exception {
    JsonNode body =
        json.readTree(
            mvc.perform(get("/fhir/Practitioner").param("identifier", NPI_SYSTEM + "|1009999999"))
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertThat(ContractFile.violations("FhirBundle", body)).isEmpty();
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-8] the 400 of {0} is the contract's Problem as application/problem+json")
  @CsvSource({
    "searchPractitioners,/fhir/Practitioner,identifier",
    "searchOrganizations,/fhir/Organization,identifier",
    "searchPractitionerRoles,/fhir/PractitionerRole,specialty"
  })
  void problemMatchesSchema(String operationId, String path, String parameter) throws Exception {
    String bad = "identifier".equals(parameter) ? NPI_SYSTEM + "|ABC" : " ";
    MvcResult r = mvc.perform(get(path).param(parameter, bad)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(ContractFile.mediaTypes(operationId, 400))
        .containsExactly("application/problem+json");
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(ContractFile.problemViolations(json.readTree(r.getResponse().getContentAsString())))
        .isEmpty();
  }
}
