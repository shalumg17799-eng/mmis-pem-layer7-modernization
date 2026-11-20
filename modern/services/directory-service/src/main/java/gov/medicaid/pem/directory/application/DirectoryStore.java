package gov.medicaid.pem.directory.application;

import java.util.Optional;

/** directory_db: the read model. Searches are answered from it alone (AC-8). */
public interface DirectoryStore {

  Optional<DirectoryEntry> find(String npi);

  void save(DirectoryEntry entry);

  /**
   * ACTIVE entries only, filtered by name (contains, any case), provider type and taxonomy when
   * given.
   */
  DirectoryPage published(String name, String providerType, String taxonomy, int page, int size);
}
