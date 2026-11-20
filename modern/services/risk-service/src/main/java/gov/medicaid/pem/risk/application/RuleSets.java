package gov.medicaid.pem.risk.application;

import gov.medicaid.pem.risk.domain.RuleSet;

/** The rule set in force now (the highest version whose effective date has come). */
public interface RuleSets {

  RuleSet inForce();
}
