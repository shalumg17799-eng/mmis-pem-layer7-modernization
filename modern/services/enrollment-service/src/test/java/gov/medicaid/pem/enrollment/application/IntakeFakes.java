package gov.medicaid.pem.enrollment.application;

import gov.medicaid.pem.enrollment.AppInputFile;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * In-memory stand-ins for the ports of the intake (no Spring, no database), shared by the unit
 * tests of {@link IntakeLoad} and of the file path of {@link SubmitApplication}.
 */
final class IntakeFakes {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final LocalDate DATE = LocalDate.parse("2026-03-31");

  private IntakeFakes() {}

  /** The run table: keeps every call so that a test can see what was written and in which order. */
  static class Runs implements IntakeRuns {
    final List<String> calls = new ArrayList<>();
    final List<int[]> counts = new ArrayList<>();
    final List<String> errorRows = new ArrayList<>();
    final List<String> attachedRecords = new ArrayList<>();
    final List<UUID> attachedApplications = new ArrayList<>();
    final Set<String> alreadyProcessed = new HashSet<>();
    UUID createdRun;
    LocalDate createdDate;
    String createdBatch;
    Instant createdAt;
    Instant completedAt;
    String failedReason;
    Instant failedAt;

    @Override
    public void create(UUID runId, LocalDate businessDate, String batchId, Instant startedAt) {
      calls.add("create");
      createdRun = runId;
      createdDate = businessDate;
      createdBatch = batchId;
      createdAt = startedAt;
    }

    @Override
    public void counts(UUID runId, int read, int written, int errored, int skipped) {
      calls.add("counts");
      counts.add(new int[] {read, written, errored, skipped});
    }

    @Override
    public void addErrorRow(UUID runId, String row) {
      calls.add("addErrorRow");
      errorRows.add(row);
    }

    @Override
    public void attach(UUID applicationId, UUID runId, String rawRecord) {
      calls.add("attach");
      attachedApplications.add(applicationId);
      attachedRecords.add(rawRecord);
    }

    @Override
    public boolean alreadyProcessed(UUID runId, LocalDate businessDate, String rawRecord) {
      calls.add("alreadyProcessed");
      return alreadyProcessed.contains(rawRecord);
    }

    @Override
    public void complete(UUID runId, Instant endedAt) {
      calls.add("complete");
      completedAt = endedAt;
    }

    @Override
    public void fail(UUID runId, String error, Instant endedAt) {
      calls.add("fail");
      failedReason = error;
      failedAt = endedAt;
    }

    @Override
    public Optional<Run> find(UUID runId) {
      return Optional.empty();
    }

    @Override
    public Optional<String> errorFile(UUID runId) {
      return Optional.of(String.join("", errorRows));
    }

    /** The counts as last written: read, written, errored, skipped. */
    int[] lastCounts() {
      return counts.get(counts.size() - 1);
    }
  }

  /** The application table and the feed-code table; a save can be made to fail for one NPI. */
  static class Store implements ApplicationStore {
    final List<StoredApplication> saved = new ArrayList<>();
    final List<String> submittedBy = new ArrayList<>();
    String failForNpi;

    @Override
    public boolean hasValidated(String npi) {
      return saved.stream().anyMatch(a -> a.status().equals("VALIDATED") && npi.equals(a.npi()));
    }

    @Override
    public Map<String, String> providerTypeMappings() {
      return Map.of("IND", "IN", "GRP", "GR", "HOSP", "FA");
    }

    @Override
    public void save(StoredApplication application, String by) {
      if (application.npi() != null && application.npi().equals(failForNpi)) {
        throw new IllegalStateException("the store is down for " + failForNpi);
      }
      saved.add(application);
      submittedBy.add(by);
    }

    @Override
    public ApplicationPage find(ApplicationQuery query) {
      return new ApplicationPage(List.copyOf(saved), query.page(), query.size(), saved.size());
    }

    /** The saved application of this NPI (the first one saved). */
    StoredApplication of(String npi) {
      return saved.stream().filter(a -> npi.equals(a.npi())).findFirst().orElseThrow();
    }
  }

  record Sent(String type, String subject, Object data) {}

  /** The outbox: the events announced, in order. */
  static class Outbox implements EventOutbox {
    final List<Sent> sent = new ArrayList<>();

    @Override
    public void enqueue(String type, String subject, Object data) {
      sent.add(new Sent(type, subject, data));
    }

    long count(String type) {
      return sent.stream().filter(s -> s.type().equals(type)).count();
    }
  }

  /** The intake, wired to the fakes with the fixed clock. */
  static class Intake {
    final Runs runs = new Runs();
    final Store store = new Store();
    final Outbox outbox = new Outbox();
    final SubmitApplication submit = new SubmitApplication(store, outbox, CLOCK);
    final IntakeLoad load = new IntakeLoad(runs, submit, CLOCK);

    /** Processes the records as a file with one record per line, as the admin "someone" would. */
    void process(String... records) {
      load.process(UUID.randomUUID(), DATE, AppInputFile.lines(records), "admin1");
    }
  }
}
