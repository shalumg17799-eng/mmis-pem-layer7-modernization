package gov.medicaid.pem.risk.infrastructure;

import gov.medicaid.pem.risk.application.Assessments;
import gov.medicaid.pem.risk.application.RuleSets;
import gov.medicaid.pem.risk.application.RuleTables;
import gov.medicaid.pem.risk.domain.RuleSet;
import gov.medicaid.pem.risk.domain.RuleTable;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** risk_db: the rule sets (risk_rule_set, risk_rule) and the assessments (risk_assessment). */
@Repository
public class JdbcRiskStore implements RuleSets, Assessments, RuleTables {

  private final JdbcTemplate jdbc;

  public JdbcRiskStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public RuleSet inForce() {
    Map<String, Object> set =
        jdbc.queryForMap(
            "SELECT id, rule_set_version, default_tier FROM risk_rule_set WHERE effective_from <= current_date"
                + " ORDER BY rule_set_version DESC LIMIT 1");
    Map<String, String> rules = new HashMap<>();
    jdbc.query(
        "SELECT taxonomy_prefix, tier FROM risk_rule WHERE rule_set_id = ?",
        rs -> {
          rules.put(rs.getString(1).trim(), rs.getString(2).trim());
        },
        set.get("id"));
    return new RuleSet(
        ((Number) set.get("rule_set_version")).intValue(),
        set.get("default_tier").toString().trim(),
        rules);
  }

  /** MAM-38: the table in force with its rules in the order they were saved. */
  @Override
  public RuleTable current() {
    Map<String, Object> set =
        jdbc.queryForMap(
            "SELECT id, rule_set_version, effective_from, default_tier FROM risk_rule_set"
                + " WHERE effective_from <= current_date ORDER BY rule_set_version DESC LIMIT 1");
    List<RuleTable.Rule> rules =
        jdbc.query(
            "SELECT taxonomy_prefix, tier FROM risk_rule WHERE rule_set_id = ? ORDER BY id",
            (rs, n) -> new RuleTable.Rule(rs.getString(1).trim(), rs.getString(2).trim()),
            set.get("id"));
    return new RuleTable(
        ((Number) set.get("rule_set_version")).intValue(),
        ((Date) set.get("effective_from")).toLocalDate(),
        set.get("default_tier").toString().trim(),
        rules);
  }

  /** MAM-38: a new version with its rules; the unique version refuses a concurrent second save. */
  @Override
  public boolean add(RuleTable table) {
    Long id;
    try {
      id =
          jdbc.queryForObject(
              "INSERT INTO risk_rule_set (rule_set_version, effective_from, default_tier)"
                  + " VALUES (?, ?, ?) RETURNING id",
              Long.class,
              table.version(),
              table.effectiveFrom(),
              table.defaultTier());
    } catch (DuplicateKeyException e) {
      return false;
    }
    for (RuleTable.Rule r : table.rules()) {
      jdbc.update(
          "INSERT INTO risk_rule (rule_set_id, taxonomy_prefix, tier) VALUES (?, ?, ?)",
          id,
          r.taxonomyPrefix(),
          r.tier());
    }
    return true;
  }

  @Override
  public void save(
      String npi, String taxonomy, String tier, int ruleSetVersion, Instant assessedAt) {
    jdbc.update(
        "INSERT INTO risk_assessment (npi, taxonomy, tier, rule_set_version, assessed_at) VALUES (?, ?, ?, ?, ?)",
        npi,
        taxonomy,
        tier,
        ruleSetVersion,
        Timestamp.from(assessedAt));
  }
}
