package gov.medicaid.pem.revalidation.infrastructure;

import gov.medicaid.pem.kit.events.OutboxWriter;
import gov.medicaid.pem.revalidation.application.EventOutbox;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The outbox bridge (sweeps run as a Spring Batch job: SweepJobConfig, SpringBatchSweepLauncher). */
@Configuration
public class RevalidationAdapters {

  @Bean
  EventOutbox eventOutbox(OutboxWriter writer) {
    return writer::enqueue;
  }
}
