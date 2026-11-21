package gov.medicaid.pem.revalidation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.revalidation.application.CompleteCase;
import gov.medicaid.pem.revalidation.application.FollowProvider;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import gov.medicaid.pem.testsupport.PemJwt;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcConnectionDetails;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two completions of one case at the same moment (MAM-48 AC-3, AC-4; question Q-132: the concurrent
 * case is a test), and a completion against the cancellation of the case by a provider that leaves
 * ACTIVE (MAM-49 AC-2): exactly one of them closes the case, the other is answered 409 (a request)
 * or finds nothing to close (an event, a status change), and the stored case is the one the winner
 * wrote. Without a row lock a completion reads the case DUE, the other closes it, and its own
 * UPDATE then matches no row but is still answered 200 with a closedAt that was never stored.
 *
 * <p>Two kinds of test, both on the real application with a real Postgres:
 *
 * <ul>
 *   <li>Deterministic: the test holds the row of the case locked in a transaction of its own (a
 *       second connection, outside the pool), starts the callers in threads, proves from
 *       pg_stat_activity that they wait for a lock, and only then lets the first transaction end.
 *       No sleeping: every wait polls a condition with its own time guard.
 *   <li>Natural: two callers released together by a latch, over {@value #ROUNDS} fresh cases.
 * </ul>
 */
@Timeout(240)
class CaseCompletionConcurrencyTest extends IntegrationTest {

  static final LocalDate DUE = LocalDate.of(2026, 2, 28);
  static final String NPI = "1003000222";
  static final int ROUNDS = 30;
  static final long GUARD_SECONDS = 30;
  static final Instant FIRST_AT = Instant.parse("2026-03-02T09:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired JdbcConnectionDetails database;
  @Autowired CompleteCase completeCase;
  @Autowired FollowProvider followProvider;
  @Autowired PlatformTransactionManager transactions;

  ExecutorService threads;

  @BeforeEach
  void setUp() {
    RevalidationFixtures.reset(jdbc);
    threads = Executors.newFixedThreadPool(2);
  }

  @AfterEach
  void tearDown() {
    threads.shutdownNow();
    RevalidationFixtures.reset(jdbc);
  }

  // ---- the stored case --------------------------------------------------------------------------

  record Stored(String status, UUID screeningId, Instant closedAt, int version) {}

  private Stored stored(UUID caseId) {
    return jdbc.queryForObject(
        "SELECT status, screening_id, closed_at, version FROM revalidation_case WHERE public_id = ?",
        (rs, n) -> {
          Timestamp closed = rs.getTimestamp("closed_at");
          return new Stored(
              rs.getString("status"),
              rs.getObject("screening_id", UUID.class),
              closed == null ? null : closed.toInstant(),
              rs.getInt("version"));
        },
        caseId);
  }

  // ---- calls ------------------------------------------------------------------------------------

  private MockHttpServletRequestBuilder completionRequest(
      UUID caseId, UUID screeningId, String result) {
    return post("/api/v1/revalidations/{caseId}/completion", caseId)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"screeningId\":\"" + screeningId + "\",\"result\":\"" + result + "\"}")
        .with(PemJwt.analyst());
  }

  private Callable<MvcResult> completion(UUID caseId, UUID screeningId) {
    MockHttpServletRequestBuilder request = completionRequest(caseId, screeningId, "PASS");
    return () -> mvc.perform(request).andReturn();
  }

  private Callable<Boolean> screeningEvent(String npi, UUID screeningId) {
    return () -> completeCase.byScreening(npi, "REVALIDATION", "PASS", screeningId);
  }

  private static <T> T answer(Future<T> call, String what) throws Exception {
    try {
      return call.get(GUARD_SECONDS, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new AssertionError(what + " was not answered within " + GUARD_SECONDS + " s", e);
    }
  }

  private static int status(MvcResult r) {
    return r.getResponse().getStatus();
  }

  private static void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(status(r)).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    JsonNode body = RevalidationFixtures.read(r.getResponse().getContentAsString());
    assertThat(body.path("status").asInt()).isEqualTo(status);
    assertThat(body.path("title").asText()).isNotBlank();
  }

  private static Instant closedAtOf(MvcResult r) throws Exception {
    return Instant.parse(
        RevalidationFixtures.read(r.getResponse().getContentAsString()).path("closedAt").asText());
  }

  /** Postgres keeps microseconds; the clock of the service has finer ticks. */
  private static void assertSameInstant(Instant actual, Instant expected, String what) {
    assertThat(Duration.between(actual, expected).abs().toNanos())
        .as(what + ": " + actual + " against " + expected)
        .isLessThanOrEqualTo(1_000L);
  }

  // ---- the row lock held by the test ------------------------------------------------------------

  private Connection direct() throws SQLException {
    return DriverManager.getConnection(
        database.getJdbcUrl(), database.getUsername(), database.getPassword());
  }

  /**
   * The row of a case locked by a transaction of the test, on a connection that is not from the
   * pool of the application (so the application can never run out of connections while the test
   * holds this one).
   */
  static final class HeldRow implements AutoCloseable {

    private final Connection connection;

    HeldRow(Connection connection, UUID caseId) throws SQLException {
      this.connection = connection;
      try {
        connection.setAutoCommit(false);
        try (PreparedStatement s =
            connection.prepareStatement(
                "SELECT status FROM revalidation_case WHERE public_id = ? FOR UPDATE")) {
          s.setObject(1, caseId);
          try (ResultSet rs = s.executeQuery()) {
            assertThat(rs.next()).as("the case to lock exists").isTrue();
          }
        }
      } catch (SQLException | RuntimeException | AssertionError e) {
        connection.close();
        throw e;
      }
    }

    /** The first completion, in the first transaction: what CompleteCase writes. */
    void completeAs(UUID caseId, UUID screeningId, Instant at) throws SQLException {
      try (PreparedStatement s =
          connection.prepareStatement(
              "UPDATE revalidation_case SET status = 'COMPLETED', screening_id = ?, closed_at = ?,"
                  + " updated_at = ?, version = version + 1 WHERE public_id = ?")) {
        s.setObject(1, screeningId);
        s.setTimestamp(2, Timestamp.from(at));
        s.setTimestamp(3, Timestamp.from(at));
        s.setObject(4, caseId);
        assertThat(s.executeUpdate()).as("rows changed by the first completion").isEqualTo(1);
      }
    }

    void commit() throws SQLException {
      connection.commit();
    }

    /** Ends the first transaction without a change. */
    void release() throws SQLException {
      connection.rollback();
    }

    @Override
    public void close() throws SQLException {
      try {
        connection.rollback();
      } finally {
        connection.close();
      }
    }
  }

  /**
   * Waits until at least {@code expected} other connections of this database wait for a lock while
   * running a statement on revalidation_case: the proof that the callers are inside the race and
   * wait, not that the test slept long enough.
   */
  private static void awaitLockWaiters(Connection monitor, int expected) {
    Awaitility.await(expected + " statement(s) on revalidation_case wait for a lock")
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(50))
        .until(
            () -> {
              try (PreparedStatement s =
                      monitor.prepareStatement(
                          "SELECT count(*) FROM pg_stat_activity WHERE datname = current_database()"
                              + " AND pid <> pg_backend_pid() AND wait_event_type = 'Lock'"
                              + " AND position('revalidation_case' in query) > 0");
                  ResultSet rs = s.executeQuery()) {
                rs.next();
                return rs.getInt(1) >= expected;
              }
            });
  }

  // ---- a gate that releases callers together ----------------------------------------------------

  /** Holds each caller until all are ready, then lets them go at the same moment. */
  static final class Gate {

    private final CountDownLatch ready;
    private final CountDownLatch go = new CountDownLatch(1);

    Gate(int callers) {
      this.ready = new CountDownLatch(callers);
    }

    <T> Callable<T> hold(Callable<T> call) {
      return () -> {
        ready.countDown();
        if (!go.await(GUARD_SECONDS, TimeUnit.SECONDS)) {
          throw new IllegalStateException("the gate was not opened within " + GUARD_SECONDS + " s");
        }
        return call.call();
      };
    }

    void open() throws InterruptedException {
      if (!ready.await(GUARD_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException(
            "the callers were not ready within " + GUARD_SECONDS + " s");
      }
      go.countDown();
    }
  }

  // ---- scenario: one request waits behind a completion made in another transaction
  // ---------------

  record WaitedBehindFirst(MvcResult waiting, UUID firstScreening, Stored stored) {}

  /**
   * The row of the case is locked; a completion request waits for it; the first transaction closes
   * the case with its own screening and commits.
   */
  private WaitedBehindFirst requestWaitsBehindAFirstCompletion() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, NPI, DUE, "DUE");
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    MvcResult waiting;
    try (Connection monitor = direct();
        HeldRow lock = new HeldRow(direct(), id)) {
      Future<MvcResult> request = threads.submit(completion(id, second));
      awaitLockWaiters(monitor, 1);
      assertThat(request.isDone()).as("the request waits while the case row is locked").isFalse();
      lock.completeAs(id, first, FIRST_AT);
      lock.commit();
      waiting = answer(request, "the request that waited");
    }
    return new WaitedBehindFirst(waiting, first, stored(id));
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a completion that waited for the row of a case closed in the meantime is answered 409 problem+json")
  void waitingRequestIsRefused() throws Exception {
    WaitedBehindFirst run = requestWaitsBehindAFirstCompletion();

    assertProblem(run.waiting(), 409);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a completion that waited for the row of a case closed in the meantime leaves the first screening id stored")
  void waitingRequestKeepsTheFirstScreening() throws Exception {
    WaitedBehindFirst run = requestWaitsBehindAFirstCompletion();

    assertThat(run.stored().screeningId()).isEqualTo(run.firstScreening());
    assertThat(run.stored().status()).isEqualTo("COMPLETED");
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a completion that waited for the row of a case closed in the meantime leaves the one closedAt and one version change")
  void waitingRequestKeepsTheFirstClosedAt() throws Exception {
    WaitedBehindFirst run = requestWaitsBehindAFirstCompletion();

    assertThat(run.stored().closedAt()).isEqualTo(FIRST_AT);
    assertThat(run.stored().version()).isEqualTo(1);
  }

  // ---- scenario: a request waits behind the first request, which is not committed yet -----------

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a second request that arrives while the first is not committed waits and is answered 409; the first answer is the stored case")
  void secondRequestWaitsForTheUncommittedFirst() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, NPI, DUE, "DUE");
    UUID first = UUID.randomUUID();
    UUID second = UUID.randomUUID();
    RevalidationCase firstAnswer;
    MvcResult secondAnswer;
    try (Connection monitor = direct()) {
      AtomicReference<Future<MvcResult>> started = new AtomicReference<>();
      firstAnswer =
          new TransactionTemplate(transactions)
              .execute(
                  tx -> {
                    RevalidationCase done = completeCase.byRequest(id, first, "PASS");
                    started.set(threads.submit(completion(id, second)));
                    awaitLockWaiters(monitor, 1);
                    assertThat(started.get().isDone())
                        .as("the second request waits for the first transaction")
                        .isFalse();
                    return done;
                  });
      secondAnswer = answer(started.get(), "the second request");
    }

    assertProblem(secondAnswer, 409);
    Stored s = stored(id);
    assertThat(s.screeningId()).isEqualTo(first);
    assertThat(s.version()).isEqualTo(1);
    assertSameInstant(s.closedAt(), firstAnswer.closedAt(), "closedAt of the first answer");
  }

  // ---- scenario: two requests wait together for the row ----------------------------------------

  record TwoRequests(UUID id, UUID first, UUID second, MvcResult one, MvcResult two) {

    List<MvcResult> answers() {
      return List.of(one, two);
    }
  }

  /** The row is locked by the test; two requests for the case both wait; the lock is let go. */
  private TwoRequests twoRequestsWaitTogether() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, NPI, DUE, "DUE");
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    MvcResult one;
    MvcResult two;
    try (Connection monitor = direct();
        HeldRow lock = new HeldRow(direct(), id)) {
      Future<MvcResult> first = threads.submit(completion(id, a));
      Future<MvcResult> second = threads.submit(completion(id, b));
      awaitLockWaiters(monitor, 2);
      assertThat(first.isDone() || second.isDone())
          .as("both requests wait while the case row is locked")
          .isFalse();
      lock.release();
      one = answer(first, "the first request");
      two = answer(second, "the second request");
    }
    return new TwoRequests(id, a, b, one, two);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] two requests that wait together for one case: exactly one is answered 200 and the other 409")
  void twoWaitingRequestsOneWins() throws Exception {
    TwoRequests run = twoRequestsWaitTogether();

    assertThat(run.answers().stream().map(CaseCompletionConcurrencyTest::status).sorted())
        .containsExactly(200, 409);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] two requests that wait together for one case: the 409 is a problem+json")
  void twoWaitingRequestsLoserIsAProblem() throws Exception {
    TwoRequests run = twoRequestsWaitTogether();

    MvcResult loser = status(run.one()) == 409 ? run.one() : run.two();
    assertProblem(loser, 409);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] two requests that wait together for one case: the stored case is the one the 200 answer shows")
  void twoWaitingRequestsStoredIsTheWinner() throws Exception {
    TwoRequests run = twoRequestsWaitTogether();

    Stored s = stored(run.id());
    for (MvcResult r : run.answers()) {
      if (status(r) == 200) {
        UUID screening = r == run.one() ? run.first() : run.second();
        assertThat(s.screeningId()).isEqualTo(screening);
        assertSameInstant(s.closedAt(), closedAtOf(r), "closedAt of the 200 answer");
      }
    }
    assertThat(s.version()).as("one change of the case").isEqualTo(1);
  }

  // ---- scenario: a request and a ScreeningCompleted wait together
  // --------------------------------

  record RequestAndEvent(
      UUID id, UUID requestScreening, UUID eventScreening, MvcResult request, boolean eventClosed) {

    boolean requestClosed() {
      return status(request) == 200;
    }

    UUID winnerScreening() {
      return requestClosed() ? requestScreening : eventScreening;
    }
  }

  /**
   * The row is locked by the test; a completion request and a REVALIDATION ScreeningCompleted (
   * CompleteCase.byScreening) for the same provider both wait; the lock is let go.
   */
  private RequestAndEvent requestAndEventWaitTogether() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, NPI, DUE, "DUE");
    UUID requestScreening = UUID.randomUUID();
    UUID eventScreening = UUID.randomUUID();
    MvcResult request;
    boolean eventClosed;
    try (Connection monitor = direct();
        HeldRow lock = new HeldRow(direct(), id)) {
      Future<MvcResult> byRequest = threads.submit(completion(id, requestScreening));
      Future<Boolean> byEvent = threads.submit(screeningEvent(NPI, eventScreening));
      awaitLockWaiters(monitor, 2);
      assertThat(byRequest.isDone() || byEvent.isDone())
          .as("the request and the event wait while the case row is locked")
          .isFalse();
      lock.release();
      request = answer(byRequest, "the request");
      eventClosed = answer(byEvent, "the event");
    }
    return new RequestAndEvent(id, requestScreening, eventScreening, request, eventClosed);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a request and a REVALIDATION ScreeningCompleted that wait together: exactly one closes the case, the request is 200 or 409")
  void requestAndEventOneCloses() throws Exception {
    RequestAndEvent run = requestAndEventWaitTogether();

    assertThat(status(run.request())).isIn(200, 409);
    assertThat(run.requestClosed() ^ run.eventClosed())
        .as(
            "exactly one of request (%s) and event (closed: %s) closes the case",
            status(run.request()), run.eventClosed())
        .isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a request and a REVALIDATION ScreeningCompleted that wait together: the stored case has the screening of the one that closed it")
  void requestAndEventStoredIsTheWinner() throws Exception {
    RequestAndEvent run = requestAndEventWaitTogether();

    Stored s = stored(run.id());
    assertThat(s.status()).isEqualTo("COMPLETED");
    assertThat(s.screeningId()).isEqualTo(run.winnerScreening());
    assertThat(s.version()).as("one change of the case").isEqualTo(1);
  }

  // ---- scenario: two ScreeningCompleted events for one provider ---------------------------------

  @Test
  @DisplayName(
      "[MAM-48 AC-4] two REVALIDATION ScreeningCompleted events for one provider that wait together: exactly one closes the case")
  void twoEventsOneCloses() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, NPI, DUE, "DUE");
    UUID a = UUID.randomUUID();
    UUID b = UUID.randomUUID();
    boolean closedByA;
    boolean closedByB;
    try (Connection monitor = direct();
        HeldRow lock = new HeldRow(direct(), id)) {
      Future<Boolean> eventA = threads.submit(screeningEvent(NPI, a));
      Future<Boolean> eventB = threads.submit(screeningEvent(NPI, b));
      awaitLockWaiters(monitor, 2);
      lock.release();
      closedByA = answer(eventA, "the first event");
      closedByB = answer(eventB, "the second event");
    }

    assertThat(closedByA ^ closedByB)
        .as("exactly one event closes the case (first: %s, second: %s)", closedByA, closedByB)
        .isTrue();
    Stored s = stored(id);
    assertThat(s.screeningId()).isEqualTo(closedByA ? a : b);
    assertThat(s.version()).as("one change of the case").isEqualTo(1);
  }

  // ---- scenario: a completion request and the cancellation by a status change wait together ----

  record RequestAndCancel(UUID id, UUID requestScreening, MvcResult request, Stored stored) {}

  /**
   * The row is locked by the test; the first caller (the completion request, or the
   * ProviderStatusChanged of the provider to S that cancels its open case) waits for it, then the
   * second; the lock is let go. The order in which they wait is the order in which they get the
   * row.
   */
  private RequestAndCancel requestAndCancellationWait(boolean cancelFirst) throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, NPI, DUE, "DUE");
    UUID screening = UUID.randomUUID();
    Callable<Boolean> cancel =
        () -> {
          followProvider.statusChanged(new TrackedProvider(NPI, "S", DUE));
          return true;
        };
    MvcResult request;
    try (Connection monitor = direct();
        HeldRow lock = new HeldRow(direct(), id)) {
      Future<MvcResult> byRequest;
      Future<Boolean> byCancel;
      if (cancelFirst) {
        byCancel = threads.submit(cancel);
        awaitLockWaiters(monitor, 1);
        byRequest = threads.submit(completion(id, screening));
      } else {
        byRequest = threads.submit(completion(id, screening));
        awaitLockWaiters(monitor, 1);
        byCancel = threads.submit(cancel);
      }
      awaitLockWaiters(monitor, 2);
      lock.release();
      request = answer(byRequest, "the request");
      answer(byCancel, "the status change");
    }
    return new RequestAndCancel(id, screening, request, stored(id));
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-2] a provider that leaves ACTIVE cancels the case before a waiting completion gets the row: the completion is answered 409 problem+json")
  void completionAfterTheCancellationIsRefused() throws Exception {
    RequestAndCancel run = requestAndCancellationWait(true);

    assertProblem(run.request(), 409);
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-2] a provider that leaves ACTIVE cancels the case before a waiting completion gets the row: the case stays CANCELLED, with its closedAt and no screening id")
  void completionAfterTheCancellationLeavesItCancelled() throws Exception {
    RequestAndCancel run = requestAndCancellationWait(true);

    assertThat(run.stored().status()).isEqualTo("CANCELLED");
    assertThat(run.stored().closedAt()).isNotNull();
    assertThat(run.stored().screeningId()).isNull();
    assertThat(run.stored().version()).as("one change of the case").isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-2] a completion gets the row before the provider's status change: the request is answered 200 and the completed case is not cancelled")
  void cancellationAfterTheCompletionFindsNothing() throws Exception {
    RequestAndCancel run = requestAndCancellationWait(false);

    assertThat(status(run.request())).isEqualTo(200);
    assertThat(run.stored().status()).isEqualTo("COMPLETED");
    assertThat(run.stored().screeningId()).isEqualTo(run.requestScreening());
    assertThat(run.stored().version()).as("one change of the case").isEqualTo(1);
  }

  @ParameterizedTest(
      name =
          "[MAM-49 AC-2] the tracked status of the provider follows the status change whichever of the two gets the row first (cancel first: {0})")
  @ValueSource(booleans = {true, false})
  void trackedStatusFollowsInBothOrders(boolean cancelFirst) throws Exception {
    requestAndCancellationWait(cancelFirst);

    assertThat(
            jdbc.queryForObject(
                "SELECT enroll_status FROM tracked_provider WHERE npi = ?", String.class, NPI))
        .isEqualTo("S");
  }

  // ---- natural races: callers released together, many fresh cases
  // --------------------------------

  record Pair(UUID id, UUID firstScreening, UUID secondScreening, MvcResult one, MvcResult two) {}

  /** Two requests per fresh case, released together by a latch. */
  private List<Pair> requestPairs() throws Exception {
    List<Pair> pairs = new ArrayList<>();
    for (int i = 0; i < ROUNDS; i++) {
      UUID id = RevalidationFixtures.insertCase(jdbc, "100300" + (1000 + i), DUE, "DUE");
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      Gate gate = new Gate(2);
      Future<MvcResult> one = threads.submit(gate.hold(completion(id, a)));
      Future<MvcResult> two = threads.submit(gate.hold(completion(id, b)));
      gate.open();
      pairs.add(new Pair(id, a, b, answer(one, "request 1"), answer(two, "request 2")));
    }
    return pairs;
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] two requests released together for one case, 30 times: every time exactly one is answered 200 and the other 409")
  void latchedRequestsOneWins() throws Exception {
    List<Pair> pairs = requestPairs();

    for (int i = 0; i < pairs.size(); i++) {
      Pair p = pairs.get(i);
      assertThat(List.of(status(p.one()), status(p.two())))
          .as("round %d: the answers of the two requests", i)
          .containsExactlyInAnyOrder(200, 409);
    }
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] two requests released together for one case, 30 times: the stored case equals the 200 answer every time")
  void latchedRequestsStoredIsTheWinner() throws Exception {
    List<Pair> pairs = requestPairs();

    for (int i = 0; i < pairs.size(); i++) {
      Pair p = pairs.get(i);
      Stored s = stored(p.id());
      assertThat(s.version()).as("round %d: one change of the case", i).isEqualTo(1);
      for (MvcResult r : List.of(p.one(), p.two())) {
        if (status(r) == 200) {
          UUID screening = r == p.one() ? p.firstScreening() : p.secondScreening();
          assertThat(s.screeningId())
              .as("round %d: screening id stored with the case answered 200", i)
              .isEqualTo(screening);
          assertSameInstant(s.closedAt(), closedAtOf(r), "round " + i + ": closedAt");
        }
      }
    }
  }

  /** A request and a ScreeningCompleted per fresh case, released together by a latch. */
  private List<RequestAndEvent> requestAndEventPairs() throws Exception {
    List<RequestAndEvent> pairs = new ArrayList<>();
    for (int i = 0; i < ROUNDS; i++) {
      String npi = "100300" + (2000 + i);
      UUID id = RevalidationFixtures.insertCase(jdbc, npi, DUE, "DUE");
      UUID requestScreening = UUID.randomUUID();
      UUID eventScreening = UUID.randomUUID();
      Gate gate = new Gate(2);
      Future<MvcResult> byRequest = threads.submit(gate.hold(completion(id, requestScreening)));
      Future<Boolean> byEvent = threads.submit(gate.hold(screeningEvent(npi, eventScreening)));
      gate.open();
      pairs.add(
          new RequestAndEvent(
              id,
              requestScreening,
              eventScreening,
              answer(byRequest, "the request"),
              answer(byEvent, "the event")));
    }
    return pairs;
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a request and a ScreeningCompleted released together, 30 times: every time exactly one closes the case")
  void latchedRequestAndEventOneCloses() throws Exception {
    List<RequestAndEvent> pairs = requestAndEventPairs();

    for (int i = 0; i < pairs.size(); i++) {
      RequestAndEvent p = pairs.get(i);
      assertThat(status(p.request())).as("round %d: the answer of the request", i).isIn(200, 409);
      assertThat(p.requestClosed() ^ p.eventClosed())
          .as(
              "round %d: exactly one of request (%s) and event (closed: %s) closes the case",
              i, status(p.request()), p.eventClosed())
          .isTrue();
    }
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] a request and a ScreeningCompleted released together, 30 times: the stored case carries the screening of the one that closed it")
  void latchedRequestAndEventStoredIsTheWinner() throws Exception {
    List<RequestAndEvent> pairs = requestAndEventPairs();

    for (int i = 0; i < pairs.size(); i++) {
      RequestAndEvent p = pairs.get(i);
      Stored s = stored(p.id());
      assertThat(s.screeningId())
          .as("round %d: screening id of the winner", i)
          .isEqualTo(p.winnerScreening());
      assertThat(s.version()).as("round %d: one change of the case", i).isEqualTo(1);
    }
  }
}
