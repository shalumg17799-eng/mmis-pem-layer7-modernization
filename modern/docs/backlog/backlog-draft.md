# Backlog draft (Phase 4, LOCAL mode)

Status: **created in Jira project MAM on 2026-11-18** (https://radiantexp.atlassian.net, board 1831), approved by the user in
advance. IDs below are the MAM keys; `jira-map.csv` maps them to the local IDs (L-E01, L-S001, ...) used before.
Story text is drafted by backlog-planner from the knowledge graph (rules, decisions, contracts) and corrected by an
independent reviewer; every acceptance criterion names its basis. Spec baselines: `modern/specs/<ID>.md`.

**15 epics, 64 stories, 14 tasks, 553 acceptance criteria.** Non-dropped rules covered: 433 of 433 (uncovered: none). Main-journey slice: 35 items (label `slice-main-journey`).

## Epics

| ID | Epic | Service / area | Items | ACs |
|---|---|---|---:|---:|
| MAM-1 | Platform and CI | platform | 9 | 57 |
| MAM-2 | Synthetic data and legacy formats | data | 6 | 41 |
| MAM-3 | provider-service | provider-service | 9 | 67 |
| MAM-4 | enrollment-service | enrollment-service | 4 | 31 |
| MAM-5 | screening-service | screening-service | 6 | 39 |
| MAM-6 | risk-service | risk-service | 3 | 21 |
| MAM-7 | revalidation-service | revalidation-service | 3 | 20 |
| MAM-8 | sanctions-service | sanctions-service | 2 | 16 |
| MAM-9 | notification-service | notification-service | 3 | 24 |
| MAM-10 | directory-service | directory-service | 4 | 31 |
| MAM-11 | tmsis-service | tmsis-service | 3 | 20 |
| MAM-12 | Web UI portal | web-ui | 6 | 42 |
| MAM-13 | Web UI console | web-ui | 8 | 62 |
| MAM-14 | End-to-end and hardening | e2e | 11 | 74 |
| MAM-15 | Parity (deferred) | parity | 1 | 8 |

## Build order

Dependencies first inside each block. Services follow the order provider, enrollment, screening, risk,
revalidation, sanctions, notification, directory, tmsis; then the portal, the console and end-to-end work.

### Phase 5: platform, data and walking skeleton

1. MAM-16 Gradle multi-project build with quality gates (JaCoCo, PIT, ArchUnit, Spotless) (platform)
2. MAM-19 Web UI toolchain scaffold: React, TypeScript, Vite, USWDS, Vitest, Playwright with axe, clients generated from the contracts (platform)
3. MAM-24 One-command local stack: docker compose, health checks, make up/down/logs/seed/reset (platform)
4. MAM-26 Keycloak realm pem with the PROVIDER, ANALYST, SUPERVISOR, ADMIN roles, one test user per role and a SYSTEM service account (platform)
5. MAM-29 API gateway: routing to every service, CORS for the web UI, token relay (api-gateway)
6. MAM-31 Event backbone: CloudEvents-style envelope, transactional outbox, idempotent consumers (platform)
7. MAM-42 Fixed-width codec with layouts exported from the graph (PRVRECRD 130 bytes and every legacy file) (libs/fixed-width)
8. MAM-44 Deterministic synthetic datasets covering at least 95% of the verified rules (testdata)
9. MAM-50 Seed data for the local stack (make seed): the provider seed (testdata)
10. MAM-34 Walking skeleton: provider lookup by NPI (console -> gateway -> provider-service -> Postgres, with login) (provider-service)
11. MAM-47 WireMock stubs for the external screening sources (NPPES, LEIE, SAM, licence board, SSA DMF): hit, clean, error, slow (infra/wiremock)

### Main journey vertical slice (J1)

12. MAM-17 Register a validated application as a PENDING, UNSCREENED provider (provider-service)
13. MAM-21 Record the screening verdict (screen flag) on the provider (provider-service)
14. MAM-23 Record the assigned risk tier on the provider (provider-service)
15. MAM-27 Analyst approves or denies an enrolment; approval sets ACTIVE and the revalidation due date (provider-service)
16. MAM-37 Search and read providers by NPI or Medicaid ID, role-checked (PINQ replacement back end) (provider-service)
17. MAM-46 Submit an enrolment application online and validate it (E101 to E104) (enrollment-service)
18. MAM-54 Read application status (a provider sees their own; an analyst sees all) (enrollment-service)
19. MAM-18 Screen a newly registered provider against the licence board, OIG LEIE, SAM and SSA DMF (mocked sources) (screening-service)
20. MAM-25 Decide the screening verdict, store the results per source and publish ScreeningCompleted (screening-service)
21. MAM-35 Assign the risk tier from the taxonomy prefix after a passed screening (risk-service)
22. MAM-58 Send sanction notices by email (TRM, SUS, RST), reject unknown actions and handle failures (notification-service)
23. MAM-59 Send notices by email for approvals, denials, rejections and revalidation (notification-service)
24. MAM-60 List the notices sent and the notice templates (notification-service)
25. MAM-61 Keep the directory of ACTIVE providers up to date from provider events (directory-service)
26. MAM-63 Public provider directory search (directory-service)
27. MAM-62 Web UI shell: sign in with Keycloak, role-based navigation, portal home (web-ui)
28. MAM-64 Portal: apply for enrolment online, with field errors E101 to E104 (web-ui)
29. MAM-66 Portal: see my application status (web-ui)
30. MAM-68 Portal: see my provider record (web-ui)
31. MAM-70 Portal: see my notices (web-ui)
32. MAM-73 Public directory page (no sign-in) (web-ui)
33. MAM-85 Console: work queues (pending approval, failed screening, revalidation due, rejected applications) (web-ui)
34. MAM-87 Console: approve or deny an enrolment (web-ui)
35. MAM-69 Journey J1 end to end: a provider applies and is enrolled (main journey) (e2e)

### Rest of Phase 6, Phase 7, Phase 8 (in dependency order)

36. MAM-36 Health, structured logs and correlation IDs across services (NFR) (platform)
37. MAM-39 CI pipeline: build, check, contract lint, web-ui tests, compose smoke test (platform)
38. MAM-33 Apply a sanction status change to the provider (TERMINATED, SUSPENDED, ACTIVE) (provider-service)
39. MAM-52 Record a sanction (terminate, suspend, reinstate) with history and apply it through provider-service (sanctions-service)
40. MAM-45 Monthly revalidation sweep: open a case for every ACTIVE provider that is due, with the legacy REVALQ file (revalidation-service)
41. MAM-48 Revalidation cases: list them and complete one (revalidation-service)
42. MAM-51 Seed data for the local stack: screening results, sanction history and read models in each service (testdata)
43. MAM-53 EBCDIC to ASCII converter for the Phase 9 parity run (tools/data)
44. MAM-30 Revalidation due date: five years, four-digit years, leap day and each completed revalidation (provider-service)
45. MAM-40 Provider history: every change and every notice sent is audited and readable (provider-service)
46. MAM-43 Export the provider master in the legacy 130-byte PRVRECRD layout (provider-service)
47. MAM-56 Run the legacy APPINPUT intake as a batch job with one error row per rejected application (enrollment-service)
48. MAM-57 Report the result of an intake run: exclusive counts, completion, failures and safe re-runs (enrollment-service)
49. MAM-20 Handle unknown licences, organisation providers and sources that fail or are slow (screening-service)
50. MAM-22 Screen the owner of a group or facility (E203 owner excluded) (screening-service)
51. MAM-28 Rescreen a provider on request or when revalidation is due (screening-service)
52. MAM-32 Run screening as a batch job and produce the legacy SCRNOUT file (screening-service)
53. MAM-38 View and change the taxonomy-prefix risk table (admin) (risk-service)
54. MAM-41 Assess a risk tier on request (risk assessment API) (risk-service)
55. MAM-49 Revalidation case states: re-screening requested and provider no longer active (revalidation-service)
56. MAM-55 Run the legacy SANCIN sanction intake as a batch job (sanctions-service)
57. MAM-65 FHIR R4 Practitioner, Organization and PractitionerRole (read-only, public) (directory-service)
58. MAM-67 Directory extract as a batch job in the legacy DIROUT format (directory-service)
59. MAM-71 Monthly T-MSIS provider extract in the legacy 120-byte layout (YYDDD period) (tmsis-service)
60. MAM-75 T-MSIS extract run: archive every file, report counts, publish ExtractProduced (tmsis-service)
61. MAM-78 Warn about providers whose enrolment year is later than the business year (tmsis-service)
62. MAM-77 Console: provider search by NPI, Medicaid ID or name (replaces CICS PINQ) (web-ui)
63. MAM-80 Console: provider detail with every record field and the status shown as text and code (web-ui)
64. MAM-82 Console: provider detail panels for screening, sanctions, revalidation, notices and audit (web-ui)
65. MAM-90 Console: record a sanction (supervisor) (web-ui)
66. MAM-92 Console: view and change the risk rules (admin) (web-ui)
67. MAM-93 Console: run the batch jobs and download their legacy files (admin) (web-ui)
68. MAM-72 Journey J2 end to end: an application is rejected (e2e)
69. MAM-74 Journey J3 end to end: screening fails and the analyst denies (e2e)
70. MAM-76 Journey J4 end to end: analyst inquiry (PINQ replacement) (e2e)
71. MAM-79 Journey J5 end to end: a supervisor suspends and reinstates a provider (e2e)
72. MAM-81 Journey J6 end to end: monthly revalidation (e2e)
73. MAM-83 Journey J7 end to end: month-end extracts and the public directory (e2e)
74. MAM-84 Security: role tests for every endpoint, dependency check, no secrets (NFR) (e2e)
75. MAM-86 Accessibility: WCAG 2.1 AA on every page, zero axe violations, keyboard-only walk-through (NFR) (e2e)
76. MAM-88 Performance smoke: 10,000 synthetic providers, batch jobs and search against the NFR targets (NFR) (e2e)
77. MAM-89 Traceability report (rule -> story -> AC -> test -> code) and runbooks (e2e)

### Parity (deferred to Phase 9)

78. MAM-91 Parity: run the legacy jobs on Hercules and compare their outputs with the modern system (deferred) (parity)

## Dependencies

| Item | Depends on |
|---|---|
| MAM-19 | MAM-16 |
| MAM-24 | MAM-16 |
| MAM-26 | MAM-24 |
| MAM-29 | MAM-26 |
| MAM-31 | MAM-16, MAM-24 |
| MAM-34 | MAM-29, MAM-50, MAM-19 |
| MAM-36 | MAM-31, MAM-24, MAM-29, MAM-46, MAM-17 |
| MAM-39 | MAM-16, MAM-19, MAM-24, MAM-34 |
| MAM-42 | MAM-16 |
| MAM-44 | MAM-42 |
| MAM-47 | MAM-24, MAM-44 |
| MAM-50 | MAM-24, MAM-44 |
| MAM-51 | MAM-50, MAM-37, MAM-25, MAM-52, MAM-61, MAM-63, MAM-45, MAM-48 |
| MAM-17 | MAM-31, MAM-34 |
| MAM-21 | MAM-17 |
| MAM-23 | MAM-17 |
| MAM-27 | MAM-21, MAM-23 |
| MAM-30 | MAM-27 |
| MAM-33 | MAM-17 |
| MAM-37 | MAM-34 |
| MAM-40 | MAM-17, MAM-21, MAM-23, MAM-27, MAM-33 |
| MAM-43 | MAM-17, MAM-42 |
| MAM-46 | MAM-31 |
| MAM-54 | MAM-46 |
| MAM-56 | MAM-46, MAM-42 |
| MAM-57 | MAM-56 |
| MAM-18 | MAM-17, MAM-37, MAM-47 |
| MAM-20 | MAM-18, MAM-25 |
| MAM-22 | MAM-18, MAM-25 |
| MAM-25 | MAM-18 |
| MAM-28 | MAM-25 |
| MAM-32 | MAM-25, MAM-42, MAM-37 |
| MAM-35 | MAM-25 |
| MAM-38 | MAM-35 |
| MAM-41 | MAM-35, MAM-38 |
| MAM-45 | MAM-27, MAM-42 |
| MAM-48 | MAM-45 |
| MAM-49 | MAM-45, MAM-48 |
| MAM-52 | MAM-33 |
| MAM-55 | MAM-52, MAM-42 |
| MAM-58 | MAM-31, MAM-24 |
| MAM-59 | MAM-58 |
| MAM-60 | MAM-58, MAM-59 |
| MAM-61 | MAM-27, MAM-17, MAM-35, MAM-37, MAM-31 |
| MAM-63 | MAM-61 |
| MAM-65 | MAM-61 |
| MAM-67 | MAM-61, MAM-42 |
| MAM-71 | MAM-43, MAM-42, MAM-37 |
| MAM-75 | MAM-71, MAM-31 |
| MAM-78 | MAM-71 |
| MAM-62 | MAM-34, MAM-26, MAM-54, MAM-37 |
| MAM-64 | MAM-62, MAM-46 |
| MAM-66 | MAM-64, MAM-54 |
| MAM-68 | MAM-62, MAM-37 |
| MAM-70 | MAM-62, MAM-60 |
| MAM-73 | MAM-63, MAM-34 |
| MAM-77 | MAM-62, MAM-37 |
| MAM-80 | MAM-77 |
| MAM-82 | MAM-80, MAM-25, MAM-52, MAM-48, MAM-60, MAM-40 |
| MAM-85 | MAM-62, MAM-37, MAM-54, MAM-25, MAM-48 |
| MAM-87 | MAM-85, MAM-27, MAM-25 |
| MAM-90 | MAM-80, MAM-52 |
| MAM-92 | MAM-62, MAM-38 |
| MAM-93 | MAM-62, MAM-56, MAM-32, MAM-45, MAM-55, MAM-67, MAM-71, MAM-75 |
| MAM-69 | MAM-62, MAM-87, MAM-70, MAM-73, MAM-64, MAM-66, MAM-68, MAM-85, MAM-47, MAM-50, MAM-46, MAM-54, MAM-17, MAM-21, MAM-23, MAM-18, MAM-25, MAM-35, MAM-27, MAM-61, MAM-63, MAM-58 |
| MAM-72 | MAM-64, MAM-66, MAM-70, MAM-46, MAM-54, MAM-58, MAM-50 |
| MAM-74 | MAM-87, MAM-22, MAM-85, MAM-80, MAM-47, MAM-58, MAM-50 |
| MAM-76 | MAM-80, MAM-77, MAM-62, MAM-37, MAM-40, MAM-69, MAM-50 |
| MAM-79 | MAM-90, MAM-52, MAM-33, MAM-58, MAM-61, MAM-73, MAM-50, MAM-51 |
| MAM-81 | MAM-48, MAM-45, MAM-28, MAM-93, MAM-85, MAM-50, MAM-51 |
| MAM-83 | MAM-75, MAM-71, MAM-67, MAM-63, MAM-93, MAM-73, MAM-50, MAM-51 |
| MAM-84 | MAM-29, MAM-26, MAM-36, MAM-50 |
| MAM-86 | MAM-62, MAM-64, MAM-66, MAM-68, MAM-70, MAM-73, MAM-77, MAM-80, MAM-85, MAM-87, MAM-90, MAM-92, MAM-93 |
| MAM-88 | MAM-44, MAM-50, MAM-45, MAM-75, MAM-37, MAM-46, MAM-18 |
| MAM-89 | MAM-69, MAM-72, MAM-74, MAM-76, MAM-79, MAM-81, MAM-83, MAM-84, MAM-86, MAM-88 |
| MAM-91 | MAM-53, MAM-42, MAM-44, MAM-56, MAM-32, MAM-45, MAM-55, MAM-67, MAM-75, MAM-43 |

## Items by epic

### MAM-1 Platform and CI

#### MAM-16 Gradle multi-project build with quality gates (JaCoCo, PIT, ArchUnit, Spotless)

Type: Task. Service: platform. Labels: svc-platform, kag-MAM-16, agentic, slice-main-journey. Depends on: none.

##### Business requirement
The delivery team needs one repeatable way to build every service and shared library on the agreed technology version, and a build that refuses to accept work that falls below the agreed quality bar (test coverage, test strength, architecture rules, code formatting). With this in place every later story is built and checked in exactly the same way.

##### Acceptance criteria
###### AC-1 One command builds everything on Java 21
Given a clean checkout of modern/ on a machine with Docker and Java 21, where Gradle is not installed locally
When the Gradle wrapper is generated once with a pinned gradle JDK 21 Docker image and ./gradlew build is run from modern/
Then the build exits with code 0 and every compiled class in every module targets Java 21 (class file major version 65)
Basis: .claude/commands/phase-5-platform.md step 2, .claude/agents/devops-engineer.md deliverable 1, modern/docs/adr/ADR-010-versions.md, .claude/skills/java-microservices/SKILL.md section 4

###### AC-2 One module per service and library, standard package layout
Given settings.gradle.kts of modern/
When ./gradlew projects is run and the module folders are listed
Then the output lists api-gateway, the nine business services (provider, enrollment, screening, risk, revalidation, sanctions, notification, directory, tmsis), libs:fixed-width and libs:test-support; each business service has the packages gov.medicaid.pem.<svc>.api, .application, .domain and .infrastructure, plus .adapter.legacy in the services that keep a legacy file (provider, enrollment, screening, revalidation, sanctions, directory, tmsis)
Basis: .claude/skills/java-microservices/SKILL.md sections 1 and 5, .claude/agents/devops-engineer.md deliverable 2, modern/docs/adr/ADR-001-service-decomposition.md, modern/docs/adr/ADR-005-legacy-format-adapters.md

###### AC-3 Coverage gate: 85% line coverage per service
Given a service module whose JaCoCo line coverage is 84%
When ./gradlew :services:<svc>:check is run
Then the build fails with a coverage-verification violation that names the 85% minimum, and the same check passes when coverage is 85% or higher (proved on a real module and recorded in the platform report, or by the build configuration lines)
Basis: .claude/skills/java-microservices/SKILL.md section 12, .claude/skills/tdd-workflow/SKILL.md section 4, .claude/agents/devops-engineer.md deliverable 1, .claude/commands/phase-5-platform.md step 5

###### AC-4 Mutation gate: 70% on domain and application
Given a service module whose PIT mutation score on the domain and application packages is 69%
When ./gradlew :services:<svc>:check is run
Then the build fails naming the 70% threshold; at 70% or higher it passes; classes outside domain and application are not mutated
Basis: .claude/skills/java-microservices/SKILL.md section 12, .claude/skills/tdd-workflow/SKILL.md section 4, .claude/agents/devops-engineer.md deliverable 1

###### AC-5 Architecture rules fail the build
Given a class in a domain package that imports a Spring class or a class of another layer, or a class in an api package that uses an infrastructure class directly
When ./gradlew :services:<svc>:check is run
Then the build fails and names the violated ArchUnit rule; code that respects the layering passes
Basis: .claude/skills/java-microservices/SKILL.md section 5, .claude/agents/devops-engineer.md deliverable 1

###### AC-6 Formatting is checked and fixable
Given a Java source file that does not follow the project format
When ./gradlew spotlessCheck (part of check) is run, then ./gradlew spotlessApply
Then spotlessCheck fails and lists the file; after spotlessApply the same check passes
Basis: .claude/agents/devops-engineer.md deliverable 1

###### AC-7 A skipped test cannot reach green
Given a test annotated @Disabled, or skipped at run time
When ./gradlew :services:<svc>:check is run
Then the build fails and names the skipped test
Basis: .claude/skills/tdd-workflow/SKILL.md section 4

###### AC-8 Every version is pinned and recorded
Given modern/gradle/libs.versions.toml
When the build's version check runs
Then it fails on any dynamic version (+, latest, a range); every library and plugin has an exact version, Spring Boot is the latest 3.x GA at Phase 5 with its matching Spring Cloud release train, and the exact versions are recorded in the ADR Versions (ADR-010)
Basis: modern/docs/adr/ADR-010-versions.md, .claude/skills/java-microservices/SKILL.md section 4, .claude/commands/phase-5-platform.md step 2

##### Out of scope
- Dependency vulnerability scan (nfr-security)
- Web UI toolchain (plt-build-2)
- CI pipeline (plt-ci)
- docker compose and service start-up (plt-compose)
- The shared event module libs/events (plt-events, QF-4)
- Business code of any service

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler; the legacy estate has no build system, CLAUDE.md section 1)
Decisions: none

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: none.


#### MAM-19 Web UI toolchain scaffold: React, TypeScript, Vite, USWDS, Vitest, Playwright with axe, clients generated from the contracts

Type: Task. Service: platform. Labels: svc-platform, kag-MAM-19, agentic, slice-main-journey. Depends on: MAM-16.

##### Business requirement
The provider portal and the analyst console are built as one web application. Its tooling has to be set up once so that every screen is built, tested and checked for accessibility the same way, and so that the application talks to the services only through typed clients generated from the agreed service contracts.

##### Acceptance criteria
###### AC-1 The web UI builds
Given modern/web-ui with its committed package.json and lock file
When npm ci and then npm run build are run
Then both exit with code 0 and a production bundle is produced in modern/web-ui/dist, which git check-ignore reports as ignored
Basis: .claude/skills/frontend-stack/SKILL.md section 2, .claude/agents/devops-engineer.md ('You may write only': modern/web-ui/ scaffold files), .gitignore (modern/web-ui/dist/)

###### AC-2 Stack and folder structure
Given the scaffold
When its configuration and folders are inspected
Then it is React with TypeScript in strict mode on Vite, with React Router and TanStack Query, and has src/app, src/features/portal, src/features/console, src/shared, src/theme, tests/ and e2e/
Basis: .claude/skills/frontend-stack/SKILL.md sections 2 and 3, modern/docs/adr/ADR-008-ui-stack-and-design-system.md

###### AC-3 USWDS is the only component library
Given package.json
When its dependencies are read
Then @trussworks/react-uswds is present at an exact version, no other component library is present, and the version is recorded in the ADR Versions (USWDS 3.x)
Basis: .claude/skills/frontend-stack/SKILL.md section 2, modern/docs/adr/ADR-010-versions.md, modern/docs/adr/ADR-008-ui-stack-and-design-system.md

###### AC-4 Typed API clients generated from the contracts
Given the nine OpenAPI files in modern/contracts/openapi (version 1.0.0)
When the client generation script (openapi-typescript) is run twice
Then a typed client exists for every operation of every contract, the second run changes no file, and a check that regenerates and compares fails when a generated file was edited by hand
Basis: .claude/skills/frontend-stack/SKILL.md section 2 (never hand-write API types), modern/docs/adr/ADR-008-ui-stack-and-design-system.md

###### AC-5 Unit and component tests run
Given a sample component with a Vitest and Testing Library test
When npm test is run
Then it exits 0, and a skipped test makes it fail
Basis: .claude/skills/frontend-stack/SKILL.md section 2, .claude/skills/tdd-workflow/SKILL.md section 4

###### AC-6 Playwright runs an accessibility scan and fails on violations
Given a sample page with an input that has no label
When npm run e2e is run with the axe scan
Then the run fails and names the axe violation; the same page with a label passes
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility)

###### AC-7 Local addresses come from configuration
Given the dev server
When it is started
Then it serves on port 5173 and reads the gateway address (http://localhost:8080) and the Keycloak address (http://localhost:8180, realm pem) from configuration, with no secret in any committed file
Basis: .claude/skills/java-microservices/SKILL.md sections 1 and 3, .claude/skills/frontend-stack/SKILL.md section 2, .claude/agents/devops-engineer.md deliverable 4

##### Out of scope
- Screens of the portal and console (ui-* stories)
- Sign-in and role-based navigation (ui-shell-login)
- Wireframes (already in modern/docs/ux/wireframes)
- The walking-skeleton page (plt-walking-skeleton)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler)
Decisions: none

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: none.


#### MAM-24 One-command local stack: docker compose, health checks, make up/down/logs/seed/reset

Type: Task. Service: platform. Labels: svc-platform, kag-MAM-24, agentic, decision-D-012, decision-D-013, decision-D-014, slice-main-journey. Depends on: MAM-16.

##### Business requirement
A developer or reviewer must be able to start the whole system on one machine with a single command, see that every part is healthy, load demonstration data, and stop or reset it again, without touching anything else that runs on the machine. Notices that the system sends must be captured locally so that they can be looked at.

##### Acceptance criteria
###### AC-1 make up starts the whole stack healthy
Given a clean clone of the repository with Docker running
When make up is run from modern/
Then docker compose ps lists postgres 16, kafka (single-node KRaft), keycloak, wiremock, mailpit, the application container and web-ui, every one healthy; in the local run mode (ADR-011) the application container hosts api-gateway and the nine business services and is healthy only when every one of them reports ready, and in the split profile each of them is its own healthy container; make up exits 0 only when all of them are healthy
Basis: .claude/commands/phase-5-platform.md (Goal, step 5), .claude/skills/java-microservices/SKILL.md sections 1 and 3, .claude/agents/devops-engineer.md deliverables 2 and 3, modern/docs/adr/ADR-010-versions.md (Postgres 16, Kafka KRaft), modern/docs/adr/ADR-011-local-run-mode-shared-jvm.md (accepted by the user 2026-11-18 as a local run mode)

###### AC-2 Pinned images and start order by health
Given modern/infra/docker-compose.yml
When a compose lint test reads it
Then every image has an exact version tag (no latest, no untagged image), every container declares a health check, and each service depends_on Postgres, Kafka and Keycloak with condition service_healthy where it needs them
Basis: .claude/agents/devops-engineer.md (Rules and deliverable 3)

###### AC-3 One database and one user per service, schemas migrated at start
Given a started stack
When each service connects with its own database user and the schemas are inspected
Then provider_db, enrollment_db, screening_db, risk_db, revalidation_db, sanctions_db, notification_db, directory_db and tmsis_db exist (created by infra/postgres/init-databases.sh with passwords from modern/infra/.env.local), each service applies its Flyway V1__init.sql (taken from modern/docs/architecture/data-model/<service>.sql) at start-up, and a service user is refused when it connects to another service's database
Basis: .claude/skills/java-microservices/SKILL.md sections 1, 3 and 8, .claude/agents/devops-engineer.md deliverables 2 and 3, modern/docs/adr/ADR-002-data-ownership-single-provider-record.md, D-013, D-014, QF-8 (default applied)

###### AC-4 Local-only secrets are generated and never committed
Given a first make up on a clone with no modern/infra/.env.local
When make up runs
Then the file is created with random values for the database users, the Keycloak admin, the four Keycloak test users (provider1, analyst1, supervisor1, admin1) and the SYSTEM client secret, the values are never printed, git check-ignore confirms the file is ignored, and git grep --untracked finds no such value in any other file
Basis: modern/docs/PROGRESS.md (Decisions 2026-11-18: random local-only passwords in modern/infra/.env.local), .claude/commands/phase-5-platform.md steps 1 and 5, MODERNIZATION.md sections 5 and 6, .gitignore (modern/infra/.env.local), QF-8 (default applied)

###### AC-5 A separate stack that does not collide with the machine
Given other Docker containers already running on the machine, and host ports 5432 and 8081 already in use
When make up and then make down are run
Then only containers of the compose project named pem are created and stopped, no other project's container is touched, the host ports published for Postgres and provider-service are configurable (variables PEM_PG_HOST_PORT and PEM_PROVIDER_HOST_PORT) and default to ports that are free, and if any published host port is busy make up stops before starting anything and names the port
Basis: modern/docs/PROGRESS.md (Decisions 2026-11-18: Docker is used as a separate stack; other projects' containers are not reused or modified), .claude/commands/phase-5-platform.md step 5

###### AC-6 The make targets behave as documented
Given modern/Makefile
When make down, logs, build, test, e2e, seed and reset are run
Then down stops the pem containers and keeps their data volumes, logs follows the pem container logs, build and test run the Gradle build and tests, e2e runs the Playwright journeys, seed loads the seed data, and reset asks for a yes before it deletes the pem volumes and changes nothing on any other answer
Basis: .claude/agents/devops-engineer.md deliverable 5, .claude/commands/phase-8-e2e-hardening.md step 1

###### AC-7 Notices and mocked sources are reachable
Given the stack is up
When a test mail is sent to SMTP port 1025 and the WireMock admin API on port 8280 is called
Then the mail appears in the Mailpit web page at http://localhost:8025 and WireMock lists the stub mappings mounted from modern/infra/wiremock
Basis: .claude/skills/java-microservices/SKILL.md section 3, D-012, D-005

###### AC-8 A runbook from clean clone to running system
Given modern/docs/runbooks/local-run.md
When its steps are followed on a clean clone
Then it lists the prerequisites, the commands, every URL and port, and the path of .env.local (never its values), and the file records whether the steps were actually executed from a clean clone
Basis: .claude/agents/devops-engineer.md deliverable 7, .claude/commands/phase-5-platform.md (Exit checks)

##### Out of scope
- Seed content (dat-seed, dat-seed-2) and stub content (dat-wiremock)
- Keycloak realm content (plt-keycloak)
- Gateway routing (plt-gateway)
- Neo4j (the user's own instance is used, not part of this stack)
- Production deployment

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler; REXX/PRVAUTOM.rexx job submission is replaced by the event bus and batch endpoints, CLAUDE.md section 10)
Decisions: D-012 REPLACE: notices are sent by email and captured locally in Mailpit; D-013 FIX: other services have no direct access to the provider data store (separate databases and users); D-014 FIX: provider-service is the single owner of the provider record (provider_db reachable only by its own user)

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: none.


#### MAM-26 Keycloak realm pem with the PROVIDER, ANALYST, SUPERVISOR, ADMIN roles, one test user per role and a SYSTEM service account

Type: Task. Service: platform. Labels: svc-platform, kag-MAM-26, agentic, decision-D-023, slice-main-journey. Depends on: MAM-24.

##### Business requirement
People must sign in with their own account and see only what their role allows. The identity service has to be set up with the four user roles, one test user for each role, and a separate service role for system-to-system calls, so that every screen and every service call can enforce access.

##### Acceptance criteria
###### AC-1 Realm pem with five roles
Given the stack is up
When http://localhost:8180/realms/pem/.well-known/openid-configuration is read and the realm roles are listed with the Keycloak admin
Then the realm answers with an issuer ending in /realms/pem and the roles PROVIDER, ANALYST, SUPERVISOR, ADMIN and SYSTEM exist, imported from infra/keycloak/realm-pem.json
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, modern/docs/architecture/nfr.md (Security), .claude/skills/java-microservices/SKILL.md section 3, .claude/agents/devops-engineer.md deliverable 3

###### AC-2 One test user per role
Given the users provider1, analyst1, supervisor1 and admin1
When each signs in through the web UI client
Then each access token carries exactly its own role of the PEM set: PROVIDER, ANALYST, SUPERVISOR and ADMIN respectively
Basis: .claude/commands/phase-5-platform.md step 1 (user decision 2026-11-18: test users provider1, analyst1, supervisor1, admin1), .claude/agents/devops-engineer.md deliverable 3, D-023

###### AC-3 No password is committed
Given the committed realm file and compose files
When git grep --untracked is run and a sign-in is tried with a wrong password
Then no password or secret value is found, the sign-in fails, and the four test-user passwords and the Keycloak admin password are read from KC_PROVIDER_PASSWORD, KC_ANALYST_PASSWORD, KC_SUPERVISOR_PASSWORD, KC_ADMIN_PASSWORD and KC_BOOTSTRAP_ADMIN_PASSWORD in modern/infra/.env.local
Basis: .claude/commands/phase-5-platform.md steps 1 and 5, MODERNIZATION.md section 5, modern/docs/PROGRESS.md (Decisions 2026-11-18)

###### AC-4 Web UI client uses authorization code with PKCE
Given the realm
When the client of the web UI is inspected
Then it is a public client that uses the authorization code flow with PKCE, its redirect address and web origin are http://localhost:5173 only, and it does not allow the password grant
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, .claude/skills/frontend-stack/SKILL.md section 2, .claude/agents/devops-engineer.md deliverable 4

###### AC-5 A service account carries the SYSTEM role
Given a confidential service-account client for system calls (sanctions-service calling provider-service, schedulers starting batch jobs)
When it requests a token with client credentials
Then the token carries the role SYSTEM and no user role, and the client secret is read from modern/infra/.env.local
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, modern/docs/architecture/overview.md (difference 1), D-013, QF-8 (default applied)

###### AC-6 The PROVIDER user is tied to an NPI
Given provider1 signed in
When its access token is read
Then it carries the claim npi with the value 1003000111, and the tokens of analyst1, supervisor1 and admin1 carry no npi claim
Basis: QF-1 (default applied), modern/docs/ux/journeys.md#J1 step 2, modern/docs/ux/journeys.md#J4 step 4, modern/docs/architecture/nfr.md (a PROVIDER only sees their own data)

##### Out of scope
- Sign-in screens and role-based navigation (ui-shell-login)
- Endpoint role checks (each service story and nfr-security)
- Real production identity provider

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (the PINQ inquiry had no authorisation, D-023)
Decisions: D-023 FIX: role-based access with Keycloak roles on every endpoint and screen; this story provides the roles and users

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: none. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM.


#### MAM-29 API gateway: routing to every service, CORS for the web UI, token relay

Type: Task. Service: api-gateway. Labels: svc-api-gateway, kag-MAM-29, agentic, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-26.

##### Business requirement
Screens and other callers need one front door to the whole system. It forwards every request to the service that owns it, passes on the identity of the signed-in user, lets the web application call it from the browser, and answers clearly when something is wrong.

##### Acceptance criteria
###### AC-1 Every contract path is routed to its owning service
Given the gateway at http://localhost:8080, the nine service skeletons running, and the nine OpenAPI contracts (1.0.0)
When a request with an X-Correlation-Id is sent through the gateway to every path of those contracts, including /api/v1/batch/..., /api/v1/legacy/... and /fhir/...
Then each request is forwarded to the service that owns it (provider 8081, enrollment 8082, screening 8083, risk 8084, revalidation 8085, sanctions 8086, notification 8087, directory 8088, tmsis 8089), shown by its correlation id in that service's log, and no request gets the gateway's own 404 'no route' answer
Basis: .claude/agents/devops-engineer.md deliverable 4, modern/docs/architecture/overview.md (api-gateway row), modern/contracts/openapi/*.yaml (version 1.0.0), .claude/skills/java-microservices/SKILL.md section 1

###### AC-2 Directory and FHIR need no token, everything else does
Given a caller with no token
When GET /api/v1/directory/providers, GET /fhir/Practitioner, GET /fhir/Organization and GET /fhir/PractitionerRole are called through the gateway, and then GET /api/v1/providers
Then the four public calls are forwarded to directory-service without a token and are never answered 401, and GET /api/v1/providers answers 401
Basis: modern/docs/adr/ADR-006-fhir-scope.md, modern/docs/adr/ADR-007-authentication-and-roles.md, modern/docs/architecture/nfr.md (Security), D-023

###### AC-3 The token is relayed unchanged
Given an ANALYST token and a PROVIDER token
When a protected path is called through the gateway with each
Then the owning service receives exactly the Authorization header that was sent, so the role decision is made by the service itself, and the gateway adds no role, claim or identity header of its own
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, .claude/skills/java-microservices/SKILL.md section 9, .claude/agents/devops-engineer.md deliverable 4, D-023

###### AC-4 CORS allows the web UI only
Given the web UI origin http://localhost:5173 and another origin
When a preflight OPTIONS request is sent from each
Then the answer for http://localhost:5173 allows the methods and the headers Authorization, Content-Type and X-Correlation-Id, and the other origin gets no CORS allow header
Basis: .claude/agents/devops-engineer.md deliverable 4, .claude/skills/frontend-stack/SKILL.md section 2

###### AC-5 A correlation id is accepted or generated
Given one request with an X-Correlation-Id header and one without
When both are sent through the gateway
Then the first keeps its id and the second gets a generated one, the service receives the id on both, and both responses return it in the X-Correlation-Id header
Basis: modern/docs/architecture/overview.md (api-gateway: correlation id), .claude/skills/java-microservices/SKILL.md section 11, modern/docs/architecture/nfr.md (Audit)

###### AC-6 Clear errors for unknown routes and unavailable services
Given an unknown path, and a service that is stopped
When each is called through the gateway
Then the unknown path answers 404 and the stopped service answers 503, both as RFC 7807 problem details that carry the correlation id and no stack trace or internal host name
Basis: .claude/skills/java-microservices/SKILL.md section 6, D-024

##### Out of scope
- Role checks inside the services and the 200 answers of each endpoint (each service story, nfr-security)
- Rate limiting and caching
- Production TLS

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (CICS transaction PINQ as the old entry point, replaced by gateway and console; D-022, D-023)
Decisions: D-023 FIX: tokens are relayed so every service enforces roles; protected routes answer 401 without a token; D-024 FIX: explicit error handling; failures are surfaced as problem details with a correlation id

##### Technical notes (agent-owned)
Service: api-gateway. Endpoints/events: GET /api/v1/directory/providers, GET /fhir/Practitioner, GET /fhir/Organization, GET /fhir/PractitionerRole, GET /api/v1/providers. Roles: PUBLIC, PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM.


#### MAM-31 Event backbone: CloudEvents-style envelope, transactional outbox, idempotent consumers

Type: Task. Service: platform. Labels: svc-platform, kag-MAM-31, agentic, decision-D-024, decision-D-012, slice-main-journey. Depends on: MAM-16, MAM-24.

##### Business requirement
Services must tell each other about business changes reliably, so that an approved provider reaches the directory and triggers a notice even if a service restarts or the same message arrives twice. This story gives every service the same, tested way to publish and to receive such messages.

##### Acceptance criteria
###### AC-1 Every event has the agreed envelope, topic and key
Given any service publishing an event
When the message on its Kafka topic is read
Then it is JSON with id (uuid), type, source, time (ISO-8601), subject (the NPI), correlationId (uuid) and data, it is on topic pem.<service>.events with the NPI as message key, and a contract test fails when a message does not match modern/contracts/asyncapi/events.yaml
Basis: .claude/skills/java-microservices/SKILL.md section 7, modern/docs/adr/ADR-003-events-and-outbox.md, modern/contracts/asyncapi/events.yaml (Envelope)

###### AC-2 The event is written in the same transaction as the change
Given a service that changes its state and publishes an event
When the transaction commits, and in a second case rolls back
Then after the commit the event appears once on the topic, and after the rollback no event appears
Basis: modern/docs/adr/ADR-003-events-and-outbox.md, .claude/skills/java-microservices/SKILL.md section 7

###### AC-3 No event is lost when Kafka is unavailable
Given Kafka is stopped while a service commits a change
When Kafka is started again
Then the event is published after the restart (at least once, none lost) and the outbox row is marked published
Basis: modern/docs/adr/ADR-003-events-and-outbox.md (at-least-once delivery)

###### AC-4 A repeated event is applied once
Given a consumer that has handled event id X
When the same event id X is delivered again
Then the second delivery has no effect on the consumer's data and the consumer's processed_event record holds X once
Basis: modern/docs/adr/ADR-003-events-and-outbox.md, .claude/skills/java-microservices/SKILL.md section 7, modern/docs/architecture/nfr.md (Availability: Kafka consumers are idempotent)

###### AC-5 The correlation id travels with the event
Given a request that carries X-Correlation-Id
When it causes an event that another service consumes
Then the envelope correlationId equals that id, and the consumer's log lines and any event it publishes next carry the same id
Basis: .claude/skills/java-microservices/SKILL.md section 11, modern/docs/architecture/nfr.md (Audit), modern/docs/adr/ADR-003-events-and-outbox.md

###### AC-6 The eight topics exist before services start
Given make up
When the Kafka topic list is read
Then pem.enrollment.events, pem.provider.events, pem.screening.events, pem.risk.events, pem.revalidation.events, pem.sanctions.events, pem.notification.events and pem.tmsis.events exist
Basis: modern/contracts/asyncapi/events.yaml (channels), .claude/skills/java-microservices/SKILL.md section 7

###### AC-7 A message that cannot be processed is parked and surfaced, not dropped
Given a consumer that fails on one message every time
When the message is delivered and later messages arrive on the same topic
Then the message is retried a configured number of times, then written to the dead-letter topic <topic>.DLT with its original envelope, an error log line names its event id and correlation id, and the later messages are still processed
Basis: QF-13 (default applied), D-024, modern/docs/adr/ADR-003-events-and-outbox.md (at-least-once, none lost)

###### AC-8 One shared module for envelope, outbox and idempotency
Given the Gradle build
When ./gradlew projects is run and the service build files are read
Then the module libs:service-kit holds the envelope type, the outbox writer and poller, the idempotent-consumer helper and the dead-letter handler (package gov.medicaid.pem.kit.events), every service that publishes or consumes events depends on it, and ADR-012 records the module
Basis: QF-4 (default applied), .claude/skills/java-microservices/SKILL.md sections 5 and 7, modern/docs/adr/ADR-003-events-and-outbox.md, modern/docs/adr/ADR-012-shared-service-kit-library.md

##### Out of scope
- Business content of each event (each service story)
- Replay tooling for dead-lettered events
- Event schema changes (contract changes need the user's approval)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: REXX/PRVAUTOM.rexx:15,23,40 (job submit, SDSF polling, alert routing stubbed; replaced by the event bus, CLAUDE.md section 10); BATCH/PRVSAN01.cbl:124-138 (dynamic CALL of PRVNT*, replaced by events and notification-service, D-012)
Decisions: D-024 FIX: explicit error handling; a message that cannot be processed is retried, dead-lettered and logged with its ids, never silent; D-012 REPLACE: the dynamic CALL of missing notice programs becomes events consumed by notification-service

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: ApplicationValidated, ApplicationRejected, ProviderRegistered, ProviderStatusChanged, ScreeningCompleted, RiskTierAssigned, RevalidationDue, SanctionApplied, NoticeSent, ExtractProduced. Roles: SYSTEM.


#### MAM-34 Walking skeleton: provider lookup by NPI (console -> gateway -> provider-service -> Postgres, with login)

Type: Task. Service: provider-service. Labels: svc-provider-service, kag-MAM-34, agentic, decision-D-014, decision-D-022, decision-D-023, slice-main-journey. Depends on: MAM-29, MAM-50, MAM-19.

##### Business requirement
Before the real features are built, one thin feature must work from the analyst's screen all the way to the database and back, with sign-in and access control in place. Looking up a provider by NPI proves that the platform, the gateway, the identity service, the first service and the web application are wired together correctly.

##### Acceptance criteria
###### AC-1 An analyst reads a seeded provider through the gateway
Given the stack is up and seeded, and an ANALYST token
When GET /api/v1/providers/{npi} is called through the gateway for a seeded NPI
Then it returns that seeded provider
Basis: .claude/commands/phase-5-platform.md step 4 (AC-1), D-014

###### AC-2 No token gives 401, another provider's NPI gives 403
Given the stack is up and seeded
When GET /api/v1/providers/{npi} is called through the gateway without a token, and then with a PROVIDER token for another NPI
Then the call without a token returns 401 and the call with the PROVIDER token for another NPI returns 403
Basis: .claude/commands/phase-5-platform.md step 4 (AC-2), D-023, QF-1 (default applied)

###### AC-3 The console page Provider lookup shows the provider
Given an analyst is signed in to the console
When they look up a seeded NPI on the console page Provider lookup
Then the page shows the provider's name, status and risk tier
Basis: .claude/commands/phase-5-platform.md step 4 (AC-3), D-022, modern/docs/ux/screen-inventory.md (console provider-search)

##### Out of scope
- Search by Medicaid ID or name (ui-console-search, prv-search-read)
- The full provider detail page (ui-console-detail)
- Role-based navigation and portal home (ui-shell-login)
- Writing or changing providers

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (PINQ inquiry replaced, D-022; no authorisation, D-023); VSAM/PRVVSM01.jcl:14-17 and DB2/PRVDDL01.sql:19-33 (dual system of record, D-014)
Decisions: D-014 FIX: provider-service is the single owner of the provider record that the lookup reads; D-022 REPLACE: console inquiry by NPI that shows the status; D-023 FIX: role-based access, 401 without a token and 403 for another provider's record

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/providers/{npi}. Roles: ANALYST, PROVIDER.


#### MAM-36 Health, structured logs and correlation IDs across services (NFR)

Type: Story. Service: platform. Labels: svc-platform, kag-MAM-36, agentic, decision-D-024. Depends on: MAM-31, MAM-24, MAM-29, MAM-46, MAM-17.

##### Business requirement
Operations staff and developers must be able to tell at a glance whether each part of the system is healthy, and to follow one request or business change across every service when something goes wrong.

##### Acceptance criteria
###### AC-1 Every service reports its health
Given a started service
When GET /actuator/health is called without a token, with its database stopped, and again after it is restarted
Then it answers 200 with status UP when the database (and Kafka, where used) is reachable and 503 with status DOWN while the database is stopped
Basis: .claude/skills/java-microservices/SKILL.md section 11, .claude/agents/devops-engineer.md deliverable 2, QF-7 (default applied), D-024

###### AC-2 Info and metrics are exposed but protected
Given a started service
When GET /actuator/info and GET /actuator/metrics are called without a token and with an ADMIN token
Then without a token they answer 401 and with an ADMIN token they answer 200
Basis: .claude/skills/java-microservices/SKILL.md section 11, QF-7 (default applied)

###### AC-3 Logs are JSON with the correlation id
Given a request with an X-Correlation-Id header
When the service's log output is read
Then every line is a JSON object with timestamp, level, service name, message and that correlationId
Basis: modern/docs/architecture/nfr.md (Audit: logs are JSON), .claude/skills/java-microservices/SKILL.md section 11

###### AC-4 The id is generated when missing and follows the events
Given a POST /api/v1/applications for a valid synthetic application sent through the gateway without X-Correlation-Id
When enrollment-service and provider-service have handled it and the logs of the gateway and both services are read
Then a generated id appears in the response header, in the log lines of the gateway, enrollment-service and provider-service, and in the envelopes of ApplicationValidated and ProviderRegistered
Basis: .claude/skills/java-microservices/SKILL.md section 11, modern/docs/adr/ADR-003-events-and-outbox.md, modern/docs/architecture/nfr.md (Audit), modern/docs/architecture/overview.md (main flow 1)

###### AC-5 Compose health uses the real health endpoint
Given the stack from make up
When one service's database is stopped
Then docker compose ps shows that service as unhealthy within the health-check interval and healthy again after the database returns
Basis: .claude/agents/devops-engineer.md deliverables 2 and 3, modern/docs/architecture/nfr.md (Availability)

##### Out of scope
- Dashboards, alerting and tracing back ends
- Log retention (Q-012: nothing is deleted until the business sets a policy)
- Business audit trail of provider changes (prv-audit)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:102-116,187-189; PRVSCR01.cbl:85-93,114,149; PRVRSK01.cbl:70-76,97; PRVREV01.cbl:76-80,93; PRVTMS01.cbl:92-100,116; PRVSAN01.cbl:90-101,115; PRVDIR01.cbl:64-65,73-81,89 (file statuses untested, return codes never set; D-024)
Decisions: D-024 FIX: failures are visible through health, JSON logs and correlation ids instead of being ignored

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: POST /api/v1/applications, ApplicationValidated, ProviderRegistered. Roles: ADMIN, PROVIDER.


#### MAM-39 CI pipeline: build, check, contract lint, web-ui tests, compose smoke test

Type: Task. Service: platform. Labels: svc-platform, kag-MAM-39, agentic. Depends on: MAM-16, MAM-19, MAM-24, MAM-34.

##### Business requirement
Every change should be checked automatically with the same steps the team runs by hand, so that a broken build, a violated contract or a failing screen is noticed before anyone depends on it.

##### Acceptance criteria
###### AC-1 Build and full check on every change
Given the CI workflow
When a change is pushed or proposed
Then it runs the Gradle build and check (unit, acceptance, integration, contract, ArchUnit, JaCoCo, PIT), and a change that lowers coverage below 85% turns the run red
Basis: .claude/agents/devops-engineer.md deliverable 6, .claude/skills/tdd-workflow/SKILL.md section 2 step 7 and section 4, QF-5 (default applied)

###### AC-2 Contracts are linted
Given the OpenAPI and AsyncAPI files in modern/contracts
When the contract lint step runs
Then it lints all nine OpenAPI files and validates the AsyncAPI file, and a deliberate error in one of them fails the run
Basis: .claude/agents/devops-engineer.md deliverable 6, modern/docs/PROGRESS.md (activity phase3.3: Redocly lint clean, AsyncAPI valid)

###### AC-3 Web UI tests run
Given modern/web-ui
When the web-ui step runs
Then npm ci and npm test run and a failing or skipped test fails the step
Basis: .claude/agents/devops-engineer.md deliverable 6, .claude/skills/tdd-workflow/SKILL.md section 4

###### AC-4 A compose smoke test proves the stack starts
Given a clean CI runner with Docker
When the smoke step runs make up, then calls the gateway health and the provider lookup of the walking skeleton, then make down
Then every container is healthy, both calls answer as expected, and the step fails and prints the logs of any container that is not healthy
Basis: .claude/agents/devops-engineer.md deliverable 6, .claude/commands/phase-5-platform.md (Goal)

###### AC-5 Pinned tools and a written fallback
Given the workflow file and modern/docs/runbooks/ci.md
When both are read
Then every action and image in the workflow has an exact version (no latest), and modern/docs/runbooks/ci.md lists the same steps (build and check, contract lint, web-ui tests, compose smoke test) with their exact commands for anyone who does not use the workflow
Basis: .claude/agents/devops-engineer.md (Rules, deliverable 6), QF-5 (default applied)

###### AC-6 The workflow is inert until the user decides
Given the repository state
When the workflow file's location is checked
Then it is at modern/.github/workflows/ci.yml, nothing is pushed, and the runbook says it must be copied to the repository root .github/workflows to run on GitHub
Basis: QF-5 (default applied), modern/docs/PROGRESS.md (Phase 0 note: git stays local, push only when the user asks), .claude/agents/devops-engineer.md ('You may write only': modern/.github/**)

##### Out of scope
- Security scans such as the dependency check (nfr-security)
- Deployment and release
- Running the Playwright journeys in CI (e2e stories, make e2e)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (platform enabler)
Decisions: none

##### Technical notes (agent-owned)
Service: platform. Endpoints/events: GET /api/v1/providers/{npi}. Roles: none.


### MAM-2 Synthetic data and legacy formats

#### MAM-42 Fixed-width codec with layouts exported from the graph (PRVRECRD 130 bytes and every legacy file)

Type: Task. Service: libs/fixed-width. Labels: svc-libs/fixed-width, kag-MAM-42, agentic, decision-D-007, decision-D-004, decision-D-015, decision-D-025, decision-D-024, slice-main-journey. Depends on: MAM-16.

##### Business requirement
The new system must read and write the old fixed-width files byte for byte, so that its output can later be compared with the old mainframe's output. One shared, tested library handles every legacy file layout, and the layouts are taken from the analysed legacy definitions rather than typed by hand.

##### Acceptance criteria
###### AC-1 Layouts are exported, not typed
Given the graph export of the legacy records
When the layout files under libs/fixed-width/src/main/resources/layouts are read
Then there is one JSON layout for each of PRVRECRD (130), SCRNREC (67), APPINPUT (200), APPERROR (250), SANCIN (80), REVALQ (80), TMSISOUT (120) and DIROUT (100), each names the graph read query that produced it, and every field position equals the position in modern/docs/analysis/data-layouts.md
Basis: .claude/skills/synthetic-data/SKILL.md sections 2 and 3, .claude/agents/data-engineer.md (deliverable 1 and Rules), modern/docs/analysis/data-layouts.md

###### AC-2 The provider master is 130 bytes and the intake record keeps bytes 131-200 reserved
Given the PRVRECRD layout
When a record is written and read
Then NPI is bytes 1-10, Medicaid ID 11-19, type 20-21, taxonomy 22-31, risk tier 32, enrolment status 33, screen flag 34, effective date 35-42, revalidation due date 43-50 and the variant 51-130, so the owner NPI at bytes 121-130 survives; a 121-byte master record is rejected; and APPINPUT is 200 bytes with bytes 1-130 as PRVRECRD and 131-200 reserved
Basis: D-007, D-025, Q-009, modern/docs/analysis/data-layouts.md (The 121 / 130 issue; File records defined inside programs)

###### AC-3 The variant is read by provider type
Given a PRVRECRD record
When it is read with type IN, and with type GR or FA
Then type IN gives last name (30, byte 51), first name (20, byte 81), licence number (15, byte 101), licence state (2, byte 116) and 13 bytes of filler; type GR or FA gives legal name (40, byte 51), DBA name (30, byte 91) and owner NPI (10, byte 121)
Basis: modern/docs/analysis/data-layouts.md (PRVRECRD), .claude/skills/synthetic-data/SKILL.md section 2 (REDEFINES discriminator), D-008

###### AC-4 Field types and dates
Given the supported field types
When values are written
Then alphanumeric fields are right-padded with spaces, numeric display fields are left zero-filled, the date 2026-01-15 is written as 20260115 in a CCYYMMDD field and as 26015 in a YYDDD field, and reading a YYDDD value requires the caller to give the century (no fixed pivot year)
Basis: D-004, D-015, .claude/skills/synthetic-data/SKILL.md section 2, modern/docs/adr/ADR-009-dates-codes-and-money.md

###### AC-5 Parse then write gives identical bytes
Given a sample record for every layout, including an IN record and a GR record
When each is parsed and written again
Then the written bytes are identical to the input bytes
Basis: .claude/skills/synthetic-data/SKILL.md section 2 (round-trip test), .claude/agents/data-engineer.md deliverable 1

###### AC-6 Bad input is rejected with an explicit error
Given a record of the wrong length, a non-numeric character in a numeric field, or an impossible date such as 20260230
When it is read
Then reading fails with an error naming the layout, record number and field, and nothing is silently truncated, padded or defaulted
Basis: D-024, D-007, D-004

###### AC-7 The library check passes
Given libs/fixed-width
When ./gradlew :libs:fixed-width:check is run
Then it exits 0 with the round-trip tests and the quality gates passing
Basis: .claude/agents/data-engineer.md deliverable 1, .claude/commands/phase-5-platform.md (Exit checks)

##### Out of scope
- Business validation of field values (each service)
- The services' own readers and writers (adapter/legacy in each service)
- EBCDIC conversion (dat-ebcdic)
- DB2 DCLGEN layout (not used by any program, D-014)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: COPYBOOKS/PRVRECRD.cpy:9-44; COPYBOOKS/SCRNREC.cpy:7-21; BATCH/PRVENR01.cbl:52-55 (APPINPUT) and 62-65 (APPERROR); BATCH/PRVSAN01.cbl:53-65 (SANCIN); BATCH/PRVREV01.cbl:46-54 (REVALQ); BATCH/PRVTMS01.cbl:47-50 and 60-82,114 (TMSISOUT, YYDDD); BATCH/PRVDIR01.cbl:44-53 (DIROUT)
Decisions: D-007 FIX: the provider master record is 130 bytes; D-004 FIX: 4-digit dates everywhere internally; D-015 PRESERVE: YYDDD only inside the legacy T-MSIS layout; D-025 PRESERVE: intake = PRVRECRD in bytes 1-130, bytes 131-200 reserved; D-024 FIX: explicit errors instead of silent handling

##### Technical notes (agent-owned)
Service: libs/fixed-width. Endpoints/events: none. Roles: none.


#### MAM-44 Deterministic synthetic datasets covering at least 95% of the verified rules

Type: Task. Service: testdata. Labels: svc-testdata, kag-MAM-44, agentic, decision-D-007, decision-D-008, decision-D-006, decision-D-016, decision-D-026, decision-D-004, slice-main-journey. Depends on: MAM-42.

##### Business requirement
Tests and later comparisons with the old system need realistic but entirely fictitious data that exercises every business rule outcome, generated the same way every time. No real provider data may ever enter the project.

##### Acceptance criteria
###### AC-1 The generator is deterministic and documents its seed
Given the generator and the seed recorded in modern/testdata/README.md
When it is run twice
Then every generated file is byte-identical between the two runs (equal sha256), and the README records the seed and the frozen business date 2026-01-15
Basis: .claude/skills/synthetic-data/SKILL.md sections 1 and 4, .claude/agents/data-engineer.md deliverable 2

###### AC-2 Enrolment intake covers every validation branch
Given fixed-width/appinput-baseline.dat
When it is read with the APPINPUT layout
Then every record is 200 bytes and the file has at least one valid IN, one valid GR, one valid FA, one record with a missing NPI (E101), one with an invalid type (E102), one with a missing taxonomy (E104) and one with a duplicate NPI (E103)
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-016, D-025

###### AC-3 The provider master seed covers every state
Given fixed-width/provider-master-seed.dat
When it is read with the PRVRECRD layout
Then every record is 130 bytes and together they hold every enrolment status P, A, T, D and S, every screen flag U, P and F, every risk tier L, M and H, the taxonomy prefixes 332, 251, 341, 291, 225 and one other, and revalidation due dates before, equal to and after 2026-01-15, all in 4-digit-year form
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-006, D-007, D-004, .claude/skills/pem-domain/SKILL.md sections 2 and 3

###### AC-4 Sanction input covers every action
Given fixed-width/sancin-baseline.dat
When it is read with the SANCIN layout
Then every record is 80 bytes and the file has T, S and R actions, a record for an unknown NPI and a record with an invalid action code
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-026

###### AC-5 The eight sample applications become legacy intake records
Given the 8 applications in aws-transform/test-data/providers.txt and the type mapping of QF-2
When scenarios/ is generated
Then a 200-byte APPINPUT record exists for each of the NPIs 1003000111, 1003000222, 1003000333, 1003000444, 1003000555, 1003000666, 1003000777 and 1003000888 with the feed type code mapped to IN, GR or FA, with synthetic Medicaid IDs and taxonomies added (the feed has none; 207Q00000X for 1003000111), and an expected-outcome list names 1003000444 as failing with E203, 1003000555 as failing with E202, 1003000111, 1003000222, 1003000333, 1003000777 and 1003000888 as passing, and 1003000666 with no expected outcome yet (QF-14)
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-008, D-016, CLAUDE.md section 9, QF-2 (default applied), QF-14 (default applied), modern/docs/ux/journeys.md#J1 step 2

###### AC-6 Coverage of at least 95% of the rules is measured
Given testdata/coverage.csv with rule_id, fixture_file and record_ref
When it is compared with the 433 non-DROPPED rules of modern/docs/architecture/rule-to-service.csv
Then at least 95% of those rules have a fixture row, and every uncovered rule is listed with a reason
Basis: .claude/agents/data-engineer.md deliverable 3, .claude/skills/synthetic-data/SKILL.md section 4, .claude/commands/phase-5-platform.md (Exit checks), QF-9 (default applied)

###### AC-7 No real data
Given all generated files
When a scan runs over them
Then every generated NPI has a valid check digit (Luhn with the 80840 prefix) and lies outside the reserved ranges 1003000000-1003000999 and 1990000000-1990000999, generated names are obviously fake (for example TESTPROV ALPHA, SAMPLE CLINIC 01), the only other NPIs and names are the fabricated values of aws-transform/test-data used verbatim in scenarios/, and the scan fails on anything else
Basis: .claude/skills/synthetic-data/SKILL.md section 1, MODERNIZATION.md section 6 rule 4, QF-12 (default applied)

###### AC-8 Every file parses with the shared library
Given every dataset under fixed-width/ and scenarios/
When it is read with libs/fixed-width
Then every record has the exact length of its layout (provider master 130) and the round trip returns identical bytes
Basis: D-007, .claude/skills/synthetic-data/SKILL.md section 2

##### Out of scope
- SQL seed files and make seed (dat-seed)
- WireMock stubs (dat-wiremock)
- The 10,000-provider performance set (nfr-performance)
- The screening outcome of 1003000666 (screening stories, QF-14)
- Real data of any kind

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:128-159 (validation branches, E101); BATCH/PRVSAN01.cbl:104-116 (T, S, R actions); BATCH/PRVRSK01.cbl:70-76,86-97,126-137 (taxonomy prefixes, due dates); COPYBOOKS/PRVRECRD.cpy:9-44
Decisions: D-007 FIX: provider master datasets are 130-byte records; D-008 PRESERVE: IN/GR/FA plus a mapping table; feed codes are mapped in the scenarios; D-006 FIX: the seed includes the new SUSPENDED status S; D-016 FIX: E101-E104 branches and E202/E203 outcomes are covered; D-026 FIX: an invalid sanction action code is included; D-004 FIX: 4-digit dates in every file

##### Technical notes (agent-owned)
Service: testdata. Endpoints/events: none. Roles: none.


#### MAM-47 WireMock stubs for the external screening sources (NPPES, LEIE, SAM, licence board, SSA DMF): hit, clean, error, slow

Type: Task. Service: infra/wiremock. Labels: svc-infra/wiremock, kag-MAM-47, agentic, decision-D-005, decision-D-016, slice-main-journey. Depends on: MAM-24, MAM-44.

##### Business requirement
Screening checks a provider against outside lists (a federal exclusion list, a debarment list, a state licence board, death records and the national provider registry). Those outside services are not available locally, so look-alike stand-ins are needed that can answer found, not found, error and slow, so that screening and the user journeys can be run safely with made-up data.

##### Acceptance criteria
###### AC-1 Five sources with four answers each
Given infra/wiremock and the stack is up
When the WireMock admin mappings are listed
Then there is a folder and mappings for each of leie, sam, nppes, license-board and ssa-dmf, and each source has a hit stub, a clean stub, a 500-error stub and a slow-response stub
Basis: .claude/skills/synthetic-data/SKILL.md section 5, .claude/agents/data-engineer.md deliverable 4, D-005

###### AC-2 The journey J1 applicant is clean on every source
Given provider 1003000111 with licence MD12345 / TX
When each source is asked about that NPI and licence
Then every source answers clean (not excluded, licence ACTIVE with expiry 2028-09, not debarred, not deceased)
Basis: modern/docs/ux/journeys.md#J1 step 4, aws-transform/test-data/license_board.txt, aws-transform/test-data/providers.txt, D-005

###### AC-3 LEIE excludes NPI 1990000111
Given the LEIE stub
When it is asked about NPI 1990000111 (the owner of 1003000444) and about any other NPI
Then 1990000111 answers excluded with exclusion type 1128(a)(1) PROGRAM-RELATED FRAUD and date 2023-01-15, and every other NPI answers clean
Basis: aws-transform/test-data/leie.txt, modern/docs/ux/journeys.md#J3 step 1, .claude/skills/synthetic-data/SKILL.md section 4, D-016

###### AC-4 The licence board knows the sample and seed licences
Given the licence-board stub
When it is asked about each licence in aws-transform/test-data/license_board.txt, about each licence of a seeded individual, and about XX00000 / TX
Then PT99900 / TX answers EXPIRED (expiry 2024-01), the other six sample licences answer ACTIVE with the expiry in the file, every licence of a seeded individual answers ACTIVE, and XX00000 / TX and any other licence answer not found
Basis: aws-transform/test-data/license_board.txt, aws-transform/test-data/providers.txt, .claude/skills/synthetic-data/SKILL.md section 4 (licence PT99900 expired), D-016 (E202), modern/docs/ux/journeys.md#J6 step 3 (seeded providers pass re-screening)

###### AC-5 SAM is empty by design, with one documented hit
Given the SAM stub
When it is asked about any entity, and about the one fabricated debarred entity named in the stub README
Then every entity answers clean except the documented one, which answers debarred
Basis: aws-transform/test-data/sam.txt, .claude/skills/synthetic-data/SKILL.md sections 4 and 5, D-016 (E205)

###### AC-6 SSA DMF has one documented deceased hit
Given the SSA DMF stub
When it is asked about any NPI, and about the one fabricated NPI named in the stub README
Then every NPI answers not deceased except the documented one, which answers deceased
Basis: .claude/skills/synthetic-data/SKILL.md section 5, D-016 (E204)

###### AC-7 Error and slow answers for every source
Given the error and slow stubs of each source and the way the README says to select them
When each is called
Then the error stub answers HTTP 500 and the slow stub answers only after its documented delay, which is longer than 5 seconds, so that retry and manual-review paths can be tested
Basis: .claude/skills/synthetic-data/SKILL.md section 5, modern/docs/architecture/nfr.md (Performance: screening of one provider under 5 s with mocked sources)

###### AC-8 The stubs are documented and tested
Given infra/wiremock/README.md and the running stack
When a test calls every stub described in the README
Then the README documents the simple JSON request and response of each source and how to select each answer, and every documented call returns exactly the documented answer
Basis: .claude/skills/synthetic-data/SKILL.md section 5, .claude/agents/data-engineer.md deliverable 4

##### Out of scope
- How screening calls the sources, retries, timeouts and how a 'not found' licence is classified (scr-screen-sources, scr-verdict)
- Whether screening calls NPPES (the overview lists four sources; the stub exists for the skill's five)
- Real external APIs (a later release)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVSCR01.cbl:116-132,146-149 (the four hard-coded Pass checks replaced by real calls, D-005)
Decisions: D-005 REPLACE: real screening against mocked external sources; these stubs are the mocked sources; D-016 FIX: E201-E205 outcomes can be produced from the stubs

##### Technical notes (agent-owned)
Service: infra/wiremock. Endpoints/events: none. Roles: none.


#### MAM-50 Seed data for the local stack (make seed): the provider seed

Type: Task. Service: testdata. Labels: svc-testdata, kag-MAM-50, agentic, decision-D-006, decision-D-007, decision-D-014, slice-main-journey. Depends on: MAM-24, MAM-44.

##### Business requirement
To demonstrate and test the system, a freshly started local stack needs a realistic set of fictitious providers in every state, loaded with one command and safe to repeat, so that every journey can start from the same known state.

##### Acceptance criteria
###### AC-1 make seed loads the provider seed as stored in the legacy file
Given the stack is up and an ANALYST token
When make seed is run and GET /api/v1/providers/{npi} is called for every NPI of fixed-width/provider-master-seed.dat
Then each call returns 200 with the same Medicaid ID, type, taxonomy, risk tier, enrolment status, screen flag, effective date, revalidation due date and name or organisation fields (including the owner NPI) as the 130-byte record, with dates in 4-digit-year form
Basis: .claude/agents/devops-engineer.md deliverable 5 (make seed), .claude/skills/synthetic-data/SKILL.md section 4 (sql/<service>-seed.sql: the same providers), D-007, D-014, D-004

###### AC-2 The provider seed covers every state
Given the seeded stack
When the seeded providers are read through GET /api/v1/providers/{npi}
Then together they hold every enrolment status P, A, T, D and S, every screen flag U, P and F, every risk tier L, M and H and the taxonomy prefixes 332, 251, 341, 291, 225 and one other
Basis: .claude/skills/synthetic-data/SKILL.md section 4, D-006, .claude/skills/pem-domain/SKILL.md sections 2 and 3

###### AC-3 The seed supports the journeys
Given the seeded stack and the journey NPIs listed in modern/testdata/README.md
When those providers are read through GET /api/v1/providers/{npi}
Then there is an ACTIVE individual with first name TESTPROV and last name ALPHA, ACTIVE providers with a revalidation due date on or before 2026-03-31 and at least one with a later due date, one ACTIVE and one TERMINATED provider whose names contain HOSPICE, and every licence of a seeded individual is one the licence-board stub answers ACTIVE
Basis: modern/docs/ux/journeys.md#J5 step 1, modern/docs/ux/journeys.md#J6 steps 1-3, modern/docs/ux/journeys.md#J7 step 3, .claude/skills/synthetic-data/SKILL.md section 1

###### AC-4 Journey applicants are never pre-registered
Given the seeded stack and an ANALYST token
When GET /api/v1/providers/{npi} is called for 1003000111, 1003000222, 1003000333, 1003000444, 1003000555, 1003000666, 1003000777, 1003000888 and 1990000111
Then each call returns 404, so applying with them is never a duplicate (E103), and no seeded NPI lies in 1003000000-1003000999 or 1990000000-1990000999
Basis: modern/docs/ux/journeys.md#J1 step 2, modern/docs/ux/journeys.md#J3 step 1, aws-transform/test-data/providers.txt, D-016 (E103), QF-12 (default applied)

###### AC-5 make seed restores exactly the seed state
Given a seeded stack in which the test set-up has added one extra provider row
When make seed is run again, and then a third time
Then after each run GET /api/v1/providers/{npi} returns 404 for the extra provider and the seeded providers are unchanged, so each journey can start from the same state
Basis: QF-6 (default applied), .claude/commands/phase-8-e2e-hardening.md step 2 (journeys run 3 times in a row), .claude/agents/devops-engineer.md deliverable 5

###### AC-6 A seed failure is explicit
Given one service's database is not reachable
When make seed is run
Then it stops with a non-zero exit and a message naming that service, and running make seed again once the database is back gives the full seed state
Basis: D-024

###### AC-7 Seed data is synthetic
Given all SQL seed files
When a scan runs over them
Then it finds only obviously fake names (such as TESTPROV ALPHA, SAMPLE CLINIC 01) and NPIs with a valid check digit outside the reserved ranges, and fails on anything else
Basis: .claude/skills/synthetic-data/SKILL.md section 1, MODERNIZATION.md section 6 rule 4, QF-12 (default applied)

##### Out of scope
- Seeded screening results, sanction history, directory entries and revalidation read model, checked through their own APIs (dat-seed-2)
- Keycloak users (plt-keycloak imports the realm)
- Notice template text (notification-service)
- The scenario applications of the journeys (dat-synthetic scenarios)
- The 10,000-provider set (nfr-performance)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: COPYBOOKS/PRVRECRD.cpy:9-44 (provider record); VSAM/PRVVSM01.jcl:14-17 and DB2/PRVDDL01.sql:19-33 (the two legacy stores replaced by one, D-014)
Decisions: D-006 FIX: seeded providers include SUSPENDED; D-007 FIX: seed provider records hold every 130-byte field including the owner NPI; D-014 FIX: provider-service holds the one seeded provider record

##### Technical notes (agent-owned)
Service: testdata. Endpoints/events: GET /api/v1/providers/{npi}. Roles: ANALYST.


#### MAM-51 Seed data for the local stack: screening results, sanction history and read models in each service

Type: Task. Service: testdata. Labels: svc-testdata, kag-MAM-51, agentic, decision-D-018, decision-D-028, decision-D-029. Depends on: MAM-50, MAM-37, MAM-25, MAM-52, MAM-61, MAM-63, MAM-45, MAM-48.

##### Business requirement
The seeded providers must look the same in every part of the system: their screening results, their sanction history, the public directory and the revalidation tracking must match the provider seed, so that the sanction, revalidation and directory journeys can start from the seed without replaying history.

##### Acceptance criteria
###### AC-1 The provider list matches the seed file
Given the seeded stack and an ANALYST token
When GET /api/v1/providers is called, after one make seed and again after a second make seed
Then both times the total equals the number of records in fixed-width/provider-master-seed.dat and the listed NPIs are exactly those of the file
Basis: .claude/skills/synthetic-data/SKILL.md section 4, QF-6 (default applied), D-014

###### AC-2 Screened providers have one result per source
Given the seeded stack and an ANALYST token
When GET /api/v1/screenings?npi=<npi> is called for every seeded provider with screen flag P or F
Then each has a completed screening with one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF; a provider with flag P has only P results and a provider with flag F has at least one F result with its error code
Basis: D-018, D-005, modern/docs/architecture/overview.md (main flow 2: four mocked sources, one result per source), modern/contracts/openapi/screening-service.yaml (SourceResult)

###### AC-3 Terminated and suspended providers have a sanction history
Given the seeded stack and an ANALYST token
When GET /api/v1/sanctions?npi=<npi> is called for every seeded provider with status T or S
Then each has a sanction row whose action matches its status (T for TERMINATED, S for SUSPENDED) with a reason and an effective date
Basis: D-018, D-006, modern/contracts/openapi/sanctions-service.yaml (Sanction)

###### AC-4 The directory lists exactly the active providers with composed names
Given the seeded stack and a caller with no token
When GET /api/v1/directory/providers is called page by page
Then it lists exactly the seeded providers with status A, an individual's displayName is the first name then the last name (TESTPROV ALPHA) and an organisation's displayName is its legal name
Basis: D-028, Q-008, modern/docs/architecture/overview.md (main flow 6: read model of active providers)

###### AC-5 The revalidation read model is seeded, not replayed
Given the seeded stack, no event replayed since make seed
When an ADMIN runs POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31 and GET /api/v1/revalidations?status=DUE is called with an ANALYST token
Then there is exactly one DUE case for each seeded ACTIVE provider whose due date is on or before 2026-03-31 and none for any other provider
Basis: D-029, D-020, modern/docs/architecture/overview.md (revalidation-service keeps its own read model, difference 4), modern/docs/ux/journeys.md#J6 steps 1-2

##### Out of scope
- The provider seed itself (dat-seed)
- Seeded notices (notification-service starts empty)
- Seeded T-MSIS archive generations (tmsis-service starts empty)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: VSAM/PRVVSM02.jcl:15-27 (SCREEN.RESULTS and SANCTION.MASTER never used, D-018); BATCH/PRVDIR01.cbl:88 (raw-byte name, D-028); BATCH/PRVREV01.cbl:81-94 (no queue state, D-029)
Decisions: D-018 FIX: seeded screening results and sanction history live in their own services' tables; D-028 FIX: seeded directory names are composed from the name fields; D-029 FIX: one revalidation case per due provider, from a seeded read model

##### Technical notes (agent-owned)
Service: testdata. Endpoints/events: GET /api/v1/providers, GET /api/v1/screenings, GET /api/v1/sanctions, GET /api/v1/directory/providers, POST /api/v1/batch/revalidation-sweeps, GET /api/v1/revalidations. Roles: ANALYST, ADMIN, PUBLIC.


#### MAM-53 EBCDIC to ASCII converter for the Phase 9 parity run

Type: Task. Service: tools/data. Labels: svc-tools/data, kag-MAM-53, agentic, decision-D-024. Depends on: none.

##### Business requirement
When the old batch jobs are run on a mainframe emulator, their output files come back in mainframe character encoding. The team needs a small, reliable tool that converts them to readable text without changing record lengths, so the old and new outputs can be compared.

##### Acceptance criteria
###### AC-1 A command-line tool converts a file
Given a file in EBCDIC code page 037 or 1047
When tools/data/ebcdic_to_ascii.py is run with the input path, the output path and the code page as a parameter
Then the output file has the same number of bytes as the input, one ASCII byte per EBCDIC byte, with no line terminators added
Basis: .claude/agents/data-engineer.md deliverable 5, .claude/skills/parity-testing/SKILL.md sections 2 and 4

###### AC-2 The code page is required and checked
Given a run with no code page, or with an unsupported one
When the tool is started
Then it exits with a non-zero code and a message that lists the supported code pages 037 and 1047, and writes no output file
Basis: .claude/agents/data-engineer.md deliverable 5, .claude/skills/parity-testing/SKILL.md section 2, D-024

###### AC-3 Known text converts as expected under both code pages
Given a fixture holding letters, digits, space and the punctuation that the legacy layouts use, encoded in 037 and in 1047
When each is converted with its own code page
Then both give the same expected ASCII text recorded in the test, and converting a 037 file with 1047 gives a different result for the bytes where the two pages differ
Basis: .claude/skills/parity-testing/SKILL.md sections 2 and 4

###### AC-4 Round trip
Given an ASCII fixed-width fixture
When it is encoded with a code page in the test and converted back with the same page
Then the result equals the original bytes
Basis: .claude/skills/parity-testing/SKILL.md section 4

###### AC-5 Record length is verified and non-text bytes are reported
Given a file whose size is not a multiple of the record length given with --lrecl, or a byte that converts to a character outside printable ASCII
When the tool runs
Then it exits non-zero with a message naming the file size and record length, or the byte offset and value of the offending byte, and never writes a replacement character
Basis: D-024, .claude/skills/parity-testing/SKILL.md section 2 (exact record lengths)

###### AC-6 The original is never touched
Given an input path and an output path that is the same file
When the tool runs
Then it refuses with a message, and in every run the input file's sha256 is unchanged
Basis: .claude/skills/parity-testing/SKILL.md section 4 step 1 (keep the originals)

##### Out of scope
- Capturing the legacy outputs (done in the other environment)
- Packed-decimal or binary fields (the legacy layouts hold display fields only)
- Comparison of the converted files (par-harness)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (supports the Phase 9 run of JCL/PRVDAY01.jcl and JCL/PRVMTH01.jcl; capture scaffolding in aws-transform/07-test-data-collection)
Decisions: D-024 FIX: explicit errors for bad code pages, sizes and bytes

##### Technical notes (agent-owned)
Service: tools/data. Endpoints/events: none. Roles: none.


### MAM-3 provider-service

#### MAM-17 Register a validated application as a PENDING, UNSCREENED provider

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-17, agentic, decision-D-001, decision-D-003, decision-D-004, decision-D-007, decision-D-008, decision-D-014, decision-D-016, decision-D-019, decision-D-028, slice-main-journey. Depends on: MAM-31, MAM-34.

##### Business requirement
When an enrolment application has passed its checks, the state must record the applicant as a provider in one single provider record that every other part of the system relies on. A newly recorded provider is always waiting for a decision and has not yet been screened. Every detail on the application is kept, including the owner of a group or a facility. An application that failed its checks never becomes a provider, and an application for a provider who is already on file never overwrites the existing record.

##### Acceptance criteria
###### AC-1 A validated individual application becomes a PENDING, UNSCREENED provider
Given provider-service is running and no provider with NPI 1003000111 exists
When it consumes an ApplicationValidated event for NPI 1003000111 (Medicaid ID MCD000111, provider type IN, taxonomy 207Q00000X, individual: last name RIVERA, first name MARIA, licence MD12345, licence state TX)
Then GET /api/v1/providers/1003000111 as SYSTEM returns 200 with enrollStatus P, screenFlag U, riskTier null, revalidationDueDate null and the NPI, Medicaid ID, provider type, taxonomy and individual fields exactly as supplied
Basis: R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00040, R-PRVENR01-cbl-00041, R-PRVENR01-cbl-00042, R-PRVENR01-cbl-00056, D-001 (the status is always set by the service, never taken from the input), D-003 (no due date before approval), D-008, D-014, D-019

###### AC-2 Registration publishes ProviderRegistered
Given the registration of NPI 1003000111 in AC-1 has been saved
When the outbox poller publishes the event
Then exactly one ProviderRegistered event is on topic pem.provider.events with message key 1003000111 and data npi 1003000111, medicaidId MCD000111, providerType IN, taxonomy 207Q00000X, enrollStatus P, screenFlag U and displayName 'MARIA RIVERA' (first name + last name)
Basis: modern/contracts/asyncapi/events.yaml (ProviderRegistered, channel pem.provider.events), ADR-003, R-PRVENR01-cbl-00042, D-028 (name composition: individual first + last, organisation legal name)

###### AC-3 A group keeps every field, including the owner NPI
Given no provider with NPI 1003000444 exists
When provider-service consumes an ApplicationValidated event for NPI 1003000444 (Medicaid ID MCD000444, provider type GR, taxonomy 251E00000X, organization: legal name EXCLUDED CLINIC LLC, DBA name EXCLUDED CLINIC, owner NPI 1990000111)
Then GET /api/v1/providers/1003000444 returns organization.legalName EXCLUDED CLINIC LLC, organization.dbaName EXCLUDED CLINIC and organization.ownerNpi 1990000111 (the owner NPI is not lost), no individual block, enrollStatus P and screenFlag U; the ProviderRegistered event carries displayName 'EXCLUDED CLINIC LLC'
Basis: R-PRVENR01-ADD-02, D-007, D-008, D-028, modern/docs/analysis/data-layouts.md (PRV-ORG-DATA, PRV-OWNER-NPI bytes 121-130)

###### AC-4 A duplicate NPI never overwrites the existing provider
Given provider 1003000111 is registered and its status is A
When a second ApplicationValidated event (a different event id) for NPI 1003000111 arrives
Then GET /api/v1/providers/1003000111 still returns the existing record unchanged (status A, same version); no second ProviderRegistered event is published; the service log contains 'DUPLICATE NPI 1003000111'; and the skipped-registrations counter on /actuator/metrics rises by 1
Basis: R-PRVENR01-cbl-00058, R-PRVENR01-cbl-00051, D-001, D-016 (E103 is raised at submission by enrollment-service; this is the second line of defence), .claude/skills/java-microservices/SKILL.md section 11 (Actuator metrics, JSON logs)

###### AC-5 A rejected application never creates a provider
Given enrollment-service rejected the application for NPI 1003000222 (submitted with an empty taxonomy, E104) and published only ApplicationRejected for it, and published ApplicationValidated for NPI 1003000111
When provider-service has processed every event on pem.enrollment.events and an ANALYST looks for NPI 1003000222
Then GET /api/v1/providers/1003000222 returns 404, GET /api/v1/providers?npi=1003000222 returns total 0, no ProviderRegistered event exists for 1003000222, and NPI 1003000111 is registered normally (P, U)
Basis: D-001, R-PRVENR01-cbl-00051 (routing an invalid record to the write step is the defect to correct), modern/docs/architecture/overview.md (main flow 1: an invalid application is never registered), modern/docs/ux/journeys.md#J2

###### AC-6 A redelivered event registers once
Given the ApplicationValidated event with id E1 for NPI 1003000111 was already processed
When the same event E1 is delivered again
Then there is still exactly one provider 1003000111, exactly one ProviderRegistered event exists, and the skipped-registrations counter is unchanged (a replay is ignored, not counted as a duplicate)
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7 (idempotent consumers, processed_event)

###### AC-7 The effective date is the registration date, in ISO form
Given the service clock date is 2026-11-18
When provider 1003000111 is registered
Then GET /api/v1/providers/1003000111 returns effectiveDate 2026-11-18 (a 4-digit-year ISO date) and revalidationDueDate null
Basis: Q-A1 (effective date = registration date), D-004, ADR-009

##### Out of scope
- Validation of the application (E101 missing NPI, E102 invalid type, E103 duplicate NPI at submission, E104 missing taxonomy) and the rejection notice: enrollment-service and notification-service (ntf-send-2).
- Screening, risk tier, approval and sanctions: prv-screen-verdict, prv-risk-tier, prv-approve, prv-status-change.
- Reading and searching the record: prv-search-read. Exporting the 130-byte master: prv-legacy-master.
- Owner disclosure rows (provider_owner: owner name, ownership percentage, disclosure date): no legacy program or application field feeds them.
- The 200-byte APPINPUT intake layout (bytes 131-200 reserved, D-025): enrollment-service.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-02, R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00040, R-PRVENR01-cbl-00041, R-PRVENR01-cbl-00042, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00056, R-PRVENR01-cbl-00058
Programs: PRVENR01: source-code/BATCH/PRVENR01.cbl:57-159 (master record, WRITE-PROVIDER-RECORD), 146-148 (VALIDATE-APPLICATION: SET PRV-PENDING, SET PRV-UNSCREENED, PERFORM WRITE-PROVIDER-RECORD), 157-171 (WRITE-PROVIDER-RECORD, duplicate key 163-166). Job step: PRVDAY01 STEP010 (source-code/JCL/PRVDAY01.jcl).
Decisions: D-001 FIX: every application is processed; an invalid one is rejected once and never reaches the master; D-003 FIX: no due date at registration; it is set at approval; D-004 FIX: 4-digit-year ISO dates; D-007 FIX: the record is 130 bytes; the owner NPI (bytes 121-130) is kept; D-008 PRESERVE: provider types IN, GR, FA; D-014 FIX: provider-service is the single owner of provider identity; D-016 FIX: E103 duplicate NPI is raised at submission by enrollment-service; here the existing record is kept and the skip counted; D-019 FIX: registration always yields PENDING; only an analyst approval sets ACTIVE; D-028 FIX: the display name is composed from the name fields

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: ApplicationValidated, ProviderRegistered. Roles: none.


#### MAM-21 Record the screening verdict (screen flag) on the provider

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-21, agentic, decision-D-005, decision-D-010, decision-D-014, decision-D-019, slice-main-journey. Depends on: MAM-17.

##### Business requirement
The result of screening a provider (cleared or not cleared, taking every source into account) must be recorded on the provider's record, so that analysts can see who is cleared and who is not. Recording a result never makes a provider active: only an analyst's decision does. Later re-screenings of an enrolled provider record their result in the same way and never change the enrolment status.

##### Acceptance criteria
###### AC-1 A passed screening sets the screen flag to P
Given provider 1003000111 is PENDING with screenFlag U
When provider-service consumes a ScreeningCompleted event for 1003000111 with reason INITIAL and result PASS (all four sources passed)
Then GET /api/v1/providers/1003000111 returns screenFlag P and enrollStatus still P
Basis: R-PRVSCR01-cbl-00046, R-PRVSCR01-cbl-00050, D-005, modern/contracts/asyncapi/events.yaml (ScreeningCompleted)

###### AC-2 A failed screening sets the screen flag to F
Given provider 1003000444 is PENDING with screenFlag U and screening-service found its owner 1990000111 on the (mocked) OIG LEIE
When provider-service consumes a ScreeningCompleted event for 1003000444 with reason INITIAL and result FAIL
Then GET /api/v1/providers/1003000444 returns screenFlag F and enrollStatus P, and GET /api/v1/providers?status=P&screenFlag=F lists 1003000444
Basis: R-PRVSCR01-cbl-00047, R-PRVSCR01-cbl-00051, D-005, modern/docs/ux/journeys.md#J3

###### AC-3 Screening never activates a provider
Given provider 1003000111 is PENDING with screenFlag U
When a ScreeningCompleted event with result PASS is consumed
Then enrollStatus is still P (not A) and no ProviderStatusChanged event is published for 1003000111; only the analyst approval (prv-approve) can set A
Basis: R-PRVSCR01-ADD-03, D-019

###### AC-4 Every verdict is saved
Given provider 1003000111 has screenFlag U and version V
When the ScreeningCompleted PASS event is consumed
Then a later GET /api/v1/providers/1003000111 returns screenFlag P and version V+1 (the verdict was written back to the provider record, not only held in memory)
Basis: R-PRVSCR01-cbl-00011, R-PRVSCR01-cbl-00049, R-PRVSCR01-cbl-00052

###### AC-5 A re-screening records its verdict the same way and never changes the status
Given provider 1003000111 is ACTIVE with screenFlag P
When a ScreeningCompleted event with reason REVALIDATION (or MANUAL) and result FAIL is consumed
Then GET /api/v1/providers/1003000111 returns screenFlag F and enrollStatus A; a later ScreeningCompleted PASS sets screenFlag P again
Basis: R-PRVSCR01-ADD-03, Q-A4 (a re-screening changes only the screen flag, plus the due date on a revalidation PASS), modern/contracts/asyncapi/events.yaml (ScreeningCompleted.reason INITIAL, REVALIDATION, MANUAL)

###### AC-6 A redelivered verdict is applied once
Given the ScreeningCompleted event with id E2 was already processed for provider 1003000111
When E2 is delivered again
Then GET /api/v1/providers/1003000111 shows the version did not increase a second time and the screen flag is unchanged
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7

##### Out of scope
- Running the checks against the licence board, OIG LEIE, SAM and SSA DMF and deciding the verdict (scr-screen-sources, scr-owner, scr-verdict).
- One screening row per source with date and detail (screening-service, D-018).
- The Medicare reciprocity check (D-010 DROP): not built.
- Setting the revalidation due date after a completed revalidation: prv-approve-2.
- The legacy SCRNOUT file: scr-batch-run.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-03, R-PRVSCR01-cbl-00011, R-PRVSCR01-cbl-00046, R-PRVSCR01-cbl-00047, R-PRVSCR01-cbl-00049, R-PRVSCR01-cbl-00050, R-PRVSCR01-cbl-00051, R-PRVSCR01-cbl-00052
Programs: PRVSCR01: source-code/BATCH/PRVSCR01.cbl:90-114 (SCREEN-NEXT-PROVIDER / SCREEN-ONE-PROVIDER: verdict 106-112, REWRITE 114). Job step: PRVDAY01 STEP030 (source-code/JCL/PRVDAY01.jcl; proc PRVSCRPR is never called).
Decisions: D-005 REPLACE: the verdict comes from the real screening-service over mocked sources; provider-service only records it; D-010 DROP: Medicare reciprocity check, nothing built; D-014 FIX: provider-service is the only writer of the screen flag; D-019 FIX: screening never sets ACTIVE; an analyst approval does

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: ScreeningCompleted. Roles: none.


#### MAM-23 Record the assigned risk tier on the provider

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-23, agentic, decision-D-002, decision-D-003, decision-D-014, slice-main-journey. Depends on: MAM-17.

##### Business requirement
The risk level assigned to a provider (limited, moderate or high) must be saved on the provider's record for every provider, and replaced when a new level is assigned, so that analysts and the directory see it. Assigning a level changes nothing else on the record; in particular it does not move the date by which the provider must be revalidated.

##### Acceptance criteria
###### AC-1 The assigned tier is saved on the provider
Given provider 1003000111 is registered, screenFlag P, riskTier null
When provider-service consumes a RiskTierAssigned event for 1003000111 with tier L (taxonomy 207Q00000X, ruleSetVersion 1)
Then GET /api/v1/providers/1003000111 returns riskTier L
Basis: R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048, D-002, modern/docs/ux/journeys.md#J1

###### AC-2 The tier is saved for every provider, not only the first
Given providers 1003000111 (taxonomy 207Q00000X), 1003000222 (taxonomy 332B00000X) and 1003000888 (taxonomy 341600000X) are registered with riskTier null
When RiskTierAssigned events arrive for them with tiers L, H and M
Then GET /api/v1/providers/{npi} returns riskTier L for 1003000111, H for 1003000222 and M for 1003000888 (provider-service stores the tier it receives and does not recompute it)
Basis: D-002, R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048, .claude/skills/pem-domain/SKILL.md sections 2 and 3 (tiers L, M, H; prefixes 332 High, 341 Moderate)

###### AC-3 A new tier replaces the old one
Given provider 1003000111 has riskTier L
When a RiskTierAssigned event with tier M arrives
Then GET /api/v1/providers/1003000111 returns riskTier M
Basis: R-PRVRSK01-cbl-00048, R-PRVRSK01-cbl-00010

###### AC-4 Assigning a tier changes nothing else, in particular not the due date
Given provider 1003000111 is ACTIVE, screenFlag P, effectiveDate 2026-11-18 and revalidationDueDate 2031-11-18
When a RiskTierAssigned event with tier M arrives
Then GET /api/v1/providers/1003000111 returns riskTier M while enrollStatus A, screenFlag P, effectiveDate 2026-11-18 and revalidationDueDate 2031-11-18 are unchanged, and no ProviderStatusChanged event is published
Basis: R-PRVRSK01-ADD-01, D-003 (the due date is set at approval and at completed revalidations, not by the risk step), modern/docs/architecture/overview.md (difference 5), R-PRVRSK01-cbl-00010

###### AC-5 Arrival order does not matter
Given provider 1003000111 is registered and its screenFlag is still U (RiskTierAssigned and ScreeningCompleted travel on different topics)
When a RiskTierAssigned event with tier L arrives before the ScreeningCompleted event
Then GET /api/v1/providers/1003000111 returns riskTier L and screenFlag U, and the later ScreeningCompleted PASS then sets screenFlag P without removing the tier
Basis: modern/contracts/asyncapi/events.yaml (pem.screening.events and pem.risk.events are separate topics), ADR-003

###### AC-6 A redelivered tier event is applied once
Given the RiskTierAssigned event with id E3 was already processed
When E3 is delivered again
Then GET /api/v1/providers/1003000111 shows the version did not increase a second time and the tier is unchanged
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7

##### Out of scope
- Choosing the tier from the taxonomy prefix and keeping the prefix table as data (risk-service: rsk-assign-tier, rsk-rules-admin; Q-005).
- Setting or resetting the revalidation due date: moved to approval and completed revalidation (prv-approve, prv-approve-2; D-003).
- The legacy rule that re-tiered every provider on every daily run (R-PRVRSK01-ADD-01): not reproduced.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-cbl-00010, R-PRVRSK01-cbl-00048
Related rules: R-PRVRSK01-ADD-01
Programs: PRVRSK01: source-code/BATCH/PRVRSK01.cbl:95-97 (CLASSIFY-ONE: PERFORM APPLY-TIER-PARA, SET-REVALIDATION-DATE, REWRITE at 97); 70-97 (no status filter, ADD-01). Job step: PRVDAY01 STEP040 (source-code/JCL/PRVDAY01.jcl).
Decisions: D-002 FIX: the risk tier is saved for every provider; D-003 FIX: the due date is not set by the risk step; D-014 FIX: provider-service is the only writer of the risk tier

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: RiskTierAssigned. Roles: none.


#### MAM-27 Analyst approves or denies an enrolment; approval sets ACTIVE and the revalidation due date

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-27, agentic, decision-D-003, decision-D-014, decision-D-019, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-21, MAM-23.

##### Business requirement
An analyst reviews a provider whose application is pending and either approves or denies the enrolment. Approval is the only way a new provider becomes active; it is allowed only after screening passed, and it starts the five-year revalidation clock. A denial closes the enrolment. Both decisions are announced so that the directory, the provider and the revalidation process are informed, and a decision cannot be taken twice.

##### Acceptance criteria
###### AC-1 Approval sets ACTIVE and the revalidation due date
Given provider 1003000111 is PENDING, screenFlag P, riskTier L, and the service clock date is 2026-11-18
When an ANALYST (analyst1) sends POST /api/v1/providers/1003000111/approval with decision APPROVE
Then the response is 200 with enrollStatus A and revalidationDueDate 2031-11-18 (approval date + 5 years); a later GET /api/v1/providers/1003000111 returns the same values
Basis: D-019, D-003, R-PRVRSK01-cbl-00009, R-PRVRSK01-cbl-00047, modern/docs/ux/journeys.md#J1, modern/contracts/openapi/provider-service.yaml (decideProvider)

###### AC-2 Approval publishes ProviderStatusChanged
Given the approval in AC-1 has been saved
When the outbox poller publishes the event
Then exactly one ProviderStatusChanged event on pem.provider.events with key 1003000111 has from P, to A, actor analyst1, revalidationDueDate 2031-11-18, providerType IN, displayName 'MARIA RIVERA', taxonomy 207Q00000X and medicaidId MCD000111
Basis: modern/contracts/asyncapi/events.yaml (ProviderStatusChanged), D-019, D-028, modern/docs/ux/journeys.md#J1

###### AC-3 Denial sets DENIED and leaves no due date
Given provider 1003000444 is PENDING with screenFlag F
When an ANALYST sends POST /api/v1/providers/1003000444/approval with decision DENY and reason 'Owner 1990000111 is on the OIG LEIE'
Then the response is 200 with enrollStatus D and revalidationDueDate null, and a ProviderStatusChanged event with from P, to D and that reason is published
Basis: D-019, Q-A2 (DENY allowed when the screen flag is P or F), modern/docs/ux/journeys.md#J3

###### AC-4 Approval needs a passed screening; denial needs a finished one
Given provider 1003000444 is PENDING with screenFlag F and provider 1003000222 is PENDING with screenFlag U
When an ANALYST sends decision APPROVE for 1003000444, APPROVE for 1003000222 and DENY for 1003000222
Then each response is 422 problem+json, both providers are unchanged (enrollStatus P, revalidationDueDate null) and no ProviderStatusChanged event is published
Basis: D-019 (approval follows a Pass screening), Q-A2, modern/contracts/openapi/provider-service.yaml (422 Business validation failed)

###### AC-5 A decision cannot be taken twice and never resets the due date
Given provider 1003000111 is ACTIVE with revalidationDueDate 2031-11-18
When an ANALYST sends decision APPROVE, and then DENY, for it again
Then each response is 409 problem+json, enrollStatus stays A, revalidationDueDate stays 2031-11-18 and no event is published
Basis: R-PRVRSK01-ADD-01, D-003, Q-A2 (a decision needs status PENDING), modern/contracts/openapi/provider-service.yaml (409 Conflict with the current state)

###### AC-6 An unknown provider gives 404
Given no provider with NPI 1003000999 exists
When an ANALYST sends POST /api/v1/providers/1003000999/approval with decision APPROVE
Then the response is 404 problem+json and nothing is created
Basis: modern/contracts/openapi/provider-service.yaml (404 Not found), D-024

###### AC-7 Only ANALYST and SUPERVISOR can decide
Given providers 1003000111 and 1003000333 are PENDING with screenFlag P
When POST /api/v1/providers/1003000111/approval with decision APPROVE is called with a PROVIDER token (provider1), an ADMIN token, a SYSTEM token and no token; then an ANALYST approves 1003000111 and a SUPERVISOR approves 1003000333
Then PROVIDER, ADMIN and SYSTEM get 403 and no token gets 401, with 1003000111 still P after those calls; the ANALYST and the SUPERVISOR each get 200
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml (decideProvider x-roles ANALYST, SUPERVISOR)

###### AC-8 Approval does not wait for the risk tier
Given provider 1003000111 is PENDING, screenFlag P and riskTier null
When an ANALYST approves it
Then the response is 200 with enrollStatus A and riskTier null; the tier is saved later when RiskTierAssigned arrives (prv-risk-tier)
Basis: Q-A5 (no risk tier needed to approve), modern/contracts/openapi/provider-service.yaml (decideProvider)

##### Out of scope
- Date arithmetic edge cases (century, leap day) and the due date after a completed revalidation: prv-approve-2.
- Console screens for approving and denying and the work queues: ui-console-approval, ui-console-queues.
- The notices for approval and denial: ntf-send-2. The directory update: dir-projection. The revalidation case: rev-sweep.
- Automatic approval: none exists; D-019 requires a human analyst decision.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-01, R-PRVRSK01-cbl-00009, R-PRVRSK01-cbl-00047
Programs: PRVRSK01: source-code/BATCH/PRVRSK01.cbl:70-97 (no status filter, due date reset each run, ADD-01), 137 (MOVE WS-DUE-DATE TO PRV-REVAL-DUE-DATE). Job step: PRVDAY01 STEP040 (source-code/JCL/PRVDAY01.jcl). Approval itself has no legacy program: nothing in the estate sets ACTIVE except the sanction reinstate (PRVSAN01.cbl:112; D-019).
Decisions: D-003 FIX: the due date is set at approval (and at each completed revalidation), never reset on every run; D-014 FIX: provider-service is the only writer of status and due date; D-019 FIX: an analyst approval sets ACTIVE after a Pass screening; D-023 FIX: role-based access on every endpoint; D-024 FIX: explicit errors (404, 409, 422)

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: POST /api/v1/providers/{npi}/approval, ProviderStatusChanged. Roles: ANALYST, SUPERVISOR.


#### MAM-30 Revalidation due date: five years, four-digit years, leap day and each completed revalidation

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-30, agentic, decision-D-003, decision-D-004. Depends on: MAM-27.

##### Business requirement
The date by which an active provider must be revalidated is exactly five years after the approval, or after the last completed revalidation, on the same day and month. It is always a real calendar date with a four-digit year, also after the year 2065 and also for approvals on 29 February. Only an approval or a passed revalidation moves the date; screening, risk assessment and a failed re-screening never do. Other services that track due dates are told when the date changes.

##### Acceptance criteria
###### AC-1 The due date keeps the day and month and adds five years
Given the service clock date is 2029-03-15 and provider 1003000111 is PENDING with screenFlag P
When an ANALYST approves it
Then revalidationDueDate is 2034-03-15 (same month and day, year + 5, 4-digit year), taken from the service clock and not from the request
Basis: R-PRVRSK01-cbl-00008, R-PRVRSK01-cbl-00039, R-PRVRSK01-cbl-00041, R-PRVRSK01-cbl-00045, R-PRVRSK01-cbl-00046, D-004, 42 CFR 455.414 (five-year revalidation; summary from .claude/skills/pem-domain/SKILL.md section 5, UNVERIFIED against eCFR)

###### AC-2 The century is never guessed
Given the service clock date is 2065-06-30 for one approval and 2097-04-01 for another
When each provider is approved
Then revalidationDueDate is 2070-06-30 and 2102-04-01 (the legacy 2-digit year with the pivot at 70 would have produced 19700630 and 20020401)
Basis: R-PRVRSK01-cbl-00040, R-PRVRSK01-cbl-00042, R-PRVRSK01-cbl-00043, R-PRVRSK01-cbl-00044, D-004 (pivot at 70 gives 19xx from 2065)

###### AC-3 An approval on 29 February gives a real date
Given the service clock date is 2028-02-29
When provider 1003000111 is approved
Then revalidationDueDate is 2033-02-28, never the impossible date 2033-02-29
Basis: R-PRVRSK01-ADD-02, D-004, ADR-009, Q-A3 (last day of February)

###### AC-4 A passed revalidation sets the next due date
Given provider 1003000111 is ACTIVE with revalidationDueDate 2026-03-31 and the service clock date is 2026-04-02
When provider-service consumes a ScreeningCompleted event for 1003000111 with reason REVALIDATION and result PASS
Then GET /api/v1/providers/1003000111 returns revalidationDueDate 2031-04-02 (the previous value is overwritten), enrollStatus A and screenFlag P
Basis: D-003, Q-A4, R-PRVRSK01-cbl-00047, modern/docs/architecture/overview.md (main flow 4), modern/docs/ux/journeys.md#J6

###### AC-5 The new due date is announced
Given the revalidation in AC-4 has been saved
When the outbox poller publishes the event
Then one ProviderStatusChanged event with key 1003000111 has from A, to A and revalidationDueDate 2031-04-02, so revalidation-service can update its own read model
Basis: Q-A4, modern/docs/architecture/overview.md (difference 4: revalidation-service keeps a read model from ProviderStatusChanged), modern/contracts/asyncapi/events.yaml (ProviderStatusChanged.revalidationDueDate)

###### AC-6 A failed re-screening leaves the due date and status alone
Given provider 1003000111 is ACTIVE with revalidationDueDate 2026-03-31
When a ScreeningCompleted event with reason REVALIDATION and result FAIL is consumed
Then GET /api/v1/providers/1003000111 still returns revalidationDueDate 2026-03-31 and enrollStatus A, and no ProviderStatusChanged event is published
Basis: Q-A4, R-PRVSCR01-ADD-03

###### AC-7 Only approval and a passed revalidation move the date
Given provider 1003000111 is PENDING with revalidationDueDate null, and provider 1003000333 is ACTIVE with revalidationDueDate 2031-11-18
When ScreeningCompleted PASS with reason INITIAL is consumed for the first and reason MANUAL for the second
Then GET /api/v1/providers/{npi} still returns revalidationDueDate null for 1003000111 and 2031-11-18 for 1003000333
Basis: D-003, Q-A4 (a MANUAL result changes only the screen flag), R-PRVRSK01-ADD-01

###### AC-8 A redelivered revalidation result is applied once
Given the ScreeningCompleted REVALIDATION PASS event with id E4 was already processed
When E4 is delivered again (on a later clock date)
Then revalidationDueDate keeps its value and no second ProviderStatusChanged event is published
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7

##### Out of scope
- Opening revalidation cases and the monthly sweep: rev-sweep, rev-cases (D-029).
- The re-screening itself: scr-rescreen.
- What a failed revalidation leads to (suspension or termination): a supervisor sanction (prv-status-change); no automatic action (Q-A4).
- The legacy 2-digit-year logic, the pivot at 70 and the daily reset (D-003, D-004): not reproduced.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-02, R-PRVRSK01-cbl-00008, R-PRVRSK01-cbl-00039, R-PRVRSK01-cbl-00040, R-PRVRSK01-cbl-00041, R-PRVRSK01-cbl-00042, R-PRVRSK01-cbl-00043, R-PRVRSK01-cbl-00044, R-PRVRSK01-cbl-00045, R-PRVRSK01-cbl-00046
Related rules: R-PRVRSK01-cbl-00047
Programs: PRVRSK01: source-code/BATCH/PRVRSK01.cbl:127-137 (SET-REVALIDATION-DATE: ACCEPT date 127, YY 128, MMDD 129, MOD(YY+5,100) 130, pivot 131-134, date 136, MOVE 137); 129-136 (Feb 29, ADD-02). Job step: PRVDAY01 STEP040 (source-code/JCL/PRVDAY01.jcl).
Decisions: D-003 FIX: the due date is set at approval and at each completed revalidation; D-004 FIX: 4-digit-year dates everywhere; no 2-digit year or century pivot is built

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: ScreeningCompleted, ProviderStatusChanged. Roles: none.


#### MAM-33 Apply a sanction status change to the provider (TERMINATED, SUSPENDED, ACTIVE)

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-33, agentic, decision-D-006, decision-D-013, decision-D-014, decision-D-023, decision-D-024, decision-D-026, decision-D-027. Depends on: MAM-17.

##### Business requirement
When a sanction is applied to an enrolled provider, the provider's enrolment status must change accordingly: a termination ends the enrolment, a suspension pauses it and is shown separately from a denial, and a reinstatement makes the provider active again. The change can only be requested through the provider service, so that no other system rewrites the provider record. It is applied whatever the provider's current status is, it is announced to the other services, and an unknown action or an incomplete request is refused.

##### Acceptance criteria
###### AC-1 Terminate sets TERMINATED and counts it
Given provider 1003000111 is ACTIVE
When a SYSTEM caller sends POST /api/v1/providers/1003000111/status-changes with action TERMINATE, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then the response is 200 with enrollStatus T, and the terminations counter on /actuator/metrics rises by 1
Basis: R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00040, D-013

###### AC-2 Suspend sets SUSPENDED, not DENIED
Given provider 1003000111 is ACTIVE
When a SYSTEM caller sends action SUSPEND, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then the response is 200 with enrollStatus S (not D) and the terminations counter does not change
Basis: R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00030, R-PRVSAN01-cbl-00041, D-006, modern/docs/ux/journeys.md#J5

###### AC-3 Reinstate sets ACTIVE
Given provider 1003000111 is SUSPENDED (status S)
When a SYSTEM caller sends action REINSTATE with effectiveDate 2026-04-30
Then the response is 200 with enrollStatus A and the terminations counter does not change
Basis: R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00042, modern/docs/ux/journeys.md#J5

###### AC-4 The change applies whatever the current status is
Given provider 1003000444 is in status D (denied) and provider 1003000222 is in status P (pending)
When REINSTATE is sent for 1003000444 and TERMINATE for 1003000222
Then GET /api/v1/providers/1003000444 returns enrollStatus A and GET /api/v1/providers/1003000222 returns T (the legacy rule applied as written)
Basis: R-PRVSAN01-ADD-04, D-027, Q-006 (answered: apply as written)

###### AC-5 The new status is saved and announced
Given provider 1003000111 is ACTIVE
When a SYSTEM caller (service-account-pem-services) applies SUSPEND with reason 'LEIE exclusion'
Then a later GET /api/v1/providers/1003000111 returns enrollStatus S, and one ProviderStatusChanged event with key 1003000111 has from A, to S, reason 'LEIE exclusion' and actor service-account-pem-services
Basis: R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00031, R-PRVSAN01-cbl-00035, R-PRVSAN01-cbl-00043, modern/contracts/asyncapi/events.yaml (ProviderStatusChanged), D-013

###### AC-6 An unknown action or an incomplete request is refused
Given provider 1003000111 is ACTIVE
When a SYSTEM caller sends action PURGE with effectiveDate 2026-03-31, and then action SUSPEND with no effectiveDate
Then each response is 422 problem+json, the provider is unchanged (status A, same version) and no ProviderStatusChanged event is published (the legacy program would have rewritten the record and called the general notice)
Basis: D-026, D-024, modern/contracts/openapi/provider-service.yaml (StatusChangeRequest: action enum TERMINATE, SUSPEND, REINSTATE; effectiveDate required; 422 is the only error listed for an invalid body)

###### AC-7 An unknown provider gives 404 and changes nothing
Given no provider with NPI 1003000999 exists
When a SYSTEM caller sends action TERMINATE with effectiveDate 2026-03-31 for 1003000999
Then the response is 404 problem+json, no provider is created and no event is published
Basis: modern/contracts/openapi/provider-service.yaml (404 Not found), D-024

###### AC-8 Only SYSTEM and SUPERVISOR can change the status
Given providers 1003000111 and 1003000333 are ACTIVE
When POST /api/v1/providers/1003000111/status-changes with action SUSPEND is called with an ANALYST token, a PROVIDER token (provider1), an ADMIN token and no token; then a SYSTEM caller suspends 1003000111 and a SUPERVISOR (supervisor1) suspends 1003000333
Then ANALYST, PROVIDER and ADMIN get 403 and no token gets 401, with 1003000111 still A after those calls; SYSTEM and SUPERVISOR each get 200
Basis: R-PRVSAN01-ADD-07, D-013, D-023, ADR-002, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint)

##### Out of scope
- The sanction history with reason and effective date, and validating T/S/R input (sanctions-service: san-record, san-intake-run; D-018).
- The TRM, SUS and RST notices: ntf-send.
- A business rule limiting which statuses a sanction may start from (Q-006 answered: not applied).
- Maintaining the provider end date from the sanction effective date (Q-A6: not done).
- Idempotency of a repeated call with the same sanctionId: not specified in any rule or decision.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-ADD-04, R-PRVSAN01-ADD-07, R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00030, R-PRVSAN01-cbl-00031, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00035, R-PRVSAN01-cbl-00040, R-PRVSAN01-cbl-00041, R-PRVSAN01-cbl-00042, R-PRVSAN01-cbl-00043
Programs: PRVSAN01: source-code/BATCH/PRVSAN01.cbl:104-116 (APPLY-SANCTION: T 106-108, S 109-110, R 111-112, REWRITE 115), 107-112 (status applied whatever the current status), 81-115 (cross-application REWRITE of the PEMAPP1 master, AP-06). No job runs PRVSAN01 in this estate.
Decisions: D-006 FIX: suspension is a new SUSPENDED status (S), not DENIED; D-013 FIX: sanctions reach the provider only through this status-change API; D-014 FIX: provider-service is the single owner of the status; D-023 FIX: role-based access; D-024 FIX: explicit errors (404, 422) instead of silent skips; D-026 FIX: unknown action codes are rejected; D-027 PRESERVE: the change applies whatever the current status (Q-006)

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: POST /api/v1/providers/{npi}/status-changes, ProviderStatusChanged. Roles: SYSTEM, SUPERVISOR.


#### MAM-37 Search and read providers by NPI or Medicaid ID, role-checked (PINQ replacement back end)

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-37, agentic, decision-D-006, decision-D-022, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-34.

##### Business requirement
Analysts, supervisors, administrators and system accounts must be able to look up a provider by the NPI they type or by Medicaid ID, and to search by name, status and screening result to build work queues, seeing every stored detail and the current status. A provider may only see their own record. The lookup must tell 'no such provider' apart from a technical failure, and nobody without the right role may read provider data.

##### Acceptance criteria
###### AC-1 Look up a provider by NPI and see every field
Given provider 1003000111 is ACTIVE, riskTier L, screenFlag P, effectiveDate 2026-11-18, revalidationDueDate 2031-11-18
When an ANALYST sends GET /api/v1/providers/1003000111
Then the response is 200 with npi 1003000111, medicaidId MCD000111, providerType IN, taxonomy 207Q00000X, riskTier L, enrollStatus A, screenFlag P, effectiveDate 2026-11-18, revalidationDueDate 2031-11-18 and individual lastName RIVERA, firstName MARIA, licenseNo MD12345, licenseState TX
Basis: R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, D-022, modern/docs/ux/screen-inventory.md (PINQ field map)

###### AC-2 An unknown NPI gives 404
Given no provider with NPI 1003000999 exists
When an ANALYST sends GET /api/v1/providers/1003000999
Then the response is 404 problem+json and contains no data of any other provider
Basis: R-PRVCICS1-cbl-00003, D-022, modern/contracts/openapi/provider-service.yaml (404 Not found)

###### AC-3 The NPI typed in the request is the key, with no carry-over
Given an ANALYST has just looked up 1003000111 successfully
When the same ANALYST then requests 1003000444, and after that 1003000999 (unknown)
Then the second response is the record of 1003000444 (never 1003000111's) and the third is 404 (never the previous provider)
Basis: R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00021, D-022 (the legacy COMMAREA carry-over is replaced; the typed NPI is the key)

###### AC-4 The status code is returned as stored, including S
Given providers exist with statuses P, A, T, D and S
When an ANALYST reads each of them
Then enrollStatus is exactly P, A, T, D and S respectively; the suspended provider shows S, not D
Basis: R-PRVCICS1-cbl-00023, D-006, D-022

###### AC-5 Search by Medicaid ID or NPI
Given provider 1003000111 has Medicaid ID MCD000111
When an ANALYST sends GET /api/v1/providers?medicaidId=MCD000111, then GET /api/v1/providers?npi=1003000111, then GET /api/v1/providers?npi=1003000999
Then the first two return an items array with exactly provider 1003000111 (total 1); the third returns 200 with an empty items array and total 0
Basis: R-PRVCICS1-ADD-07, D-022, modern/docs/ux/journeys.md#J4

###### AC-6 Search by name, status and screen flag with paging (work queues)
Given 1003000111 is PENDING with screenFlag P, 1003000444 is PENDING with screenFlag F, 1003000222 is PENDING with screenFlag U, and 25 providers exist in total
When an ANALYST sends GET /api/v1/providers?status=P&screenFlag=P, then ?status=P&screenFlag=F, then ?name=RIVERA, then ?page=1&size=20
Then the first returns only 1003000111, the second only 1003000444, the third only providers whose name contains RIVERA (1003000111), and the fourth returns 5 items with page 1, size 20 and total 25
Basis: modern/contracts/openapi/provider-service.yaml (searchProviders parameters, ProviderPage), modern/docs/ux/screen-inventory.md (console work-queues), modern/docs/ux/journeys.md#J1, modern/docs/ux/journeys.md#J3

###### AC-7 Role checks: a provider sees only their own record
Given provider1 is a PROVIDER whose token carries the npi claim 1003000111, and 1003000111 is ACTIVE
When GET /api/v1/providers/1003000111, GET /api/v1/providers/1003000444 and GET /api/v1/providers are each called with provider1's token, with ANALYST, SUPERVISOR, ADMIN and SYSTEM tokens, and with no token
Then provider1 gets 200 with enrollStatus A for 1003000111, 403 for 1003000444 and 403 for the search; ANALYST, SUPERVISOR, ADMIN and SYSTEM get 200 on all three; no token gets 401 on all three
Basis: R-PRVCICS1-ADD-05, D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml (getProvider: a PROVIDER sees only their own record), .claude/commands/phase-5-platform.md (PROVIDER token for another NPI gives 403), modern/docs/ux/journeys.md#J1 step 7, modern/docs/ux/journeys.md#J4

###### AC-8 A read failure is not reported as 'not found'
Given the provider database is unavailable
When an ANALYST sends GET /api/v1/providers/1003000111 with X-Correlation-Id 7d2f9c4e-1b3a-4c5d-8e6f-000000000123
Then the response is an RFC 7807 problem with a 5xx status (not 404) and the same X-Correlation-Id header, and the failure is logged with that correlation id
Basis: R-PRVCICS1-ADD-04, D-022, D-024, .claude/skills/java-microservices/SKILL.md sections 6 and 11

##### Out of scope
- The console search and detail screens, the 'No provider found' text and the status banner (ui-console-search, ui-console-detail).
- Screening, sanction, revalidation and notice panels of the detail page (their own services).
- The public directory search: dir-search.
- The walking-skeleton lookup (plt-walking-skeleton) is the starting point; this story completes and role-tests it.

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-ADD-07, R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, R-PRVCICS1-cbl-00023
Programs: PRVCICS1: source-code/ONLINE/PRVCICS1.cbl:28-61 (MAIN-LOGIC, no authorisation check ADD-05), 25-61 (COMMAREA of 10 bytes, ADD-07), 29-42 (retained NPI, 00015), 33-42 (captured NPI, 00019), 37-42 (READ FILE PRVPROVN, 00003 and 00022), 40 (RIDFLD, 00021), 44-50 (RESP handling, ADD-04), 45-46 (FORMAT-PROVIDER, 00023). CICS transaction PINQ; no JCL job runs it.
Decisions: D-006 FIX: SUSPENDED (S) is shown as its own status; D-022 REPLACE: inquiry by NPI or Medicaid ID; the typed NPI is the key; not-found and read errors are distinguished; D-023 FIX: role-based access on every endpoint; D-024 FIX: explicit error handling and a logged failure

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM.


#### MAM-40 Provider history: every change and every notice sent is audited and readable

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-40, agentic, decision-D-012, decision-D-018, decision-D-023. Depends on: MAM-17, MAM-21, MAM-23, MAM-27, MAM-33.

##### Business requirement
Every change to a provider's record, and every notice sent about the provider, must be kept in a history that supervisors and administrators can read: who or what made the change, when, what the record looked like before and after, and which request it belongs to. The history can be read page by page, and nobody can edit or delete it.

##### Acceptance criteria
###### AC-1 Every change writes one history entry, oldest first
Given provider 1003000111 was registered, got ScreeningCompleted PASS, got RiskTierAssigned L, was approved by an ANALYST and then suspended by a SYSTEM caller, and no NoticeSent event has been consumed for it
When a SUPERVISOR sends GET /api/v1/providers/1003000111/audit
Then the items contain exactly five entries (registration, screening verdict, risk tier, approval, suspension) ordered by time, oldest first
Basis: modern/docs/architecture/nfr.md (Audit), .claude/skills/java-microservices/SKILL.md section 8, modern/docs/architecture/data-model/provider-service.sql (audit_log)

###### AC-2 An entry shows before, after and the request it belongs to
Given the history of AC-1, where the approval was sent with X-Correlation-Id 5a1e7b20-3c4d-4e5f-9a6b-000000000456
When the SUPERVISOR reads the approval entry
Then it has at, actor, an action that names the approval, before with enrollStatus P, after with enrollStatus A and revalidationDueDate 2031-11-18, and correlationId 5a1e7b20-3c4d-4e5f-9a6b-000000000456; the registration entry has before null
Basis: modern/contracts/openapi/provider-service.yaml (AuditEntry; correlationId is a uuid), modern/docs/architecture/nfr.md (Audit: who, when, action, before, after, correlation id)

###### AC-3 The actor is the user or the producing service
Given changes made by analyst1 (approval), by the SYSTEM service account service-account-pem-services (suspension) and by events from enrollment-service, screening-service and risk-service
When the SUPERVISOR reads the history
Then the approval entry's actor is analyst1, the suspension entry's actor is service-account-pem-services, and the registration, screening and tier entries have the producing service (enrollment-service, screening-service, risk-service) as actor
Basis: modern/docs/architecture/nfr.md (Audit: who), modern/contracts/asyncapi/events.yaml (envelope source), modern/infra/keycloak/realm-pem.json (users analyst1, service-account-pem-services)

###### AC-4 A notice sent is recorded once
Given a NoticeSent event with id E5, noticeId N1, npi 1003000111 and template APPROVED
When provider-service consumes it, and then E5 is delivered a second time
Then GET /api/v1/providers/1003000111/audit has exactly one entry referring to noticeId N1 and template APPROVED, and the provider's own fields and version are unchanged
Basis: modern/contracts/asyncapi/events.yaml (NoticeSent consumed by provider-service), modern/docs/architecture/overview.md (difference 3), D-012, ADR-003

###### AC-5 A status change entry carries reason, effective date and sanction id
Given a SYSTEM caller applied SUSPEND with reason 'LEIE exclusion', effectiveDate 2026-03-31 and sanctionId S1
When the SUPERVISOR reads the suspension entry
Then it has before enrollStatus A, after enrollStatus S and carries the reason 'LEIE exclusion', the effective date 2026-03-31 and sanctionId S1; GET /api/v1/providers/1003000111 shows endDate unchanged
Basis: Q-A6, D-018, modern/contracts/openapi/provider-service.yaml (StatusChangeRequest)

###### AC-6 The history is paged
Given provider 1003000111 has 25 history entries
When a SUPERVISOR sends GET /api/v1/providers/1003000111/audit?page=1&size=20
Then the response has 5 items, page 1, size 20 and total 25
Basis: modern/contracts/openapi/provider-service.yaml (AuditPage, page and size), .claude/skills/java-microservices/SKILL.md section 6 (pagination)

###### AC-7 Only SUPERVISOR and ADMIN can read it; an unknown provider gives 404
Given provider 1003000111 exists and 1003000999 does not
When GET /api/v1/providers/1003000111/audit is called with SUPERVISOR, ADMIN, ANALYST, PROVIDER (provider1) and SYSTEM tokens and with no token, and GET /api/v1/providers/1003000999/audit with a SUPERVISOR token
Then SUPERVISOR and ADMIN get 200; ANALYST, PROVIDER and SYSTEM get 403; no token gets 401; the unknown NPI gets 404
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml (getProviderAudit x-roles, 404)

###### AC-8 The history is append-only
Given provider 1003000111 has a history
When a later change is made, and a SUPERVISOR tries PUT, PATCH and DELETE on /api/v1/providers/1003000111/audit
Then the earlier entries are returned unchanged, the new entry is added at the end, and PUT, PATCH and DELETE each get 405 problem+json
Basis: Q-012 (nothing is deleted until a retention policy is set), modern/docs/architecture/nfr.md (Data retention), modern/contracts/openapi/provider-service.yaml (only GET is defined on the audit path)

##### Out of scope
- Screening result rows (screening-service), sanction history (sanctions-service) and the notice log (notification-service): this history only holds the provider's own changes and references to notices.
- Deleting or archiving history (Q-012: nothing is deleted).
- Showing the history in the console (ui-console-detail). Note: the contract and NFR allow only SUPERVISOR and ADMIN, so the console must hide the history from ANALYST.

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy program: the legacy master kept no history of changes (D-018). Related legacy evidence: PRVSAN01.cbl:57-116 (reason and effective date never stored, R-PRVSAN01-ADD-03).
Decisions: D-012 REPLACE: every notice sent is recorded in the provider history through NoticeSent; D-018 FIX: real history instead of unused clusters; D-023 FIX: role-based access

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/providers/{npi}/audit, NoticeSent. Roles: SUPERVISOR, ADMIN.


#### MAM-43 Export the provider master in the legacy 130-byte PRVRECRD layout

Type: Story. Service: provider-service. Labels: svc-provider-service, kag-MAM-43, agentic, decision-D-004, decision-D-006, decision-D-007, decision-D-023. Depends on: MAM-17, MAM-42.

##### Business requirement
For the planned side-by-side comparison with the old system, an administrator must be able to export all providers in the old fixed 130-byte master record layout, so that the result can be compared byte for byte with the file the old system produced. Every field is included, among them the owner of a group or facility.

##### Acceptance criteria
###### AC-1 Every record is exactly 130 bytes
Given providers 1003000111 (IN), 1003000444 (GR) and 1003000777 (FA) exist
When an ADMIN sends GET /api/v1/legacy/provider-master
Then the response is 200 text/plain with one line per provider and every line exactly 130 characters long
Basis: D-007, ADR-005, modern/contracts/openapi/provider-service.yaml (exportProviderMaster), modern/docs/analysis/data-layouts.md (130 bytes)

###### AC-2 Records are in NPI order with no header or trailer
Given the three providers of AC-1
When the ADMIN exports the master
Then the lines are in ascending NPI order (1003000111, 1003000444, 1003000777) and there is no header or trailer line
Basis: CLAUDE.md section 5.3 (the master is a KSDS keyed by PRV-NPI), ADR-005

###### AC-3 An individual record has the PRVRECRD field positions
Given provider 1003000111: Medicaid ID MCD000111, type IN, taxonomy 207Q00000X, riskTier L, status A, screenFlag P, effectiveDate 2026-11-18, revalidationDueDate 2031-11-18, RIVERA, MARIA, licence MD12345, state TX
When the ADMIN exports the master
Then its line has the NPI in bytes 1-10, 'MCD000111' in 11-19, 'IN' in 20-21, the taxonomy in 22-31, 'L' in byte 32, 'A' in byte 33, 'P' in byte 34, '20261118' in 35-42, '20311118' in 43-50, 'RIVERA' space-padded in 51-80, 'MARIA' space-padded in 81-100, 'MD12345' space-padded in 101-115, 'TX' in 116-117 and spaces in 118-130
Basis: modern/docs/analysis/data-layouts.md (PRVRECRD positions), D-004 (CCYYMMDD, 4-digit year), .claude/skills/synthetic-data/SKILL.md section 2 (alphanumeric right-padded with spaces)

###### AC-4 A group record keeps the owner NPI in bytes 121-130
Given provider 1003000444: type GR, legal name EXCLUDED CLINIC LLC, DBA name EXCLUDED CLINIC, owner NPI 1990000111
When the ADMIN exports the master
Then its line has the legal name space-padded in bytes 51-90, the DBA name space-padded in 91-120 and '1990000111' in bytes 121-130 (the owner NPI is not cut off); a legal name longer than 40 characters is cut to its first 40 characters
Basis: D-007, R-PRVENR01-ADD-02, modern/docs/analysis/data-layouts.md (PRV-ORG-DATA: PRV-LEGAL-NAME X(40), PRV-DBA-NAME X(30), PRV-OWNER-NPI X(10)), .claude/skills/cobol-to-java/SKILL.md section 3 (alphanumeric MOVE truncates on the right, only in legacy-format writers)

###### AC-5 A suspended provider is exported with status S
Given provider 1003000888 has status S
When the ADMIN exports the master
Then byte 33 of its line is 'S'
Basis: D-006, modern/docs/analysis/data-layouts.md (PRV-ENROLL-STATUS byte 33)

###### AC-6 Unset values use the legacy padding
Given provider 1003000222 is PENDING with riskTier null, screenFlag U and revalidationDueDate null
When the ADMIN exports the master
Then byte 32 is a space, byte 34 is 'U' and bytes 43-50 are '00000000'
Basis: modern/docs/analysis/data-layouts.md (PRV-RISK-TIER X(01), PRV-REVAL-DUE-DATE 9(08)), .claude/skills/synthetic-data/SKILL.md section 2 (alphanumeric right-padded with spaces, numeric display left zero-filled), D-029 (the legacy treats a zero due date as unset)

###### AC-7 The export decodes to the same values as the API
Given the export of AC-1
When each line is decoded with the fixed-width codec (dat-codec) using the PRVRECRD layout
Then the decoded fields equal the fields returned by GET /api/v1/providers/{npi} for that NPI
Basis: ADR-005, D-007

###### AC-8 Only ADMIN can export
Given the providers of AC-1
When GET /api/v1/legacy/provider-master is called with ADMIN, ANALYST, SUPERVISOR, PROVIDER and SYSTEM tokens and with no token
Then ADMIN gets 200; ANALYST, SUPERVISOR, PROVIDER and SYSTEM get 403; no token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint)

##### Out of scope
- Loading a legacy master file into provider-service: no such load is planned.
- The other legacy files (APPINPUT, SCRNOUT, REVALQ, DIROUT, TMSISOUT): their own services.
- Running the legacy jobs on Hercules and comparing outputs: par-harness. EBCDIC conversion: dat-ebcdic.

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-ADD-02
Programs: No legacy program: replaces reading the VSAM master MMPRV.PROVIDER.MASTER.KSDS (source-code/VSAM/PRVVSM01.jcl:14-17, RECORDSIZE 121 vs the 130-byte copybook source-code/COPYBOOKS/PRVRECRD.cpy:9-44; PRVENR01.cbl:57-159).
Decisions: D-004 FIX: dates are written as CCYYMMDD with a 4-digit year; D-006 FIX: status S is exported for a suspended provider; D-007 FIX: the record is 130 bytes, the owner NPI is kept; D-023 FIX: ADMIN only

##### Technical notes (agent-owned)
Service: provider-service. Endpoints/events: GET /api/v1/legacy/provider-master. Roles: ADMIN.


### MAM-4 enrollment-service

#### MAM-46 Submit an enrolment application online and validate it (E101 to E104)

Type: Story. Service: enrollment-service. Labels: svc-enrollment-service, kag-MAM-46, agentic, decision-D-001, decision-D-008, decision-D-016, decision-D-019, decision-D-023, slice-main-journey. Depends on: MAM-31.

##### Business requirement
A provider, or an analyst acting for a provider, can apply for Medicaid enrolment online. Before anything else happens the system checks that the application names the provider's identification number, that the provider type is individual, group or facility, that a specialty (taxonomy) code is given, and that no application for the same identification number has already been accepted. Every failed check is reported to the applicant once, with its legacy error code. A rejected application is kept for review but never becomes a provider. An accepted application is passed on so that the provider can be registered as pending and then screened. Codes used by the old provider feeds are translated to the three official types.

##### Acceptance criteria
###### AC-1 A valid application is accepted
Given A PROVIDER user is signed in, no application exists yet for NPI 1003000111, and the user sends an application with NPI 1003000111, the synthetic Medicaid ID M00000111, providerType IN, taxonomy 207Q00000X and individual details (lastName RIVERA, firstName MARIA, licenseNo MD12345, licenseState TX)
When POST /api/v1/applications is called
Then 201 with an Application whose status is VALIDATED, source is PORTAL, errors are empty and providerType is IN; exactly one ApplicationValidated event is published with subject 1003000111 carrying applicationId, npi 1003000111, medicaidId M00000111, providerType IN, taxonomy 207Q00000X and the individual details; no ApplicationRejected event is published
Basis: R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00039, R-PRVENR01-cbl-00007, D-001, D-019, modern/docs/ux/journeys.md#J1, modern/contracts/asyncapi/events.yaml#ApplicationValidated

###### AC-2 A missing or malformed NPI fails with E101
Given A PROVIDER user is signed in and sends an otherwise valid application whose npi is an empty string, and a second one whose npi is 12345 (not 10 digits)
When POST /api/v1/applications is called with each request
Then Each call returns 422 problem+json whose errors list has exactly one entry with code E101 and field npi (the full four-character code, not cut to E1); the application is stored with status REJECTED and that single error, and GET /api/v1/applications?status=REJECTED shows it to the same user; no ApplicationValidated event and no ApplicationRejected event is published, because an event needs a 10-digit NPI as its subject and key
Basis: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00025, R-PRVENR01-cbl-00054, R-PRVENR01-ADD-05, D-001, D-016, Q-B1, Q-B5, Q-B6

###### AC-3 An invalid provider type fails with E102
Given A PROVIDER user sends an otherwise valid application for NPI 1003000222 with providerType XX, a code that is not IN, GR or FA and is not in the provider-type mapping table
When POST /api/v1/applications is called
Then 422 problem+json with exactly one error, code E102 on field providerType; the application is stored as REJECTED; exactly one ApplicationRejected event is published with subject 1003000222 and errors [E102 on providerType]; no ApplicationValidated event is published. Separate otherwise valid applications for NPI 1003000111 with providerType IN, NPI 1003000444 with providerType GR and NPI 1003000777 with providerType FA (organisation details given for GR and FA) each return 201 with status VALIDATED
Basis: R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00029, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00053, R-PRVENR01-ADD-05, D-001, D-016, Q-B1

###### AC-4 A missing taxonomy fails with E104
Given No application exists yet for NPI 1003000111, and a PROVIDER user leaves the taxonomy empty in an otherwise valid application for that NPI (journey J2)
When POST /api/v1/applications is called
Then 422 problem+json with exactly one error, code E104 on field taxonomy; the application is stored as REJECTED with that one error; exactly one ApplicationRejected event is published with subject 1003000111 and errors [E104 on taxonomy]; no ApplicationValidated event is published, so nothing is registered
Basis: R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00035, R-PRVENR01-cbl-00055, R-PRVENR01-ADD-05, D-001, D-016, modern/docs/ux/journeys.md#J2, Q-B1

###### AC-5 A duplicate NPI fails with E103
Given An application for NPI 1003000111 is already VALIDATED and a PROVIDER user submits another otherwise valid application for the same NPI
When POST /api/v1/applications is called
Then 422 problem+json with exactly one error, code E103 on field npi; the new application is stored as REJECTED and exactly one ApplicationRejected event is published for it; the first application is still VALIDATED and only one ApplicationValidated event exists for that NPI. A new application for an NPI whose earlier application was only REJECTED is accepted (VALIDATED) and does not fail E103
Basis: R-PRVENR01-ADD-04, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00058, D-016, D-001, Q-B3, modern/docs/architecture/overview.md (main flow 1)

###### AC-6 Several failed checks give one error each, and a rejected application is never registered
Given A PROVIDER user sends an application with a valid NPI 1003000333 (no earlier application), providerType XX and an empty taxonomy
When POST /api/v1/applications is called
Then 422 problem+json with exactly two errors, one E102 on providerType and one E104 on taxonomy, each listed once; exactly one REJECTED application is stored (not two); exactly one ApplicationRejected event is published carrying both errors; no ApplicationValidated event is published, so the provider is never registered and can never reach approval
Basis: R-PRVENR01-cbl-00028, R-PRVENR01-cbl-00032, R-PRVENR01-cbl-00034, R-PRVENR01-cbl-00049, R-PRVENR01-cbl-00050, R-PRVENR01-cbl-00052, D-001, D-016, D-019, Q-B1, modern/contracts/openapi/enrollment-service.yaml#Application (errors: exactly one error per failed check)

###### AC-7 Feed provider-type codes are mapped to IN, GR or FA
Given The provider-type mapping table holds IND to IN, GRP to GR and HOSP to FA, and a PROVIDER user sends otherwise valid applications for NPI 1003000111 with providerType IND, NPI 1003000444 with providerType GRP (organisation details given) and NPI 1003000777 with providerType HOSP, and one for NPI 1003000888 with providerType ZZZ, which is neither IN, GR or FA nor in the table
When POST /api/v1/applications is called for each
Then The first three return 201 with status VALIDATED, and both the Application and its ApplicationValidated event show providerType IN, GR and FA respectively; the ZZZ application returns 422 with exactly one error, code E102 on providerType
Basis: D-008, Q-B7, aws-transform/test-data/providers.txt, modern/contracts/openapi/enrollment-service.yaml#ApplicationRequest, modern/docs/architecture/data-model/enrollment-service.sql (provider_type_mapping)

###### AC-8 Only PROVIDER and ANALYST may submit
Given Tokens for PROVIDER, ANALYST, SUPERVISOR, ADMIN and SYSTEM, and a request without a token
When POST /api/v1/applications is called with a valid application
Then PROVIDER and ANALYST get 201; SUPERVISOR, ADMIN and SYSTEM get 403 problem+json; the call without a token gets 401; a body that is not valid JSON or lacks a required property gets 400 problem+json and nothing is stored
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml#submitApplication

##### Out of scope
- Registering the provider as PENDING / UNSCREENED and emitting ProviderRegistered (prv-register)
- Screening, risk tier and approval (scr-*, rsk-*, prv-approve)
- The portal form and its linked error summary (ui-portal-apply)
- The 'Application rejected' notice (ntf-send consumes ApplicationRejected)
- Checks on names, licence or owner: the legacy checks only NPI, type and taxonomy; licences and owners are screened later
- The legacy APPINPUT file load (enr-intake-run)
- Submit response-time target (nfr-performance)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00024, R-PRVENR01-cbl-00025, R-PRVENR01-cbl-00028, R-PRVENR01-cbl-00029, R-PRVENR01-cbl-00030, R-PRVENR01-cbl-00032, R-PRVENR01-cbl-00034, R-PRVENR01-cbl-00035, R-PRVENR01-cbl-00039, R-PRVENR01-cbl-00049, R-PRVENR01-cbl-00050, R-PRVENR01-cbl-00052, R-PRVENR01-cbl-00053, R-PRVENR01-cbl-00054, R-PRVENR01-cbl-00055
Related rules: R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00058
Programs: PRVENR01 VALIDATE-APPLICATION 127-148 (NPI check 128-133, type check 134-139, taxonomy check 141-145, error code move 129), INVALID-APP 155-159, WRITE-PROVIDER-RECORD duplicate branch 160-166; copybooks PRVRECRD (type codes IN/GR/FA) and ERRCODES (E101-E104); job PRVDAY01 STEP010
Decisions: D-001 FIX: each invalid application is rejected with one error per failed check, stored once, announced once and never registered; D-008 PRESERVE: IN/GR/FA kept; feed codes translated through a mapping table; D-016 FIX: E101, E102, E103 and E104 are real validation results with full four-character codes; D-019 Analyst approval sets ACTIVE: a validated application only leads to a PENDING provider and a rejected one is never registered; D-023 FIX: role check on the endpoint (PROVIDER, ANALYST)

##### Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: POST /api/v1/applications, ApplicationValidated, ApplicationRejected. Roles: PROVIDER, ANALYST.


#### MAM-54 Read application status (a provider sees their own; an analyst sees all)

Type: Story. Service: enrollment-service. Labels: svc-enrollment-service, kag-MAM-54, agentic, decision-D-001, decision-D-016, decision-D-023, slice-main-journey. Depends on: MAM-46.

##### Business requirement
Providers need to see what happened to the applications they sent, and analysts need to see every application, including the rejected ones, so that they can follow up. A provider sees only their own applications and, for a rejected one, every reason it was rejected. An analyst or supervisor sees all applications and can narrow the list by outcome or by identification number.

##### Acceptance criteria
###### AC-1 A provider lists only their own applications
Given Two PROVIDER users: user A has submitted an application for NPI 1003000111 (VALIDATED) and user B one for NPI 1003000222 (VALIDATED)
When User A calls GET /api/v1/applications, and then GET /api/v1/applications?npi=1003000222
Then The first call returns 200 with items containing only the application for 1003000111 and total 1; the second returns 200 with no items and total 0; the application for 1003000222 is never shown to user A
Basis: modern/contracts/openapi/enrollment-service.yaml#listApplications, modern/docs/architecture/nfr.md, modern/docs/architecture/data-model/enrollment-service.sql (submitted_by), Q-B2, D-023

###### AC-2 A provider cannot open someone else's application
Given User B's application id, and user A signed in as a PROVIDER
When User A calls GET /api/v1/applications/{applicationId} with B's id
Then 403 problem+json, and none of B's data is in the response
Basis: modern/docs/architecture/nfr.md, D-023, .claude/commands/phase-5-platform.md, modern/docs/ux/journeys.md#J4, Q-B2

###### AC-3 A validated application shows its status
Given User A's application for NPI 1003000111 was accepted (journey J1)
When User A calls GET /api/v1/applications/{applicationId}
Then 200 with status VALIDATED, no errors, source PORTAL, npi 1003000111, medicaidId M00000111, providerType IN, taxonomy 207Q00000X, the individual details and submittedAt
Basis: modern/docs/ux/journeys.md#J1, modern/contracts/openapi/enrollment-service.yaml#getApplication

###### AC-4 A rejected application shows exactly one error per failed check
Given User A's application was rejected for an empty taxonomy (journey J2), and a second one was rejected for providerType XX with an empty taxonomy
When User A calls GET /api/v1/applications/{applicationId} for each
Then The first has status REJECTED and exactly one error with code E104, field taxonomy and a message; the second has status REJECTED and exactly two errors, E102 on providerType and E104 on taxonomy, each listed once
Basis: modern/docs/ux/journeys.md#J2, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, D-001, D-016

###### AC-5 An analyst sees every application and can filter
Given Applications of several providers with statuses VALIDATED and REJECTED, some loaded from a legacy file and some submitted in the portal
When An ANALYST, and then a SUPERVISOR, calls GET /api/v1/applications?status=REJECTED and GET /api/v1/applications?npi=1003000111
Then The first returns every REJECTED application of every provider (source PORTAL and LEGACY_FILE); the second returns only the applications for NPI 1003000111; each response has items, page, size and total, defaulting to page 0 and size 20
Basis: modern/docs/ux/screen-inventory.md (console work-queues: rejected applications), modern/contracts/openapi/enrollment-service.yaml#listApplications, D-023

###### AC-6 Paging and filter values are checked
Given An ANALYST is signed in
When GET /api/v1/applications is called with size=201, with status=FOO, and with npi=123
Then Each call returns 400 problem+json; a call with size=200 returns 200
Basis: modern/contracts/openapi/enrollment-service.yaml#listApplications

###### AC-7 An unknown application id is not found
Given No application has the id 00000000-0000-0000-0000-000000000000
When An ANALYST calls GET /api/v1/applications/00000000-0000-0000-0000-000000000000
Then 404 problem+json
Basis: modern/contracts/openapi/enrollment-service.yaml#getApplication

###### AC-8 Only PROVIDER, ANALYST and SUPERVISOR may read applications
Given Tokens for PROVIDER, ANALYST, SUPERVISOR, ADMIN and SYSTEM, and a request without a token
When GET /api/v1/applications and GET /api/v1/applications/{applicationId} are called
Then PROVIDER (own applications only), ANALYST and SUPERVISOR get 200; ADMIN and SYSTEM get 403 problem+json; a call without a token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml

##### Out of scope
- The portal status page and the console work-queue screens (ui-portal-status, ui-console-queues)
- The provider's enrolment status after registration or approval (prv-search-read)
- Notices about rejected applications (ntf-list)
- Changing or withdrawing an application (no such function exists in the legacy estate or the contract)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00046
Programs: No legacy program reads applications back: this story is new. Related: PRVENR01 WRITE-ERROR-RECORD 173-179 (the APPERROR rows that were reviewed; contract x-legacy 'APPERROR review')
Decisions: D-001 FIX: a rejected application and every one of its errors stay readable, once each; D-016 FIX: errors are shown with their E101-E104 codes; D-023 FIX: role-based access; a PROVIDER sees only their own applications

##### Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: GET /api/v1/applications, GET /api/v1/applications/{applicationId}. Roles: PROVIDER, ANALYST, SUPERVISOR.


#### MAM-56 Run the legacy APPINPUT intake as a batch job with one error row per rejected application

Type: Story. Service: enrollment-service. Labels: svc-enrollment-service, kag-MAM-56, agentic, decision-D-001, decision-D-007, decision-D-016, decision-D-017, decision-D-020, decision-D-023, decision-D-025. Depends on: MAM-46, MAM-42.

##### Business requirement
Some applications still arrive in the old fixed-width daily application file. An administrator or a scheduler can load such a file for a stated business date and batch identifier. Each application in the file is checked exactly like an online one, a bad application never stops the rest of the file, and every rejected application is listed once in an error file in the old layout, so that the run can later be compared with what the legacy job produced.

##### Acceptance criteria
###### AC-1 An intake run starts with an explicit business date and batch id
Given An ADMIN token and an APPINPUT file of three valid 200-byte records
When POST /api/v1/batch/intake-runs is called with the file, businessDate 2026-03-31 and batchId B0331001
Then 202 with a BatchRun that has a runId, a job, businessDate 2026-03-31 and status RUNNING or COMPLETED; GET /api/v1/batch/intake-runs/{runId} returns the same businessDate; the service's JSON log contains the line 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH B0331001'
Basis: R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00010, R-PRVENR01-cbl-00015, R-PRVENR01-cbl-00017, R-PRVENR01-cbl-00018, D-017, D-020, Q-B8, modern/contracts/openapi/enrollment-service.yaml#startIntakeRun

###### AC-2 A missing batch id defaults to DEFBATCH
Given An ADMIN token and the same APPINPUT file
When POST /api/v1/batch/intake-runs is called with businessDate 2026-03-31 and no batchId (or a batchId of blanks)
Then 202, and the service's JSON log contains the line 'PRVENR01: STARTING ENROLLMENT LOAD - BATCH DEFBATCH'
Basis: R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00016, D-017, Q-B8

###### AC-3 Each record is read as the 130-byte PRVRECRD in bytes 1-130
Given An APPINPUT file with two 200-byte records. Record 1: NPI 1003000111 (bytes 1-10), Medicaid ID M00000111 (11-19), type IN (20-21), taxonomy 207Q00000X (22-31), risk tier H, status A, screen flag P and two dates in bytes 32-50, last name RIVERA (51-80), first name MARIA (81-100), licence number MD12345 (101-115), licence state TX (116-117) and non-blank text in bytes 131-200. Record 2: NPI 1003000444, type GR, taxonomy 207Q00000X, legal name EXCLUDED CLINIC LLC (51-90), DBA name blank (91-120) and owner NPI 1990000111 (121-130)
When The run is started and completes
Then GET /api/v1/applications?npi=1003000111 as ANALYST shows one VALIDATED application with source LEGACY_FILE and exactly those individual values; the application for 1003000444 shows legalName EXCLUDED CLINIC LLC and ownerNpi 1990000111 (so bytes 121-130 are read); neither the applications nor their ApplicationValidated events carry the text of bytes 131-200 or the risk tier, status, screen flag or dates of bytes 32-50
Basis: R-PRVENR01-ADD-01, R-PRVENR01-cbl-00012, R-PRVENR01-cbl-00019, R-PRVENR01-cbl-00007, D-025, D-007, Q-009, modern/docs/analysis/data-layouts.md (PRVRECRD)

###### AC-4 Each record gets the same validation as an online application
Given A file of four records, each otherwise valid: record 1 is a valid application for NPI 1003000111, record 2 has a blank NPI, record 3 is for NPI 1003000222 with type XX, record 4 is for NPI 1003000333 with an empty taxonomy
When The run completes
Then GET /api/v1/applications as ANALYST shows record 1 VALIDATED with one ApplicationValidated event, and records 2, 3 and 4 REJECTED with source LEGACY_FILE and exactly one error each (E101 on npi, E102 on providerType, E104 on taxonomy); ApplicationRejected events are published for 1003000222 and 1003000333 only (record 2 has no NPI to use as the event subject)
Basis: R-PRVENR01-cbl-00002, R-PRVENR01-cbl-00022, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00024, D-016, D-001, Q-B6

###### AC-5 One rejected application does not stop the load
Given A file of three records: a valid application for NPI 1003000111, an application for 1003000222 with an empty taxonomy, and a valid application for 1003000333
When The run completes
Then All three records are processed in file order: 1003000111 and 1003000333 are VALIDATED, 1003000222 is REJECTED with one error E104, and the run's counts.read is 3; the rejected application produces no ApplicationValidated event
Basis: R-PRVENR01-cbl-00038, R-PRVENR01-cbl-00048, R-PRVENR01-cbl-00020, R-PRVENR01-cbl-00002, D-001

###### AC-6 The APPERROR file has exactly one row per rejected application
Given A file with a valid record and three rejected ones: a blank NPI with type IN, NPI 1003000222 with type XX and an empty taxonomy (two failed checks), and NPI 1003000333 with type IN and an empty taxonomy
When The run completes and an ADMIN calls GET /api/v1/batch/intake-runs/{runId}/error-file
Then 200 with exactly three rows, in input order, one per rejected application and none for the valid one; each row is 250 characters: bytes 1-10 the NPI as read (blanks for a blank NPI), bytes 11-16 ' TYPE:', bytes 17-18 the type code as read, bytes 19-40 ' ERR:VALIDATION-FAILED' and bytes 41-250 spaces (for example the second row starts '1003000222 TYPE:XX ERR:VALIDATION-FAILED'); for a run with no rejected application the call returns 200 with an empty file
Basis: R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00014, R-PRVENR01-cbl-00027, R-PRVENR01-cbl-00033, R-PRVENR01-cbl-00037, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045, R-PRVENR01-cbl-00046, R-PRVENR01-cbl-00047, R-PRVENR01-ADD-06, D-001, D-016, Q-B4, ADR-005

###### AC-7 Only ADMIN and SYSTEM may start a run, only ADMIN may download the error file, and the run parameters are required
Given Tokens for ADMIN, SYSTEM, PROVIDER, ANALYST and SUPERVISOR, a request without a token, and the runId 00000000-0000-0000-0000-000000000000 that no run has
When POST /api/v1/batch/intake-runs is called with a file and businessDate, and GET /api/v1/batch/intake-runs/{runId}/error-file is called
Then POST returns 202 for ADMIN and SYSTEM, 403 problem+json for the others and 401 without a token; the error file returns 200 for ADMIN, 403 for every other role and 401 without a token, and 404 problem+json for the unknown runId; a POST without businessDate or without a file returns 400 problem+json and no run is created
Basis: D-023, D-020, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml#startIntakeRun, modern/contracts/openapi/enrollment-service.yaml#getIntakeErrorFile

##### Out of scope
- Counts, completion status, failure handling and re-runs of the run (enr-intake-run-2)
- The legacy SORT step of PRVDAY01 (D-009, DROP) and the screening and risk steps that follow STEP010 (scr-batch-run, rsk-assign-tier)
- A cron-scheduled start of the intake: the file arrives through the endpoint (Q-B9)
- The console batch-jobs screen (ui-console-batch)
- The fixed-width codec library itself (dat-codec) and the byte-level comparison with legacy output (par-harness)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-01, R-PRVENR01-ADD-06, R-PRVENR01-cbl-00001, R-PRVENR01-cbl-00002, R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00010, R-PRVENR01-cbl-00012, R-PRVENR01-cbl-00014, R-PRVENR01-cbl-00015, R-PRVENR01-cbl-00016, R-PRVENR01-cbl-00017, R-PRVENR01-cbl-00018, R-PRVENR01-cbl-00019, R-PRVENR01-cbl-00020, R-PRVENR01-cbl-00022, R-PRVENR01-cbl-00027, R-PRVENR01-cbl-00033, R-PRVENR01-cbl-00037, R-PRVENR01-cbl-00038, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045, R-PRVENR01-cbl-00046, R-PRVENR01-cbl-00047, R-PRVENR01-cbl-00048
Related rules: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00007, R-PRVENR01-cbl-00024
Programs: PRVENR01 INITIALIZE-PROGRAM 85-100, PROCESS-APPLICATIONS 102-116, VALIDATE-APPLICATION 128-145, INVALID-APP 155-159, WRITE-ERROR-RECORD 173-179, data division 55-103 (APP-INPUT-RECORD 200 bytes read INTO the 130-byte PRVRECRD, APP-ERROR-RECORD 250 bytes); job PRVDAY01 STEP010 (APPINPUT, APPERROR, PARM BATCH_ID at JCL/PRVDAY01.jcl:20)
Decisions: D-001 FIX: every record is processed; each rejected application gets exactly one APPERROR row and is never registered; D-007 FIX: the provider record is 130 bytes, so the owner NPI in bytes 121-130 is read; D-016 FIX: validation results use E101-E104; APPERROR bytes 41-250 are defined (spaces); D-017 FIX: the batch id is a real job parameter (default DEFBATCH); D-020 FIX: the business date is an explicit job parameter; D-023 FIX: role check on every run endpoint (POST ADMIN, SYSTEM; error file ADMIN); D-025 PRESERVE: intake = PRVRECRD layout in bytes 1-130, bytes 131-200 reserved and ignored (Q-009)

##### Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: POST /api/v1/batch/intake-runs, GET /api/v1/batch/intake-runs/{runId}/error-file, ApplicationValidated, ApplicationRejected. Roles: ADMIN, SYSTEM.


#### MAM-57 Report the result of an intake run: exclusive counts, completion, failures and safe re-runs

Type: Story. Service: enrollment-service. Labels: svc-enrollment-service, kag-MAM-57, agentic, decision-D-001, decision-D-016, decision-D-023, decision-D-024. Depends on: MAM-56.

##### Business requirement
After an intake run, operations need an honest result: how many applications were read, accepted, rejected and skipped, and whether the run finished cleanly, finished with rejected applications, or failed and why. The old job counted one application several times, hid a hard error behind a warning code and could loop forever on a bad record. The new run reports counts that add up and an explicit result, and a run can be repeated for the same business date without loading anything twice.

##### Acceptance criteria
###### AC-1 A clean run reports its counts, and each run starts from zero
Given A first run for business date 2026-03-31 of a file with three valid applications (NPIs 1003000111, 1003000222, 1003000333), followed by a run for business date 2026-04-01 of a file with one valid application for NPI 1003000777
When Both runs complete and an ADMIN calls GET /api/v1/batch/intake-runs/{runId} for each
Then The first shows status COMPLETED, endedAt set, error null and counts read 3, written 3, errored 0, skipped 0; the second shows read 1, written 1, errored 0, skipped 0, so nothing is carried over from the first run
Basis: R-PRVENR01-cbl-00009, R-PRVENR01-cbl-00011, R-PRVENR01-cbl-00021, R-PRVENR01-cbl-00057, R-PRVENR01-cbl-00061, R-PRVENR01-cbl-00062, R-PRVENR01-cbl-00067, D-024, modern/contracts/openapi/enrollment-service.yaml#getIntakeRun

###### AC-2 Every record is counted exactly once
Given A file of four records: valid applications for NPIs 1003000111 and 1003000777, one with a blank NPI, and one for NPI 1003000222 with type XX and an empty taxonomy (two failed checks)
When The run completes and an ADMIN reads the run and downloads its error file
Then counts are read 4, written 2, errored 2, skipped 0, so that read equals written plus errored plus skipped; the record with two failed checks is counted once, not twice; the downloaded error file is complete and holds exactly 2 rows
Basis: R-PRVENR01-cbl-00026, R-PRVENR01-cbl-00031, R-PRVENR01-cbl-00036, R-PRVENR01-cbl-00060, R-PRVENR01-cbl-00063, R-PRVENR01-ADD-08, D-024, D-001

###### AC-3 A duplicate NPI is counted as skipped
Given A file holding NPI 1003000111 (no earlier application), the same NPI 1003000111 again, and NPI 1003000222
When The run completes
Then The first 1003000111 record and 1003000222 are VALIDATED; the second 1003000111 record is REJECTED with exactly one error E103 and has one APPERROR row; counts are read 3, written 2, errored 0, skipped 1; the first application is unchanged and exactly one ApplicationValidated event exists for 1003000111
Basis: R-PRVENR01-ADD-04, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00013, R-PRVENR01-cbl-00064, D-016, Q-B3

###### AC-4 A run with rejected applications completes, not as a failure
Given A file with two valid applications and one rejected application
When The run completes
Then GET /api/v1/batch/intake-runs/{runId} shows status COMPLETED (not FAILED), error null and counts.errored 1, the modern form of the legacy return code 4 'completed with warnings'; the two valid applications keep their VALIDATED status
Basis: R-PRVENR01-cbl-00065, R-PRVENR01-cbl-00066, R-PRVENR01-cbl-00068, D-024, Q-B8

###### AC-5 An unreadable record ends the run with a stated failure
Given A file of four records whose third record is only 150 bytes long (the file is cut short)
When The run is started
Then The run does not stay RUNNING and never loops: GET /api/v1/batch/intake-runs/{runId} shows status FAILED, an error text naming record 3, endedAt set; records 1 and 2 keep their results and record 4 is not processed; counts are read 3, written 2, errored 1, skipped 0
Basis: R-PRVENR01-cbl-00023, R-PRVENR01-ADD-03, R-PRVENR01-ADD-08, D-024, Q-B8, Q-B14

###### AC-6 A hard failure is never shown as a warning
Given A file of three records: an application for NPI 1003000222 with an empty taxonomy, then valid applications for NPIs 1003000111 and 1003000333, and an unexpected failure injected while the application for 1003000111 is being stored
When The run is started
Then The run's status is FAILED (not COMPLETED, although rejected applications alone would give COMPLETED) with an error text naming record 2 and NPI 1003000111; counts are read 2, written 0, errored 2, skipped 0; the REJECTED application for 1003000222 and its APPERROR row remain; no ApplicationValidated event is published for 1003000111 and record 3 is not processed
Basis: R-PRVENR01-cbl-00059, R-PRVENR01-ADD-07, D-024, Q-B8, Q-B14

###### AC-7 Run results are for ADMIN only and unknown runs are not found
Given Tokens for ADMIN, SYSTEM, PROVIDER, ANALYST and SUPERVISOR, and a request without a token
When GET /api/v1/batch/intake-runs/{runId} is called, and again with the unknown runId 00000000-0000-0000-0000-000000000000
Then ADMIN gets 200; SYSTEM, PROVIDER, ANALYST and SUPERVISOR get 403 problem+json; a call without a token gets 401; the unknown runId returns 404 problem+json
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/enrollment-service.yaml#getIntakeRun

###### AC-8 Re-running a business date loads nothing twice
Given A run for business date 2026-03-31 of a file of four records (valid 1003000111, valid 1003000333, 1003000222 with an empty taxonomy, valid 1003000777) ended FAILED because of a storage failure injected at record 4, after records 1 to 3 were processed
When An ADMIN starts a new run for business date 2026-03-31 with the same file, and then a third run for the same date and file
Then The second run shows status COMPLETED with counts read 4, written 1, errored 0, skipped 3: only 1003000777 is newly VALIDATED with one ApplicationValidated event, the three records already processed on 2026-03-31 get no new application, no APPERROR row and no event, and its error file is empty; the third run shows read 4, written 0, errored 0, skipped 4 and publishes no event
Basis: modern/docs/architecture/nfr.md (Availability: batch jobs can be re-run for a business date and are idempotent), ADR-004, D-024, Q-B11, Q-B14, modern/docs/architecture/data-model/enrollment-service.sql (application.raw_record, intake_run_id)

##### Out of scope
- Starting the run, reading the file and writing the APPERROR rows (enr-intake-run)
- Gating the later daily steps on the intake return code: the legacy skipped screening and risk when STEP010 ended with RC 4; here screening starts per provider from ProviderRegistered, so a run with rejections does not block it (overview.md main flows 1 and 2)
- Showing run failures in the console (ui-console-batch)
- A second load of the same file under a different business date: its valid applications are then duplicates (E103, skipped), as in AC-3

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVENR01-ADD-03, R-PRVENR01-ADD-07, R-PRVENR01-ADD-08, R-PRVENR01-cbl-00008, R-PRVENR01-cbl-00009, R-PRVENR01-cbl-00011, R-PRVENR01-cbl-00013, R-PRVENR01-cbl-00021, R-PRVENR01-cbl-00023, R-PRVENR01-cbl-00026, R-PRVENR01-cbl-00031, R-PRVENR01-cbl-00036, R-PRVENR01-cbl-00057, R-PRVENR01-cbl-00059, R-PRVENR01-cbl-00060, R-PRVENR01-cbl-00061, R-PRVENR01-cbl-00062, R-PRVENR01-cbl-00063, R-PRVENR01-cbl-00064, R-PRVENR01-cbl-00065, R-PRVENR01-cbl-00066, R-PRVENR01-cbl-00067, R-PRVENR01-cbl-00068
Related rules: R-PRVENR01-ADD-04, R-PRVENR01-cbl-00051, R-PRVENR01-cbl-00058
Programs: PRVENR01 PROCESS-APPLICATIONS 102-116 (read error 111-114), WRITE-PROVIDER-RECORD 157-171 (status 00 written, 22 duplicate skipped, other error RC 8), TERMINATE-PROGRAM 181-189 (four counts, RC 4 when errored > 0); job PRVDAY01 STEP010 and the gate IF (STEP010.RC = 0) at JCL/PRVDAY01.jcl:38
Decisions: D-001 FIX: a rejected application is counted once and the run goes on; D-016 FIX: a duplicate NPI is the validation result E103; D-023 FIX: role check on the run result endpoint (ADMIN); D-024 FIX: exclusive counts, an explicit result per run (COMPLETED or FAILED with a reason), no endless loop, a hard error is never downgraded to a warning, a re-run for a business date is safe

##### Technical notes (agent-owned)
Service: enrollment-service. Endpoints/events: GET /api/v1/batch/intake-runs/{runId}, ApplicationValidated, ApplicationRejected. Roles: ADMIN.


### MAM-5 screening-service

#### MAM-18 Screen a newly registered provider against the licence board, OIG LEIE, SAM and SSA DMF (mocked sources)

Type: Story. Service: screening-service. Labels: svc-screening-service, kag-MAM-18, agentic, decision-D-005, decision-D-016, decision-D-010, decision-D-014, slice-main-journey. Depends on: MAM-17, MAM-37, MAM-47.

##### Business requirement
Before anyone approves an enrolment, the state must check that the provider holds a valid licence, is not on the federal list of excluded providers, is not barred from federal programmes and is not recorded as deceased. Today these four checks do nothing and every provider passes. After this story each check asks its source and records what the source answered, so a provider with an expired licence, an exclusion, a debarment or a death record is caught. The sources are simulated for now; the real services will be connected in a later release.

##### Acceptance criteria
###### AC-1 Screening starts when a provider is registered
Given provider-service has published ProviderRegistered for NPI 1003000111 (type IN, taxonomy 207Q00000X, enrollStatus P, screenFlag U) and provider-service GET /api/v1/providers/1003000111 returns licence MD12345 in TX
When screening-service receives the event
Then GET /api/v1/screenings?npi=1003000111 (ANALYST token) returns one screening with reason INITIAL that holds one result for each of LICBOARD, OIGLEIE, SAMGOV and SSADMF, and the licence-board mock's request log shows the lookup for licence MD12345 in TX (the values read from provider-service)
Basis: R-PRVSCR01-cbl-00020, R-PRVSCR01-cbl-00021, R-PRVSCR01-cbl-00032, D-005, D-014, ADR-002, QUESTION C-2, QUESTION C-4, modern/contracts/asyncapi/events.yaml#ProviderRegistered, modern/docs/architecture/overview.md#Main flows (2), modern/docs/ux/journeys.md#J1

###### AC-2 A clean provider passes all four sources
Given NPI 1003000111 with licence MD12345 in TX, which the licence-board mock reports ACTIVE, and the LEIE, SAM and SSA DMF mocks return their clean answer for it
When the screening completes
Then the LICBOARD, OIGLEIE, SAMGOV and SSADMF results are all P, each with a verdictAt time and a non-blank detail, and the request log of each of the four mocks shows exactly one lookup for this provider (the checks really ask the sources)
Basis: R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00029, R-PRVSCR01-cbl-00034, R-PRVSCR01-cbl-00039, R-PRVSCR01-cbl-00043, D-005, aws-transform/test-data/license_board.txt, modern/docs/ux/journeys.md#J1

###### AC-3 An expired licence fails the licence source (E202)
Given an individual (IN) provider 1003000555 with licence PT99900 in TX, which the licence-board mock reports as EXPIRED
When the screening completes
Then the LICBOARD result is F with errorCode E202 and a detail stating that the licence is expired
Basis: R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00029, D-016, D-005, aws-transform/test-data/license_board.txt, .claude/skills/pem-domain/SKILL.md#4 (E202 licence expired)

###### AC-4 A provider on the OIG LEIE fails (E201)
Given an individual (IN) provider with NPI 1990000111 is registered, and the LEIE mock lists 1990000111 (as in the sample LEIE feed)
When the screening completes
Then the OIGLEIE result is F with errorCode E201 and a detail stating that the provider is excluded
Basis: R-PRVSCR01-cbl-00007, R-PRVSCR01-cbl-00034, D-016, D-005, aws-transform/test-data/leie.txt, .claude/skills/pem-domain/SKILL.md#4 (E201 excluded)

###### AC-5 A debarred provider fails SAM (E205)
Given provider 1003000111, for whose name the SAM mock's hit stub returns status DEBARRED
When the screening completes
Then the SAMGOV result is F with errorCode E205 and a detail stating that the provider is debarred
Basis: R-PRVSCR01-cbl-00008, R-PRVSCR01-cbl-00038, R-PRVSCR01-cbl-00039, D-016, D-005, QUESTION C-2, aws-transform/test-data/sam.txt (DEBARRED in column 2 triggers a fail), .claude/skills/pem-domain/SKILL.md#4 (E205 debarred)

###### AC-6 A deceased provider fails SSA DMF (E204)
Given provider 1003000111, which the SSA DMF mock's hit stub reports as deceased
When the screening completes
Then the SSADMF result is F with errorCode E204 and a detail stating that the provider is deceased
Basis: R-PRVSCR01-cbl-00009, R-PRVSCR01-cbl-00043, D-016, D-005, .claude/skills/pem-domain/SKILL.md#4 (E204 deceased), .claude/skills/synthetic-data/SKILL.md#5 (hit stub)

###### AC-7 The same registration never creates two screenings
Given ProviderRegistered for NPI 1003000111 is delivered twice with the same event id
When both deliveries are processed
Then GET /api/v1/screenings?npi=1003000111 returns exactly one screening and each mock's request log shows one lookup for the provider
Basis: ADR-003, R-PRVSCR01-cbl-00021, .claude/skills/java-microservices/SKILL.md#7 (idempotent consumers)

##### Out of scope
- Medicare reciprocity / PECOS check (dropped, D-010): PECOS is not a screening source
- An NPPES lookup (not one of the four legacy checks; question C-4)
- Overall verdict, per-source storage and the ScreeningCompleted event (scr-verdict)
- Owner screening (scr-owner); unknown licences, organisation providers and sources that fail or are slow (scr-screen-sources-2)
- Setting the provider's screen flag (provider-service, prv-screen-verdict)
- The batch run and the SCRNOUT file (scr-batch-run)
- Connecting the real licence boards, LEIE, SAM, NPPES or SSA DMF services

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00006, R-PRVSCR01-cbl-00007, R-PRVSCR01-cbl-00008, R-PRVSCR01-cbl-00009, R-PRVSCR01-cbl-00020, R-PRVSCR01-cbl-00021, R-PRVSCR01-cbl-00029, R-PRVSCR01-cbl-00032, R-PRVSCR01-cbl-00034, R-PRVSCR01-cbl-00038, R-PRVSCR01-cbl-00039, R-PRVSCR01-cbl-00043
Programs: source-code/BATCH/PRVSCR01.cbl: SCREEN-NEXT-PROVIDER 90-91, SCREEN-ONE-PROVIDER 101-104, CHECK-LICENSE 116-119, CHECK-LEIE-EXCLUSION 121-124, CHECK-SAM-DEBARMENT 126-128, CHECK-SSA-DMF 130-132 (all four hard-code P at 118, 123, 127, 131). Job step: source-code/JCL/PRVDAY01.jcl STEP030
Decisions: D-005 REPLACE: the four stubs become real lookups against mocked sources; D-016 FIX: E201, E202, E204 and E205 are the error codes of the failing source; D-010 DROP: the Medicare reciprocity check is not built; D-014 FIX: provider-service is the single owner of provider data; screening-service reads licence and name through provider-service's API (ADR-002, question C-2)

##### Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, ProviderRegistered. Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM.


#### MAM-20 Handle unknown licences, organisation providers and sources that fail or are slow

Type: Story. Service: screening-service. Labels: svc-screening-service, kag-MAM-20, agentic, decision-D-024, decision-D-016, decision-D-005. Depends on: MAM-18, MAM-25.

##### Business requirement
Real sources are sometimes down, slow or have no record of a licence, and some providers (groups and facilities) hold no individual licence at all. Without a clear rule a provider could be passed by mistake, or a screening could hang for ever. This story defines what is recorded in these cases so that nobody is approved on an answer that was never received, so that organisations can still be enrolled, and so that an analyst can see why a screening failed and run it again.

##### Acceptance criteria
###### AC-1 A source that returns an error is recorded as not answered
Given provider 1003000111, for which the LEIE mock's error stub (HTTP 500) applies
When the screening runs
Then the LEIE mock's request log shows the lookup and one retry, the OIGLEIE result is C with no errorCode and a detail stating that the source could not be reached, the other three sources are still recorded, the screening result is FAIL and ScreeningCompleted is published with result FAIL
Basis: QUESTION C-1, R-PRVSCR01-cbl-00010, R-PRVSCR01-ADD-05, D-024, D-005, .claude/skills/synthetic-data/SKILL.md#5 (500-error stub for the retry path), modern/docs/analysis/data-layouts.md#SCRNREC (SCR-COND = C)

###### AC-2 A slow source does not hold the screening
Given provider 1003000111, for which the SAM mock's slow stub answers after the 2-second limit
When the screening runs
Then the SAMGOV result is C with no errorCode and a detail stating that the source did not answer in time, and the screening is COMPLETED within 5 seconds of the event
Basis: QUESTION C-1, R-PRVSCR01-cbl-00010, modern/docs/architecture/nfr.md#Performance (screening of one provider under 5 s), .claude/skills/synthetic-data/SKILL.md#5 (slow stub)

###### AC-3 A licence the board does not know fails without an error code
Given an individual (IN) provider 1003000666 with licence XX00000 in TX, which the licence-board mock does not know
When the screening completes
Then the LICBOARD result is F with no errorCode and a detail stating that the licence was not found, and the screening result is FAIL
Basis: QUESTION C-3, D-016, R-PRVSCR01-cbl-00010, aws-transform/test-data/providers.txt, aws-transform/test-data/license_board.txt (XX00000 not listed)

###### AC-4 Groups and facilities are not checked against a licence
Given a facility (FA) provider 1003000333 whose provider record holds no licence number and no owner NPI
When the screening runs
Then the LICBOARD result is P with a detail stating that the licence check is not applicable, the licence-board mock's request log shows no lookup for it, and the OIGLEIE, SAMGOV and SSADMF sources are looked up as usual
Basis: QUESTION C-3, D-008, R-PRVSCR01-cbl-00032, modern/docs/analysis/data-layouts.md#PRVRECRD (the organisation view has no licence fields), modern/contracts/openapi/provider-service.yaml (licenseNo only under the individual data)

###### AC-5 A provider that cannot be read is not screened on made-up data
Given ProviderRegistered for NPI 1003000777, and provider-service GET /api/v1/providers/1003000777 answers 5xx (or does not answer) until it is restored
When the event is processed while provider-service is failing, and provider-service is then restored
Then while provider-service fails, no screening of 1003000777 is COMPLETED and no ScreeningCompleted is published for it; after it is restored, the event is processed again and exactly one COMPLETED screening and one ScreeningCompleted exist for 1003000777
Basis: D-024, R-PRVSCR01-ADD-05, ADR-003 (at-least-once delivery, idempotent consumers), QUESTION C-2

##### Out of scope
- Starting a new screening after a source has recovered (scr-rescreen)
- The analyst screen that shows why a screening failed (ui-console-detail, ui-console-queues)
- Changing a provider's licence data
- Any retry policy other than the default applied through question C-1 (one retry, 2-second limit, configurable)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSCR01-ADD-05, R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00032
Programs: No direct legacy equivalent: PRVSCR01.cbl never handled a failing source because all four checks were stubs returning P (118-131) and no status was tested (R-PRVSCR01-ADD-05, lines 114-149)
Decisions: D-024 FIX: explicit error handling, failures are surfaced and never silent; D-016 FIX: error codes only where the legacy defines one; no code is invented for 'licence not found' or 'source unavailable'; D-005 REPLACE: real screening against mocked sources

##### Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, ProviderRegistered, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM.


#### MAM-22 Screen the owner of a group or facility (E203 owner excluded)

Type: Story. Service: screening-service. Labels: svc-screening-service, kag-MAM-22, agentic, decision-D-016, decision-D-005. Depends on: MAM-18, MAM-25.

##### Business requirement
A group or facility is only as trustworthy as the person who owns it. When the provider is an organisation, the state must also check its owner against the federal list of excluded providers, and a provider whose owner is excluded must not pass screening. This story adds the owner check to the exclusion source, with its own error so that the analyst can see that the owner, not the provider itself, is the problem.

##### Acceptance criteria
###### AC-1 An excluded owner fails the group (E203)
Given a group (GR) provider 1003000444 with owner NPI 1990000111, and the LEIE mock lists 1990000111 but not 1003000444
When the screening completes
Then the OIGLEIE result is F with errorCode E203 and a detail naming owner NPI 1990000111, the other three sources are still recorded, the screening result is FAIL, and GET /api/v1/screenings?result=FAIL lists it
Basis: R-PRVSCR01-cbl-00033, D-016, D-005, QUESTION C-5, modern/docs/ux/journeys.md#J3, aws-transform/test-data/leie.txt, aws-transform/test-data/providers.txt

###### AC-2 A clean owner passes
Given a group (GR) provider 1003000222 with owner NPI 1990000999 (as in the sample feed), neither of which is on the LEIE mock
When the screening completes
Then the OIGLEIE result is P and the LEIE mock's request log shows two lookups, one for 1003000222 and one for 1990000999
Basis: R-PRVSCR01-cbl-00033, R-PRVSCR01-cbl-00034, QUESTION C-5, aws-transform/test-data/providers.txt

###### AC-3 Providers without an owner NPI are checked on their own NPI only
Given the individual (IN) provider 1003000111, and the facility (FA) provider 1003000333 whose owner NPI is blank
When each is screened
Then for each, the LEIE mock's request log shows exactly one lookup (the provider's own NPI) and the OIGLEIE result carries no E203
Basis: R-PRVSCR01-cbl-00033, modern/docs/analysis/data-layouts.md#PRVRECRD (owner NPI exists only in the organisation view), modern/contracts/openapi/provider-service.yaml (ownerNpi only under the organisation data)

###### AC-4 A provider and its owner both on the LEIE report E201
Given a group (GR) provider 1003000444 whose own NPI and whose owner NPI 1990000111 are both listed by the LEIE mock
When the screening completes
Then the single OIGLEIE result is F with errorCode E201 and a detail that names both 1003000444 and owner 1990000111
Basis: QUESTION C-6, D-016, R-PRVSCR01-cbl-00033, modern/docs/architecture/data-model/screening-service.sql (one result per source: UNIQUE screening_id, source)

###### AC-5 An owner lookup that fails is recorded as not answered
Given a group (GR) provider 1003000222 with owner NPI 1990000999, and the LEIE mock's error stub (HTTP 500) applies to the lookup of 1990000999
When the screening runs
Then after one retry the OIGLEIE result is C with no errorCode and the screening result is FAIL
Basis: QUESTION C-1, R-PRVSCR01-cbl-00010, D-024

##### Out of scope
- Owners with 5 percent or more listed in provider-service's ownership table (not exposed by any API in 1.0.0; question C-5)
- Checking owners against SAM, the licence board or SSA DMF (the legacy comment and E203 'owner excluded' concern the LEIE only)
- An ownership percentage threshold (the legacy master holds one owner NPI and no percentage)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-cbl-00033
Related rules: R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00034
Programs: source-code/BATCH/PRVSCR01.cbl: CHECK-LEIE-EXCLUSION 121-124 (the comment at 122 says it matches NPI and 5%+ owners; the code only sets P). Owner NPI is PRV-OWNER-NPI, bytes 121-130 of source-code/COPYBOOKS/PRVRECRD.cpy
Decisions: D-016 FIX: E203 owner excluded is implemented as a screening result; D-005 REPLACE: the LEIE stub becomes a real lookup of the provider and its owner

##### Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, ADMIN, SYSTEM.


#### MAM-25 Decide the screening verdict, store the results per source and publish ScreeningCompleted

Type: Story. Service: screening-service. Labels: svc-screening-service, kag-MAM-25, agentic, decision-D-005, decision-D-018, decision-D-016, decision-D-023, slice-main-journey. Depends on: MAM-18.

##### Business requirement
After the four checks the system must decide one overall answer: the provider passes only if every check passed. It must also keep what each source said, with the date and a short explanation, because the programme has to be able to show later why a provider was passed or failed. Today the legacy job keeps only a bare pass for each check and no explanation. After this story the verdict is announced to the rest of the system, the analysts can list and open the screenings, and nothing that was recorded is overwritten.

##### Acceptance criteria
###### AC-1 All four sources pass: the screening passes
Given a screening of NPI 1003000111 whose four source results are all P
When the last result is recorded
Then GET /api/v1/screenings/{screeningId} returns status COMPLETED, result PASS and a completedAt time
Basis: R-PRVSCR01-cbl-00010, D-005, modern/docs/ux/journeys.md#J1

###### AC-2 One failing source fails the screening
Given a screening in which exactly one of the four sources is F (for example OIGLEIE for NPI 1003000444) and the other three are P
When the last result is recorded
Then the screening has status COMPLETED and result FAIL, and the same holds whichever one of the four sources is the F
Basis: R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00037, modern/contracts/asyncapi/events.yaml#ScreeningCompleted (any source FAIL means FAIL), modern/docs/ux/journeys.md#J3

###### AC-3 One stored result per source
Given a completed screening of NPI 1003000555 whose LICBOARD result is F (E202)
When GET /api/v1/screenings/{screeningId} is called
Then results holds exactly four entries, one each for LICBOARD, OIGLEIE, SAMGOV and SSADMF, each with result P, F or C, a verdictAt time and a non-blank detail, and an errorCode only where one applies (here E202 on LICBOARD; none on the P results)
Basis: R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00030, R-PRVSCR01-cbl-00031, R-PRVSCR01-cbl-00035, R-PRVSCR01-cbl-00036, R-PRVSCR01-cbl-00040, R-PRVSCR01-cbl-00041, R-PRVSCR01-cbl-00042, R-PRVSCR01-cbl-00044, R-PRVSCR01-cbl-00045, D-005, D-018, D-016, QUESTION C-3, modern/contracts/openapi/screening-service.yaml#SourceResult

###### AC-4 Results are kept and never overwritten
Given two screenings of NPI 1003000555 have completed at different times
When GET /api/v1/screenings?npi=1003000555 is called
Then both screenings are returned, each with its own four results, and the earlier one is unchanged
Basis: D-018, R-PRVSCR01-ADD-02, modern/docs/architecture/nfr.md#Audit (screening results are kept), modern/docs/architecture/data-model/screening-service.sql

###### AC-5 ScreeningCompleted is published for every completed screening
Given a screening of NPI 1003000111 with taxonomy 207Q00000X completes (PASS or FAIL)
When the result is committed
Then exactly one ScreeningCompleted is published to pem.screening.events with message key 1003000111 and data holding screeningId, npi, reason, result, taxonomy 207Q00000X and sources (source, result and detail of each of the four)
Basis: modern/contracts/asyncapi/events.yaml#ScreeningCompleted, ADR-003, D-005, R-PRVSCR01-cbl-00010, modern/docs/architecture/overview.md#Main flows (2)

###### AC-6 Failed screenings can be listed with the failing source
Given NPI 1003000444 failed on OIGLEIE with errorCode E203
When an ANALYST calls GET /api/v1/screenings?result=FAIL
Then the response (items, page, size, total) includes that screening with its per-source results, where OIGLEIE shows F and errorCode E203
Basis: modern/contracts/openapi/screening-service.yaml#listScreenings (result filter), modern/docs/ux/screen-inventory.md#console provider-detail (GET /api/v1/screenings), modern/docs/ux/journeys.md#J3, D-016

###### AC-7 One screening can be read, an unknown id is not found
Given a known screeningId and an unknown screeningId
When GET /api/v1/screenings/{screeningId} is called for each
Then the known id returns 200 with the screening and the unknown id returns 404 with a problem+json body
Basis: modern/contracts/openapi/screening-service.yaml#getScreening, .claude/skills/java-microservices/SKILL.md#6 (RFC 7807 errors)

###### AC-8 Only staff can read screenings
Given tokens for ANALYST, SUPERVISOR, ADMIN, PROVIDER and SYSTEM, and a request with no token
When each calls GET /api/v1/screenings and GET /api/v1/screenings/{screeningId}
Then ANALYST, SUPERVISOR and ADMIN receive 200, PROVIDER and SYSTEM receive 403, and the request with no token receives 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md#Security, modern/contracts/openapi/screening-service.yaml (x-roles)

##### Out of scope
- Setting the provider's screen flag (provider-service, prv-screen-verdict)
- Assigning the risk tier (rsk-assign-tier)
- Sending notices (notification-service)
- The legacy SCRNOUT file and the run's failure counter (scr-batch-run)
- Starting a screening on request or for revalidation (scr-rescreen)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00010, R-PRVSCR01-cbl-00030, R-PRVSCR01-cbl-00031, R-PRVSCR01-cbl-00035, R-PRVSCR01-cbl-00036, R-PRVSCR01-cbl-00037, R-PRVSCR01-cbl-00040, R-PRVSCR01-cbl-00041, R-PRVSCR01-cbl-00042, R-PRVSCR01-cbl-00044, R-PRVSCR01-cbl-00045
Programs: source-code/BATCH/PRVSCR01.cbl: SCREEN-ONE-PROVIDER 106-113 (verdict: all four P gives pass, otherwise fail), CHECK-* paragraphs 116-132, WRITE-SCREEN-ROW 146-149 (sets only the NPI and the pass flag). Job step: source-code/JCL/PRVDAY01.jcl STEP030
Decisions: D-005 REPLACE: results carry source, verdict time and detail; D-018 FIX: screening results are a real table that keeps every screening; D-016 FIX: the failing source carries its E2xx code where one applies; D-023 FIX: role-based access on every endpoint

##### Technical notes (agent-owned)
Service: screening-service. Endpoints/events: GET /api/v1/screenings, GET /api/v1/screenings/{screeningId}, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, ADMIN.


#### MAM-28 Rescreen a provider on request or when revalidation is due

Type: Story. Service: screening-service. Labels: svc-screening-service, kag-MAM-28, agentic, decision-D-005, decision-D-029, decision-D-018, decision-D-023. Depends on: MAM-25.

##### Business requirement
A provider's situation changes over time: a licence lapses, a name appears on an exclusion list, a source that was down comes back, and every enrolled provider must be revalidated about every five years. The state therefore needs to be able to screen a provider again without waiting for a new application. An analyst can ask for a fresh screening, and the monthly revalidation process triggers one automatically. Earlier results are kept so that the history is complete.

##### Acceptance criteria
###### AC-1 An analyst starts a screening on request
Given provider 1003000555 exists in provider-service and has one earlier screening, and an ANALYST token
When the ANALYST calls POST /api/v1/screenings with npi 1003000555
Then the response is 202 with a Screening of reason MANUAL and status RUNNING, and when it completes GET /api/v1/screenings/{screeningId} shows four fresh results while GET /api/v1/screenings?npi=1003000555 still returns the earlier screening unchanged
Basis: modern/contracts/openapi/screening-service.yaml#requestScreening, D-005, D-018

###### AC-2 A malformed or unknown NPI is refused
Given an ANALYST token
When POST /api/v1/screenings is called with npi 123, and then with npi 1003000999, which provider-service does not know
Then the first call returns 400 and the second returns 404, both with a problem+json body, and no screening is created
Basis: modern/contracts/openapi/screening-service.yaml#requestScreening (npi pattern, 400, 404), D-024, .claude/skills/java-microservices/SKILL.md#6 (RFC 7807 errors)

###### AC-3 Revalidation due starts a re-screening
Given revalidation-service has published RevalidationDue for a case of NPI 1003000222 with dueDate 2026-02-28
When screening-service receives the event
Then a screening with reason REVALIDATION is started for 1003000222 and, when it completes, a ScreeningCompleted with reason REVALIDATION is published for it
Basis: modern/contracts/asyncapi/events.yaml#RevalidationDue, modern/contracts/asyncapi/events.yaml#ScreeningCompleted, modern/docs/architecture/overview.md#Main flows (4), D-029

###### AC-4 One re-screening per revalidation case
Given the RevalidationDue of criterion 3 is delivered twice with the same event id
When both deliveries are processed
Then exactly one REVALIDATION screening exists for 1003000222 and exactly one ScreeningCompleted is published for it
Basis: ADR-003, D-029

###### AC-5 A re-screening uses the provider's current data
Given provider 1003000111 passed its first screening with licence MD12345, and provider-service GET /api/v1/providers/1003000111 now returns licence PT99900 in TX (EXPIRED on the licence-board mock)
When a MANUAL screening of 1003000111 runs
Then the licence-board mock's request log shows a lookup for PT99900, not for MD12345, and the new screening's LICBOARD result is F with errorCode E202
Basis: QUESTION C-2, D-014, ADR-002, D-016, aws-transform/test-data/license_board.txt

###### AC-6 Only analysts, supervisors and the system can start a screening
Given tokens for ANALYST, SUPERVISOR, SYSTEM, PROVIDER and ADMIN, and a request with no token
When each calls POST /api/v1/screenings with npi 1003000111
Then ANALYST, SUPERVISOR and SYSTEM are accepted with 202, PROVIDER and ADMIN receive 403 and the request with no token receives 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md#Security, modern/contracts/openapi/screening-service.yaml#requestScreening (x-roles)

##### Out of scope
- Closing the revalidation case (rev-cases)
- The unscreened-providers batch run (scr-batch-run)
- What provider-service does with a failed manual re-screening of an ACTIVE provider (no automatic status change is defined; see question C-12 for revalidation)
- The console button that starts a screening (ui-console-detail)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy equivalent: PRVSCR01.cbl (lines 90-92) screens only records whose screen flag is U, and PRVREV01.cbl (88-94) only wrote a queue row; nothing ever screened a provider again
Decisions: D-005 REPLACE: the same real checks are used for every screening reason; D-029 FIX: one revalidation case per provider; its due notice starts exactly one re-screening; D-018 FIX: results of every screening are kept; D-023 FIX: role-based access on every endpoint

##### Technical notes (agent-owned)
Service: screening-service. Endpoints/events: POST /api/v1/screenings, GET /api/v1/screenings, GET /api/v1/screenings/{screeningId}, RevalidationDue, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, SYSTEM.


#### MAM-32 Run screening as a batch job and produce the legacy SCRNOUT file

Type: Story. Service: screening-service. Labels: svc-screening-service, kag-MAM-32, agentic, decision-D-020, decision-D-024, decision-D-005, decision-D-004, decision-D-009. Depends on: MAM-25, MAM-42, MAM-37.

##### Business requirement
The daily enrolment job used to screen every provider that was still unscreened and write one line per check into a fixed-format file that other programmes and, later, the parity comparison depend on. The new system must still be able to do the same on demand for a business date: screen every provider that is still unscreened, produce the same file, and tell the administrator how many providers were read, screened and failed. Unlike the legacy job it must say clearly when something went wrong instead of finishing silently.

##### Acceptance criteria
###### AC-1 An administrator or the scheduler starts screening for a business date
Given tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a request with no token
When each calls POST /api/v1/batch/screening-runs with businessDate 2026-03-31, and ADMIN also calls it with a body without businessDate
Then ADMIN and SYSTEM receive 202 with a BatchRun (runId, job naming the screening job, businessDate 2026-03-31, status RUNNING); ANALYST, SUPERVISOR and PROVIDER receive 403; no token receives 401; the body without businessDate returns 400 with a problem+json body
Basis: R-PRVSCR01-cbl-00001, R-PRVSCR01-cbl-00013, R-PRVSCR01-cbl-00014, R-PRVSCR01-cbl-00015, R-PRVSCR01-cbl-00016, R-PRVSCR01-cbl-00017, D-020, D-023, ADR-004, modern/contracts/openapi/screening-service.yaml#startScreeningRun (x-roles ADMIN, SYSTEM; businessDate required)

###### AC-2 Only unscreened providers are screened
Given provider-service holds four providers, 1003000111 (screenFlag U), 1003000555 (U), 1003000222 (P) and 1003000444 (F), and none has a screening yet
When the run for 2026-03-31 executes
Then GET /api/v1/screenings shows one new screening with reason INITIAL for 1003000111 and one for 1003000555, and none for 1003000222 or 1003000444; every provider returned by provider-service GET /api/v1/providers (all pages) is read exactly once and the run stops when no page remains
Basis: R-PRVSCR01-cbl-00002, R-PRVSCR01-cbl-00003, R-PRVSCR01-cbl-00018, R-PRVSCR01-cbl-00019, R-PRVSCR01-cbl-00022, R-PRVSCR01-cbl-00023, D-014, ADR-002, modern/contracts/openapi/provider-service.yaml#searchProviders (SYSTEM may read)

###### AC-3 The SCRNOUT file has four 67-byte rows per screened provider
Given the run of criterion 2 has completed
When an ADMIN calls GET /api/v1/batch/screening-runs/{runId}/results-file
Then the body has 8 records of 67 bytes, four per screened provider in ascending NPI order (1003000111 then 1003000555) and, for each provider, in the order LICBOARD, OIGLEIE, SAMGOV, SSADMF; each record holds NPI (bytes 1-10), SOURCE (11-18, left-justified, space-padded), RESULT (19, P, F or C), VERDICT-DATE (20-27, CCYYMMDD, here 20260331) and DETAIL (28-67, left-justified, cut to 40 characters)
Basis: R-PRVSCR01-cbl-00053, R-PRVSCR01-cbl-00054, R-PRVSCR01-cbl-00055, R-PRVSCR01-cbl-00056, R-PRVSCR01-cbl-00057, R-PRVSCR01-cbl-00058, R-PRVSCR01-cbl-00059, R-PRVSCR01-cbl-00060, R-PRVSCR01-ADD-02, D-005, D-004, ADR-005, QUESTION C-7, modern/docs/analysis/data-layouts.md#SCRNREC, .claude/skills/cobol-to-java/SKILL.md (padding and truncation only in legacy-format adapters)

###### AC-4 A failed provider's rows show the failing source
Given provider 1003000555 (licence PT99900, EXPIRED on the licence-board mock) is screened by the run
When the SCRNOUT file of the run is downloaded
Then its LICBOARD row has RESULT F and its OIGLEIE, SAMGOV and SSADMF rows have RESULT P (the legacy wrote P on every row)
Basis: R-PRVSCR01-ADD-01, R-PRVSCR01-cbl-00010, D-005, aws-transform/test-data/license_board.txt

###### AC-5 The run records how many providers it read, screened, skipped and failed
Given the run of criterion 2 (4 providers read, 2 screened, 2 skipped, 1 failed)
When an ADMIN reads the run through GET /api/v1/batch/screening-runs/{runId} (run-status call proposed by question C-8; not in contract 1.0.0)
Then the BatchRun has status COMPLETED, an endedAt time and counts read 4, screened 2, skipped 2 and failed 1, where failed is the number of providers whose screening result is FAIL
Basis: R-PRVSCR01-cbl-00012, R-PRVSCR01-cbl-00048, R-PRVSCR01-cbl-00061, R-PRVSCR01-cbl-00062, D-024, ADR-004 (every run records its counts and failure reason), QUESTION C-8, modern/docs/analysis/data-layouts.md#WRKSTORE (read, written, skipped, errored counters)

###### AC-6 A failure is reported, never silent
Given a run during which provider-service answers 5xx, and another run whose SCRNOUT file cannot be written
When each run executes and an ADMIN reads it through the run-status call of question C-8
Then each run ends with status FAILED and the reason in error, it stops reading providers instead of retrying for ever, and it is never reported as COMPLETED
Basis: R-PRVSCR01-ADD-04, R-PRVSCR01-ADD-05, R-PRVSCR01-ADD-06, D-024, ADR-004, QUESTION C-8, modern/contracts/openapi/screening-service.yaml#BatchRun (status FAILED, error)

###### AC-7 Running again does not screen anyone twice
Given the run for 2026-03-31 has completed and an INITIAL screening (RUNNING or COMPLETED) exists for 1003000111 and 1003000555, whose screen flag is still U in provider-service
When another run is started for 2026-03-31
Then no further screening and no further ScreeningCompleted is created for 1003000111 or 1003000555, and the second run's SCRNOUT file has no rows for them
Basis: modern/docs/architecture/nfr.md#Availability (batch jobs can be re-run for a business date and are idempotent), ADR-004, R-PRVSCR01-cbl-00022

###### AC-8 Only an administrator can download the SCRNOUT file
Given a completed run id, an unknown run id, and tokens for ADMIN, SYSTEM, ANALYST and PROVIDER
When each calls GET /api/v1/batch/screening-runs/{runId}/results-file, and ADMIN also calls it with the unknown run id
Then ADMIN receives 200 with the file, SYSTEM, ANALYST and PROVIDER receive 403, and the unknown run id returns 404 with a problem+json body
Basis: modern/docs/architecture/nfr.md#Security, D-023, modern/contracts/openapi/screening-service.yaml#getScreeningResultsFile (x-roles ADMIN, 404)

##### Out of scope
- PRVDAY01 STEP020 SORT (dropped, D-009)
- The cron expression of the daily schedule (configuration, ADR-004)
- The console page that starts the run (ui-console-batch)
- Comparing the file with a legacy run (par-harness)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSCR01-ADD-04, R-PRVSCR01-ADD-05, R-PRVSCR01-ADD-06, R-PRVSCR01-cbl-00001, R-PRVSCR01-cbl-00002, R-PRVSCR01-cbl-00003, R-PRVSCR01-cbl-00012, R-PRVSCR01-cbl-00013, R-PRVSCR01-cbl-00014, R-PRVSCR01-cbl-00015, R-PRVSCR01-cbl-00016, R-PRVSCR01-cbl-00017, R-PRVSCR01-cbl-00018, R-PRVSCR01-cbl-00019, R-PRVSCR01-cbl-00022, R-PRVSCR01-cbl-00023, R-PRVSCR01-cbl-00048, R-PRVSCR01-cbl-00053, R-PRVSCR01-cbl-00054, R-PRVSCR01-cbl-00055, R-PRVSCR01-cbl-00056, R-PRVSCR01-cbl-00057, R-PRVSCR01-cbl-00058, R-PRVSCR01-cbl-00059, R-PRVSCR01-cbl-00060, R-PRVSCR01-cbl-00061, R-PRVSCR01-cbl-00062
Related rules: R-PRVSCR01-ADD-01, R-PRVSCR01-ADD-02, R-PRVSCR01-cbl-00010
Programs: source-code/BATCH/PRVSCR01.cbl: MAIN-CONTROL 74-82 (open 76-77, loop 79, close 80, failure count display 81), SCREEN-NEXT-PROVIDER 84-93, SCREEN-ONE-PROVIDER 95-114 (counter at 112, REWRITE at 114), WRITE-SCREEN-ROW 146-149. Job step: source-code/JCL/PRVDAY01.jcl STEP030 (DD SCRNOUT); STEP020 SORT dropped
Decisions: D-020 FIX: the business date is an explicit job parameter; D-024 FIX: a job result per run, failures surfaced, no endless loop; D-005 REPLACE: SCRNOUT rows now carry source, date and detail; D-004 FIX: 4-digit-year dates (CCYYMMDD in the file); D-009 DROP: the SORT step is not built

##### Technical notes (agent-owned)
Service: screening-service. Endpoints/events: POST /api/v1/batch/screening-runs, GET /api/v1/batch/screening-runs/{runId}/results-file, GET /api/v1/screenings, ScreeningCompleted. Roles: ADMIN, SYSTEM.


### MAM-6 risk-service

#### MAM-35 Assign the risk tier from the taxonomy prefix after a passed screening

Type: Story. Service: risk-service. Labels: svc-risk-service, kag-MAM-35, agentic, decision-D-002, decision-Q-005, decision-D-024, slice-main-journey. Depends on: MAM-25.

##### Business requirement
Every enrolled provider has a risk level (limited, moderate or high) that decides how closely the state watches it. Once a provider has passed screening, the system must work out that level from the first three characters of the provider's specialty code, using one table, and announce it so that the provider record keeps it, the analyst sees it before approving and the directory can show it. The legacy job probably never saved the level it worked out, so this story makes sure a level is always produced and announced, and always from the same table. A provider that failed screening gets no level because it will not be approved.

##### Acceptance criteria
###### AC-1 A passed screening gets a risk tier, which is announced for saving
Given ScreeningCompleted with result PASS for NPI 1003000111 and taxonomy 207Q00000X, and the rule set in force is version 1 (the legacy table)
When risk-service processes the event
Then exactly one RiskTierAssigned is published to pem.risk.events with message key 1003000111 and data npi 1003000111, taxonomy 207Q00000X, tier L and ruleSetVersion 1
Basis: D-002, R-PRVRSK01-ADD-04, R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00037, modern/contracts/asyncapi/events.yaml#RiskTierAssigned, modern/docs/architecture/overview.md#Main flows (2: provider-service saves the tier), modern/docs/ux/journeys.md#J1

###### AC-2 Prefixes 332 and 251 are High
Given ScreeningCompleted PASS for a provider whose taxonomy starts with 332 (for example 332B00000X), and another whose taxonomy starts with 251 (for example 251E00000X)
When risk-service processes the events
Then each RiskTierAssigned carries tier H
Basis: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00022, R-PRVRSK01-cbl-00023, R-PRVRSK01-cbl-00024, R-PRVRSK01-cbl-00035, Q-005, .claude/skills/pem-domain/SKILL.md#3, 42 CFR 455.450 (summary, unverified)

###### AC-3 Prefixes 341, 291 and 225 are Moderate
Given ScreeningCompleted PASS for taxonomies starting with 341 (for example 341600000X), 291 (291U00000X) and 225 (225100000X)
When risk-service processes the events
Then each RiskTierAssigned carries tier M
Basis: R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00026, R-PRVRSK01-cbl-00027, R-PRVRSK01-cbl-00028, R-PRVRSK01-cbl-00029, R-PRVRSK01-cbl-00030, R-PRVRSK01-cbl-00036, Q-005, .claude/skills/pem-domain/SKILL.md#3, 42 CFR 455.450 (summary, unverified)

###### AC-4 Every other prefix is Limited
Given ScreeningCompleted PASS for taxonomy 207Q00000X, and for taxonomy 999Z00000X, whose first three characters are in no rule
When risk-service processes the events
Then each RiskTierAssigned carries the rule set's default tier L
Basis: R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00025, R-PRVRSK01-cbl-00033, R-PRVRSK01-cbl-00034, R-PRVRSK01-cbl-00037, R-PRVRSK01-ADD-03, Q-005, modern/docs/ux/journeys.md#J1

###### AC-5 Each assessment carries exactly one tier, and a later passed screening is assessed again
Given NPI 1003000111 (taxonomy 207Q00000X) already received RiskTierAssigned tier L from its INITIAL screening, and a ScreeningCompleted PASS with reason REVALIDATION then arrives for it
When risk-service processes the event
Then a second RiskTierAssigned is published for 1003000111 carrying exactly one tier (L) and the ruleSetVersion in force at that moment; no event ever carries more than one tier
Basis: R-PRVRSK01-cbl-00007, R-PRVRSK01-cbl-00038, R-PRVRSK01-cbl-00031, R-PRVRSK01-cbl-00032, D-002, QUESTION C-15, modern/docs/architecture/overview.md#Main flows (2: on PASS), modern/docs/architecture/data-model/risk-service.sql (risk_assessment keeps every tier assigned)

###### AC-6 A failed screening gets no tier
Given ScreeningCompleted with result FAIL for NPI 1003000444
When risk-service processes the event
Then no RiskTierAssigned is published for 1003000444
Basis: modern/docs/architecture/overview.md#Main flows (2: on PASS), .claude/skills/pem-domain/SKILL.md#6 (risk tier assigned on PASS), modern/contracts/asyncapi/events.yaml#RiskTierAssigned (after a PASS screening)

###### AC-7 Every passed screening is classified exactly once
Given ScreeningCompleted PASS events for 1003000111, 1003000222 and 1003000333, the one for 1003000222 delivered twice with the same event id
When all are processed
Then exactly one RiskTierAssigned is published for each of the three NPIs: none is skipped and the duplicate delivery publishes nothing more
Basis: R-PRVRSK01-cbl-00001, R-PRVRSK01-cbl-00002, R-PRVRSK01-cbl-00011, R-PRVRSK01-cbl-00012, R-PRVRSK01-cbl-00013, R-PRVRSK01-cbl-00014, R-PRVRSK01-cbl-00015, R-PRVRSK01-cbl-00016, R-PRVRSK01-cbl-00017, D-002, ADR-003

###### AC-8 A failure to save is not silent, and assignments are counted
Given a database error occurs while storing the assessment for a ScreeningCompleted PASS event of NPI 1003000111
When the event is processed, and is later delivered again after the database is back
Then the failed attempt is written to the JSON log with the event's correlation id, no RiskTierAssigned is published for the failed attempt, exactly one RiskTierAssigned is published after the redelivery, and the Actuator metrics endpoint shows the count of tiers assigned increased by one
Basis: R-PRVRSK01-ADD-05, R-PRVRSK01-ADD-06, R-PRVRSK01-ADD-07, D-024, ADR-003, .claude/skills/java-microservices/SKILL.md#11 (Actuator metrics, JSON logs, correlation id)

##### Out of scope
- Saving the tier on the provider record (provider-service, prv-risk-tier)
- The revalidation due date that PRVRSK01 also set (provider-service owns it, D-003)
- Attaching meanings to the prefixes (question Q-005: none attached)
- Changing the rule table (rsk-rules-admin) and the on-request assessment (rsk-assess-api)
- Assigning a tier to every provider on every daily run (legacy behaviour replaced: a tier is assigned on a PASS screening, overview flow 2; D-002, D-003)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-04, R-PRVRSK01-ADD-05, R-PRVRSK01-ADD-06, R-PRVRSK01-ADD-07, R-PRVRSK01-cbl-00001, R-PRVRSK01-cbl-00002, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, R-PRVRSK01-cbl-00007, R-PRVRSK01-cbl-00011, R-PRVRSK01-cbl-00012, R-PRVRSK01-cbl-00013, R-PRVRSK01-cbl-00014, R-PRVRSK01-cbl-00015, R-PRVRSK01-cbl-00016, R-PRVRSK01-cbl-00017, R-PRVRSK01-cbl-00022, R-PRVRSK01-cbl-00023, R-PRVRSK01-cbl-00024, R-PRVRSK01-cbl-00025, R-PRVRSK01-cbl-00026, R-PRVRSK01-cbl-00027, R-PRVRSK01-cbl-00028, R-PRVRSK01-cbl-00029, R-PRVRSK01-cbl-00030, R-PRVRSK01-cbl-00031, R-PRVRSK01-cbl-00032, R-PRVRSK01-cbl-00033, R-PRVRSK01-cbl-00034, R-PRVRSK01-cbl-00035, R-PRVRSK01-cbl-00036, R-PRVRSK01-cbl-00037, R-PRVRSK01-cbl-00038
Related rules: R-PRVRSK01-ADD-03
Programs: source-code/BATCH/PRVRSK01.cbl: MAIN-CONTROL 61-67, CLASSIFY-NEXT 69-76, CLASSIFY-ONE 78-97 (EVALUATE 83-93, ALTER at 86, 90, 92, REWRITE at 97), APPLY-TIER-PARA 103-104, LIMITED-TIER 106-108, MODERATE-TIER 109-111, HIGH-TIER 112-114, TIER-DONE 115-116. Job step: source-code/JCL/PRVDAY01.jcl STEP040, tier part only
Decisions: D-002 FIX: the risk tier is saved: every passed screening yields exactly one tier (no ALTER path), published in RiskTierAssigned for provider-service to save (overview flow 2); Q-005 Option A: the legacy prefix table is kept exactly, as data, with no meanings attached; D-024 FIX: explicit error handling; failures logged, retried and counted

##### Technical notes (agent-owned)
Service: risk-service. Endpoints/events: ScreeningCompleted, RiskTierAssigned. Roles: SYSTEM.


#### MAM-38 View and change the taxonomy-prefix risk table (admin)

Type: Story. Service: risk-service. Labels: svc-risk-service, kag-MAM-38, agentic, decision-Q-005, decision-D-023. Depends on: MAM-35.

##### Business requirement
Which specialty codes are high, moderate or limited risk is business knowledge that will change over time, and the business must be able to see the table and change it without a software release. Staff can read the current table, an administrator can save a changed table as a new version, and each assignment records which version it used. At the start the table holds exactly the values the legacy job used.

##### Acceptance criteria
###### AC-1 Staff can read the current table
Given the initial rule set
When an ANALYST, SUPERVISOR or ADMIN calls GET /api/v1/risk-rules
Then the response is 200 with version 1, defaultTier L and exactly the rules 332 H, 251 H, 341 M, 291 M and 225 M
Basis: Q-005, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, .claude/skills/pem-domain/SKILL.md#3, modern/contracts/openapi/risk-service.yaml#RiskRuleSet, modern/docs/ux/wireframes/console-risk-rules.html

###### AC-2 An administrator saves a changed table as a new version
Given the current version is 1 and an ADMIN token
When the ADMIN calls PUT /api/v1/risk-rules with version 1, defaultTier L and the legacy rules plus prefix 207 as M
Then the response is 200 with version 2, GET /api/v1/risk-rules then returns version 2 including prefix 207, and the next ScreeningCompleted PASS for taxonomy 207Q00000X produces a RiskTierAssigned with tier M and ruleSetVersion 2
Basis: QUESTION C-9, modern/contracts/openapi/risk-service.yaml#replaceRiskRules, modern/docs/ux/wireframes/console-risk-rules.html (Save as new version), .claude/skills/pem-domain/SKILL.md#3 (data-driven table the business can change)

###### AC-3 A stale or inconsistent table is refused
Given the current version is 2
When an ADMIN calls PUT /api/v1/risk-rules with version 1, and then with version 2 and a table that lists prefix 332 twice
Then both calls return 422 with a problem+json body and GET /api/v1/risk-rules still returns version 2 unchanged
Basis: QUESTION C-9, modern/contracts/openapi/risk-service.yaml#replaceRiskRules (422 business validation), modern/docs/architecture/data-model/risk-service.sql (a prefix is unique within a rule set)

###### AC-4 Malformed rules are rejected
Given an ADMIN token
When PUT /api/v1/risk-rules is called with a 2-character prefix, a lower-case prefix, a tier X, or no defaultTier
Then each call returns 400 with a problem+json body and GET /api/v1/risk-rules returns the table unchanged
Basis: modern/contracts/openapi/risk-service.yaml#RiskRule (taxonomyPrefix pattern ^[0-9A-Z]{3}$, tier L, M or H), modern/contracts/openapi/risk-service.yaml#RiskRuleSet (required version, defaultTier, rules)

###### AC-5 Saving a new version re-tiers nobody
Given NPI 1003000111 was assigned tier L under version 1
When an ADMIN saves version 2 in which prefix 207 is M
Then no RiskTierAssigned is published because of the change, so the tier announced for 1003000111 stays L until a new passed screening of 1003000111 is assessed
Basis: QUESTION C-9, modern/docs/architecture/data-model/risk-service.sql (each assessment keeps its rule_set_version)

###### AC-6 Only an administrator can change the table
Given tokens for ADMIN, SUPERVISOR, ANALYST and PROVIDER, and a request with no token
When each calls PUT /api/v1/risk-rules, and then GET /api/v1/risk-rules
Then PUT is accepted only for ADMIN (SUPERVISOR, ANALYST and PROVIDER receive 403); GET is accepted for ANALYST, SUPERVISOR and ADMIN (PROVIDER receives 403); no token receives 401 on both
Basis: modern/docs/architecture/nfr.md#Security, D-023, ADR-007, modern/docs/ux/screen-inventory.md#console risk-rules (ADMIN), modern/contracts/openapi/risk-service.yaml (x-roles)

##### Out of scope
- The console page (ui-console-risk-rules)
- Who changed the table and when (the data model has no audit field for rule changes)
- Meanings or labels for the prefixes (Q-005: none attached)
- Re-tiering existing providers after a change
- A second-person approval of a rule change
- A future effectiveFrom date (question C-9: a saved version applies from the moment it is saved)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006
Programs: source-code/BATCH/PRVRSK01.cbl: CLASSIFY-ONE EVALUATE 83-93 (prefix lists 332/251, 341/291/225, WHEN OTHER); the lists become data, not code
Decisions: Q-005 Option A: the legacy prefix table is kept exactly, as data, with no meanings attached; D-023 FIX: role-based access; the table is changed by ADMIN only

##### Technical notes (agent-owned)
Service: risk-service. Endpoints/events: GET /api/v1/risk-rules, PUT /api/v1/risk-rules, RiskTierAssigned. Roles: ADMIN, ANALYST, SUPERVISOR.


#### MAM-41 Assess a risk tier on request (risk assessment API)

Type: Story. Service: risk-service. Labels: svc-risk-service, kag-MAM-41, agentic, decision-Q-005, decision-D-002. Depends on: MAM-35, MAM-38.

##### Business requirement
Analysts and other services sometimes need to know which risk level a specialty code would get without changing anything, for example to explain a level to a provider or to check a code. This story provides that question-and-answer service. It uses the same table as the real assignment, looks only at the first three characters of the code, and never records or announces anything.

##### Acceptance criteria
###### AC-1 Prefixes in the table are assessed with their tier
Given rule set version 1 is in force and an ANALYST token
When POST /api/v1/risk-assessments is called with taxonomy 332B00000X, 251E00000X, 341600000X, 291U00000X, 225100000X and 207Q00000X
Then each response is 200 with the taxonomy, ruleSetVersion 1 and tier H, H, M, M, M and L in that order
Basis: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006, Q-005, modern/contracts/openapi/risk-service.yaml#assessRisk

###### AC-2 Only the first three characters count
Given rule set version 1 is in force
When POST /api/v1/risk-assessments is called with taxonomy 3320000000 and with 332ZZZZZZZ
Then both responses return tier H, because only the first three characters are used and the rest is ignored
Basis: R-PRVRSK01-cbl-00003, R-PRVRSK01-cbl-00018, R-PRVRSK01-cbl-00019, R-PRVRSK01-cbl-00020, R-PRVRSK01-cbl-00021

###### AC-3 A blank or too-short taxonomy gets the default tier, as in the legacy
Given rule set version 1 is in force
When POST /api/v1/risk-assessments is called with taxonomy blank, and with taxonomy 33
Then each response is 200 with tier L (the default tier), not an error
Basis: R-PRVRSK01-ADD-03, R-PRVRSK01-cbl-00006 (any other prefix, including blank, is Limited), Q-005

###### AC-4 A missing or too-long taxonomy is a bad request
Given an ANALYST token
When POST /api/v1/risk-assessments is called with no taxonomy, and with a taxonomy of 11 characters
Then each call returns 400 with a problem+json body
Basis: modern/contracts/openapi/risk-service.yaml#RiskAssessmentRequest (taxonomy required, maxLength 10)

###### AC-5 The assessment has no side effect
Given provider 1003000111 with riskTier L in provider-service
When POST /api/v1/risk-assessments is called with taxonomy 332B00000X, once without and once with npi 1003000111
Then each response carries tier H, but no RiskTierAssigned is published and GET /api/v1/providers/1003000111 still shows riskTier L
Basis: modern/contracts/openapi/risk-service.yaml#assessRisk (compute the tier, no side effect), D-002, ADR-002

###### AC-6 The answer uses the table in force
Given rule set version 2 is in force, with prefix 207 as M
When POST /api/v1/risk-assessments is called with taxonomy 207Q00000X
Then the response returns tier M and ruleSetVersion 2
Basis: modern/contracts/openapi/risk-service.yaml#RiskAssessment (ruleSetVersion), QUESTION C-9, modern/docs/architecture/data-model/risk-service.sql

###### AC-7 Only analysts, supervisors and the system can ask
Given tokens for ANALYST, SUPERVISOR, SYSTEM, PROVIDER and ADMIN, and a request with no token
When each calls POST /api/v1/risk-assessments with taxonomy 207Q00000X
Then ANALYST, SUPERVISOR and SYSTEM receive 200, PROVIDER and ADMIN receive 403, and the request with no token receives 401
Basis: modern/docs/architecture/nfr.md#Security, D-023, ADR-007, modern/contracts/openapi/risk-service.yaml#assessRisk (x-roles)

##### Out of scope
- Storing or announcing a tier (rsk-assign-tier)
- Changing the table (rsk-rules-admin)
- Validating that a taxonomy exists in a national code list (the legacy does not; R-PRVRSK01-ADD-03)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVRSK01-ADD-03, R-PRVRSK01-cbl-00003, R-PRVRSK01-cbl-00018, R-PRVRSK01-cbl-00019, R-PRVRSK01-cbl-00020, R-PRVRSK01-cbl-00021
Related rules: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006
Programs: source-code/BATCH/PRVRSK01.cbl: CLASSIFY-ONE 78-93 (taxonomy group is the first 3 characters of PRV-TAXONOMY, lines 79-80; EVALUATE 83-93)
Decisions: Q-005 Option A: the legacy prefix table is kept exactly, as data, with no meanings attached; D-002 FIX: the tier is produced by the event path (rsk-assign-tier); this endpoint never saves or announces

##### Technical notes (agent-owned)
Service: risk-service. Endpoints/events: POST /api/v1/risk-assessments. Roles: ANALYST, SUPERVISOR, SYSTEM.


### MAM-7 revalidation-service

#### MAM-45 Monthly revalidation sweep: open a case for every ACTIVE provider that is due, with the legacy REVALQ file

Type: Story. Service: revalidation-service. Labels: svc-revalidation-service, kag-MAM-45, agentic, decision-D-003, decision-D-029, decision-D-004, decision-D-020, decision-D-024, decision-D-006, decision-D-014. Depends on: MAM-27, MAM-42.

##### Business requirement
Providers must be revalidated at least every five years. Once a month the state must find every active provider whose revalidation date has arrived and open one revalidation case for each, so that nobody is forgotten and nobody is queued twice. Today the list is rebuilt from scratch every month with no memory of earlier months, and the dates it relies on are reset every day, so it rarely finds anyone. After this story each due provider has exactly one open case, the administrator can run the sweep for any business date, and the same fixed-format queue file as before can be downloaded.

##### Acceptance criteria
###### AC-1 An administrator or the scheduler starts the sweep for a business date
Given tokens for ADMIN, SYSTEM, ANALYST and PROVIDER, and a request with no token
When each calls POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31, and ADMIN also calls it with a body without businessDate
Then ADMIN and SYSTEM receive 202 with a BatchRun (runId, job naming the revalidation sweep, businessDate 2026-03-31, status RUNNING); ANALYST and PROVIDER receive 403; no token receives 401; the body without businessDate returns 400; every due date is then compared with businessDate 2026-03-31 (4-digit year), not with the clock date
Basis: R-PRVREV01-cbl-00001, R-PRVREV01-cbl-00002, R-PRVREV01-cbl-00008, R-PRVREV01-cbl-00009, R-PRVREV01-cbl-00010, D-020, D-004, D-023, ADR-004, modern/contracts/openapi/revalidation-service.yaml#startRevalidationSweep (x-roles ADMIN, SYSTEM), modern/docs/ux/journeys.md#J6

###### AC-2 An ACTIVE provider whose due date has arrived gets one case
Given revalidation-service tracks provider 1003000222 as status A with revalidation due date 2026-02-28 and provider 1003000333 as status A with due date 2026-03-31, both from ProviderStatusChanged events
When the sweep runs for 2026-03-31
Then GET /api/v1/revalidations?npi=1003000222 and ?npi=1003000333 each return exactly one open case (status DUE or IN_SCREENING) whose dueDate is the provider's own due date (2026-02-28 and 2026-03-31)
Basis: R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00011, R-PRVREV01-cbl-00012, R-PRVREV01-cbl-00013, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, R-PRVREV01-ADD-01, D-029, D-003, D-014, QUESTION C-13, modern/docs/ux/journeys.md#J6, 42 CFR 455.414 (summary, unverified)

###### AC-3 Providers that are not due, not ACTIVE or moved forward get no case
Given tracked providers: 1003000777 status A due 2026-04-01; 1003000888 status S due 2026-02-28; 1003000555 status T due 2026-02-28; and 1003000111 status A due 2026-02-28 that, before the sweep, received a ProviderStatusChanged with revalidationDueDate 2031-03-31
When the sweep runs for 2026-03-31
Then GET /api/v1/revalidations?npi=... returns no case for any of these four providers and the run's REVALQ file has no row for them
Basis: R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, D-006, D-003, QUESTION C-10, modern/contracts/asyncapi/events.yaml#ProviderStatusChanged (revalidationDueDate)

###### AC-4 Opening a case announces it
Given the sweep opens a case for provider 1003000222
When the case is committed
Then exactly one RevalidationDue is published to pem.revalidation.events with message key 1003000222 and data caseId, npi 1003000222 and dueDate 2026-02-28
Basis: modern/contracts/asyncapi/events.yaml#RevalidationDue, R-PRVREV01-cbl-00005, D-029, ADR-003

###### AC-5 A provider that already has an open case is not queued again
Given provider 1003000222 has an open case (status DUE or IN_SCREENING) from the sweep for 2026-03-31
When another sweep runs, for 2026-03-31 again and then for 2026-04-30
Then GET /api/v1/revalidations?npi=1003000222 still returns one case, no second RevalidationDue is published for it, and the later runs' REVALQ files have no row for it
Basis: R-PRVREV01-ADD-02, D-029, modern/docs/architecture/data-model/revalidation-service.sql (at most one open case per provider), modern/docs/architecture/nfr.md#Availability (re-runnable, idempotent)

###### AC-6 A provider with no due date is not queued, and the gap is visible
Given tracked provider 1003000666 with status A and no revalidation due date (revalidationDueDate null)
When the sweep runs for 2026-03-31 and an ADMIN reads the run through the run-status call of question C-8
Then no case is opened for 1003000666, the REVALQ file has no row for it, and the run's counts show it under noDueDate
Basis: R-PRVREV01-ADD-03, D-029, D-024, QUESTION C-11, QUESTION C-8

###### AC-7 The legacy REVALQ file lists the cases the run opened
Given the run of criterion 2, which opened the cases for 1003000222 and 1003000333
When an ADMIN calls GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file
Then the body has 2 records of 80 bytes in ascending NPI order, each holding NPI (bytes 1-10), the case due date as CCYYMMDD (11-18), RESCREEN-REQUIRED left-justified and space-padded to 20 characters (19-38) and 42 spaces (39-80), for example 1003000222 then 20260228; a run that opens no case returns an empty file; SYSTEM, ANALYST and PROVIDER tokens receive 403 and an unknown runId returns 404
Basis: R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00017, R-PRVREV01-cbl-00018, R-PRVREV01-cbl-00019, R-PRVREV01-cbl-00020, ADR-005, D-004, modern/docs/analysis/data-layouts.md#PRVREV01:REVAL-OUT-RECORD, modern/contracts/openapi/revalidation-service.yaml#getRevalidationQueueFile (x-roles ADMIN, 404), modern/docs/ux/journeys.md#J6

###### AC-8 The run reports how many cases it queued, and a failure is never silent
Given the run of criterion 2, which evaluated every tracked provider and opened 2 cases, and another run whose database or file write fails
When an ADMIN reads each run through GET /api/v1/batch/revalidation-sweeps/{runId} (run-status call proposed by question C-8; not in contract 1.0.0)
Then the first run has status COMPLETED and counts queued 2, reached only after every tracked provider was evaluated; the failed run has status FAILED with the reason in error and is never reported as COMPLETED
Basis: R-PRVREV01-cbl-00006, R-PRVREV01-cbl-00007, R-PRVREV01-cbl-00014, R-PRVREV01-cbl-00021, R-PRVREV01-cbl-00022, R-PRVREV01-cbl-00023, R-PRVREV01-cbl-00024, R-PRVREV01-ADD-04, D-024, ADR-004 (every run records its counts and failure reason), QUESTION C-8, modern/contracts/openapi/revalidation-service.yaml#BatchRun

##### Out of scope
- Re-screening the providers (screening-service, scr-rescreen)
- Completing, listing and cancelling cases (rev-cases, rev-cases-2)
- Setting the due date (provider-service owns it, D-003)
- The cron expression of the monthly schedule (configuration, ADR-004)
- The console page that starts the sweep (ui-console-batch)
- A re-run for the same business date opens no new case, so its REVALQ file is empty (consequence of D-029; the earlier run's file stays available)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVREV01-ADD-02, R-PRVREV01-ADD-03, R-PRVREV01-ADD-04, R-PRVREV01-cbl-00001, R-PRVREV01-cbl-00002, R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004, R-PRVREV01-cbl-00005, R-PRVREV01-cbl-00006, R-PRVREV01-cbl-00007, R-PRVREV01-cbl-00008, R-PRVREV01-cbl-00009, R-PRVREV01-cbl-00010, R-PRVREV01-cbl-00011, R-PRVREV01-cbl-00012, R-PRVREV01-cbl-00013, R-PRVREV01-cbl-00014, R-PRVREV01-cbl-00015, R-PRVREV01-cbl-00016, R-PRVREV01-cbl-00017, R-PRVREV01-cbl-00018, R-PRVREV01-cbl-00019, R-PRVREV01-cbl-00020, R-PRVREV01-cbl-00021, R-PRVREV01-cbl-00022, R-PRVREV01-cbl-00023, R-PRVREV01-cbl-00024
Related rules: R-PRVREV01-ADD-01
Programs: source-code/BATCH/PRVREV01.cbl: MAIN-CONTROL 63-73 (program id at 64, date at 65, open 66-67, loop 69, close 70, summary display 71-72), CHECK-NEXT 75-86 (active and due test 81-84), QUEUE-FOR-REVAL 88-94 (queue row 89-93, counter 94). Job step: source-code/JCL/PRVMTH01.jcl STEP010 (DD REVALQ)
Decisions: D-003 FIX: the due date compared is the one set at approval and at each completed revalidation, never reset daily; D-029 FIX: one case with its own state per provider; a provider is not queued again while its case is open; D-004 FIX: 4-digit-year dates (ISO in the API, CCYYMMDD in REVALQ); D-020 FIX: the business date is an explicit job parameter; D-024 FIX: a job result per run, failures surfaced; D-006 FIX: status S (suspended) exists; only ACTIVE (A) providers are swept; D-014 FIX: provider-service is the single owner; revalidation-service keeps only its own read model built from ProviderStatusChanged (overview differences 4)

##### Technical notes (agent-owned)
Service: revalidation-service. Endpoints/events: POST /api/v1/batch/revalidation-sweeps, GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file, GET /api/v1/revalidations, ProviderStatusChanged, RevalidationDue. Roles: ADMIN, SYSTEM, ANALYST, SUPERVISOR.


#### MAM-48 Revalidation cases: list them and complete one

Type: Story. Service: revalidation-service. Labels: svc-revalidation-service, kag-MAM-48, agentic, decision-D-003, decision-D-029, decision-D-023. Depends on: MAM-45.

##### Business requirement
Once a revalidation case has been opened, staff must be able to see it, and the case must be closed when the provider's re-screening is done, so that the provider is not chased again and, after a pass, gets a new five-year date. Today the legacy queue only listed the provider and nothing recorded what happened afterwards. After this story analysts see the open and closed cases, a re-screening result closes the case by itself, an analyst can close it by hand, and the provider's next due date comes from the provider record, not from this service.

##### Acceptance criteria
###### AC-1 Analysts list revalidation cases
Given cases exist in several statuses, including one DUE case for NPI 1003000222
When an ANALYST or SUPERVISOR calls GET /api/v1/revalidations?status=DUE&page=0&size=20, then ?npi=1003000222, then ?status=OPEN
Then the first two responses are 200 with items (caseId, npi, dueDate, status, openedAt, closedAt), page, size and total holding only the matching cases; the invalid status value OPEN returns 400 with a problem+json body
Basis: modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases (status enum, 400), D-029, modern/docs/ux/screen-inventory.md#console work-queues (GET /api/v1/revalidations), modern/docs/ux/journeys.md#J6

###### AC-2 An analyst or the system completes a case with a screening result
Given an open case for NPI 1003000222 and a completed REVALIDATION screening of 1003000222 with result PASS
When an ANALYST (or SYSTEM) calls POST /api/v1/revalidations/{caseId}/completion with that screeningId and result PASS
Then the response is 200 with the case in status COMPLETED and closedAt set, and GET /api/v1/revalidations?status=DUE and ?status=IN_SCREENING no longer list it
Basis: modern/contracts/openapi/revalidation-service.yaml#completeRevalidation, D-003, D-029

###### AC-3 Unknown and closed cases are refused
Given an unknown caseId, and a case that is already COMPLETED or CANCELLED
When POST /api/v1/revalidations/{caseId}/completion is called for each
Then the unknown case returns 404 and the closed case returns 409, both with a problem+json body, and the closed case is unchanged
Basis: modern/contracts/openapi/revalidation-service.yaml#completeRevalidation (404, 409), D-029

###### AC-4 A passed re-screening completes the case by itself
Given an open case for 1003000222, and screening-service publishes ScreeningCompleted with reason REVALIDATION and result PASS for 1003000222
When revalidation-service processes the event
Then GET /api/v1/revalidations?npi=1003000222 shows the case COMPLETED with closedAt set, without any REST call
Basis: modern/contracts/asyncapi/events.yaml#ScreeningCompleted, modern/docs/architecture/overview.md#Main flows (4), modern/docs/ux/journeys.md#J6 (on PASS the case completes), D-003, D-029

###### AC-5 A failed re-screening closes the case but does not move the due date
Given an open case for 1003000222 (due 2026-02-28) and ScreeningCompleted with reason REVALIDATION and result FAIL for 1003000222
When revalidation-service processes the event
Then the case becomes COMPLETED, the due date tracked for 1003000222 stays 2026-02-28 (no new date reaches revalidation-service), revalidation-service publishes nothing for it, and the screening is listed by GET /api/v1/screenings?result=FAIL
Basis: QUESTION C-12, modern/contracts/openapi/revalidation-service.yaml#CompletionRequest (result PASS or FAIL), D-003

###### AC-6 Only revalidation screenings close cases
Given an open case for 1003000222, a ScreeningCompleted with reason INITIAL or MANUAL for 1003000222, and a ScreeningCompleted with reason REVALIDATION for NPI 1003000999, which has no open case
When revalidation-service processes the events
Then the case for 1003000222 keeps its status, and the event for 1003000999 is ignored without error and opens no case
Basis: modern/docs/architecture/overview.md#Main flows (4: on ScreeningCompleted REVALIDATION), modern/contracts/asyncapi/events.yaml#ScreeningCompleted (reason), D-029

###### AC-7 The next due date comes from provider-service and is followed here
Given the case for 1003000222 was completed with PASS, and provider-service then publishes ProviderStatusChanged for 1003000222 (from A to A) with revalidationDueDate 2031-03-31
When the sweep runs for 2026-04-30
Then no new case is opened for 1003000222 and its REVALQ file has no row for it, because the tracked due date is now 2031-03-31
Basis: R-PRVREV01-ADD-01, D-003, ADR-002, QUESTION C-10, modern/docs/architecture/overview.md#Where this design differs (5), modern/contracts/asyncapi/events.yaml#ProviderStatusChanged

###### AC-8 Only the right roles can list and complete cases
Given tokens for ANALYST, SUPERVISOR, SYSTEM, ADMIN and PROVIDER, and a request with no token
When each calls GET /api/v1/revalidations and POST /api/v1/revalidations/{caseId}/completion
Then GET is accepted for ANALYST and SUPERVISOR; POST is accepted for SYSTEM and ANALYST; every other role receives 403 and no token receives 401
Basis: modern/docs/architecture/nfr.md#Security, D-023, ADR-007, modern/contracts/openapi/revalidation-service.yaml (x-roles)

##### Out of scope
- Opening cases (rev-sweep)
- The states IN_SCREENING and CANCELLED (rev-cases-2)
- Computing and storing the next due date (provider-service; question C-10)
- Any automatic suspension or termination after a failed revalidation (question C-12: not done)
- The console work queue (ui-console-queues)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVREV01-ADD-01
Programs: No direct legacy equivalent: source-code/BATCH/PRVREV01.cbl QUEUE-FOR-REVAL 88-94 wrote the queue row and nothing tracked it afterwards (D-029). The due date was reset daily by source-code/BATCH/PRVRSK01.cbl SET-REVALIDATION-DATE 126-137 (R-PRVREV01-ADD-01, D-003)
Decisions: D-003 FIX: the due date moves only at approval and at a completed revalidation, never daily; it is set by provider-service; D-029 FIX: one revalidation case per provider with its own state; D-023 FIX: role-based access on every endpoint

##### Technical notes (agent-owned)
Service: revalidation-service. Endpoints/events: GET /api/v1/revalidations, POST /api/v1/revalidations/{caseId}/completion, ScreeningCompleted, ProviderStatusChanged. Roles: ANALYST, SUPERVISOR, SYSTEM.


#### MAM-49 Revalidation case states: re-screening requested and provider no longer active

Type: Story. Service: revalidation-service. Labels: svc-revalidation-service, kag-MAM-49, agentic, decision-D-029, decision-D-006. Depends on: MAM-45, MAM-48.

##### Business requirement
A revalidation case passes through stages, and staff need to see which stage it is in: waiting to be screened, being screened, finished, or no longer needed because the provider is no longer active. This story defines when a case moves to the screening stage and when it is cancelled, so that the work queue never shows cases that nobody can act on, and so that a provider who is reinstated while still overdue is picked up again at the next sweep.

##### Acceptance criteria
###### AC-1 A case moves to IN_SCREENING when the re-screening is requested
Given the sweep opened a case for 1003000222 in status DUE
When the RevalidationDue event for that case has been published
Then GET /api/v1/revalidations?npi=1003000222 shows the case with status IN_SCREENING
Basis: QUESTION C-13, modern/contracts/asyncapi/events.yaml#RevalidationDue (re-screening starts), modern/contracts/openapi/revalidation-service.yaml#RevalidationCase (status)

###### AC-2 A provider that is no longer ACTIVE cancels its open case
Given an open case (DUE or IN_SCREENING) for 1003000222
When provider-service publishes ProviderStatusChanged for 1003000222 from A to S (and, in separate examples, to T or D)
Then the case becomes CANCELLED with closedAt set and is listed by GET /api/v1/revalidations?status=CANCELLED
Basis: QUESTION C-14, R-PRVREV01-cbl-00003, D-006, D-029

###### AC-3 A late screening result does not reopen a cancelled case
Given the case for 1003000222 is CANCELLED
When ScreeningCompleted with reason REVALIDATION arrives for 1003000222
Then GET /api/v1/revalidations?npi=1003000222 still shows the case CANCELLED and no error is raised
Basis: QUESTION C-14, D-029

###### AC-4 A reinstated provider that is still overdue gets a new case
Given the case for 1003000222 was CANCELLED, the provider is reinstated (ProviderStatusChanged from S to A) and its due date is still 2026-02-28
When the sweep runs for 2026-03-31
Then a new open case (status DUE or IN_SCREENING) is opened for 1003000222 and the cancelled case remains listed with status CANCELLED
Basis: QUESTION C-14, R-PRVREV01-cbl-00004, D-029, D-006

##### Out of scope
- Opening cases and the REVALQ file (rev-sweep)
- Completing cases (rev-cases)
- Sanctions themselves (sanctions-service)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004
Programs: No direct legacy equivalent: PRVREV01.cbl CHECK-NEXT 81-84 only tested that the provider was ACTIVE and due; no stage was kept (D-029)
Decisions: D-029 FIX: one revalidation case per provider with its own state (DUE, IN_SCREENING, COMPLETED, CANCELLED); D-006 FIX: status S (suspended) exists; a provider that leaves ACTIVE cancels its open case

##### Technical notes (agent-owned)
Service: revalidation-service. Endpoints/events: GET /api/v1/revalidations, RevalidationDue, ProviderStatusChanged, ScreeningCompleted. Roles: ANALYST, SUPERVISOR, SYSTEM.


### MAM-8 sanctions-service

#### MAM-52 Record a sanction (terminate, suspend, reinstate) with history and apply it through provider-service

Type: Story. Service: sanctions-service. Labels: svc-sanctions-service, kag-MAM-52, agentic, decision-D-006, decision-D-012, decision-D-013, decision-D-018, decision-D-023, decision-D-024, decision-D-026, decision-D-027. Depends on: MAM-33.

##### Business requirement
A supervisor can terminate, suspend or reinstate a provider, giving a reason and an effective date. The system checks that the action is one of the three known ones and that the provider exists, changes the provider's status through the provider service, keeps a permanent history of every sanction, and announces the sanction so that a notice can be sent. A suspension is a status of its own and is no longer confused with a denial. As in the old program, a sanction is applied whatever the provider's current status is. Nothing is recorded as applied if the status could not be changed.

##### Acceptance criteria
###### AC-1 A termination sets the provider to TERMINATED
Given A SUPERVISOR is signed in and provider 1003000111 has status A (ACTIVE) in provider-service
When POST /api/v1/sanctions is called with npi 1003000111, action T, reason 'SYNTHETIC TEST REASON' and effectiveDate 2026-03-31
Then 201 with a Sanction that has a sanctionId, npi 1003000111, action T, resultingStatus T, effectiveDate 2026-03-31, the reason, recordedAt, and recordedBy set to the supervisor; the status was changed only through provider-service's POST /api/v1/providers/1003000111/status-changes with action TERMINATE, effectiveDate 2026-03-31 and the same sanctionId, so GET /api/v1/providers/1003000111 now shows status T (TERMINATED); exactly one SanctionApplied event is published with subject 1003000111, action T, effectiveDate 2026-03-31 and the same sanctionId
Basis: R-PRVSAN01-cbl-00002, R-PRVSAN01-cbl-00018, R-PRVSAN01-cbl-00019, R-PRVSAN01-cbl-00020, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00004, D-012, D-013, D-018, modern/contracts/openapi/provider-service.yaml#changeProviderStatus, modern/contracts/asyncapi/events.yaml#SanctionApplied

###### AC-2 A suspension sets the provider to SUSPENDED, not DENIED
Given A SUPERVISOR is signed in and provider 1003000333 has status A (ACTIVE)
When POST /api/v1/sanctions is called with npi 1003000333, action S, a reason and effectiveDate 2026-03-31
Then 201 with resultingStatus S; provider-service received a status change with action SUSPEND; GET /api/v1/providers/1003000333 shows status S (SUSPENDED) and not D (DENIED); exactly one SanctionApplied event with action S is published
Basis: R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00005, D-006, modern/docs/ux/journeys.md#J5, modern/contracts/openapi/provider-service.yaml#StatusChangeRequest

###### AC-3 A reinstatement sets the provider to ACTIVE
Given A SUPERVISOR is signed in and provider 1003000333 has status S (suspended in AC-2)
When POST /api/v1/sanctions is called with npi 1003000333, action R, a reason and effectiveDate 2026-04-30
Then 201 with resultingStatus A; provider-service received a status change with action REINSTATE; GET /api/v1/providers/1003000333 shows status A (ACTIVE); exactly one SanctionApplied event with action R is published
Basis: R-PRVSAN01-cbl-00033, R-PRVSAN01-cbl-00006, D-006, modern/docs/ux/journeys.md#J5, modern/contracts/openapi/provider-service.yaml#StatusChangeRequest

###### AC-4 A sanction is applied whatever the current status
Given A SUPERVISOR is signed in; provider 1003000444 has status D (DENIED), provider 1003000555 has status P (PENDING) and provider 1003000666 has status D (DENIED)
When R is recorded for 1003000444 and for 1003000555, and T is recorded for 1003000666
Then Each call returns 201 with no refusal based on the current status: 1003000444 and 1003000555 become A (ACTIVE) and 1003000666 becomes T (TERMINATED); one SanctionApplied event is published per call
Basis: D-027, Q-006, R-PRVSAN01-ADD-04, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00034

###### AC-5 An unknown action code is rejected
Given A SUPERVISOR is signed in and provider 1003000111 is ACTIVE
When POST /api/v1/sanctions is called for npi 1003000111 with action X, and again with an empty action
Then Each call returns 422 problem+json whose detail names the field action and the allowed values T, S and R, with no legacy code (none of E101-E205 applies); no sanction is stored (GET /api/v1/sanctions?npi=1003000111 is unchanged); provider-service receives no status change and the provider's status is unchanged; no SanctionApplied event is published, so no notice is triggered
Basis: R-PRVSAN01-ADD-02, D-026, modern/docs/ux/journeys.md#J5, Q-B10

###### AC-6 A sanction for an unknown provider is not found
Given A SUPERVISOR is signed in and no provider has NPI 1003000999 (provider-service answers 404)
When POST /api/v1/sanctions is called with npi 1003000999, action T and effectiveDate 2026-03-31
Then 404 problem+json whose detail names NPI 1003000999; no sanction is stored for it and no SanctionApplied event is published
Basis: R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00022, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, D-013

###### AC-7 A failure to apply is never reported as not found or as applied
Given A SUPERVISOR is signed in and provider-service is unreachable, or answers with a server error, for NPI 1003000111 (fault injected)
When POST /api/v1/sanctions is called with action T and effectiveDate 2026-03-31 for 1003000111
Then 503 problem+json whose detail names NPI 1003000111 (not 404 and not 201); no sanction is recorded as applied (GET /api/v1/sanctions?npi=1003000111 has no new row) and no SanctionApplied event is published
Basis: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-05, D-024, D-013, Q-B13

###### AC-8 The sanction history is readable, and roles are enforced
Given Sanctions T and then R were recorded for NPI 1003000111, and tokens for every role
When GET /api/v1/sanctions?npi=1003000111 and POST /api/v1/sanctions are called
Then ANALYST, SUPERVISOR and ADMIN get 200 with items for both rows, each with action, reason, effectiveDate, recordedBy, recordedAt and resultingStatus (T and A), plus page, size and total (defaults page 0, size 20); PROVIDER and SYSTEM get 403 on the GET and a call without a token gets 401; POST is allowed only for SUPERVISOR (ANALYST, ADMIN, SYSTEM and PROVIDER get 403, no token gets 401)
Basis: R-PRVSAN01-ADD-03, D-018, D-023, ADR-007, modern/docs/architecture/nfr.md, modern/docs/ux/journeys.md#J5, modern/contracts/openapi/sanctions-service.yaml#listSanctions

##### Out of scope
- Setting the status, the audit entry and ProviderStatusChanged inside provider-service (prv-status-change)
- Sending the TRM, SUS or RST notice from SanctionApplied (ntf-send, D-012)
- Removing a suspended or terminated provider from the public directory (dir-projection)
- The console record-sanction screen (ui-console-sanction)
- Any restriction of sanctions by current status (Q-006: none; D-027 preserves the legacy rule)
- Loading sanctions from a SANCIN file (san-intake-run)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-03, R-PRVSAN01-ADD-05, R-PRVSAN01-cbl-00002, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00018, R-PRVSAN01-cbl-00019, R-PRVSAN01-cbl-00020, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00022, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00033
Related rules: R-PRVSAN01-ADD-04, R-PRVSAN01-ADD-07, R-PRVSAN01-cbl-00004, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00006, R-PRVSAN01-cbl-00007, R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, R-PRVSAN01-cbl-00026, R-PRVSAN01-cbl-00028, R-PRVSAN01-cbl-00034, R-PRVSAN01-cbl-00035
Programs: PRVSAN01 PROCESS-SANCTION 89-102 (master lookup by NPI 95-101, not-found message 100), APPLY-SANCTION 104-116 (T 106-108, S 109-110, R 111-112, REWRITE 115), GENERATE-NOTICE 124-138; no JCL job runs PRVSAN01 in this estate
Decisions: D-006 FIX: S sets a new SUSPENDED status (S), no longer DENIED; D-012 REPLACE: the dynamic CALL to missing PRVNT programs becomes the SanctionApplied event consumed by notification-service; D-013 FIX: sanctions-service changes the status only through provider-service's API, never the provider's data; D-018 FIX: a real sanction history table (reason, effective date, who, resulting status); D-023 FIX: role check on every endpoint (POST SUPERVISOR; GET ANALYST, SUPERVISOR, ADMIN); D-024 FIX: an apply failure is surfaced (503) and distinguished from not found; nothing is recorded as applied; D-026 FIX: unknown action codes are rejected with an error; D-027 PRESERVE: T, S and R apply whatever the current status is (Q-006)

##### Technical notes (agent-owned)
Service: sanctions-service. Endpoints/events: POST /api/v1/sanctions, GET /api/v1/sanctions, SanctionApplied. Roles: SUPERVISOR, ANALYST, ADMIN.


#### MAM-55 Run the legacy SANCIN sanction intake as a batch job

Type: Story. Service: sanctions-service. Labels: svc-sanctions-service, kag-MAM-55, agentic, decision-D-004, decision-D-006, decision-D-013, decision-D-018, decision-D-020, decision-D-023, decision-D-024, decision-D-026, decision-D-027. Depends on: MAM-52, MAM-42.

##### Business requirement
Sanction actions also arrive in the old fixed-width sanction file. An administrator or a scheduler can load such a file for a stated business date. Every record is applied like a sanction entered by a supervisor. Records for unknown providers, with unknown actions or with an invalid date are counted and skipped without stopping the run, the number of terminations is reported as before, the run ends with an explicit result that says whether it completed or failed and why, and running the same business date again applies nothing twice.

##### Acceptance criteria
###### AC-1 A sanction intake run starts with an explicit business date
Given An ADMIN token and a SANCIN file of 80-byte records
When POST /api/v1/batch/sanction-intake-runs is called with the file and businessDate 2026-03-31
Then 202 with a BatchRun that has a runId, a job, businessDate 2026-03-31 and status RUNNING or COMPLETED; GET /api/v1/batch/sanction-intake-runs/{runId} returns the same businessDate; a call without businessDate or without the file returns 400 problem+json and creates no run
Basis: R-PRVSAN01-cbl-00011, D-020, modern/contracts/openapi/sanctions-service.yaml#startSanctionIntakeRun

###### AC-2 Each 80-byte record is read from its fixed positions until end of file
Given A SANCIN file whose records hold the NPI in bytes 1-10, the action in byte 11, the reason in bytes 12-51, the effective date as CCYYMMDD in bytes 52-59 and filler in bytes 60-80, for example NPI 1003000111, action T, reason 'SYNTHETIC TEST REASON', date 20260331
When The run completes
Then Every record is read in file order through the last one and counts.read equals the number of records; GET /api/v1/sanctions?npi=1003000111 shows effectiveDate 2026-03-31 (the 4-digit-year date stored as an ISO date) and reason 'SYNTHETIC TEST REASON' (bytes 12-51 without trailing spaces)
Basis: R-PRVSAN01-cbl-00001, R-PRVSAN01-cbl-00011, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00013, R-PRVSAN01-cbl-00056, R-PRVSAN01-cbl-00057, D-004, modern/docs/analysis/data-layouts.md (PRVSAN01:SANCTION-IN-RECORD)

###### AC-3 Each record is applied through provider-service and kept in the history
Given ACTIVE provider 1003000111, ACTIVE provider 1003000333 and suspended provider 1003000222, and a file with T for 1003000111, S for 1003000333 and R for 1003000222
When The run completes
Then The providers show status T, S and A respectively, set only through provider-service's POST /api/v1/providers/{npi}/status-changes; GET /api/v1/sanctions shows one history row per record with sourceFeed SANCIN and recordedBy the account that started the run; one SanctionApplied event is published per record
Basis: R-PRVSAN01-cbl-00017, D-013, D-018, D-006, D-027, modern/docs/analysis/programs/PRVSAN01.md (DD SANCIN)

###### AC-4 Records that cannot be applied are counted, the run goes on and completes
Given A file of five records: a valid T for 1003000111, a T for NPI 1003000999 that no provider has, action X for 1003000333, a valid action for 1003000333 with effective date 20261331 (not a valid date), and a valid T for 1003000222
When The run ends
Then GET /api/v1/batch/sanction-intake-runs/{runId} shows status COMPLETED, endedAt set, error null and counts read 5, applied 2, notFound 1, rejected 2; history rows and SanctionApplied events exist only for the two applied records (1003000111 and 1003000222); provider 1003000333 is unchanged; the service's JSON log contains the line 'PRVSAN01: NPI NOT FOUND 1003000999'; the records after each skipped one are still processed
Basis: R-PRVSAN01-ADD-06, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-01, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, D-024, D-026, Q-B12

###### AC-5 Only terminations are counted as terminated
Given All providers exist, and a file with T for 1003000111, S for 1003000333, R for 1003000222 and T for 1003000555
When The run completes
Then counts.applied is 4 and counts.terminated is 2 (S and R do not count); the service's JSON log contains the line 'PRVSAN01: PROVIDERS TERMINATED=00002' (the legacy 5-digit counter)
Basis: R-PRVSAN01-cbl-00027, R-PRVSAN01-cbl-00054, R-PRVSAN01-cbl-00055, R-PRVSAN01-cbl-00010, R-PRVSAN01-cbl-00058, Q-B8

###### AC-6 An unreadable record or a failure to apply ends the run with a stated failure
Given A file of three valid records whose second record is only 60 bytes long, and in another run a file of three valid records where provider-service answers with a server error for the NPI of record 2 (fault injected)
When Each run is started
Then Each run does not stay RUNNING: status FAILED, endedAt set and an error text naming record 2 (and its NPI in the second case); record 1 of each run keeps its result (history row and event) and counts.applied is 1; record 3 is not processed; the failing sanction is not recorded as applied and has no SanctionApplied event
Basis: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-05, D-024, Q-B14

###### AC-7 Re-running a business date applies nothing twice
Given A run for business date 2026-03-31 of a file with T for 1003000111 and S for 1003000333 completed, and the same file is loaded again for business date 2026-03-31
When The second run completes
Then Status COMPLETED with counts read 2, applied 0, skipped 2; no new history row and no SanctionApplied event is created, so no second notice is triggered; the providers keep status T and S
Basis: modern/docs/architecture/nfr.md (Availability: batch jobs can be re-run for a business date and are idempotent), ADR-004, D-024, D-018, Q-B11

###### AC-8 Only ADMIN and SYSTEM may start a run and only ADMIN may read its result
Given Tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a request without a token
When POST /api/v1/batch/sanction-intake-runs and GET /api/v1/batch/sanction-intake-runs/{runId} are called, and GET with the unknown runId 00000000-0000-0000-0000-000000000000
Then POST returns 202 for ADMIN and SYSTEM, 403 problem+json for the others, 401 without a token; GET returns 200 for ADMIN, 403 for every other role and 401 without a token; the unknown runId returns 404 problem+json
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md, modern/contracts/openapi/sanctions-service.yaml#getSanctionIntakeRun

##### Out of scope
- Applying one sanction: the rules for T, S and R, history and the apply call are in san-record and are reused here
- Any behaviour for batchId: the legacy PRVSAN01 has no batch id; the field exists only because the contract shares one request shape with the intake run
- A cron-scheduled start: no file location is defined for it (Q-B9)
- Run order against the directory and T-MSIS jobs, which the legacy never scheduled (job-flow.md, implicit order)
- The console batch-jobs screen (ui-console-batch)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-ADD-06, R-PRVSAN01-cbl-00001, R-PRVSAN01-cbl-00010, R-PRVSAN01-cbl-00011, R-PRVSAN01-cbl-00012, R-PRVSAN01-cbl-00013, R-PRVSAN01-cbl-00017, R-PRVSAN01-cbl-00027, R-PRVSAN01-cbl-00054, R-PRVSAN01-cbl-00055, R-PRVSAN01-cbl-00056, R-PRVSAN01-cbl-00057, R-PRVSAN01-cbl-00058
Related rules: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-05, R-PRVSAN01-cbl-00003, R-PRVSAN01-cbl-00021, R-PRVSAN01-cbl-00023, R-PRVSAN01-cbl-00024, R-PRVSAN01-cbl-00025, R-PRVSAN01-cbl-00029, R-PRVSAN01-cbl-00033
Programs: PRVSAN01 MAIN-CONTROL 79-87 (loop and final count 85-86, WS-TERM-COUNT PIC 9(05) at 76), PROCESS-SANCTION 89-102, APPLY-SANCTION 104-116 (termination count 107-108); input SANCIN 80 bytes (SANCTION-IN-RECORD); no JCL job runs PRVSAN01 and no JCL defines the SANCIN DD
Decisions: D-004 FIX: the CCYYMMDD effective date is stored as a 4-digit-year ISO date; D-006 FIX: S sets SUSPENDED; D-013 FIX: the status is changed only through provider-service's API; D-018 FIX: every record applied is kept in the sanction history, once; D-020 FIX: the business date is an explicit job parameter; D-023 FIX: role check on the run endpoints (POST ADMIN, SYSTEM; GET ADMIN); D-024 FIX: not-found and rejected records are counted, the run has an explicit result, a failure is surfaced with a reason, a re-run for a business date is safe; D-026 FIX: unknown action codes are rejected and counted; D-027 PRESERVE: each action is applied whatever the current status

##### Technical notes (agent-owned)
Service: sanctions-service. Endpoints/events: POST /api/v1/batch/sanction-intake-runs, GET /api/v1/batch/sanction-intake-runs/{runId}, SanctionApplied. Roles: ADMIN, SYSTEM.


### MAM-9 notification-service

#### MAM-58 Send sanction notices by email (TRM, SUS, RST), reject unknown actions and handle failures

Type: Story. Service: notification-service. Labels: svc-notification-service, kag-MAM-58, agentic, decision-D-012, decision-D-024, decision-D-026, slice-main-journey. Depends on: MAM-31, MAM-24.

##### Business requirement
When a sanction is recorded against a provider (termination, suspension or reinstatement), the provider must be told by email with the matching notice. This replaces the legacy notice programs that were never delivered. A sanction with an action that is not recognised is refused and reported instead of being sent as a general notice. A notice that cannot be sent, or a missing template, must never stop the processing and never undo the status change; it is recorded as failed and made visible. Each notice is sent once even if the triggering event arrives twice, and every sent notice is announced so that it appears in the provider's history.

##### Acceptance criteria
###### AC-1 A termination sends the TRM notice
Given notification-service is running with the seeded templates and Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 with action T, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the TRM template and the TRM body filled in with NPI 1003000111
Basis: R-PRVSAN01-cbl-00044, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00050, R-PRVSAN01-cbl-00051, R-PRVSAN01-cbl-00008, D-012, Q-A7, Q-A8

###### AC-2 A suspension sends the SUS notice
Given Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 with action S, reason 'LEIE exclusion' and effectiveDate 2026-03-31
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the SUS template and the SUS body filled in with NPI 1003000111
Basis: R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00032, D-012, Q-A7, Q-A8, modern/docs/ux/journeys.md#J5

###### AC-3 A reinstatement sends the RST notice
Given Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 with action R, reason 'Exclusion lifted' and effectiveDate 2026-04-30
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the RST template and the RST body filled in with NPI 1003000111
Basis: R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00048, R-PRVSAN01-cbl-00036, R-PRVSAN01-cbl-00037, R-PRVSAN01-cbl-00039 (no legacy code sends it: built new), D-012, Q-A7, Q-A8, modern/docs/ux/journeys.md#J5

###### AC-4 An action outside T, S and R is refused, not sent as a general notice
Given Mailpit is empty
When it consumes a SanctionApplied event for NPI 1003000111 whose action is X (a value the contract does not allow), followed by a valid one with action S for NPI 1003000444
Then no email and no NoticeSent event are produced for the first event, an error log line names its event id and the action X, the event is acknowledged without stopping the consumer, and the SUS email for 1003000444 arrives
Basis: R-PRVSAN01-cbl-00049 (legacy GEN fallback, superseded), D-026 (unknown action codes are rejected; the decision wins, Q-001), D-024, modern/contracts/asyncapi/events.yaml (SanctionApplied.action enum T, S, R), Q-A10

###### AC-5 Every sent notice is announced
Given the TRM notice of AC-1 was sent by email
When the outbox poller publishes the event
Then exactly one NoticeSent event is on topic pem.notification.events with key 1003000111 and data noticeId, npi 1003000111 and template TRM
Basis: R-PRVSAN01-cbl-00052, modern/contracts/asyncapi/events.yaml (NoticeSent), D-012, ADR-003

###### AC-6 A failed send is logged, surfaced and does not stop the processing
Given the provider status of 1003000111 was already changed to S and the mail server (Mailpit) is stopped
When a SanctionApplied event for NPI 1003000111 (action S) and then one for NPI 1003000444 (action T) are consumed
Then for each, a warning log line names the template (SUS, then TRM), the NPI, status FAILED and the cause; no NoticeSent event is published for either; the second event is processed although the first failed; and GET /api/v1/providers/1003000111 still returns enrollStatus S (nothing is rolled back)
Basis: R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00053, D-024, D-012

###### AC-7 A missing template is a named failure, not a crash
Given the TRM template row has been removed and Mailpit is empty
When a SanctionApplied event with action T for NPI 1003000111 is consumed, followed by one with action S for NPI 1003000444
Then for the first, the log contains 'NOTICE TEMPLATE NOT FOUND TRM' (the legacy 'NOTICE PGM NOT FOUND' with the template code) and status FAILED, and no email and no NoticeSent are produced; the SUS email for 1003000444 still arrives
Basis: R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00053, R-PRVSAN01-cbl-00050 (the notice is found by code, not built from a program name), D-012, D-024

###### AC-8 A redelivered event sends one notice
Given the SanctionApplied event with id E6 for NPI 1003000111 (action S) was already processed
When E6 is delivered again
Then Mailpit still holds exactly one SUS email for NPI 1003000111 and only one NoticeSent event exists for it
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7 (idempotent consumers)

##### Out of scope
- Notices for approval, denial, rejection and revalidation: ntf-send-2.
- Listing notices (including the FAILED ones) and templates: ntf-list.
- Notifying the downstream systems Claims and MCO named in the legacy program header (no interface exists in the estate; Q-A7).
- The general notice GEN: seeded and listed, but not sent for any event (Q-A10).
- Resending a FAILED notice: no rule and no decision exist for it.
- Final notice wording, including which sanction details (reason, effective date) appear in the body: placeholder text is seeded as data for the business to replace (Q-A8).
- A contact email on the provider record: not held by the legacy master (Q-A7).

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00009, R-PRVSAN01-cbl-00032, R-PRVSAN01-cbl-00036, R-PRVSAN01-cbl-00037, R-PRVSAN01-cbl-00038, R-PRVSAN01-cbl-00039, R-PRVSAN01-cbl-00044, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048, R-PRVSAN01-cbl-00049, R-PRVSAN01-cbl-00050, R-PRVSAN01-cbl-00051, R-PRVSAN01-cbl-00052, R-PRVSAN01-cbl-00053
Programs: PRVSAN01: source-code/BATCH/PRVSAN01.cbl:124-138 (GENERATE-NOTICE: suffix TRM 126, SUS 127, RST 128, GEN 129, STRING 131-133, dynamic CALL 134, ON EXCEPTION 135-137), 116 (PERFORM GENERATE-NOTICE after the REWRITE). The PRVNTTRM, PRVNTSUS, PRVNTRST and PRVNTGEN programs are missing from the estate (R-PRVSAN01-cbl-00039 NOT_FOUND). No job runs PRVSAN01.
Decisions: D-012 REPLACE: notification-service sends the TRM, SUS and RST notices by email and logs them, instead of the dynamic CALL to missing programs; D-024 FIX: a failed notice is recorded and logged; processing continues; D-026 FIX: an unknown action code is rejected (no GEN notice is sent for it; Q-A10)

##### Technical notes (agent-owned)
Service: notification-service. Endpoints/events: SanctionApplied, NoticeSent. Roles: none.


#### MAM-59 Send notices by email for approvals, denials, rejections and revalidation

Type: Story. Service: notification-service. Labels: svc-notification-service, kag-MAM-59, agentic, decision-D-001, decision-D-012, decision-D-016, decision-D-019, slice-main-journey. Depends on: MAM-58.

##### Business requirement
Providers must also be told by email about decisions on their application: approved, denied, or rejected after the checks, and when their revalidation falls due. The emails can be seen by the person demonstrating the system (in Mailpit) and, through the notice list, by the provider. A provider receives one notice per event, and a change caused by a sanction is not announced a second time.

##### Acceptance criteria
###### AC-1 An approval sends the 'Enrolment approved' notice
Given Mailpit is empty and provider 1003000111 was approved
When notification-service consumes a ProviderStatusChanged event for 1003000111 with from P and to A
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject 'Enrolment approved', and one NoticeSent event with template APPROVED is published
Basis: modern/docs/ux/journeys.md#J1, D-019, Q-A7, Q-A8, modern/contracts/asyncapi/events.yaml (ProviderStatusChanged, NoticeSent)

###### AC-2 A denial sends the DENIED notice
Given Mailpit is empty and provider 1003000444 was denied by an analyst
When notification-service consumes a ProviderStatusChanged event for 1003000444 with from P and to D
Then exactly one email arrives in Mailpit addressed to 1003000444@providers.pem.local with the subject of the DENIED template, and one NoticeSent event with template DENIED is published
Basis: modern/docs/ux/journeys.md#J3 (a notice is sent), Q-A7, Q-A8

###### AC-3 A sanction-driven status change sends no second notice
Given the SUS notice for NPI 1003000111 was already sent from a SanctionApplied event and Mailpit holds only that email
When notification-service consumes the ProviderStatusChanged events A to S, S to A, A to T and A to A for 1003000111
Then no further email arrives in Mailpit and no further NoticeSent event is published
Basis: Q-A8 (only P to A and P to D produce a notice from ProviderStatusChanged), modern/docs/ux/journeys.md#J5, Q-A4 (a revalidation announces A to A)

###### AC-4 A rejected application sends the 'Application rejected' notice
Given Mailpit is empty and the application for NPI 1003000222 was rejected with error E104
When notification-service consumes an ApplicationRejected event for NPI 1003000222 with one error (code E104, field taxonomy)
Then exactly one email arrives in Mailpit addressed to 1003000222@providers.pem.local with the subject 'Application rejected', and one NoticeSent event with template REJECTED is published
Basis: modern/docs/ux/journeys.md#J2, D-001, D-016, Q-A7, Q-A8

###### AC-5 A rejected application without an NPI gets no notice
Given Mailpit is empty
When it consumes an ApplicationRejected event with no NPI (error E101, field npi)
Then no email arrives, no NoticeSent event is published, an information log line names the event id, and the event is acknowledged without error
Basis: Q-A7 (nobody to address without an NPI), modern/contracts/openapi/notification-service.yaml (Notice.npi is required), modern/contracts/asyncapi/events.yaml (ApplicationRejectedData.npi is optional)

###### AC-6 A revalidation due sends the REVALIDATION_DUE notice
Given Mailpit is empty
When it consumes a RevalidationDue event with a caseId, npi 1003000111 and dueDate 2026-03-31
Then exactly one email arrives in Mailpit addressed to 1003000111@providers.pem.local with the subject of the REVALIDATION_DUE template, and one NoticeSent event with template REVALIDATION_DUE is published
Basis: modern/contracts/asyncapi/events.yaml (RevalidationDue consumed by notification-service), modern/contracts/openapi/notification-service.yaml (template REVALIDATION_DUE), modern/docs/ux/screen-inventory.md (portal notices: approval, denial, sanctions, revalidation), Q-A7, Q-A8

###### AC-7 An extract-produced event is consumed without a notice
Given Mailpit is empty
When it consumes an ExtractProduced event (runId, businessDate 2026-03-31, rowCount 3)
Then no email arrives, no NoticeSent event is published and the event is acknowledged without error
Basis: Q-A9 (no NPI and no recipient defined for EXTRACT_READY), modern/contracts/asyncapi/events.yaml (ExtractProduced consumed by notification-service)

###### AC-8 A redelivered approval event sends one notice
Given the ProviderStatusChanged event with id E7 (1003000111, P to A) was already processed and its APPROVED email sent
When E7 is delivered again
Then Mailpit still holds exactly one 'Enrolment approved' email for 1003000111 and only one NoticeSent event with template APPROVED exists for it
Basis: ADR-003, .claude/skills/java-microservices/SKILL.md section 7 (idempotent consumers)

##### Out of scope
- Sanction notices TRM, SUS and RST, unknown sanction actions and the failed-send and missing-template handling: ntf-send (the same handling applies to these notice types).
- Listing notices and templates: ntf-list.
- Sending an EXTRACT_READY notice to an operations mailbox (Q-A9: not built until the business names a recipient).
- Business-approved wording of subjects and bodies other than 'Enrolment approved' and 'Application rejected' (Q-A8).
- Adding a contact email to the application or provider (Q-A7).

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy program sends these notices (the PRVNT* programs are missing; PRVSAN01.cbl:124-138 only covers sanction notices). New behaviour from journeys J1, J2, J3 and the approved event contract.
Decisions: D-001 FIX: a rejected application is rejected once and the applicant is told; D-012 REPLACE: notification-service replaces the missing notice programs; D-016 FIX: the rejection carries the validation error codes; D-019 FIX: the approval by an analyst is what the provider is told about

##### Technical notes (agent-owned)
Service: notification-service. Endpoints/events: ProviderStatusChanged, ApplicationRejected, RevalidationDue, ExtractProduced, NoticeSent. Roles: none.


#### MAM-60 List the notices sent and the notice templates

Type: Story. Service: notification-service. Labels: svc-notification-service, kag-MAM-60, agentic, decision-D-012, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-58, MAM-59.

##### Business requirement
Providers must be able to see the notices that were sent to them, staff must be able to see the notices sent to any provider, and administrators must be able to see the notice templates in use. A provider never sees another provider's notices, and a notice that could not be sent shows as failed.

##### Acceptance criteria
###### AC-1 Staff list the notices of a provider
Given notices exist for NPI 1003000111 (APPROVED, SENT) and for NPI 1003000444 (DENIED, SENT)
When an ANALYST sends GET /api/v1/notices?npi=1003000111
Then the response is 200 with items containing only the notices of 1003000111, each with noticeId, npi, template, channel EMAIL, status and sentAt, plus page 0, size 20 and total
Basis: modern/contracts/openapi/notification-service.yaml (listNotices, Notice, NoticePage), D-012

###### AC-2 Invalid filters are refused
Given the notice log has entries
When an ANALYST sends GET /api/v1/notices?npi=ABC and GET /api/v1/notices?size=500
Then each response is 400 problem+json
Basis: modern/contracts/openapi/notification-service.yaml (npi pattern, size maximum 200, 400 Invalid request)

###### AC-3 A provider sees only their own notices
Given provider1 is a PROVIDER whose token carries the npi claim 1003000111, and notices exist for 1003000111 and 1003000444
When provider1 sends GET /api/v1/notices, then GET /api/v1/notices?npi=1003000444
Then the first response lists only notices of 1003000111; the second is 403
Basis: modern/docs/architecture/nfr.md (a PROVIDER only ever sees their own notices), D-023, modern/contracts/openapi/notification-service.yaml (listNotices: a PROVIDER sees only their own), .claude/commands/phase-5-platform.md (by analogy: a PROVIDER token for another NPI gives 403)

###### AC-4 The provider sees the approval notice
Given the ProviderStatusChanged P to A event for 1003000111 was processed (journey J1)
When provider1 sends GET /api/v1/notices
Then the items include a notice with npi 1003000111, template APPROVED, channel EMAIL and status SENT
Basis: modern/docs/ux/journeys.md#J1, modern/docs/ux/screen-inventory.md (portal notices)

###### AC-5 A failed notice shows as FAILED
Given a notice for NPI 1003000111 could not be sent because the mail server was down
When an ANALYST sends GET /api/v1/notices?npi=1003000111
Then the notice is listed with status FAILED
Basis: D-024, R-PRVSAN01-cbl-00009, modern/contracts/openapi/notification-service.yaml (Notice.status SENT, FAILED)

###### AC-6 Who may list notices
Given notices exist for 1003000111 and 1003000444
When GET /api/v1/notices is called with ANALYST, SUPERVISOR, ADMIN and PROVIDER tokens, with a SYSTEM token and with no token
Then ANALYST, SUPERVISOR and ADMIN get 200 with the notices of both providers; PROVIDER gets 200 with only its own; SYSTEM gets 403; no token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/notification-service.yaml (listNotices x-roles PROVIDER, ANALYST, SUPERVISOR, ADMIN)

###### AC-7 An administrator lists the templates
Given the seeded templates exist
When an ADMIN sends GET /api/v1/notice-templates
Then the response is 200 with an array of the nine templates TRM, SUS, RST, GEN, APPROVED, DENIED, REJECTED, REVALIDATION_DUE and EXTRACT_READY, each with code, subject and body
Basis: modern/contracts/openapi/notification-service.yaml (listNoticeTemplates, NoticeTemplate, Notice.template values), D-012, Q-A8

###### AC-8 Only ADMIN can list templates
Given the seeded templates exist
When GET /api/v1/notice-templates is called with ANALYST, SUPERVISOR, PROVIDER and SYSTEM tokens and with no token
Then ANALYST, SUPERVISOR, PROVIDER and SYSTEM get 403; no token gets 401
Basis: D-023, ADR-007, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/notification-service.yaml (listNoticeTemplates x-roles ADMIN)

##### Out of scope
- Editing templates through the API or the UI: no endpoint exists in the contract.
- Showing the notices in the portal and in the console detail page (ui-portal-notices, ui-console-detail).
- Resending a notice: no rule or decision exists.

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: No legacy program: the notice programs PRVNTTRM, PRVNTSUS, PRVNTRST and PRVNTGEN are missing from the estate (PRVSAN01.cbl:124-138; D-012).
Decisions: D-012 REPLACE: a notice log and templates in notification-service; D-023 FIX: role-based access; a provider sees only their own notices; D-024 FIX: failures are surfaced, not silent

##### Technical notes (agent-owned)
Service: notification-service. Endpoints/events: GET /api/v1/notices, GET /api/v1/notice-templates. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN.


### MAM-10 directory-service

#### MAM-61 Keep the directory of ACTIVE providers up to date from provider events

Type: Story. Service: directory-service. Labels: svc-directory-service, kag-MAM-61, agentic, decision-D-028, decision-D-013, decision-D-014, decision-D-006, decision-D-019, decision-D-027, decision-D-008, slice-main-journey. Depends on: MAM-27, MAM-17, MAM-35, MAM-37, MAM-31.

##### Business requirement
The provider directory must list a provider only while that provider is enrolled and active. The directory keeps its own up-to-date list, built from the changes the provider record reports as providers are registered, given a risk level, approved, suspended, terminated, denied or reinstated, so that searching the directory never depends on the provider record being available. A provider appears in the directory once an analyst has approved it and disappears as soon as it is no longer active. Each provider is shown by a readable name (first and last name for an individual, the legal name for a group or facility) instead of the raw stored name text, which for individuals ran on into the first characters of the licence number.

##### Acceptance criteria
###### AC-1 A pending provider is not published
Given directory-service has consumed ProviderRegistered for the individual provider NPI 1003000111 (Medicaid ID M00000111, type IN, enrollStatus P, screenFlag U, taxonomy 207Q00000X)
When anyone calls GET /api/v1/directory/providers without a token
Then NPI 1003000111 is not among the returned items and the page total does not count it: a provider that is not ACTIVE is not published.
Basis: R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, R-PRVDIR01-ADD-01, Q-008, modern/docs/ux/journeys.md#J1, modern/docs/architecture/overview.md (flows 1 and 6), OQ-D11

###### AC-2 Approval publishes the provider with the directory fields
Given NPI 1003000111 was registered (Medicaid ID M00000111, type IN, taxonomy 207Q00000X), directory-service consumed RiskTierAssigned with tier L for it, and provider-service answers GET /api/v1/providers/1003000111 with last name RIVERA, first name MARIA, licence MD12345 and licence state TX
When directory-service consumes ProviderStatusChanged for NPI 1003000111 with from P and to A (the analyst approval)
Then GET /api/v1/directory/providers returns exactly one item for NPI 1003000111 with medicaidId M00000111, providerType IN (the legacy code), riskTier L, taxonomy 207Q00000X and displayName 'MARIA RIVERA'; provider-service received GET /api/v1/providers/1003000111 authenticated with the SYSTEM service token, and directory-service read no other service's database.
Basis: R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, D-019, D-013, D-008, Q-008, ADR-007, modern/docs/ux/journeys.md#J1, modern/docs/architecture/overview.md (flows 3 and 6), modern/contracts/asyncapi/events.yaml (ProviderStatusChanged, RiskTierAssigned), modern/docs/architecture/nfr.md (GET /api/v1/providers/{npi} allows SYSTEM), OQ-D1

###### AC-3 The display name is composed from the name fields, never raw bytes
Given provider-service holds NPI 1003000111 (type IN) with last name RIVERA, first name MARIA, licence number MD12345 and licence state TX, and NPI 1003000333 (type FA, Medicaid ID M00000333) with legal name SUNSET HOSPICE INC and DBA name SUNSET HOSPICE CARE, and both become ACTIVE
When anyone calls GET /api/v1/directory/providers
Then displayName for 1003000111 is 'MARIA RIVERA' and for 1003000333 is 'SUNSET HOSPICE INC'; neither contains the licence number, the DBA name, padding spaces or more than 60 characters (the legacy name field held the 30-byte last name, the 20-byte first name and the first 10 characters of the licence number, untrimmed).
Basis: D-028, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00023, R-PRVDIR01-cbl-00025, Q-008, modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry (displayName maxLength 60, composed from the name fields), OQ-D1, OQ-D2, modern/docs/ux/journeys.md#J7

###### AC-4 A provider that is no longer ACTIVE is withdrawn
Given NPI 1003000333 (type FA, SUNSET HOSPICE INC) is published as ACTIVE
When directory-service consumes ProviderStatusChanged for it with from A and to S (suspended)
Then GET /api/v1/directory/providers no longer returns NPI 1003000333; the same happens for a change from A to T (terminated), and a provider that was denied (to D), such as NPI 1003000444, is never returned.
Basis: R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00004, R-PRVDIR01-ADD-01, D-006, modern/docs/ux/journeys.md#J5

###### AC-5 A reinstated provider is published again
Given NPI 1003000333 was suspended and is not returned by GET /api/v1/directory/providers
When directory-service consumes ProviderStatusChanged for it with from S and to A (reinstated)
Then GET /api/v1/directory/providers returns NPI 1003000333 again, exactly once, with the same medicaidId, providerType, riskTier, taxonomy and displayName as before the suspension.
Basis: R-PRVDIR01-cbl-00018, D-006, D-027, modern/docs/ux/journeys.md#J5

###### AC-6 Only the enrolment status decides publication
Given NPI 1003000444 (type GR, legal name EXCLUDED CLINIC LLC), whose screening result is FAIL (screen flag F) and for which no RiskTierAssigned was ever received, was set ACTIVE by a reinstatement (ProviderStatusChanged from D to A) whose revalidationDueDate is 2025-01-31, already in the past
When anyone calls GET /api/v1/directory/providers
Then NPI 1003000444 is returned, with riskTier null: a failed screening or an overdue revalidation does not remove an ACTIVE provider from the directory, exactly as the legacy filter (status ACTIVE only).
Basis: R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00018, Q-008, D-027, modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry (riskTier nullable)

###### AC-7 A redelivered event changes nothing
Given directory-service already processed the ProviderStatusChanged event with id 7f9c0e2a-0000-4000-8000-000000000001 (NPI 1003000111, from P to A) and recorded that id as processed
When the same event (same id) is delivered again
Then GET /api/v1/directory/providers still returns exactly one item for NPI 1003000111, with unchanged data, and the page total is unchanged.
Basis: ADR-003, java-microservices skill section 7 (processed_event), modern/docs/architecture/data-model/directory-service.sql (processed_event)

###### AC-8 Searches are answered from the directory's own data
Given NPI 1003000111 is published and provider-service is not reachable
When anyone calls GET /api/v1/directory/providers
Then the response is 200 and still contains NPI 1003000111: the directory reads neither the legacy provider master nor provider-service when it answers a search.
Basis: D-013, D-014, ADR-002, R-PRVDIR01-ADD-05, modern/docs/architecture/overview.md (no service reads another service's database)

##### Out of scope
- Search filters (providerType, taxonomy, name), paging limits and request validation of the public search (dir-search)
- FHIR resources (dir-fhir) and the legacy DIROUT file (dir-extract)
- Addresses, phone numbers and network data: the legacy master does not hold them (Q-008, ADR-006)
- Reading the legacy provider master directly: replaced by events and provider-service's API (D-013; PRVDIR01 read PEMAPP1's master, AP-06)
- Event retry, dead-letter handling and the transactional outbox itself (plt-events)
- Loading providers that existed before the directory service started (dat-seed)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-05, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00023
Related rules: R-PRVDIR01-cbl-00025
Programs: PRVDIR01 (source-code/BATCH/PRVDIR01.cbl): EXTRACT-NEXT 72-81 (ACTIVE filter 78-79), WRITE-DIR-ROW 83-90 (field mapping 84-88, raw name 88), MAIN-CONTROL 62-70 and READ 73 (direct read of PEMAPP1's provider master, replaced by events and provider-service's API, D-013). No JCL job runs PRVDIR01.
Decisions: D-028 FIX: compose the name from the name fields (individual: first + last; organisation: legal name); D-013 FIX: sanctions-service and directory-service call provider-service's API; D-014 FIX: provider-service is the single owner (the directory keeps only a read model, ADR-002); D-006 A new SUSPENDED status (a suspended provider is not published); D-019 An analyst approval step sets ACTIVE after a Pass screening (which publishes the provider); D-027 PRESERVE the legacy rule (sanctions apply whatever the status, so a reinstate can make a denied provider ACTIVE); D-008 PRESERVE: keep IN/GR/FA plus a mapping table

##### Technical notes (agent-owned)
Service: directory-service. Endpoints/events: GET /api/v1/directory/providers, GET /api/v1/providers/{npi}, ProviderRegistered, RiskTierAssigned, ProviderStatusChanged. Roles: SYSTEM, PUBLIC.


#### MAM-63 Public provider directory search

Type: Story. Service: directory-service. Labels: svc-directory-service, kag-MAM-63, agentic, decision-D-028, decision-D-006, decision-D-008, slice-main-journey. Depends on: MAM-61.

##### Business requirement
Anyone, including people who are not signed in, can search the public directory of enrolled providers by name, provider type or specialty code and page through the results. Only active providers are listed. Each entry shows the few facts the legacy directory already held for managed care organisations (Medicaid ID, NPI, provider type, risk level and a readable name) plus the specialty code; no addresses, phone numbers, screening results, sanctions or licence numbers are shown.

##### Acceptance criteria
###### AC-1 Anyone can search without signing in
Given the directory holds ACTIVE entries for NPI 1003000111 (type IN, displayName 'MARIA RIVERA') and NPI 1003000333 (type FA, displayName 'SUNSET HOSPICE INC')
When a caller with no Authorization header calls GET /api/v1/directory/providers
Then the response is 200 application/json with a page object: items holding both entries, page 0, size 20 (the defaults) and total 2.
Basis: ADR-007, ADR-006, modern/docs/architecture/nfr.md (GET /api/v1/directory/providers: PUBLIC), modern/contracts/openapi/directory-service.yaml#searchDirectory (security none; page default 0, size default 20; DirectoryPage), Q-008

###### AC-2 Only ACTIVE providers are listed
Given the directory knows NPI 1003000111 (A active), 1003000333 (S suspended), 1003000444 (D denied) and 1003000555 (P pending)
When anyone calls GET /api/v1/directory/providers with no filters
Then items contain only NPI 1003000111 and total is 1.
Basis: R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, D-006, Q-008, modern/docs/ux/journeys.md#J5, modern/docs/ux/journeys.md#J7

###### AC-3 Search by name finds the composed name
Given the directory holds ACTIVE entries 'MARIA RIVERA' (NPI 1003000111) and 'SUNSET HOSPICE INC' (NPI 1003000333)
When anyone calls GET /api/v1/directory/providers with name=HOSPICE, then with name=rivera, then with name=ZZZZ
Then the first call returns only NPI 1003000333, the second only NPI 1003000111 (matching ignores letter case), and the third returns 200 with no items and total 0 (not an error).
Basis: D-028, R-PRVDIR01-ADD-02, modern/docs/ux/journeys.md#J7, OQ-D3, modern/contracts/openapi/directory-service.yaml#searchDirectory (name: Name contains)

###### AC-4 Filter by provider type
Given ACTIVE entries for NPI 1003000111 (type IN) and NPI 1003000333 (type FA)
When anyone calls GET /api/v1/directory/providers with providerType=IN, then with providerType=FA, and then with providerType=XX
Then the first call returns only NPI 1003000111, the second only NPI 1003000333; the third is rejected with 400 application/problem+json because XX is not IN, GR or FA.
Basis: D-008, modern/contracts/openapi/directory-service.yaml#searchDirectory (providerType enum IN, GR, FA; response 400)

###### AC-5 Filter by taxonomy and combine filters
Given ACTIVE entries for NPI 1003000111 (type IN, taxonomy 207Q00000X) and NPI 1003000333 (type FA, taxonomy 251G00000X)
When anyone calls GET /api/v1/directory/providers with taxonomy=207Q00000X, and then with taxonomy=207Q00000X and providerType=FA
Then the first call returns only NPI 1003000111; the second returns 200 with no items and total 0 because every filter given must match.
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (taxonomy), Q-008, OQ-D3

###### AC-6 Results are paged in a stable order
Given 25 synthetic ACTIVE entries in the directory, NPI 1003001001 to 1003001025
When anyone calls GET /api/v1/directory/providers with page=0 and size=10, then page=2 and size=10, then size=201
Then page 0 returns NPI 1003001001 to 1003001010 in ascending NPI order with page 0, size 10 and total 25; page 2 returns NPI 1003001021 to 1003001025; size=201 (and likewise size=0 or page=-1) is rejected with 400 application/problem+json.
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (page minimum 0; size 1 to 200, default 20; response 400), java-microservices skill section 6 (page, size, items, total), OQ-D3

###### AC-7 Only the published facts are returned
Given the ACTIVE provider NPI 1003000111, whose provider record also holds licence MD12345, screen flag P, a revalidation due date and a sanction history
When anyone calls GET /api/v1/directory/providers with name=RIVERA
Then the item contains only npi, medicaidId, providerType, riskTier, displayName and taxonomy: no licence number, screen flag, status, due date, sanction or address data.
Basis: Q-008, ADR-006, modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry

##### Out of scope
- The public directory web page (ui-public-directory)
- FHIR search (dir-fhir) and the legacy DIROUT file (dir-extract)
- Search by NPI or Medicaid ID on the REST directory: the contract has no such parameter (the FHIR identifier search covers NPI)
- Addresses, phones and networks (Q-008, ADR-006)
- Response-time targets and the security test of every role (nfr-performance, nfr-security)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018
Programs: PRVDIR01: EXTRACT-NEXT 78-79 (ACTIVE filter). The public search has no legacy equivalent: the legacy only produced a flat file for managed care organisations (PRVDIR01 WRITE-DIR-ROW 89). No JCL job runs PRVDIR01.
Decisions: D-028 FIX: compose the name from the name fields (the name searched and shown); D-006 A new SUSPENDED status (suspended providers are not listed); D-008 PRESERVE: keep IN/GR/FA plus a mapping table

##### Technical notes (agent-owned)
Service: directory-service. Endpoints/events: GET /api/v1/directory/providers. Roles: PUBLIC.


#### MAM-65 FHIR R4 Practitioner, Organization and PractitionerRole (read-only, public)

Type: Story. Service: directory-service. Labels: svc-directory-service, kag-MAM-65, agentic, decision-D-028, decision-D-008, decision-D-006. Depends on: MAM-61.

##### Business requirement
Besides the simple directory search, the directory offers its active providers in the standard healthcare data format (FHIR) so that other systems can look them up without signing in. Individual providers are offered as practitioners, groups and facilities as organisations, and each individual's specialty (their taxonomy code) is offered as a practitioner role. Only facts the legacy provider record already holds are offered: identifiers, name, provider type, specialty and licence. Addresses, phone numbers and networks are not offered because the business has not provided them. The data can be read but not changed.

##### Acceptance criteria
###### AC-1 Practitioner search by NPI
Given NPI 1003000111 is an ACTIVE individual provider (type IN)
When anyone, with no token, calls GET /fhir/Practitioner with identifier=http://hl7.org/fhir/sid/us-npi|1003000111
Then the response is 200 application/fhir+json: a Bundle of type searchset with total 1 whose one entry is a Practitioner carrying the NPI identifier 1003000111.
Basis: ADR-006, modern/contracts/openapi/directory-service.yaml#searchPractitioners (identifier: NPI as system|value; FhirBundle searchset), pem-domain skill section 7, OQ-D5

###### AC-2 Practitioner carries only legacy facts
Given the ACTIVE individual NPI 1003000111 (Medicaid ID M00000111) with last name RIVERA, first name MARIA, licence number MD12345 and licence state TX
When the Practitioner is returned by GET /fhir/Practitioner with name=RIVERA
Then it holds the NPI and the Medicaid ID as identifiers, a name with family RIVERA and given MARIA, and the licence MD12345 (state TX) as a qualification, and it holds no address, telecom or network element.
Basis: Q-008, ADR-006, R-PRVDIR01-ADD-02, D-028, OQ-D1, OQ-D5

###### AC-3 Organization search for groups and facilities
Given NPI 1003000333 is an ACTIVE organisation of type FA named SUNSET HOSPICE INC (Medicaid ID M00000333) and NPI 1003000111 is an ACTIVE individual
When anyone calls GET /fhir/Organization with name=HOSPICE, and then with identifier=http://hl7.org/fhir/sid/us-npi|1003000333
Then both calls return a searchset Bundle with total 1 whose entry is an Organization with the NPI identifier 1003000333, the Medicaid ID M00000333, the name 'SUNSET HOSPICE INC' and the legacy provider type code FA; the individual 1003000111 never appears among Organizations.
Basis: ADR-006, Q-008, D-008, D-028, modern/contracts/openapi/directory-service.yaml#searchOrganizations, OQ-D5

###### AC-4 PractitionerRole carries the taxonomy as specialty
Given NPI 1003000111 is ACTIVE (type IN) with taxonomy 207Q00000X and NPI 1003000555 is ACTIVE (type IN) with taxonomy 225100000X
When anyone calls GET /fhir/PractitionerRole with specialty=207Q00000X
Then the Bundle has total 1 whose entry is a PractitionerRole that references the Practitioner for NPI 1003000111 and whose specialty code is 207Q00000X; NPI 1003000555 is not returned.
Basis: R-PRVDIR01-ADD-04, Q-008, ADR-006, modern/contracts/openapi/directory-service.yaml#searchPractitionerRoles (specialty = taxonomy code), OQ-D5

###### AC-5 Only ACTIVE providers appear and reinstatement brings them back
Given NPI 1003000333 (type FA) is suspended (S) and NPI 1003000111 (type IN) is pending (P)
When anyone searches /fhir/Practitioner, /fhir/Organization and /fhir/PractitionerRole for them, and the suspended provider is later reinstated (ProviderStatusChanged from S to A)
Then before the reinstatement every Bundle has total 0 for them (no error); after it, NPI 1003000333 is returned by GET /fhir/Organization and NPI 1003000111, still pending, is not returned by any call.
Basis: R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, D-006, Q-008, modern/docs/ux/journeys.md#J5

###### AC-6 The FHIR API is read-only
Given an ACTIVE Practitioner for NPI 1003000111 exists
When a caller sends POST, PUT or DELETE to /fhir/Practitioner
Then each call is rejected with a 4xx status (never 2xx), and a following GET /fhir/Practitioner with identifier=http://hl7.org/fhir/sid/us-npi|1003000111 returns the unchanged resource.
Basis: ADR-006 (read-only), modern/contracts/openapi/directory-service.yaml (only GET is defined under /fhir)

###### AC-7 Resources are valid FHIR R4
Given ACTIVE providers of type IN (NPI 1003000111), GR (NPI 1003000444) and FA (NPI 1003000333) exist
When the Practitioner, Organization and PractitionerRole searches are called and every returned Bundle is checked with the HAPI FHIR validator against FHIR R4
Then the validator reports no errors for any Bundle or resource.
Basis: pem-domain skill section 7 (validate with the HAPI FHIR validator), ADR-006

###### AC-8 A malformed identifier search is rejected
Given any ACTIVE provider exists
When anyone calls GET /fhir/Practitioner with identifier=http://hl7.org/fhir/sid/us-npi|ABC (not a 10-digit NPI)
Then the response is 400 application/problem+json.
Basis: modern/contracts/openapi/directory-service.yaml#searchPractitioners (response 400), modern/contracts/openapi/directory-service.yaml#/components/schemas/DirectoryEntry (NPI pattern of 10 digits), OQ-D12

##### Out of scope
- FHIR Location resources and any address, phone or network data: not held by the legacy master (Q-008, ADR-006)
- A PractitionerRole link to an employing organisation: the legacy master holds none (OQ-D5)
- Write operations, FHIR paging links and search parameters beyond those in the contract
- The simple directory search (dir-search) and the legacy DIROUT file (dir-extract)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVDIR01-ADD-04
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021
Programs: PRVDIR01: DIR-OUT-RECORD 47-53 and WRITE-DIR-ROW 83-90 (the legacy row has no taxonomy, address, phone or network data, so it cannot feed a PractitionerRole as it is); ACTIVE filter 78-79. FHIR is new (ADR-006). No JCL job runs PRVDIR01.
Decisions: D-028 FIX: compose the name from the name fields (individual: first + last; organisation: legal name); D-008 PRESERVE: keep IN/GR/FA plus a mapping table; D-006 A new SUSPENDED status (suspended providers are not offered)

##### Technical notes (agent-owned)
Service: directory-service. Endpoints/events: GET /fhir/Practitioner, GET /fhir/Organization, GET /fhir/PractitionerRole. Roles: PUBLIC.


#### MAM-67 Directory extract as a batch job in the legacy DIROUT format

Type: Story. Service: directory-service. Labels: svc-directory-service, kag-MAM-67, agentic, decision-D-028, decision-D-020, decision-D-024, decision-D-023, decision-D-013. Depends on: MAM-61, MAM-42.

##### Business requirement
Managed care organisations received a directory file listing every active provider, one fixed-width record per provider. The new system must still produce that file in exactly the legacy layout, so it can be compared byte for byte with the old one, but with a properly composed provider name. An administrator can run the job for a chosen business date and download the file. The run reports how many providers it wrote, and a failed run is visible instead of silently producing nothing.

##### Acceptance criteria
###### AC-1 An administrator runs the extract and sees the result
Given an ADMIN token and a directory holding two ACTIVE entries (NPI 1003000111 and NPI 1003000333)
When the ADMIN calls POST /api/v1/batch/directory-extracts with businessDate 2026-03-31
Then the response is 202 with the finished BatchRun: a runId, job PRVDIR01 (the legacy program id), businessDate 2026-03-31, status COMPLETED, endedAt set and counts.rows 2, equal to the number of records in the file.
Basis: R-PRVDIR01-cbl-00001, R-PRVDIR01-cbl-00010, R-PRVDIR01-cbl-00013, R-PRVDIR01-cbl-00014, R-PRVDIR01-cbl-00008, R-PRVDIR01-cbl-00027, R-PRVDIR01-cbl-00028, R-PRVDIR01-cbl-00009, R-PRVDIR01-cbl-00029, D-024, D-020, ADR-004, modern/contracts/openapi/directory-service.yaml#startDirectoryExtract (202, BatchRun), OQ-D7, OQ-D10

###### AC-2 The business date is an explicit parameter
Given an ADMIN token
When the ADMIN calls POST /api/v1/batch/directory-extracts without businessDate, and then with businessDate 31/03/2026 (not an ISO date)
Then both calls are rejected with 400 application/problem+json and no run is created.
Basis: D-020, ADR-004, ADR-009 (ISO dates in APIs), modern/contracts/openapi/directory-service.yaml#/components/schemas/BatchRunRequest (businessDate required, format date)

###### AC-3 Only ACTIVE entries are written, one record each, in NPI order
Given the directory holds ACTIVE entries for NPI 1003000333 and NPI 1003000111, a suspended entry for NPI 1003000777, a denied one for NPI 1003000444 and a pending one for NPI 1003000555
When a run for 2026-03-31 completes and the ADMIN calls GET /api/v1/batch/directory-extracts/{runId}/file
Then the file has exactly two records, NPI 1003000111 first and NPI 1003000333 second (ascending NPI, the key order in which the legacy read its master), each 100 bytes, with no header, trailer or record for the other providers; when the directory holds no ACTIVE entry, the run is COMPLETED with counts.rows 0 and the file is empty.
Basis: R-PRVDIR01-cbl-00002, R-PRVDIR01-cbl-00003, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00015, R-PRVDIR01-cbl-00016, R-PRVDIR01-cbl-00017, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00007, R-PRVDIR01-cbl-00026, R-PRVDIR01-ADD-01, D-006, D-013, ADR-005, modern/docs/analysis/data-layouts.md (PRVDIR01:DIR-OUT-RECORD), OQ-D4

###### AC-4 The record has the legacy DIROUT layout
Given a completed run whose file holds a record for NPI 1003000111 (Medicaid ID M00000111, type IN, risk tier L)
When the ADMIN downloads the file and reads that record
Then the record is 100 bytes: bytes 1-9 'M00000111', bytes 10-19 '1003000111', bytes 20-21 'IN', byte 22 'L', bytes 23-82 the name (60 bytes) and bytes 83-100 spaces.
Basis: R-PRVDIR01-cbl-00024, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00005, ADR-005, modern/docs/analysis/data-layouts.md (PRVDIR01:DIR-OUT-RECORD, 100 bytes), modern/docs/architecture/field-to-column.csv, OQ-D4

###### AC-5 The name field holds the composed name
Given NPI 1003000111 (last name RIVERA, first name MARIA, licence number MD12345) and NPI 1003000333 (type FA, legal name SUNSET HOSPICE INC, DBA name SUNSET HOSPICE CARE) are in a completed run
When the ADMIN reads bytes 23-82 of each record
Then the name of 1003000111 is 'MARIA RIVERA' followed by spaces to 60 bytes and the name of 1003000333 is 'SUNSET HOSPICE INC' followed by spaces to 60 bytes; no record contains the licence number, the DBA name or the padding of the separate last-name and first-name fields.
Basis: D-028, R-PRVDIR01-ADD-02, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00023, R-PRVDIR01-cbl-00025, modern/docs/architecture/field-to-column.csv (DIR-NAME composed from the name fields), OQ-D2

###### AC-6 Running again for the same date creates a new run
Given a completed run A for business date 2026-03-31 and no change to the directory since
When the ADMIN calls POST /api/v1/batch/directory-extracts again for 2026-03-31
Then the response carries a different runId, and the file of the new run is byte-identical to the file of run A, which can still be downloaded.
Basis: ADR-004 (jobs can be re-run for a date), modern/docs/architecture/nfr.md (batch jobs can be re-run and are idempotent), Q-012, OQ-D6

###### AC-7 A failed run is visible and produces no file
Given the directory cannot store the extract file (for example the database rejects the write)
When the ADMIN calls POST /api/v1/batch/directory-extracts for 2026-03-31
Then the BatchRun in the response has status FAILED, a non-empty error text and endedAt set, and GET /api/v1/batch/directory-extracts/{runId}/file for that run returns 404 (no partial file).
Basis: D-024, R-PRVDIR01-ADD-03, R-PRVDIR01-cbl-00011, R-PRVDIR01-cbl-00012, modern/contracts/openapi/directory-service.yaml#/components/schemas/BatchRun (error: why the run failed), OQ-D10

###### AC-8 Only the right roles can run and download
Given tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a call with no token
When each calls POST /api/v1/batch/directory-extracts and GET /api/v1/batch/directory-extracts/{runId}/file
Then ADMIN and SYSTEM may start a run (202); only ADMIN may download the file (200, SYSTEM gets 403); ANALYST, SUPERVISOR and PROVIDER get 403 on both; no token gets 401.
Basis: modern/docs/architecture/nfr.md (security table), modern/contracts/openapi/directory-service.yaml (x-roles), ADR-007, D-023

##### Out of scope
- Sending the file to the managed care organisations: no delivery mechanism exists in the estate; the file is downloaded by an ADMIN
- A call to read a run's status later: the contract has only the start call and the file download (OQ-D10)
- ACs for the scheduled run (cron in configuration, ADR-004): it is configured, but no call in the contract lists directory runs, so its result cannot be observed (OQ-D10)
- Taxonomy, address, phone or network columns in DIROUT: the legacy record has none and must stay 100 bytes (R-PRVDIR01-ADD-04, Q-008)
- The public search (dir-search) and FHIR (dir-fhir)
- Reading the legacy provider master: the file is built from the directory's own data (D-013)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVDIR01-ADD-03, R-PRVDIR01-cbl-00001, R-PRVDIR01-cbl-00002, R-PRVDIR01-cbl-00003, R-PRVDIR01-cbl-00007, R-PRVDIR01-cbl-00008, R-PRVDIR01-cbl-00009, R-PRVDIR01-cbl-00010, R-PRVDIR01-cbl-00011, R-PRVDIR01-cbl-00012, R-PRVDIR01-cbl-00013, R-PRVDIR01-cbl-00014, R-PRVDIR01-cbl-00015, R-PRVDIR01-cbl-00016, R-PRVDIR01-cbl-00017, R-PRVDIR01-cbl-00024, R-PRVDIR01-cbl-00025, R-PRVDIR01-cbl-00026, R-PRVDIR01-cbl-00027, R-PRVDIR01-cbl-00028, R-PRVDIR01-cbl-00029
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-05, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00005, R-PRVDIR01-cbl-00006, R-PRVDIR01-cbl-00018, R-PRVDIR01-cbl-00019, R-PRVDIR01-cbl-00020, R-PRVDIR01-cbl-00021, R-PRVDIR01-cbl-00022, R-PRVDIR01-cbl-00023
Programs: PRVDIR01: MAIN-CONTROL 62-70 (program id 63, open 64-65, end-of-file flag 66, loop 67, close and count display 68-69), EXTRACT-NEXT 72-81 (read 73, end of file 75-76, ACTIVE filter 78-79), WRITE-DIR-ROW 83-90 (mapping 84-88, write 89, count 90), DIR-OUT-RECORD 47-53. No JCL job runs PRVDIR01 (job-flow.md); the DD DIROUT has no dataset definition.
Decisions: D-028 FIX: compose the name from the name fields (individual: first + last; organisation: legal name); D-020 FIX: the business date is an explicit job parameter; D-024 FIX: explicit error handling, a job result per run, failures surfaced; D-023 FIX: role-based access (Keycloak roles) on every endpoint and screen; D-013 FIX: sanctions-service and directory-service call provider-service's API (here: the file is built from the directory's own read model, never from the provider master)

##### Technical notes (agent-owned)
Service: directory-service. Endpoints/events: POST /api/v1/batch/directory-extracts, GET /api/v1/batch/directory-extracts/{runId}/file. Roles: ADMIN, SYSTEM.


### MAM-11 tmsis-service

#### MAM-71 Monthly T-MSIS provider extract in the legacy 120-byte layout (YYDDD period)

Type: Story. Service: tmsis-service. Labels: svc-tmsis-service, kag-MAM-71, agentic, decision-D-015, decision-D-020, decision-D-021, decision-D-004, decision-D-006, decision-D-014. Depends on: MAM-43, MAM-42, MAM-37.

##### Business requirement
Each month the state reports its enrolled providers to the federal Medicaid statistical data programme (T-MSIS). The monthly provider file must list every active provider, one fixed-width record per provider, in exactly the layout the legacy job produced, so the new file can be compared byte for byte with the old one. Each record carries the Medicaid ID, the NPI, an active status and a reporting period written the legacy way, as a two-digit year followed by the day of the year, taken from the business date of the run. Providers who are pending, denied, suspended or terminated are not reported.

##### Acceptance criteria
###### AC-1 Only ACTIVE providers are reported
Given provider-service holds ACTIVE providers NPI 1003000333 and NPI 1003000111, and non-active providers NPI 1003000444 (D denied), 1003000555 (P pending), 1003000777 (S suspended) and 1003000888 (T terminated)
When an ADMIN runs POST /api/v1/batch/tmsis-extracts for businessDate 2026-03-31 and downloads GET /api/v1/batch/tmsis-extracts/{runId}/file
Then the file has exactly two records, NPI 1003000111 first and NPI 1003000333 second (ascending NPI, the key order in which the legacy read its master), no record for the four non-active providers and no header or trailer record.
Basis: R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00030, R-PRVTMS01-cbl-00031, R-PRVTMS01-cbl-00032, R-PRVTMS01-ADD-04, R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00026, Q-007, D-006, modern/docs/analysis/job-flow.md (PRVMTH01 STEP020), OQ-D4

###### AC-2 The record has the legacy 120-byte layout
Given a completed run whose file holds a record for NPI 1003000111 (Medicaid ID M00000111)
When the ADMIN downloads the file and reads that record
Then the record is 120 bytes: bytes 1-5 'PRV01', bytes 6-14 'M00000111' copied unchanged, bytes 15-24 '1003000111' copied unchanged, byte 25 'A' (also for every other record), bytes 26-30 the period and bytes 31-120 spaces.
Basis: R-PRVTMS01-cbl-00007, R-PRVTMS01-cbl-00008, R-PRVTMS01-cbl-00009, R-PRVTMS01-cbl-00011, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00038, R-PRVTMS01-cbl-00039, R-PRVTMS01-cbl-00040, R-PRVTMS01-cbl-00044, R-PRVTMS01-cbl-00045, R-PRVTMS01-cbl-00046, R-PRVTMS01-cbl-00047, R-PRVTMS01-cbl-00049, R-PRVTMS01-cbl-00050, R-PRVTMS01-cbl-00051, R-PRVTMS01-ADD-05, Q-007, ADR-005, modern/docs/analysis/data-layouts.md (PRVTMS01:WS-TMSIS-DETAIL), OQ-D4

###### AC-3 The period is the business date as an explicit YYDDD field
Given a run for businessDate 2026-03-31 started on another calendar day
When the ADMIN reads bytes 26-30 of every record in the run's file
Then every record holds '26090' (year 26, day of the year 090), the same for the whole run, taken from the business date and not from the system date.
Basis: D-015, D-020, D-021, R-PRVTMS01-ADD-01, R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042, R-PRVTMS01-cbl-00048, R-PRVTMS01-cbl-00002, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00022, Q-007, ADR-009 (YYDDD only inside the legacy T-MSIS extract)

###### AC-4 The period names a day, not a month, and handles edge dates
Given runs for businessDate 2026-01-05, 2026-03-30 and 2028-12-31
When the ADMIN reads the period bytes (26-30) of a record in each file
Then they are '26005' (day number padded to three digits), '26089' (different from the '26090' of 2026-03-31 although the month is the same) and '28366' (2028 is a leap year with 366 days).
Basis: D-015, D-004, R-PRVTMS01-ADD-07, R-PRVTMS01-cbl-00043, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00024, Q-007

###### AC-5 Every ACTIVE provider is read, however many there are
Given provider-service holds 450 synthetic ACTIVE providers (NPI 1003001001 to 1003001450), more than two pages of 200
When a run for 2026-03-31 completes and the ADMIN downloads its file
Then the file has 450 records, each NPI exactly once, in ascending NPI order; no provider is missed or repeated.
Basis: R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00026, R-PRVTMS01-cbl-00027, R-PRVTMS01-cbl-00028, R-PRVTMS01-cbl-00029, R-PRVTMS01-cbl-00025, R-PRVTMS01-cbl-00023, modern/contracts/openapi/provider-service.yaml#searchProviders (page size at most 200)

###### AC-6 Providers are read through provider-service's API
Given tmsis-service owns only its own database and a SYSTEM service account
When a run for 2026-03-31 starts
Then provider-service receives GET /api/v1/providers with status=A, page after page, authenticated with the SYSTEM service token; tmsis-service holds only extract data of its own and never reads the provider database.
Basis: D-014, ADR-002, ADR-007, modern/docs/architecture/overview.md (flow 7), modern/docs/architecture/nfr.md (GET /api/v1/providers allows SYSTEM)

##### Out of scope
- The real CMS T-MSIS provider file format and a reporting month: the legacy extract is kept as it is until the business supplies the CMS specification (Q-007 option B)
- Reporting providers that are not ACTIVE or any status other than A (Q-007, R-PRVTMS01-ADD-04)
- The run result, archive generations and the ExtractProduced event (tms-run-archive)
- The future-enrolment warning (tms-future-enrolment)
- The legacy compile defect of the period field name past column 72: it only concerns the Phase 9 Hercules run (D-021)
- Two-digit years from the year 2100 on: YYDDD is kept only because the legacy file has it (D-015)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVTMS01-ADD-01, R-PRVTMS01-ADD-04, R-PRVTMS01-ADD-05, R-PRVTMS01-ADD-07, R-PRVTMS01-cbl-00002, R-PRVTMS01-cbl-00003, R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00007, R-PRVTMS01-cbl-00008, R-PRVTMS01-cbl-00009, R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00011, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00022, R-PRVTMS01-cbl-00023, R-PRVTMS01-cbl-00024, R-PRVTMS01-cbl-00025, R-PRVTMS01-cbl-00026, R-PRVTMS01-cbl-00027, R-PRVTMS01-cbl-00028, R-PRVTMS01-cbl-00029, R-PRVTMS01-cbl-00030, R-PRVTMS01-cbl-00031, R-PRVTMS01-cbl-00032, R-PRVTMS01-cbl-00038, R-PRVTMS01-cbl-00039, R-PRVTMS01-cbl-00040, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042, R-PRVTMS01-cbl-00043, R-PRVTMS01-cbl-00044, R-PRVTMS01-cbl-00045, R-PRVTMS01-cbl-00046, R-PRVTMS01-cbl-00047, R-PRVTMS01-cbl-00048, R-PRVTMS01-cbl-00049, R-PRVTMS01-cbl-00050, R-PRVTMS01-cbl-00051
Related rules: R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019
Programs: PRVTMS01 (source-code/BATCH/PRVTMS01.cbl): EXTRACT-NEXT 91-100 (read 92-95, ACTIVE filter 97-99), WRITE-TMSIS-ROW 102-117 (field moves 111-114, status A 113, period 114, assemble 115, write 116), MAIN-CONTROL 80-82 (Julian date and its split), WS-TMSIS-DETAIL 67-73 (120-byte layout), WS-JULIAN-WORK 59-65. Job: PRVMTH01 STEP020 (seq 2), DD TMSISOUT = MMPRV.TMSIS.PROVIDER.D&DATE.
Decisions: D-015 PRESERVE in the legacy-format extract; ISO dates internally; D-020 FIX: the business date is an explicit job parameter; D-021 FIX: not applicable to the new system (the field is explicit); the Phase 9 Hercules run must patch the line; D-004 FIX: 4-digit dates everywhere (the two-digit year exists only inside the legacy file); D-006 A new SUSPENDED status (suspended providers are not reported); D-014 FIX: provider-service is the single owner (tmsis-service reads it through its API)

##### Technical notes (agent-owned)
Service: tmsis-service. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file, GET /api/v1/providers. Roles: ADMIN, SYSTEM.


#### MAM-75 T-MSIS extract run: archive every file, report counts, publish ExtractProduced

Type: Story. Service: tmsis-service. Labels: svc-tmsis-service, kag-MAM-75, agentic, decision-D-015, decision-D-020, decision-D-024, decision-D-023. Depends on: MAM-71, MAM-31.

##### Business requirement
The monthly T-MSIS extract is run by an administrator for a chosen business date, or automatically each month. Each run reports how many providers it wrote, keeps a permanent copy of its file as the next archive generation so that earlier months can still be retrieved, and tells the rest of the system that the extract is ready. If a run fails, the failure is visible, nothing is archived and nobody is told the extract is ready.

##### Acceptance criteria
###### AC-1 An administrator starts an extract for a business date
Given an ADMIN token and ACTIVE providers in provider-service
When the ADMIN calls POST /api/v1/batch/tmsis-extracts with businessDate 2026-03-31
Then the response is 202 with a BatchRun carrying a runId, job PRVTMS01 (the legacy program id), businessDate 2026-03-31 and status RUNNING or COMPLETED (the run may finish before the answer is sent), and GET /api/v1/batch/tmsis-extracts then lists that runId.
Basis: R-PRVTMS01-cbl-00016, D-020, ADR-004, modern/contracts/openapi/tmsis-service.yaml#startTmsisExtract (202, BatchRun), modern/contracts/openapi/tmsis-service.yaml#listTmsisExtracts

###### AC-2 The run reports its result and row count
Given a run for 2026-03-31 over 2 ACTIVE providers, and another over none
When each run ends and the ADMIN calls GET /api/v1/batch/tmsis-extracts
Then the list shows each run as COMPLETED with endedAt set; the first has counts.rows 2, equal to the number of records in its file, the second counts.rows 0 with an empty file.
Basis: R-PRVTMS01-cbl-00013, R-PRVTMS01-cbl-00014, R-PRVTMS01-cbl-00015, R-PRVTMS01-cbl-00052, R-PRVTMS01-cbl-00053, R-PRVTMS01-cbl-00054, R-PRVTMS01-cbl-00055, D-024, OQ-D7

###### AC-3 The run uses the business date, not the system date
Given the system clock shows 2026-05-10
When the ADMIN starts a run with businessDate 2026-03-31 and the run completes
Then the BatchRun shows businessDate 2026-03-31 and bytes 26-30 of every record of the run's file hold '26090', not '26130' (the day of the system date).
Basis: D-020, D-015, R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00017, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019, ADR-004

###### AC-4 Every run's file is archived as a new generation
Given an empty archive, then completed runs for 2026-02-28 and 2026-03-31, and then a second run for 2026-03-31 with no data change
When the ADMIN calls GET /api/v1/batch/tmsis-extracts and downloads each run's file
Then the three runs show counts.archiveGeneration 1, 2 and 3; every earlier run's file can still be downloaded byte for byte unchanged after the later runs, and the third file is byte-identical to the second (nothing is overwritten or deleted).
Basis: modern/docs/analysis/job-flow.md (PRVMTH01 STEP030: IEBGENER to MMPRV.TMSIS.ARCHIVE(+1)), modern/docs/architecture/data-model/tmsis-service.sql (extract_archive.generation), modern/contracts/openapi/tmsis-service.yaml#listTmsisExtracts (runs and archive generations), Q-012, OQ-D6, OQ-D7

###### AC-5 ExtractProduced is published when the extract is archived
Given a run for 2026-03-31 that wrote 2 rows
When the run is archived and completes
Then exactly one ExtractProduced event is published on pem.tmsis.events with source tmsis-service, data.runId equal to the run's runId, data.businessDate 2026-03-31, data.periodYyddd '26090' and data.rowCount 2, and it is published only after the run is COMPLETED and archived.
Basis: modern/contracts/asyncapi/events.yaml (ExtractProduced), ADR-003, D-015, modern/docs/architecture/overview.md (flow 7), OQ-D8

###### AC-6 A failed run is visible and leaves nothing behind
Given provider-service answers with an error (or is not reachable) while the run reads the providers
When the ADMIN starts a run for 2026-03-31
Then the list shows the run as FAILED with a non-empty error text and endedAt set; GET /api/v1/batch/tmsis-extracts/{runId}/file returns 404; no archive generation is added and no ExtractProduced event is published (the legacy archived whatever file was there even when the extract step failed).
Basis: D-024, R-PRVTMS01-ADD-06, modern/docs/analysis/job-flow.md (PRVMTH01: STEP030 runs even if STEP020 failed), modern/contracts/openapi/tmsis-service.yaml (BatchRun.error; 404 on the file)

###### AC-7 The monthly schedule uses the Clock's date
Given the monthly schedule is configured and fires with the injected Clock at 2026-04-30 and no explicit business date
When the scheduled run completes and the ADMIN calls GET /api/v1/batch/tmsis-extracts
Then the list shows a COMPLETED run with businessDate 2026-04-30, whose file records hold period '26120'.
Basis: ADR-004 (cron in configuration; business date defaults to the injected Clock), java-microservices skill section 10, D-020, D-015

###### AC-8 Access and input checks
Given tokens for ADMIN, SYSTEM, ANALYST, SUPERVISOR and PROVIDER, and a call with no token
When each calls POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts and GET /api/v1/batch/tmsis-extracts/{runId}/file, and the ADMIN also posts without businessDate
Then ADMIN and SYSTEM may start a run (202); only ADMIN may list runs and download files (SYSTEM gets 403 on both); ANALYST, SUPERVISOR and PROVIDER get 403 everywhere; no token gets 401; a start without businessDate is rejected with 400 application/problem+json.
Basis: modern/docs/architecture/nfr.md (security table), modern/contracts/openapi/tmsis-service.yaml (x-roles), ADR-007, D-023, D-020, modern/contracts/openapi/tmsis-service.yaml#/components/schemas/BatchRunRequest

##### Out of scope
- What the records contain and how they are read from provider-service (tms-extract)
- The future-enrolment warning (tms-future-enrolment)
- The notice that notification-service sends when it consumes ExtractProduced (ntf-send)
- Deleting or aging out old archive generations: nothing is deleted until the business sets a retention policy (Q-012)
- Delivery of the file to CMS: no delivery mechanism exists in the estate
- The console batch-jobs screen (ui-console-batch)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVTMS01-ADD-06, R-PRVTMS01-cbl-00001, R-PRVTMS01-cbl-00013, R-PRVTMS01-cbl-00014, R-PRVTMS01-cbl-00015, R-PRVTMS01-cbl-00016, R-PRVTMS01-cbl-00017, R-PRVTMS01-cbl-00018, R-PRVTMS01-cbl-00019, R-PRVTMS01-cbl-00052, R-PRVTMS01-cbl-00053, R-PRVTMS01-cbl-00054, R-PRVTMS01-cbl-00055
Related rules: R-PRVTMS01-cbl-00010, R-PRVTMS01-cbl-00012, R-PRVTMS01-cbl-00041, R-PRVTMS01-cbl-00042
Programs: PRVTMS01: MAIN-CONTROL 78-89 (program id 79, date 80, end-of-file flag 85, close 87, count display 88), WRITE-TMSIS-ROW 117 (count). Jobs: PRVMTH01 STEP020 (PRVTMS01) and STEP030 (IEBGENER copy of the T-MSIS file to MMPRV.TMSIS.ARCHIVE(+1), which runs even if STEP020 failed).
Decisions: D-015 PRESERVE in the legacy-format extract; ISO dates internally; D-020 FIX: the business date is an explicit job parameter; D-024 FIX: explicit error handling, a job result per run, failures surfaced; D-023 FIX: role-based access (Keycloak roles) on every endpoint and screen

##### Technical notes (agent-owned)
Service: tmsis-service. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file, ExtractProduced. Roles: ADMIN, SYSTEM.


#### MAM-78 Warn about providers whose enrolment year is later than the business year

Type: Story. Service: tmsis-service. Labels: svc-tmsis-service, kag-MAM-78, agentic, decision-D-004, decision-D-020, decision-D-015. Depends on: MAM-71.

##### Business requirement
While preparing the monthly T-MSIS file, the system checks the enrolment start date of each active provider. When the year of that date is later than the year of the run's business date, the date is probably a data-entry mistake, so the system raises a warning that names the provider. The warning never stops the provider from being reported. The check must work for any year: the legacy check compared only two-digit years, so it wrongly warned about every provider enrolled before the year 2000 and could never warn about a date after 2099.

##### Acceptance criteria
###### AC-1 A later enrolment year raises a warning that names the provider
Given provider-service returns NPI 1003000777 as ACTIVE with effectiveDate 2027-01-15
When the ADMIN runs POST /api/v1/batch/tmsis-extracts for businessDate 2026-03-31
Then the run's JSON log holds exactly one line 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000777' and the run in GET /api/v1/batch/tmsis-extracts shows counts.futureEnrolments 1.
Basis: R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00035, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037, D-004, D-020, OQ-D7, OQ-D9

###### AC-2 The flagged provider is still reported unchanged
Given the run of AC-1 for businessDate 2026-03-31
When the ADMIN downloads GET /api/v1/batch/tmsis-extracts/{runId}/file
Then the file still holds the record for NPI 1003000777 with status A and period 26090, counted in counts.rows: the warning excludes nothing and alters nothing.
Basis: R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037, D-015

###### AC-3 Only a year strictly later than the business year is flagged
Given ACTIVE providers NPI 1003000111 effective 2026-03-31, NPI 1003000333 effective 2026-12-31 and NPI 1003000777 effective 2027-01-01
When a run for businessDate 2026-03-31 completes
Then only NPI 1003000777 (year 2027) is flagged and counts.futureEnrolments is 1; NPI 1003000333 is not flagged although its date is later in the same year 2026, and NPI 1003000111 is not flagged.
Basis: R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00035, D-004, OQ-D9

###### AC-4 Enrolments before the year 2000 are not flagged
Given NPI 1003000333 is ACTIVE with effectiveDate 1998-05-01
When a run for businessDate 2026-03-31 completes
Then no warning line names NPI 1003000333 and counts.futureEnrolments does not count it (the legacy two-digit test, 98 greater than 26, flagged it); its record is in the file.
Basis: D-004, R-PRVTMS01-ADD-02, R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00033

###### AC-5 Years after 2099 are flagged
Given NPI 1003000888 is ACTIVE with effectiveDate 2101-01-01
When a run for businessDate 2026-03-31 completes
Then the run's log holds 'PRVTMS01: FUTURE ENROLLMENT? NPI 1003000888' and counts.futureEnrolments counts it (the legacy two-digit test, 01 not greater than 26, could never flag it).
Basis: D-004, R-PRVTMS01-ADD-02, R-PRVTMS01-cbl-00034, R-PRVTMS01-cbl-00035

###### AC-6 Only ACTIVE providers are checked
Given NPI 1003000555 is pending (P) with effectiveDate 2027-01-01
When a run for businessDate 2026-03-31 completes
Then NPI 1003000555 has no record in the file, no warning line and no count in counts.futureEnrolments: only reported (ACTIVE) providers are evaluated.
Basis: R-PRVTMS01-cbl-00004, R-PRVTMS01-ADD-04, Q-007

##### Out of scope
- Excluding the flagged provider from the file or changing its record: the legacy only warns (R-PRVTMS01-cbl-00006)
- A month or day comparison of the enrolment date: the legacy compares years only, and D-004 changes only the year width (OQ-D9)
- An enrolment age in days: the legacy fields WS-ENROLL-JUL and WS-AGE-DAYS are declared but never used (R-PRVTMS01-ADD-03, Q-007)
- The two-digit year taken from positions 3-4 of the date (R-PRVTMS01-cbl-00033, -00034): replaced by 4-digit years (D-004)
- Sending a notice or correcting the date: no notice exists for this warning in the estate
- The file layout, period and archive (tms-extract, tms-run-archive)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVTMS01-ADD-02, R-PRVTMS01-ADD-03, R-PRVTMS01-cbl-00005, R-PRVTMS01-cbl-00006, R-PRVTMS01-cbl-00033, R-PRVTMS01-cbl-00034, R-PRVTMS01-cbl-00035, R-PRVTMS01-cbl-00036, R-PRVTMS01-cbl-00037
Related rules: R-PRVTMS01-cbl-00004, R-PRVTMS01-cbl-00020, R-PRVTMS01-cbl-00021, R-PRVTMS01-cbl-00023
Programs: PRVTMS01: WRITE-TMSIS-ROW 102-110 (enrolment year 104-105, comparison 107, warning 108-109), MAIN-CONTROL 80-82 (current year), WS-JULIAN-WORK 59-65 (WS-ENROLL-JUL and WS-AGE-DAYS never used). Job: PRVMTH01 STEP020.
Decisions: D-004 FIX: 4-digit dates everywhere (the year comparison is correct for every century); D-020 FIX: the business date is an explicit job parameter (its year is the reference year); D-015 PRESERVE in the legacy-format extract; ISO dates internally

##### Technical notes (agent-owned)
Service: tmsis-service. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file. Roles: ADMIN, SYSTEM.


### MAM-12 Web UI portal

#### MAM-62 Web UI shell: sign in with Keycloak, role-based navigation, portal home

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-62, agentic, decision-D-023, decision-D-006, decision-D-019, decision-D-004, decision-D-024, slice-main-journey. Depends on: MAM-34, MAM-26, MAM-54, MAM-37.

##### Business requirement
People use one web application to work with the enrolment system. Everyone signs in with their own account, and after signing in each person sees only the areas their role allows: providers see their own enrolment pages, analysts and supervisors see the working screens for provider checks and approvals, and administrators see the set-up and batch-job screens. Someone who is not allowed to open a page is told so clearly instead of being shown another person's data. A provider's start page lists their applications and summarises their own record, with an obvious way to apply for enrolment.

##### Acceptance criteria
###### AC-1 Sign-in is required and uses the identity service
Given A visitor who is not signed in
When They open /portal, /console/providers or any other /portal or /console route
Then The browser is sent to the sign-in page of the Keycloak realm pem (client pem-web) using the authorization code flow with PKCE (the authorize request carries an S256 code challenge and the web UI never handles a password); after a successful sign-in the visitor lands on the route that was asked for, and every API call carries the access token as a bearer token to the gateway at http://localhost:8080
Basis: ADR-007, ADR-008, .claude/skills/frontend-stack/SKILL.md section 2, modern/infra/keycloak/realm-pem.json (client pem-web, pkce.code.challenge.method S256), D-023, modern/docs/ux/journeys.md#J1 (step 1)

###### AC-2 The menu shows only what the role allows
Given The test users provider1 (PROVIDER), analyst1 (ANALYST), supervisor1 (SUPERVISOR) and admin1 (ADMIN) defined in the Keycloak realm file
When Each user signs in
Then provider1 sees the menu entries My enrolment (/portal), Apply for enrolment (/portal/apply), My provider record (/portal/provider) and My notices (/portal/notices) and no console entry; analyst1 and supervisor1 see Provider search (/console/providers) and Work queues (/console/queues); admin1 sees Provider search, Risk-tier rules (/console/admin/risk-rules) and Batch jobs (/console/admin/jobs)
Basis: modern/docs/ux/screen-inventory.md (roles column), modern/docs/ux/wireframes/index.html, D-023, .claude/commands/phase-5-platform.md (test users), modern/infra/keycloak/realm-pem.json

###### AC-3 A role cannot open the areas of other roles
Given provider1, analyst1 and admin1 are each signed in
When provider1 types /console/providers in the address bar, analyst1 types /portal/apply, and admin1 types /console/queues
Then Each sees an access-denied page (heading 'Access denied', the text 403 and a link to their own start page) and the browser makes no API call for the data of the page it was refused; the same happens for every route outside the user's role in the screen inventory
Basis: D-023, R-PRVCICS1-ADD-05, modern/docs/ux/journeys.md#J4 (step 4), modern/docs/ux/screen-inventory.md, modern/docs/architecture/nfr.md (Security: roles per endpoint)

###### AC-4 Portal home lists my applications and leads to the form
Given provider1 (NPI 1003000111) is signed in and has one application, submitted on 03/01/2026, that is Validated
When /portal opens
Then The page calls GET /api/v1/applications and shows a table with the columns Submitted (03/01/2026), NPI (1003000111 in full), Status ('Validated') and a View link to /portal/applications/:applicationId; a status is always written as text ('Submitted', 'Validated' or 'Rejected'), never by colour alone; a button 'Apply for enrolment' leads to /portal/apply; with no applications the table is replaced by the text 'No applications yet'
Basis: modern/docs/ux/wireframes/portal-home.html, modern/docs/ux/screen-inventory.md (portal home), modern/docs/ux/journeys.md#J1 (step 1), ADR-009, D-004, .claude/skills/frontend-stack/SKILL.md section 6, modern/contracts/openapi/enrollment-service.yaml#listApplications

###### AC-5 Portal home summarises my own provider record
Given provider1's access token carries the claim npi = 1003000111 and that provider is Active (A) with screen flag Pass (P)
When /portal opens
Then The page calls GET /api/v1/providers/1003000111 (the NPI comes from the token, never from something typed) and shows NPI, Medicaid ID, Provider type 'Individual (IN)', Taxonomy, 'Enrolment status' 'Active (A)' and 'Screen flag' 'Pass (P)' with a link to My provider record; when the provider does not exist yet (404) the summary says that no provider record exists yet and the applications table is still shown
Basis: modern/docs/ux/wireframes/portal-home.html, modern/infra/keycloak/realm-pem.json (npi claim), D-006, D-019, D-023, .claude/skills/frontend-stack/SKILL.md section 6, modern/contracts/openapi/provider-service.yaml#getProvider

###### AC-6 Sign-out and ended sessions
Given provider1 is signed in
When They choose Sign out, or the API answers a call with 401 because the token expired
Then After Sign out the Keycloak session is ended and the browser shows a signed-out page, and opening /portal asks for sign-in again; after a 401 the user is sent to sign in again and no data of the earlier page stays on the screen, instead of an empty or half-filled page
Basis: ADR-007, modern/infra/keycloak/realm-pem.json (post.logout.redirect.uris), D-024, modern/contracts/openapi/provider-service.yaml (401 Not authenticated), .claude/skills/frontend-stack/SKILL.md section 2

###### AC-7 The shell and portal home have zero axe violations
Given The Playwright test visits the portal home (with applications, without applications), the signed-out page and the access-denied page
When It runs an axe scan on each page and walks the menu with the keyboard only
Then Every scan reports zero violations; each page has one h1 and the landmarks header, nav and main; a 'Skip to main content' link is the first focus stop; menu entries, View links and 'Apply for enrolment' can be reached and used with Tab and Enter and the focus is always visible
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-home.html, modern/docs/ux/journeys.md (every journey runs an axe scan)

##### Out of scope
- Creating or managing user accounts and passwords (Keycloak administration, plt-keycloak)
- The contents of the other portal and console pages (their own stories)
- A production identity provider
- Self-service account pages, password reset and multi-factor sign-in
- Showing or listing another provider's data to a provider, which is never allowed

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVCICS1-ADD-05
Programs: PRVCICS1.cbl:28-61 (MAIN-LOGIC: no authorisation check, any user of PINQ reads any provider; R-PRVCICS1-ADD-05, D-023). No job step; the CICS transaction PINQ has no sign-on in the estate.
Decisions: D-023 FIX (accepted, Q-010): role-based access (Keycloak roles) on every endpoint and screen; D-006 A new SUSPENDED status with its own label; D-019 Analyst approval sets ACTIVE; the provider's status is shown after approval; D-004 FIX: 4-digit years in every date, shown as MM/DD/YYYY; D-024 FIX: an ended session is surfaced, never shown as an empty page

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/applications, GET /api/v1/providers/{npi}. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN.


#### MAM-64 Portal: apply for enrolment online, with field errors E101 to E104

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-64, agentic, decision-D-001, decision-D-016, decision-D-008, decision-D-024, slice-main-journey. Depends on: MAM-62, MAM-46.

##### Business requirement
A provider applies for Medicaid enrolment by filling in one online form: the provider's identification number, the state-issued Medicaid ID, the provider type (individual, group or facility), the specialty (taxonomy) code, and either the person's name and licence or the organisation's legal name, trade name and owner. When something is missing or wrong, the form says exactly what and where, once per problem and with the same error codes as the old system, and nothing is registered. A good application is accepted and the provider can follow its progress.

##### Acceptance criteria
###### AC-1 The form shows every field with a visible label
Given provider1 is signed in
When /portal/apply opens
Then The form has labelled inputs NPI (10 digits), Medicaid ID (at most 9 characters), Provider type (a list that starts with 'Choose...' and offers exactly Individual (IN), Group (GR) and Facility (FA)), Taxonomy code (10 characters) and, for Individual, Last name (30), First name (20), Licence number (15) and Licence state (2); choosing Group or Facility replaces the individual inputs by Legal name, DBA name and Owner NPI; a 'Submit application' button ends the form
Basis: modern/docs/ux/wireframes/portal-apply.html, modern/contracts/openapi/enrollment-service.yaml#ApplicationRequest, modern/contracts/openapi/enrollment-service.yaml#Individual, modern/contracts/openapi/enrollment-service.yaml#Organization, D-008, .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/ux/screen-inventory.md (portal apply)

###### AC-2 A valid application is accepted (journey J1)
Given provider1 is signed in and no application exists for NPI 1003000111
When They enter NPI 1003000111, Medicaid ID TX0000111, type Individual (IN), taxonomy 207Q00000X, last name RIVERA, first name MARIA, licence number MD12345 and licence state TX and choose Submit application
Then The page sends POST /api/v1/applications with providerType IN and the individual details, receives 201 with status VALIDATED and opens /portal/applications/:applicationId, which shows 'Validated'
Basis: modern/docs/ux/journeys.md#J1 (steps 2 and 3), modern/contracts/openapi/enrollment-service.yaml#submitApplication, aws-transform/test-data/providers.txt (1003000111 RIVERA MARIA MD, MD12345, TX), D-001, modern/docs/ux/wireframes/portal-home.html (Medicaid ID TX0000111), QE-11

###### AC-3 An empty taxonomy gives one error E104 beside the field and in the summary (journey J2)
Given provider1 fills the form for NPI 1003000111 with every field except the taxonomy code, which stays empty
When They choose Submit application
Then The request is sent with the empty taxonomy (the browser does not block the submit for a missing NPI, type or taxonomy, so that the service stores the rejection, journey J2 steps 3 and 4); the API answers 422 with exactly one error, E104 on taxonomy; the page stays on /portal/apply, shows an error summary at the top ('1 error in the application') with a link 'E104: ...' that moves the focus to the Taxonomy code field, shows the same code and message next to that field, keeps everything that was typed, and shows no success page
Basis: modern/docs/ux/journeys.md#J2 (steps 1 and 2), D-001, D-016, R-PRVENR01-cbl-00005, .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/ux/wireframes/portal-apply.html, modern/contracts/openapi/enrollment-service.yaml#FieldError

###### AC-4 E101, E102 and E103 appear at their own field, once per failed check
Given provider1 submits, in turn, (a) a blank NPI, (b) no provider type chosen, (c) NPI 1003000111 for which an application is already VALIDATED, and (d) no provider type and an empty taxonomy together
When The API answers 422 for each submission
Then (a) E101 is shown next to NPI, (b) E102 next to Provider type, (c) E103 next to NPI, and (d) two errors, E102 next to Provider type and E104 next to Taxonomy code, each listed once in the summary and once at its field, with the summary heading '2 errors in the application'; every code is shown in full (E101, not E1) followed by the message returned by the API
Basis: D-001, D-016, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, modern/docs/ux/wireframes/portal-apply.html, modern/contracts/openapi/enrollment-service.yaml#submitApplication

###### AC-5 A rejected application can be found afterwards and nothing is registered
Given The submission of AC-3 stored an application with status REJECTED
When provider1 chooses the link 'My applications' that the page offers after the errors, or opens /portal, and then opens My provider record
Then The portal home lists that application with status 'Rejected' and a View link to /portal/applications/:applicationId; opening it shows the rejected application (ui-portal-status); My provider record (GET /api/v1/providers/1003000111 answers 404) says that no record exists yet
Basis: QE-1, modern/docs/ux/journeys.md#J2 (steps 2 and 3), modern/docs/ux/screen-inventory.md (portal home calls GET /api/v1/applications), D-001

###### AC-6 Other failures are shown, never swallowed
Given provider1 has filled a complete form
When The API answers 400, 401, 403 or 5xx, or the network call fails
Then The page shows an error summary 'The application could not be submitted' with a reason that matches the failure (401: sign in again; 403: you are not allowed to apply; 400 and 5xx: try again, with the detail of a 400), keeps what was typed, shows no success page and no partial status, and lets the user submit again
Basis: D-024, modern/contracts/openapi/enrollment-service.yaml#submitApplication (400, 401, 403 responses), .claude/skills/frontend-stack/SKILL.md section 5

###### AC-7 The form has zero axe violations in every state
Given The Playwright test opens the empty form, the form after the one-error rejection of AC-3 and the form after the two-error rejection of AC-4
When It runs an axe scan in each state and completes and submits the form with the keyboard only
Then Every scan reports zero violations; every input has a visible label; after a failed submit the focus moves to the error summary; each error is written as code plus text next to its field, never by colour alone; the whole form can be completed and sent with Tab, Space and Enter
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J2 (step 2)

##### Out of scope
- Validating the application (enrollment-service, enr-submit); the page only shows the answer
- Checks on names, licence or owner at apply time (the legacy checks only NPI, type and taxonomy; screening checks the rest)
- The application status page (ui-portal-status) and the portal home table (ui-shell-login)
- Changing or withdrawing an application after it is sent (no such function exists)
- Loading applications from the legacy APPINPUT file (enr-intake-run, console batch page)
- The 'Application rejected' notice (ntf-send-2; shown by ui-portal-notices)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-ADD-04, R-PRVENR01-ADD-05, R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005
Programs: PRVENR01.cbl:127-148 (VALIDATE-APPLICATION: NPI, type and taxonomy checks), 155 (INVALID-APP, no exit: fall-through, D-001), 173-179 (WRITE-ERROR-RECORD); job PRVDAY01.jcl STEP010 (PRVENR01).
Decisions: D-001 FIX: every application is processed and each invalid one is rejected with exactly one error per failed check; a rejected application is never registered; D-016 FIX: error codes E101-E205 are real validation and screening results; D-008 PRESERVE: provider types IN, GR, FA (feed codes are mapped by the service); D-024 FIX: errors are handled explicitly and surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: POST /api/v1/applications. Roles: PROVIDER.


#### MAM-66 Portal: see my application status

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-66, agentic, decision-D-001, decision-D-016, decision-D-023, decision-D-024, decision-D-004, slice-main-journey. Depends on: MAM-64, MAM-54.

##### Business requirement
A provider can open any of their applications to see what happened to it: when it was sent, whether it was accepted or rejected and, for a rejected one, every reason it was rejected, each with its error code. A provider never sees another provider's application.

##### Acceptance criteria
###### AC-1 A validated application shows its status and details (journey J1)
Given provider1's application for NPI 1003000111 was accepted (status VALIDATED)
When /portal/applications/:applicationId opens
Then The page calls GET /api/v1/applications/{applicationId} and shows the application id, Status 'Validated', the submitted date as MM/DD/YYYY, NPI 1003000111 in full, Medicaid ID, Provider type 'Individual (IN)', Taxonomy 207Q00000X, the individual details and the text 'No validation errors'
Basis: modern/docs/ux/journeys.md#J1 (step 3), modern/docs/ux/wireframes/portal-application-status.html, modern/contracts/openapi/enrollment-service.yaml#getApplication, ADR-009, D-004, .claude/skills/frontend-stack/SKILL.md section 6

###### AC-2 A rejected application shows exactly one error row (journey J2)
Given provider1's application was rejected because the taxonomy was empty
When The status page of that application opens
Then Status shows 'Rejected' and a table 'Validation errors' with the columns Code, Field and Message holds exactly one row: E104, taxonomy and the message returned by the API (for example 'Taxonomy code is required')
Basis: modern/docs/ux/journeys.md#J2 (step 3), modern/docs/ux/wireframes/portal-application-status.html, D-001, D-016, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00045

###### AC-3 Several failed checks give several rows, each once
Given A second application of provider1 was rejected for provider type XX and an empty taxonomy
When Its status page opens
Then The table lists exactly two rows, E102 on providerType and E104 on taxonomy, each once, in the order returned by the API; no error is repeated and no error is missing
Basis: D-001, D-016, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00044, modern/contracts/openapi/enrollment-service.yaml#Application (exactly one error per failed check)

###### AC-4 Someone else's or an unknown application is never shown
Given provider1 is signed in; another user's application has a known id; no application has the id 00000000-0000-0000-0000-000000000000
When provider1 opens /portal/applications/ with the other user's id, and then with the unknown id
Then For the other user's id the API answers 403 and the page shows an access-denied message with none of that application's data (no NPI, no errors); for the unknown id the API answers 404 and the page shows 'Application not found' with a link to My enrolment
Basis: modern/docs/ux/journeys.md#J4 (step 4), modern/docs/architecture/nfr.md (a PROVIDER only sees their own data), .claude/skills/frontend-stack/SKILL.md section 6, D-023, modern/contracts/openapi/enrollment-service.yaml#getApplication (403, 404)

###### AC-5 Loading failures are shown, not an empty page
Given provider1 opens a status page
When The API answers 401 or 5xx or the network call fails
Then The page shows a message that matches the failure (sign in again, or 'The application could not be loaded. Try again.') and no status, so a failed load is never mistaken for 'Validated' or 'Rejected'
Basis: D-024, modern/contracts/openapi/enrollment-service.yaml#getApplication (401)

###### AC-6 The status page has zero axe violations
Given The Playwright test opens a validated application and a rejected application with two errors
When It runs an axe scan on each page
Then Both scans report zero violations; the page has one h1 and the landmarks header, nav and main; the error table has header cells; the status is text, not colour alone; the page can be used with the keyboard only
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-application-status.html

##### Out of scope
- Reading the status of other users' applications (console work queues show rejected applications to analysts)
- Notices about the application (ui-portal-notices)
- The provider's enrolment status after registration or approval (ui-portal-record)
- Withdrawing or editing an application (no such function exists)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-cbl-00003, R-PRVENR01-cbl-00004, R-PRVENR01-cbl-00005, R-PRVENR01-cbl-00006, R-PRVENR01-cbl-00043, R-PRVENR01-cbl-00044, R-PRVENR01-cbl-00045
Programs: PRVENR01.cbl:127-148 (VALIDATE-APPLICATION), 155 (INVALID-APP), 173-179 (WRITE-ERROR-RECORD: one APPERROR row per rejected application after D-001); job PRVDAY01.jcl STEP010 (PRVENR01, APPERROR file).
Decisions: D-001 FIX: exactly one error per failed check; a rejected application is kept for review and never registered; D-016 FIX: error codes E101-E205 shown as stable identifiers; D-023 FIX: a provider sees only their own applications; D-024 FIX: failures are surfaced; D-004 FIX: dates shown with 4-digit years as MM/DD/YYYY

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/applications/{applicationId}. Roles: PROVIDER.


#### MAM-68 Portal: see my provider record

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-68, agentic, decision-D-006, decision-D-007, decision-D-002, decision-D-003, decision-D-004, decision-D-023, slice-main-journey. Depends on: MAM-62, MAM-37.

##### Business requirement
A provider can see their own provider record: their identifiers, type, specialty, enrolment status (including pending, active, terminated, denied and suspended), screening result, risk level, effective date and next revalidation date, plus either their personal details and licence or their organisation's details. Every code is shown with its meaning, dates are shown with four-digit years, and a provider can never see someone else's record.

##### Acceptance criteria
###### AC-1 The individual record is shown in full (journey J1)
Given provider1's provider is Active, screen flag Pass, risk tier Limited, effective 2026-03-02, revalidation due 2031-03-02, last name RIVERA, first name MARIA, licence MD12345, state TX
When /portal/provider opens
Then The page calls GET /api/v1/providers/1003000111 and shows, as a table with header cells: NPI 1003000111; Medicaid ID TX0000111; Provider type Individual (IN); Taxonomy 207Q00000X; Enrolment status Active (A); Screen flag Pass (P); Risk tier Limited (L); Effective date 03/02/2026; Revalidation due 03/02/2031; Last name RIVERA; First name MARIA; Licence number MD12345; Licence state TX
Basis: modern/docs/ux/wireframes/portal-provider-record.html, modern/docs/ux/journeys.md#J1 (step 7), .claude/skills/frontend-stack/SKILL.md section 6, ADR-009, D-002, D-003, D-004

###### AC-2 A group or facility shows its organisation details
Given A PROVIDER user whose provider is a group (GR) or facility (FA) with a legal name, a DBA name and an owner NPI
When /portal/provider opens
Then Provider type reads 'Group (GR)' or 'Facility (FA)' and the table shows Legal name, DBA name and Owner NPI (10 digits, in full) instead of last name, first name, licence number and licence state
Basis: modern/docs/ux/screen-inventory.md (CICS field table: organisation view), D-007, modern/contracts/openapi/provider-service.yaml#Organization, D-008

###### AC-3 Every status, screen flag and risk tier has its own wording
Given Providers with enrolment status P, A, T, D and S, screen flag U, P and F, risk tier L, M, H and no tier yet, and no revalidation date yet
When Their records are shown on /portal/provider
Then The status reads Pending (P), Active (A), Terminated (T), Denied (D) and Suspended (S), so Suspended is never shown as Denied; the screen flag reads Unscreened (U), Pass (P) or Fail (F); the tier reads Limited (L), Moderate (M) or High (H), or 'Not assigned' when the API gives none; a missing revalidation date reads 'Not set'
Basis: D-006, R-PRVCICS1-ADD-06, .claude/skills/frontend-stack/SKILL.md section 6, modern/docs/ux/screen-inventory.md (CICS field table), modern/contracts/openapi/provider-service.yaml#Provider (riskTier and revalidationDueDate nullable)

###### AC-4 Only my own record is ever requested (journey J4)
Given provider1 is signed in with the claim npi = 1003000111
When /portal/provider opens, and, in a second check, the API answers 403
Then The only provider request the page makes is GET /api/v1/providers/1003000111, the page has no NPI input and no NPI in its address, and after a 403 it shows an access-denied message with no provider data
Basis: modern/docs/ux/journeys.md#J4 (step 4), modern/docs/architecture/nfr.md (a PROVIDER only sees their own data), D-023, modern/infra/keycloak/realm-pem.json (npi claim), .claude/skills/frontend-stack/SKILL.md section 6

###### AC-5 No record yet is explained
Given provider1 has only a rejected application, or an accepted one that has not been registered yet
When /portal/provider opens and the API answers 404
Then The page says that there is no provider record yet, that a record is created when an application is accepted, and links to My enrolment and to Apply for enrolment; it shows no table and no error code
Basis: modern/contracts/openapi/provider-service.yaml#getProvider (404), D-001, modern/docs/ux/journeys.md#J2 (step 2: nothing is registered)

###### AC-6 Dates keep four-digit years
Given A provider whose revalidation due date is 2070-03-02 and one whose effective date is 2026-03-02
When Their records are shown
Then The dates read 03/02/2070 and 03/02/2026; no date is shown with a two-digit year and none is shifted by a century
Basis: D-004, ADR-009

###### AC-7 The record page has zero axe violations
Given The Playwright test opens an individual record, an organisation record and the no-record page
When It runs an axe scan on each and reads the page with the keyboard only
Then Every scan reports zero violations; the page has one h1 and landmarks; the record table has header cells; status is text plus code, never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-provider-record.html

##### Out of scope
- Editing the record (no function exists; changes go through applications, approval and sanctions)
- Another provider's record, which is never shown
- Screening results per source, sanctions and audit history (console only)
- Notices (ui-portal-notices)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVCICS1-ADD-06
Programs: PRVCICS1.cbl:63-73 (FORMAT-PROVIDER status labels A, T, P, otherwise DENIED); COPYBOOKS/PRVRECRD.cpy:9-44 (record fields, 130 bytes, D-007); PRVRSK01.cbl:126-137 (SET-REVALIDATION-DATE, two-digit year pivot at :131, D-004). No job step.
Decisions: D-006 A new SUSPENDED status with its own label; D-007 The record is 130 bytes, so the owner NPI is kept and shown; D-002 FIX: the risk tier is saved and therefore shown; D-003 FIX: due date set at approval and at each completed revalidation, shown here; D-004 FIX: 4-digit years everywhere; D-023 FIX: a provider sees only their own record

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers/{npi}. Roles: PROVIDER.


#### MAM-70 Portal: see my notices

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-70, agentic, decision-D-012, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-62, MAM-60.

##### Business requirement
A provider can see every notice the programme has sent to them, such as approval, denial, rejection, suspension, reinstatement, termination and revalidation notices, with the date, a plain name for the notice, how it was sent and whether it was delivered. A provider sees only their own notices.

##### Acceptance criteria
###### AC-1 The approval notice is listed (journey J1)
Given provider1 was approved and the notice with template APPROVED was sent on 03/05/2026 by email
When /portal/notices opens
Then The page calls GET /api/v1/notices (with no npi parameter) and shows a table with the columns Sent, Notice, Channel and Status holding the row 03/05/2026, 'Enrolment approved (APPROVED)', 'Email', 'Sent'
Basis: modern/docs/ux/journeys.md#J1 (step 7), modern/docs/ux/wireframes/portal-notices.html, modern/contracts/openapi/notification-service.yaml#Notice, D-012, QE-2

###### AC-2 The rejection notice is listed (journey J2)
Given provider1's application was rejected and the notice with template REJECTED was sent
When /portal/notices opens
Then The table shows a row whose Notice reads 'Application rejected (REJECTED)'
Basis: modern/docs/ux/journeys.md#J2 (step 4), modern/contracts/openapi/notification-service.yaml#Notice, D-012, QE-2

###### AC-3 Every other template has a plain name and its code
Given Notices with the templates DENIED, TRM, SUS, RST, GEN and REVALIDATION_DUE, and one with a template code the page does not know
When /portal/notices opens
Then The Notice column reads 'Enrolment denied (DENIED)', 'Termination notice (TRM)', 'Suspension notice (SUS)', 'Reinstatement notice (RST)', 'General notice (GEN)' and 'Revalidation due (REVALIDATION_DUE)', and for the unknown template just the code itself
Basis: QE-2, modern/contracts/openapi/notification-service.yaml#Notice (template enum), D-012, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048, .claude/skills/frontend-stack/SKILL.md section 6, modern/docs/ux/journeys.md#J5 (notices SUS and RST)

###### AC-4 Delivery state is written as text
Given One notice with status SENT and one with status FAILED
When /portal/notices opens
Then The Status column reads 'Sent' and 'Failed' as words, never by colour alone, so a notice that was not delivered is visible to the provider
Basis: modern/contracts/openapi/notification-service.yaml#Notice (status SENT, FAILED), D-024, .claude/skills/frontend-stack/SKILL.md section 5

###### AC-5 Only my own notices are shown
Given Notices exist for NPI 1003000111 (provider1) and for NPI 1003000222
When provider1 opens /portal/notices
Then Only the notices of 1003000111 are shown and the page has no input or address parameter that could ask for another NPI
Basis: modern/docs/architecture/nfr.md (a PROVIDER only sees their own notices), modern/docs/ux/journeys.md#J4 (step 4), D-023, .claude/skills/frontend-stack/SKILL.md section 6, modern/contracts/openapi/notification-service.yaml#listNotices

###### AC-6 Empty, long and failed lists are handled
Given provider1 with no notices, then with more than 20 notices, then an API failure
When /portal/notices opens each time
Then With none the page says 'No notices yet'; with more than 20 it shows paging controls (page 0, size 20 by default, total shown); on a 401 or 5xx or a network failure it shows 'The notices could not be loaded. Try again.' and never the text 'No notices yet'
Basis: modern/contracts/openapi/notification-service.yaml#listNotices (page, size), D-024

###### AC-7 The notices page has zero axe violations
Given The Playwright test opens the page with notices, with none and with the load failure
When It runs an axe scan on each state
Then Every scan reports zero violations; the table has header cells; the page has one h1 and landmarks; paging controls work with the keyboard only
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/portal-notices.html

##### Out of scope
- Reading the full text of a notice (the Notice contract carries no subject or body)
- Sending notices (notification-service, ntf-send, ntf-send-2) and viewing the email itself (Mailpit)
- Notices of other providers, which are never shown
- The wireframe row 'Application received', which has no template in the notice contract
- GEN for an unknown sanction action: D-026 rejects unknown actions, so no GEN notice is produced that way (ntf-send AC-4); the label exists only because GEN is in the template enum

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSAN01-cbl-00008, R-PRVSAN01-cbl-00045, R-PRVSAN01-cbl-00046, R-PRVSAN01-cbl-00047, R-PRVSAN01-cbl-00048
Programs: PRVSAN01.cbl:124-138 (GENERATE-NOTICE: dynamic CALL of PRVNTTRM, PRVNTSUS, PRVNTRST or PRVNTGEN at :134; the programs are absent from the estate, AP-05, D-012). No job step.
Decisions: D-012 REPLACE: notification-service sends the TRM, SUS, RST and GEN notices and the new APPROVED, DENIED, REJECTED and REVALIDATION_DUE notices; D-023 FIX: a provider sees only their own notices; D-024 FIX: failures are surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/notices. Roles: PROVIDER.


#### MAM-73 Public directory page (no sign-in)

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-73, agentic, decision-D-028, decision-D-006, decision-D-008, decision-D-024, slice-main-journey. Depends on: MAM-63, MAM-34.

##### Business requirement
Anyone, without signing in, can look up Medicaid providers who are currently enrolled and active. A visitor searches by name and optionally by provider type and sees each provider's name, identification number, type and specialty. Providers who are pending, denied, terminated or suspended never appear, and names are shown as proper display names.

##### Acceptance criteria
###### AC-1 The page opens without signing in
Given A visitor who is not signed in
When They open /directory
Then The page loads without any redirect to Keycloak, shows the h1 'Find a Medicaid provider', an input 'Name', a list 'Provider type' (Any, Individual (IN), Group (GR), Facility (FA)) and a Search button, and none of its requests carries an Authorization header
Basis: modern/docs/ux/screen-inventory.md (public directory, PUBLIC), modern/docs/ux/wireframes/public-directory.html, ADR-006, ADR-007, modern/contracts/openapi/directory-service.yaml#searchDirectory (x-roles PUBLIC)

###### AC-2 Search by name (journey J7)
Given Active providers MARIA RIVERA (NPI 1003000111, Individual, taxonomy 207Q00000X) and SUNSET HOSPICE INC (NPI 1003000333, Facility, taxonomy 251G00000X)
When A visitor types 'HOSPICE' and chooses Search, and then types 'RIVERA' and chooses Search
Then The page calls GET /api/v1/directory/providers?name=HOSPICE and the table 'Active providers' (columns Name, NPI, Type, Specialty (taxonomy)) shows the row SUNSET HOSPICE INC | 1003000333 | Facility (FA) | 251G00000X; the second search calls GET /api/v1/directory/providers?name=RIVERA and shows MARIA RIVERA | 1003000111 | Individual (IN) | 207Q00000X
Basis: modern/docs/ux/journeys.md#J7 (step 3), modern/docs/ux/wireframes/public-directory.html, modern/contracts/openapi/directory-service.yaml#DirectoryEntry, D-028, Q-008

###### AC-3 Filter by provider type
Given Active providers of all three types
When A visitor chooses Facility (FA) and searches, and then chooses Any and searches
Then The first call carries providerType=FA and lists only facilities; the second call carries no providerType parameter and lists all types
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (providerType), D-008, modern/docs/ux/wireframes/public-directory.html

###### AC-4 Names are composed, not raw bytes
Given An individual provider with first name MARIA, last name RIVERA and licence number MD12345, and a facility with legal name SUNSET HOSPICE INC
When They appear in the results
Then The Name column shows 'MARIA RIVERA' and 'SUNSET HOSPICE INC' exactly as returned in displayName; the licence number or other bytes of the record never appear in a name, and the page does not cut a name in the middle of a word
Basis: D-028, R-PRVDIR01-ADD-02, modern/contracts/openapi/directory-service.yaml#DirectoryEntry (displayName composed from the name fields)

###### AC-5 A suspended provider does not appear (journey J5)
Given An Active provider was suspended (status S) by a supervisor, and a Pending, a Denied and a Terminated provider exist
When A visitor searches for the suspended provider's name and for the others
Then None of them is listed; the page only shows what the API returns for active providers and never adds or fills in a provider itself
Basis: modern/docs/ux/journeys.md#J5 (step 3), R-PRVDIR01-ADD-01, R-PRVDIR01-cbl-00018, D-006, Q-008

###### AC-6 Only public columns are shown
Given The API also returns Medicaid ID and risk tier for each entry
When The results are shown
Then The table has exactly the columns Name, NPI (10 digits in full), Type and Specialty (taxonomy); Medicaid ID and risk tier are not displayed, and no address, phone or network column exists
Basis: modern/docs/ux/wireframes/public-directory.html, ADR-006, Q-008, R-PRVDIR01-ADD-04

###### AC-7 Empty, long, refused and failed searches are handled
Given A search with no match, a search with more than 20 matches, a request the API refuses with 400, and an API failure
When The page shows each outcome
Then No match shows 'No providers found'; many matches show the total and paging controls (page 0, size 20 by default); a 400 shows the problem detail next to the search form; a 5xx or network failure shows 'The directory is not available right now. Try again.' and never 'No providers found'
Basis: modern/contracts/openapi/directory-service.yaml#searchDirectory (page, size, 400), D-024

###### AC-8 The directory page has zero axe violations
Given The Playwright test opens the empty page, a results page, the no-match state and the failure state with no user signed in
When It runs an axe scan on each state and uses the page with the keyboard only
Then Every scan reports zero violations; the page has one h1 and the landmarks header and main with a 'Skip to main content' link; inputs have visible labels; the table has header cells
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/public-directory.html

##### Out of scope
- The FHIR Practitioner, Organization and PractitionerRole API (dir-fhir); the page does not browse FHIR
- A taxonomy (specialty) search box, which the wireframe does not show
- Addresses, phones and networks (Q-008: not held by the legacy master)
- The nightly DIROUT file (ui-console-batch)
- Keeping the directory up to date from provider events (dir-projection)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVDIR01-ADD-01, R-PRVDIR01-ADD-02, R-PRVDIR01-ADD-04, R-PRVDIR01-cbl-00004, R-PRVDIR01-cbl-00018
Programs: PRVDIR01.cbl:72-81 (EXTRACT-NEXT: active filter), 83-90 (WRITE-DIR-ROW: name is the first 60 bytes of PRV-VARIANT at :88, D-028). No job step; PRVDIR01 is run by no JCL.
Decisions: D-028 FIX (accepted, Q-010): the name is composed from the name fields (individual: first + last; organisation: legal name); D-006 A suspended provider has its own status and is not listed; D-008 PRESERVE: provider types IN, GR, FA; D-024 FIX: failures are surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/directory/providers. Roles: PUBLIC.


### MAM-13 Web UI console

#### MAM-77 Console: provider search by NPI, Medicaid ID or name (replaces CICS PINQ)

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-77, agentic, decision-D-022, decision-D-023, decision-D-024. Depends on: MAM-62, MAM-37.

##### Business requirement
A state analyst can look up a provider by identification number, Medicaid ID or name. The number the analyst types is the one searched, a blank or malformed entry is sent back for correction, an unknown provider is reported plainly as 'No provider found', and the last number used is remembered so the analyst can continue without retyping. This replaces the old inquiry transaction, whose typed number was never used and whose messages never reached the screen. Only staff roles can use it.

##### Acceptance criteria
###### AC-1 The inquiry screen opens ready for input
Given analyst1 is signed in and has not searched in this browser tab
When /console/providers opens
Then The page shows the h1 'Provider search' with labelled inputs NPI, Medicaid ID and Name contains and a Search button; the NPI input is empty; no results table and no message are shown; the page has made no search call
Basis: R-PRVCICS1-cbl-00016, R-PRVCICS1-cbl-00014, D-022, modern/docs/ux/wireframes/console-provider-search.html, modern/docs/ux/screen-inventory.md (console provider-search)

###### AC-2 The typed NPI is the search key
Given The provider with NPI 1003000111 exists (RIVERA MARIA, Individual, Active, Limited)
When analyst1 types 1003000111 into NPI and chooses Search
Then The page calls GET /api/v1/providers?npi=1003000111 with exactly the typed value and shows a results table (NPI, Name, Type, Status, Risk) with one row: 1003000111 | RIVERA MARIA | Individual (IN) | Active (A) | Limited (L), whose NPI links to /console/providers/1003000111
Basis: R-PRVCICS1-ADD-02, R-PRVCICS1-cbl-00002, R-PRVCICS1-cbl-00018, R-PRVCICS1-cbl-00003, D-022, modern/docs/ux/journeys.md#J4 (step 1), modern/docs/ux/wireframes/console-provider-search.html

###### AC-3 Search by Medicaid ID or by name
Given The same provider, with Medicaid ID TX0000111
When analyst1 clears NPI and enters Medicaid ID TX0000111, and then clears it and enters the name 'RIVERA'
Then The page calls GET /api/v1/providers?medicaidId=TX0000111 and then GET /api/v1/providers?name=RIVERA, sending only the filled inputs, and each search shows the row for 1003000111
Basis: modern/docs/ux/journeys.md#J4 (step 1: then by Medicaid ID), R-PRVCICS1-ADD-07, modern/docs/ux/screen-inventory.md (new Medicaid ID and name inputs), modern/contracts/openapi/provider-service.yaml#searchProviders, D-022, QE-11

###### AC-4 A blank or malformed entry is not searched
Given The inquiry screen
When analyst1 chooses Search with all three inputs empty, and then with NPI 12345
Then No search call is made; an error summary at the top links to the NPI field ('Enter an NPI, a Medicaid ID or a name' for the empty search, 'NPI must be 10 digits' for 12345), the same text appears next to the field, the focus moves to the summary and what was typed stays, so the analyst can correct it and search again
Basis: R-PRVCICS1-cbl-00020, D-022, modern/contracts/openapi/provider-service.yaml#searchProviders (npi pattern ^[0-9]{10}$), .claude/skills/frontend-stack/SKILL.md section 5

###### AC-5 An unknown provider shows 'No provider found' (journey J4)
Given No provider has NPI 1003000999 (an NPI that is in none of the sample feeds)
When analyst1 searches NPI 1003000999
Then The API answers 200 with no items (total 0) and the page shows the message 'No provider found' in a status area, with no results table; the message that PINQ set as PROVIDER NOT FOUND and never sent now reaches the screen, and the page is neither blank nor an error page
Basis: R-PRVCICS1-cbl-00004, R-PRVCICS1-cbl-00024, R-PRVCICS1-cbl-00025, R-PRVCICS1-ADD-01, R-PRVCICS1-cbl-00036, D-022, modern/docs/ux/journeys.md#J4 (step 3), modern/contracts/openapi/provider-service.yaml#ProviderPage

###### AC-6 The last NPI is kept for the next inquiry
Given analyst1 searched NPI 1003000111 and opened its detail page
When They return to /console/providers in the same browser tab, then search NPI 1003000222, and finally open the page in a new tab
Then The NPI input is pre-filled with 1003000111 on the return visit (and nothing is searched until they choose Search); after the search for 1003000222 that NPI is the one kept; the new tab shows an empty NPI input, as on a first visit
Basis: R-PRVCICS1-cbl-00001, R-PRVCICS1-cbl-00011, R-PRVCICS1-cbl-00012, R-PRVCICS1-cbl-00013, R-PRVCICS1-cbl-00014, R-PRVCICS1-cbl-00017, R-PRVCICS1-cbl-00042, R-PRVCICS1-cbl-00043, R-PRVCICS1-cbl-00044, R-PRVCICS1-cbl-00045, modern/docs/ux/screen-inventory.md (NPI kept as the last search, replaces the COMMAREA), QE-10, D-022

###### AC-7 Failures are shown, never swallowed
Given The inquiry screen
When The API answers 401, 403, 400 or 5xx, or the network call fails
Then The page shows a message that says which: 401 sends the analyst to sign in again, 403 reads 'You are not allowed to search providers', 400 shows the detail the API gives, and 5xx or a network failure reads 'The search could not be completed. Try again.'; none of them is shown as 'No provider found', and no failure leaves a blank table
Basis: R-PRVCICS1-ADD-03, R-PRVCICS1-ADD-04, D-022, D-023, D-024, modern/contracts/openapi/provider-service.yaml#searchProviders (400, 401, 403)

###### AC-8 The search screen has zero axe violations
Given The Playwright test opens the empty screen, a results page, the 'No provider found' state and a validation-error state
When It runs an axe scan on each state and uses the screen with the keyboard only
Then Every scan reports zero violations; Enter in any input starts the search; the results table has header cells; status and risk are written as text with their code; the focus is always visible
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J4

##### Out of scope
- The provider detail page (ui-console-detail and ui-console-detail-2)
- The search back end (prv-search-read)
- Role checks on the API (nfr-security); the route guard is in ui-shell-login
- Advanced filters by status or screen flag (those are used by the work queues)
- Storing the last search on the server (QE-10)

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVCICS1-ADD-02, R-PRVCICS1-ADD-03, R-PRVCICS1-cbl-00001, R-PRVCICS1-cbl-00002, R-PRVCICS1-cbl-00004, R-PRVCICS1-cbl-00011, R-PRVCICS1-cbl-00012, R-PRVCICS1-cbl-00013, R-PRVCICS1-cbl-00014, R-PRVCICS1-cbl-00016, R-PRVCICS1-cbl-00017, R-PRVCICS1-cbl-00018, R-PRVCICS1-cbl-00020, R-PRVCICS1-cbl-00024, R-PRVCICS1-cbl-00025, R-PRVCICS1-cbl-00042, R-PRVCICS1-cbl-00043, R-PRVCICS1-cbl-00044, R-PRVCICS1-cbl-00045
Related rules: R-PRVCICS1-ADD-01, R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-ADD-07, R-PRVCICS1-cbl-00003, R-PRVCICS1-cbl-00015, R-PRVCICS1-cbl-00019, R-PRVCICS1-cbl-00021, R-PRVCICS1-cbl-00022, R-PRVCICS1-cbl-00036
Programs: PRVCICS1.cbl:28-42 (MAIN-LOGIC: COMMAREA moved to the key at :29-31, RECEIVE MAP :33-35 with no INTO), 44-51 (RESP evaluation), 47-56 (NOTFND message set but not sent, SEND MAP), 58-61 (RETURN TRANSID PINQ with COMMAREA). CICS transaction PINQ; no job step.
Decisions: D-022 REPLACE (accepted, Q-010): analyst-console inquiry by NPI or Medicaid ID that shows the status; D-023 FIX (accepted, Q-010): role-based access on every endpoint and screen; D-024 FIX (accepted, Q-010): explicit error handling, failures surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers. Roles: ANALYST, SUPERVISOR, ADMIN.


#### MAM-80 Console: provider detail with every record field and the status shown as text and code

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-80, agentic, decision-D-022, decision-D-006, decision-D-007, decision-D-023, decision-D-024. Depends on: MAM-77.

##### Business requirement
When an analyst opens a provider, the page shows everything the provider record holds, each field with its meaning, and states the enrolment status in words and as the stored code. The old inquiry screen looked the provider up but never actually showed the status message; the new page always does. Active, terminated, pending, denied and suspended each have their own wording, an unrecognised status is not hidden, and an unknown provider is reported plainly.

##### Acceptance criteria
###### AC-1 An individual record is shown in full, with meanings (journey J4)
Given Provider 1003000111: Medicaid ID TX0000111, type IN, taxonomy 207Q00000X, risk tier L, status A, screen flag P, effective 2026-03-02, revalidation due 2031-03-02, last name RIVERA, first name MARIA, licence MD12345, state TX
When analyst1 opens /console/providers/1003000111 from the search results
Then The page calls GET /api/v1/providers/1003000111 and shows the NPI in full in the heading and a table 'Provider record' with NPI 1003000111; Medicaid ID TX0000111; Provider type Individual (IN); Taxonomy 207Q00000X; Enrolment status Active (A); Screen flag Pass (P); Risk tier Limited (L); Effective date 03/02/2026; Revalidation due 03/02/2031; Last name RIVERA; First name MARIA; Licence number MD12345; Licence state TX, so that the provider data and the status message are shown together
Basis: R-PRVCICS1-cbl-00010, R-PRVCICS1-cbl-00041, modern/docs/ux/journeys.md#J4 (step 2), modern/docs/ux/wireframes/console-provider-detail.html, modern/docs/ux/screen-inventory.md (CICS field table), .claude/skills/frontend-stack/SKILL.md section 6, D-022, ADR-009

###### AC-2 A group or facility shows its organisation fields
Given Provider 1003000444 is a group (GR) with legal name EXCLUDED CLINIC LLC and owner NPI 1990000111
When analyst1 opens /console/providers/1003000444
Then Provider type reads 'Group (GR)' and the table shows Legal name, DBA name and Owner NPI 1990000111 in full; last name, first name, licence number and licence state are not shown
Basis: modern/docs/ux/screen-inventory.md (CICS field table: organisation view), D-007, aws-transform/test-data/providers.txt (1003000444, owner 1990000111), modern/contracts/openapi/provider-service.yaml#Organization

###### AC-3 The status banner states the status in words and code
Given Providers whose enrolment status is A, T, P, D and S
When analyst1 opens the detail page of each
Then A banner at the top reads exactly 'Status: Active (A)', 'Status: Terminated (T)', 'Status: Pending (P)', 'Status: Denied (D)' or 'Status: Suspended (S)'; exactly one label is shown for a provider; the same text is the value of the Enrolment status row; the banner is text that a screen reader reads, and colour or an icon may add to it but never replaces it
Basis: R-PRVCICS1-ADD-01, R-PRVCICS1-cbl-00005, R-PRVCICS1-cbl-00006, R-PRVCICS1-cbl-00007, R-PRVCICS1-cbl-00008, R-PRVCICS1-cbl-00026, R-PRVCICS1-cbl-00027, R-PRVCICS1-cbl-00028, R-PRVCICS1-cbl-00030, R-PRVCICS1-cbl-00031, R-PRVCICS1-cbl-00032, R-PRVCICS1-cbl-00033, R-PRVCICS1-cbl-00034, R-PRVCICS1-cbl-00037, R-PRVCICS1-cbl-00038, R-PRVCICS1-cbl-00039, D-022, modern/docs/ux/wireframes/console-provider-detail.html (Status: Active (A), text and code, never colour alone)

###### AC-4 Denied is shown for a denied provider, with the code
Given A provider whose status is D, for example 1003000444 after the analyst denied it
When analyst1 opens its detail page
Then The banner reads 'Status: Denied (D)' and the Enrolment status row reads 'Denied (D)'
Basis: R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00035, R-PRVCICS1-cbl-00040, modern/docs/ux/journeys.md#J3 (step 4)

###### AC-5 Suspended has its own wording (journey J5)
Given Provider 1003000111 was suspended by a sanction (status S)
When analyst1 opens its detail page
Then The banner and the Enrolment status row read 'Suspended (S)', not 'Denied (D)'
Basis: R-PRVCICS1-ADD-06, D-006, modern/docs/ux/journeys.md#J5 (step 2), modern/docs/ux/screen-inventory.md (PRV-ENROLL-STATUS: Suspended (S))

###### AC-6 An unrecognised status keeps the legacy catch-all label and shows the stored code
Given A record whose status value is not P, A, T, D or S (a blank value, or X, served by a test double of the API, since the 1.0.0 contract allows only P, A, T, D and S)
When analyst1 opens its detail page
Then The banner reads 'Status: Denied (X)' for X and 'Status: Denied (blank)' for an empty value, so the legacy catch-all label is kept and the real stored value is still visible to the analyst
Basis: R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00029, R-PRVCICS1-cbl-00035, QE-3

###### AC-7 Unknown providers and failures are told apart
Given NPI 1003000999 has no provider record; and separate API failures 401, 403 and 5xx
When analyst1 opens /console/providers/1003000999, and then the page fails to load with each failure
Then For the unknown NPI the API answers 404 and the page shows 'No provider found' with a link 'Back to provider search' and no table; a 401 sends the analyst to sign in, a 403 shows 'You are not allowed to see this provider', and a 5xx or network failure shows 'The provider could not be loaded. Try again.'; none of these is shown as a status
Basis: R-PRVCICS1-cbl-00036, R-PRVCICS1-ADD-04, modern/docs/ux/journeys.md#J4 (step 3), D-022, D-023, D-024, modern/contracts/openapi/provider-service.yaml#getProvider (401, 403, 404)

###### AC-8 The detail page has zero axe violations
Given The Playwright test opens an individual record, an organisation record, one record per status and the not-found state
When It runs an axe scan on each and reads the page with the keyboard only
Then Every scan reports zero violations; the page has one h1 and landmarks; the record table has header cells; the status banner is never colour alone; the focus order follows the visual order
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J4

##### Out of scope
- Screening, sanctions, revalidation, notices and audit panels (ui-console-detail-2)
- The Approve or deny and Record sanction actions (ui-console-approval, ui-console-sanction)
- Editing provider data (no such function)
- The VSAM READ ERROR text of the legacy program, replaced by the generic failure message of AC-7

##### Legacy trace (agent-owned, do not edit)
Rules: R-PRVCICS1-ADD-01, R-PRVCICS1-ADD-06, R-PRVCICS1-cbl-00005, R-PRVCICS1-cbl-00006, R-PRVCICS1-cbl-00007, R-PRVCICS1-cbl-00008, R-PRVCICS1-cbl-00009, R-PRVCICS1-cbl-00010, R-PRVCICS1-cbl-00026, R-PRVCICS1-cbl-00027, R-PRVCICS1-cbl-00028, R-PRVCICS1-cbl-00029, R-PRVCICS1-cbl-00030, R-PRVCICS1-cbl-00031, R-PRVCICS1-cbl-00032, R-PRVCICS1-cbl-00033, R-PRVCICS1-cbl-00034, R-PRVCICS1-cbl-00035, R-PRVCICS1-cbl-00036, R-PRVCICS1-cbl-00037, R-PRVCICS1-cbl-00038, R-PRVCICS1-cbl-00039, R-PRVCICS1-cbl-00040, R-PRVCICS1-cbl-00041
Related rules: R-PRVCICS1-ADD-04, R-PRVCICS1-ADD-05, R-PRVCICS1-cbl-00023
Programs: PRVCICS1.cbl:53-56 (SEND MAP FROM WS-PROVIDER-AREA only: WS-MSG never sent), 63-73 (FORMAT-PROVIDER: EVALUATE active, terminated, pending, otherwise DENIED; labels at :65-72), 47-52 (NOTFND and other RESP messages). CICS transaction PINQ; no job step.
Decisions: D-022 REPLACE (accepted, Q-010): analyst-console inquiry that shows the status; D-006 A new SUSPENDED status with its own label; D-007 The record is 130 bytes: owner NPI is part of the record shown; D-023 FIX (accepted, Q-010): role-based access on every screen; D-024 FIX (accepted, Q-010): failures surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers/{npi}. Roles: ANALYST, SUPERVISOR, ADMIN.


#### MAM-82 Console: provider detail panels for screening, sanctions, revalidation, notices and audit

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-82, agentic, decision-D-005, decision-D-016, decision-D-018, decision-D-029, decision-D-012, decision-D-023, decision-D-024. Depends on: MAM-80, MAM-25, MAM-52, MAM-48, MAM-60, MAM-40.

##### Business requirement
Below the provider record, an analyst, supervisor or administrator sees what has happened to the provider: the screening results source by source, the sanctions history, the revalidation cases, the notices sent, and the audit trail of changes. Each person sees the panels their role is allowed to read, and one panel failing to load never hides the others.

##### Acceptance criteria
###### AC-1 Screening results per source (journey J3)
Given Provider 1003000444, whose owner 1990000111 is on the mocked OIG LEIE, was screened with overall result FAIL; provider 1003000111 was screened with PASS on LICBOARD, OIGLEIE, SAMGOV and SSADMF
When analyst1 opens /console/providers/1003000444 and then /console/providers/1003000111
Then The page calls GET /api/v1/screenings?npi=<npi> and the panel 'Screening' shows each screening (with its reason, date and overall result) and a table Source | Result | Verdict | Detail | Error code with one row per source returned; for 1003000444 the OIGLEIE row reads 'Fail (F)' with its detail and 'E203 owner excluded'; for 1003000111 the four rows read 'Pass (P)'; a result C reads 'Conditional (C)'; a provider with no screening shows 'Not screened yet'
Basis: modern/docs/ux/journeys.md#J3 (step 3), modern/docs/ux/wireframes/console-provider-detail.html, D-005, D-016, D-018, .claude/skills/pem-domain/SKILL.md section 4 (E201-E205 meanings), modern/docs/analysis/data-layouts.md (SCR-RESULT P, F, C=SCR-COND), QE-12, modern/contracts/openapi/screening-service.yaml#SourceResult

###### AC-2 The sanctions history (journey J5)
Given Provider 1003000111 was suspended and then reinstated by supervisor1
When An analyst, supervisor or administrator opens its detail page
Then The page calls GET /api/v1/sanctions?npi=1003000111 and the panel 'Sanctions' shows the rows Effective date | Action | Reason | By, with 'Suspend (S)' and 'Reinstate (R)' (and 'Terminate (T)' when it applies) and the reasons; a provider with no sanction shows 'None recorded'
Basis: modern/docs/ux/journeys.md#J5 (step 4), modern/docs/ux/wireframes/console-provider-detail.html, D-018, D-006, modern/contracts/openapi/sanctions-service.yaml#Sanction

###### AC-3 Revalidation cases, one per provider and period
Given Provider 1003000222 has a revalidation case DUE for 2026-02-28 and an older case COMPLETED
When An analyst or supervisor opens its detail page
Then The page calls GET /api/v1/revalidations?npi=1003000222 and the panel 'Revalidation' shows the provider's revalidation due date and a table Due date | Case | Opened | Closed with the case states written as 'Due', 'In screening', 'Completed' or 'Cancelled'; a provider with no case shows 'No revalidation case'
Basis: D-029, D-003, modern/contracts/openapi/revalidation-service.yaml#RevalidationCase, modern/docs/ux/screen-inventory.md (console provider-detail), R-PRVREV01-ADD-02

###### AC-4 Notices sent to the provider
Given Provider 1003000111 received the notices SUS and RST after a suspension and a reinstatement
When The detail page opens
Then The page calls GET /api/v1/notices?npi=1003000111 and the panel 'Notices' lists Sent | Notice | Status with 'Suspension notice (SUS)' and 'Reinstatement notice (RST)' and their state 'Sent' or 'Failed' in words
Basis: modern/docs/ux/journeys.md#J5 (steps 2 and 4), D-012, QE-2, modern/contracts/openapi/notification-service.yaml#listNotices, R-PRVSAN01-cbl-00008

###### AC-5 The audit trail (supervisor and administrator)
Given Provider 1003000111 was approved by analyst1 on 03/02/2026
When supervisor1 or admin1 opens its detail page
Then The page calls GET /api/v1/providers/1003000111/audit and the panel 'Audit' shows When | Who | Action rows from the API, for example 03/02/2026 | analyst1 | the approval action
Basis: modern/docs/ux/wireframes/console-provider-detail.html, modern/docs/ux/journeys.md#J4 (step 2), modern/docs/architecture/nfr.md (Audit), modern/contracts/openapi/provider-service.yaml#getProviderAudit

###### AC-6 Each role sees only the panels it may read
Given analyst1 (ANALYST), supervisor1 (SUPERVISOR) and admin1 (ADMIN) open the same provider
When The detail page loads for each
Then analyst1 sees Screening, Sanctions, Revalidation and Notices and no Audit panel and no audit request; supervisor1 sees all five panels; admin1 sees Screening, Sanctions, Notices and Audit and no Revalidation panel and no revalidation request; a panel that is not shown is not requested, so no 403 is raised
Basis: QE-4, D-023, modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml#getProviderAudit (SUPERVISOR, ADMIN), modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases (ANALYST, SUPERVISOR)

###### AC-7 One failing panel does not hide the others
Given The sanctions call answers 5xx while the other calls succeed
When The detail page loads
Then The record, banner, screening, revalidation and notices panels are shown normally and the Sanctions panel shows 'The sanctions could not be loaded' with a Retry button; the panel is not shown as 'None recorded'
Basis: D-024, D-018, modern/docs/ux/screen-inventory.md (console provider-detail calls six APIs)

###### AC-8 The panels have zero axe violations
Given The Playwright test opens a fully populated detail page for each role and a page with a failed panel
When It runs an axe scan on each page and reads the panels with the keyboard only
Then Every scan reports zero violations; each panel has a heading and a table with header cells; results and statuses are text plus code, never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J3, modern/docs/ux/journeys.md#J4

##### Out of scope
- The provider record and the status banner (ui-console-detail)
- Starting a screening or completing a revalidation case from the page (no screen in the inventory)
- Showing the before and after values of an audit entry (not in the wireframe)
- Back-end behaviour of screening, sanctions, revalidation, notification and audit (their own stories)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVREV01-ADD-02, R-PRVSAN01-cbl-00008
Programs: PRVSCR01.cbl:95-114 (SCREEN-ONE-PROVIDER), 116-132 (CHECK-LICENSE, CHECK-LEIE-EXCLUSION, CHECK-SAM-DEBARMENT, CHECK-SSA-DMF: stubs returning P), 146-149 (WRITE-SCREEN-ROW, SCRNOUT 67 bytes); PRVSAN01.cbl:104-116 (APPLY-SANCTION, no history kept), 124-138 (GENERATE-NOTICE); PRVREV01.cbl:75-94 (CHECK-NEXT, QUEUE-FOR-REVAL: no case state). Job steps PRVDAY01.jcl STEP030, PRVMTH01.jcl STEP010.
Decisions: D-005 REPLACE: real screening with per-source results, shown in the Screening panel; D-016 FIX: screening error codes E201-E205 shown with their meaning; D-018 FIX: screening results and sanction history are real data and are shown; D-029 FIX (accepted, Q-010): one revalidation case per provider with its own state; D-012 REPLACE: notices come from notification-service; D-023 FIX: each role sees only what it may read; D-024 FIX: failures are surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/screenings, GET /api/v1/sanctions, GET /api/v1/revalidations, GET /api/v1/notices, GET /api/v1/providers/{npi}/audit. Roles: ANALYST, SUPERVISOR, ADMIN.


#### MAM-85 Console: work queues (pending approval, failed screening, revalidation due, rejected applications)

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-85, agentic, decision-D-019, decision-D-029, decision-D-005, decision-D-016, decision-D-001, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-62, MAM-37, MAM-54, MAM-25, MAM-48.

##### Business requirement
Analysts and supervisors work from four lists: providers waiting for approval after a passed screening, providers whose screening failed, providers whose revalidation is due, and applications that were rejected. Each list names the provider, why it is there and how to open it, so nothing waits unseen. After a decision or a new screening the lists change accordingly.

##### Acceptance criteria
###### AC-1 Pending approval lists providers with a passed screening (journey J1)
Given Provider 1003000111 is Pending (P) with screen flag Pass (P), and provider 1003000444 is Pending (P) with screen flag Fail (F)
When analyst1 opens /console/queues
Then The page calls GET /api/v1/providers?status=P&screenFlag=P and the section 'Pending approval' lists 1003000111 (columns NPI, Name, Screened 'Pass (P)', and a 'Review' link to /console/providers/1003000111/approval) and does not list 1003000444
Basis: modern/docs/ux/journeys.md#J1 (step 5), D-019, modern/docs/ux/wireframes/console-work-queues.html, modern/docs/ux/screen-inventory.md (console work-queues), modern/contracts/openapi/provider-service.yaml#searchProviders (status, screenFlag), R-PRVSCR01-ADD-03

###### AC-2 Failed screening shows the failing source (journey J3)
Given Provider 1003000444 (owner 1990000111 on the mocked OIG LEIE) is Pending (P) with screen flag Fail (F), failed on OIGLEIE with E203
When analyst1 opens /console/queues
Then The page calls GET /api/v1/providers?status=P&screenFlag=F and, for each listed provider, GET /api/v1/screenings?npi=<npi>&result=FAIL; the section 'Failed screening' lists 1003000444 EXCLUDED CLINIC LLC with the failing source 'OIGLEIE (E203 owner excluded)' and the NPI links to /console/providers/1003000444; the provider is not listed under Pending approval
Basis: modern/docs/ux/journeys.md#J3 (steps 1 to 3), D-005, D-016, QE-9, .claude/skills/pem-domain/SKILL.md section 4 (E203 owner excluded), modern/docs/ux/wireframes/console-work-queues.html, modern/contracts/openapi/screening-service.yaml#listScreenings

###### AC-3 Revalidation due lists open cases once each (journey J6)
Given The revalidation sweep opened one case for provider 1003000222 with due date 2026-02-28; its status is DUE or already IN_SCREENING; an older case of another provider is COMPLETED
When analyst1 opens /console/queues
Then The page calls GET /api/v1/revalidations?status=DUE and GET /api/v1/revalidations?status=IN_SCREENING and the section 'Revalidation due' lists the case of 1003000222 once, with the columns NPI, Due (02/28/2026) and Case ('Due' or 'In screening'); completed and cancelled cases are not listed
Basis: modern/docs/ux/journeys.md#J6 (step 2), D-029, D-003, QE-5, modern/docs/ux/wireframes/console-work-queues.html, modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases, R-PRVREV01-ADD-02

###### AC-4 Rejected applications are listed for follow-up
Given Two applications were rejected: one for NPI 1003000111 with E104, and one with a blank NPI with E101
When analyst1 opens /console/queues
Then The page calls GET /api/v1/applications?status=REJECTED and the section 'Rejected applications' lists each with NPI (or '(no NPI)' when blank), Submitted date as MM/DD/YYYY, the error codes found (E104; E101) and the source ('Portal' or 'Legacy file')
Basis: modern/docs/ux/screen-inventory.md (console work-queues: rejected applications), D-001, D-016, D-004, R-PRVENR01-ADD-05, modern/contracts/openapi/enrollment-service.yaml#listApplications, modern/contracts/openapi/enrollment-service.yaml#Application (source PORTAL, LEGACY_FILE)

###### AC-5 Counts, empty lists and paging
Given The four sections in turn have 1 entry, no entry and more than 20 entries
When /console/queues is shown
Then Each heading carries its count ('Pending approval (1)'); an empty section says 'Nothing pending approval', 'No failed screenings', 'No revalidations due' or 'No rejected applications'; a section with more than 20 entries shows paging controls (page 0, size 20 by default)
Basis: modern/contracts/openapi/provider-service.yaml#searchProviders (page, size), modern/contracts/openapi/enrollment-service.yaml#listApplications (page, size), modern/contracts/openapi/revalidation-service.yaml#listRevalidationCases (page, size), modern/docs/ux/wireframes/console-work-queues.html

###### AC-6 One failing queue does not hide the others
Given The revalidations call answers 5xx while the other calls succeed
When /console/queues loads
Then The other three sections are shown normally and 'Revalidation due' shows 'The list could not be loaded' with a Retry button, never 'No revalidations due'
Basis: D-024, modern/docs/ux/screen-inventory.md (console work-queues calls three APIs)

###### AC-7 Only analysts and supervisors open the queues
Given analyst1, supervisor1, admin1 and provider1 are signed in
When Each opens /console/queues
Then analyst1 and supervisor1 see the four sections; admin1 and provider1 see the access-denied page and no queue request is made (the applications and revalidations endpoints do not allow ADMIN)
Basis: D-023, modern/docs/ux/screen-inventory.md (roles ANALYST, SUPERVISOR), modern/docs/architecture/nfr.md (roles per endpoint)

###### AC-8 The queues page has zero axe violations
Given The Playwright test opens the page with all four sections filled, with all empty and with one section failed
When It runs an axe scan on each and uses the page with the keyboard only
Then Every scan reports zero violations; each section has a heading and a table with header cells; the Review and NPI links can be reached with Tab and opened with Enter
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J1

##### Out of scope
- Approving or denying (ui-console-approval)
- Screening, risk and revalidation processing (their services)
- Completing a revalidation case from the queue (no screen in the inventory)
- Reassigning or claiming work items (no such function exists)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVENR01-ADD-05, R-PRVREV01-ADD-02, R-PRVSCR01-ADD-03
Programs: PRVREV01.cbl:75-94 (CHECK-NEXT, QUEUE-FOR-REVAL: REVALQ rows with no case state, D-029); PRVSCR01.cbl:90-111 (SCREEN-ONE-PROVIDER sets the screen flag only, R-PRVSCR01-ADD-03); PRVENR01.cbl:173-179 (WRITE-ERROR-RECORD, APPERROR rows). Job steps PRVMTH01.jcl STEP010 (PRVREV01), PRVDAY01.jcl STEP030 and STEP010.
Decisions: D-019 FIX: an analyst approval step sets ACTIVE after a Pass screening, so a Pending-approval queue is needed; D-029 FIX (accepted, Q-010): one revalidation case per provider with its own state; D-005 REPLACE: screening results per source decide the failed-screening queue; D-016 FIX: error codes shown with the failing source; D-001 FIX: rejected applications are kept and listed; D-023 FIX: only analysts and supervisors; D-024 FIX: failures are surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers, GET /api/v1/screenings, GET /api/v1/revalidations, GET /api/v1/applications. Roles: ANALYST, SUPERVISOR.


#### MAM-87 Console: approve or deny an enrolment

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-87, agentic, decision-D-019, decision-D-003, decision-D-004, decision-D-023, decision-D-024, slice-main-journey. Depends on: MAM-85, MAM-27, MAM-25.

##### Business requirement
Nothing in the old system ever made a provider active. An analyst or supervisor now reviews a pending provider and its screening results and either approves the enrolment, which makes the provider active with a revalidation date five years ahead, or denies it, optionally with a reason. Approval is only offered after a passed screening, and if someone else has already decided, the analyst is told and shown the current status.

##### Acceptance criteria
###### AC-1 The approval page and how to reach it
Given Provider 1003000111 is Pending (P) with screen flag Pass (P) and has a passed screening
When analyst1 chooses 'Review' in the Pending approval queue, or opens the provider's detail page and chooses 'Approve or deny'
Then /console/providers/1003000111/approval opens and shows NPI, Name, Status 'Pending (P)', Screen flag 'Pass (P)' and the per-source screening results, from GET /api/v1/providers/1003000111 and GET /api/v1/screenings?npi=1003000111; on the detail page the 'Approve or deny' action is offered only to ANALYST and SUPERVISOR and only when the status is Pending (P) and the screen flag is Pass (P) or Fail (F)
Basis: modern/docs/ux/journeys.md#J1 (step 6), modern/docs/ux/journeys.md#J3 (step 4), D-019, QE-6, modern/docs/ux/wireframes/console-approval.html, modern/docs/ux/screen-inventory.md (console approval)

###### AC-2 Approve makes the provider Active (journey J1)
Given The approval page of 1003000111 (Pending, Pass)
When analyst1 selects 'Approve (becomes Active)' and chooses 'Record decision'
Then The page sends POST /api/v1/providers/1003000111/approval with decision APPROVE, receives 200 with the provider in status A and a revalidation due date exactly five years after the approval date, and confirms 'Active (A)' with the due date returned by the service as MM/DD/YYYY (approval on 03/05/2026 shows 03/05/2031); the provider is no longer listed under Pending approval
Basis: D-019, D-003, D-004, modern/docs/ux/journeys.md#J1 (step 6), modern/contracts/openapi/provider-service.yaml#decideProvider, modern/docs/ux/wireframes/console-approval.html, R-PRVSCR01-ADD-03

###### AC-3 Deny with a reason (journey J3)
Given The approval page of 1003000444 (Pending, screen flag Fail, failed on OIGLEIE with E203)
When analyst1 selects 'Deny (becomes Denied)', types the reason 'Owner is on the OIG exclusion list' (at most 200 characters, with a character counter) and chooses 'Record decision'
Then The page sends POST /api/v1/providers/1003000444/approval with decision DENY and the reason, receives 200 with status D and confirms 'Denied (D)'; the provider is no longer listed under Failed screening; a longer reason is not sent
Basis: modern/docs/ux/journeys.md#J3 (step 4), D-019, QE-6, modern/contracts/openapi/provider-service.yaml#ApprovalRequest (reason maxLength 200), modern/docs/ux/wireframes/console-approval.html

###### AC-4 Approval needs a passed screening
Given A Pending provider whose screen flag is Fail (F), and another whose screen flag is Unscreened (U)
When analyst1 opens the approval page of each
Then For the Fail provider the Approve option is disabled with the text 'Approval needs a passed screening' and only Deny can be recorded; for the Unscreened provider neither decision is offered and the page says that screening has not finished; nothing is sent to the API in either case until a permitted decision is recorded
Basis: D-019, QE-6, modern/contracts/openapi/provider-service.yaml#decideProvider (a pending provider whose screening passed), R-PRVSCR01-ADD-03

###### AC-5 Refusals from the service are shown and the page is refreshed
Given Another analyst has already approved 1003000111 while analyst1's approval page was open
When analyst1 records a decision
Then The API answers 409 and the page shows the problem detail, reloads the provider so that it reads 'Active (A)' and offers no further decision; an answer of 422 shows its detail at the decision form and keeps what was typed; a 404 shows 'No provider found'; a 5xx or network failure shows 'The decision could not be recorded. Try again.' and the page makes no claim that a decision was recorded
Basis: modern/contracts/openapi/provider-service.yaml#decideProvider (404, 409, 422), D-024, D-019

###### AC-6 Only analysts and supervisors decide
Given analyst1, supervisor1, admin1 and provider1 are signed in
When Each opens /console/providers/1003000111/approval
Then analyst1 and supervisor1 see the decision form; admin1 and provider1 see the access-denied page; if the API answers 403 to a decision the page shows 'You are not allowed to record this decision'
Basis: D-023, modern/docs/ux/screen-inventory.md (roles ANALYST, SUPERVISOR), modern/docs/architecture/nfr.md (roles per endpoint), modern/contracts/openapi/provider-service.yaml#decideProvider (x-roles ANALYST, SUPERVISOR)

###### AC-7 The approval page has zero axe violations
Given The Playwright test opens the page for a Pass provider, a Fail provider, an Unscreened provider and after a refusal
When It runs an axe scan on each state and records a decision with the keyboard only
Then Every scan reports zero violations; the decision is a labelled set of radio buttons and the Reason has a visible label; the status and screen flag are text plus code, never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J1, modern/docs/ux/journeys.md#J3

##### Out of scope
- Setting the status and the due date (provider-service, prv-approve); the page shows what the service returns
- The notice sent after approval or denial (notification-service) and its display (ui-portal-notices, ui-console-detail-2)
- Waiting for the risk tier before approval: the page shows no risk tier; the tier is saved by provider-service when it is assigned
- Making the reason mandatory (the contract makes it optional)
- Reversing a decision (a sanction is the only way to change an Active provider)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSCR01-ADD-03
Programs: PRVSAN01.cbl:112 (the only statement in the estate that sets ACTIVE is the sanction reinstate, D-019); PRVSCR01.cbl:90-111 (SCREEN-ONE-PROVIDER sets only the screen flag, R-PRVSCR01-ADD-03); PRVENR01.cbl:146 (status PENDING). No job step approves a provider.
Decisions: D-019 FIX: an analyst approval step sets ACTIVE after a Pass screening; D-003 FIX: the revalidation due date is set at approval (approval date + 5 years); D-004 FIX: 4-digit years in all dates; D-023 FIX: only ANALYST and SUPERVISOR decide; D-024 FIX: refusals and failures are surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/providers/{npi}, GET /api/v1/screenings, POST /api/v1/providers/{npi}/approval. Roles: ANALYST, SUPERVISOR.


#### MAM-90 Console: record a sanction (supervisor)

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-90, agentic, decision-D-006, decision-D-018, decision-D-026, decision-D-027, decision-D-023, decision-D-024, decision-D-004. Depends on: MAM-80, MAM-52.

##### Business requirement
A supervisor can terminate, suspend or reinstate a provider by choosing the action, giving a reason and an effective date. The provider's status changes at once, the history keeps every action, and a suspension is a status of its own, no longer confused with a denial. The form never invents rules about which status may be sanctioned, and an unknown action is refused with a message at the field.

##### Acceptance criteria
###### AC-1 Only supervisors reach the sanction form
Given supervisor1, analyst1, admin1 and provider1 are signed in and an Active provider 1003000111 exists
When Each opens /console/providers/1003000111
Then Only supervisor1 sees the action 'Record sanction', which opens /console/providers/1003000111/sanctions/new; typing that address as analyst1, admin1 or provider1 shows the access-denied page
Basis: D-023, modern/docs/ux/screen-inventory.md (console record-sanction, SUPERVISOR), modern/docs/ux/journeys.md#J5 (step 1), modern/contracts/openapi/sanctions-service.yaml#recordSanction (SUPERVISOR)

###### AC-2 The form and its required values
Given supervisor1 is on the sanction form of 1003000111
When The page opens and supervisor1 chooses 'Record sanction' with no action and no date
Then The form shows the NPI (read-only), an Action list (Terminate (T), Suspend (S), Reinstate (R)), a Reason (at most 60 characters) and an Effective date (MM/DD/YYYY); with no action or an empty or impossible date nothing is sent and field errors with an error summary appear; a valid date is sent in the ISO form (for example 03/31/2026 as 2026-03-31)
Basis: modern/docs/ux/wireframes/console-record-sanction.html, modern/contracts/openapi/sanctions-service.yaml#SanctionRequest (npi, action and effectiveDate required; reason maxLength 60), D-004, ADR-009, .claude/skills/frontend-stack/SKILL.md sections 5 and 6

###### AC-3 Each action sets its own status
Given Active providers 1003000333 and 1003000777
When supervisor1 records Suspend (S) for 1003000333, Terminate (T) for 1003000777 and then Reinstate (R) for 1003000333, each with a reason and the effective date 03/31/2026
Then Each time the page sends POST /api/v1/sanctions with npi, action, reason and effectiveDate 2026-03-31, receives 201 with resultingStatus S, T and A, and confirms 'Suspended (S)', 'Terminated (T)' and 'Active (A)' respectively, so a suspension is never shown as Denied
Basis: D-006, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016, modern/contracts/openapi/sanctions-service.yaml#Sanction (resultingStatus T, S, A), modern/docs/ux/journeys.md#J5 (steps 2 and 4)

###### AC-4 Suspend and reinstate keep a history (journey J5)
Given Provider 1003000111 is Active
When supervisor1 records Suspend (S) with a reason and an effective date, and later Reinstate (R)
Then After the first action the page confirms 'Suspended (S)' and after the second 'Active (A)'; the sanction history on the page (GET /api/v1/sanctions?npi=1003000111) lists both rows with Effective date, Action, Reason and By
Basis: modern/docs/ux/journeys.md#J5 (steps 2 and 4), D-018, R-PRVSAN01-ADD-03, modern/docs/ux/wireframes/console-record-sanction.html (Sanction history: Effective, Action, Reason, By), modern/contracts/openapi/sanctions-service.yaml#listSanctions

###### AC-5 An unknown action code is rejected at the field (journey J5)
Given A request that carries an action other than T, S or R (the form only offers T, S and R, so the test sends one through a test double of the API)
When The service answers 422
Then The message of the answer (which names the action field and the allowed values T, S and R) is shown next to the Action field and in the error summary; the status and history shown on the page are unchanged
Basis: D-026, modern/docs/ux/journeys.md#J5 (step 5), R-PRVSAN01-ADD-02, modern/contracts/openapi/sanctions-service.yaml#recordSanction (422)

###### AC-6 No status is blocked by the page
Given Providers that are Pending (P), Denied (D), Terminated (T), Suspended (S) and Active (A)
When supervisor1 opens the sanction form of each
Then All three actions are offered whatever the status; for example Reinstate (R) is offered for a Denied provider and the page sends it, and the page shows whatever status the service returns
Basis: D-027, Q-006, R-PRVSAN01-ADD-04, modern/docs/ux/screen-inventory.md (console record-sanction: D-027)

###### AC-7 Unknown providers and failures are shown, nothing is claimed that did not happen
Given The form for NPI 1003000999 (no provider), and then API answers 403, 5xx and a network failure
When supervisor1 records a sanction
Then A 404 shows 'No provider found' and records nothing; a 403 shows 'You are not allowed to record sanctions'; a 5xx or network failure shows 'The sanction could not be applied. Nothing was recorded.' and the history and status shown stay as they were
Basis: D-024, D-023, modern/contracts/openapi/sanctions-service.yaml#recordSanction (403, 404, 422), R-PRVSAN01-ADD-01

###### AC-8 The sanction form has zero axe violations
Given The Playwright test opens the empty form, the form with field errors, the form after a successful action and the failure state
When It runs an axe scan on each and records a sanction with the keyboard only
Then Every scan reports zero violations; every input has a visible label; errors are shown next to the field and in a linked summary; the history table has header cells
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J5

##### Out of scope
- Applying the status and sending the notice (sanctions-service, provider-service, notification-service)
- The SUS and RST notices: sent by ntf-send, shown in the Notices panel of ui-console-detail-2 and checked end to end by e2e-j5
- The public directory no longer showing a suspended provider (ui-public-directory and dir-projection)
- Loading sanctions from the legacy SANCIN file (ui-console-batch)
- Restricting actions by the current status (D-027 preserves the legacy rule, pending the business answer)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVSAN01-ADD-01, R-PRVSAN01-ADD-02, R-PRVSAN01-ADD-03, R-PRVSAN01-ADD-04, R-PRVSAN01-cbl-00005, R-PRVSAN01-cbl-00014, R-PRVSAN01-cbl-00015, R-PRVSAN01-cbl-00016
Programs: PRVSAN01.cbl:104-116 (APPLY-SANCTION: T gives TERMINATED, S gives DENIED at :109-110, R gives ACTIVE at :112; any other code is accepted), 124-138 (GENERATE-NOTICE, CALL at :134). No job step runs PRVSAN01.
Decisions: D-006 A new SUSPENDED status; a suspension is no longer a denial; D-018 FIX: sanction history is kept and shown; D-026 FIX (accepted, Q-010): unknown action codes are rejected with an error; D-027 PRESERVE (Q-006): sanctions apply whatever the current status; D-023 FIX: only SUPERVISOR records sanctions; D-024 FIX: failures are surfaced; D-004 FIX: 4-digit years; the date is sent as ISO

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: POST /api/v1/sanctions, GET /api/v1/sanctions, GET /api/v1/providers/{npi}. Roles: SUPERVISOR.


#### MAM-92 Console: view and change the risk rules (admin)

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-92, agentic, decision-D-002, decision-D-023, decision-D-024. Depends on: MAM-62, MAM-38.

##### Business requirement
The table that turns a provider's specialty prefix into a risk level (limited, moderate or high) is kept as business data, not buried in a program. An administrator can see the current table with its version, add, change or remove rows, and save the result as a new version. The screen starts with the legacy values and refuses entries that are not valid. Nobody else can change the table.

##### Acceptance criteria
###### AC-1 The current table is shown with the legacy values
Given The initial rule set (version 1, default tier Limited)
When admin1 opens /console/admin/risk-rules
Then The page calls GET /api/v1/risk-rules and shows 'Version 1 (default tier: Limited (L))' and a table Taxonomy prefix | Tier with 332 High (H), 251 High (H), 341 Moderate (M), 291 Moderate (M) and 225 Moderate (M); no meaning (such as a specialty name) is shown next to a prefix
Basis: modern/docs/ux/wireframes/console-risk-rules.html, Q-005, .claude/skills/pem-domain/SKILL.md section 3, modern/contracts/openapi/risk-service.yaml#RiskRuleSet, R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006

###### AC-2 A changed table is saved as a new version
Given admin1 is on the page showing version 1
When They add the row 207 with tier Moderate (M) and choose 'Save as new version'
Then The page sends PUT /api/v1/risk-rules with the version it loaded (1), defaultTier L and the six rules, receives 200 and shows 'Version 2' with the six rows returned by the API
Basis: modern/contracts/openapi/risk-service.yaml#replaceRiskRules (replace the table with a new version), modern/docs/ux/wireframes/console-risk-rules.html (Save as new version), Q-005, D-002

###### AC-3 Rows and the default tier can be edited
Given The table of version 1
When admin1 changes the tier of 225 to High (H), removes the row 291 and changes the default tier to Moderate (M) before saving
Then The page sends exactly those rows and that default tier in the PUT request body, and the table shown after saving matches what was sent; tiers are chosen from High (H), Moderate (M) and Limited (L) only
Basis: modern/contracts/openapi/risk-service.yaml#RiskRuleSet, modern/contracts/openapi/risk-service.yaml#RiskRule (tier L, M, H), .claude/skills/pem-domain/SKILL.md section 3 (data-driven table), Q-005

###### AC-4 Invalid prefixes are refused at the field
Given admin1 is editing the table
When They enter the prefixes '20' (too short), '2o7' (a lower-case letter) or leave a prefix empty and choose 'Save as new version'
Then Nothing is sent; an error summary links to the cell and a message next to it says that a prefix is exactly 3 upper-case letters or digits
Basis: modern/contracts/openapi/risk-service.yaml#RiskRule (taxonomyPrefix pattern ^[0-9A-Z]{3}$), .claude/skills/frontend-stack/SKILL.md section 5, Q-005

###### AC-5 Refusals from the service are shown and the table is reloaded
Given Another administrator saved version 2 while admin1 was editing version 1, or the table lists a prefix twice
When admin1 chooses 'Save as new version'
Then The API answers 422 and the page shows the problem detail, reloads and shows the table that the service holds now with its version, and does not show the rejected table as saved; a 400 (a malformed table) is shown the same way
Basis: modern/contracts/openapi/risk-service.yaml#replaceRiskRules (400, 422), D-024

###### AC-6 Only administrators open the screen
Given admin1, supervisor1, analyst1 and provider1 are signed in
When Each tries to open /console/admin/risk-rules
Then admin1 sees the table and the Save button; supervisor1, analyst1 and provider1 see no menu entry and the access-denied page when they type the address; if the API answers 403 to a save the page shows 'You are not allowed to change the risk rules'
Basis: D-023, QE-8, modern/docs/ux/screen-inventory.md (console risk-rules, ADMIN), modern/docs/architecture/nfr.md (PUT /api/v1/risk-rules: ADMIN), modern/contracts/openapi/risk-service.yaml#replaceRiskRules (ADMIN)

###### AC-7 The risk-rules screen has zero axe violations
Given The Playwright test opens the table, the table with field errors and the page after a refusal
When It runs an axe scan on each and edits and saves the table with the keyboard only
Then Every scan reports zero violations; each cell input has a label that names its row and column; the Add and Remove buttons have names that say which row they act on; errors are linked from the summary; tiers are written as text plus code
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/wireframes/console-risk-rules.html

##### Out of scope
- Computing the risk tier and publishing RiskTierAssigned (risk-service)
- Re-tiering providers that already have a tier (a saved table applies to later assessments only, rsk-rules-admin)
- Names or meanings for prefixes (Q-005: none attached)
- A future effective date or a second-person approval of a change
- Who changed the table and when (no audit field in the data model)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Related rules: R-PRVRSK01-cbl-00004, R-PRVRSK01-cbl-00005, R-PRVRSK01-cbl-00006
Programs: PRVRSK01.cbl:78-97 (CLASSIFY-ONE: taxonomy prefix 332, 251 High; 341, 291, 225 Moderate; else Limited), 103-116 (APPLY-TIER-PARA, LIMITED-TIER, MODERATE-TIER, HIGH-TIER; ALTER at :86 :90 :92, D-002). Job step PRVDAY01.jcl STEP040 (PRVRSK01).
Decisions: D-002 FIX: the risk tier is saved; the table that drives it is now data the administrator can change; D-023 FIX: only ADMIN changes the table; D-024 FIX: refusals are surfaced

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: GET /api/v1/risk-rules, PUT /api/v1/risk-rules. Roles: ADMIN.


#### MAM-93 Console: run the batch jobs and download their legacy files (admin)

Type: Story. Service: web-ui. Labels: svc-web-ui, kag-MAM-93, agentic, decision-D-020, decision-D-017, decision-D-015, decision-D-025, decision-D-024, decision-D-009, decision-D-023, decision-D-004. Depends on: MAM-62, MAM-56, MAM-32, MAM-45, MAM-55, MAM-67, MAM-71, MAM-75.

##### Business requirement
The scheduled jobs of the old system (enrolment intake, screening, revalidation sweep, sanction intake, directory extract and the monthly extract for the federal reporting feed) can be run on demand by an administrator for a business date that the administrator chooses. The page shows each run with its date, status and counts, shows why a run failed, and lets the administrator download the legacy-format files that the runs produce, so they can be compared with the old system.

##### Acceptance criteria
###### AC-1 The form asks for an explicit business date and, where needed, a file
Given admin1 is signed in
When /console/admin/jobs opens and admin1 picks each job in turn
Then The page shows a 'Business date (MM/DD/YYYY)' input that is empty and is not filled in by the page, a Job list with Enrolment intake (APPINPUT), Screening run, Revalidation sweep, Sanction intake (SANCIN), Directory extract (DIROUT) and T-MSIS extract, a 'Run job' button and a 'Recent runs' table; picking Enrolment intake or Sanction intake adds a required file input and an optional 'Batch id' of at most 8 characters; 'Run job' with no valid date, or with no file for an intake job, sends nothing and shows field errors
Basis: modern/docs/ux/wireframes/console-batch-jobs.html, modern/docs/ux/screen-inventory.md (console batch-jobs), D-020, D-017, modern/contracts/openapi/enrollment-service.yaml#FileRunRequest, modern/contracts/openapi/sanctions-service.yaml#FileRunRequest

###### AC-2 Run the revalidation sweep (journey J6)
Given admin1 is on the page
When They choose Revalidation sweep, enter 03/31/2026 and choose 'Run job'
Then The page sends POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31, receives 202 and adds a row to 'Recent runs' with Job 'Revalidation sweep', Business date 03/31/2026, the Status in words (Running, Completed or Failed) and the Counts
Basis: modern/docs/ux/journeys.md#J6 (step 1), D-020, D-004, modern/contracts/openapi/revalidation-service.yaml#startRevalidationSweep, D-029

###### AC-3 Run the screening, directory and T-MSIS jobs (journey J7)
Given admin1 is on the page
When They run Screening run, Directory extract and T-MSIS extract, each for 03/31/2026
Then The page sends POST /api/v1/batch/screening-runs, POST /api/v1/batch/directory-extracts and POST /api/v1/batch/tmsis-extracts with businessDate 2026-03-31 and adds a row for each; the T-MSIS runs are also listed from GET /api/v1/batch/tmsis-extracts, and each new T-MSIS run adds a new row there (a new archive generation)
Basis: modern/docs/ux/journeys.md#J7 (steps 1 and 2), D-015, D-020, modern/contracts/openapi/tmsis-service.yaml#listTmsisExtracts, modern/contracts/openapi/screening-service.yaml#startScreeningRun, modern/contracts/openapi/directory-service.yaml#startDirectoryExtract, modern/docs/ux/wireframes/console-batch-jobs.html

###### AC-4 File intake runs upload the file and follow the run
Given admin1 picks Enrolment intake and then Sanction intake, each with a legacy file, business date 03/31/2026 and the batch id 'B0331'
When They choose 'Run job'
Then The page sends a multipart POST /api/v1/batch/intake-runs and a multipart POST /api/v1/batch/sanction-intake-runs with the file, businessDate 2026-03-31 and batchId B0331, and then refreshes the run from GET /api/v1/batch/intake-runs/{runId} and GET /api/v1/batch/sanction-intake-runs/{runId} until its status is Completed or Failed, showing the counts the service returns
Basis: D-017, D-020, D-025, modern/contracts/openapi/enrollment-service.yaml#startIntakeRun, modern/contracts/openapi/enrollment-service.yaml#getIntakeRun, modern/contracts/openapi/sanctions-service.yaml#getSanctionIntakeRun, modern/docs/analysis/job-flow.md (PRVDAY01 STEP010, PRVSAN01 SANCIN)

###### AC-5 Failed runs and refusals are shown
Given A run ends with status FAILED and an error text, and other requests are answered with 400, 403 or 422
When The page shows them
Then The failed run's row shows the status 'Failed' and the error text returned for it; a 400 or 422 shows the problem detail in an error summary at the form; a 403 shows 'You are not allowed to run jobs'; no failure is shown as Completed or silently dropped
Basis: D-024, modern/contracts/openapi/enrollment-service.yaml#BatchRun (error), modern/contracts/openapi/enrollment-service.yaml#startIntakeRun (400, 403, 422), modern/contracts/openapi/tmsis-service.yaml#startTmsisExtract (400, 403)

###### AC-6 The legacy files can be downloaded (journeys J6 and J7)
Given Completed runs of the T-MSIS extract, the directory extract, the revalidation sweep, the screening run and the enrolment intake
When admin1 chooses 'Download' on each run
Then The page requests GET /api/v1/batch/tmsis-extracts/{runId}/file (TMSISOUT, 120-byte records), GET /api/v1/batch/directory-extracts/{runId}/file (DIROUT, 100), GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file (REVALQ, 80), GET /api/v1/batch/screening-runs/{runId}/results-file (SCRNOUT, 67) and GET /api/v1/batch/intake-runs/{runId}/error-file (APPERROR, 250) with the administrator's token and saves each file exactly as received, byte for byte; the sanction intake run has no file; a 404 shows 'The file is not available (yet)'
Basis: modern/docs/ux/journeys.md#J6 (step 4), modern/docs/ux/journeys.md#J7 (steps 1 and 2), ADR-005, MODERNIZATION.md section 6 rule 3 (legacy-format adapters), D-015, QE-7, modern/contracts/openapi/tmsis-service.yaml#getTmsisExtractFile, modern/contracts/openapi/directory-service.yaml#getDirectoryExtractFile, modern/contracts/openapi/revalidation-service.yaml#getRevalidationQueueFile, modern/contracts/openapi/screening-service.yaml#getScreeningResultsFile, modern/contracts/openapi/enrollment-service.yaml#getIntakeErrorFile

###### AC-7 Only administrators open the page
Given admin1, analyst1, supervisor1 and provider1 are signed in
When Each opens /console/admin/jobs
Then admin1 sees the page; the others see no menu entry and the access-denied page when they type the address; every batch request is made with the administrator's token
Basis: D-023, modern/docs/ux/screen-inventory.md (console batch-jobs, ADMIN), modern/docs/architecture/nfr.md (batch endpoints: ADMIN, SYSTEM)

###### AC-8 The batch page has zero axe violations
Given The Playwright test opens the empty page, the page with an intake job selected, the page with runs and the page with a failed run
When It runs an axe scan on each state and runs a job with the keyboard only
Then Every scan reports zero violations; every input has a visible label; the runs table has header cells; run status is text, never colour alone; the Download buttons name the job and date they belong to
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/architecture/nfr.md (Accessibility), modern/docs/ux/journeys.md#J6, modern/docs/ux/journeys.md#J7

##### Out of scope
- The jobs themselves and their schedules (the services, ADR-004)
- The PRVDAY01 SORT step (D-009, dropped)
- The export of the provider master in the 130-byte layout (GET /api/v1/legacy/provider-master has no screen in the inventory)
- Comparing the legacy files with the legacy system's own output (Phase 9 parity)
- Status polling for the screening run, revalidation sweep and directory extract beyond the 202 answer (QE-7)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: JCL/PRVDAY01.jcl (STEP010 PRVENR01, STEP030 PRVSCR01, STEP040 PRVRSK01; IF at :38; SORT :43-50 dropped by D-009), JCL/PRVMTH01.jcl (STEP010 PRVREV01, STEP020 PRVTMS01, STEP030 IEBGENER archive), PRVSAN01.cbl (SANCIN intake, run by no JCL), PRVDIR01.cbl (DIROUT, run by no JCL), REXX/PRVAUTOM.rexx:15 (SUBMIT of PRVDAY01, replaced by schedules and this page).
Decisions: D-020 FIX: the business date is an explicit job parameter (the legacy &DATE and &BATCH_DATE are defined nowhere); D-017 FIX: the batch id is a real job parameter; D-015 PRESERVE: the T-MSIS legacy-format file with YYDDD period stays; ISO dates internally; D-025 PRESERVE (Q-009): the intake file is PRVRECRD in bytes 1-130, bytes 131-200 reserved; D-024 FIX: a result per run and failures surfaced; D-009 DROP: the SORT step has no job and no AC; D-023 FIX: ADMIN only; D-004 FIX: the business date is entered as MM/DD/YYYY and sent as ISO

##### Technical notes (agent-owned)
Service: web-ui. Endpoints/events: POST /api/v1/batch/intake-runs, GET /api/v1/batch/intake-runs/{runId}, GET /api/v1/batch/intake-runs/{runId}/error-file, POST /api/v1/batch/screening-runs, GET /api/v1/batch/screening-runs/{runId}/results-file, POST /api/v1/batch/revalidation-sweeps, GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file, POST /api/v1/batch/sanction-intake-runs, GET /api/v1/batch/sanction-intake-runs/{runId}, POST /api/v1/batch/directory-extracts, GET /api/v1/batch/directory-extracts/{runId}/file, POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file. Roles: ADMIN.


### MAM-14 End-to-end and hardening

#### MAM-69 Journey J1 end to end: a provider applies and is enrolled (main journey)

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-69, agentic, decision-D-001, decision-D-005, decision-D-002, decision-D-019, decision-D-003, decision-D-012, decision-D-028, slice-main-journey. Depends on: MAM-62, MAM-87, MAM-70, MAM-73, MAM-64, MAM-66, MAM-68, MAM-85, MAM-47, MAM-50, MAM-46, MAM-54, MAM-17, MAM-21, MAM-23, MAM-18, MAM-25, MAM-35, MAM-27, MAM-61, MAM-63, MAM-58.

##### Business requirement
A new provider must be able to apply online and, once the automatic checks and an analyst's decision are complete, be enrolled, appear in the public directory and be told so. This story proves the whole path works for real, from the portal to the notice, in the running system.

##### Acceptance criteria
###### AC-1 The provider signs in, applies and sees Validated
Given the stack is up at the seed state and provider1 (PROVIDER, NPI 1003000111) is signed in at the portal home
When provider1 chooses Apply and submits NPI 1003000111, the Medicaid ID that the dat-synthetic scenario assigns to it, type Individual (IN), taxonomy 207Q00000X, last name RIVERA, first name MARIA and licence MD12345 / TX
Then GET /api/v1/applications/{applicationId} returns status VALIDATED with no errors and the page /portal/applications/:applicationId shows the status Validated
Basis: modern/docs/ux/journeys.md#J1 steps 1-3, D-001, modern/contracts/openapi/enrollment-service.yaml (Application status), aws-transform/test-data/providers.txt, QF-1 (default applied)

###### AC-2 Registered Pending and Unscreened, then screened on all four sources
Given the application is VALIDATED
When the services have processed ApplicationValidated and ProviderRegistered
Then pem.provider.events carries ProviderRegistered for subject 1003000111 with enrollStatus P and screenFlag U, pem.screening.events carries ScreeningCompleted with reason INITIAL, result PASS and source results LICBOARD, OIGLEIE, SAMGOV and SSADMF each P, GET /api/v1/screenings?npi=1003000111 lists those four results, and GET /api/v1/providers/1003000111 shows screen flag P
Basis: modern/docs/ux/journeys.md#J1 step 4, D-005, modern/contracts/asyncapi/events.yaml (ProviderRegistered, ScreeningCompleted), modern/contracts/openapi/screening-service.yaml (SourceResult), modern/docs/architecture/overview.md (main flows 1 and 2)

###### AC-3 Risk tier Limited is assigned and saved
Given screening passed for taxonomy 207Q00000X
When risk-service has handled ScreeningCompleted
Then pem.risk.events carries RiskTierAssigned with tier L for 1003000111 and GET /api/v1/providers/1003000111 shows risk tier L (Limited)
Basis: modern/docs/ux/journeys.md#J1 step 4, D-002, Q-005, modern/contracts/asyncapi/events.yaml (RiskTierAssigned)

###### AC-4 The analyst sees it under Pending approval
Given the provider is Pending with screen flag P and tier L
When analyst1 opens the console work queues at /console/queues
Then 1003000111 is listed under Pending approval and not under Failed screening
Basis: modern/docs/ux/journeys.md#J1 step 5, modern/docs/ux/screen-inventory.md (console work-queues), D-019

###### AC-5 Approval sets Active and the revalidation due date
Given analyst1 is on /console/providers/1003000111/approval
When analyst1 approves
Then POST /api/v1/providers/1003000111/approval with decision APPROVE returns the provider with status A and a revalidation due date exactly five years after the approval date, and pem.provider.events carries ProviderStatusChanged from P to A with that revalidationDueDate
Basis: modern/docs/ux/journeys.md#J1 step 6, D-019, D-003, modern/contracts/openapi/provider-service.yaml (decideProvider), modern/contracts/asyncapi/events.yaml (ProviderStatusChanged)

###### AC-6 The provider appears in the public directory
Given the provider is Active
When anyone without a token calls GET /api/v1/directory/providers?name=RIVERA and searches RIVERA on /directory
Then 1003000111 is listed in both with the name MARIA RIVERA
Basis: modern/docs/PROGRESS.md (Current run checklist item 4: main journey slice includes the directory), modern/docs/architecture/overview.md (main flows 3 and 6), modern/docs/adr/ADR-006-fhir-scope.md, modern/docs/adr/ADR-007-authentication-and-roles.md, D-028, Q-008

###### AC-7 The notice is visible in Mailpit and in the portal
Given ProviderStatusChanged from P to A
When notification-service has handled it
Then pem.notification.events carries NoticeSent with template APPROVED for 1003000111, the email Enrolment approved addressed to 1003000111@example.test is visible in Mailpit at http://localhost:8025, /portal/notices shows Enrolment approved to provider1, and /portal/provider shows Active (A)
Basis: modern/docs/ux/journeys.md#J1 step 7, modern/docs/PROGRESS.md (Current run checklist item 4: Mailpit notice), D-012, .claude/skills/java-microservices/SKILL.md section 3 (mailpit), modern/contracts/asyncapi/events.yaml (NoticeSent), QF-3 (default applied)

###### AC-8 Repeatable and accessible
Given the journey as one Playwright test in modern/web-ui/e2e/journeys/ with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/ux/journeys.md (introduction), modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- Rejection and failure paths (J2, J3)
- Sanctions, revalidation and extracts (J5 to J7)
- Security and performance checks (nfr stories)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:128-159 (enrolment validation, D-001); BATCH/PRVSCR01.cbl:116-132,146-149 (stub checks, D-005); BATCH/PRVRSK01.cbl:70-76,86-97,103-116,126-137 (risk tier and due date, D-002, D-003); BATCH/PRVSAN01.cbl:112 (the only legacy statement that sets ACTIVE, D-019); JCL/PRVDAY01.jcl STEP010, STEP030, STEP040
Decisions: D-001 FIX: every application is processed; a valid one is registered Pending and Unscreened; D-005 REPLACE: screening runs against the mocked sources; D-002 FIX: the risk tier is saved; D-019 FIX: an analyst approval sets ACTIVE after a Pass screening; D-003 FIX: the due date is set at approval (approval + 5 years); D-012 REPLACE: the notice is sent by notification-service; D-028 FIX: the directory shows the composed name

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/applications, GET /api/v1/applications/{applicationId}, GET /api/v1/providers, GET /api/v1/providers/{npi}, GET /api/v1/screenings, POST /api/v1/providers/{npi}/approval, GET /api/v1/directory/providers, GET /api/v1/notices, ApplicationValidated, ProviderRegistered, ScreeningCompleted, RiskTierAssigned, ProviderStatusChanged, NoticeSent. Roles: PROVIDER, ANALYST, PUBLIC.


#### MAM-72 Journey J2 end to end: an application is rejected

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-72, agentic, decision-D-001, decision-D-016, decision-D-012. Depends on: MAM-64, MAM-66, MAM-70, MAM-46, MAM-54, MAM-58, MAM-50.

##### Business requirement
When an application is incomplete, the provider must be told exactly which field is wrong, nothing must be enrolled, and a notice must record the rejection. This story proves that the rejection path works for real in the running system.

##### Acceptance criteria
###### AC-1 The error summary links to the field
Given the stack is at the seed state and provider1 is signed in at /portal/apply
When provider1 enters the J1 values for NPI 1003000111 but leaves the taxonomy empty, and submits
Then an error summary at the top of the form lists E104 and links to the taxonomy field, E104 is shown next to the taxonomy field, and no other field shows an error
Basis: modern/docs/ux/journeys.md#J2 steps 1-2, D-016, .claude/skills/frontend-stack/SKILL.md section 5, QF-6 (default applied)

###### AC-2 Nothing is registered and the rejection is published
Given the rejected submission
When the services have handled it
Then GET /api/v1/providers/1003000111 with an ANALYST token returns 404, no ProviderRegistered event exists for 1003000111, and pem.enrollment.events carries ApplicationRejected whose errors list contains only E104
Basis: modern/docs/ux/journeys.md#J2 step 2, D-001, modern/contracts/asyncapi/events.yaml (ApplicationRejected)

###### AC-3 The application shows Rejected with exactly one error row
Given the rejected application
When provider1 opens /portal/applications/:applicationId
Then the status is Rejected and the error table has exactly one row, code E104 for the taxonomy field
Basis: modern/docs/ux/journeys.md#J2 step 3, D-001, D-016, modern/docs/ux/screen-inventory.md (portal application-status)

###### AC-4 The provider is told the application was rejected
Given ApplicationRejected was published
When provider1 opens /portal/notices
Then the notice Application rejected is listed, and GET /api/v1/notices shows template REJECTED for 1003000111
Basis: modern/docs/ux/journeys.md#J2 step 4, D-012, modern/docs/architecture/overview.md (notification-service consumes ApplicationRejected), modern/contracts/openapi/notification-service.yaml (Notice template)

###### AC-5 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- Rejection through the legacy intake file (enr-intake-run)
- Several failed checks on one application
- Screening failures (J3)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVENR01.cbl:128-159 (GO TO INVALID-APP at 132, 139, 144; only EC-MISSING-NPI is ever used at 129; D-001, D-016); JCL/PRVDAY01.jcl STEP010
Decisions: D-001 FIX: an invalid application is rejected with exactly one error row per failed check and never registered; D-016 FIX: E101-E104 are real validation results, here E104; D-012 REPLACE: the rejection notice is sent by notification-service

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/applications, GET /api/v1/applications/{applicationId}, GET /api/v1/providers/{npi}, GET /api/v1/notices, ApplicationRejected, NoticeSent. Roles: PROVIDER, ANALYST.


#### MAM-74 Journey J3 end to end: screening fails and the analyst denies

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-74, agentic, decision-D-005, decision-D-019, decision-D-016, decision-D-012. Depends on: MAM-87, MAM-22, MAM-85, MAM-80, MAM-47, MAM-58, MAM-50.

##### Business requirement
When the automatic checks find a problem, such as an owner on a federal exclusion list, the analyst must see the failure with its reason and be able to deny the enrolment, and the provider must be told. This story proves that path works for real in the running system.

##### Acceptance criteria
###### AC-1 Screening fails with E203 owner excluded
Given the application for NPI 1003000444 (type GR mapped from GRP, legal name EXCLUDED CLINIC LLC, owner NPI 1990000111, which is on the mocked LEIE, with the Medicaid ID and taxonomy of the dat-synthetic scenario) submitted by the test set-up as analyst1
When the provider is registered and screened
Then ScreeningCompleted for 1003000444 has result FAIL, the OIGLEIE source result is F with errorCode E203 (owner excluded), and GET /api/v1/providers/1003000444 shows screen flag F and status P
Basis: modern/docs/ux/journeys.md#J3 step 1, D-005, D-016, aws-transform/test-data/leie.txt, aws-transform/test-data/providers.txt, QF-2 (default applied)

###### AC-2 The analyst sees it under Failed screening
Given the failed screening
When analyst1 opens /console/queues
Then 1003000444 is listed under Failed screening and not under Pending approval
Basis: modern/docs/ux/journeys.md#J3 step 2, modern/docs/ux/screen-inventory.md (console work-queues)

###### AC-3 The detail shows the failing source
Given analyst1 opens /console/providers/1003000444
When the screening table is read
Then it shows source OIGLEIE with result Fail (F), its detail text, and the error E203 owner excluded
Basis: modern/docs/ux/journeys.md#J3 step 3, modern/docs/ux/screen-inventory.md (console provider-detail), D-005

###### AC-4 The analyst denies with a reason
Given analyst1 is on /console/providers/1003000444/approval
When analyst1 chooses Deny and enters a reason
Then POST /api/v1/providers/1003000444/approval with decision DENY returns the provider with status D, and pem.provider.events carries ProviderStatusChanged from P to D
Basis: modern/docs/ux/journeys.md#J3 step 4, D-019, modern/contracts/openapi/provider-service.yaml (decideProvider, ApprovalRequest)

###### AC-5 A denial notice is sent
Given ProviderStatusChanged from P to D
When notification-service has handled it
Then GET /api/v1/notices lists a notice with template DENIED for 1003000444 and the email addressed to 1003000444@example.test is visible in Mailpit
Basis: modern/docs/ux/journeys.md#J3 step 4, D-012, modern/contracts/openapi/notification-service.yaml (Notice template), QF-3 (default applied)

###### AC-6 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- Failures from the licence board, SAM or SSA DMF (screening stories)
- Approval of a provider whose screening passed (J1)
- Rescreening (J6)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVSCR01.cbl:85-93,114-132,146-149 (stub checks, D-005; screening results never populated); BATCH/PRVSAN01.cbl:112 (only statement that sets ACTIVE, D-019); JCL/PRVDAY01.jcl STEP030
Decisions: D-005 REPLACE: real screening against mocked sources produces the failure; D-019 FIX: the analyst's decision sets the status; here DENIED; D-016 FIX: E203 owner excluded is a real screening result; D-012 REPLACE: the denial notice is sent by notification-service

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/applications, GET /api/v1/providers/{npi}, GET /api/v1/screenings, POST /api/v1/providers/{npi}/approval, GET /api/v1/notices, ProviderRegistered, ScreeningCompleted, ProviderStatusChanged, NoticeSent. Roles: ANALYST.


#### MAM-76 Journey J4 end to end: analyst inquiry (PINQ replacement)

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-76, agentic, decision-D-022, decision-D-023, decision-D-006. Depends on: MAM-80, MAM-77, MAM-62, MAM-37, MAM-40, MAM-69, MAM-50.

##### Business requirement
Analysts must be able to look up any provider by NPI or Medicaid ID and see everything the old inquiry screen held plus the status message it never showed, while providers can see only their own record. This story proves the inquiry and its access control work for real in the running system.

##### Acceptance criteria
###### AC-1 Search by NPI
Given provider 1003000111 is enrolled (the test set-up replays the J1 API steps) and analyst1 is on /console/providers
When analyst1 types NPI 1003000111 and searches
Then GET /api/v1/providers?npi=1003000111 is called and the provider is shown
Basis: modern/docs/ux/journeys.md#J4 step 1, D-022, modern/docs/ux/screen-inventory.md (console provider-search), QF-6 (default applied)

###### AC-2 Search by Medicaid ID
Given the same provider and its Medicaid ID
When analyst1 searches by that Medicaid ID
Then GET /api/v1/providers?medicaidId=<id> is called and the same provider is shown
Basis: modern/docs/ux/journeys.md#J4 step 1, modern/docs/ux/screen-inventory.md (R-PRVCICS1-ADD-07 extra search inputs), D-022

###### AC-3 The detail shows every field with its meaning
Given analyst1 opens /console/providers/1003000111
When the page is read
Then it shows the NPI in full, Medicaid ID, type Individual (IN), taxonomy 207Q00000X, risk tier Limited (L), status Active (A) as text and code, screen flag Pass (P), effective and revalidation due dates as MM/DD/YYYY, last name, first name, licence number and state, and panels for screening per source, sanctions, revalidation and notices
Basis: modern/docs/ux/journeys.md#J4 step 2, modern/docs/ux/screen-inventory.md (PINQ field map), .claude/skills/frontend-stack/SKILL.md section 6, D-022, D-006

###### AC-4 Audit history is shown to a supervisor
Given supervisor1 opens the same provider detail
When the page is read
Then it also shows the audit history from GET /api/v1/providers/1003000111/audit, while analyst1's page shows no audit panel and makes no call to it
Basis: modern/docs/architecture/nfr.md (Security: audit is SUPERVISOR and ADMIN), modern/contracts/openapi/provider-service.yaml (getProviderAudit x-roles), QF-11 (default applied), modern/docs/ux/journeys.md#J4 step 2

###### AC-5 An unknown NPI says No provider found
Given analyst1 is on /console/providers
When analyst1 searches for an NPI that no provider has (1003000999)
Then the page shows No provider found, and the API returns an empty page rather than an error
Basis: modern/docs/ux/journeys.md#J4 step 3, D-022, modern/docs/ux/screen-inventory.md (WS-MSG PROVIDER NOT FOUND)

###### AC-6 A provider cannot use the console and sees only their own record
Given provider1 is signed in
When provider1 opens /console/providers, calls GET /api/v1/providers and GET /api/v1/providers/{npi} for another NPI, and opens /portal/provider
Then the console route shows the 403 refused page, both API calls return 403, and the portal shows only provider1's own record
Basis: modern/docs/ux/journeys.md#J4 step 4, D-023, modern/docs/architecture/nfr.md (Security), .claude/skills/frontend-stack/SKILL.md section 6

###### AC-7 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- Changing a provider from the inquiry screen
- Role tests for every endpoint (nfr-security)
- Admin view of revalidation cases (the revalidations endpoint allows ANALYST and SUPERVISOR only, QF-11)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (NPI typed on the screen never reaches the key, no status message sent, no authorisation; D-022, D-023); ONLINE/PRVCICS1.cbl:47-72 (WS-MSG built but not sent)
Decisions: D-022 REPLACE: console inquiry by NPI or Medicaid ID that shows the status; D-023 FIX: role-based access; a PROVIDER cannot open console routes or other providers' records; D-006 FIX: status is shown as text and code (Suspended (S) included)

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}, GET /api/v1/providers/{npi}/audit. Roles: ANALYST, SUPERVISOR, PROVIDER.


#### MAM-79 Journey J5 end to end: a supervisor suspends and reinstates a provider

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-79, agentic, decision-D-006, decision-D-018, decision-D-026, decision-D-012, decision-D-013, decision-D-027. Depends on: MAM-90, MAM-52, MAM-33, MAM-58, MAM-61, MAM-73, MAM-50, MAM-51.

##### Business requirement
A supervisor must be able to suspend a provider and later reinstate them, with the provider told each time, the public directory following the status, and the full history kept. This story proves that path works for real in the running system.

##### Acceptance criteria
###### AC-1 The supervisor opens Record sanction from an active provider
Given supervisor1 is signed in and the seeded ACTIVE individual TESTPROV ALPHA exists
When supervisor1 opens the provider detail and chooses Record sanction
Then the page /console/providers/:npi/sanctions/new is shown with the action, reason and effective date fields
Basis: modern/docs/ux/journeys.md#J5 step 1, modern/docs/ux/screen-inventory.md (console record-sanction)

###### AC-2 Suspend sets Suspended and sends notice SUS
Given the Record sanction page
When supervisor1 chooses Suspend (S), enters a reason and an effective date, and submits
Then POST /api/v1/sanctions returns 201, GET /api/v1/providers/{npi} shows status S (Suspended), pem.provider.events carries ProviderStatusChanged from A to S published by provider-service, pem.sanctions.events carries SanctionApplied with action S, and a notice with template SUS is listed by GET /api/v1/notices and visible in Mailpit
Basis: modern/docs/ux/journeys.md#J5 step 2, D-006, D-013, D-012, D-018, modern/contracts/asyncapi/events.yaml (SanctionApplied)

###### AC-3 The suspended provider leaves the public directory
Given the provider is Suspended
When anyone without a token searches GET /api/v1/directory/providers and /directory for ALPHA
Then the provider is not listed
Basis: modern/docs/ux/journeys.md#J5 step 3, modern/docs/architecture/overview.md (main flow 6: active providers only), D-006

###### AC-4 Reinstate sets Active and sends notice RST
Given the provider is Suspended
When supervisor1 records action Reinstate (R)
Then GET /api/v1/providers/{npi} shows status A (Active), SanctionApplied carries action R, and a notice with template RST is listed and visible in Mailpit
Basis: modern/docs/ux/journeys.md#J5 step 4, D-006, D-012, D-027

###### AC-5 The history shows both actions
Given both sanctions were recorded
When GET /api/v1/sanctions?npi=<npi> is called and the detail sanctions panel is opened
Then both the Suspend and the Reinstate are listed, in order, with their reasons and effective dates
Basis: modern/docs/ux/journeys.md#J5 step 4, D-018

###### AC-6 An unknown action code is rejected
Given the provider is Active
When POST /api/v1/sanctions is called with action X
Then it answers 422 with a problem detail that names the field action, no sanction is stored, and the provider's status is unchanged
Basis: modern/docs/ux/journeys.md#J5 step 5, D-026, modern/contracts/openapi/sanctions-service.yaml (recordSanction 422; SanctionRequest action)

###### AC-7 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- Termination (T) paths and the SANCIN batch file (san-intake-run)
- Sanction rules that depend on the current status (Q-006: not applied)
- Role tests for the sanctions endpoints (nfr-security)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVSAN01.cbl:104-116 (apply T, S, R; unknown codes accepted, D-026; no status check, D-027); :109-110 (S sets DENIED, D-006); :115 (REWRITE of the master, D-013); :124-138 (dynamic CALL of PRVNT*, D-012)
Decisions: D-006 FIX: Suspend sets the new SUSPENDED status S; D-018 FIX: sanction history is kept with reason and effective date; D-026 FIX: unknown action codes are rejected with an error; D-012 REPLACE: notices SUS and RST are sent by notification-service; D-013 FIX: sanctions-service changes the status through provider-service's API; D-027 PRESERVE: a reinstate applies whatever the current status (Q-006)

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/sanctions, GET /api/v1/sanctions, GET /api/v1/providers/{npi}, GET /api/v1/directory/providers, GET /api/v1/notices, SanctionApplied, ProviderStatusChanged, NoticeSent. Roles: SUPERVISOR, PUBLIC.


#### MAM-81 Journey J6 end to end: monthly revalidation

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-81, agentic, decision-D-003, decision-D-029, decision-D-020, decision-D-024. Depends on: MAM-48, MAM-45, MAM-28, MAM-93, MAM-85, MAM-50, MAM-51.

##### Business requirement
Every month the administrator must be able to find the providers whose revalidation is due, see each of them once as a case, have them checked again automatically, and receive the legacy queue file. This story proves that monthly cycle works for real in the running system.

##### Acceptance criteria
###### AC-1 The administrator runs the sweep for a business date
Given admin1 is on /console/admin/jobs at the seed state
When admin1 runs the revalidation sweep for the business date 03/31/2026
Then POST /api/v1/batch/revalidation-sweeps with businessDate 2026-03-31 returns a run that ends COMPLETED, shows a queued count equal to the number of ACTIVE providers whose due date is on or before 2026-03-31, and shows no failure reason
Basis: modern/docs/ux/journeys.md#J6 step 1, D-020, D-024, R-PRVREV01-cbl-00003, R-PRVREV01-cbl-00004

###### AC-2 Each due provider has exactly one case
Given the completed sweep
When analyst1 opens /console/queues and GET /api/v1/revalidations?status=DUE is called
Then each due provider appears once under Revalidation due, and no provider that is not due or not ACTIVE has a case
Basis: modern/docs/ux/journeys.md#J6 step 2, D-029

###### AC-3 Running the sweep again does not duplicate cases
Given the same sweep was already run for 2026-03-31
When admin1 runs it again for the same date
Then the second run completes and the number of cases per provider is still one
Basis: D-029, modern/docs/architecture/nfr.md (Availability: batch jobs are idempotent), modern/docs/adr/ADR-004-batch-and-scheduling.md

###### AC-4 Re-screening completes the case and sets the next due date
Given the cases are DUE
When RevalidationDue events are handled and re-screening passes
Then pem.screening.events carries ScreeningCompleted with reason REVALIDATION and result PASS for each due provider, each case becomes COMPLETED, and GET /api/v1/providers/{npi} shows a new revalidation due date later than 2026-03-31
Basis: modern/docs/ux/journeys.md#J6 step 3, D-003, modern/docs/architecture/overview.md (main flow 4), modern/contracts/asyncapi/events.yaml (RevalidationDue, ScreeningCompleted), modern/contracts/openapi/revalidation-service.yaml (RevalidationCase status)

###### AC-5 The REVALQ file can be downloaded
Given the completed sweep run
When admin1 downloads GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file
Then every record is 80 bytes with NPI in bytes 1-10, the due date in bytes 11-18 as CCYYMMDD, the text RESCREEN-REQUIRED starting at byte 19 and spaces to byte 80, and the number of records equals the queued count
Basis: modern/docs/ux/journeys.md#J6 step 4, modern/docs/analysis/data-layouts.md (REVAL-OUT-RECORD), R-PRVREV01-cbl-00005, D-004, modern/docs/adr/ADR-005-legacy-format-adapters.md

###### AC-6 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- Failed re-screening and its follow-up
- Scheduled (cron) runs of the sweep
- Revalidation notices (not part of the journey)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVREV01.cbl:64-94 (sweep of ACTIVE providers due today or earlier, no state kept; D-029); BATCH/PRVRSK01.cbl:70-76,126-137 (due date reset on every run, D-003); JCL/PRVMTH01.jcl:14-22 (STEP010)
Decisions: D-003 FIX: the next due date is set when a revalidation is completed; D-029 FIX: one revalidation case per provider with its own state; D-020 FIX: the business date 2026-03-31 is an explicit job parameter; D-024 FIX: the run result shows its status, counts and failure reason

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/batch/revalidation-sweeps, GET /api/v1/revalidations, GET /api/v1/batch/revalidation-sweeps/{runId}/queue-file, GET /api/v1/providers/{npi}, RevalidationDue, ScreeningCompleted, ProviderStatusChanged. Roles: ADMIN, ANALYST, SYSTEM.


#### MAM-83 Journey J7 end to end: month-end extracts and the public directory

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-83, agentic, decision-D-015, decision-D-028, decision-D-020. Depends on: MAM-75, MAM-71, MAM-67, MAM-63, MAM-93, MAM-73, MAM-50, MAM-51.

##### Business requirement
At month end the administrator must be able to produce the federal reporting file and the managed-care directory file in their legacy formats, with the reporting file archived, and anyone must be able to find active providers in the public directory with properly written names. This story proves that works for real in the running system.

##### Acceptance criteria
###### AC-1 The T-MSIS extract is produced in the legacy layout
Given admin1 is on /console/admin/jobs at the seed state
When admin1 runs the T-MSIS extract for 03/31/2026 and downloads the TMSISOUT file
Then POST /api/v1/batch/tmsis-extracts with businessDate 2026-03-31 returns a run that ends COMPLETED, and every record of the file is 120 bytes with the record type PRV01 in bytes 1-5, the Medicaid ID in bytes 6-14, the NPI in bytes 15-24, status A in byte 25, the period 26090 in bytes 26-30 and spaces in bytes 31-120
Basis: modern/docs/ux/journeys.md#J7 step 1, D-015, D-020, Q-007, R-PRVTMS01-cbl-00043, modern/docs/analysis/data-layouts.md (WS-TMSIS-DETAIL)

###### AC-2 The run is archived and the event is published
Given the completed extract run
When GET /api/v1/batch/tmsis-extracts is called and pem.tmsis.events is read
Then the list shows the run (COMPLETED, business date 2026-03-31) as a new entry next to any earlier runs, and ExtractProduced carries the business date 2026-03-31, periodYyddd 26090 and a rowCount equal to the number of records in the downloaded file
Basis: modern/docs/ux/journeys.md#J7 step 1, modern/contracts/openapi/tmsis-service.yaml (listTmsisExtracts: runs and archive generations, GDG MMPRV.TMSIS.ARCHIVE), modern/contracts/asyncapi/events.yaml (ExtractProduced), D-015

###### AC-3 The directory extract is produced in the legacy layout
Given admin1 is on /console/admin/jobs
When admin1 runs the directory extract for 03/31/2026 and downloads the DIROUT file
Then POST /api/v1/batch/directory-extracts with businessDate 2026-03-31 returns a run that ends COMPLETED and every record is 100 bytes with the Medicaid ID in bytes 1-9, the NPI in 10-19, the type in 20-21, the risk tier in 22, the name in 23-82 and spaces in 83-100
Basis: modern/docs/ux/journeys.md#J7 step 2, modern/docs/analysis/data-layouts.md (DIR-OUT-RECORD), Q-008, D-020

###### AC-4 Names are composed, not raw bytes
Given the DIROUT record of the seeded individual TESTPROV ALPHA
When its name field is read
Then it contains the first name TESTPROV and then the last name ALPHA and no part of the licence number, and the record of a seeded organisation shows its legal name
Basis: D-028, modern/docs/ux/journeys.md#J7 step 3, R-PRVDIR01-ADD-02

###### AC-5 Only ACTIVE providers are in both files
Given the seeded providers in every status
When the NPIs in the TMSISOUT and DIROUT files are compared with GET /api/v1/providers?status=A
Then both files contain exactly the ACTIVE providers and none that is Pending, Terminated, Denied or Suspended
Basis: R-PRVTMS01-cbl-00004, R-PRVDIR01-cbl-00004, modern/docs/architecture/overview.md (main flows 6 and 7), D-006

###### AC-6 Anyone can search the public directory for HOSPICE
Given a visitor with no sign-in
When the visitor searches HOSPICE on /directory and GET /api/v1/directory/providers?name=HOSPICE is called with no token
Then only ACTIVE providers whose names contain HOSPICE are listed, each with a properly composed name, and the seeded TERMINATED hospice is not listed
Basis: modern/docs/ux/journeys.md#J7 step 3, D-028, Q-008, modern/docs/adr/ADR-007-authentication-and-roles.md

###### AC-7 Repeatable and accessible
Given the journey as one Playwright test with an axe scan on every page it visits
When it is run three times in a row, each time from the seed state
Then all three runs pass and every axe scan reports zero violations
Basis: .claude/commands/phase-8-e2e-hardening.md steps 2 and 4, modern/docs/architecture/nfr.md (Accessibility), QF-6 (default applied)

##### Out of scope
- The real CMS T-MSIS file layout (Q-007: later story)
- Address, phone and network data in the directory (Q-008)
- The FHIR resources (dir-fhir)
- The T-MSIS future-enrolment flag (tms-future-enrolment)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: BATCH/PRVTMS01.cbl:60-62,80,97-117 (ACTIVE only, status A, period YYDDD of the run date; D-015); BATCH/PRVDIR01.cbl:62-90 (ACTIVE only, name = first 60 bytes of the variant at :88; D-028, D-013); JCL/PRVMTH01.jcl:25-45 (STEP020, STEP030)
Decisions: D-015 PRESERVE: the legacy T-MSIS extract keeps the YYDDD period (26090 for business date 2026-03-31); D-028 FIX: directory names are composed from the name fields; D-020 FIX: the business date is an explicit job parameter

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: POST /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts, GET /api/v1/batch/tmsis-extracts/{runId}/file, POST /api/v1/batch/directory-extracts, GET /api/v1/batch/directory-extracts/{runId}/file, GET /api/v1/directory/providers, GET /api/v1/providers, ExtractProduced. Roles: ADMIN, PUBLIC.


#### MAM-84 Security: role tests for every endpoint, dependency check, no secrets (NFR)

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-84, agentic, decision-D-023. Depends on: MAM-29, MAM-26, MAM-36, MAM-50.

##### Business requirement
Only the right people may do the right things. Every screen and every service call must be restricted by role, a provider must never see another provider's data, no secret may be stored in the code, and known vulnerable libraries must not be shipped. This story proves it with tests rather than by inspection.

##### Acceptance criteria
###### AC-1 Every endpoint answers correctly for every role
Given the 39 endpoints in the Security table of nfr.md, tokens for PROVIDER (NPI 1003000111), ANALYST, SUPERVISOR, ADMIN and SYSTEM, and no token
When each endpoint is called with each token and without a token through the gateway
Then every role listed for the endpoint gets an answer that is not 401 or 403 (a PROVIDER on its own NPI), every other role gets 403, a call with no token to a protected endpoint gets 401, and the four PUBLIC endpoints answer 200 without a token
Basis: modern/docs/architecture/nfr.md (Security), modern/docs/adr/ADR-007-authentication-and-roles.md, D-023, .claude/commands/phase-8-e2e-hardening.md step 3, .claude/skills/java-microservices/SKILL.md section 9

###### AC-2 Contracts and the role table do not drift
Given the x-roles of every operation in the nine OpenAPI contracts and the table in nfr.md
When a drift test compares them
Then the test passes only when every operation has the same roles in both and fails naming the operation otherwise
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md (Consequences), modern/docs/architecture/nfr.md (Security)

###### AC-3 A provider only sees their own data
Given provider1 (NPI 1003000111) and data of other providers
When provider1 calls GET /api/v1/providers/{npi} for another NPI, and GET /api/v1/applications and GET /api/v1/notices with and without an npi filter for another provider
Then the first call returns 403 and the lists contain only provider1's own items
Basis: modern/docs/architecture/nfr.md (Security: a PROVIDER only sees their own data), .claude/skills/frontend-stack/SKILL.md section 6, D-023, QF-1 (default applied)

###### AC-4 Forged and expired tokens are refused
Given a token signed by another key, an expired token and a token for another realm
When each is sent through the gateway to a protected endpoint
Then each is refused with 401
Basis: modern/docs/adr/ADR-007-authentication-and-roles.md, .claude/skills/java-microservices/SKILL.md section 9

###### AC-5 No high or critical vulnerable dependency
Given the Gradle build and the web UI
When the OWASP dependency check and npm audit run
Then a report in modern/docs/reports lists the result and has no high or critical finding, or each one has a written justification
Basis: .claude/commands/phase-8-e2e-hardening.md step 3, .claude/skills/java-microservices/SKILL.md section 12

###### AC-6 No secret in any file
Given the repository
When git grep --untracked and git check-ignore modern/infra/.env.local are run
Then no password, token or key is found in any tracked or untracked file, and .env.local is ignored
Basis: .claude/commands/phase-5-platform.md step 5, MODERNIZATION.md sections 5 and 6, modern/docs/PROGRESS.md (Decisions 2026-11-18)

###### AC-7 Only health is open on the actuator
Given every service
When /actuator/info and /actuator/metrics are called without a token
Then both answer 401 while /actuator/health answers 200
Basis: QF-7 (default applied), .claude/skills/java-microservices/SKILL.md section 11

###### AC-8 An optional ZAP baseline scan of the gateway
Given the user has agreed to the optional scan
When the pinned zaproxy/zap-stable image scans http://localhost:8080
Then the report lists no high alert, or each one has a written justification; if the user has not agreed, the report states that the scan was not run
Basis: .claude/commands/phase-8-e2e-hardening.md step 3

##### Out of scope
- The role tests inside each service's own story (Phase 6)
- Penetration testing
- Production hardening and TLS

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-61 (any PINQ user could read any provider, D-023)
Decisions: D-023 FIX: role-based access (Keycloak roles) on every endpoint and screen, proved by tests

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}, GET /api/v1/applications, GET /api/v1/notices, GET /api/v1/directory/providers, GET /fhir/Practitioner, GET /fhir/Organization, GET /fhir/PractitionerRole. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM, PUBLIC.


#### MAM-86 Accessibility: WCAG 2.1 AA on every page, zero axe violations, keyboard-only walk-through (NFR)

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-86, agentic, decision-D-006. Depends on: MAM-62, MAM-64, MAM-66, MAM-68, MAM-70, MAM-73, MAM-77, MAM-80, MAM-85, MAM-87, MAM-90, MAM-92, MAM-93.

##### Business requirement
Every person must be able to use the portal, the console and the public directory, including people who use only a keyboard or a screen reader. This story proves that every page meets the agreed accessibility standard.

##### Acceptance criteria
###### AC-1 Zero axe violations on every page
Given the 13 screens of modern/docs/ux/screen-inventory.md (5 portal, 7 console, 1 public)
When each is opened in its main state in Playwright and scanned with axe for WCAG 2.0 and 2.1 level A and AA
Then every scan reports zero violations
Basis: modern/docs/architecture/nfr.md (Accessibility), .claude/skills/frontend-stack/SKILL.md section 5, .claude/commands/phase-8-e2e-hardening.md step 4

###### AC-2 Error, empty and refused states are scanned too
Given the apply form with errors, the provider search with No provider found, and a console route refused with 403
When each is scanned with axe
Then every scan reports zero violations and form errors appear next to their fields and in a summary linked to the fields
Basis: .claude/skills/frontend-stack/SKILL.md section 5, modern/docs/ux/journeys.md#J2 and #J4

###### AC-3 A keyboard-only pass is written up per journey
Given journeys J1 to J7
When each journey's main steps are completed using only the keyboard in Playwright
Then every journey completes, and a written report in modern/docs/reports lists the steps and the verdict for each of the seven journeys
Basis: .claude/commands/phase-8-e2e-hardening.md step 4, modern/docs/ux/journeys.md

###### AC-4 Focus is visible and the tab order is logical
Given every page
When the Tab key is pressed through the page
Then each interactive element shows a visible focus indicator and the focus order follows the visual order
Basis: .claude/skills/frontend-stack/SKILL.md section 5

###### AC-5 Contrast and status are not left to colour
Given every page
When the axe colour-contrast rule runs and the status badges are read
Then text contrast is at least 4.5:1, and every status shows text with its code, for example Suspended (S), never colour alone
Basis: .claude/skills/frontend-stack/SKILL.md sections 5 and 6, D-006

###### AC-6 Page structure
Given every page
When its structure is read
Then it has exactly one h1 and landmarks, every table has headers and every input has a visible label
Basis: .claude/skills/frontend-stack/SKILL.md section 5

###### AC-7 A violation breaks the run
Given a page with one axe violation
When make e2e is run
Then the run fails naming the page and the rule, and no violation is suppressed without a written justification in the repository
Basis: modern/docs/architecture/nfr.md (Accessibility), .claude/commands/phase-8-e2e-hardening.md (Exit checks)

##### Out of scope
- The Keycloak sign-in page itself
- Screen reader testing with real assistive technology
- Translations

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: ONLINE/PRVCICS1.cbl:28-72 (green-screen inquiry replaced by the console)
Decisions: D-006 FIX: status is always shown as text with its code, including Suspended (S)

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: none. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, PUBLIC.


#### MAM-88 Performance smoke: 10,000 synthetic providers, batch jobs and search against the NFR targets (NFR)

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-88, agentic. Depends on: MAM-44, MAM-50, MAM-45, MAM-75, MAM-37, MAM-46, MAM-18.

##### Business requirement
The system must stay responsive and finish its monthly jobs in a reasonable time when it holds a realistic number of providers. This story fills the system with 10,000 fictitious providers and measures it against the agreed targets, saying plainly which targets are still only placeholders.

##### Acceptance criteria
###### AC-1 A larger synthetic set is generated and loaded
Given the deterministic generator with its recorded seed
When it generates 10,000 providers and they are loaded into the stack
Then the 130-byte provider file holds 10,000 records, GET /api/v1/providers reports a total of at least 10,000, and every NPI and name passes the synthetic-data rules
Basis: .claude/commands/phase-8-e2e-hardening.md step 5, .claude/skills/synthetic-data/SKILL.md section 1, D-007

###### AC-2 Search and detail respond within the target
Given 20 concurrent users with k6 (pinned Docker image)
When they call GET /api/v1/providers and GET /api/v1/providers/{npi} through the gateway
Then the k6 summary shows a p95 under 500 ms for each
Basis: modern/docs/architecture/nfr.md (Performance: search and detail), .claude/commands/phase-8-e2e-hardening.md step 5

###### AC-3 Submit and screening respond within the target
Given the 10,000-provider stack
When applications are submitted with POST /api/v1/applications and single providers are screened against the mocked sources
Then the p95 of a submit is under 1 s and a screening of one provider takes under 5 s
Basis: modern/docs/architecture/nfr.md (Performance)

###### AC-4 The monthly jobs finish within the proportional target
Given the 10,000-provider stack
When the revalidation sweep and the T-MSIS extract are run
Then each completes within 90 seconds (the 15-minute target for 100,000 providers scaled to 10,000), and the report says this is a projection
Basis: modern/docs/architecture/nfr.md (Performance: 100,000 providers in under 15 minutes), QF-10 (default applied), .claude/commands/phase-8-e2e-hardening.md step 5

###### AC-5 The report states every result and which targets are DEFAULT
Given the measurements
When the report in modern/docs/reports is read
Then it lists each target with the measured value and met or not met, marks every target still marked DEFAULT in nfr.md as DEFAULT, and lists any target not met without tuning it away
Basis: .claude/commands/phase-8-e2e-hardening.md step 5, modern/docs/architecture/nfr.md (Performance: DEFAULT until the user gives volumes), MODERNIZATION.md section 6 rule 8

##### Out of scope
- A 100,000-provider load (QF-10 option B)
- Tuning and fixes for any missed target (separate Defect stories after the user agrees)
- Availability and failover testing

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: JCL/PRVMTH01.jcl:14-45 (monthly sweep and extract jobs); JCL/PRVDAY01.jcl:55-63 (STEP030 screening run)
Decisions: none

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: GET /api/v1/providers, GET /api/v1/providers/{npi}, POST /api/v1/applications, POST /api/v1/screenings, POST /api/v1/batch/revalidation-sweeps, POST /api/v1/batch/tmsis-extracts. Roles: ANALYST, ADMIN, PROVIDER.


#### MAM-89 Traceability report (rule -> story -> AC -> test -> code) and runbooks

Type: Story. Service: e2e. Labels: svc-e2e, kag-MAM-89, agentic. Depends on: MAM-69, MAM-72, MAM-74, MAM-76, MAM-79, MAM-81, MAM-83, MAM-84, MAM-86, MAM-88.

##### Business requirement
For compliance and for confidence, every legacy business rule that was kept must be traceable to the work item that implements it, the acceptance criterion that states it, the automated test that proves it and the code that carries it, with a plain list of anything not yet proven. Operations staff also need written runbooks for starting, running and fixing the system.

##### Acceptance criteria
###### AC-1 A rule-to-code trace for every kept rule
Given the knowledge graph and the latest test results
When the traceability report is generated
Then modern/docs/reports/traceability.md has one row for each of the 433 non-DROPPED rules of rule-to-service.csv with its story, AC, test names, code classes and the test result
Basis: .claude/commands/phase-8-e2e-hardening.md step 6, modern/docs/architecture/rule-to-service.csv, QF-9 (default applied)

###### AC-2 Rules without a passing test are listed
Given the report
When its summary is read
Then it states the number of rules without a passing test, whose target is zero, and lists every exception with its reason for the user to accept
Basis: .claude/commands/phase-8-e2e-hardening.md (step 6 and Exit checks)

###### AC-3 Dropped rules are shown with their decision
Given the 22 DROPPED rules of rule-to-service.csv
When the report's dropped section is read
Then each is listed with its decision (15 by D-011 PRVDEAD1, 7 by D-010 Medicare reciprocity) and no story or test is expected for them
Basis: modern/docs/analysis/defect-register.md (D-010, D-011), modern/docs/architecture/rule-to-service.csv

###### AC-4 Every FIX or REPLACE decision traces to a passing AC test
Given the 29 decisions D-001 to D-029
When the decision section is read
Then every FIX or REPLACE decision lists at least one story, AC and passing test, except D-021, which is listed as an exception because its only AC is in the deferred parity story
Basis: .claude/skills/jira-backlog/SKILL.md section 2, modern/docs/analysis/defect-register.md, D-021

###### AC-5 The report is generated, not written by hand
Given the generator and the test result files
When it is run twice with no change
Then both runs give the same report apart from the timestamp
Basis: .claude/commands/phase-8-e2e-hardening.md step 6, MODERNIZATION.md section 6 rule 8

###### AC-6 The operations runbook covers every job
Given modern/docs/runbooks/operations.md
When it is read
Then it lists the six batch jobs (intake, screening, revalidation sweep, sanction intake, directory extract, T-MSIS extract) with their configured schedule, their manual-trigger endpoint under /api/v1/batch, the business-date parameter, where to read the run result, and how to read logs and health
Basis: .claude/commands/phase-8-e2e-hardening.md step 7, modern/docs/adr/ADR-004-batch-and-scheduling.md, D-020, D-024

###### AC-7 The troubleshooting runbook covers the known failure modes
Given modern/docs/runbooks/troubleshooting.md
When it is read
Then it has a section for an unhealthy container, a busy host port, a failed sign-in, events not flowing (including dead-lettered events), and a batch run in status FAILED, each with the commands to look and to fix
Basis: .claude/commands/phase-8-e2e-hardening.md step 7, D-024, QF-13 (default applied)

###### AC-8 The local-run runbook was tested
Given modern/docs/runbooks/local-run.md
When its steps are executed from a clean clone
Then the result is recorded in the file, and the file states honestly if it was not executed
Basis: .claude/commands/phase-5-platform.md (Exit checks), .claude/commands/phase-8-e2e-hardening.md step 7, MODERNIZATION.md section 6 rule 8

##### Out of scope
- Loading Epic, Story and AC nodes into the graph (Phase 4 kag-curator)
- Code review of the whole system
- Parity with legacy output (par-harness)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: none (cross-cutting report over all legacy programs)
Decisions: none

##### Technical notes (agent-owned)
Service: e2e. Endpoints/events: none. Roles: none.


### MAM-15 Parity (deferred)

#### MAM-91 Parity: run the legacy jobs on Hercules and compare their outputs with the modern system (deferred)

Type: Story. Service: parity. Labels: svc-parity, kag-MAM-91, agentic, decision-D-021, decision-D-020, decision-D-006. Depends on: MAM-53, MAM-42, MAM-44, MAM-56, MAM-32, MAM-45, MAM-55, MAM-67, MAM-75, MAM-43.

##### Business requirement
To prove that the new system reproduces the old one, the old batch jobs will be run on a mainframe emulator with the same fictitious inputs, their output files captured, and compared record by record with the new system's output. Every difference must be explained by an approved decision or become a defect. This work waits until the legacy outputs exist.

##### Acceptance criteria
###### AC-1 Without golden files the run stops and says what to capture
Given no files in modern/parity/golden
When the parity phase is started
Then it prints the capture checklist, lists the synthetic input files under modern/testdata/fixed-width to send to the legacy environment, sets phase 9 to WAITING FOR LEGACY OUTPUTS in PROGRESS.md and stops
Basis: .claude/commands/phase-9-parity.md (Preconditions), .claude/skills/parity-testing/SKILL.md section 2

###### AC-2 Golden files are stored with a manifest
Given captured runs of PRVDAY01, PRVMTH01, PRVSAN01 and PRVDIR01 (the last two with ad-hoc JCL)
When they are placed under modern/parity/golden/<job>/<run-id>
Then each run has inputs, before, after and logs folders and a manifest.json with the job, the business date 2026-01-15, the code page, file names, record lengths and sha256 of each file
Basis: .claude/skills/parity-testing/SKILL.md sections 2 and 3

###### AC-3 Legacy files are converted with the recorded code page
Given golden files captured in binary mode
When the converter runs with the code page from the manifest
Then converted files exist next to the originals, which are unchanged, and record lengths match the manifest
Basis: .claude/skills/parity-testing/SKILL.md section 4 step 1, .claude/agents/data-engineer.md deliverable 5

###### AC-4 The modern system runs the same inputs on the same date
Given the same synthetic inputs and the business date 2026-01-15
When the modern jobs are run through their legacy-format adapters
Then they produce APPERROR, SCRNOUT, REVALQ, TMSISOUT, DIROUT and the 130-byte master export, and both sides are parsed with the same layouts from libs/fixed-width
Basis: .claude/skills/parity-testing/SKILL.md section 4 steps 2 and 3, modern/docs/adr/ADR-005-legacy-format-adapters.md, D-007, D-020

###### AC-5 Every difference is classified
Given both sides parsed
When records are matched by key (NPI, or position where there is no key) and compared field by field
Then each difference is MATCH, INTENDED (naming an approved decision D-0xx) or UNEXPLAINED, only run-dependent fields listed in the manifest are normalised, and no business field is
Basis: .claude/skills/parity-testing/SKILL.md section 4 steps 4 to 6

###### AC-6 The report lists intended and unexplained differences
Given a finished comparison
When modern/docs/reports/parity-<job>-<run-id>.md is read
Then it has a summary per file with MATCH, INTENDED and UNEXPLAINED counts, a table of INTENDED differences with their decision IDs (for example D-006 for sanction S giving DENIED in the legacy and SUSPENDED in the modern system) and a table of UNEXPLAINED differences with file, key, field, legacy value, modern value and likely cause
Basis: .claude/skills/parity-testing/SKILL.md section 5, D-006

###### AC-7 A human decides every unexplained difference
Given an UNEXPLAINED difference
When it is shown to the user
Then the parity checker has not marked it INTENDED on its own; if the modern system is wrong a Defect story is created, and if the legacy is wrong and the modern behaviour stays a new decision D-0xx is recorded; the comparison is re-run until there are zero UNEXPLAINED differences
Basis: .claude/commands/phase-9-parity.md steps 3 and 4, .claude/skills/parity-testing/SKILL.md section 5

###### AC-8 The legacy run is patched outside the source folder
Given the Hercules run of PRVMTH01 and PRVDAY01
When the jobs are prepared
Then PRVTMS01 runs from a patched copy in which the identifier past column 72 (BATCH/PRVTMS01.cbl:114) is fixed, the undefined &DATE and &BATCH_DATE are given explicit values, source-code/ is unchanged, and the manifest records each patch
Basis: D-021, D-020, CLAUDE.md sections 7 and 11 (source-code is read-only), modern/docs/PROGRESS.md (Decisions 2026-11-18: Phase 9 legacy outputs from the local mmis-hercules container)

##### Out of scope
- Running the legacy jobs (done by the user in the Hercules container)
- PRVDAY01 STEP020 SORT, whose output is never read (D-009 DROP)
- The Medicare reciprocity check (D-010 DROP) and PRVDEAD1 (D-011 DROP)
- Fixing differences (Defect stories through the normal build loop)

##### Legacy trace (agent-owned, do not edit)
Rules: none (enabler)
Programs: JCL/PRVDAY01.jcl (STEP010, STEP030, STEP040; &DATE and &BATCH_DATE undefined); JCL/PRVMTH01.jcl (STEP010-STEP030); BATCH/PRVSAN01.cbl and BATCH/PRVDIR01.cbl (no JCL exists; ad-hoc JCL needed); BATCH/PRVTMS01.cbl:114 (identifier past column 72, D-021)
Decisions: D-021 FIX: not applicable to the new system; the Phase 9 Hercules run patches the PRVTMS01 line in a copy; D-020 FIX: the business date is an explicit parameter, also for the legacy run; D-006 FIX: sanction S gives SUSPENDED, so the legacy DENIED is a proposed INTENDED difference

##### Technical notes (agent-owned)
Service: parity. Endpoints/events: none. Roles: none.


## AWS KeyFunctionality -> items

| KeyFunctionality | Items |
|---|---|
| PRVCICS1:active-provider-identification | MAM-80 |
| PRVCICS1:analyst-npi-input-capture | MAM-37, MAM-77 |
| PRVCICS1:denied-provider-identification | MAM-80 |
| PRVCICS1:npi-session-continuity | MAM-37, MAM-77 |
| PRVCICS1:pending-provider-identification | MAM-80 |
| PRVCICS1:provider-enrollment-status-determination | MAM-80 |
| PRVCICS1:provider-inquiry-results-display | MAM-80 |
| PRVCICS1:provider-master-record-lookup | MAM-37, MAM-77 |
| PRVCICS1:provider-not-found-handling | MAM-77 |
| PRVCICS1:terminated-provider-identification | MAM-80 |
| PRVCICS1:transaction-session-persistence | MAM-77 |
| PRVDIR01:active-provider-count-tracking | MAM-67 |
| PRVDIR01:active-provider-filtering | MAM-61 |
| PRVDIR01:directory-extract-completion-reporting | MAM-67 |
| PRVDIR01:directory-record-output | MAM-67 |
| PRVDIR01:provider-directory-extract-initialization | MAM-67 |
| PRVDIR01:provider-directory-record-mapping | MAM-61 |
| PRVDIR01:sequential-provider-record-retrieval | MAM-67 |
| PRVENR01:batch-completion-reporting-and-return-code-setting | MAM-57 |
| PRVENR01:batch-initialization | MAM-56, MAM-57 |
| PRVENR01:invalid-application-written-to-provider-master-(defect) | MAM-17, MAM-46, MAM-56 |
| PRVENR01:npi-presence-validation | MAM-46, MAM-56, MAM-57 |
| PRVENR01:provider-application-reading | MAM-56, MAM-57 |
| PRVENR01:provider-enrollment-status-assignment | MAM-17 |
| PRVENR01:provider-master-record-writing | MAM-17, MAM-57 |
| PRVENR01:provider-taxonomy-code-validation | MAM-46, MAM-56, MAM-57 |
| PRVENR01:provider-type-classification-validation | MAM-46, MAM-56, MAM-57 |
| PRVENR01:validation-error-record-writing | MAM-56 |
| PRVREV01:active-provider-filter | MAM-45 |
| PRVREV01:program-initialization | MAM-45 |
| PRVREV01:provider-master-file-sweep | MAM-45 |
| PRVREV01:queue-provider-for-re | MAM-45 |
| PRVREV01:revalidation-count-tracking | MAM-45 |
| PRVREV01:revalidation-due-date-check | MAM-45 |
| PRVREV01:revalidation-summary-reporting | MAM-45 |
| PRVRSK01:high-risk-tier-assignment | MAM-35, MAM-41 |
| PRVRSK01:limited-risk-tier-assignment | MAM-35 |
| PRVRSK01:moderate-risk-tier-assignment | MAM-35 |
| PRVRSK01:provider-file-initialization | MAM-35 |
| PRVRSK01:provider-record-update | MAM-23 |
| PRVRSK01:provider-taxonomy-group-extraction | MAM-41 |
| PRVRSK01:revalidation-due-date-assignment | MAM-27 |
| PRVRSK01:revalidation-due-date-calculation | MAM-30 |
| PRVRSK01:risk-tier-flag-update-on-provider-record | MAM-35 |
| PRVRSK01:sequential-provider-record-retrieval | MAM-35 |
| PRVSAN01:provider-lookup-by-npi | MAM-52 |
| PRVSAN01:provider-master-record-update | MAM-33, MAM-58 |
| PRVSAN01:provider-not-found-handling | MAM-52 |
| PRVSAN01:provider-reinstatement | MAM-33, MAM-52, MAM-58 |
| PRVSAN01:provider-suspension | MAM-33, MAM-52, MAM-58 |
| PRVSAN01:provider-termination | MAM-33, MAM-52, MAM-55 |
| PRVSAN01:sanction-input-processing | MAM-33, MAM-55 |
| PRVSAN01:sanction-notice-generation | MAM-58 |
| PRVSAN01:termination-count-reporting | MAM-55 |
| PRVSCR01:oig-leie-exclusion-check | MAM-18, MAM-22, MAM-25 |
| PRVSCR01:pending-provider-selection | MAM-18, MAM-32 |
| PRVSCR01:provider-record-update | MAM-21 |
| PRVSCR01:provider-screening-initiation | MAM-32 |
| PRVSCR01:sam-debarment-check | MAM-18, MAM-25 |
| PRVSCR01:screening-output-record-writing | MAM-32 |
| PRVSCR01:screening-summary-reporting | MAM-32 |
| PRVSCR01:screening-verdict-determination | MAM-21, MAM-32 |
| PRVSCR01:ssa-death-master-file-check | MAM-18, MAM-25 |
| PRVSCR01:state-license-verification | MAM-18, MAM-25 |
| PRVTMS01:active-provider-filtering | MAM-71 |
| PRVTMS01:enrollment-date-future | MAM-78 |
| PRVTMS01:extracted-record-count-tracking | MAM-75 |
| PRVTMS01:extraction-summary-reporting | MAM-75 |
| PRVTMS01:file-closure-and-process-completion | MAM-75 |
| PRVTMS01:future-enrollment-alert | MAM-78 |
| PRVTMS01:julian-date-decomposition | MAM-71 |
| PRVTMS01:program-initialization | MAM-75 |
| PRVTMS01:provider-active-status-assignment | MAM-71 |
| PRVTMS01:provider-master-file-sequential-read | MAM-71 |
| PRVTMS01:provider-medicaid-id-mapping | MAM-71 |
| PRVTMS01:provider-npi-mapping | MAM-71 |
| PRVTMS01:submission-period-assignment | MAM-71 |
| PRVTMS01:t | MAM-71 |

## Coverage: rule -> item

Every non-dropped rule (433) and the item that owns it. Uncovered rules: **none**.

| Rule | Service | Item |
|---|---|---|
| R-PRVCICS1-ADD-01 | web-ui | MAM-80 |
| R-PRVCICS1-ADD-02 | web-ui | MAM-77 |
| R-PRVCICS1-ADD-03 | web-ui | MAM-77 |
| R-PRVCICS1-ADD-04 | provider-service | MAM-37 |
| R-PRVCICS1-ADD-05 | provider-service | MAM-37 |
| R-PRVCICS1-ADD-06 | web-ui | MAM-80 |
| R-PRVCICS1-ADD-07 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00001 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00002 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00003 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00004 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00005 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00006 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00007 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00008 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00009 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00010 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00011 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00012 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00013 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00014 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00015 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00016 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00017 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00018 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00019 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00020 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00021 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00022 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00023 | provider-service | MAM-37 |
| R-PRVCICS1-cbl-00024 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00025 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00026 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00027 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00028 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00029 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00030 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00031 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00032 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00033 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00034 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00035 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00036 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00037 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00038 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00039 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00040 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00041 | web-ui | MAM-80 |
| R-PRVCICS1-cbl-00042 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00043 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00044 | web-ui | MAM-77 |
| R-PRVCICS1-cbl-00045 | web-ui | MAM-77 |
| R-PRVDIR01-ADD-01 | directory-service | MAM-61 |
| R-PRVDIR01-ADD-02 | directory-service | MAM-61 |
| R-PRVDIR01-ADD-03 | directory-service | MAM-67 |
| R-PRVDIR01-ADD-04 | directory-service | MAM-65 |
| R-PRVDIR01-ADD-05 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00001 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00002 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00003 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00004 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00005 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00006 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00007 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00008 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00009 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00010 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00011 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00012 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00013 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00014 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00015 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00016 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00017 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00018 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00019 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00020 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00021 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00022 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00023 | directory-service | MAM-61 |
| R-PRVDIR01-cbl-00024 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00025 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00026 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00027 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00028 | directory-service | MAM-67 |
| R-PRVDIR01-cbl-00029 | directory-service | MAM-67 |
| R-PRVENR01-ADD-01 | enrollment-service | MAM-56 |
| R-PRVENR01-ADD-02 | provider-service | MAM-17 |
| R-PRVENR01-ADD-03 | enrollment-service | MAM-57 |
| R-PRVENR01-ADD-04 | enrollment-service | MAM-46 |
| R-PRVENR01-ADD-05 | enrollment-service | MAM-46 |
| R-PRVENR01-ADD-06 | enrollment-service | MAM-56 |
| R-PRVENR01-ADD-07 | enrollment-service | MAM-57 |
| R-PRVENR01-ADD-08 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00001 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00002 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00003 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00004 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00005 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00006 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00007 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00008 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00009 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00010 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00011 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00012 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00013 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00014 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00015 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00016 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00017 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00018 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00019 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00020 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00021 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00022 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00023 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00024 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00025 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00026 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00027 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00028 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00029 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00030 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00031 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00032 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00033 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00034 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00035 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00036 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00037 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00038 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00039 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00040 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00041 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00042 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00043 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00044 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00045 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00046 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00047 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00048 | enrollment-service | MAM-56 |
| R-PRVENR01-cbl-00049 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00050 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00051 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00052 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00053 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00054 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00055 | enrollment-service | MAM-46 |
| R-PRVENR01-cbl-00056 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00057 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00058 | provider-service | MAM-17 |
| R-PRVENR01-cbl-00059 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00060 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00061 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00062 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00063 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00064 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00065 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00066 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00067 | enrollment-service | MAM-57 |
| R-PRVENR01-cbl-00068 | enrollment-service | MAM-57 |
| R-PRVREV01-ADD-01 | revalidation-service | MAM-48 |
| R-PRVREV01-ADD-02 | revalidation-service | MAM-45 |
| R-PRVREV01-ADD-03 | revalidation-service | MAM-45 |
| R-PRVREV01-ADD-04 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00001 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00002 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00003 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00004 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00005 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00006 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00007 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00008 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00009 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00010 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00011 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00012 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00013 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00014 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00015 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00016 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00017 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00018 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00019 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00020 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00021 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00022 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00023 | revalidation-service | MAM-45 |
| R-PRVREV01-cbl-00024 | revalidation-service | MAM-45 |
| R-PRVRSK01-ADD-01 | provider-service | MAM-27 |
| R-PRVRSK01-ADD-02 | provider-service | MAM-30 |
| R-PRVRSK01-ADD-03 | risk-service | MAM-41 |
| R-PRVRSK01-ADD-04 | risk-service | MAM-35 |
| R-PRVRSK01-ADD-05 | risk-service | MAM-35 |
| R-PRVRSK01-ADD-06 | risk-service | MAM-35 |
| R-PRVRSK01-ADD-07 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00001 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00002 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00003 | risk-service | MAM-41 |
| R-PRVRSK01-cbl-00004 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00005 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00006 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00007 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00008 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00009 | provider-service | MAM-27 |
| R-PRVRSK01-cbl-00010 | provider-service | MAM-23 |
| R-PRVRSK01-cbl-00011 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00012 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00013 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00014 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00015 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00016 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00017 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00018 | risk-service | MAM-41 |
| R-PRVRSK01-cbl-00019 | risk-service | MAM-41 |
| R-PRVRSK01-cbl-00020 | risk-service | MAM-41 |
| R-PRVRSK01-cbl-00021 | risk-service | MAM-41 |
| R-PRVRSK01-cbl-00022 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00023 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00024 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00025 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00026 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00027 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00028 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00029 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00030 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00031 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00032 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00033 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00034 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00035 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00036 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00037 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00038 | risk-service | MAM-35 |
| R-PRVRSK01-cbl-00039 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00040 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00041 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00042 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00043 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00044 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00045 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00046 | provider-service | MAM-30 |
| R-PRVRSK01-cbl-00047 | provider-service | MAM-27 |
| R-PRVRSK01-cbl-00048 | provider-service | MAM-23 |
| R-PRVSAN01-ADD-01 | sanctions-service | MAM-52 |
| R-PRVSAN01-ADD-02 | sanctions-service | MAM-52 |
| R-PRVSAN01-ADD-03 | sanctions-service | MAM-52 |
| R-PRVSAN01-ADD-04 | provider-service | MAM-33 |
| R-PRVSAN01-ADD-05 | sanctions-service | MAM-52 |
| R-PRVSAN01-ADD-06 | sanctions-service | MAM-55 |
| R-PRVSAN01-ADD-07 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00001 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00002 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00003 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00004 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00005 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00006 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00007 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00008 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00009 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00010 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00011 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00012 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00013 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00014 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00015 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00016 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00017 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00018 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00019 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00020 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00021 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00022 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00023 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00024 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00025 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00026 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00027 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00028 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00029 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00030 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00031 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00032 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00033 | sanctions-service | MAM-52 |
| R-PRVSAN01-cbl-00034 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00035 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00036 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00037 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00038 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00039 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00040 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00041 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00042 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00043 | provider-service | MAM-33 |
| R-PRVSAN01-cbl-00044 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00045 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00046 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00047 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00048 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00049 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00050 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00051 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00052 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00053 | notification-service | MAM-58 |
| R-PRVSAN01-cbl-00054 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00055 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00056 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00057 | sanctions-service | MAM-55 |
| R-PRVSAN01-cbl-00058 | sanctions-service | MAM-55 |
| R-PRVSCR01-ADD-01 | screening-service | MAM-18 |
| R-PRVSCR01-ADD-02 | screening-service | MAM-25 |
| R-PRVSCR01-ADD-03 | provider-service | MAM-21 |
| R-PRVSCR01-ADD-04 | screening-service | MAM-32 |
| R-PRVSCR01-ADD-05 | screening-service | MAM-32 |
| R-PRVSCR01-ADD-06 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00001 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00002 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00003 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00006 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00007 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00008 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00009 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00010 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00011 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00012 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00013 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00014 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00015 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00016 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00017 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00018 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00019 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00020 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00021 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00022 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00023 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00029 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00030 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00031 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00032 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00033 | screening-service | MAM-22 |
| R-PRVSCR01-cbl-00034 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00035 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00036 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00037 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00038 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00039 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00040 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00041 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00042 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00043 | screening-service | MAM-18 |
| R-PRVSCR01-cbl-00044 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00045 | screening-service | MAM-25 |
| R-PRVSCR01-cbl-00046 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00047 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00048 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00049 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00050 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00051 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00052 | provider-service | MAM-21 |
| R-PRVSCR01-cbl-00053 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00054 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00055 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00056 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00057 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00058 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00059 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00060 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00061 | screening-service | MAM-32 |
| R-PRVSCR01-cbl-00062 | screening-service | MAM-32 |
| R-PRVTMS01-ADD-01 | tmsis-service | MAM-71 |
| R-PRVTMS01-ADD-02 | tmsis-service | MAM-78 |
| R-PRVTMS01-ADD-03 | tmsis-service | MAM-78 |
| R-PRVTMS01-ADD-04 | tmsis-service | MAM-71 |
| R-PRVTMS01-ADD-05 | tmsis-service | MAM-71 |
| R-PRVTMS01-ADD-06 | tmsis-service | MAM-75 |
| R-PRVTMS01-ADD-07 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00001 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00002 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00003 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00004 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00005 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00006 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00007 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00008 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00009 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00010 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00011 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00012 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00013 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00014 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00015 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00016 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00017 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00018 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00019 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00020 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00021 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00022 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00023 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00024 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00025 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00026 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00027 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00028 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00029 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00030 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00031 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00032 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00033 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00034 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00035 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00036 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00037 | tmsis-service | MAM-78 |
| R-PRVTMS01-cbl-00038 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00039 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00040 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00041 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00042 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00043 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00044 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00045 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00046 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00047 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00048 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00049 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00050 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00051 | tmsis-service | MAM-71 |
| R-PRVTMS01-cbl-00052 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00053 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00054 | tmsis-service | MAM-75 |
| R-PRVTMS01-cbl-00055 | tmsis-service | MAM-75 |

## Added after Phase 4

#### L-S079 Gateway pool limits: a wait limit for a connection and a connection limit per service (Jira key: not created yet)

Type: Story. Service: api-gateway. Parent: MAM-1 (Platform and CI; the epic is Done, the story is To Do). Link: relates to MAM-29. Labels: svc-api-gateway, kag-L-S079, agentic. Status: DRAFT; to be created in Jira, To Do, not built now (user, 2026-11-24, answers to Q-157 and the review of MAM-29).

##### Business requirement
When one service stops answering, the callers of every other service must not be slowed down. The front door answers a call to a silent service quickly with a clear error, and one service can never use up all the connections the front door has.

##### Acceptance criteria
###### AC-1 A call to a silent service is refused quickly
Given every connection from the gateway to one service is in use by calls that wait for a silent service
When one more call to that service arrives
Then the caller gets the 503 problem answer within 3 seconds, not after the 3 minutes of the library default
Basis: modern/docs/reports/review-should-fix.md (MAM-29, 2026-11-24, first note), ADR-013, Q-157, modern/docs/reports/stories/MAM-88.md (gap)

###### AC-2 One silent service cannot take all connections
Given a silent service holds as many connections as its own limit allows (default 50 of the 200 in all) because many callers wait for it
When a call to a different service arrives
Then it is answered in its normal time and is not delayed by the silent service
Basis: modern/docs/reports/review-should-fix.md (MAM-29, 2026-11-24, first note), ADR-013

###### AC-3 The limits are settings with documented defaults
Given the three values (wait for a connection, limit per service, limit in all) are set as properties of the gateway
When the gateway starts with other values
Then AC-1 and AC-2 hold with those values, and ADR-013 and the runbook state the defaults (wait 2 seconds, per service 50, in all 200; all DEFAULT, to be looked at again with the performance smoke)
Basis: ADR-013, modern/docs/runbooks/operations.md

###### AC-4 Calls that keep flowing are not cut
Given a download from a service that keeps sending data for longer than the wait limit
When the pool limits are in force
Then the download completes, and a service's 401 answer to a write still reaches the caller with its body
Basis: ADR-013, MAM-29 AC-6, modern/docs/reports/stories/MAM-29.md ("Review fix 2")

##### Out of scope
- Changing the read limit per read (30 seconds) or the connect limit (2 seconds).
- Retrying calls: the gateway never repeats a write.

##### Legacy trace (agent-owned, do not edit)
Rules: none (technical story)
Programs: none
Decisions: none; ADR-013 (accepted 2026-11-24)

##### Technical notes (agent-owned)
Service: api-gateway. Endpoints/events: none new. Roles: none. Tests first; the performance smoke (MAM-88) is not changed.
