package gov.medicaid.pem.sanctions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * MAM-55: the SANCIN intake (PRVSAN01 MAIN-LOGIC, PROCESS-SANCTION) with provider-service, the
 * history, the outbox and the run table replaced by in-memory fakes. Each record is applied through
 * RecordSanction on its own; counts, log lines and the end of a run are what the acceptance
 * criteria name (D-024, D-026, D-027).
 */
class SanctionIntakeTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 3, 31);
  static final LocalDate EFFECTIVE = LocalDate.of(2026, 3, 31);
  static final UUID RUN = UUID.fromString("5d1c1c60-0000-4000-8000-000000000055");
  static final String REASON = "SYNTHETIC TEST REASON";

  record Call(String npi, String action, String reason, LocalDate effectiveDate, UUID sanctionId) {}

  record Sent(String type, String subject, Object data) {}

  record Counts(int read, int applied, int notFound, int rejected) {}

  record Link(UUID sanctionId, UUID runId) {}

  record Check(
      UUID runId,
      LocalDate businessDate,
      String npi,
      String action,
      String reason,
      LocalDate date) {}

  record Created(UUID runId, LocalDate businessDate, Instant startedAt) {}

  record Failure(UUID runId, String error, Instant endedAt) {}

  final List<Call> calls = new ArrayList<>();
  final List<Sanction> stored = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  final Map<String, ProviderStatusPort.Outcome> outcomes = new HashMap<>();
  final Set<String> failingHistory = new HashSet<>();

  final SanctionHistory history =
      new SanctionHistory() {
        @Override
        public void add(Sanction s) {
          if (failingHistory.contains(s.npi())) {
            throw new IllegalStateException("history unavailable for " + s.npi());
          }
          stored.add(s);
        }

        @Override
        public SanctionPage find(String npi, int page, int size) {
          return new SanctionPage(List.copyOf(stored), page, size, stored.size());
        }
      };

  /**
   * In-memory sanction_intake_run; terminated is the run's linked T sanctions, as the table does.
   */
  final class FakeRuns implements SanctionIntakeRuns {
    final List<Created> created = new ArrayList<>();
    final List<Counts> counts = new ArrayList<>();
    final List<Link> links = new ArrayList<>();
    final List<Check> checks = new ArrayList<>();
    final List<Instant> completed = new ArrayList<>();
    final List<Failure> failures = new ArrayList<>();
    final Set<String> alreadyApplied = new HashSet<>();

    static String key(String npi, String action, String reason, LocalDate date) {
      return npi + "|" + action + "|" + reason + "|" + date;
    }

    @Override
    public void create(UUID runId, LocalDate businessDate, Instant startedAt) {
      created.add(new Created(runId, businessDate, startedAt));
    }

    @Override
    public void counts(UUID runId, int read, int applied, int notFound, int rejected) {
      counts.add(new Counts(read, applied, notFound, rejected));
    }

    @Override
    public void link(UUID sanctionId, UUID runId) {
      links.add(new Link(sanctionId, runId));
    }

    @Override
    public boolean alreadyApplied(
        UUID runId,
        LocalDate businessDate,
        String npi,
        String action,
        String reason,
        LocalDate date) {
      checks.add(new Check(runId, businessDate, npi, action, reason, date));
      return alreadyApplied.contains(key(npi, action, reason, date));
    }

    @Override
    public void complete(UUID runId, Instant endedAt) {
      completed.add(endedAt);
    }

    @Override
    public void fail(UUID runId, String error, Instant endedAt) {
      failures.add(new Failure(runId, error, endedAt));
    }

    @Override
    public Optional<Run> find(UUID runId) {
      Counts c = counts.isEmpty() ? new Counts(0, 0, 0, 0) : counts.get(counts.size() - 1);
      int terminated =
          (int)
              links.stream()
                  .filter(
                      l ->
                          stored.stream()
                              .anyMatch(
                                  s ->
                                      s.sanctionId().equals(l.sanctionId())
                                          && s.action() == SanctionAction.T))
                  .count();
      return Optional.of(
          new Run(
              runId,
              BUSINESS_DATE,
              completed.isEmpty() ? "RUNNING" : "COMPLETED",
              c.read(),
              c.applied(),
              c.notFound(),
              c.rejected(),
              terminated,
              null,
              NOW,
              completed.isEmpty() ? null : completed.get(0)));
    }

    Counts lastCounts() {
      return counts.get(counts.size() - 1);
    }
  }

  final FakeRuns runs = new FakeRuns();
  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  final RecordSanction record =
      new RecordSanction(
          (npi, action, reason, effectiveDate, sanctionId) -> {
            calls.add(new Call(npi, action, reason, effectiveDate, sanctionId));
            return outcomes.getOrDefault(npi, ProviderStatusPort.Outcome.APPLIED);
          },
          history,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          TransactionOperations.withoutTransaction(),
          clock);

  final SanctionIntake intake = new SanctionIntake(runs, record, clock);

  final ListAppender<ILoggingEvent> log = new ListAppender<>();
  final Logger logger = (Logger) LoggerFactory.getLogger(SanctionIntake.class);

  @BeforeEach
  void captureLog() {
    log.start();
    logger.addAppender(log);
    logger.setLevel(Level.TRACE);
  }

  @AfterEach
  void releaseLog() {
    logger.detachAppender(log);
    logger.setLevel(null);
  }

  List<String> lines() {
    return log.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
  }

  List<String> lines(Level level) {
    return log.list.stream()
        .filter(e -> e.getLevel() == level)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }

  static String rec(String npi, String action, String ccyymmdd) {
    return SancinFile.record(npi, action, REASON, ccyymmdd);
  }

  void process(String... records) {
    intake.process(RUN, BUSINESS_DATE, SancinFile.lines(records), "admin1");
  }

  List<String> calledNpis() {
    return calls.stream().map(Call::npi).toList();
  }

  List<String> storedNpis() {
    return stored.stream().map(s -> s.npi()).toList();
  }

  // ---- AC-1 ------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] opening a run records it for the explicit business date at the clock's time")
  void open() {
    UUID id = intake.open(LocalDate.of(2026, 3, 31));
    assertThat(id).isNotNull();
    assertThat(runs.created).containsExactly(new Created(id, LocalDate.of(2026, 3, 31), NOW));
  }

  @Test
  @DisplayName("[MAM-55 AC-1] every run has an id of its own")
  void openTwice() {
    UUID a = intake.open(BUSINESS_DATE);
    UUID b = intake.open(BUSINESS_DATE);
    assertThat(a).isNotEqualTo(b);
    assertThat(runs.created).extracting(Created::runId).containsExactly(a, b);
  }

  // ---- AC-2 ------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-2] NPI, action, reason and date come from bytes 1-10, 11, 12-51 and 52-59")
  void positions() {
    process(rec("1003000111", "T", "20260331"));
    assertThat(calls).hasSize(1);
    Call c = calls.get(0);
    assertThat(c.npi()).isEqualTo("1003000111");
    assertThat(c.action()).isEqualTo("TERMINATE");
    assertThat(c.reason()).isEqualTo(REASON);
    assertThat(c.effectiveDate()).isEqualTo(LocalDate.of(2026, 3, 31));
  }

  @Test
  @DisplayName("[MAM-55 AC-2] the reason is bytes 12-51 without trailing spaces, inner spaces kept")
  void reasonIsFortyBytesWithoutTrailingSpaces() {
    String full = "A".repeat(19) + " " + "B".repeat(20);
    assertThat(full).hasSize(40);
    process(
        SancinFile.record("1003000111", "T", full, "20260331"),
        SancinFile.record("1003000333", "S", "SHORT", "20260331"));
    assertThat(calls.get(0).reason()).isEqualTo(full);
    assertThat(calls.get(1).reason()).isEqualTo("SHORT");
    assertThat(stored.get(0).reason()).isEqualTo(full);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a reason field of only spaces is passed on as no reason")
  void blankReason() {
    process(SancinFile.record("1003000111", "T", "", "20260331"));
    assertThat(calls.get(0).reason()).isNull();
    assertThat(stored.get(0).reason()).isNull();
  }

  @ParameterizedTest(name = "[MAM-55 AC-2] CCYYMMDD {0} is the date {1} with its 4-digit year")
  @CsvSource({
    "20260331, 2026-03-31",
    "20240229, 2024-02-29",
    "19991231, 1999-12-31",
    "20000101, 2000-01-01",
    "20261231, 2026-12-31"
  })
  void effectiveDate(String ccyymmdd, String expected) {
    process(rec("1003000111", "T", ccyymmdd));
    assertThat(calls.get(0).effectiveDate()).isEqualTo(LocalDate.parse(expected));
    assertThat(stored.get(0).effectiveDate()).isEqualTo(LocalDate.parse(expected));
  }

  @Test
  @DisplayName("[MAM-55 AC-2] every record is read in file order through the last one")
  void fileOrder() {
    process(
        rec("1003000111", "T", "20260331"),
        rec("1003000333", "S", "20260331"),
        rec("1003000222", "R", "20260331"),
        rec("1003000555", "T", "20260331"));
    assertThat(calledNpis())
        .containsExactly("1003000111", "1003000333", "1003000222", "1003000555");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(4, 4, 0, 0));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-2] counts.read grows by one after every record, so a run shows its progress")
  void countsAfterEveryRecord() {
    process(
        rec("1003000111", "T", "20260331"),
        rec("1003000333", "S", "20260331"),
        rec("1003000222", "R", "20260331"));
    assertThat(runs.counts)
        .containsExactly(new Counts(1, 1, 0, 0), new Counts(2, 2, 0, 0), new Counts(3, 3, 0, 0));
  }

  @Test
  @DisplayName("[MAM-55 AC-2] a file with no records completes the run with nothing read")
  void emptyFile() {
    intake.process(RUN, BUSINESS_DATE, new byte[0], "admin1");
    assertThat(calls).isEmpty();
    assertThat(runs.completed).containsExactly(NOW);
    assertThat(runs.find(RUN).orElseThrow().read()).isZero();
  }

  @Test
  @DisplayName("[MAM-55 AC-2] records of a file of 80-byte blocks without line ends are all read")
  void blocksAreRead() {
    intake.process(
        RUN,
        BUSINESS_DATE,
        SancinFile.blocks(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331")),
        "admin1");
    assertThat(calledNpis()).containsExactly("1003000111", "1003000333");
    assertThat(runs.lastCounts().read()).isEqualTo(2);
  }

  // ---- AC-3 ------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-3] T, S and R are applied through provider-service as TERMINATE, SUSPEND and REINSTATE")
  void actionsAreApplied() {
    process(
        rec("1003000111", "T", "20260331"),
        rec("1003000333", "S", "20260331"),
        rec("1003000222", "R", "20260331"));
    assertThat(calls)
        .extracting(Call::npi, Call::action)
        .containsExactly(
            tuple("1003000111", "TERMINATE"),
            tuple("1003000333", "SUSPEND"),
            tuple("1003000222", "REINSTATE"));
    assertThat(stored).extracting(Sanction::resultingStatus).containsExactly("T", "S", "A");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] each record gets one history row with sourceFeed SANCIN and the caller as recordedBy")
  void historyRows() {
    intake.process(
        RUN,
        BUSINESS_DATE,
        SancinFile.lines(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331")),
        "service-account-pem-services");
    assertThat(stored).hasSize(2);
    assertThat(stored)
        .allSatisfy(
            s -> {
              assertThat(s.sourceFeed()).isEqualTo("SANCIN");
              assertThat(s.recordedBy()).isEqualTo("service-account-pem-services");
              assertThat(s.recordedAt()).isEqualTo(NOW);
              assertThat(s.reason()).isEqualTo(REASON);
              assertThat(s.effectiveDate()).isEqualTo(EFFECTIVE);
            });
    assertThat(storedNpis()).containsExactly("1003000111", "1003000333");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] one SanctionApplied event is written per record, with that sanction's data")
  void events() {
    process(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331"));
    assertThat(sent).hasSize(2);
    assertThat(sent).extracting(Sent::type).containsOnly("SanctionApplied");
    assertThat(sent).extracting(Sent::subject).containsExactly("1003000111", "1003000333");
    assertThat(sent.get(0).data())
        .isEqualTo(
            new SanctionAppliedData(
                stored.get(0).sanctionId(), "1003000111", "T", REASON, EFFECTIVE));
    assertThat(sent.get(1).data())
        .isEqualTo(
            new SanctionAppliedData(
                stored.get(1).sanctionId(), "1003000333", "S", REASON, EFFECTIVE));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] the provider-service call carries the id of the sanction that is then kept")
  void sanctionIdIsTheSame() {
    process(rec("1003000111", "T", "20260331"));
    assertThat(calls.get(0).sanctionId()).isEqualTo(stored.get(0).sanctionId());
  }

  @Test
  @DisplayName("[MAM-55 AC-3] every applied sanction is linked to its run")
  void linkedToTheRun() {
    process(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331"));
    assertThat(runs.links)
        .containsExactly(
            new Link(stored.get(0).sanctionId(), RUN), new Link(stored.get(1).sanctionId(), RUN));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] a sanction applies whatever the current status: nothing is checked first (D-027)")
  void noStatusCheck() {
    process(rec("1003000222", "R", "20260331"));
    assertThat(calls).hasSize(1);
    assertThat(runs.lastCounts().applied()).isEqualTo(1);
  }

  // ---- AC-4 ------------------------------------------------------------------------------------

  void fiveRecordsOfAc4() {
    outcomes.put("1003000999", ProviderStatusPort.Outcome.NOT_FOUND);
    process(
        rec("1003000111", "T", "20260331"),
        rec("1003000999", "T", "20260331"),
        rec("1003000333", "X", "20260331"),
        rec("1003000333", "S", "20261331"),
        rec("1003000222", "T", "20260331"));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] five records: read 5, applied 2, notFound 1, rejected 2, and the run completes")
  void counts() {
    fiveRecordsOfAc4();
    assertThat(runs.lastCounts()).isEqualTo(new Counts(5, 2, 1, 2));
    assertThat(runs.completed).containsExactly(NOW);
    assertThat(runs.failures).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-4] the counts are kept after every record, each skipped one included")
  void countsAfterEachSkippedRecord() {
    fiveRecordsOfAc4();
    assertThat(runs.counts)
        .containsExactly(
            new Counts(1, 1, 0, 0),
            new Counts(2, 1, 1, 0),
            new Counts(3, 1, 1, 1),
            new Counts(4, 1, 1, 2),
            new Counts(5, 2, 1, 2));
  }

  @Test
  @DisplayName("[MAM-55 AC-4] history rows and events exist only for the two applied records")
  void onlyAppliedRecordsAreKept() {
    fiveRecordsOfAc4();
    assertThat(storedNpis()).containsExactly("1003000111", "1003000222");
    assertThat(sent).extracting(Sent::subject).containsExactly("1003000111", "1003000222");
    assertThat(runs.links).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] an action X and an impossible date never reach provider-service; the records after them are still processed")
  void rejectedRecordsAreNotSent() {
    fiveRecordsOfAc4();
    assertThat(calledNpis()).containsExactly("1003000111", "1003000999", "1003000222");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-4] an NPI provider-service does not know logs 'PRVSAN01: NPI NOT FOUND <npi>' once")
  void notFoundLine() {
    fiveRecordsOfAc4();
    assertThat(lines(Level.WARN))
        .filteredOn(l -> l.contains("NOT FOUND"))
        .containsExactly("PRVSAN01: NPI NOT FOUND 1003000999");
  }

  @Test
  @DisplayName("[MAM-55 AC-4] a rejected record is logged with its record number")
  void rejectedLines() {
    fiveRecordsOfAc4();
    List<String> warnings = lines(Level.WARN);
    assertThat(warnings)
        .anySatisfy(l -> assertThat(l).contains("RECORD 3", "1003000333", "REJECTED"));
    assertThat(warnings)
        .anySatisfy(l -> assertThat(l).contains("RECORD 4", "REJECTED", "20261331"));
  }

  @Test
  @DisplayName("[MAM-55 AC-4] the run ends with one summary line of the counts")
  void summaryLine() {
    fiveRecordsOfAc4();
    assertThat(lines(Level.INFO))
        .contains(
            "PRVSAN01 run " + RUN + " for 2026-03-31: READ=5 APPLIED=2 NOT-FOUND=1 REJECTED=2");
  }

  @ParameterizedTest(name = "[MAM-55 AC-4] action \"{0}\" is rejected and counted")
  @ValueSource(strings = {"X", "t", "s", "r", " ", "1", "0"})
  void unknownActions(String action) {
    process(rec("1003000333", action, "20260331"));
    assertThat(calls).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 0, 0, 1));
  }

  @ParameterizedTest(name = "[MAM-55 AC-4] the date \"{0}\" is not valid: rejected and counted")
  @ValueSource(
      strings = {
        "20261331",
        "20260230",
        "20230229",
        "20260431",
        "20260000",
        "00000000",
        "2026ABCD",
        "        "
      })
  void invalidDates(String date) {
    process(rec("1003000333", "S", date));
    assertThat(calls).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 0, 0, 1));
  }

  @ParameterizedTest(
      name =
          "[MAM-55 AC-4] the NPI \"{0}\" is not 10 digits: rejected, provider-service not called")
  @ValueSource(strings = {"ABCDEFGHIJ", "100300011", "1003-00111", "          "})
  void invalidNpis(String npi) {
    process(rec(npi, "T", "20260331"));
    assertThat(calls).isEmpty();
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 0, 0, 1));
  }

  @Test
  @DisplayName("[MAM-55 AC-4] the run keeps going after each kind of skipped record")
  void goesOnAfterEverySkippedRecord() {
    outcomes.put("1003000999", ProviderStatusPort.Outcome.NOT_FOUND);
    process(
        rec("1003000999", "T", "20260331"),
        rec("1003000333", "X", "20260331"),
        rec("1003000333", "S", "20261331"),
        rec("1003000111", "T", "20260331"));
    assertThat(storedNpis()).containsExactly("1003000111");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(4, 1, 1, 2));
  }

  // ---- AC-5 ------------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-5] T, S, R, T: four applied and 'PRVSAN01: PROVIDERS TERMINATED=00002'")
  void onlyTerminationsAreCounted() {
    process(
        rec("1003000111", "T", "20260331"),
        rec("1003000333", "S", "20260331"),
        rec("1003000222", "R", "20260331"),
        rec("1003000555", "T", "20260331"));
    assertThat(runs.lastCounts().applied()).isEqualTo(4);
    assertThat(runs.find(RUN).orElseThrow().terminated()).isEqualTo(2);
    assertThat(lines(Level.INFO)).contains("PRVSAN01: PROVIDERS TERMINATED=00002");
  }

  @Test
  @DisplayName("[MAM-55 AC-5] no termination logs the 5-digit counter 00000")
  void noTermination() {
    process(rec("1003000333", "S", "20260331"), rec("1003000222", "R", "20260331"));
    assertThat(lines(Level.INFO)).contains("PRVSAN01: PROVIDERS TERMINATED=00000");
  }

  @Test
  @DisplayName("[MAM-55 AC-5] twelve terminations log 00012: the legacy counter is PIC 9(05)")
  void twelveTerminations() {
    String[] records = new String[12];
    for (int i = 0; i < records.length; i++) {
      records[i] = rec(String.format("10030002%02d", i), "T", "20260331");
    }
    process(records);
    assertThat(lines(Level.INFO)).contains("PRVSAN01: PROVIDERS TERMINATED=00012");
  }

  @Test
  @DisplayName("[MAM-55 AC-5] a rejected or not-found T is not a termination")
  void skippedTerminationsAreNotCounted() {
    outcomes.put("1003000999", ProviderStatusPort.Outcome.NOT_FOUND);
    process(rec("1003000999", "T", "20260331"), rec("1003000333", "T", "20261331"));
    assertThat(lines(Level.INFO)).contains("PRVSAN01: PROVIDERS TERMINATED=00000");
  }

  @Test
  @DisplayName("[MAM-55 AC-5] the terminated line is written once, after the run is completed")
  void terminatedLineOnce() {
    process(rec("1003000111", "T", "20260331"));
    assertThat(lines()).filteredOn(l -> l.contains("PROVIDERS TERMINATED")).hasSize(1);
    assertThat(runs.completed).hasSize(1);
  }

  // ---- AC-6 ------------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-6] a record of 60 bytes ends the run with an error naming record 2")
  void shortRecord() {
    assertThatThrownBy(
            () ->
                process(
                    rec("1003000111", "T", "20260331"),
                    SancinFile.cut(rec("1003000333", "S", "20260331"), 60),
                    rec("1003000222", "R", "20260331")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2")
        .hasMessageContaining("60 bytes")
        .hasMessageContaining("80");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] after an unreadable record 1 keeps its result and record 3 is not processed")
  void shortRecordKeepsRecordOne() {
    assertThatThrownBy(
            () ->
                process(
                    rec("1003000111", "T", "20260331"),
                    SancinFile.cut(rec("1003000333", "S", "20260331"), 60),
                    rec("1003000222", "R", "20260331")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(calledNpis()).containsExactly("1003000111");
    assertThat(storedNpis()).containsExactly("1003000111");
    assertThat(sent).extracting(Sent::subject).containsExactly("1003000111");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 1, 0, 0));
    assertThat(runs.completed).isEmpty();
  }

  @ParameterizedTest(
      name = "[MAM-55 AC-6] a record of {0} bytes is unreadable: the run ends naming it")
  @ValueSource(ints = {0, 1, 60, 79, 81, 200})
  void wrongLengths(int length) {
    String bad =
        length <= 80
            ? SancinFile.cut(rec("1003000333", "S", "20260331"), length)
            : rec("1003000333", "S", "20260331") + "X".repeat(length - 80);
    assertThatThrownBy(() -> process(rec("1003000111", "T", "20260331"), bad))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2 is " + length + " bytes, not 80");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 1, 0, 0));
  }

  @Test
  @DisplayName("[MAM-55 AC-6] an unreadable first record fails the run with nothing read")
  void unreadableFirstRecord() {
    assertThatThrownBy(() -> process(SancinFile.cut(rec("1003000111", "T", "20260331"), 79)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 1");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(0, 0, 0, 0));
    assertThat(calls).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when the first record cannot be applied the run is left with counts of zero, written")
  void providerFailureOnTheFirstRecord() {
    outcomes.put("1003000111", ProviderStatusPort.Outcome.FAILED);
    assertThatThrownBy(
            () -> process(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 1")
        .hasMessageContaining("1003000111");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(0, 0, 0, 0));
    assertThat(calledNpis()).containsExactly("1003000111");
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when keeping the first sanction fails the run is left with counts of zero, written")
  void historyFailureOnTheFirstRecord() {
    failingHistory.add("1003000111");
    assertThatThrownBy(
            () -> process(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 1 NPI 1003000111 could not be applied");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(0, 0, 0, 0));
    assertThat(calledNpis()).containsExactly("1003000111");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] a status change provider-service could not apply ends the run naming record 2 and its NPI")
  void providerFailure() {
    outcomes.put("1003000333", ProviderStatusPort.Outcome.FAILED);
    assertThatThrownBy(
            () ->
                process(
                    rec("1003000111", "T", "20260331"),
                    rec("1003000333", "S", "20260331"),
                    rec("1003000222", "R", "20260331")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2")
        .hasMessageContaining("1003000333");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the failing sanction is not recorded or announced, record 1 keeps its result, record 3 is not processed")
  void providerFailureKeepsOnlyRecordOne() {
    outcomes.put("1003000333", ProviderStatusPort.Outcome.FAILED);
    assertThatThrownBy(
            () ->
                process(
                    rec("1003000111", "T", "20260331"),
                    rec("1003000333", "S", "20260331"),
                    rec("1003000222", "R", "20260331")))
        .isInstanceOf(IllegalStateException.class);
    assertThat(calledNpis()).containsExactly("1003000111", "1003000333");
    assertThat(storedNpis()).containsExactly("1003000111");
    assertThat(sent).extracting(Sent::subject).containsExactly("1003000111");
    assertThat(runs.links).hasSize(1);
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 1, 0, 0));
    assertThat(runs.completed).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] a failure while keeping the sanction also ends the run naming record 2 and its NPI")
  void historyFailure() {
    failingHistory.add("1003000333");
    assertThatThrownBy(
            () ->
                process(
                    rec("1003000111", "T", "20260331"),
                    rec("1003000333", "S", "20260331"),
                    rec("1003000222", "R", "20260331")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2 NPI 1003000333 could not be applied")
        .hasMessageContaining("history unavailable");
    assertThat(storedNpis()).containsExactly("1003000111");
    assertThat(calledNpis()).doesNotContain("1003000222");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 1, 0, 0));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] an unreadable record is not counted as rejected: rejected records are skipped, these end the run")
  void unreadableIsNotRejected() {
    assertThatThrownBy(
            () ->
                process(
                    rec("1003000111", "X", "20260331"),
                    SancinFile.cut(rec("1003000333", "S", "20260331"), 60)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("record 2");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(1, 0, 0, 1));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-6] the run is marked FAILED with the reason at the clock's time and the failure is logged")
  void failedRun() {
    intake.failed(RUN, BUSINESS_DATE, "record 2 is 60 bytes, not 80");
    assertThat(runs.failures)
        .containsExactly(new Failure(RUN, "record 2 is 60 bytes, not 80", NOW));
    assertThat(lines(Level.ERROR))
        .containsExactly(
            "PRVSAN01 run " + RUN + " for 2026-03-31 FAILED: record 2 is 60 bytes, not 80");
    assertThat(runs.completed).isEmpty();
  }

  // ---- AC-7 ------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-7] records an earlier run of the same business date applied are skipped: nothing is sent, kept or announced")
  void rerunSkips() {
    runs.alreadyApplied.add(FakeRuns.key("1003000111", "T", REASON, EFFECTIVE));
    runs.alreadyApplied.add(FakeRuns.key("1003000333", "S", REASON, EFFECTIVE));
    process(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331"));
    assertThat(calls).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(runs.links).isEmpty();
    assertThat(runs.lastCounts()).isEqualTo(new Counts(2, 0, 0, 0));
    assertThat(runs.completed).containsExactly(NOW);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] a skipped record is read but neither applied, not found nor rejected: skipped is 2")
  void skippedCount() {
    runs.alreadyApplied.add(FakeRuns.key("1003000111", "T", REASON, EFFECTIVE));
    runs.alreadyApplied.add(FakeRuns.key("1003000333", "S", REASON, EFFECTIVE));
    process(rec("1003000111", "T", "20260331"), rec("1003000333", "S", "20260331"));
    SanctionIntakeRuns.Run run = runs.find(RUN).orElseThrow();
    assertThat(run.read()).isEqualTo(2);
    assertThat(run.applied()).isZero();
    assertThat(run.skipped()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] the check names the run, the business date and the record's NPI, action, reason and date")
  void checkKey() {
    process(rec("1003000111", "T", "20260331"));
    assertThat(runs.checks)
        .containsExactly(new Check(RUN, BUSINESS_DATE, "1003000111", "T", REASON, EFFECTIVE));
  }

  @Test
  @DisplayName("[MAM-55 AC-7] the reason is checked as an empty text when the record has none")
  void checkKeyWithoutReason() {
    process(SancinFile.record("1003000111", "T", "", "20260331"));
    assertThat(runs.checks.get(0).reason()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-55 AC-7] a skipped record is logged as already applied for the business date")
  void skippedLine() {
    runs.alreadyApplied.add(FakeRuns.key("1003000111", "T", REASON, EFFECTIVE));
    process(rec("1003000111", "T", "20260331"));
    assertThat(lines(Level.INFO))
        .anySatisfy(
            l ->
                assertThat(l)
                    .contains(
                        "RECORD 1", "1003000111", "ALREADY APPLIED", "2026-03-31", "SKIPPED"));
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] a re-run applies the records that were not applied before and skips the others")
  void partialRerun() {
    runs.alreadyApplied.add(FakeRuns.key("1003000111", "T", REASON, EFFECTIVE));
    process(
        rec("1003000111", "T", "20260331"),
        rec("1003000333", "S", "20260331"),
        rec("1003000222", "R", "20260331"));
    assertThat(calledNpis()).containsExactly("1003000333", "1003000222");
    assertThat(runs.lastCounts()).isEqualTo(new Counts(3, 2, 0, 0));
    assertThat(runs.find(RUN).orElseThrow().skipped()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-7] a record that differs in action, reason or date from an applied one is not skipped")
  void differentRecordIsNotSkipped() {
    runs.alreadyApplied.add(FakeRuns.key("1003000111", "T", REASON, EFFECTIVE));
    process(
        rec("1003000111", "S", "20260331"),
        rec("1003000111", "T", "20260430"),
        SancinFile.record("1003000111", "T", "ANOTHER REASON", "20260331"));
    assertThat(calls).hasSize(3);
    assertThat(runs.lastCounts()).isEqualTo(new Counts(3, 3, 0, 0));
  }

  @Test
  @DisplayName("[MAM-55 AC-7] skipped is read minus applied, notFound and rejected")
  void skippedIsDerived() {
    var run =
        new SanctionIntakeRuns.Run(RUN, BUSINESS_DATE, "COMPLETED", 5, 2, 1, 1, 1, null, NOW, NOW);
    assertThat(run.skipped()).isEqualTo(1);
    var rerun =
        new SanctionIntakeRuns.Run(RUN, BUSINESS_DATE, "COMPLETED", 2, 0, 0, 0, 0, null, NOW, NOW);
    assertThat(rerun.skipped()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-55 AC-7] skipped is never negative")
  void skippedNeverNegative() {
    var run =
        new SanctionIntakeRuns.Run(RUN, BUSINESS_DATE, "RUNNING", 1, 2, 0, 0, 0, null, NOW, null);
    assertThat(run.skipped()).isZero();
  }
}
