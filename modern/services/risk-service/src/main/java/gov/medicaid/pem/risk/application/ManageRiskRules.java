package gov.medicaid.pem.risk.application;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.risk.domain.RuleTable;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * getRiskRules and replaceRiskRules (MAM-38, question C-9): the table in force can be read by staff
 * and replaced by an administrator. A replacement must name the version it was based on (a stale
 * version is refused with 422), must be well formed (400) and list each prefix once (422); it is
 * saved as the next version, in force from the service's date, and re-tiers nobody: no
 * RiskTierAssigned is published (AC-5).
 */
@Service
public class ManageRiskRules {

  private static final Logger LOG = LoggerFactory.getLogger(ManageRiskRules.class);

  private final RuleTables tables;
  private final Clock clock;

  public ManageRiskRules(RuleTables tables, Clock clock) {
    this.tables = tables;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public RuleTable current() {
    return tables.current();
  }

  @Transactional
  public RuleTable replace(Integer basedOn, String defaultTier, List<RuleTable.Rule> rules) {
    if (basedOn == null) {
      throw ApiException.badRequest("version is required.");
    }
    RuleTable.malformed(defaultTier, rules)
        .ifPresent(
            why -> {
              throw ApiException.badRequest(why);
            });
    RuleTable current = tables.current();
    if (basedOn != current.version()) {
      throw unprocessable(
          "version "
              + basedOn
              + " is not the table in force (version "
              + current.version()
              + "); reload it and try again.");
    }
    RuleTable.inconsistent(rules)
        .ifPresent(
            why -> {
              throw unprocessable(why);
            });
    RuleTable next = new RuleTable(current.version() + 1, LocalDate.now(clock), defaultTier, rules);
    if (!tables.add(next)) {
      throw unprocessable(
          "version "
              + basedOn
              + " was replaced by someone else meanwhile; reload it and try again.");
    }
    LOG.info(
        "risk rule table version {} saved ({} rules, default tier {}), in force from {}",
        next.version(),
        rules.size(),
        defaultTier,
        next.effectiveFrom());
    return next;
  }

  private static ApiException unprocessable(String detail) {
    return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, null, detail, List.of());
  }
}
