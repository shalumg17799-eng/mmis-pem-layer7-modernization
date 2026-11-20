package gov.medicaid.pem.sanctions.infrastructure;

import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.sanctions.application.ProviderStatusPort;
import java.time.Duration;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls provider-service POST /api/v1/providers/{npi}/status-changes with a SYSTEM token (D-013,
 * ADR-007). 404 means the provider does not exist; any other error, or no answer within {@code
 * pem.services.provider-timeout}, means the status could not be changed (D-024).
 */
@Component
public class ProviderServiceClient implements ProviderStatusPort {

  private static final Logger LOG = LoggerFactory.getLogger(ProviderServiceClient.class);

  private final RestClient http;

  public ProviderServiceClient(
      ServiceClients clients,
      @Value("${pem.services.provider-url}") String providerUrl,
      @Value("${pem.services.provider-timeout}") Duration timeout) {
    this.http = clients.create(providerUrl, timeout);
  }

  @Override
  public Outcome change(
      String npi, String action, String reason, LocalDate effectiveDate, UUID sanctionId) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("action", action);
    if (reason != null) {
      body.put("reason", reason);
    }
    body.put("effectiveDate", effectiveDate.toString());
    body.put("sanctionId", sanctionId.toString());
    try {
      http.post()
          .uri("/api/v1/providers/{npi}/status-changes", npi)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .toBodilessEntity();
      return Outcome.APPLIED;
    } catch (HttpClientErrorException.NotFound e) {
      return Outcome.NOT_FOUND;
    } catch (RestClientException | IllegalStateException e) {
      LOG.warn(
          "status change {} of NPI {} (sanction {}) not applied by provider-service: {}",
          action,
          npi,
          sanctionId,
          e.toString());
      return Outcome.FAILED;
    }
  }
}
