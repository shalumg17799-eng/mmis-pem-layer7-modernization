package gov.medicaid.pem.provider.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** Steps of MAM-37 that read a page of providers (requests and fields: HttpAnswerSteps). */
public class ProviderSearchSteps {

  @Autowired private HttpAnswerSteps http;

  private List<String> npis(int n) throws Exception {
    List<String> out = new ArrayList<>();
    for (JsonNode item : http.body(n).path("items")) {
      out.add(item.path("npi").asText());
    }
    return out;
  }

  /** An empty list of NPIs ("") means an empty items array. */
  @Then("answer {int} lists exactly the providers {string} with total {int}")
  public void listsExactly(int n, String expected, int total) throws Exception {
    List<String> want = expected.isBlank() ? List.of() : Arrays.asList(expected.split(","));
    assertThat(http.body(n).path("items").isArray()).isTrue();
    assertThat(npis(n)).containsExactlyElementsOf(want);
    assertThat(http.body(n).path("total").asInt()).isEqualTo(total);
  }

  @Then("answer {int} has {int} items, page {int}, size {int} and total {int}")
  public void hasPage(int n, int items, int page, int size, int total) throws Exception {
    JsonNode b = http.body(n);
    assertThat(b.path("items").size()).isEqualTo(items);
    assertThat(b.path("page").asInt()).isEqualTo(page);
    assertThat(b.path("size").asInt()).isEqualTo(size);
    assertThat(b.path("total").asInt()).isEqualTo(total);
  }

  @Then("answer {int} lists the providers {string} in that order")
  public void listsInOrder(int n, String expected) throws Exception {
    assertThat(npis(n)).containsExactlyElementsOf(Arrays.asList(expected.split(",")));
  }

  @Then("answer {int} lists only providers whose name contains {string}")
  public void namesContain(int n, String text) throws Exception {
    assertThat(http.body(n).path("items").size()).isPositive();
    for (JsonNode item : http.body(n).path("items")) {
      String names = item.path("individual").toString() + item.path("organization").toString();
      assertThat(names.toUpperCase()).contains(text.toUpperCase());
    }
  }
}
