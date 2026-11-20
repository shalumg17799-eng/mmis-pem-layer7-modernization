package gov.medicaid.pem.directory.infrastructure;

/**
 * A LIKE / ILIKE pattern for text that is searched as typed: the wildcards {@code %} and {@code _}
 * and the escape character {@code \} in it are literal characters (PostgreSQL's escape character is
 * the backslash; the queries of the directory name search declare it with {@code ESCAPE}).
 */
final class LikePattern {

  private LikePattern() {}

  /** The pattern that matches any value that contains {@code text}. */
  static String contains(String text) {
    return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
