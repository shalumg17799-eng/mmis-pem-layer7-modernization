package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.revalidation.application.FailureReason;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

/**
 * The blank-message fallback of FailureReason (MAM-45 AC-8, D-024): a reason is one line of at most
 * 200 characters whatever the failure carries. When the message of the failure (or of the root
 * database cause) is only white space, FailureReason falls back to Throwable.toString(), which is
 * not cut and keeps the line breaks. These tests are red until that fallback is cut and joined to
 * one line like the rest (defect reported to the lead, 2026-11-21).
 *
 * <p>They live outside the application package on purpose: PIT runs the tests of the application
 * package for every mutant and stops when one of them fails, so a red test there would hide the
 * mutation score of the whole module. The green unit tests of every other branch are in
 * application/FailureReasonTest.
 */
class FailureReasonBlankMessageTest {

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a message of only white space ({0} characters) still gives a reason of at most 200 characters")
  @ValueSource(ints = {200, 300, 5000})
  void blankMessageIsCutToo(int length) {
    String reason = FailureReason.of(new IllegalStateException(" ".repeat(length)));

    assertThat(reason).hasSizeLessThanOrEqualTo(FailureReason.MAX);
  }

  @ParameterizedTest(
      name = "[MAM-45 AC-8] a message of only line breaks and spaces still gives a one-line reason")
  @ValueSource(strings = {"\n", " \n ", "\r\n\r\n", "\n\n\n\n\n"})
  void blankMessageIsOneLine(String message) {
    String reason = FailureReason.of(new IllegalStateException(message));

    assertThat(reason).doesNotContain("\n").doesNotContain("\r");
  }

  @ParameterizedTest(
      name =
          "[MAM-45 AC-8] a database error whose root message is only white space ({0} characters) still gives at most 200 characters, one line")
  @ValueSource(ints = {300, 5000})
  void blankDatabaseRootIsCutToo(int length) {
    Throwable failure = new DuplicateKeyException("SQL [x]", new SQLException("\n".repeat(length)));

    String reason = FailureReason.of(failure);

    assertThat(reason).hasSizeLessThanOrEqualTo(FailureReason.MAX).doesNotContain("\n");
  }
}
