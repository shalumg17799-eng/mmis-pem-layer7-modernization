package gov.medicaid.pem.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * MAM-60 AC-7 and MAM-59 Lane 3: a template body always carries the NPI (Q-A8): {npi} is replaced
 * by the NPI of the notice, everywhere it stands, and by nothing else.
 */
class TemplateTest {

  @Test
  @DisplayName("[MAM-59 AC-1] the placeholder {npi} is replaced by the NPI of the notice")
  void placeholderIsReplaced() {
    Template t = new Template("APPROVED", "Enrolment approved", "Provider NPI {npi} is approved.");

    assertThat(t.bodyFor("1003000111")).isEqualTo("Provider NPI 1003000111 is approved.");
  }

  @Test
  @DisplayName("[MAM-59 AC-1] every {npi} in the body is replaced, not only the first")
  void everyPlaceholderIsReplaced() {
    Template t = new Template("GEN", "General notice", "{npi}: a notice for {npi}.");

    assertThat(t.bodyFor("1003000444")).isEqualTo("1003000444: a notice for 1003000444.");
  }

  @Test
  @DisplayName("[MAM-59 AC-1] other braces and a lower or upper case spelling are left as they are")
  void otherTextIsUntouched() {
    Template t = new Template("GEN", "General notice", "{NPI} {npi } {id} {npi}");

    assertThat(t.bodyFor("1003000111")).isEqualTo("{NPI} {npi } {id} 1003000111");
  }

  @Test
  @DisplayName("[MAM-60 AC-7] the code, the subject and the body are the three parts of a template")
  void parts() {
    Template t = new Template("TRM", "Termination notice", "body");

    assertThat(t.code()).isEqualTo("TRM");
    assertThat(t.subject()).isEqualTo("Termination notice");
    assertThat(t.body()).isEqualTo("body");
    assertThat(t.bodyFor("1003000111")).isEqualTo("body");
  }

  @Test
  @DisplayName(
      "[MAM-59 AC-1] a character of the NPI that is special in a pattern is copied as it is")
  void replacementIsLiteral() {
    Template t = new Template("GEN", "General notice", "NPI {npi}");

    assertThat(t.bodyFor("$1\\")).isEqualTo("NPI $1\\");
  }
}
