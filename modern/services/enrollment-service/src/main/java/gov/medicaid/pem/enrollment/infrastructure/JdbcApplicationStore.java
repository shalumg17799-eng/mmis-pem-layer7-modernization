package gov.medicaid.pem.enrollment.infrastructure;

import gov.medicaid.pem.enrollment.application.ApplicationPage;
import gov.medicaid.pem.enrollment.application.ApplicationQuery;
import gov.medicaid.pem.enrollment.application.ApplicationStore;
import gov.medicaid.pem.enrollment.application.NewApplication;
import gov.medicaid.pem.enrollment.application.OwnedApplication;
import gov.medicaid.pem.enrollment.application.StoredApplication;
import gov.medicaid.pem.enrollment.domain.ErrorCode;
import gov.medicaid.pem.enrollment.domain.ValidationError;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * enrollment_db through plain JDBC: application, application_error and provider_type_mapping (V1
 * data model).
 */
@Repository
public class JdbcApplicationStore
    implements ApplicationStore, gov.medicaid.pem.enrollment.application.ApplicationReader {

  private static final String COLUMNS =
      "id, public_id, npi, medicaid_id, provider_type, taxonomy, last_name, first_name, license_no, license_state,"
          + " legal_name, dba_name, owner_npi, status, source, submitted_at";

  private final JdbcTemplate jdbc;

  public JdbcApplicationStore(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean hasValidated(String npi) {
    Integer n =
        jdbc.queryForObject(
            "SELECT count(*) FROM application WHERE npi = ? AND status = 'VALIDATED'",
            Integer.class,
            npi);
    return n != null && n > 0;
  }

  @Override
  public Map<String, String> providerTypeMappings() {
    Map<String, String> out = new HashMap<>();
    jdbc.query(
        "SELECT feed_code, legacy_code FROM provider_type_mapping",
        rs -> {
          out.put(rs.getString(1).trim(), rs.getString(2).trim());
        });
    return out;
  }

  @Override
  public void save(StoredApplication a, String submittedBy) {
    NewApplication.Individual ind = a.individual();
    NewApplication.Organization org = a.organization();
    Long id =
        jdbc.queryForObject(
            "INSERT INTO application (public_id, npi, medicaid_id, provider_type, taxonomy, last_name, first_name,"
                + " license_no, license_state, legal_name, dba_name, owner_npi, status, source, submitted_by,"
                + " submitted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id",
            Long.class,
            a.applicationId(),
            fits(a.npi(), 10),
            a.medicaidId(),
            a.providerType(),
            a.taxonomy(),
            ind == null ? null : ind.lastName(),
            ind == null ? null : ind.firstName(),
            ind == null ? null : ind.licenseNo(),
            ind == null ? null : ind.licenseState(),
            org == null ? null : org.legalName(),
            org == null ? null : org.dbaName(),
            org == null ? null : org.ownerNpi(),
            a.status(),
            a.source(),
            submittedBy,
            Timestamp.from(a.submittedAt()));
    for (ValidationError e : a.errors()) {
      jdbc.update(
          "INSERT INTO application_error (application_id, code, field, message) VALUES (?, ?, ?, ?)",
          id,
          e.code().name(),
          e.field(),
          e.code().message());
    }
  }

  /**
   * A value longer than its column is not a valid NPI anyway (E101); it is kept out rather than
   * cut.
   */
  private static String fits(String value, int length) {
    return value == null || value.length() > length ? null : value;
  }

  @Override
  public ApplicationPage find(ApplicationQuery q) {
    StringBuilder where = new StringBuilder(" WHERE 1 = 1");
    List<Object> args = new ArrayList<>();
    if (q.status() != null) {
      where.append(" AND status = ?");
      args.add(q.status());
    }
    if (q.npi() != null) {
      where.append(" AND npi = ?");
      args.add(q.npi());
    }
    if (q.submittedBy() != null) {
      where.append(" AND (submitted_by = ?");
      args.add(q.submittedBy());
      if (q.ownNpi() != null) {
        where.append(" OR npi = ?");
        args.add(q.ownNpi());
      }
      where.append(")");
    }
    Long total =
        jdbc.queryForObject("SELECT count(*) FROM application" + where, Long.class, args.toArray());
    List<Object> pageArgs = new ArrayList<>(args);
    pageArgs.add(q.size());
    pageArgs.add((long) q.page() * q.size());
    Map<Long, Row> rows = new LinkedHashMap<>();
    jdbc.query(
        "SELECT "
            + COLUMNS
            + " FROM application"
            + where
            + " ORDER BY submitted_at DESC, id DESC LIMIT ? OFFSET ?",
        rs -> {
          rows.put(rs.getLong("id"), row(rs));
        },
        pageArgs.toArray());
    Map<Long, List<ValidationError>> errors = errorsOf(rows.keySet());
    List<StoredApplication> items =
        rows.entrySet().stream()
            .map(e -> e.getValue().toApplication(errors.getOrDefault(e.getKey(), List.of())))
            .toList();
    return new ApplicationPage(items, q.page(), q.size(), total == null ? 0 : total);
  }

  private Map<Long, List<ValidationError>> errorsOf(java.util.Set<Long> ids) {
    Map<Long, List<ValidationError>> out = new HashMap<>();
    if (ids.isEmpty()) {
      return out;
    }
    String marks = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
    jdbc.query(
        "SELECT application_id, code, field FROM application_error WHERE application_id IN ("
            + marks
            + ") ORDER BY id",
        rs -> {
          out.computeIfAbsent(rs.getLong(1), k -> new ArrayList<>())
              .add(new ValidationError(ErrorCode.valueOf(rs.getString(2).trim()), rs.getString(3)));
        },
        ids.toArray());
    return out;
  }

  private record Row(
      UUID publicId,
      String npi,
      String medicaidId,
      String providerType,
      String taxonomy,
      NewApplication.Individual individual,
      NewApplication.Organization organization,
      String status,
      String source,
      java.time.Instant submittedAt) {

    StoredApplication toApplication(List<ValidationError> errors) {
      return new StoredApplication(
          publicId,
          status,
          errors,
          npi,
          medicaidId,
          providerType,
          taxonomy,
          individual,
          organization,
          source,
          submittedAt);
    }
  }

  private static Row row(ResultSet rs) throws SQLException {
    String last = trim(rs.getString("last_name"));
    String first = trim(rs.getString("first_name"));
    String licence = trim(rs.getString("license_no"));
    String state = trim(rs.getString("license_state"));
    String legal = trim(rs.getString("legal_name"));
    String dba = trim(rs.getString("dba_name"));
    String owner = trim(rs.getString("owner_npi"));
    return new Row(
        rs.getObject("public_id", UUID.class),
        npiOf(rs.getString("npi")),
        trim(rs.getString("medicaid_id")),
        trim(rs.getString("provider_type")),
        trim(rs.getString("taxonomy")),
        last == null && first == null && licence == null && state == null
            ? null
            : new NewApplication.Individual(last, first, licence, state),
        legal == null && dba == null && owner == null
            ? null
            : new NewApplication.Organization(legal, dba, owner),
        rs.getString("status"),
        rs.getString("source"),
        rs.getTimestamp("submitted_at").toInstant());
  }

  /**
   * The contract requires an npi on every application; one that was missing or too long is shown as
   * empty.
   */
  private static String npiOf(String value) {
    String v = trim(value);
    return v == null ? "" : v;
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  @Override
  public Optional<OwnedApplication> byId(UUID applicationId) {
    List<Object[]> found =
        jdbc.query(
            "SELECT " + COLUMNS + ", submitted_by FROM application WHERE public_id = ?",
            (rs, n) -> new Object[] {rs.getLong("id"), row(rs), rs.getString("submitted_by")},
            applicationId);
    if (found.isEmpty()) {
      return Optional.empty();
    }
    long id = (Long) found.get(0)[0];
    List<ValidationError> errors = errorsOf(java.util.Set.of(id)).getOrDefault(id, List.of());
    return Optional.of(
        new OwnedApplication(
            ((Row) found.get(0)[1]).toApplication(errors), (String) found.get(0)[2]));
  }
}
