package gov.medicaid.pem.tmsis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The fake provider-service of the tmsis tests against contract provider-service 1.2.0 (Q-132): the
 * tests of the walk are only worth something if the double answers {@code afterNpi} exactly as the
 * contract says, so this is checked here, over HTTP, with no Spring context. The double was written
 * to the contract in the same round as the tests that use it; these tests stop it from drifting
 * away from the contract (they are green from the start, as they test the test code itself).
 *
 * <p>Every request has its own time limit and the class is limited by JUnit.
 */
@Timeout(60)
class TestProviderServiceKeysetTest {

  static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  static final ObjectMapper JSON = new ObjectMapper();

  /** The NPI of the i-th provider (1-based): 1003001001, 1003001002, ... */
  static String npi(int i) {
    return String.format("1003%06d", 1000 + i);
  }

  record Answer(int status, String contentType, JsonNode body) {

    List<String> npis() {
      List<String> out = new ArrayList<>();
      body.path("items").forEach(i -> out.add(i.path("npi").asText()));
      return out;
    }
  }

  static Answer get(String query) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(TestProviderService.baseUrl() + "/api/v1/providers?" + query))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
    HttpResponse<String> r = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    return new Answer(
        r.statusCode(), r.headers().firstValue("Content-Type").orElse(""), JSON.readTree(r.body()));
  }

  /** Ten ACTIVE providers npi(1) to npi(10) and two PENDING ones, npi(11) and npi(12). */
  @BeforeEach
  void tenActiveAndTwoPending() {
    TestProviderService.reset();
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 10; i++) {
      all.add(Prov.active(npi(i)));
    }
    all.add(Prov.of(npi(11), "P"));
    all.add(Prov.of(npi(12), "P"));
    TestProviderService.holds(all);
  }

  @AfterEach
  void forgetEverything() {
    TestProviderService.reset();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] with afterNpi the answer holds the ACTIVE providers after the key, ascending, at most size of them, with page 0 and the total of ALL ACTIVE providers")
  void keysetAnswer() throws Exception {
    Answer a = get("status=A&size=3&afterNpi=" + npi(4));
    assertThat(a.status()).isEqualTo(200);
    assertThat(a.npis()).containsExactly(npi(5), npi(6), npi(7));
    assertThat(a.body().path("page").asInt()).isZero();
    assertThat(a.body().path("size").asInt()).isEqualTo(3);
    assertThat(a.body().path("total").asInt()).isEqualTo(10);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the key is only a position: a key that is not on file gives the providers after it; the key itself is never in the answer")
  void keyNeedNotBeOnFile() throws Exception {
    assertThat(get("status=A&size=2&afterNpi=1003001000").npis()).containsExactly(npi(1), npi(2));
    assertThat(get("status=A&size=2&afterNpi=1003001004").npis()).doesNotContain(npi(4));
    assertThat(get("status=A&size=2&afterNpi=1003009999").npis()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] after the last provider the list is empty (the end of a walk) and the total still counts all of them")
  void emptyAfterTheLast() throws Exception {
    Answer a = get("status=A&size=200&afterNpi=" + npi(10));
    assertThat(a.status()).isEqualTo(200);
    assertThat(a.npis()).isEmpty();
    assertThat(a.body().path("total").asInt()).isEqualTo(10);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the key combines with the status filter: pending providers after the key are not listed and not counted")
  void keyCombinesWithTheFilter() throws Exception {
    Answer a = get("status=A&size=200&afterNpi=" + npi(8));
    assertThat(a.npis()).containsExactly(npi(9), npi(10));
    assertThat(a.body().path("total").asInt()).isEqualTo(10);
    assertThat(get("status=P&size=200&afterNpi=" + npi(10)).npis())
        .containsExactly(npi(11), npi(12));
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] without afterNpi the answer is the offset page of 1.1.0: page 1 of size 3 is the fourth to sixth ACTIVE provider, page 1, total 10")
  void offsetAnswerIsUnchanged() throws Exception {
    Answer a = get("status=A&page=1&size=3");
    assertThat(a.npis()).containsExactly(npi(4), npi(5), npi(6));
    assertThat(a.body().path("page").asInt()).isEqualTo(1);
    assertThat(a.body().path("total").asInt()).isEqualTo(10);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] page 0 with afterNpi is allowed, as is no page at all: the answer is the same")
  void pageZeroWithAKey() throws Exception {
    Answer withPage = get("status=A&size=3&page=0&afterNpi=" + npi(4));
    assertThat(withPage.status()).isEqualTo(200);
    assertThat(withPage.npis()).isEqualTo(get("status=A&size=3&afterNpi=" + npi(4)).npis());
  }

  @ParameterizedTest(
      name =
          "[MAM-71 AC-5] afterNpi \"{0}\" is not exactly 10 digits: a 400 problem, never coerced")
  @ValueSource(
      strings = {
        "",
        "100300100",
        "10030010044",
        "abcdefghij",
        "+003001004",
        "%201003001004",
        "1003001004%20"
      })
  void malformedKeyIsA400(String key) throws Exception {
    Answer a = get("status=A&size=3&afterNpi=" + key);
    assertThat(a.status()).isEqualTo(400);
    assertThat(a.contentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName("[MAM-71 AC-5] afterNpi given twice is a 400 problem")
  void keyGivenTwiceIsA400() throws Exception {
    assertThat(get("status=A&size=3&afterNpi=" + npi(1) + "&afterNpi=" + npi(2)).status())
        .isEqualTo(400);
  }

  @Test
  @DisplayName("[MAM-71 AC-5] afterNpi together with a page other than 0 is a 400 problem")
  void keyWithAnotherPageIsA400() throws Exception {
    assertThat(get("status=A&size=3&page=1&afterNpi=" + npi(1)).status()).isEqualTo(400);
  }

  @ParameterizedTest(name = "[MAM-71 AC-5] size {0} is outside 1 to 200: a 400 problem")
  @ValueSource(strings = {"0", "201", "-1", "x"})
  void sizeOutsideTheContractIsA400(String size) throws Exception {
    assertThat(get("status=A&size=" + size).status()).isEqualTo(400);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] the data can change between two answers: the first answer shows the data as it was, the second the data as it is, and the change happens once")
  void dataChangesBetweenAnswers() throws Exception {
    TestProviderService.afterSearch(1, () -> TestProviderService.changeStatus(npi(2), "T"));
    Answer first = get("status=A&size=200");
    Answer second = get("status=A&size=200");
    Answer third = get("status=A&size=200");
    assertThat(first.body().path("total").asInt()).isEqualTo(10);
    assertThat(first.npis()).contains(npi(2));
    assertThat(second.body().path("total").asInt()).isEqualTo(9);
    assertThat(second.npis()).doesNotContain(npi(2));
    assertThat(third.body().path("total").asInt()).isEqualTo(9);
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a provider-service that ignores the key answers the first page again, three times, then an empty list (so that a defective reader cannot loop for ever)")
  void ignoredKeyAnswersAreBounded() throws Exception {
    TestProviderService.answerIgnoringTheKey();
    assertThat(get("status=A&size=2").npis()).containsExactly(npi(1), npi(2));
    for (int i = 0; i < 3; i++) {
      assertThat(get("status=A&size=2&afterNpi=" + npi(2)).npis()).containsExactly(npi(1), npi(2));
    }
    assertThat(get("status=A&size=2&afterNpi=" + npi(2)).npis()).isEmpty();
  }
}
