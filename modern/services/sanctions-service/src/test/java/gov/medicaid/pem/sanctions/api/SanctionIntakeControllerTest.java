package gov.medicaid.pem.sanctions.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gov.medicaid.pem.kit.web.ProblemHandler;
import gov.medicaid.pem.sanctions.IntakeApi;
import gov.medicaid.pem.sanctions.SancinFile;
import gov.medicaid.pem.sanctions.application.ProviderStatusPort;
import gov.medicaid.pem.sanctions.application.RecordSanction;
import gov.medicaid.pem.sanctions.application.SanctionHistory;
import gov.medicaid.pem.sanctions.application.SanctionIntake;
import gov.medicaid.pem.sanctions.application.SanctionIntakeLauncher;
import gov.medicaid.pem.sanctions.application.SanctionIntakeRuns;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionOperations;

/**
 * MAM-55 unit test of SanctionIntakeController without Spring's context: what the endpoint hands to
 * the job (run id, business date, the file's bytes, the login of the caller as recordedBy, AC-3),
 * and what it answers when the job cannot be started (AC-6: the run does not stay RUNNING).
 */
class SanctionIntakeControllerTest {

  static final Instant NOW = Instant.parse("2026-11-19T10:00:00Z");

  record Launch(UUID runId, LocalDate businessDate, byte[] file, String recordedBy) {}

  /** sanction_intake_run in memory: the state a run has after create, complete and fail. */
  static final class MemoryRuns implements SanctionIntakeRuns {
    final Map<UUID, Run> byId = new LinkedHashMap<>();

    @Override
    public void create(UUID runId, LocalDate businessDate, Instant startedAt) {
      byId.put(
          runId, new Run(runId, businessDate, "RUNNING", 0, 0, 0, 0, 0, null, startedAt, null));
    }

    @Override
    public void counts(UUID runId, int read, int applied, int notFound, int rejected) {}

    @Override
    public void link(UUID sanctionId, UUID runId) {}

    @Override
    public boolean alreadyApplied(
        UUID runId,
        LocalDate businessDate,
        String npi,
        String action,
        String reason,
        LocalDate date) {
      return false;
    }

    @Override
    public void complete(UUID runId, Instant endedAt) {
      Run r = byId.get(runId);
      byId.put(
          runId,
          new Run(
              runId,
              r.businessDate(),
              "COMPLETED",
              r.read(),
              r.applied(),
              r.notFound(),
              r.rejected(),
              r.terminated(),
              null,
              r.startedAt(),
              endedAt));
    }

    @Override
    public void fail(UUID runId, String error, Instant endedAt) {
      Run r = byId.get(runId);
      byId.put(
          runId,
          new Run(
              runId,
              r.businessDate(),
              "FAILED",
              r.read(),
              r.applied(),
              r.notFound(),
              r.rejected(),
              r.terminated(),
              error,
              r.startedAt(),
              endedAt));
    }

    @Override
    public Optional<Run> find(UUID runId) {
      return Optional.ofNullable(byId.get(runId));
    }
  }

  final ObjectMapper json = new ObjectMapper();
  final MemoryRuns runs = new MemoryRuns();
  final List<Launch> launches = new ArrayList<>();
  RuntimeException launchFailure;
  final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  final SanctionIntakeLauncher launcher =
      (runId, businessDate, file, recordedBy) -> {
        launches.add(new Launch(runId, businessDate, file, recordedBy));
        if (launchFailure != null) {
          throw launchFailure;
        }
      };

  final SanctionIntake intake =
      new SanctionIntake(
          runs,
          new RecordSanction(
              (npi, action, reason, effectiveDate, sanctionId) ->
                  ProviderStatusPort.Outcome.APPLIED,
              mock(SanctionHistory.class),
              (type, subject, data) -> {},
              TransactionOperations.withoutTransaction(),
              clock),
          clock);

  /** Spring Boot's JSON settings: dates as ISO text, not as numbers. */
  final MappingJackson2HttpMessageConverter converter =
      new MappingJackson2HttpMessageConverter(
          Jackson2ObjectMapperBuilder.json()
              .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
              .build());

  final MockMvc mvc =
      standaloneSetup(new SanctionIntakeController(intake, launcher, runs))
          .setMessageConverters(converter)
          .setControllerAdvice(new ProblemHandler())
          .build();

  @AfterEach
  void signOut() {
    SecurityContextHolder.clearContext();
  }

  static void signIn(String subject, String preferredUsername) {
    Jwt.Builder jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject);
    if (preferredUsername != null) {
      jwt.claim("preferred_username", preferredUsername);
    }
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt.build(), List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
  }

  byte[] file() {
    return SancinFile.lines(
        SancinFile.record("1003000111", "T", "SYNTHETIC TEST REASON", "20260331"));
  }

  MvcResult start(byte[] bytes, String date) throws Exception {
    var request =
        multipart(IntakeApi.BASE)
            .file(new MockMultipartFile("file", "sancin.dat", "application/octet-stream", bytes))
            .param("businessDate", date);
    return mvc.perform(request).andReturn();
  }

  JsonNode body(MvcResult r) throws Exception {
    return json.readTree(r.getResponse().getContentAsString());
  }

  // ---- AC-1, AC-2: what the job is given -------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-1] the 202 answer is the run just opened: RUNNING, the job PRVSAN01 and the business date")
  void accepted() throws Exception {
    signIn("sub-admin1", "admin1");
    MvcResult r = start(file(), "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    JsonNode run = body(r);
    assertThat(run.path("status").asText()).isEqualTo("RUNNING");
    assertThat(run.path("job").asText()).isEqualTo("PRVSAN01");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-03-31");
    assertThat(run.path("startedAt").asText()).isEqualTo("2026-11-19T10:00:00Z");
    assertThat(run.has("endedAt")).isFalse();
    assertThat(run.has("error")).isFalse();
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-2] the job gets the run id of the answer, the business date and the bytes of the file")
  void launchArguments() throws Exception {
    signIn("sub-admin1", "admin1");
    byte[] bytes = file();
    MvcResult r = start(bytes, "2026-03-31");
    assertThat(launches).hasSize(1);
    Launch l = launches.get(0);
    assertThat(l.runId().toString()).isEqualTo(body(r).path("runId").asText());
    assertThat(l.businessDate()).isEqualTo(LocalDate.of(2026, 3, 31));
    assertThat(l.file()).isEqualTo(bytes);
  }

  @Test
  @DisplayName("[MAM-55 AC-1] a refused request starts no job")
  void refusedRequestStartsNothing() throws Exception {
    signIn("sub-admin1", "admin1");
    MvcResult r = start(file(), "31/03/2026");
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(launches).isEmpty();
    assertThat(runs.byId).isEmpty();
  }

  /** A multipart file whose bytes cannot be read, as when the upload broke off. */
  static final class UnreadableFile extends MockMultipartFile {
    UnreadableFile() {
      super("file", "sancin.dat", "application/octet-stream", new byte[] {1});
    }

    @Override
    public byte[] getBytes() throws IOException {
      throw new IOException("the upload broke off");
    }
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-1] an upload that cannot be read is a 400 problem+json: no job is started and no run is created")
  void unreadableUpload() throws Exception {
    signIn("sub-admin1", "admin1");
    MvcResult r =
        mvc.perform(
                multipart(IntakeApi.BASE)
                    .file(new UnreadableFile())
                    .param("businessDate", "2026-03-31"))
            .andReturn();
    assertThat(r.getResponse().getStatus()).isEqualTo(400);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
    assertThat(body(r).path("detail").asText()).contains("file");
    assertThat(launches).isEmpty();
    assertThat(runs.byId).isEmpty();
  }

  // ---- AC-3: recordedBy ------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-3] recordedBy is the preferred_username claim of the caller's token")
  void recordedByIsTheLogin() throws Exception {
    signIn("sub-admin1", "admin1");
    start(file(), "2026-03-31");
    assertThat(launches.get(0).recordedBy()).isEqualTo("admin1");
  }

  @Test
  @DisplayName("[MAM-55 AC-3] without that claim recordedBy is the principal's name")
  void recordedByFallsBackToThePrincipal() throws Exception {
    signIn("sub-admin1", null);
    start(file(), "2026-03-31");
    assertThat(launches.get(0).recordedBy()).isEqualTo("sub-admin1");
  }

  @Test
  @DisplayName(
      "[MAM-55 AC-3] a caller that is not signed in by a token is recorded under the principal's name")
  void recordedByForANonTokenPrincipal() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "legacy-batch", "n/a", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    start(file(), "2026-03-31");
    assertThat(launches.get(0).recordedBy()).isEqualTo("legacy-batch");
  }

  // ---- AC-6: a job that cannot be started
  // --------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-55 AC-6] when the job cannot be started the run is FAILED with the reason, not left RUNNING")
  void launchFailure() throws Exception {
    signIn("sub-admin1", "admin1");
    launchFailure = new IllegalStateException("job PRVSAN01 not started: boom");
    MvcResult r = start(file(), "2026-03-31");
    assertThat(r.getResponse().getStatus()).isEqualTo(202);
    JsonNode run = body(r);
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).isEqualTo("job PRVSAN01 not started: boom");
    assertThat(run.path("endedAt").asText()).isEqualTo("2026-11-19T10:00:00Z");
  }

  @Test
  @DisplayName("[MAM-55 AC-6] a launch failure without a message is reported by its exception text")
  void launchFailureWithoutMessage() throws Exception {
    signIn("sub-admin1", "admin1");
    launchFailure = new IllegalStateException();
    JsonNode run = body(start(file(), "2026-03-31"));
    assertThat(run.path("status").asText()).isEqualTo("FAILED");
    assertThat(run.path("error").asText()).isEqualTo("java.lang.IllegalStateException");
  }

  // ---- AC-8: reading ---------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-55 AC-8] reading a run that does not exist is 404 problem+json")
  void unknownRun() throws Exception {
    MvcResult r = IntakeApi.read(mvc, "00000000-0000-0000-0000-000000000000", null);
    assertThat(r.getResponse().getStatus()).isEqualTo(404);
    assertThat(r.getResponse().getContentType()).startsWith("application/problem+json");
  }

  @Test
  @DisplayName("[MAM-55 AC-4] a run read back shows the counts in the six contract names")
  void readCounts() throws Exception {
    signIn("sub-admin1", "admin1");
    UUID id = UUID.randomUUID();
    runs.create(id, LocalDate.of(2026, 3, 31), NOW);
    runs.byId.put(
        id,
        new SanctionIntakeRuns.Run(
            id, LocalDate.of(2026, 3, 31), "COMPLETED", 5, 2, 1, 1, 2, null, NOW, NOW));
    JsonNode counts = body(IntakeApi.read(mvc, id.toString(), null)).path("counts");
    assertThat(counts.path("read").asInt()).isEqualTo(5);
    assertThat(counts.path("applied").asInt()).isEqualTo(2);
    assertThat(counts.path("notFound").asInt()).isEqualTo(1);
    assertThat(counts.path("rejected").asInt()).isEqualTo(1);
    assertThat(counts.path("skipped").asInt()).isEqualTo(1);
    assertThat(counts.path("terminated").asInt()).isEqualTo(2);
  }
}
