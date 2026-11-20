#!/bin/sh
# One database and one login per service (java-microservices 1 and 8). Each service owns its database; PUBLIC loses
# CONNECT so no service can read another service's database. Passwords come from modern/infra/.env.local.
set -eu
for svc in provider enrollment screening risk revalidation sanctions notification directory tmsis; do
  upper=$(echo "$svc" | tr 'a-z' 'A-Z')
  eval "pw=\${PG_${upper}_PASSWORD}"
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER ${svc}_svc WITH PASSWORD '${pw}';
CREATE DATABASE ${svc}_db OWNER ${svc}_svc;
REVOKE CONNECT ON DATABASE ${svc}_db FROM PUBLIC;
GRANT CONNECT ON DATABASE ${svc}_db TO ${svc}_svc;
SQL
done
echo "created 9 service databases"
