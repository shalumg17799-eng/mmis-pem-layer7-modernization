package gov.medicaid.pem.risk.infrastructure;

import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.risk.application.EventOutbox;
import gov.medicaid.pem.risk.application.TierMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The outbox bridge (ADR-012) and /actuator/metrics/pem.risk.tiers.assigned (registered at start,
 * reads 0).
 */
@Configuration
public class RiskAdapters {

  @Bean
  EventOutbox eventOutbox(OutboxWriter writer) {
    return writer::enqueue;
  }

  @Bean
  TierMetrics tierMetrics(MeterRegistry registry) {
    Counter assigned =
        Counter.builder("pem.risk.tiers.assigned")
            .description("Risk tiers assigned after a passed screening")
            .register(registry);
    return assigned::increment;
  }
}
