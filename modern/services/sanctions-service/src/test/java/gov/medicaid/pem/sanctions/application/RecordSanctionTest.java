package gov.medicaid.pem.sanctions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.sanctions.domain.Sanction;
import gov.medicaid.pem.sanctions.domain.SanctionAction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

/**
 * MAM-52: a sanction is applied through provider-service first; only an applied sanction is kept in
 * the history and announced as SanctionApplied (D-013, D-018, D-024, D-026).
 */
class RecordSanctionTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  static final LocalDate EFFECTIVE = LocalDate.of(2026, 3, 31);

  record Call(String npi, String action, String reason, LocalDate effectiveDate, UUID sanctionId) {}

  record Sent(String type, String subject, Object data) {}

  final List<Call> calls = new ArrayList<>();
  final List<Sanction> stored = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();
  ProviderStatusPort.Outcome answer = ProviderStatusPort.Outcome.APPLIED;

  final SanctionHistory history =
      new SanctionHistory() {
        @Override
        public void add(Sanction s) {
          stored.add(s);
        }

        @Override
        public SanctionPage find(String npi, int page, int size) {
          return new SanctionPage(List.copyOf(stored), page, size, stored.size());
        }
      };

  final RecordSanction record =
      new RecordSanction(
          (npi, action, reason, effectiveDate, sanctionId) -> {
            calls.add(new Call(npi, action, reason, effectiveDate, sanctionId));
            return answer;
          },
          history,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  static SanctionCommand command(String npi, String action, LocalDate effective) {
    return new SanctionCommand(npi, action, "SYNTHETIC TEST REASON", effective, null);
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-1] T is applied as TERMINATE with the sanction's id, then kept and announced once")
  void terminate() {
    Sanction s = record.record(command("1003000111", "T", EFFECTIVE), "supervisor1");
    assertThat(s.sanctionId()).isNotNull();
    assertThat(s.npi()).isEqualTo("1003000111");
    assertThat(s.action()).isEqualTo(SanctionAction.T);
    assertThat(s.resultingStatus()).isEqualTo("T");
    assertThat(s.reason()).isEqualTo("SYNTHETIC TEST REASON");
    assertThat(s.effectiveDate()).isEqualTo(EFFECTIVE);
    assertThat(s.recordedBy()).isEqualTo("supervisor1");
    assertThat(s.recordedAt()).isEqualTo(NOW);
    assertThat(calls)
        .containsExactly(
            new Call(
                "1003000111", "TERMINATE", "SYNTHETIC TEST REASON", EFFECTIVE, s.sanctionId()));
    assertThat(stored).containsExactly(s);
    assertThat(sent)
        .containsExactly(
            new Sent(
                "SanctionApplied",
                "1003000111",
                new SanctionAppliedData(
                    s.sanctionId(), "1003000111", "T", "SYNTHETIC TEST REASON", EFFECTIVE)));
  }

  @Test
  @DisplayName("[MAM-52 AC-2] S is applied as SUSPEND and results in S")
  void suspend() {
    Sanction s = record.record(command("1003000333", "S", EFFECTIVE), "supervisor1");
    assertThat(calls.get(0).action()).isEqualTo("SUSPEND");
    assertThat(s.resultingStatus()).isEqualTo("S");
    assertThat(sent).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-52 AC-3] R is applied as REINSTATE and results in A")
  void reinstate() {
    Sanction s =
        record.record(command("1003000333", "R", LocalDate.of(2026, 4, 30)), "supervisor1");
    assertThat(calls.get(0).action()).isEqualTo("REINSTATE");
    assertThat(s.resultingStatus()).isEqualTo("A");
    assertThat(sent).hasSize(1);
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-5] an unknown or empty action is a 422 naming action and T, S, R, with no code; nothing is applied")
  void unknownAction() {
    for (String action : new String[] {"X", "", null}) {
      assertThatThrownBy(
              () -> record.record(command("1003000111", action, EFFECTIVE), "supervisor1"))
          .isInstanceOfSatisfying(
              ApiException.class,
              e -> {
                assertThat(e.status().value()).isEqualTo(422);
                assertThat(e.getMessage()).contains("action", "T", "S", "R");
                assertThat(e.code()).isNull();
              });
    }
    assertThat(calls).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-5] a missing effective date or a malformed NPI is a 422; nothing is applied")
  void incomplete() {
    assertThatThrownBy(() -> record.record(command("1003000111", "T", null), "supervisor1"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(422));
    assertThatThrownBy(() -> record.record(command("10030001", "T", EFFECTIVE), "supervisor1"))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(422));
    assertThat(calls).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-52 AC-6] an NPI provider-service does not know is a 404; nothing is kept")
  void notFound() {
    answer = ProviderStatusPort.Outcome.NOT_FOUND;
    assertThatThrownBy(() -> record.record(command("1003000999", "T", EFFECTIVE), "supervisor1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(404);
              assertThat(e.getMessage()).contains("1003000999");
            });
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-7] a status change that could not be applied is a 503, never 404 or 201; nothing is kept")
  void failed() {
    answer = ProviderStatusPort.Outcome.FAILED;
    assertThatThrownBy(() -> record.record(command("1003000111", "T", EFFECTIVE), "supervisor1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(503);
              assertThat(e.getMessage()).contains("1003000111");
            });
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
  }
}
