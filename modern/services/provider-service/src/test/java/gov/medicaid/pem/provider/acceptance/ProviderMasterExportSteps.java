package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.fixedwidth.FixedRecord;
import gov.medicaid.pem.fixedwidth.FixedWidthCodec;
import gov.medicaid.pem.testsupport.PemJwt;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * Steps of MAM-43: GET /api/v1/legacy/provider-master read as bytes, checked field by field against
 * the PRVRECRD positions (data-layouts.md) and through the fixed-width codec.
 */
public class ProviderMasterExportSteps {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper json;

  private MvcResult export;
  private List<String> lines = List.of();

  @When("a(n) {string} caller exports the provider master")
  public void exports(String who) throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/legacy/provider-master");
    RequestPostProcessor token = HttpAnswerSteps.caller(who);
    if (token != null) {
      req = req.with(token);
    }
    export = mvc.perform(req).andReturn();
    String body = new String(export.getResponse().getContentAsByteArray(), StandardCharsets.US_ASCII);
    lines = body.isEmpty() ? List.of() : Arrays.asList(body.split("\n", -1));
  }

  private String line(String npi) {
    List<String> found = lines.stream().filter(l -> l.startsWith(npi)).toList();
    assertThat(found).as("export lines of NPI %s", npi).hasSize(1);
    return found.get(0);
  }

  /** Bytes from to (1-based, inclusive) of the record. */
  private String bytes(String npi, int from, int to) {
    return line(npi).substring(from - 1, to);
  }

  @Then("the export has status {int} and media type {string}")
  public void status(int status, String media) {
    assertThat(export.getResponse().getStatus()).isEqualTo(status);
    assertThat(export.getResponse().getContentType()).startsWith(media);
  }

  @Then("the export has status {int}")
  public void onlyStatus(int status) {
    assertThat(export.getResponse().getStatus()).isEqualTo(status);
  }

  @Then("the export has exactly {int} lines")
  public void lineCount(int count) {
    assertThat(lines).hasSize(count);
  }

  @Then("every export line is exactly {int} characters long")
  public void everyLine(int length) {
    assertThat(lines).isNotEmpty();
    assertThat(lines).allSatisfy(l -> assertThat(l).hasSize(length));
  }

  /** The lines start with the NPIs in that order; a header or trailer line would add a line. */
  @Then("the export lines are for the NPIs {string} in that order")
  public void order(String npis) {
    List<String> want = Arrays.asList(npis.split(","));
    assertThat(lines.stream().map(l -> l.substring(0, 10)).toList()).containsExactlyElementsOf(want);
  }

  @Then("the export does not end with a line break")
  public void noTrailingBreak() {
    String body = new String(export.getResponse().getContentAsByteArray(), StandardCharsets.US_ASCII);
    assertThat(body).doesNotEndWith("\n").doesNotEndWith("\r");
  }

  @Then("the line of NPI {string} has {string} in bytes {int} to {int}")
  public void exactly(String npi, String expected, int from, int to) {
    assertThat(expected).as("expected text has the width of the field").hasSize(to - from + 1);
    assertThat(bytes(npi, from, to)).isEqualTo(expected);
  }

  @Then("the line of NPI {string} has {string} space-padded in bytes {int} to {int}")
  public void padded(String npi, String text, int from, int to) {
    int width = to - from + 1;
    assertThat(text.length()).as("text fits the field").isLessThanOrEqualTo(width);
    assertThat(bytes(npi, from, to)).isEqualTo(text + " ".repeat(width - text.length()));
  }

  @Then("the line of NPI {string} has only spaces in bytes {int} to {int}")
  public void spaces(String npi, int from, int to) {
    assertThat(bytes(npi, from, to)).isEqualTo(" ".repeat(to - from + 1));
  }

  @Then("every export line decodes with the PRVRECRD codec to the fields the API returns for that NPI")
  public void decodes() throws Exception {
    FixedWidthCodec codec = FixedWidthCodec.forLayout("PRVRECRD");
    assertThat(lines).isNotEmpty();
    long number = 0;
    for (String l : lines) {
      number++;
      FixedRecord r = codec.read(l.getBytes(StandardCharsets.US_ASCII), number);
      String npi = r.get("PRV-NPI");
      MvcResult api =
          mvc.perform(get("/api/v1/providers/{npi}", npi).with(PemJwt.admin())).andReturn();
      assertThat(api.getResponse().getStatus()).isEqualTo(200);
      JsonNode p = json.readTree(api.getResponse().getContentAsString());
      assertThat(r.get("PRV-MEDICAID-ID")).as("medicaidId").isEqualTo(p.path("medicaidId").asText());
      assertThat(r.get("PRV-TYPE-CODE")).as("type").isEqualTo(p.path("providerType").asText());
      assertThat(r.get("PRV-TAXONOMY")).as("taxonomy").isEqualTo(p.path("taxonomy").asText());
      assertThat(r.get("PRV-RISK-TIER")).as("tier").isEqualTo(p.path("riskTier").asText(""));
      assertThat(r.get("PRV-ENROLL-STATUS")).as("status").isEqualTo(p.path("enrollStatus").asText());
      assertThat(r.get("PRV-SCREEN-FLAG")).as("flag").isEqualTo(p.path("screenFlag").asText());
      assertThat(r.getDate("PRV-EFFECTIVE-DATE"))
          .as("effective date")
          .isEqualTo(LocalDate.parse(p.path("effectiveDate").asText()));
      assertThat(r.getDate("PRV-REVAL-DUE-DATE"))
          .as("due date")
          .isEqualTo(LocalDate.parse(p.path("revalidationDueDate").asText()));
      if ("IN".equals(r.get("PRV-TYPE-CODE"))) {
        JsonNode i = p.path("individual");
        assertThat(r.get("PRV-LAST-NAME")).isEqualTo(i.path("lastName").asText());
        assertThat(r.get("PRV-FIRST-NAME")).isEqualTo(i.path("firstName").asText());
        assertThat(r.get("PRV-LICENSE-NO")).isEqualTo(i.path("licenseNo").asText());
        assertThat(r.get("PRV-LICENSE-STATE")).isEqualTo(i.path("licenseState").asText());
      } else {
        JsonNode o = p.path("organization");
        assertThat(r.get("PRV-LEGAL-NAME")).isEqualTo(o.path("legalName").asText());
        assertThat(r.get("PRV-DBA-NAME")).isEqualTo(o.path("dbaName").asText());
        assertThat(r.get("PRV-OWNER-NPI")).isEqualTo(o.path("ownerNpi").asText());
      }
    }
  }
}
