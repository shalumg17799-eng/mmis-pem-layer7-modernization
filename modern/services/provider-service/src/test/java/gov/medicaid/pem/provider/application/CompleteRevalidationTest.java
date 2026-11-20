package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-30: a passed revalidation is saved with the new due date and announced once. */
class CompleteRevalidationTest {

  record Sent(String type, String subject, Object data) {}

  final Map<String, Provider> db = new HashMap<>();
  final List<Provider> saved = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();

  final CompleteRevalidation complete =
      new CompleteRevalidation(
          npi -> Optional.ofNullable(db.get(npi)),
          saved::add,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          Clock.fixed(Instant.parse("2026-04-02T10:00:00Z"), ZoneOffset.UTC));

  static Provider active(String npi) {
    return new Provider(
        npi,
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        RiskTier.LIMITED,
        EnrollStatus.ACTIVE,
        ScreenFlag.PASS,
        LocalDate.of(2021, 3, 31),
        null,
        LocalDate.of(2026, 3, 31),
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        3);
  }

  @Test
  @DisplayName(
      "[MAM-30 AC-4/AC-5] the new due date (service clock + 5 years) is saved and announced once from A to A")
  void passed() {
    db.put("1003000111", active("1003000111"));
    assertThat(complete.passed("1003000111")).isTrue();
    assertThat(saved).hasSize(1);
    assertThat(saved.get(0).revalidationDueDate()).isEqualTo(LocalDate.of(2031, 4, 2));
    assertThat(sent).hasSize(1);
    assertThat(sent.get(0).type()).isEqualTo("ProviderStatusChanged");
    assertThat(sent.get(0).subject()).isEqualTo("1003000111");
    ProviderStatusChangedData d = (ProviderStatusChangedData) sent.get(0).data();
    assertThat(d.from()).isEqualTo("A");
    assertThat(d.to()).isEqualTo("A");
    assertThat(d.revalidationDueDate()).isEqualTo(LocalDate.of(2031, 4, 2));
    assertThat(d.npi()).isEqualTo("1003000111");
  }

  @Test
  @DisplayName(
      "[MAM-30 AC-4] a revalidation for an NPI that is not on file changes and announces nothing")
  void unknown() {
    assertThat(complete.passed("1003000999")).isFalse();
    assertThat(saved).isEmpty();
    assertThat(sent).isEmpty();
  }
}
