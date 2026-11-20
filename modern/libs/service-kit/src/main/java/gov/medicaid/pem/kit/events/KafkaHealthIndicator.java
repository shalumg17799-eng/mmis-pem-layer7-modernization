package gov.medicaid.pem.kit.events;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.kafka.core.KafkaAdmin;

/**
 * MAM-36 AC-1: the 'kafka' component of /actuator/health. UP when the broker answers a cluster
 * description within the timeout, DOWN otherwise. The admin client is created on first use.
 */
public class KafkaHealthIndicator extends AbstractHealthIndicator implements DisposableBean {

  private final KafkaAdmin admin;
  private final Duration timeout;
  private AdminClient client;

  public KafkaHealthIndicator(KafkaAdmin admin, Duration timeout) {
    super("Kafka health check failed");
    this.admin = admin;
    this.timeout = timeout;
  }

  @Override
  protected void doHealthCheck(Health.Builder builder) throws Exception {
    DescribeClusterResult cluster =
        client().describeCluster(new DescribeClusterOptions().timeoutMs((int) timeout.toMillis()));
    String clusterId = cluster.clusterId().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    int nodes = cluster.nodes().get(timeout.toMillis(), TimeUnit.MILLISECONDS).size();
    builder.up().withDetail("clusterId", clusterId).withDetail("nodes", nodes);
  }

  private synchronized AdminClient client() {
    if (client == null) {
      client = AdminClient.create(admin.getConfigurationProperties());
    }
    return client;
  }

  @Override
  public synchronized void destroy() {
    if (client != null) {
      client.close(Duration.ofSeconds(1));
      client = null;
    }
  }
}
