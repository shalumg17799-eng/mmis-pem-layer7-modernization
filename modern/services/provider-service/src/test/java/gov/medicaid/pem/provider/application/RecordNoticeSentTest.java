package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-40 AC-4 / D-012: a notice sent is added to the provider history as a reference. */
class RecordNoticeSentTest {

  final Map<String, Provider> db = new HashMap<>();
  final List<AuditEntry> entries = new ArrayList<>();
  final RecordNoticeSent record =
      new RecordNoticeSent(npi -> Optional.ofNullable(db.get(npi)), entries::add);

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
      "[MAM-40 AC-4] a notice for a provider on file adds one NOTICE_SENT entry with the notice id, the template and the sender")
  void recordsReference() {
    db.put("1003000111", provider("1003000111"));

    boolean recorded =
        record.noticeSent(
            "1003000111",
            "a1a1a1a1-0000-4000-8000-0000000000a1",
            "APPROVED",
            "notification-service");

    assertThat(recorded).isTrue();
    assertThat(entries).hasSize(1);
    AuditEntry e = entries.get(0);
    assertThat(e.npi()).isEqualTo("1003000111");
    assertThat(e.actor()).isEqualTo("notification-service");
    assertThat(e.action()).isEqualTo("NOTICE_SENT");
    assertThat(e.before()).isNull();
    assertThat(e.after())
        .containsExactly(
            Map.entry(
                "notice",
                Map.of(
                    "noticeId", "a1a1a1a1-0000-4000-8000-0000000000a1", "template", "APPROVED")));
  }

  @Test
  @DisplayName("[MAM-40 AC-4] the actor of the entry is the sender named by the caller")
  void actorIsTheCallersSender() {
    db.put("1003000111", provider("1003000111"));

    record.noticeSent("1003000111", "n-1", "SUSPENDED", "some-other-sender");

    assertThat(entries.get(0).actor()).isEqualTo("some-other-sender");
  }

  @Test
  @DisplayName("[MAM-40 AC-4] a notice for an NPI that is not on file records nothing and says so")
  void unknownProvider() {
    boolean recorded = record.noticeSent("1009999999", "n-1", "APPROVED", "notification-service");

    assertThat(recorded).isFalse();
    assertThat(entries).isEmpty();
  }
}
