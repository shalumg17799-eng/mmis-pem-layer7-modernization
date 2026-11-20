package gov.medicaid.pem.tmsis.application;

import java.util.stream.Stream;
import org.springframework.core.NestedExceptionUtils;

/**
 * The reason stored with and shown for a failed run (D-024): the first line of the root cause's
 * message, at most {@value #MAX} characters, and no SQL statement text. A message that carries the
 * statement before the database's own words ("... SQL [INSERT ...]; ERROR: reason Where: ...") is
 * cut down to the database's words. The log has the whole exception with its stack trace.
 */
public final class FailureReason {

  /** The longest reason, prefix included. */
  public static final int MAX = 200;

  private static final String DATABASE_MARK = "ERROR: ";
  private static final String[] DATABASE_DETAILS = {
    " Where:", " Detail:", " Hint:", " Call getNextException"
  };

  private FailureReason() {}

  /** The reason of this failure. */
  public static String of(Throwable failure) {
    return of("", failure);
  }

  /** The reason of this failure, after a fixed beginning that is part of the {@value #MAX}. */
  public static String of(String prefix, Throwable failure) {
    Throwable root = NestedExceptionUtils.getMostSpecificCause(failure);
    String message = root.getMessage() == null ? "" : root.getMessage();
    String line = message.strip().lines().findFirst().orElse("");
    int mark = line.lastIndexOf(DATABASE_MARK);
    if (mark >= 0) {
      line = line.substring(mark + DATABASE_MARK.length());
    }
    String text = line;
    int end =
        Stream.of(DATABASE_DETAILS)
            .mapToInt(text::indexOf)
            .filter(i -> i >= 0)
            .min()
            .orElse(text.length());
    String reason = prefix + text.substring(0, end).strip();
    if (reason.equals(prefix)) {
      reason = prefix + root.getClass().getSimpleName();
    }
    return reason.substring(0, Math.min(reason.length(), MAX));
  }
}
