# Operations runbook: the six batch jobs (MAM-89 AC-6)

The legacy daily job PRVDAY01 (PRVENR01 intake, PRVSCR01 screening) and month-end job PRVMTH01 (PRVREV01 sweep,
PRVTMS01 T-MSIS), plus the never-scheduled PRVSAN01 (sanction file) and PRVDIR01 (directory file), are Spring Batch jobs
in the new system (ADR-004). Every job takes a **business date** (`businessDate`, ISO `YYYY-MM-DD` in the API,
MM/DD/YYYY on the page), records its counts and its failure reason, and can be re-run for a date (D-020, D-024).

All calls go through the gateway `http://localhost:8080` with an ADMIN token (the SYSTEM service account may start runs;
only ADMIN reads files). In the browser: sign in as `admin1` and open **Batch jobs** (`/console/admin/jobs`): choose the
job and the business date, **Run job**; the **Recent runs** table follows the run to Completed or Failed and offers
**Download** for the legacy file.

| Job (legacy) | Start (POST) | Run result (GET) | Legacy file (GET, ADMIN) | Input | Configured schedule |
|---|---|---|---|---|---|
| Enrolment intake (PRVENR01, APPINPUT 200 B) | `/api/v1/batch/intake-runs` (multipart: `file`, `businessDate`, optional `batchId`) | `/api/v1/batch/intake-runs/{runId}` | `.../{runId}/error-file` (APPERROR 250 B) | the APPINPUT file | none: started on demand; a scheduled start needs a file location (Q-B9, out of scope of MAM-56) |
| Screening run (PRVSCR01) | `/api/v1/batch/screening-runs` (`{"businessDate": "2026-03-31"}`) | `/api/v1/batch/screening-runs/{runId}` | `.../{runId}/results-file` (SCRNOUT 67 B) | unscreened providers (flag U) | none configured (the daily cron is out of scope of MAM-32; ADR-004) |
| Revalidation sweep (PRVREV01) | `/api/v1/batch/revalidation-sweeps` (`businessDate`) | `/api/v1/batch/revalidation-sweeps/{runId}` | `.../{runId}/queue-file` (REVALQ 80 B) | ACTIVE providers due on or before the date | none configured (the monthly cron is out of scope of MAM-45; ADR-004) |
| Sanction intake (PRVSAN01, SANCIN 80 B) | `/api/v1/batch/sanction-intake-runs` (multipart: `file`, `businessDate`) | `/api/v1/batch/sanction-intake-runs/{runId}` | - (results are the sanction history) | the SANCIN file | none: on demand (Q-B9, out of scope of MAM-55) |
| Directory extract (PRVDIR01) | `/api/v1/batch/directory-extracts` (`businessDate`) | `/api/v1/batch/directory-extracts/{runId}` | `.../{runId}/file` (DIROUT 100 B) | ACTIVE providers | none configured (out of scope of MAM-67) |
| T-MSIS extract (PRVTMS01) | `/api/v1/batch/tmsis-extracts` (`businessDate`) | `GET /api/v1/batch/tmsis-extracts` (list of runs and archive generations) | `.../{runId}/file` (TMSISOUT 120 B) | ACTIVE providers | **`0 0 22 L * *` (22:00 UTC on the last day of each month)**, `pem.tmsis.cron` / `PEM_TMSIS_CRON`, `-` turns it off; the business date is then the Clock's date (MAM-75 AC-7) |

A run answers `202` with its `runId`; the run result shows `status` (`STARTED`, `COMPLETED`, `FAILED`), `counts` (for
example `read`, `written`, `errored`, `skipped`, `queued`, `rows`) and `error` (the failure reason, for example the record
number of a wrong-length record). A second run for the same business date is allowed: the intake jobs skip the
records already applied for that date, the sweep opens no second case, the extracts write the same bytes.

Example (Git Bash, token from Keycloak with the admin password from `modern/infra/.env.local`; never print it):

```bash
pw=$(grep '^KC_ADMIN_PASSWORD=' infra/.env.local | cut -d= -f2-)   # from modern/; the value is never echoed
TOKEN=$(curl -s -X POST http://localhost:8180/realms/pem/protocol/openid-connect/token -d grant_type=password \
  -d client_id=pem-test -d username=admin1 --data-urlencode "password=$pw" | sed -E 's/.*"access_token":"([^"]+)".*/\1/'); unset pw
curl -s -X POST http://localhost:8080/api/v1/batch/revalidation-sweeps -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"businessDate":"2026-03-31"}'
curl -s http://localhost:8080/api/v1/batch/revalidation-sweeps/<runId> -H "Authorization: Bearer $TOKEN"
curl -s -o REVALQ.D20260331 http://localhost:8080/api/v1/batch/revalidation-sweeps/<runId>/queue-file -H "Authorization: Bearer $TOKEN"
```

## Logs and health

- Health: `./pem.sh ps` (every container `healthy`); per service
  `curl -s http://localhost:<port>/actuator/health/readiness` (provider 18081 by default, enrollment 8082 ... tmsis 8089,
  gateway 8080). The `pem-apps` health check calls all ten.
- Logs: `./pem.sh logs` (all pem containers) or
  `docker compose -p pem --env-file infra/.env.local -f infra/docker-compose.yml logs -f --tail 200 pem-apps`.
  Lines are JSON (ECS); the field `pemService` names the service and `correlationId` follows one request or run across
  services. Job lines carry the legacy text where the story keeps it, for example
  `PRVENR01: STARTING ENROLLMENT LOAD - BATCH <id>` and `PROVIDERS TERMINATED=00001`.
- Failed runs: see troubleshooting.md, "A batch run in status FAILED".
