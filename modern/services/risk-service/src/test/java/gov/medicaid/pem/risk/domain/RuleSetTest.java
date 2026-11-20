package gov.medicaid.pem.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-35: PRVRSK01's taxonomy-prefix rule as a data-driven table (Q-005); rule set version 1 = the
 * legacy table.
 */
class RuleSetTest {

  static final RuleSet LEGACY =
      new RuleSet(1, "L", Map.of("332", "H", "251", "H", "341", "M", "291", "M", "225", "M"));

  @ParameterizedTest(name = "[MAM-35 AC-2/3/4] taxonomy {0} gives tier {1}")
  @CsvSource({
    "332B00000X,H",
    "251E00000X,H",
    "251G00000X,H",
    "341600000X,M",
    "291U00000X,M",
    "225100000X,M",
    "207Q00000X,L",
    "999Z00000X,L",
    "33,L"
  })
  void tier(String taxonomy, String tier) {
    assertThat(LEGACY.tierFor(taxonomy)).isEqualTo(tier);
  }

  @Test
  @DisplayName("[MAM-35 AC-4] a prefix in no rule gets the rule set's default tier")
  void defaultTier() {
    assertThat(new RuleSet(2, "M", Map.of()).tierFor("207Q00000X")).isEqualTo("M");
  }

  @Test
  @DisplayName("[MAM-35] a missing taxonomy gets the default tier")
  void missingTaxonomy() {
    assertThat(LEGACY.tierFor(null)).isEqualTo("L");
  }
}
