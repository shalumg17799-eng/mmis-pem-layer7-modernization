package gov.medicaid.pem.provider.application;

/** Operational counters of the registration (java-microservices section 11). */
public interface RegistrationMetrics {

  /**
   * A validated application whose NPI is already registered was skipped (legacy duplicate-key
   * branch).
   */
  void duplicateSkipped();
}
