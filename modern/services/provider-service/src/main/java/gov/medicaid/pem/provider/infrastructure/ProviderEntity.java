package gov.medicaid.pem.provider.infrastructure;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * JPA mapping of the {@code provider} table (Flyway V1). Codes are stored as the legacy
 * one/two-character values.
 */
@Entity
@Table(name = "provider")
public class ProviderEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  UUID publicId;

  String npi;

  @Column(name = "medicaid_id")
  String medicaidId;

  @Column(name = "provider_type")
  String providerType;

  String taxonomy;

  @Column(name = "risk_tier")
  String riskTier;

  @Column(name = "enroll_status")
  String enrollStatus;

  @Column(name = "screen_flag")
  String screenFlag;

  @Column(name = "effective_date")
  LocalDate effectiveDate;

  @Column(name = "end_date")
  LocalDate endDate;

  @Column(name = "reval_due_date")
  LocalDate revalDueDate;

  @Column(name = "last_name")
  String lastName;

  @Column(name = "first_name")
  String firstName;

  @Column(name = "license_no")
  String licenseNo;

  @Column(name = "license_state")
  String licenseState;

  @Column(name = "legal_name")
  String legalName;

  @Column(name = "dba_name")
  String dbaName;

  @Column(name = "owner_npi")
  String ownerNpi;

  @Column(name = "created_at", insertable = false, updatable = false)
  Instant createdAt;

  @Column(name = "updated_at")
  Instant updatedAt;

  @Version Integer version;
}
