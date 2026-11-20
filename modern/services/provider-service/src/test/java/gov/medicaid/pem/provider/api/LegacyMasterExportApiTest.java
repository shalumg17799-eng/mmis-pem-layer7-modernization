package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.PemJwt;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * MAM-43 on the real application and database: GET /api/v1/legacy/provider-master as bytes (D-007,
 * ADR-005). The records come out of CHAR and VARCHAR columns, so padding and trimming are checked
 * here, with every enrolment status and a larger table.
 */
class LegacyMasterExportApiTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void clean() {
    ProviderRows.wipe(jdbc);
  }

  private MvcResult export() throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/legacy/provider-master").with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return r;
  }

  private List<String> lines() throws Exception {
    String body =
        new String(export().getResponse().getContentAsByteArray(), StandardCharsets.US_ASCII);
    return body.isEmpty() ? List.of() : Arrays.asList(body.split("\n", -1));
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-1] with no provider stored the export is 200 text/plain with an empty body")
  void emptyMaster() throws Exception {
    MvcResult r = export();

    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
    assertThat(r.getResponse().getContentAsByteArray()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-1] the body is plain ASCII, records are separated by one LF (no CR), and every record is 130 bytes")
  void asciiLinesOf130Bytes() throws Exception {
    ProviderRows.individual("1003000111").due(LocalDate.of(2031, 11, 18)).insert(jdbc);
    ProviderRows.group("1003000444").due(LocalDate.of(2031, 11, 18)).insert(jdbc);

    byte[] body = export().getResponse().getContentAsByteArray();

    assertThat(body).hasSize(130 + 1 + 130);
    assertThat(body[130]).isEqualTo((byte) '\n');
    for (int i = 0; i < body.length; i++) {
      if (i != 130) {
        assertThat(body[i]).as("byte %d", i + 1).isBetween((byte) 0x20, (byte) 0x7e);
      }
    }
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-2] thirty providers stored in scrambled order are exported in ascending NPI order")
  void thirtyProvidersInKeyOrder() throws Exception {
    for (int i = 0; i < 30; i++) {
      int scrambled = (i * 7) % 30;
      ProviderRows.individual(String.format("1005%06d", scrambled))
          .due(LocalDate.of(2031, 11, 18))
          .insert(jdbc);
    }

    List<String> lines = lines();

    assertThat(lines).hasSize(30);
    assertThat(lines).allSatisfy(l -> assertThat(l).hasSize(130));
    List<String> npis = lines.stream().map(l -> l.substring(0, 10)).toList();
    assertThat(npis).isSorted();
    assertThat(npis.get(0)).isEqualTo("1005000000");
    assertThat(npis.get(29)).isEqualTo("1005000029");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-3] a Medicaid ID shorter than its CHAR(9) column and a taxonomy are exported at their positions, padded with spaces")
  void charColumnsKeepTheirPositions() throws Exception {
    ProviderRows.individual("1003000111")
        .medicaidId("MCD1")
        .taxonomy("207Q0")
        .due(LocalDate.of(2031, 11, 18))
        .insert(jdbc);

    String line = lines().get(0);

    assertThat(line.substring(10, 19)).isEqualTo("MCD1     ");
    assertThat(line.substring(19, 21)).isEqualTo("IN");
    assertThat(line.substring(21, 31)).isEqualTo("207Q0     ");
    assertThat(line.substring(31, 34)).isEqualTo(" AP");
  }

  @Test
  @DisplayName("[MAM-43 AC-5] every enrolment status is exported as its code in byte 33")
  void everyStatus() throws Exception {
    String[] statuses = {"P", "A", "T", "D", "S"};
    for (int i = 0; i < statuses.length; i++) {
      ProviderRows.individual("100300030" + i)
          .status(statuses[i])
          .due(LocalDate.of(2031, 11, 18))
          .insert(jdbc);
    }

    List<String> lines = lines();

    assertThat(lines).hasSize(5);
    for (int i = 0; i < statuses.length; i++) {
      assertThat(lines.get(i).substring(32, 33))
          .as(lines.get(i).substring(0, 10))
          .isEqualTo(statuses[i]);
    }
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-5] a terminated provider's end date has no place in the 130-byte record: the dates stay at 35-50")
  void endDateIsNotExported() throws Exception {
    ProviderRows.individual("1003000222")
        .status("T")
        .end(LocalDate.of(2026, 3, 31))
        .due(LocalDate.of(2031, 11, 18))
        .insert(jdbc);

    String line = lines().get(0);

    assertThat(line).hasSize(130).doesNotContain("20260331");
    assertThat(line.substring(34, 42)).isEqualTo("20261118");
    assertThat(line.substring(42, 50)).isEqualTo("20311118");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-6] a provider stored with no risk tier and no due date is exported with a space in byte 32, flag U and 99991231 in bytes 43-50 (Q-124 A)")
  void unsetColumnsUseTheLegacyPadding() throws Exception {
    ProviderRows.individual("1003000222").status("P").flag("U").insert(jdbc);

    String line = lines().get(0);

    assertThat(line).hasSize(130);
    assertThat(line.substring(31, 32)).isEqualTo(" ");
    assertThat(line.substring(32, 33)).isEqualTo("P");
    assertThat(line.substring(33, 34)).isEqualTo("U");
    assertThat(line.substring(34, 42)).isEqualTo("20261118");
    assertThat(line.substring(42, 50)).isEqualTo("99991231");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-6] providers with and without a due date side by side: only the unset one gets 99991231, and every line is read back by the codec")
  void mixedDueDatesAreReadBackByTheCodec() throws Exception {
    ProviderRows.individual("1003000111").due(LocalDate.of(2031, 11, 18)).insert(jdbc);
    ProviderRows.individual("1003000222").status("P").flag("U").insert(jdbc);
    ProviderRows.group("1003000444").insert(jdbc);

    List<String> lines = lines();

    assertThat(lines).hasSize(3);
    FixedWidthCodec codec = FixedWidthCodec.forLayout("PRVRECRD");
    assertThat(
            codec
                .read(lines.get(0).getBytes(StandardCharsets.US_ASCII), 1)
                .getDate("PRV-REVAL-DUE-DATE"))
        .isEqualTo(LocalDate.of(2031, 11, 18));
    assertThat(
            codec
                .read(lines.get(1).getBytes(StandardCharsets.US_ASCII), 2)
                .getDate("PRV-REVAL-DUE-DATE"))
        .isEqualTo(LocalDate.of(9999, 12, 31));
    assertThat(
            codec
                .read(lines.get(2).getBytes(StandardCharsets.US_ASCII), 3)
                .getDate("PRV-REVAL-DUE-DATE"))
        .isEqualTo(LocalDate.of(9999, 12, 31));
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-7] every exported line decodes with the PRVRECRD codec to the values GET /api/v1/providers/{npi} returns, for individuals and organisations")
  void exportDecodesToTheApiValues() throws Exception {
    ProviderRows.individual("1003000111")
        .medicaidId("MCD1")
        .riskTier("L")
        .due(LocalDate.of(2031, 11, 18))
        .insert(jdbc);
    ProviderRows.group("1003000444")
        .status("S")
        .flag("P")
        .riskTier("M")
        .due(LocalDate.of(2031, 12, 31))
        .insert(jdbc);
    ProviderRows.group("1003000777")
        .type("FA")
        .legalName("SAMPLE HOSPITAL 01")
        .dbaName("SAMPLE HOSP")
        .ownerNpi("1990000222")
        .status("T")
        .flag("F")
        .riskTier("H")
        .due(LocalDate.of(2031, 1, 5))
        .insert(jdbc);

    FixedWidthCodec codec = FixedWidthCodec.forLayout("PRVRECRD");
    List<String> lines = lines();

    assertThat(lines).hasSize(3);
    long number = 0;
    for (String line : lines) {
      FixedRecord r = codec.read(line.getBytes(StandardCharsets.US_ASCII), ++number);
      JsonNode p = api(r.get("PRV-NPI"));
      assertThat(r.get("PRV-MEDICAID-ID")).isEqualTo(p.path("medicaidId").asText());
      assertThat(r.get("PRV-TYPE-CODE")).isEqualTo(p.path("providerType").asText());
      assertThat(r.get("PRV-TAXONOMY")).isEqualTo(p.path("taxonomy").asText());
      assertThat(r.get("PRV-RISK-TIER")).isEqualTo(p.path("riskTier").asText());
      assertThat(r.get("PRV-ENROLL-STATUS")).isEqualTo(p.path("enrollStatus").asText());
      assertThat(r.get("PRV-SCREEN-FLAG")).isEqualTo(p.path("screenFlag").asText());
      assertThat(r.getDate("PRV-EFFECTIVE-DATE"))
          .isEqualTo(LocalDate.parse(p.path("effectiveDate").asText()));
      assertThat(r.getDate("PRV-REVAL-DUE-DATE"))
          .isEqualTo(LocalDate.parse(p.path("revalidationDueDate").asText()));
      if ("IN".equals(r.get("PRV-TYPE-CODE"))) {
        assertThat(r.get("PRV-LAST-NAME")).isEqualTo(p.at("/individual/lastName").asText());
        assertThat(r.get("PRV-FIRST-NAME")).isEqualTo(p.at("/individual/firstName").asText());
        assertThat(r.get("PRV-LICENSE-NO")).isEqualTo(p.at("/individual/licenseNo").asText());
        assertThat(r.get("PRV-LICENSE-STATE")).isEqualTo(p.at("/individual/licenseState").asText());
      } else {
        assertThat(r.get("PRV-LEGAL-NAME")).isEqualTo(p.at("/organization/legalName").asText());
        assertThat(r.get("PRV-DBA-NAME")).isEqualTo(p.at("/organization/dbaName").asText());
        assertThat(r.get("PRV-OWNER-NPI")).isEqualTo(p.at("/organization/ownerNpi").asText());
      }
    }
  }

  private JsonNode api(String npi) throws Exception {
    MvcResult r = mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.admin())).andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- AC-8: ADMIN only
  // ---------------------------------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-43 AC-8] {0} is refused with 403 problem+json and sees no provider data")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "SYSTEM"})
  @DisplayName("[MAM-43 AC-8] ANALYST, SUPERVISOR and SYSTEM cannot export the master")
  void otherRolesAreRefused(String role) throws Exception {
    ProviderRows.individual("1003000111").due(LocalDate.of(2031, 11, 18)).insert(jdbc);

    MvcResult r =
        mvc.perform(get("/api/v1/legacy/provider-master").with(PemJwt.user("x", null, role)))
            .andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(r.getResponse().getContentAsString())
        .doesNotContain("1003000111")
        .doesNotContain("RIVERA");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-8] a PROVIDER, even one whose own NPI is in the file, and a user with no PEM role get 403")
  void providerAndRolelessAreRefused() throws Exception {
    ProviderRows.individual("1003000111").due(LocalDate.of(2031, 11, 18)).insert(jdbc);

    mvc.perform(get("/api/v1/legacy/provider-master").with(PemJwt.provider("1003000111")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/legacy/provider-master").with(PemJwt.user("nobody", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("[MAM-43 AC-8] no token gives a 401 problem+json and no data")
  void noTokenIsRefused() throws Exception {
    ProviderRows.individual("1003000111").due(LocalDate.of(2031, 11, 18)).insert(jdbc);

    MvcResult r = mvc.perform(get("/api/v1/legacy/provider-master")).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(r.getResponse().getContentAsString()).doesNotContain("1003000111");
  }

  @Test
  @DisplayName("[MAM-43 AC-8] an ADMIN gets 200 text/plain with the file")
  void adminGetsTheFile() throws Exception {
    ProviderRows.individual("1003000111").due(LocalDate.of(2031, 11, 18)).insert(jdbc);

    MvcResult r = export();

    assertThat(r.getResponse().getContentType()).startsWith("text/plain");
    assertThat(r.getResponse().getContentAsString()).startsWith("1003000111");
  }

  @Test
  @DisplayName(
      "[MAM-43 AC-4] an organisation's names are left-aligned at 51 and 91, with trailing spaces, and the owner NPI is in 121-130")
  void organisationPositions() throws Exception {
    ProviderRows.group("1003000444")
        .type("FA")
        .legalName("SAMPLE HOSPITAL 01")
        .dbaName("SAMPLE HOSP")
        .ownerNpi("1990000222")
        .due(LocalDate.of(2031, 11, 18))
        .insert(jdbc);

    String line = lines().get(0);

    assertThat(line.substring(19, 21)).isEqualTo("FA");
    assertThat(line.substring(50, 90)).isEqualTo("SAMPLE HOSPITAL 01" + " ".repeat(22));
    assertThat(line.substring(90, 120)).isEqualTo("SAMPLE HOSP" + " ".repeat(19));
    assertThat(line.substring(120, 130)).isEqualTo("1990000222");
  }
}
