package gov.medicaid.pem.enrollment.application;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An application as submitted (contract ApplicationRequest); the variant part is the individual or
 * the organisation.
 */
public record NewApplication(
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    Individual individual,
    Organization organization) {

  /** PRV-INDIVIDUAL-DATA view (type IN). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Individual(
      String lastName, String firstName, String licenseNo, String licenseState) {}

  /** PRV-ORG-DATA view (types GR and FA). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Organization(String legalName, String dbaName, String ownerNpi) {}
}
