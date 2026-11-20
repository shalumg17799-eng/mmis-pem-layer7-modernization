package gov.medicaid.pem.tmsis.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestClock;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.Tmsisout;
import gov.medicaid.pem.tmsis.infrastructure.TmsisSchedule;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

/**
 * MAM-75 AC-7 (ADR-004): the monthly schedule is configured (a cron in configuration, month end),
 * the task registered with the scheduler runs the extract for the injected Clock's date and no
 * explicit business date, and the run it starts completes with records whose period is that date's.
 * The cron instant itself is not waited for: the registered task is run directly, and the trigger
 * is asked when it would fire next.
 */
@Timeout(120)
class ScheduledRunTest extends IntegrationTest {

  @Autowired ScheduledTaskHolder scheduler;
  @Autowired Environment environment;
  @Autowired TmsisSchedule schedule;

  /** The cron task the scheduler holds for TmsisSchedule.monthly(). */
  CronTask monthlyTask() {
    Set<ScheduledTask> tasks = scheduler.getScheduledTasks();
    return tasks.stream()
        .map(ScheduledTask::getTask)
        .filter(CronTask.class::isInstance)
        .map(CronTask.class::cast)
        .filter(t -> String.valueOf(t.getRunnable()).endsWith("TmsisSchedule.monthly"))
        .findFirst()
        .orElseThrow(
            () -> new AssertionError("TmsisSchedule.monthly() is not scheduled: " + tasks));
  }

  Instant nextFiring(Instant after) {
    CronTrigger trigger = (CronTrigger) monthlyTask().getTrigger();
    return trigger.nextExecution(new SimpleTriggerContext(Clock.fixed(after, ZoneOffset.UTC)));
  }

  // ---- the schedule is configured
  // ----------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the scheduler holds the monthly task with the cron of the configuration: 22:00 on the last day of every month")
  void cronIsInConfiguration() {
    assertThat(monthlyTask().getExpression()).isEqualTo("0 0 22 L * *");
    assertThat(environment.getProperty("pem.tmsis.cron")).isEqualTo("0 0 22 L * *");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the cron and its zone are read from configuration, not written into the code")
  void cronAndZoneArePlaceholders() throws Exception {
    Method monthly = TmsisSchedule.class.getMethod("monthly");
    Scheduled scheduled = monthly.getAnnotation(Scheduled.class);
    assertThat(scheduled.cron()).startsWith("${pem.tmsis.cron");
    assertThat(scheduled.zone()).startsWith("${pem.tmsis.zone");
    assertThat(environment.getProperty("pem.tmsis.zone")).isEqualTo("UTC");
  }

  @ParameterizedTest(name = "[MAM-75 AC-7] after {0} the schedule fires next at {1}")
  @CsvSource({
    "2026-04-01T00:00:00Z, 2026-04-30T22:00:00Z",
    "2026-04-30T22:00:01Z, 2026-05-31T22:00:00Z",
    "2026-02-01T00:00:00Z, 2026-02-28T22:00:00Z",
    "2028-02-01T00:00:00Z, 2028-02-29T22:00:00Z",
    "2026-12-01T00:00:00Z, 2026-12-31T22:00:00Z",
    "2026-01-15T10:00:00Z, 2026-01-31T22:00:00Z"
  })
  void firesAtTheEndOfTheMonth(String after, String next) {
    assertThat(nextFiring(Instant.parse(after))).isEqualTo(Instant.parse(next));
  }

  // ---- the scheduled run uses the Clock's date -------------------------------------------------

  /** The run the scheduled task started: the only run in the list. */
  JsonNode onlyRun() throws Exception {
    Awaitility.await("the scheduled task has started a run")
        .atMost(Duration.ofSeconds(20))
        .pollInterval(Duration.ofMillis(100))
        .until(() -> api.list(0, 20).path("total").asInt() >= 1);
    JsonNode page = api.list(0, 20);
    assertThat(page.path("items")).hasSize(1);
    return api.await(page.path("items").get(0).path("runId").asText());
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the registered task, run with the Clock at 2026-04-30, starts a COMPLETED run for the business date 2026-04-30 whose records hold 26120")
  void scheduledRunUsesTheClocksDate() throws Exception {
    TestProviderService.holds(Prov.active("1003000333"), Prov.active("1003000111"));
    TestClock.set("2026-04-30T22:00:00Z");
    monthlyTask().getRunnable().run();
    JsonNode run = onlyRun();
    assertThat(run.path("status").asText()).as(run.toString()).isEqualTo("COMPLETED");
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-04-30");
    List<String> records = Tmsisout.records(api.file(run.path("runId").asText()));
    assertThat(records).hasSize(2);
    assertThat(records).allSatisfy(r -> assertThat(Tmsisout.period(r)).isEqualTo("26120"));
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the schedule bean's monthly() does the same: the business date is the Clock's, whatever day the system is on")
  void monthlyMethodUsesTheClocksDate() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    TestClock.set("2026-12-31T22:00:00Z");
    schedule.monthly();
    JsonNode run = onlyRun();
    assertThat(run.path("businessDate").asText()).isEqualTo("2026-12-31");
    assertThat(Tmsisout.period(Tmsisout.records(api.file(run.path("runId").asText())).get(0)))
        .isEqualTo("26365");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the scheduled run is archived and announced like a manual one: generation 1 and one ExtractProduced event")
  void scheduledRunIsArchivedAndAnnounced() throws Exception {
    TestProviderService.holds(Prov.active("1003000111"));
    TestClock.set("2026-04-30T22:00:00Z");
    schedule.monthly();
    JsonNode run = onlyRun();
    assertThat(run.path("counts").path("archiveGeneration").asInt()).isEqualTo(1);
    List<String> events = extractProducedEnvelopes();
    assertThat(events).hasSize(1);
    assertThat(json.readTree(events.get(0)).path("data").path("periodYyddd").asText())
        .isEqualTo("26120");
  }
}
