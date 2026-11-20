package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderApplication;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-17: the registration use case stores once, announces once and never overwrites (D-001,
 * D-014).
 */
class RegisterProviderTest {

  record Sent(String type, String subject, Object data) {}

  static class Registry implements ProviderRegistry {
    final List<Provider> added = new ArrayList<>();
    final List<String> existing = new ArrayList<>();

    @Override
    public boolean exists(String npi) {
      return existing.contains(npi) || added.stream().anyMatch(p -> p.npi().equals(npi));
    }

    @Override
    public void add(Provider provider) {
      added.add(provider);
    }
  }

  final Registry registry = new Registry();
  final List<Sent> sent = new ArrayList<>();
  final int[] skipped = {0};
  final RegisterProvider register =
      new RegisterProvider(
          registry,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          () -> skipped[0]++,
          Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneOffset.UTC));

  static ProviderApplication rivera() {
    return new ProviderApplication(
        "1003000111",
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null);
  }

  @Test
  @DisplayName(
      "[MAM-17 AC-1] a new NPI is added as PENDING / UNSCREENED with the clock date as effective date")
  void registers() {
    assertThat(register.register(rivera())).isEqualTo(RegistrationOutcome.REGISTERED);
    assertThat(registry.added).hasSize(1);
    Provider p = registry.added.get(0);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.PENDING);
    assertThat(p.screenFlag()).isEqualTo(ScreenFlag.UNSCREENED);
    assertThat(p.effectiveDate()).isEqualTo(LocalDate.of(2026, 11, 18));
    assertThat(skipped[0]).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-17 AC-2] registration announces exactly one ProviderRegistered with the display name")
  void announces() {
    register.register(rivera());
    assertThat(sent)
        .containsExactly(
            new Sent(
                "ProviderRegistered",
                "1003000111",
                new ProviderRegisteredData(
                    "1003000111", "MCD000111", "IN", "207Q00000X", "P", "U", "MARIA RIVERA")));
  }

  @Test
  @DisplayName(
      "[MAM-17 AC-4] an NPI that already exists is skipped: nothing added, nothing announced, counted once")
  void duplicate() {
    registry.existing.add("1003000111");
    assertThat(register.register(rivera())).isEqualTo(RegistrationOutcome.DUPLICATE);
    assertThat(registry.added).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(skipped[0]).isEqualTo(1);
  }
}
