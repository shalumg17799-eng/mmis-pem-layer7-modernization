package gov.medicaid.pem.kit.events;

/** Topic naming: one topic per producer, {@code pem.<service>.events} (java-microservices 7). */
public final class Topics {

  private Topics() {}

  public static String forService(String serviceName) {
    return "pem." + serviceName.replaceFirst("-service$", "") + ".events";
  }
}
