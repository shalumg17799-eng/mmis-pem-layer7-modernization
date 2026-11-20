package gov.medicaid.pem.tmsis.infrastructure;

import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.tmsis.application.EventOutbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The outbox bridge of tmsis-service (topic pem.tmsis.events). */
@Configuration
public class TmsisAdapters {

  @Bean
  EventOutbox eventOutbox(OutboxWriter writer) {
    return writer::enqueue;
  }
}
