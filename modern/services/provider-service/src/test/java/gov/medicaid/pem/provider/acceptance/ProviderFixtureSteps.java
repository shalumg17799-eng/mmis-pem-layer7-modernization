package gov.medicaid.pem.provider.acceptance;

import gov.medicaid.pem.provider.ProviderRows;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Providers for the scenarios of MAM-37, MAM-40 and MAM-43: the table of the story is written into
 * provider_db exactly as given (synthetic data only).
 */
public class ProviderFixtureSteps {

  @Autowired private JdbcTemplate jdbc;

  /**
   * Replaces the whole provider table (and the history) with the rows of the table. Columns: npi,
   * medicaidId, providerType, taxonomy, riskTier, enrollStatus, screenFlag, effectiveDate, endDate,
   * revalidationDueDate, lastName, firstName, licenseNo, licenseState, legalName, dbaName,
   * ownerNpi. A column that is not in the table keeps the default of {@link ProviderRows}; an empty
   * cell is no value.
   */
  @Given("only these providers are stored:")
  public void onlyTheseProviders(DataTable table) {
    ProviderRows.wipe(jdbc);
    for (Map<String, String> row : table.asMaps()) {
      ProviderRows.Builder b = ProviderRows.individual(row.get("npi"));
      if (row.containsKey("medicaidId")) {
        b.medicaidId(blank(row.get("medicaidId")));
      }
      if (row.containsKey("providerType")) {
        b.type(blank(row.get("providerType")));
      }
      if (row.containsKey("taxonomy")) {
        b.taxonomy(blank(row.get("taxonomy")));
      }
      if (row.containsKey("riskTier")) {
        b.riskTier(blank(row.get("riskTier")));
      }
      if (row.containsKey("enrollStatus")) {
        b.status(blank(row.get("enrollStatus")));
      }
      if (row.containsKey("screenFlag")) {
        b.flag(blank(row.get("screenFlag")));
      }
      if (row.containsKey("effectiveDate")) {
        b.effective(date(row.get("effectiveDate")));
      }
      if (row.containsKey("endDate")) {
        b.end(date(row.get("endDate")));
      }
      if (row.containsKey("revalidationDueDate")) {
        b.due(date(row.get("revalidationDueDate")));
      }
      if (row.containsKey("lastName") || row.containsKey("firstName")) {
        b.name(blank(row.get("lastName")), blank(row.get("firstName")));
      }
      if (row.containsKey("licenseNo") || row.containsKey("licenseState")) {
        b.licence(blank(row.get("licenseNo")), blank(row.get("licenseState")));
      }
      if (row.containsKey("legalName")) {
        b.legalName(blank(row.get("legalName")));
      }
      if (row.containsKey("dbaName")) {
        b.dbaName(blank(row.get("dbaName")));
      }
      if (row.containsKey("ownerNpi")) {
        b.ownerNpi(blank(row.get("ownerNpi")));
      }
      b.insert(jdbc);
    }
  }

  /** Adds filler individuals with NPIs 1004000001 upwards, named FILLER01 upwards. */
  @Given("{int} further providers are stored with status {string} and screen flag {string}")
  public void furtherProviders(int count, String status, String flag) {
    for (int i = 1; i <= count; i++) {
      ProviderRows.individual(String.format("1004%06d", i))
          .medicaidId(String.format("MCDF%05d", i))
          .name(String.format("FILLER%02d", i), "TESTFIRST")
          .status(status)
          .flag(flag)
          .insert(jdbc);
    }
  }

  private static String blank(String v) {
    return v == null || v.isBlank() ? null : v;
  }

  private static LocalDate date(String v) {
    return blank(v) == null ? null : LocalDate.parse(v);
  }
}
