package gov.medicaid.pem.directory.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.directory.application.ProviderDetails;
import gov.medicaid.pem.directory.application.ProviderFacts;
import gov.medicaid.pem.directory.application.ProviderProfile;
import gov.medicaid.pem.directory.application.ProviderProfiles;
import gov.medicaid.pem.kit.client.ServiceClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * provider-service GET /api/v1/providers/{npi} with the SYSTEM token (ADR-007, Q-071 default A).
 */
@Component
public class ProviderServiceClient implements ProviderFacts, ProviderProfiles {

  private final RestClient http;

  public ProviderServiceClient(
      ServiceClients clients, @Value("${pem.services.provider-url}") String providerUrl) {
    this.http = clients.create(providerUrl);
  }

  @Override
  public ProviderDetails details(String npi) {
    JsonNode p = http.get().uri("/api/v1/providers/{npi}", npi).retrieve().body(JsonNode.class);
    JsonNode i = p.path("individual");
    JsonNode o = p.path("organization");
    return new ProviderDetails(
        npi,
        text(p, "medicaidId"),
        text(p, "providerType"),
        text(p, "taxonomy"),
        text(i, "firstName"),
        text(i, "lastName"),
        text(o, "legalName"));
  }

  /** MAM-65: the names and licence that the FHIR resources carry. */
  @Override
  public ProviderProfile profile(String npi) {
    JsonNode p = http.get().uri("/api/v1/providers/{npi}", npi).retrieve().body(JsonNode.class);
    JsonNode i = p.path("individual");
    JsonNode o = p.path("organization");
    return new ProviderProfile(
        npi,
        text(i, "firstName"),
        text(i, "lastName"),
        text(o, "legalName"),
        text(i, "licenseNo"),
        text(i, "licenseState"));
  }

  private static String text(JsonNode n, String field) {
    JsonNode v = n.path(field);
    return v.isMissingNode() || v.isNull() ? null : v.asText();
  }
}
