package gov.medicaid.pem.directory;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Synthetic rows of directory_db.directory_entry for the MAM-65 and MAM-67 tests (fabricated NPIs,
 * names and licences; no real provider data).
 */
public final class DirectoryFixtures {

  private DirectoryFixtures() {}

  /** One directory_entry row. A blank value is stored as null. */
  public record Row(
      String npi,
      String medicaidId,
      String providerType,
      String riskTier,
      String enrollStatus,
      String displayName,
      String taxonomy,
      String lastName,
      String firstName,
      String legalName,
      String licenseNo,
      String licenseState) {

    /** An individual (type IN): the display name is first name + last name (D-028). */
    public static Row individual(
        String npi,
        String medicaidId,
        String tier,
        String status,
        String taxonomy,
        String first,
        String last,
        String licenseNo,
        String licenseState) {
      return new Row(
          npi,
          medicaidId,
          "IN",
          tier,
          status,
          (first + " " + last).trim(),
          taxonomy,
          last,
          first,
          null,
          licenseNo,
          licenseState);
    }

    /** A group (GR) or facility (FA): the display name is the legal name (D-028). */
    public static Row organization(
        String npi,
        String medicaidId,
        String type,
        String tier,
        String status,
        String taxonomy,
        String legalName) {
      return new Row(
          npi,
          medicaidId,
          type,
          tier,
          status,
          legalName,
          taxonomy,
          null,
          null,
          legalName,
          null,
          null);
    }

    public Row withStatus(String status) {
      return new Row(
          npi,
          medicaidId,
          providerType,
          riskTier,
          status,
          displayName,
          taxonomy,
          lastName,
          firstName,
          legalName,
          licenseNo,
          licenseState);
    }
  }

  public static void clear(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM directory_entry");
  }

  public static void insert(JdbcTemplate jdbc, Row r) {
    jdbc.update(
        "INSERT INTO directory_entry (npi, medicaid_id, provider_type, risk_tier, enroll_status,"
            + " display_name, taxonomy, last_name, first_name, legal_name, license_no,"
            + " license_state) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        blankToNull(r.npi()),
        blankToNull(r.medicaidId()),
        blankToNull(r.providerType()),
        blankToNull(r.riskTier()),
        blankToNull(r.enrollStatus()),
        r.displayName() == null ? "" : r.displayName(),
        blankToNull(r.taxonomy()),
        blankToNull(r.lastName()),
        blankToNull(r.firstName()),
        blankToNull(r.legalName()),
        blankToNull(r.licenseNo()),
        blankToNull(r.licenseState()));
  }

  /** Empties the directory, then inserts the rows in the order given (not NPI order). */
  public static void replaceAll(JdbcTemplate jdbc, Row... rows) {
    clear(jdbc);
    for (Row r : rows) {
      insert(jdbc, r);
    }
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
