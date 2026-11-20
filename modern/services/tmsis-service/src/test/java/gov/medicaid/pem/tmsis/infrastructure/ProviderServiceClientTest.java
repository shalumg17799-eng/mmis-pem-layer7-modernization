package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Fault;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TestProviderService.Request;
import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestClientException;

/**
 * MAM-71 AC-5 and AC-6, MAM-78 AC-1: the client of provider-service (D-014, ADR-002, ADR-007) calls
 * GET /api/v1/providers?status=A&page=&size= with the SYSTEM token and reads the page of the
 * contract (items with npi, medicaidId, enrollStatus and effectiveDate, and the total). A failure
 * of provider-service is an exception, never an empty page: a provider must not be missed without a
 * trace. A provider-service that does not answer ends the call within the client's timeout.
 */
@Timeout(120)
class ProviderServiceClientTest extends IntegrationTest {

  @Autowired ActiveProviders providers;

  // ---- AC-6: the call ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-6] the client calls GET /api/v1/providers with status=A, page and size, and the SYSTEM token as a bearer token")
  void theCall() {
    TestProviderService.holds(Prov.active("1003000111"));
    providers.page(2, 50);
    assertThat(TestProviderService.searches()).hasSize(1);
    Request call = TestProviderService.searches().get(0);
    assertThat(call.method()).isEqualTo("GET");
    assertThat(call.path()).isEqualTo("/api/v1/providers");
    assertThat(call.query())
        .containsEntry("status", "A")
        .containsEntry("page", "2")
        .containsEntry("size", "50");
    assertThat(call.authorization()).isEqualTo("Bearer " + TestProviderService.SYSTEM_TOKEN);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-6] the client asks for ACTIVE providers only: no other query parameter than status, page and size")
  void onlyThreeParameters() {
    TestProviderService.holds(Prov.active("1003000111"));
    providers.page(0, 200);
    assertThat(TestProviderService.searches().get(0).query())
        .containsOnlyKeys("status", "page", "size");
  }

  @Test
  @DisplayName("[MAM-71 AC-6] every call carries an X-Correlation-Id")
  void correlationIdHeader() {
    TestProviderService.holds(Prov.active("1003000111"));
    providers.page(0, 200);
    assertThat(TestProviderService.searches().get(0).correlationId()).isNotBlank();
  }

  // ---- the page that is read --------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a page holds the NPI, Medicaid ID, status and enrolment date of each provider, and the total")
  void pageContent() {
    TestProviderService.holds(
        Prov.active("1003000111").effective("2026-03-31"),
        Prov.active("1003000333").effective("1998-05-01"));
    ActiveProviders.Page page = providers.page(0, 200);
    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items())
        .containsExactly(
            new ProviderRow("1003000111", "M00000111", "A", LocalDate.parse("2026-03-31")),
            new ProviderRow("1003000333", "M00000333", "A", LocalDate.parse("1998-05-01")));
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the total is the number of all ACTIVE providers, not of the page: 450 providers, page 2 of 200 has 50 items")
  void totalAndLastPage() {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      all.add(Prov.active(String.format("1003%06d", 1000 + i)));
    }
    TestProviderService.holds(all);
    ActiveProviders.Page last = providers.page(2, 200);
    assertThat(last.items()).hasSize(50);
    assertThat(last.total()).isEqualTo(450);
    assertThat(providers.page(3, 200).items()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-1] an enrolment date that is missing is read as no date, not as an error")
  void missingEffectiveDate() {
    TestProviderService.holds(new Prov("1003000111", "M00000111", "A", null));
    assertThat(providers.page(0, 200).items().get(0).effectiveDate()).isNull();
  }

  @Test
  @DisplayName("[MAM-71 AC-5] a page with no provider is an empty page with total 0")
  void emptyPage() {
    TestProviderService.holds();
    ActiveProviders.Page page = providers.page(0, 200);
    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  // ---- failures are not hidden ------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-75 AC-6] a 500 of provider-service is an exception, not an empty page")
  void serverError() {
    TestProviderService.providerFails(Fault.SERVER_ERROR);
    assertThatThrownBy(() -> providers.page(0, 200)).isInstanceOf(RestClientException.class);
  }

  @Test
  @DisplayName("[MAM-75 AC-6] a cut connection is an exception, not an empty page")
  void connectionCut() {
    TestProviderService.providerFails(Fault.CONNECTION_CUT);
    assertThatThrownBy(() -> providers.page(0, 200)).isInstanceOf(RestClientException.class);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] an answer that is no provider page is an exception, not an empty page")
  void notAPage() {
    TestProviderService.providerFails(Fault.NOT_A_PAGE);
    assertThatThrownBy(() -> providers.page(0, 200))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("provider page");
  }

  @Test
  @DisplayName("[MAM-75 AC-6] an answer that is not JSON is an exception, not an empty page")
  void notJson() {
    TestProviderService.providerFails(Fault.NOT_JSON);
    assertThatThrownBy(() -> providers.page(0, 200)).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-6] provider-service does not answer: the call ends with an exception within the client timeout, long before the answer would have come (lesson d)")
  void noAnswerEndsWithinTheTimeout() {
    TestProviderService.providerFails(Fault.SLOW);
    long start = System.nanoTime();
    assertThatThrownBy(() -> providers.page(0, 200)).isInstanceOf(RestClientException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start))
        .isLessThan(Duration.ofMillis(TestProviderService.SLOW_MILLIS - 3000));
  }

  @Test
  @DisplayName(
      "[MAM-78 AC-1] an enrolment date that is no date is an exception, not a missing date")
  void invalidEffectiveDate() {
    TestProviderService.holds(Prov.active("1003000111").effective("31/12/2026"));
    assertThatThrownBy(() -> providers.page(0, 200)).isInstanceOf(RuntimeException.class);
  }
}
