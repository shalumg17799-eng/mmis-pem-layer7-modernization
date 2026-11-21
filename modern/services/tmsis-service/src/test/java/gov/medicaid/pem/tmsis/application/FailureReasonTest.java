package gov.medicaid.pem.tmsis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.sql.SQLException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;

/**
 * The reason stored with and shown for a failed run (MAM-75 AC-6, D-024): the first line of the
 * root cause's message, at most 200 characters, with no SQL statement text; a database message that
 * carries the statement before its own words is cut down to the database's words. The runs that the
 * code review of 2026-11-20 asks to fail with "provider data changed during the extract; run it
 * again" and "interrupted: tmsis-service stopped during the run" show exactly their sentence
 * through this class. These unit tests cover every branch, so that mutation testing of the
 * application package does not depend on the integration tests.
 */
class FailureReasonTest {

  private static final String CHANGED = "provider data changed during the extract; run it again";
  private static final String INTERRUPTED = "interrupted: tmsis-service stopped during the run";

  // ---- a plain message is the reason, unchanged -------------------------------------------------

  @ParameterizedTest(name = "[MAM-75 AC-6] the sentence \"{0}\" is shown unchanged")
  @ValueSource(
      strings = {
        CHANGED,
        INTERRUPTED,
        "provider-service answered without a provider page",
        "job PRVTMS01 not started: no job instance"
      })
  void aSentenceIsShownUnchanged(String sentence) {
    assertThat(FailureReason.of(new IllegalStateException(sentence))).isEqualTo(sentence);
  }

  @Test
  @DisplayName("[MAM-75 AC-6] only the first line of the message is shown")
  void onlyTheFirstLine() {
    assertThat(FailureReason.of(new IllegalStateException("first line\nsecond line\nthird")))
        .isEqualTo("first line");
  }

  @Test
  @DisplayName("[MAM-75 AC-6] the reason of a failure is the message of its root cause")
  void rootCauseWins() {
    Throwable failure =
        new IllegalStateException(
            "outer words", new RuntimeException("inner", new IOException("root words")));
    assertThat(FailureReason.of(failure)).isEqualTo("root words");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a message with white space around it is shown stripped, and a message of white space only gives the name of the exception")
  void whiteSpaceIsStripped() {
    assertThat(FailureReason.of(new IllegalStateException("  padded words \n")))
        .isEqualTo("padded words");
    assertThat(FailureReason.of(new IllegalStateException("   \n  ")))
        .isEqualTo("IllegalStateException");
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a failure without a message is shown by the name of its class")
  void noMessageShowsTheClassName() {
    assertThat(FailureReason.of(new IllegalArgumentException()))
        .isEqualTo("IllegalArgumentException");
    assertThat(FailureReason.of(new IllegalStateException("outer", new IOException())))
        .isEqualTo("IOException");
  }

  // ---- a database message: the database's own words
  // ----------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a database message that carries the statement first is cut down to the database's words: the SQL text and the ERROR: mark are gone")
  void statementBeforeTheDatabaseWords() {
    SQLException pg =
        new SQLException(
            "ERROR: duplicate key value violates unique constraint \"extract_archive_generation_key\"\n"
                + "  Detail: Key (generation)=(3) already exists.");
    Throwable failure =
        new DuplicateKeyException(
            "PreparedStatementCallback; SQL [INSERT INTO extract_archive (generation) VALUES (?)];"
                + " ERROR: duplicate key value violates unique constraint \"extract_archive_generation_key\"",
            pg);
    assertThat(FailureReason.of(failure))
        .isEqualTo(
            "duplicate key value violates unique constraint \"extract_archive_generation_key\"")
        .doesNotContain("INSERT");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a statement in front of the ERROR: mark is cut: the words start right after the mark")
  void markInTheMiddleOfTheLine() {
    assertThat(
            FailureReason.of(
                new IllegalStateException(
                    "could not execute statement [INSERT INTO x]; ERROR: lock timeout")))
        .isEqualTo("lock timeout");
  }

  @Test
  @DisplayName("[MAM-75 AC-6] an ERROR: mark at the very start of the line is cut too")
  void markAtTheStartOfTheLine() {
    assertThat(FailureReason.of(new SQLException("ERROR: relation \"extract_run\" does not exist")))
        .isEqualTo("relation \"extract_run\" does not exist");
  }

  @ParameterizedTest(name = "[MAM-75 AC-6] the detail after the database's words is cut: {0}")
  @ValueSource(
      strings = {
        "ERROR: deadlock detected Where: SQL statement",
        "ERROR: deadlock detected Detail: Process 5 waits",
        "ERROR: deadlock detected Hint: See the server log",
        "ERROR: deadlock detected Call getNextException to see other errors"
      })
  void detailIsCut(String message) {
    assertThat(FailureReason.of(new SQLException(message))).isEqualTo("deadlock detected");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] the earliest of several detail markers ends the words: Detail before Where gives the words before Detail")
  void earliestDetailMarkerWins() {
    assertThat(
            FailureReason.of(
                new SQLException("ERROR: lock timeout Detail: row 7 Where: SQL statement")))
        .isEqualTo("lock timeout");
    assertThat(
            FailureReason.of(
                new SQLException("ERROR: lock timeout Where: SQL statement Detail: row 7")))
        .isEqualTo("lock timeout");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a database message that has only a detail after its mark has no words: the name of the exception is shown")
  void nothingBeforeTheDetail() {
    assertThat(FailureReason.of(new SQLException("ERROR:  Where: SQL statement")))
        .isEqualTo("SQLException");
  }

  // ---- the prefix and the limit ----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a run that could not be started shows its fixed beginning and then the reason")
  void prefixIsKept() {
    assertThat(
            FailureReason.of(
                "could not be started: ", new IllegalStateException("no job instance")))
        .isEqualTo("could not be started: no job instance");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] a prefix with nothing after it is followed by the name of the exception")
  void prefixWithoutWords() {
    assertThat(FailureReason.of("could not be started: ", new IllegalStateException()))
        .isEqualTo("could not be started: IllegalStateException");
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a reason of 200 characters is shown whole, one of 201 is cut to 200")
  void limitIsTwoHundred() {
    assertThat(FailureReason.MAX).isEqualTo(200);
    String whole = "x".repeat(200);
    assertThat(FailureReason.of(new IllegalStateException(whole))).isEqualTo(whole);
    assertThat(FailureReason.of(new IllegalStateException(whole + "y"))).isEqualTo(whole);
  }

  @Test
  @DisplayName("[MAM-75 AC-6] the prefix counts towards the 200 characters")
  void prefixCountsTowardsTheLimit() {
    String reason =
        FailureReason.of("could not be started: ", new IllegalStateException("w".repeat(300)));
    assertThat(reason).hasSize(200).startsWith("could not be started: w");
  }
}
