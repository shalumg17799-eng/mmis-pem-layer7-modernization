package gov.medicaid.pem.revalidation.application;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * The store ports of the application layer in memory, with the rules of JdbcRevalidationStore: a
 * case is closed or moved to IN_SCREENING only from an open state, tracked providers are listed by
 * NPI. Every call that changes something is also written to {@link #calls}, in order, so a test can
 * say what happened before what. The unit tests of the application classes use it; it is a fake of
 * the ports, never of the class under test.
 */
final class InMemoryStore
    implements CaseStore, SweepRuns, CaseCompletions, CaseStages, ProviderTracking {

  /** A call of SweepRuns.complete. */
  record Completion(UUID runId, int queued, int noDueDate, byte[] file, Instant endedAt) {}

  /** A call of SweepRuns.fail. */
  record Failure(UUID runId, String error, Instant endedAt) {}

  /** A call of EventOutbox.enqueue. */
  record Event(String type, String subject, Object data) {}

  /** A call of CaseCompletions.close. */
  record Closing(UUID caseId, CaseStatus status, UUID screeningId, Instant at) {}

  static final Instant EARLIER = Instant.parse("2026-03-01T10:00:00Z");
  static final Instant CLOSED = Instant.parse("2026-03-15T10:00:00Z");

  final List<String> calls = new ArrayList<>();
  final Map<String, TrackedProvider> tracked = new TreeMap<>();
  final Map<UUID, RevalidationCase> cases = new LinkedHashMap<>();
  final Map<UUID, UUID> openedByRun = new HashMap<>();
  final List<SweepRun> created = new ArrayList<>();
  final List<Completion> completions = new ArrayList<>();
  final List<Failure> failures = new ArrayList<>();
  final List<Closing> closings = new ArrayList<>();
  final List<Event> events = new ArrayList<>();

  /** When set, the next EventOutbox.enqueue / CaseStore.open throws it. */
  RuntimeException failOnEvent;

  RuntimeException failOnOpen;

  final EventOutbox outbox =
      (type, subject, data) -> {
        if (failOnEvent != null) {
          throw failOnEvent;
        }
        calls.add("event:" + type + ":" + subject);
        events.add(new Event(type, subject, data));
      };

  // ---- fixtures -------------------------------------------------------------------------------

  void track(String npi, String status, LocalDate due) {
    tracked.put(npi, new TrackedProvider(npi, status, due));
  }

  RevalidationCase addCase(String npi, CaseStatus status, LocalDate due) {
    RevalidationCase c =
        new RevalidationCase(
            UUID.randomUUID(), npi, due, status, EARLIER, status.open() ? null : CLOSED);
    cases.put(c.caseId(), c);
    return c;
  }

  RevalidationCase caseOf(UUID caseId) {
    return cases.get(caseId);
  }

  // ---- CaseStore ------------------------------------------------------------------------------

  @Override
  public List<TrackedProvider> trackedProviders() {
    return new ArrayList<>(tracked.values());
  }

  @Override
  public boolean hasOpenCase(String npi) {
    return cases.values().stream().anyMatch(c -> c.npi().equals(npi) && c.status().open());
  }

  @Override
  public void open(RevalidationCase opened, UUID runId) {
    if (failOnOpen != null) {
      throw failOnOpen;
    }
    calls.add("open:" + opened.npi());
    cases.put(opened.caseId(), opened);
    openedByRun.put(opened.caseId(), runId);
  }

  @Override
  public CasePage find(String npi, CaseStatus status, int page, int size) {
    throw new UnsupportedOperationException("the application unit tests do not page");
  }

  // ---- SweepRuns ------------------------------------------------------------------------------

  @Override
  public void create(SweepRun run) {
    calls.add("create");
    created.add(run);
  }

  @Override
  public void complete(UUID runId, int queued, int noDueDate, byte[] queueFile, Instant endedAt) {
    calls.add("complete");
    completions.add(new Completion(runId, queued, noDueDate, queueFile, endedAt));
  }

  @Override
  public void fail(UUID runId, String error, Instant endedAt) {
    calls.add("fail");
    failures.add(new Failure(runId, error, endedAt));
  }

  @Override
  public Optional<SweepRun> find(UUID runId) {
    return created.stream().filter(r -> r.runId().equals(runId)).findFirst();
  }

  @Override
  public Optional<byte[]> queueFile(UUID runId) {
    return completions.stream()
        .filter(c -> c.runId().equals(runId))
        .findFirst()
        .map(Completion::file);
  }

  // ---- CaseCompletions ------------------------------------------------------------------------

  @Override
  public Optional<RevalidationCase> findCase(UUID caseId) {
    return Optional.ofNullable(cases.get(caseId));
  }

  @Override
  public Optional<RevalidationCase> openCaseOf(String npi) {
    return cases.values().stream()
        .filter(c -> c.npi().equals(npi) && c.status().open())
        .findFirst();
  }

  @Override
  public void close(UUID caseId, CaseStatus status, UUID screeningId, Instant at) {
    calls.add("close:" + status);
    closings.add(new Closing(caseId, status, screeningId, at));
    RevalidationCase c = cases.get(caseId);
    if (c != null && c.status().open()) {
      cases.put(
          caseId, new RevalidationCase(caseId, c.npi(), c.dueDate(), status, c.openedAt(), at));
    }
  }

  // ---- CaseStages -----------------------------------------------------------------------------

  @Override
  public boolean markInScreening(UUID caseId) {
    RevalidationCase c = cases.get(caseId);
    if (c == null || c.status() != CaseStatus.DUE) {
      return false;
    }
    cases.put(
        caseId,
        new RevalidationCase(
            caseId, c.npi(), c.dueDate(), CaseStatus.IN_SCREENING, c.openedAt(), null));
    return true;
  }

  // ---- ProviderTracking -----------------------------------------------------------------------

  @Override
  public void track(TrackedProvider provider) {
    calls.add("track:" + provider.npi());
    tracked.put(provider.npi(), provider);
  }
}
