#!/usr/bin/env bash
# make seed: loads modern/testdata/seed/<service>_db/*.sql into each service database, in file-name order, as that
# service's own database (never across databases). Each file runs in ONE transaction (psql --single-transaction), so a
# failure leaves that database in its previous state. Each file is re-runnable: it restores exactly the seed state.
# A failure stops the run with exit 1 and names the service (MAM-50 AC-6). SEED_DIR overrides the folder (tests).
set -euo pipefail
cd "$(dirname "$0")/../.."
dir=${SEED_DIR:-testdata/seed}
[ -d "$dir" ] || { echo "seed FAILED: no seed folder $dir" >&2; exit 1; }
loaded=0
for db in "$dir"/*_db; do
  [ -d "$db" ] || continue
  name=$(basename "$db"); svc=${name%_db}-service
  for f in "$db"/*.sql; do
    [ -f "$f" ] || continue
    if ! docker exec -i pem-postgres-1 psql -v ON_ERROR_STOP=1 --single-transaction -q -U pem_admin -d "$name" < "$f"; then
      echo "seed FAILED for $svc (database $name, file $(basename "$f")); that database keeps its previous state" >&2
      exit 1
    fi
    echo "seeded $svc (database $name) from $(basename "$f")"
    loaded=$((loaded + 1))
  done
done
echo "seed files loaded: $loaded"
# Journeys start with an empty Mailpit inbox (MAM-69 AC-8); skipped when Mailpit is not running.
if curl -s -m 5 -o /dev/null -X DELETE "${PEM_MAILPIT_URL:-http://localhost:8025}/api/v1/messages"; then
  echo "mailpit inbox emptied"
fi
