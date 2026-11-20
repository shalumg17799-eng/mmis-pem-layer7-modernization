package gov.medicaid.pem.risk.application;

import gov.medicaid.pem.risk.domain.RuleTable;

/** The versioned risk tables of risk_db (risk_rule_set, risk_rule), as the business edits them. */
public interface RuleTables {

  /** The table in force now (the highest version whose effective date has come). */
  RuleTable current();

  /**
   * Stores the table as a new version; false when that version already exists (someone saved
   * first).
   */
  boolean add(RuleTable table);
}
