package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.ProviderRows;
import io.cucumber.java.After;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Steps of MAM-37 AC-9 (Q-132): a caller walks all providers of a filter by key (the optional
 * parameter afterNpi), page by page, while other steps change providers between two pages. The walk
 * is kept here; the plain requests and their answers stay in {@link HttpAnswerSteps}.
 */
public class ProviderKeysetWalkSteps {

  /** A walk that does not end after this many answers fails (a service that ignores the key). */
  private static final int MAX_ANSWERS = 20;

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper json;

  private final List<List<String>> pages = new ArrayList<>();
  private String who;
  private String filters;
  private int size;

  @After("@MAM-37 and @AC-9")
  public void removeTheProviders() {
    ProviderRows.wipe(jdbc);
  }

  /** Adds individuals with the NPIs first, first+1 ... (status A, screen flag P, tier L). */
  @Given("{int} ACTIVE providers are stored with consecutive NPIs from {string}")
  public void activeProviders(int count, String first) {
    long start = Long.parseLong(first);
    for (int i = 0; i < count; i++) {
      ProviderRows.individual(String.valueOf(start + i))
          .name("ACTIVE" + (i + 1), "TESTFIRST")
          .status("A")
          .flag("P")
          .riskTier("L")
          .insert(jdbc);
    }
  }

  private List<String> read(String afterNpi) throws Exception {
    String target =
        "/api/v1/providers?"
            + filters
            + "&size="
            + size
            + (afterNpi == null ? "" : "&afterNpi=" + afterNpi);
    MvcResult r =
        mvc.perform(HttpAnswerSteps.request("GET", target).with(HttpAnswerSteps.caller(who)))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    List<String> npis = new ArrayList<>();
    for (JsonNode item : json.readTree(r.getResponse().getContentAsString()).path("items")) {
      npis.add(item.path("npi").asText());
    }
    if (afterNpi != null && !npis.isEmpty()) {
      assertThat(npis.get(0))
          .as("answer " + (pages.size() + 1) + " must start after the key " + afterNpi)
          .isGreaterThan(afterNpi);
    }
    return npis;
  }

  /** The first call of a walk: the filters and the size, no afterNpi. */
  @When("an {string} caller starts a walk by key over {string} with size {int}")
  public void startsWalk(String caller, String filter, int pageSize) throws Exception {
    who = caller;
    filters = filter;
    size = pageSize;
    pages.clear();
    pages.add(read(null));
  }

  /**
   * Each next call repeats the filters and the size, with afterNpi = the last NPI of the answer
   * before.
   */
  @When("the walk continues by key until a page holds fewer than {int} items")
  public void continuesWalk(int fewerThan) throws Exception {
    while (pages.get(pages.size() - 1).size() >= fewerThan) {
      if (pages.size() >= MAX_ANSWERS) {
        fail(
            "the walk by key did not end after "
                + MAX_ANSWERS
                + " answers: afterNpi is probably ignored. Answers so far: "
                + pages.stream().map(List::size).toList());
      }
      List<String> last = pages.get(pages.size() - 1);
      pages.add(read(last.get(last.size() - 1)));
    }
  }

  private List<String> all() {
    return pages.stream().flatMap(List::stream).toList();
  }

  @Then("the walk has {int} pages")
  public void hasPages(int count) {
    assertThat(pages).hasSize(count);
  }

  @Then("the walk lists the providers in ascending NPI order")
  public void ascending() {
    assertThat(all()).isSorted();
  }

  @Then("the walk lists no provider twice")
  public void noDuplicates() {
    assertThat(all()).doesNotHaveDuplicates();
  }

  @Then("the walk lists every NPI from {string} to {string} except {string} exactly once")
  public void everyNpiOnce(String from, String to, String except) {
    for (long npi = Long.parseLong(from); npi <= Long.parseLong(to); npi++) {
      String s = String.valueOf(npi);
      if (!s.equals(except)) {
        assertThat(Collections.frequency(all(), s)).as("times " + s + " is listed").isEqualTo(1);
      }
    }
  }

  @Then("the walk lists {string} at most once")
  public void atMostOnce(String npiList) {
    for (String npi : Arrays.asList(npiList.split(","))) {
      assertThat(Collections.frequency(all(), npi)).as("times " + npi + " is listed").isLessThan(2);
    }
  }

  @Then("page {int} of the walk lists {string}")
  public void pageLists(int n, String npi) {
    assertThat(pages.get(n - 1)).contains(npi);
  }
}
