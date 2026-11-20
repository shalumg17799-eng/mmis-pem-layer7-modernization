package gov.medicaid.pem.directory.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.directory.DirectoryFixtures;
import gov.medicaid.pem.directory.DirectoryFixtures.Row;
import gov.medicaid.pem.directory.IntegrationTest;
import gov.medicaid.pem.directory.application.FhirDirectory;
import gov.medicaid.pem.directory.application.ProviderProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MAM-65: the FHIR view of directory_db.directory_entry on a real Postgres: ACTIVE rows only, the
 * right provider types, name and taxonomy filters, NPI order, the total beside a limit, and the
 * profile columns written when a provider is published.
 */
class JdbcFhirDirectoryTest extends IntegrationTest {

  @Autowired FhirDirectory directory;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void rows() {
    DirectoryFixtures.replaceAll(
        jdbc,
        Row.individual(
            "1003000555", "M00000555", null, "A", "225100000X", "PAT", "SAMPLE", null, null),
        Row.individual(
            "1003000111", "M00000111", "L", "A", "207Q00000X", "MARIA", "RIVERA", "MD12345", "TX"),
        Row.individual(
            "1003000222", "M00000222", "M", "P", "207Q00000X", "MARIO", "RIVERO", null, null),
        Row.organization(
            "1003000333", "M00000333", "FA", "H", "A", "251G00000X", "SUNSET HOSPICE INC"),
        Row.organization(
            "1003000444", "M00000444", "GR", null, "A", "251E00000X", "EXCLUDED CLINIC LLC"),
        Row.organization(
            "1003000666", "M00000666", "FA", "H", "S", "251G00000X", "SAMPLE HOSPICE 09"));
  }

  static List<String> npis(FhirDirectory.Result r) {
    return r.listings().stream().map(l -> l.entry().npi()).toList();
  }

  // ---- individuals ----------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-65 AC-1] individuals are the ACTIVE type IN rows, in ascending NPI order")
  void individualsActiveOnly() {
    FhirDirectory.Result r = directory.individuals(null, null, 200);
    assertThat(npis(r)).containsExactly("1003000111", "1003000555");
    assertThat(r.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-65 AC-1] an exact NPI finds one individual")
  void individualByNpi() {
    FhirDirectory.Result r = directory.individuals("1003000111", null, 200);
    assertThat(npis(r)).containsExactly("1003000111");
  }

  @Test
  @DisplayName("[MAM-65 AC-5] a pending individual is not found, not even by its NPI")
  void pendingNotFound() {
    assertThat(directory.individuals("1003000222", null, 200).total()).isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-3] an organisation's NPI is not found among the individuals")
  void organisationNotAnIndividual() {
    assertThat(directory.individuals("1003000333", null, 200).total()).isZero();
  }

  @ParameterizedTest(
      name =
          "[MAM-65 AC-2] the name text <{0}> finds the individual by last or first name, in any case")
  @ValueSource(strings = {"RIVERA", "rivera", "Riv", "ARIA", "maria", "iver"})
  void individualNameMatches(String text) {
    assertThat(npis(directory.individuals(null, text, 200))).containsExactly("1003000111");
  }

  @Test
  @DisplayName("[MAM-65 AC-2] a name text that is only blanks is no filter")
  void blankNameNoFilter() {
    assertThat(directory.individuals(null, "   ", 200).total()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-65 AC-2] the NPI and the name filters both apply")
  void npiAndName() {
    assertThat(directory.individuals("1003000111", "SAMPLE", 200).total()).isZero();
    assertThat(directory.individuals("1003000111", "RIVERA", 200).total()).isEqualTo(1);
  }

  @ParameterizedTest(
      name = "[MAM-65 AC-2] the wildcard text <{0}> is taken literally and matches nothing")
  @ValueSource(strings = {"%", "_", "R_VERA", "RIV%", "\\"})
  void wildcardsAreLiteral(String text) {
    assertThat(directory.individuals(null, text, 200).total()).isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-1] the total counts every match while the limit cuts the entries")
  void limitAndTotal() {
    FhirDirectory.Result r = directory.individuals(null, null, 1);
    assertThat(npis(r)).containsExactly("1003000111");
    assertThat(r.total()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] a listing carries the entry and the profile: names, licence and state")
  void listingHasProfile() {
    FhirDirectory.Listing l = directory.individuals("1003000111", null, 200).listings().get(0);
    assertThat(l.entry().medicaidId()).isEqualTo("M00000111");
    assertThat(l.entry().providerType()).isEqualTo("IN");
    assertThat(l.entry().riskTier()).isEqualTo("L");
    assertThat(l.entry().taxonomy()).isEqualTo("207Q00000X");
    assertThat(l.entry().displayName()).isEqualTo("MARIA RIVERA");
    assertThat(l.profile())
        .isEqualTo(new ProviderProfile("1003000111", "MARIA", "RIVERA", null, "MD12345", "TX"));
  }

  // ---- organizations --------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-65 AC-3] organisations are the ACTIVE groups and facilities, in NPI order")
  void organizationsActiveOnly() {
    FhirDirectory.Result r = directory.organizations(null, null, 200);
    assertThat(npis(r)).containsExactly("1003000333", "1003000444");
    assertThat(r.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-65 AC-3] the name filter matches the legal name in any case")
  void organizationName() {
    assertThat(npis(directory.organizations(null, "hospice", 200))).containsExactly("1003000333");
  }

  @Test
  @DisplayName("[MAM-65 AC-3] a row without a legal name is matched on its display name")
  void organizationDisplayNameFallback() {
    jdbc.update("UPDATE directory_entry SET legal_name = NULL WHERE npi = '1003000444'");
    assertThat(npis(directory.organizations(null, "EXCLUDED", 200))).containsExactly("1003000444");
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-3] an exact NPI finds the facility, and the individual NPI finds nothing")
  void organizationByNpi() {
    assertThat(npis(directory.organizations("1003000333", null, 200)))
        .containsExactly("1003000333");
    assertThat(directory.organizations("1003000111", null, 200).total()).isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-5] a suspended facility is not found")
  void suspendedFacility() {
    assertThat(directory.organizations("1003000666", null, 200).total()).isZero();
    assertThat(directory.organizations(null, "SAMPLE HOSPICE", 200).total()).isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-3] the legal name is on the profile of an organisation")
  void organizationProfile() {
    FhirDirectory.Listing l = directory.organizations("1003000333", null, 200).listings().get(0);
    assertThat(l.profile().legalName()).isEqualTo("SUNSET HOSPICE INC");
    assertThat(l.entry().providerType()).isEqualTo("FA");
  }

  // ---- taxonomy -------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-65 AC-4] a taxonomy finds the ACTIVE individuals that have it")
  void taxonomy() {
    assertThat(npis(directory.individualsWithTaxonomy("207Q00000X", 200)))
        .containsExactly("1003000111");
  }

  @Test
  @DisplayName("[MAM-65 AC-4] a taxonomy is exact, not a prefix")
  void taxonomyExact() {
    assertThat(directory.individualsWithTaxonomy("207Q", 200).total()).isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-4] an organisation's taxonomy gives no individual role")
  void organisationTaxonomy() {
    assertThat(directory.individualsWithTaxonomy("251G00000X", 200).total()).isZero();
  }

  @Test
  @DisplayName("[MAM-65 AC-4] without a taxonomy every ACTIVE individual has a role, in NPI order")
  void noTaxonomy() {
    FhirDirectory.Result r = directory.individualsWithTaxonomy(null, 200);
    assertThat(npis(r)).containsExactly("1003000111", "1003000555");
    assertThat(r.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-65 AC-4] the limit cuts the roles and the total stays")
  void taxonomyLimit() {
    FhirDirectory.Result r = directory.individualsWithTaxonomy(null, 1);
    assertThat(r.listings()).hasSize(1);
    assertThat(r.total()).isEqualTo(2);
  }

  // ---- profile --------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-65 AC-2] saveProfile keeps names and licence on the existing row, only that row")
  void saveProfile() {
    directory.saveProfile(
        new ProviderProfile("1003000555", "PATRICIA", "SAMPLER", null, "LIC999", "SD"));
    ProviderProfile saved =
        directory.individuals("1003000555", null, 200).listings().get(0).profile();
    assertThat(saved)
        .isEqualTo(new ProviderProfile("1003000555", "PATRICIA", "SAMPLER", null, "LIC999", "SD"));
    ProviderProfile other =
        directory.individuals("1003000111", null, 200).listings().get(0).profile();
    assertThat(other.lastName()).isEqualTo("RIVERA");
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] saveProfile cuts values to the column sizes: first 20, last 30, legal 60, licence 15, state 2")
  void saveProfileCuts() {
    directory.saveProfile(
        new ProviderProfile(
            "1003000555", "F".repeat(25), "L".repeat(35), "G".repeat(65), "N".repeat(20), "TXX"));
    ProviderProfile saved =
        directory.individuals("1003000555", null, 200).listings().get(0).profile();
    assertThat(saved.firstName()).isEqualTo("F".repeat(20));
    assertThat(saved.lastName()).isEqualTo("L".repeat(30));
    assertThat(saved.legalName()).isEqualTo("G".repeat(60));
    assertThat(saved.licenseNo()).isEqualTo("N".repeat(15));
    assertThat(saved.licenseState()).isEqualTo("TX");
  }

  @Test
  @DisplayName("[MAM-65 AC-2] saveProfile with no values clears them")
  void saveProfileNulls() {
    directory.saveProfile(new ProviderProfile("1003000111", null, null, null, null, null));
    ProviderProfile saved =
        directory.individuals("1003000111", null, 200).listings().get(0).profile();
    assertThat(saved.firstName()).isNull();
    assertThat(saved.lastName()).isNull();
    assertThat(saved.licenseNo()).isNull();
    assertThat(saved.licenseState()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-65 AC-2] saveProfile for an NPI the directory does not have changes nothing and does not fail")
  void saveProfileUnknownNpi() {
    directory.saveProfile(new ProviderProfile("1009999999", "A", "B", null, null, null));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM directory_entry", Integer.class))
        .isEqualTo(6);
  }
}
