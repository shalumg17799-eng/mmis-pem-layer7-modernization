package gov.medicaid.pem.notification;

import static gov.medicaid.pem.notification.NoticeFixtures.mailbox;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.medicaid.pem.notification.application.SendSanctionNotice;
import gov.medicaid.pem.testsupport.OpenApiContract;
import gov.medicaid.pem.testsupport.PemJwt;
import java.net.ServerSocket;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * MAM-60 Lane 3: GET /api/v1/notices and GET /api/v1/notice-templates through the real application
 * (MockMvc, security filter chain, Postgres). The contract (listNotices, listNoticeTemplates), the
 * roles with their 401 and 403 problem answers (lesson h), the invalid filters, a text filter that
 * never treats % _ or a backslash as a wildcard (lesson e), what a PROVIDER may see, the FAILED
 * notice of a real failed send, and a correlation id that is never echoed unsanitised (lesson g).
 * Every test starts with an empty notice table and uses the NPIs 1003900501 to 1003900503.
 */
class NoticeApiTest extends IntegrationTest {

  static final String A = "1003900501";
  static final String B = "1003900502";
  static final String C = "1003900503";
  static final String SAFE = "[A-Za-z0-9._:-]{1,64}";

  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate jdbc;
  @Autowired ObjectMapper json;
  @Autowired SendSanctionNotice sanctions;
  @Autowired JavaMailSender mail;

  private final List<UUID> events = new ArrayList<>();
  private String host;
  private int port;

  @BeforeEach
  void start() {
    jdbc.update("DELETE FROM notice");
    JavaMailSenderImpl sender = (JavaMailSenderImpl) mail;
    host = sender.getHost();
    port = sender.getPort();
  }

  @AfterEach
  void stop() {
    JavaMailSenderImpl sender = (JavaMailSenderImpl) mail;
    sender.setHost(host);
    sender.setPort(port);
    jdbc.update("DELETE FROM notice");
    NoticeFixtures.clean(jdbc, events, A, B, C);
  }

  // ---- helpers
  // -----------------------------------------------------------------------------------

  private void insert(String npi, String template, String status, String created) {
    Timestamp at = Timestamp.from(Instant.parse(created));
    if ("SENT".equals(status)) {
      jdbc.update(
          "INSERT INTO notice (public_id, npi, template_code, channel, recipient, status, event_id,"
              + " sent_at, created_at) VALUES (?, ?, ?, 'EMAIL', ?, ?, ?, ?, ?)",
          UUID.randomUUID(),
          npi,
          template,
          mailbox(npi),
          status,
          UUID.randomUUID(),
          at,
          at);
    } else {
      jdbc.update(
          "INSERT INTO notice (public_id, npi, template_code, channel, recipient, status, event_id,"
              + " created_at) VALUES (?, ?, ?, 'EMAIL', ?, ?, ?, ?)",
          UUID.randomUUID(),
          npi,
          template,
          mailbox(npi),
          status,
          UUID.randomUUID(),
          at);
    }
  }

  private void insert(String npi, String template) {
    insert(npi, template, "SENT", "2026-11-20T10:00:00Z");
  }

  /** A FAILED notice made by the real code: the mail server cannot be reached. */
  private void realFailedNotice(String npi) throws Exception {
    JavaMailSenderImpl sender = (JavaMailSenderImpl) mail;
    int closed;
    try (ServerSocket s = new ServerSocket(0)) {
      closed = s.getLocalPort();
    }
    sender.setPort(closed);
    UUID event = UUID.randomUUID();
    events.add(event);
    assertThat(sanctions.send(npi, "S", event)).isEqualTo(SendSanctionNotice.Outcome.FAILED);
  }

  private static RequestPostProcessor token(String role) {
    return switch (role) {
      case "ANALYST" -> PemJwt.analyst();
      case "SUPERVISOR" -> PemJwt.supervisor();
      case "ADMIN" -> PemJwt.admin();
      case "SYSTEM" -> PemJwt.system();
      case "PROVIDER" -> PemJwt.provider(A);
      case "PROVIDER_WITHOUT_NPI" -> PemJwt.user("provider-x", null, "PROVIDER");
      case "GUEST" -> PemJwt.user("guest1", null, "GUEST");
      case "NOROLE" -> PemJwt.user("nobody1", null);
      default -> null;
    };
  }

  private MockHttpServletRequestBuilder request(String uri, String role) {
    MockHttpServletRequestBuilder req = get(uri);
    if ("GARBAGE".equals(role)) {
      return req.header("Authorization", "Bearer not-a-token");
    }
    RequestPostProcessor t = token(role);
    return t == null ? req : req.with(t);
  }

  private MvcResult call(String uri, String role) throws Exception {
    return mvc.perform(request(uri, role)).andReturn();
  }

  private MvcResult callWithParam(String role, String name, String... values) throws Exception {
    return mvc.perform(request("/api/v1/notices", role).param(name, values)).andReturn();
  }

  private JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  private void assertProblem(MvcResult r, int status) throws Exception {
    assertThat(r.getResponse().getStatus()).isEqualTo(status);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    JsonNode p = body(r);
    assertThat(p.path("status").asInt()).isEqualTo(status);
    assertThat(p.path("title").asText()).isNotBlank();
  }

  private List<String> field(MvcResult r, String name) throws Exception {
    List<String> out = new ArrayList<>();
    body(r).path("items").forEach(n -> out.add(n.path(name).asText()));
    return out;
  }

  // ---- AC-1: the list
  // ------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-60 AC-1] the answer of listNotices is a NoticePage of the contract: required members,"
          + " no others, enums")
  void listFollowsTheContract() throws Exception {
    insert(A, "APPROVED");
    insert(A, "TRM");
    insert(B, "DENIED");

    MvcResult r = call("/api/v1/notices", "ANALYST");

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    OpenApiContract.of("notification-service")
        .assertResponse("listNotices", 200, r.getResponse().getContentAsString());
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-1] with an NPI filter the list holds only that provider's notices and the total"
          + " counts only them")
  void filterByNpi() throws Exception {
    insert(A, "APPROVED");
    insert(A, "TRM");
    insert(B, "DENIED");

    MvcResult r = callWithParam("ANALYST", "npi", A);

    assertThat(field(r, "npi")).containsExactly(A, A);
    assertThat(body(r).path("total").asInt()).isEqualTo(2);
    assertThat(body(r).path("page").asInt()).isZero();
    assertThat(body(r).path("size").asInt()).isEqualTo(20);
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-1] without a filter staff get the notices of every provider; the total counts"
          + " them all")
  void noFilter() throws Exception {
    insert(A, "APPROVED");
    insert(B, "DENIED");
    insert(C, "REJECTED");

    MvcResult r = call("/api/v1/notices", "ADMIN");

    assertThat(field(r, "npi")).containsExactlyInAnyOrder(A, B, C);
    assertThat(body(r).path("total").asInt()).isEqualTo(3);
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-1] notices come newest first, in pages of the size asked for, and every page"
          + " echoes its page and size and the same total")
  void newestFirstAndPaging() throws Exception {
    insert(A, "APPROVED", "SENT", "2026-11-20T10:00:01Z");
    insert(A, "DENIED", "SENT", "2026-11-20T10:00:02Z");
    insert(A, "TRM", "SENT", "2026-11-20T10:00:03Z");
    insert(A, "SUS", "SENT", "2026-11-20T10:00:04Z");
    insert(A, "RST", "SENT", "2026-11-20T10:00:05Z");
    String[][] expected = {{"RST", "SUS"}, {"TRM", "DENIED"}, {"APPROVED"}, {}};

    for (int page = 0; page < expected.length; page++) {
      MvcResult r =
          mvc.perform(
                  request("/api/v1/notices", "ANALYST")
                      .param("npi", A)
                      .param("page", String.valueOf(page))
                      .param("size", "2"))
              .andReturn();
      assertThat(field(r, "template")).as("page %d", page).containsExactly(expected[page]);
      assertThat(body(r).path("page").asInt()).isEqualTo(page);
      assertThat(body(r).path("size").asInt()).isEqualTo(2);
      assertThat(body(r).path("total").asInt()).isEqualTo(5);
    }
  }

  @Test
  @DisplayName("[MAM-60 AC-1] a sent notice carries its sent time as an ISO-8601 date-time")
  void sentAtIsIso() throws Exception {
    insert(A, "APPROVED", "SENT", "2026-11-20T10:15:30Z");

    MvcResult r = callWithParam("ANALYST", "npi", A);

    JsonNode n = body(r).path("items").get(0);
    assertThat(OffsetDateTime.parse(n.path("sentAt").asText()).toInstant())
        .isEqualTo(Instant.parse("2026-11-20T10:15:30Z"));
    assertThat(n.path("channel").asText()).isEqualTo("EMAIL");
    assertThat(n.path("status").asText()).isEqualTo("SENT");
  }

  @Test
  @DisplayName("[MAM-60 AC-1] an empty notice log is an empty page with total 0, not an error")
  void emptyLog() throws Exception {
    MvcResult r = call("/api/v1/notices", "ANALYST");

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("items")).isEmpty();
    assertThat(body(r).path("total").asInt()).isZero();
  }

  // ---- AC-2: invalid filters
  // -----------------------------------------------------------------------

  static Stream<Arguments> invalidFilters() {
    return Stream.of(
        Arguments.of("npi", "ABC"),
        Arguments.of("npi", "100300011"),
        Arguments.of("npi", "10030001111"),
        Arguments.of("npi", ""),
        Arguments.of("npi", "1003000111\n"),
        Arguments.of("npi", " 1003000111"),
        Arguments.of("npi", "1003000111 "),
        Arguments.of("npi", "1003-00111"),
        Arguments.of("npi", "1003000111,1003000444"),
        Arguments.of("page", "-1"),
        Arguments.of("page", "abc"),
        Arguments.of("page", "1.5"),
        Arguments.of("page", "99999999999"),
        Arguments.of("size", "0"),
        Arguments.of("size", "-5"),
        Arguments.of("size", "201"),
        Arguments.of("size", "500"),
        Arguments.of("size", "abc"));
  }

  @ParameterizedTest(name = "[MAM-60 AC-2] {0}={1} is refused with a 400 problem+json")
  @MethodSource("invalidFilters")
  void invalidFilterIsRefused(String name, String value) throws Exception {
    insert(A, "APPROVED");

    assertProblem(callWithParam("ANALYST", name, value), 400);
  }

  @Test
  @DisplayName("[MAM-60 AC-2] the NPI named twice is refused with a 400 problem+json")
  void npiTwiceIsRefused() throws Exception {
    insert(A, "APPROVED");

    assertProblem(callWithParam("ANALYST", "npi", A, B), 400);
  }

  @ParameterizedTest(name = "[MAM-60 AC-2] the page size {0} is accepted and echoed")
  @ValueSource(ints = {1, 20, 199, 200})
  void validSizesAreAccepted(int size) throws Exception {
    insert(A, "APPROVED");

    MvcResult r = callWithParam("ANALYST", "size", String.valueOf(size));

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    assertThat(body(r).path("size").asInt()).isEqualTo(size);
  }

  @ParameterizedTest(
      name =
          "[MAM-60 AC-2] the NPI filter ''{0}'' is refused, never used as a pattern: % _ and a"
              + " backslash are not wildcards (lesson e)")
  @ValueSource(
      strings = {
        "%",
        "1003900%",
        "10039005%1",
        "1003900501%",
        "%1003900501",
        "_",
        "100390050_",
        "1003900__1",
        "\\",
        "1003900501\\"
      })
  void wildcardsAreNotWildcards(String value) throws Exception {
    insert(A, "APPROVED");
    insert(B, "DENIED");

    MvcResult r = callWithParam("ANALYST", "npi", value);

    assertProblem(r, 400);
    assertThat(r.getResponse().getContentAsString()).doesNotContain(A).doesNotContain(B);
  }

  // ---- AC-3: a provider sees only their own notices
  // ---------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-60 AC-3] a provider is listed only their own notices; the total, the paging and the"
          + " NPI filter all stay within them")
  void providerSeesOnlyTheirOwn() throws Exception {
    insert(A, "APPROVED", "SENT", "2026-11-20T10:00:01Z");
    insert(A, "TRM", "SENT", "2026-11-20T10:00:02Z");
    insert(A, "SUS", "SENT", "2026-11-20T10:00:03Z");
    insert(B, "DENIED", "SENT", "2026-11-20T10:00:04Z");
    insert(B, "RST", "SENT", "2026-11-20T10:00:05Z");

    MvcResult all = call("/api/v1/notices", "PROVIDER");
    assertThat(field(all, "npi")).containsExactly(A, A, A);
    assertThat(body(all).path("total").asInt()).isEqualTo(3);

    MvcResult second =
        mvc.perform(request("/api/v1/notices", "PROVIDER").param("page", "1").param("size", "2"))
            .andReturn();
    assertThat(field(second, "template")).containsExactly("APPROVED");
    assertThat(body(second).path("total").asInt()).isEqualTo(3);

    MvcResult named = callWithParam("PROVIDER", "npi", A);
    assertThat(field(named, "npi")).containsExactly(A, A, A);
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-3] a provider who names another NPI gets 403 problem+json, whether or not that"
          + " provider has notices")
  void providerCannotNameAnotherNpi() throws Exception {
    insert(B, "DENIED");

    assertProblem(callWithParam("PROVIDER", "npi", B), 403);
    assertProblem(callWithParam("PROVIDER", "npi", C), 403);
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-3] a provider who names their own NPI and another one together does not get the"
          + " other provider's notices")
  void providerCannotSmuggleAnotherNpi() throws Exception {
    insert(A, "APPROVED");
    insert(B, "DENIED");

    MvcResult r = callWithParam("PROVIDER", "npi", A, B);

    assertThat(r.getResponse().getStatus()).isIn(400, 403);
    assertThat(r.getResponse().getContentAsString()).doesNotContain("DENIED");
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-3] a PROVIDER token without an npi claim is refused with 403 problem+json")
  void providerWithoutNpiClaim() throws Exception {
    insert(A, "APPROVED");

    assertProblem(call("/api/v1/notices", "PROVIDER_WITHOUT_NPI"), 403);
  }

  // ---- AC-5: a FAILED notice
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-60 AC-5] the notice of a send that really failed is listed with status FAILED,"
          + " channel EMAIL and its template")
  void realFailedNoticeIsListed() throws Exception {
    realFailedNotice(A);

    MvcResult r = callWithParam("ANALYST", "npi", A);

    JsonNode n = body(r).path("items").get(0);
    assertThat(body(r).path("total").asInt()).isEqualTo(1);
    assertThat(n.path("status").asText()).isEqualTo("FAILED");
    assertThat(n.path("template").asText()).isEqualTo("SUS");
    assertThat(n.path("channel").asText()).isEqualTo("EMAIL");
    assertThat(n.path("npi").asText()).isEqualTo(A);
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-5] the notice of a send that really failed has no sent time: the member sentAt is"
          + " absent, not null (the contract says string)")
  void failedNoticeHasNoNullSentAt() throws Exception {
    realFailedNotice(A);

    JsonNode n = body(callWithParam("ANALYST", "npi", A)).path("items").get(0);

    assertThat(n.has("sentAt") && n.get("sentAt").isNull())
        .as("sentAt of a FAILED notice is %s", n.get("sentAt"))
        .isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-5] a list with the notice of a send that really failed follows the NoticePage"
          + " contract")
  void listWithAFailedNoticeFollowsTheContract() throws Exception {
    insert(A, "APPROVED");
    realFailedNotice(A);

    MvcResult r = callWithParam("ANALYST", "npi", A);

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    OpenApiContract.of("notification-service")
        .assertResponse("listNotices", 200, r.getResponse().getContentAsString());
  }

  // ---- AC-6, AC-8: who may call
  // ----------------------------------------------------------------------------

  @ParameterizedTest(name = "[MAM-60 AC-6] GET /api/v1/notices with {0} answers {1}")
  @CsvSource({
    "ANALYST,200",
    "SUPERVISOR,200",
    "ADMIN,200",
    "PROVIDER,200",
    "SYSTEM,403",
    "GUEST,403",
    "NOROLE,403",
    "NONE,401",
    "GARBAGE,401"
  })
  void rolesOfTheNoticeList(String role, int status) throws Exception {
    insert(A, "APPROVED");

    MvcResult r = call("/api/v1/notices", role);

    if (status == 200) {
      assertThat(r.getResponse().getStatus()).isEqualTo(200);
    } else {
      assertProblem(r, status);
    }
  }

  @ParameterizedTest(name = "[MAM-60 AC-8] GET /api/v1/notice-templates with {0} answers {1}")
  @CsvSource({
    "ADMIN,200",
    "ANALYST,403",
    "SUPERVISOR,403",
    "PROVIDER,403",
    "SYSTEM,403",
    "GUEST,403",
    "NOROLE,403",
    "NONE,401",
    "GARBAGE,401"
  })
  void rolesOfTheTemplateList(String role, int status) throws Exception {
    MvcResult r = call("/api/v1/notice-templates", role);

    if (status == 200) {
      assertThat(r.getResponse().getStatus()).isEqualTo(200);
    } else {
      assertProblem(r, status);
    }
  }

  // ---- AC-7: the templates
  // ---------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-60 AC-7] the template list follows the contract: nine templates with code and subject"
          + " (and body), and the contract's template codes")
  void templatesFollowTheContract() throws Exception {
    MvcResult r = call("/api/v1/notice-templates", "ADMIN");

    assertThat(r.getResponse().getStatus()).isEqualTo(200);
    OpenApiContract.of("notification-service")
        .assertResponse("listNoticeTemplates", 200, r.getResponse().getContentAsString());
    List<String> codes = new ArrayList<>();
    body(r).forEach(t -> codes.add(t.path("code").asText()));
    assertThat(codes)
        .containsExactlyInAnyOrder(
            "TRM",
            "SUS",
            "RST",
            "GEN",
            "APPROVED",
            "DENIED",
            "REJECTED",
            "REVALIDATION_DUE",
            "EXTRACT_READY");
  }

  @Test
  @DisplayName(
      "[MAM-60 AC-7] every template body carries the NPI placeholder, so a notice always names its"
          + " provider (Q-A8), and the subjects of the fixed notices are those of J1 and J2")
  void everyBodyHasTheNpi() throws Exception {
    JsonNode templates = body(call("/api/v1/notice-templates", "ADMIN"));

    assertThat(templates).hasSize(9);
    templates.forEach(t -> assertThat(t.path("body").asText()).contains("{npi}"));
    assertThat(subject(templates, "APPROVED")).isEqualTo("Enrolment approved");
    assertThat(subject(templates, "REJECTED")).isEqualTo("Application rejected");
  }

  private static String subject(JsonNode templates, String code) {
    for (JsonNode t : templates) {
      if (t.path("code").asText().equals(code)) {
        return t.path("subject").asText();
      }
    }
    throw new AssertionError("no template " + code);
  }

  // ---- lesson g: the correlation id of a request
  // ------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-60 AC-1] an X-Correlation-Id header with characters that could forge a log line is"
          + " replaced in the response, a safe one is echoed (lesson g)")
  void correlationIdHeader() throws Exception {
    insert(A, "APPROVED");

    MvcResult hostile =
        mvc.perform(
                request("/api/v1/notices", "ANALYST")
                    .header("X-Correlation-Id", "bad id <script>\r\nforged"))
            .andReturn();
    MvcResult safe =
        mvc.perform(request("/api/v1/notices", "ANALYST").header("X-Correlation-Id", "req-123"))
            .andReturn();

    String echoed = hostile.getResponse().getHeader("X-Correlation-Id");
    assertThat(echoed).isNotNull().isNotEqualTo("bad id <script>\r\nforged").matches(SAFE);
    assertThat(safe.getResponse().getHeader("X-Correlation-Id")).isEqualTo("req-123");
  }
}
