package gov.medicaid.pem.tmsis;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * The injected Clock of the tests: fixed at the business date of the test suite (2026-01-15, 10:00
 * UTC) until a test sets another instant. It is one object for the whole test JVM, so every Spring
 * context of the test run shares it; each test resets it.
 */
public final class TestClock extends Clock {

  public static final Instant DEFAULT = Instant.parse("2026-01-15T10:00:00Z");
  public static final TestClock INSTANCE = new TestClock();

  private volatile Instant now = DEFAULT;

  private TestClock() {}

  /** The clock shows this instant from now on. */
  public static void set(String instant) {
    INSTANCE.now = Instant.parse(instant);
  }

  /** The clock shows 2026-01-15T10:00:00Z again. */
  public static void reset() {
    INSTANCE.now = DEFAULT;
  }

  @Override
  public ZoneId getZone() {
    return ZoneOffset.UTC;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return Clock.fixed(now, zone);
  }

  @Override
  public Instant instant() {
    return now;
  }

  /** Makes this the Clock bean of the application (the kit's system clock backs off). */
  @TestConfiguration
  public static class Config {

    @Bean
    Clock clock() {
      return INSTANCE;
    }
  }
}
