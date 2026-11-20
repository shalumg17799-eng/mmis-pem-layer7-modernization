package gov.medicaid.pem.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * Checks an event envelope against its schema in modern/contracts/asyncapi/events.yaml: required
 * properties, no undeclared property, a null only where the schema allows it (type list with
 * "null"), const and enum values, string formats uuid, date and date-time, patterns, and nested
 * objects, arrays and allOf/anyOf.
 */
public final class EventContract {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final Map<String, Object> spec;

  private EventContract(Map<String, Object> spec) {
    this.spec = spec;
  }

  public static EventContract load() {
    Path p = locate(Path.of("contracts", "asyncapi", "events.yaml"));
    try {
      return new EventContract(new Yaml().load(Files.readString(p)));
    } catch (IOException e) {
      throw new IllegalStateException("cannot read " + p, e);
    }
  }

  /** The violations of the envelope (JSON text) against components.schemas.{schema}. */
  public List<String> violations(String schema, String envelopeJson) {
    List<String> errors = new ArrayList<>();
    try {
      check(named(schema), JSON.readTree(envelopeJson), "$", errors);
    } catch (IOException e) {
      errors.add("not JSON: " + envelopeJson);
    }
    return errors;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> named(String schema) {
    Map<String, Object> components = (Map<String, Object>) spec.get("components");
    Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
    Map<String, Object> s = (Map<String, Object>) schemas.get(schema);
    if (s == null) {
      throw new IllegalArgumentException("no schema " + schema + " in events.yaml");
    }
    return s;
  }

  /** Follows $ref and merges allOf (later parts are more specific and win per property). */
  @SuppressWarnings("unchecked")
  private Map<String, Object> resolve(Map<String, Object> node) {
    Object ref = node.get("$ref");
    if (ref != null) {
      Object cur = spec;
      for (String part : ((String) ref).substring(2).split("/")) {
        cur = ((Map<String, Object>) cur).get(part);
      }
      return resolve((Map<String, Object>) cur);
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
      Map<String, Object> p = resolve((Map<String, Object>) part);
      for (Map.Entry<String, Object> e : p.entrySet()) {
        if (e.getKey().equals("properties")) {
          for (Map.Entry<String, Object> prop : ((Map<String, Object>) e.getValue()).entrySet()) {
            Map<String, Object> mergedProp = new LinkedHashMap<>();
            Object before = properties.get(prop.getKey());
            if (before != null) {
              mergedProp.putAll(resolve((Map<String, Object>) before));
            }
            mergedProp.putAll(resolve((Map<String, Object>) prop.getValue()));
            properties.put(prop.getKey(), mergedProp);
          }
        } else if (e.getKey().equals("required")) {
          required.addAll((List<Object>) e.getValue());
        } else {
          merged.put(e.getKey(), e.getValue());
        }
      }
    }
    merged.put("properties", properties);
    merged.put("required", required);
    return merged;
  }

  @SuppressWarnings("unchecked")
  private void check(Map<String, Object> raw, JsonNode value, String path, List<String> errors) {
    Map<String, Object> schema = resolve(raw);
    Object type = schema.get("type");
    List<Object> types =
        type instanceof List<?> l ? (List<Object>) l : type == null ? List.of() : List.of(type);
    if (value == null || value.isNull() || value.isMissingNode()) {
      if (!types.isEmpty() && !types.contains("null")) {
        errors.add(path + " is null, but the schema says " + types);
      }
      return;
    }
    if (schema.containsKey("const") && !String.valueOf(schema.get("const")).equals(value.asText())) {
      errors.add(path + " = " + value + " but the schema says const " + schema.get("const"));
    }
    if (schema.get("enum") instanceof List<?> e && value.isValueNode()) {
      boolean listed = e.stream().anyMatch(x -> String.valueOf(x).equals(value.asText()));
      if (!listed) {
        errors.add(path + " = " + value + " not in " + e);
      }
    }
    if (schema.get("anyOf") instanceof List<?> alternatives) {
      boolean any = false;
      for (Object alt : alternatives) {
        List<String> trial = new ArrayList<>();
        check((Map<String, Object>) alt, value, path, trial);
        any |= trial.isEmpty();
      }
      if (!any) {
        errors.add(path + " = " + value + " matches none of the alternatives");
      }
    }
    if (types.contains("object") || schema.containsKey("properties")) {
      checkObject(schema, value, path, errors);
    } else if (types.contains("array")) {
      if (!value.isArray()) {
        errors.add(path + " is not an array");
        return;
      }
      for (int i = 0; i < value.size(); i++) {
        check((Map<String, Object>) schema.get("items"), value.get(i), path + "[" + i + "]", errors);
      }
    } else if (types.contains("string") || (types.isEmpty() && value.isTextual())) {
      checkString(schema, value, path, errors);
    } else if (types.contains("integer") && !value.isIntegralNumber()) {
      errors.add(path + " is not an integer");
    }
  }

  @SuppressWarnings("unchecked")
  private void checkObject(
      Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
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
  }

  private void checkString(
      Map<String, Object> schema, JsonNode value, String path, List<String> errors) {
    if (!value.isTextual()) {
      errors.add(path + " is not a string");
      return;
    }
    String text = value.asText();
    Object format = schema.get("format");
    try {
      if ("uuid".equals(format)) {
        UUID.fromString(text);
        if (text.length() != 36) {
          errors.add(path + " = " + text + " is not a uuid");
        }
      } else if ("date".equals(format)) {
        LocalDate.parse(text);
      } else if ("date-time".equals(format)) {
        Instant.parse(text);
      }
    } catch (IllegalArgumentException | DateTimeParseException e) {
      errors.add(path + " = " + text + " is not a " + format);
    }
    Object pattern = schema.get("pattern");
    if (pattern != null && !Pattern.compile((String) pattern).matcher(text).find()) {
      errors.add(path + " = " + text + " does not match " + pattern);
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
