package gov.medicaid.pem.notification;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * One Mailpit for the whole test JVM (the same image as the local stack): SMTP on 1025, the read
 * API on 8025.
 */
public final class TestMail {

  public static final GenericContainer<?> MAILPIT =
      new GenericContainer<>(
              DockerImageName.parse(
                  "axllent/mailpit:v1.34.0@sha256:23a2ef85bc50853ab3093645d1d64679afd8887e89da5e48a88940c172591a4e"))
          .withExposedPorts(1025, 8025)
          .waitingFor(Wait.forHttp("/api/v1/messages").forPort(8025));

  static {
    MAILPIT.start();
  }

  private TestMail() {}

  public static String api() {
    return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025) + "/api/v1";
  }

  public static void register(DynamicPropertyRegistry r) {
    r.add("spring.mail.host", MAILPIT::getHost);
    r.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
  }
}
