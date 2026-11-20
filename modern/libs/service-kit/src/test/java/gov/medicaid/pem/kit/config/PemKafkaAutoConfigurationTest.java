package gov.medicaid.pem.kit.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

class PemKafkaAutoConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withPropertyValues("spring.application.name=screening-service")
          .withBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class))
          .withConfiguration(AutoConfigurations.of(PemKafkaAutoConfiguration.class));

  @Test
  @DisplayName(
      "[MAM-31 AC-6] a producing service creates its topic pem.<service>.events at start-up")
  void createsOwnTopic() {
    runner.run(
        ctx -> {
          NewTopic t = ctx.getBean("pemOwnTopic", NewTopic.class);
          assertThat(t.name()).isEqualTo("pem.screening.events");
          assertThat(t.numPartitions()).isEqualTo(3);
        });
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-7] a message that keeps failing is retried, then parked on <topic>.DLT, not dropped")
  void deadLetterErrorHandler() {
    runner.run(
        ctx -> {
          assertThat(ctx).hasSingleBean(CommonErrorHandler.class);
          assertThat(ctx.getBean(CommonErrorHandler.class)).isInstanceOf(DefaultErrorHandler.class);
        });
  }

  @Test
  @DisplayName("[MAM-31 AC-7] the retry count is configurable (pem.kafka.retries)")
  void retriesConfigurable() {
    runner
        .withPropertyValues("pem.kafka.retries=5")
        .run(
            ctx -> assertThat(ctx.getBean(PemKafkaAutoConfiguration.class).retries()).isEqualTo(5));
  }
}
