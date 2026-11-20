package gov.medicaid.pem.notification;

import static gov.medicaid.pem.notification.NoticeFixtures.ENROLLMENT;
import static gov.medicaid.pem.notification.NoticeFixtures.PROVIDER;
import static gov.medicaid.pem.notification.NoticeFixtures.REVALIDATION;
import static gov.medicaid.pem.notification.NoticeFixtures.mailbox;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import gov.medicaid.pem.testsupport.PemContainers;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.DockerClientFactory;

/**
 * Code review of notification-service, 2026-11-20, MUST-FIX 1, through the real Kafka: the events
 * of APPROVED, DENIED, REJECTED and REVALIDATION_DUE notices that cannot be sent are recorded as
 * FAILED and acknowledged, not retried twice and parked on {@code <topic>.DLT} (D-024; MAM-59 AC-1,
 * AC-2, AC-4, AC-6; MAM-60 AC-5). A Kafka event can be consumed by the listener of any cached
 * application context of the test JVM, so the two faults here are ones that every context sees: the
 * Mailpit of the test JVM is paused, so it accepts no mail (it is the very case of the review, "a
 * J1 approval while Mailpit is down"), and a template that is renamed in the shared database. Every
 * wait has its own deadline and the class has a time limit, so a defect fails the test and never
 * hangs it. Every test has its own NPIs (10039007xx).
 */
@Timeout(value = 4, unit = TimeUnit.MINUTES)
class NoticeSendFailureEventsTest extends IntegrationTest {

  @Autowired JdbcTemplate jdbc;

  private final List<UUID> events = new ArrayList<>();
  private final List<String> npis = new ArrayList<>();
  private final List<String[]> partitions = new ArrayList<>();
  private LogCapture log;
  private boolean paused;
  private String renamed;

  @BeforeEach
  void start() {
    log = LogCapture.open();
  }

  @AfterEach
  void stop() {
    try {
      resumeMail();
    } finally {
      if (renamed != null) {
        jdbc.update("UPDATE notice_template SET code = ? WHERE code = ?", renamed, renamed + "_X");
      }
      // The partition must move past the event (handled, or parked on the DLT) before the rows are
      // cleaned, or a late retry would write them again. Best effort: a timeout here is the
      // failure of the test itself, which was already reported.
      for (String[] p : partitions) {
        try {
          events.add(NoticeFixtures.awaitBehind(jdbc, p[0], p[1]));
        } catch (AssertionError e) {
          // reported by the test; nothing more to clean up for this event
        }
      }
      log.close();
      NoticeFixtures.clean(jdbc, events, npis.toArray(String[]::new));
    }
  }

  /** Notice code, MAM-59 criterion, NPI, topic, event type, event data. */
  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(
            "APPROVED",
            "AC-1",
            "1003900701",
            PROVIDER,
            "ProviderStatusChanged",
            StatusNoticeEventsTest.statusChanged("1003900701", "P", "A")),
        Arguments.of(
            "DENIED",
            "AC-2",
            "1003900702",
            PROVIDER,
            "ProviderStatusChanged",
            StatusNoticeEventsTest.statusChanged("1003900702", "P", "D")),
        Arguments.of(
            "REJECTED",
            "AC-4",
            "1003900703",
            ENROLLMENT,
            "ApplicationRejected",
            StatusNoticeEventsTest.rejected("1003900703", "E104", "taxonomy")),
        Arguments.of(
            "REVALIDATION_DUE",
            "AC-6",
            "1003900704",
            REVALIDATION,
            "RevalidationDue",
            StatusNoticeEventsTest.revalidationDue("1003900704")));
  }

  // ---- helpers
  // -----------------------------------------------------------------------------------

  private void pauseMail() {
    DockerClientFactory.instance()
        .client()
        .pauseContainerCmd(TestMail.MAILPIT.getContainerId())
        .exec();
    paused = true;
  }

  private void resumeMail() {
    if (paused) {
      paused = false;
      DockerClientFactory.instance()
          .client()
          .unpauseContainerCmd(TestMail.MAILPIT.getContainerId())
          .exec();
    }
  }

  private UUID publish(String topic, String type, String npi, Map<String, Object> data) {
    UUID id = UUID.randomUUID();
    events.add(id);
    npis.add(npi);
    partitions.add(new String[] {topic, npi});
    NoticeFixtures.publish(topic, npi, NoticeFixtures.envelope(id, type, npi, data));
    return id;
  }

  /** Waits for the event to be handled; says so when the kit gave up on it instead. */
  private void awaitHandled(String topic, UUID id, Duration limit) {
    try {
      NoticeFixtures.await(
          () -> NoticeFixtures.processedCount(jdbc, id) > 0,
          limit,
          "event " + id + " to be handled");
    } catch (AssertionError e) {
      boolean parked = onDeadLetterTopic(topic, id);
      throw new AssertionError(
          e.getMessage()
              + (parked
                  ? "; it was retried and parked on "
                      + topic
                      + ".DLT, so the listener threw instead of recording a FAILED notice"
                  : "; it is not on " + topic + ".DLT"),
          e);
    }
  }

  /** Whether the dead letter topic of the topic holds a record with the event id. */
  private static boolean onDeadLetterTopic(String topic, UUID eventId) {
    String dlt = topic + ".DLT";
    String servers = PemContainers.KAFKA.getBootstrapServers();
    try (AdminClient admin =
        AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers))) {
      if (!admin.listTopics().names().get(15, TimeUnit.SECONDS).contains(dlt)) {
        return false;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while listing the topics", e);
    } catch (Exception e) {
      throw new IllegalStateException("cannot list the Kafka topics", e);
    }
    Map<String, Object> cfg =
        Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
            servers,
            ConsumerConfig.GROUP_ID_CONFIG,
            "probe-" + UUID.randomUUID(),
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
            "false",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
            StringDeserializer.class);
    try (var consumer = new KafkaConsumer<String, String>(cfg)) {
      List<TopicPartition> parts =
          consumer.partitionsFor(dlt, Duration.ofSeconds(15)).stream()
              .map(p -> new TopicPartition(dlt, p.partition()))
              .toList();
      consumer.assign(parts);
      consumer.seekToBeginning(parts);
      Map<TopicPartition, Long> end = consumer.endOffsets(parts, Duration.ofSeconds(15));
      long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
      while (System.nanoTime() < deadline
          && parts.stream().anyMatch(p -> consumer.position(p) < end.get(p))) {
        for (ConsumerRecord<String, String> r : consumer.poll(Duration.ofMillis(500))) {
          if (r.value() != null && r.value().contains(eventId.toString())) {
            return true;
          }
        }
      }
    }
    return false;
  }

  // ---- the mail server does not answer
  // --------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {4} event of the {0} notice, while the mail server does not answer, is"
              + " handled once: not retried and not parked on the dead letter topic, its id is in"
              + " processed_event")
  @MethodSource("cases")
  void mailServerDownIsNotParkedOnTheDeadLetterTopic(
      String code, String ac, String npi, String topic, String type, Map<String, Object> data) {
    UUID id;
    pauseMail();
    try {
      id = publish(topic, type, npi, data);
      awaitHandled(topic, id, Duration.ofSeconds(45));
    } finally {
      resumeMail();
    }

    assertThat(NoticeFixtures.processedCount(jdbc, id)).isEqualTo(1);
    assertThat(onDeadLetterTopic(topic, id)).as("event on %s.DLT", topic).isFalse();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {4} event of the {0} notice, while the mail server does not answer,"
              + " leaves one FAILED notice for the event, no email and no NoticeSent (D-024)")
  @MethodSource("cases")
  void mailServerDownLeavesAFailedNotice(
      String code, String ac, String npi, String topic, String type, Map<String, Object> data) {
    UUID id;
    pauseMail();
    try {
      id = publish(topic, type, npi, data);
      awaitHandled(topic, id, Duration.ofSeconds(45));
    } finally {
      resumeMail();
    }

    assertThat(
            jdbc.queryForList(
                "SELECT template_code || '/' || status || '/' || event_id FROM notice WHERE npi = ?",
                String.class,
                npi))
        .containsExactly(code + "/FAILED/" + id);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
    assertThat(NoticeFixtures.mailTo(mailbox(npi))).isEmpty();
  }

  // ---- a template that is not set up
  // ----------------------------------------------------------------

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {4} event of the {0} notice, with the {0} template missing from"
              + " notice_template, leaves a FAILED notice for the event, no email and no NoticeSent"
              + " (D-024)")
  @MethodSource("cases")
  void missingTemplateLeavesAFailedNotice(
      String code, String ac, String npi, String topic, String type, Map<String, Object> data) {
    jdbc.update("UPDATE notice_template SET code = ? WHERE code = ?", code + "_X", code);
    renamed = code;

    UUID id = publish(topic, type, npi, data);
    NoticeFixtures.awaitProcessed(jdbc, id);

    assertThat(
            jdbc.queryForList(
                "SELECT template_code || '/' || status || '/' || event_id FROM notice WHERE npi = ?",
                String.class,
                npi))
        .containsExactly(code + "/FAILED/" + id);
    assertThat(NoticeFixtures.noticeSent(jdbc, npi)).isZero();
    assertThat(NoticeFixtures.mailTo(mailbox(npi))).isEmpty();
  }

  @ParameterizedTest(
      name =
          "[MAM-59 {1}] the {4} event of the {0} notice, with the {0} template missing from"
              + " notice_template, is one WARN line: NOTICE {0} FOR NPI .. STATUS FAILED (event"
              + " ..), cause: NOTICE TEMPLATE NOT FOUND {0} (D-024)")
  @MethodSource("cases")
  void missingTemplateWarning(
      String code, String ac, String npi, String topic, String type, Map<String, Object> data) {
    jdbc.update("UPDATE notice_template SET code = ? WHERE code = ?", code + "_X", code);
    renamed = code;

    UUID id = publish(topic, type, npi, data);
    NoticeFixtures.awaitProcessed(jdbc, id);

    assertThat(log.messages(Level.WARN))
        .contains(
            "NOTICE "
                + code
                + " FOR NPI "
                + npi
                + " STATUS FAILED (event "
                + id
                + "), cause: NOTICE TEMPLATE NOT FOUND "
                + code);
  }
}
