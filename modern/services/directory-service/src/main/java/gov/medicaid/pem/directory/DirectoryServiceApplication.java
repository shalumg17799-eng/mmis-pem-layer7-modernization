package gov.medicaid.pem.directory;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;

/**
 * directory-service (port 8088, database directory_db). Replaces PRVDIR01. Its configuration file
 * is {@code directory-service.yml}, so the local run mode can host several services in one JVM
 * (ADR-011); it also runs on its own.
 */
@SpringBootApplication
public class DirectoryServiceApplication {

  public static final String NAME = "directory-service";

  public static SpringApplicationBuilder builder() {
    return new SpringApplicationBuilder(DirectoryServiceApplication.class)
        .properties("spring.config.name=" + NAME);
  }

  public static void main(String[] args) {
    builder().run(args);
  }
}
