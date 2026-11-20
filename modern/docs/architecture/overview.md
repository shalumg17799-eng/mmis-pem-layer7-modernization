# Architecture overview

**Status: PROPOSED (Phase 3), waiting for the user's review.** Java 21 / Spring Boot microservices, Kafka, Postgres (one database per service), Keycloak,
React. Starting point: `.claude/skills/java-microservices/SKILL.md`; behaviour: the verified rules (`../analysis/rule-verification.csv`) and the decisions
(`../analysis/defect-register.md`). Contracts: `../../contracts/`. Mappings: `rule-to-service.csv` (455 rules) and `field-to-column.csv` (126 legacy fields).

## Services

| Service | Port | Database | Replaces | Responsibility | Owned data | APIs | Publishes | Consumes |
|---|---|---|---|---|---|---|---|---|
| api-gateway | 8080 | none | entry point | Routing to the services, token relay, correlation id | none | all `/api/v1/*` and `/fhir/*` routes | none | none |
| provider-service | 8081 | provider_db | VSAM master + DB2 PROVIDER/PROVIDER_OWNER; status changes; PINQ lookup | The single provider record and its lifecycle: registration, screening verdict, risk tier, approval (D-019), status changes, due date, audit. | provider, provider_owner, audit_log | legacy, providers | ProviderRegistered, ProviderStatusChanged | ApplicationValidated, ScreeningCompleted, RiskTierAssigned, NoticeSent |
| enrollment-service | 8082 | enrollment_db | PRVENR01 / PRVDAY01 STEP010 | Applications from the portal and the legacy APPINPUT file; validation E101-E104 and E103; one error per failed check. | application, application_error, intake_run, provider_type_mapping | applications, batch | ApplicationValidated, ApplicationRejected | none |
| screening-service | 8083 | screening_db | PRVSCR01 / PRVDAY01 STEP030 | Screening against licence board, OIG LEIE, SAM, SSA DMF (mocked external sources); one result per source. | screening, screening_result, screening_run | batch, screenings | ScreeningCompleted | ProviderRegistered, RevalidationDue |
| risk-service | 8084 | risk_db | PRVRSK01 / PRVDAY01 STEP040 (tier only) | Risk tier from the data-driven taxonomy-prefix table. | risk_rule_set, risk_rule, risk_assessment | risk-assessments, risk-rules | RiskTierAssigned | ScreeningCompleted |
| revalidation-service | 8085 | revalidation_db | PRVREV01 / PRVMTH01 STEP010 | Revalidation cases with state; monthly sweep; re-screening. | revalidation_case, sweep_run, tracked_provider | batch, revalidations | RevalidationDue | ScreeningCompleted, ProviderStatusChanged |
| sanctions-service | 8086 | sanctions_db | PRVSAN01 | Sanction history; validates T/S/R; applies the status through provider-service's API; legacy SANCIN. | sanction, sanction_intake_run | batch, sanctions | SanctionApplied | none |
| notification-service | 8087 | notification_db | PRVNT* (missing programs) | Notices (the missing PRVNT* programs) by email, logged. | notice_template, notice | notice-templates, notices | NoticeSent | ApplicationRejected, ProviderStatusChanged, RevalidationDue, SanctionApplied, ExtractProduced |
| directory-service | 8088 | directory_db | PRVDIR01 | Directory read model of active providers; public search; FHIR R4; legacy DIROUT file. | directory_entry, extract_run | batch, directory, fhir | none | ProviderRegistered, RiskTierAssigned, ProviderStatusChanged |
| tmsis-service | 8089 | tmsis_db | PRVTMS01 / PRVMTH01 STEP020-030 | Monthly T-MSIS extract in the legacy 120-byte format, with archive generations. | extract_run, extract_row, extract_archive | batch | ExtractProduced | none |
| web-ui | 5173 | none | CICS PINQ + new portal | React portal (PROVIDER) and console (ANALYST, SUPERVISOR, ADMIN); public directory page | none | calls the APIs through the gateway | none | none |

Data ownership: only provider-service writes provider status, screen flag, risk tier and revalidation due date; every other service sends an event or calls
its API, and no service reads another service's database (fixes AP-06, AP-12; D-013, D-014). Events use the envelope, outbox and idempotent consumers of the
skill (section 7); message key = NPI.

## Main flows

1. **Enrolment.** A provider submits an application in the portal (or an ADMIN loads a legacy APPINPUT file). enrollment-service validates E101, E102, E104
   and E103 (duplicate NPI); an invalid application gets exactly one error per failed check and ApplicationRejected, and is never registered (D-001).
   A valid one emits ApplicationValidated; provider-service registers it PENDING / UNSCREENED and emits ProviderRegistered.
2. **Screening.** screening-service consumes ProviderRegistered, checks the four mocked sources, stores one result per source and emits ScreeningCompleted
   (any FAIL = FAIL; D-005). provider-service sets the screen flag; risk-service, on PASS, assigns the tier from its table and emits RiskTierAssigned;
   provider-service saves the tier (D-002).
3. **Approval.** An analyst approves or denies in the console (D-019). provider-service sets ACTIVE (due date = approval + 5 years, D-003) or DENIED and
   emits ProviderStatusChanged; directory-service, notification-service and revalidation-service react.
4. **Revalidation.** The monthly sweep (Spring Batch, business date as a parameter, D-020) opens one case per due provider (D-029) and emits
   RevalidationDue; screening-service re-screens; on ScreeningCompleted (REVALIDATION) the case completes and provider-service sets the next due date.
5. **Sanctions.** A SUPERVISOR records T, S or R (unknown codes rejected, D-026), or an ADMIN loads a SANCIN file. sanctions-service stores the history
   (D-018), calls provider-service's status API (T TERMINATED, S SUSPENDED (D-006), R ACTIVE; applied whatever the current status, D-027) and emits
   SanctionApplied; notification-service sends the TRM / SUS / RST notice (D-012).
6. **Directory.** directory-service keeps a read model from provider events, publishes active providers (public search and FHIR R4) and writes the nightly
   legacy DIROUT file with a properly composed name (D-028).
7. **T-MSIS.** tmsis-service, monthly, reads active providers through provider-service's API, writes the legacy 120-byte extract with the YYDDD period
   (D-015, Q-007), archives a new generation and emits ExtractProduced.

## Where this design differs from the skill's default, and why

| # | Difference | Reason |
|---|---|---|
| 1 | A fifth role `SYSTEM` for service accounts (client credentials) | sanctions-service must call provider-service, and schedulers start batch jobs, with no user (ADR-007). |
| 2 | Directory and FHIR endpoints are `PUBLIC` (no token) | A provider directory is public; every other endpoint requires a role (ADR-006, ADR-007). |
| 3 | NoticeSent is consumed by provider-service (audit history) and ExtractProduced by notification-service | Every event needs at least one consumer; both are useful (Phase 3 check). |
| 4 | ScreeningCompleted and ProviderStatusChanged are also consumed by revalidation-service | It keeps its own read model of due dates instead of reading provider-service's database. |
| 5 | The revalidation due date is set by provider-service at approval and at each completed revalidation, not by the risk step | Decision D-003 (legacy PRVRSK01 reset it on every run). |
| 6 | Every service that replaces a file-based job has `/api/v1/batch/...` run endpoints and legacy-format file downloads | MODERNIZATION.md rule 3 (legacy-format adapters for Phase 9 parity; ADR-005). |
| 7 | `web-ui` appears as an owner in `rule-to-service.csv` | The PINQ display rules (labels, messages, session NPI) belong to the console screen, not to a backend service. |

## Decisions assumed (defaults applied, see `../analysis/questions.md`)

Q-005 legacy risk table kept as data; Q-006 sanctions apply whatever the status; Q-007 legacy T-MSIS extract kept; Q-008 directory publishes legacy fields
only; Q-009 intake = PRVRECRD in bytes 1-130; Q-010 defaults for D-021 to D-029; Q-011 the seven journeys; Q-012 no data deletion until a retention policy is set.
