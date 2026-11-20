package gov.medicaid.pem.screening.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.screening.application.Fakes.Outbox;
import gov.medicaid.pem.screening.application.Fakes.Store;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-20, MAM-22, MAM-28: what screening one provider does with the reason (INITIAL, REVALIDATION,
 * MANUAL), the given id, a source that gave no answer, and a failure on the way (the screening is
 * stored and announced together or not at all).
 */
class ScreenProviderReasonsTest {

  /** A clock that moves on one second with every reading. */
  static final class TickingClock extends Clock {
    private Instant now = Instant.parse("2026-01-15T10:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      Instant t = now;
      now = now.plusSeconds(1);
      return t;
    }
  }

  final Store store = new Store();
  final Outbox outbox = new Outbox();
  final List<String> order = new ArrayList<>();
  List<SourceResult> answers = Fakes.results("P", "P", "P", "P");
  RuntimeException providerFailure;
  RuntimeException sourcesFailure;
  RuntimeException storeFailure;

  final ScreeningStore failingStore =
      new ScreeningStore() {
        @Override
        public void save(Screening s) {
          order.add("save");
          if (storeFailure != null) {
            throw storeFailure;
          }
          store.save(s);
        }

        @Override
        public ScreeningPage find(String npi, String result, int page, int size) {
          return store.find(npi, result, page, size);
        }

        @Override
        public java.util.Optional<Screening> get(UUID id) {
          return store.get(id);
        }
      };

  final ScreenProvider screen =
      new ScreenProvider(
          npi -> {
            order.add("provider");
            if (providerFailure != null) {
              throw providerFailure;
            }
            return Fakes.facts(npi);
          },
          facts -> {
            order.add("sources");
            if (sourcesFailure != null) {
              throw sourcesFailure;
            }
            return answers;
          },
          failingStore,
          (type, subject, data) -> {
            order.add("outbox");
            outbox.enqueue(type, subject, data);
          },
          new TickingClock());

  @ParameterizedTest(name = "[MAM-28 AC-3] reason {0} is stored and announced as it is")
  @ValueSource(strings = {"INITIAL", "REVALIDATION", "MANUAL"})
  void reasonIsKept(String reason) {
    Screening s = screen.screen("1003000111", reason);
    assertThat(s.reason()).isEqualTo(reason);
    assertThat(store.saved.get(0).reason()).isEqualTo(reason);
    assertThat(((ScreeningCompletedData) outbox.sent.get(0).data()).reason()).isEqualTo(reason);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] a screening with a given id keeps it in the store and in the event")
  void givenIdIsKept() {
    UUID id = UUID.randomUUID();
    Screening s = screen.screen(id, "1003000555", "MANUAL");
    assertThat(s.screeningId()).isEqualTo(id);
    assertThat(store.saved.get(0).screeningId()).isEqualTo(id);
    assertThat(((ScreeningCompletedData) outbox.sent.get(0).data()).screeningId()).isEqualTo(id);
  }

  @Test
  @DisplayName("[MAM-18 AC-1] a screening without a given id gets a new id every time")
  void newIdEveryTime() {
    UUID first = screen.screen("1003000111", "INITIAL").screeningId();
    UUID second = screen.screen("1003000111", "INITIAL").screeningId();
    assertThat(first).isNotEqualTo(second);
  }

  @Test
  @DisplayName(
      "[MAM-25 AC-1] the screening starts when the provider is read and ends after the sources answered")
  void startAndCompletionTimes() {
    Screening s = screen.screen("1003000111", "INITIAL");
    assertThat(s.startedAt()).isEqualTo(Instant.parse("2026-01-15T10:00:00Z"));
    assertThat(s.completedAt()).isEqualTo(Instant.parse("2026-01-15T10:00:01Z"));
    assertThat(s.startedAt()).isBefore(s.completedAt());
  }

  @Test
  @DisplayName(
      "[MAM-18 AC-1] the provider is read first, then the sources are asked, then it is stored, then announced")
  void order() {
    screen.screen("1003000111", "INITIAL");
    assertThat(order).containsExactly("provider", "sources", "save", "outbox");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-5] a provider that cannot be read: no source is asked, nothing stored or announced")
  void providerUnreadable() {
    providerFailure =
        new ProviderFactsSource.Unavailable("1003000777", new RuntimeException("5xx"));
    assertThatThrownBy(() -> screen.screen("1003000777", "INITIAL"))
        .isInstanceOf(ProviderFactsSource.Unavailable.class);
    assertThat(order).containsExactly("provider");
    assertThat(store.saved).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-2] a provider that provider-service does not know: failure passed on, nothing stored")
  void providerUnknown() {
    providerFailure = ApiException.notFound("no such provider");
    assertThatThrownBy(() -> screen.screen("1003000999", "MANUAL"))
        .isInstanceOf(ApiException.class);
    assertThat(store.saved).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-32 AC-6] when storing the screening fails, it is not announced")
  void storeFailsNothingAnnounced() {
    storeFailure = new IllegalStateException("database is down");
    assertThatThrownBy(() -> screen.screen("1003000111", "INITIAL"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(outbox.sent).isEmpty();
    assertThat(order).containsExactly("provider", "sources", "save");
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-1] a source without an answer (C) still gives a stored FAIL screening and an announced FAIL")
  void notAnsweredIsFailAndAnnounced() {
    answers =
        List.of(
            new SourceResult(Source.LICBOARD, "P", Instant.EPOCH, "LIC", null),
            new SourceResult(Source.OIGLEIE, "C", Instant.EPOCH, "COULD NOT BE REACHED", null),
            new SourceResult(Source.SAMGOV, "P", Instant.EPOCH, "SAM", null),
            new SourceResult(Source.SSADMF, "P", Instant.EPOCH, "DMF", null));
    Screening s = screen.screen("1003000111", "INITIAL");
    assertThat(s.result()).isEqualTo("FAIL");
    ScreeningCompletedData data = (ScreeningCompletedData) outbox.sent.get(0).data();
    assertThat(data.result()).isEqualTo("FAIL");
    assertThat(data.sources())
        .contains(new ScreeningCompletedData.SourceSummary("OIGLEIE", "C", "COULD NOT BE REACHED"));
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-1] the event is keyed by the NPI and carries the taxonomy of the provider")
  void eventKeyAndTaxonomy() {
    screen.screen("1003000444", "INITIAL");
    assertThat(outbox.sent.get(0).subject()).isEqualTo("1003000444");
    assertThat(((ScreeningCompletedData) outbox.sent.get(0).data()).taxonomy())
        .isEqualTo("207Q00000X");
    assertThat(((ScreeningCompletedData) outbox.sent.get(0).data()).npi()).isEqualTo("1003000444");
  }
}
