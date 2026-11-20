package gov.medicaid.pem.directory.domain;

/**
 * PRVDIR01 rebuilt: only an ACTIVE provider is published (the legacy filter, status only); the name
 * is composed from the name fields (D-028): first name + last name for an individual, the legal
 * name for a group or facility, trimmed, single-spaced and at most 60 characters (never the raw
 * 60-byte PRV-VARIANT slice).
 */
public final class DirectoryRules {

  static final int NAME_MAX = 60;

  private DirectoryRules() {}

  public static boolean published(String enrollStatus) {
    return "A".equals(enrollStatus);
  }

  public static String displayName(String providerType, String first, String last, String legal) {
    String name =
        "IN".equals(providerType) ? (clean(first) + " " + clean(last)).trim() : clean(legal);
    return name.length() <= NAME_MAX ? name : name.substring(0, NAME_MAX).trim();
  }

  private static String clean(String s) {
    return s == null ? "" : s.trim().replaceAll("\\s+", " ");
  }
}
