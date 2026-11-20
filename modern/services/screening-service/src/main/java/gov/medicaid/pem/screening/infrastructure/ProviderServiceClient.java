package gov.medicaid.pem.screening.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.screening.application.ProviderFacts;
import gov.medicaid.pem.screening.application.ProviderFactsSource;
import gov.medicaid.pem.screening.application.ProviderListing;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Reads the provider from provider-service GET /api/v1/providers/{npi} with a SYSTEM token (Q-040
 * A). The name is composed as in D-028: an individual by first + last name, an organisation by its
 * legal name. A 5xx answer or no answer is reported as {@link ProviderFactsSource.Unavailable}
 * (MAM-20 AC-5); a 4xx answer (such as 404 for an unknown NPI) is passed on as it is.
 */
@Component
public class ProviderServiceClient implements ProviderFactsSource, ProviderListing {

  private final RestClient http;

  public ProviderServiceClient(
      ServiceClients clients, @Value("${pem.services.provider-url}") String providerUrl) {
    this.http = clients.create(providerUrl);
  }

  @Override
  public ProviderFacts facts(String npi) {
    JsonNode p;
    try {
      p = http.get().uri("/api/v1/providers/{npi}", npi).retrieve().body(JsonNode.class);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new ProviderFactsSource.Unavailable(npi, e);
    }
    if (p == null) {
      throw new ProviderFactsSource.Unavailable(npi, new IllegalStateException("empty answer"));
    }
    JsonNode i = p.path("individual");
    JsonNode o = p.path("organization");
    String name =
        i.isMissingNode()
            ? text(o, "legalName")
            : (nz(text(i, "firstName")) + " " + nz(text(i, "lastName"))).trim();
    return new ProviderFacts(
        npi,
        text(p, "providerType"),
        text(p, "taxonomy"),
        text(i, "licenseNo"),
        text(i, "licenseState"),
        name,
        text(o, "ownerNpi"));
  }

  /** MAM-32: one page of every provider (GET /api/v1/providers, unfiltered, ascending NPI). */
  @Override
  public Page page(int page, int size) {
    JsonNode body;
    try {
      body =
          http.get()
              .uri("/api/v1/providers?page={page}&size={size}", page, size)
              .retrieve()
              .body(JsonNode.class);
    } catch (HttpServerErrorException | ResourceAccessException e) {
      throw new ProviderFactsSource.Unavailable("(provider list page " + page + ")", e);
    }
    if (body == null || !body.path("items").isArray()) {
      throw new IllegalStateException("provider-service answered without a provider page");
    }
    List<ProviderListing.Row> rows = new ArrayList<>();
    for (JsonNode p : body.path("items")) {
      rows.add(new ProviderListing.Row(p.path("npi").asText(), p.path("screenFlag").asText()));
    }
    return new Page(rows, body.path("total").asLong());
  }

  static String text(JsonNode n, String field) {
    JsonNode v = n.path(field);
    return v.isMissingNode() || v.isNull() ? null : v.asText();
  }

  private static String nz(String s) {
    return s == null ? "" : s;
  }
}
