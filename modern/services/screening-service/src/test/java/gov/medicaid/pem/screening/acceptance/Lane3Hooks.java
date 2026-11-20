package gov.medicaid.pem.screening.acceptance;

import static gov.medicaid.pem.screening.TestSources.MOCK;

import gov.medicaid.pem.screening.ScreeningWorld;
import gov.medicaid.pem.screening.TestSources;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every scenario of MAM-20, MAM-22, MAM-28 and MAM-32 starts in an empty mocked world: no stub, no
 * request log, no screening, run or event of an earlier scenario (the hook of ScreeningSteps does
 * the same for MAM-18 and MAM-25).
 */
public class Lane3Hooks {

  @Autowired private JdbcTemplate jdbc;

  @Before(value = "@MAM-20 or @MAM-22 or @MAM-28 or @MAM-32", order = 0)
  public void emptyWorld() {
    MOCK.resetAll();
    TestSources.stubToken();
    jdbc.update("DELETE FROM screening_result");
    jdbc.update("DELETE FROM screening");
    jdbc.update("DELETE FROM screening_run");
    jdbc.update("DELETE FROM outbox_event");
  }

  @After(value = "@MAM-20 or @MAM-22 or @MAM-28 or @MAM-32", order = 0)
  public void restoreStubs() {
    MOCK.resetAll();
    TestSources.stubToken();
    ScreeningWorld.cleanSources();
  }
}
