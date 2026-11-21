package gov.medicaid.pem.revalidation.application;

import java.sql.SQLException;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;

/**
 * The reason stored with and shown for a failed sweep run (D-024, MAM-45 AC-8): one line of at most
 * {@value #MAX} characters that holds no SQL statement text. A failure with a database error in its
 * chain shows the database's own words (the first line of the root cause, without its "ERROR:"
 * mark); any other failure shows its own message. A failure whose message is missing or only white
 * space shows its class name. The log has the whole exception with its stack trace.
 */
public final class FailureReason {

  /** The longest reason. */
  public static final int MAX = 200;

  private static final String DATABASE_MARK = "ERROR: ";

  private FailureReason() {}

  /** The reason of this failure. */
  public static String of(Throwable failure) {
    boolean database = hasDatabaseError(failure);
    Throwable shown = database ? NestedExceptionUtils.getMostSpecificCause(failure) : failure;
    String line = firstLine(shown.getMessage());
    String words = database ? afterDatabaseMark(line) : line;
    String reason = words.isBlank() ? shown.getClass().getName() : words;
    return reason.substring(0, Math.min(reason.length(), MAX));
  }

  private static String firstLine(String message) {
    return message == null ? "" : message.strip().lines().findFirst().orElse("");
  }

  private static String afterDatabaseMark(String line) {
    int mark = line.lastIndexOf(DATABASE_MARK);
    return mark < 0 ? line : line.substring(mark + DATABASE_MARK.length()).strip();
  }

  private static boolean hasDatabaseError(Throwable failure) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t instanceof DataAccessException || t instanceof SQLException) {
        return true;
      }
    }
    return false;
  }
}
