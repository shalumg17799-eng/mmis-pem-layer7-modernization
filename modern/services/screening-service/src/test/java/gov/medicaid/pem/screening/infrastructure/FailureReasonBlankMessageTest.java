package gov.medicaid.pem.screening.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.screening.application.FailureReason;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

/**
 * The blank-message fallback of FailureReason (MAM-32 AC-6, D-024, Q-152): a reason is one line of
 * at most 200 characters whatever the failure carries, and a failure without a usable message is
 * shown by its class name (as in revalidation-service). When the message of the failure (or of the
 * root database cause) is empty or only white space, FailureReason falls back to
 * Throwable.toString(), which is not cut and keeps the line breaks. These tests are red until that
 * fallback is cut and joined to one line like the rest (defect reported to the lead, 2026-11-21).
 *
 * <p>They live outside the application package on purpose: PIT runs the tests of the application
 * package for every mutant and stops when one of them fails, so a red test there would hide the
 * mutation score of the whole module. The green unit tests of every other branch are in
 * application/FailureReasonTest.
 */
class FailureReasonBlankMessageTest {

  private static final String ILLEGAL_STATE = "java.lang.IllegalStateException";
  private static final String SQL_EXCEPTION = "java.sql.SQLException";

  // ---- the length and the line: whatever the blank message holds --------------------------------

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a message of only white space ({0} characters) still gives a reason of at most 200 characters")
  @ValueSource(ints = {200, 300, 5000})
  void blankMessageIsCutToo(int length) {
    String reason = FailureReason.of(new IllegalStateException(" ".repeat(length)));

    assertThat(reason).hasSizeLessThanOrEqualTo(FailureReason.MAX);
  }

  @ParameterizedTest(
      name = "[MAM-32 AC-6] a message of only line breaks and spaces still gives a one-line reason")
  @ValueSource(strings = {"\n", " \n ", "\r\n\r\n", "\n\n\n\n\n"})
  void blankMessageIsOneLine(String message) {
    String reason = FailureReason.of(new IllegalStateException(message));

    assertThat(reason).doesNotContain("\n").doesNotContain("\r");
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error whose root message is only white space ({0} characters) still gives at most 200 characters, one line")
  @ValueSource(ints = {300, 5000})
  void blankDatabaseRootIsCutToo(int length) {
    Throwable failure = new DuplicateKeyException("SQL [x]", new SQLException("\n".repeat(length)));

    String reason = FailureReason.of(failure);

    assertThat(reason).hasSizeLessThanOrEqualTo(FailureReason.MAX).doesNotContain("\n");
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database root whose first line is only the ERROR: mark ({0} detail characters) gives at most 200 characters, one line, none of its detail lines")
  @ValueSource(ints = {10, 300, 5000})
  void rootWithOnlyTheMarkInItsFirstLineIsShortToo(int length) {
    String detail = "  Detail: Failing row contains (" + "x".repeat(length) + ").";
    Throwable failure =
        new DuplicateKeyException("SQL [x]", new SQLException("ERROR: \n" + detail));

    String reason = FailureReason.of(failure);

    assertThat(reason)
        .hasSizeLessThanOrEqualTo(FailureReason.MAX)
        .doesNotContain("\n")
        .doesNotContain("Detail");
  }

  // ---- what is shown instead: the class name (the same as revalidation-service) -----------------

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a failure whose message is blank is shown by its class name (case {index})")
  @ValueSource(strings = {"", " ", "   ", "\t", "\n", " \n ", "\r\n\r\n", "\n\n\n\n\n"})
  void blankMessageShowsTheClassName(String message) {
    assertThat(FailureReason.of(new IllegalStateException(message))).isEqualTo(ILLEGAL_STATE);
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] a database error whose root message is blank is shown by the root's class name (case {index})")
  @ValueSource(strings = {"", "   ", "\n", " \n ", "ERROR: \n  Detail: Key (npi)=(x)"})
  void blankDatabaseRootShowsItsClassName(String message) {
    Throwable failure =
        new DuplicateKeyException("wrapper words, SQL [DELETE FROM x]", new SQLException(message));

    assertThat(FailureReason.of(failure)).isEqualTo(SQL_EXCEPTION);
  }
}
