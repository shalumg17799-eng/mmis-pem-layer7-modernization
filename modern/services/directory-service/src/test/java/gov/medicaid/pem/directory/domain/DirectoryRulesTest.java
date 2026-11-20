package gov.medicaid.pem.directory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-61: who is published (status ACTIVE only, the legacy filter) and how the name is composed
 * (D-028).
 */
class DirectoryRulesTest {

  @Test
  @DisplayName(
      "[MAM-61 AC-2/AC-3] an individual is shown as first name + last name, trimmed, single-spaced")
  void individual() {
    assertThat(DirectoryRules.displayName("IN", "MARIA", "RIVERA", null)).isEqualTo("MARIA RIVERA");
    assertThat(DirectoryRules.displayName("IN", "MARIA   ", "  RIVERA ", null))
        .isEqualTo("MARIA RIVERA");
    assertThat(DirectoryRules.displayName("IN", null, "RIVERA", null)).isEqualTo("RIVERA");
  }

  @Test
  @DisplayName("[MAM-61 AC-3] a group or facility is shown by its legal name, never the DBA name")
  void organisation() {
    assertThat(DirectoryRules.displayName("FA", null, null, "SUNSET HOSPICE INC"))
        .isEqualTo("SUNSET HOSPICE INC");
    assertThat(DirectoryRules.displayName("GR", null, null, " EXCLUDED CLINIC LLC "))
        .isEqualTo("EXCLUDED CLINIC LLC");
  }

  @Test
  @DisplayName("[MAM-61 AC-3] a name is never longer than 60 characters")
  void maxLength() {
    String legal = "SAMPLE " + "X".repeat(70);
    assertThat(DirectoryRules.displayName("FA", null, null, legal)).hasSize(60);
  }

  @Test
  @DisplayName("[MAM-61 AC-1/AC-4/AC-6] only an ACTIVE provider is published")
  void active() {
    assertThat(DirectoryRules.published("A")).isTrue();
  }

  @ParameterizedTest(name = "[MAM-61 AC-1/AC-4] a provider with status {0} is not published")
  @ValueSource(strings = {"P", "T", "D", "S"})
  void notActive(String status) {
    assertThat(DirectoryRules.published(status)).isFalse();
  }
}
