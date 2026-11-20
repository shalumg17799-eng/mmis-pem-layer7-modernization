package gov.medicaid.pem.notification.domain;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Which event gives which notice (defaults Q-A7 / Q-A8 / Q-A9): ProviderStatusChanged P to A gives
 * APPROVED and P to D gives DENIED, every other transition nothing (sanction notices come from
 * SanctionApplied); an ApplicationRejected gives REJECTED only when it names a 10-digit NPI.
 * Notices go to the synthetic mailbox {@code <npi>@providers.pem.local}.
 */
public final class NoticeRules {

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");
  static final String DOMAIN = "@providers.pem.local";

  private NoticeRules() {}

  public static Optional<String> forStatusChange(String from, String to) {
    if (!"P".equals(from)) {
      return Optional.empty();
    }
    if ("A".equals(to)) {
      return Optional.of("APPROVED");
    }
    if ("D".equals(to)) {
      return Optional.of("DENIED");
    }
    return Optional.empty();
  }

  /**
   * MAM-58, PRVSAN01 GENERATE-NOTICE 126-128: SanctionApplied T gives TRM, S gives SUS, R gives
   * RST. Any other action gives nothing: the legacy GEN fallback (129) is superseded by D-026
   * (Q-A10).
   */
  public static Optional<String> forSanction(String action) {
    if (action == null) {
      return Optional.empty();
    }
    return switch (action) {
      case "T" -> Optional.of("TRM");
      case "S" -> Optional.of("SUS");
      case "R" -> Optional.of("RST");
      default -> Optional.empty();
    };
  }

  public static Optional<String> forRejection(String npi) {
    return npi != null && NPI.matcher(npi).matches() ? Optional.of("REJECTED") : Optional.empty();
  }

  public static String recipient(String npi) {
    return npi + DOMAIN;
  }
}
