package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.Scrnout;
import gov.medicaid.pem.testsupport.PemJwt;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-32 AC-2 to AC-5 against the real job (Postgres, Spring Batch, the mocked provider-service and
 * sources): which providers are screened, how many pages are read, and the SCRNOUT file byte by
 * byte (R-PRVSCR01-cbl-00053 to 00060, ADR-005, question C-7 A).
 */
class BatchRunFileTest extends Lane3Test {

  static final String DATE = "2026-03-31";
  static final String P_111 = "1003000111";
  static final String P_555 = "1003000555";
  static final String P_222 = "1003000222";
  static final String P_444 = "1003000444";

  /** The four providers of the story: 111 and 555 unscreened, 222 passed, 444 failed. */
  void fourProviders() {
    ScreeningWorld.lists(
        List.of(
            flag(ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "MD12345", "TX"), "U"),
            flag(ScreeningWorld.individual(P_555, "TESTPROV", "ECHO", "PT99900", "TX"), "U"),
            flag(ScreeningWorld.individual(P_222, "TESTPROV", "BRAVO", "MD22222", "TX"), "P"),
            flag(ScreeningWorld.individual(P_444, "TESTPROV", "DELTA", "MD44444", "TX"), "F")));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
    ScreeningWorld.licence("PT99900", "TX", "EXPIRED", "2024-01");
  }

  static ObjectNode flag(ObjectNode p, String flag) {
    return ScreeningWorld.withFlag(p, flag);
  }

  static String pad(String detail) {
    return detail + " ".repeat(40 - detail.length());
  }

  // ---- AC-3: the file, byte by byte -------------------------------------------------------

  @Test
  @DisplayName("[MAM-32 AC-3] the SCRNOUT file of the story is exactly these eight 67-byte records")
  void exactFile() throws Exception {
    fourProviders();
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    byte[] file = resultsFile(run.path("runId").asText());
    String expected =
        "1003000111"
            + "LICBOARD"
            + "P"
            + "20260331"
            + pad("LICENCE MD12345 TX ACTIVE UNTIL 2028-09")
            + "1003000111"
            + "OIGLEIE "
            + "P"
            + "20260331"
            + pad("NOT EXCLUDED")
            + "1003000111"
            + "SAMGOV  "
            + "P"
            + "20260331"
            + pad("NOT DEBARRED")
            + "1003000111"
            + "SSADMF  "
            + "P"
            + "20260331"
            + pad("NOT DECEASED")
            + "1003000555"
            + "LICBOARD"
            + "F"
            + "20260331"
            + pad("LICENCE PT99900 TX EXPIRED 2024-01")
            + "1003000555"
            + "OIGLEIE "
            + "P"
            + "20260331"
            + pad("NOT EXCLUDED")
            + "1003000555"
            + "SAMGOV  "
            + "P"
            + "20260331"
            + pad("NOT DEBARRED")
            + "1003000555"
            + "SSADMF  "
            + "P"
            + "20260331"
            + pad("NOT DECEASED");
    assertThat(file).hasSize(8 * Scrnout.LENGTH);
    assertThat(new String(file, StandardCharsets.ISO_8859_1)).isEqualTo(expected);
    Scrnout.assertFixedLengthAscii(file);
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-3] business date {0}: every record has the verdict date {1}, whatever today is")
  @CsvSource({
    "2026-03-31,20260331",
    "2026-01-05,20260105",
    "2024-02-29,20240229",
    "2031-12-31,20311231"
  })
  void verdictDateIsTheBusinessDate(String date, String expected) throws Exception {
    fourProviders();
    JsonNode run = runToEnd(date);
    List<Scrnout.Row> rows = Scrnout.rows(resultsFile(run.path("runId").asText()));
    assertThat(rows).hasSize(8).allSatisfy(r -> assertThat(r.date()).isEqualTo(expected));
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] providers are written in ascending NPI order even when provider-service lists them in another order")
  void ascendingNpiOrder() throws Exception {
    ScreeningWorld.lists(
        List.of(
            flag(ScreeningWorld.individual(P_555, "TESTPROV", "ECHO", "MD12345", "TX"), "U"),
            flag(
                ScreeningWorld.individual("1003000333", "TESTPROV", "CHARLIE", "MD12345", "TX"),
                "U"),
            flag(ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "MD12345", "TX"), "U")));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2028-09");
    JsonNode run = runToEnd(DATE);
    List<String> npis =
        Scrnout.rows(resultsFile(run.path("runId").asText())).stream()
            .map(Scrnout.Row::npi)
            .distinct()
            .toList();
    assertThat(npis).containsExactly(P_111, "1003000333", P_555);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] a detail longer than 40 characters is cut to 40 in the file and kept whole (to 60) in the API")
  void longDetailIsCut() throws Exception {
    ScreeningWorld.lists(
        List.of(
            flag(
                ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "ABCDEFGHIJKLMNO", "TX"),
                "U")));
    ScreeningWorld.licence(
        "ABCDEFGHIJKLMNO", "TX", "SUSPENDED-BY-THE-BOARD-PENDING-REVIEW", "2028-09");
    JsonNode run = runToEnd(DATE);
    List<Scrnout.Row> rows = Scrnout.rows(resultsFile(run.path("runId").asText()));
    String full = "LICENCE ABCDEFGHIJKLMNO TX SUSPENDED-BY-THE-BOARD-PENDING-REVIEW";
    assertThat(rows.get(0).source().trim()).isEqualTo("LICBOARD");
    assertThat(rows.get(0).result()).isEqualTo("F");
    assertThat(rows.get(0).detail()).isEqualTo(full.substring(0, 40));
    MvcResult r = get("/api/v1/screenings?npi=" + P_111, PemJwt.analyst());
    assertThat(body(r).path("items").get(0).path("results").get(0).path("detail").asText())
        .isEqualTo(full.substring(0, 60));
  }

  @Test
  @DisplayName("[MAM-32 AC-4] a source that gave no answer is written as C with its detail")
  void notAnsweredIsC() throws Exception {
    fourProviders();
    ScreeningWorld.sourceError("SAMGOV", P_111, 500);
    JsonNode run = runToEnd(DATE);
    List<Scrnout.Row> rows = Scrnout.rows(resultsFile(run.path("runId").asText()));
    Scrnout.Row sam =
        rows.stream()
            .filter(r -> r.npi().equals(P_111) && r.source().trim().equals("SAMGOV"))
            .findFirst()
            .orElseThrow();
    assertThat(sam.result()).isEqualTo("C");
    assertThat(sam.detail()).isEqualTo(pad("COULD NOT BE REACHED"));
    assertThat(run.path("counts").path("failed").asInt()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-4] the failing source of provider 555 is LICBOARD alone: F there, P on the other three rows")
  void failedProviderRows() throws Exception {
    fourProviders();
    JsonNode run = runToEnd(DATE);
    List<Scrnout.Row> rows = Scrnout.rows(resultsFile(run.path("runId").asText()));
    List<String> of555 =
        rows.stream()
            .filter(r -> r.npi().equals(P_555))
            .map(r -> r.source().trim() + r.result())
            .toList();
    assertThat(of555).containsExactly("LICBOARDF", "OIGLEIEP", "SAMGOVP", "SSADMFP");
  }

  @Test
  @DisplayName("[MAM-32 AC-3] the file is the same when it is downloaded again")
  void downloadIsRepeatable() throws Exception {
    fourProviders();
    String runId = runToEnd(DATE).path("runId").asText();
    assertThat(resultsFile(runId)).isEqualTo(resultsFile(runId));
  }

  // ---- AC-2: which providers, which pages -------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-2] only the unscreened providers get an INITIAL screening, each linked to the run")
  void onlyUnscreened() throws Exception {
    fourProviders();
    String runId = runToEnd(DATE).path("runId").asText();
    List<String> npis =
        jdbc.queryForList("SELECT s.npi FROM screening s ORDER BY s.npi", String.class).stream()
            .map(String::trim)
            .toList();
    assertThat(npis).containsExactly(P_111, P_555);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM screening s JOIN screening_run r ON r.id = s.run_id"
                    + " WHERE r.public_id = ?::uuid AND s.reason = 'INITIAL'",
                Integer.class,
                runId))
        .isEqualTo(2);
    assertThat(completedEvents(P_111)).isEqualTo(1);
    assertThat(completedEvents(P_555)).isEqualTo(1);
    assertThat(completedEvents(P_222)).isZero();
    assertThat(completedEvents(P_444)).isZero();
    assertThat(ScreeningWorld.providerReads(P_222)).isZero();
    assertThat(ScreeningWorld.providerReads(P_444)).isZero();
  }

  @Test
  @DisplayName("[MAM-32 AC-2] a provider listed without a screen flag is read but not screened")
  void flagMissing() throws Exception {
    ObjectNode noFlag = ScreeningWorld.individual(P_222, "TESTPROV", "BRAVO", "MD22222", "TX");
    noFlag.remove("screenFlag");
    ScreeningWorld.lists(List.of(noFlag));
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(1);
    assertThat(run.path("counts").path("screened").asInt()).isZero();
    assertThat(screeningCount(P_222)).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-2] no provider at all: the run is COMPLETED with all counts 0 and an empty file")
  void noProviders() throws Exception {
    ScreeningWorld.lists(List.of());
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("read").asInt()).isZero();
    assertThat(run.path("counts").path("screened").asInt()).isZero();
    assertThat(run.path("counts").path("skipped").asInt()).isZero();
    assertThat(run.path("counts").path("failed").asInt()).isZero();
    assertThat(resultsFile(run.path("runId").asText())).isEmpty();
    assertThat(ScreeningWorld.listedPages()).containsExactly("0");
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-2] {0} providers are read in {1} page request(s), the run stops when no page remains")
  @CsvSource({"1,1", "199,1", "200,1", "201,2", "400,2", "401,3"})
  void pagesAreReadOnceUntilTheLast(int total, int pages) throws Exception {
    List<ObjectNode> providers = new ArrayList<>();
    for (int i = 1; i <= total; i++) {
      providers.add(
          flag(
              ScreeningWorld.individual(
                  String.format("1004%06d", i), "TESTPROV", "N" + i, "LIC" + i, "TX"),
              "P"));
    }
    ScreeningWorld.lists(providers);
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(total);
    assertThat(run.path("counts").path("skipped").asInt()).isEqualTo(total);
    List<String> expected = new ArrayList<>();
    for (int p = 0; p < pages; p++) {
      expected.add(String.valueOf(p));
    }
    assertThat(ScreeningWorld.listedPages()).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("[MAM-32 AC-2] the pages are asked with the page size 200")
  void pageSizeIs200() throws Exception {
    fourProviders();
    runToEnd(DATE);
    assertThat(
            MOCK.findAll(getRequestedFor(urlPathEqualTo(ScreeningWorld.PROVIDERS)))
                .get(0)
                .queryParameter("size")
                .firstValue())
        .isEqualTo("200");
  }

  // ---- AC-5: the counts -------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-5] four read, two screened, two skipped, one failed: the BatchRun says so")
  void counts() throws Exception {
    fourProviders();
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(run.path("counts").path("read").asInt()).isEqualTo(4);
    assertThat(run.path("counts").path("screened").asInt()).isEqualTo(2);
    assertThat(run.path("counts").path("skipped").asInt()).isEqualTo(2);
    assertThat(run.path("counts").path("failed").asInt()).isEqualTo(1);
    assertThat(run.has("error")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-5] failed is the number of providers whose screening is FAIL, not the number of failing sources")
  void failedCountsProviders() throws Exception {
    ScreeningWorld.lists(
        List.of(flag(ScreeningWorld.individual(P_111, "TESTPROV", "ALPHA", "XX00001", "TX"), "U")));
    ScreeningWorld.leieListed(P_111);
    ScreeningWorld.sourceError("SAMGOV", P_111, 500);
    JsonNode run = runToEnd(DATE);
    assertThat(run.path("counts").path("screened").asInt()).isEqualTo(1);
    assertThat(run.path("counts").path("failed").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-32 AC-5] the counts of a run are the same when it is read again")
  void countsAreStable() throws Exception {
    fourProviders();
    String runId = runToEnd(DATE).path("runId").asText();
    assertThat(run(runId)).isEqualTo(run(runId));
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-3] the ScreeningCompleted events of the run are INITIAL, one per screened provider")
  void eventsOfTheRun() throws Exception {
    fourProviders();
    runToEnd(DATE);
    assertThat(completedEvent(P_111).path("data").path("reason").asText()).isEqualTo("INITIAL");
    assertThat(completedEvent(P_555).path("data").path("result").asText()).isEqualTo("FAIL");
    assertThat(completedEvent(P_111).path("data").path("result").asText()).isEqualTo("PASS");
  }
}
