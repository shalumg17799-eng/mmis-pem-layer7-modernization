package gov.medicaid.pem.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.risk.domain.RuleSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

/**
 * MAM-41: the tier the table in force gives a taxonomy (PRVRSK01 CLASSIFY-ONE), on request, with no
 * side effect (D-002): the use case has no store and no outbox to write to. Business date
 * 2026-01-15 (fixed Clock).
 */
class AssessOnRequestTest {

  static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
  static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  static final RuleSet V1 =
      new RuleSet(1, "L", Map.of("332", "H", "251", "H", "341", "M", "291", "M", "225", "M"));

  final AssessOnRequest assess = new AssessOnRequest(() -> V1, CLOCK);

  static ApiException refused(ThrowingCallable call) {
    Throwable t = catchThrowable(call);
    assertThat(t).isInstanceOf(ApiException.class);
    return (ApiException) t;
  }

  // ---- AC-1 .. AC-3 ----------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-41 AC-1] taxonomy {0} gives tier {1} under rule set version 1")
  @CsvSource({
    "332B00000X,H",
    "251E00000X,H",
    "341600000X,M",
    "291U00000X,M",
    "225100000X,M",
    "207Q00000X,L"
  })
  void tierOfTheTable(String taxonomy, String tier) {
    AssessOnRequest.Assessment a = assess.assess(null, taxonomy);

    assertThat(a.taxonomy()).isEqualTo(taxonomy);
    assertThat(a.tier()).isEqualTo(tier);
    assertThat(a.ruleSetVersion()).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-41 AC-1] the answer is stamped with the business time of the Clock")
  void assessedAt() {
    assertThat(assess.assess(null, "332B00000X").assessedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("[MAM-41 AC-1] without an NPI the answer carries none")
  void noNpiInTheAnswer() {
    assertThat(assess.assess(null, "332B00000X").npi()).isNull();
  }

  @Test
  @DisplayName(
      "[MAM-41 AC-5] with an NPI the answer repeats it, and the tier is the same as without")
  void npiRepeated() {
    AssessOnRequest.Assessment with = assess.assess("1003000111", "332B00000X");
    AssessOnRequest.Assessment without = assess.assess(null, "332B00000X");

    assertThat(with.npi()).isEqualTo("1003000111");
    assertThat(with.tier()).isEqualTo("H").isEqualTo(without.tier());
    assertThat(with.ruleSetVersion()).isEqualTo(without.ruleSetVersion());
  }

  @ParameterizedTest(
      name = "[MAM-41 AC-2] taxonomy {0} gives tier H: only the first three characters count")
  @ValueSource(strings = {"3320000000", "332ZZZZZZZ", "332", "332B"})
  void onlyThreeCharacters(String taxonomy) {
    assertThat(assess.assess(null, taxonomy).tier()).isEqualTo("H");
  }

  @ParameterizedTest(name = "[MAM-41 AC-3] taxonomy \"{0}\" gets the default tier L, not an error")
  @ValueSource(strings = {"", " ", "   ", "          ", "3", "33"})
  void blankOrShort(String taxonomy) {
    AssessOnRequest.Assessment a = assess.assess(null, taxonomy);

    assertThat(a.tier()).isEqualTo("L");
    assertThat(a.taxonomy()).isEqualTo(taxonomy);
  }

  // ---- AC-4 ------------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-41 AC-4] no taxonomy is a 400")
  void noTaxonomy() {
    ApiException e = refused(() -> assess.assess(null, null));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("taxonomy is required");
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a taxonomy of 11 characters is a 400")
  void tooLong() {
    ApiException e = refused(() -> assess.assess(null, "332B00000XY"));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("at most 10");
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a taxonomy of exactly 10 characters is accepted")
  void tenCharacters() {
    assertThat(assess.assess(null, "332B00000X").tier()).isEqualTo("H");
    assertThat(assess.assess(null, "          ").tier()).isEqualTo("L");
  }

  @ParameterizedTest(name = "[MAM-41 AC-4] NPI \"{0}\" is a 400 (10 digits are required)")
  @EmptySource
  @ValueSource(
      strings = {
        "123",
        "100300011",
        "10030001111",
        "100300011A",
        " 1003000111",
        "1003000111 ",
        "10-3000111"
      })
  void badNpi(String npi) {
    ApiException e = refused(() -> assess.assess(npi, "332B00000X"));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("10 digits");
  }

  @Test
  @DisplayName("[MAM-41 AC-5] an NPI of 10 digits is accepted, leading zeros kept")
  void goodNpi() {
    assertThat(assess.assess("0003000111", "207Q00000X").npi()).isEqualTo("0003000111");
  }

  // ---- AC-6 ------------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-41 AC-6] the answer uses the table in force at the moment of the question")
  void usesTheTableInForce() {
    AtomicReference<RuleSet> inForce = new AtomicReference<>(V1);
    AssessOnRequest live = new AssessOnRequest(inForce::get, CLOCK);

    assertThat(live.assess(null, "207Q00000X").tier()).isEqualTo("L");
    assertThat(live.assess(null, "207Q00000X").ruleSetVersion()).isEqualTo(1);

    inForce.set(
        new RuleSet(
            2,
            "L",
            Map.of("332", "H", "251", "H", "341", "M", "291", "M", "225", "M", "207", "M")));

    AssessOnRequest.Assessment now = live.assess(null, "207Q00000X");
    assertThat(now.tier()).isEqualTo("M");
    assertThat(now.ruleSetVersion()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-41 AC-6] a blank taxonomy gets the default tier of the table in force")
  void defaultTierOfTheTableInForce() {
    AssessOnRequest m = new AssessOnRequest(() -> new RuleSet(2, "M", Map.of()), CLOCK);

    assertThat(m.assess(null, "").tier()).isEqualTo("M");
    assertThat(m.assess(null, "33").tier()).isEqualTo("M");
    assertThat(m.assess(null, "207Q00000X").tier()).isEqualTo("M");
  }

  @Test
  @DisplayName("[MAM-41 AC-4] a refused request never asks for the table")
  void refusedDoesNotLookAtTheTable() {
    int[] asked = {0};
    AssessOnRequest counting =
        new AssessOnRequest(
            () -> {
              asked[0]++;
              return V1;
            },
            CLOCK);

    refused(() -> counting.assess(null, null));
    refused(() -> counting.assess(null, "332B00000XY"));
    refused(() -> counting.assess("12", "332B00000X"));

    assertThat(asked[0]).isZero();
  }
}
