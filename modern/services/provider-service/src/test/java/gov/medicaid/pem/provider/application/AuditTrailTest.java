package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

/**
 * MAM-40: the shape of a history entry. before and after hold the provider fields that a change can
 * touch (as the codes of the record), plus one named part for the request details.
 */
class AuditTrailTest {

  static Provider provider(RiskTier tier) {
    return new Provider(
        "1003000111",
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        tier,
        EnrollStatus.ACTIVE,
        ScreenFlag.PASS,
        LocalDate.of(2026, 11, 18),
        null,
        LocalDate.of(2031, 11, 18),
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        4);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] a missing provider has no snapshot: the registration entry has before null")
  void noSnapshotWithoutProvider() {
    assertThat(AuditTrail.snapshot(null)).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] a snapshot holds exactly the 11 record fields, in order, as legacy codes")
  void snapshotFields() {
    assertThat(AuditTrail.snapshot(provider(RiskTier.LIMITED)))
        .containsExactly(
            entry("npi", "1003000111"),
            entry("medicaidId", "MCD000111"),
            entry("providerType", "IN"),
            entry("taxonomy", "207Q00000X"),
            entry("riskTier", "L"),
            entry("enrollStatus", "A"),
            entry("screenFlag", "P"),
            entry("effectiveDate", LocalDate.of(2026, 11, 18)),
            entry("endDate", null),
            entry("revalidationDueDate", LocalDate.of(2031, 11, 18)),
            entry("version", 4L));
  }

  @Test
  @DisplayName("[MAM-40 AC-2] a provider without a risk tier shows riskTier null, not a code")
  void snapshotWithoutTier() {
    assertThat(AuditTrail.snapshot(provider(null))).containsEntry("riskTier", null);
  }

  @Test
  @DisplayName("[MAM-40 AC-5] a snapshot with a named part carries the request details last")
  void snapshotWithPart() {
    Map<String, Object> details =
        AuditTrail.details("action", "SUSPEND", "reason", "LEIE exclusion");
    Map<String, Object> after =
        AuditTrail.snapshot(provider(RiskTier.HIGH), "statusChange", details);
    assertThat(after).containsEntry("riskTier", "H").containsEntry("statusChange", details);
    assertThat(List.copyOf(after.keySet()).get(after.size() - 1)).isEqualTo("statusChange");
    assertThat(after).hasSize(12);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-5] details keep their order and keep null values (a reason may be absent)")
  void detailsKeepOrderAndNulls() {
    Map<String, Object> d = AuditTrail.details("action", "REINSTATE", "reason", null, "n", 3);
    assertThat(d)
        .containsExactly(entry("action", "REINSTATE"), entry("reason", null), entry("n", 3));
  }

  @Test
  @DisplayName("[MAM-40 AC-5] details ignore a trailing key that has no value")
  void detailsIgnoreDanglingKey() {
    assertThat(AuditTrail.details("a", 1, "dangling")).containsExactly(entry("a", 1));
    assertThat(AuditTrail.details("only")).isEmpty();
    assertThat(AuditTrail.details()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] record() appends one entry with the correlation id of the request being handled")
  void recordUsesCorrelationId() {
    List<AuditEntry> entries = new ArrayList<>();
    AuditTrail trail = entries::add;
    MDC.put("correlationId", "5a1e7b20-3c4d-4e5f-9a6b-000000000456");
    Map<String, Object> before = Map.of("enrollStatus", "P");
    Map<String, Object> after = Map.of("enrollStatus", "A");

    trail.record("1003000111", "analyst1", "APPROVED", before, after);

    assertThat(entries)
        .containsExactly(
            new AuditEntry(
                "1003000111",
                null,
                "analyst1",
                "APPROVED",
                before,
                after,
                "5a1e7b20-3c4d-4e5f-9a6b-000000000456"));
  }

  @Test
  @DisplayName("[MAM-40 AC-2] without a correlation id in the log context the entry has none")
  void recordWithoutCorrelationId() {
    List<AuditEntry> entries = new ArrayList<>();
    AuditTrail trail = entries::add;

    trail.record("1003000111", "enrollment-service", "REGISTERED", null, Map.of());

    assertThat(entries).hasSize(1);
    assertThat(entries.get(0).correlationId()).isNull();
    assertThat(entries.get(0).at()).isNull();
    assertThat(entries.get(0).before()).isNull();
  }
}
