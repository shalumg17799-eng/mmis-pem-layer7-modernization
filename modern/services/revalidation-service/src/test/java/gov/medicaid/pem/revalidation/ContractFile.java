package gov.medicaid.pem.revalidation;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.yaml.snakeyaml.Yaml;

/**
 * modern/contracts/openapi/revalidation-service.yaml (1.1.0) for the contract tests: the roles,
 * status codes and media types of an operation, and a response body checked against a component
 * schema (required properties, no properties outside the schema, enums, types, formats, patterns
 * and additionalProperties). The shared OpenApiContract helper cannot read additionalProperties,
 * which BatchRun.counts needs.
 */
public final class ContractFile {

  private static final Map<String, Object> SPEC = load();

  private ContractFile() {}

  @SuppressWarnings("unchecked")
  private static Map<String, Object> load() {
    Path rel = Path.of("contracts", "openapi", "revalidation-service.yaml");
    Path dir = Path.of("").toAbsolutePath();
    while (dir != null) {
      for (Path c : List.of(dir.resolve(rel), dir.resolve("modern").resolve(rel))) {
        if (Files.isRegularFile(c)) {
          try {
            return new Yaml().load(Files.readString(c));
          } catch (IOException e) {
            throw new IllegalStateException("cannot read " + c, e);
          }
        }
      }
      dir = dir.getParent();
    }
    throw new IllegalStateException("contract not found: " + rel);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  /** The operation object with this operationId. */
  public static Map<String, Object> operation(String operationId) {
    for (Object item : map(SPEC.get("paths")).values()) {
      for (Object op : map(item).values()) {
        if (op instanceof Map<?, ?> m && operationId.equals(m.get("operationId"))) {
          return map(m);
        }
      }
    }
    throw new AssertionError("operation " + operationId + " is not in the contract");
  }

  /** The x-roles of the operation. */
  @SuppressWarnings("unchecked")
  public static List<String> roles(String operationId) {
    return (List<String>) operation(operationId).get("x-roles");
  }

  /** The status codes the contract documents for the operation. */
  public static Set<String> statuses(String operationId) {
    return new TreeSet<>(map(operation(operationId).get("responses")).keySet());
  }

  /** The media types the contract declares for the status of the operation. */
  public static Set<String> mediaTypes(String operationId, int status) {
    Map<String, Object> responses = map(operation(operationId).get("responses"));
    Map<String, Object> response = map(responses.get(String.valueOf(status)));
    return map(response.get("content")).keySet();
  }

  /** The media types of the request body of the operation. */
  public static Set<String> requestMediaTypes(String operationId) {
    return map(map(operation(operationId).get("requestBody")).get("content")).keySet();
  }

  /** The properties the schema names as required. */
  @SuppressWarnings("unchecked")
  public static List<String> required(String schemaName) {
    return (List<String>) schema(schemaName).getOrDefault("required", List.of());
  }

  /** The violations of the body against the component schema; empty when it conforms. */
  public static List<String> violations(String schemaName, JsonNode body) {
    List<String> errors = new ArrayList<>();
    check(schema(schemaName), body, "$", errors);
    return errors;
  }

  /**
   * The violations of a Problem body. RFC 7807 also has the member {@code instance}, which Spring
   * sets on every problem answer; the contract's Problem schema does not list it, so it is allowed
   * here (reported with the Lane 3 tests as a contract gap, not as a service defect).
   */
  public static List<String> problemViolations(JsonNode body) {
    List<String> errors = violations("Problem", body);
    errors.removeIf(e -> e.equals("$.instance is not in the contract"));
    return errors;
  }

  /** The name of the component schema of the documented answer (JSON or problem+json). */
  public static String responseSchemaName(String operationId, int status) {
    Map<String, Object> responses = map(operation(operationId).get("responses"));
    Map<String, Object> response = map(responses.get(String.valueOf(status)));
    if (response == null) {
      throw new AssertionError("status " + status + " is not documented for " + operationId);
    }
    Map<String, Object> content = map(response.get("content"));
    Map<String, Object> media =
        map(
            content.containsKey("application/json")
                ? content.get("application/json")
                : content.get("application/problem+json"));
    String ref = (String) map(media.get("schema")).get("$ref");
    return ref.substring(ref.lastIndexOf('/') + 1);
  }

  /** The violations of an answer against the schema the contract documents for its status. */
  public static List<String> responseViolations(String operationId, int status, JsonNode body) {
    String name = responseSchemaName(operationId, status);
    return name.equals("Problem") ? problemViolations(body) : violations(name, body);
  }

  private static Map<String, Object> schema(String name) {
    return map(map(map(SPEC.get("components")).get("schemas")).get(name));
  }

  private static Map<String, Object> resolve(Map<String, Object> node) {
    Object ref = node.get("$ref");
    if (ref == null) {
      return node;
    }
    String name = ((String) ref).substring(((String) ref).lastIndexOf('/') + 1);
    return resolve(schema(name));
  }

  @SuppressWarnings("unchecked")
  private static void check(
      Map<String, Object> schemaOrRef, JsonNode value, String path, List<String> errors) {
    Map<String, Object> schema = resolve(schemaOrRef);
    Object type = schema.get("type");
    List<Object> types =
        type instanceof List<?> l ? (List<Object>) l : type == null ? List.of() : List.of(type);
    if (value.isNull()) {
      if (!types.isEmpty() && !types.contains("null")) {
        errors.add(path + " is null");
      }
      return;
    }
    if (schema.get("enum") instanceof List<?> e
        && value.isValueNode()
        && !e.contains(value.asText())) {
      errors.add(path + " = " + value + " not in " + e);
    }
    if (types.contains("object") || schema.containsKey("properties")) {
      checkObject(schema, value, path, errors);
    } else if (types.contains("array")) {
      if (!value.isArray()) {
        errors.add(path + " is not an array");
        return;
      }
      Object items = schema.get("items");
      for (int i = 0; i < value.size() && items != null; i++) {
        check((Map<String, Object>) items, value.get(i), path + "[" + i + "]", errors);
      }
    } else if (types.contains("string")) {
      checkString(schema, value, path, errors);
    } else if (types.contains("integer") && !value.isIntegralNumber()) {
      errors.add(path + " is not an integer");
    }
  }

  @SuppressWarnings("unchecked")
  private static void checkObject(
      Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
    if (!value.isObject()) {
      errors.add(path + " is not an object");
      return;
    }
    Map<String, Object> props = (Map<String, Object>) schema.getOrDefault("properties", Map.of());
    for (Object required : (List<Object>) schema.getOrDefault("required", List.of())) {
      if (!value.has((String) required)) {
        errors.add(path + "." + required + " is required");
      }
    }
    Object additional = schema.get("additionalProperties");
    for (Iterator<String> it = value.fieldNames(); it.hasNext(); ) {
      String field = it.next();
      if (props.containsKey(field)) {
        check((Map<String, Object>) props.get(field), value.get(field), path + "." + field, errors);
      } else if (additional instanceof Map<?, ?> m) {
        check((Map<String, Object>) m, value.get(field), path + "." + field, errors);
      } else if (!props.isEmpty() && !Boolean.TRUE.equals(additional)) {
        // a schema that lists properties is closed; one that lists none (an open object) is not
        errors.add(path + "." + field + " is not in the contract");
      }
    }
  }

  private static void checkString(
      Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
    if (!value.isTextual()) {
      errors.add(path + " is not a string");
      return;
    }
    String text = value.asText();
    try {
      switch (String.valueOf(schema.get("format"))) {
        case "uuid" -> UUID.fromString(text);
        case "date" -> LocalDate.parse(text);
        case "date-time" -> Instant.parse(text);
        default -> {
          // no format to check
        }
      }
    } catch (RuntimeException e) {
      errors.add(path + " = " + text + " is not a valid " + schema.get("format"));
    }
    if (schema.get("pattern") instanceof String p && !text.matches(p)) {
      errors.add(path + " = " + text + " does not match " + p);
    }
    if (schema.get("maxLength") instanceof Integer max && text.length() > max) {
      errors.add(path + " is longer than " + max);
    }
  }
}
