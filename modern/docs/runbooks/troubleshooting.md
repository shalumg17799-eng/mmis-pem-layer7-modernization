# Troubleshooting runbook (MAM-89 AC-7)

Run the commands from `modern/` in Git Bash. `C` below stands for the compose prefix of the pem stack:

```bash
C="docker compose --project-name pem --env-file infra/.env.local -f infra/docker-compose.yml --profile apps"
```

Only ever act on project `pem`: never stop, remove or limit another project's container (shared machine). Never print
or paste a value of `infra/.env.local`.

## 1. A container is not healthy

Look:
```bash
./pem.sh ps                                   # every container should read (healthy)
$C logs --tail 200 <service>                  # postgres | kafka | keycloak | wiremock | mailpit | pem-apps | web-ui
for p in 8080 18081 8082 8083 8084 8085 8086 8087 8088 8089; do echo "$p $(curl -s -o /dev/null -w '%{http_code}' http://localhost:$p/actuator/health/readiness)"; done
docker stats --no-stream                      # memory: PEM needs about 1.2 GiB, 2.5 GiB at its limits
```
`pem-apps` hosts the gateway and the nine services; its health check calls all ten readiness endpoints, so one
unready service makes the whole container unhealthy. The JSON log lines carry `pemService` (which service) and
`correlationId`.

Fix:
```bash
$C restart <service>                          # pem-apps takes about 2 minutes to come back
./pem.sh down && ./pem.sh up                  # whole stack; data volumes are kept
```
If memory is short, show `docker stats` to the owner of the other projects and wait; do not stop their containers.

## 2. A host port is busy on `./pem.sh up`

Look: the error names the port. Find the owner (PowerShell):
```powershell
Get-NetTCPConnection -LocalPort 5433 -State Listen | Select-Object LocalPort, OwningProcess
Get-Process -Id <OwningProcess>
```
Fix: the Postgres and provider-service host ports are configurable:
```bash
PEM_PG_HOST_PORT=5434 PEM_PROVIDER_HOST_PORT=18091 ./pem.sh up
```
The other published ports are fixed (8080, 8082-8089, 8180, 8280, 8025, 1025, 9092, 5173: Keycloak and CORS accept only
http://localhost:5173); free them on the host, or stop the PEM stack that holds them (`./pem.sh down`).

## 3. A sign-in fails

Look:
```bash
curl -s http://localhost:8180/realms/pem/.well-known/openid-configuration | head -c 200; echo   # Keycloak answers
pw=$(grep '^KC_ANALYST_PASSWORD=' infra/.env.local | cut -d= -f2-)
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8180/realms/pem/protocol/openid-connect/token \
  -d grant_type=password -d client_id=pem-test -d username=analyst1 --data-urlencode "password=$pw"; unset pw   # 200 = the user and password are fine
```
Fix by cause:
- Wrong password typed: open `infra/.env.local` yourself and copy it (never paste it into chat, tickets or logs).
- "Invalid redirect" or a CORS error: the web UI must be opened as http://localhost:5173 (the only redirect and origin
  of the `pem-web` client), not 127.0.0.1 or another port.
- The page loops or says the session expired: close the window or use a new private window.
- `ERR_CONNECTION_RESET` or a hanging page on this machine: `localhost` resolves to `::1`, where WSL's relay answers;
  start the browser with `--host-resolver-rules="MAP localhost 127.0.0.1"` (see docs/DEMO.md).
- Keycloak not healthy: section 1.

## 4. Events are not flowing (including dead-lettered events)

Symptoms: an application stays Validated and the provider is never registered or screened; a decision is recorded
but no notice arrives; the directory does not change.

Look:
```bash
# events written but not yet sent to Kafka (transactional outbox, one table per service database)
for db in enrollment_db provider_db screening_db risk_db revalidation_db sanctions_db notification_db directory_db tmsis_db; do
  echo "$db $($C exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d $db -Atc 'select count(*) from outbox_event where published_at is null'" 2>/dev/null)"
done
$C logs --tail 500 pem-apps | grep -E 'not published yet|\.DLT|exhausted|Kafka' | tail -20
$C logs --tail 50 kafka
```
- Rows with `published_at` empty that do not go away: Kafka is unreachable or stuck (the relay logs
  "outbox event <id> not published yet (will retry)"); nothing is lost, the relay retries.
- A consumer that fails on a message retries it the configured number of times, then parks it on `<topic>.DLT` with its
  original envelope (service-kit, MAM-31 AC-7); later messages keep flowing. The error lines name the event id and the
  correlation id; follow the id with `grep <correlationId>` through the logs of all services.

Fix:
```bash
$C restart kafka          # wait about 30 s; the outbox relays publish the waiting rows
```
If the consumers lost their group coordinator (seen once on this machine), restart Kafka as above and then, for a demo
or test stack, `./pem.sh seed` and start the journey again. A dead-lettered message is not replayed automatically: fix
the cause (the log line names it), then repeat the business action (for example re-run the batch job for the same
business date, which is idempotent) or record the decision again. The native Kafka image has no command-line tools, so
topics are not listed from the container.

## 5. A batch run ends in status FAILED

Look:
```bash
curl -s http://localhost:8080/api/v1/batch/<job-path>/<runId> -H "Authorization: Bearer $TOKEN"   # status, counts, error
$C logs --tail 500 pem-apps | grep -E '<runId>|PRV(ENR|SCR|REV|SAN|DIR|TMS)01' | tail -30
$C exec -T postgres sh -c "psql -U \"\$POSTGRES_USER\" -d <service>_db -c 'select job_execution_id, status, exit_code, left(exit_message, 200) from batch_job_execution order by job_execution_id desc limit 5'"
```
(`<job-path>` and the token: see operations.md.) The `error` field names the cause, for example "record 2 is 150 bytes,
not 200" (a wrong-length record in an intake file), an invalid business date, or a service that did not answer.

Fix: correct the input (every APPINPUT record 200 bytes, SANCIN 80 bytes; the business date a real calendar date) or the
failing dependency (sections 1 and 4), then run the job again for the same business date: records already applied for
that date are skipped, the sweep opens no second case and the extracts write the same bytes (D-020, D-024).
