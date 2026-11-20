package gov.medicaid.pem.enrollment.application;

import static gov.medicaid.pem.enrollment.application.IntakeFakes.CLOCK;
import static gov.medicaid.pem.enrollment.application.IntakeFakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.enrollment.application.IntakeFakes.Outbox;
import gov.medicaid.pem.enrollment.application.IntakeFakes.Store;
import gov.medicaid.pem.enrollment.domain.ValidationError;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The file path of the submit use case (MAM-56): {@code fromFile} gives an application of the
 * APPINPUT file the same validation, storage and events as an online one, with source LEGACY_FILE,
 * and ties it to its intake run through the {@code attach} callback in the same unit of work.
 */
class SubmitFromFileTest {

  /** The order in which the port calls happen: save, attach, enqueue. */
  final List<String> order = new ArrayList<>();

  final Store store =
      new Store() {
        @Override
        public void save(StoredApplication application, String by) {
          order.add("save");
          super.save(application, by);
        }
      };

  final Outbox outbox =
      new Outbox() {
        @Override
        public void enqueue(String type, String subject, Object data) {
          order.add("enqueue " + type);
          super.enqueue(type, subject, data);
        }
      };

  final SubmitApplication submit = new SubmitApplication(store, outbox, CLOCK);

  static NewApplication application(String npi, String type, String taxonomy) {
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
      "[MAM-56 AC-4] a valid record is stored VALIDATED from LEGACY_FILE under the name of the starter, at the clock time")
  void validFromFile() {
    StoredApplication a =
        submit.fromFile(application("1003000111", "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(a.status()).isEqualTo("VALIDATED");
    assertThat(a.source()).isEqualTo("LEGACY_FILE");
    assertThat(a.errors()).isEmpty();
    assertThat(a.submittedAt()).isEqualTo(NOW);
    assertThat(store.saved).containsExactly(a);
    assertThat(store.submittedBy).containsExactly("admin1");
  }

  @Test
  @DisplayName("[MAM-56 AC-4] a valid record is announced once as ApplicationValidated with its NPI as subject")
  void validAnnounced() {
    StoredApplication a =
        submit.fromFile(application("1003000111", "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(outbox.sent).hasSize(1);
    assertThat(outbox.sent.get(0).type()).isEqualTo("ApplicationValidated");
    assertThat(outbox.sent.get(0).subject()).isEqualTo("1003000111");
    assertThat(outbox.sent.get(0).data())
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
  @DisplayName("[MAM-56 AC-4] a blank NPI is REJECTED with exactly E101 and no event (no subject for it)")
  void blankNpi() {
    StoredApplication a = submit.fromFile(application(null, "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(a.status()).isEqualTo("REJECTED");
    assertThat(codes(a.errors())).containsExactly("E101/npi");
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName("[MAM-56 AC-4] a record that is no 10-digit NPI is REJECTED with E101 and no event")
  void malformedNpi() {
    StoredApplication a =
        submit.fromFile(application("100300011", "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(codes(a.errors())).containsExactly("E101/npi");
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-56 AC-4] type XX and an empty taxonomy are two errors, E102 and E104, one per failed check")
  void twoChecksFail() {
    StoredApplication a = submit.fromFile(application("1003000222", "XX", ""), "admin1", id -> {});
    assertThat(codes(a.errors())).containsExactlyInAnyOrder("E102/providerType", "E104/taxonomy");
    assertThat(outbox.sent).hasSize(1);
    assertThat(outbox.sent.get(0).type()).isEqualTo("ApplicationRejected");
    assertThat(outbox.sent.get(0).subject()).isEqualTo("1003000222");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-3] a second application for an NPI that is already VALIDATED is REJECTED with exactly E103")
  void duplicate() {
    submit.fromFile(application("1003000111", "IN", "207Q00000X"), "admin1", id -> {});
    StoredApplication again =
        submit.fromFile(application("1003000111", "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(again.status()).isEqualTo("REJECTED");
    assertThat(codes(again.errors())).containsExactly("E103/npi");
    assertThat(outbox.count("ApplicationValidated")).isEqualTo(1);
    assertThat(outbox.count("ApplicationRejected")).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-57 AC-8] the run key is written for the stored application: attach gets its id once")
  void attachGetsTheApplicationId() {
    List<UUID> attached = new ArrayList<>();
    StoredApplication a =
        submit.fromFile(application("1003000111", "IN", "207Q00000X"), "admin1", attached::add);
    assertThat(attached).containsExactly(a.applicationId());
  }

  @Test
  @DisplayName("[MAM-57 AC-8] a REJECTED application is tied to its run too")
  void attachForRejected() {
    List<UUID> attached = new ArrayList<>();
    StoredApplication a =
        submit.fromFile(application("1003000222", "IN", ""), "admin1", attached::add);
    assertThat(attached).containsExactly(a.applicationId());
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-8] the order is: save, tie to the run, announce, so all three belong to one unit of work")
  void orderOfTheWrites() {
    submit.fromFile(application("1003000111", "IN", "207Q00000X"), "admin1", id -> order.add("attach"));
    assertThat(order).containsExactly("save", "attach", "enqueue ApplicationValidated");
  }

  @Test
  @DisplayName(
      "[MAM-57 AC-8] when the tie to the run fails, nothing is announced and the failure reaches the caller (the transaction rolls back)")
  void failedAttachAnnouncesNothing() {
    assertThatThrownBy(
            () ->
                submit.fromFile(
                    application("1003000111", "IN", "207Q00000X"),
                    "admin1",
                    id -> {
                      throw new IllegalStateException("the run key cannot be written");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("the run key cannot be written");
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[Q-095] a record of the file is not held to the own-NPI rule: the file is loaded by staff under any NPI")
  void noOwnNpiRuleForFiles() {
    StoredApplication a =
        submit.fromFile(application("1003000555", "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(a.status()).isEqualTo("VALIDATED");
  }

  @Test
  @DisplayName(
      "[Q-100] an individual record with a blank NPI is REJECTED with E101, not refused: the rejection is the answer")
  void blankNpiIsRejectedNotRefused() {
    StoredApplication a = submit.fromFile(application("", "IN", "207Q00000X"), "admin1", id -> {});
    assertThat(codes(a.errors())).containsExactly("E101/npi");
  }
}
