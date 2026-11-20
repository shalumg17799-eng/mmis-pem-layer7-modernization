package gov.medicaid.pem.screening.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-18 / MAM-25: one screening per request, four results, the verdict, stored once and announced
 * once.
 */
class ScreenProviderTest {

  static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");
  static final ProviderFacts RIVERA =
      new ProviderFacts("1003000111", "IN", "207Q00000X", "MD12345", "TX", "MARIA RIVERA", null);

  record Sent(String type, String subject, Object data) {}

  final List<Screening> saved = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final List<ProviderFacts> asked = new ArrayList<>();
  List<SourceResult> answers = results("P", "P", "P", "P");

  static List<SourceResult> results(String lic, String leie, String sam, String dmf) {
    return List.of(
        new SourceResult(Source.LICBOARD, lic, NOW, "LIC", lic.equals("F") ? "E202" : null),
        new SourceResult(Source.OIGLEIE, leie, NOW, "LEIE", null),
        new SourceResult(Source.SAMGOV, sam, NOW, "SAM", null),
        new SourceResult(Source.SSADMF, dmf, NOW, "DMF", null));
  }

  final ScreeningStore store =
      new ScreeningStore() {
        @Override
        public void save(Screening s) {
          saved.add(s);
        }

        @Override
        public ScreeningPage find(String npi, String result, int page, int size) {
          return new ScreeningPage(List.copyOf(saved), page, size, saved.size());
        }

        @Override
        public Optional<Screening> get(UUID id) {
          return saved.stream().filter(s -> s.screeningId().equals(id)).findFirst();
        }
      };

  final ScreenProvider screen =
      new ScreenProvider(
          npi -> RIVERA,
          facts -> {
            asked.add(facts);
            return answers;
          },
          store,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName(
      "[MAM-18 AC-1] the sources are asked with the facts read from provider-service, once")
  void asksSources() {
    screen.screen("1003000111", "INITIAL");
    assertThat(asked).containsExactly(RIVERA);
  }

  @Test
  @DisplayName(
      "[MAM-25 AC-1] four P results give a COMPLETED PASS screening with start and completion times")
  void pass() {
    Screening s = screen.screen("1003000111", "INITIAL");
    assertThat(s.status()).isEqualTo("COMPLETED");
    assertThat(s.result()).isEqualTo("PASS");
    assertThat(s.reason()).isEqualTo("INITIAL");
    assertThat(s.npi()).isEqualTo("1003000111");
    assertThat(s.results()).hasSize(4);
    assertThat(s.startedAt()).isEqualTo(NOW);
    assertThat(s.completedAt()).isEqualTo(NOW);
    assertThat(saved).containsExactly(s);
  }

  @Test
  @DisplayName("[MAM-25 AC-2] one F result gives a COMPLETED FAIL screening")
  void fail() {
    answers = results("F", "P", "P", "P");
    assertThat(screen.screen("1003000111", "INITIAL").result()).isEqualTo("FAIL");
  }

  @Test
  @DisplayName(
      "[MAM-25 AC-5] every completed screening is announced once as ScreeningCompleted with the taxonomy and the sources")
  void announces() {
    answers = results("F", "P", "P", "P");
    Screening s = screen.screen("1003000111", "INITIAL");
    assertThat(sent)
        .containsExactly(
            new Sent(
                "ScreeningCompleted",
                "1003000111",
                new ScreeningCompletedData(
                    s.screeningId(),
                    "1003000111",
                    "INITIAL",
                    "FAIL",
                    "207Q00000X",
                    List.of(
                        new ScreeningCompletedData.SourceSummary("LICBOARD", "F", "LIC"),
                        new ScreeningCompletedData.SourceSummary("OIGLEIE", "P", "LEIE"),
                        new ScreeningCompletedData.SourceSummary("SAMGOV", "P", "SAM"),
                        new ScreeningCompletedData.SourceSummary("SSADMF", "P", "DMF")))));
  }

  @Test
  @DisplayName("[MAM-25 AC-7] reading an unknown screening id is a 404")
  void unknown() {
    ReadScreenings read = new ReadScreenings(store);
    Screening s = screen.screen("1003000111", "INITIAL");
    assertThat(read.get(s.screeningId())).isEqualTo(s);
    assertThatThrownBy(() -> read.get(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
  }

  @Test
  @DisplayName("[MAM-25 AC-6] the list passes the NPI, result and page through to the store")
  void list() {
    screen.screen("1003000111", "INITIAL");
    ScreeningPage p = new ReadScreenings(store).list(null, "FAIL", 0, 20);
    assertThat(p.items()).hasSize(1);
    assertThat(p.page()).isZero();
    assertThat(p.size()).isEqualTo(20);
  }
}
