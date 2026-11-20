package gov.medicaid.pem.provider.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import java.time.LocalDate;

/**
 * The {@code Provider} schema of contracts/openapi/provider-service.yaml (codes as stored legacy
 * values).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderResponse(
    String npi,
    String medicaidId,
    String providerType,
    String taxonomy,
    String riskTier,
    String enrollStatus,
    String screenFlag,
    LocalDate effectiveDate,
    LocalDate endDate,
    LocalDate revalidationDueDate,
    Individual individual,
    Organization organization,
    long version) {

  /** PRV-INDIVIDUAL-DATA view (type IN). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Individual(
      String lastName, String firstName, String licenseNo, String licenseState) {}

  /** PRV-ORG-DATA view (types GR and FA). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Organization(String legalName, String dbaName, String ownerNpi) {}

  static ProviderResponse of(Provider p) {
    boolean individual = p.providerType() == ProviderType.INDIVIDUAL;
    return new ProviderResponse(
        p.npi(),
        p.medicaidId(),
        p.providerType().code(),
        p.taxonomy(),
        p.riskTier() == null ? null : p.riskTier().code(),
        p.enrollStatus().code(),
        p.screenFlag().code(),
        p.effectiveDate(),
        p.endDate(),
        p.revalidationDueDate(),
        individual
            ? new Individual(p.lastName(), p.firstName(), p.licenseNo(), p.licenseState())
            : null,
        individual ? null : new Organization(p.legalName(), p.dbaName(), p.ownerNpi()),
        p.version());
  }
}
