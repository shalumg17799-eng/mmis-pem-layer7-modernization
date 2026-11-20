package gov.medicaid.pem.kit.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class ServiceTokenProviderTest {

  @Test
  @DisplayName(
      "[MAM-26 AC-5] a SYSTEM token is fetched with client credentials once and reused until it nearly expires")
  void fetchesAndCaches() {
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://kc/token"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content().string(org.hamcrest.Matchers.containsString("grant_type=client_credentials")))
        .andRespond(
            withSuccess(
                "{\"access_token\":\"tok-1\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

    var clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
    var provider =
        new ServiceTokenProvider(
            builder.build(), "http://kc/token", "pem-services", "s3cr3t", clock);

    assertThat(provider.token()).isEqualTo("tok-1");
    assertThat(provider.token()).isEqualTo("tok-1");
    server.verify();
  }
}
