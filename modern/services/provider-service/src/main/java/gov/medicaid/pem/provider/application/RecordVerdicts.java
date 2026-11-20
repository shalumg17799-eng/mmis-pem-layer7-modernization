package gov.medicaid.pem.provider.application;

import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the screening verdict (MAM-21, PRVSCR01 REWRITE) and the assigned risk tier (MAM-23,
 * PRVRSK01 REWRITE) to the provider record. Neither ever changes the enrolment status (D-019) or
 * the due date (D-003); the tier is stored as received, never recomputed (D-002).
 */
@Service
public class RecordVerdicts {

  private static final Logger LOG = LoggerFactory.getLogger(RecordVerdicts.class);

  static final String SCREENING = "screening-service";
  static final String RISK = "risk-service";

  private final ProviderChanges changes;
  private AuditTrail audit = AuditTrail.NONE;
  private ProviderStore store;

  public RecordVerdicts(ProviderChanges changes) {
    this.changes = changes;
  }

  /**
   * MAM-40: the provider history and the store it reads before and after each change; unit tests
   * that do not wire them get no history.
   */
  @Autowired(required = false)
  void setAuditTrail(AuditTrail audit, ProviderStore store) {
    this.audit = audit;
    this.store = store;
  }

  @Transactional
  public boolean screeningCompleted(String npi, String verdict) {
    return screeningCompleted(npi, verdict, SCREENING);
  }

  /** The actor of the history entry is the service that produced the event (MAM-40 AC-3). */
  @Transactional
  public boolean screeningCompleted(String npi, String verdict, String actor) {
    ScreenFlag flag = ScreenFlag.fromVerdict(verdict);
    Provider before = current(npi);
    boolean applied = logged(npi, changes.setScreenFlag(npi, flag), "screening verdict");
    audited(applied, npi, actor, "SCREENING_VERDICT", before, "screening", "result", verdict);
    return applied;
  }

  @Transactional
  public boolean riskTierAssigned(String npi, String tier) {
    return riskTierAssigned(npi, tier, RISK);
  }

  /** The actor of the history entry is the service that produced the event (MAM-40 AC-3). */
  @Transactional
  public boolean riskTierAssigned(String npi, String tier, String actor) {
    RiskTier t = RiskTier.fromCode(tier);
    Provider before = current(npi);
    boolean applied = logged(npi, changes.setRiskTier(npi, t), "risk tier");
    audited(applied, npi, actor, "RISK_TIER_ASSIGNED", before, "risk", "tier", tier);
    return applied;
  }

  private Provider current(String npi) {
    return store == null ? null : store.findByNpi(npi).orElse(null);
  }

  private void audited(
      boolean applied,
      String npi,
      String actor,
      String action,
      Provider before,
      String part,
      String key,
      String value) {
    if (!applied || store == null) {
      return;
    }
    audit.record(
        npi,
        actor,
        action,
        AuditTrail.snapshot(before),
        AuditTrail.snapshot(current(npi), part, AuditTrail.details(key, value)));
  }

  private static boolean logged(String npi, boolean applied, String what) {
    if (!applied) {
      LOG.warn("{} for NPI {} not applied: no such provider", what, npi);
    }
    return applied;
  }
}
