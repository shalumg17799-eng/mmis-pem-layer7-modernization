package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.FailureReason;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-71 AC-5 ("no provider is missed or repeated"), code review of tmsis-service 2026-11-20,
 * MUST-FIX 1, with the user's decision Q-132 (2026-11-20 20:22): the reader walks the ACTIVE
 * providers by key (contract provider-service 1.2.0: afterNpi, the NPI of the last row of the
 * answer before), so a provider that is approved or terminated while the run reads can no longer
 * shift the pages; and it keeps a safety net that ends the walk with a short, clear reason (the run
 * is then FAILED, D-024) when an answer cannot be right.
 *
 * <p>What the safety net must notice, and nothing less: (1) an NPI that is not greater than the one
 * before it, inside an answer or between two answers (a repeated NPI, an out-of-order answer, an
 * answer that does not respect the key, as a provider-service older than 1.2.0 answers when it
 * ignores afterNpi); (2) a total that differs from the total of the first answer (a provider
 * entered or left ACTIVE during the walk: the file would mix two moments; the safer default of the
 * user's order, reported as a decision for the lead). A total that stays the same while one
 * provider left and another entered is not a change the reader can see; by key it is still right:
 * every provider that is ACTIVE for the whole walk is read exactly once.
 *
 * <p>The interface the tests assume (the java-developer adds it to ActiveProviders; {@code page}
 * stays, ProviderServiceClientTest calls it): {@code Page after(String afterNpi, int size)}, with
 * {@code afterNpi} null for the first request. The fakes of this class answer exactly as the
 * contract says; they declare {@code after} without an {@code @Override} only because the port does
 * not have it yet.
 */
@Timeout(60)
class ProviderKeysetReaderTest {

  /** The reason of the changed total (the user's order, Q-132). */
  static final String CHANGED = "provider data changed during the extract; run it again";

  /** The NPI of the i-th provider (1-based): 1003001001, 1003001002, ... */
  static String npi(int i) {
    return String.format("1003%06d", 1000 + i);
  }

  static ProviderRow row(String npi) {
    return new ProviderRow(npi, "M" + npi.substring(2), "A", LocalDate.parse("2026-01-15"));
  }

  /**
   * A provider-service as contract 1.2.0 says, whose ACTIVE set can change between two answers.
   * total is the number of ALL ACTIVE providers at the moment of the answer, never only those after
   * the key.
   */
  static final class Directory implements ActiveProviders {
    private final List<String> active = new ArrayList<>();
    private final Map<Integer, Runnable> afterAnswer = new HashMap<>();
    final List<String> asked = new ArrayList<>();
    private int answers;

    /** count providers with the NPIs npi(1) to npi(count). */
    static Directory of(int count) {
      Directory d = new Directory();
      for (int i = 1; i <= count; i++) {
        d.active.add(npi(i));
      }
      return d;
    }

    /** count providers with every second NPI: 1003002002, 1003002004, ... (odd ones are free). */
    static Directory spaced(int count) {
      Directory d = new Directory();
      for (int i = 1; i <= count; i++) {
        d.active.add(String.format("1003%06d", 2000 + 2 * i));
      }
      return d;
    }

    String nth(int n) {
      return active.get(n - 1);
    }

    void approve(String npi) {
      active.add(npi);
      Collections.sort(active);
    }

    void terminate(String npi) {
      active.remove(npi);
    }

    /** After the n-th answer (1 = the first) and before the next one, the data changes. */
    void afterAnswer(int n, Runnable change) {
      afterAnswer.put(n, change);
    }

    /** The offset form of 1.1.0; the reader must not use it any more. */
    @Override
    public Page page(int page, int size) {
      asked.add("page" + page + "/" + size);
      int from = Math.min(page * size, active.size());
      return answered(active.subList(from, Math.min(from + size, active.size())));
    }

    /** The keyset form of 1.2.0: the matches after the key, ascending, at most size of them. */
    public Page after(String afterNpi, int size) {
      asked.add((afterNpi == null ? "none" : afterNpi) + "/" + size);
      return answered(
          active.stream()
              .filter(n -> afterNpi == null || n.compareTo(afterNpi) > 0)
              .limit(size)
              .toList());
    }

    private Page answered(List<String> npis) {
      Page page =
          new Page(npis.stream().map(ProviderKeysetReaderTest::row).toList(), active.size());
      answers++;
      Runnable change = afterAnswer.remove(answers);
      if (change != null) {
        change.run();
      }
      return page;
    }
  }

  /**
   * A provider-service that gives the answers the test scripted, whatever it is asked: one that
   * breaks its contract. Asking for an answer that was not scripted is a test failure (a reader
   * that does not stop).
   */
  static final class Scripted implements ActiveProviders {
    private final List<Page> answers = new ArrayList<>();
    final List<String> asked = new ArrayList<>();

    Scripted answer(long total, String... npis) {
      answers.add(new Page(Arrays.stream(npis).map(ProviderKeysetReaderTest::row).toList(), total));
      return this;
    }

    /** An answer of the NPIs npi(from) to npi(to). */
    Scripted answerRange(long total, int from, int to) {
      List<String> npis = new ArrayList<>();
      for (int i = from; i <= to; i++) {
        npis.add(npi(i));
      }
      return answer(total, npis.toArray(String[]::new));
    }

    @Override
    public Page page(int page, int size) {
      asked.add("page" + page + "/" + size);
      return next();
    }

    public Page after(String afterNpi, int size) {
      asked.add((afterNpi == null ? "none" : afterNpi) + "/" + size);
      return next();
    }

    private Page next() {
      if (asked.size() > answers.size()) {
        throw new AssertionError(
            "the reader asked for answer "
                + asked.size()
                + " but only "
                + answers.size()
                + " were scripted: it did not stop");
      }
      return answers.get(asked.size() - 1);
    }
  }

  /** What a walk handed out, and the failure that ended it (null when it ended with null = EOF). */
  record Outcome(List<ProviderRow> rows, RuntimeException failure) {

    List<String> npis() {
      return rows.stream().map(ProviderRow::npi).toList();
    }
  }

  /** Reads until the end of the file or the first failure; never more than 100 000 rows. */
  static Outcome walk(ActiveProviders providers) {
    ProviderPagesReader reader = new ProviderPagesReader(providers);
    List<ProviderRow> rows = new ArrayList<>();
    try {
      ProviderRow row;
      while ((row = reader.read()) != null) {
        rows.add(row);
        if (rows.size() > 100_000) {
          throw new AssertionError("the reader does not stop");
        }
      }
      return new Outcome(rows, null);
    } catch (RuntimeException e) {
      return new Outcome(rows, e);
    }
  }

  /** The walk must have ended with a failure; fails with the rows it handed out otherwise. */
  static RuntimeException failureOf(Outcome outcome) {
    assertThat(outcome.failure())
        .as(
            "the walk must fail, but it completed with %d rows (first %s, last %s)",
            outcome.rows().size(),
            outcome.rows().isEmpty() ? "-" : outcome.rows().get(0).npi(),
            outcome.rows().isEmpty() ? "-" : outcome.rows().get(outcome.rows().size() - 1).npi())
        .isNotNull();
    return outcome.failure();
  }

  /** The reason the run would show: one short sentence, not just the name of an exception. */
  static void assertShortClearReason(RuntimeException failure) {
    String reason = FailureReason.of(failure);
    assertThat(reason).isNotBlank().contains(" ").doesNotContain("Exception");
    assertThat(reason).hasSizeLessThanOrEqualTo(FailureReason.MAX);
  }

  /** A row is never handed out twice and the rows handed out never go back. */
  static void assertNoRepeatAndAscending(Outcome outcome) {
    assertThat(outcome.npis()).doesNotHaveDuplicates().isSorted();
  }

  // ---- the data changes while the run reads (the reviewer's cases)
  // -------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] 401 ACTIVE, the provider #5 is terminated after the first answer: the walk ends with 'provider data changed during the extract; run it again', not with 400 rows without #201")
  void terminatedBeforeTheBoundary() {
    Directory providers = Directory.of(401);
    providers.afterAnswer(1, () -> providers.terminate(npi(5)));
    Outcome outcome = walk(providers);
    assertThat(FailureReason.of(failureOf(outcome))).isEqualTo(CHANGED);
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider is approved before the boundary after the first answer: the walk ends with the reason of a changed total, the boundary provider is not written twice")
  void approvedBeforeTheBoundary() {
    Directory providers = Directory.spaced(401);
    providers.afterAnswer(1, () -> providers.approve("1003002101"));
    Outcome outcome = walk(providers);
    assertThat(FailureReason.of(failureOf(outcome))).isEqualTo(CHANGED);
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider is approved after the boundary (the end of the list) after the first answer: the total is 402, not 401, so the walk ends with the reason of a changed total (the safer default)")
  void approvedAfterTheBoundary() {
    Directory providers = Directory.of(401);
    providers.afterAnswer(1, () -> providers.approve("1003009999"));
    assertThat(FailureReason.of(failureOf(walk(providers)))).isEqualTo(CHANGED);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider is terminated after the boundary after the first answer: the total is 400, not 401, so the walk ends with the reason of a changed total")
  void terminatedAfterTheBoundary() {
    Directory providers = Directory.of(401);
    providers.afterAnswer(1, () -> providers.terminate(npi(300)));
    assertThat(FailureReason.of(failureOf(walk(providers)))).isEqualTo(CHANGED);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the total is compared on every answer, not only on the second: a change after the third of five answers ends the walk")
  void changeOnALaterAnswer() {
    Directory providers = Directory.of(1000);
    providers.afterAnswer(3, () -> providers.approve("1003009999"));
    Outcome outcome = walk(providers);
    assertThat(FailureReason.of(failureOf(outcome))).isEqualTo(CHANGED);
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] one provider leaves ACTIVE before the boundary and one enters after it (the total stays 401, no change can be seen): by key every provider is read once, none is missed, #201 is there")
  void pairedChangeIsStillReadRight() {
    Directory providers = Directory.of(401);
    providers.afterAnswer(
        1,
        () -> {
          providers.terminate(npi(5));
          providers.approve("1003009999");
        });
    Outcome outcome = walk(providers);
    assertThat(outcome.failure()).isNull();
    List<String> expected = new ArrayList<>();
    for (int i = 1; i <= 401; i++) {
      expected.add(npi(i));
    }
    expected.add("1003009999");
    assertThat(outcome.npis()).containsExactlyElementsOf(expected);
    assertThat(providers.asked).containsExactly("none/200", npi(200) + "/200", npi(400) + "/200");
  }

  // ---- the key is the last NPI of the answer before
  // ----------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the key of a request is the last NPI of the answer before, not a count or a page number: 200 NPIs ending in 1003001399 give the key 1003001399")
  void keyIsTheLastNpiOfTheAnswerBefore() {
    String[] first = new String[200];
    for (int k = 0; k < 200; k++) {
      first[k] = String.format("1003%06d", 1001 + 2 * k);
    }
    String[] second = new String[100];
    for (int k = 0; k < 100; k++) {
      second[k] = String.format("1003%06d", 1401 + 2 * k);
    }
    Scripted providers = new Scripted().answer(300, first).answer(300, second);
    Outcome outcome = walk(providers);
    assertThat(providers.asked).containsExactly("none/200", "1003001399/200");
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.rows()).hasSize(300);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] an empty answer ends the walk (contract 1.2.0), even if the total says there are more: no failure, no further request")
  void emptyAnswerEndsTheWalk() {
    Scripted providers =
        new Scripted().answerRange(600, 1, 200).answerRange(600, 201, 400).answer(600);
    Outcome outcome = walk(providers);
    assertThat(providers.asked).containsExactly("none/200", npi(200) + "/200", npi(400) + "/200");
    assertThat(outcome.failure()).isNull();
    assertThat(outcome.rows()).hasSize(400);
  }

  // ---- an answer that cannot be right
  // ------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] an answer that starts with the key itself (the last NPI is repeated) ends the walk with a short reason; nothing is handed out twice")
  void answerRepeatsTheLastNpi() {
    Scripted providers = new Scripted().answerRange(400, 1, 200).answerRange(400, 200, 399);
    Outcome outcome = walk(providers);
    assertShortClearReason(failureOf(outcome));
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider-service that ignores the key (before 1.2.0: the first page again) ends the walk with a short reason after the second answer; no NPI is handed out twice")
  void answerIgnoresTheKey() {
    Scripted providers = new Scripted().answerRange(400, 1, 200).answerRange(400, 1, 200);
    Outcome outcome = walk(providers);
    assertShortClearReason(failureOf(outcome));
    assertNoRepeatAndAscending(outcome);
    assertThat(providers.asked).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] an answer whose first NPI is below the key (1003001150, the key is 1003001200) ends the walk with a short reason")
  void firstNpiBelowTheKey() {
    Scripted providers = new Scripted().answerRange(400, 1, 200).answerRange(400, 150, 349);
    Outcome outcome = walk(providers);
    assertShortClearReason(failureOf(outcome));
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] an answer that is out of order inside (1003001001, 1003001003, 1003001002) ends the walk with a short reason; rows already handed out never go back")
  void outOfOrderInsideAnAnswer() {
    Scripted providers = new Scripted().answer(3, npi(1), npi(3), npi(2));
    Outcome outcome = walk(providers);
    assertShortClearReason(failureOf(outcome));
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] an answer that repeats an NPI inside (1003001001, 1003001002, 1003001002) ends the walk with a short reason")
  void repeatedInsideAnAnswer() {
    Scripted providers = new Scripted().answer(3, npi(1), npi(2), npi(2));
    Outcome outcome = walk(providers);
    assertShortClearReason(failureOf(outcome));
    assertNoRepeatAndAscending(outcome);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider-service that lists in descending order (1003001003, 1003001002, 1003001001) ends the walk with a short reason: the key means nothing on such answers")
  void descendingAnswer() {
    Scripted providers = new Scripted().answer(3, npi(3), npi(2), npi(1));
    Outcome outcome = walk(providers);
    assertShortClearReason(failureOf(outcome));
    assertNoRepeatAndAscending(outcome);
  }
}
