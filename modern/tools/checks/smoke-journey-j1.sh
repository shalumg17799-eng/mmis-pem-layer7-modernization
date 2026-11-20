#!/usr/bin/env bash
# Journey J1 through the gateway with real Keycloak tokens, API level (needs: make up && make seed).
# provider1 applies -> registered P/U -> screened on four sources -> tier -> analyst1 approves -> directory -> Mailpit.
# Passwords are read from infra/.env.local and never printed.
set -uo pipefail
cd "$(dirname "$0")/../.."
set -a; . infra/.env.local; set +a
KC=http://localhost:8180/realms/pem/protocol/openid-connect/token
GW=http://localhost:8080
MAIL=http://localhost:8025/api/v1/messages
NPI=1003000111
token() { curl -s -X POST "$KC" -d grant_type=password -d client_id=pem-test --data-urlencode "username=$1" --data-urlencode "password=$2" \
  | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p'; }
fail=0
pass() { echo "PASS $*"; }
bad() { echo "FAIL $*"; fail=1; }
# wait_for <description> <command that prints something matching the regex> <regex>: up to 90 s
wait_for() {
  local what=$1 cmd=$2 re=$3 out=""
  for _ in $(seq 1 45); do
    out=$(eval "$cmd" 2>/dev/null)
    if echo "$out" | grep -Eq "$re"; then pass "$what"; return 0; fi
    curl -s -m 2 -o /dev/null "$GW/actuator/health" || true
    sleep 2 2>/dev/null || ping -n 3 127.0.0.1 > /dev/null
  done
  bad "$what (last answer: $(echo "$out" | cut -c1-200))"
}

P=$(token provider1 "$KC_PROVIDER_PASSWORD"); A=$(token analyst1 "$KC_ANALYST_PASSWORD")
[ -n "$P" ] && [ -n "$A" ] || { echo "FAIL no tokens from Keycloak"; exit 1; }

r=$(curl -s -w ' HTTP%{http_code}' -X POST "$GW/api/v1/applications" -H "Authorization: Bearer $P" -H 'Content-Type: application/json' \
  -d '{"npi":"'$NPI'","medicaidId":"TX0000111","providerType":"IN","taxonomy":"207Q00000X","individual":{"lastName":"RIVERA","firstName":"MARIA","licenseNo":"MD12345","licenseState":"TX"}}')
echo "$r" | grep -q 'HTTP201' && echo "$r" | grep -q '"status":"VALIDATED"' && pass "1 apply: 201 VALIDATED" || bad "1 apply: $(echo "$r" | cut -c1-200)"

prov="curl -s -H 'Authorization: Bearer $A' $GW/api/v1/providers/$NPI"
wait_for "2 registered PENDING" "$prov" '"enrollStatus":"P"'
wait_for "3 screening PASS (screen flag P)" "$prov" '"screenFlag":"P"'
scr=$(curl -s -H "Authorization: Bearer $A" "$GW/api/v1/screenings?npi=$NPI")
for s in LICBOARD OIGLEIE SAMGOV SSADMF; do
  echo "$scr" | grep -Eq "\"source\":\"$s\",\"result\":\"P\"" && pass "3 $s P" || bad "3 $s not P"
done
wait_for "4 risk tier L" "$prov" '"riskTier":"L"'

r=$(curl -s -w ' HTTP%{http_code}' -X POST "$GW/api/v1/providers/$NPI/approval" -H "Authorization: Bearer $A" -H 'Content-Type: application/json' -d '{"decision":"APPROVE"}')
echo "$r" | grep -q 'HTTP200' && echo "$r" | grep -q '"enrollStatus":"A"' && pass "5 approve: 200 Active, due $(echo "$r" | sed -n 's/.*"revalidationDueDate":"\([^"]*\)".*/\1/p')" || bad "5 approve: $(echo "$r" | cut -c1-200)"

wait_for "6 public directory lists MARIA RIVERA" "curl -s '$GW/api/v1/directory/providers?name=RIVERA'" '"displayName":"MARIA RIVERA"'
wait_for "7 Mailpit holds 'Enrolment approved' for $NPI" "curl -s $MAIL" "\"Address\":\"$NPI@providers.pem.local\".*\"Subject\":\"Enrolment approved\"|\"Subject\":\"Enrolment approved\".*\"Address\":\"$NPI@providers.pem.local\""
exit $fail
