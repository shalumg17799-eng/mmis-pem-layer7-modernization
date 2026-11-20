package gov.medicaid.pem.provider.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.testsupport.PemJwt;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-50: the provider seed (modern/testdata/seed/provider_db/*.sql) loaded into a fresh
 * provider_db and read back through GET /api/v1/providers/{npi}, exactly as make seed does on the
 * local stack.
 */
class ProviderSeedTest extends IntegrationTest {

  static final List<String> JOURNEY_NPIS =
      List.of(
          "1003000111",
          "1003000222",
          "1003000333",
          "1003000444",
          "1003000555",
          "1003000666",
          "1003000777",
          "1003000888",
          "1990000111");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired DataSource dataSource;
  @Autowired ObjectMapper json;

  static Path seedDir() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      Path c = dir.resolve("modern/testdata/seed/provider_db");
      if (Files.isDirectory(c)) {
        return c;
      }
      Path d = dir.resolve("testdata/seed/provider_db");
      if (Files.isDirectory(d)) {
        return d;
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("modern/testdata/seed/provider_db not found");
  }

  void runSeed() throws Exception {
    var populator = new ResourceDatabasePopulator();
    try (var files = Files.list(seedDir())) {
      files
          .filter(p -> p.toString().endsWith(".sql"))
          .sorted()
          .forEach(p -> populator.addScript(new FileSystemResource(p)));
    }
    populator.execute(dataSource);
  }

  List<String> seededNpis() {
    return jdbc.queryForList("SELECT npi FROM provider ORDER BY npi", String.class).stream()
        .map(String::trim)
        .toList();
  }

  JsonNode read(String npi) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.analyst())).andReturn();
    assertThat(r.getResponse().getStatus()).as("GET provider %s", npi).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  String name(JsonNode p) {
    return p.has("individual")
        ? p.path("individual").path("firstName").asText()
            + " "
            + p.path("individual").path("lastName").asText()
        : p.path("organization").path("legalName").asText();
  }

  @BeforeEach
  void seed() throws Exception {
    runSeed();
  }

  @Test
  @DisplayName(
      "[MAM-50 AC-2] the seeded providers cover every status, screen flag, risk tier and the six taxonomy prefixes")
  void coversEveryState() throws Exception {
    Set<String> status = new HashSet<>(),
        flags = new HashSet<>(),
        tiers = new HashSet<>(),
        prefixes = new HashSet<>();
    for (String npi : seededNpis()) {
      JsonNode p = read(npi);
      status.add(p.path("enrollStatus").asText());
      flags.add(p.path("screenFlag").asText());
      if (p.hasNonNull("riskTier")) {
        tiers.add(p.path("riskTier").asText());
      }
      prefixes.add(p.path("taxonomy").asText().substring(0, 3));
    }
    assertThat(status).contains("P", "A", "T", "D", "S");
    assertThat(flags).contains("U", "P", "F");
    assertThat(tiers).contains("L", "M", "H");
    assertThat(prefixes).contains("332", "251", "341", "291", "225");
    assertThat(prefixes).as("one other prefix").hasSizeGreaterThan(5);
  }

  @Test
  @DisplayName(
      "[MAM-50 AC-3] the seed holds TESTPROV ALPHA, ACTIVE providers due on or before 2026-03-31 and later, and an ACTIVE and a TERMINATED hospice")
  void supportsTheJourneys() throws Exception {
    List<JsonNode> all = new ArrayList<>();
    for (String npi : seededNpis()) {
      all.add(read(npi));
    }
    assertThat(all)
        .anySatisfy(
            p -> {
              assertThat(p.path("enrollStatus").asText()).isEqualTo("A");
              assertThat(p.path("providerType").asText()).isEqualTo("IN");
              assertThat(p.path("individual").path("firstName").asText()).isEqualTo("TESTPROV");
              assertThat(p.path("individual").path("lastName").asText()).isEqualTo("ALPHA");
            });
    LocalDate cutoff = LocalDate.of(2026, 3, 31);
    List<LocalDate> activeDue =
        all.stream()
            .filter(
                p ->
                    p.path("enrollStatus").asText().equals("A")
                        && p.hasNonNull("revalidationDueDate"))
            .map(p -> LocalDate.parse(p.path("revalidationDueDate").asText()))
            .toList();
    assertThat(activeDue).anyMatch(d -> !d.isAfter(cutoff));
    assertThat(activeDue).anyMatch(d -> d.isAfter(cutoff));
    assertThat(all)
        .anyMatch(p -> name(p).contains("HOSPICE") && p.path("enrollStatus").asText().equals("A"));
    assertThat(all)
        .anyMatch(p -> name(p).contains("HOSPICE") && p.path("enrollStatus").asText().equals("T"));
  }

  @Test
  @DisplayName(
      "[MAM-50 AC-3] every seeded licence is in the documented list the licence-board stub must answer ACTIVE")
  void licencesAreDocumentedForTheStub() throws Exception {
    Path readme = seedDir().getParent().getParent().resolve("README.md");
    String doc = Files.readString(readme);
    for (String npi : seededNpis()) {
      JsonNode p = read(npi);
      if (p.has("individual")) {
        assertThat(doc)
            .as(
                "licence %s of %s listed in testdata/README.md",
                p.path("individual").path("licenseNo").asText(), npi)
            .contains(p.path("individual").path("licenseNo").asText());
      }
    }
  }

  @Test
  @DisplayName(
      "[MAM-50 AC-4] the journey applicants are never pre-registered and no seeded NPI is in the reserved ranges")
  void journeyApplicantsAreFree() throws Exception {
    for (String npi : JOURNEY_NPIS) {
      assertThat(
              mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.analyst()))
                  .andReturn()
                  .getResponse()
                  .getStatus())
          .as("journey NPI %s", npi)
          .isEqualTo(404);
    }
    assertThat(seededNpis())
        .isNotEmpty()
        .noneMatch(n -> n.startsWith("1003000") || n.startsWith("1990000"));
  }

  @Test
  @DisplayName(
      "[MAM-50 AC-5] running the seed again removes an extra provider and leaves the seeded ones unchanged")
  void reseedRestoresTheSeedState() throws Exception {
    Map<String, String> before = new LinkedHashMap<>();
    for (String npi : seededNpis()) {
      before.put(npi, read(npi).toString());
    }
    jdbc.update(
        "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, enroll_status, screen_flag,"
            + " effective_date) VALUES (?, '1005999998', 'X99999999', 'IN', '207Q00000X', 'P', 'U', DATE '2026-01-15')",
        UUID.randomUUID());
    runSeed();
    runSeed();
    assertThat(
            mvc.perform(get("/api/v1/providers/1005999998").with(PemJwt.analyst()))
                .andReturn()
                .getResponse()
                .getStatus())
        .isEqualTo(404);
    Map<String, String> after = new LinkedHashMap<>();
    for (String npi : seededNpis()) {
      after.put(npi, read(npi).toString());
    }
    assertThat(after).isEqualTo(before);
  }

  @Test
  @DisplayName(
      "[MAM-50 AC-7] the seed files hold only obviously fake names and NPIs with a valid check digit outside the reserved ranges")
  void seedIsSynthetic() throws Exception {
    StringBuilder all = new StringBuilder();
    try (var files = Files.list(seedDir())) {
      for (Path f : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
        all.append(Files.readString(f)).append('\n');
      }
    }
    String sql = all.toString().replaceAll("(?m)--.*$", "");
    Matcher npi = Pattern.compile("'(\\d{10})'").matcher(sql);
    int seen = 0;
    while (npi.find()) {
      String n = npi.group(1);
      assertThat(validNpiCheckDigit(n)).as("NPI %s has a valid check digit", n).isTrue();
      assertThat(n).doesNotStartWith("1003000").doesNotStartWith("1990000");
      seen++;
    }
    assertThat(seen).as("NPIs in the seed files").isPositive();
    List<String> owners =
        jdbc.queryForList(
            "SELECT owner_npi FROM provider WHERE owner_npi IS NOT NULL", String.class);
    assertThat(owners)
        .allSatisfy(o -> assertThat(validNpiCheckDigit(o.trim())).as("owner NPI %s", o).isTrue());
    for (Map<String, Object> row :
        jdbc.queryForList("SELECT first_name, last_name, legal_name, dba_name FROM provider")) {
      check(row.get("first_name"), "TESTPROV");
      check(
          row.get("last_name"),
          "(ALPHA|BRAVO|CHARLIE|DELTA|ECHO|FOXTROT|GOLF|HOTEL|INDIA|JULIETT|KILO|LIMA)");
      check(row.get("legal_name"), "(SAMPLE|TESTPROV)( [A-Z]+)+ \\d{2}");
      check(row.get("dba_name"), "(SAMPLE|TESTPROV)( [A-Z]+)+ \\d{2}");
    }
  }

  private static void check(Object value, String fakePattern) {
    if (value != null) {
      assertThat(value.toString().trim()).as("obviously fake name").matches(fakePattern);
    }
  }

  /** NPI check digit: Luhn over the prefix 80840 and the first nine digits. */
  static boolean validNpiCheckDigit(String npi) {
    String base = "80840" + npi.substring(0, 9);
    int sum = 0;
    for (int i = 0; i < base.length(); i++) {
      int d = base.charAt(base.length() - 1 - i) - '0';
      if (i % 2 == 0) {
        d *= 2;
        if (d > 9) {
          d -= 9;
        }
      }
      sum += d;
    }
    return (10 - sum % 10) % 10 == npi.charAt(9) - '0';
  }
}
