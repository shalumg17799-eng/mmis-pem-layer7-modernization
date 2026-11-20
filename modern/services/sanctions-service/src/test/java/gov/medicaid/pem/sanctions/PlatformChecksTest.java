package gov.medicaid.pem.sanctions;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.FieldDef;
import gov.medicaid.pem.fixedwidth.FieldType;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.fixedwidth.RecordLayout;
import gov.medicaid.pem.testsupport.PemArchitecture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Platform checks of sanctions-service, written with its Lane 3 tests (MAM-55): the layering rules
 * every service follows, and the SANCIN layout the intake reads (exported from the graph, never
 * typed by hand).
 */
class PlatformChecksTest {

  @Test
  @DisplayName("[MAM-55] sanctions-service respects the layering rules")
  void architecture() {
    PemArchitecture.check("gov.medicaid.pem.sanctions");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-2] the SANCIN layout is 80 bytes: NPI 1-10, action 11, reason 12-51, date 52-59, filler 60-80")
  void sancinLayout() {
    RecordLayout layout = FixedWidthCodec.forLayout("SANCIN").layout();
    assertThat(layout.length()).isEqualTo(80);
    assertThat(layout.fields()).hasSize(5);
    assertField(layout.field("SAN-NPI"), 1, 10, FieldType.ALPHANUMERIC);
    assertField(layout.field("SAN-ACTION"), 11, 1, FieldType.ALPHANUMERIC);
    assertField(layout.field("SAN-REASON"), 12, 40, FieldType.ALPHANUMERIC);
    assertField(layout.field("SAN-EFF-DATE"), 52, 8, FieldType.DATE_CCYYMMDD);
    assertField(layout.field("FILLER"), 60, 21, FieldType.ALPHANUMERIC);
  }

  @Test
  @DisplayName("[MAM-55 AC-2] the test fixture writes its fields where the layout puts them")
  void fixtureFollowsTheLayout() {
    String record = SancinFile.record("1003000111", "T", "SYNTHETIC TEST REASON", "20260331");
    assertThat(record).hasSize(80);
    assertThat(record.substring(0, 10)).isEqualTo("1003000111");
    assertThat(record.substring(10, 11)).isEqualTo("T");
    assertThat(record.substring(11, 51).trim()).isEqualTo("SYNTHETIC TEST REASON");
    assertThat(record.substring(51, 59)).isEqualTo("20260331");
    assertThat(record.substring(59)).isEqualTo(" ".repeat(21));
  }

  private static void assertField(FieldDef f, int start, int length, FieldType type) {
    assertThat(f.start()).as("%s start", f.name()).isEqualTo(start);
    assertThat(f.length()).as("%s length", f.name()).isEqualTo(length);
    assertThat(f.type()).as("%s type", f.name()).isEqualTo(type);
  }
}
