#!/usr/bin/env bash
# MAM-34 AC-1 and AC-2 through the gateway with real Keycloak tokens (needs: make up && make seed).
# Usage: tools/checks/smoke-walking-skeleton.sh [seeded NPI]. Passwords are read from infra/.env.local and never printed.
set -euo pipefail
cd "$(dirname "$0")/../.."
NPI="${1:-$(grep -m1 -oE "'[0-9]{10}'" testdata/seed/provider_db/*.sql 2>/dev/null | head -1 | tr -d "'" || true)}"
[ -n "$NPI" ] || { echo "no seeded NPI found; run make seed or pass one"; exit 2; }
set -a; . infra/.env.local; set +a
KC=http://localhost:8180/realms/pem/protocol/openid-connect/token
GW=http://localhost:8080
token() { curl -s -X POST "$KC" -d grant_type=password -d client_id=pem-test --data-urlencode "username=$1" --data-urlencode "password=$2" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS $1 ($3)"; else echo "FAIL $1: expected $2, got $3"; fail=1; fi; }

ANALYST=$(token analyst1 "$KC_ANALYST_PASSWORD")
PROVIDER=$(token provider1 "$KC_PROVIDER_PASSWORD")
body=$(curl -s -w '\n%{http_code}' -H "Authorization: Bearer $ANALYST" "$GW/api/v1/providers/$NPI")
check "AC-1 analyst reads seeded provider $NPI through the gateway" 200 "$(echo "$body" | tail -1)"
echo "$body" | head -1 | grep -q "\"npi\":\"$NPI\"" && echo "PASS AC-1 body is provider $NPI" || { echo "FAIL AC-1 body"; fail=1; }
check "AC-2 no token" 401 "$(curl -s -o /dev/null -w '%{http_code}' "$GW/api/v1/providers/$NPI")"
if [ "$NPI" != "1003000111" ]; then
  check "AC-2 provider1 (npi 1003000111) reads another NPI" 403 "$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $PROVIDER" "$GW/api/v1/providers/$NPI")"
fi
exit $fail
