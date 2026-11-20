package gov.medicaid.pem.screening.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The small classes of the application package that the stories of Lane 3 lean on: the provider
 * facts (MAM-20 AC-4), the provider read failure (MAM-20 AC-5), the counts of a run (MAM-32 AC-5)
 * and the immutable records of a screening and of its event.
 */
class ApplicationRecordsTest {

  static final Instant AT = Instant.parse("2026-01-15T10:00:00Z");

  // ---- ProviderFacts ----------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-20 AC-4] provider type {0} is an individual: {1}")
  @CsvSource({"IN,true", "GR,false", "FA,false", "in,false", "INDIVIDUAL,false"})
  void individual(String type, boolean expected) {
    assertThat(
            new ProviderFacts("1003000111", type, "207Q00000X", null, null, null, null)
                .individual())
        .isEqualTo(expected);
  }

  @ParameterizedTest(name = "[MAM-20 AC-4] a missing provider type <{0}> is not an individual")
  @NullSource
  void missingTypeIsNotIndividual(String type) {
    assertThat(new ProviderFacts("1003000111", type, null, null, null, null, null).individual())
        .isFalse();
  }

  // ---- ProviderFactsSource.Unavailable ----------------------------------------------------

  @Test
  @DisplayName("[MAM-20 AC-5] Unavailable names the NPI and the cause in its message")
  void unavailableMessage() {
    var e =
        new ProviderFactsSource.Unavailable("1003000777", new IllegalStateException("no answer"));
    assertThat(e.getMessage()).contains("1003000777").contains("no answer");
    assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("[MAM-20 AC-5] Unavailable is found directly and anywhere in a cause chain")
  void unavailableFound() {
    var unavailable = new ProviderFactsSource.Unavailable("1003000777", new RuntimeException("x"));
    assertThat(ProviderFactsSource.Unavailable.in(unavailable)).isTrue();
    assertThat(
            ProviderFactsSource.Unavailable.in(
                new RuntimeException(
                    "listener failed", new IllegalStateException("wrapped", unavailable))))
        .isTrue();
  }

  @Test
  @DisplayName("[MAM-20 AC-5] a failure that is not a provider read failure is not Unavailable")
  void notUnavailable() {
    assertThat(ProviderFactsSource.Unavailable.in(new RuntimeException("other"))).isFalse();
    assertThat(
            ProviderFactsSource.Unavailable.in(
                new RuntimeException("outer", new IllegalArgumentException("inner"))))
        .isFalse();
    assertThat(ProviderFactsSource.Unavailable.in(null)).isFalse();
  }

  // ---- ScreeningRuns.Run ------------------------------------------------------------------

  private static ScreeningRuns.Run run(int read, int screened) {
    return new ScreeningRuns.Run(
        UUID.randomUUID(), LocalDate.of(2026, 3, 31), "COMPLETED", read, screened, 0, null, AT, AT);
  }

  @ParameterizedTest(name = "[MAM-32 AC-5] read {0} and screened {1} give skipped {2}")
  @CsvSource({"4,2,2", "4,0,4", "4,4,0", "0,0,0", "401,3,398"})
  void skipped(int read, int screened, int skipped) {
    assertThat(run(read, screened).skipped()).isEqualTo(skipped);
  }

  @Test
  @DisplayName("[MAM-32 AC-5] skipped is never negative, also when more were screened than read")
  void skippedNeverNegative() {
    assertThat(run(2, 3).skipped()).isZero();
  }

  @Test
  @DisplayName("[MAM-32 AC-1] the status of a newly opened run is RUNNING")
  void runningConstant() {
    assertThat(ScreeningRuns.Run.RUNNING).isEqualTo("RUNNING");
  }

  // ---- paging and listing -----------------------------------------------------------------

  @Test
  @DisplayName("[MAM-32 AC-2] a provider list is read in pages of 200")
  void pageSize() {
    assertThat(ProviderListing.PAGE_SIZE).isEqualTo(200);
  }

  // ---- immutability -----------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-18 AC-1] a screening keeps its own copy of the results given to it")
  void screeningCopiesResults() {
    List<SourceResult> results = new ArrayList<>(Fakes.results("P", "P", "P", "P"));
    Screening s =
        new Screening(
            UUID.randomUUID(), "1003000111", "INITIAL", "COMPLETED", "PASS", results, AT, AT);
    results.clear();
    assertThat(s.results()).hasSize(4);
    assertThatThrownBy(() -> s.results().add(Fakes.results("P", "P", "P", "P").get(0)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("[MAM-18 AC-1] a page of screenings keeps its own copy of the items")
  void pageCopiesItems() {
    List<Screening> items = new ArrayList<>();
    items.add(
        new Screening(
            UUID.randomUUID(), "1003000111", "INITIAL", "COMPLETED", "PASS", List.of(), AT, AT));
    ScreeningPage page = new ScreeningPage(items, 0, 20, 1);
    items.clear();
    assertThat(page.items()).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-25 AC-5] the event data keeps its own copy of the four source summaries")
  void eventDataCopiesSources() {
    List<ScreeningCompletedData.SourceSummary> sources = new ArrayList<>();
    sources.add(new ScreeningCompletedData.SourceSummary("LICBOARD", "P", "OK"));
    ScreeningCompletedData data =
        new ScreeningCompletedData(
            UUID.randomUUID(), "1003000111", "INITIAL", "PASS", "207Q00000X", sources);
    sources.clear();
    assertThat(data.sources()).hasSize(1);
  }

  @ParameterizedTest(name = "[MAM-20 AC-1] the sources are named {0}")
  @ValueSource(strings = {"LICBOARD", "OIGLEIE", "SAMGOV", "SSADMF"})
  void sourceNames(String name) {
    assertThat(Source.valueOf(name).name()).isEqualTo(name);
  }

  @Test
  @DisplayName("[MAM-18 AC-1] there are exactly four sources: no NPPES (question C-4 A)")
  void fourSources() {
    assertThat(Source.values())
        .containsExactly(Source.LICBOARD, Source.OIGLEIE, Source.SAMGOV, Source.SSADMF);
  }
}
