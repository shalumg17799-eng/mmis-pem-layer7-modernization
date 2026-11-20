package gov.medicaid.pem.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.enrollment.domain.ValidationError;
import gov.medicaid.pem.kit.security.CurrentUser;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-46: the submit use case decides what is stored and which event is announced (D-001, D-016,
 * D-019).
 */
class SubmitApplicationTest {

  static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");
  static final CurrentUser PROVIDER =
      new CurrentUser("provider1", Set.of("PROVIDER"), Optional.of("1003000111"));
  static final CurrentUser ANALYST =
      new CurrentUser("analyst1", Set.of("ANALYST"), Optional.empty());

  /** In-memory store: applications in submission order and the feed-code table of AC-7. */
  static class FakeStore implements ApplicationStore {
    final List<StoredApplication> saved = new ArrayList<>();
    final List<String> submittedBy = new ArrayList<>();
    ApplicationQuery lastQuery;

    @Override
    public boolean hasValidated(String npi) {
      return saved.stream().anyMatch(a -> a.status().equals("VALIDATED") && npi.equals(a.npi()));
    }

    @Override
    public Map<String, String> providerTypeMappings() {
      return Map.of("IND", "IN", "GRP", "GR", "HOSP", "FA");
    }

    @Override
    public void save(StoredApplication application, String by) {
      saved.add(application);
      submittedBy.add(by);
    }

    @Override
    public ApplicationPage find(ApplicationQuery query) {
      lastQuery = query;
      return new ApplicationPage(List.copyOf(saved), query.page(), query.size(), saved.size());
    }
  }

  record Sent(String type, String subject, Object data) {}

  static class FakeOutbox implements EventOutbox {
    final List<Sent> sent = new ArrayList<>();

    @Override
    public void enqueue(String type, String subject, Object data) {
      sent.add(new Sent(type, subject, data));
    }
  }

  final FakeStore store = new FakeStore();
  final FakeOutbox outbox = new FakeOutbox();
  final SubmitApplication submit =
      new SubmitApplication(store, outbox, Clock.fixed(NOW, ZoneOffset.UTC));

  static NewApplication individual(String npi, String type, String taxonomy) {
    return new NewApplication(
        npi,
        "M00000111",
        type,
        taxonomy,
        new NewApplication.Individual("RIVERA", "MARIA", "MD12345", "TX"),
        null);
  }

  static List<String> codes(List<ValidationError> errors) {
    return errors.stream().map(e -> e.code().name() + "/" + e.field()).toList();
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-1] a valid application is stored VALIDATED from the PORTAL and announced once with its details")
  void valid() {
    StoredApplication a = submit.submit(individual("1003000111", "IN", "207Q00000X"), PROVIDER);
    assertThat(a.status()).isEqualTo("VALIDATED");
    assertThat(a.source()).isEqualTo("PORTAL");
    assertThat(a.errors()).isEmpty();
    assertThat(a.providerType()).isEqualTo("IN");
    assertThat(a.submittedAt()).isEqualTo(NOW);
    assertThat(a.applicationId()).isNotNull();
    assertThat(store.saved).containsExactly(a);
    assertThat(store.submittedBy).containsExactly("provider1");
    assertThat(outbox.sent).hasSize(1);
    Sent e = outbox.sent.get(0);
    assertThat(e.type()).isEqualTo("ApplicationValidated");
    assertThat(e.subject()).isEqualTo("1003000111");
    assertThat(e.data())
        .isEqualTo(
            new ApplicationValidatedData(
                a.applicationId(),
                "1003000111",
                "M00000111",
                "IN",
                "207Q00000X",
                new NewApplication.Individual("RIVERA", "MARIA", "MD12345", "TX"),
                null));
  }

  @Test
  @DisplayName("[MAM-46 AC-7] a feed code is stored and announced as its legacy type")
  void mapped() {
    StoredApplication a = submit.submit(individual("1003000111", "IND", "207Q00000X"), ANALYST);
    assertThat(a.providerType()).isEqualTo("IN");
    assertThat(((ApplicationValidatedData) outbox.sent.get(0).data()).providerType())
        .isEqualTo("IN");
    assertThat(store.submittedBy).containsExactly("analyst1");
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-3] an invalid type is stored REJECTED and announced once as ApplicationRejected with its error")
  void rejected() {
    StoredApplication a = submit.submit(individual("1003000222", "XX", "207Q00000X"), PROVIDER);
    assertThat(a.status()).isEqualTo("REJECTED");
    assertThat(codes(a.errors())).containsExactly("E102/providerType");
    assertThat(a.providerType()).isNull();
    assertThat(store.saved).containsExactly(a);
    assertThat(outbox.sent)
        .containsExactly(
            new Sent(
                "ApplicationRejected",
                "1003000222",
                new ApplicationRejectedData(
                    a.applicationId(),
                    "1003000222",
                    List.of(new ApplicationRejectedData.Error("E102", "providerType")))));
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-6] several failed checks are stored and announced once, with every error")
  void several() {
    StoredApplication a = submit.submit(individual("1003000333", "XX", ""), PROVIDER);
    assertThat(codes(a.errors())).containsExactlyInAnyOrder("E102/providerType", "E104/taxonomy");
    assertThat(store.saved).hasSize(1);
    assertThat(outbox.sent).hasSize(1);
    assertThat(((ApplicationRejectedData) outbox.sent.get(0).data()).errors()).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-2] an application without a 10-digit NPI is stored REJECTED but never announced")
  void noNpiNoEvent() {
    StoredApplication empty = submit.submit(individual("", "IN", "207Q00000X"), PROVIDER);
    StoredApplication shortNpi = submit.submit(individual("12345", "IN", "207Q00000X"), PROVIDER);
    assertThat(codes(empty.errors())).containsExactly("E101/npi");
    assertThat(codes(shortNpi.errors())).containsExactly("E101/npi");
    assertThat(store.saved).hasSize(2);
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-5] a second application for a VALIDATED NPI fails E103; a REJECTED one does not block")
  void duplicate() {
    submit.submit(individual("1003000111", "IN", "207Q00000X"), PROVIDER);
    StoredApplication second =
        submit.submit(individual("1003000111", "IN", "207Q00000X"), PROVIDER);
    assertThat(codes(second.errors())).containsExactly("E103/npi");
    assertThat(outbox.sent)
        .extracting(Sent::type)
        .containsExactly("ApplicationValidated", "ApplicationRejected");

    CurrentUser provider2 =
        new CurrentUser("provider2", Set.of("PROVIDER"), Optional.of("1003000222"));
    submit.submit(individual("1003000222", "IN", ""), provider2);
    StoredApplication retry =
        submit.submit(individual("1003000222", "IN", "207Q00000X"), provider2);
    assertThat(retry.status()).isEqualTo("VALIDATED");
  }

  @Test
  @DisplayName(
      "[MAM-46 AC-2] a PROVIDER lists only what they submitted or what carries their NPI; staff list everything")
  void listing() {
    ListApplications list = new ListApplications(store);
    list.list("REJECTED", null, 0, 20, PROVIDER);
    assertThat(store.lastQuery)
        .isEqualTo(new ApplicationQuery("REJECTED", null, "provider1", "1003000111", 0, 20));
    list.list(null, "1003000111", 1, 5, ANALYST);
    assertThat(store.lastQuery)
        .isEqualTo(new ApplicationQuery(null, "1003000111", null, null, 1, 5));
    CurrentUser noClaim = new CurrentUser("provider9", Set.of("PROVIDER"), Optional.empty());
    list.list(null, null, 0, 20, noClaim);
    assertThat(store.lastQuery)
        .isEqualTo(new ApplicationQuery(null, null, "provider9", null, 0, 20));
  }
}
