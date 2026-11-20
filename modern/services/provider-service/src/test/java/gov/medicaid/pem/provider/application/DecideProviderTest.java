package gov.medicaid.pem.provider.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.provider.domain.Decision;
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
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-27: the decision use case stores the decision, announces ProviderStatusChanged once, and
 * refuses explicitly.
 */
class DecideProviderTest {

  static final CurrentUser ANALYST =
      new CurrentUser("analyst1", Set.of("ANALYST"), Optional.empty());

  record Sent(String type, String subject, Object data) {}

  final Map<String, Provider> db = new HashMap<>();
  final List<Provider> saved = new ArrayList<>();
  final List<Sent> sent = new ArrayList<>();

  final DecideProvider decide =
      new DecideProvider(
          npi -> Optional.ofNullable(db.get(npi)),
          saved::add,
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          Clock.fixed(Instant.parse("2026-11-18T15:00:00Z"), ZoneOffset.UTC));

  static Provider provider(String npi, EnrollStatus status, ScreenFlag flag) {
    return new Provider(
        npi,
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        RiskTier.LIMITED,
        status,
        flag,
        LocalDate.of(2026, 11, 1),
        null,
        null,
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        2);
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-1/AC-2] an approval is saved and announced with from, to, actor, due date, type, name, taxonomy and Medicaid ID")
  void approve() {
    db.put("1003000111", provider("1003000111", EnrollStatus.PENDING, ScreenFlag.PASS));
    Provider p = decide.decide("1003000111", Decision.APPROVE, null, ANALYST);
    assertThat(p.enrollStatus()).isEqualTo(EnrollStatus.ACTIVE);
    assertThat(saved).containsExactly(p);
    assertThat(sent)
        .containsExactly(
            new Sent(
                "ProviderStatusChanged",
                "1003000111",
                new ProviderStatusChangedData(
                    "1003000111",
                    "P",
                    "A",
                    null,
                    "analyst1",
                    LocalDate.of(2031, 11, 18),
                    "IN",
                    "MARIA RIVERA",
                    "207Q00000X",
                    "MCD000111")));
  }

  @Test
  @DisplayName("[MAM-27 AC-3] a denial carries its reason and no due date")
  void deny() {
    db.put("1003000444", provider("1003000444", EnrollStatus.PENDING, ScreenFlag.FAIL));
    decide.decide("1003000444", Decision.DENY, "Owner 1990000111 is on the OIG LEIE", ANALYST);
    ProviderStatusChangedData d = (ProviderStatusChangedData) sent.get(0).data();
    assertThat(d.to()).isEqualTo("D");
    assertThat(d.reason()).isEqualTo("Owner 1990000111 is on the OIG LEIE");
    assertThat(d.revalidationDueDate()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-27 AC-4] an approval without a passed screening is a 422 and nothing is saved or announced")
  void unprocessable() {
    db.put("1003000444", provider("1003000444", EnrollStatus.PENDING, ScreenFlag.FAIL));
    assertThatThrownBy(() -> decide.decide("1003000444", Decision.APPROVE, null, ANALYST))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(422));
    assertThat(saved).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-27 AC-5] a second decision is a 409 and nothing is saved or announced")
  void conflict() {
    db.put("1003000111", provider("1003000111", EnrollStatus.ACTIVE, ScreenFlag.PASS));
    assertThatThrownBy(() -> decide.decide("1003000111", Decision.DENY, null, ANALYST))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(409));
    assertThat(saved).isEmpty();
    assertThat(sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-27 AC-6] an unknown provider is a 404")
  void unknown() {
    assertThatThrownBy(() -> decide.decide("1003000999", Decision.APPROVE, null, ANALYST))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
  }
}
