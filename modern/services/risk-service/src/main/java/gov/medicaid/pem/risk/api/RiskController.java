package gov.medicaid.pem.risk.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.risk.application.AssessOnRequest;
import gov.medicaid.pem.risk.application.ManageRiskRules;
import gov.medicaid.pem.risk.domain.RuleTable;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The risk table and on-request assessments (contract risk-service.yaml; MAM-38, MAM-41). */
@RestController
@RequestMapping("/api/v1")
public class RiskController {

  private final ManageRiskRules rules;
  private final AssessOnRequest assess;

  public RiskController(ManageRiskRules rules, AssessOnRequest assess) {
    this.rules = rules;
    this.assess = assess;
  }

  /** Contract RiskRule. */
  public record RiskRule(String taxonomyPrefix, String tier) {}

  /** Contract RiskRuleSet. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RiskRuleSet(
      Integer version, LocalDate effectiveFrom, String defaultTier, List<RiskRule> rules) {

    static RiskRuleSet of(RuleTable t) {
      return new RiskRuleSet(
          t.version(),
          t.effectiveFrom(),
          t.defaultTier(),
          t.rules().stream().map(r -> new RiskRule(r.taxonomyPrefix(), r.tier())).toList());
    }
  }

  /** Contract RiskAssessmentRequest. */
  public record RiskAssessmentRequest(String npi, String taxonomy) {}

  /** getRiskRules: x-roles ANALYST, SUPERVISOR, ADMIN. */
  @GetMapping("/risk-rules")
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'ADMIN')")
  public RiskRuleSet getRiskRules() {
    return RiskRuleSet.of(rules.current());
  }

  /** replaceRiskRules: x-roles ADMIN; saved as the next version (question C-9). */
  @PutMapping("/risk-rules")
  @PreAuthorize("hasRole('ADMIN')")
  public RiskRuleSet replaceRiskRules(@RequestBody(required = false) RiskRuleSet body) {
    if (body == null) {
      throw ApiException.badRequest("A rule set is required.");
    }
    List<RuleTable.Rule> proposed =
        body.rules() == null
            ? null
            : body.rules().stream()
                .map(r -> r == null ? null : new RuleTable.Rule(r.taxonomyPrefix(), r.tier()))
                .toList();
    return RiskRuleSet.of(rules.replace(body.version(), body.defaultTier(), proposed));
  }

  /** assessRisk: x-roles ANALYST, SUPERVISOR, SYSTEM; no side effect. */
  @PostMapping("/risk-assessments")
  @PreAuthorize("hasAnyRole('ANALYST', 'SUPERVISOR', 'SYSTEM')")
  public AssessOnRequest.Assessment assessRisk(
      @RequestBody(required = false) RiskAssessmentRequest body) {
    if (body == null) {
      throw ApiException.badRequest("taxonomy is required.");
    }
    return assess.assess(body.npi(), body.taxonomy());
  }
}
