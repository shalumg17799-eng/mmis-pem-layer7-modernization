package gov.medicaid.pem.provider.api;

import static gov.medicaid.pem.provider.api.KeysetFixtures.CONCURRENT_ACTIVE;
import static gov.medicaid.pem.provider.api.KeysetFixtures.npi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MAM-37 AC-9 (Q-132): a caller that walks every provider of a filter by key (afterNpi), as the
 * T-MSIS extract will. Complete walks return every match exactly once in NPI order; and the
 * concurrent-change cases: between two answers of a walk providers are terminated and approved
 * through the real API, and every provider that stayed ACTIVE for the whole walk must still be
 * returned exactly once, none skipped and none twice, while the walk ends. The same changes make
 * the old offset walk (page and size, no key) miss or repeat a provider: that is the reason for the
 * change, pinned here as the documented behaviour of the old paging, not as a requirement.
 *
 * <p>A real race cannot be repeated, but the only moment at which the data can change under a
 * reader is between its requests, so every change is made between two answers, which makes each
 * case deterministic. Every walk has a limit on the number of answers, so that a service that
 * ignores the key (and returns the first page again and again) fails the test instead of hanging
 * it.
 */
@Timeout(value = 4, unit = TimeUnit.MINUTES)
class ProviderKeysetWalkTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @AfterEach
  void clean() {
    ProviderRows.wipe(jdbc);
  }

  // ---- the walks
  // -----------------------------------------------------------------------------------

  /** The answers of one walk, in order: the NPIs of each answer and its total. */
  private record Walk(List<List<String>> pages, List<Long> totals) {

    List<String> npis() {
      return pages.stream().flatMap(List::stream).toList();
    }

    List<Integer> sizes() {
      return pages.stream().map(List::size).toList();
    }
  }

  private JsonNode page(String filters, int size, String afterNpi, Integer page) throws Exception {
    MockHttpServletRequestBuilder req =
        get("/api/v1/providers").with(PemJwt.analyst()).param("size", String.valueOf(size));
    if (filters != null && !filters.isBlank() && !"-".equals(filters.trim())) {
      for (String pair : filters.trim().split("&")) {
        int eq = pair.indexOf('=');
        req = req.param(pair.substring(0, eq), pair.substring(eq + 1));
      }
    }
    if (afterNpi != null) {
      req = req.param("afterNpi", afterNpi);
    }
    if (page != null) {
      req = req.param("page", String.valueOf(page));
    }
    MvcResult r = mvc.perform(req).andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("npi").asText()));
    return out;
  }

  /**
   * The walk of the contract: the first call has no afterNpi, each next call repeats the filters
   * and the size and sets afterNpi to the last NPI of the answer before; it ends with the first
   * answer that holds fewer than size items (the last one with providers, or an empty one). After
   * each answer {@code afterAnswer} receives the number of the answer (1, 2, ...) and may change
   * data. The walk fails when the answers do not move forward or do not end.
   */
  private Walk walkByKey(String filters, int size, int maxAnswers, IntConsumer afterAnswer)
      throws Exception {
    List<List<String>> pages = new ArrayList<>();
    List<Long> totals = new ArrayList<>();
    String key = null;
    for (int n = 1; ; n++) {
      if (n > maxAnswers) {
        fail(
            "the walk by key did not end after "
                + maxAnswers
                + " answers (filters "
                + filters
                + ", size "
                + size
                + "): the last key was "
                + key
                + "; afterNpi is probably ignored. Answers so far: "
                + pages);
      }
      JsonNode answer = page(filters, size, key, null);
      List<String> npis = npis(answer);
      if (key != null && !npis.isEmpty()) {
        assertThat(npis.get(0))
            .as("answer " + n + " must start after the key " + key + " (afterNpi is ignored?)")
            .isGreaterThan(key);
      }
      pages.add(npis);
      totals.add(answer.path("total").asLong());
      if (npis.size() < size) {
        return new Walk(pages, totals);
      }
      key = npis.get(npis.size() - 1);
      afterAnswer.accept(n);
    }
  }

  /**
   * The old walk: page 0, 1, 2 ... of the same size, until an answer holds fewer than size items.
   */
  private Walk walkByOffset(String filters, int size, int maxAnswers, IntConsumer afterAnswer)
      throws Exception {
    List<List<String>> pages = new ArrayList<>();
    List<Long> totals = new ArrayList<>();
    for (int n = 1; ; n++) {
      if (n > maxAnswers) {
        fail("the offset walk did not end after " + maxAnswers + " answers: " + pages);
      }
      JsonNode answer = page(filters, size, null, n - 1);
      List<String> npis = npis(answer);
      pages.add(npis);
      totals.add(answer.path("total").asLong());
      if (npis.size() < size) {
        return new Walk(pages, totals);
      }
      afterAnswer.accept(n);
    }
  }

  /** The page sizes of a complete walk over count providers: full pages, then the rest (or 0). */
  private static List<Integer> shape(int count, int size) {
    List<Integer> out = new ArrayList<>();
    for (int i = 0; i < count / size; i++) {
      out.add(size);
    }
    out.add(count % size);
    return out;
  }

  private static List<String> full(String threeDigitList) {
    return Stream.of(threeDigitList.trim().split("\\s+")).map(s -> npi(s)).toList();
  }

  // ---- complete walks without any change
  // --------------------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-37 AC-9] a walk by key with size={0} returns the eight providers once")
  @ValueSource(ints = {1, 2, 3, 4, 7, 8, 9, 200})
  @DisplayName(
      "[MAM-37 AC-9] a complete walk by key returns every provider once in ascending NPI order, whatever the page size; full pages, then a short or an empty one")
  void walkReturnsEveryProviderOnce(int size) throws Exception {
    KeysetFixtures.eight(jdbc);

    Walk walk = walkByKey("-", size, 20, n -> {});

    assertThat(walk.npis())
        .containsExactly(
            npi("111"),
            npi("222"),
            npi("333"),
            npi("444"),
            npi("555"),
            npi("666"),
            npi("777"),
            npi("888"));
    assertThat(walk.sizes()).isEqualTo(shape(8, size));
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] a walk over {0} with size={1} returns {2}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          status=A               | 2   | 333 555 666 777 888
          status=A&screenFlag=P  | 3   | 333 555 777 888
          name=RIVERA            | 1   | 111 555 888
          screenFlag=F           | 5   | 444 666
          status=P               | 200 | 111 444
          npi=1003000555         | 2   | 555
          medicaidId=MCD000666   | 2   | 666
          """)
  @DisplayName(
      "[MAM-37 AC-9] a walk over a filter returns exactly the providers that match it, once each, in NPI order")
  void walkOverAFilter(String filters, int size, String expected) throws Exception {
    KeysetFixtures.eight(jdbc);

    Walk walk = walkByKey(filters, size, 20, n -> {});

    assertThat(walk.npis()).containsExactlyElementsOf(full(expected));
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] a walk over a filter that matches nothing ends with its first answer: empty, total 0")
  void walkOverNothing() throws Exception {
    KeysetFixtures.eight(jdbc);

    Walk walk = walkByKey("status=S", 5, 3, n -> {});

    assertThat(walk.pages()).containsExactly(List.of());
    assertThat(walk.totals()).containsExactly(0L);
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] the total is the same on every answer of a walk while the data does not change")
  void totalIsStableWhileNothingChanges() throws Exception {
    KeysetFixtures.eight(jdbc);

    Walk walk = walkByKey("status=A", 2, 10, n -> {});

    assertThat(walk.totals()).containsOnly(5L).hasSize(walk.pages().size());
    assertThat(walk.pages().size()).isGreaterThan(1);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] a walk by key through 450 providers with size={0}")
  @ValueSource(ints = {200, 150, 90})
  @DisplayName(
      "[MAM-37 AC-9] a walk through the 450 ACTIVE providers of AC-9 returns the same list as the database in NPI order, with total 450 on every answer")
  void walkThroughFourHundredAndFifty(int size) throws Exception {
    KeysetFixtures.ac9(jdbc);
    List<String> inTheDatabase =
        jdbc.queryForList(
            "SELECT npi FROM provider WHERE enroll_status = 'A' ORDER BY npi", String.class);

    Walk walk = walkByKey("status=A", size, 10, n -> {});

    assertThat(inTheDatabase).hasSize(450);
    assertThat(walk.npis()).containsExactlyElementsOf(inTheDatabase);
    assertThat(walk.sizes()).isEqualTo(shape(450, size));
    assertThat(walk.totals()).containsOnly(450L);
  }

  // ---- the concurrent-change cases
  // -------------------------------------------------------------------------

  private static final int SIZE = 5;

  /** terminate and approve are the real analyst and sanction calls of the service. */
  private void terminate(String npi) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/v1/providers/{npi}/status-changes", npi)
                    .with(PemJwt.supervisor())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        json.writeValueAsString(
                            Map.of(
                                "action",
                                "TERMINATE",
                                "reason",
                                "LEIE exclusion",
                                "effectiveDate",
                                "2026-01-15"))))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("enrollStatus").asText())
        .as("status of " + npi + " after the termination")
        .isEqualTo("T");
  }

  private void approve(String npi) throws Exception {
    MvcResult r =
        mvc.perform(
                post("/api/v1/providers/{npi}/approval", npi)
                    .with(PemJwt.analyst())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("decision", "APPROVE"))))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("enrollStatus").asText())
        .as("status of " + npi + " after the approval")
        .isEqualTo("A");
  }

  /** After the first answer of a walk: the providers are terminated, then the others approved. */
  private IntConsumer changesAfterTheFirstAnswer(List<String> terminate, List<String> approve) {
    return n -> {
      if (n != 1) {
        return;
      }
      try {
        for (String npi : terminate) {
          terminate(npi);
        }
        for (String npi : approve) {
          approve(npi);
        }
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    };
  }

  /**
   * The cases: a label, the providers terminated after the first answer, the providers approved
   * after it, and what the OLD offset walk of the same size then misses and repeats (the first
   * answer holds 1003002010 to 1003002050; the key after it is 1003002050; 1003002025 lies before
   * the key and 1003002125 after it).
   */
  static Stream<Arguments> changes() {
    return Stream.of(
        Arguments.of(
            "a provider that was already returned is terminated",
            List.of("1003002030"),
            List.of(),
            List.of("1003002060"),
            List.of()),
        Arguments.of(
            "a provider before the key is approved",
            List.of(),
            List.of("1003002025"),
            List.of(),
            List.of("1003002050")),
        Arguments.of(
            "a returned provider is terminated and one before the key is approved",
            List.of("1003002030"),
            List.of("1003002025"),
            List.of(),
            List.of()),
        Arguments.of(
            "a returned provider is terminated and one after the key is approved",
            List.of("1003002030"),
            List.of("1003002125"),
            List.of("1003002060"),
            List.of()),
        Arguments.of(
            "a provider after the key is approved",
            List.of(),
            List.of("1003002125"),
            List.of(),
            List.of()),
        Arguments.of(
            "the provider at the key (the last one returned) is terminated",
            List.of("1003002050"),
            List.of(),
            List.of("1003002060"),
            List.of()),
        Arguments.of(
            "a provider that is not yet returned is terminated",
            List.of("1003002070"),
            List.of(),
            List.of(),
            List.of()),
        Arguments.of(
            "two returned providers are terminated and one provider before and one after the key are approved",
            List.of("1003002030", "1003002040"),
            List.of("1003002025", "1003002125"),
            List.of("1003002060"),
            List.of()));
  }

  private static List<String> stable(List<String> terminated) {
    List<String> stable = new ArrayList<>(CONCURRENT_ACTIVE);
    stable.removeAll(terminated);
    return stable;
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] walk by key while {0}")
  @MethodSource("changes")
  @DisplayName(
      "[MAM-37 AC-9] terminations and approvals between two answers of a walk by key: every provider that stayed ACTIVE is returned exactly once, none twice, in NPI order, and the walk ends")
  void keyWalkSurvivesChanges(
      String label,
      List<String> terminate,
      List<String> approve,
      List<String> offsetMissed,
      List<String> offsetRepeated)
      throws Exception {
    KeysetFixtures.concurrent(jdbc);

    Walk walk = walkByKey("status=A", SIZE, 12, changesAfterTheFirstAnswer(terminate, approve));

    List<String> all = walk.npis();
    Set<String> mayAppear = new HashSet<>(CONCURRENT_ACTIVE);
    mayAppear.addAll(approve);
    assertThat(all).as("ascending NPI order").isSorted();
    assertThat(all).as("no provider twice").doesNotHaveDuplicates();
    assertThat(all).as("every provider that stayed ACTIVE").containsAll(stable(terminate));
    assertThat(all).as("only providers that were ACTIVE at some time").isSubsetOf(mayAppear);
    assertThat(walk.pages().get(0))
        .as("the first answer is read before the changes")
        .containsExactly("1003002010", "1003002020", "1003002030", "1003002040", "1003002050");
    assertThat(walk.sizes().subList(0, walk.sizes().size() - 1))
        .as("every answer but the last is full")
        .containsOnly(SIZE);
    assertThat(walk.sizes().get(walk.sizes().size() - 1)).isLessThan(SIZE);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] total moves with the data while {0}")
  @MethodSource("changes")
  @DisplayName(
      "[MAM-37 AC-9] the total of every answer is the number of all ACTIVE providers at that moment: 20 before the changes, 20 minus the terminated plus the approved after them (it is how a caller notices a change)")
  void totalShowsTheChange(
      String label,
      List<String> terminate,
      List<String> approve,
      List<String> offsetMissed,
      List<String> offsetRepeated)
      throws Exception {
    KeysetFixtures.concurrent(jdbc);

    Walk walk = walkByKey("status=A", SIZE, 12, changesAfterTheFirstAnswer(terminate, approve));

    long after = 20 - terminate.size() + approve.size();
    assertThat(walk.totals().get(0)).isEqualTo(20L);
    assertThat(walk.totals().subList(1, walk.totals().size())).containsOnly(after);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] OLD offset walk while {0}")
  @MethodSource("changes")
  @DisplayName(
      "[MAM-37 AC-9] documented behaviour of the old paging (the reason for Q-132): the same changes make an offset walk miss or repeat a provider that stayed ACTIVE")
  void offsetWalkMissesOrRepeats(
      String label,
      List<String> terminate,
      List<String> approve,
      List<String> offsetMissed,
      List<String> offsetRepeated)
      throws Exception {
    KeysetFixtures.concurrent(jdbc);

    Walk walk = walkByOffset("status=A", SIZE, 12, changesAfterTheFirstAnswer(terminate, approve));

    List<String> all = walk.npis();
    List<String> missed = new ArrayList<>(stable(terminate));
    missed.removeAll(all);
    Set<String> seen = new HashSet<>();
    List<String> repeated = new ArrayList<>();
    for (String npi : all) {
      if (!seen.add(npi) && !repeated.contains(npi)) {
        repeated.add(npi);
      }
    }
    assertThat(missed).as("stayed ACTIVE but never returned").isEqualTo(offsetMissed);
    assertThat(repeated).as("returned more than once").isEqualTo(offsetRepeated);
  }

  // ---- many changes at once, the same every time
  // ------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-37 AC-9] 90 providers, a seeded random mix of terminations and approvals after every answer of a walk with size 7: whoever stayed ACTIVE is returned exactly once, the rest at most once")
  void seededRandomChanges() throws Exception {
    ProviderRows.wipe(jdbc);
    List<String> active = new ArrayList<>();
    List<String> pending = new ArrayList<>();
    for (int i = 1; i <= 60; i++) {
      String npi = String.valueOf(1003003000L + 7L * i);
      ProviderRows.individual(npi).name("ACTIVE" + i, "TESTFIRST").status("A").insert(jdbc);
      active.add(npi);
    }
    for (int i = 1; i <= 30; i++) {
      String npi = String.valueOf(1003003000L + 7L * i + 3L);
      ProviderRows.individual(npi)
          .name("PENDING" + i, "TESTFIRST")
          .status("P")
          .flag("P")
          .riskTier("L")
          .insert(jdbc);
      pending.add(npi);
    }
    Random random = new Random(20261120L);
    Set<String> currentlyActive = new TreeSet<>(active);
    Set<String> currentlyPending = new TreeSet<>(pending);
    Set<String> terminated = new LinkedHashSet<>();
    Set<String> approved = new LinkedHashSet<>();

    Walk walk =
        walkByKey(
            "status=A",
            7,
            30,
            n -> {
              for (int k = 0; k < 3; k++) {
                if (!currentlyActive.isEmpty()) {
                  String npi = pick(currentlyActive, random);
                  jdbc.update("UPDATE provider SET enroll_status = 'T' WHERE npi = ?", npi);
                  currentlyActive.remove(npi);
                  terminated.add(npi);
                }
                if (!currentlyPending.isEmpty()) {
                  String npi = pick(currentlyPending, random);
                  jdbc.update("UPDATE provider SET enroll_status = 'A' WHERE npi = ?", npi);
                  currentlyPending.remove(npi);
                  currentlyActive.add(npi);
                  approved.add(npi);
                }
              }
            });

    List<String> all = walk.npis();
    Set<String> stable = new LinkedHashSet<>(active);
    stable.removeAll(terminated);
    Set<String> mayAppear = new HashSet<>(active);
    mayAppear.addAll(approved);
    assertThat(terminated.size()).as("the test really changed data").isGreaterThanOrEqualTo(15);
    assertThat(approved.size()).as("the test really changed data").isGreaterThanOrEqualTo(15);
    assertThat(all).as("ascending NPI order").isSorted();
    assertThat(all).as("no provider twice").doesNotHaveDuplicates();
    assertThat(all).as("every provider that stayed ACTIVE").containsAll(stable);
    assertThat(all).as("only providers that were ACTIVE at some time").isSubsetOf(mayAppear);
  }

  // ---- a real second thread
  // --------------------------------------------------------------------------------------

  /** Waits until the other thread has made at least this many more changes, or fails. */
  private static void awaitFlips(AtomicInteger flips, int more) throws InterruptedException {
    int target = flips.get() + more;
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
    while (flips.get() < target) {
      if (System.nanoTime() > deadline) {
        fail("the thread that changes providers made no progress (" + flips.get() + " changes)");
      }
      Thread.sleep(2);
    }
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] while another thread keeps flipping 30 providers between ACTIVE and TERMINATED, a walk by key returns each of the 120 providers that never change exactly once, and no provider twice")
  void walkWhileAnotherThreadChangesProviders() throws Exception {
    ProviderRows.wipe(jdbc);
    List<String> steady = new ArrayList<>();
    List<String> flipping = new ArrayList<>();
    for (int i = 1; i <= 150; i++) {
      String npi = String.valueOf(1003004000L + 2L * i);
      ProviderRows.individual(npi).name("PROVIDER" + i, "TESTFIRST").status("A").insert(jdbc);
      (i % 5 == 0 ? flipping : steady).add(npi);
    }
    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicInteger flips = new AtomicInteger();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<?> flipper =
        pool.submit(
            () -> {
              while (!stop.get()) {
                for (String npi : flipping) {
                  if (stop.get()) {
                    return;
                  }
                  jdbc.update(
                      "UPDATE provider SET enroll_status = CASE enroll_status WHEN 'A' THEN 'T' ELSE 'A' END WHERE npi = ?",
                      npi);
                  flips.incrementAndGet();
                }
              }
            });
    Walk walk;
    try {
      walk =
          walkByKey(
              "status=A",
              7,
              60,
              n -> {
                try {
                  awaitFlips(flips, 5);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
              });
    } finally {
      stop.set(true);
      pool.shutdown();
      assertThat(pool.awaitTermination(30, TimeUnit.SECONDS))
          .as("the changing thread stops")
          .isTrue();
    }
    flipper.get(30, TimeUnit.SECONDS);

    List<String> all = walk.npis();
    Set<String> mayAppear = new HashSet<>(steady);
    mayAppear.addAll(flipping);
    assertThat(flips.get()).as("providers were changed during the walk").isGreaterThan(20);
    assertThat(all).as("ascending NPI order").isSorted();
    assertThat(all).as("no provider twice").doesNotHaveDuplicates();
    assertThat(all).as("every provider that never changed").containsAll(steady);
    assertThat(all).as("only known providers").isSubsetOf(mayAppear);
  }

  private static String pick(Collection<String> from, Random random) {
    return new ArrayList<>(from).get(random.nextInt(from.size()));
  }
}
