package gov.medicaid.pem.provider.infrastructure;

import gov.medicaid.pem.provider.application.ExportProviderMaster;
import gov.medicaid.pem.provider.application.ProviderStore;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/** The provider store on Postgres (provider_db). */
@Component
public class JpaProviderStore implements ProviderStore, ExportProviderMaster.AllProviders {

  private final ProviderJpaRepository repo;

  public JpaProviderStore(ProviderJpaRepository repo) {
    this.repo = repo;
  }

  @Override
  public Optional<Provider> findByNpi(String npi) {
    return repo.findByNpi(npi).map(JpaProviderStore::toDomain);
  }

  /** MAM-43: the whole master in ascending NPI order (the key order of the legacy KSDS). */
  @Override
  public List<Provider> allByNpi() {
    return repo.findAll(Sort.by("npi")).stream().map(JpaProviderStore::toDomain).toList();
  }

  static Provider toDomain(ProviderEntity e) {
    return new Provider(
        trim(e.npi),
        trim(e.medicaidId),
        ProviderType.fromCode(e.providerType),
        trim(e.taxonomy),
        RiskTier.fromNullableCode(e.riskTier),
        EnrollStatus.fromCode(e.enrollStatus),
        ScreenFlag.fromCode(e.screenFlag),
        e.effectiveDate,
        e.endDate,
        e.revalDueDate,
        e.lastName,
        e.firstName,
        e.licenseNo,
        trim(e.licenseState),
        e.legalName,
        e.dbaName,
        trim(e.ownerNpi),
        e.version == null ? 0 : e.version);
  }

  /** CHAR(n) columns come back padded with spaces. */
  private static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
