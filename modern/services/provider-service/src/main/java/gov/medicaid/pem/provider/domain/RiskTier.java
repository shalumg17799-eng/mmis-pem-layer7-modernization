package gov.medicaid.pem.provider.domain;

/** Risk tier (PRV-RISK-TIER), 42 CFR 455.450 levels. The stored value is the legacy code. */
public enum RiskTier {
  LIMITED("L"),
  MODERATE("M"),
  HIGH("H");

  private final String code;

  RiskTier(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static RiskTier fromCode(String code) {
    RiskTier tier = fromNullableCode(code);
    if (tier == null) {
      throw new IllegalArgumentException("unknown RiskTier code: " + code);
    }
    return tier;
  }

  /** Null or blank (no tier assigned yet) gives null; any other unknown code is refused. */
  public static RiskTier fromNullableCode(String code) {
    if (code == null || code.isBlank()) {
      return null;
    }
    for (RiskTier v : values()) {
      if (v.code.equals(code.trim())) {
        return v;
      }
    }
    throw new IllegalArgumentException("unknown RiskTier code: " + code);
  }
}
