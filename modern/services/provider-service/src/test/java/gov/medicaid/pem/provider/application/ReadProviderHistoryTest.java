package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** MAM-40: reading the history of a provider (AC-6 paging, AC-7 unknown NPI). */
class ReadProviderHistoryTest {

  final Map<String, Provider> db = new HashMap<>();
  final List<String> asked = new ArrayList<>();
  final AuditEntry entry =
      new AuditEntry(
          "1003000111",
          Instant.parse("2026-11-18T09:00:00Z"),
          "enrollment-service",
          "REGISTERED",
          null,
          Map.of("enrollStatus", "P"),
          null);
  final AuditHistory history =
      (npi, page, size) -> {
        asked.add(npi + "/" + page + "/" + size);
        return new AuditHistory.Page(List.of(entry), 25);
      };
  final ReadProviderHistory read =
      new ReadProviderHistory(npi -> Optional.ofNullable(db.get(npi)), history);

  static Provider provider(String npi) {
    return Provider.builder()
        .npi(npi)
        .medicaidId("MCD000111")
        .providerType(ProviderType.INDIVIDUAL)
        .taxonomy("207Q00000X")
        .enrollStatus(EnrollStatus.ACTIVE)
        .screenFlag(ScreenFlag.PASS)
        .effectiveDate(LocalDate.of(2026, 11, 18))
        .build();
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-6] the page and size asked for are passed on, and the total of the history is returned")
  void passesPagingOn() {
    db.put("1003000111", provider("1003000111"));

    AuditHistory.Page page = read.of("1003000111", 3, 7);

    assertThat(asked).containsExactly("1003000111/3/7");
    assertThat(page.items()).containsExactly(entry);
    assertThat(page.total()).isEqualTo(25);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-7] an NPI that is not on file is a 404, and the history is not even looked at")
  void unknownNpi() {
    assertThatThrownBy(() -> read.of("1003000999", 0, 20))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND);
              assertThat(e.getMessage()).contains("1003000999");
            });
    assertThat(asked).isEmpty();
  }

  @Test
  @DisplayName("[MAM-40 AC-7] a provider with no history yet gives an empty page, not a 404")
  void knownProviderWithoutHistory() {
    db.put("1003000111", provider("1003000111"));
    ReadProviderHistory empty =
        new ReadProviderHistory(
            npi -> Optional.ofNullable(db.get(npi)),
            (npi, page, size) -> new AuditHistory.Page(List.of(), 0));

    assertThat(empty.of("1003000111", 0, 20).items()).isEmpty();
    assertThat(empty.of("1003000111", 0, 20).total()).isZero();
  }
}
