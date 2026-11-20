package gov.medicaid.pem.revalidation;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * revalidation-service (port 8085, database revalidation_db). Replaces PRVREV01, PRVMTH01 step 010.
 * Its configuration file is {@code revalidation-service.yml}, so the local run mode can host
 * several services in one JVM (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class RevalidationServiceApplication {

  public static final String NAME = "revalidation-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(RevalidationServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
