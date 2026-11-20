package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * MAM-71 AC-6 and MAM-75 AC-7: the configuration file of tmsis-service. It owns its own database
 * (tmsis_db) and never names the provider database, reaches provider-service by URL, and keeps the
 * month-end cron and its zone in configuration (ADR-004).
 */
class ServiceConfigTest {

  static Properties config() {
    YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
    yaml.setResources(new ClassPathResource("tmsis-service.yml"));
    return yaml.getObject();
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-6] the datasource is tmsis_db and no other database is named in the configuration")
  void ownDatabaseOnly() {
    Properties p = config();
    assertThat(p.getProperty("spring.datasource.url")).endsWith("/tmsis_db");
    assertThat(p.getProperty("spring.datasource.username")).isEqualTo("tmsis_svc");
    assertThat(p.values().stream().map(String::valueOf))
        .noneMatch(v -> v.contains("provider_db") || v.contains("directory_db"));
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-6] providers are reached through provider-service's URL, an HTTP address")
  void providerServiceUrl() {
    assertThat(config().getProperty("pem.services.provider-url")).startsWith("http://");
  }

  @Test
  @DisplayName(
      "[MAM-75 AC-7] the cron is month end at 22:00 UTC and can be overridden by PEM_TMSIS_CRON")
  void cronInConfiguration() {
    Properties p = config();
    assertThat(p.getProperty("pem.tmsis.cron")).isEqualTo("${PEM_TMSIS_CRON:0 0 22 L * *}");
    assertThat(p.getProperty("pem.tmsis.zone")).isEqualTo("UTC");
  }

  @Test
  @DisplayName("[MAM-75] the service runs on port 8089 by default")
  void port() {
    assertThat(config().getProperty("server.port")).isEqualTo("${PEM_TMSIS_PORT:8089}");
  }

  @Test
  @DisplayName("[MAM-71 AC-6] the Flyway migrations are tmsis-service's own")
  void flywayLocation() {
    assertThat(config().getProperty("spring.flyway.locations"))
        .isEqualTo("classpath:db/migration/tmsis");
  }
}
