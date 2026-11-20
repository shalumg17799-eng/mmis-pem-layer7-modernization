package gov.medicaid.pem.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.enrollment.application.IntakeFakes.Store;
import gov.medicaid.pem.kit.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-54 AC-1 and AC-5: what the list use case asks the store. A PROVIDER's query is bound to the
 * applications they submitted and to their own NPI (D-023); staff ask without that binding. The
 * page the store answers is returned as it is, and the filters and the paging are passed on.
 */
class ListApplicationsTest {

  static final StoredApplication APP =
      new StoredApplication(
          UUID.fromString("0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11"),
          "REJECTED",
          List.of(),
          "1003000111",
          "M00000111",
          "IN",
          "207Q00000X",
          null,
          null,
          "LEGACY_FILE",
          Instant.parse("2026-03-01T10:00:00Z"));
  static final ApplicationPage PAGE = new ApplicationPage(List.of(APP), 2, 50, 101L);

  /** The query the use case sent to the store. */
  ApplicationQuery asked;

  final ListApplications list =
      new ListApplications(
          new Store() {
            @Override
            public ApplicationPage find(ApplicationQuery query) {
              asked = query;
              return PAGE;
            }
          });

  static CurrentUser provider(String name, String npi) {
    return new CurrentUser(name, Set.of("PROVIDER"), Optional.ofNullable(npi));
  }

  static CurrentUser staff(String role) {
    return new CurrentUser("staff1", Set.of(role), Optional.empty());
  }

  @Test
  @DisplayName("[MAM-54 AC-5] the page of the store is returned as it is")
  void pageIsReturned() {
    assertThat(list.list(null, null, 2, 50, staff("ANALYST"))).isSameAs(PAGE);
  }

  @Test
  @DisplayName(
      "[MAM-54 AC-1] a PROVIDER's query is bound to their login and to the NPI of their token")
  void providerIsBound() {
    list.list(null, "1003000222", 0, 20, provider("provider-a", "1003000111"));
    assertThat(asked.submittedBy()).isEqualTo("provider-a");
    assertThat(asked.ownNpi()).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("[MAM-54 AC-1] a PROVIDER without an npi claim is bound to their login only")
  void providerWithoutNpiClaim() {
    list.list(null, null, 0, 20, provider("provider-a", null));
    assertThat(asked.submittedBy()).isEqualTo("provider-a");
    assertThat(asked.ownNpi()).isNull();
  }

  @Test
  @DisplayName("[MAM-54 AC-5] an ANALYST and a SUPERVISOR ask without any binding")
  void staffIsNotBound() {
    for (String role : List.of("ANALYST", "SUPERVISOR")) {
      list.list("REJECTED", null, 0, 20, staff(role));
      assertThat(asked.submittedBy()).as(role).isNull();
      assertThat(asked.ownNpi()).as(role).isNull();
    }
  }

  @Test
  @DisplayName("[MAM-54 AC-5] the status and NPI filters and the paging are passed on unchanged")
  void filtersAndPagingArePassedOn() {
    list.list("REJECTED", "1003000111", 3, 200, staff("ANALYST"));
    assertThat(asked.status()).isEqualTo("REJECTED");
    assertThat(asked.npi()).isEqualTo("1003000111");
    assertThat(asked.page()).isEqualTo(3);
    assertThat(asked.size()).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "[MAM-54 AC-1] the NPI filter of a PROVIDER stays a filter, it never widens the binding")
  void providerFilterKeepsTheBinding() {
    list.list("VALIDATED", "1003000222", 0, 20, provider("provider-a", "1003000111"));
    assertThat(asked.npi()).isEqualTo("1003000222");
    assertThat(asked.status()).isEqualTo("VALIDATED");
    assertThat(asked.submittedBy()).isEqualTo("provider-a");
  }
}
