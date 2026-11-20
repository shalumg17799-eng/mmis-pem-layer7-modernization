package gov.medicaid.pem.directory.infrastructure;

import gov.medicaid.pem.directory.application.DirectoryEntry;
import gov.medicaid.pem.directory.application.DirectoryPage;
import gov.medicaid.pem.directory.application.DirectoryStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** directory_db.directory_entry: one row per NPI, upserted by the projection. */
@Repository
public class JdbcDirectoryStore implements DirectoryStore {

  private static final String COLUMNS =
      "npi, medicaid_id, provider_type, risk_tier, enroll_status, display_name, taxonomy";

  private final JdbcTemplate jdbc;

  public JdbcDirectoryStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<DirectoryEntry> find(String npi) {
    return jdbc
        .query(
            "SELECT " + COLUMNS + " FROM directory_entry WHERE npi = ?",
            JdbcDirectoryStore::row,
            npi)
        .stream()
        .findFirst();
  }

  @Override
  public void save(DirectoryEntry e) {
    jdbc.update(
        "INSERT INTO directory_entry ("
            + COLUMNS
            + ") VALUES (?, ?, ?, ?, ?, ?, ?)"
            + " ON CONFLICT (npi) DO UPDATE SET medicaid_id = EXCLUDED.medicaid_id,"
            + " provider_type = EXCLUDED.provider_type, risk_tier = EXCLUDED.risk_tier,"
            + " enroll_status = EXCLUDED.enroll_status, display_name = EXCLUDED.display_name,"
            + " taxonomy = EXCLUDED.taxonomy, updated_at = now(), version = directory_entry.version + 1",
        e.npi(),
        e.medicaidId(),
        e.providerType(),
        e.riskTier(),
        e.enrollStatus(),
        e.displayName(),
        e.taxonomy());
  }

  @Override
  public DirectoryPage published(
      String name, String providerType, String taxonomy, int page, int size) {
    StringBuilder where = new StringBuilder(" WHERE enroll_status = 'A'");
    List<Object> args = new ArrayList<>();
    if (name != null && !name.isBlank()) {
      where.append(" AND display_name ILIKE ? ESCAPE '\\'");
      args.add(LikePattern.contains(name.trim()));
    }
    if (providerType != null) {
      where.append(" AND provider_type = ?");
      args.add(providerType);
    }
    if (taxonomy != null) {
      where.append(" AND taxonomy = ?");
      args.add(taxonomy);
    }
    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM directory_entry" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(size);
    pageArgs.add((long) page * size);
    List<DirectoryEntry> items =
        jdbc.query(
            "SELECT "
                + COLUMNS
                + " FROM directory_entry"
                + where
                + " ORDER BY npi LIMIT ? OFFSET ?",
            JdbcDirectoryStore::row,
            pageArgs.toArray());
    return new DirectoryPage(items, page, size, total == null ? 0 : total);
  }

  private static DirectoryEntry row(ResultSet rs, int n) throws SQLException {
    return new DirectoryEntry(
        trim(rs.getString("npi")),
        trim(rs.getString("medicaid_id")),
        trim(rs.getString("provider_type")),
        trim(rs.getString("risk_tier")),
        trim(rs.getString("enroll_status")),
        rs.getString("display_name"),
        trim(rs.getString("taxonomy")));
  }

  private static String trim(String s) {
    return s == null ? null : s.trim();
  }
}
