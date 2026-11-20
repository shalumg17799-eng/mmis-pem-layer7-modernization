package gov.medicaid.pem.sanctions.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Lane 3 unit test for MAM-52 AC-8 (the sanction history is readable): ListSanctions hands the NPI,
 * the page and the size to the history and returns its page as it is. The intake of MAM-55 and the
 * history of MAM-52 are read through the same endpoint.
 */
class ListSanctionsTest {

  record Query(String npi, int page, int size) {}

  final List<Query> queries = new ArrayList<>();

  final Sanction sanction =
      new Sanction(
          UUID.fromString("5d1c1c60-0000-4000-8000-000000000052"),
          "1003000111",
          SanctionAction.T,
          "SYNTHETIC TEST REASON",
          LocalDate.of(2026, 3, 31),
          "SANCIN",
          "admin1",
          Instant.parse("2026-11-19T10:00:00Z"));

  final SanctionHistory history =
      new SanctionHistory() {
        @Override
        public void add(Sanction s) {
          throw new AssertionError("reading the history never adds to it");
        }

        @Override
        public SanctionPage find(String npi, int page, int size) {
          queries.add(new Query(npi, page, size));
          return new SanctionPage(List.of(sanction), page, size, 41);
        }
      };

  final ListSanctions list = new ListSanctions(history);

  @Test
  @DisplayName("[MAM-52 AC-8] the page is asked for with the NPI, the page and the size given")
  void asksTheHistory() {
    list.page("1003000111", 2, 50);
    assertThat(queries).containsExactly(new Query("1003000111", 2, 50));
  }

  @Test
  @DisplayName("[MAM-52 AC-8] the page the history answers is returned as it is")
  void returnsThePage() {
    SanctionPage page = list.page("1003000111", 2, 50);
    assertThat(page.items()).containsExactly(sanction);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.total()).isEqualTo(41);
  }

  @Test
  @DisplayName("[MAM-52 AC-8] no NPI means the history of every provider")
  void everyProvider() {
    list.page(null, 0, 20);
    assertThat(queries).containsExactly(new Query(null, 0, 20));
  }
}
