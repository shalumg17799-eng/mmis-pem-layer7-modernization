package gov.medicaid.pem.tmsis.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.tmsis.application.ActiveProviders;
import gov.medicaid.pem.tmsis.application.ProviderRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads the ACTIVE providers from provider-service GET /api/v1/providers?status=A with the SYSTEM
 * token (MAM-71 AC-6, D-014, ADR-007); tmsis-service never reads the provider database.
 */
@Component
public class ProviderServiceClient implements ActiveProviders {

  private final RestClient http;

  public ProviderServiceClient(
      ServiceClients clients, @Value("${pem.services.provider-url}") String providerUrl) {
    this.http = clients.create(providerUrl);
  }

  @Override
  public Page page(int page, int size) {
    JsonNode body =
        http.get()
            .uri("/api/v1/providers?status=A&page={page}&size={size}", page, size)
            .retrieve()
            .body(JsonNode.class);
    if (body == null || !body.path("items").isArray()) {
      throw new IllegalStateException("provider-service answered without a provider page");
    }
    List<ProviderRow> rows = new ArrayList<>();
    for (JsonNode p : body.path("items")) {
      rows.add(
          new ProviderRow(
              p.path("npi").asText(),
              p.path("medicaidId").asText(),
              p.path("enrollStatus").asText(),
              date(p.path("effectiveDate"))));
    }
    return new Page(rows, body.path("total").asLong());
  }

  private static LocalDate date(JsonNode n) {
    return n.isMissingNode() || n.isNull() || n.asText().isBlank()
        ? null
        : LocalDate.parse(n.asText());
  }
}
