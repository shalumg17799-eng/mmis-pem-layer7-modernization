package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.testsupport.PemJwt;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TestProviderService.Request;
import gov.medicaid.pem.tmsis.TmsisApi;
import gov.medicaid.pem.tmsis.Tmsisout;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-71: the T-MSIS file of a run, end to end against the real application with Postgres and a
 * fake provider-service: which providers are reported and in which order (AC-1), the 120-byte
 * record (AC-2), the YYDDD period of the business date (AC-3, AC-4), every provider however many
 * there are (AC-5), and the way the providers are read (AC-6).
 */
class ReportedProvidersApiTest extends IntegrationTest {

  static final String DATE = "2026-03-31";

  /** ACTIVE 1003000333 and 1003000111, and one provider each in D, P, S and T. */
  static void sixProviders() {
    TestProviderService.holds(
        Prov.active("1003000333"),
        Prov.of("1003000444", "D"),
        Prov.of("1003000555", "P"),
        Prov.active("1003000111"),
        Prov.of("1003000777", "S"),
        Prov.of("1003000888", "T"));
  }

  byte[] fileOfRun(String date) throws Exception {
    JsonNode run = api.runToEnd(date);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    return api.file(run.path("runId").asText());
  }

  // ---- AC-1 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-1] of two ACTIVE and four non-active providers the file has exactly two records, NPI 1003000111 first and 1003000333 second")
  void onlyActiveProvidersInNpiOrder() throws Exception {
    sixProviders();
    byte[] file = fileOfRun(DATE);
    assertThat(file).hasSize(240);
    assertThat(Tmsisout.npis(file)).containsExactly("1003000111", "1003000333");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-1] no record is written for the denied, pending, suspended and terminated providers, and there is no header or trailer")
  void noRecordForNonActive() throws Exception {
    sixProviders();
    byte[] file = fileOfRun(DATE);
    assertThat(Tmsisout.npis(file))
        .doesNotContain("1003000444", "1003000555", "1003000777", "1003000888");
    Tmsisout.records(file).forEach(r -> assertThat(Tmsisout.recordType(r)).isEqualTo("PRV01"));
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-1] even if provider-service answers with providers of every status, only the ACTIVE ones are reported")
  void onlyActiveEvenIfTheFilterIsIgnored() throws Exception {
    sixProviders();
    TestProviderService.ignoreStatusFilter();
    byte[] file = fileOfRun(DATE);
    assertThat(Tmsisout.npis(file)).containsExactly("1003000111", "1003000333");
  }

  @ParameterizedTest(
      name = "[MAM-71 AC-1] a provider in status {0} alone gives a COMPLETED run with no record")
  @ValueSource(strings = {"P", "D", "S", "T"})
  void nonActiveAloneGivesAnEmptyFile(String status) throws Exception {
    TestProviderService.holds(Prov.of("1003000444", status));
    TestProviderService.ignoreStatusFilter();
    assertThat(fileOfRun(DATE)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-1] the file is in ascending NPI order even when provider-service lists the providers in descending order")
  void fileOrderIsTheExtractsNotTheProviderServices() throws Exception {
    sixProviders();
    TestProviderService.answerInDescendingOrder();
    assertThat(Tmsisout.npis(fileOfRun(DATE))).containsExactly("1003000111", "1003000333");
  }

  @Test
  @DisplayName("[MAM-71 AC-1] provider-service is asked for the ACTIVE providers only (status=A)")
  void onlyStatusAIsAskedFor() throws Exception {
    sixProviders();
    fileOfRun(DATE);
    assertThat(TestProviderService.searches()).isNotEmpty();
    assertThat(TestProviderService.searches())
        .allSatisfy(r -> assertThat(r.query()).containsEntry("status", "A"));
  }

  // ---- AC-2 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-2] the record of NPI 1003000111 is 120 bytes: PRV01, M00000111, 1003000111, A, 26090 and 90 spaces")
  void wholeRecord() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    byte[] file = fileOfRun(DATE);
    assertThat(file).hasSize(120);
    assertThat(new String(file, StandardCharsets.ISO_8859_1))
        .isEqualTo(Tmsisout.expected("M00000111", "1003000111", "26090"));
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] bytes 1-5 are PRV01, 6-14 the Medicaid ID, 15-24 the NPI, 25 an A and 31-120 spaces in every record")
  void everyRecordHasTheLegacyFields() throws Exception {
    sixProviders();
    List<String> records = Tmsisout.records(fileOfRun(DATE));
    assertThat(records).hasSize(2);
    assertThat(records)
        .allSatisfy(
            r -> {
              assertThat(r).hasSize(120);
              assertThat(Tmsisout.recordType(r)).isEqualTo("PRV01");
              assertThat(Tmsisout.status(r)).isEqualTo("A");
              assertThat(Tmsisout.filler(r)).isEqualTo(" ".repeat(90));
            });
    assertThat(Tmsisout.medicaidId(records.get(0))).isEqualTo("M00000111");
    assertThat(Tmsisout.medicaidId(records.get(1))).isEqualTo("M00000333");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] the Medicaid ID is copied unchanged: 9 characters as they are, a shorter one padded with spaces")
  void medicaidIdCopiedUnchanged() throws Exception {
    TestProviderService.holds(
        Prov.active("1003000111").medicaid("AB1234567"),
        Prov.active("1003000333").medicaid("M123"));
    List<String> records = Tmsisout.records(fileOfRun(DATE));
    assertThat(Tmsisout.medicaidId(records.get(0))).isEqualTo("AB1234567");
    assertThat(Tmsisout.medicaidId(records.get(1))).isEqualTo("M123     ");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-2] the file has no line ends and is text/plain (contract: 120-byte records)")
  void noLineEnds() throws Exception {
    sixProviders();
    JsonNode run = api.runToEnd(DATE);
    MvcResult result =
        api.getPath(PemJwt.admin(), TmsisApi.BASE + "/" + run.path("runId").asText() + "/file");
    assertThat(result.getResponse().getContentType()).startsWith("text/plain");
    assertThat(result.getResponse().getContentAsString()).doesNotContain("\n").doesNotContain("\r");
  }

  // ---- AC-3 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-3] a run for 2026-03-31 started on another day (the clock shows 2026-05-10): every record holds 26090")
  void periodIsTheBusinessDateNotTheSystemDate() throws Exception {
    sixProviders();
    TestClock.set("2026-05-10T09:30:00Z");
    List<String> records = Tmsisout.records(fileOfRun(DATE));
    assertThat(records).hasSize(2);
    assertThat(records).allSatisfy(r -> assertThat(Tmsisout.period(r)).isEqualTo("26090"));
  }

  @Test
  @DisplayName("[MAM-71 AC-3] the period is the same on every record of the run")
  void samePeriodForTheWholeRun() throws Exception {
    List<Prov> many = new ArrayList<>();
    for (int i = 1; i <= 25; i++) {
      many.add(Prov.active(String.format("10030002%02d", i)));
    }
    TestProviderService.holds(many);
    List<String> records = Tmsisout.records(fileOfRun(DATE));
    assertThat(records).hasSize(25);
    assertThat(records.stream().map(Tmsisout::period).distinct()).containsExactly("26090");
  }

  // ---- AC-4 -------------------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-71 AC-4] a run for {0} writes the period {1}")
  @CsvSource({
    "2026-01-05, 26005",
    "2026-03-30, 26089",
    "2026-03-31, 26090",
    "2028-12-31, 28366",
    "2026-12-31, 26365",
    "2028-02-29, 28060"
  })
  void periodOfTheRunsDate(String date, String period) throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    List<String> records = Tmsisout.records(fileOfRun(date));
    assertThat(records).hasSize(1);
    assertThat(Tmsisout.period(records.get(0))).isEqualTo(period);
  }

  // ---- AC-5 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 450 ACTIVE providers (more than two pages of 200) give 450 records, each NPI once, in ascending NPI order")
  void fourHundredFiftyProviders() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1001; i <= 1450; i++) {
      all.add(Prov.active("1003" + String.format("%06d", i)));
    }
    TestProviderService.holds(all);
    TestProviderService.answerInDescendingOrder();
    JsonNode run = api.runToEnd(DATE);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("rows").asInt()).isEqualTo(450);
    List<String> npis = Tmsisout.npis(api.file(run.path("runId").asText()));
    assertThat(npis).hasSize(450).doesNotHaveDuplicates().isSorted();
    assertThat(npis.get(0)).isEqualTo("1003001001");
    assertThat(npis.get(449)).isEqualTo("1003001450");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 450 providers are read as three pages of 200: page 0, 1 and 2, each with size 200")
  void threePagesOf200() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      all.add(Prov.active(String.format("1003%06d", 1000 + i)));
    }
    TestProviderService.holds(all);
    fileOfRun(DATE);
    List<Request> searches = TestProviderService.searches();
    assertThat(searches).extracting(r -> r.query().get("page")).containsExactly("0", "1", "2");
    assertThat(searches).allSatisfy(r -> assertThat(r.query()).containsEntry("size", "200"));
  }

  @ParameterizedTest(
      name = "[MAM-71 AC-5] {0} ACTIVE providers give {0} records, none missed and none repeated")
  @ValueSource(ints = {1, 199, 200, 201, 400, 401})
  void pageBoundaries(int count) throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= count; i++) {
      all.add(Prov.active(String.format("1003%06d", 2000 + i)));
    }
    TestProviderService.holds(all);
    List<String> npis = Tmsisout.npis(fileOfRun(DATE));
    assertThat(npis).hasSize(count).doesNotHaveDuplicates().isSorted();
  }

  // ---- AC-6 -------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-6] provider-service receives GET /api/v1/providers with status=A, page after page, with the SYSTEM service token")
  void providersAreReadThroughTheApiWithTheSystemToken() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      all.add(Prov.active(String.format("1003%06d", 3000 + i)));
    }
    TestProviderService.holds(all);
    fileOfRun(DATE);
    List<Request> searches = TestProviderService.searches();
    assertThat(searches).hasSize(3);
    assertThat(searches)
        .allSatisfy(
            r -> {
              assertThat(r.method()).isEqualTo("GET");
              assertThat(r.path()).isEqualTo("/api/v1/providers");
              assertThat(r.query()).containsEntry("status", "A");
              assertThat(r.authorization()).isEqualTo("Bearer " + TestProviderService.SYSTEM_TOKEN);
            });
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-6] every call to provider-service carries an X-Correlation-Id (java-microservices 11)")
  void callsCarryACorrelationId() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    fileOfRun(DATE);
    assertThat(TestProviderService.searches())
        .allSatisfy(r -> assertThat(r.correlationId()).isNotBlank());
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-6] tmsis-service holds only extract data of its own: no provider, owner, screening or sanction table")
  void onlyExtractTablesInItsDatabase() {
    List<String> tables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
            String.class);
    assertThat(tables)
        .contains("extract_run", "extract_row", "extract_archive", "outbox_event")
        .doesNotContain(
            "provider",
            "provider_owner",
            "screening",
            "sanction",
            "application",
            "directory_entry",
            "revalidation_queue");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-6] the extract rows of a run are tmsis-service's own copy: the file stays the same when provider-service changes later")
  void fileIsFrozenAtTheRun() throws Exception {
    sixProviders();
    JsonNode run = api.runToEnd(DATE);
    byte[] first = api.file(run.path("runId").asText());
    TestProviderService.holds(Prov.active("1003000999"));
    api.runToEnd(DATE);
    assertThat(api.file(run.path("runId").asText())).isEqualTo(first);
  }
}
