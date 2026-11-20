package gov.medicaid.pem.directory.infrastructure;

import gov.medicaid.pem.directory.application.DirectoryEntry;
import gov.medicaid.pem.directory.application.FhirDirectory;
import gov.medicaid.pem.directory.application.ProviderProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The FHIR view of directory_db.directory_entry (MAM-65): the profile columns (names, legal name,
 * licence) of the existing read model, ACTIVE rows only, ordered by NPI.
 */
@Repository
public class JdbcFhirDirectory implements FhirDirectory {

  private static final String COLUMNS =
      "npi, medicaid_id, provider_type, risk_tier, enroll_status, display_name, taxonomy,"
          + " first_name, last_name, legal_name, license_no, license_state";

  private final JdbcTemplate jdbc;

  public JdbcFhirDirectory(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void saveProfile(ProviderProfile p) {
    jdbc.update(
        "UPDATE directory_entry SET first_name = ?, last_name = ?, legal_name = ?, license_no = ?,"
            + " license_state = ?, updated_at = now() WHERE npi = ?",
        cut(p.firstName(), 20),
        cut(p.lastName(), 30),
        cut(p.legalName(), 60),
        cut(p.licenseNo(), 15),
        cut(p.licenseState(), 2),
        p.npi());
  }

  @Override
  public Result individuals(String npi, String name, int limit) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder(" WHERE enroll_status = 'A' AND provider_type = 'IN'");
    npi(where, args, npi);
    if (name != null && !name.isBlank()) {
      where.append(" AND (last_name ILIKE ? OR first_name ILIKE ?)");
      String like = LikePattern.contains(name.trim());
      args.add(like);
      args.add(like);
    }
    return query(where, args, limit);
  }

  @Override
  public Result organizations(String npi, String name, int limit) {
    List<Object> args = new ArrayList<>();
    StringBuilder where =
        new StringBuilder(" WHERE enroll_status = 'A' AND provider_type IN ('GR', 'FA')");
    npi(where, args, npi);
    if (name != null && !name.isBlank()) {
      where.append(" AND coalesce(legal_name, display_name) ILIKE ?");
      args.add(LikePattern.contains(name.trim()));
    }
    return query(where, args, limit);
  }

  @Override
  public Result individualsWithTaxonomy(String taxonomy, int limit) {
    List<Object> args = new ArrayList<>();
    StringBuilder where = new StringBuilder(" WHERE enroll_status = 'A' AND provider_type = 'IN'");
    if (taxonomy != null) {
      where.append(" AND taxonomy = ?");
      args.add(taxonomy);
    }
    return query(where, args, limit);
  }

  private static void npi(StringBuilder where, List<Object> args, String npi) {
    if (npi != null) {
      where.append(" AND npi = ?");
      args.add(npi);
    }
  }

  private Result query(StringBuilder where, List<Object> args, int limit) {
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM directory_entry" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(limit);
    List<Listing> rows =
        jdbc.query(
            "SELECT " + COLUMNS + " FROM directory_entry" + where + " ORDER BY npi LIMIT ?",
            JdbcFhirDirectory::row,
            pageArgs.toArray());
    return new Result(rows, total == null ? 0 : total);
  }

  private static Listing row(ResultSet rs, int n) throws SQLException {
    String npi = trim(rs.getString("npi"));
    return new Listing(
        new DirectoryEntry(
            npi,
            trim(rs.getString("medicaid_id")),
            trim(rs.getString("provider_type")),
            trim(rs.getString("risk_tier")),
            trim(rs.getString("enroll_status")),
            rs.getString("display_name"),
            trim(rs.getString("taxonomy"))),
        new ProviderProfile(
            npi,
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("legal_name"),
            rs.getString("license_no"),
            trim(rs.getString("license_state"))));
  }

  private static String cut(String s, int max) {
    return s == null || s.length() <= max ? s : s.substring(0, max);
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
