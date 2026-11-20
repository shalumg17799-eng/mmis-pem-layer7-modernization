# Non-functional requirements

**Status: PROPOSED (Phase 3).** Values marked DEFAULT are placeholders until the user gives volumes and targets.

## Security: roles per endpoint

Keycloak realm `pem`. Roles: PROVIDER, ANALYST, SUPERVISOR, ADMIN, plus SYSTEM for service accounts (ADR-007). `PUBLIC` = no token.
A PROVIDER only ever sees their own provider, applications and notices (checked by a test per endpoint). Every endpoint has a test per role (skill section 9).

| Service | Method | Path | Roles |
|---|---|---|---|
| provider-service | GET | `/api/v1/providers` | ANALYST, SUPERVISOR, ADMIN, SYSTEM |
| provider-service | GET | `/api/v1/providers/{npi}` | PROVIDER, ANALYST, SUPERVISOR, ADMIN, SYSTEM |
| provider-service | POST | `/api/v1/providers/{npi}/approval` | ANALYST, SUPERVISOR |
| provider-service | POST | `/api/v1/providers/{npi}/status-changes` | SYSTEM, SUPERVISOR |
| provider-service | GET | `/api/v1/providers/{npi}/audit` | SUPERVISOR, ADMIN |
| provider-service | GET | `/api/v1/legacy/provider-master` | ADMIN |
| enrollment-service | POST | `/api/v1/applications` | PROVIDER, ANALYST |
| enrollment-service | GET | `/api/v1/applications` | PROVIDER, ANALYST, SUPERVISOR |
| enrollment-service | GET | `/api/v1/applications/{applicationId}` | PROVIDER, ANALYST, SUPERVISOR |
| enrollment-service | POST | `/api/v1/batch/intake-runs` | ADMIN, SYSTEM |
| enrollment-service | GET | `/api/v1/batch/intake-runs/{runId}` | ADMIN |
| enrollment-service | GET | `/api/v1/batch/intake-runs/{runId}/error-file` | ADMIN |
| screening-service | GET | `/api/v1/screenings` | ANALYST, SUPERVISOR, ADMIN |
| screening-service | POST | `/api/v1/screenings` | ANALYST, SUPERVISOR, SYSTEM |
| screening-service | GET | `/api/v1/screenings/{screeningId}` | ANALYST, SUPERVISOR, ADMIN |
| screening-service | POST | `/api/v1/batch/screening-runs` | ADMIN, SYSTEM |
| screening-service | GET | `/api/v1/batch/screening-runs/{runId}` | ADMIN |
| screening-service | GET | `/api/v1/batch/screening-runs/{runId}/results-file` | ADMIN |
| risk-service | GET | `/api/v1/risk-rules` | ANALYST, SUPERVISOR, ADMIN |
| risk-service | PUT | `/api/v1/risk-rules` | ADMIN |
| risk-service | POST | `/api/v1/risk-assessments` | ANALYST, SUPERVISOR, SYSTEM |
| revalidation-service | GET | `/api/v1/revalidations` | ANALYST, SUPERVISOR |
| revalidation-service | POST | `/api/v1/revalidations/{caseId}/completion` | SYSTEM, ANALYST |
| revalidation-service | POST | `/api/v1/batch/revalidation-sweeps` | ADMIN, SYSTEM |
| revalidation-service | GET | `/api/v1/batch/revalidation-sweeps/{runId}` | ADMIN |
| revalidation-service | GET | `/api/v1/batch/revalidation-sweeps/{runId}/queue-file` | ADMIN |
| sanctions-service | POST | `/api/v1/sanctions` | SUPERVISOR |
| sanctions-service | GET | `/api/v1/sanctions` | ANALYST, SUPERVISOR, ADMIN |
| sanctions-service | POST | `/api/v1/batch/sanction-intake-runs` | ADMIN, SYSTEM |
| sanctions-service | GET | `/api/v1/batch/sanction-intake-runs/{runId}` | ADMIN |
| notification-service | GET | `/api/v1/notices` | PROVIDER, ANALYST, SUPERVISOR, ADMIN |
| notification-service | GET | `/api/v1/notice-templates` | ADMIN |
| directory-service | GET | `/api/v1/directory/providers` | PUBLIC |
| directory-service | GET | `/fhir/Practitioner` | PUBLIC |
| directory-service | GET | `/fhir/Organization` | PUBLIC |
| directory-service | GET | `/fhir/PractitionerRole` | PUBLIC |
| directory-service | POST | `/api/v1/batch/directory-extracts` | ADMIN, SYSTEM |
| directory-service | GET | `/api/v1/batch/directory-extracts/{runId}` | ADMIN |
| directory-service | GET | `/api/v1/batch/directory-extracts/{runId}/file` | ADMIN |
| tmsis-service | POST | `/api/v1/batch/tmsis-extracts` | ADMIN, SYSTEM |
| tmsis-service | GET | `/api/v1/batch/tmsis-extracts` | ADMIN |
| tmsis-service | GET | `/api/v1/batch/tmsis-extracts/{runId}/file` | ADMIN |

## Audit

- provider-service writes `audit_log` (who, when, action, before, after, correlation id) for every provider state change (skill section 8).
- Screening results (per source, with verdict time and detail) and sanction history are kept: the 42 CFR 455 audit trail (D-005, D-018).
- Every request and event carries `X-Correlation-Id`; logs are JSON.

## Performance (DEFAULT until the user gives volumes)

| Measure | DEFAULT target |
|---|---|
| Provider search / detail (p95) | under 500 ms at 20 concurrent users |
| Application submit (p95) | under 1 s |
| Screening of one provider (mocked sources) | under 5 s |
| Monthly T-MSIS extract / revalidation sweep | 100,000 providers in under 15 minutes |

## Availability (DEFAULT)

Business hours 99.5%; batch jobs can be re-run for a business date and are idempotent; Kafka consumers are idempotent.

## Accessibility

WCAG 2.1 AA (frontend-stack skill section 5); every Playwright journey runs an axe scan and fails on any violation.

## Data retention (OPEN: Q-012)

No retention period is defined anywhere in the estate. Default applied: nothing is deleted or archived away until the business sets a policy.
