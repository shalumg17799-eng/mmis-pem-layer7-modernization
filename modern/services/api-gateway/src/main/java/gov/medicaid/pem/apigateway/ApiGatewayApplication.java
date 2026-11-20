package gov.medicaid.pem.apigateway;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * api-gateway (port 8080): routes /api/v1/** and /fhir/** to the services (Spring Cloud Gateway,
 * Web MVC).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class ApiGatewayApplication {

  public static final String NAME = "api-gateway";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(ApiGatewayApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
