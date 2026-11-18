# modern/

Everything created for the PEM modernization lives here. The legacy estate in `source-code/` and the generated AWS output in `aws-transform/` stay read-only.

| Folder | What goes there |
|---|---|
| `docs/PROGRESS.md` | Phase status, decisions, open questions, configured connections (names only) |
| `docs/analysis/` | Verified legacy rules, job flow, layouts, defect register, `questions.md`, graph report |
| `docs/analysis/programs/` | One analysis file per legacy program |
| `docs/architecture/` | Service design, mappings, NFRs |
| `docs/architecture/data-model/` | Per-service data models |
| `docs/adr/` | Architecture decision records |
| `docs/ux/` | Screen inventory and user journeys |
| `docs/ux/wireframes/` | Low-fidelity USWDS wireframes |
| `docs/backlog/` | Draft and approved Jira backlog |
| `docs/reports/` | One report per phase and per service or UI area |
| `docs/reports/stories/<KEY>/` | Everything about one story: its report, `red-report.md`, `red-commit`, `trace.json` (no hidden temp folder) |
| `docs/runbooks/` | How to run, operate and troubleshoot the system |
| `kag/` | Neo4j knowledge graph: constraints, validation, loader runner |
| `kag/loaders/` | Python loader scripts (`load_<nn>_<what>.py`), the only writers to Neo4j |
| `kag/tests/` | pytest tests for the loaders and parsers |
| `specs/` | One spec baseline per Jira story (`<KEY>.md`) |
| `contracts/openapi/` | REST contracts, one per service |
| `contracts/asyncapi/` | Event contracts |
| `services/` | The Spring Boot microservices, one folder each |
| `libs/` | Shared libraries (fixed-width codec, test support) |
| `web-ui/` | React + TypeScript portal and console |
| `infra/` | docker compose, Postgres init, Keycloak realm, WireMock stubs |
| `testdata/` | Synthetic legacy-format files and seed data (never real data) |
| `parity/` | Legacy-vs-modern output comparison (Phase 9) |
| `parity/golden/` | Captured legacy job outputs and expected results |
| `tools/` | Developer scripts and helpers |
| `tools/data/` | Data-generation and conversion scripts |
