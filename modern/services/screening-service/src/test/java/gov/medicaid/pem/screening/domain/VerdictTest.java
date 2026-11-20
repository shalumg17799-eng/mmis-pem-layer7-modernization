package gov.medicaid.pem.screening.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** MAM-25: anything not P fails the screening (R-PRVSCR01-cbl-00010). */
class VerdictTest {

  static final Instant AT = Instant.parse("2026-11-18T10:00:00Z");

  static List<SourceResult> allPass() {
    return List.of(
        new SourceResult(Source.LICBOARD, "P", AT, "OK", null),
        new SourceResult(Source.OIGLEIE, "P", AT, "OK", null),
        new SourceResult(Source.SAMGOV, "P", AT, "OK", null),
        new SourceResult(Source.SSADMF, "P", AT, "OK", null));
  }

  @Test
  @DisplayName("[MAM-25 AC-1] four P results pass")
  void pass() {
    assertThat(Verdict.of(allPass())).isEqualTo("PASS");
  }

  @ParameterizedTest(name = "[MAM-25 AC-2] one {0} result fails the screening")
  @ValueSource(strings = {"F", "C"})
  void oneNotPass(String result) {
    List<SourceResult> r = new java.util.ArrayList<>(allPass());
    r.set(2, new SourceResult(Source.SAMGOV, result, AT, "X", null));
    assertThat(Verdict.of(r)).isEqualTo("FAIL");
  }

  @ParameterizedTest(
      name = "[MAM-25 AC-2] a failing {0} fails the screening, whichever source it is")
  @EnumSource(Source.class)
  void anySource(Source failing) {
    List<SourceResult> r =
        allPass().stream()
            .map(x -> x.source() == failing ? new SourceResult(failing, "F", AT, "X", null) : x)
            .toList();
    assertThat(Verdict.of(r)).isEqualTo("FAIL");
  }

  @Test
  @DisplayName("[MAM-25 AC-3] a screening without its four results is not a pass")
  void incomplete() {
    assertThat(Verdict.of(allPass().subList(0, 3))).isEqualTo("FAIL");
  }
}
