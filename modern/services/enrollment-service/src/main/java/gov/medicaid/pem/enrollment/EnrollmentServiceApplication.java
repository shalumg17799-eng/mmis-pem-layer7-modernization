package gov.medicaid.pem.enrollment;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * enrollment-service (port 8082, database enrollment_db). Replaces PRVENR01, PRVDAY01 step 010. Its
 * configuration file is {@code enrollment-service.yml}, so the local run mode can host several
 * services in one JVM (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class EnrollmentServiceApplication {

  public static final String NAME = "enrollment-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(EnrollmentServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
