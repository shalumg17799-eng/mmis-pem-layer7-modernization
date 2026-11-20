package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.Decision;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderApplication;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-40 at the level of the use cases: every change of a provider's record appends exactly one
 * history entry, with the actor, the action, the record before and after, and the details of the
 * request; a change that is refused or not applied appends nothing.
 */
class AuditHistoryUseCasesTest {

  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneOffset.UTC);
  static final CurrentUser ANALYST =
      new CurrentUser("analyst1", Set.of("ANALYST"), Optional.empty());
  static final CurrentUser SYSTEM =
      new CurrentUser("service-account-pem-services", Set.of("SYSTEM"), Optional.empty());

  final Map<String, Provider> db = new HashMap<>();
  final List<AuditEntry> entries = new ArrayList<>();
  final List<String> outbox = new ArrayList<>();
  final ProviderStore store = npi -> Optional.ofNullable(db.get(npi));
  final DecisionWriter writer = p -> db.put(p.npi(), p);

  static Provider provider(
      String npi, EnrollStatus status, ScreenFlag flag, RiskTier tier, LocalDate due) {
    return new Provider(
        npi,
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        tier,
        status,
        flag,
        LocalDate.of(2026, 11, 1),
        null,
        due,
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        2);
  }

  // ---- registration (MAM-17 flow, MAM-40 AC-1, AC-2, AC-3) --------------------------------------

  static class Registry implements ProviderRegistry {
    final Map<String, Provider> rows = new HashMap<>();

    @Override
    public boolean exists(String npi) {
      return rows.containsKey(npi);
    }

    @Override
    public void add(Provider provider) {
      rows.put(provider.npi(), provider);
    }
  }

  RegisterProvider registration() {
    RegisterProvider r =
        new RegisterProvider(new Registry(), (t, s, d) -> outbox.add(t), () -> {}, CLOCK);
    r.setAuditTrail(entries::add);
    return r;
  }

  static ProviderApplication application(String npi) {
    return new ProviderApplication(
        npi,
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-1] a registration appends one REGISTERED entry: before null, after PENDING and UNSCREENED, actor enrollment-service")
  void registrationEntry() {
    registration().register(application("1003000111"));

    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.npi()).isEqualTo("1003000111");
    assertThat(e.actor()).isEqualTo("enrollment-service");
    assertThat(e.action()).isEqualTo("REGISTERED");
    assertThat(e.before()).isNull();
    assertThat(e.after())
        .containsEntry("enrollStatus", "P")
        .containsEntry("screenFlag", "U")
        .containsEntry("riskTier", null)
        .containsEntry("effectiveDate", LocalDate.of(2026, 11, 18))
        .containsEntry("version", 0L);
  }

  @Test
  @DisplayName("[MAM-40 AC-3] the actor of a registration is the producing service named by the event")
  void registrationActorIsTheSource() {
    registration().register(application("1003000111"), "enrollment-service-2");

    assertThat(entries.get(0).actor()).isEqualTo("enrollment-service-2");
  }

  @Test
  @DisplayName("[MAM-40 AC-1] a duplicate NPI is skipped and appends no entry")
  void duplicateAppendsNothing() {
    RegisterProvider register = registration();
    register.register(application("1003000111"));
    register.register(application("1003000111"));

    assertThat(entries).hasSize(1);
  }

  // ---- screening verdict and risk tier (MAM-21, MAM-23, MAM-40 AC-1, AC-3) ----------------------

  RecordVerdicts verdicts() {
    ProviderChanges changes =
        new ProviderChanges() {
          @Override
          public boolean setScreenFlag(String npi, ScreenFlag flag) {
            Provider p = db.get(npi);
            if (p == null) {
              return false;
            }
            db.put(npi, provider(npi, p.enrollStatus(), flag, p.riskTier(), p.revalidationDueDate()));
            return true;
          }

          @Override
          public boolean setRiskTier(String npi, RiskTier tier) {
            Provider p = db.get(npi);
            if (p == null) {
              return false;
            }
            db.put(
                npi, provider(npi, p.enrollStatus(), p.screenFlag(), tier, p.revalidationDueDate()));
            return true;
          }
        };
    RecordVerdicts v = new RecordVerdicts(changes);
    v.setAuditTrail(entries::add, store);
    return v;
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-1] a screening verdict appends one SCREENING_VERDICT entry: before U, after P and the result")
  void verdictEntry() {
    db.put(
        "1003000111",
        provider("1003000111", EnrollStatus.PENDING, ScreenFlag.UNSCREENED, null, null));

    verdicts().screeningCompleted("1003000111", "PASS", "screening-service");

    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.actor()).isEqualTo("screening-service");
    assertThat(e.action()).isEqualTo("SCREENING_VERDICT");
    assertThat(e.before()).containsEntry("screenFlag", "U");
    assertThat(e.after())
        .containsEntry("screenFlag", "P")
        .containsEntry("screening", Map.of("result", "PASS"));
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-3] without a named producer the actors are screening-service and risk-service")
  void defaultActors() {
    db.put(
        "1003000111",
        provider("1003000111", EnrollStatus.PENDING, ScreenFlag.UNSCREENED, null, null));
    RecordVerdicts v = verdicts();

    v.screeningCompleted("1003000111", "FAIL");
    v.riskTierAssigned("1003000111", "M");

    assertThat(entries).extracting(AuditEntry::actor).containsExactly("screening-service", "risk-service");
    assertThat(entries.get(0).after()).containsEntry("screenFlag", "F");
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-1] a risk tier appends one RISK_TIER_ASSIGNED entry: before no tier, after the tier and the tier received")
  void tierEntry() {
    db.put(
        "1003000111", provider("1003000111", EnrollStatus.PENDING, ScreenFlag.PASS, null, null));

    verdicts().riskTierAssigned("1003000111", "L", "risk-service");

    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.action()).isEqualTo("RISK_TIER_ASSIGNED");
    assertThat(e.actor()).isEqualTo("risk-service");
    assertThat(e.before()).containsEntry("riskTier", null);
    assertThat(e.after()).containsEntry("riskTier", "L").containsEntry("risk", Map.of("tier", "L"));
  }

  @Test
  @DisplayName("[MAM-40 AC-1] a verdict or tier for a provider that is not on file appends nothing")
  void unknownProviderAppendsNothing() {
    RecordVerdicts v = verdicts();

    v.screeningCompleted("1009999999", "PASS", "screening-service");
    v.riskTierAssigned("1009999999", "H", "risk-service");

    assertThat(entries).isEmpty();
  }

  // ---- the analyst's decision (MAM-27, MAM-40 AC-1, AC-2, AC-3) ---------------------------------

  DecideProvider decision() {
    DecideProvider d = new DecideProvider(store, writer, (t, s, data) -> outbox.add(t), CLOCK);
    d.setAuditTrail(entries::add);
    return d;
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] an approval appends one APPROVED entry: actor analyst1, before P, after A with the due date and the decision")
  void approvalEntry() {
    db.put(
        "1003000111",
        provider("1003000111", EnrollStatus.PENDING, ScreenFlag.PASS, RiskTier.LIMITED, null));

    decision().decide("1003000111", Decision.APPROVE, null, ANALYST);

    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.actor()).isEqualTo("analyst1");
    assertThat(e.action()).isEqualTo("APPROVED");
    assertThat(e.before()).containsEntry("enrollStatus", "P").containsEntry("revalidationDueDate", null);
    assertThat(e.after())
        .containsEntry("enrollStatus", "A")
        .containsEntry("revalidationDueDate", LocalDate.of(2031, 11, 18));
    Map<String, Object> decisionDetails = new java.util.LinkedHashMap<>();
    decisionDetails.put("decision", "APPROVE");
    decisionDetails.put("reason", null);
    assertThat(e.after()).containsEntry("decision", decisionDetails);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-1] a denial appends one DENIED entry with the reason, and no due date after")
  void denialEntry() {
    db.put(
        "1003000444",
        provider("1003000444", EnrollStatus.PENDING, ScreenFlag.FAIL, RiskTier.HIGH, null));

    decision().decide("1003000444", Decision.DENY, "Owner is on the LEIE list", ANALYST);

    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.action()).isEqualTo("DENIED");
    assertThat(e.before()).containsEntry("enrollStatus", "P");
    assertThat(e.after()).containsEntry("enrollStatus", "D").containsEntry("revalidationDueDate", null);
    assertThat(e.after().get("decision"))
        .isEqualTo(
            Map.of("decision", "DENY", "reason", "Owner is on the LEIE list"));
  }

  @Test
  @DisplayName("[MAM-40 AC-1] a refused decision appends nothing")
  void refusedDecisionAppendsNothing() {
    db.put(
        "1003000111",
        provider("1003000111", EnrollStatus.ACTIVE, ScreenFlag.PASS, RiskTier.LIMITED, null));

    assertThatThrownBy(() -> decision().decide("1003000111", Decision.APPROVE, null, ANALYST))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(() -> decision().decide("1009999999", Decision.APPROVE, null, ANALYST))
        .isInstanceOf(ApiException.class);

    assertThat(entries).isEmpty();
  }

  // ---- the status change of a sanction (MAM-33, MAM-40 AC-1, AC-3, AC-5) ------------------------

  ChangeProviderStatus statusChange() {
    ChangeProviderStatus c =
        new ChangeProviderStatus(store, writer, (t, s, d) -> outbox.add(t), () -> {});
    c.setAuditTrail(entries::add);
    return c;
  }

  @ParameterizedTest(name = "{0} appends a {1} entry from {2} to {3}")
  @CsvSource({
    "TERMINATE, TERMINATED, A, T",
    "SUSPEND, SUSPENDED, A, S",
    "REINSTATE, REINSTATED, S, A"
  })
  @DisplayName(
      "[MAM-40 AC-5] a status change appends one entry that carries before, after, reason, effective date and sanction id")
  void statusChangeEntry(String action, String entryAction, String from, String to) {
    UUID sanctionId = UUID.fromString("0a3c1f7e-5b2d-4c8a-9e61-000000000001");
    db.put(
        "1003000111",
        provider(
            "1003000111",
            EnrollStatus.fromCode(from),
            ScreenFlag.PASS,
            RiskTier.LIMITED,
            LocalDate.of(2031, 1, 15)));

    statusChange()
        .change(
            "1003000111",
            new StatusChangeCommand(action, "LEIE exclusion", LocalDate.of(2026, 3, 31), sanctionId),
            SYSTEM);

    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.actor()).isEqualTo("service-account-pem-services");
    assertThat(e.action()).isEqualTo(entryAction);
    assertThat(e.before()).containsEntry("enrollStatus", from);
    assertThat(e.after()).containsEntry("enrollStatus", to).containsEntry("endDate", null);
    assertThat(e.after().get("statusChange"))
        .isEqualTo(
            Map.of(
                "action",
                action,
                "reason",
                "LEIE exclusion",
                "effectiveDate",
                LocalDate.of(2026, 3, 31),
                "sanctionId",
                sanctionId));
  }

  @Test
  @DisplayName("[MAM-40 AC-5] a refused or unknown status change appends nothing")
  void refusedStatusChangeAppendsNothing() {
    db.put(
        "1003000111",
        provider("1003000111", EnrollStatus.ACTIVE, ScreenFlag.PASS, RiskTier.LIMITED, null));
    LocalDate day = LocalDate.of(2026, 3, 31);

    assertThatThrownBy(
            () ->
                statusChange()
                    .change("1003000111", new StatusChangeCommand("PURGE", null, day, null), SYSTEM))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                statusChange()
                    .change(
                        "1003000111", new StatusChangeCommand("SUSPEND", null, null, null), SYSTEM))
        .isInstanceOf(ApiException.class);
    assertThatThrownBy(
            () ->
                statusChange()
                    .change(
                        "1009999999", new StatusChangeCommand("SUSPEND", null, day, null), SYSTEM))
        .isInstanceOf(ApiException.class);

    assertThat(entries).isEmpty();
  }

  // ---- a passed revalidation (MAM-30, MAM-40 AC-1) ----------------------------------------------

  @Test
  @DisplayName(
      "[MAM-40 AC-1] a passed revalidation appends one REVALIDATION_PASSED entry with the old and the new due date")
  void revalidationEntry() {
    db.put(
        "1003000111",
        provider(
            "1003000111",
            EnrollStatus.ACTIVE,
            ScreenFlag.PASS,
            RiskTier.LIMITED,
            LocalDate.of(2026, 3, 31)));
    CompleteRevalidation complete =
        new CompleteRevalidation(store, writer, (t, s, d) -> outbox.add(t), CLOCK);
    complete.setAuditTrail(entries::add);

    boolean applied = complete.passed("1003000111");

    assertThat(applied).isTrue();
    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.actor()).isEqualTo("screening-service");
    assertThat(e.action()).isEqualTo("REVALIDATION_PASSED");
    assertThat(e.before()).containsEntry("revalidationDueDate", LocalDate.of(2026, 3, 31));
    assertThat(e.after()).containsEntry("revalidationDueDate", LocalDate.of(2031, 11, 18));
    assertThat(complete.passed("1009999999")).isFalse();
    assertThat(entries).hasSize(1);
  }
}
