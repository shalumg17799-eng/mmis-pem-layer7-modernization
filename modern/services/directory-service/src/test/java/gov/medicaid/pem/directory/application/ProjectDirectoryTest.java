package gov.medicaid.pem.directory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-61: the directory read model follows the provider events and reads provider details only when
 * it must.
 */
class ProjectDirectoryTest {

  final Map<String, DirectoryEntry> db = new HashMap<>();
  final List<String> asked = new ArrayList<>();
  final Map<String, ProviderDetails> provider = new HashMap<>();

  final DirectoryStore store =
      new DirectoryStore() {
        @Override
        public Optional<DirectoryEntry> find(String npi) {
          return Optional.ofNullable(db.get(npi));
        }

        @Override
        public void save(DirectoryEntry entry) {
          db.put(entry.npi(), entry);
        }

        @Override
        public DirectoryPage published(
            String name, String providerType, String taxonomy, int page, int size) {
          throw new UnsupportedOperationException();
        }
      };

  final ProjectDirectory project =
      new ProjectDirectory(
          store,
          npi -> {
            asked.add(npi);
            return provider.get(npi);
          });

  @Test
  @DisplayName("[MAM-61 AC-1] a registration is kept as PENDING, without asking provider-service")
  void registered() {
    project.registered("1003000111", "M00000111", "IN", "207Q00000X", "MARIA RIVERA");
    assertThat(db.get("1003000111"))
        .isEqualTo(
            new DirectoryEntry(
                "1003000111", "M00000111", "IN", null, "P", "MARIA RIVERA", "207Q00000X"));
    assertThat(asked).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-61 AC-2] an approval reads the provider, composes the name and keeps the tier already received")
  void approved() {
    project.registered("1003000111", "M00000111", "IN", "207Q00000X", "MARIA RIVERA");
    project.tierAssigned("1003000111", "L");
    provider.put(
        "1003000111",
        new ProviderDetails(
            "1003000111", "M00000111", "IN", "207Q00000X", "MARIA", "RIVERA", null));
    project.statusChanged("1003000111", "A");
    assertThat(db.get("1003000111"))
        .isEqualTo(
            new DirectoryEntry(
                "1003000111", "M00000111", "IN", "L", "A", "MARIA RIVERA", "207Q00000X"));
    assertThat(asked).containsExactly("1003000111");
  }

  @Test
  @DisplayName("[MAM-61 AC-4] a suspension keeps the entry but withdraws it (status S)")
  void suspended() {
    db.put(
        "1003000333",
        new DirectoryEntry(
            "1003000333", "M00000333", "FA", "H", "A", "SUNSET HOSPICE INC", "251G00000X"));
    project.statusChanged("1003000333", "S");
    assertThat(db.get("1003000333").enrollStatus()).isEqualTo("S");
    assertThat(db.get("1003000333").riskTier()).isEqualTo("H");
    assertThat(asked).isEmpty();
  }

  @Test
  @DisplayName("[MAM-61 AC-4] a denial of a provider the directory never saw changes nothing")
  void deniedUnknown() {
    project.statusChanged("1003000444", "D");
    assertThat(db).isEmpty();
    assertThat(asked).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-61 AC-5] a tier for a provider the directory has not seen yet is kept, from provider-service details")
  void tierFirst() {
    provider.put(
        "1003000333",
        new ProviderDetails(
            "1003000333", "M00000333", "FA", "251G00000X", null, null, "SUNSET HOSPICE INC"));
    project.tierAssigned("1003000333", "H");
    assertThat(db.get("1003000333").riskTier()).isEqualTo("H");
    assertThat(db.get("1003000333").displayName()).isEqualTo("SUNSET HOSPICE INC");
    assertThat(db.get("1003000333").enrollStatus()).isEqualTo("P");
  }
}
