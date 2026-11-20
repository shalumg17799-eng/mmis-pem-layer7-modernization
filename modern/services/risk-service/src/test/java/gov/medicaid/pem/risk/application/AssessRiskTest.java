package gov.medicaid.pem.risk.application;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.risk.domain.RuleSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-35: only a PASS is tiered; the assessment is stored, announced once and counted. */
class AssessRiskTest {

  static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

  record Sent(String type, String subject, Object data) {}

  record Stored(String npi, String taxonomy, String tier, int version, Instant at) {}

  final List<Sent> sent = new ArrayList<>();
  final List<Stored> stored = new ArrayList<>();
  final int[] counted = {0};

  final AssessRisk assess =
      new AssessRisk(
          () -> new RuleSet(1, "L", Map.of("332", "H", "341", "M")),
          (npi, taxonomy, tier, version, at) ->
              stored.add(new Stored(npi, taxonomy, tier, version, at)),
          (type, subject, data) -> sent.add(new Sent(type, subject, data)),
          () -> counted[0]++,
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  @DisplayName(
      "[MAM-35 AC-1] a PASS is assessed with the rule set in force, stored, announced and counted")
  void pass() {
    assertThat(assess.screeningCompleted("1003000111", "PASS", "207Q00000X")).contains("L");
    assertThat(stored).containsExactly(new Stored("1003000111", "207Q00000X", "L", 1, NOW));
    assertThat(sent)
        .containsExactly(
            new Sent(
                "RiskTierAssigned",
                "1003000111",
                new RiskTierAssignedData("1003000111", "207Q00000X", "L", 1)));
    assertThat(counted[0]).isEqualTo(1);
  }

  @Test
  @DisplayName("[MAM-35 AC-2] the tier comes from the rule set")
  void tierFromRules() {
    assertThat(assess.screeningCompleted("1003000222", "PASS", "332B00000X")).contains("H");
    assertThat(assess.screeningCompleted("1003000333", "PASS", "341600000X")).contains("M");
  }

  @Test
  @DisplayName("[MAM-35 AC-6] a FAIL gets no tier: nothing stored, announced or counted")
  void fail() {
    assertThat(assess.screeningCompleted("1003000444", "FAIL", "251E00000X")).isEmpty();
    assertThat(stored).isEmpty();
    assertThat(sent).isEmpty();
    assertThat(counted[0]).isZero();
  }
}
