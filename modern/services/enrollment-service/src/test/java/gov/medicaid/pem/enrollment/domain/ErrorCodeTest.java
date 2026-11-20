package gov.medicaid.pem.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The enrolment validation codes of ERRCODES.cpy that the intake reports (D-016): E101 to E104,
 * always with their full four characters. The APPERROR file and the error lists name them.
 */
class ErrorCodeTest {

  @Test
  @DisplayName("[MAM-56 AC-4] exactly the four enrolment codes E101, E102, E103 and E104 exist")
  void fourCodes() {
    assertThat(Arrays.stream(ErrorCode.values()).map(Enum::name))
        .containsExactly("E101", "E102", "E103", "E104");
  }

  @ParameterizedTest(name = "[MAM-56 AC-4] {0} says what is wrong: it mentions \"{1}\"")
  @CsvSource({
    "E101, NPI",
    "E102, provider type",
    "E103, already been accepted",
    "E104, taxonomy",
  })
  void messageSaysWhatIsWrong(ErrorCode code, String keyword) {
    assertThat(code.message()).isNotBlank().containsIgnoringCase(keyword.trim());
  }

  @Test
  @DisplayName("[MAM-56 AC-4] every code is four characters long, never cut to E1 (D-016)")
  void fullFourCharacterCodes() {
    assertThat(ErrorCode.values()).allSatisfy(c -> assertThat(c.name()).hasSize(4));
  }

  @Test
  @DisplayName("[MAM-57 AC-3] a duplicate NPI has its own code E103, apart from a missing NPI E101")
  void duplicateIsNotMissing() {
    assertThat(ErrorCode.E103).isNotEqualTo(ErrorCode.E101);
    assertThat(ErrorCode.E103.message()).isNotEqualTo(ErrorCode.E101.message());
  }
}
