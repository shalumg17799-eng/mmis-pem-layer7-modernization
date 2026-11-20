package gov.medicaid.pem.screening.domain;

import java.time.Instant;

/**
 * How each source answer becomes a result (replaces the hard-coded P of PRVSCR01 116-132, D-005).
 * Defaults applied: an unknown individual licence fails without a code and groups and facilities
 * have no licence check (Q-041 A); a source that cannot be reached gives C (SCR-COND) without a
 * code (Q-039 A).
 */
public final class SourceRules {

  static final int DETAIL_MAX = 60;

  private SourceRules() {}

  public static SourceResult licence(
      boolean individual,
      String licenseNo,
      String state,
      boolean found,
      String status,
      String expiry,
      Instant at) {
    if (!individual) {
      return new SourceResult(Source.LICBOARD, "P", at, "NOT APPLICABLE", null);
    }
    if (!found) {
      return new SourceResult(Source.LICBOARD, "F", at, "LICENCE NOT FOUND", null);
    }
    String id = "LICENCE " + licenseNo + " " + state + " ";
    if ("ACTIVE".equals(status)) {
      return new SourceResult(Source.LICBOARD, "P", at, cut(id + "ACTIVE UNTIL " + expiry), null);
    }
    if ("EXPIRED".equals(status)) {
      return new SourceResult(Source.LICBOARD, "F", at, cut(id + "EXPIRED " + expiry), "E202");
    }
    return new SourceResult(Source.LICBOARD, "F", at, cut(id + status), null);
  }

  public static SourceResult leie(boolean excluded, String type, String since, Instant at) {
    return excluded
        ? new SourceResult(
            Source.OIGLEIE, "F", at, cut("EXCLUDED " + type + " SINCE " + since), "E201")
        : new SourceResult(Source.OIGLEIE, "P", at, "NOT EXCLUDED", null);
  }

  /** One LEIE answer: listed or not, with the exclusion type and date when listed. */
  public record Exclusion(boolean excluded, String type, String since) {

    public static final Exclusion NONE = new Exclusion(false, null, null);
  }

  /**
   * MAM-22 (D-016, R-PRVSCR01-cbl-00033): the provider and, for an organisation, its owner checked
   * against the LEIE, as one OIGLEIE result. Both listed: E201 naming both (question C-6); the
   * provider only: E201 as before; the owner only: E203 naming the owner; neither: P. A blank owner
   * NPI means the provider is checked on its own NPI only.
   */
  public static SourceResult leie(
      String npi, Exclusion own, String ownerNpi, Exclusion owner, Instant at) {
    boolean ownerListed = ownerNpi != null && !ownerNpi.isBlank() && owner.excluded();
    if (own.excluded() && ownerListed) {
      return new SourceResult(
          Source.OIGLEIE,
          "F",
          at,
          cut("NPI " + npi + " AND OWNER " + ownerNpi + " EXCLUDED"),
          "E201");
    }
    if (own.excluded()) {
      return leie(true, own.type(), own.since(), at);
    }
    if (ownerListed) {
      return new SourceResult(
          Source.OIGLEIE,
          "F",
          at,
          cut("OWNER " + ownerNpi + " EXCLUDED " + owner.type() + " SINCE " + owner.since()),
          "E203");
    }
    return leie(false, null, null, at);
  }

  public static SourceResult sam(boolean debarred, Instant at) {
    return debarred
        ? new SourceResult(Source.SAMGOV, "F", at, "DEBARRED", "E205")
        : new SourceResult(Source.SAMGOV, "P", at, "NOT DEBARRED", null);
  }

  public static SourceResult ssaDmf(boolean deceased, String dateOfDeath, Instant at) {
    return deceased
        ? new SourceResult(Source.SSADMF, "F", at, cut("DECEASED " + dateOfDeath), "E204")
        : new SourceResult(Source.SSADMF, "P", at, "NOT DECEASED", null);
  }

  public static SourceResult unreachable(Source source, Instant at) {
    return new SourceResult(source, "C", at, "COULD NOT BE REACHED", null);
  }

  /** MAM-20 AC-2: a source that did not answer within its time limit (C, no code, Q-039 A). */
  public static SourceResult timedOut(Source source, Instant at) {
    return new SourceResult(source, "C", at, "DID NOT ANSWER IN TIME", null);
  }

  static String cut(String detail) {
    return detail.length() <= DETAIL_MAX ? detail : detail.substring(0, DETAIL_MAX);
  }
}
