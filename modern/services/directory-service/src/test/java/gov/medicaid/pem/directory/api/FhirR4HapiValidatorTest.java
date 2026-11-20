package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.common.hapi.validation.support.CachingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MAM-65 AC-7: every Bundle and resource the live API returns for ACTIVE providers of type IN, GR
 * and FA is checked with the HAPI FHIR validator against FHIR R4, and the validator reports no
 * error (decision Q-112). The structural Fhir4Shape check stays next to it in FhirR4ValidityTest.
 *
 * <p>The validator runs fully offline: the R4 definitions come from the HAPI jars and there is no
 * terminology server (no tx.fhir.org). Its support chain is
 *
 * <ul>
 *   <li>DefaultProfileValidationSupport: the R4 structure definitions, code systems and value sets
 *       of the FHIR specification;
 *   <li>InMemoryTerminologyServerValidationSupport: expands and checks those value sets in memory;
 *   <li>CommonCodeSystemsTerminologyService: language tags, mime types, currencies, countries, UCUM
 *       and USPS codes.
 * </ul>
 *
 * <p>An external code system that none of these knows (the NUCC provider taxonomy) cannot be
 * checked offline. HAPI reports it as the warning "CodeSystem is unknown and can't be validated",
 * not as an error, because the bindings that carry it are preferred or example, not required. That
 * is the validator's own behaviour, so no setting is needed for it. The validator is deliberately
 * NOT switched to setNoTerminologyChecks(true): that would also hide wrong codes in the FHIR code
 * systems, and a control test below proves the configured validator still rejects such a code. The
 * NPI identifier system needs no check: Identifier.system is a plain URI.
 *
 * <p>Warnings and information messages are printed, never asserted away. Errors and fatal messages
 * fail the test and are listed in the failure message as "label | severity | path | message".
 */
class FhirR4HapiValidatorTest extends IntegrationTest {

  private static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
  private static final String NUCC_SYSTEM = "http://nucc.org/provider-taxonomy";

  /** Heavy to build (it loads the R4 definitions): created once for the class. */
  private static FhirValidator validator;

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeAll
  static void validator() {
    FhirContext r4 = FhirContext.forR4();
    CachingValidationSupport support =
        new CachingValidationSupport(
            new ValidationSupportChain(
                new DefaultProfileValidationSupport(r4),
                new InMemoryTerminologyServerValidationSupport(r4),
                new CommonCodeSystemsTerminologyService(r4)));
    FhirInstanceValidator instance = new FhirInstanceValidator(support);
    // Terminology checks stay ON: an unknown external code system is a warning (see the class
    // comment), while a wrong code in a FHIR code system is still an error.
    instance.setNoTerminologyChecks(false);
    validator = r4.newValidator().registerValidatorModule(instance);
  }

  @AfterAll
  static void release() {
    validator = null; // the R4 definitions are large; give the heap back to the other tests
  }

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

  /** One GET search: the resource type and an optional query parameter. */
  record Search(String type, String param, String value) {

    MockHttpServletRequestBuilder request() {
      MockHttpServletRequestBuilder request = get("/fhir/" + type);
      return param == null ? request : request.param(param, value);
    }

    @Override
    public String toString() {
      return "/fhir/" + type + (param == null ? "" : "?" + param + "=" + value);
    }
  }

  static Stream<Search> searches() {
    return Stream.of(
        new Search("Practitioner", null, null),
        new Search("Practitioner", "identifier", NPI_SYSTEM + "|1003000111"),
        new Search("Practitioner", "name", "RIVERA"),
        new Search("Organization", null, null),
        new Search("Organization", "identifier", NPI_SYSTEM + "|1003000444"),
        new Search("Organization", "name", "SUNSET"),
        new Search("PractitionerRole", null, null),
        new Search("PractitionerRole", "specialty", "207Q00000X"),
        new Search("Practitioner", "name", "NOSUCHNAME"));
  }

  /** The body the API returns for the search. */
  String body(Search search) throws Exception {
    return mvc.perform(search.request()).andReturn().getResponse().getContentAsString();
  }

  String body(String type) throws Exception {
    return body(new Search(type, null, null));
  }

  /** Runs the HAPI validator, prints every message and returns all of them. */
  static List<SingleValidationMessage> messagesOf(String label, String resourceJson) {
    List<SingleValidationMessage> messages =
        validator.validateWithResult(resourceJson).getMessages();
    for (SingleValidationMessage m : messages) {
      System.out.println("HAPI-MAM-65-AC-7 " + line(label, m));
    }
    return messages;
  }

  static String line(String label, SingleValidationMessage m) {
    return label + " | " + m.getSeverity() + " | " + m.getLocationString() + " | " + m.getMessage();
  }

  /** The errors and fatal messages of the validator, one readable line each. */
  static List<String> errorsOf(String label, String resourceJson) {
    List<String> errors = new ArrayList<>();
    for (SingleValidationMessage m : messagesOf(label, resourceJson)) {
      if (m.getSeverity() == ResultSeverityEnum.ERROR
          || m.getSeverity() == ResultSeverityEnum.FATAL) {
        errors.add(line(label, m));
      }
    }
    return errors;
  }

  @ParameterizedTest(name = "[MAM-65 AC-7] HAPI reports no error for the Bundle of GET {0}")
  @MethodSource("searches")
  void bundleHasNoErrors(Search search) throws Exception {
    assertThat(errorsOf(search.toString(), body(search))).isEmpty();
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-7] HAPI reports no error for any single resource of the {0} search")
  @ValueSource(strings = {"Practitioner", "Organization", "PractitionerRole"})
  void everyResourceHasNoErrors(String type) throws Exception {
    JsonNode entries = json.readTree(body(type)).path("entry");
    assertThat(entries.size()).as("resources returned by the %s search", type).isPositive();
    List<String> errors = new ArrayList<>();
    for (JsonNode entry : entries) {
      JsonNode resource = entry.path("resource");
      errors.addAll(
          errorsOf(
              resource.path("resourceType").asText() + "/" + resource.path("id").asText(),
              json.writeValueAsString(resource)));
    }
    assertThat(errors).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] the validated searches return individuals, a group and a facility, with positive totals")
  void inGrFaAreValidated() throws Exception {
    JsonNode practitioners = json.readTree(body("Practitioner"));
    JsonNode organizations = json.readTree(body("Organization"));
    JsonNode roles = json.readTree(body("PractitionerRole"));
    assertThat(practitioners.path("entry").findValuesAsText("id")).contains("1003000111");
    assertThat(organizations.path("entry").findValuesAsText("id"))
        .containsExactly("1003000333", "1003000444");
    assertThat(roles.path("total").asInt()).isPositive();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] the NUCC taxonomy code system, which cannot be checked offline, is a warning and not an error")
  void unknownExternalCodeSystemIsNotAnError() throws Exception {
    List<SingleValidationMessage> aboutNucc =
        messagesOf("PractitionerRole", body("PractitionerRole")).stream()
            .filter(m -> m.getMessage().contains(NUCC_SYSTEM))
            .toList();
    assertThat(aboutNucc).isNotEmpty();
    assertThat(aboutNucc)
        .extracting(SingleValidationMessage::getSeverity)
        .containsOnly(ResultSeverityEnum.WARNING);
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] control: the configured validator reports an error for a Bundle whose entries have no fullUrl")
  void controlMissingFullUrl() throws Exception {
    JsonNode bundle = json.readTree(body("Practitioner"));
    for (JsonNode entry : bundle.path("entry")) {
      ((ObjectNode) entry).remove("fullUrl");
    }
    assertThat(errorsOf("control-no-fullUrl", json.writeValueAsString(bundle))).isNotEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-7] control: the configured validator reports an error for a code outside a required value set")
  void controlInvalidCode() {
    String practitioner = "{\"resourceType\":\"Practitioner\",\"id\":\"1\",\"gender\":\"robot\"}";
    assertThat(errorsOf("control-gender", practitioner)).isNotEmpty();
  }
}
