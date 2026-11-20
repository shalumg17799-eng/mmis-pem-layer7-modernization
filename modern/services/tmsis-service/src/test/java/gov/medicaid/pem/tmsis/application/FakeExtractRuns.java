package gov.medicaid.pem.tmsis.application;

import gov.medicaid.pem.tmsis.domain.TmsisRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * An in-memory {@link ExtractRuns} for the unit tests of the application classes: it keeps the runs
 * and their rows, hands the rows back in ascending NPI order (as the real table does), gives each
 * archived file the next generation, and notes every call in {@link #calls} so a test can check the
 * order of the steps.
 */
class FakeExtractRuns implements ExtractRuns {

  final Map<UUID, ExtractRun> runs = new HashMap<>();
  final Map<UUID, List<TmsisRecord>> rows = new HashMap<>();
  final Map<UUID, byte[]> files = new HashMap<>();
  final List<String> calls = new ArrayList<>();
  int generation;
  boolean findReturnsNothing;
  RuntimeException completeFails;

  @Override
  public void create(ExtractRun run) {
    calls.add("create");
    runs.put(run.runId(), run);
  }

  @Override
  public void addRows(UUID runId, List<TmsisRecord> added) {
    calls.add("addRows");
    rows.computeIfAbsent(runId, k -> new ArrayList<>()).addAll(added);
  }

  @Override
  public void addFutureEnrolments(UUID runId, int count) {
    calls.add("addFutureEnrolments");
    ExtractRun r = runs.get(runId);
    runs.put(
        runId,
        new ExtractRun(
            runId,
            r.businessDate(),
            r.periodYyddd(),
            r.status(),
            r.rows(),
            r.futureEnrolments() + count,
            r.archiveGeneration(),
            r.error(),
            r.startedAt(),
            r.endedAt()));
  }

  @Override
  public List<TmsisRecord> rows(UUID runId) {
    calls.add("rows");
    return rows.getOrDefault(runId, List.of()).stream()
        .sorted(Comparator.comparing(TmsisRecord::npi))
        .toList();
  }

  @Override
  public int complete(UUID runId, int rowCount, byte[] file, Instant at) {
    calls.add("complete");
    if (completeFails != null) {
      throw completeFails;
    }
    ExtractRun r = runs.get(runId);
    generation++;
    files.put(runId, file);
    runs.put(
        runId,
        new ExtractRun(
            runId,
            r.businessDate(),
            r.periodYyddd(),
            ExtractRun.COMPLETED,
            rowCount,
            r.futureEnrolments(),
            generation,
            null,
            r.startedAt(),
            at));
    return generation;
  }

  @Override
  public void fail(UUID runId, String error, Instant at) {
    calls.add("fail");
    ExtractRun r = runs.get(runId);
    runs.put(
        runId,
        new ExtractRun(
            runId,
            r.businessDate(),
            r.periodYyddd(),
            ExtractRun.FAILED,
            r.rows(),
            r.futureEnrolments(),
            null,
            error,
            r.startedAt(),
            at));
  }

  @Override
  public Optional<ExtractRun> find(UUID runId) {
    calls.add("find");
    return findReturnsNothing ? Optional.empty() : Optional.ofNullable(runs.get(runId));
  }

  @Override
  public RunPage page(int page, int size) {
    return new RunPage(List.copyOf(runs.values()), runs.size());
  }

  @Override
  public Optional<byte[]> file(UUID runId) {
    return Optional.ofNullable(files.get(runId));
  }
}
