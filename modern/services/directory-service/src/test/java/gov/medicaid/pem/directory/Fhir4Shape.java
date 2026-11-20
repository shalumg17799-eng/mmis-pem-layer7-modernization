package gov.medicaid.pem.directory;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * A structural FHIR R4 check of a searchset Bundle and the Practitioner, Organization and
 * PractitionerRole resources in it (MAM-65 AC-7). It is NOT the HAPI FHIR validator: that is run on
 * the same Bundles by FhirR4HapiValidatorTest (decision Q-112), and this check stays next to it. It
 * checks what the R4 specification fixes without profiles or terminology: the element names of each
 * type, cardinalities, the Bundle and search codes, the id format, absolute URIs, and that no
 * string, array or object is empty. Each violation is one readable line.
 */
public final class Fhir4Shape {

  private static final Set<String> BASE =
      Set.of("resourceType", "id", "meta", "implicitRules", "language", "text", "contained");
  private static final Set<String> BUNDLE =
      Set.of(
          "resourceType",
          "id",
          "meta",
          "implicitRules",
          "language",
          "identifier",
          "type",
          "timestamp",
          "total",
          "link",
          "entry",
          "signature");
  private static final Set<String> BUNDLE_TYPES =
      Set.of(
          "document",
          "message",
          "transaction",
          "transaction-response",
          "batch",
          "batch-response",
          "history",
          "searchset",
          "collection");
  private static final Set<String> ENTRY =
      Set.of(
          "id",
          "extension",
          "modifierExtension",
          "link",
          "fullUrl",
          "resource",
          "search",
          "request",
          "response");
  private static final Set<String> SEARCH_MODES = Set.of("match", "include", "outcome");
  private static final Map<String, Set<String>> RESOURCES =
      Map.of(
          "Practitioner",
          with(
              "extension",
              "modifierExtension",
              "identifier",
              "active",
              "name",
              "telecom",
              "address",
              "gender",
              "birthDate",
              "photo",
              "qualification",
              "communication"),
          "Organization",
          with(
              "extension",
              "modifierExtension",
              "identifier",
              "active",
              "type",
              "name",
              "alias",
              "telecom",
              "address",
              "partOf",
              "contact",
              "endpoint"),
          "PractitionerRole",
          with(
              "extension",
              "modifierExtension",
              "identifier",
              "active",
              "period",
              "practitioner",
              "organization",
              "code",
              "specialty",
              "location",
              "healthcareService",
              "telecom",
              "availableTime",
              "notAvailable",
              "availabilityExceptions",
              "endpoint"));
  private static final Set<String> IDENTIFIER =
      Set.of("id", "extension", "use", "type", "system", "value", "period", "assigner");
  private static final Set<String> CODEABLE_CONCEPT = Set.of("id", "extension", "coding", "text");
  private static final Set<String> CODING =
      Set.of("id", "extension", "system", "version", "code", "display", "userSelected");
  private static final Set<String> HUMAN_NAME =
      Set.of("id", "extension", "use", "text", "family", "given", "prefix", "suffix", "period");
  private static final Set<String> REFERENCE =
      Set.of("id", "extension", "reference", "type", "identifier", "display");
  private static final Set<String> QUALIFICATION =
      Set.of("id", "extension", "modifierExtension", "identifier", "code", "period", "issuer");
  private static final Pattern ID = Pattern.compile("[A-Za-z0-9\\-.]{1,64}");
  private static final Pattern ABSOLUTE_URI = Pattern.compile("[A-Za-z][A-Za-z0-9+.\\-]*:\\S+");

  private Fhir4Shape() {}

  private static Set<String> with(String... elements) {
    Set<String> s = new java.util.HashSet<>(BASE);
    s.addAll(List.of(elements));
    return s;
  }

  /** The violations of a searchset Bundle and of every resource in it; empty when it conforms. */
  public static List<String> violations(JsonNode bundle) {
    List<String> v = new ArrayList<>();
    if (!"Bundle".equals(bundle.path("resourceType").asText(null))) {
      v.add("Bundle.resourceType must be Bundle");
    }
    unknown(bundle, BUNDLE, "Bundle", v);
    JsonNode type = bundle.path("type");
    if (!type.isTextual() || !BUNDLE_TYPES.contains(type.asText())) {
      v.add("Bundle.type (1..1) must be one of " + BUNDLE_TYPES + ", but is " + type);
    } else if (!"searchset".equals(type.asText())) {
      v.add("Bundle.type must be searchset for a search answer, but is " + type.asText());
    }
    JsonNode total = bundle.path("total");
    if (!total.isMissingNode() && !(total.isIntegralNumber() && total.asLong() >= 0)) {
      v.add("Bundle.total must be an unsigned integer, but is " + total);
    }
    JsonNode entries = bundle.path("entry");
    if (!entries.isMissingNode() && !entries.isArray()) {
      v.add("Bundle.entry must be an array");
    }
    if (entries.isArray()) {
      if (total.isIntegralNumber() && total.asLong() < entries.size()) {
        v.add("Bundle.total " + total + " is less than the " + entries.size() + " entries");
      }
      for (int i = 0; i < entries.size(); i++) {
        entry(entries.get(i), "Bundle.entry[" + i + "]", v);
      }
    }
    noEmpty(bundle, "Bundle", v);
    return v;
  }

  /**
   * Bundle entries without a fullUrl. R4 says "Except for transactions and batches, each entry in a
   * Bundle must have a fullUrl", and the HL7 validator reports a missing one as an error.
   */
  public static List<String> entriesWithoutFullUrl(JsonNode bundle) {
    List<String> out = new ArrayList<>();
    JsonNode entries = bundle.path("entry");
    for (int i = 0; i < entries.size(); i++) {
      String fullUrl = entries.get(i).path("fullUrl").asText("");
      if (!ABSOLUTE_URI.matcher(fullUrl).matches()) {
        out.add(
            "Bundle.entry[" + i + "].fullUrl is missing or not an absolute URI: '" + fullUrl + "'");
      }
    }
    return out;
  }

  private static void entry(JsonNode entry, String path, List<String> v) {
    unknown(entry, ENTRY, path, v);
    JsonNode search = entry.path("search");
    if (!search.isMissingNode() && !SEARCH_MODES.contains(search.path("mode").asText(""))) {
      v.add(
          path + ".search.mode must be one of " + SEARCH_MODES + ", but is " + search.path("mode"));
    }
    JsonNode resource = entry.path("resource");
    if (!resource.isObject()) {
      v.add(path + " has no resource (a searchset entry without request or response needs one)");
      return;
    }
    resource(resource, path + ".resource", v);
  }

  private static void resource(JsonNode r, String path, List<String> v) {
    String type = r.path("resourceType").asText("");
    Set<String> allowed = RESOURCES.get(type);
    if (allowed == null) {
      v.add(
          path
              + ".resourceType '"
              + type
              + "' is not Practitioner, Organization or PractitionerRole");
      return;
    }
    unknown(r, allowed, path + " (" + type + ")", v);
    if (!ID.matcher(r.path("id").asText("")).matches()) {
      v.add(path + ".id must match [A-Za-z0-9\\-.]{1,64}, but is " + r.path("id"));
    }
    if (r.has("active") && !r.get("active").isBoolean()) {
      v.add(path + ".active must be a boolean");
    }
    identifiers(r.path("identifier"), path + ".identifier", v);
    switch (type) {
      case "Practitioner" -> practitioner(r, path, v);
      case "Organization" -> organization(r, path, v);
      default -> practitionerRole(r, path, v);
    }
  }

  private static void practitioner(JsonNode r, String path, List<String> v) {
    JsonNode names = r.path("name");
    for (int i = 0; i < names.size(); i++) {
      JsonNode n = names.get(i);
      unknown(n, HUMAN_NAME, path + ".name[" + i + "]", v);
      if (n.has("given") && !n.get("given").isArray()) {
        v.add(path + ".name[" + i + "].given must be an array of strings");
      }
    }
    JsonNode qualifications = r.path("qualification");
    for (int i = 0; i < qualifications.size(); i++) {
      JsonNode q = qualifications.get(i);
      String p = path + ".qualification[" + i + "]";
      unknown(q, QUALIFICATION, p, v);
      if (!q.has("code")) {
        v.add(p + ".code is required (1..1)");
      }
      codeableConcept(q.path("code"), p + ".code", v);
      identifiers(q.path("identifier"), p + ".identifier", v);
      reference(q.path("issuer"), p + ".issuer", v);
    }
  }

  private static void organization(JsonNode r, String path, List<String> v) {
    if (!r.has("name") && !r.has("identifier")) {
      v.add(path + " needs a name or an identifier (org-1)");
    }
    if (r.has("name") && !r.get("name").isTextual()) {
      v.add(path + ".name must be a string");
    }
    JsonNode types = r.path("type");
    for (int i = 0; i < types.size(); i++) {
      codeableConcept(types.get(i), path + ".type[" + i + "]", v);
    }
  }

  private static void practitionerRole(JsonNode r, String path, List<String> v) {
    reference(r.path("practitioner"), path + ".practitioner", v);
    JsonNode specialties = r.path("specialty");
    for (int i = 0; i < specialties.size(); i++) {
      codeableConcept(specialties.get(i), path + ".specialty[" + i + "]", v);
    }
  }

  private static void identifiers(JsonNode ids, String path, List<String> v) {
    for (int i = 0; i < ids.size(); i++) {
      JsonNode id = ids.get(i);
      String p = path + "[" + i + "]";
      unknown(id, IDENTIFIER, p, v);
      if (id.has("system") && !ABSOLUTE_URI.matcher(id.get("system").asText("")).matches()) {
        v.add(p + ".system must be an absolute URI, but is " + id.get("system"));
      }
      if (id.has("type")) {
        codeableConcept(id.get("type"), p + ".type", v);
      }
    }
  }

  private static void codeableConcept(JsonNode cc, String path, List<String> v) {
    if (cc.isMissingNode()) {
      return;
    }
    unknown(cc, CODEABLE_CONCEPT, path, v);
    JsonNode codings = cc.path("coding");
    for (int i = 0; i < codings.size(); i++) {
      JsonNode c = codings.get(i);
      unknown(c, CODING, path + ".coding[" + i + "]", v);
      if (c.has("system") && !ABSOLUTE_URI.matcher(c.get("system").asText("")).matches()) {
        v.add(
            path + ".coding[" + i + "].system must be an absolute URI, but is " + c.get("system"));
      }
    }
  }

  private static void reference(JsonNode ref, String path, List<String> v) {
    if (!ref.isMissingNode()) {
      unknown(ref, REFERENCE, path, v);
    }
  }

  private static void unknown(JsonNode node, Set<String> allowed, String where, List<String> v) {
    if (!node.isObject()) {
      return;
    }
    for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
      String field = it.next();
      if (!allowed.contains(field)) {
        v.add(where + " has the element '" + field + "', which R4 does not define there");
      }
    }
  }

  /** FHIR JSON never holds null, an empty string, an empty array or an empty object. */
  private static void noEmpty(JsonNode node, String path, List<String> v) {
    if (node.isNull() || (node.isTextual() && node.asText().isEmpty())) {
      v.add(path + " is null or an empty string");
    } else if ((node.isArray() || node.isObject()) && node.isEmpty()) {
      v.add(path + " is an empty " + (node.isArray() ? "array" : "object"));
    } else if (node.isContainerNode()) {
      if (node.isArray()) {
        for (int i = 0; i < node.size(); i++) {
          noEmpty(node.get(i), path + "[" + i + "]", v);
        }
      } else {
        node.properties().forEach(e -> noEmpty(e.getValue(), path + "." + e.getKey(), v));
      }
    }
  }
}
