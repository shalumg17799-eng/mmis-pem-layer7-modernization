package gov.medicaid.pem.directory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-67 AC-3: the page of published entries that the extract collects cannot change under it. */
class DirectoryPageTest {

  static final DirectoryEntry RIVERA =
      new DirectoryEntry("1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", "207Q00000X");

  @Test
  @DisplayName("[MAM-67 AC-3] a page keeps the items it was made with, in order")
  void keepsItems() {
    DirectoryPage page = new DirectoryPage(List.of(RIVERA), 2, 10, 21);
    assertThat(page.items()).containsExactly(RIVERA);
    assertThat(page.page()).isEqualTo(2);
    assertThat(page.size()).isEqualTo(10);
    assertThat(page.total()).isEqualTo(21);
  }

  @Test
  @DisplayName("[MAM-67 AC-3] changing the list a page was made from does not change the page")
  void copiesItems() {
    List<DirectoryEntry> source = new ArrayList<>(List.of(RIVERA));
    DirectoryPage page = new DirectoryPage(source, 0, 10, 1);
    source.clear();
    assertThat(page.items()).containsExactly(RIVERA);
  }

  @Test
  @DisplayName("[MAM-67 AC-3] the items of a page cannot be changed")
  void immutable() {
    DirectoryPage page = new DirectoryPage(List.of(RIVERA), 0, 10, 1);
    assertThatThrownBy(() -> page.items().add(RIVERA))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
