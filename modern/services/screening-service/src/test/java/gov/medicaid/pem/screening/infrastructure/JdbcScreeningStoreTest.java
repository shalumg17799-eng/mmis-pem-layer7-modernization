package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.application.Screening;
import gov.medicaid.pem.screening.application.ScreeningPage;
import gov.medicaid.pem.screening.application.ScreeningStore;
import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * MAM-28 AC-1 on the screening table: screenings are only added (D-018), read back whole, listed
 * newest first by NPI and result, and a page says how many there are in all.
 */
class JdbcScreeningStoreTest extends Lane3Test {

  @Autowired ScreeningStore store;

  static Screening screening(String npi, String reason, String result, Instant started) {
    return new Screening(
        UUID.randomUUID(),
        npi,
        reason,
        "COMPLETED",
        result,
        List.of(
            new SourceResult(
                Source.LICBOARD, "F", started, "LICENCE PT99900 TX EXPIRED 2024-01", "E202"),
            new SourceResult(Source.OIGLEIE, "P", started, "NOT EXCLUDED", null),
            new SourceResult(Source.SAMGOV, "C", started, "COULD NOT BE REACHED", null),
            new SourceResult(Source.SSADMF, "P", started, "NOT DECEASED", null)),
        started,
        started.plusSeconds(2));
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] a screening is read back as it was saved: ids, reason, status, result, times, four results with their codes")
  void roundTrip() {
    Instant at = Instant.parse("2026-03-31T08:00:00.123456Z");
    Screening saved = screening("1003000555", "MANUAL", "FAIL", at);
    store.save(saved);
    assertThat(store.get(saved.screeningId())).hasValue(saved);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] a screening that is not there is not found")
  void unknown() {
    assertThat(store.get(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-28 AC-1] the NPI filter lists the screenings of that NPI only, newest first")
  void byNpiNewestFirst() {
    Instant t0 = Instant.parse("2026-03-31T08:00:00Z");
    Screening old = screening("1003000555", "INITIAL", "FAIL", t0);
    Screening newer = screening("1003000555", "MANUAL", "PASS", t0.plusSeconds(60));
    Screening other = screening("1003000111", "INITIAL", "PASS", t0.plusSeconds(120));
    store.save(old);
    store.save(newer);
    store.save(other);
    ScreeningPage page = store.find("1003000555", null, 0, 20);
    assertThat(page.items().stream().map(Screening::screeningId))
        .containsExactly(newer.screeningId(), old.screeningId());
    assertThat(page.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] the result filter and the NPI filter work together")
  void resultAndNpi() {
    Instant t0 = Instant.parse("2026-03-31T08:00:00Z");
    store.save(screening("1003000555", "INITIAL", "FAIL", t0));
    store.save(screening("1003000555", "MANUAL", "PASS", t0.plusSeconds(60)));
    store.save(screening("1003000111", "INITIAL", "FAIL", t0.plusSeconds(120)));
    assertThat(store.find("1003000555", "FAIL", 0, 20).total()).isEqualTo(1);
    assertThat(store.find(null, "FAIL", 0, 20).total()).isEqualTo(2);
    assertThat(store.find(null, "PASS", 0, 20).total()).isEqualTo(1);
    assertThat(store.find(null, null, 0, 20).total()).isEqualTo(3);
    assertThat(store.find("1003000999", null, 0, 20).items()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] a page holds the size asked for, the next page the rest, and every page knows the total")
  void paging() {
    Instant t0 = Instant.parse("2026-03-31T08:00:00Z");
    for (int i = 0; i < 5; i++) {
      store.save(screening("1003000555", "MANUAL", "PASS", t0.plusSeconds(i)));
    }
    ScreeningPage first = store.find("1003000555", null, 0, 2);
    ScreeningPage second = store.find("1003000555", null, 1, 2);
    ScreeningPage third = store.find("1003000555", null, 2, 2);
    assertThat(first.items()).hasSize(2);
    assertThat(second.items()).hasSize(2);
    assertThat(third.items()).hasSize(1);
    assertThat(List.of(first.total(), second.total(), third.total())).containsOnly(5L);
    assertThat(first.page()).isZero();
    assertThat(second.page()).isEqualTo(1);
    assertThat(first.size()).isEqualTo(2);
    assertThat(
            List.of(first, second, third).stream()
                .flatMap(p -> p.items().stream())
                .map(Screening::screeningId)
                .distinct())
        .hasSize(5);
    assertThat(store.find("1003000555", null, 3, 2).items()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] screenings are only added: saving a second screening leaves the first unchanged")
  void firstIsUnchanged() {
    Instant t0 = Instant.parse("2026-03-31T08:00:00Z");
    Screening first = screening("1003000555", "INITIAL", "FAIL", t0);
    store.save(first);
    store.save(screening("1003000555", "MANUAL", "PASS", t0.plusSeconds(60)));
    assertThat(store.get(first.screeningId())).hasValue(first);
  }
}
