package gov.medicaid.pem.fixedwidth;

import static gov.medicaid.pem.fixedwidth.Samples.FA_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.GR_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.IN_MASTER;
import static gov.medicaid.pem.fixedwidth.Samples.bytes;
import static gov.medicaid.pem.fixedwidth.Samples.slice;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * MAM-42 AC-3: the 80-byte PRV-VARIANT (bytes 51-130) is read through the view the provider type
 * selects: IN gives the individual view, GR and FA the organisation view (REDEFINES discriminator,
 * D-008).
 */
class ProviderVariantTest {

  private final FixedWidthCodec codec = FixedWidthCodec.forLayout("PRVRECRD");

  @Test
  @DisplayName(
      "[MAM-42 AC-3] the provider type code (PRV-TYPE-CODE) is the discriminator that picks the view")
  void typeCodeIsTheDiscriminator() {
    assertThat(codec.layout().discriminator()).isEqualTo("PRV-TYPE-CODE");
  }

  @ParameterizedTest(name = "{0} is bytes {1}, {2} long, for type IN")
  @CsvSource({
    "PRV-LAST-NAME,51,30",
    "PRV-FIRST-NAME,81,20",
    "PRV-LICENSE-NO,101,15",
    "PRV-LICENSE-STATE,116,2"
  })
  @DisplayName(
      "[MAM-42 AC-3] the individual view has last name, first name, licence number and state at their bytes")
  void individualViewPositions(String field, int start, int length) {
    FieldDef def = codec.layout().field(field);
    assertThat(def.start()).isEqualTo(start);
    assertThat(def.length()).isEqualTo(length);
    assertThat(def.views()).containsExactly("IN");
  }

  @Test
  @DisplayName("[MAM-42 AC-3] the individual view ends with 13 bytes of filler at byte 118")
  void individualViewHasThirteenBytesOfFiller() {
    List<FieldDef> fillers =
        codec.layout().fields().stream()
            .filter(f -> f.name().startsWith("FILLER") && f.views().contains("IN"))
            .toList();
    assertThat(fillers).hasSize(1);
    assertThat(fillers.get(0).start()).isEqualTo(118);
    assertThat(fillers.get(0).length()).isEqualTo(13);
  }

  @ParameterizedTest(name = "{0} is bytes {1}, {2} long, for types GR and FA")
  @CsvSource({"PRV-LEGAL-NAME,51,40", "PRV-DBA-NAME,91,30", "PRV-OWNER-NPI,121,10"})
  @DisplayName(
      "[MAM-42 AC-3] the organisation view has legal name, DBA name and owner NPI at their bytes")
  void organisationViewPositions(String field, int start, int length) {
    FieldDef def = codec.layout().field(field);
    assertThat(def.start()).isEqualTo(start);
    assertThat(def.length()).isEqualTo(length);
    assertThat(def.views()).isEqualTo(Set.of("GR", "FA"));
  }

  @ParameterizedTest(name = "type {0}: the view's fields fill bytes 51-130 with no gap or overlap")
  @ValueSource(strings = {"IN", "GR", "FA"})
  @DisplayName("[MAM-42 AC-3] each view covers the 80 variant bytes exactly once")
  void everyViewCoversTheVariant(String type) {
    List<FieldDef> view =
        codec.layout().fields().stream()
            .filter(f -> f.views().contains(type))
            .sorted(Comparator.comparingInt(FieldDef::start))
            .toList();
    int nextByte = 51;
    for (FieldDef field : view) {
      assertThat(field.start()).as("start of " + field.name()).isEqualTo(nextByte);
      nextByte = field.start() + field.length();
    }
    assertThat(nextByte - 1).as("last byte of the " + type + " view").isEqualTo(130);
  }

  @ParameterizedTest(name = "individual field {0} reads {1}")
  @CsvSource({
    "PRV-LAST-NAME,TESTPROV",
    "PRV-FIRST-NAME,ALPHA",
    "PRV-LICENSE-NO,MD12345",
    "PRV-LICENSE-STATE,TX"
  })
  @DisplayName("[MAM-42 AC-3] a record of type IN reads the individual view")
  void individualValues(String field, String expected) {
    assertThat(codec.read(bytes(IN_MASTER), 1).get(field)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "group field {0} reads {1}")
  @CsvSource({
    "PRV-LEGAL-NAME,SAMPLE CLINIC 01",
    "PRV-DBA-NAME,SAMPLE CLINIC DBA",
    "PRV-OWNER-NPI,1990000111"
  })
  @DisplayName("[MAM-42 AC-3] a record of type GR reads the organisation view")
  void groupValues(String field, String expected) {
    assertThat(codec.read(bytes(GR_MASTER), 1).get(field)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "facility field {0} reads {1}")
  @CsvSource({"PRV-LEGAL-NAME,SAMPLE HOSPITAL 01", "PRV-DBA-NAME,''", "PRV-OWNER-NPI,1990000999"})
  @DisplayName("[MAM-42 AC-3] a record of type FA reads the organisation view, as GR does")
  void facilityValues(String field, String expected) {
    assertThat(codec.read(bytes(FA_MASTER), 1).get(field)).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-3] an individual record has no legal name: asking for it is an error, not the bytes reinterpreted")
  void individualRecordHasNoOrganisationFields() {
    FixedRecord individual = codec.read(bytes(IN_MASTER), 1);
    assertThatThrownBy(() -> individual.get("PRV-LEGAL-NAME"))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.field()).isEqualTo("PRV-LEGAL-NAME"));
  }

  @ParameterizedTest(name = "a {0} record has no last name")
  @ValueSource(strings = {"GR", "FA"})
  @DisplayName(
      "[MAM-42 AC-3] a group or facility record has no individual fields: asking for one is an error")
  void organisationRecordHasNoIndividualFields(String type) {
    String record = type.equals("GR") ? GR_MASTER : FA_MASTER;
    FixedRecord organisation = codec.read(bytes(record), 1);
    assertThatThrownBy(() -> organisation.get("PRV-LAST-NAME"))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.field()).isEqualTo("PRV-LAST-NAME"));
  }

  @ParameterizedTest(name = "type code ''{0}'' reads, but no view is chosen")
  @ValueSource(strings = {"XX", "  ", "in"})
  @DisplayName(
      "[MAM-42 AC-3] an unknown type code still reads (business validation is the service's, E102), but no view is guessed")
  void unknownTypeHasNoView(String type) {
    FixedRecord record = codec.read(Samples.overwrite(IN_MASTER, 20, type), 1);
    assertThat(record.get("PRV-TYPE-CODE")).isEqualTo(type.trim());
    assertThatThrownBy(() -> record.get("PRV-LAST-NAME")).isInstanceOf(FixedWidthException.class);
  }

  @Test
  @DisplayName("[MAM-42 AC-3] the raw 80-byte PRV-VARIANT can always be read, whatever the type")
  void rawVariantIsAlwaysReadable() {
    FixedRecord record = codec.read(Samples.overwrite(IN_MASTER, 20, "XX"), 1);
    assertThat(record.get("PRV-VARIANT")).startsWith("TESTPROV").endsWith("TX");
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-3] writing a type IN record puts last name at byte 51, first name at 81, licence at 101 and state at 116")
  void individualIsWrittenByView() {
    byte[] written = codec.write(Samples.individualRecord(codec));
    assertThat(slice(written, 51, 80)).isEqualTo(Samples.IN_VARIANT);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-3] writing a type GR record puts legal name at byte 51, DBA name at 91 and owner NPI at 121")
  void groupIsWrittenByView() {
    byte[] written = codec.write(Samples.groupRecord(codec));
    assertThat(slice(written, 51, 80)).isEqualTo(Samples.GR_VARIANT);
  }

  @Test
  @DisplayName(
      "[MAM-42 AC-3] setting a field of the other view is rejected, not written over the same bytes")
  void otherViewIsNotWritable() {
    FixedRecord record = codec.newRecord();
    record.set("PRV-TYPE-CODE", "IN");
    assertThatThrownBy(() -> record.set("PRV-LEGAL-NAME", "SAMPLE CLINIC 01"))
        .isInstanceOfSatisfying(
            FixedWidthException.class, ex -> assertThat(ex.field()).isEqualTo("PRV-LEGAL-NAME"));
  }
}
