package gov.medicaid.pem.provider.infrastructure;

import gov.medicaid.pem.provider.application.StatusChangeMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * /actuator/metrics/pem.provider.terminations (the legacy 'PROVIDERS TERMINATED' count): registered
 * at start, so it reads 0 before the first termination.
 */
@Component
public class MicrometerStatusChangeMetrics implements StatusChangeMetrics {

  private final Counter terminations;

  public MicrometerStatusChangeMetrics(MeterRegistry registry) {
    this.terminations =
        Counter.builder("pem.provider.terminations")
            .description("Providers terminated by a sanction status change")
            .register(registry);
  }

  @Override
  public void terminated() {
    terminations.increment();
  }
}
