package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.tomakehurst.wiremock.http.Fault;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.application.ProviderFacts;
import gov.medicaid.pem.screening.application.ProviderFactsSource;
import gov.medicaid.pem.screening.application.ProviderListing;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.HttpClientErrorException;

/**
 * What screening-service reads from provider-service (Q-040 A, D-028): the type, the taxonomy, the
 * licence of an individual, the name as it is sent to SAM, the owner NPI of an organisation, the
 * provider pages of the batch run, and how a failure of provider-service is told apart: a 5xx or no
 * answer is {@code Unavailable} (retried), a 4xx such as 404 is passed on as it is.
 */
class ProviderServiceClientTest extends Lane3Test {

  @Autowired ProviderFactsSource providers;
  @Autowired ProviderListing listing;

  @Test
  @DisplayName(
      "[MAM-20 AC-4] an individual: type, taxonomy, licence, state and the name first + last, no owner")
  void individual() {
    ScreeningWorld.holds(
        ScreeningWorld.individual("1003000111", "MARIA", "RIVERA", "MD12345", "TX"));
    assertThat(providers.facts("1003000111"))
        .isEqualTo(
            new ProviderFacts(
                "1003000111", "IN", "207Q00000X", "MD12345", "TX", "MARIA RIVERA", null));
  }

  @Test
  @DisplayName(
      "[MAM-22 AC-1] an organisation: type, taxonomy, the legal name and the owner NPI, no licence")
  void organisation() {
    ScreeningWorld.holds(
        ScreeningWorld.organization("1003000444", "GR", "EXCLUDED CLINIC LLC", "1990000111"));
    assertThat(providers.facts("1003000444"))
        .isEqualTo(
            new ProviderFacts(
                "1003000444", "GR", "251G00000X", null, null, "EXCLUDED CLINIC LLC", "1990000111"));
  }

  @Test
  @DisplayName("[MAM-22 AC-3] an organisation without an owner NPI has none")
  void noOwner() {
    ScreeningWorld.holds(
        ScreeningWorld.organization("1003000333", "FA", "SUNSET HOSPICE INC", null));
    assertThat(providers.facts("1003000333").ownerNpi()).isNull();
  }

  @Test
  @DisplayName("[MAM-22 AC-3] an owner NPI that is JSON null is no owner")
  void ownerNull() {
    ObjectNode p = ScreeningWorld.organization("1003000333", "FA", "SUNSET HOSPICE INC", null);
    ((ObjectNode) p.get("organization")).putNull("ownerNpi");
    ScreeningWorld.holds(p);
    assertThat(providers.facts("1003000333").ownerNpi()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-4] an organisation record without any organisation data is read without failing: no name, no owner")
  void organisationWithoutData() {
    ObjectNode p = ScreeningWorld.base("1003000333", "FA", "251G00000X", "U");
    ScreeningWorld.holds(p);
    ProviderFacts facts = providers.facts("1003000333");
    assertThat(facts.individual()).isFalse();
    assertThat(facts.ownerNpi()).isNull();
    assertThat(facts.licenseNo()).isNull();
  }

  @Test
  @DisplayName("[MAM-20 AC-3] an individual with a last name only is sent to SAM by that name")
  void lastNameOnly() {
    ObjectNode p = ScreeningWorld.individual("1003000111", "X", "RIVERA", "MD12345", "TX");
    ((ObjectNode) p.get("individual")).remove("firstName");
    ScreeningWorld.holds(p);
    assertThat(providers.facts("1003000111").name()).isEqualTo("RIVERA");
  }

  @Test
  @DisplayName("[MAM-20 AC-3] an individual without a licence has no licence number and no state")
  void noLicence() {
    ScreeningWorld.holds(
        ScreeningWorld.individual("1003000666", "TESTPROV", "FOXTROT", null, null));
    ProviderFacts facts = providers.facts("1003000666");
    assertThat(facts.licenseNo()).isNull();
    assertThat(facts.licenseState()).isNull();
  }

  @Test
  @DisplayName("[MAM-28 AC-2] provider-service is asked with the SYSTEM token and a correlation id")
  void systemToken() {
    ScreeningWorld.holds(
        ScreeningWorld.individual("1003000111", "MARIA", "RIVERA", "MD12345", "TX"));
    providers.facts("1003000111");
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/api/v1/providers/1003000111"))
                    .withHeader("Authorization", equalTo("Bearer test-system-token"))
                    .withHeader("X-Correlation-Id", matching("[A-Za-z0-9._:-]{1,64}"))))
        .hasSize(1);
  }

  // ---- failures ---------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-28 AC-2] a 404 is passed on as it is: the caller decides it means unknown")
  void notFoundIsPassedOn() {
    ScreeningWorld.providerUnknown("1003000999");
    assertThatThrownBy(() -> providers.facts("1003000999"))
        .isInstanceOf(HttpClientErrorException.NotFound.class);
  }

  @ParameterizedTest(
      name =
          "[MAM-20 AC-5] a {0} from provider-service is a client error that is passed on, not Unavailable")
  @ValueSource(ints = {400, 401, 403, 409, 422})
  void clientErrorsArePassedOn(int status) {
    ScreeningWorld.providerAnswers("1003000111", status);
    assertThatThrownBy(() -> providers.facts("1003000111"))
        .isInstanceOf(HttpClientErrorException.class)
        .isNotInstanceOf(ProviderFactsSource.Unavailable.class);
  }

  @ParameterizedTest(name = "[MAM-20 AC-5] a {0} from provider-service is Unavailable")
  @ValueSource(ints = {500, 502, 503, 504})
  void serverErrorsAreUnavailable(int status) {
    ScreeningWorld.providerAnswers("1003000111", status);
    assertThatThrownBy(() -> providers.facts("1003000111"))
        .isInstanceOf(ProviderFactsSource.Unavailable.class)
        .hasMessageContaining("1003000111");
  }

  @ParameterizedTest(
      name = "[MAM-20 AC-5] provider-service ending the call with {0} is Unavailable")
  @org.junit.jupiter.params.provider.EnumSource(
      value = Fault.class,
      names = {"CONNECTION_RESET_BY_PEER", "EMPTY_RESPONSE"})
  void noAnswerIsUnavailable(Fault fault) {
    ScreeningWorld.providerFault("1003000111", fault);
    assertThatThrownBy(() -> providers.facts("1003000111"))
        .isInstanceOf(ProviderFactsSource.Unavailable.class);
  }

  @Test
  @DisplayName(
      "[MAM-20 AC-5] an HTTP 200 without any body is Unavailable, never an invented provider")
  void emptyBodyIsUnavailable() {
    MOCK.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(
                urlPathEqualTo("/api/v1/providers/1003000111"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200)));
    assertThatThrownBy(() -> providers.facts("1003000111"))
        .isInstanceOf(ProviderFactsSource.Unavailable.class);
  }

  // ---- the provider pages -----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-32 AC-2] a page of the list gives the NPI and the screen flag of each provider and the total")
  void page() {
    ScreeningWorld.lists(
        List.of(
            ScreeningWorld.withFlag(
                ScreeningWorld.individual("1003000111", "A", "B", "MD12345", "TX"), "U"),
            ScreeningWorld.withFlag(
                ScreeningWorld.individual("1003000222", "C", "D", "MD22222", "TX"), "P")));
    ProviderListing.Page page = listing.page(0, 200);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items())
        .containsExactly(
            new ProviderListing.Row("1003000111", "U"), new ProviderListing.Row("1003000222", "P"));
  }

  @Test
  @DisplayName(
      "[MAM-32 AC-6] a list answer without items is no page: the call fails instead of reading nothing")
  void noItems() {
    ScreeningWorld.listBody("{\"hello\":1}");
    assertThatThrownBy(() -> listing.page(0, 200)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a list answer with items that is no array is no page")
  void itemsNoArray() {
    ScreeningWorld.listBody("{\"items\":\"x\",\"total\":1}");
    assertThatThrownBy(() -> listing.page(0, 200)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a list answer without a body is no page")
  void emptyListBody() {
    MOCK.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/providers"))
            .atPriority(1)
            .willReturn(aResponse().withStatus(200)));
    assertThatThrownBy(() -> listing.page(0, 200)).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest(name = "[MAM-32 AC-6] a {0} on the list is Unavailable and names the page")
  @ValueSource(ints = {500, 503})
  void listServerError(int status) {
    ScreeningWorld.listAnswers(status);
    assertThatThrownBy(() -> listing.page(2, 200))
        .isInstanceOf(ProviderFactsSource.Unavailable.class)
        .hasMessageContaining("page 2");
  }

  @Test
  @DisplayName("[MAM-32 AC-6] a 4xx on the list is passed on as it is")
  void listClientError() {
    ScreeningWorld.listAnswers(403);
    assertThatThrownBy(() -> listing.page(0, 200)).isInstanceOf(HttpClientErrorException.class);
  }

  @Test
  @DisplayName("[MAM-32 AC-2] the list is asked with the page and the size given")
  void listParameters() {
    MOCK.stubFor(
        com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v1/providers"))
            .atPriority(1)
            .willReturn(okJson("{\"items\":[],\"page\":3,\"size\":50,\"total\":0}")));
    listing.page(3, 50);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo("/api/v1/providers"))
                    .withQueryParam("page", equalTo("3"))
                    .withQueryParam("size", equalTo("50"))))
        .hasSize(1);
  }
}
