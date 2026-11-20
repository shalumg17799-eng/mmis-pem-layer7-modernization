package gov.medicaid.pem.kit.config;

import static gov.medicaid.pem.kit.client.CallProbe.failureOf;
import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.kit.client.CallProbe.Failure;
import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.kit.client.ServiceTokenProvider;
import gov.medicaid.pem.kit.client.SilentServer;
import gov.medicaid.pem.kit.client.StubHttpServer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Written after the code (the timeouts exist already): the limits of the service clients come from
 * {@code pem.client.connect-timeout} and {@code pem.client.read-timeout}, have defaults, can be
 * overridden, and apply to the token client as well as to the clients of other services. Every test
 * carries a 30 s guard in its own thread, so a missing limit makes a test FAIL and never hang the
 * build.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class PemClientTimeoutConfigurationTest {

  private static final Duration AT_LEAST = Duration.ofMillis(500);
  private static final Duration BOUND = Duration.ofSeconds(4);

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  PemWebAutoConfiguration.class, PemClientAutoConfiguration.class))
          .withPropertyValues(
              "spring.application.name=sanctions-service", "pem.client.client-secret=x");

  @Test
  @DisplayName(
      "[MAM-26 AC-5] without settings the client limits are the documented defaults: connect 2 s, read 5 s")
  void defaultsComeFromTheKit() {
    runner
        .withPropertyValues("pem.client.token-uri=http://keycloak:8180/token")
        .run(
            ctx -> {
              var client = ctx.getBean(PemKitProperties.class).getClient();
              assertThat(client.getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
              assertThat(client.getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
            });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] pem.client.connect-timeout and pem.client.read-timeout override the defaults")
  void limitsCanBeOverridden() {
    runner
        .withPropertyValues(
            "pem.client.token-uri=http://keycloak:8180/token",
            "pem.client.connect-timeout=1500ms",
            "pem.client.read-timeout=PT3S")
        .run(
            ctx -> {
              var client = ctx.getBean(PemKitProperties.class).getClient();
              assertThat(client.getConnectTimeout()).isEqualTo(Duration.ofMillis(1500));
              assertThat(client.getReadTimeout()).isEqualTo(Duration.ofSeconds(3));
            });
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] the configured read limit applies to the clients of other services: an unanswered call fails within it")
  void serviceClientsUseTheConfiguredReadLimit() throws Exception {
    try (var tokenServer = new StubHttpServer(Duration.ZERO);
        var silent = new SilentServer()) {
      runner
          .withPropertyValues(
              "pem.client.token-uri=" + tokenServer.url() + "/token",
              "pem.client.connect-timeout=8s",
              "pem.client.read-timeout=600ms")
          .run(
              ctx -> {
                RestClient client = ctx.getBean(ServiceClients.class).create(silent.url());

                Failure failure = failureOf(client);

                assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
                assertThat(failure.elapsed()).isBetween(AT_LEAST, BOUND);
              });
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] the configured read limit applies to the token client: an unanswered token request fails within it")
  void tokenClientUsesTheConfiguredReadLimit() throws Exception {
    try (var silent = new SilentServer()) {
      runner
          .withPropertyValues(
              "pem.client.token-uri=" + silent.url() + "/token",
              "pem.client.connect-timeout=8s",
              "pem.client.read-timeout=600ms")
          .run(
              ctx -> {
                ServiceTokenProvider tokens = ctx.getBean(ServiceTokenProvider.class);

                Failure failure = failureOf(tokens::token);

                assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
                assertThat(failure.elapsed()).isBetween(AT_LEAST, BOUND);
              });
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] with no read limit configured the token client still gives up after the default instead of hanging")
  void tokenClientHasADefaultLimit() throws Exception {
    try (var silent = new SilentServer()) {
      runner
          .withPropertyValues("pem.client.token-uri=" + silent.url() + "/token")
          .run(
              ctx -> {
                ServiceTokenProvider tokens = ctx.getBean(ServiceTokenProvider.class);

                Failure failure = failureOf(tokens::token);

                assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
                assertThat(failure.elapsed())
                    .isBetween(
                        ServiceClients.DEFAULT_READ_TIMEOUT.minusSeconds(1),
                        ServiceClients.DEFAULT_READ_TIMEOUT.plusSeconds(7));
              });
    }
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] a call to another service fails within the limit when the token endpoint does not answer")
  void callFailsWhenTheTokenEndpointIsSilent() throws Exception {
    try (var silentToken = new SilentServer();
        var service = new StubHttpServer(Duration.ZERO)) {
      runner
          .withPropertyValues(
              "pem.client.token-uri=" + silentToken.url() + "/token",
              "pem.client.connect-timeout=8s",
              "pem.client.read-timeout=600ms")
          .run(
              ctx -> {
                RestClient client = ctx.getBean(ServiceClients.class).create(service.url());

                Failure failure = failureOf(client);

                assertThat(failure.error()).isInstanceOf(ResourceAccessException.class);
                assertThat(failure.elapsed()).isBetween(AT_LEAST, BOUND);
              });
    }
  }
}
