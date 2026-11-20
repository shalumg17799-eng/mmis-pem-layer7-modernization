package gov.medicaid.pem.tmsis;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * tmsis-service (port 8089, database tmsis_db). Replaces PRVTMS01, PRVMTH01 steps 020-030. Its
 * configuration file is {@code tmsis-service.yml}, so the local run mode can host several services
 * in one JVM (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class TmsisServiceApplication {

  public static final String NAME = "tmsis-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(TmsisServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
