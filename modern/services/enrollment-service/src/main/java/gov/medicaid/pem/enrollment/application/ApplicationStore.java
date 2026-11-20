package gov.medicaid.pem.enrollment.application;

import java.util.Map;

/** Persistence port of enrollment_db. */
public interface ApplicationStore {

  /** True when an application for this NPI is already VALIDATED (E103). */
  boolean hasValidated(String npi);

  /** The provider_type_mapping table: feed code to IN, GR or FA (D-008). */
  Map<String, String> providerTypeMappings();

  void save(StoredApplication application, String submittedBy);

  ApplicationPage find(ApplicationQuery query);
}
