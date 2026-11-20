package gov.medicaid.pem.screening.infrastructure;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static gov.medicaid.pem.screening.TestSources.MOCK;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import gov.medicaid.pem.screening.Lane3Test;
import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * MAM-28 AC-5 on the whole service (question C-2, D-014): every screening, whatever started it,
 * reads the provider's data from provider-service afresh. What changed since the first screening
 * (licence, name, provider type, owner, taxonomy) is what the sources are asked about and what the
 * new ScreeningCompleted carries; the first screening stays as it was (D-018).
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class CurrentDataTest extends Lane3Test {

  static final String NPI = "1003000111";
  static final String ORG = "1003000444";

  /** The provider as first registered: an individual with the ACTIVE licence MD12345. */
  void firstScreening() {
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX"));
    ScreeningWorld.licence("MD12345", "TX", "ACTIVE", "2030-12", 3);
    ScreeningWorld.licence("PT99900", "TX", "EXPIRED", "2024-01", 3);
    awaitProcessed(providerRegistered(NPI));
    assertThat(screeningCount(NPI)).isEqualTo(1);
    MOCK.resetRequests();
  }

  /** The newest screening of the NPI as the API lists it. */
  JsonNode newest(String npi) throws Exception {
    return body(get("/api/v1/screenings?npi=" + npi, PemJwt.analyst())).path("items").get(0);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] a REVALIDATION screening asks the board about the current licence PT99900, not about the first one, MD12345: LICBOARD is F with E202")
  void revalidationUsesTheCurrentLicence() throws Exception {
    firstScreening();
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "PT99900", "TX"));
    awaitProcessed(revalidationDue(NPI, "2026-02-28"));
    JsonNode s = newest(NPI);
    assertThat(s.path("reason").asText()).isEqualTo("REVALIDATION");
    JsonNode licence = resultOf(s, "LICBOARD");
    assertThat(licence.path("result").asText()).isEqualTo("F");
    assertThat(licence.path("errorCode").asText()).isEqualTo("E202");
    assertThat(ScreeningWorld.lookups("LICBOARD", "PT99900")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("LICBOARD", "MD12345")).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] the first screening is not rewritten by the re-screening: it keeps its licence result P")
  void firstScreeningStaysAsItWas() throws Exception {
    firstScreening();
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "PT99900", "TX"));
    screen(NPI);
    List<JsonNode> all = new ArrayList<>();
    body(get("/api/v1/screenings?npi=" + NPI, PemJwt.analyst())).path("items").forEach(all::add);
    assertThat(all).hasSize(2);
    assertThat(all.get(0).path("reason").asText()).isEqualTo("MANUAL");
    assertThat(resultOf(all.get(0), "LICBOARD").path("result").asText()).isEqualTo("F");
    assertThat(all.get(1).path("reason").asText()).isEqualTo("INITIAL");
    assertThat(resultOf(all.get(1), "LICBOARD").path("result").asText()).isEqualTo("P");
    assertThat(resultOf(all.get(1), "LICBOARD").path("detail").asText()).contains("MD12345");
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] a MANUAL screening of an organisation uses its current owner: an owner added since the first screening is looked up and fails it with E203")
  void manualUsesTheCurrentOwner() throws Exception {
    ScreeningWorld.holds(ScreeningWorld.organization(ORG, "GR", "EXCLUDED CLINIC LLC", null));
    awaitProcessed(providerRegistered(ORG));
    ScreeningWorld.leieListed("1990000111");
    ScreeningWorld.holds(
        ScreeningWorld.organization(ORG, "GR", "EXCLUDED CLINIC LLC", "1990000111"));
    MOCK.resetRequests();
    JsonNode s = screen(ORG);
    JsonNode leie = resultOf(s, "OIGLEIE");
    assertThat(leie.path("result").asText()).isEqualTo("F");
    assertThat(leie.path("errorCode").asText()).isEqualTo("E203");
    assertThat(leie.path("detail").asText()).contains("1990000111");
    assertThat(ScreeningWorld.lookups("OIGLEIE", "1990000111")).isEqualTo(1);
    assertThat(ScreeningWorld.lookups("OIGLEIE", ORG)).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] SAM is asked with the provider's current name, not the name of the first screening")
  void samIsAskedWithTheCurrentName() throws Exception {
    firstScreening();
    ScreeningWorld.holds(ScreeningWorld.individual(NPI, "MARIA", "GARCIA", "MD12345", "TX"));
    screen(NPI);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo(ScreeningWorld.SAM))
                    .withQueryParam("name", equalTo("MARIA GARCIA"))))
        .hasSize(1);
    assertThat(
            MOCK.findAll(
                getRequestedFor(urlPathEqualTo(ScreeningWorld.SAM))
                    .withQueryParam("name", equalTo("MARIA RIVERA"))))
        .isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] a provider that is a facility now is not checked against a licence any more: LICBOARD is P, not applicable, and the board is not asked")
  void manualUsesTheCurrentType() throws Exception {
    firstScreening();
    ScreeningWorld.holds(ScreeningWorld.organization(NPI, "FA", "RIVERA CLINIC", null));
    JsonNode s = screen(NPI);
    JsonNode licence = resultOf(s, "LICBOARD");
    assertThat(licence.path("result").asText()).isEqualTo("P");
    assertThat(licence.path("detail").asText()).contains("NOT APPLICABLE");
    assertThat(ScreeningWorld.lookups("LICBOARD")).isZero();
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] the ScreeningCompleted of the re-screening carries the current taxonomy, the first one the old taxonomy")
  void eventCarriesTheCurrentTaxonomy() throws Exception {
    firstScreening();
    ObjectNode changed = ScreeningWorld.individual(NPI, "MARIA", "RIVERA", "MD12345", "TX");
    changed.put("taxonomy", "332B00000X");
    ScreeningWorld.holds(changed);
    screen(NPI);
    List<String> taxonomies =
        jdbc.queryForList(
            "SELECT payload::jsonb -> 'data' ->> 'taxonomy' FROM outbox_event"
                + " WHERE type = 'ScreeningCompleted' AND subject = ? ORDER BY created_at",
            String.class,
            NPI);
    assertThat(taxonomies).containsExactly("207Q00000X", "332B00000X");
  }

  @Test
  @DisplayName(
      "[MAM-28 AC-5] the provider is read for each screening, so a re-screening never uses what an earlier one read")
  void providerIsReadForEveryScreening() throws Exception {
    firstScreening();
    screen(NPI);
    screen(NPI);
    assertThat(ScreeningWorld.providerReads(NPI)).isGreaterThanOrEqualTo(2);
  }
}
