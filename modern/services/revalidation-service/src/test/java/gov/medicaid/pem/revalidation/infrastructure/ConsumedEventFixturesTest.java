package gov.medicaid.pem.revalidation.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.revalidation.EventsContractFile;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The events that the tests of this service feed to its three consumers are the events that
 * modern/contracts/asyncapi/events.yaml allows (ProviderStatusChangedEnvelope,
 * ScreeningCompletedEnvelope, RevalidationDueEnvelope), so a consumer test never passes on an input
 * that the producing service could not send. The checker itself is shown to reject a status, a
 * reason and a result outside the contract's enums, so an accepted event means something.
 */
class ConsumedEventFixturesTest {

  static final String PROVIDER = "ProviderStatusChangedEnvelope";
  static final String SCREENING = "ScreeningCompletedEnvelope";
  static final String DUE = "RevalidationDueEnvelope";

  private static JsonNode read(String json) {
    return RevalidationFixtures.read(json);
  }

  @ParameterizedTest(
      name = "[MAM-49 AC-2] a ProviderStatusChanged from A to {0} conforms to the contract")
  @ValueSource(strings = {"P", "A", "T", "D", "S"})
  void providerStatusChangedConforms(String to) {
    JsonNode event =
        read(
            RevalidationFixtures.providerStatusChanged(
                UUID.randomUUID(), "1003000222", "A", to, "2026-02-28"));

    assertThat(EventsContractFile.violations(PROVIDER, event)).as(event.toString()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-6] a ProviderStatusChanged whose revalidationDueDate is null conforms to the contract")
  void providerStatusChangedWithoutDueDateConforms() {
    JsonNode event =
        read(
            RevalidationFixtures.providerStatusChanged(
                UUID.randomUUID(), "1003000666", "P", "A", null));

    assertThat(EventsContractFile.violations(PROVIDER, event)).as(event.toString()).isEmpty();
    assertThat(event.path("data").path("revalidationDueDate").isNull()).isTrue();
  }

  @ParameterizedTest(
      name =
          "[MAM-48 AC-4] a ScreeningCompleted with reason {0} and result {1} conforms to the contract")
  @CsvSource({
    "INITIAL, PASS",
    "INITIAL, FAIL",
    "REVALIDATION, PASS",
    "REVALIDATION, FAIL",
    "MANUAL, PASS",
    "MANUAL, FAIL"
  })
  void screeningCompletedConforms(String reason, String result) {
    JsonNode event =
        read(
            RevalidationFixtures.screeningCompleted(
                UUID.randomUUID(), "1003000222", reason, result, UUID.randomUUID()));

    assertThat(EventsContractFile.violations(SCREENING, event)).as(event.toString()).isEmpty();
  }

  @Test
  @DisplayName("[MAM-49 AC-1] a RevalidationDue of a case conforms to the contract")
  void revalidationDueConforms() {
    JsonNode event =
        read(
            RevalidationFixtures.revalidationDue(
                UUID.randomUUID(), UUID.randomUUID(), "1003000222", "2026-02-28"));

    assertThat(EventsContractFile.violations(DUE, event)).as(event.toString()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-45 AC-3] the checker refuses a status outside the contract's P, A, T, D, S, so the conforming fixtures prove something")
  void checkerRefusesAnUnknownStatus() {
    ObjectNode event =
        (ObjectNode)
            read(
                RevalidationFixtures.providerStatusChanged(
                    UUID.randomUUID(), "1003000222", "A", "A", "2026-02-28"));
    ((ObjectNode) event.get("data")).put("to", "X");

    assertThat(EventsContractFile.violations(PROVIDER, event))
        .anyMatch(v -> v.startsWith("$.data.to"));
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-6] the checker refuses a reason or a result outside the contract's enums")
  void checkerRefusesAnUnknownReasonOrResult() {
    ObjectNode reason =
        (ObjectNode)
            read(
                RevalidationFixtures.screeningCompleted(
                    UUID.randomUUID(), "1003000222", "REVALIDATION", "PASS", UUID.randomUUID()));
    ((ObjectNode) reason.get("data")).put("reason", "ANNUAL");
    ObjectNode result =
        (ObjectNode)
            read(
                RevalidationFixtures.screeningCompleted(
                    UUID.randomUUID(), "1003000222", "REVALIDATION", "PASS", UUID.randomUUID()));
    ((ObjectNode) result.get("data")).put("result", "CLEAR");

    assertThat(EventsContractFile.violations(SCREENING, reason))
        .anyMatch(v -> v.startsWith("$.data.reason"));
    assertThat(EventsContractFile.violations(SCREENING, result))
        .anyMatch(v -> v.startsWith("$.data.result"));
  }
}
