package gov.medicaid.pem.testsupport;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Postgres and Kafka for integration tests, the same images as the local stack, started once per
 * test JVM and shared by every test class (use {@code @ImportTestcontainers(PemContainers.class)}).
 * Small memory settings: this machine is shared.
 */
public interface PemContainers {

  @ServiceConnection
  PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.4-alpine"))
          .withCommand(
              "postgres",
              "-c",
              "shared_buffers=16MB",
              "-c",
              "fsync=off",
              "-c",
              "max_connections=50")
          .withReuse(false);

  @ServiceConnection
  KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.2"));
}
