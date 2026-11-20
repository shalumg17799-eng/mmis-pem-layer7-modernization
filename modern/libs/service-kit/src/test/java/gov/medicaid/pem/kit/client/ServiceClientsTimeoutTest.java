package gov.medicaid.pem.kit.client;

import static gov.medicaid.pem.kit.client.CallProbe.failureOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import gov.medicaid.pem.kit.client.CallProbe.Failure;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.MDC;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Written after the code (the timeouts exist already): a call to a service that accepts the
 * connection but does not answer must end with an exception within the read limit, never hang.
 * Every test carries a 30 s guard in its own thread, so a missing limit makes a test FAIL and never
 * hang the build.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ServiceClientsTimeoutTest {

  /** Short, so that a missing limit shows within seconds. */
  private static final Duration READ = Duration.ofMillis(600);

  /** Far from READ, so that swapping the two limits would show. */
  private static final Duration CONNECT = Duration.ofSeconds(8);

  /** A call limited to READ must end well within this; also far below the 30 s guard. */
  private static final Duration BOUND = Duration.ofSeconds(4);

  /** Not shorter than the limit (less the clock slack): an early error is not the timeout. */
  private static final Duration AT_LEAST = Duration.ofMillis(500);

  private static ServiceTokenProvider tokens() {
    ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
    when(tokens.token()).thenReturn("tok-1");
    return tokens;
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] a call to a service that accepts the connection but never answers ends with an exception within the read limit")
  void unansweredCallEndsWithinTheReadLimit() throws Exception {
    try (var silent = new SilentServer()) {
      RestClient client = new ServiceClients(tokens(), CONNECT, READ).create(silent.url());

      Failure failure = failureOf(client);

      assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
      assertThat(failure.elapsed()).isLessThan(BOUND);
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] the failure of an unanswered call is a timeout of a held connection, not an earlier error")
  void unansweredCallFailsBecauseOfTheLimit() throws Exception {
    try (var silent = new SilentServer()) {
      RestClient client = new ServiceClients(tokens(), CONNECT, READ).create(silent.url());

      Failure failure = failureOf(client);

      assertThat(silent.acceptedAConnection()).isTrue();
      assertThat(failure.causedByTimeout()).isTrue();
      assertThat(failure.elapsed()).isGreaterThanOrEqualTo(AT_LEAST);
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] one client may use its own single limit, which replaces the configured read limit")
  void singleLimitReplacesTheConfiguredLimits() throws Exception {
    try (var silent = new SilentServer()) {
      var clients = new ServiceClients(tokens(), CONNECT, Duration.ofSeconds(60));

      Failure failure = failureOf(clients.create(silent.url(), READ));

      assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
      assertThat(failure.elapsed()).isBetween(AT_LEAST, BOUND);
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] with no configuration the read limit is the default (ServiceClients.DEFAULT_READ_TIMEOUT)")
  void defaultReadLimitApplies() throws Exception {
    try (var silent = new SilentServer()) {
      RestClient client = new ServiceClients(tokens()).create(silent.url());

      Failure failure = failureOf(client);

      assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
      assertThat(failure.elapsed())
          .isBetween(
              ServiceClients.DEFAULT_READ_TIMEOUT.minusSeconds(1),
              ServiceClients.DEFAULT_READ_TIMEOUT.plusSeconds(7));
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] the client builder (also used for the token client) ends an unanswered call within its read limit")
  void builderLimitsAnUnansweredCall() throws Exception {
    try (var silent = new SilentServer()) {
      RestClient client = ServiceClients.builder(CONNECT, READ).baseUrl(silent.url()).build();

      Failure failure = failureOf(client);

      assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
      assertThat(failure.elapsed()).isBetween(AT_LEAST, BOUND);
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] the client builder gives its connect limit to the HTTP client (white box: no black-box way to hold a connect)")
  void builderSetsTheConnectLimit() {
    RestClient.Builder builder = ServiceClients.builder(Duration.ofSeconds(3), READ);

    HttpClient http = (HttpClient) ReflectionTestUtils.getField(factoryOf(builder), "httpClient");

    assertThat(http.connectTimeout()).contains(Duration.ofSeconds(3));
  }

  @Test
  @DisplayName("[MAM-26 AC-5] the client builder gives its read limit to the request factory")
  void builderSetsTheReadLimit() {
    RestClient.Builder builder = ServiceClients.builder(CONNECT, Duration.ofMillis(750));

    Object readTimeout = ReflectionTestUtils.getField(factoryOf(builder), "readTimeout");

    assertThat(readTimeout).isEqualTo(Duration.ofMillis(750));
  }

  private static JdkClientHttpRequestFactory factoryOf(RestClient.Builder builder) {
    return (JdkClientHttpRequestFactory) ReflectionTestUtils.getField(builder, "requestFactory");
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] the token client ends an unanswered token request with an exception within its read limit")
  void tokenRequestToASilentServerFails() throws Exception {
    try (var silent = new SilentServer()) {
      var provider =
          new ServiceTokenProvider(
              ServiceClients.builder(CONNECT, READ).build(),
              silent.url() + "/token",
              "pem-services",
              "s3cr3t",
              Clock.systemUTC());

      Failure failure = failureOf(provider::token);

      assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
      assertThat(failure.elapsed()).isBetween(AT_LEAST, BOUND);
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] a slow answer that comes within the read limit is still accepted (the limit is not a blanket failure)")
  void slowAnswerWithinTheLimitIsAccepted() throws Exception {
    try (var stub = new StubHttpServer(Duration.ofMillis(300))) {
      RestClient client =
          new ServiceClients(tokens(), CONNECT, Duration.ofSeconds(3)).create(stub.url());

      String body = client.get().uri("/slow").retrieve().body(String.class);

      assertThat(body).isEqualTo("{\"ok\":true}");
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] over a real connection a limited client still sends the SYSTEM token as a Bearer header")
  void limitedClientSendsTheSystemToken() throws Exception {
    try (var stub = new StubHttpServer(Duration.ZERO)) {
      RestClient client = new ServiceClients(tokens(), CONNECT, READ).create(stub.url());

      client.get().uri("/echo").retrieve().toBodilessEntity();

      assertThat(stub.lastAuthorization()).isEqualTo("Bearer tok-1");
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] over a real connection a limited client still sends the current correlation id")
  void limitedClientSendsTheCorrelationId() throws Exception {
    try (var stub = new StubHttpServer(Duration.ZERO)) {
      RestClient client = new ServiceClients(tokens(), CONNECT, READ).create(stub.url());

      MDC.put(CorrelationId.MDC_KEY, "corr-77");
      try {
        client.get().uri("/echo").retrieve().toBodilessEntity();
      } finally {
        MDC.remove(CorrelationId.MDC_KEY);
      }

      assertThat(stub.lastCorrelationId()).isEqualTo("corr-77");
    }
  }
}
