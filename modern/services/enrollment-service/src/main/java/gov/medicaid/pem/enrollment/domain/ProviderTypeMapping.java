package gov.medicaid.pem.enrollment.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Provider types (PRV-TYPE-CODE): the legacy codes IN, GR and FA stand for themselves; feed codes
 * are translated through the provider_type_mapping table (D-008). Anything else has no legacy type.
 */
public final class ProviderTypeMapping {

  private static final Set<String> LEGACY = Set.of("IN", "GR", "FA");

  private final Map<String, String> feedToLegacy;

  private ProviderTypeMapping(Map<String, String> feedToLegacy) {
    this.feedToLegacy = Map.copyOf(feedToLegacy);
  }

  public static ProviderTypeMapping of(Map<String, String> feedToLegacy) {
    return new ProviderTypeMapping(feedToLegacy);
  }

  public Optional<String> toLegacy(String code) {
    if (code == null) {
      return Optional.empty();
    }
    if (LEGACY.contains(code)) {
      return Optional.of(code);
    }
    return Optional.ofNullable(feedToLegacy.get(code));
  }
}
