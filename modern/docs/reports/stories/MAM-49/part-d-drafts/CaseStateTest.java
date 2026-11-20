package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import gov.medicaid.pem.revalidation.domain.TrackedProvider;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-49: a case is IN_SCREENING once its RevalidationDue is out (C-13); a provider that leaves
 * ACTIVE cancels its open case (C-14, D-006); the tracked status and due date always follow.
 */
class CaseStateTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  final Map<UUID, RevalidationCase> cases = new HashMap<>();
  final List<TrackedProvider> tracked = new ArrayList<>();

  final CaseCompletions completions =
      new CaseCompletions() {
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
          RevalidationCase c = cases.get(caseId);
          cases.put(
              caseId,
              new RevalidationCase(caseId, c.npi(), c.dueDate(), status, c.openedAt(), at));
        }
      };

  final CaseStages stages =
      caseId -> {
        RevalidationCase c = cases.get(caseId);
        if (c == null || c.status() != CaseStatus.DUE) {
          return false;
        }
        cases.put(
            caseId,
            new RevalidationCase(
                caseId, c.npi(), c.dueDate(), CaseStatus.IN_SCREENING, c.openedAt(), null));
        return true;
      };

  final FollowProvider follow =
      new FollowProvider(tracked::add, completions, Clock.fixed(NOW, ZoneOffset.UTC));
  final RequestScreeningStage requested = new RequestScreeningStage(stages);

  RevalidationCase add(String npi, CaseStatus status) {
    RevalidationCase c =
        new RevalidationCase(
            UUID.randomUUID(),
            npi,
            LocalDate.of(2026, 2, 28),
            status,
            Instant.parse("2026-03-31T10:00:00Z"),
            null);
    cases.put(c.caseId(), c);
    return c;
  }

  @Test
  @DisplayName("[MAM-49 AC-1] a DUE case becomes IN_SCREENING when its RevalidationDue is out")
  void inScreening() {
    RevalidationCase c = add("1003000222", CaseStatus.DUE);
    assertThat(requested.revalidationDuePublished(c.caseId())).isTrue();
    assertThat(cases.get(c.caseId()).status()).isEqualTo(CaseStatus.IN_SCREENING);
    assertThat(requested.revalidationDuePublished(c.caseId())).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-2] a provider leaving ACTIVE (S, T or D) cancels its open case with closedAt; the read model follows")
  void cancels() {
    for (String status : new String[] {"S", "T", "D"}) {
      cases.clear();
      RevalidationCase c = add("1003000222", CaseStatus.IN_SCREENING);
      follow.statusChanged(new TrackedProvider("1003000222", status, LocalDate.of(2026, 2, 28)));
      assertThat(cases.get(c.caseId()).status()).isEqualTo(CaseStatus.CANCELLED);
      assertThat(cases.get(c.caseId()).closedAt()).isEqualTo(NOW);
    }
    assertThat(tracked).hasSize(3).allMatch(t -> t.npi().equals("1003000222"));
  }

  @Test
  @DisplayName(
      "[MAM-49 AC-4] an ACTIVE provider keeps its open case; with no open case only the read model changes")
  void activeKeepsCase() {
    RevalidationCase c = add("1003000222", CaseStatus.DUE);
    follow.statusChanged(new TrackedProvider("1003000222", "A", LocalDate.of(2031, 3, 31)));
    assertThat(cases.get(c.caseId()).status()).isEqualTo(CaseStatus.DUE);
    follow.statusChanged(new TrackedProvider("1003000999", "S", null));
    assertThat(tracked).extracting(TrackedProvider::npi).containsExactly("1003000222", "1003000999");
    assertThat(cases).hasSize(1);
  }
}
