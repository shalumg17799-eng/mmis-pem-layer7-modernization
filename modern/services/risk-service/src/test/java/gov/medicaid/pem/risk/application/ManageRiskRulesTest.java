package gov.medicaid.pem.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import gov.medicaid.pem.kit.web.ApiException;
import gov.medicaid.pem.risk.domain.RuleTable;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * MAM-38 (question C-9): the table in force can be read, and replaced by naming the version it was
 * based on. Stale version or inconsistent table: 422; malformed: 400; saved as the next version in
 * force from the business date; no event is involved (AC-5). Business date 2026-01-15 (fixed
 * Clock).
 */
class ManageRiskRulesTest {

  static final LocalDate TODAY = LocalDate.of(2026, 1, 15);
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-15T10:00:00Z"), ZoneOffset.UTC);

  /** The stored versions in order; the last one is in force. */
  static class MemoryTables implements RuleTables {

    final List<RuleTable> versions = new ArrayList<>();
    boolean someoneSavedFirst;

    MemoryTables(int inForce) {
      for (int v = 1; v <= inForce; v++) {
        versions.add(new RuleTable(v, LocalDate.of(2000, 1, 1).plusYears(v - 1), "L", legacy()));
      }
    }

    @Override
    public RuleTable current() {
      return versions.get(versions.size() - 1);
    }

    @Override
    public boolean add(RuleTable table) {
      if (someoneSavedFirst) {
        return false;
      }
      versions.add(table);
      return true;
    }
  }

  static List<RuleTable.Rule> legacy() {
    return List.of(
        new RuleTable.Rule("332", "H"),
        new RuleTable.Rule("251", "H"),
        new RuleTable.Rule("341", "M"),
        new RuleTable.Rule("291", "M"),
        new RuleTable.Rule("225", "M"));
  }

  static List<RuleTable.Rule> legacyPlus(RuleTable.Rule extra) {
    List<RuleTable.Rule> all = new ArrayList<>(legacy());
    all.add(extra);
    return all;
  }

  static ApiException refused(ThrowingCallable call) {
    Throwable t = catchThrowable(call);
    assertThat(t).isInstanceOf(ApiException.class);
    return (ApiException) t;
  }

  final ListAppender<ILoggingEvent> log = new ListAppender<>();
  final Logger logger = (Logger) LoggerFactory.getLogger(ManageRiskRules.class);

  @BeforeEach
  void listen() {
    logger.setLevel(Level.INFO);
    log.start();
    logger.addAppender(log);
  }

  @AfterEach
  void stopListening() {
    logger.detachAppender(log);
  }

  // ---- read
  // --------------------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-38 AC-1] reading gives the table in force")
  void current() {
    MemoryTables tables = new MemoryTables(1);

    assertThat(new ManageRiskRules(tables, CLOCK).current()).isSameAs(tables.current());
  }

  // ---- save
  // --------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-2] a table based on version 1 is saved as version 2, in force from the business date")
  void savedAsNextVersion() {
    MemoryTables tables = new MemoryTables(1);
    RuleTable v1 = tables.current();
    List<RuleTable.Rule> proposed = legacyPlus(new RuleTable.Rule("207", "M"));

    RuleTable saved = new ManageRiskRules(tables, CLOCK).replace(1, "L", proposed);

    assertThat(saved.version()).isEqualTo(2);
    assertThat(saved.effectiveFrom()).isEqualTo(TODAY);
    assertThat(saved.defaultTier()).isEqualTo("L");
    assertThat(saved.rules()).containsExactlyElementsOf(proposed);
    assertThat(tables.versions).containsExactly(v1, saved);
    assertThat(tables.current()).isSameAs(saved);
  }

  @Test
  @DisplayName("[MAM-38 AC-2] version 1 is kept as it was: nothing of the old table is changed")
  void oldVersionKept() {
    MemoryTables tables = new MemoryTables(1);

    new ManageRiskRules(tables, CLOCK).replace(1, "M", List.of(new RuleTable.Rule("207", "M")));

    RuleTable v1 = tables.versions.get(0);
    assertThat(v1.version()).isEqualTo(1);
    assertThat(v1.defaultTier()).isEqualTo("L");
    assertThat(v1.rules()).containsExactlyElementsOf(legacy());
  }

  @Test
  @DisplayName("[MAM-38 AC-2] the next version follows the version in force, not a fixed number")
  void nextVersionFollowsTheOneInForce() {
    MemoryTables tables = new MemoryTables(4);

    RuleTable saved = new ManageRiskRules(tables, CLOCK).replace(4, "L", legacy());

    assertThat(saved.version()).isEqualTo(5);
    assertThat(tables.versions).hasSize(5);
  }

  @Test
  @DisplayName("[MAM-38 AC-2] two saves in a row give versions 2 and 3")
  void twoSaves() {
    MemoryTables tables = new MemoryTables(1);
    ManageRiskRules manage = new ManageRiskRules(tables, CLOCK);

    assertThat(manage.replace(1, "L", legacy()).version()).isEqualTo(2);
    assertThat(manage.replace(2, "L", legacyPlus(new RuleTable.Rule("207", "M"))).version())
        .isEqualTo(3);
    assertThat(manage.current().rules()).hasSize(6);
  }

  @Test
  @DisplayName("[MAM-38 AC-2] the new version takes the default tier of the request")
  void defaultTierOfTheRequest() {
    MemoryTables tables = new MemoryTables(1);

    RuleTable saved = new ManageRiskRules(tables, CLOCK).replace(1, "H", legacy());

    assertThat(saved.defaultTier()).isEqualTo("H");
    assertThat(tables.current().defaultTier()).isEqualTo("H");
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-2] a table with no rules is saved too: every taxonomy then gets the default tier")
  void saveWithoutRules() {
    MemoryTables tables = new MemoryTables(1);

    RuleTable saved = new ManageRiskRules(tables, CLOCK).replace(1, "M", List.of());

    assertThat(saved.version()).isEqualTo(2);
    assertThat(saved.rules()).isEmpty();
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-2] the saved version is logged with its number, size, default tier and date")
  void logged() {
    new ManageRiskRules(new MemoryTables(1), CLOCK)
        .replace(1, "L", legacyPlus(new RuleTable.Rule("207", "M")));

    assertThat(log.list)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.INFO);
              assertThat(e.getFormattedMessage())
                  .contains("version 2")
                  .contains("6 rules")
                  .contains("default tier L")
                  .contains("2026-01-15");
            });
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a refused save logs nothing as saved")
  void refusedNotLogged() {
    ManageRiskRules manage = new ManageRiskRules(new MemoryTables(2), CLOCK);

    refused(() -> manage.replace(1, "L", legacy()));

    assertThat(log.list).isEmpty();
  }

  // ---- 400: malformed ---------------------------------------------------------------------------

  @Test
  @DisplayName("[MAM-38 AC-4] a request that names no version is a 400")
  void noVersion() {
    MemoryTables tables = new MemoryTables(1);

    ApiException e = refused(() -> new ManageRiskRules(tables, CLOCK).replace(null, "L", legacy()));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("version is required");
    assertThat(tables.versions).hasSize(1);
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] default tier \"{0}\" is a 400 and nothing is saved")
  @NullSource
  @ValueSource(strings = {"", "X", "l"})
  void badDefaultTier(String tier) {
    MemoryTables tables = new MemoryTables(1);

    ApiException e = refused(() -> new ManageRiskRules(tables, CLOCK).replace(1, tier, legacy()));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("defaultTier");
    assertThat(tables.versions).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-38 AC-4] no rules list is a 400")
  void noRulesList() {
    MemoryTables tables = new MemoryTables(1);

    ApiException e = refused(() -> new ManageRiskRules(tables, CLOCK).replace(1, "L", null));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("rules are required");
    assertThat(tables.versions).hasSize(1);
  }

  @ParameterizedTest(
      name = "[MAM-38 AC-4] a rule with prefix \"{0}\" is a 400 and nothing is saved")
  @ValueSource(strings = {"20", "20q", "2077", ""})
  void badPrefix(String prefix) {
    MemoryTables tables = new MemoryTables(1);

    ApiException e =
        refused(
            () ->
                new ManageRiskRules(tables, CLOCK)
                    .replace(1, "L", legacyPlus(new RuleTable.Rule(prefix, "M"))));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("taxonomyPrefix");
    assertThat(tables.versions).hasSize(1);
  }

  @ParameterizedTest(name = "[MAM-38 AC-4] a rule with tier \"{0}\" is a 400 and nothing is saved")
  @NullSource
  @ValueSource(strings = {"X", "", "m"})
  void badTier(String tier) {
    MemoryTables tables = new MemoryTables(1);

    ApiException e =
        refused(
            () ->
                new ManageRiskRules(tables, CLOCK)
                    .replace(1, "L", legacyPlus(new RuleTable.Rule("207", tier))));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(e.getMessage()).contains("tier must be L, M or H");
    assertThat(tables.versions).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a missing entry in the rules list is a 400")
  void emptyEntry() {
    MemoryTables tables = new MemoryTables(1);

    ApiException e =
        refused(
            () ->
                new ManageRiskRules(tables, CLOCK)
                    .replace(1, "L", Arrays.asList(new RuleTable.Rule("332", "H"), null)));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(tables.versions).hasSize(1);
  }

  @Test
  @DisplayName("[MAM-38 AC-4] a malformed table is a 400 even when its version is stale")
  void malformedBeforeStale() {
    MemoryTables tables = new MemoryTables(2);

    ApiException e =
        refused(
            () ->
                new ManageRiskRules(tables, CLOCK)
                    .replace(1, "L", legacyPlus(new RuleTable.Rule("207", "X"))));

    assertThat(e.status()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  // ---- 422: stale or inconsistent ---------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-38 AC-3] a table based on version 1 when version 2 is in force is a 422 and nothing is saved")
  void staleVersion() {
    MemoryTables tables = new MemoryTables(2);

    ApiException e = refused(() -> new ManageRiskRules(tables, CLOCK).replace(1, "L", legacy()));

    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(e.getMessage()).contains("version 1").contains("version 2").contains("reload");
    assertThat(tables.versions).hasSize(2);
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a table based on a version ahead of the one in force is a 422 too")
  void versionAhead() {
    MemoryTables tables = new MemoryTables(2);

    ApiException e = refused(() -> new ManageRiskRules(tables, CLOCK).replace(3, "L", legacy()));

    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(tables.versions).hasSize(2);
  }

  @Test
  @DisplayName(
      "[MAM-38 AC-3] a table that lists prefix 332 twice is a 422 naming the prefix, nothing is saved")
  void duplicatePrefix() {
    MemoryTables tables = new MemoryTables(2);

    ApiException e =
        refused(
            () ->
                new ManageRiskRules(tables, CLOCK)
                    .replace(2, "L", legacyPlus(new RuleTable.Rule("332", "M"))));

    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(e.getMessage()).contains("332").contains("more than once");
    assertThat(tables.versions).hasSize(2);
    assertThat(tables.current().version()).isEqualTo(2);
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a stale version is reported before a duplicate prefix")
  void staleBeforeDuplicate() {
    MemoryTables tables = new MemoryTables(2);

    ApiException e =
        refused(
            () ->
                new ManageRiskRules(tables, CLOCK)
                    .replace(1, "L", legacyPlus(new RuleTable.Rule("332", "M"))));

    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(e.getMessage()).contains("not the table in force");
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a save that lost the race to another administrator is a 422")
  void lostTheRace() {
    MemoryTables tables = new MemoryTables(1);
    tables.someoneSavedFirst = true;

    ApiException e = refused(() -> new ManageRiskRules(tables, CLOCK).replace(1, "L", legacy()));

    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(e.getMessage()).contains("replaced by someone else").contains("reload");
    assertThat(tables.versions).hasSize(1);
    assertThat(log.list).isEmpty();
  }

  @Test
  @DisplayName("[MAM-38 AC-3] a business refusal carries no legacy error code and no field errors")
  void noCodes() {
    ApiException e =
        refused(() -> new ManageRiskRules(new MemoryTables(2), CLOCK).replace(1, "L", legacy()));

    assertThat(e.code()).isNull();
    assertThat(e.errors()).isEmpty();
  }
}
