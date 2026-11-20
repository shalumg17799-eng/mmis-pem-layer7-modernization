package gov.medicaid.pem.provider.api;

import static gov.medicaid.pem.provider.api.KeysetFixtures.npi;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-37 AC-9 (Q-132, contract 1.2.0) on the real application and database: one answer of
 * searchProviders with the optional keyset parameter afterNpi. The answer holds only providers
 * whose NPI is greater than the key, in ascending NPI order, at most size of them; every other
 * filter combines with it; total still counts all matches; a key that is not exactly 10 digits, a
 * repeated key and a key with a page other than 0 are 400 problems; a call without the key answers
 * as it did before. The whole walks and the concurrent-change cases are in {@link
 * ProviderKeysetWalkTest}.
 *
 * <p>The data is {@link KeysetFixtures#eight}: 111 P/P, 222 T/P, 333 A/P, 444 P/F, 555 A/P, 666
 * A/F, 777 A/P, 888 A/P (the last three digits of 1003000xxx; status/screen flag).
 */
class ProviderKeysetSearchApiTest extends IntegrationTest {

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    KeysetFixtures.eight(jdbc);
  }

  // ---- helpers
  // ------------------------------------------------------------------------------------

  private MvcResult searchAs(RequestPostProcessor who, String... paramsAsNameValue)
      throws Exception {
    MockHttpServletRequestBuilder req = get("/api/v1/providers");
    if (who != null) {
      req = req.with(who);
    }
    for (int i = 0; i < paramsAsNameValue.length; i += 2) {
      req = req.param(paramsAsNameValue[i], paramsAsNameValue[i + 1]);
    }
    return mvc.perform(req).andReturn();
  }

  private MvcResult search(String... paramsAsNameValue) throws Exception {
    return searchAs(PemJwt.analyst(), paramsAsNameValue);
  }

  /**
   * A query like "status=A&amp;name=RIVERA" (or "-" for none) followed by more name/value pairs.
   */
  private MvcResult searchQuery(String query, String... more) throws Exception {
    List<String> pairs = new ArrayList<>();
    if (query != null && !query.isBlank() && !"-".equals(query.trim())) {
      for (String pair : query.trim().split("&")) {
        int eq = pair.indexOf('=');
        pairs.add(pair.substring(0, eq));
        pairs.add(pair.substring(eq + 1));
      }
    }
    pairs.addAll(List.of(more));
    return search(pairs.toArray(String[]::new));
  }

  private JsonNode ok(MvcResult r) throws Exception {
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("npi").asText()));
    return out;
  }

  /** "444 555" is 1003000444 and 1003000555; blank or null is the empty list. */
  private static List<String> full(String threeDigitList) {
    if (threeDigitList == null || threeDigitList.isBlank()) {
      return List.of();
    }
    return Stream.of(threeDigitList.trim().split("\\s+")).map(s -> npi(s)).toList();
  }

  private void assertRefused(MvcResult r, String what) throws Exception {
    String body = r.getResponse().getContentAsString();
    assertThat(r.getResponse().getStatus()).as(what + ": " + body).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).as(what).startsWith("application/problem+json");
    JsonNode problem = json.readTree(body);
    assertThat(problem.path("status").asInt()).as(what).isEqualTo(400);
    assertThat(problem.path("detail").asText()).as(what + ": the problem says why").isNotBlank();
    assertThat(problem.has("items")).as(what + ": nothing is listed").isFalse();
  }

  // ---- the key: only greater NPIs, in order
  // -------------------------------------------------------

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi={0} lists {1}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          0000000000 | 111 222 333 444 555 666 777 888
          1003000110 | 111 222 333 444 555 666 777 888
          1003000111 | 222 333 444 555 666 777 888
          1003000112 | 222 333 444 555 666 777 888
          1003000443 | 444 555 666 777 888
          1003000444 | 555 666 777 888
          1003000445 | 555 666 777 888
          1003000777 | 888
          1003000887 | 888
          1003000888 |
          1003000889 |
          9999999999 |
          """)
  @DisplayName(
      "[MAM-37 AC-9] afterNpi lists only the providers whose NPI is greater than the key, in ascending NPI order; the key itself is not listed and need not be on file")
  void keyListsOnlyGreaterNpisInOrder(String key, String expected) throws Exception {
    JsonNode page = ok(search("afterNpi", key));

    assertThat(npis(page)).containsExactlyElementsOf(full(expected));
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi=1003000111 with size={0} lists {1}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          1   | 222
          3   | 222 333 444
          6   | 222 333 444 555 666 777
          7   | 222 333 444 555 666 777 888
          8   | 222 333 444 555 666 777 888
          200 | 222 333 444 555 666 777 888
          """)
  @DisplayName(
      "[MAM-37 AC-9] with a key the answer holds at most size providers, the first ones after the key, and says its size")
  void sizeCutsTheAnswer(int size, String expected) throws Exception {
    JsonNode page = ok(search("afterNpi", "1003000111", "size", String.valueOf(size)));

    assertThat(npis(page)).containsExactlyElementsOf(full(expected));
    assertThat(page.path("size").asInt()).isEqualTo(size);
  }

  @Test
  @DisplayName("[MAM-37 AC-9] with a key and no size the answer has the default size 20")
  void defaultSizeWithAKey() throws Exception {
    JsonNode page = ok(search("afterNpi", "1003000444"));

    assertThat(page.path("size").asInt()).isEqualTo(20);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi with size={0} is refused")
  @ValueSource(strings = {"0", "201", "-1", "abc"})
  @DisplayName(
      "[MAM-37 AC-9] the size limits 1 to 200 also hold with a key: anything else is a 400")
  void sizeLimitsHoldWithAKey(String size) throws Exception {
    assertRefused(search("afterNpi", "1003000111", "size", size), "size=" + size);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] {0} with afterNpi={1} lists {2}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          status=A               | 1003000333 | 555 666 777 888
          status=A               | 1003000444 | 555 666 777 888
          status=A&screenFlag=P  | 1003000333 | 555 777 888
          status=A&screenFlag=P  | 1003000000 | 333 555 777 888
          status=P               | 1003000111 | 444
          status=T               | 1003000111 | 222
          status=T               | 1003000222 |
          status=S               | 1003000000 |
          screenFlag=F           | 1003000111 | 444 666
          screenFlag=F           | 1003000444 | 666
          name=RIVERA            | 1003000111 | 555 888
          name=rivera            | 1003000555 | 888
          name=hospital          | 1003000111 | 777
          name=RIVERA&status=A   | 1003000555 | 888
          name=RIVERA&status=T   | 1003000000 |
          npi=1003000888         | 1003000444 | 888
          npi=1003000222         | 1003000444 |
          npi=1003000444         | 1003000444 |
          medicaidId=MCD000666   | 1003000555 | 666
          medicaidId=MCD000666   | 1003000666 |
          """)
  @DisplayName(
      "[MAM-37 AC-9] every other filter (npi, medicaidId, name, status, screenFlag) combines with the key by AND; the key need not match the filters")
  void filtersCombineWithTheKey(String filters, String key, String expected) throws Exception {
    JsonNode page = ok(searchQuery(filters, "afterNpi", key));

    assertThat(npis(page)).containsExactlyElementsOf(full(expected));
  }

  // ---- the end of a walk: a short answer, then an empty one
  // ----------------------------------------

  @Test
  @DisplayName("[MAM-37 AC-9] the last answer that holds providers has fewer items than size")
  void lastAnswerIsShort() throws Exception {
    JsonNode page = ok(search("afterNpi", "1003000666", "size", "3"));

    assertThat(npis(page)).containsExactly(npi("777"), npi("888"));
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] a key at or beyond the last NPI gives 200 with an empty items array, page 0, the size asked and the total of all matches")
  void emptyAnswerAtTheEnd() throws Exception {
    JsonNode page = ok(search("afterNpi", "1003000888", "size", "3"));

    assertThat(page.path("items").isArray()).isTrue();
    assertThat(page.path("items").size()).isZero();
    assertThat(page.path("page").asInt()).isZero();
    assertThat(page.path("size").asInt()).isEqualTo(3);
    assertThat(page.path("total").asInt()).isEqualTo(8);
  }

  // ---- total: every match, never only those after the key (RULE 4)
  // --------------------------------

  @ParameterizedTest(name = "[MAM-37 AC-9] with filters {0} the total is {1} whatever the key")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          -                      | 8
          status=A               | 5
          status=A&screenFlag=P  | 4
          name=RIVERA            | 3
          status=T               | 1
          status=S               | 0
          """)
  @DisplayName(
      "[MAM-37 AC-9] total is the number of ALL providers that match the filters: the same with no key, with a key in the middle, at the end and beyond the end")
  void totalCountsEveryMatch(String filters, int total) throws Exception {
    assertThat(ok(searchQuery(filters)).path("total").asInt()).as("no key").isEqualTo(total);
    for (String key : new String[] {"0000000000", "1003000444", "1003000888", "9999999999"}) {
      assertThat(ok(searchQuery(filters, "afterNpi", key)).path("total").asInt())
          .as("total with key " + key)
          .isEqualTo(total);
    }
  }

  // ---- page: absent or 0
  // -------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-37 AC-9] a key with page=0 is allowed and gives the same answer as a key alone, with page 0")
  void pageZeroWithAKey() throws Exception {
    JsonNode alone = ok(search("afterNpi", "1003000333", "size", "2"));
    JsonNode withPage = ok(search("afterNpi", "1003000333", "size", "2", "page", "0"));

    assertThat(npis(withPage)).containsExactly(npi("444"), npi("555"));
    assertThat(withPage).isEqualTo(alone);
    assertThat(withPage.path("page").asInt()).isZero();
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi with page={0} is refused")
  @ValueSource(strings = {"1", "2", "5", "100"})
  @DisplayName(
      "[MAM-37 AC-9] a key together with a page other than 0 is a 400 problem: the page is neither ignored nor used")
  void pageOtherThanZeroIsRefused(String page) throws Exception {
    assertRefused(search("afterNpi", "1003000111", "size", "3", "page", page), "page=" + page);
  }

  // ---- strict input (lesson b): nothing coerced, nothing ignored
  // ----------------------------------

  static Stream<Arguments> malformedKeys() {
    return Stream.of(
        Arguments.of("empty", ""),
        Arguments.of("one space", " "),
        Arguments.of("9 digits", "100300044"),
        Arguments.of("11 digits", "10030004444"),
        Arguments.of("letters", "ABC"),
        Arguments.of("5 digits", "12345"),
        Arguments.of("a letter at the end", "100300044A"),
        Arguments.of("the letter O for a zero", "1003O00444"),
        Arguments.of("leading space", " 1003000444"),
        Arguments.of("trailing space", "1003000444 "),
        Arguments.of("trailing newline", "1003000444\n"),
        Arguments.of("plus sign, 10 characters", "+100300044"),
        Arguments.of("plus sign, 11 characters", "+1003000444"),
        Arguments.of("minus sign", "-100300044"),
        Arguments.of("decimal point", "1003000444.0"),
        Arguments.of("exponent", "1e9"),
        Arguments.of("the word null", "null"),
        Arguments.of("Arabic-Indic digits", "١٠٠٣٠٠٠٤٤٤"),
        Arguments.of("full-width digits", "１００３０００４４４"));
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi {0} is refused")
  @MethodSource("malformedKeys")
  @DisplayName(
      "[MAM-37 AC-9] an afterNpi that is not exactly 10 ASCII digits is a 400 problem+json, never coerced and never ignored")
  void malformedKeyIsRefused(String label, String value) throws Exception {
    assertRefused(search("afterNpi", value), "afterNpi " + label);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi {0} with other filters is refused")
  @MethodSource("malformedKeys")
  @DisplayName(
      "[MAM-37 AC-9] a malformed afterNpi is a 400 also when the other filters and a size are valid")
  void malformedKeyIsRefusedWithFilters(String label, String value) throws Exception {
    assertRefused(
        search("status", "A", "size", "5", "afterNpi", value), "status=A size=5 afterNpi " + label);
  }

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi given as {0} is refused")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          two different keys     | 1003000111 | 1003000222
          the same key twice     | 1003000111 | 1003000111
          a key and an empty one | 1003000111 |
          """)
  @DisplayName(
      "[MAM-37 AC-9] afterNpi given more than once is a 400 problem: the first value is not taken and the rest dropped")
  void repeatedKeyIsRefused(String label, String first, String second) throws Exception {
    MvcResult r =
        mvc.perform(
                get("/api/v1/providers")
                    .with(PemJwt.analyst())
                    .param("afterNpi", first, second == null ? "" : second))
            .andReturn();

    assertRefused(r, "afterNpi " + label);
  }

  @Test
  @DisplayName("[MAM-37 AC-9] afterNpi given three times is a 400 problem")
  void keyGivenThreeTimesIsRefused() throws Exception {
    MvcResult r =
        mvc.perform(
                get("/api/v1/providers")
                    .with(PemJwt.analyst())
                    .param("afterNpi", "1003000111", "1003000222", "1003000333"))
            .andReturn();

    assertRefused(r, "afterNpi three times");
  }

  // ---- NPIs above the int range: the key is a position in NPI order, not a number
  // -----------------

  @ParameterizedTest(name = "[MAM-37 AC-9] afterNpi={0} lists {1}")
  @CsvSource(
      delimiter = '|',
      textBlock =
          """
          1003000888 | 1100000000 1999999999 2003000111 3003000111 9003000111
          1999999999 | 2003000111 3003000111 9003000111
          2147483647 | 3003000111 9003000111
          2147483648 | 3003000111 9003000111
          2999999999 | 3003000111 9003000111
          4294967295 | 9003000111
          4294967296 | 9003000111
          9003000110 | 9003000111
          9003000111 |
          9999999999 |
          """)
  @DisplayName(
      "[MAM-37 AC-9] NPIs above 2147483647 and 4294967295 are ordered and compared like every other 10-digit NPI")
  void keysAboveTheIntRange(String key, String expected) throws Exception {
    String[] extra = {"1100000000", "1999999999", "2003000111", "3003000111", "9003000111"};
    for (int i = 0; i < extra.length; i++) {
      ProviderRows.individual(extra[i]).medicaidId("MCDX0000" + (i + 1)).status("A").insert(jdbc);
    }

    JsonNode page = ok(search("afterNpi", key, "size", "200"));

    List<String> want =
        expected == null || expected.isBlank() ? List.of() : List.of(expected.trim().split("\\s+"));
    assertThat(npis(page)).containsExactlyElementsOf(want);
  }

  // ---- the answer keeps to the contract
  // ------------------------------------------------------------

  @ParameterizedTest(
      name = "[MAM-37 AC-9] the answer to afterNpi={0}, size={1} keeps to ProviderPage")
  @CsvSource({
    "1003000000, 3",
    "1003000333, 3",
    "1003000666, 3",
    "1003000888, 3",
    "0000000000, 200"
  })
  @DisplayName(
      "[MAM-37 AC-9] a keyset answer (a full page, the short last one, the empty one) keeps to the ProviderPage contract, item by item")
  void keysetAnswerMatchesContract(String key, String size) throws Exception {
    MvcResult r = search("afterNpi", key, "size", size);

    OpenApiContract.of("provider-service")
        .assertResponse("searchProviders", 200, r.getResponse().getContentAsString());
  }

  // ---- nothing changes without the parameter (one regression check)
  // --------------------------------

  @Test
  @DisplayName(
      "[MAM-37 AC-9] without afterNpi the answer is as before: offset pages cut from the NPI order, page and size echoed, total of every match")
  void withoutTheKeyNothingChanges() throws Exception {
    JsonNode all = ok(search());
    JsonNode second = ok(search("page", "1", "size", "3"));
    JsonNode beyond = ok(search("page", "5", "size", "3"));
    JsonNode filtered = ok(search("status", "A", "page", "1", "size", "2"));

    assertThat(npis(all))
        .containsExactly(
            npi("111"),
            npi("222"),
            npi("333"),
            npi("444"),
            npi("555"),
            npi("666"),
            npi("777"),
            npi("888"));
    assertThat(all.path("page").asInt()).isZero();
    assertThat(all.path("size").asInt()).isEqualTo(20);
    assertThat(npis(second)).containsExactly(npi("444"), npi("555"), npi("666"));
    assertThat(second.path("page").asInt()).isEqualTo(1);
    assertThat(second.path("size").asInt()).isEqualTo(3);
    assertThat(second.path("total").asInt()).isEqualTo(8);
    assertThat(npis(beyond)).isEmpty();
    assertThat(beyond.path("total").asInt()).isEqualTo(8);
    assertThat(npis(filtered)).containsExactly(npi("666"), npi("777"));
    assertThat(filtered.path("total").asInt()).isEqualTo(5);
  }

  // ---- roles: the new parameter opens no side door
  // --------------------------------------------------

  @ParameterizedTest(name = "[MAM-37 AC-9] {0} may page by key")
  @ValueSource(strings = {"ANALYST", "SUPERVISOR", "ADMIN", "SYSTEM"})
  @DisplayName(
      "[MAM-37 AC-9] staff roles and SYSTEM may page by key and get the providers after it")
  void staffMayPageByKey(String role) throws Exception {
    MvcResult r = searchAs(PemJwt.user("x", null, role), "afterNpi", "1003000666");

    assertThat(npis(ok(r))).containsExactly(npi("777"), npi("888"));
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] a PROVIDER cannot page by key, even with their own NPI as the key: 403 problem+json")
  void providerCannotPageByKey() throws Exception {
    MvcResult r = searchAs(PemJwt.provider("1003000111"), "afterNpi", "1003000111");

    assertThat(r.getResponse().getStatus()).isEqualTo(403);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName(
      "[MAM-37 AC-9] the role is checked before the key: a PROVIDER with a malformed key gets 403, not 400")
  void roleIsCheckedBeforeTheKey() throws Exception {
    MvcResult r = searchAs(PemJwt.provider("1003000111"), "afterNpi", "ABC");

    assertThat(r.getResponse().getStatus()).isEqualTo(403);
  }

  @Test
  @DisplayName("[MAM-37 AC-9] no token gives 401 problem+json on a keyset call")
  void noTokenCannotPageByKey() throws Exception {
    MvcResult r = searchAs(null, "afterNpi", "1003000111");

    assertThat(r.getResponse().getStatus()).isEqualTo(401);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName("[MAM-37 AC-9] a user with no PEM role gets 403 on a keyset call")
  void noRoleCannotPageByKey() throws Exception {
    MvcResult r = searchAs(PemJwt.user("nobody", null), "afterNpi", "1003000111");

    assertThat(r.getResponse().getStatus()).isEqualTo(403);
  }
}
