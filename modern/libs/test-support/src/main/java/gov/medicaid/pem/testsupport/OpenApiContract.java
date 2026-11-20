package gov.medicaid.pem.testsupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Contract test helper: checks a JSON response against the response schema of an operation in
 * modern/contracts/openapi/<service>.yaml (required properties, no unknown properties, enum values,
 * basic types, and $ref objects recursively). Uses only SnakeYAML and Jackson, which Spring Boot
 * already brings.
 */
public final class OpenApiContract {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final Map<String, Object> spec;

  private OpenApiContract(Map<String, Object> spec) {
    this.spec = spec;
  }

  public static OpenApiContract of(String service) {
    Path p = locate(Path.of("contracts", "openapi", service + ".yaml"));
    try {
      return new OpenApiContract(new Yaml().load(Files.readString(p)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + p, e);
    }
  }

  /**
   * Asserts the body matches the schema of operationId's response for this status
   * (application/json).
   */
  public void assertResponse(String operationId, int status, String body) {
    Map<String, Object> schema = responseSchema(operationId, status);
    try {
      List<String> errors = new ArrayList<>();
      check(schema, JSON.readTree(body), "$", errors);
      if (!errors.isEmpty()) {
        fail("response of " + operationId + " " + status + " breaks the contract: " + errors);
      }
    } catch (IOException e) {
      fail("response is not JSON: " + body);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> responseSchema(String operationId, int status) {
    Map<String, Object> paths = (Map<String, Object>) spec.get("paths");
    for (Object item : paths.values()) {
      for (Object op : ((Map<String, Object>) item).values()) {
        Map<String, Object> o = (Map<String, Object>) op;
        if (operationId.equals(o.get("operationId"))) {
          Map<String, Object> responses = (Map<String, Object>) o.get("responses");
          Map<String, Object> r = (Map<String, Object>) responses.get(String.valueOf(status));
          assertThat(r).as("status %s documented for %s", status, operationId).isNotNull();
          r = resolve(r);
          Map<String, Object> content = (Map<String, Object>) r.get("content");
          Map<String, Object> media =
              (Map<String, Object>)
                  content.getOrDefault("application/json", content.get("application/problem+json"));
          return resolve((Map<String, Object>) media.get("schema"));
        }
      }
    }
    throw new AssertionError("operation " + operationId + " not in the contract");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> resolve(Map<String, Object> node) {
    Object ref = node.get("$ref");
    if (ref == null) {
      return node;
    }
    Object cur = spec;
    for (String part : ((String) ref).substring(2).split("/")) {
      cur = ((Map<String, Object>) cur).get(part);
    }
    return resolve((Map<String, Object>) cur);
  }

  @SuppressWarnings("unchecked")
  private void check(Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
    schema = resolve(schema);
    Object type = schema.get("type");
    List<Object> types =
        type instanceof List<?> l ? (List<Object>) l : type == null ? List.of() : List.of(type);
    if (value.isNull()) {
      if (!types.isEmpty() && !types.contains("null")) {
        errors.add(path + " is null");
      }
      return;
    }
    Object enums = schema.get("enum");
    if (enums instanceof List<?> e
        && value.isValueNode()
        && !e.contains(value.isNumber() ? value.numberValue() : value.asText())) {
      errors.add(path + " = " + value + " not in " + e);
    }
    if (types.contains("object") || schema.containsKey("properties")) {
      if (!value.isObject()) {
        errors.add(path + " is not an object");
        return;
      }
      Map<String, Object> props = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
      for (Object req : (List<Object>) schema.getOrDefault("required", List.of())) {
        if (!value.has((String) req)) {
          errors.add(path + "." + req + " is required");
        }
      }
      for (Iterator<String> it = value.fieldNames(); it.hasNext(); ) {
        String f = it.next();
        if (!props.containsKey(f)) {
          errors.add(path + "." + f + " is not in the contract");
        } else {
          check((Map<String, Object>) props.get(f), value.get(f), path + "." + f, errors);
        }
      }
    } else if (types.contains("array")) {
      if (!value.isArray()) {
        errors.add(path + " is not an array");
        return;
      }
      for (int i = 0; i < value.size(); i++) {
        check(
            (Map<String, Object>) schema.get("items"), value.get(i), path + "[" + i + "]", errors);
      }
    } else if (types.contains("string") && !value.isTextual()) {
      errors.add(path + " is not a string");
    } else if (types.contains("integer") && !value.isIntegralNumber()) {
      errors.add(path + " is not an integer");
    }
  }

  private static Path locate(Path rel) {
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isRegularFile(c)) {
          return c;
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("contract not found: " + rel);
  }
}
