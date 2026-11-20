package gov.medicaid.pem.screening.application;

import static gov.medicaid.pem.screening.application.Fakes.CLOCK;
import static gov.medicaid.pem.screening.application.Fakes.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.screening.application.Fakes.Outbox;
import gov.medicaid.pem.screening.application.Fakes.Store;
import gov.medicaid.pem.screening.domain.SourceResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

/**
 * MAM-28 AC-1 and AC-2: a screening on request is answered at once as RUNNING (202), runs in the
 * background with the same id and the provider's current data, and is refused with a 404 for a
 * provider that provider-service does not know; nothing is created then.
 */
class RequestScreeningTest {

  static final String NPI = "1003000555";

  final Store store = new Store();
  final Outbox outbox = new Outbox();
  final List<Runnable> background = new ArrayList<>();
  final List<String> providerReads = new ArrayList<>();
  RuntimeException providerFailure;
  RuntimeException sourcesFailure;
  List<SourceResult> answers = Fakes.results("P", "P", "P", "P");

  final ProviderFactsSource providers =
      npi -> {
        providerReads.add(npi);
        if (providerFailure != null) {
          throw providerFailure;
        }
        return Fakes.facts(npi);
      };

  final ScreenProvider screen =
      new ScreenProvider(
          providers,
          facts -> {
            if (sourcesFailure != null) {
              throw sourcesFailure;
            }
            return answers;
          },
          store,
          outbox,
          CLOCK);

  final RequestScreening request = new RequestScreening(providers, screen, background::add, CLOCK);

  @Test
  @DisplayName("[MAM-28 AC-1] the answer is a RUNNING MANUAL screening of the NPI without results")
  void running() {
    Screening s = request.request(NPI);
    assertThat(s.npi()).isEqualTo(NPI);
    assertThat(s.reason()).isEqualTo("MANUAL");
    assertThat(s.status()).isEqualTo("RUNNING");
    assertThat(s.result()).isNull();
    assertThat(s.results()).isEmpty();
    assertThat(s.startedAt()).isEqualTo(NOW);
    assertThat(s.completedAt()).isNull();
    assertThat(s.screeningId()).isNotNull();
  }

  @Test
  @DisplayName("[MAM-28 AC-1] nothing is stored or announced before the background run")
  void nothingYet() {
    request.request(NPI);
    assertThat(background).hasSize(1);
    assertThat(store.saved).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] the background run stores a COMPLETED MANUAL screening with the id of the answer")
  void completesWithTheSameId() {
    Screening running = request.request(NPI);
    background.get(0).run();
    assertThat(store.saved).hasSize(1);
    Screening done = store.saved.get(0);
    assertThat(done.screeningId()).isEqualTo(running.screeningId());
    assertThat(done.reason()).isEqualTo("MANUAL");
    assertThat(done.status()).isEqualTo("COMPLETED");
    assertThat(done.results()).hasSize(4);
    assertThat(outbox.sent).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-28 AC-5] the provider is read again by the background run: current data")
  void providerReadAgain() {
    request.request(NPI);
    assertThat(providerReads).containsExactly(NPI);
    background.get(0).run();
    assertThat(providerReads).containsExactly(NPI, NPI);
  }

  @Test
  @DisplayName("[MAM-28 AC-1] two requests are two screenings with two ids")
  void twoRequests() {
    Screening first = request.request(NPI);
    Screening second = request.request(NPI);
    assertThat(first.screeningId()).isNotEqualTo(second.screeningId());
    background.forEach(Runnable::run);
    assertThat(store.saved).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-2] an NPI provider-service does not know is a 404 and nothing is started")
  void unknownProvider() {
    providerFailure =
        HttpClientErrorException.create(
            HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null);
    assertThatThrownBy(() -> request.request("1003000999"))
        .isInstanceOfSatisfying(
            ApiException.class,
            e -> {
              assertThat(e.status().value()).isEqualTo(404);
              assertThat(e.getMessage()).contains("1003000999");
            });
    assertThat(background).isEmpty();
    assertThat(store.saved).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-2] a provider-service that cannot be read is not a 404: the failure is passed on")
  void unavailableIsNotNotFound() {
    providerFailure = new ProviderFactsSource.Unavailable(NPI, new IllegalStateException("down"));
    assertThatThrownBy(() -> request.request(NPI))
        .isInstanceOf(ProviderFactsSource.Unavailable.class);
    assertThat(background).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-2] another client error of provider-service is passed on, not turned into a 404")
  void otherClientErrorIsPassedOn() {
    providerFailure =
        HttpClientErrorException.create(
            HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], null);
    assertThatThrownBy(() -> request.request(NPI))
        .isInstanceOf(HttpClientErrorException.Forbidden.class);
    assertThat(background).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-1] a background run that fails does not throw out of the executor and stores nothing")
  void backgroundFailureStaysInTheRunnable() {
    request.request(NPI);
    sourcesFailure = new IllegalStateException("sources are down");
    background.get(0).run();
    assertThat(store.saved).isEmpty();
    assertThat(outbox.sent).isEmpty();
  }
}
