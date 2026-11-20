package gov.medicaid.pem.provider;

import java.sql.Date;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Provider rows for the integration tests of the search, history and export stories (synthetic data
 * only), written straight into provider_db with the column values of the acceptance criteria.
 */
public final class ProviderRows {

  private ProviderRows() {}

  /** Removes every provider, owner and history row, so that a test can count the whole table. */
  public static void wipe(JdbcTemplate jdbc) {
    jdbc.update("DELETE FROM provider_owner");
    jdbc.update("DELETE FROM provider");
    jdbc.update("DELETE FROM audit_log");
  }

  /** An ACTIVE individual with screen flag P, as in the examples of the stories. */
  public static Builder individual(String npi) {
    return new Builder(npi).type("IN");
  }

  /** A PENDING group with an owner (type GR). */
  public static Builder group(String npi) {
    return new Builder(npi)
        .type("GR")
        .status("P")
        .flag("U")
        .legalName("SAMPLE CLINIC GROUP")
        .dbaName("SAMPLE CLINIC")
        .ownerNpi("1990000111");
  }

  /** Builder of one provider row. */
  public static final class Builder {
    private final String npi;
    private String medicaidId;
    private String type = "IN";
    private String taxonomy = "207Q00000X";
    private String riskTier;
    private String status = "A";
    private String flag = "P";
    private LocalDate effective = LocalDate.of(2026, 11, 18);
    private LocalDate end;
    private LocalDate due;
    private String lastName;
    private String firstName;
    private String licenseNo;
    private String licenseState;
    private String legalName;
    private String dbaName;
    private String ownerNpi;

    Builder(String npi) {
      this.npi = npi;
      this.medicaidId = "MCD" + npi.substring(4);
      this.lastName = "RIVERA";
      this.firstName = "MARIA";
      this.licenseNo = "MD12345";
      this.licenseState = "TX";
    }

    public Builder medicaidId(String v) {
      medicaidId = v;
      return this;
    }

    public Builder type(String v) {
      type = v;
      return this;
    }

    public Builder taxonomy(String v) {
      taxonomy = v;
      return this;
    }

    public Builder riskTier(String v) {
      riskTier = v == null || v.isBlank() ? null : v;
      return this;
    }

    public Builder status(String v) {
      status = v;
      return this;
    }

    public Builder flag(String v) {
      flag = v;
      return this;
    }

    public Builder effective(LocalDate v) {
      effective = v;
      return this;
    }

    public Builder end(LocalDate v) {
      end = v;
      return this;
    }

    public Builder due(LocalDate v) {
      due = v;
      return this;
    }

    public Builder name(String last, String first) {
      lastName = last;
      firstName = first;
      return this;
    }

    public Builder licence(String no, String state) {
      licenseNo = no;
      licenseState = state;
      return this;
    }

    public Builder legalName(String v) {
      legalName = v;
      return this;
    }

    public Builder dbaName(String v) {
      dbaName = v;
      return this;
    }

    public Builder ownerNpi(String v) {
      ownerNpi = v;
      return this;
    }

    /**
     * Only the view of the type is stored: names and licence for IN, the organisation for GR/FA.
     */
    public Builder insert(JdbcTemplate jdbc) {
      boolean in = "IN".equals(type);
      jdbc.update(
          "INSERT INTO provider (public_id, npi, medicaid_id, provider_type, taxonomy, risk_tier,"
              + " enroll_status, screen_flag, effective_date, end_date, reval_due_date, last_name,"
              + " first_name, license_no, license_state, legal_name, dba_name, owner_npi)"
              + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          npi,
          medicaidId,
          type,
          taxonomy,
          riskTier,
          status,
          flag,
          sql(effective),
          sql(end),
          sql(due),
          in ? lastName : null,
          in ? firstName : null,
          in ? licenseNo : null,
          in ? licenseState : null,
          in ? null : legalName,
          in ? null : dbaName,
          in ? null : ownerNpi);
      return this;
    }

    private static Date sql(LocalDate d) {
      return d == null ? null : Date.valueOf(d);
    }
  }
}
