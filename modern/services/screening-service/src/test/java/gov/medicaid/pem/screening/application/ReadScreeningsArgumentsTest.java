package gov.medicaid.pem.screening.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-28 AC-1: listing the screenings of an NPI hands exactly the filter, the page and the size to
 * the store (earlier screenings are only read, never changed: D-018).
 */
class ReadScreeningsArgumentsTest {

  record Query(String npi, String result, int page, int size) {}

  final List<Query> queries = new ArrayList<>();

  final ScreeningStore store =
      new ScreeningStore() {
        @Override
        public void save(Screening screening) {
          throw new AssertionError("reading must not write");
        }

        @Override
        public ScreeningPage find(String npi, String result, int page, int size) {
          queries.add(new Query(npi, result, page, size));
          return new ScreeningPage(List.of(), page, size, 0);
        }

        @Override
        public Optional<Screening> get(UUID screeningId) {
          return Optional.empty();
        }
      };

  @Test
  @DisplayName("[MAM-28 AC-1] the NPI, the result, the page and the size reach the store unchanged")
  void argumentsPassedThrough() {
    ScreeningPage page = new ReadScreenings(store).list("1003000555", "PASS", 3, 50);
    assertThat(queries).containsExactly(new Query("1003000555", "PASS", 3, 50));
    assertThat(page.page()).isEqualTo(3);
    assertThat(page.size()).isEqualTo(50);
    assertThat(page.total()).isZero();
  }

  @Test
  @DisplayName("[MAM-28 AC-1] without filters the store gets no NPI and no result")
  void noFilters() {
    new ReadScreenings(store).list(null, null, 0, 20);
    assertThat(queries).containsExactly(new Query(null, null, 0, 20));
  }
}
