package gov.medicaid.pem.revalidation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.revalidation.domain.CaseStatus;
import gov.medicaid.pem.revalidation.domain.RevalidationCase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-48: an open case is completed by an analyst or the system with a screening result, or by a
 * REVALIDATION ScreeningCompleted; closed and unknown cases are refused (D-029).
 */
class CompleteCaseTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  final Map<UUID, RevalidationCase> cases = new HashMap<>();
  final Map<UUID, UUID> screeningOf = new HashMap<>();

  final CaseCompletions store =
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
          screeningOf.put(caseId, screeningId);
        }
      };

  final CompleteCase complete = new CompleteCase(store, Clock.fixed(NOW, ZoneOffset.UTC));

  RevalidationCase add(String npi, CaseStatus status) {
    RevalidationCase c =
        new RevalidationCase(
            UUID.randomUUID(),
            npi,
            LocalDate.of(2026, 2, 28),
            status,
            Instant.parse("2026-03-31T10:00:00Z"),
            status.open() ? null : Instant.parse("2026-04-01T10:00:00Z"));
    cases.put(c.caseId(), c);
    return c;
  }

  @Test
  @DisplayName("[MAM-48 AC-2] an open case is COMPLETED with closedAt and the screening id")
  void completesOpenCase() {
    RevalidationCase c = add("1003000222", CaseStatus.DUE);
    UUID screening = UUID.randomUUID();
    RevalidationCase done = complete.byRequest(c.caseId(), screening, "PASS");
    assertThat(done.status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(done.closedAt()).isEqualTo(NOW);
    assertThat(screeningOf.get(c.caseId())).isEqualTo(screening);
    RevalidationCase inScreening = add("1003000333", CaseStatus.IN_SCREENING);
    assertThat(complete.byRequest(inScreening.caseId(), UUID.randomUUID(), "FAIL").status())
        .isEqualTo(CaseStatus.COMPLETED);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-3] an unknown case is a 404 and a COMPLETED or CANCELLED case a 409, unchanged")
  void refused() {
    assertThatThrownBy(() -> complete.byRequest(UUID.randomUUID(), UUID.randomUUID(), "PASS"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
    for (CaseStatus closed : new CaseStatus[] {CaseStatus.COMPLETED, CaseStatus.CANCELLED}) {
      RevalidationCase c = add("1003000444", closed);
      assertThatThrownBy(() -> complete.byRequest(c.caseId(), UUID.randomUUID(), "PASS"))
          .isInstanceOfSatisfying(
              ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
      assertThat(cases.get(c.caseId())).isEqualTo(c);
    }
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-4/AC-5] a REVALIDATION ScreeningCompleted, PASS or FAIL, completes the provider's open case")
  void screeningCompletesCase() {
    RevalidationCase pass = add("1003000222", CaseStatus.DUE);
    RevalidationCase fail = add("1003000333", CaseStatus.IN_SCREENING);
    UUID screening = UUID.randomUUID();
    assertThat(complete.byScreening("1003000222", "REVALIDATION", "PASS", screening)).isTrue();
    assertThat(complete.byScreening("1003000333", "REVALIDATION", "FAIL", UUID.randomUUID()))
        .isTrue();
    assertThat(cases.get(pass.caseId()).status()).isEqualTo(CaseStatus.COMPLETED);
    assertThat(cases.get(pass.caseId()).closedAt()).isEqualTo(NOW);
    assertThat(screeningOf.get(pass.caseId())).isEqualTo(screening);
    assertThat(cases.get(fail.caseId()).status()).isEqualTo(CaseStatus.COMPLETED);
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-6] INITIAL and MANUAL screenings close nothing; a REVALIDATION result without an open case is ignored")
  void otherScreenings() {
    RevalidationCase c = add("1003000222", CaseStatus.DUE);
    assertThat(complete.byScreening("1003000222", "INITIAL", "PASS", UUID.randomUUID())).isFalse();
    assertThat(complete.byScreening("1003000222", "MANUAL", "PASS", UUID.randomUUID())).isFalse();
    assertThat(complete.byScreening("1003000999", "REVALIDATION", "PASS", UUID.randomUUID()))
        .isFalse();
    assertThat(cases.get(c.caseId()).status()).isEqualTo(CaseStatus.DUE);
    assertThat(cases).hasSize(1);
  }
}
