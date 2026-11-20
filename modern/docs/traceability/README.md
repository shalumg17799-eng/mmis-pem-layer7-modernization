# Traceability (files, not Neo4j)

Neo4j holds the legacy context only (legacy code + AWS Transform output + legacy analysis; user decision 2026-11-19).
Traceability from legacy rule to running code lives here and in each Jira MAM story's "Legacy trace" section.

| File | What | How it is made |
|---|---|---|
| `rule-trace.csv` | every legacy rule -> owning service -> Jira story -> ACs citing it -> tests with result -> code units | `python modern/tools/trace/build_rule_trace.py` after every story |
| `graph-export/<Label>.csv` | the design and backlog nodes that were in Neo4j until 2026-11-19 (Epic, Story, AcceptanceCriterion, TestCase, CodeUnit, ApiEndpoint, Event, Service, Table, Column, UiScreen, Decision) | `modern/kag/retire_delivery_layer.py` (one-off) |
| `graph-export/relationships.csv` | every relationship that touched those nodes (OWNED_BY, MAPS_TO, DERIVED_FROM, HAS_AC, CONTAINS, EXPOSES, ...) | same |
| `graph-export/legacy-design-properties.csv` | design values that had been written onto legacy nodes (BusinessRule.owner, Field.dropped, Field.dropReason) | same |

The current design itself stays in `modern/docs/architecture/`, `modern/contracts/`, `modern/docs/adr/`, and the
backlog in Jira MAM and `modern/specs/`.
