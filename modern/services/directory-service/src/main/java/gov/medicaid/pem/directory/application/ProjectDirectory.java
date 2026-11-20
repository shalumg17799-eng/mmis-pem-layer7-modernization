package gov.medicaid.pem.directory.application;

import gov.medicaid.pem.directory.domain.DirectoryRules;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the directory read model in step with the provider events (MAM-61): a registration is kept
 * as PENDING; a change to ACTIVE re-reads the provider from provider-service and composes the name
 * (D-028); any other status change only moves the status, which withdraws the provider; a tier is
 * kept whatever the order of arrival.
 */
@Service
public class ProjectDirectory {

  private final DirectoryStore store;
  private final ProviderFacts facts;

  private ProviderProfiles profiles;
  private FhirDirectory fhir;

  public ProjectDirectory(DirectoryStore store, ProviderFacts facts) {
    this.store = store;
    this.facts = facts;
  }

  /**
   * MAM-65: the profile (names, licence) that the FHIR resources show, kept when a provider is
   * published; unit tests that do not wire it keep no profile.
   */
  @Autowired(required = false)
  void setProfiles(ProviderProfiles profiles, FhirDirectory fhir) {
    this.profiles = profiles;
    this.fhir = fhir;
  }

  @Transactional
  public void registered(
      String npi, String medicaidId, String providerType, String taxonomy, String displayName) {
    String tier = store.find(npi).map(DirectoryEntry::riskTier).orElse(null);
    store.save(new DirectoryEntry(npi, medicaidId, providerType, tier, "P", displayName, taxonomy));
  }

  @Transactional
  public void tierAssigned(String npi, String tier) {
    DirectoryEntry e = store.find(npi).orElseGet(() -> fromProvider(npi, "P", null));
    store.save(
        new DirectoryEntry(
            e.npi(),
            e.medicaidId(),
            e.providerType(),
            tier,
            e.enrollStatus(),
            e.displayName(),
            e.taxonomy()));
  }

  @Transactional
  public void statusChanged(String npi, String to) {
    Optional<DirectoryEntry> existing = store.find(npi);
    if (DirectoryRules.published(to)) {
      store.save(fromProvider(npi, to, existing.map(DirectoryEntry::riskTier).orElse(null)));
      if (profiles != null) {
        fhir.saveProfile(profiles.profile(npi));
      }
      return;
    }
    existing.ifPresent(
        e ->
            store.save(
                new DirectoryEntry(
                    e.npi(),
                    e.medicaidId(),
                    e.providerType(),
                    e.riskTier(),
                    to,
                    e.displayName(),
                    e.taxonomy())));
  }

  private DirectoryEntry fromProvider(String npi, String status, String tier) {
    ProviderDetails d = facts.details(npi);
    return new DirectoryEntry(
        npi,
        d.medicaidId(),
        d.providerType(),
        tier,
        status,
        DirectoryRules.displayName(d.providerType(), d.firstName(), d.lastName(), d.legalName()),
        d.taxonomy());
  }
}
