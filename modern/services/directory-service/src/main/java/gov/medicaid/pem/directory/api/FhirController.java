package gov.medicaid.pem.directory.api;

import gov.medicaid.pem.directory.application.DirectoryEntry;
import gov.medicaid.pem.directory.application.FhirDirectory;
import gov.medicaid.pem.directory.application.ProviderProfile;
import gov.medicaid.pem.kit.web.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only FHIR R4 searches of the public directory (MAM-65, ADR-006): Practitioner (IN),
 * Organization (GR, FA) and PractitionerRole (IN, specialty = taxonomy), ACTIVE providers only
 * (R-PRVDIR01-ADD-01), built only from fields the legacy master holds (Q-008): NPI, Medicaid ID,
 * names, licence, provider type and taxonomy; no address, telecom or network. PUBLIC, no token.
 * Only GET is mapped, so any other method is refused with a 4xx. The fullUrl of every Bundle entry
 * is the configured public base URL ({@code pem.fhir.public-base-url}) +
 * /fhir/&lt;Type&gt;/&lt;id&gt;, never built from the request or from a forwarded header, which a
 * caller controls.
 */
@RestController
@RequestMapping("/fhir")
public class FhirController {

  static final String NPI_SYSTEM = "http://hl7.org/fhir/sid/us-npi";
  static final String TAXONOMY_SYSTEM = "http://nucc.org/provider-taxonomy";
  static final MediaType FHIR_JSON = MediaType.parseMediaType("application/fhir+json");

  /** Entries in one Bundle; total always counts every match. */
  static final int MAX_ENTRIES = 200;

  /** The public base URL of the fullUrl when none is configured: the local stack's gateway. */
  static final String DEFAULT_PUBLIC_BASE_URL = "http://localhost:8080";

  private static final Pattern NPI = Pattern.compile("[0-9]{10}");

  private final FhirDirectory directory;
  private final String fhirBase;

  public FhirController(FhirDirectory directory) {
    this(directory, DEFAULT_PUBLIC_BASE_URL);
  }

  @Autowired
  public FhirController(
      FhirDirectory directory, @Value("${pem.fhir.public-base-url}") String publicBaseUrl) {
    this.directory = directory;
    this.fhirBase = publicBaseUrl.replaceAll("/+$", "") + "/fhir/";
  }

  /** searchPractitioners: identifier (system|NPI) and/or name. */
  @GetMapping("/Practitioner")
  public ResponseEntity<Map<String, Object>> searchPractitioners(
      @RequestParam(required = false) String identifier,
      @RequestParam(required = false) String name) {
    return bundle(
        directory.individuals(npi(identifier), name, MAX_ENTRIES), FhirController::practitioner);
  }

  /** searchOrganizations: identifier (system|NPI) and/or name; groups and facilities only. */
  @GetMapping("/Organization")
  public ResponseEntity<Map<String, Object>> searchOrganizations(
      @RequestParam(required = false) String identifier,
      @RequestParam(required = false) String name) {
    return bundle(
        directory.organizations(npi(identifier), name, MAX_ENTRIES), FhirController::organization);
  }

  /** searchPractitionerRoles: specialty = the taxonomy code (code or system|code). */
  @GetMapping("/PractitionerRole")
  public ResponseEntity<Map<String, Object>> searchPractitionerRoles(
      @RequestParam(required = false) String specialty) {
    String code = specialty;
    if (specialty != null && specialty.contains("|")) {
      code = specialty.substring(specialty.indexOf('|') + 1);
    }
    if (code != null && code.isBlank()) {
      throw ApiException.badRequest("specialty must be a taxonomy code.");
    }
    return bundle(
        directory.individualsWithTaxonomy(code, MAX_ENTRIES), FhirController::practitionerRole);
  }

  /** The NPI of an identifier token (system|value or value); anything else is a 400. */
  static String npi(String identifier) {
    if (identifier == null) {
      return null;
    }
    String value = identifier;
    int bar = identifier.indexOf('|');
    if (bar >= 0) {
      String system = identifier.substring(0, bar);
      if (!system.isEmpty() && !NPI_SYSTEM.equals(system)) {
        throw ApiException.badRequest(
            "Only the NPI identifier system " + NPI_SYSTEM + " is supported.");
      }
      value = identifier.substring(bar + 1);
    }
    if (!NPI.matcher(value).matches()) {
      throw ApiException.badRequest("The NPI must be 10 digits.");
    }
    return value;
  }

  private ResponseEntity<Map<String, Object>> bundle(
      FhirDirectory.Result result, Function<FhirDirectory.Listing, Map<String, Object>> mapper) {
    // R4: every entry of a searchset has an absolute fullUrl, <public base>/fhir/<Type>/<id>
    Map<String, Object> b = new LinkedHashMap<>();
    b.put("resourceType", "Bundle");
    b.put("type", "searchset");
    b.put("total", result.total());
    List<Map<String, Object>> entries = new ArrayList<>();
    for (FhirDirectory.Listing l : result.listings()) {
      Map<String, Object> resource = mapper.apply(l);
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("fullUrl", fhirBase + resource.get("resourceType") + "/" + resource.get("id"));
      entry.put("resource", resource);
      entry.put("search", Map.of("mode", "match"));
      entries.add(entry);
    }
    if (!entries.isEmpty()) {
      b.put("entry", entries);
    }
    return ResponseEntity.ok().contentType(FHIR_JSON).body(b);
  }

  private static Map<String, Object> practitioner(FhirDirectory.Listing l) {
    DirectoryEntry e = l.entry();
    ProviderProfile p = l.profile();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("resourceType", "Practitioner");
    r.put("id", e.npi());
    r.put("identifier", identifiers(e));
    r.put("active", true);
    Map<String, Object> name = new LinkedHashMap<>();
    if (present(p.lastName())) {
      name.put("family", p.lastName().trim());
    }
    if (present(p.firstName())) {
      name.put("given", List.of(p.firstName().trim()));
    }
    if (name.isEmpty() && present(e.displayName())) {
      name.put("text", e.displayName());
    }
    if (!name.isEmpty()) {
      r.put("name", List.of(name));
    }
    if (present(p.licenseNo())) {
      Map<String, Object> q = new LinkedHashMap<>();
      q.put("identifier", List.of(Map.of("value", p.licenseNo().trim())));
      q.put("code", Map.of("text", "State licence"));
      if (present(p.licenseState())) {
        q.put("issuer", Map.of("display", p.licenseState()));
      }
      r.put("qualification", List.of(q));
    }
    return r;
  }

  private static Map<String, Object> organization(FhirDirectory.Listing l) {
    DirectoryEntry e = l.entry();
    ProviderProfile p = l.profile();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("resourceType", "Organization");
    r.put("id", e.npi());
    r.put("identifier", identifiers(e));
    r.put("active", true);
    if (present(e.providerType())) {
      r.put(
          "type",
          List.of(
              Map.of(
                  "coding", List.of(Map.of("code", e.providerType())), "text", e.providerType())));
    }
    String name = present(p.legalName()) ? p.legalName().trim() : e.displayName();
    if (present(name)) {
      r.put("name", name);
    }
    return r;
  }

  private static Map<String, Object> practitionerRole(FhirDirectory.Listing l) {
    DirectoryEntry e = l.entry();
    Map<String, Object> r = new LinkedHashMap<>();
    r.put("resourceType", "PractitionerRole");
    r.put("id", e.npi());
    r.put("active", true);
    r.put("practitioner", Map.of("reference", "Practitioner/" + e.npi()));
    if (present(e.taxonomy())) {
      r.put(
          "specialty",
          List.of(
              Map.of("coding", List.of(Map.of("system", TAXONOMY_SYSTEM, "code", e.taxonomy())))));
    }
    return r;
  }

  private static List<Map<String, Object>> identifiers(DirectoryEntry e) {
    List<Map<String, Object>> ids = new ArrayList<>();
    ids.add(Map.of("system", NPI_SYSTEM, "value", e.npi()));
    if (present(e.medicaidId())) {
      ids.add(Map.of("type", Map.of("text", "Medicaid ID"), "value", e.medicaidId()));
    }
    return ids;
  }

  private static boolean present(String s) {
    return s != null && !s.isBlank();
  }
}
