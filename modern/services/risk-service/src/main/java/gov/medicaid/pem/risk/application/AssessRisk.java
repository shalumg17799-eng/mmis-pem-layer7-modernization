package gov.medicaid.pem.risk.application;

import gov.medicaid.pem.risk.domain.RuleSet;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assigns the risk tier after a passed screening (MAM-35, overview flow 2): the tier comes from the
 * rule set in force, is stored, announced as RiskTierAssigned for provider-service to save (D-002)
 * and counted. A failed screening gets no tier.
 */
@Service
public class AssessRisk {

  private final RuleSets rules;
  private final Assessments assessments;
  private final EventOutbox outbox;
  private final TierMetrics metrics;
  private final Clock clock;

  public AssessRisk(
      RuleSets rules,
      Assessments assessments,
      EventOutbox outbox,
      TierMetrics metrics,
      Clock clock) {
    this.rules = rules;
    this.assessments = assessments;
    this.outbox = outbox;
    this.metrics = metrics;
    this.clock = clock;
  }

  @Transactional
  public Optional<String> screeningCompleted(String npi, String result, String taxonomy) {
    if (!"PASS".equals(result)) {
      return Optional.empty();
    }
    RuleSet set = rules.inForce();
    String tier = set.tierFor(taxonomy);
    Instant now = clock.instant();
    assessments.save(npi, taxonomy, tier, set.version(), now);
    outbox.enqueue(
        "RiskTierAssigned", npi, new RiskTierAssignedData(npi, taxonomy, tier, set.version()));
    metrics.assigned();
    return Optional.of(tier);
  }
}
