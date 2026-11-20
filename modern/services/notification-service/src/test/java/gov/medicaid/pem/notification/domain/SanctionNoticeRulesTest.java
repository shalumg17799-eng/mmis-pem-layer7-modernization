package gov.medicaid.pem.notification.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** MAM-58: PRVSAN01 GENERATE-NOTICE (126-129) with the GEN fallback superseded by D-026 (Q-A10). */
class SanctionNoticeRulesTest {

  @Test
  @DisplayName("[MAM-58 AC-1] action T gives the TRM notice")
  void terminate() {
    assertThat(NoticeRules.forSanction("T")).contains("TRM");
  }

  @Test
  @DisplayName("[MAM-58 AC-2] action S gives the SUS notice")
  void suspend() {
    assertThat(NoticeRules.forSanction("S")).contains("SUS");
  }

  @Test
  @DisplayName("[MAM-58 AC-3] action R gives the RST notice")
  void reinstate() {
    assertThat(NoticeRules.forSanction("R")).contains("RST");
  }

  @Test
  @DisplayName("[MAM-58 AC-4] any other action gives no notice, not the general notice GEN")
  void other() {
    assertThat(NoticeRules.forSanction("X")).isEmpty();
    assertThat(NoticeRules.forSanction("")).isEmpty();
    assertThat(NoticeRules.forSanction(null)).isEmpty();
    assertThat(NoticeRules.forSanction("GEN")).isEmpty();
    assertThat(NoticeRules.forSanction("t")).isEmpty();
  }
}
