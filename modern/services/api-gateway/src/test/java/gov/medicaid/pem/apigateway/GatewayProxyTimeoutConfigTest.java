package gov.medicaid.pem.apigateway;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.kit.client.ServiceClients;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * MAM-29 AC-6 and D-024 (code review of 2026-11-20, MUST-FIX 1), the shipped configuration: the
 * calls the gateway forwards have a connect limit and a read limit when nothing is overridden. The
 * behaviour with a short limit is in {@link GatewayProxyTimeoutTest}; this class reads {@code
 * api-gateway.yml} itself (no Spring context, no network), so the SHIPPED DEFAULTS are tested,
 * which a test that sets its own limit cannot do. The connect limit has no behaviour test: it needs
 * an address that neither answers nor refuses, which no machine gives reliably.
 *
 * <p>A property name is accepted if the jar's configuration metadata (Spring Cloud Gateway Server
 * MVC 4.3.5 and Spring Boot 3.5) defines it for the proxy's HTTP client; that the proxy really
 * honours the one that is set is proved by the behaviour tests. The environment variable {@code
 * PEM_GATEWAY_READ_TIMEOUT} is the name given in the review; it is the same variable that {@link
 * UpstreamFailureTestBase} sets.
 *
 * <p>The window of the read limit is the test's reasoning, not a business rule (recorded in the red
 * report): longer than the limit of a service's own calls to other services ({@link
 * ServiceClients#DEFAULT_READ_TIMEOUT}), so a service can still answer with its own timeout problem
 * before the gateway gives up; and not unbounded, here 5 minutes at most. The connect limit is a
 * few seconds at most.
 */
class GatewayProxyTimeoutConfigTest {

  static final List<String> READ_KEYS =
      List.of(
          "spring.http.client.read-timeout",
          "spring.cloud.gateway.server.webmvc.http-client.read-timeout",
          "spring.cloud.gateway.mvc.http-client.read-timeout");

  static final List<String> CONNECT_KEYS =
      List.of(
          "spring.http.client.connect-timeout",
          "spring.cloud.gateway.server.webmvc.http-client.connect-timeout",
          "spring.cloud.gateway.mvc.http-client.connect-timeout");

  static final String READ_VARIABLE = "PEM_GATEWAY_READ_TIMEOUT";
  static final Duration LONGEST_READ = Duration.ofMinutes(5);
  static final Duration LONGEST_CONNECT = Duration.ofSeconds(10);

  /**
   * The first of the keys that api-gateway.yml sets, with its placeholders resolved against the
   * overrides only (not the machine's environment, so the result is the same everywhere).
   */
  private static Duration shipped(List<String> keys, Map<String, Object> overrides)
      throws IOException {
    MutablePropertySources sources = new MutablePropertySources();
    sources.addFirst(new MapPropertySource("overrides", overrides));
    for (PropertySource<?> source :
        new YamlPropertySourceLoader()
            .load("api-gateway", new ClassPathResource("api-gateway.yml"))) {
      sources.addLast(source);
    }
    Binder binder =
        new Binder(
            ConfigurationPropertySources.from(sources),
            new PropertySourcesPlaceholdersResolver(sources));
    for (String key : keys) {
      BindResult<Duration> bound = binder.bind(key, Duration.class);
      if (bound.isBound()) {
        return bound.get();
      }
    }
    throw new AssertionError(
        "api-gateway.yml sets none of "
            + keys
            + ": the calls the gateway forwards have no limit (a service that never answers"
            + " holds the caller for ever)");
  }

  @Test
  @DisplayName("[MAM-29 AC-6] the shipped configuration sets a read limit for forwarded calls")
  void readLimitIsConfigured() throws Exception {
    assertThat(shipped(READ_KEYS, Map.of())).isPositive();
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the read limit is longer than a service's own limit for its calls to other services")
  void readLimitExceedsTheServicesOwnLimit() throws Exception {
    assertThat(shipped(READ_KEYS, Map.of())).isGreaterThan(ServiceClients.DEFAULT_READ_TIMEOUT);
  }

  @Test
  @DisplayName("[MAM-29 AC-6] the read limit is not unbounded (5 minutes at most)")
  void readLimitIsBounded() throws Exception {
    assertThat(shipped(READ_KEYS, Map.of())).isLessThanOrEqualTo(LONGEST_READ);
  }

  @Test
  @DisplayName("[MAM-29 AC-6] the shipped configuration sets a connect limit for forwarded calls")
  void connectLimitIsConfigured() throws Exception {
    assertThat(shipped(CONNECT_KEYS, Map.of())).isPositive();
  }

  @Test
  @DisplayName("[MAM-29 AC-6] the connect limit is a few seconds (10 seconds at most)")
  void connectLimitIsShort() throws Exception {
    assertThat(shipped(CONNECT_KEYS, Map.of())).isLessThanOrEqualTo(LONGEST_CONNECT);
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-6] the environment variable PEM_GATEWAY_READ_TIMEOUT sets the read limit")
  void readLimitFollowsTheEnvironmentVariable() throws Exception {
    assertThat(shipped(READ_KEYS, Map.of(READ_VARIABLE, "7s"))).isEqualTo(Duration.ofSeconds(7));
  }
}
