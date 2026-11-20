package gov.medicaid.pem.directory.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MAM-65: the public FHIR R4 API against the real application with Postgres (Testcontainers): no
 * token, ACTIVE providers only, only legacy facts, GET only, a bad identifier is a 400.
 */
class FhirApiTest extends IntegrationTest {

  static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void directory() {
    DirectoryFixtures.replaceAll(
        jdbc,
        // inserted out of NPI order on purpose
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"),
        Row.individual(
            "1003000555", "M00000555", null, "A", "225100000X", "PAT", "SAMPLE", null, null),
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", "MD12345", "TX"),
        Row.organization(
            "1003000444", "M00000444", "GR", null, "A", "251E00000X", "EXCLUDED CLINIC LLC"));
  }

  MvcResult search(String path, String... params) throws Exception {
    MockHttpServletRequestBuilder request = get(path);
    for (int i = 0; i < params.length; i += 2) {
      request.param(params[i], params[i + 1]);
    }
    return mvc.perform(request).andReturn();
  }

  JsonNode bundle(String path, String... params) throws Exception {
    MvcResult r = search(path, params);
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  static List<String> ids(JsonNode bundle) {
    List<String> out = new ArrayList<>();
    bundle.path("entry").forEach(e -> out.add(e.path("resource").path("id").asText()));
    return out;
  }

  // ---- AC-1 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-1] anyone, with no token, finds an ACTIVE individual by NPI: 200 application/fhir+json, searchset total 1")
  void practitionerByNpi() throws Exception {
    MvcResult r = search("/fhir/Practitioner", "identifier", NPI_SYSTEM + "|1003000111");
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("application/fhir+json");
    JsonNode b = json.readTree(r.getResponse().getContentAsString());
    assertThat(b.path("resourceType").asText()).isEqualTo("Bundle");
    assertThat(b.path("type").asText()).isEqualTo("searchset");
    assertThat(b.path("total").asInt()).isEqualTo(1);
    JsonNode p = b.path("entry").get(0).path("resource");
    assertThat(p.path("resourceType").asText()).isEqualTo("Practitioner");
    assertThat(p.path("identifier").get(0).path("system").asText()).isEqualTo(NPI_SYSTEM);
    assertThat(p.path("identifier").get(0).path("value").asText()).isEqualTo("1003000111");
  }

  @ParameterizedTest(name = "[MAM-65 AC-1] {0} answers without a token")
  @ValueSource(strings = {"/fhir/Practitioner", "/fhir/Organization", "/fhir/PractitionerRole"})
  void publicWithoutToken(String path) throws Exception {
    assertThat(search(path).getResponse().getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName("[MAM-65 AC-1] a signed-in user is answered the same as anyone")
  void signedInSameAnswer() throws Exception {
    MvcResult anonymous = search("/fhir/Practitioner");
    MvcResult signedIn = mvc.perform(get("/fhir/Practitioner").with(PemJwt.analyst())).andReturn();
    assertThat(signedIn.getResponse().getStatus()).isEqualTo(200);
    assertThat(signedIn.getResponse().getContentAsString())
        .isEqualTo(anonymous.getResponse().getContentAsString());
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-1] a search without parameters lists every ACTIVE individual in ascending NPI order")
  void practitionersInNpiOrder() throws Exception {
    JsonNode b = bundle("/fhir/Practitioner");
    assertThat(ids(b)).containsExactly("1003000111", "1003000555");
    assertThat(b.path("total").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-1] an NPI that is not in the directory is a Bundle with total 0, not an error")
  void unknownNpi() throws Exception {
    JsonNode b = bundle("/fhir/Practitioner", "identifier", NPI_SYSTEM + "|1009999999");
    assertThat(b.path("total").asInt()).isZero();
    assertThat(b.has("entry")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-1] the identifier search does not find an organisation among the Practitioners")
  void organisationNpiNotAPractitioner() throws Exception {
    JsonNode b = bundle("/fhir/Practitioner", "identifier", NPI_SYSTEM + "|1003000333");
    assertThat(b.path("total").asInt()).isZero();
  }

  // ---- AC-2 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-2] searching Practitioner by name=RIVERA returns the identifiers, the name and the licence")
  void practitionerFacts() throws Exception {
    JsonNode b = bundle("/fhir/Practitioner", "name", "RIVERA");
    assertThat(b.path("total").asInt()).isEqualTo(1);
    JsonNode p = b.path("entry").get(0).path("resource");
    assertThat(p.path("identifier").get(0).path("value").asText()).isEqualTo("1003000111");
    assertThat(p.path("identifier").get(1).path("value").asText()).isEqualTo("M00000111");
    assertThat(p.path("name").get(0).path("family").asText()).isEqualTo("RIVERA");
    assertThat(p.path("name").get(0).path("given").get(0).asText()).isEqualTo("MARIA");
    JsonNode q = p.path("qualification").get(0);
    assertThat(q.path("identifier").get(0).path("value").asText()).isEqualTo("MD12345");
    assertThat(q.path("issuer").path("display").asText()).isEqualTo("TX");
  }

  @Test
  @DisplayName("[MAM-65 AC-2] the Practitioner has no address, telecom or network element")
  void noContactData() throws Exception {
    String p = bundle("/fhir/Practitioner", "name", "RIVERA").toString().toLowerCase();
    assertThat(p).doesNotContain("\"address\"", "\"telecom\"", "network");
  }

  @Test
  @DisplayName("[MAM-65 AC-2] the name search matches the first name too, in any case")
  void nameMatchesFirstNameAnyCase() throws Exception {
    assertThat(ids(bundle("/fhir/Practitioner", "name", "maria"))).containsExactly("1003000111");
  }

  // ---- AC-3 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-3] Organization by name=HOSPICE and by identifier both return the facility with its legacy type FA")
  void organizationSearches() throws Exception {
    for (String[] query :
        new String[][] {{"name", "HOSPICE"}, {"identifier", NPI_SYSTEM + "|1003000333"}}) {
      JsonNode b = bundle("/fhir/Organization", query);
      assertThat(b.path("total").asInt()).isEqualTo(1);
      JsonNode o = b.path("entry").get(0).path("resource");
      assertThat(o.path("resourceType").asText()).isEqualTo("Organization");
      assertThat(o.path("identifier").get(0).path("value").asText()).isEqualTo("1003000333");
      assertThat(o.path("identifier").get(1).path("value").asText()).isEqualTo("M00000333");
      assertThat(o.path("name").asText()).isEqualTo("SUNSET HOSPICE INC");
      assertThat(o.path("type").get(0).path("coding").get(0).path("code").asText()).isEqualTo("FA");
    }
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-3] groups and facilities are Organizations, individuals never, in NPI order")
  void organizationsOnly() throws Exception {
    assertThat(ids(bundle("/fhir/Organization"))).containsExactly("1003000333", "1003000444");
  }

  // ---- AC-4 -----------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-4] specialty=207Q00000X returns only the role of NPI 1003000111, referencing its Practitioner")
  void roleBySpecialty() throws Exception {
    JsonNode b = bundle("/fhir/PractitionerRole", "specialty", "207Q00000X");
    assertThat(b.path("total").asInt()).isEqualTo(1);
    JsonNode r = b.path("entry").get(0).path("resource");
    assertThat(r.path("practitioner").path("reference").asText())
        .isEqualTo("Practitioner/1003000111");
    assertThat(r.path("specialty").get(0).path("coding").get(0).path("code").asText())
        .isEqualTo("207Q00000X");
  }

  @Test
  @DisplayName("[MAM-65 AC-4] a taxonomy no ACTIVE individual has gives total 0")
  void roleUnknownSpecialty() throws Exception {
    assertThat(bundle("/fhir/PractitionerRole", "specialty", "999Z00000X").path("total").asInt())
        .isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-4] the organisation's taxonomy is not a PractitionerRole")
  void organisationHasNoRole() throws Exception {
    assertThat(bundle("/fhir/PractitionerRole", "specialty", "251G00000X").path("total").asInt())
        .isZero();
  }

  // ---- AC-5 -----------------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-65 AC-5] a provider with status {0} is in no Bundle")
  @ValueSource(strings = {"P", "S", "T", "D"})
  void onlyActive(String status) throws Exception {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000111",
            "M00000111",
            "L",
            status,
            "207Q00000X",
            "MARIA",
            "RIVERA",
            "MD12345",
            "TX"),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", status, "251G00000X", "SUNSET HOSPICE INC"),
        Row.organization(
            "1003000444", "M00000444", "GR", null, status, "251E00000X", "EXCLUDED CLINIC LLC"));
    for (String path :
        new String[] {"/fhir/Practitioner", "/fhir/Organization", "/fhir/PractitionerRole"}) {
      JsonNode b = bundle(path);
      assertThat(b.path("total").asInt()).as(path).isZero();
      assertThat(b.has("entry")).as(path).isFalse();
    }
  }

  @Test
  @DisplayName("[MAM-65 AC-5] a suspended provider found by its own NPI is still not offered")
  void suspendedByNpi() throws Exception {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "S", "251G00000X", "SUNSET HOSPICE INC"));
    assertThat(
            bundle("/fhir/Organization", "identifier", NPI_SYSTEM + "|1003000333")
                .path("total")
                .asInt())
        .isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-5] a provider that is ACTIVE again is offered again")
  void reactivated() throws Exception {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "S", "251G00000X", "SUNSET HOSPICE INC"));
    jdbc.update("UPDATE directory_entry SET enroll_status = 'A' WHERE npi = '1003000333'");
    assertThat(ids(bundle("/fhir/Organization"))).containsExactly("1003000333");
  }

  // ---- AC-6 -----------------------------------------------------------------------------------

  static final String PRACTITIONER_BODY =
      "{\"resourceType\":\"Practitioner\",\"id\":\"1003000999\",\"active\":true}";

  MockHttpServletRequestBuilder write(String method, String path) {
    MockHttpServletRequestBuilder b =
        switch (method) {
          case "POST" -> post(path);
          case "PUT" -> put(path);
          case "PATCH" -> patch(path);
          default -> delete(path);
        };
    return b.contentType("application/fhir+json").content(PRACTITIONER_BODY);
  }

  @ParameterizedTest(name = "[MAM-65 AC-6] {0} on {1} is rejected with 405, never 2xx")
  @CsvSource({
    "POST,/fhir/Practitioner",
    "PUT,/fhir/Practitioner",
    "DELETE,/fhir/Practitioner",
    "PATCH,/fhir/Practitioner",
    "POST,/fhir/Organization",
    "PUT,/fhir/Organization",
    "DELETE,/fhir/Organization",
    "POST,/fhir/PractitionerRole",
    "PUT,/fhir/PractitionerRole",
    "DELETE,/fhir/PractitionerRole"
  })
  void readOnly(String method, String path) throws Exception {
    MvcResult r = mvc.perform(write(method, path)).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(405);
  }

  @Test
  @DisplayName("[MAM-65 AC-6] a write by an ADMIN is rejected too, and the resource is unchanged")
  void adminCannotWrite() throws Exception {
    MvcResult before = search("/fhir/Practitioner", "identifier", NPI_SYSTEM + "|1003000111");
    for (String method : new String[] {"POST", "PUT", "DELETE"}) {
      MvcResult r =
          mvc.perform(write(method, "/fhir/Practitioner").with(PemJwt.admin())).andReturn();
      assertThat(r.getResponse().getStatus()).as(method).isBetween(400, 499);
    }
    MvcResult after = search("/fhir/Practitioner", "identifier", NPI_SYSTEM + "|1003000111");
    assertThat(after.getResponse().getContentAsString())
        .isEqualTo(before.getResponse().getContentAsString());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_entry", Integer.class))
        .isEqualTo(4);
  }

  // ---- AC-8 -----------------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-65 AC-8] identifier <{0}> is a 400 problem+json")
  @ValueSource(
      strings = {
        NPI_SYSTEM + "|ABC",
        NPI_SYSTEM + "|123456789",
        NPI_SYSTEM + "|",
        "http://example.org/other|1003000111",
        "ABC"
      })
  void malformedIdentifier(String identifier) throws Exception {
    for (String path : new String[] {"/fhir/Practitioner", "/fhir/Organization"}) {
      MvcResult r = search(path, "identifier", identifier);
      assertThat(r.getResponse().getStatus()).as(path).isEqualTo(400);
      assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    }
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-8] a FHIR client that asks for application/fhir+json gets the 400 as problem+json")
  void badIdentifierWithFhirAccept() throws Exception {
    MvcResult r =
        mvc.perform(
                get("/fhir/Practitioner")
                    .param("identifier", NPI_SYSTEM + "|ABC")
                    .accept(MediaType.parseMediaType("application/fhir+json")))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName("[MAM-65 AC-1] a FHIR client that asks for application/fhir+json gets the Bundle")
  void fhirAccept() throws Exception {
    MvcResult r =
        mvc.perform(
                get("/fhir/Practitioner")
                    .param("identifier", NPI_SYSTEM + "|1003000111")
                    .accept(MediaType.parseMediaType("application/fhir+json")))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(r.getResponse().getContentType()).startsWith("application/fhir+json");
  }
}
