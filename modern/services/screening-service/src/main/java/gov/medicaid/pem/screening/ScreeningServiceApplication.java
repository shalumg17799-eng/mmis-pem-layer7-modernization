package gov.medicaid.pem.screening;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * screening-service (port 8083, database screening_db). Replaces PRVSCR01. Its configuration file
 * is {@code screening-service.yml}, so the local run mode can host several services in one JVM
 * (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class ScreeningServiceApplication {

  public static final String NAME = "screening-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(ScreeningServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
