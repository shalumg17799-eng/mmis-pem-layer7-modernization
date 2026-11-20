package gov.medicaid.pem.provider.domain;

/** Screening flag (PRV-SCREEN-FLAG). The stored value is the legacy code. */
public enum ScreenFlag {
  UNSCREENED("U"),
  PASS("P"),
  FAIL("F");

  private final String code;

  ScreenFlag(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  public static ScreenFlag fromCode(String code) {
    for (ScreenFlag v : values()) {
      if (v.code.equals(code == null ? null : code.trim())) {
        return v;
      }
    }
    throw new IllegalArgumentException("unknown ScreenFlag code: " + code);
  }

  /**
   * The screening verdict of ScreeningCompleted as a screen flag (PRVSCR01 106-112: PASS -> P, FAIL
   * -> F).
   */
  public static ScreenFlag fromVerdict(String verdict) {
    if ("PASS".equals(verdict)) {
      return PASS;
    }
    if ("FAIL".equals(verdict)) {
      return FAIL;
    }
    throw new IllegalArgumentException("unknown screening verdict: " + verdict);
  }
}
