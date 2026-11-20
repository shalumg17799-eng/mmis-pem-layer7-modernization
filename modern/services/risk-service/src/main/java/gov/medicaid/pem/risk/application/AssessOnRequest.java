package gov.medicaid.pem.risk.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.risk.domain.RuleSet;
import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * assessRisk (MAM-41): the tier the table in force gives a taxonomy (PRVRSK01 CLASSIFY-ONE: only
 * the first three characters count; a blank or shorter value gets the default tier,
 * R-PRVRSK01-ADD-03). No side effect: nothing is stored and no RiskTierAssigned is published
 * (D-002).
 */
@Service
public class AssessOnRequest {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");
  static final int TAXONOMY_MAX = 10;

  /** Contract RiskAssessment (npi only when one was given). */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Assessment(
      String npi, String taxonomy, String tier, int ruleSetVersion, Instant assessedAt) {}

  private final RuleSets rules;
  private final Clock clock;

  public AssessOnRequest(RuleSets rules, Clock clock) {
    this.rules = rules;
    this.clock = clock;
  }

  public Assessment assess(String npi, String taxonomy) {
    if (taxonomy == null) {
      throw ApiException.badRequest("taxonomy is required.");
    }
    if (taxonomy.length() > TAXONOMY_MAX) {
      throw ApiException.badRequest("taxonomy must be at most " + TAXONOMY_MAX + " characters.");
    }
    if (npi != null && !NPI.matcher(npi).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    RuleSet set = rules.inForce();
    return new Assessment(npi, taxonomy, set.tierFor(taxonomy), set.version(), clock.instant());
  }
}
