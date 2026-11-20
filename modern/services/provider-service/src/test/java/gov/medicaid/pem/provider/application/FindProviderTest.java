package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class FindProviderTest {

  /** In-memory stand-in for the database port (a boundary). */
  static class InMemoryProviders implements ProviderStore {
    final Map<String, Provider> byNpi = new HashMap<>();

    @Override
    public Optional<Provider> findByNpi(String npi) {
      return Optional.ofNullable(byNpi.get(npi));
    }
  }

  private final InMemoryProviders store = new InMemoryProviders();
  private final FindProvider find = new FindProvider(store);

  private static Provider active(String npi) {
    return Provider.builder()
        .npi(npi)
        .medicaidId("900000101")
        .providerType(ProviderType.INDIVIDUAL)
        .taxonomy("207Q00000X")
        .enrollStatus(EnrollStatus.ACTIVE)
        .screenFlag(ScreenFlag.PASS)
        .effectiveDate(LocalDate.of(2026, 1, 15))
        .build();
  }

  private static CurrentUser user(String npi, String... roles) {
    return new CurrentUser("u", Set.of(roles), Optional.ofNullable(npi));
  }

  @Test
  @DisplayName("[MAM-34 AC-1] an analyst gets the provider stored for the NPI")
  void analystGetsProvider() {
    store.byNpi.put("1005000101", active("1005000101"));
    assertThat(find.byNpi("1005000101", user(null, "ANALYST")).npi()).isEqualTo("1005000101");
  }

  @Test
  @DisplayName("[MAM-34 AC-2] a provider may read their own NPI only")
  void providerOwnOnly() {
    store.byNpi.put("1003000111", active("1003000111"));
    store.byNpi.put("1005000101", active("1005000101"));
    assertThat(find.byNpi("1003000111", user("1003000111", "PROVIDER")).npi())
        .isEqualTo("1003000111");
    assertThatThrownBy(() -> find.byNpi("1005000101", user("1003000111", "PROVIDER")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName(
      "[MAM-34 AC-2] the access check comes before the lookup, so a provider cannot probe other NPIs")
  void noProbing() {
    assertThatThrownBy(() -> find.byNpi("1009999999", user("1003000111", "PROVIDER")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("[MAM-34 AC-1] an unknown NPI is a 404 problem")
  void unknownNpi() {
    assertThatThrownBy(() -> find.byNpi("1009999999", user(null, "ANALYST")))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
  }
}
