package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.AccessDeniedException;

/**
 * MAM-60 Lane 3, unit level: ListNotices hands back exactly the page the store returned, for every
 * kind of user, and a PROVIDER's NPI is the one of the token (never one that was typed in).
 */
class ListNoticesResultTest {

  /** Remembers what it was asked and answers with its own page each time. */
  static final class RecordingLog implements NoticePorts.NoticeLog {
    final List<String> npis = new ArrayList<>();
    NoticePage lastPage;

    @Override
    public NoticePage find(String npi, int page, int size) {
      npis.add(npi);
      lastPage =
          new NoticePage(
              List.of(
                  new NoticeView(
                      UUID.randomUUID(),
                      npi == null ? "1003000999" : npi,
                      "APPROVED",
                      "EMAIL",
                      "SENT",
                      Instant.parse("2026-11-20T10:00:00Z"))),
              page,
              size,
              42);
      return lastPage;
    }

    @Override
    public List<Template> templates() {
      return List.of(new Template("TRM", "Termination notice", "NPI {npi}"));
    }
  }

  final RecordingLog store = new RecordingLog();
  final ListNotices list = new ListNotices(store);

  private static CurrentUser staff(String role) {
    return new CurrentUser(role.toLowerCase() + "1", Set.of(role), Optional.empty());
  }

  private static CurrentUser provider(String npi) {
    return new CurrentUser("provider1", Set.of("PROVIDER"), Optional.ofNullable(npi));
  }

  @ParameterizedTest(
      name = "[MAM-60 AC-1/AC-6] {0} gets the page of the store for the NPI filter {1}, unchanged")
  @CsvSource(
      value = {"ANALYST,1003000111", "SUPERVISOR,1003000444", "ADMIN,null", "SYSTEM,null"},
      nullValues = "null")
  void staffGetThePageOfTheStore(String role, String npi) {
    NoticePage result = list.notices(npi, 3, 25, staff(role));

    assertThat(result).isSameAs(store.lastPage);
    assertThat(result.page()).isEqualTo(3);
    assertThat(result.size()).isEqualTo(25);
    assertThat(result.total()).isEqualTo(42);
    assertThat(store.npis).containsExactly(npi);
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-3] a provider gets the page of the store for the NPI of the token, with the"
          + " paging asked for")
  void providerGetsTheirOwnPage() {
    NoticePage unfiltered = list.notices(null, 1, 5, provider("1003000111"));
    assertThat(unfiltered).isSameAs(store.lastPage);
    assertThat(unfiltered.page()).isEqualTo(1);
    assertThat(unfiltered.size()).isEqualTo(5);

    NoticePage named = list.notices("1003000111", 0, 20, provider("1003000111"));
    assertThat(named).isSameAs(store.lastPage);
    assertThat(store.npis).containsExactly("1003000111", "1003000111");
  }

  @ParameterizedTest(
      name =
          "[MAM-60 AC-3] a provider whose token holds {0} who asks for {1} is refused, and the"
              + " store is never asked")
  @CsvSource({
    "1003000111,1003000444",
    "1003000111,1003000112",
    "1003000111,1003000110",
    "1003000111,'1003000111 '",
    "1003000111,''"
  })
  void providerCannotNameAnotherNpi(String own, String asked) {
    assertThatThrownBy(() -> list.notices(asked, 0, 20, provider(own)))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(store.npis).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-3] a user who is neither staff nor a provider with an NPI is refused, and the"
          + " store is never asked")
  void unknownKindOfUserIsRefused() {
    CurrentUser nobody = new CurrentUser("guest", Set.of("GUEST"), Optional.empty());

    assertThatThrownBy(() -> list.notices(null, 0, 20, nobody))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(store.npis).isEmpty();
  }

  @Test
  @DisplayName("[MAM-60 AC-7] the templates are the list of the store, in its order")
  void templatesAreTheListOfTheStore() {
    assertThat(list.templates())
        .containsExactly(new Template("TRM", "Termination notice", "NPI {npi}"));
  }
}
