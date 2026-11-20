# Requirement checklist (FINAL PLAN Part C)

Generated 2026-11-20 19:27 by `modern/tools/trace/build_checklist.py` from the repo; do not edit by hand.

## 1. Business stories: 59 of 64 built

37 built test-first (Outcome DONE); 22 built in the build phase with their tests in Part D (Outcome BUILT).

| Story | Service | Title | Built | Outcome (story report) |
|---|---|---|---|---|
| MAM-17 | provider-service | Register a validated application as a PENDING, UNSCREENED provider | yes | DONE (AC-1..AC-7), In Review |
| MAM-18 | screening-service | Screen a newly registered provider against the licence board, OIG LEIE, SAM and SSA DMF (mocked sources) | yes | DONE (built test-first with MAM-25), In Review |
| MAM-20 | screening-service | Handle unknown licences, organisation providers and sources that fail or are slow | yes | BUILT (build-phase: tests in Part D), batch A3 |
| MAM-21 | provider-service | Record the screening verdict (screen flag) on the provider | yes | DONE (built test-first with MAM-23), In Review |
| MAM-22 | screening-service | Screen the owner of a group or facility (E203 owner excluded) | yes | BUILT (build-phase: tests in Part D), batch A3 |
| MAM-23 | provider-service | Record the assigned risk tier on the provider | yes | DONE (built test-first with MAM-21), In Review |
| MAM-25 | screening-service | Decide the screening verdict, store the results per source and publish ScreeningCompleted | yes | DONE (built test-first with MAM-18), In Review |
| MAM-27 | provider-service | Analyst approves or denies an enrolment; approval sets ACTIVE and the revalidation due date | yes | DONE (built test-first), In Review |
| MAM-28 | screening-service | Rescreen a provider on request or when revalidation is due | yes | BUILT (build-phase: tests in Part D), batch A1 |
| MAM-30 | provider-service | Revalidation due date: five years, four-digit years, leap day and each completed revalidation | yes | DONE (fast-mode, gates postponed) |
| MAM-32 | screening-service | Run screening as a batch job and produce the legacy SCRNOUT file | yes | BUILT (build-phase: tests in Part D), batch A5; the job itself not run live (see question) |
| MAM-33 | provider-service | Apply a sanction status change to the provider (TERMINATED, SUSPENDED, ACTIVE) | yes | DONE (fast-mode, gates postponed) |
| MAM-35 | risk-service | Assign the risk tier from the taxonomy prefix after a passed screening | yes | DONE (built test-first), In Review |
| MAM-36 | platform | Health, structured logs and correlation IDs across services (NFR) | yes | DONE for AC-1, AC-2, AC-3, AC-4 (fast-mode, gates postponed); AC-5 MOVED TO PART D (user plan |
| MAM-37 | provider-service | Search and read providers by NPI or Medicaid ID, role-checked (PINQ replacement back end) | yes | BUILT (build-phase: tests in Part D), batch A3 |
| MAM-38 | risk-service | View and change the taxonomy-prefix risk table (admin) | yes | BUILT (build-phase: tests in Part D), batch A6 |
| MAM-40 | provider-service | Provider history: every change and every notice sent is audited and readable | yes | BUILT (build-phase: tests in Part D), batch A3 |
| MAM-41 | risk-service | Assess a risk tier on request (risk assessment API) | yes | BUILT (build-phase: tests in Part D), batch A6 |
| MAM-43 | provider-service | Export the provider master in the legacy 130-byte PRVRECRD layout | yes | BUILT (build-phase: tests in Part D), batch A5 (not a batch job: no Spring Batch) |
| MAM-45 | revalidation-service | Monthly revalidation sweep: open a case for every ACTIVE provider that is due, with the legacy REVALQ file | yes | BUILT (build-phase: tests in Part D), batch A1 |
| MAM-46 | enrollment-service | Submit an enrolment application online and validate it (E101 to E104) | yes | DONE (AC-1..AC-8), In Review |
| MAM-48 | revalidation-service | Revalidation cases: list them and complete one | yes | BUILT (build-phase: tests in Part D), batch A1 |
| MAM-49 | revalidation-service | Revalidation case states: re-screening requested and provider no longer active | yes | BUILT (build-phase: tests in Part D), batch A1 |
| MAM-52 | sanctions-service | Record a sanction (terminate, suspend, reinstate) with history and apply it through provider-service | yes | DONE (fast-mode, gates postponed) |
| MAM-54 | enrollment-service | Read application status (a provider sees their own; an analyst sees all) | yes | DONE (AC-1..AC-8), In Review |
| MAM-55 | sanctions-service | Run the legacy SANCIN sanction intake as a batch job | yes | BUILT (build-phase: tests in Part D), batch A5 |
| MAM-56 | enrollment-service | Run the legacy APPINPUT intake as a batch job with one error row per rejected application | yes | BUILT (build-phase: tests in Part D), batch A5 |
| MAM-57 | enrollment-service | Report the result of an intake run: exclusive counts, completion, failures and safe re-runs | yes | BUILT (build-phase: tests in Part D), batch A5 (with MAM-56) |
| MAM-58 | notification-service | Send sanction notices by email (TRM, SUS, RST), reject unknown actions and handle failures | yes | DONE (fast-mode, gates postponed) |
| MAM-59 | notification-service | Send notices by email for approvals, denials, rejections and revalidation | yes | DONE (built test-first), In Review |
| MAM-60 | notification-service | List the notices sent and the notice templates | yes | DONE (AC-1..AC-8), In Review |
| MAM-61 | directory-service | Keep the directory of ACTIVE providers up to date from provider events | yes | DONE (built test-first), In Review |
| MAM-62 | web-ui | Web UI shell: sign in with Keycloak, role-based navigation, portal home | yes | DONE (built test-first), In Review |
| MAM-63 | directory-service | Public provider directory search | yes | DONE (AC-1..AC-7), In Review |
| MAM-64 | web-ui | Portal: apply for enrolment online, with field errors E101 to E104 | yes | DONE (AC-1..AC-7), In Review |
| MAM-65 | directory-service | FHIR R4 Practitioner, Organization and PractitionerRole (read-only, public) | yes | BUILT (build-phase: tests in Part D), batch A6 |
| MAM-66 | web-ui | Portal: see my application status | yes | DONE (fast-mode, gates postponed); AC-1..AC-6 built, tests 20/20 green; 19 recorded MAM-64 tests are now superseded (decision below) |
| MAM-67 | directory-service | Directory extract as a batch job in the legacy DIROUT format | yes | BUILT (build-phase: tests in Part D), batch A5 |
| MAM-68 | web-ui | Portal: see my provider record | yes | DONE (fast-mode, gates postponed); AC-1..AC-7 built, tests 30/30 green |
| MAM-69 | e2e | Journey J1 end to end: a provider applies and is enrolled (main journey) | yes | DONE (AC-1..AC-8), In Review |
| MAM-70 | web-ui | Portal: see my notices | yes | DONE (fast-mode, gates postponed); AC-1..AC-7 built, tests 21/21 green |
| MAM-71 | tmsis-service | Monthly T-MSIS provider extract in the legacy 120-byte layout (YYDDD period) | yes | BUILT (build-phase: tests in Part D), batch A4 |
| MAM-72 | e2e | Journey J2 end to end: an application is rejected | yes | DONE (AC-1..AC-5), fast-mode (code review postponed), In Review |
| MAM-73 | web-ui | Public directory page (no sign-in) | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, Playwright 7/7, Vitest 24/25 (1 recorded test is wrong) |
| MAM-74 | e2e | Journey J3 end to end: screening fails and the analyst denies | yes | DONE (AC-1..AC-6), journey passes live; In Review |
| MAM-75 | tmsis-service | T-MSIS extract run: archive every file, report counts, publish ExtractProduced | yes | BUILT (build-phase: tests in Part D), batch A4; AC-5 built after Q-061 answer B |
| MAM-76 | e2e | Journey J4 end to end: analyst inquiry (PINQ replacement) | yes | DONE (AC-1..AC-7), journey passes live; In Review |
| MAM-77 | web-ui | Console: provider search by NPI, Medicaid ID or name (replaces CICS PINQ) | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 35/35 green; 3 recorded MAM-87 tests conflict (below) |
| MAM-78 | tmsis-service | Warn about providers whose enrolment year is later than the business year | yes | BUILT (build-phase: tests in Part D), done with batch A4 (same service, tmsis-service) |
| MAM-79 | e2e | Journey J5 end to end: a supervisor suspends and reinstates a provider | yes | DONE (AC-1..AC-7), journey passes live; In Review |
| MAM-80 | web-ui | Console: provider detail with every record field and the status shown as text and code | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 31/31 green |
| MAM-81 | e2e | Journey J6 end to end: monthly revalidation | yes | BUILT (build-phase: journey test written by the lead after the build, on the user's instruction 2026-11-19 20:00); AC-1..AC-6 pass live |
| MAM-82 | web-ui | Console: provider detail panels for screening, sanctions, revalidation, notices and audit | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 38/38 green |
| MAM-83 | e2e | Journey J7 end to end: month-end extracts and the public directory | yes | BUILT (build-phase: journey test written by the lead after the build, on the user's instruction 2026-11-19 20:00); AC-1..AC-7 pass live |
| MAM-84 | e2e | Security: role tests for every endpoint, dependency check, no secrets (NFR) | no | no report yet |
| MAM-85 | web-ui | Console: work queues (pending approval, failed screening, revalidation due, rejected applications) | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 35/35 green |
| MAM-86 | e2e | Accessibility: WCAG 2.1 AA on every page, zero axe violations, keyboard-only walk-through (NFR) | no | no report yet |
| MAM-87 | web-ui | Console: approve or deny an enrolment | yes | DONE (AC-1..AC-7), In Review |
| MAM-88 | e2e | Performance smoke: 10,000 synthetic providers, batch jobs and search against the NFR targets (NFR) | no | no report yet |
| MAM-89 | e2e | Traceability report (rule -> story -> AC -> test -> code) and runbooks | no | no report yet |
| MAM-90 | web-ui | Console: record a sanction (supervisor) | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 44/44 green |
| MAM-91 | parity | Parity: run the legacy jobs on Hercules and compare their outputs with the modern system (deferred) | no | no report yet |
| MAM-92 | web-ui | Console: view and change the risk rules (admin) | yes | DONE (fast-mode, gates postponed); AC-1..AC-7 built, recorded tests green |
| MAM-93 | web-ui | Console: run the batch jobs and download their legacy files (admin) | yes | DONE (fast-mode, gates postponed); AC-1..AC-8 built, tests 57/57 green |

Tasks (not business stories): MAM-16 (open), MAM-19 (built), MAM-24 (built), MAM-26 (built), MAM-29 (built), MAM-31 (built), MAM-34 (built), MAM-39 (built), MAM-42 (built), MAM-44 (built), MAM-47 (built), MAM-50 (built), MAM-51 (built), MAM-53 (built)

## 2. Legacy rules: 433 of 433 non-dropped rules reach built code; 0 gaps

## 3. Services with business code

| Service | Main classes | Stories built / total |
|---|---|---|
| provider-service | 55 | 9 / 9 |
| enrollment-service | 33 | 4 / 4 |
| screening-service | 35 | 6 / 6 |
| risk-service | 16 | 3 / 3 |
| revalidation-service | 33 | 3 / 3 |
| sanctions-service | 22 | 2 / 2 |
| notification-service | 15 | 3 / 3 |
| directory-service | 28 | 4 / 4 |
| tmsis-service | 26 | 3 / 3 |

## 4. Screens: 13 of 13 built

| Route | Title | Component |
|---|---|---|
| /portal | My enrolment | PortalHome |
| /portal/apply | Apply for enrolment | ApplyPage |
| /portal/applications/:applicationId | Application status | ApplicationStatus |
| /portal/provider | My provider record | ProviderRecord |
| /portal/notices | My notices | NoticesPage |
| /console/providers | Provider search | ProviderSearch |
| /console/providers/:npi | Provider detail | ProviderDetail |
| /console/queues | Work queues | QueuesPage |
| /console/providers/:npi/approval | Approve or deny | ApprovalPage |
| /console/providers/:npi/sanctions/new | Record a sanction | SanctionPage |
| /console/admin/risk-rules | Risk-tier rules | RiskRulesPage |
| /console/admin/jobs | Batch jobs | JobsPage |
| /directory | Find a Medicaid provider (public) | PublicDirectory |

UX overhaul status per screen: see the UI agent's group reports and `modern/docs/reports/ui-screenshots/`.

## 5. Journeys

| Journey | Story | Test | Story built | Click steps |
|---|---|---|---|---|
| J1 | MAM-69 | j1-enrolment.spec.ts | yes | modern/docs/ux/journeys.md#J1 and modern/docs/DEMO.md |
| J2 | MAM-72 | j2-rejected.spec.ts | yes | modern/docs/ux/journeys.md#J2 |
| J3 | MAM-74 | j3-screening-fails.spec.ts | yes | modern/docs/ux/journeys.md#J3 |
| J4 | MAM-76 | j4-inquiry.spec.ts | yes | modern/docs/ux/journeys.md#J4 |
| J5 | MAM-79 | j5-sanctions.spec.ts | yes | modern/docs/ux/journeys.md#J5 |
| J6 | MAM-81 | j6-revalidation.spec.ts | yes | modern/docs/ux/journeys.md#J6 |
| J7 | MAM-83 | j7-extracts.spec.ts | yes | modern/docs/ux/journeys.md#J7 |

## 6. Open questions (1) and BLOCKED items

- Q-131: (provider-service, MAM-40, 2026-11-20 18:55) The audit history answer and its contract disagree ( (OPEN, needs the user)
- PROGRESS: | 2026-11-20 18:12 | HARD DEADLINE 20:00: PIT and reviews cut, deferred work stopped, deploy 2 started (Jira Done 42 of 93) | User message 18:02 (recorded above). **Done since 17:36**: MAM-29 (gateway
- PROGRESS: | 2026-11-20 17:36 | Deploy 1 DEPLOYED; checkpoint commit 2e20ead pushed; 6 stories + 3 epics Done (Jira Done 40 of 93) | **Deploy 1**: `RESULT DEPLOYED` at 17:07 (pem-apps healthy 16:59:59; J1 passed
- PROGRESS: | 2026-11-20 16:18 | Parity: legacy run captured; MAM-84 scans | **Parity stage 1 (legacy run on Hercules) PARTIAL but with real golden files**: MVS jobs 193-197 ran with the lab's load modules (code 
- PROGRESS: | 2026-11-19 21:04 | Platform tasks + MAM-89 groundwork | Done in Jira with comment (AC checks): MAM-34 (smoke-walking-skeleton.sh: AC-1 200, AC-2 401/403), MAM-50 (AC-1: after make seed 16/16 provide
- PROGRESS: | 2026-11-19 16:41 | Part C tooling | New `modern/tools/trace/build_checklist.py` writes `docs/reports/requirement-checklist.md` (64 business stories = the 64 items of type Story; rules -> built code;
- PROGRESS: | 2026-11-19 16:10 | J1 x2 BLOCKED (env) | `./pem.sh j1 2` attempt 1: run 1 PASSED end to end (24.6 s, all AC-1..AC-8 incl. notices and provider record), run 2 failed `page.goto: net::ERR_CONNECTION_R
- PROGRESS: | 2026-11-19 14:20 | MAM-64 | Portal apply: 101 Vitest + 25 Playwright green (lead re-ran both), hashes OK; live J1 AC-1 passes through it. Jira comment + in-review. MAM-87: 73/74 Vitest, 27/28 Playwr
