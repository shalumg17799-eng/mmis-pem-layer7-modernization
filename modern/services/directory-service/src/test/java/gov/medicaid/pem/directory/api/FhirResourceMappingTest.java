package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.application.DirectoryEntry;
import gov.medicaid.pem.directory.application.FhirDirectory;
import gov.medicaid.pem.directory.application.ProviderProfile;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.kit.web.ProblemHandler;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * MAM-65: how a directory listing becomes a FHIR R4 Practitioner, Organization or PractitionerRole
 * (ADR-006, Q-008): only legacy facts, nothing invented, the Bundle counts every match. The
 * controller runs without Spring Boot, over an in-memory directory.
 */
class FhirResourceMappingTest {

  static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
  static final ObjectMapper JSON = new ObjectMapper();

  /** What the controller asked the directory for: "<kind>:<npi>:<name or taxonomy>:<limit>". */
  final List<String> asked = new ArrayList<>();

  List<FhirDirectory.Listing> listings = new ArrayList<>();
  long total = -1;

  final FhirDirectory directory =
      new FhirDirectory() {
        @Override
        public void saveProfile(ProviderProfile profile) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Result individuals(String npi, String name, int limit) {
          asked.add("individuals:" + npi + ":" + name + ":" + limit);
          return result();
        }

        @Override
        public Result organizations(String npi, String name, int limit) {
          asked.add("organizations:" + npi + ":" + name + ":" + limit);
          return result();
        }

        @Override
        public Result individualsWithTaxonomy(String taxonomy, int limit) {
          asked.add("taxonomy:" + taxonomy + ":" + limit);
          return result();
        }
      };

  final MockMvc mvc =
      MockMvcBuilders.standaloneSetup(new FhirController(directory))
          .setControllerAdvice(new ProblemHandler())
          .build();

  FhirDirectory.Result result() {
    return new FhirDirectory.Result(listings, total < 0 ? listings.size() : total);
  }

  static FhirDirectory.Listing individual(
      String first, String last, String licenseNo, String licenseState) {
    return new FhirDirectory.Listing(
        new DirectoryEntry("1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", "207Q00000X"),
        new ProviderProfile("1003000111", first, last, null, licenseNo, licenseState));
  }

  static FhirDirectory.Listing organization(String type, String legalName, String displayName) {
    return new FhirDirectory.Listing(
        new DirectoryEntry("1003000333", "M00000333", type, "H", "A", displayName, "251G00000X"),
        new ProviderProfile("1003000333", null, null, legalName, null, null));
  }

  MvcResult call(String path, String... params) throws Exception {
    var request = get(path);
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mvc.perform(request).andReturn();
  }

  JsonNode bundle(String path, String... params) throws Exception {
    MvcResult r = call(path, params);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return JSON.readTree(r.getResponse().getContentAsString());
  }

  JsonNode firstResource(String path, String... params) throws Exception {
    return bundle(path, params).path("entry").path(0).path("resource");
  }

  // ---- Bundle ---------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-65 AC-1] the answer is application/fhir+json")
  void contentType() throws Exception {
    assertThat(call("/fhir/Practitioner").getResponse().getContentType())
        .startsWith("application/fhir+json");
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-1] a search answers a Bundle of type searchset with the total of matches")
  void searchset() throws Exception {
    listings.add(individual("MARIA", "RIVERA", "MD12345", "TX"));
    JsonNode b = bundle("/fhir/Practitioner");
    assertThat(b.path("resourceType").asText()).isEqualTo("Bundle");
    assertThat(b.path("type").asText()).isEqualTo("searchset");
    assertThat(b.path("total").asInt()).isEqualTo(1);
    assertThat(b.path("entry")).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-65 AC-5] no match is a Bundle with total 0 and no entry element, not an error")
  void noMatch() throws Exception {
    JsonNode b = bundle("/fhir/Practitioner");
    assertThat(b.path("total").asInt()).isZero();
    assertThat(b.has("entry")).isFalse();
  }

  @Test
  @DisplayName("[MAM-65 AC-1] each entry has search.mode match")
  void searchMode() throws Exception {
    listings.add(individual("MARIA", "RIVERA", null, null));
    assertThat(
            bundle("/fhir/Practitioner").path("entry").path(0).path("search").path("mode").asText())
        .isEqualTo("match");
  }

  @Test
  @DisplayName("[MAM-65 AC-1] the total counts every match even when fewer entries are returned")
  void totalBeyondEntries() throws Exception {
    listings.add(individual("MARIA", "RIVERA", null, null));
    total = 250;
    JsonNode b = bundle("/fhir/Practitioner");
    assertThat(b.path("total").asInt()).isEqualTo(250);
    assertThat(b.path("entry")).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-65 AC-1] at most 200 entries are asked for")
  void entryLimit() throws Exception {
    call("/fhir/Practitioner");
    call("/fhir/Organization");
    call("/fhir/PractitionerRole");
    assertThat(asked)
        .containsExactly(
            "individuals:null:null:200", "organizations:null:null:200", "taxonomy:null:200");
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-1] the NPI of the identifier and the name text are passed to the directory")
  void searchParametersPassed() throws Exception {
    call("/fhir/Practitioner", "identifier", NPI_SYSTEM + "|1003000111", "name", "RIVERA");
    call("/fhir/Organization", "identifier", "1003000333", "name", "HOSPICE");
    assertThat(asked)
        .containsExactly(
            "individuals:1003000111:RIVERA:200", "organizations:1003000333:HOSPICE:200");
  }

  // ---- Practitioner ---------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-65 AC-2] a Practitioner holds the NPI and the Medicaid ID as identifiers")
  void practitionerIdentifiers() throws Exception {
    listings.add(individual("MARIA", "RIVERA", null, null));
    JsonNode ids = firstResource("/fhir/Practitioner").path("identifier");
    assertThat(ids).hasSize(2);
    assertThat(ids.get(0).path("system").asText()).isEqualTo(NPI_SYSTEM);
    assertThat(ids.get(0).path("value").asText()).isEqualTo("1003000111");
    assertThat(ids.get(1).path("type").path("text").asText()).isEqualTo("Medicaid ID");
    assertThat(ids.get(1).path("value").asText()).isEqualTo("M00000111");
  }

  @Test
  @DisplayName("[MAM-65 AC-2] a Practitioner without a Medicaid ID has only the NPI identifier")
  void practitionerWithoutMedicaidId() throws Exception {
    listings.add(
        new FhirDirectory.Listing(
            new DirectoryEntry("1003000111", null, "IN", null, "A", "MARIA RIVERA", null),
            new ProviderProfile("1003000111", "MARIA", "RIVERA", null, null, null)));
    JsonNode ids = firstResource("/fhir/Practitioner").path("identifier");
    assertThat(ids).hasSize(1);
    assertThat(ids.get(0).path("system").asText()).isEqualTo(NPI_SYSTEM);
  }

  @Test
  @DisplayName("[MAM-65 AC-2] the resource id is the NPI and the Practitioner is active")
  void practitionerIdAndActive() throws Exception {
    listings.add(individual("MARIA", "RIVERA", null, null));
    JsonNode p = firstResource("/fhir/Practitioner");
    assertThat(p.path("resourceType").asText()).isEqualTo("Practitioner");
    assertThat(p.path("id").asText()).isEqualTo("1003000111");
    assertThat(p.path("active").asBoolean()).isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] the name has family RIVERA and given MARIA, without the legacy padding")
  void practitionerName() throws Exception {
    listings.add(individual("MARIA     ", "RIVERA                    ", null, null));
    JsonNode name = firstResource("/fhir/Practitioner").path("name").path(0);
    assertThat(name.path("family").asText()).isEqualTo("RIVERA");
    assertThat(name.path("given")).hasSize(1);
    assertThat(name.path("given").get(0).asText()).isEqualTo("MARIA");
    assertThat(name.has("text")).isFalse();
  }

  @Test
  @DisplayName("[MAM-65 AC-2] without name fields the name is the directory display name as text")
  void practitionerNameFallback() throws Exception {
    listings.add(individual(null, "  ", null, null));
    JsonNode name = firstResource("/fhir/Practitioner").path("name").path(0);
    assertThat(name.path("text").asText()).isEqualTo("MARIA RIVERA");
    assertThat(name.has("family")).isFalse();
    assertThat(name.has("given")).isFalse();
  }

  @Test
  @DisplayName("[MAM-65 AC-2] with no name at all there is no name element")
  void practitionerWithoutAnyName() throws Exception {
    listings.add(
        new FhirDirectory.Listing(
            new DirectoryEntry("1003000111", "M00000111", "IN", "L", "A", "", null),
            new ProviderProfile("1003000111", null, null, null, null, null)));
    assertThat(firstResource("/fhir/Practitioner").has("name")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] the licence MD12345 of state TX is a qualification with the state as issuer")
  void qualification() throws Exception {
    listings.add(individual("MARIA", "RIVERA", "MD12345", "TX"));
    JsonNode q = firstResource("/fhir/Practitioner").path("qualification").path(0);
    assertThat(q.path("identifier").path(0).path("value").asText()).isEqualTo("MD12345");
    assertThat(q.path("issuer").path("display").asText()).isEqualTo("TX");
    assertThat(q.path("code").path("text").asText()).isEqualTo("State licence");
  }

  @Test
  @DisplayName("[MAM-65 AC-2] a licence without a state has no issuer")
  void qualificationWithoutState() throws Exception {
    listings.add(individual("MARIA", "RIVERA", " MD12345 ", null));
    JsonNode q = firstResource("/fhir/Practitioner").path("qualification").path(0);
    assertThat(q.path("identifier").path(0).path("value").asText()).isEqualTo("MD12345");
    assertThat(q.has("issuer")).isFalse();
  }

  @Test
  @DisplayName("[MAM-65 AC-2] no licence number means no qualification element")
  void noQualification() throws Exception {
    listings.add(individual("MARIA", "RIVERA", "  ", "TX"));
    assertThat(firstResource("/fhir/Practitioner").has("qualification")).isFalse();
  }

  @ParameterizedTest(name = "[MAM-65 AC-2] a {0} holds no address, telecom or network element")
  @ValueSource(strings = {"Practitioner", "Organization", "PractitionerRole"})
  void onlyLegacyFacts(String type) throws Exception {
    listings.add(
        "Organization".equals(type)
            ? organization("FA", "SUNSET HOSPICE INC", "SUNSET HOSPICE INC")
            : individual("MARIA", "RIVERA", "MD12345", "TX"));
    String json = firstResource("/fhir/" + type).toString().toLowerCase();
    assertThat(json).doesNotContain("\"address\"", "\"telecom\"", "network");
  }

  // ---- Organization ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-3] an Organization has the NPI and Medicaid ID identifiers, the legal name and the type code FA")
  void organizationFacility() throws Exception {
    listings.add(organization("FA", "SUNSET HOSPICE INC", "SUNSET HOSPICE INC"));
    JsonNode o = firstResource("/fhir/Organization");
    assertThat(o.path("resourceType").asText()).isEqualTo("Organization");
    assertThat(o.path("id").asText()).isEqualTo("1003000333");
    assertThat(o.path("identifier").get(0).path("value").asText()).isEqualTo("1003000333");
    assertThat(o.path("identifier").get(1).path("value").asText()).isEqualTo("M00000333");
    assertThat(o.path("name").asText()).isEqualTo("SUNSET HOSPICE INC");
    assertThat(o.path("type").get(0).path("coding").get(0).path("code").asText()).isEqualTo("FA");
    assertThat(o.path("active").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("[MAM-65 AC-3] a group carries the legacy type code GR (D-008)")
  void organizationGroup() throws Exception {
    listings.add(organization("GR", "EXCLUDED CLINIC LLC", "EXCLUDED CLINIC LLC"));
    JsonNode o = firstResource("/fhir/Organization");
    assertThat(o.path("type").get(0).path("coding").get(0).path("code").asText()).isEqualTo("GR");
    assertThat(o.path("type").get(0).path("text").asText()).isEqualTo("GR");
  }

  @Test
  @DisplayName("[MAM-65 AC-3] the legal name is preferred to the display name and is trimmed")
  void organizationLegalNameFirst() throws Exception {
    listings.add(organization("FA", "  SUNSET HOSPICE INC  ", "SOMETHING ELSE"));
    assertThat(firstResource("/fhir/Organization").path("name").asText())
        .isEqualTo("SUNSET HOSPICE INC");
  }

  @Test
  @DisplayName("[MAM-65 AC-3] without a legal name the display name is the name")
  void organizationDisplayNameFallback() throws Exception {
    listings.add(organization("FA", null, "SUNSET HOSPICE INC"));
    assertThat(firstResource("/fhir/Organization").path("name").asText())
        .isEqualTo("SUNSET HOSPICE INC");
  }

  @Test
  @DisplayName("[MAM-65 AC-3] with neither legal nor display name there is no name element")
  void organizationWithoutName() throws Exception {
    listings.add(organization("FA", " ", ""));
    assertThat(firstResource("/fhir/Organization").has("name")).isFalse();
  }

  // ---- PractitionerRole -----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-4] a PractitionerRole references the Practitioner of the NPI and carries the taxonomy as specialty")
  void practitionerRole() throws Exception {
    listings.add(individual("MARIA", "RIVERA", null, null));
    JsonNode r = firstResource("/fhir/PractitionerRole", "specialty", "207Q00000X");
    assertThat(r.path("resourceType").asText()).isEqualTo("PractitionerRole");
    assertThat(r.path("id").asText()).isEqualTo("1003000111");
    assertThat(r.path("practitioner").path("reference").asText())
        .isEqualTo("Practitioner/1003000111");
    JsonNode coding = r.path("specialty").get(0).path("coding").get(0);
    assertThat(coding.path("code").asText()).isEqualTo("207Q00000X");
    assertThat(coding.path("system").asText()).isEqualTo("http://nucc.org/provider-taxonomy");
    assertThat(r.path("active").asBoolean()).isTrue();
  }

  @Test
  @DisplayName("[MAM-65 AC-4] the specialty can be given as system|code and the code is used")
  void specialtyWithSystem() throws Exception {
    call("/fhir/PractitionerRole", "specialty", "http://nucc.org/provider-taxonomy|207Q00000X");
    assertThat(asked).containsExactly("taxonomy:207Q00000X:200");
  }

  @Test
  @DisplayName("[MAM-65 AC-4] without a specialty parameter every individual's role is searched")
  void noSpecialty() throws Exception {
    call("/fhir/PractitionerRole");
    assertThat(asked).containsExactly("taxonomy:null:200");
  }

  @ParameterizedTest(name = "[MAM-65 AC-8] specialty <{0}> is not a taxonomy code: 400")
  @ValueSource(strings = {"", "  ", "|", "http://nucc.org/provider-taxonomy|"})
  void blankSpecialty(String specialty) throws Exception {
    MvcResult r = call("/fhir/PractitionerRole", "specialty", specialty);
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(asked).isEmpty();
  }

  @Test
  @DisplayName("[MAM-65 AC-4] a role without taxonomy has no specialty element")
  void roleWithoutTaxonomy() throws Exception {
    listings.add(
        new FhirDirectory.Listing(
            new DirectoryEntry("1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", null),
            new ProviderProfile("1003000111", "MARIA", "RIVERA", null, null, null)));
    assertThat(firstResource("/fhir/PractitionerRole").has("specialty")).isFalse();
  }

  // ---- the identifier parameter ---------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-65 AC-1] identifier <{0}> is the NPI 1003000111")
  @ValueSource(
      strings = {
        "http://hl7.org/fhir/sid/us-npi|1003000111",
        "|1003000111",
        "1003000111",
        "0000000000"
      })
  void npiAccepted(String identifier) {
    assertThat(FhirController.npi(identifier)).hasSize(10).containsOnlyDigits();
  }

  @Test
  @DisplayName("[MAM-65 AC-1] no identifier parameter means no NPI filter")
  void noIdentifier() {
    assertThat(FhirController.npi(null)).isNull();
  }

  @ParameterizedTest(name = "[MAM-65 AC-8] identifier <{0}> is rejected with a 400")
  @ValueSource(
      strings = {
        "http://hl7.org/fhir/sid/us-npi|ABC",
        "http://hl7.org/fhir/sid/us-npi|100300011",
        "http://hl7.org/fhir/sid/us-npi|10030001111",
        "http://hl7.org/fhir/sid/us-npi|",
        "http://hl7.org/fhir/sid/us-npi|1003000111 ",
        "http://hl7.org/fhir/sid/us-npi| 1003000111",
        "http://hl7.org/fhir/sid/us-npi|100300011A",
        "http://example.org/other-system|1003000111",
        "ABC",
        "",
        "1003000111|1003000111"
      })
  void npiRejected(String identifier) {
    assertThatThrownBy(() -> FhirController.npi(identifier))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(400));
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-8] a bad identifier answers 400 problem+json and the directory is not asked")
  void badIdentifierAnswer() throws Exception {
    MvcResult r = call("/fhir/Organization", "identifier", NPI_SYSTEM + "|ABC");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode problem = JSON.readTree(r.getResponse().getContentAsString());
    assertThat(problem.path("status").asInt()).isEqualTo(400);
    assertThat(problem.path("detail").asText()).contains("10 digits");
    assertThat(asked).isEmpty();
  }

  @Test
  @DisplayName("[MAM-65 AC-8] another identifier system is named in the 400 detail")
  void otherSystem() throws Exception {
    MvcResult r = call("/fhir/Practitioner", "identifier", "http://example.org/x|1003000111");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(JSON.readTree(r.getResponse().getContentAsString()).path("detail").asText())
        .contains(NPI_SYSTEM);
  }
}
