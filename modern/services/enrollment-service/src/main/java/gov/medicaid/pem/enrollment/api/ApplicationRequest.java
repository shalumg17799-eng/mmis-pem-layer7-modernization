package gov.medicaid.pem.enrollment.api;

import gov.medicaid.pem.enrollment.application.NewApplication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Contract ApplicationRequest. Shape errors (a missing required property, a value longer than its
 * field) are 400; the business checks E101-E104 run on what is present and give 422.
 */
public record ApplicationRequest(
    @NotNull String npi,
    @NotNull @Size(max = 9) String medicaidId,
    @NotNull String providerType,
    @NotNull @Size(max = 10) String taxonomy,
    @Valid Individual individual,
    @Valid Organization organization) {

  /** Contract Individual. */
  public record Individual(
      @Size(max = 30) String lastName,
      @Size(max = 20) String firstName,
      @Size(max = 15) String licenseNo,
      @Size(min = 2, max = 2) String licenseState) {}

  /** Contract Organization. */
  public record Organization(
      @Size(max = 60) String legalName,
      @Size(max = 30) String dbaName,
      @Pattern(regexp = "^[0-9]{10}$") String ownerNpi) {}

  NewApplication toCommand() {
    return new NewApplication(
        npi,
        medicaidId,
        providerType,
        taxonomy,
        individual == null
            ? null
            : new NewApplication.Individual(
                individual.lastName(),
                individual.firstName(),
                individual.licenseNo(),
                individual.licenseState()),
        organization == null
            ? null
            : new NewApplication.Organization(
                organization.legalName(), organization.dbaName(), organization.ownerNpi()));
  }
}
