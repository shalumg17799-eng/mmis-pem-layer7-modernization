package gov.medicaid.pem.fixedwidth;

import static gov.medicaid.pem.fixedwidth.Samples.GR_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.IN_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.RESERVED;
import static gov.medicaid.pem.fixedwidth.Samples.bytes;
import static gov.medicaid.pem.fixedwidth.Samples.slice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * MAM-42 AC-2: the provider master is 130 bytes (D-007) and the 200-byte intake record keeps bytes
 * 131-200 reserved (D-025, Q-009).
 */
class ProviderMasterLayoutTest {

  private final FixedWidthCodec master = FixedWidthCodec.forLayout("PRVRECRD");
  private final FixedWidthCodec intake = FixedWidthCodec.forLayout("APPINPUT");

  /**
   * A field with every filler called FILLER, so PRVRECRD and APPINPUT can be compared whatever the
   * exporter names them.
   */
  private record Placed(String name, int start, int length, FieldType type) {
    static Placed of(FieldDef field) {
      String name = field.name().startsWith("FILLER") ? "FILLER" : field.name();
      return new Placed(name, field.start(), field.length(), field.type());
    }
  }

  @Test
  @DisplayName("[MAM-42 AC-2] the PRVRECRD layout is 130 bytes, not the legacy FD's 121")
  void masterLayoutIs130Bytes() {
    assertThat(master.layout().length()).isEqualTo(130);
  }

  @Test
  @DisplayName("[MAM-42 AC-2] a written provider master record is exactly 130 bytes")
  void writtenMasterIs130Bytes() {
    assertThat(master.write(Samples.groupRecord(master))).hasSize(130);
  }

  @ParameterizedTest(name = "{0} is bytes {1} to {2}")
  @CsvSource({
    "PRV-NPI,1,10",
    "PRV-MEDICAID-ID,11,19",
    "PRV-TYPE-CODE,20,21",
    "PRV-TAXONOMY,22,31",
    "PRV-RISK-TIER,32,32",
    "PRV-ENROLL-STATUS,33,33",
    "PRV-SCREEN-FLAG,34,34",
    "PRV-EFFECTIVE-DATE,35,42",
    "PRV-REVAL-DUE-DATE,43,50",
    "PRV-VARIANT,51,130"
  })
  @DisplayName("[MAM-42 AC-2] the common fields sit at the documented bytes")
  void commonFieldPositions(String field, int firstByte, int lastByte) {
    FieldDef def = master.layout().field(field);
    assertThat(def.start()).as(field + " first byte").isEqualTo(firstByte);
    assertThat(def.start() + def.length() - 1).as(field + " last byte").isEqualTo(lastByte);
  }

  @ParameterizedTest(name = "{0} is written at bytes {1} to {2}")
  @CsvSource({
    "PRV-NPI,1,10,1003000444",
    "PRV-MEDICAID-ID,11,19,TXM000444",
    "PRV-TYPE-CODE,20,21,GR",
    "PRV-TAXONOMY,22,31,193200000X",
    "PRV-RISK-TIER,32,32,H",
    "PRV-ENROLL-STATUS,33,33,P",
    "PRV-SCREEN-FLAG,34,34,U",
    "PRV-EFFECTIVE-DATE,35,42,20260115",
    "PRV-REVAL-DUE-DATE,43,50,20310115",
    "PRV-LEGAL-NAME,51,90,SAMPLE CLINIC 01",
    "PRV-DBA-NAME,91,120,SAMPLE CLINIC DBA",
    "PRV-OWNER-NPI,121,130,1990000111"
  })
  @DisplayName("[MAM-42 AC-2] writing a record puts each value at its bytes")
  void writtenValuesLandAtTheirBytes(String field, int firstByte, int lastByte, String expected) {
    byte[] written = master.write(Samples.groupRecord(master));
    assertThat(slice(written, firstByte, lastByte - firstByte + 1).trim()).isEqualTo(expected);
  }

  @Test
  @DisplayName("[MAM-42 AC-2] the owner NPI at bytes 121-130 is written and survives a read")
  void ownerNpiSurvivesWriteAndRead() {
    byte[] written = master.write(Samples.groupRecord(master));
    assertThat(master.read(written, 1).get("PRV-OWNER-NPI")).isEqualTo("1990000111");
  }

  @ParameterizedTest(name = "a {0}-byte master record is rejected")
  @CsvSource({"121", "129", "131"})
  @DisplayName(
      "[MAM-42 AC-2] a master record that is not 130 bytes is rejected (121 is the legacy defect)")
  void wrongLengthMasterIsRejected(int length) {
    byte[] record = Arrays.copyOf(bytes(GR_MASTER + RESERVED), length);
    assertThatThrownBy(() -> master.read(record, 1))
        .isInstanceOfSatisfying(
            FixedWidthException.class,
            ex ->
                assertThat(ex)
                    .hasMessageContaining("PRVRECRD")
                    .hasMessageContaining("130")
                    .hasMessageContaining(String.valueOf(length)));
  }

  @Test
  @DisplayName("[MAM-42 AC-2] the APPINPUT layout is 200 bytes")
  void intakeLayoutIs200Bytes() {
    assertThat(intake.layout().length()).isEqualTo(200);
  }

  @Test
  @DisplayName("[MAM-42 AC-2] APPINPUT bytes 1-130 are the PRVRECRD layout")
  void intakeStartsWithTheMasterLayout() {
    List<Placed> intakeHead =
        intake.layout().fields().stream().filter(f -> f.start() <= 130).map(Placed::of).toList();
    List<Placed> masterFields = master.layout().fields().stream().map(Placed::of).toList();
    assertThat(intakeHead).containsExactlyInAnyOrderElementsOf(masterFields);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-2] APPINPUT bytes 131-200 are reserved: one run of unnamed filler, no data fields")
  void intakeTailIsReserved() {
    List<FieldDef> tail =
        intake.layout().fields().stream()
            .filter(f -> f.start() > 130)
            .sorted(Comparator.comparingInt(FieldDef::start))
            .toList();
    assertThat(tail).isNotEmpty();
    assertThat(tail.get(0).start()).isEqualTo(131);
    assertThat(tail.get(tail.size() - 1).start() + tail.get(tail.size() - 1).length() - 1)
        .isEqualTo(200);
    assertThat(tail)
        .allSatisfy(
            f -> assertThat(f.name().toUpperCase(Locale.ROOT)).matches("(RESERVED|FILLER).*"));
  }

  @Test
  @DisplayName("[MAM-42 AC-2] reading APPINPUT interprets bytes 1-130 as a provider record")
  void intakeReadsTheMasterFields() {
    FixedRecord read = intake.read(bytes(IN_MASTER + RESERVED), 1);
    assertThat(read.get("PRV-NPI")).isEqualTo("1003000111");
    assertThat(read.get("PRV-LAST-NAME")).isEqualTo("TESTPROV");
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-2] reading APPINPUT ignores whatever sits in reserved bytes 131-200, as the legacy does")
  void intakeIgnoresReservedContent() {
    byte[] record = bytes(IN_MASTER + "Z".repeat(70));
    assertThat(intake.read(record, 1).get("PRV-NPI")).isEqualTo("1003000111");
  }

  @Test
  @DisplayName("[MAM-42 AC-2] a record written as APPINPUT has spaces in reserved bytes 131-200")
  void intakeWritesReservedAsSpaces() {
    byte[] written = intake.write(Samples.individualRecord(intake));
    assertThat(slice(written, 131, 70)).isEqualTo(RESERVED);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-2] a record written as APPINPUT is the 130-byte master record plus 70 reserved bytes")
  void intakeWritesMasterThenReserved() {
    byte[] written = intake.write(Samples.individualRecord(intake));
    assertThat(written).isEqualTo(bytes(IN_MASTER + RESERVED));
  }
}
