package gov.medicaid.pem.apigateway;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where each service listens ({@code pem.gateway.services.<name>}) and which origins may call the
 * gateway.
 */
@ConfigurationProperties("pem.gateway")
public record GatewayProperties(Map<String, String> services, List<String> corsOrigins) {

  public String url(String service) {
    String url = services.get(service);
    if (url == null) {
      throw new IllegalStateException(
          "no URL configured for " + service + " (pem.gateway.services." + service + ")");
    }
    return url;
  }
}
