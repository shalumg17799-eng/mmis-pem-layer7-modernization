# CI runbook (MAM-39)

The CI workflow is `modern/.github/workflows/ci.yml`. It is **inert where it is**: GitHub only runs workflows from the
repository root. To switch it on, copy it to `.github/workflows/ci.yml` at the repository root and push (a user decision;
nothing has been pushed). Every action and tool in it has an exact version; change a version only on purpose.

Anyone without the workflow runs the same four steps by hand from `modern/` (Git Bash on Windows, any shell elsewhere):

| Step | What it proves | Commands |
|---|---|---|
| 1. Build and full check | Gradle build; unit, acceptance, integration, contract and ArchUnit tests; JaCoCo (85% per service) and PIT; red on any failure | `./gradlew --no-daemon build checkAll` |
| 2. Contract lint | All OpenAPI files lint; the AsyncAPI file validates; roles, events and rule/field coverage are consistent | `npx --yes @redocly/cli@1.34.3 lint contracts/openapi/*.yaml`<br>`npx --yes @asyncapi/cli@2.14.0 validate contracts/asyncapi/events.yaml`<br>`python tools/checks/check_phase3.py` (from the repository root: `python modern/tools/checks/check_phase3.py`) |
| 3. Web UI tests | Lint and unit tests; a failing or skipped test fails | `cd web-ui && npm ci --no-audit --no-fund && npm run -s lint && npm test` |
| 4. Compose smoke test | The stack starts healthy, the gateway answers and the walking-skeleton provider lookup works | `make up` (or `./pem.sh up`; waits for every container to be healthy)<br>`curl -sf http://localhost:8080/actuator/health`<br>`./pem.sh seed`, then GET `http://localhost:8080/api/v1/providers/1005940865` with an analyst1 token (password from `infra/.env.local`, never printed)<br>`make down` |

Tool versions used by the workflow: Ubuntu runner 24.04, Temurin Java 21.0.7, Node 20.19.2, Python 3.12.10,
actions/checkout 4.2.2, actions/setup-java 4.7.1, actions/setup-node 4.4.0, actions/setup-python 5.6.0,
@redocly/cli 1.34.3, @asyncapi/cli 2.14.0, PyYAML 6.0.2.

If step 4 fails, print the logs of every pem container that is not healthy:
`docker ps -a --filter label=com.docker.compose.project=pem` then `docker logs --tail 200 <name>`.

Not done yet: the workflow has never run (no push, no GitHub runner); the pinned versions were chosen, not tested.
