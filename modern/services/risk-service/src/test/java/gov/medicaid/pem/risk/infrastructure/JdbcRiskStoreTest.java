package gov.medicaid.pem.risk.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.risk.IntegrationTest;
import gov.medicaid.pem.risk.RiskFixtures;
import gov.medicaid.pem.risk.application.RuleSets;
import gov.medicaid.pem.risk.application.RuleTables;
import gov.medicaid.pem.risk.domain.RuleSet;
import gov.medicaid.pem.risk.domain.RuleTable;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MAM-38 and MAM-41 persistence (risk_db, real Postgres): the versioned tables risk_rule_set and
 * risk_rule behind getRiskRules, replaceRiskRules and assessRisk: the version in force, a new
 * version with its rules, a second save of the same version refused (question C-9).
 */
class JdbcRiskStoreTest extends IntegrationTest {

  static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

  @Autowired RuleTables tables;
  @Autowired RuleSets sets;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void startFromTheLegacyTable() {
    RiskFixtures.resetToLegacyTable(jdbc);
  }

  @AfterEach
  void restoreTheLegacyTable() {
    RiskFixtures.resetToLegacyTable(jdbc);
  }

  private static List<RuleTable.Rule> rules(String... entries) {
    return Arrays.stream(entries)
        .map(e -> new RuleTable.Rule(e.split("=")[0], e.split("=")[1]))
        .toList();
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-1] the table in force is version 1: default L, date 2000-01-01, the five legacy rules")
  void legacyTable() {
    RuleTable t = tables.current();

    assertThat(t.version()).isEqualTo(1);
    assertThat(t.defaultTier()).isEqualTo("L");
    assertThat(t.effectiveFrom()).isEqualTo(LocalDate.of(2000, 1, 1));
    assertThat(t.rules())
        .containsExactlyElementsOf(rules("332=H", "251=H", "341=M", "291=M", "225=M"));
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-2] a new version is stored with its rules in the order given, and becomes the one in force")
  void newVersion() {
    RuleTable next = new RuleTable(2, TODAY, "M", rules("207=H", "332=L", "251=M"));

    assertThat(tables.add(next)).isTrue();

    RuleTable t = tables.current();
    assertThat(t.version()).isEqualTo(2);
    assertThat(t.defaultTier()).isEqualTo("M");
    assertThat(t.effectiveFrom()).isEqualTo(TODAY);
    assertThat(t.rules()).containsExactlyElementsOf(rules("207=H", "332=L", "251=M"));
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-2] the assessment reads the new version: its number, default tier and prefix tiers")
  void assessmentReadsTheNewVersion() {
    tables.add(new RuleTable(2, TODAY, "M", rules("207=H", "332=L")));

    RuleSet set = sets.inForce();

    assertThat(set.version()).isEqualTo(2);
    assertThat(set.defaultTier()).isEqualTo("M");
    assertThat(set.prefixTiers()).containsOnly(Map.entry("207", "H"), Map.entry("332", "L"));
    assertThat(set.tierFor("207Q00000X")).isEqualTo("H");
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-1] the legacy rule set reads as version 1 with the five prefixes and default L")
  void legacyRuleSet() {
    RuleSet set = sets.inForce();

    assertThat(set.version()).isEqualTo(1);
    assertThat(set.defaultTier()).isEqualTo("L");
    assertThat(set.prefixTiers())
        .containsOnly(
            Map.entry("332", "H"),
            Map.entry("251", "H"),
            Map.entry("341", "M"),
            Map.entry("291", "M"),
            Map.entry("225", "M"));
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-3] saving a version that already exists returns false and stores nothing")
  void sameVersionTwice() {
    boolean saved = tables.add(new RuleTable(1, TODAY, "H", rules("207=M")));

    assertThat(saved).isFalse();
    RuleTable t = tables.current();
    assertThat(t.version()).isEqualTo(1);
    assertThat(t.defaultTier()).isEqualTo("L");
    assertThat(t.rules()).hasSize(5);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM risk_rule_set", Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-3] the second of two saves of version 2 returns false and keeps the first one's rules")
  void secondSaveOfVersion2() {
    assertThat(tables.add(new RuleTable(2, TODAY, "L", rules("207=M")))).isTrue();

    assertThat(tables.add(new RuleTable(2, TODAY, "H", rules("208=H", "209=H")))).isFalse();

    RuleTable t = tables.current();
    assertThat(t.defaultTier()).isEqualTo("L");
    assertThat(t.rules()).containsExactly(rules("207=M").get(0));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM risk_rule r JOIN risk_rule_set s ON s.id = r.rule_set_id WHERE s.rule_set_version = 2",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-6] the highest version whose date has come is the one in force, for reading and for assessing")
  void highestVersionWins() {
    tables.add(new RuleTable(2, TODAY.minusDays(1), "M", rules("207=M")));
    tables.add(new RuleTable(3, TODAY, "H", rules("207=H")));

    assertThat(tables.current().version()).isEqualTo(3);
    assertThat(sets.inForce().version()).isEqualTo(3);
    assertThat(sets.inForce().defaultTier()).isEqualTo("H");
  }

  @Test
  @DisplayName("[MAM-41 AC-6] a version whose date has not come yet is not in force")
  void futureVersionNotInForce() {
    tables.add(new RuleTable(2, TODAY.plusDays(2), "H", rules("207=H")));

    assertThat(tables.current().version()).isEqualTo(1);
    assertThat(sets.inForce().version()).isEqualTo(1);
    assertThat(sets.inForce().tierFor("207Q00000X")).isEqualTo("L");
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-2] saving a new version leaves version 1 and its rules exactly as they were")
  void version1Kept() {
    tables.add(new RuleTable(2, TODAY, "H", rules("332=L")));

    assertThat(
            jdbc.queryForList(
                "SELECT s.default_tier || ':' || r.taxonomy_prefix || '=' || r.tier FROM risk_rule r"
                    + " JOIN risk_rule_set s ON s.id = r.rule_set_id WHERE s.rule_set_version = 1",
                String.class))
        .containsExactlyInAnyOrder("L:332=H", "L:251=H", "L:341=M", "L:291=M", "L:225=M");
  }

  @Test
  @DisplayName("[MAM-38 AC-3] the database refuses a prefix twice in one rule set")
  void prefixUniquePerRuleSet() {
    Long set =
        jdbc.queryForObject("SELECT id FROM risk_rule_set WHERE rule_set_version = 1", Long.class);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO risk_rule (rule_set_id, taxonomy_prefix, tier) VALUES (?, '332', 'M')",
                    set))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  @DisplayName("[MAM-38 AC-3] the database refuses a second rule set with the same version")
  void versionUnique() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO risk_rule_set (rule_set_version, effective_from, default_tier) VALUES (1, DATE '2026-01-15', 'L')"))
        .isInstanceOf(DuplicateKeyException.class);
  }
}
