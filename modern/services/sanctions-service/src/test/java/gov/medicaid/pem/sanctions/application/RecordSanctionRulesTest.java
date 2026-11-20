package gov.medicaid.pem.sanctions.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.sanctions.domain.Sanction;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Lane 3 unit tests for RecordSanction, which the SANCIN intake applies record by record (MAM-55
 * AC-3): the field limits of the contract's SanctionRequest (reason at most 60, sourceFeed at most
 * 20, NPI 10 digits, effectiveDate required), optional fields, and the order that keeps a failed
 * status change out of the history: provider-service first, then the history row and the event
 * together in one transaction (D-013, D-018, D-012).
 */
class RecordSanctionRulesTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");
  static final LocalDate EFFECTIVE = LocalDate.of(2026, 3, 31);

  final List<String> trail = new ArrayList<>();
  final List<Sanction> stored = new ArrayList<>();
  final List<Object> events = new ArrayList<>();
  boolean inTransaction;

  final TransactionOperations transaction =
      new TransactionOperations() {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
          inTransaction = true;
          trail.add("transaction opens");
          try {
            return action.doInTransaction(null);
          } finally {
            inTransaction = false;
          }
        }
      };

  final SanctionHistory history =
      new SanctionHistory() {
        @Override
        public void add(Sanction s) {
          trail.add("history row, in transaction: " + inTransaction);
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
            trail.add("provider-service, in transaction: " + inTransaction);
            return ProviderStatusPort.Outcome.APPLIED;
          },
          history,
          (type, subject, data) -> {
            trail.add("event " + type + ", in transaction: " + inTransaction);
            events.add(data);
          },
          transaction,
          Clock.fixed(NOW, ZoneOffset.UTC));

  static SanctionCommand command(String npi, String reason, String sourceFeed, LocalDate date) {
    return new SanctionCommand(npi, "T", reason, date, sourceFeed);
  }

  void assertRefused(SanctionCommand c, String... named) {
    assertThatThrownBy(() -> record.record(c, "supervisor1"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(422);
              assertThat(e.getMessage()).contains(named);
              assertThat(e.code()).isNull();
            });
    assertThat(trail).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(events).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-1] provider-service is called first; the history row and the event are written together inside one transaction")
  void order() {
    record.record(command("1003000111", "SYNTHETIC TEST REASON", null, EFFECTIVE), "supervisor1");
    assertThat(trail)
        .containsExactly(
            "provider-service, in transaction: false",
            "transaction opens",
            "history row, in transaction: true",
            "event SanctionApplied, in transaction: true");
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-1] the answer carries the source feed it was given, SANCIN for the intake")
  void sourceFeedIsKept() {
    Sanction s =
        record.record(
            command("1003000111", "SYNTHETIC TEST REASON", "SANCIN", EFFECTIVE), "admin1");
    assertThat(s.sourceFeed()).isEqualTo("SANCIN");
    assertThat(s.recordedBy()).isEqualTo("admin1");
    assertThat(stored).containsExactly(s);
  }

  @Test
  @DisplayName("[MAM-52 AC-1] a sanction entered online has no source feed")
  void noSourceFeed() {
    Sanction s =
        record.record(
            command("1003000111", "SYNTHETIC TEST REASON", null, EFFECTIVE), "supervisor1");
    assertThat(s.sourceFeed()).isNull();
  }

  @Test
  @DisplayName("[MAM-52 AC-1] a sanction needs no reason: it is kept and announced without one")
  void noReason() {
    Sanction s = record.record(command("1003000111", null, "SANCIN", EFFECTIVE), "admin1");
    assertThat(s.reason()).isNull();
    assertThat(events)
        .containsExactly(
            new SanctionAppliedData(s.sanctionId(), "1003000111", "T", null, EFFECTIVE));
  }

  @Test
  @DisplayName("[MAM-52 AC-1] a reason of 60 characters is accepted")
  void reasonAtTheLimit() {
    Sanction s =
        record.record(command("1003000111", "R".repeat(60), null, EFFECTIVE), "supervisor1");
    assertThat(s.reason()).hasSize(60);
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-5] a reason of 61 characters is a 422 naming the field and the limit; nothing is applied")
  void reasonTooLong() {
    assertRefused(command("1003000111", "R".repeat(61), null, EFFECTIVE), "reason", "60");
  }

  @Test
  @DisplayName("[MAM-52 AC-1] a source feed of 20 characters is accepted")
  void sourceFeedAtTheLimit() {
    Sanction s =
        record.record(command("1003000111", "X", "F".repeat(20), EFFECTIVE), "supervisor1");
    assertThat(s.sourceFeed()).hasSize(20);
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-5] a source feed of 21 characters is a 422 naming the field and the limit; nothing is applied")
  void sourceFeedTooLong() {
    assertRefused(command("1003000111", "X", "F".repeat(21), EFFECTIVE), "sourceFeed", "20");
  }

  @Test
  @DisplayName(
      "[MAM-52 AC-5] a missing effective date is a 422 naming the field; nothing is applied")
  void noEffectiveDate() {
    assertRefused(command("1003000111", "X", null, null), "effectiveDate");
  }

  @Test
  @DisplayName("[MAM-52 AC-5] a missing NPI is a 422 naming the field; nothing is applied")
  void noNpi() {
    assertRefused(command(null, "X", null, EFFECTIVE), "npi");
  }

  @ParameterizedTest(
      name = "[MAM-52 AC-5] the NPI \"{0}\" is not 10 digits: 422 naming npi; nothing is applied")
  @ValueSource(strings = {"", "100300011", "10030001111", "ABCDEFGHIJ", "1003 00111", "100300011A"})
  void npiIsNotTenDigits(String npi) {
    assertRefused(command(npi, "X", null, EFFECTIVE), "npi", "10 digits");
  }

  @Test
  @DisplayName("[MAM-52 AC-1] an NPI of exactly 10 digits is accepted, leading zeros included")
  void npiWithLeadingZeros() {
    Sanction s = record.record(command("0003000111", "X", null, EFFECTIVE), "supervisor1");
    assertThat(s.npi()).isEqualTo("0003000111");
  }

  @Test
  @DisplayName("[MAM-52 AC-1] recordedAt is the clock's time, not the effective date")
  void recordedAt() {
    Sanction s =
        record.record(command("1003000111", "X", null, LocalDate.of(2025, 1, 2)), "supervisor1");
    assertThat(s.recordedAt()).isEqualTo(NOW);
    assertThat(s.effectiveDate()).isEqualTo(LocalDate.of(2025, 1, 2));
  }
}
