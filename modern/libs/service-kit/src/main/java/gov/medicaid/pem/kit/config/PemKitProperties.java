package gov.medicaid.pem.kit.config;

import gov.medicaid.pem.kit.client.ServiceClients;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings shared by every service ({@code pem.*}). Values come from environment variables, never
 * from code.
 */
@ConfigurationProperties("pem")
public class PemKitProperties {

  private final Security security = new Security();
  private final Outbox outbox = new Outbox();
  private final Client client = new Client();

  public Security getSecurity() {
    return security;
  }

  public Outbox getOutbox() {
    return outbox;
  }

  public Client getClient() {
    return client;
  }

  public static class Security {
    private boolean enabled = true;
    private String issuer;
    private String jwksUri;
    private List<String> publicPaths = new ArrayList<>();

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getIssuer() {
      return issuer;
    }

    public void setIssuer(String issuer) {
      this.issuer = issuer;
    }

    public String getJwksUri() {
      return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
      this.jwksUri = jwksUri;
    }

    public List<String> getPublicPaths() {
      return publicPaths;
    }

    public void setPublicPaths(List<String> publicPaths) {
      this.publicPaths = publicPaths;
    }
  }

  public static class Outbox {
    private boolean enabled = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  public static class Client {
    private String tokenUri;
    private String clientId = "pem-services";
    private String clientSecret;
    private Duration connectTimeout = ServiceClients.DEFAULT_CONNECT_TIMEOUT;
    private Duration readTimeout = ServiceClients.DEFAULT_READ_TIMEOUT;

    public String getTokenUri() {
      return tokenUri;
    }

    public void setTokenUri(String tokenUri) {
      this.tokenUri = tokenUri;
    }

    public String getClientId() {
      return clientId;
    }

    public void setClientId(String clientId) {
      this.clientId = clientId;
    }

    public String getClientSecret() {
      return clientSecret;
    }

    public void setClientSecret(String clientSecret) {
      this.clientSecret = clientSecret;
    }

    /**
     * Limit for connecting to the token endpoint and to other services ({@code
     * pem.client.connect-timeout}).
     */
    public Duration getConnectTimeout() {
      return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
      this.connectTimeout = connectTimeout;
    }

    /**
     * Limit for the answer of the token endpoint and of other services ({@code
     * pem.client.read-timeout}).
     */
    public Duration getReadTimeout() {
      return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
      this.readTimeout = readTimeout;
    }
  }
}
