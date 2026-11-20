package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

/** MAM-60: who sees which notices; the templates come straight from the store. */
class ListNoticesTest {

  private String askedNpi = "unset";
  private int askedPage = -1;
  private int askedSize = -1;

  private final NoticePorts.NoticeLog log =
      new NoticePorts.NoticeLog() {
        @Override
        public NoticePage find(String npi, int page, int size) {
          askedNpi = npi;
          askedPage = page;
          askedSize = size;
          return new NoticePage(
              List.of(
                  new NoticeView(
                      UUID.randomUUID(), "1003000111", "APPROVED", "EMAIL", "SENT", Instant.now())),
              page,
              size,
              1);
        }

        @Override
        public List<Template> templates() {
          return List.of(new Template("GEN", "General notice", "body {npi}"));
        }
      };

  private final ListNotices list = new ListNotices(log);

  private static CurrentUser provider(String npi) {
    return new CurrentUser("provider1", Set.of("PROVIDER"), Optional.ofNullable(npi));
  }

  private static CurrentUser staff(String role) {
    return new CurrentUser(role.toLowerCase() + "1", Set.of(role), Optional.empty());
  }

  @Test
  @DisplayName(
      "MAM-60 AC-1: staff get the notices of the NPI they ask for, with the paging they ask for")
  void staffFilter() {
    NoticePage p = list.notices("1003000111", 2, 50, staff("ANALYST"));
    assertThat(askedNpi).isEqualTo("1003000111");
    assertThat(askedPage).isEqualTo(2);
    assertThat(askedSize).isEqualTo(50);
    assertThat(p.items()).hasSize(1);
    assertThat(p.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("MAM-60 AC-6: staff without an NPI filter get every provider's notices")
  void staffAll() {
    list.notices(null, 0, 20, staff("SUPERVISOR"));
    assertThat(askedNpi).isNull();
    list.notices(null, 0, 20, staff("ADMIN"));
    assertThat(askedNpi).isNull();
  }

  @Test
  @DisplayName("MAM-60 AC-3: a provider without a filter gets only their own NPI")
  void providerOwn() {
    list.notices(null, 0, 20, provider("1003000111"));
    assertThat(askedNpi).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("MAM-60 AC-3: a provider may name their own NPI")
  void providerOwnNamed() {
    list.notices("1003000111", 0, 20, provider("1003000111"));
    assertThat(askedNpi).isEqualTo("1003000111");
  }

  @Test
  @DisplayName(
      "MAM-60 AC-3: a provider asking for another NPI is refused, and the store is not asked")
  void providerOther() {
    assertThatThrownBy(() -> list.notices("1003000444", 0, 20, provider("1003000111")))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(askedNpi).isEqualTo("unset");
  }

  @Test
  @DisplayName("MAM-60 AC-3: a provider whose token has no NPI sees nothing (refused)")
  void providerWithoutNpi() {
    assertThatThrownBy(() -> list.notices(null, 0, 20, provider(null)))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(askedNpi).isEqualTo("unset");
  }

  @Test
  @DisplayName("MAM-60 AC-7: the templates are listed as stored")
  void templates() {
    assertThat(list.templates())
        .extracting(Template::code, Template::subject, Template::body)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("GEN", "General notice", "body {npi}"));
  }
}
