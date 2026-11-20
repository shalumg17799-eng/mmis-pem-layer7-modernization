# Screen inventory

One row per screen of the PEM web UI (`modern/web-ui/`, one app with role-based areas; frontend-stack skill).
Every API call below exists in `modern/contracts/openapi/` (checked by `modern/tools/checks/check_phase3.py`). Wireframes: `wireframes/index.html`.

| area | screen | route | roles | purpose | API calls | stories/rules served |
|---|---|---|---|---|---|---|
| portal | home | `/portal` | PROVIDER | The provider's applications and current enrolment status. | `GET /api/v1/applications`<br>`GET /api/v1/providers/{npi}` | D-019 (status after approval); journeys 1 and 2 |
| portal | apply | `/portal/apply` | PROVIDER | Enrolment application form; validates E101 (NPI), E102 (type), E104 (taxonomy) and E103 (duplicate NPI). | `POST /api/v1/applications` | PRVENR01 validation rules (enrollment-service); D-001, D-016, D-008 |
| portal | application-status | `/portal/applications/:applicationId` | PROVIDER | One application with every validation error (one per failed check). | `GET /api/v1/applications/{applicationId}` | PRVENR01 00003-00006, 00043-00047; ApplicationRejected; D-001 |
| portal | provider-record | `/portal/provider` | PROVIDER | The provider's own record (never another provider's). | `GET /api/v1/providers/{npi}` | PRVCICS1 fields; D-006 (Suspended), frontend-stack section 6 |
| portal | notices | `/portal/notices` | PROVIDER | Notices sent to the provider (approval, denial, sanctions, revalidation). | `GET /api/v1/notices` | PRVSAN01 notice rules (notification-service); D-012 |
| console | provider-search | `/console/providers` | ANALYST, SUPERVISOR, ADMIN | Find a provider by NPI, Medicaid ID or name (replaces CICS PINQ; the typed NPI is the key, D-022). | `GET /api/v1/providers` | PRVCICS1 00001-00002, 00012-00020, 00042-00045; R-PRVCICS1-ADD-02, -07; D-022 |
| console | provider-detail | `/console/providers/:npi` | ANALYST, SUPERVISOR, ADMIN | Everything about one provider: every PRVRECRD field with its meaning, the status message the CICS screen never sent, screening per source, sanctions, revalidation, notices, audit. | `GET /api/v1/providers/{npi}`<br>`GET /api/v1/screenings`<br>`GET /api/v1/sanctions`<br>`GET /api/v1/revalidations`<br>`GET /api/v1/notices`<br>`GET /api/v1/providers/{npi}/audit` | PRVCICS1 00003-00011, 00021-00041; R-PRVCICS1-ADD-01, -06; D-022, D-006, D-018 |
| console | work-queues | `/console/queues` | ANALYST, SUPERVISOR | Pending approval (screen PASS), failed screening, revalidation due, rejected applications. | `GET /api/v1/providers`<br>`GET /api/v1/revalidations`<br>`GET /api/v1/applications` | D-019, D-029; PRVREV01 rules (revalidation-service) |
| console | approval | `/console/providers/:npi/approval` | ANALYST, SUPERVISOR | The analyst approval step that sets ACTIVE after a PASS screening (or DENIED). | `GET /api/v1/providers/{npi}`<br>`GET /api/v1/screenings`<br>`POST /api/v1/providers/{npi}/approval` | D-019, D-003 (due date = approval + 5 years); R-PRVSCR01-ADD-03 |
| console | record-sanction | `/console/providers/:npi/sanctions/new` | SUPERVISOR | Terminate, suspend or reinstate a provider with reason and effective date; history kept. | `POST /api/v1/sanctions`<br>`GET /api/v1/sanctions` | PRVSAN01 rules (sanctions-service, provider-service); D-006, D-018, D-026, D-027 |
| console | risk-rules | `/console/admin/risk-rules` | ADMIN | The data-driven taxonomy-prefix table (legacy values by default). | `GET /api/v1/risk-rules`<br>`PUT /api/v1/risk-rules` | PRVRSK01 rules (risk-service); Q-005 |
| console | batch-jobs | `/console/admin/jobs` | ADMIN | Run the scheduled jobs on demand for a business date and download the legacy-format files. | `POST /api/v1/batch/intake-runs`<br>`POST /api/v1/batch/screening-runs`<br>`POST /api/v1/batch/revalidation-sweeps`<br>`POST /api/v1/batch/sanction-intake-runs`<br>`POST /api/v1/batch/directory-extracts`<br>`POST /api/v1/batch/tmsis-extracts`<br>`GET /api/v1/batch/tmsis-extracts` | PRVDAY01 and PRVMTH01 steps; D-017, D-020, D-024, D-015 |
| public | directory | `/directory` | PUBLIC | Public search of active providers (FHIR API alongside). | `GET /api/v1/directory/providers` | PRVDIR01 rules (directory-service); D-028, Q-008 |

## The CICS PINQ inquiry, rebuilt (from `modern/docs/analysis/programs/PRVCICS1.md`)

PRVCICS1 **receives** the NPI (`DFHCOMMAREA` / map input, 10 bytes) and **sends** `WS-PROVIDER-AREA` (every PRVRECRD field) plus the
message `WS-MSG`, which the legacy screen never actually sends (D-022). The console shows all of them:

| CICS field | Direction | Console screen | Shown as |
|---|---|---|---|
| NPI (`WS-NPI-IN`, COMMAREA) | received | provider-search | NPI input; also kept as the last search (replaces the COMMAREA) |
| (new) Medicaid ID, name | received | provider-search | extra search inputs (R-PRVCICS1-ADD-07) |
| PRV-NPI | sent | provider-detail | NPI, in full |
| PRV-MEDICAID-ID | sent | provider-detail | Medicaid ID |
| PRV-TYPE-CODE | sent | provider-detail | Individual (IN) / Group (GR) / Facility (FA) |
| PRV-TAXONOMY | sent | provider-detail | Taxonomy code |
| PRV-RISK-TIER | sent | provider-detail | Limited (L) / Moderate (M) / High (H) |
| PRV-ENROLL-STATUS | sent | provider-detail | Pending (P) / Active (A) / Terminated (T) / Denied (D) / Suspended (S) |
| PRV-SCREEN-FLAG | sent | provider-detail | Unscreened (U) / Pass (P) / Fail (F) |
| PRV-EFFECTIVE-DATE | sent | provider-detail | MM/DD/YYYY |
| PRV-REVAL-DUE-DATE | sent | provider-detail | MM/DD/YYYY |
| PRV-LAST-NAME, PRV-FIRST-NAME, PRV-LICENSE-NO, PRV-LICENSE-STATE | sent | provider-detail | individual view (type IN) |
| PRV-LEGAL-NAME, PRV-DBA-NAME, PRV-OWNER-NPI | sent | provider-detail | organisation view (type GR or FA) |
| `WS-MSG` (ACTIVE / TERMINATED / PENDING / DENIED / PROVIDER NOT FOUND / VSAM READ ERROR) | sent (legacy: never) | provider-detail, provider-search | status banner with text and code; 'No provider found' message; error summary |

