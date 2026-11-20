package gov.medicaid.pem.risk.application;

import java.time.Instant;

/** risk_assessment keeps every tier assigned (data model). */
public interface Assessments {

  void save(String npi, String taxonomy, String tier, int ruleSetVersion, Instant assessedAt);
}
