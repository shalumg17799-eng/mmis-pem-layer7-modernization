package gov.medicaid.pem.directory.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.medicaid.pem.directory.application.DirectoryPage;
import gov.medicaid.pem.directory.application.DirectoryStore;
import gov.medicaid.pem.kit.web.ApiException;
import java.util.Map;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * searchDirectory (PUBLIC): ACTIVE providers only, answered from the directory's own data (D-013).
 */
@RestController
@RequestMapping("/api/v1/directory/providers")
public class DirectoryController {

  /** The contract's providerType enum (D-008: IN, GR, FA are kept). */
  private static final Set<String> PROVIDER_TYPES = Set.of("IN", "GR", "FA");

  private final DirectoryStore store;

  public DirectoryController(DirectoryStore store) {
    this.store = store;
  }

  /** Contract DirectoryEntry. */
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Entry(
      String npi,
      String medicaidId,
      String providerType,
      String riskTier,
      String displayName,
      String taxonomy) {}

  @GetMapping
  public Map<String, Object> searchDirectory(
      @RequestParam(required = false) String name,
      @RequestParam(required = false) String providerType,
      @RequestParam(required = false) String taxonomy,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1 || size > 200) {
      throw ApiException.badRequest("page must be 0 or more and size 1 to 200.");
    }
    if (providerType != null && !PROVIDER_TYPES.contains(providerType)) {
      throw ApiException.badRequest("providerType must be IN, GR or FA.");
    }
    DirectoryPage p = store.published(name, providerType, taxonomy, page, size);
    return Map.of(
        "items",
        p.items().stream()
            .map(
                e ->
                    new Entry(
                        e.npi(),
                        e.medicaidId(),
                        e.providerType(),
                        e.riskTier(),
                        e.displayName(),
                        e.taxonomy()))
            .toList(),
        "page",
        p.page(),
        "size",
        p.size(),
        "total",
        p.total());
  }
}
