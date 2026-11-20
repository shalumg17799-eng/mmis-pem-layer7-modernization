package gov.medicaid.pem.provider.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.provider.IntegrationTest;
import gov.medicaid.pem.provider.ProviderRows;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MAM-40 on the real application and database: the shape and order of the history as read through
 * GET /api/v1/providers/{npi}/audit, its paging and its refusals, and the guarantee that it is
 * append-only (Q-012, D-018, D-023).
 */
class ProviderAuditApiTest extends IntegrationTest {

  static final Instant T0 = Instant.parse("2026-11-18T09:00:00Z");

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;

  @BeforeEach
  void seed() {
    ProviderRows.wipe(jdbc);
    ProviderRows.individual("1003000111").insert(jdbc);
  }

  private void audit(
      String action, Instant at, String actor, String before, String after, UUID correlationId) {
    jdbc.update(
        "INSERT INTO audit_log (npi, actor, action, before_json, after_json, correlation_id, at)"
            + " VALUES ('1003000111', ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?)",
        actor,
        action,
        before,
        after,
        correlationId,
        Timestamp.from(at));
  }

  private JsonNode read(String query) throws Exception {
    MvcResult r =
        mvc.perform(get("/api/v1/providers/1003000111/audit" + query).with(PemJwt.supervisor()))
            .andReturn();
    assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
    return json.readTree(r.getResponse().getContentAsString());
  }

  private static List<String> actions(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(i -> out.add(i.path("action").asText()));
    return out;
  }

  // ---- AC-1, AC-2: order and shape
  // -----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-40 AC-1] entries come oldest first by time, whatever order they were written in")
  void orderedByTime() throws Exception {
    audit("THIRD", T0.plusSeconds(30), "a", null, null, null);
    audit("FIRST", T0.plusSeconds(10), "a", null, null, null);
    audit("SECOND", T0.plusSeconds(20), "a", null, null, null);

    assertThat(actions(read(""))).containsExactly("FIRST", "SECOND", "THIRD");
  }

  @Test
  @DisplayName("[MAM-40 AC-1] entries with the same time keep the order in which they were written")
  void sameTimeKeepsInsertionOrder() throws Exception {
    for (String action : new String[] {"E1", "E2", "E3", "E4"}) {
      audit(action, T0, "a", null, null, null);
    }

    assertThat(actions(read(""))).containsExactly("E1", "E2", "E3", "E4");
  }

  @Test
  @DisplayName("[MAM-40 AC-1] a provider with no entries gives 200 and an empty history, total 0")
  void emptyHistory() throws Exception {
    JsonNode page = read("");

    assertThat(page.path("items").isArray()).isTrue();
    assertThat(page.path("items").size()).isZero();
    assertThat(page.path("total").asInt()).isZero();
  }

  @Test
  @DisplayName("[MAM-40 AC-1] the history of one provider holds none of another provider's entries")
  void onlyThisProvidersEntries() throws Exception {
    ProviderRows.individual("1003000444").insert(jdbc);
    audit("MINE", T0, "a", null, null, null);
    jdbc.update(
        "INSERT INTO audit_log (npi, actor, action, at) VALUES ('1003000444', 'a', 'OTHERS', ?)",
        Timestamp.from(T0));

    JsonNode page = read("");

    assertThat(actions(page)).containsExactly("MINE");
    assertThat(page.path("total").asInt()).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] an entry returns at, actor, action, before, after and the correlation id as stored")
  void entryShape() throws Exception {
    UUID corr = UUID.fromString("5a1e7b20-3c4d-4e5f-9a6b-000000000456");
    audit(
        "APPROVED",
        T0,
        "analyst1",
        "{\"enrollStatus\":\"P\",\"revalidationDueDate\":null}",
        "{\"enrollStatus\":\"A\",\"revalidationDueDate\":\"2031-11-18\"}",
        corr);

    JsonNode e = read("").path("items").get(0);

    assertThat(Instant.parse(e.path("at").asText())).isEqualTo(T0);
    assertThat(e.path("actor").asText()).isEqualTo("analyst1");
    assertThat(e.path("action").asText()).isEqualTo("APPROVED");
    assertThat(e.at("/before/enrollStatus").asText()).isEqualTo("P");
    assertThat(e.at("/before/revalidationDueDate").isNull()).isTrue();
    assertThat(e.at("/after/enrollStatus").asText()).isEqualTo("A");
    assertThat(e.at("/after/revalidationDueDate").asText()).isEqualTo("2031-11-18");
    assertThat(e.path("correlationId").asText()).isEqualTo(corr.toString());
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-2] an entry with no before and no correlation id shows before null and has no correlationId")
  void entryWithoutBeforeAndCorrelation() throws Exception {
    audit("REGISTERED", T0, "enrollment-service", null, "{\"enrollStatus\":\"P\"}", null);

    JsonNode e = read("").path("items").get(0);

    assertThat(e.has("before")).isTrue();
    assertThat(e.path("before").isNull()).isTrue();
    assertThat(e.at("/after/enrollStatus").asText()).isEqualTo("P");
    assertThat(e.has("correlationId")).isFalse();
  }

  @Test
  @DisplayName("[MAM-40 AC-2] the history answer keeps to the AuditPage contract, entry by entry")
  void answerMatchesContract() throws Exception {
    audit("REGISTERED", T0, "enrollment-service", null, "{\"enrollStatus\":\"P\"}", null);
    audit(
        "APPROVED",
        T0.plusSeconds(1),
        "analyst1",
        "{\"enrollStatus\":\"P\"}",
        "{\"enrollStatus\":\"A\"}",
        UUID.randomUUID());
    MvcResult r =
        mvc.perform(get("/api/v1/providers/1003000111/audit").with(PemJwt.supervisor()))
            .andReturn();

    OpenApiContract.of("provider-service")
        .assertResponse("getProviderAudit", 200, r.getResponse().getContentAsString());
  }

  // ---- AC-6: paging
  // ----------------------------------------------------------------------------------

  private void twentyFiveEntries() {
    for (int i = 1; i <= 25; i++) {
      audit("ENTRY_" + i, T0.plusSeconds(i), "seed", null, null, null);
    }
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-6] the first page holds 20 of 25 entries by default, and the answer says so")
  void firstPage() throws Exception {
    twentyFiveEntries();

    JsonNode page = read("");

    assertThat(page.path("items").size()).isEqualTo(20);
    assertThat(page.path("page").asInt()).isZero();
    assertThat(page.path("size").asInt()).isEqualTo(20);
    assertThat(page.path("total").asInt()).isEqualTo(25);
    assertThat(actions(page).get(0)).isEqualTo("ENTRY_1");
    assertThat(actions(page).get(19)).isEqualTo("ENTRY_20");
  }

  @Test
  @DisplayName("[MAM-40 AC-6] page 1 of size 20 holds the last 5 of 25 entries")
  void secondPage() throws Exception {
    twentyFiveEntries();

    JsonNode page = read("?page=1&size=20");

    assertThat(actions(page))
        .containsExactly("ENTRY_21", "ENTRY_22", "ENTRY_23", "ENTRY_24", "ENTRY_25");
    assertThat(page.path("page").asInt()).isEqualTo(1);
    assertThat(page.path("size").asInt()).isEqualTo(20);
    assertThat(page.path("total").asInt()).isEqualTo(25);
  }

  @Test
  @DisplayName("[MAM-40 AC-6] a page of another size is cut from the same order: page 2 of size 10")
  void otherPageSize() throws Exception {
    twentyFiveEntries();

    JsonNode page = read("?page=2&size=10");

    assertThat(actions(page))
        .containsExactly("ENTRY_21", "ENTRY_22", "ENTRY_23", "ENTRY_24", "ENTRY_25");
    assertThat(page.path("size").asInt()).isEqualTo(10);
  }

  @Test
  @DisplayName("[MAM-40 AC-6] a page beyond the last is empty and still reports the total")
  void pageBeyondTheEnd() throws Exception {
    twentyFiveEntries();

    JsonNode page = read("?page=9&size=20");

    assertThat(page.path("items").size()).isZero();
    assertThat(page.path("total").asInt()).isEqualTo(25);
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-6] the largest page size is 200; 0, 201, a negative page or text is a 400 problem")
  void pagingLimits() throws Exception {
    twentyFiveEntries();

    assertThat(read("?size=200").path("items").size()).isEqualTo(25);
    for (String query : new String[] {"?size=0", "?size=201", "?page=-1", "?size=abc", "?page=x"}) {
      MvcResult r =
          mvc.perform(get("/api/v1/providers/1003000111/audit" + query).with(PemJwt.supervisor()))
              .andReturn();
      assertThat(r.getResponse().getStatus()).as(query).isEqualTo(400);
      assertThat(r.getResponse().getContentType()).as(query).startsWith("application/problem+json");
    }
  }

  // ---- AC-7: roles and refusals
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-40 AC-7] an NPI that is not 10 digits is a 400 problem, before any lookup")
  void malformedNpi() throws Exception {
    mvc.perform(get("/api/v1/providers/12AB/audit").with(PemJwt.supervisor()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400));
  }

  @Test
  @DisplayName("[MAM-40 AC-7] a user with no PEM role gets 403")
  void noRole() throws Exception {
    mvc.perform(get("/api/v1/providers/1003000111/audit").with(PemJwt.user("nobody", null)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-7] a SUPERVISOR who is also a PROVIDER keeps the SUPERVISOR right, ANALYST plus PROVIDER does not")
  void roleCombinations() throws Exception {
    mvc.perform(
            get("/api/v1/providers/1003000111/audit")
                .with(PemJwt.user("both", "1003000111", "PROVIDER", "SUPERVISOR")))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/providers/1003000111/audit")
                .with(PemJwt.user("other", "1003000111", "PROVIDER", "ANALYST")))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-7] the history of an NPI that is not on file is a 404 problem, even though other providers have entries")
  void unknownProvider() throws Exception {
    audit("MINE", T0, "a", null, null, null);

    MvcResult r =
        mvc.perform(get("/api/v1/providers/1003000999/audit").with(PemJwt.admin())).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(r.getResponse().getContentAsString()).doesNotContain("MINE");
  }

  // ---- AC-8: append-only
  // -----------------------------------------------------------------------------------

  private MockHttpServletRequestBuilder method(String name) {
    String url = "/api/v1/providers/1003000111/audit";
    return switch (name) {
      case "PUT" -> put(url);
      case "PATCH" -> patch(url);
      case "DELETE" -> delete(url);
      case "POST" -> post(url);
      default -> throw new IllegalArgumentException(name);
    };
  }

  @ParameterizedTest(name = "[MAM-40 AC-8] {0} on the history is refused with 405")
  @ValueSource(strings = {"PUT", "PATCH", "DELETE", "POST"})
  @DisplayName(
      "[MAM-40 AC-8] PUT, PATCH, DELETE and POST on the history are 405 problems that allow only reading")
  void onlyReadingIsDefined(String name) throws Exception {
    audit("KEEP", T0, "a", null, null, null);

    MvcResult r = mvc.perform(method(name).with(PemJwt.supervisor())).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(405);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(json.readTree(r.getResponse().getContentAsString()).path("status").asInt())
        .isEqualTo(405);
    assertThat(r.getResponse().getHeader("Allow")).contains("GET").doesNotContain("PUT", "DELETE");
    assertThat(actions(read(""))).containsExactly("KEEP");
  }

  @ParameterizedTest(name = "[MAM-40 AC-8] {0} by an ADMIN on the history is refused with 405")
  @ValueSource(strings = {"PUT", "PATCH", "DELETE"})
  @DisplayName("[MAM-40 AC-8] not even an ADMIN can change or delete the history")
  void adminCannotEither(String name) throws Exception {
    audit("KEEP", T0, "a", null, null, null);

    MvcResult r = mvc.perform(method(name).with(PemJwt.admin())).andReturn();

    assertThat(r.getResponse().getStatus()).isEqualTo(405);
    assertThat(actions(read(""))).containsExactly("KEEP");
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-8] an anonymous caller who tries to delete the history gets 401, not 405")
  void anonymousDelete() throws Exception {
    mvc.perform(delete("/api/v1/providers/1003000111/audit")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "[MAM-40 AC-8] no production code updates, deletes or truncates audit_log: the history can only grow")
  void noCodePathEditsTheHistory() throws IOException {
    Pattern edits =
        Pattern.compile(
            "(?is)(update\\s+audit_log|delete\\s+from\\s+audit_log|truncate\\s+(table\\s+)?audit_log|drop\\s+table\\s+audit_log)");
    Path main = mainSources();
    List<String> offenders = new ArrayList<>();
    try (Stream<Path> files = Files.walk(main)) {
      for (Path p : files.filter(Files::isRegularFile).toList()) {
        if (edits.matcher(Files.readString(p)).find()) {
          offenders.add(main.relativize(p).toString());
        }
      }
    }

    assertThat(offenders).as("main sources that edit audit_log").isEmpty();
  }

  private static Path mainSources() {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c :
          List.of(
              dir.resolve("src/main"),
              dir.resolve("services/provider-service/src/main"),
              dir.resolve("modern/services/provider-service/src/main"))) {
        if (Files.isDirectory(c.resolve("java/gov/medicaid/pem/provider"))) {
          return c;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("provider-service src/main not found");
  }
}
