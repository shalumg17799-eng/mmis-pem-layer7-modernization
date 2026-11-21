package gov.medicaid.pem.tmsis.infrastructure;

import static gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.CHANGED;
import static gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.failureOf;
import static gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.npi;
import static gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.walk;
import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.tmsis.application.FailureReason;
import gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.Directory;
import gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.Outcome;
import gov.medicaid.pem.tmsis.infrastructure.ProviderKeysetReaderTest.Scripted;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-71 AC-5 ("no provider is missed or repeated"), code review of tmsis-service 2026-11-21,
 * MUST-FIX 1 (the walk ends too early), contract provider-service 1.2.0 (the walk ends at the first
 * answer whose items list is empty; an answer with fewer items than size is the last one that holds
 * providers), Q-132, R-PRVTMS01-cbl-00027 (the loop reads until the end of the master).
 *
 * <p>The defect: the reader also ended the walk once the rows it had read reached the total of the
 * answer. A provider that leaves ACTIVE after the key (one already read) and one that enters ACTIVE
 * after the key keep the total, so the second answer, a full one, makes the rows read equal to the
 * total while the last provider, ACTIVE for the whole run, still comes: the walk ended, the run was
 * COMPLETED and the file was one record short. The rows read can also exceed the total after such a
 * pair (one provider read before it left, one that entered), so the total says nothing about where
 * the list ends. The only things that end a walk are the answer itself: an empty one, or one with
 * fewer than 200 providers.
 *
 * <p>The cases of the reviewer: {@code Directory.spaced(400)} (every second NPI 1003002002 to
 * 1003002800), after the first answer 1003002010 (read already) terminated and 1003002501 approved:
 * 1003002800 must be read, in 3 requests. An exact multiple of 200 providers costs one more
 * request, the one that gets the empty answer (corrected expectations in {@link
 * ProviderPagesReaderTest}, Q-097 C).
 */
@Timeout(60)
class ProviderWalkEndTest {

  static final int PAGE = 200;

  /** The reviewer's case: 1003002010 terminated and 1003002501 approved after the first answer. */
  static Directory reviewersCase() {
    Directory providers = Directory.spaced(400);
    providers.afterAnswer(
        1,
        () -> {
          providers.terminate("1003002010");
          providers.approve("1003002501");
        });
    return providers;
  }

  // ---- the reviewer's case
  // ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 400 ACTIVE, 1003002010 terminated and 1003002501 approved after the first answer (the total stays 400): the last provider 1003002800 is read, not lost behind a full answer")
  void lastProviderIsReadAfterAPairedChange() {
    Outcome outcome = walk(reviewersCase());
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.npis()).contains("1003002800");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the same walk gives every provider once: the 400 that were ACTIVE at the start (1003002010 was read before it left) and 1003002501, ascending, 401 rows")
  void everyProviderOfTheWalkIsReadOnce() {
    Directory providers = reviewersCase();
    List<String> expected = new ArrayList<>();
    for (int i = 1; i <= 400; i++) {
      expected.add(providers.nth(i));
    }
    expected.add("1003002501");
    Collections.sort(expected);
    Outcome outcome = walk(providers);
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.npis()).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the same walk is 3 requests: none, after 1003002400, after 1003002798 (the third is short, with the last provider, and ends the walk)")
  void reviewersCaseIsThreeRequests() {
    Directory providers = reviewersCase();
    walk(providers);
    assertThat(providers.asked).containsExactly("none/200", "1003002400/200", "1003002798/200");
  }

  // ---- the same pair of changes after any answer, with more answers
  // ---------------------------------

  @ParameterizedTest(
      name =
          "[MAM-71 AC-5] 600 ACTIVE, one provider already read leaves and one enters after answer {0}"
              + " (total stays 600): the last provider is read and each provider once, in 4 requests")
  @ValueSource(ints = {1, 2})
  void pairedChangeAfterAnyAnswer(int answer) {
    Directory providers = Directory.spaced(600);
    // the one that leaves was read in the first answer; the one that enters is an odd NPI inside
    // the
    // block of the answer after the change (the next one)
    String leaves = providers.nth(5);
    String enters = String.valueOf(Long.parseLong(providers.nth(answer * PAGE + 50)) + 1);
    providers.afterAnswer(
        answer,
        () -> {
          providers.terminate(leaves);
          providers.approve(enters);
        });
    List<String> expected = new ArrayList<>();
    for (int i = 1; i <= 600; i++) {
      expected.add(providers.nth(i));
    }
    expected.add(enters);
    Collections.sort(expected);

    Outcome outcome = walk(providers);

    assertThat(outcome.failure()).isNull();
    assertThat(outcome.npis()).containsExactlyElementsOf(expected);
    assertThat(providers.asked).hasSize(4);
  }

  // ---- an exact multiple of the answer size
  // ---------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-71 AC-5] {0} ACTIVE providers (an exact multiple of 200): the last request has the key"
              + " of the last NPI and gets the empty answer that ends the walk")
  @ValueSource(ints = {200, 400, 600})
  void exactMultipleEndsWithTheEmptyAnswer(int count) {
    Directory providers = Directory.of(count);
    Outcome outcome = walk(providers);
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.rows()).hasSize(count);
    assertThat(providers.asked).hasSize(count / PAGE + 1);
    assertThat(providers.asked.get(providers.asked.size() - 1)).isEqualTo(npi(count) + "/200");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] after the empty answer of an exact multiple the reader answers null for ever and asks for nothing more")
  void endOfFileIsStableAfterTheEmptyAnswer() {
    Directory providers = Directory.of(200);
    ProviderPagesReader reader = new ProviderPagesReader(providers);
    int rows = 0;
    while (reader.read() != null) {
      rows++;
    }
    assertThat(rows).isEqualTo(200);
    assertThat(providers.asked).hasSize(2);
    assertThat(reader.read()).isNull();
    assertThat(reader.read()).isNull();
    assertThat(providers.asked).hasSize(2);
  }

  // ---- the total is not where the list ends
  // ---------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a full answer is followed by another request even when the rows read have reached the total: the total (100 on every answer here) does not end the walk, the empty answer does")
  void totalDoesNotEndTheWalk() {
    Scripted providers =
        new Scripted().answerRange(100, 1, 200).answerRange(100, 201, 400).answer(100);
    Outcome outcome = walk(providers);
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.rows()).hasSize(400);
    assertThat(providers.asked).containsExactly("none/200", npi(200) + "/200", npi(400) + "/200");
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] rows read above the total (401 rows after a pair of changes, total 400) are no failure and no reason to stop: the short answer ends the walk")
  void rowsAboveTheTotalAreNotAFailure() {
    Scripted providers =
        new Scripted().answerRange(400, 1, 200).answerRange(400, 201, 400).answer(400, npi(401));
    Outcome outcome = walk(providers);
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.rows()).hasSize(401);
  }

  // ---- the safety net also looks at the answer that ends the walk
  // -------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 400 ACTIVE, a provider already read is terminated after the last full answer: the empty answer that ends the walk shows a total of 399, so the walk ends with the reason of a changed total")
  void terminationAfterTheLastFullAnswerIsSeenOnTheEmptyAnswer() {
    Directory providers = Directory.of(400);
    providers.afterAnswer(2, () -> providers.terminate(npi(10)));
    Outcome outcome = walk(providers);
    assertThat(FailureReason.of(failureOf(outcome))).isEqualTo(CHANGED);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 400 ACTIVE, a provider is approved after the last full answer: the answer after the key shows a total of 401, so the walk ends with the reason of a changed total")
  void approvalAfterTheLastFullAnswerIsSeenOnTheNextAnswer() {
    Directory providers = Directory.of(400);
    providers.afterAnswer(2, () -> providers.approve("1003009999"));
    Outcome outcome = walk(providers);
    assertThat(FailureReason.of(failureOf(outcome))).isEqualTo(CHANGED);
  }
}
