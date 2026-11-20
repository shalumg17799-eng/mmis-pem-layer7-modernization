package gov.medicaid.pem.directory.application;

import java.util.List;

/**
 * The read model behind the FHIR searches (MAM-65): ACTIVE providers only (R-PRVDIR01-ADD-01), with
 * their profile. Each search returns at most {@code limit} rows and the total that match.
 */
public interface FhirDirectory {

  /** One published provider with its profile. */
  record Listing(DirectoryEntry entry, ProviderProfile profile) {}

  record Result(List<Listing> listings, long total) {}

  /** Keeps the profile of a provider (written when it becomes ACTIVE). */
  void saveProfile(ProviderProfile profile);

  /** Individuals (IN): exact NPI and/or a name that contains the text (last or first name). */
  Result individuals(String npi, String name, int limit);

  /** Groups and facilities (GR, FA): exact NPI and/or a legal name that contains the text. */
  Result organizations(String npi, String name, int limit);

  /** Individuals (IN) with the taxonomy code (PractitionerRole specialty). */
  Result individualsWithTaxonomy(String taxonomy, int limit);
}
