package gov.medicaid.pem.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Shared fixtures of the risk-service tests of MAM-38 and MAM-41: the legacy table (rule set
 * version 1, Q-005 option A), the rule-set request bodies, and the reset that puts the shared test
 * database back to the seeded state (only version 1) so that every test, and MAM-35's scenarios,
 * start from the legacy table.
 */
public final class RiskFixtures {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** The legacy table as "prefix=tier" entries, in the order PRVRSK01 lists them. */
  public static final List<String> LEGACY = List.of("332=H", "251=H", "341=M", "291=M", "225=M");

  private RiskFixtures() {}

  /** Removes every rule set above version 1 (and its rules): the seeded state of risk_db. */
  public static void resetToLegacyTable(JdbcTemplate jdbc) {
    jdbc.update(
        "DELETE FROM risk_rule WHERE rule_set_id IN"
            + " (SELECT id FROM risk_rule_set WHERE rule_set_version > 1)");
    jdbc.update("DELETE FROM risk_rule_set WHERE rule_set_version > 1");
  }

  /** The legacy rules plus one more entry such as "207=M". */
  public static List<String> legacyPlus(String entry) {
    List<String> all = new ArrayList<>(LEGACY);
    all.add(entry);
    return all;
  }

  /**
   * A PUT body: version and default tier are left out when null; each rule is "prefix=tier". A tier
   * written as "-" is left out of the rule.
   */
  public static String ruleSet(Integer version, String defaultTier, List<String> rules) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (version != null) {
      body.put("version", version);
    }
    if (defaultTier != null) {
      body.put("defaultTier", defaultTier);
    }
    if (rules != null) {
      List<Map<String, Object>> list = new ArrayList<>();
      for (String r : rules) {
        String[] parts = r.split("=", 2);
        Map<String, Object> rule = new LinkedHashMap<>();
        rule.put("taxonomyPrefix", parts[0]);
        if (!"-".equals(parts[1])) {
          rule.put("tier", parts[1]);
        }
        list.add(rule);
      }
      body.put("rules", list);
    }
    try {
      return JSON.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  /** A request body of the assessment API; taxonomy and npi are left out when null. */
  public static String assessmentRequest(String taxonomy, String npi) {
    Map<String, Object> body = new LinkedHashMap<>();
    if (npi != null) {
      body.put("npi", npi);
    }
    if (taxonomy != null) {
      body.put("taxonomy", taxonomy);
    }
    try {
      return JSON.writeValueAsString(body);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * A problem+json body without its "instance" member. Spring adds the request path as the RFC 7807
   * member "instance" to every problem response, but the Problem schema of the contracts does not
   * list it (reported in the Lane 3 report of MAM-38/MAM-41), and the contract helper refuses
   * members a schema does not list. Everything else of the body is still checked.
   */
  public static String withoutInstance(String problemJson) {
    try {
      ObjectNode node = (ObjectNode) JSON.readTree(problemJson);
      node.remove("instance");
      return JSON.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  /** "prefix=tier" entries of a RiskRuleSet JSON body, in the order the response lists them. */
  public static List<String> rulesOf(JsonNode ruleSet) {
    List<String> out = new ArrayList<>();
    for (JsonNode r : ruleSet.path("rules")) {
      out.add(r.path("taxonomyPrefix").asText() + "=" + r.path("tier").asText());
    }
    return out;
  }
}
