#!/usr/bin/env bash
# Run the constraints and every loader in order. The connection comes ONLY from the environment:
# NEO4J_URI, NEO4J_USERNAME, NEO4J_PASSWORD, NEO4J_DATABASE (never printed, never stored in a file).
#   ./load_all.sh             write to the database named by NEO4J_DATABASE (idempotent: re-running changes nothing)
#   ./load_all.sh --dry-run   parse and count only; needs no database and no variables
set -euo pipefail
cd "$(dirname "$0")"
PY="${PY:-.venv/Scripts/python.exe}"
[ -x "$PY" ] || PY=".venv/bin/python"
ARG="${1:-}"
if [ "$ARG" != "--dry-run" ]; then
  for v in NEO4J_URI NEO4J_USERNAME NEO4J_PASSWORD NEO4J_DATABASE; do
    [ -n "${!v:-}" ] || { echo "Missing environment variable: $v" >&2; exit 2; }
  done
fi
"$PY" -W ignore loaders/load_00_constraints.py $ARG
for f in loaders/load_0[1-8]_*.py; do
  "$PY" -W ignore "$f" $ARG
done
