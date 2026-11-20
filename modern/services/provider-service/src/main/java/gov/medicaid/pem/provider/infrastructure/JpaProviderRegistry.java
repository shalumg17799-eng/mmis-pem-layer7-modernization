package gov.medicaid.pem.provider.infrastructure;

import gov.medicaid.pem.provider.application.ProviderRegistry;
import gov.medicaid.pem.provider.domain.Provider;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adds new provider records to provider_db (the single owner of provider identity, D-014). */
@Component
public class JpaProviderRegistry implements ProviderRegistry {

  private final ProviderJpaRepository repo;
  private final Clock clock;

  public JpaProviderRegistry(ProviderJpaRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  @Override
  public boolean exists(String npi) {
    return repo.findByNpi(npi).isPresent();
  }

  @Override
  public void add(Provider p) {
    ProviderEntity e = new ProviderEntity();
    e.publicId = UUID.randomUUID();
    e.npi = p.npi();
    e.medicaidId = p.medicaidId();
    e.providerType = p.providerType().code();
    e.taxonomy = p.taxonomy();
    e.riskTier = p.riskTier() == null ? null : p.riskTier().code();
    e.enrollStatus = p.enrollStatus().code();
    e.screenFlag = p.screenFlag().code();
    e.effectiveDate = p.effectiveDate();
    e.endDate = p.endDate();
    e.revalDueDate = p.revalidationDueDate();
    e.lastName = p.lastName();
    e.firstName = p.firstName();
    e.licenseNo = p.licenseNo();
    e.licenseState = p.licenseState();
    e.legalName = p.legalName();
    e.dbaName = p.dbaName();
    e.ownerNpi = p.ownerNpi();
    e.updatedAt = clock.instant();
    repo.saveAndFlush(e);
  }
}
