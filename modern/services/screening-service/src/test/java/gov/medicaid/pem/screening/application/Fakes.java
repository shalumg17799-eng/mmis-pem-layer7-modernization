package gov.medicaid.pem.screening.application;

import gov.medicaid.pem.screening.domain.Source;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** In-memory ports for the unit tests of the application classes (no Spring, no database). */
final class Fakes {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

  private Fakes() {}

  static ProviderFacts facts(String npi) {
    return new ProviderFacts(npi, "IN", "207Q00000X", "MD12345", "TX", "MARIA RIVERA", null);
  }

  static List<SourceResult> results(String lic, String leie, String sam, String dmf) {
    return List.of(
        new SourceResult(Source.LICBOARD, lic, NOW, "LIC", null),
        new SourceResult(Source.OIGLEIE, leie, NOW, "LEIE", null),
        new SourceResult(Source.SAMGOV, sam, NOW, "SAM", null),
        new SourceResult(Source.SSADMF, dmf, NOW, "DMF", null));
  }

  /** A store that keeps what it is given. */
  static final class Store implements ScreeningStore {
    final List<Screening> saved = new ArrayList<>();

    @Override
    public void save(Screening s) {
      saved.add(s);
    }

    @Override
    public ScreeningPage find(String npi, String result, int page, int size) {
      return new ScreeningPage(List.copyOf(saved), page, size, saved.size());
    }

    @Override
    public Optional<Screening> get(UUID id) {
      return saved.stream().filter(s -> s.screeningId().equals(id)).findFirst();
    }
  }

  /** An outbox that remembers every event. */
  static final class Outbox implements EventOutbox {
    record Sent(String type, String subject, Object data) {}

    final List<Sent> sent = new ArrayList<>();

    @Override
    public void enqueue(String type, String subject, Object data) {
      sent.add(new Sent(type, subject, data));
    }
  }

  /** ScreeningRuns in memory: the run table and the links of screenings to runs. */
  static final class RunsFake implements ScreeningRuns {
    final Map<UUID, Run> runs = new LinkedHashMap<>();
    final Map<UUID, UUID> links = new LinkedHashMap<>();
    final Set<String> initial = new HashSet<>();
    List<Screened> screenedOfRun = List.of();
    byte[] fileWritten;
    int failedCount;

    @Override
    public void create(UUID runId, LocalDate businessDate, Instant startedAt) {
      runs.put(runId, new Run(runId, businessDate, Run.RUNNING, 0, 0, 0, null, startedAt, null));
    }

    @Override
    public void link(UUID screeningId, UUID runId) {
      links.put(screeningId, runId);
    }

    @Override
    public boolean hasInitialScreening(String npi) {
      return initial.contains(npi);
    }

    @Override
    public List<Screened> screened(UUID runId) {
      return screenedOfRun;
    }

    @Override
    public void complete(UUID runId, int read, int failed, byte[] file, Instant endedAt) {
      Run r = runs.get(runId);
      fileWritten = file;
      failedCount = failed;
      runs.put(
          runId,
          new Run(
              runId,
              r.businessDate(),
              "COMPLETED",
              read,
              screenedOfRun.size(),
              failed,
              null,
              r.startedAt(),
              endedAt));
    }

    @Override
    public void fail(UUID runId, int read, String error, Instant endedAt) {
      Run r = runs.get(runId);
      runs.put(
          runId,
          new Run(runId, r.businessDate(), "FAILED", read, 0, 0, error, r.startedAt(), endedAt));
    }

    @Override
    public Optional<Run> find(UUID runId) {
      return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<byte[]> file(UUID runId) {
      return Optional.ofNullable(fileWritten);
    }
  }
}
