package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.revalidation.application.CasePage;
import gov.medicaid.pem.revalidation.application.SweepRun;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * revalidation_db behind the stories (real Postgres): the tracked_provider read model, the cases
 * with their one-open-case-per-provider rule (D-029), their stages and closing, the list with its
 * filters, order and paging, and the sweep runs with counts, failure reason and REVALQ file.
 */
@Timeout(180)
class JdbcRevalidationStoreTest extends IntegrationTest {

  static final Instant T0 = Instant.parse("2026-03-31T08:00:00Z");
  static final Instant T1 = Instant.parse("2026-03-31T08:00:05Z");
  static final LocalDate DUE = LocalDate.of(2026, 2, 28);

  @Autowired JdbcRevalidationStore store;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private RevalidationCase newCase(String npi, LocalDate due, CaseStatus status) {
    return new RevalidationCase(UUID.randomUUID(), npi, due, status, T0, null);
  }

  // ---- tracked providers (MAM-45 AC-2, AC-3, AC-6; MAM-48 AC-7) --------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-2] a tracked provider is stored with its status and due date and read back by NPI order")
  void trackAndRead() {
    store.track(new TrackedProvider("1003000333", "A", LocalDate.of(2026, 3, 31)));
    store.track(new TrackedProvider("1003000222", "S", DUE));

    assertThat(store.trackedProviders())
        .containsExactly(
            new TrackedProvider("1003000222", "S", DUE),
            new TrackedProvider("1003000333", "A", LocalDate.of(2026, 3, 31)));
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-7] tracking the same provider again replaces its status and due date, one row, version counted")
  void trackUpserts() {
    store.track(new TrackedProvider("1003000222", "A", DUE));
    store.track(new TrackedProvider("1003000222", "A", LocalDate.of(2031, 3, 31)));
    store.track(new TrackedProvider("1003000222", "S", LocalDate.of(2031, 3, 31)));

    assertThat(store.trackedProviders())
        .containsExactly(new TrackedProvider("1003000222", "S", LocalDate.of(2031, 3, 31)));
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM tracked_provider WHERE npi = '1003000222'", Integer.class))
        .isEqualTo(2);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-6] a provider with no due date is stored and read back with a null due date")
  void noDueDate() {
    store.track(new TrackedProvider("1003000666", "A", null));
    store.track(new TrackedProvider("1003000666", "A", null));

    assertThat(store.trackedProviders())
        .containsExactly(new TrackedProvider("1003000666", "A", null));
  }

  // ---- cases (MAM-45 AC-2, AC-5; MAM-49 AC-4) ---------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-5] hasOpenCase is true for DUE and IN_SCREENING and false for closed cases or other providers")
  void hasOpenCase() {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "IN_SCREENING");
    RevalidationFixtures.insertCase(jdbc, "1003000444", DUE, "COMPLETED");
    RevalidationFixtures.insertCase(jdbc, "1003000555", DUE, "CANCELLED");

    assertThat(store.hasOpenCase("1003000222")).isTrue();
    assertThat(store.hasOpenCase("1003000333")).isTrue();
    assertThat(store.hasOpenCase("1003000444")).isFalse();
    assertThat(store.hasOpenCase("1003000555")).isFalse();
    assertThat(store.hasOpenCase("1003000999")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-2] opening a case stores it tied to its run, with its own due date and an open status")
  void openStoresTheCase() {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "RUNNING", LocalDate.of(2026, 3, 31), 0, 0, null, null);
    RevalidationCase c = newCase("1003000222", DUE, CaseStatus.DUE);

    store.open(c, run);

    assertThat(store.findCase(c.caseId())).contains(c);
    assertThat(
            jdbc.queryForObject(
                "SELECT r.public_id FROM revalidation_case c JOIN sweep_run r ON r.id = c.sweep_run_id"
                    + " WHERE c.public_id = ?",
                UUID.class,
                c.caseId()))
        .isEqualTo(run);
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-5] the database refuses a second open case of a provider (unique index), DUE or IN_SCREENING")
  void secondOpenCaseIsRefused() {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "RUNNING", LocalDate.of(2026, 3, 31), 0, 0, null, null);
    store.open(newCase("1003000222", DUE, CaseStatus.DUE), run);

    assertThatThrownBy(() -> store.open(newCase("1003000222", DUE, CaseStatus.DUE), run))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> store.open(newCase("1003000222", DUE, CaseStatus.IN_SCREENING), run))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThat(RevalidationFixtures.casesOf(jdbc, "1003000222")).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-49 AC-4] a closed case does not block a new open case of the provider")
  void closedCasesDoNotBlock() {
    UUID run =
        RevalidationFixtures.insertRun(
            jdbc, "RUNNING", LocalDate.of(2026, 3, 31), 0, 0, null, null);
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "CANCELLED");
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "COMPLETED");

    store.open(newCase("1003000222", DUE, CaseStatus.DUE), run);

    assertThat(RevalidationFixtures.casesOf(jdbc, "1003000222")).isEqualTo(3);
    assertThat(store.hasOpenCase("1003000222")).isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] findCase finds a case by its public id, and nothing for an unknown id")
  void findCase() {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "COMPLETED");

    assertThat(store.findCase(id))
        .hasValueSatisfying(
            c -> {
              assertThat(c.npi()).isEqualTo("1003000222");
              assertThat(c.status()).isEqualTo(CaseStatus.COMPLETED);
              assertThat(c.dueDate()).isEqualTo(DUE);
              assertThat(c.closedAt()).isEqualTo(Instant.parse("2026-03-15T10:00:00Z"));
            });
    assertThat(store.findCase(UUID.randomUUID())).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-4] openCaseOf returns the DUE or IN_SCREENING case of the provider, never a closed one")
  void openCaseOf() {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "COMPLETED");
    UUID open = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "IN_SCREENING");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "CANCELLED");

    assertThat(store.openCaseOf("1003000222")).map(RevalidationCase::caseId).contains(open);
    assertThat(store.openCaseOf("1003000333")).isEmpty();
    assertThat(store.openCaseOf("1003000999")).isEmpty();
  }

  // ---- closing and stages (MAM-48 AC-2, AC-4; MAM-49 AC-1, AC-2) --------------------------------

  @Test
  @DisplayName(
      "[MAM-48 AC-2] close stores the status, the screening id and closedAt and counts the version")
  void closeCompletes() {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    UUID screening = UUID.randomUUID();

    store.close(id, CaseStatus.COMPLETED, screening, T1);

    RevalidationCase c = store.findCase(id).orElseThrow();
    assertThat(c.status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(c.closedAt()).isEqualTo(T1);
    assertThat(
            jdbc.queryForObject(
                "SELECT screening_id FROM revalidation_case WHERE public_id = ?", UUID.class, id))
        .isEqualTo(screening);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM revalidation_case WHERE public_id = ?", Integer.class, id))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-49 AC-2] close can cancel with no screening id")
  void closeCancels() {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "IN_SCREENING");

    store.close(id, CaseStatus.CANCELLED, null, T1);

    assertThat(store.findCase(id).orElseThrow().status()).isEqualTo(CaseStatus.CANCELLED);
    assertThat(
            jdbc.queryForObject(
                "SELECT screening_id FROM revalidation_case WHERE public_id = ?", UUID.class, id))
        .isNull();
  }

  @Test
  @DisplayName("[MAM-49 AC-3] close leaves a case that is already closed exactly as it was")
  void closeLeavesClosedCasesAlone() {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "CANCELLED");

    store.close(id, CaseStatus.COMPLETED, UUID.randomUUID(), T1);

    RevalidationCase c = store.findCase(id).orElseThrow();
    assertThat(c.status()).isEqualTo(CaseStatus.CANCELLED);
    assertThat(c.closedAt()).isEqualTo(Instant.parse("2026-03-15T10:00:00Z"));
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM revalidation_case WHERE public_id = ?", Integer.class, id))
        .isZero();
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-1] markInScreening moves a DUE case to IN_SCREENING once, and says whether it moved")
  void markInScreening() {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    assertThat(store.markInScreening(id)).isTrue();
    assertThat(store.findCase(id).orElseThrow().status()).isEqualTo(CaseStatus.IN_SCREENING);
    assertThat(store.markInScreening(id)).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-1] markInScreening never reopens a closed case and ignores an unknown one")
  void markInScreeningGuards() {
    UUID completed = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "COMPLETED");
    UUID cancelled = RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "CANCELLED");

    assertThat(store.markInScreening(completed)).isFalse();
    assertThat(store.markInScreening(cancelled)).isFalse();
    assertThat(store.markInScreening(UUID.randomUUID())).isFalse();
    assertThat(store.findCase(completed).orElseThrow().status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(store.findCase(cancelled).orElseThrow().status()).isEqualTo(CaseStatus.CANCELLED);
  }

  // ---- the list (MAM-48 AC-1)
  // --------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-48 AC-1] find lists the oldest due date first, then by NPI")
  void findOrder() {
    RevalidationFixtures.insertCase(jdbc, "1003000444", LocalDate.of(2026, 4, 30), "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", LocalDate.of(2026, 3, 31), "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000222", LocalDate.of(2026, 3, 31), "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000111", LocalDate.of(2026, 5, 31), "DUE");

    CasePage page = store.find(null, null, 0, 20);

    assertThat(page.items())
        .extracting(RevalidationCase::npi)
        .containsExactly("1003000222", "1003000333", "1003000444", "1003000111");
    assertThat(page.total()).isEqualTo(4);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-1] find pages with the page number times the page size as offset, the total counts every match")
  void findPaging() {
    for (int i = 1; i <= 5; i++) {
      RevalidationFixtures.insertCase(jdbc, "100300000" + i, LocalDate.of(2026, 3, i), "DUE");
    }

    CasePage second = store.find(null, null, 1, 2);
    CasePage last = store.find(null, null, 2, 2);
    CasePage beyond = store.find(null, null, 3, 2);

    assertThat(second.items())
        .extracting(RevalidationCase::npi)
        .containsExactly("1003000003", "1003000004");
    assertThat(second.page()).isEqualTo(1);
    assertThat(second.size()).isEqualTo(2);
    assertThat(second.total()).isEqualTo(5);
    assertThat(last.items()).extracting(RevalidationCase::npi).containsExactly("1003000005");
    assertThat(beyond.items()).isEmpty();
    assertThat(beyond.total()).isEqualTo(5);
  }

  @Test
  @DisplayName("[MAM-48 AC-1] find filters by status, by NPI and by both")
  void findFilters() {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "COMPLETED");
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000444", DUE, "CANCELLED");

    assertThat(store.find(null, CaseStatus.DUE, 0, 20).total()).isEqualTo(2);
    assertThat(store.find("1003000222", null, 0, 20).total()).isEqualTo(2);
    assertThat(store.find("1003000222", CaseStatus.DUE, 0, 20).items())
        .extracting(RevalidationCase::npi, RevalidationCase::status)
        .containsExactly(org.assertj.core.groups.Tuple.tuple("1003000222", CaseStatus.DUE));
    assertThat(store.find("1003000999", null, 0, 20).total()).isZero();
    assertThat(store.find(null, CaseStatus.IN_SCREENING, 0, 20).items()).isEmpty();
  }

  // ---- the sweep runs (MAM-45 AC-7, AC-8)
  // --------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-45 AC-8] a new run is stored RUNNING with its business date, zero counts and no end")
  void createRun() {
    UUID id = UUID.randomUUID();

    store.create(new SweepRun(id, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));

    assertThat(store.find(id))
        .contains(new SweepRun(id, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] completing a run stores COMPLETED, both counts, the file and the end time")
  void completeRun() {
    UUID id = UUID.randomUUID();
    store.create(new SweepRun(id, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));
    String file = "1003000222" + "20260228" + "RESCREEN-REQUIRED   " + " ".repeat(42);

    store.complete(id, 1, 3, file.getBytes(StandardCharsets.ISO_8859_1), T1);

    assertThat(store.find(id))
        .contains(new SweepRun(id, LocalDate.of(2026, 3, 31), "COMPLETED", 1, 3, null, T0, T1));
    assertThat(store.queueFile(id))
        .hasValueSatisfying(
            b -> assertThat(new String(b, StandardCharsets.ISO_8859_1)).isEqualTo(file));
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-8] failing a run stores FAILED, the reason and the end time, and no file")
  void failRun() {
    UUID id = UUID.randomUUID();
    store.create(new SweepRun(id, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));

    store.fail(id, "SYNTHETIC REASON", T1);

    assertThat(store.find(id))
        .contains(
            new SweepRun(
                id, LocalDate.of(2026, 3, 31), "FAILED", 0, 0, "SYNTHETIC REASON", T0, T1));
    assertThat(store.queueFile(id)).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-7] a run that is RUNNING or unknown has no file; an empty file is an empty array")
  void queueFileOnlyOfCompletedRuns() {
    UUID running = UUID.randomUUID();
    store.create(new SweepRun(running, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));
    UUID empty = UUID.randomUUID();
    store.create(new SweepRun(empty, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));
    store.complete(empty, 0, 0, new byte[0], T1);

    assertThat(store.queueFile(running)).isEmpty();
    assertThat(store.queueFile(UUID.randomUUID())).isEmpty();
    assertThat(store.queueFile(empty)).hasValueSatisfying(b -> assertThat(b).isEmpty());
  }

  @Test
  @DisplayName("[MAM-45 AC-8] find of an unknown run is empty")
  void unknownRun() {
    Optional<SweepRun> none = store.find(UUID.randomUUID());

    assertThat(none).isEmpty();
  }

  @Test
  @DisplayName("[MAM-45 AC-8] the failure reason column holds long reasons without an error")
  void longReasonIsStored() {
    UUID id = UUID.randomUUID();
    store.create(new SweepRun(id, LocalDate.of(2026, 3, 31), "RUNNING", 0, 0, null, T0, null));

    store.fail(id, "x".repeat(2000), T1);

    List<String> reasons =
        jdbc.queryForList(
            "SELECT failure_reason FROM sweep_run WHERE public_id = ?", String.class, id);
    assertThat(reasons)
        .singleElement()
        .satisfies(r -> assertThat(r).isNotBlank().hasSizeLessThanOrEqualTo(500));
  }
}
