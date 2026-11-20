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
      "[MAM-71 AC-5] 450 providers are read as pages 0, 1 and 2 of 200: 450 rows, each NPI once, in the order given")
  void fourHundredFifty() {
    Pages pages = new Pages(450);
    List<ProviderRow> rows = readAll(new ProviderPagesReader(pages));
    assertThat(pages.asked).containsExactly("0/200", "1/200", "2/200");
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
    "200, 200, 1",
    "201, 201, 2",
    "399, 399, 2",
    "400, 400, 2",
    "401, 401, 3",
    "450, 450, 3",
    "1000, 1000, 5"
  })
  void pageBoundaries(int providers, int rows, int requests) {
    Pages pages = new Pages(providers);
    assertThat(readAll(new ProviderPagesReader(pages))).hasSize(rows);
    assertThat(pages.asked).hasSize(requests);
  }

  @Test
  @DisplayName("[MAM-71 AC-5] every request asks for the pages in order and for the full size of 200")
  void requestsAreInOrder() {
    Pages pages = new Pages(1000);
    readAll(new ProviderPagesReader(pages));
    assertThat(pages.asked).containsExactly("0/200", "1/200", "2/200", "3/200", "4/200");
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
