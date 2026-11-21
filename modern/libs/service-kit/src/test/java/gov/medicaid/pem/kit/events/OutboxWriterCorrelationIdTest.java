package gov.medicaid.pem.kit.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.kit.web.CorrelationId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.yaml.snakeyaml.Yaml;

/**
 * Q-142 (option A, applied as the default): the event contract (events.yaml, Envelope) says the
 * correlationId of an event is a uuid, but the request filter accepts any SAFE id (letters, digits,
 * . _ : -, 1 to 64 characters), and {@link OutboxWriter#enqueue} copied the id of the current
 * request or consumed event into every event. A caller that sent {@code X-Correlation-Id: trace-42}
 * got events whose correlationId was "trace-42".
 *
 * <p>The decision these tests pin: an event carries the current correlation id only when that id is
 * a uuid, otherwise it gets a new uuid. A uuid is what {@code java.util.UUID.fromString} accepts
 * (the same test as KitEventOutbox.uuidOf of enrollment-service, so every service behaves the same)
 * and it is stored in its canonical lower-case text ({@code UUID.toString()}): the contract says
 * uuid, so "5A1E..." is stored as "5a1e..." and "1-1-1-1-1" as
 * "00000001-0001-0001-0001-000000000001". Nothing else changes: the id in the log context (MDC)
 * stays exactly as the request filter or the record interceptor put it, and {@code
 * CorrelationId.currentOrNew} and {@code CorrelationId.sanitize} are not part of this item.
 *
 * <p>The story is MAM-31 (the event backbone): AC-1 says the envelope correlationId is a uuid and
 * AC-5 says the correlation id travels with the event, so the tests carry those tags with Q-142.
 */
class OutboxWriterCorrelationIdTest {

  private static final String NPI = "1003000111";
  private static final String A_UUID = "5a1e7b20-3c4d-4e5f-9a6b-000000000456";
  private static final String CANONICAL_UUID =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);
  private final OutboxWriter writer = new OutboxWriter(jdbc, json, clock, "provider-service");

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  // ---- samples --------------------------------------------------------------------------

  static Stream<Arguments> uuids() {
    return Stream.of(
        Arguments.of("a version 4 uuid", A_UUID),
        Arguments.of("the nil uuid", "00000000-0000-0000-0000-000000000000"),
        Arguments.of("a version 7 uuid", "01890a5d-ac96-774b-bcce-b302099a8057"),
        Arguments.of("a uuid with every hex letter", "abcdef01-abcd-abcd-abcd-abcdef012345"));
  }

  static Stream<Arguments> uuidsInUpperOrMixedCase() {
    return Stream.of(
        Arguments.of(
            "an upper-case uuid",
            "5A1E7B20-3C4D-4E5F-9A6B-00000000ABCD",
            "5a1e7b20-3c4d-4e5f-9a6b-00000000abcd"),
        Arguments.of(
            "a mixed-case uuid",
            "5a1E7b20-3C4d-4E5f-9a6B-0000000aBcDe",
            "5a1e7b20-3c4d-4e5f-9a6b-0000000abcde"));
  }

  /** What java.util.UUID.fromString accepts although the groups are shorter than in a uuid. */
  static Stream<Arguments> nonCanonicalUuids() {
    return Stream.of(
        Arguments.of("1-1-1-1-1", "1-1-1-1-1", "00000001-0001-0001-0001-000000000001"),
        Arguments.of("A-B-C-D-E", "A-B-C-D-E", "0000000a-000b-000c-000d-00000000000e"),
        Arguments.of(
            "a uuid whose last group is short",
            "5a1e7b20-3c4d-4e5f-9a6b-45",
            "5a1e7b20-3c4d-4e5f-9a6b-000000000045"));
  }

  /** Ids the request filter keeps (CorrelationId.sanitize) that are no uuid at all. */
  static Stream<Arguments> safeIdsThatAreNoUuid() {
    return Stream.of(
        Arguments.of("trace-42", "trace-42"),
        Arguments.of("an id with colons", "svc:req:42"),
        Arguments.of("an id with dots and underscores", "trace_42.v2"),
        Arguments.of("a 64-character id", "0123456789abcdef".repeat(4)),
        Arguments.of("a uuid without dashes", "5a1e7b203c4d4e5f9a6b000000000456"),
        Arguments.of("a uuid with a suffix", A_UUID + "-1"),
        Arguments.of(
            "a uuid shape with a character that is no hex digit",
            "zzzzzzzz-3c4d-4e5f-9a6b-000000000456"));
  }

  /** Values the filter never lets into the MDC, but any code can put there. */
  static Stream<Arguments> idsThatAreNotEvenSafe() {
    return Stream.of(
        Arguments.of("an empty id", ""),
        Arguments.of("a blank id", " "),
        Arguments.of("an id with a line break", "bad\r\nvalue"),
        Arguments.of("a uuid followed by a line break", A_UUID + "\n"),
        Arguments.of("a uuid followed by a space", A_UUID + " "),
        Arguments.of("an id of 65 characters", "x".repeat(65)));
  }

  static Stream<Arguments> everyKindOfCurrentId() {
    return Stream.of(
        Arguments.of("a uuid", A_UUID),
        Arguments.of("an upper-case uuid", "5A1E7B20-3C4D-4E5F-9A6B-00000000ABCD"),
        Arguments.of("a non-canonical uuid", "1-1-1-1-1"),
        Arguments.of("trace-42", "trace-42"),
        Arguments.of("an id with colons", "svc:req:42"),
        Arguments.of("a 64-character id", "0123456789abcdef".repeat(4)),
        Arguments.of("no id", null));
  }

  // ---- helpers --------------------------------------------------------------------------

  private void enqueue() {
    writer.enqueue("ProviderRegistered", NPI, Map.of("npi", NPI));
  }

  private void enqueueUnder(String currentId) {
    if (currentId != null) {
      MDC.put(CorrelationId.MDC_KEY, currentId);
    }
    enqueue();
  }

  /** The envelopes of the outbox rows written so far (the payload column), oldest first. */
  private List<JsonNode> storedEnvelopes(int count) throws Exception {
    ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
    verify(jdbc, times(count))
        .update(
            eq(OutboxWriter.INSERT_SQL),
            any(UUID.class),
            anyString(),
            anyString(),
            payload.capture(),
            any());
    List<JsonNode> envelopes = new ArrayList<>();
    for (String text : payload.getAllValues()) {
      envelopes.add(json.readTree(text));
    }
    return envelopes;
  }

  private JsonNode storedEnvelope() throws Exception {
    return storedEnvelopes(1).get(0);
  }

  private String storedCorrelationId() throws Exception {
    return storedEnvelope().path("correlationId").asText();
  }

  private List<String> storedCorrelationIds(int count) throws Exception {
    return storedEnvelopes(count).stream().map(e -> e.path("correlationId").asText()).toList();
  }

  // ---- the premise: these ids are safe for the filter and are no uuid -------------------

  @ParameterizedTest(name = "[MAM-31 AC-1] Q-142: the request filter keeps {0} as it is")
  @MethodSource("safeIdsThatAreNoUuid")
  void theRequestFilterKeepsTheSampleIds(String what, String id) {
    assertThat(CorrelationId.sanitize(id)).isEqualTo(id);
  }

  @ParameterizedTest(name = "[MAM-31 AC-1] Q-142: {0} is no uuid for java.util.UUID either")
  @MethodSource("safeIdsThatAreNoUuid")
  void theSampleIdsAreNoUuid(String what, String id) {
    assertThatThrownBy(() -> UUID.fromString(id)).isInstanceOf(IllegalArgumentException.class);
  }

  // ---- a uuid travels with the event -----------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-31 AC-5] Q-142: {0} as the current id is stored in the envelope unchanged")
  @MethodSource("uuids")
  void aUuidTravelsUnchanged(String what, String uuid) throws Exception {
    enqueueUnder(uuid);

    assertThat(storedCorrelationId()).isEqualTo(uuid);
  }

  @ParameterizedTest(
      name = "[MAM-31 AC-1] Q-142: {0} as the current id is stored in lower case (canonical uuid)")
  @MethodSource("uuidsInUpperOrMixedCase")
  void aUuidIsStoredInLowerCase(String what, String current, String stored) throws Exception {
    enqueueUnder(current);

    assertThat(storedCorrelationId()).isEqualTo(stored);
  }

  @ParameterizedTest(
      name =
          "[MAM-31 AC-1] Q-142: {0} as the current id is stored as the canonical uuid it stands for")
  @MethodSource("nonCanonicalUuids")
  void aNonCanonicalUuidIsStoredInCanonicalForm(String what, String current, String stored)
      throws Exception {
    enqueueUnder(current);

    assertThat(storedCorrelationId()).isEqualTo(stored);
  }

  // ---- an id that is no uuid is not put into the event -----------------------------------

  @ParameterizedTest(
      name = "[MAM-31 AC-1] Q-142: {0} as the current id: the event carries a new uuid instead")
  @MethodSource("safeIdsThatAreNoUuid")
  void anIdThatIsNoUuidIsReplacedByANewUuid(String what, String id) throws Exception {
    enqueueUnder(id);

    assertThat(storedCorrelationId()).matches(CANONICAL_UUID).isNotEqualTo(id);
  }

  @ParameterizedTest(
      name = "[MAM-31 AC-1] Q-142: {0} as the current id: the event carries a new uuid instead")
  @MethodSource("idsThatAreNotEvenSafe")
  void anUnsafeIdIsReplacedByANewUuid(String what, String id) throws Exception {
    enqueueUnder(id);

    assertThat(storedCorrelationId()).matches(CANONICAL_UUID).isNotEqualTo(id);
  }

  @ParameterizedTest(
      name = "[MAM-31 AC-1] Q-142: two requests with the id {0} get two different uuids")
  @MethodSource("someSafeIdsThatAreNoUuid")
  void twoRequestsWithTheSameIdGetDifferentUuids(String what, String id) throws Exception {
    for (int request = 0; request < 2; request++) {
      enqueueUnder(id);
      MDC.remove(CorrelationId.MDC_KEY);
    }

    assertThat(storedCorrelationIds(2))
        .doesNotHaveDuplicates()
        .allMatch(stored -> stored.matches(CANONICAL_UUID));
  }

  static Stream<Arguments> someSafeIdsThatAreNoUuid() {
    return safeIdsThatAreNoUuid().limit(4);
  }

  // ---- no current id ---------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-31 AC-5] Q-142: without a current id the event carries a canonical uuid")
  void withoutACurrentIdTheEventGetsAUuid() throws Exception {
    enqueue();

    assertThat(storedCorrelationId()).matches(CANONICAL_UUID);
  }

  @Test
  @DisplayName("[MAM-31 AC-5] Q-142: without a current id two events get two different uuids")
  void withoutACurrentIdTwoEventsGetDifferentUuids() throws Exception {
    enqueue();
    enqueue();

    assertThat(storedCorrelationIds(2))
        .doesNotHaveDuplicates()
        .allMatch(stored -> stored.matches(CANONICAL_UUID));
  }

  // ---- the id in the log context is not touched ------------------------------------------

  @ParameterizedTest(
      name = "[MAM-31 AC-5] Q-142: with {0} as the current id the log context is unchanged after")
  @MethodSource("everyKindOfCurrentId")
  void theLogContextIsNotChanged(String what, String current) {
    enqueueUnder(current);

    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo(current);
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-5] Q-142: when the outbox row cannot be written the log context is unchanged too")
  void theLogContextIsNotChangedWhenTheInsertFails() {
    doThrow(new IllegalStateException("database is down"))
        .when(jdbc)
        .update(eq(OutboxWriter.INSERT_SQL), any(), any(), any(), any(), any());
    MDC.put(CorrelationId.MDC_KEY, "trace-42");

    assertThatThrownBy(this::enqueue).isInstanceOf(IllegalStateException.class);

    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("trace-42");
  }

  // ---- an event that is consumed, then a new event is written ----------------------------

  /** What the Kafka listener container does before it hands the record to the handler. */
  @SuppressWarnings("unchecked")
  private void startHandlingAnEventWithTheCorrelationId(String correlationId) {
    String envelope =
        json.createObjectNode()
            .put("id", UUID.randomUUID().toString())
            .put("type", "ProviderRegistered")
            .put("correlationId", correlationId)
            .toString();
    new CorrelationIdRecordInterceptor()
        .intercept(
            new ConsumerRecord<>("pem.provider.events", 0, 0L, NPI, envelope),
            mock(org.apache.kafka.clients.consumer.Consumer.class));
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-5] Q-142: the event written while a consumed event with a uuid is handled carries that uuid")
  void aConsumedEventWithAUuidGivesTheNextEventTheSameUuid() throws Exception {
    startHandlingAnEventWithTheCorrelationId(A_UUID);

    enqueue();

    assertThat(storedCorrelationId()).isEqualTo(A_UUID);
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-1] Q-142: the event written while a consumed event with the id trace-42 is handled carries a new uuid")
  void aConsumedEventWithANonUuidIdGivesTheNextEventANewUuid() throws Exception {
    startHandlingAnEventWithTheCorrelationId("trace-42");

    enqueue();

    assertThat(storedCorrelationId()).matches(CANONICAL_UUID).isNotEqualTo("trace-42");
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-5] Q-142: while a consumed event with the id trace-42 is handled its own id stays in the log context")
  void aConsumedEventKeepsItsOwnIdInTheLogContext() {
    startHandlingAnEventWithTheCorrelationId("trace-42");

    enqueue();

    assertThat(MDC.get(CorrelationId.MDC_KEY)).isEqualTo("trace-42");
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-1] Q-142: event work run with the id trace-42 (CorrelationId.with) writes an event with a new uuid")
  void eventWorkWithANonUuidIdWritesANewUuid() throws Exception {
    CorrelationId.with("trace-42", () -> writer.enqueue("ProviderRegistered", NPI, Map.of()));

    assertThat(storedCorrelationId()).matches(CANONICAL_UUID).isNotEqualTo("trace-42");
  }

  @Test
  @DisplayName(
      "[MAM-31 AC-5] Q-142: event work run with a uuid (CorrelationId.with) writes an event with that uuid")
  void eventWorkWithAUuidWritesThatUuid() throws Exception {
    CorrelationId.with(A_UUID, () -> writer.enqueue("ProviderRegistered", NPI, Map.of()));

    assertThat(storedCorrelationId()).isEqualTo(A_UUID);
  }

  // ---- the stored payload conforms to the Envelope of events.yaml ------------------------

  @ParameterizedTest(
      name =
          "[MAM-31 AC-1] Q-142: with {0} as the current id the stored payload is a valid Envelope of events.yaml")
  @MethodSource("everyKindOfCurrentId")
  void theStoredEnvelopeConformsToTheContract(String what, String current) throws Exception {
    enqueueUnder(current);

    assertThat(EnvelopeContract.violations(storedEnvelope())).isEmpty();
  }

  // ---- the contract check itself must be able to fail ------------------------------------

  private ObjectNode validEnvelope() {
    ObjectNode envelope = json.createObjectNode();
    envelope.put("id", "7c9e6679-7425-40de-944b-e07fc1f90ae7");
    envelope.put("type", "ProviderRegistered");
    envelope.put("source", "provider-service");
    envelope.put("time", "2026-01-15T10:00:00Z");
    envelope.put("subject", NPI);
    envelope.put("correlationId", A_UUID);
    envelope.putObject("data").put("npi", NPI);
    return envelope;
  }

  static Stream<Arguments> brokenEnvelopes() {
    return Stream.of(
        Arguments.of(
            "a correlationId that is no uuid",
            (Consumer<ObjectNode>) e -> e.put("correlationId", "trace-42"),
            "$.correlationId"),
        Arguments.of(
            "a correlationId in a non-canonical uuid form",
            (Consumer<ObjectNode>) e -> e.put("correlationId", "1-1-1-1-1"),
            "$.correlationId"),
        Arguments.of(
            "an id that is no uuid", (Consumer<ObjectNode>) e -> e.put("id", "not-a-uuid"), "$.id"),
        Arguments.of(
            "a time that is no date-time",
            (Consumer<ObjectNode>) e -> e.put("time", "yesterday"),
            "$.time"),
        Arguments.of(
            "a subject that is neither an NPI nor a uuid",
            (Consumer<ObjectNode>) e -> e.put("subject", "12345"),
            "$.subject"),
        Arguments.of(
            "a missing member",
            (Consumer<ObjectNode>) e -> e.remove("correlationId"),
            "$.correlationId is required"),
        Arguments.of(
            "a member outside the contract",
            (Consumer<ObjectNode>) e -> e.put("traceId", "x"),
            "$.traceId is not in the contract"),
        Arguments.of(
            "data that is no object", (Consumer<ObjectNode>) e -> e.put("data", "text"), "$.data"));
  }

  @Test
  @DisplayName("[MAM-31 AC-1] Q-142: the contract check accepts a valid envelope")
  void theContractCheckAcceptsAValidEnvelope() {
    assertThat(EnvelopeContract.violations(validEnvelope())).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-31 AC-1] Q-142: the contract check reports {0}")
  @MethodSource("brokenEnvelopes")
  void theContractCheckReportsABrokenEnvelope(
      String what, Consumer<ObjectNode> breakIt, String expectedPath) {
    ObjectNode envelope = validEnvelope();
    breakIt.accept(envelope);

    assertThat(EnvelopeContract.violations(envelope)).anyMatch(v -> v.startsWith(expectedPath));
  }

  /**
   * The Envelope schema of modern/contracts/asyncapi/events.yaml, read from the file: required
   * members, members outside the contract, type, format (uuid in its textual form of 8-4-4-4-12 hex
   * digits, not what java.util.UUID.fromString accepts; date-time), pattern and anyOf.
   */
  static final class EnvelopeContract {

    private static final Pattern UUID_FORMAT =
        Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Map<String, Object> ENVELOPE = load();

    private EnvelopeContract() {}

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
      return (Map<String, Object>) o;
    }

    private static Map<String, Object> load() {
      Path rel = Path.of("contracts", "asyncapi", "events.yaml");
      for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
        for (Path candidate : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
          if (Files.isRegularFile(candidate)) {
            try {
              Map<String, Object> spec = new Yaml().load(Files.readString(candidate));
              return map(map(map(spec.get("components")).get("schemas")).get("Envelope"));
            } catch (IOException e) {
              throw new UncheckedIOException("cannot read " + candidate, e);
            }
          }
        }
      }
      throw new IllegalStateException("contract not found: " + rel);
    }

    /** The violations of the envelope against the Envelope schema; empty if it conforms. */
    static List<String> violations(JsonNode envelope) {
      List<String> errors = new ArrayList<>();
      if (!envelope.isObject()) {
        errors.add("$ is not an object");
        return errors;
      }
      Map<String, Object> properties = map(ENVELOPE.get("properties"));
      for (Object required : (List<?>) ENVELOPE.get("required")) {
        if (!envelope.has((String) required)) {
          errors.add("$." + required + " is required");
        }
      }
      envelope
          .fieldNames()
          .forEachRemaining(
              name -> {
                if (properties.containsKey(name)) {
                  check(map(properties.get(name)), envelope.get(name), "$." + name, errors);
                } else {
                  errors.add("$." + name + " is not in the contract");
                }
              });
      return errors;
    }

    private static void check(
        Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
      Object type = schema.get("type");
      if ("string".equals(type) && !value.isTextual()) {
        errors.add(path + " is not a string");
      } else if ("object".equals(type) && !value.isObject()) {
        errors.add(path + " is not an object");
      } else if (value.isTextual()) {
        errors.addAll(textViolations(schema, value.asText(), path));
      }
    }

    private static List<String> textViolations(
        Map<String, Object> schema, String text, String path) {
      List<String> errors = new ArrayList<>();
      Object format = schema.get("format");
      if ("uuid".equals(format) && !UUID_FORMAT.matcher(text).matches()) {
        errors.add(path + " = " + text + " is not a valid uuid");
      }
      if ("date-time".equals(format) && !isDateTime(text)) {
        errors.add(path + " = " + text + " is not a valid date-time");
      }
      if (schema.get("pattern") instanceof String pattern
          && !Pattern.compile(pattern).matcher(text).find()) {
        errors.add(path + " = " + text + " does not match " + pattern);
      }
      if (schema.get("anyOf") instanceof List<?> branches
          && branches.stream().noneMatch(b -> textViolations(map(b), text, path).isEmpty())) {
        errors.add(path + " = " + text + " matches none of " + branches);
      }
      return errors;
    }

    private static boolean isDateTime(String text) {
      try {
        OffsetDateTime.parse(text);
        return true;
      } catch (DateTimeParseException e) {
        return false;
      }
    }
  }
}
