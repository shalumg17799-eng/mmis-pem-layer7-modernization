# CI runbook (MAM-39)

The CI workflow is `.github/workflows/ci.yml` at the repository root. It was kept inert under `modern/.github/workflows/`
until the user decided to switch it on (2026-11-20 19:32); it now runs on every push and pull request. Its run results
are recorded in `modern/docs/reports/stories/MAM-39.md`. Every action and tool in it has an exact version; change a
version only on purpose.

Anyone without the workflow runs the same four steps by hand from `modern/` (Git Bash on Windows, any shell elsewhere):

| Step | What it proves | Commands |
|---|---|---|
| 1. Build and full check | Gradle build; unit, acceptance, integration, contract and ArchUnit tests; JaCoCo (85% per service) and PIT; red on any failure | `./gradlew --no-daemon build checkAll` |
| 2. Contract lint | All OpenAPI files lint; the AsyncAPI file validates; roles, events and rule/field coverage are consistent | `npx --yes @redocly/cli@1.34.3 lint contracts/openapi/*.yaml`<br>`npx --yes @asyncapi/cli@2.14.0 validate contracts/asyncapi/events.yaml`<br>`python tools/checks/check_phase3.py` (from the repository root: `python modern/tools/checks/check_phase3.py`) |
| 3. Web UI tests | Lint and unit tests; a failing or skipped test fails | `cd web-ui && npm ci --no-audit --no-fund && npm run -s lint && npm test` |
| 4. Compose smoke test | The stack starts healthy, the gateway answers and the walking-skeleton provider lookup works | `make up` (or `./pem.sh up`; waits for every container to be healthy)<br>`curl -sf http://localhost:8080/actuator/health`<br>`./pem.sh seed`, then GET `http://localhost:8080/api/v1/providers/1005940865` with an analyst1 token (password from `infra/.env.local`, never printed)<br>`make down` |

Tool versions used by the workflow: Ubuntu runner 24.04, Temurin Java 21.0.7, Node 20.19.2 for the contract lint and
Node 22.22.2 for the web UI tests and the compose smoke test (the web UI test libraries need Node 22), Python 3.12.10,
actions/checkout 4.2.2, actions/setup-java 4.7.1, actions/setup-node 4.4.0, actions/setup-python 5.6.0,
@redocly/cli 1.34.3, @asyncapi/cli 2.14.0, PyYAML 6.0.2.

If step 4 fails, print the logs of every pem container that is not healthy:
`docker ps -a --filter label=com.docker.compose.project=pem` then `docker logs --tail 200 <name>`.

State on 2026-11-20: the workflow ran once (commit f72d4e5) and was red in the Gradle job and the web UI job; the causes,
the fixes and the local reproductions in Linux containers are in `modern/docs/reports/stories/MAM-39.md`. The fixes reach
GitHub with the next push. Time limits: 150 minutes for the Gradle job (the build is sequential and runs PIT for nine
services), 15 for the contract lint, 30 for the web UI tests, 45 for the compose smoke test.
