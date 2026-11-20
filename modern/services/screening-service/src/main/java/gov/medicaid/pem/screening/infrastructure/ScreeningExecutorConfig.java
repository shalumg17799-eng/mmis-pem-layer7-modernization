package gov.medicaid.pem.screening.infrastructure;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The executor that runs requested screenings in the background (MAM-28), one at a time. */
@Configuration
public class ScreeningExecutorConfig {

  @Bean(destroyMethod = "shutdown")
  Executor screeningExecutor() {
    return Executors.newSingleThreadExecutor(r -> new Thread(r, "requested-screening"));
  }
}
