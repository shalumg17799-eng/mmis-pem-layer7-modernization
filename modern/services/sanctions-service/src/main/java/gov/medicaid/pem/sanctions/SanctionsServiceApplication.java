package gov.medicaid.pem.sanctions;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * sanctions-service (port 8086, database sanctions_db). Replaces PRVSAN01. Its configuration file
 * is {@code sanctions-service.yml}, so the local run mode can host several services in one JVM
 * (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class SanctionsServiceApplication {

  public static final String NAME = "sanctions-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(SanctionsServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
