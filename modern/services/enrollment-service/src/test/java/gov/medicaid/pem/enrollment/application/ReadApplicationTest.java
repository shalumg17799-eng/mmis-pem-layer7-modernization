package gov.medicaid.pem.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import gov.medicaid.pem.kit.web.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/**
 * MAM-54: one application by id; a PROVIDER reads only their own (submitted by them or for their
 * NPI).
 */
class ReadApplicationTest {

  static final UUID ID = UUID.fromString("0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11");
  static final StoredApplication APP =
      new StoredApplication(
          ID,
          "VALIDATED",
          List.of(),
          "1003000222",
          "M00000222",
          "IN",
          "207Q00000X",
          null,
          null,
          "PORTAL",
          Instant.parse("2026-03-01T10:00:00Z"));

  final ReadApplication read =
      new ReadApplication(
          id ->
              id.equals(ID)
                  ? Optional.of(new OwnedApplication(APP, "provider-b"))
                  : Optional.empty());

  static CurrentUser provider(String name, String npi) {
    return new CurrentUser(name, Set.of("PROVIDER"), Optional.ofNullable(npi));
  }

  @Test
  @DisplayName("[MAM-54 AC-3] the provider who submitted it reads the application")
  void submitter() {
    assertThat(read.byId(ID, provider("provider-b", null))).isEqualTo(APP);
  }

  @Test
  @DisplayName("[MAM-54 AC-3] the provider whose NPI it carries reads the application")
  void ownNpi() {
    assertThat(read.byId(ID, provider("someone-else", "1003000222"))).isEqualTo(APP);
  }

  @Test
  @DisplayName("[MAM-54 AC-2] another provider is refused (403)")
  void otherProvider() {
    assertThatThrownBy(() -> read.byId(ID, provider("provider-a", "1003000111")))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  @DisplayName("[MAM-54 AC-5] staff read every application")
  void staff() {
    assertThat(read.byId(ID, new CurrentUser("analyst1", Set.of("ANALYST"), Optional.empty())))
        .isEqualTo(APP);
  }

  @Test
  @DisplayName("[MAM-54 AC-7] an unknown id is a 404")
  void unknown() {
    assertThatThrownBy(
            () ->
                read.byId(
                    UUID.randomUUID(),
                    new CurrentUser("analyst1", Set.of("ANALYST"), Optional.empty())))
        .isInstanceOfSatisfying(
            ApiException.class, e -> assertThat(e.status().value()).isEqualTo(404));
  }
}
