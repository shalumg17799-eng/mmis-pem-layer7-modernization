package gov.medicaid.pem.kit.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxRelayTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);

  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final TransactionTemplate tx = mock(TransactionTemplate.class);
  private final OutboxRelay relay = new OutboxRelay(jdbc, kafka, tx, "provider-service");

  private final UUID id = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

  @SuppressWarnings("unchecked")
  private void givenOnePendingRow() {
    when(tx.execute(any()))
        .thenAnswer(
            inv ->
                ((org.springframework.transaction.support.TransactionCallback<Object>)
                        inv.getArgument(0))
                    .doInTransaction(new SimpleTransactionStatus()));
    when(jdbc.query(eq(OutboxRelay.SELECT_SQL), any(RowMapper.class), anyInt()))
        .thenReturn(List.of(new OutboxRelay.Row(id, "1003000111", "{\"type\":\"X\"}")));
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-1] pending outbox rows go to pem.<service>.events keyed by NPI, then are marked published")
  void publishesAndMarks() {
    givenOnePendingRow();
    when(kafka.send("pem.provider.events", "1003000111", "{\"type\":\"X\"}"))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    int sent = relay.publishPending();

    assertThat(sent).isEqualTo(1);
    verify(kafka).send("pem.provider.events", "1003000111", "{\"type\":\"X\"}");
    verify(jdbc).update(OutboxRelay.MARK_SQL, id);
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-3] a failed send leaves the row unpublished so it is retried (at-least-once)")
  void failedSendIsRetriedLater() {
    givenOnePendingRow();
    when(kafka.send("pem.provider.events", "1003000111", "{\"type\":\"X\"}"))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

    int sent = relay.publishPending();

    assertThat(sent).isZero();
    verify(jdbc, never()).update(OutboxRelay.MARK_SQL, id);
  }
}
