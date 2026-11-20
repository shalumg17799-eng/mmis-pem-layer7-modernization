package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.PemJwt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract test for the change of provider-service 1.2.0 (MAM-37 AC-9, Q-132): searchProviders in
 * modern/contracts/openapi/provider-service.yaml declares the optional query parameter afterNpi and
 * keeps every 1.1.0 parameter (additive); and the service treats every value exactly as the
 * contract's own pattern says: a value the pattern admits is accepted, any other is a 400.
 */
class ProviderKeysetContractTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    KeysetFixtures.eight(jdbc);
  }

  // ---- the contract file
  // ----------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private static Map<String, Object> contract() throws IOException {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c :
          List.of(
              dir.resolve("contracts/openapi/provider-service.yaml"),
              dir.resolve("modern/contracts/openapi/provider-service.yaml"))) {
        if (Files.isRegularFile(c)) {
          return new Yaml().load(Files.readString(c));
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("contracts/openapi/provider-service.yaml not found");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> searchParameters() throws IOException {
    Map<String, Object> paths = (Map<String, Object>) contract().get("paths");
    Map<String, Object> providers = (Map<String, Object>) paths.get("/api/v1/providers");
    Map<String, Object> search = (Map<String, Object>) providers.get("get");
    assertThat(search.get("operationId")).isEqualTo("searchProviders");
    return (List<Map<String, Object>>) search.get("parameters");
  }

  private static Map<String, Object> parameter(String name) throws IOException {
    return searchParameters().stream()
        .filter(p -> name.equals(p.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("searchProviders has no parameter " + name));
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] the contract declares afterNpi as an optional query parameter: a string of exactly 10 digits")
  void contractDeclaresAfterNpi() throws Exception {
    Map<String, Object> afterNpi = parameter("afterNpi");

    assertThat(afterNpi.get("in")).isEqualTo("query");
    assertThat(afterNpi.get("required")).isIn(null, Boolean.FALSE);
    @SuppressWarnings("unchecked")
    Map<String, Object> schema = (Map<String, Object>) afterNpi.get("schema");
    assertThat(schema.get("type")).isEqualTo("string");
    assertThat(schema.get("pattern")).isEqualTo("^[0-9]{10}$");
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] the change is additive: every 1.1.0 parameter of searchProviders is still there and still optional")
  void contractKeepsTheOldParameters() throws Exception {
    List<String> names = new ArrayList<>();
    for (Map<String, Object> p : searchParameters()) {
      names.add((String) p.get("name"));
      assertThat(p.get("required")).as("required: " + p.get("name")).isIn(null, Boolean.FALSE);
      assertThat(p.get("in")).as("in: " + p.get("name")).isEqualTo("query");
    }

    assertThat(names)
        .contains("npi", "medicaidId", "name", "status", "screenFlag", "page", "size", "afterNpi");
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] the change is a minor version bump: provider-service 1.2.0 or a later 1.x")
  void contractIsAMinorBump() throws Exception {
    @SuppressWarnings("unchecked")
    String version = (String) ((Map<String, Object>) contract().get("info")).get("version");

    assertThat(version).matches("1\\.([2-9]|[1-9][0-9]+)\\.[0-9]+");
  }

  // ---- the service follows the contract's own pattern
  // -------------------------------------------------------

  static Stream<Arguments> candidates() {
    return Stream.of(
            "1003000444",
            "0000000000",
            "9999999999",
            "1003000999",
            "1003000",
            "100300044",
            "10030004444",
            "",
            " ",
            "ABC",
            "12345",
            "1003O00444",
            " 1003000444",
            "1003000444 ",
            "1003000444\n",
            "+100300044",
            "-100300044",
            "1003000444.0",
            "١٠٠٣٠٠٠٤٤٤",
            "1003000444,1003000555")
        .map(v -> Arguments.of(v.replace("\n", "\\n"), v));
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi=[{0}] is treated as the contract pattern says")
  @MethodSource("candidates")
  @DisplayName(
      "[MAM-37 AC-9] a value that the contract pattern of afterNpi admits is answered with 200, any other value with a 400 problem+json")
  void serviceFollowsThePattern(String shown, String value) throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> schema = (Map<String, Object>) parameter("afterNpi").get("schema");
    boolean admitted = Pattern.compile((String) schema.get("pattern")).matcher(value).matches();

    MvcResult r =
        mvc.perform(get("/api/v1/providers").param("afterNpi", value).with(PemJwt.analyst()))
            .andReturn();

    assertThat(r.getResponse().getStatus())
        .as(r.getResponse().getContentAsString())
        .isEqualTo(admitted ? 200 : 400);
    if (!admitted) {
      assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    }
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] a key that the pattern admits but that holds no provider is not an error: 200 with an empty page")
  void admittedKeyWithNoProviderIsNotAnError() throws Exception {
    ProviderRows.wipe(jdbc);

    MvcResult r =
        mvc.perform(get("/api/v1/providers").param("afterNpi", "1003000444").with(PemJwt.analyst()))
            .andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode page = json.readTree(r.getResponse().getContentAsString());
    assertThat(page.path("items").isArray()).isTrue();
    assertThat(page.path("items").size()).isZero();
    assertThat(page.path("total").asInt()).isZero();
  }
}
