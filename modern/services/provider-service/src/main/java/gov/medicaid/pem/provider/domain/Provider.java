package gov.medicaid.pem.provider.domain;

import java.time.LocalDate;

/**
 * The provider golden record (PRVRECRD, 130 bytes, D-007; plus the DB2 PROVIDER fields, D-014). The
 * variant part is the individual view (type IN: names and licence) or the organisation view (GR,
 * FA: legal name, DBA name, owner NPI).
 */
public record Provider(
    String npi,
    String medicaidId,
    ProviderType providerType,
    String taxonomy,
    RiskTier riskTier,
    EnrollStatus enrollStatus,
    ScreenFlag screenFlag,
    LocalDate effectiveDate,
    LocalDate endDate,
    LocalDate revalidationDueDate,
    String lastName,
    String firstName,
    String licenseNo,
    String licenseState,
    String legalName,
    String dbaName,
    String ownerNpi,
    long version) {

  public static Builder builder() {
    return new Builder();
  }

  /** The same record with another revalidation due date (MAM-30). */
  public Provider withRevalidationDueDate(LocalDate due) {
    return new Provider(
        npi,
        medicaidId,
        providerType,
        taxonomy,
        riskTier,
        enrollStatus,
        screenFlag,
        effectiveDate,
        endDate,
        due,
        lastName,
        firstName,
        licenseNo,
        licenseState,
        legalName,
        dbaName,
        ownerNpi,
        version);
  }

  /** The same record with another enrolment status (MAM-33). */
  public Provider withEnrollStatus(EnrollStatus status) {
    return new Provider(
        npi,
        medicaidId,
        providerType,
        taxonomy,
        riskTier,
        status,
        screenFlag,
        effectiveDate,
        endDate,
        revalidationDueDate,
        lastName,
        firstName,
        licenseNo,
        licenseState,
        legalName,
        dbaName,
        ownerNpi,
        version);
  }

  /**
   * Builder for a new provider record: the identity, codes and effective date; the rest starts
   * empty.
   */
  public static final class Builder {
    private String npi;
    private String medicaidId;
    private ProviderType providerType;
    private String taxonomy;
    private EnrollStatus enrollStatus;
    private ScreenFlag screenFlag;
    private LocalDate effectiveDate;

    public Builder npi(String v) {
      npi = v;
      return this;
    }

    public Builder medicaidId(String v) {
      medicaidId = v;
      return this;
    }

    public Builder providerType(ProviderType v) {
      providerType = v;
      return this;
    }

    public Builder taxonomy(String v) {
      taxonomy = v;
      return this;
    }

    public Builder enrollStatus(EnrollStatus v) {
      enrollStatus = v;
      return this;
    }

    public Builder screenFlag(ScreenFlag v) {
      screenFlag = v;
      return this;
    }

    public Builder effectiveDate(LocalDate v) {
      effectiveDate = v;
      return this;
    }

    public Provider build() {
      return new Provider(
          npi,
          medicaidId,
          providerType,
          taxonomy,
          null,
          enrollStatus,
          screenFlag,
          effectiveDate,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          0);
    }
  }
}
