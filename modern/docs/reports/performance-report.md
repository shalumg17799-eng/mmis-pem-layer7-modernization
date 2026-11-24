# Performance smoke report (MAM-88)

Run `20261124-083002` of `modern/tools/perf/run_perf_smoke.py`, started 2026-11-24T08:30:02Z, finished 2026-11-24T08:42:59Z (UTC), git HEAD `fbe7a94` plus the uncommitted work tree at the time of the run, business date of the jobs 2026-11-24.

**Result: 6 of 6 targets MET, 0 NOT MET, 0 NOT MEASURED.** Every target below is a DEFAULT of nfr.md (a placeholder until the user gives volumes and targets), so MET and NOT MET are against placeholders, not against agreed service levels.

## Targets (AC-5)

| # | Target | Limit | Measured | Result | nfr.md |
|---|---|---|---|---|---|
| 1 | Provider search, GET /api/v1/providers: p95 at 20 concurrent users (AC-2) | under 500 ms | p95 144 ms (avg 72.1 ms, p99 212 ms, max 4,329 ms); 16,365 requests, 272.5/s, 20 users, 60s, 0.00 % failed | **MET** | DEFAULT |
| 2 | Provider detail, GET /api/v1/providers/{npi}: p95 at 20 concurrent users (AC-2) | under 500 ms | p95 60.0 ms (avg 32.6 ms, p99 88.6 ms, max 229 ms); 36,250 requests, 603.4/s, 20 users, 60s, 0.00 % failed | **MET** | DEFAULT |
| 3 | Application submit, POST /api/v1/applications: p95 (AC-3) | under 1,000 ms | p95 sequential 34.8 ms (40 submits, 1 at a time), concurrent 63.2 ms (40 submits, 5 at a time); the worse is shown; 80 of 80 answered 201 VALIDATED | **MET** | DEFAULT |
| 4 | Screening of one provider against the mocked sources, POST /api/v1/screenings to COMPLETED (AC-3) | under 5,000 ms | 137 ms from the POST to COMPLETED (1 screening(s) of one provider each, result PASS; the service's own started-to-completed time 0.0 s; polled every 0.1 s) | **MET** | DEFAULT |
| 5 | T-MSIS extract of 10,000 providers, POST to COMPLETED (projection of 15 min for 100,000) (AC-4) | under 90 s | 1.6 s from the POST to COMPLETED, business date 2026-11-24 (the service's own run time 1.1 s); counts {"rows": 6008, "futureEnrolments": 0, "archiveGeneration": 35} | **MET** | DEFAULT |
| 6 | Revalidation sweep of 10,000 providers, POST to COMPLETED (projection of 15 min for 100,000) (AC-4) | under 90 s | 2.1 s from the POST to COMPLETED, business date 2026-11-24 (the service's own run time 1.8 s); counts {"queued": 1708, "noDueDate": 0} | **MET** | DEFAULT |

The `nfr.md` rows behind them (section `Performance (DEFAULT until the user gives volumes)`):

- Provider search / detail (p95): under 500 ms at 20 concurrent users
- Application submit (p95): under 1 s
- Screening of one provider (mocked sources): under 5 s
- Monthly T-MSIS extract / revalidation sweep: 100,000 providers in under 15 minutes

## Targets not met (not tuned away)

None.

## The 90 s job target is a projection

nfr.md states the job target for 100,000 providers in under 15 minutes (900 s, 9 ms per provider). The story scales it to the 10,000 providers of this smoke: **90 s**. That is a linear projection of a target, not a measurement at 100,000 providers (a 100,000-provider load is out of scope, QF-10 option B). A job that is fast at 10,000 providers can still be slow at 100,000 if its time is not linear in the number of providers, and a job that misses 90 s here does not by itself miss 15 minutes at 100,000 either.

## Data set (AC-1)

- Generator: `generate.py perf`, seed 20260115, 10,000 providers; file `provider-master-perf.dat` holds 10,000 records of 130 bytes (1,310,000 bytes).
- NPIs: 0 with an invalid check digit, 0 duplicates, 0 in a reserved range; names: 0 that are not fake (individuals `TESTPROV PERFnnnnn`, organisations `SAMPLE PERF ...`).
- Mix: types {"FA": 2500, "GR": 1500, "IN": 6000}, statuses {"A": 6000, "D": 800, "P": 1200, "S": 800, "T": 1200}.
- GET /api/v1/providers reported a total of 10,016 (16 seeded + the set; the story needs at least 10,000); the walk of the NPI block through the API (afterNpi keyset) returned 10,000 providers in 51 calls (1.0 s) and equals the generated NPIs.
- Keyset paging (`afterNpi`, contract 1.2.0): **honoured**.
- AC-1: **MET**.

## How it was measured

- **Search and detail (AC-2):** k6 `grafana/k6:2.4.0@sha256:d41bb79cd8de5a7ad619b53570e3712633e8bff0937010a5c6c57a4747e7a276` in a throwaway container on the compose network, calling the gateway as `http://pem-apps:8080` (inside Docker: no Docker Desktop port forwarder in the path), 20 virtual users for 60s each, think time 0 ms (every user always has a request in flight, so 20 requests are concurrent for the whole run), ANALYST token, an NPI sample read back through the API. Search mixes 60 % list pages over the whole set (offset paging), 20 % status filters, 10 % NPI lookups, 5 % Medicaid-ID lookups, 5 % name searches; the target is the p95 of all of them together, the p95 per kind is listed below. A run only counts when under 1 % of the requests failed and over 99 % of the checks passed, so fast error answers cannot pass. No warm-up is discarded.
  - search.js p95 per kind: medicaidId 146 ms, name 167 ms, npi 131 ms, page 144 ms, status 139 ms
  - detail.js p95 per kind: detail 60.0 ms
- **Submit and screening (AC-3):** a Python client on the host (`http://127.0.0.1:8080`, through the published host port, on Docker Desktop its port forwarder; one keep-alive connection per worker), nearest-rank percentiles. Submits: 40 one at a time, then 40 with 5 at a time, valid group applications under the PROVIDER role; the stricter of the two p95 values is the result. Screening: POST /api/v1/screenings of a perf individual (ANALYST), polled every 0.1 s until COMPLETED; the time is the wall time from just before the POST to the poll that saw COMPLETED (an upper bound within one poll interval).
- **Jobs (AC-4):** POST to the batch endpoint with an ADMIN token, then polled every 0.5 s until COMPLETED (the T-MSIS run through the list of runs, which is all its contract offers). Wall time of the client, from just before the POST; the service's own started-to-ended time is listed next to it. The T-MSIS extract ran first and alone; the sweep second (it starts one re-screening per due provider, which the runner waited for before it reseeded).

## Machine conditions

- Free memory at the start: **1169 MB** of 16127 MB; at the end 910 MB. The run waited 480 s in total for free memory to be at least 1024 MB.
- CPU: 8 logical processors, load 46 % at the start. Processes at the start: java=2,node=4,python=7; at the end: java=2,node=4,python=7. Running containers at the start: 26 (Docker is shared with other projects).
- pem containers at the start: pem-pem-apps-1 cpu 3.72% mem 508.4MiB / 1GiB; pem-postgres-1 cpu 1.16% mem 79.68MiB / 256MiB; pem-keycloak-1 cpu 0.19% mem 533.5MiB / 600MiB.
- Other builds and test runs may have run on this machine while the measurements ran (the runner cannot see them; the stack lock only keeps other users of the stack away). The pem services run in ONE JVM with a 512 MB heap and C1-only compilation (ADR-011), so these numbers are those of the local runtime, not of the later deployment.

## Steps and timings

| Step | Started (UTC) | Seconds | Status |
|---|---|---|---|
| preflight (containers, k6 image, tokens, afterNpi, machine) | 2026-11-24T08:30:02Z | 8.3 | OK |
| (a) normal seed | 2026-11-24T08:30:10Z | 11.4 | OK |
| (b) load the providers and check AC-1 | 2026-11-24T08:30:22Z | 9.6 | OK |
| (c) AC-2 k6: search and detail, 20 users | 2026-11-24T08:30:31Z | 614.8 | OK |
| (d) AC-3 submit and screening | 2026-11-24T08:40:46Z | 9.2 | OK |
| (e) AC-4 T-MSIS extract and revalidation sweep | 2026-11-24T08:40:55Z | 5.5 | OK |
| (f) restore the seed state | 2026-11-24T08:41:01Z | 118.8 | OK |

## State left behind

- Restore of the seed state: **OK**. re-screenings finished after 90 s seeded (attempt 1): API total 16, provider_db.provider 16, revalidation_db.tracked_provider 16, revalidation_db.revalidation_case 0, enrollment_db.application 0, provider_db.provider perf block 0, revalidation_db.tracked_provider perf block 0
- Not reset by the seed, so left in place: tmsis_db now holds 35 extract run(s), 12282 extract row(s) and 35 archive generation(s) (this run added one run with its rows and one archive generation); the Spring Batch job tables and the Kafka topics keep this run's jobs and events.

## Not verified

- One run on one shared machine: no repetition, so no spread between runs; the numbers move with free memory, CPU and what else runs in Docker.
- The jobs are scaled to 10,000 providers; nothing here says how they behave at 100,000.
- Availability, failover and a 100,000-provider load are out of scope (MAM-88).
- Search and detail were measured from inside the compose network, submit, screening and the jobs from the host: the two vantage points differ by the Docker Desktop port forwarder.
- The mocked sources (WireMock) answer immediately for the clean path; real sources will be slower, so the screening time is a lower bound for production.
