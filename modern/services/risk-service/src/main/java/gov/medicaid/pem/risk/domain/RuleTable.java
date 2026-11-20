package gov.medicaid.pem.risk.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The taxonomy-prefix risk table as the business sees and changes it (MAM-38, Q-005, question C-9):
 * a version, the date it takes effect, the default tier and the prefix rules in their order. A
 * changed table is always saved as a new version; earlier versions are kept, and nobody is
 * re-tiered by a change (AC-5).
 */
public record RuleTable(
    int version, LocalDate effectiveFrom, String defaultTier, List<Rule> rules) {

  private static final Pattern PREFIX = Pattern.compile("[0-9A-Z]{3}");
  private static final Set<String> TIERS = Set.of("L", "M", "H");

  /** One row: a three-character taxonomy prefix and its tier. */
  public record Rule(String taxonomyPrefix, String tier) {}

  public RuleTable {
    rules = List.copyOf(rules);
  }

  /** The table as the assessment uses it. */
  public RuleSet ruleSet() {
    Map<String, String> m = new LinkedHashMap<>();
    rules.forEach(r -> m.put(r.taxonomyPrefix(), r.tier()));
    return new RuleSet(version, defaultTier, m);
  }

  /**
   * Why a proposed table is malformed (a 400: contract RiskRule and RiskRuleSet), or empty when it
   * is well formed.
   */
  public static Optional<String> malformed(String defaultTier, List<Rule> rules) {
    if (defaultTier == null || !TIERS.contains(defaultTier)) {
      return Optional.of("defaultTier must be L, M or H.");
    }
    if (rules == null) {
      return Optional.of("rules are required.");
    }
    for (Rule r : rules) {
      if (r == null
          || r.taxonomyPrefix() == null
          || !PREFIX.matcher(r.taxonomyPrefix()).matches()) {
        return Optional.of("taxonomyPrefix must be 3 characters, digits or capital letters.");
      }
      if (r.tier() == null || !TIERS.contains(r.tier())) {
        return Optional.of("tier must be L, M or H.");
      }
    }
    return Optional.empty();
  }

  /** Why a well-formed table is inconsistent (a 422), or empty: a prefix may appear only once. */
  public static Optional<String> inconsistent(List<Rule> rules) {
    Set<String> seen = new HashSet<>();
    for (Rule r : rules) {
      if (!seen.add(r.taxonomyPrefix())) {
        return Optional.of("taxonomyPrefix " + r.taxonomyPrefix() + " is listed more than once.");
      }
    }
    return Optional.empty();
  }
}
