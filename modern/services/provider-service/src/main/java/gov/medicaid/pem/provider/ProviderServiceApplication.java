package gov.medicaid.pem.provider;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * provider-service (port 8081, database provider_db): the single provider golden record (ADR-002).
 * Its configuration file is {@code provider-service.yml}, so several services can share one JVM in
 * the local runtime (ADR-011).
 */
@SpringBootApplication
public class ProviderServiceApplication {

  public static final String NAME = "provider-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(ProviderServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
