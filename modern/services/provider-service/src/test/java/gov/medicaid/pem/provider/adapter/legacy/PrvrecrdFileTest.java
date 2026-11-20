package gov.medicaid.pem.provider.adapter.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.provider.domain.EnrollStatus;
import gov.medicaid.pem.provider.domain.Provider;
import gov.medicaid.pem.provider.domain.ProviderType;
import gov.medicaid.pem.provider.domain.RiskTier;
import gov.medicaid.pem.provider.domain.ScreenFlag;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * MAM-43: the 130-byte PRVRECRD record written from a provider, byte by byte (data-layouts.md: NPI
 * 1-10, Medicaid ID 11-19, type 20-21, taxonomy 22-31, tier 32, status 33, screen flag 34,
 * effective date 35-42, due date 43-50, then the variant 51-130: IN last name 51-80, first name
 * 81-100, licence 101-115, state 116-117, filler 118-130; GR/FA legal name 51-90, DBA name 91-120,
 * owner NPI 121-130).
 */
class PrvrecrdFileTest {

  final PrvrecrdFile file = new PrvrecrdFile();

  static Provider individual() {
    return new Provider(
        "1003000111",
        "MCD000111",
        ProviderType.INDIVIDUAL,
        "207Q00000X",
        RiskTier.LIMITED,
        EnrollStatus.ACTIVE,
        ScreenFlag.PASS,
        LocalDate.of(2026, 11, 18),
        null,
        LocalDate.of(2031, 11, 18),
        "RIVERA",
        "MARIA",
        "MD12345",
        "TX",
        null,
        null,
        null,
        3);
  }

  static Provider organisation(ProviderType type, String legal, String dba, String owner) {
    return new Provider(
        "1003000444",
        "MCD000444",
        type,
        "261QM0801X",
        RiskTier.MODERATE,
        EnrollStatus.ACTIVE,
        ScreenFlag.PASS,
        LocalDate.of(2026, 11, 18),
        null,
        LocalDate.of(2031, 11, 18),
        null,
        null,
        null,
        null,
        legal,
        dba,
        owner,
        1);
  }

  static String pad(String s, int width) {
    return s + " ".repeat(width - s.length());
  }

  /** Bytes from..to of the record, 1-based and inclusive. */
  static String bytes(String record, int from, int to) {
    return record.substring(from - 1, to);
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-3] an individual record is exactly the PRVRECRD layout, field by field, 130 bytes")
  void individualRecord() {
    String expected =
        "1003000111"
            + "MCD000111"
            + "IN"
            + "207Q00000X"
            + "L"
            + "A"
            + "P"
            + "20261118"
            + "20311118"
            + pad("RIVERA", 30)
            + pad("MARIA", 20)
            + pad("MD12345", 15)
            + "TX"
            + " ".repeat(13);

    String written = file.write(List.of(individual()));

    assertThat(expected).hasSize(130);
    assertThat(written).isEqualTo(expected);
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-4] a group record has the legal name, the DBA name and the owner NPI in 121-130")
  void groupRecord() {
    String written =
        file.write(
            List.of(
                organisation(
                    ProviderType.GROUP, "EXCLUDED CLINIC LLC", "EXCLUDED CLINIC", "1990000111")));

    assertThat(written).hasSize(130);
    assertThat(bytes(written, 20, 21)).isEqualTo("GR");
    assertThat(bytes(written, 51, 90)).isEqualTo(pad("EXCLUDED CLINIC LLC", 40));
    assertThat(bytes(written, 91, 120)).isEqualTo(pad("EXCLUDED CLINIC", 30));
    assertThat(bytes(written, 121, 130)).isEqualTo("1990000111");
  }

  @Test
  @DisplayName("[MAM-43 AC-4] a facility record is written like a group record")
  void facilityRecord() {
    String written =
        file.write(
            List.of(
                organisation(
                    ProviderType.FACILITY, "SAMPLE HOSPITAL 01", "SAMPLE HOSP", "1990000222")));

    assertThat(bytes(written, 20, 21)).isEqualTo("FA");
    assertThat(bytes(written, 51, 90)).isEqualTo(pad("SAMPLE HOSPITAL 01", 40));
    assertThat(bytes(written, 91, 120)).isEqualTo(pad("SAMPLE HOSP", 30));
    assertThat(bytes(written, 121, 130)).isEqualTo("1990000222");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-4] a legal name longer than 40 characters is cut to its first 40, a DBA name to 30, and the owner NPI stays whole")
  void longNamesAreCutOnTheRight() {
    String legal = "NORTHWEST REGIONAL COMMUNITY HEALTH ALLIANCE LLC";
    String dba = "NORTHWEST REGIONAL COMMUNITY HEALTH";
    String written =
        file.write(List.of(organisation(ProviderType.GROUP, legal, dba, "1990000111")));

    assertThat(written).hasSize(130);
    assertThat(bytes(written, 51, 90)).isEqualTo(legal.substring(0, 40));
    assertThat(bytes(written, 91, 120)).isEqualTo(dba.substring(0, 30));
    assertThat(bytes(written, 121, 130)).isEqualTo("1990000111");
  }

  @Test
  @DisplayName("[MAM-43 AC-4] a name of exactly the field width is written whole")
  void nameOfExactWidth() {
    String legal = "L".repeat(40);
    String dba = "D".repeat(30);
    String written =
        file.write(List.of(organisation(ProviderType.GROUP, legal, dba, "1990000111")));

    assertThat(bytes(written, 51, 90)).isEqualTo(legal);
    assertThat(bytes(written, 91, 120)).isEqualTo(dba);
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-3] an individual's last name of 30, first name of 20, licence of 15 characters fill their fields; longer ones are cut")
  void individualNamesAtAndOverTheLimit() {
    Provider p = individual();
    Provider longNames =
        new Provider(
            p.npi(),
            p.medicaidId(),
            p.providerType(),
            p.taxonomy(),
            p.riskTier(),
            p.enrollStatus(),
            p.screenFlag(),
            p.effectiveDate(),
            null,
            p.revalidationDueDate(),
            "L".repeat(31),
            "F".repeat(21),
            "N".repeat(16),
            "TX",
            null,
            null,
            null,
            0);

    String written = file.write(List.of(longNames));

    assertThat(bytes(written, 51, 80)).isEqualTo("L".repeat(30));
    assertThat(bytes(written, 81, 100)).isEqualTo("F".repeat(20));
    assertThat(bytes(written, 101, 115)).isEqualTo("N".repeat(15));
    assertThat(bytes(written, 116, 117)).isEqualTo("TX");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-4] the view of the type decides: a group never shows individual names, an individual never shows an organisation")
  void onlyTheViewOfTheTypeIsWritten() {
    Provider g =
        organisation(ProviderType.GROUP, "SAMPLE CLINIC GROUP", "SAMPLE CLINIC", "1990000111");
    Provider groupWithNames =
        new Provider(
            g.npi(),
            g.medicaidId(),
            g.providerType(),
            g.taxonomy(),
            g.riskTier(),
            g.enrollStatus(),
            g.screenFlag(),
            g.effectiveDate(),
            null,
            g.revalidationDueDate(),
            "SHOULDNOTSHOW",
            "NOR",
            "THIS",
            "ZZ",
            g.legalName(),
            g.dbaName(),
            g.ownerNpi(),
            0);
    Provider i = individual();
    Provider individualWithOrganisation =
        new Provider(
            i.npi(),
            i.medicaidId(),
            i.providerType(),
            i.taxonomy(),
            i.riskTier(),
            i.enrollStatus(),
            i.screenFlag(),
            i.effectiveDate(),
            null,
            i.revalidationDueDate(),
            i.lastName(),
            i.firstName(),
            i.licenseNo(),
            i.licenseState(),
            "SHOULDNOTSHOW",
            "NOR",
            "1990000999",
            0);

    String group = file.write(List.of(groupWithNames));
    String individual = file.write(List.of(individualWithOrganisation));

    assertThat(group).doesNotContain("SHOULDNOTSHOW").doesNotContain("ZZ");
    assertThat(bytes(group, 51, 90)).isEqualTo(pad("SAMPLE CLINIC GROUP", 40));
    assertThat(individual).doesNotContain("SHOULDNOTSHOW").doesNotContain("1990000999");
    assertThat(bytes(individual, 118, 130)).isEqualTo(" ".repeat(13));
  }

  @ParameterizedTest(name = "status {0} is written as byte 33 {1}")
  @EnumSource(EnrollStatus.class)
  @DisplayName(
      "[MAM-43 AC-5] every enrolment status is written as its code, S for a suspended provider")
  void everyStatus(EnrollStatus status) {
    Provider p = individual().withEnrollStatus(status);

    String written = file.write(List.of(p));

    assertThat(bytes(written, 33, 33)).isEqualTo(status.code());
  }

  @Test
  @DisplayName("[MAM-43 AC-5] a suspended provider has S in byte 33, not D")
  void suspendedIsS() {
    String written = file.write(List.of(individual().withEnrollStatus(EnrollStatus.SUSPENDED)));

    assertThat(bytes(written, 33, 33)).isEqualTo("S");
  }

  @ParameterizedTest(name = "screen flag {0} is written as byte 34")
  @EnumSource(ScreenFlag.class)
  @DisplayName("[MAM-43 AC-3] every screen flag is written as its code in byte 34")
  void everyScreenFlag(ScreenFlag flag) {
    Provider i = individual();
    Provider p =
        new Provider(
            i.npi(),
            i.medicaidId(),
            i.providerType(),
            i.taxonomy(),
            i.riskTier(),
            i.enrollStatus(),
            flag,
            i.effectiveDate(),
            null,
            i.revalidationDueDate(),
            i.lastName(),
            i.firstName(),
            i.licenseNo(),
            i.licenseState(),
            null,
            null,
            null,
            0);

    assertThat(bytes(file.write(List.of(p)), 34, 34)).isEqualTo(flag.code());
  }

  @ParameterizedTest(name = "risk tier {0} is written as byte 32")
  @EnumSource(RiskTier.class)
  @DisplayName("[MAM-43 AC-3] every risk tier is written as its code in byte 32")
  void everyTier(RiskTier tier) {
    Provider i = individual();
    Provider p =
        new Provider(
            i.npi(),
            i.medicaidId(),
            i.providerType(),
            i.taxonomy(),
            tier,
            i.enrollStatus(),
            i.screenFlag(),
            i.effectiveDate(),
            null,
            i.revalidationDueDate(),
            i.lastName(),
            i.firstName(),
            i.licenseNo(),
            i.licenseState(),
            null,
            null,
            null,
            0);

    assertThat(bytes(file.write(List.of(p)), 32, 32)).isEqualTo(tier.code());
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-6] an unset risk tier is a space in byte 32 and an unset due date is 00000000; the screen flag U stays")
  void unsetValuesUseTheLegacyPadding() {
    Provider i = individual();
    Provider pending =
        new Provider(
            "1003000222",
            "MCD000222",
            ProviderType.INDIVIDUAL,
            "207Q00000X",
            null,
            EnrollStatus.PENDING,
            ScreenFlag.UNSCREENED,
            LocalDate.of(2026, 11, 18),
            null,
            null,
            i.lastName(),
            i.firstName(),
            i.licenseNo(),
            i.licenseState(),
            null,
            null,
            null,
            0);

    String written = file.write(List.of(pending));

    assertThat(written).hasSize(130);
    assertThat(bytes(written, 32, 32)).isEqualTo(" ");
    assertThat(bytes(written, 33, 33)).isEqualTo("P");
    assertThat(bytes(written, 34, 34)).isEqualTo("U");
    assertThat(bytes(written, 35, 42)).isEqualTo("20261118");
    assertThat(bytes(written, 43, 50)).isEqualTo("00000000");
  }

  @Test
  @DisplayName("[MAM-43 AC-3] dates are CCYYMMDD with a 4-digit year and zero-filled month and day")
  void datesHaveFourDigitYears() {
    Provider i = individual();
    Provider p =
        new Provider(
            i.npi(),
            i.medicaidId(),
            i.providerType(),
            i.taxonomy(),
            i.riskTier(),
            i.enrollStatus(),
            i.screenFlag(),
            LocalDate.of(2026, 1, 5),
            null,
            LocalDate.of(2031, 12, 31),
            i.lastName(),
            i.firstName(),
            i.licenseNo(),
            i.licenseState(),
            null,
            null,
            null,
            0);

    String written = file.write(List.of(p));

    assertThat(bytes(written, 35, 42)).isEqualTo("20260105");
    assertThat(bytes(written, 43, 50)).isEqualTo("20311231");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-1] a Medicaid ID shorter than 9 is space-padded and the record stays 130 bytes")
  void shortMedicaidIdIsPadded() {
    Provider i = individual();
    Provider p =
        new Provider(
            i.npi(),
            "MCD1",
            i.providerType(),
            i.taxonomy(),
            i.riskTier(),
            i.enrollStatus(),
            i.screenFlag(),
            i.effectiveDate(),
            null,
            i.revalidationDueDate(),
            i.lastName(),
            i.firstName(),
            i.licenseNo(),
            i.licenseState(),
            null,
            null,
            null,
            0);

    String written = file.write(List.of(p));

    assertThat(written).hasSize(130);
    assertThat(bytes(written, 11, 19)).isEqualTo("MCD1     ");
    assertThat(bytes(written, 20, 21)).isEqualTo("IN");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-2] records are joined by one line feed, in the order given, with no header and no trailer")
  void recordsJoinedByLineFeed() {
    Provider a = individual();
    Provider b =
        organisation(ProviderType.GROUP, "EXCLUDED CLINIC LLC", "EXCLUDED CLINIC", "1990000111");

    String written = file.write(List.of(a, b));

    String[] lines = written.split("\n", -1);
    assertThat(lines).hasSize(2);
    assertThat(lines[0]).hasSize(130).startsWith("1003000111");
    assertThat(lines[1]).hasSize(130).startsWith("1003000444");
    assertThat(written).hasSize(130 + 1 + 130);
    assertThat(written).doesNotContain("\r").doesNotEndWith("\n").doesNotStartWith("\n");
  }

  @Test
  @DisplayName("[MAM-43 AC-1] no provider gives an empty text, not a header")
  void emptyList() {
    assertThat(file.write(List.of())).isEmpty();
  }

  @Test
  @DisplayName("[MAM-43 AC-7] what is written decodes with the PRVRECRD codec to the same values")
  void decodesWithTheCodec() {
    String written = file.write(List.of(individual()));

    FixedRecord r =
        FixedWidthCodec.forLayout("PRVRECRD").read(written.getBytes(StandardCharsets.US_ASCII), 1);

    assertThat(r.get("PRV-NPI")).isEqualTo("1003000111");
    assertThat(r.get("PRV-MEDICAID-ID")).isEqualTo("MCD000111");
    assertThat(r.get("PRV-TYPE-CODE")).isEqualTo("IN");
    assertThat(r.get("PRV-TAXONOMY")).isEqualTo("207Q00000X");
    assertThat(r.get("PRV-RISK-TIER")).isEqualTo("L");
    assertThat(r.get("PRV-ENROLL-STATUS")).isEqualTo("A");
    assertThat(r.get("PRV-SCREEN-FLAG")).isEqualTo("P");
    assertThat(r.getDate("PRV-EFFECTIVE-DATE")).isEqualTo(LocalDate.of(2026, 11, 18));
    assertThat(r.getDate("PRV-REVAL-DUE-DATE")).isEqualTo(LocalDate.of(2031, 11, 18));
    assertThat(r.get("PRV-LAST-NAME")).isEqualTo("RIVERA");
    assertThat(r.get("PRV-FIRST-NAME")).isEqualTo("MARIA");
    assertThat(r.get("PRV-LICENSE-NO")).isEqualTo("MD12345");
    assertThat(r.get("PRV-LICENSE-STATE")).isEqualTo("TX");
  }
}
