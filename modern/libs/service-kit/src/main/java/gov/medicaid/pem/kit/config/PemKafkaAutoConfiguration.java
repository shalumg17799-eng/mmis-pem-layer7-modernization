package gov.medicaid.pem.kit.config;

import gov.medicaid.pem.kit.events.CorrelationIdRecordInterceptor;
import gov.medicaid.pem.kit.events.KafkaHealthIndicator;
import gov.medicaid.pem.kit.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka conventions (ADR-003): a producing service creates its own topic {@code
 * pem.<service>.events} at start-up; a message a consumer cannot process is retried {@code
 * pem.kafka.retries} times, then parked on {@code <topic>.DLT} with its original envelope (logged
 * with its event id), and later messages are still processed.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class)
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnBean(KafkaTemplate.class)
public class PemKafkaAutoConfiguration {

  private final int retries;

  public PemKafkaAutoConfiguration(@Value("${pem.kafka.retries:2}") int retries) {
    this.retries = retries;
  }

  public int retries() {
    return retries;
  }

  @Bean
  @ConditionalOnProperty(prefix = "pem.kafka", name = "produces", matchIfMissing = true)
  public NewTopic pemOwnTopic(@Value("${spring.application.name}") String service) {
    return TopicBuilder.name(Topics.forService(service)).partitions(3).replicas(1).build();
  }

  /** MAM-36 AC-4: a consumed event's correlationId is in the MDC while it is handled. */
  @Bean
  @ConditionalOnMissingBean(RecordInterceptor.class)
  public RecordInterceptor<Object, Object> pemCorrelationIdRecordInterceptor() {
    return new CorrelationIdRecordInterceptor();
  }

  /** MAM-36 AC-1: the 'kafka' health component, where the service uses Kafka and has actuator. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthIndicator")
  static class KafkaHealthConfiguration {

    @Bean
    @ConditionalOnBean(KafkaAdmin.class)
    @ConditionalOnMissingBean(name = "kafkaHealthIndicator")
    public KafkaHealthIndicator kafkaHealthIndicator(
        KafkaAdmin admin, @Value("${pem.health.kafka-timeout:3s}") String timeout) {
      return new KafkaHealthIndicator(admin, DurationStyle.detectAndParse(timeout));
    }
  }

  @Bean
  @ConditionalOnMissingBean(CommonErrorHandler.class)
  public DefaultErrorHandler pemKafkaErrorHandler(KafkaTemplate<?, ?> template) {
    var recoverer =
        new DeadLetterPublishingRecoverer(
            template, (rec, ex) -> new TopicPartition(rec.topic() + ".DLT", -1));
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, retries));
  }
}
