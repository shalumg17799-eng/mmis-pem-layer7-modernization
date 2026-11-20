#!/usr/bin/env bash
# PEM local stack, one command per task (MAM-24). The Makefile calls this script; use it directly where make is missing.
#   ./pem.sh up | down | ps | logs | build | test | e2e | a11y | seed | j1 [runs] | journey <name> [runs] | reset
# Only compose project "pem" is touched. Secrets: infra/.env.local (generated, git-ignored, never printed).
set -euo pipefail
cd "$(dirname "$0")"
COMPOSE=(docker compose --project-name pem --env-file infra/.env.local -f infra/docker-compose.yml --profile apps)
PORTS=("${PEM_PG_HOST_PORT:-5433}" 9092 8180 8280 8025 1025 8080 "${PEM_PROVIDER_HOST_PORT:-18081}" 8082 8083 8084 8085 8086 8087 8088 8089 5173)

port_owner_ok() {  # free, or already published by a pem container
  local p=$1
  if docker ps --filter "label=com.docker.compose.project=pem" --format '{{.Ports}}' | grep -qE "(:|^)$p->"; then return 0; fi
  if (netstat -an 2>/dev/null || ss -ltn 2>/dev/null) | grep -E "LISTEN" | grep -qE "[:.]$p[[:space:]]"; then return 1; fi
  return 0
}

up() {
  bash infra/scripts/gen-env-local.sh
  for p in "${PORTS[@]}"; do
    port_owner_ok "$p" || { echo "port $p is in use by another program; free it or set PEM_PG_HOST_PORT / PEM_PROVIDER_HOST_PORT" >&2; exit 1; }
  done
  ./gradlew --no-daemon -q :infra:local-runtime:bootJar
  (cd web-ui && { [ -d node_modules ] || npm ci --no-audit --no-fund; } && npm run -s build)
  "${COMPOSE[@]}" up -d --wait --wait-timeout 600
  "${COMPOSE[@]}" ps --format 'table {{.Name}}\t{{.Status}}'
  cat <<'EOF'

PEM is up:
  Web UI (portal and console)  http://localhost:5173
  API gateway                  http://localhost:8080
  Keycloak (realm pem)         http://localhost:8180
  Mailpit (notices)            http://localhost:8025
  Swagger UI per service       http://localhost:<port>/swagger-ui.html  (provider 18081, enrollment 8082 ... tmsis 8089)
Test users: provider1, analyst1, supervisor1, admin1 (passwords in modern/infra/.env.local)
EOF
}

case "${1:-}" in
  up) up ;;
  down) "${COMPOSE[@]}" down ;;
  ps) "${COMPOSE[@]}" ps --format 'table {{.Name}}\t{{.Status}}' ;;
  logs) "${COMPOSE[@]}" logs -f --tail 100 ;;
  build) ./gradlew --no-daemon build -x test && (cd web-ui && npm run -s build) ;;
  test) ./gradlew --no-daemon checkAll && (cd web-ui && npm run -s lint && npm test) ;;
  e2e)  # default Playwright suite (mocked gateway; it holds the accessibility gate and repository checks of MAM-86), then
        # every journey J1-J7 on the live stack, each from a fresh seed, then the accessibility scans and keyboard journeys
    (cd web-ui && NODE_OPTIONS=--dns-result-order=ipv4first npx playwright test) || exit 1
    for j in j1-enrolment j2-rejected j3-screening-fails j4-inquiry j5-sanctions j6-revalidation j7-extracts; do
      "$0" journey "$j" 1 || exit 1
    done
    "$0" a11y || exit 1 ;;
  a11y)  # MAM-86: axe on every screen and state, focus, contrast and structure checks (read-only, from the seed state), then
         # journeys J1-J7 with the keyboard only (they change data: each seeds itself); the stack is left at the seed state
    # both parts always run and the stack is always reseeded; the target fails when either part failed
    bash infra/scripts/seed.sh; a11y_failed=0
    (cd web-ui && PEM_JOURNEYS=1 NODE_OPTIONS=--dns-result-order=ipv4first npx playwright test e2e/a11y/pages.a11y.spec.ts --reporter=line) || { echo "accessibility page scans FAILED" >&2; a11y_failed=1; }
    (cd web-ui && PEM_JOURNEYS=1 NODE_OPTIONS=--dns-result-order=ipv4first npx playwright test e2e/a11y/keyboard-journeys.spec.ts --reporter=line) || { echo "keyboard journeys FAILED" >&2; a11y_failed=1; }
    bash infra/scripts/seed.sh
    [ "$a11y_failed" -eq 0 ] || exit 1 ;;
  seed) bash infra/scripts/seed.sh ;;
  journey)  # ./pem.sh journey <j1-enrolment|j5-sanctions|...> [runs]: a journey on the live stack, each run from a fresh seed
    name="${2:?usage: ./pem.sh journey <name> [runs]}"; runs="${3:-1}"
    for i in $(seq 1 "$runs"); do
      echo "$name run $i of $runs: seeding"; bash infra/scripts/seed.sh
      (cd web-ui && PEM_JOURNEYS=1 NODE_OPTIONS=--dns-result-order=ipv4first npx playwright test "e2e/journeys/$name.spec.ts" --reporter=line) || { echo "$name run $i FAILED" >&2; exit 1; }
      echo "$name run $i of $runs: PASSED"
    done ;;
  j1)  # journey J1 on the live stack, N times (default 1), each run from a fresh seed (MAM-69 AC-8)
    runs="${2:-1}"
    for i in $(seq 1 "$runs"); do
      echo "J1 run $i of $runs: seeding"; bash infra/scripts/seed.sh
      (cd web-ui && PEM_JOURNEYS=1 NODE_OPTIONS=--dns-result-order=ipv4first npx playwright test e2e/journeys/j1-enrolment.spec.ts --reporter=line) || { echo "J1 run $i FAILED" >&2; exit 1; }
      echo "J1 run $i of $runs: PASSED"
    done ;;
  reset)
    read -r -p "This deletes the pem data volumes (databases, Kafka). Type yes to continue: " answer
    if [ "$answer" = "yes" ]; then "${COMPOSE[@]}" down -v; else echo "nothing changed"; fi ;;
  *) echo "usage: ./pem.sh up|down|ps|logs|build|test|e2e|a11y|seed|j1 [runs]|journey <name> [runs]|reset" >&2; exit 2 ;;
esac
