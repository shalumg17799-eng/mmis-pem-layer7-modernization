# Medco-Transform

AI agents modernize a legacy Medicaid **Provider Enrollment (PEM)** mainframe application (COBOL) into a working **Java 21 / Spring Boot microservices** application with a **React** web UI. The agents work from the AWS Transform analysis and a Neo4j knowledge graph, plan in Jira, write tests first, and every behaviour traces back to a legacy business rule.

## Where is everything?

| I am looking for | It is in |
|---|---|
| **The COBOL application** (legacy, read-only) | [`source-code/`](source-code/): 9 COBOL programs, JCL, VSAM, DB2, CICS, REXX |
| **AWS Transform output** (403 business rules, lineage, data dictionary, dependency graph, docs, test plan) | [`aws-transform/`](aws-transform/), folders `01-` to `08-` are generated and read-only; `test-data/` holds sample feeds |
| **The agents** (11 roles: analyst, architect, developers, reviewer...) | [`.claude/agents/`](.claude/agents/) |
| **The skills** (domain, architecture, TDD, graph rules the agents read) | [`.claude/skills/`](.claude/skills/) |
| **The phase commands** (`/phase-0-setup` ... `/phase-9-parity`, `/status`) | [`.claude/commands/`](.claude/commands/) |
| **The new application** (everything we build) | [`modern/`](modern/) |
| **Target backend** (Java microservices) | `modern/services/<service>/` |
| **Target frontend** (React web UI) | `modern/web-ui/` |
| **Tests** | graph and parser tests `modern/kag/tests/`; service tests `modern/services/<service>/src/test/`; UI tests `modern/web-ui/tests/` and `modern/web-ui/e2e/`; legacy-vs-modern parity `modern/parity/` |
| **The knowledge graph** (Neo4j loaders and validation) | `modern/kag/` |
| **API and event contracts** | `modern/contracts/openapi/` and `modern/contracts/asyncapi/` |
| **Jira story specs** (one baseline per story) | `modern/specs/` |
| **Docs, decisions, reports, questions** | `modern/docs/` ([PROGRESS.md](modern/docs/PROGRESS.md) is the status page) |
| **Shared code, infrastructure, synthetic data** | `modern/libs/`, `modern/infra/`, `modern/testdata/` |
| **The rules the agents obey** | [`MODERNIZATION.md`](MODERNIZATION.md) |
| **A guide to the legacy estate** | [`CLAUDE.md`](CLAUDE.md) |
| **How to run it** | [`HOW-TO-RUN.md`](HOW-TO-RUN.md) |
| Original zips (backup), local secrets, MCP config | `_archive/`, `.env` (never committed), `.mcp.json` |

Read-only: `source-code/`, `aws-transform/01-` to `08-`, `_archive/`. There is no scratch or temp folder: every file in the repo is either source, a deliverable or an audit record.

## How the agentic pipeline runs

Each phase is one command; each phase hands its output to the next one. The status of every phase is in `modern/docs/PROGRESS.md`.

| Phase | What happens | Main agents | Skills they read | Output |
|---|---|---|---|---|
| 0 Setup | tools, git, protections, `modern/` skeleton | orchestrator | | `modern/` |
| 1 Knowledge graph | legacy facts and AWS output loaded into Neo4j | kag-curator | kag-ontology | `modern/kag/`, Neo4j |
| 2 Legacy analysis | every business rule verified against the COBOL; defects and questions | legacy-analyst | pem-legacy-estate, pem-domain | `modern/docs/analysis/` |
| 3 Architecture and UX | services, contracts, data model, wireframes | architect, ui-developer | java-microservices, frontend-stack | `modern/docs/architecture/`, `modern/contracts/`, `modern/docs/ux/` |
| 4 Backlog | Jira epics and stories, spec baselines | backlog-planner | jira-backlog | Jira, `modern/specs/` |
| 5 Platform | build, docker compose, Keycloak, gateway, codec, test data | devops-engineer, data-engineer | java-microservices, synthetic-data | `modern/infra/`, `modern/libs/`, `modern/testdata/` |
| 6 Services | one service at a time, tests first | test-writer, java-developer, code-reviewer | tdd-workflow, cobol-to-java | `modern/services/` |
| 7 UI | portal and console, tests first | test-writer, ui-developer, code-reviewer | tdd-workflow, frontend-stack | `modern/web-ui/` |
| 8 End to end | journeys, security, accessibility, performance smoke | test-writer, devops-engineer | tdd-workflow | `modern/docs/reports/` |
| 9 Parity | legacy outputs compared with the modern system's | parity-checker | parity-testing | `modern/parity/` |

Rule to story to test to code is recorded in the graph, so any behaviour can be traced back to its legacy rule. A person edits a requirement in Jira and the agents forward-engineer it into tests and code: how that loop works is still to be designed (`MODERNIZATION.md` section 8).
