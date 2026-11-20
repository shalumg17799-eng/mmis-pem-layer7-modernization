package gov.medicaid.pem.risk.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-38: the risk table as the business sees and changes it (Q-005, question C-9): a version, a
 * date, the default tier and the prefix rules; a proposed table is well formed (else 400, contract
 * RiskRule and RiskRuleSet) and consistent (else 422, a prefix once).
 */
class RuleTableTest {

  static final LocalDate DAY = LocalDate.of(2026, 1, 15);

  static List<RuleTable.Rule> legacy() {
    return List.of(
        new RuleTable.Rule("332", "H"),
        new RuleTable.Rule("251", "H"),
        new RuleTable.Rule("341", "M"),
        new RuleTable.Rule("291", "M"),
        new RuleTable.Rule("225", "M"));
  }

  static List<RuleTable.Rule> rulesWith(RuleTable.Rule... extra) {
    List<RuleTable.Rule> all = new ArrayList<>(legacy());
    all.addAll(Arrays.asList(extra));
    return all;
  }

  // ---- the table -------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-1] the table holds its version, date, default tier and rules in their order")
  void holdsItsValues() {
    RuleTable t = new RuleTable(1, DAY, "L", legacy());

    assertThat(t.version()).isEqualTo(1);
    assertThat(t.effectiveFrom()).isEqualTo(DAY);
    assertThat(t.defaultTier()).isEqualTo("L");
    assertThat(t.rules()).containsExactlyElementsOf(legacy());
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-1] the table as the assessment uses it maps each prefix to its tier, with the version and default tier")
  void asRuleSet() {
    RuleSet set = new RuleTable(3, DAY, "M", legacy()).ruleSet();

    assertThat(set.version()).isEqualTo(3);
    assertThat(set.defaultTier()).isEqualTo("M");
    assertThat(set.prefixTiers())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("332", "H", "251", "H", "341", "M", "291", "M", "225", "M"));
  }

  @Test
  @DisplayName("[MAM-38 AC-1] the legacy table gives the PRVRSK01 tiers for its prefixes")
  void legacyTiers() {
    RuleSet set = new RuleTable(1, DAY, "L", legacy()).ruleSet();

    assertThat(set.tierFor("332B00000X")).isEqualTo("H");
    assertThat(set.tierFor("341600000X")).isEqualTo("M");
    assertThat(set.tierFor("207Q00000X")).isEqualTo("L");
  }

  @Test
  @DisplayName("[MAM-38 AC-5] a saved table cannot be changed afterwards through its rule list")
  void tableIsImmutable() {
    List<RuleTable.Rule> source = new ArrayList<>(legacy());
    RuleTable t = new RuleTable(1, DAY, "L", source);

    source.add(new RuleTable.Rule("207", "M"));

    assertThat(t.rules()).hasSize(5);
    assertThatThrownBy(() -> t.rules().add(new RuleTable.Rule("207", "M")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  // ---- malformed: the default tier -------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-38 AC-4] default tier \"{0}\" is malformed")
  @NullSource
  @ValueSource(strings = {"", "X", "l", "LL", " L", "m"})
  void badDefaultTier(String tier) {
    assertThat(RuleTable.malformed(tier, legacy()))
        .hasValueSatisfying(w -> assertThat(w).contains("defaultTier"));
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] default tier {0} is well formed")
  @ValueSource(strings = {"L", "M", "H"})
  void goodDefaultTier(String tier) {
    assertThat(RuleTable.malformed(tier, legacy())).isEmpty();
  }

  // ---- malformed: the rules --------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-38 AC-4] a table without a rules list is malformed")
  void rulesRequired() {
    assertThat(RuleTable.malformed("L", null))
        .hasValueSatisfying(w -> assertThat(w).contains("rules are required"));
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a table with no rules at all (only the default tier) is well formed")
  void emptyRulesAreWellFormed() {
    assertThat(RuleTable.malformed("L", List.of())).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] prefix \"{0}\" is malformed")
  @ValueSource(
      strings = {
        "",
        "2",
        "20",
        "2077",
        "20q",
        "abc",
        "20-",
        "20 ",
        " 207",
        "2.7",
        "\uFF12\uFF10\uFF17"
      })
  void badPrefix(String prefix) {
    assertThat(RuleTable.malformed("L", List.of(new RuleTable.Rule(prefix, "M"))))
        .hasValueSatisfying(w -> assertThat(w).contains("taxonomyPrefix"));
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a rule with no prefix is malformed")
  void nullPrefix() {
    assertThat(RuleTable.malformed("L", List.of(new RuleTable.Rule(null, "M"))))
        .hasValueSatisfying(w -> assertThat(w).contains("taxonomyPrefix"));
  }

  @Test
  @DisplayName("[MAM-38 AC-4] an empty entry in the rules list is malformed")
  void nullRule() {
    assertThat(RuleTable.malformed("L", Arrays.asList(new RuleTable.Rule("332", "H"), null)))
        .hasValueSatisfying(w -> assertThat(w).contains("taxonomyPrefix"));
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] prefix {0} is well formed")
  @ValueSource(strings = {"332", "207", "000", "999", "2A1", "ABC", "00A", "Z9Z"})
  void goodPrefix(String prefix) {
    assertThat(RuleTable.malformed("L", List.of(new RuleTable.Rule(prefix, "M")))).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] tier \"{0}\" of a rule is malformed")
  @NullSource
  @ValueSource(strings = {"", "X", "h", "HH", "l", "1"})
  void badRuleTier(String tier) {
    assertThat(RuleTable.malformed("L", List.of(new RuleTable.Rule("207", tier))))
        .hasValueSatisfying(w -> assertThat(w).contains("tier must be L, M or H"));
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] tier {0} of a rule is well formed")
  @ValueSource(strings = {"L", "M", "H"})
  void goodRuleTier(String tier) {
    assertThat(RuleTable.malformed("L", List.of(new RuleTable.Rule("207", tier)))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a bad rule is found wherever it stands in the list")
  void badRuleLater() {
    assertThat(RuleTable.malformed("L", rulesWith(new RuleTable.Rule("20", "M"))))
        .hasValueSatisfying(w -> assertThat(w).contains("taxonomyPrefix"));
    assertThat(RuleTable.malformed("L", rulesWith(new RuleTable.Rule("207", "X"))))
        .hasValueSatisfying(w -> assertThat(w).contains("tier"));
  }

  @Test
  @DisplayName("[MAM-38 AC-4] the legacy table plus a new prefix is well formed")
  void legacyIsWellFormed() {
    assertThat(RuleTable.malformed("L", rulesWith(new RuleTable.Rule("207", "M")))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-38 AC-4] the default tier is reported before the rules")
  void defaultTierFirst() {
    assertThat(RuleTable.malformed("X", null))
        .hasValueSatisfying(w -> assertThat(w).contains("defaultTier"));
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a bad prefix is reported before a bad tier of the same rule")
  void prefixBeforeTier() {
    assertThat(RuleTable.malformed("L", List.of(new RuleTable.Rule("20", "X"))))
        .hasValueSatisfying(w -> assertThat(w).contains("taxonomyPrefix"));
  }

  // ---- inconsistent ----------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-3] a prefix listed twice makes the table inconsistent, naming the prefix")
  void duplicatePrefix() {
    assertThat(RuleTable.inconsistent(rulesWith(new RuleTable.Rule("332", "M"))))
        .hasValueSatisfying(w -> assertThat(w).contains("332").contains("more than once"));
  }

  @Test
  @DisplayName("[MAM-38 AC-3] the same rule listed twice is also inconsistent")
  void identicalDuplicate() {
    assertThat(RuleTable.inconsistent(rulesWith(new RuleTable.Rule("332", "H")))).isPresent();
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a duplicate that is not next to its twin is found")
  void duplicateFarApart() {
    assertThat(
            RuleTable.inconsistent(
                List.of(
                    new RuleTable.Rule("207", "M"),
                    new RuleTable.Rule("332", "H"),
                    new RuleTable.Rule("251", "H"),
                    new RuleTable.Rule("207", "L"))))
        .hasValueSatisfying(w -> assertThat(w).contains("207"));
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a table that lists every prefix once is consistent")
  void consistent() {
    assertThat(RuleTable.inconsistent(rulesWith(new RuleTable.Rule("207", "M")))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a table without rules is consistent")
  void consistentEmpty() {
    assertThat(RuleTable.inconsistent(List.of())).isEmpty();
  }
}
