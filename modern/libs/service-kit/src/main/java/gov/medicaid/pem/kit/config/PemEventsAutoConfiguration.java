package gov.medicaid.pem.kit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.kit.events.OutboxRelay;
import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.kit.events.ProcessedEvents;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional outbox and idempotent consumers for services that have a database and Kafka
 * (ADR-003).
 */
@AutoConfiguration(
    after = {
      KafkaAutoConfiguration.class,
      JdbcTemplateAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class,
      TransactionAutoConfiguration.class
    })
@ConditionalOnClass({KafkaTemplate.class, JdbcTemplate.class})
@ConditionalOnBean({JdbcTemplate.class, KafkaTemplate.class, PlatformTransactionManager.class})
@EnableScheduling
public class PemEventsAutoConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "pem.outbox", name = "enabled", matchIfMissing = true)
  public OutboxWriter outboxWriter(
      JdbcTemplate jdbc,
      ObjectMapper json,
      Clock clock,
      @Value("${spring.application.name}") String service) {
    return new OutboxWriter(jdbc, json, clock, service);
  }

  @Bean
  @ConditionalOnProperty(prefix = "pem.outbox", name = "enabled", matchIfMissing = true)
  public OutboxRelay outboxRelay(
      JdbcTemplate jdbc,
      KafkaTemplate<String, String> kafka,
      PlatformTransactionManager txm,
      @Value("${spring.application.name}") String service) {
    return new OutboxRelay(jdbc, kafka, new TransactionTemplate(txm), service);
  }

  @Bean
  public ProcessedEvents processedEvents(JdbcTemplate jdbc) {
    return new ProcessedEvents(jdbc);
  }
}
