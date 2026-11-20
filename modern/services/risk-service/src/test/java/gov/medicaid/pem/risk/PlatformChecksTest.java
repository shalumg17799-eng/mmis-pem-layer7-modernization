package gov.medicaid.pem.risk;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.testsupport.PemArchitecture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Platform checks of risk-service, written with its first story (MAM-35). */
class PlatformChecksTest extends IntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("[MAM-35] risk-service respects the layering rules")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.risk");
  }

  @Test
  @DisplayName(
      "[MAM-35] the legacy rule table is rule set version 1 with the five PRVRSK01 prefixes and default L")
  void legacyRuleSet() {
    assertThat(
            jdbc.queryForObject(
                "SELECT default_tier FROM risk_rule_set WHERE rule_set_version = 1", String.class))
        .isEqualTo("L");
    assertThat(
            jdbc.queryForList(
                "SELECT r.taxonomy_prefix || '=' || r.tier FROM risk_rule r JOIN risk_rule_set s ON s.id = r.rule_set_id"
                    + " WHERE s.rule_set_version = 1",
                String.class))
        .containsExactlyInAnyOrder("332=H", "251=H", "341=M", "291=M", "225=M");
  }
}
