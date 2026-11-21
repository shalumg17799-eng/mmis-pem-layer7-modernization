package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-71 AC-5: every ACTIVE provider is read, page after page of 200, until the last page: no
 * provider is missed or repeated (R-PRVTMS01-cbl-00003, -00026 to -00029, -00025, -00023). The
 * provider-service of the test holds N providers and answers pages as its contract does.
 *
 * <p>Q-132 (user decision 2026-11-20, contract provider-service 1.2.0): the pages are walked by
 * key, not by page number: the first request has no key and each next one has the NPI of the last
 * row of the answer before. The fake answers both ways, as the real service does, and logs the
 * requests of the key form as "key/size" ("none" for the first); the requests of the offset form
 * ("page/size") are still logged by {@code page}, which the reader no longer uses. The cases of a
 * provider list that changes during the walk are in {@link ProviderKeysetReaderTest}.
 *
 * <p>Correction of 2026-11-21 under Q-097 C (code review of tmsis-service, MUST-FIX 1, contract
 * provider-service 1.2.0: the walk ends at the first answer whose items list is empty, or one with
 * fewer items than size): a walk no longer ends because the rows read have reached the total, so an
 * exact multiple of 200 providers costs one more request, the one that gets the empty answer (200
 * providers: 2 requests, 400: 3, 1000: 6; the six keys of 1000 providers end with 1003001000). The
 * expected values only moved with the contract; every assertion is as strong as before. The cases
 * of the end of a walk are in {@link ProviderWalkEndTest}.
 */
class ProviderPagesReaderTest {

  /** A provider-service with N providers that notes the pages asked for. */
  static final class Pages implements ActiveProviders {
    final List<ProviderRow> all = new ArrayList<>();
    final List<String> asked = new ArrayList<>();
    int failFromPage = -1;
    Long totalOverride;

    Pages(int n) {
      for (int i = 1; i <= n; i++) {
        String npi = String.format("10030%05d", i);
        all.add(new ProviderRow(npi, "M" + npi.substring(2), "A", LocalDate.parse("2026-01-15")));
      }
    }

    @Override
    public Page page(int page, int size) {
      asked.add(page + "/" + size);
      if (failFromPage >= 0 && page >= failFromPage) {
        throw new IllegalStateException("provider-service answered 500");
      }
      int from = Math.min(page * size, all.size());
      int to = Math.min(from + size, all.size());
      return new Page(all.subList(from, to), totalOverride != null ? totalOverride : all.size());
    }

    /**
     * Keyset paging (contract 1.2.0): the matches whose NPI is greater than the key, ascending, at
     * most size of them; a null key is the start of the walk; total counts all of them. The request
     * that follows the {@code failFromPage}-th one (0 = the first request) fails.
     */
    public Page after(String afterNpi, int size) {
      asked.add((afterNpi == null ? "none" : afterNpi) + "/" + size);
      if (failFromPage >= 0 && asked.size() > failFromPage) {
        throw new IllegalStateException("provider-service answered 500");
      }
      List<ProviderRow> rest =
          all.stream()
              .filter(p -> afterNpi == null || p.npi().compareTo(afterNpi) > 0)
              .limit(size)
              .toList();
      return new Page(rest, totalOverride != null ? totalOverride : all.size());
    }
  }

  static List<ProviderRow> readAll(ProviderPagesReader reader) {
    List<ProviderRow> out = new ArrayList<>();
    ProviderRow row;
    while ((row = reader.read()) != null) {
      out.add(row);
      if (out.size() > 100_000) {
        throw new AssertionError("the reader does not stop");
      }
    }
    return out;
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 450 providers are read as three requests of 200 by key (none, 1003000200, 1003000400): 450 rows, each NPI once, in the order given")
  void fourHundredFifty() {
    Pages pages = new Pages(450);
    List<ProviderRow> rows = readAll(new ProviderPagesReader(pages));
    assertThat(pages.asked).containsExactly("none/200", "1003000200/200", "1003000400/200");
    assertThat(rows).hasSize(450);
    assertThat(rows).extracting(ProviderRow::npi).doesNotHaveDuplicates().isSorted();
    assertThat(rows.get(0).npi()).isEqualTo("1003000001");
    assertThat(rows.get(449).npi()).isEqualTo("1003000450");
  }

  @ParameterizedTest(
      name = "[MAM-71 AC-5] {0} providers: {1} rows read in {2} request(s), none missed")
  @CsvSource({
    "0, 0, 1",
    "1, 1, 1",
    "199, 199, 1",
    "200, 200, 2",
    "201, 201, 2",
    "399, 399, 2",
    "400, 400, 3",
    "401, 401, 3",
    "450, 450, 3",
    "1000, 1000, 6"
  })
  void pageBoundaries(int providers, int rows, int requests) {
    Pages pages = new Pages(providers);
    assertThat(readAll(new ProviderPagesReader(pages))).hasSize(rows);
    assertThat(pages.asked).hasSize(requests);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] every request asks for the keys in order (each the last NPI of the answer before) and for the full size of 200")
  void requestsAreInOrder() {
    Pages pages = new Pages(1000);
    readAll(new ProviderPagesReader(pages));
    assertThat(pages.asked)
        .containsExactly(
            "none/200",
            "1003000200/200",
            "1003000400/200",
            "1003000600/200",
            "1003000800/200",
            "1003001000/200");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] after the last row the reader answers null (end of file) and asks for no further page")
  void endOfFileIsStable() {
    Pages pages = new Pages(3);
    ProviderPagesReader reader = new ProviderPagesReader(pages);
    readAll(reader);
    int asked = pages.asked.size();
    assertThat(reader.read()).isNull();
    assertThat(reader.read()).isNull();
    assertThat(pages.asked).hasSize(asked);
  }

  @Test
  @DisplayName("[MAM-71 AC-5] nothing is requested before the first row is read")
  void lazy() {
    Pages pages = new Pages(5);
    new ProviderPagesReader(pages);
    assertThat(pages.asked).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a page shorter than 200 is the last page even if the total says more (no endless loop)")
  void shortPageEndsTheRead() {
    Pages pages = new Pages(10);
    pages.totalOverride = 5000L;
    assertThat(readAll(new ProviderPagesReader(pages))).hasSize(10);
    assertThat(pages.asked).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a failing page is not hidden: the error reaches the caller after the rows of the first page")
  void failingPageSurfaces() {
    Pages pages = new Pages(450);
    pages.failFromPage = 1;
    ProviderPagesReader reader = new ProviderPagesReader(pages);
    for (int i = 0; i < 200; i++) {
      assertThat(reader.read()).isNotNull();
    }
    assertThatThrownBy(reader::read)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("provider-service answered 500");
  }
}
