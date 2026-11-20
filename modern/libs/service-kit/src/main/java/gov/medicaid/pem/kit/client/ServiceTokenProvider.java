package gov.medicaid.pem.kit.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Gets a SYSTEM token with client credentials (Keycloak client pem-services) and reuses it until
 * shortly before expiry.
 */
public class ServiceTokenProvider {

  private static final long REFRESH_MARGIN_SECONDS = 30;

  private final RestClient http;
  private final String tokenUri;
  private final String clientId;
  private final String clientSecret;
  private final Clock clock;
  private String token;
  private Instant expiresAt = Instant.MIN;

  public ServiceTokenProvider(
      RestClient http, String tokenUri, String clientId, String clientSecret, Clock clock) {
    this.http = http;
    this.tokenUri = tokenUri;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.clock = clock;
  }

  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") long expiresIn) {}

  public synchronized String token() {
    Instant now = clock.instant();
    if (token == null || now.isAfter(expiresAt.minusSeconds(REFRESH_MARGIN_SECONDS))) {
      var form = new LinkedMultiValueMap<String, String>();
      form.add("grant_type", "client_credentials");
      form.add("client_id", clientId);
      form.add("client_secret", clientSecret);
      TokenResponse r =
          http.post()
              .uri(tokenUri)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (r == null || r.accessToken() == null) {
        throw new IllegalStateException("no access token from " + tokenUri);
      }
      token = r.accessToken();
      expiresAt = now.plusSeconds(r.expiresIn());
    }
    return token;
  }
}
