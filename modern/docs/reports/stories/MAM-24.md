```
Phase / item:      MAM-24 One-command local stack: docker compose, health checks, make up/down/logs/seed/reset
Outcome:           DONE (Phase 5 platform task; test recorded in MAM-24.tests.sha256); AC-1..AC-8 checked 2026-11-19 20:50-20:53
What was done:     Stack built in Phase 5 (infra/docker-compose.yml project "pem", pem.sh + Makefile, init-databases.sh, seed).
                   Two small fixes found by this AC check (lead, 2026-11-19):
                   - AC-2: web-ui image tag "nginx:alpine" had no version; now nginx:1.31.2-alpine with the SAME digest
                     (the digest's NGINX_VERSION is 1.31.2), so the image is unchanged and nothing was restarted
                   - AC-6: "make e2e" ran only the default Playwright suite since the journeys moved behind PEM_JOURNEYS=1;
                     it now runs the default suite, then every journey J1-J7 from a fresh seed (pem.sh e2e)
Files created/changed: infra/docker-compose.yml (nginx tag), pem.sh (e2e target); this report
Checks run:        AC-1 docker compose -p pem ps (20:30): postgres, kafka, keycloak, wiremock, mailpit, pem-apps (gateway + 9
                        services), web-ui all Up (healthy); postgres 16.4, Kafka native 4.3.2 (KRaft single node)
                   AC-2 compose parsed: every image has a version tag and a sha256 digest; every container has a health check;
                        pem-apps depends on postgres, kafka, keycloak, wiremock, mailpit (service_healthy), web-ui on pem-apps;
                        docker compose config --quiet -> valid after the tag fix
                   AC-3 9 databases provider_db .. tmsis_db, each owned by its own user <service>_svc; Flyway history present
                   AC-4 infra/.env.local holds generated values (names only read), git check-ignore -> ignored; no value in any
                        repository file (scan of tracked + untracked files, 0 hits; see MAM-26)
                   AC-5 compose name "pem"; PEM_PG_HOST_PORT (default 5433) and PEM_PROVIDER_HOST_PORT (default 18081)
                   AC-6 Makefile targets call pem.sh; down keeps volumes (compose down without -v); logs follows; build/test run
                        Gradle + npm; seed loads the seed; reset asks "Type yes" before down -v (read, not run); e2e fixed above
                   AC-7 a test mail to SMTP 1025 appeared in Mailpit (then deleted, inbox back to 0); WireMock admin lists 27
                        mappings (5 mapping files mounted from infra/wiremock)
                   AC-8 docs/runbooks/local-run.md: prerequisites, commands, URLs and ports, the .env.local path, and the
                        statement that a run from a truly clean clone has not been done yet
Not verified:      make up / make down / make reset were not executed now (the stack must stay up at the seed state; reset is
                   never run); the fixed "make e2e" was not run end to end (its parts ran today: default suite 152/152 at 20:38,
                   journeys J1-J7 PASSED 20:10-20:24); a run from a truly clean clone (AC-8 records that it was not done)
Questions for you: none
Next step:         Jira Done (user approval 2026-11-19 20:34)
```
