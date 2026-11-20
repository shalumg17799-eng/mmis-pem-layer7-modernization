package gov.medicaid.pem.kit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.json.JsonMapper;
import gov.medicaid.pem.kit.client.ServiceClients;
import gov.medicaid.pem.kit.client.ServiceTokenProvider;
import gov.medicaid.pem.kit.events.EventJson;
import gov.medicaid.pem.kit.events.OutboxRelay;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.kit.web.CorrelationId;
import gov.medicaid.pem.kit.web.ProblemHandler;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClient;

class HelpersTest {

  @Test
  @DisplayName(
      "[MAM-31 AC-5] event processing runs with the event's correlation id, then restores the previous one")
  void correlationIdForEventWork() {
    MDC.put(CorrelationId.MDC_KEY, "outer");
    String seen = CorrelationId.with("from-event", () -> MDC.get(CorrelationId.MDC_KEY));
    assertThat(seen).isEqualTo("from-event");
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("outer");
    MDC.clear();
    CorrelationId.with("x", () -> 1);
    assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-7] a message that is not an envelope, or data of the wrong shape, is rejected clearly")
  void invalidEventsAreRejected() {
    var json = JsonMapper.builder().findAndAddModules().build();
    assertThatThrownBy(() -> EventJson.parse(json, "not json"))
        .isInstanceOf(IllegalArgumentException.class);
    var e =
        EventJson.parse(
            json, "{\"id\":\"00000000-0000-0000-0000-000000000001\",\"data\":{\"n\":[1]}}");
    assertThatThrownBy(() -> EventJson.data(json, e, Num.class))
        .isInstanceOf(IllegalArgumentException.class);
  }

  record Num(int n) {}

  @Test
  @DisplayName(
      "[MAM-26 AC-5] calls to another service carry the SYSTEM token and the correlation id")
  void serviceClientsAddTokenAndCorrelation() {
    ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);
    when(tokens.token()).thenReturn("sys-token");
    RestClient.Builder builder = RestClient.builder();
    MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo("http://provider:8081/api/v1/providers/1003000111"))
        .andExpect(header("Authorization", "Bearer sys-token"))
        .andExpect(header(CorrelationId.HEADER, "corr-9"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    MDC.put(CorrelationId.MDC_KEY, "corr-9");
    try {
      new ServiceClients(tokens, () -> builder)
          .create("http://provider:8081")
          .get()
          .uri("/api/v1/providers/1003000111")
          .retrieve()
          .toBodilessEntity();
    } finally {
      MDC.clear();
    }
    server.verify();
  }

  @Test
  @DisplayName("[MAM-31 AC-3] the scheduled poll publishes nothing when the outbox is empty")
  @SuppressWarnings("unchecked")
  void pollWithEmptyOutbox() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    TransactionTemplate tx = mock(TransactionTemplate.class);
    when(tx.execute(any())).thenReturn(0);
    new OutboxRelay(mock(JdbcTemplate.class), kafka, tx, "risk-service").poll();
    verify(kafka, never()).send(any(String.class), any(String.class), any(String.class));
  }

  @Test
  @DisplayName("[MAM-29 AC-6] validation and conflict errors keep their status and legacy code")
  void otherProblemShapes() {
    var h = new ProblemHandler();
    assertThat(
            h.handleApi(ApiException.conflict("E103", "Duplicate NPI")).getBody().getProperties())
        .containsEntry("code", "E103");
    assertThat(h.handleApi(ApiException.badRequest("bad")).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    var ex = new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, null, "x", List.of());
    assertThat(ex.errors()).isEmpty();
  }
}
