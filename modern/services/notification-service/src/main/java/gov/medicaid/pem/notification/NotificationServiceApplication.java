package gov.medicaid.pem.notification;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * notification-service (port 8087, database notification_db). Replaces the missing PRVNT* notice
 * programs. Its configuration file is {@code notification-service.yml}, so the local run mode can
 * host several services in one JVM (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class NotificationServiceApplication {

  public static final String NAME = "notification-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(NotificationServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
