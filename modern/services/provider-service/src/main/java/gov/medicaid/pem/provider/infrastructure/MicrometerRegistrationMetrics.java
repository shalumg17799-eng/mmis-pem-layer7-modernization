package gov.medicaid.pem.provider.infrastructure;

import gov.medicaid.pem.provider.application.RegistrationMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * /actuator/metrics/pem.provider.registrations.skipped: registered at start, so it reads 0 before
 * the first skip.
 */
@Component
public class MicrometerRegistrationMetrics implements RegistrationMetrics {

  private final Counter skipped;

  public MicrometerRegistrationMetrics(MeterRegistry registry) {
    this.skipped =
        Counter.builder("pem.provider.registrations.skipped")
            .description("Validated applications skipped because the NPI is already registered")
            .register(registry);
  }

  @Override
  public void duplicateSkipped() {
    skipped.increment();
  }
}
