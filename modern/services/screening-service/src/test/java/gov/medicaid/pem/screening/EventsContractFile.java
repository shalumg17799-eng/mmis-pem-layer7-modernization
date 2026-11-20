package gov.medicaid.pem.screening;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * modern/contracts/asyncapi/events.yaml (1.1.0) for the event contract tests: a published event
 * envelope checked against a component schema such as ScreeningCompletedEnvelope. It reads $ref,
 * allOf (the envelope and the data of the event are merged, the later definition of a property
 * wins), anyOf, const, enum, type (a null is only allowed where the schema says null), required,
 * properties (an object that lists properties is closed: a member outside them is a violation),
 * items, format (uuid, date, date-time), pattern and maxLength. Each violation names its path, for
 * example {@code $.data.reason is null}.
 */
public final class EventsContractFile {

  private static final Map<String, Object> SPEC = load();

  private EventsContractFile() {}

  @SuppressWarnings("unchecked")
  private static Map<String, Object> load() {
    Path rel = Path.of("contracts", "asyncapi", "events.yaml");
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

  /**
   * The violations of the event (envelope with its data) against the schema; empty if it conforms.
   */
  public static List<String> violations(String schemaName, JsonNode event) {
    List<String> errors = new ArrayList<>();
    check(schema(schemaName), event, "$", errors);
    return errors;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object o) {
    return (Map<String, Object>) o;
  }

  private static Map<String, Object> schema(String name) {
    Map<String, Object> found = map(map(map(SPEC.get("components")).get("schemas")).get(name));
    if (found == null) {
      throw new AssertionError("schema " + name + " is not in events.yaml");
    }
    return found;
  }

  /** The schema with its $ref resolved and its allOf parts merged into one. */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> flatten(Map<String, Object> node) {
    Object ref = node.get("$ref");
    if (ref != null) {
      String name = ((String) ref).substring(((String) ref).lastIndexOf('/') + 1);
      return flatten(schema(name));
    }
    Object allOf = node.get("allOf");
    if (allOf == null) {
      return node;
    }
    Map<String, Object> merged = new LinkedHashMap<>(node);
    merged.remove("allOf");
    Map<String, Object> properties = new LinkedHashMap<>();
    List<Object> required = new ArrayList<>();
    for (Object part : (List<Object>) allOf) {
      Map<String, Object> p = flatten(map(part));
      properties.putAll(map(p.getOrDefault("properties", Map.of())));
      required.addAll((List<Object>) p.getOrDefault("required", List.of()));
      p.forEach(
          (k, v) -> {
            if (!k.equals("properties") && !k.equals("required")) {
              merged.putIfAbsent(k, v);
            }
          });
    }
    merged.put("properties", properties);
    merged.put("required", required);
    return merged;
  }

  @SuppressWarnings("unchecked")
  private static void check(
      Map<String, Object> node, JsonNode value, String path, List<String> errors) {
    Map<String, Object> schema = flatten(node);
    Object type = schema.get("type");
    List<Object> types =
        type instanceof List<?> l ? (List<Object>) l : type == null ? List.of() : List.of(type);
    if (value.isNull()) {
      if (!types.isEmpty() && !types.contains("null")) {
        errors.add(path + " is null");
      }
      return;
    }
    if (schema.containsKey("const")
        && !String.valueOf(schema.get("const")).equals(value.asText())) {
      errors.add(path + " = " + value + " is not the constant " + schema.get("const"));
    }
    if (schema.get("enum") instanceof List<?> e && !e.contains(value.asText())) {
      errors.add(path + " = " + value + " not in " + e);
    }
    if (schema.get("anyOf") instanceof List<?> any) {
      boolean one =
          any.stream()
              .anyMatch(
                  branch -> {
                    List<String> sub = new ArrayList<>();
                    check(map(branch), value, path, sub);
                    return sub.isEmpty();
                  });
      if (!one) {
        errors.add(path + " = " + value + " matches none of " + any);
      }
    }
    if (types.contains("object") || schema.containsKey("properties")) {
      checkObject(schema, value, path, errors);
    } else if (types.contains("array")) {
      if (!value.isArray()) {
        errors.add(path + " is not an array");
      } else if (schema.get("items") != null) {
        for (int i = 0; i < value.size(); i++) {
          check(map(schema.get("items")), value.get(i), path + "[" + i + "]", errors);
        }
      }
    } else if (types.contains("string") && !value.isTextual()) {
      errors.add(path + " is not a string");
    } else if (types.contains("integer") && !value.isIntegralNumber()) {
      errors.add(path + " is not an integer");
    }
    if (value.isTextual()) {
      checkText(schema, value.asText(), path, errors);
    }
  }

  @SuppressWarnings("unchecked")
  private static void checkObject(
      Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
    if (!value.isObject()) {
      errors.add(path + " is not an object");
      return;
    }
    Map<String, Object> props = map(schema.getOrDefault("properties", Map.of()));
    for (Object required : (List<Object>) schema.getOrDefault("required", List.of())) {
      if (!value.has((String) required)) {
        errors.add(path + "." + required + " is required");
      }
    }
    for (Iterator<String> it = value.fieldNames(); it.hasNext(); ) {
      String field = it.next();
      if (props.containsKey(field)) {
        check(map(props.get(field)), value.get(field), path + "." + field, errors);
      } else if (!props.isEmpty()) {
        errors.add(path + "." + field + " is not in the contract");
      }
    }
  }

  private static void checkText(
      Map<String, Object> schema, String text, String path, List<String> errors) {
    try {
      switch (String.valueOf(schema.get("format"))) {
        case "uuid" -> UUID.fromString(text);
        case "date" -> LocalDate.parse(text);
        case "date-time" -> OffsetDateTime.parse(text);
        default -> {
          // no format to check
        }
      }
    } catch (RuntimeException e) {
      errors.add(path + " = " + text + " is not a valid " + schema.get("format"));
    }
    if (schema.get("pattern") instanceof String p && !Pattern.compile(p).matcher(text).find()) {
      errors.add(path + " = " + text + " does not match " + p);
    }
    if (schema.get("maxLength") instanceof Integer max && text.length() > max) {
      errors.add(path + " is longer than " + max);
    }
  }
}
