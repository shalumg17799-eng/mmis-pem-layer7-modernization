package gov.medicaid.pem.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * Q-095 (user decision 2026-11-19): the submit use case refuses an individual application under an
 * NPI other than the PROVIDER's own before anything is stored or announced.
 */
class SubmitOwnNpiTest {

  static final CurrentUser PROVIDER =
      new CurrentUser("provider1", Set.of("PROVIDER"), Optional.of("1003000111"));
  static final CurrentUser NO_CLAIM =
      new CurrentUser("provider9", Set.of("PROVIDER"), Optional.empty());
  static final CurrentUser ANALYST =
      new CurrentUser("analyst1", Set.of("ANALYST"), Optional.empty());

  final SubmitApplicationTest.FakeStore store = new SubmitApplicationTest.FakeStore();
  final SubmitApplicationTest.FakeOutbox outbox = new SubmitApplicationTest.FakeOutbox();
  final SubmitApplication submit =
      new SubmitApplication(
          store, outbox, Clock.fixed(Instant.parse("2026-11-19T10:00:00Z"), ZoneOffset.UTC));

  static NewApplication application(String npi, String type) {
    boolean org = type.equals("GR") || type.equals("GRP") || type.equals("FA");
    return new NewApplication(
        npi,
        "M00000111",
        type,
        org ? "193200000X" : "207Q00000X",
        org ? null : new NewApplication.Individual("RIVERA", "MARIA", "MD12345", "TX"),
        org ? new NewApplication.Organization("SAMPLE CLINIC 01", null, null) : null);
  }

  @Test
  @DisplayName("[Q-095] IN and IND under the provider's own NPI are stored and announced")
  void own() {
    assertThat(submit.submit(application("1003000111", "IN"), PROVIDER).status())
        .isEqualTo("VALIDATED");
    assertThat(store.saved).hasSize(1);
    assertThat(outbox.sent).hasSize(1);
  }

  @Test
  @DisplayName(
      "[Q-095] IN or IND under another NPI is refused (403): nothing is stored or announced")
  void other() {
    for (String type : new String[] {"IN", "IND"}) {
      assertThatThrownBy(() -> submit.submit(application("1003000222", type), PROVIDER))
          .isInstanceOf(AccessDeniedException.class);
    }
    assertThat(store.saved).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName("[Q-095] IN from a PROVIDER token without an npi claim is refused (403)")
  void noClaim() {
    assertThatThrownBy(() -> submit.submit(application("1003000111", "IN"), NO_CLAIM))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(store.saved).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName("[Q-095] GR and GRP under another NPI are handled as before")
  void group() {
    assertThat(submit.submit(application("1003000444", "GR"), PROVIDER).status())
        .isEqualTo("VALIDATED");
    assertThat(submit.submit(application("1003000555", "GRP"), PROVIDER).status())
        .isEqualTo("VALIDATED");
    assertThat(outbox.sent).hasSize(2);
  }

  @Test
  @DisplayName("[Q-095] staff are not restricted: an ANALYST submits IN for any NPI")
  void staff() {
    assertThat(submit.submit(application("1003000222", "IN"), ANALYST).status())
        .isEqualTo("VALIDATED");
  }
}
