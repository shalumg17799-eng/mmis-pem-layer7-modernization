package gov.medicaid.pem.risk.application;

/** Operational counter of assigned tiers (java-microservices section 11, D-024). */
public interface TierMetrics {

  void assigned();
}
