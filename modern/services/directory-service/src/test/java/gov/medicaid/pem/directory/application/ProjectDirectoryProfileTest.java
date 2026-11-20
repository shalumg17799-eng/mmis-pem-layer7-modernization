package gov.medicaid.pem.directory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-65 AC-2 and AC-5: when a provider becomes ACTIVE its profile (names, licence) is read from
 * provider-service and kept with the directory entry, after the entry exists; a provider that does
 * not become ACTIVE is not read at all.
 */
class ProjectDirectoryProfileTest {

  /** What happened, in order: save:<npi>:<status>, profile-read:<npi>, profile-saved:<npi>. */
  final List<String> log = new ArrayList<>();

  final Map<String, DirectoryEntry> db = new HashMap<>();
  final Map<String, ProviderDetails> provider = new HashMap<>();

  final DirectoryStore store =
      new DirectoryStore() {
        @Override
        public Optional<DirectoryEntry> find(String npi) {
          return Optional.ofNullable(db.get(npi));
        }

        @Override
        public void save(DirectoryEntry entry) {
          log.add("save:" + entry.npi() + ":" + entry.enrollStatus());
          db.put(entry.npi(), entry);
        }

        @Override
        public DirectoryPage published(
            String name, String providerType, String taxonomy, int page, int size) {
          throw new UnsupportedOperationException();
        }
      };

  final ProviderProfiles profiles =
      npi -> {
        log.add("profile-read:" + npi);
        return new ProviderProfile(npi, "MARIA", "RIVERA", null, "MD12345", "TX");
      };

  final List<ProviderProfile> savedProfiles = new ArrayList<>();

  final FhirDirectory fhir =
      new FhirDirectory() {
        @Override
        public void saveProfile(ProviderProfile profile) {
          log.add("profile-saved:" + profile.npi());
          savedProfiles.add(profile);
        }

        @Override
        public Result individuals(String npi, String name, int limit) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Result organizations(String npi, String name, int limit) {
          throw new UnsupportedOperationException();
        }

        @Override
        public Result individualsWithTaxonomy(String taxonomy, int limit) {
          throw new UnsupportedOperationException();
        }
      };

  final ProjectDirectory project = new ProjectDirectory(store, npi -> provider.get(npi));

  ProjectDirectoryProfileTest() {
    project.setProfiles(profiles, fhir);
    provider.put(
        "1003000111",
        new ProviderDetails(
            "1003000111", "M00000111", "IN", "207Q00000X", "MARIA", "RIVERA", null));
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] a provider that becomes ACTIVE has its profile read from provider-service and kept")
  void profileKept() {
    project.statusChanged("1003000111", "A");
    assertThat(savedProfiles)
        .containsExactly(
            new ProviderProfile("1003000111", "MARIA", "RIVERA", null, "MD12345", "TX"));
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] the profile is kept after the entry is saved, so the update finds its row")
  void profileAfterEntry() {
    project.statusChanged("1003000111", "A");
    assertThat(log)
        .containsExactly(
            "save:1003000111:A", "profile-read:1003000111", "profile-saved:1003000111");
  }

  @ParameterizedTest(name = "[MAM-65 AC-5] a change to {0} does not read or keep a profile")
  @ValueSource(strings = {"P", "S", "T", "D"})
  void notPublished(String status) {
    db.put(
        "1003000111",
        new DirectoryEntry(
            "1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", "207Q00000X"));
    project.statusChanged("1003000111", status);
    assertThat(log).containsExactly("save:1003000111:" + status);
    assertThat(savedProfiles).isEmpty();
  }

  @Test
  @DisplayName("[MAM-65 AC-5] without the profile ports the entry is still published")
  void withoutProfilePorts() {
    ProjectDirectory bare = new ProjectDirectory(store, npi -> provider.get(npi));
    bare.statusChanged("1003000111", "A");
    assertThat(db.get("1003000111").enrollStatus()).isEqualTo("A");
    assertThat(savedProfiles).isEmpty();
  }

  @Test
  @DisplayName("[MAM-65 AC-5] reinstatement S to A publishes the provider again with its profile")
  void reinstated() {
    db.put(
        "1003000111",
        new DirectoryEntry(
            "1003000111", "M00000111", "IN", "L", "S", "MARIA RIVERA", "207Q00000X"));
    project.statusChanged("1003000111", "A");
    assertThat(db.get("1003000111").enrollStatus()).isEqualTo("A");
    assertThat(db.get("1003000111").riskTier()).isEqualTo("L");
    assertThat(savedProfiles).hasSize(1);
  }
}
