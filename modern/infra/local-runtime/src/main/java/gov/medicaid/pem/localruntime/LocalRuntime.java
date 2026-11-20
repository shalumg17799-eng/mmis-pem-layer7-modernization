package gov.medicaid.pem.localruntime;

import gov.medicaid.pem.apigateway.ApiGatewayApplication;
import gov.medicaid.pem.directory.DirectoryServiceApplication;
import gov.medicaid.pem.enrollment.EnrollmentServiceApplication;
import gov.medicaid.pem.notification.NotificationServiceApplication;
import gov.medicaid.pem.provider.ProviderServiceApplication;
import gov.medicaid.pem.revalidation.RevalidationServiceApplication;
import gov.medicaid.pem.risk.RiskServiceApplication;
import gov.medicaid.pem.sanctions.SanctionsServiceApplication;
import gov.medicaid.pem.screening.ScreeningServiceApplication;
import gov.medicaid.pem.tmsis.TmsisServiceApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * Local run mode (ADR-011): starts the ten Spring Boot applications side by side in one JVM. Each
 * one is started from its own builder, so it keeps its own context, configuration file, port,
 * database, Kafka consumer group and topic; they talk to each other only over HTTP and Kafka. The
 * same applications run on their own in production.
 */
public final class LocalRuntime {

  private LocalRuntime() {}

  static Map<String, Supplier<SpringApplicationBuilder>> applications() {
    Map<String, Supplier<SpringApplicationBuilder>> apps = new LinkedHashMap<>();
    apps.put(ProviderServiceApplication.NAME, ProviderServiceApplication::builder);
    apps.put(EnrollmentServiceApplication.NAME, EnrollmentServiceApplication::builder);
    apps.put(ScreeningServiceApplication.NAME, ScreeningServiceApplication::builder);
    apps.put(RiskServiceApplication.NAME, RiskServiceApplication::builder);
    apps.put(RevalidationServiceApplication.NAME, RevalidationServiceApplication::builder);
    apps.put(SanctionsServiceApplication.NAME, SanctionsServiceApplication::builder);
    apps.put(NotificationServiceApplication.NAME, NotificationServiceApplication::builder);
    apps.put(DirectoryServiceApplication.NAME, DirectoryServiceApplication::builder);
    apps.put(TmsisServiceApplication.NAME, TmsisServiceApplication::builder);
    apps.put(ApiGatewayApplication.NAME, ApiGatewayApplication::builder);
    return apps;
  }

  public static void main(String[] args) {
    for (var app : applications().entrySet()) {
      long start = System.nanoTime();
      app.getValue().get().run(args);
      System.out.printf(
          "PEM local run mode: %s started in %d ms%n",
          app.getKey(), (System.nanoTime() - start) / 1_000_000);
    }
    System.out.println("PEM local run mode: all applications started");
  }
}
