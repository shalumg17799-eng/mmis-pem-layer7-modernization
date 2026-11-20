package gov.medicaid.pem.risk;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * risk-service (port 8084, database risk_db). Replaces PRVRSK01. Its configuration file is {@code
 * risk-service.yml}, so the local run mode can host several services in one JVM (ADR-011); it also
 * runs on its own.
 */
@SpringBootApplication
public class RiskServiceApplication {

  public static final String NAME = "risk-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(RiskServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
