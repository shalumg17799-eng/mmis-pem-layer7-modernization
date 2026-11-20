package gov.medicaid.pem.provider.domain;

/**
 * Provider type (PRV-TYPE-CODE): individual, group, facility (D-008). The stored value is the
 * legacy code.
 */
public enum ProviderType {
  INDIVIDUAL("IN"),
  GROUP("GR"),
  FACILITY("FA");

  private final String code;

  ProviderType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static ProviderType fromCode(String code) {
    for (ProviderType v : values()) {
      if (v.code.equals(code == null ? null : code.trim())) {
        return v;
      }
    }
    throw new IllegalArgumentException("unknown ProviderType code: " + code);
  }
}
