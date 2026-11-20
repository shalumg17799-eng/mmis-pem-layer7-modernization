package gov.medicaid.pem.risk.domain;

import java.util.Map;

/**
 * A versioned taxonomy-prefix table (PRVRSK01 CLASSIFY-ONE as data, Q-005): the first three
 * characters of the taxonomy pick the tier; any other prefix gets the default tier. Exactly one
 * tier per assessment (D-002, no ALTER path).
 */
public record RuleSet(int version, String defaultTier, Map<String, String> prefixTiers) {

  public RuleSet {
    prefixTiers = Map.copyOf(prefixTiers);
  }

  public String tierFor(String taxonomy) {
    if (taxonomy == null || taxonomy.length() < 3) {
      return defaultTier;
    }
    return prefixTiers.getOrDefault(taxonomy.substring(0, 3), defaultTier);
  }
}
