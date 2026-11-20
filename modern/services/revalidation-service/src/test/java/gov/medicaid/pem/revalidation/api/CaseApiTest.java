package gov.medicaid.pem.revalidation.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.revalidation.IntegrationTest;
import gov.medicaid.pem.revalidation.RevalidationFixtures;
import gov.medicaid.pem.testsupport.PemJwt;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * The case endpoints on the real application (MAM-48 AC-1 to AC-3): the list with its filters and
 * paging, the values it refuses (including the characters of a text search, which are never
 * wildcards here: the NPI filter is an exact 10-digit match), and the completion call with its
 * bodies and its 404 and 409 answers, all as problem+json.
 */
@Timeout(180)
class CaseApiTest extends IntegrationTest {

  static final LocalDate DUE = LocalDate.of(2026, 2, 28);

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void cleanBefore() {
    RevalidationFixtures.reset(jdbc);
  }

  @AfterEach
  void cleanAfter() {
    RevalidationFixtures.reset(jdbc);
  }

  private MvcResult list(String query) throws Exception {
    return mvc.perform(
            MockMvcRequestBuilders.get("/api/v1/revalidations" + query).with(PemJwt.analyst()))
        .andReturn();
  }

  private MvcResult complete(UUID caseId, String body) throws Exception {
    MockHttpServletRequestBuilder request =
        MockMvcRequestBuilders.post("/api/v1/revalidations/{caseId}/completion", caseId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body);
    return mvc.perform(request.with(PemJwt.analyst())).andReturn();
  }

  private static JsonNode json(MvcResult r) throws Exception {
    return RevalidationFixtures.read(r.getResponse().getContentAsString());
  }

  private static void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).contains("application/problem+json");
    assertThat(json(r).path("title").asText()).isNotBlank();
    assertThat(json(r).path("status").asInt()).isEqualTo(status);
  }

  private static List<String> npis(JsonNode page) {
    List<String> out = new ArrayList<>();
    page.path("items").forEach(c -> out.add(c.path("npi").asText()));
    return out;
  }

  private static String completion(String result) {
    return "{\"screeningId\":\"" + UUID.randomUUID() + "\",\"result\":\"" + result + "\"}";
  }

  // ---- AC-1: the list ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-48 AC-1] without parameters the list is page 0 of size 20 and holds every case")
  void defaults() throws Exception {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "COMPLETED");

    JsonNode page = json(list(""));

    assertThat(page.path("page").asInt()).isZero();
    assertThat(page.path("size").asInt()).isEqualTo(20);
    assertThat(page.path("total").asInt()).isEqualTo(2);
    assertThat(npis(page)).containsExactly("1003000222", "1003000333");
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-1] each item has caseId, npi, dueDate, status, openedAt and, when closed, closedAt")
  void itemShape() throws Exception {
    UUID open = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "CANCELLED");

    JsonNode items = json(list("?size=10")).path("items");

    JsonNode first = items.get(0);
    assertThat(first.path("caseId").asText()).isEqualTo(open.toString());
    assertThat(first.path("npi").asText()).isEqualTo("1003000222");
    assertThat(first.path("dueDate").asText()).isEqualTo("2026-02-28");
    assertThat(first.path("status").asText()).isEqualTo("DUE");
    assertThat(first.path("openedAt").asText()).isEqualTo("2026-03-01T10:00:00Z");
    assertThat(first.has("closedAt")).as("an open case has no closedAt").isFalse();
    JsonNode second = items.get(1);
    assertThat(second.path("status").asText()).isEqualTo("CANCELLED");
    assertThat(second.path("closedAt").asText()).isEqualTo("2026-03-15T10:00:00Z");
  }

  @Test
  @DisplayName("[MAM-48 AC-1] the status and the NPI filters combine: both must match")
  void filtersCombine() throws Exception {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "COMPLETED");
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "DUE");

    JsonNode page = json(list("?status=DUE&npi=1003000222"));

    assertThat(page.path("total").asInt()).isEqualTo(1);
    assertThat(npis(page)).containsExactly("1003000222");
    assertThat(page.path("items").get(0).path("status").asText()).isEqualTo("DUE");
  }

  @Test
  @DisplayName("[MAM-48 AC-1] a page beyond the last holds no item but still reports the total")
  void pageBeyondTheLast() throws Exception {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    JsonNode page = json(list("?page=5&size=10"));

    assertThat(page.path("items")).isEmpty();
    assertThat(page.path("total").asInt()).isEqualTo(1);
    assertThat(page.path("page").asInt()).isEqualTo(5);
  }

  @Test
  @DisplayName("[MAM-48 AC-1] the smallest and the largest page size are accepted")
  void sizeBounds() throws Exception {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");
    RevalidationFixtures.insertCase(jdbc, "1003000333", DUE, "DUE");

    assertThat(npis(json(list("?size=1")))).hasSize(1);
    assertThat(json(list("?size=200")).path("size").asInt()).isEqualTo(200);
    assertThat(npis(json(list("?size=200")))).hasSize(2);
  }

  @ParameterizedTest(name = "[MAM-48 AC-1] the query {0} is refused with a 400 problem")
  @ValueSource(
      strings = {
        "?status=OPEN",
        "?status=due",
        "?status=Due",
        "?status=",
        "?status=CLOSED",
        "?page=-1",
        "?page=abc",
        "?page=1.5",
        "?size=0",
        "?size=-5",
        "?size=201",
        "?size=abc",
        "?size=99999999999",
        "?npi=",
        "?npi=123",
        "?npi=100300022X",
        "?npi=10030002221",
        "?npi=+003000222",
        "?npi=1003000222%20"
      })
  void refusedQueries(String query) throws Exception {
    assertProblem(list(query), 400);
  }

  @ParameterizedTest(
      name =
          "[MAM-48 AC-1] the NPI filter treats {0} as plain characters, never as a wildcard: 400, no case is found")
  @ValueSource(strings = {"%", "%%%%%%%%%%", "1003000_22", "100300022%", "_003000222", "\\"})
  void npiFilterHasNoWildcards(String npi) throws Exception {
    RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.get("/api/v1/revalidations")
                    .param("npi", npi)
                    .with(PemJwt.analyst()))
            .andReturn();

    assertProblem(r, 400);
  }

  // ---- AC-2: complete ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-48 AC-2] completing returns the case COMPLETED with its closedAt, and the list follows")
  void completeAnswer() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    MvcResult r = complete(id, completion("PASS"));

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    JsonNode c = json(r);
    assertThat(c.path("caseId").asText()).isEqualTo(id.toString());
    assertThat(c.path("npi").asText()).isEqualTo("1003000222");
    assertThat(c.path("dueDate").asText()).isEqualTo("2026-02-28");
    assertThat(c.path("status").asText()).isEqualTo("COMPLETED");
    assertThat(c.path("closedAt").asText()).isNotBlank();
    assertThat(npis(json(list("?status=COMPLETED")))).containsExactly("1003000222");
    assertThat(npis(json(list("?status=DUE")))).isEmpty();
  }

  @Test
  @DisplayName("[MAM-48 AC-2] the screening id of the request is kept with the closed case")
  void screeningIdIsKept() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "IN_SCREENING");
    UUID screening = UUID.randomUUID();

    complete(id, "{\"screeningId\":\"" + screening + "\",\"result\":\"PASS\"}");

    assertThat(
            jdbc.queryForObject(
                "SELECT screening_id FROM revalidation_case WHERE public_id = ?", UUID.class, id))
        .isEqualTo(screening);
    assertThat(
            jdbc.queryForObject(
                "SELECT closed_at IS NOT NULL FROM revalidation_case WHERE public_id = ?",
                Boolean.class,
                id))
        .isTrue();
  }

  @Test
  @DisplayName(
      "[MAM-48 AC-2] a FAIL result closes the case too and leaves the tracked due date alone (C-12)")
  void failClosesAndKeepsTheDate() throws Exception {
    RevalidationFixtures.track(jdbc, "1003000222", "A", DUE);
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    MvcResult r = complete(id, completion("FAIL"));

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(json(r).path("status").asText()).isEqualTo("COMPLETED");
    assertThat(
            jdbc.queryForObject(
                "SELECT reval_due_date::text FROM tracked_provider WHERE npi = '1003000222'",
                String.class))
        .isEqualTo("2026-02-28");
    assertThat(RevalidationFixtures.outbox(jdbc, "RevalidationDue")).isEmpty();
  }

  @ParameterizedTest(name = "[MAM-48 AC-2] the completion body {0} is refused with a 400 problem")
  @ValueSource(
      strings = {
        "",
        "not json",
        "[]",
        "null",
        "{}",
        "{\"result\":\"PASS\"}",
        "{\"screeningId\":\"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11\"}",
        "{\"screeningId\":\"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11\",\"result\":\"MAYBE\"}",
        "{\"screeningId\":\"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11\",\"result\":\"pass\"}",
        "{\"screeningId\":\"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11\",\"result\":\"\"}",
        "{\"screeningId\":\"0b9c5d1e-6a4f-4c1a-9a43-3c2d6f0a1b11\",\"result\":null}",
        "{\"screeningId\":null,\"result\":\"PASS\"}",
        "{\"screeningId\":\"not-a-uuid\",\"result\":\"PASS\"}",
        "{\"screeningId\":42,\"result\":\"PASS\"}"
      })
  void refusedCompletionBodies(String body) throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    assertProblem(complete(id, body), 400);

    assertThat(RevalidationFixtures.caseStatus(jdbc, id)).isEqualTo("DUE");
  }

  // ---- AC-3: unknown and closed cases -----------------------------------------------------------

  @Test
  @DisplayName("[MAM-48 AC-3] an unknown case is a 404 problem")
  void unknownCase() throws Exception {
    assertProblem(complete(UUID.randomUUID(), completion("PASS")), 404);
  }

  @Test
  @DisplayName("[MAM-48 AC-3] a case id that is not a UUID is a 400 problem")
  void caseIdIsNotAUuid() throws Exception {
    MvcResult r =
        mvc.perform(
                MockMvcRequestBuilders.post(
                        "/api/v1/revalidations/{caseId}/completion", "1003000222")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(completion("PASS"))
                    .with(PemJwt.analyst()))
            .andReturn();

    assertProblem(r, 400);
  }

  @ParameterizedTest(name = "[MAM-48 AC-3] a {0} case is a 409 problem and is left unchanged")
  @ValueSource(strings = {"COMPLETED", "CANCELLED"})
  void closedCase(String status) throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, status);
    String before =
        jdbc.queryForObject(
            "SELECT status || '|' || closed_at || '|' || version FROM revalidation_case WHERE public_id = ?",
            String.class,
            id);

    assertProblem(complete(id, completion("PASS")), 409);

    assertThat(
            jdbc.queryForObject(
                "SELECT status || '|' || closed_at || '|' || version FROM revalidation_case WHERE public_id = ?",
                String.class,
                id))
        .isEqualTo(before);
  }

  @Test
  @DisplayName("[MAM-48 AC-3] completing the same case twice: 200 then 409")
  void secondCompletionIsRefused() throws Exception {
    UUID id = RevalidationFixtures.insertCase(jdbc, "1003000222", DUE, "DUE");

    assertThat(complete(id, completion("PASS")).getResponse().getStatus()).isEqualTo(200);
    assertProblem(complete(id, completion("FAIL")), 409);
  }
}
