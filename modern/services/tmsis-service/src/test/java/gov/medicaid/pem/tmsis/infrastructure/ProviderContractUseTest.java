package gov.medicaid.pem.tmsis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import gov.medicaid.pem.tmsis.IntegrationTest;
import gov.medicaid.pem.tmsis.TestProviderService;
import gov.medicaid.pem.tmsis.TestProviderService.Prov;
import gov.medicaid.pem.tmsis.TestProviderService.Request;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.yaml.snakeyaml.Yaml;

/**
 * The calls tmsis-service makes to provider-service, against modern/contracts/openapi/
 * provider-service.yaml 1.2.0 (Q-132: searchProviders gets the optional afterNpi; a call without it
 * answers exactly as in 1.1.0). The contract is the one source of truth for the walk: the request
 * of a run uses only parameters the contract declares, with a size and a status the contract
 * allows, SYSTEM may call the operation, and the walk follows the contract's own recipe: the first
 * call has no afterNpi, each next one repeats the filters and the size and sets afterNpi to the npi
 * of the last item of the answer before (ten digits), with page absent or 0.
 */
@Timeout(120)
class ProviderContractUseTest extends IntegrationTest {

  static final Map<String, Object> SPEC = load();

  @SuppressWarnings("unchecked")
  static Map<String, Object> load() {
    Path rel = Path.of("contracts", "openapi", "provider-service.yaml");
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
  static Map<String, Object> searchProviders() {
    for (Object item : ((Map<String, Object>) SPEC.get("paths")).values()) {
      for (Object op : ((Map<String, Object>) item).values()) {
        if (op instanceof Map<?, ?> m && "searchProviders".equals(m.get("operationId"))) {
          return (Map<String, Object>) m;
        }
      }
    }
    throw new AssertionError("operation searchProviders is not in the contract");
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> parameter(String name) {
    for (Object p : (List<Object>) searchProviders().get("parameters")) {
      Map<String, Object> m = (Map<String, Object>) p;
      if (name.equals(m.get("name"))) {
        return m;
      }
    }
    throw new AssertionError("parameter " + name + " is not declared by searchProviders");
  }

  @SuppressWarnings("unchecked")
  static List<Object> objects(Object list) {
    return (List<Object>) list;
  }

  @SuppressWarnings("unchecked")
  static Map<String, Object> schemaOf(String name) {
    return (Map<String, Object>) parameter(name).get("schema");
  }

  @SuppressWarnings("unchecked")
  static Set<String> declaredQueryParameters() {
    Set<String> names = new LinkedHashSet<>();
    for (Object p : (List<Object>) searchProviders().get("parameters")) {
      Map<String, Object> m = (Map<String, Object>) p;
      if ("query".equals(m.get("in"))) {
        names.add((String) m.get("name"));
      }
    }
    return names;
  }

  /** A run over 450 ACTIVE providers (1003001001 to 1003001450); the searches it made. */
  List<Request> run450() throws Exception {
    List<Prov> all = new ArrayList<>();
    for (int i = 1; i <= 450; i++) {
      all.add(Prov.active(String.format("1003%06d", 1000 + i)));
    }
    TestProviderService.holds(all);
    api.runToEnd("2026-03-31");
    return TestProviderService.searches();
  }

  // ---- the contract itself
  // -----------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-6] the provider contract tmsis-service is built against is 1.2.0 or later: searchProviders declares afterNpi (ten digits), size at most 200 and SYSTEM as a caller")
  void contractDeclaresWhatTheWalkUses() {
    String[] version = ((String) ((Map<?, ?>) SPEC.get("info")).get("version")).split("\\.");
    int major = Integer.parseInt(version[0]);
    int minor = Integer.parseInt(version[1]);
    assertThat(major > 1 || (major == 1 && minor >= 2)).as("contract version").isTrue();
    assertThat(schemaOf("afterNpi").get("pattern")).isEqualTo("^[0-9]{10}$");
    assertThat(schemaOf("size").get("maximum")).isEqualTo(200);
    assertThat(objects(searchProviders().get("x-roles"))).contains("SYSTEM");
  }

  // ---- the requests of a run
  // ---------------------------------------------------------------------

  @Test
  @DisplayName(
      "[MAM-71 AC-6] every request of a run uses only query parameters the contract declares, the status A and a size the contract allows")
  void requestsUseOnlyDeclaredParameters() throws Exception {
    List<Request> searches = run450();
    assertThat(searches).isNotEmpty();
    int max = (Integer) schemaOf("size").get("maximum");
    int min = (Integer) schemaOf("size").get("minimum");
    assertThat(searches)
        .allSatisfy(
            r -> {
              assertThat(declaredQueryParameters()).containsAll(r.query().keySet());
              assertThat(objects(schemaOf("status").get("enum"))).contains(r.query().get("status"));
              assertThat(Integer.parseInt(r.query().get("size"))).isBetween(min, max);
            });
  }

  @Test
  @DisplayName(
      "[MAM-71 AC-5] a run follows the contract's recipe for a walk: the first call has no afterNpi, each next one has the ten-digit npi of the last item of the answer before, and page is absent or 0")
  void walkFollowsTheContractsRecipe() throws Exception {
    List<Request> searches = run450();
    assertThat(searches).hasSize(3);
    assertThat(searches.get(0).query()).doesNotContainKey("afterNpi");
    for (int i = 1; i < searches.size(); i++) {
      String key = searches.get(i).query().get("afterNpi");
      assertThat(key).as("afterNpi of request %d", i + 1).isNotNull().matches("^[0-9]{10}$");
    }
    assertThat(searches)
        .allSatisfy(r -> assertThat(r.query().getOrDefault("page", "0")).isEqualTo("0"));
    assertThat(searches.get(1).query().get("afterNpi")).isEqualTo("1003001200");
    assertThat(searches.get(2).query().get("afterNpi")).isEqualTo("1003001400");
  }
}
