package gov.medicaid.pem.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-41: only the first three characters of the taxonomy count (PRVRSK01 CLASSIFY-ONE, lines
 * 79-93, R-PRVRSK01-cbl-00003/18/19/20/21); a blank or shorter value gets the default tier
 * (R-PRVRSK01-ADD-03); the answer comes from the table in force (rule set version).
 */
class RuleSetPrefixTest {

  static final RuleSet LEGACY =
      new RuleSet(1, "L", Map.of("332", "H", "251", "H", "341", "M", "291", "M", "225", "M"));

  @ParameterizedTest(
      name = "[MAM-41 AC-2] taxonomy {0} is High: the characters after 332 are ignored")
  @ValueSource(strings = {"332", "3320", "33200", "3320000000", "332ZZZZZZZ", "332 000000"})
  void onlyFirstThreeCount(String taxonomy) {
    assertThat(LEGACY.tierFor(taxonomy)).isEqualTo("H");
  }

  @ParameterizedTest(name = "[MAM-41 AC-2] taxonomy {0} is Limited: 332 is not at the start")
  @ValueSource(strings = {"X332000000", "0332000000", "3 32000000", " 332000000", "233200000X"})
  void prefixMustBeAtTheStart(String taxonomy) {
    assertThat(LEGACY.tierFor(taxonomy)).isEqualTo("L");
  }

  @Test
  @DisplayName("[MAM-41 AC-1] exactly three characters that are a prefix of the table are enough")
  void threeCharactersAreEnough() {
    assertThat(LEGACY.tierFor("341")).isEqualTo("M");
    assertThat(LEGACY.tierFor("251")).isEqualTo("H");
    assertThat(LEGACY.tierFor("225")).isEqualTo("M");
  }

  @ParameterizedTest(name = "[MAM-41 AC-3] taxonomy \"{0}\" gets the default tier L")
  @ValueSource(
      strings = {"", " ", "  ", "   ", "          ", "3", "33", "2", "25", "29", "34", "22"})
  void blankOrShortGetsDefault(String taxonomy) {
    assertThat(LEGACY.tierFor(taxonomy)).isEqualTo("L");
  }

  @Test
  @DisplayName("[MAM-41 AC-3] a taxonomy that is missing altogether gets the default tier")
  void nullGetsDefault() {
    assertThat(LEGACY.tierFor(null)).isEqualTo("L");
  }

  @Test
  @DisplayName("[MAM-41 AC-3] the default tier is the one of the table, not always L")
  void defaultIsTheTables() {
    RuleSet table = new RuleSet(2, "H", Map.of("332", "L"));

    assertThat(table.tierFor("")).isEqualTo("H");
    assertThat(table.tierFor("33")).isEqualTo("H");
    assertThat(table.tierFor("999Z00000X")).isEqualTo("H");
    assertThat(table.tierFor("332B00000X")).isEqualTo("L");
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-6] a table with prefix 207 as M gives M for 207Q00000X and keeps its version")
  void newerTable() {
    RuleSet v2 =
        new RuleSet(
            2, "L", Map.of("332", "H", "251", "H", "341", "M", "291", "M", "225", "M", "207", "M"));

    assertThat(v2.tierFor("207Q00000X")).isEqualTo("M");
    assertThat(v2.version()).isEqualTo(2);
    assertThat(LEGACY.tierFor("207Q00000X")).isEqualTo("L");
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-1] a rule set is a copy: the map it was built from cannot change it later")
  void copiesItsMap() {
    Map<String, String> source = new HashMap<>(Map.of("332", "H"));
    RuleSet set = new RuleSet(1, "L", source);

    source.put("207", "M");
    source.put("332", "L");

    assertThat(set.tierFor("207Q00000X")).isEqualTo("L");
    assertThat(set.tierFor("332B00000X")).isEqualTo("H");
  }
}
