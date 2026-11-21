package gov.medicaid.pem.screening.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.UncategorizedSQLException;

/**
 * The reason stored with and shown for a failed screening run (MAM-32 AC-6, D-024): one line of at
 * most 200 characters without SQL text. A failure with a database error in its chain shows the
 * database's own words (the first line of the root cause without its "ERROR:" mark); any other
 * failure shows its own message. These are unit tests of every branch, so that mutation testing
 * does not depend on the integration tests (BatchRunFailureTest, BatchRunDatabaseErrorTest).
 *
 * <p>The cases where the message is blank and the reason must still be short and on one line are in
 * infrastructure/FailureReasonBlankMessageTest (red until the fallback of FailureReason is cut).
 */
class FailureReasonTest {

  private static final String PG_DUPLICATE =
      "ERROR: duplicate key value violates unique constraint \"screening_public_id_key\"\n"
          + "  Detail: Key (public_id)=(00000000-0000-4000-8000-000000000001) already exists.";
  private static final String DUPLICATE_WORDS =
      "duplicate key value violates unique constraint \"screening_public_id_key\"";

  private static String sqlOf(String message) {
    return "PreparedStatementCallback; SQL [INSERT INTO screening (public_id, npi, reason) VALUES"
        + " (?, ?, ?)]; "
        + message;
  }

  // ---- a database error in the chain: the database's own words ---------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a Spring data access error gives the database's own first line, without the ERROR: mark, the detail lines and the SQL statement")
  void dataAccessErrorGivesTheDatabaseWords() {
    SQLException pg = new SQLException(PG_DUPLICATE);
    Throwable failure = new DuplicateKeyException(sqlOf(PG_DUPLICATE), pg);

    assertThat(FailureReason.of(failure)).isEqualTo(DUPLICATE_WORDS);
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a plain SQLException is a database error too: its words, no mark")
  void sqlExceptionGivesItsWords() {
    SQLException failure =
        new SQLException("ERROR: relation \"screening_run\" does not exist\n  Position: 15");

    assertThat(FailureReason.of(failure)).isEqualTo("relation \"screening_run\" does not exist");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a database error deep in the chain of a failure that is not one itself still gives the database's words")
  void databaseErrorDeepInTheChain() {
    SQLException pg = new SQLException("ERROR: SYNTHETIC DISK FULL");
    UncategorizedSQLException spring =
        new UncategorizedSQLException(
            "PreparedStatementCallback", "UPDATE screening_run SET status = ?", pg);
    Throwable failure =
        new IllegalStateException("screening run not completed: " + spring.getMessage(), spring);

    assertThat(FailureReason.of(failure)).isEqualTo("SYNTHETIC DISK FULL");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a SQLException below an exception that is no data access error is found by walking the cause chain")
  void sqlExceptionBelowAPlainException() {
    Throwable failure =
        new RuntimeException(
            "wrapper", new IllegalStateException("middle", new SQLException("ERROR: deep words")));

    assertThat(FailureReason.of(failure)).isEqualTo("deep words");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a SQLException a hundred causes down is found: the whole chain is walked")
  void sqlExceptionAHundredCausesDown() {
    Throwable failure = new SQLException("ERROR: SYNTHETIC LOCK TIMEOUT");
    for (int level = 0; level < 100; level++) {
      failure = new RuntimeException("level " + level + " " + "w".repeat(300), failure);
    }

    assertThat(FailureReason.of(failure)).isEqualTo("SYNTHETIC LOCK TIMEOUT");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a data access error without a SQLException shows the root cause of the chain")
  void dataAccessErrorWithoutSqlException() {
    Throwable failure =
        new DataAccessResourceFailureException(
            "Could not get JDBC Connection", new IOException("Connection refused"));

    assertThat(FailureReason.of(failure)).isEqualTo("Connection refused");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a data access error that is itself the root cause shows its own message")
  void dataAccessErrorAloneShowsItsMessage() {
    assertThat(FailureReason.of(new QueryTimeoutException("statement timeout")))
        .isEqualTo("statement timeout");
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a database message without an ERROR: mark is shown as it is")
  void databaseMessageWithoutMark() {
    SQLException pg = new SQLException("connection reset by peer");

    assertThat(FailureReason.of(new UncategorizedSQLException("task", "SELECT 1", pg)))
        .isEqualTo("connection reset by peer");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] the ERROR: mark is cut where it stands in the first line, not only at its start")
  void databaseMarkInTheMiddleOfTheLine() {
    SQLException pg = new SQLException("could not execute: ERROR: lock timeout");

    assertThat(FailureReason.of(pg)).isEqualTo("lock timeout");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] blank lines before the ERROR: mark of a database message do not hide its words")
  void databaseMessageWithLeadingBlankLines() {
    SQLException pg =
        new SQLException("\n\n  ERROR: SYNTHETIC SERIALIZATION FAILURE\n  Hint: retry");

    assertThat(FailureReason.of(pg)).isEqualTo("SYNTHETIC SERIALIZATION FAILURE");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a database error shows the first line of the root cause, not of the wrapper")
  void databaseShowsTheRootCauseNotTheWrapper() {
    SQLException root = new SQLException("ERROR: root words");
    Throwable failure = new DuplicateKeyException("wrapper words, SQL [DELETE FROM x]", root);

    assertThat(FailureReason.of(failure)).isEqualTo("root words").doesNotContain("DELETE");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a database error whose root cause has no message shows the root cause by name")
  void databaseRootWithoutMessage() {
    Throwable failure = new DataAccessResourceFailureException("wrapper words", new SQLException());

    assertThat(FailureReason.of(failure)).isEqualTo("java.sql.SQLException");
  }

  // ---- any other failure: its own message -------------------------------------------------------

  @Test
  @DisplayName("[MAM-32 AC-6] a failure that is not a database error gives its own message")
  void otherFailureGivesItsMessage() {
    assertThat(FailureReason.of(new IllegalStateException("provider-service could not be read")))
        .isEqualTo("provider-service could not be read");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a failure that is not a database error gives its own message, not the message of its cause")
  void otherFailureIgnoresItsCause() {
    Throwable failure =
        new IllegalStateException(
            "SCRNOUT file not written", new IllegalArgumentException("record 7 is short"));

    assertThat(FailureReason.of(failure)).isEqualTo("SCRNOUT file not written");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] the ERROR: mark is cut only from a database error: any other message keeps its words")
  void otherFailureKeepsAnErrorMark() {
    assertThat(FailureReason.of(new IllegalArgumentException("ERROR: bad business date")))
        .isEqualTo("ERROR: bad business date");
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a message of several lines gives its first line")
  void otherFailureFirstLine() {
    assertThat(FailureReason.of(new IllegalStateException("first line\nsecond line\r\nthird")))
        .isEqualTo("first line");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a message whose lines are separated by a lone CR gives its first line")
  void otherFailureFirstLineOfCarriageReturns() {
    assertThat(FailureReason.of(new IllegalStateException("first line\rsecond line")))
        .isEqualTo("first line");
  }

  @ParameterizedTest(
      name =
          "[MAM-32 AC-6] blank lines and spaces before the message do not make the reason empty ({0})")
  @ValueSource(strings = {"\nboom", "\r\n\r\n  boom", "   boom\nmore", "\t\nboom\n"})
  void leadingBlankLines(String message) {
    assertThat(FailureReason.of(new IllegalStateException(message))).isEqualTo("boom");
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a failure without a message is shown by its class name")
  void noMessageGivesTheClassName() {
    assertThat(FailureReason.of(new IllegalStateException()))
        .isEqualTo("java.lang.IllegalStateException");
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a failure with a blank message is still shown, by its class name first")
  void blankMessageGivesTheClassName() {
    assertThat(FailureReason.of(new IllegalStateException("   ")))
        .startsWith("java.lang.IllegalStateException");
  }

  // ---- the length: never more than 200 characters ----------------------------------------------

  @ParameterizedTest(name = "[MAM-32 AC-6] a message of {0} characters gives a reason of {1}")
  @CsvSource({"1, 1", "199, 199", "200, 200", "201, 200", "202, 200", "1000, 200", "50000, 200"})
  void lengthIsCutAt200(int length, int expected) {
    String message = "x".repeat(length);

    String reason = FailureReason.of(new IllegalStateException(message));

    assertThat(reason).hasSize(expected).isEqualTo(message.substring(0, expected));
  }

  @Test
  @DisplayName("[MAM-32 AC-6] the limit is 200 characters and is published as MAX")
  void maxIs200() {
    assertThat(FailureReason.MAX).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] the cut keeps the first 200 characters of the first line and drops the lines after it")
  void longFirstLineWithMoreLines() {
    String first = "a".repeat(150) + "b".repeat(150);

    String reason = FailureReason.of(new IllegalStateException(first + "\nsecond line"));

    assertThat(reason).isEqualTo("a".repeat(150) + "b".repeat(50));
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a long database message is cut at 200 characters after the mark has been removed")
  void longDatabaseMessage() {
    SQLException pg = new SQLException("ERROR: " + "d".repeat(500));

    assertThat(FailureReason.of(new DuplicateKeyException("SQL [x]", pg)))
        .isEqualTo("d".repeat(200));
  }

  @Test
  @DisplayName("[MAM-32 AC-6] the 200 characters are counted after the ERROR: mark, not with it")
  void databaseCutCountsAfterTheMark() {
    SQLException pg = new SQLException("ERROR: " + "d".repeat(200));

    assertThat(FailureReason.of(pg)).hasSize(200);
  }

  static Stream<Arguments> failuresOfEveryKind() {
    String longText = "z".repeat(900);
    String lines = "line one\nline two\r\nline three\n" + longText;
    return Stream.of(
        Arguments.of("long message", new IllegalStateException(longText)),
        Arguments.of("long message with line breaks", new IllegalStateException(lines)),
        Arguments.of("long message with lone CRs", new IllegalStateException("a\rb\r" + longText)),
        Arguments.of("no message", new IllegalStateException()),
        Arguments.of(
            "nested causes, long messages",
            new RuntimeException(
                longText,
                new IllegalStateException(longText, new IllegalArgumentException(lines)))),
        Arguments.of(
            "database error, long message",
            new DuplicateKeyException(
                "SQL [" + longText + "]",
                new SQLException("ERROR: " + longText + "\n  Detail: " + longText))),
        Arguments.of(
            "database error, long message with line breaks",
            new UncategorizedSQLException("task", longText, new SQLException(lines))),
        Arguments.of(
            "database error deep in the chain",
            new RuntimeException(
                longText, new RuntimeException(lines, new SQLException("ERROR: " + lines)))),
        Arguments.of(
            "database root without message",
            new DataAccessResourceFailureException(longText, new SQLException())),
        Arguments.of(
            "leading line breaks and a long message",
            new IllegalStateException("\n\n\n" + longText)));
  }

  @ParameterizedTest(
      name = "[MAM-32 AC-6] whatever the failure carries ({0}): at most 200 characters")
  @MethodSource("failuresOfEveryKind")
  void neverLongerThan200(String kind, Throwable failure) {
    assertThat(FailureReason.of(failure)).isNotBlank().hasSizeLessThanOrEqualTo(FailureReason.MAX);
  }

  @ParameterizedTest(name = "[MAM-32 AC-6] whatever the failure carries ({0}): one line")
  @MethodSource("failuresOfEveryKind")
  void alwaysOneLine(String kind, Throwable failure) {
    assertThat(FailureReason.of(failure)).doesNotContain("\n").doesNotContain("\r");
  }

  @ParameterizedTest(name = "[MAM-32 AC-6] a database error ({0}) shows no SQL statement")
  @MethodSource("sqlStatements")
  void noSqlTextOfADatabaseError(String statement) {
    SQLException pg = new SQLException("ERROR: SYNTHETIC FAILURE");
    Throwable failure = new UncategorizedSQLException("PreparedStatementCallback", statement, pg);

    assertThat(failure.getMessage()).as("the wrapper carries the statement").contains(statement);
    assertThat(FailureReason.of(failure)).isEqualTo("SYNTHETIC FAILURE");
  }

  static Stream<String> sqlStatements() {
    return Stream.of(
        "INSERT INTO screening (public_id, npi, reason, started_at) VALUES (?, ?, ?, ?)",
        "UPDATE screening_run SET status = 'FAILED', failure_reason = ? WHERE public_id = ?",
        "SELECT public_id, status FROM screening_run WHERE business_date = ?",
        "INSERT INTO outbox_event (event_type, payload) VALUES (?, ?)",
        "DELETE FROM processed_event");
  }
}
