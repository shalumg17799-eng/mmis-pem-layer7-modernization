package gov.medicaid.pem.kit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.kit.client.ServiceTokenProvider;
import gov.medicaid.pem.kit.events.OutboxRelay;
import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import gov.medicaid.pem.kit.web.ProblemHandler;
import java.time.Clock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;

class PemKitAutoConfigurationTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withPropertyValues(
              "spring.application.name=provider-service",
              "pem.security.issuer=http://localhost:8180/realms/pem",
              "pem.security.jwks-uri=http://keycloak:8180/realms/pem/protocol/openid-connect/certs",
              "pem.security.public-paths=/fhir/**")
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class,
                  org.springframework.boot.autoconfigure.web.servlet
                      .DispatcherServletAutoConfiguration.class,
                  org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  OAuth2ResourceServerAutoConfiguration.class,
                  PemWebAutoConfiguration.class,
                  PemSecurityAutoConfiguration.class,
                  PemEventsAutoConfiguration.class,
                  PemClientAutoConfiguration.class));

  @Test
  @DisplayName(
      "[MAM-36 AC-4] every service gets the correlation-id filter and an injectable UTC clock")
  void webDefaults() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(FilterRegistrationBean.class);
          assertThat(ctx.getBean(Clock.class).getZone().getId()).isEqualTo("Z");
          assertThat(ctx.getBean(PemKitProperties.class).getSecurity().getPublicPaths())
              .containsExactly("/fhir/**");
        });
  }

  @Test
  @DisplayName(
      "[MAM-34 AC-2] a service is a resource server: JWT decoder, security chain and problem handler")
  void securityDefaults() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(JwtDecoder.class);
          assertThat(ctx).hasSingleBean(SecurityFilterChain.class);
          assertThat(ctx).hasSingleBean(ProblemHandler.class);
        });
  }

  @Test
  @DisplayName(
      "[MAM-29 AC-3] security can be switched off for the gateway (pem.security.enabled=false)")
  void securityOff() {
    runner
        .withPropertyValues("pem.security.enabled=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(ProblemHandler.class));
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-8] a service with a database and Kafka gets the outbox writer, relay and idempotency guard")
  void eventsWhenDatabaseAndKafkaExist() {
    runner
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(OutboxWriter.class);
              assertThat(ctx).hasSingleBean(OutboxRelay.class);
              assertThat(ctx).hasSingleBean(ProcessedEvents.class);
            });
  }

  @Test
  @DisplayName("[MAM-31 AC-8] without a database there is no outbox (the gateway)")
  void noEventsWithoutDatabase() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxWriter.class));
  }

  @Test
  @DisplayName("[MAM-31 AC-8] the outbox can be switched off (pem.outbox.enabled=false)")
  void eventsOff() {
    runner
        .withPropertyValues("pem.outbox.enabled=false")
        .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
        .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
        .withBean(PlatformTransactionManager.class, () -> mock(PlatformTransactionManager.class))
        .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxRelay.class));
  }

  @Test
  @DisplayName(
      "[MAM-26 AC-5] with a token URI a service can call other services with a SYSTEM token")
  void clientWhenTokenUriSet() {
    runner
        .withPropertyValues(
            "pem.client.token-uri=http://keycloak:8180/realms/pem/protocol/openid-connect/token",
            "pem.client.client-secret=x")
        .run(
            ctx -> {
              assertThat(ctx).hasSingleBean(ServiceTokenProvider.class);
              assertThat(ctx).hasSingleBean(ServiceClients.class);
              PemKitProperties p = ctx.getBean(PemKitProperties.class);
              assertThat(p.getClient().getClientId()).isEqualTo("pem-services");
              assertThat(p.getClient().getClientSecret()).isEqualTo("x");
              assertThat(p.getOutbox().isEnabled()).isTrue();
              assertThat(p.getSecurity().isEnabled()).isTrue();
            });
  }

  @Test
  @DisplayName("[MAM-26 AC-5] without a token URI there is no service client")
  void noClientWithoutTokenUri() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(ServiceTokenProvider.class));
  }
}
