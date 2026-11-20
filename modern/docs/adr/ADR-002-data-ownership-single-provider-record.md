# ADR-002: Data ownership and the single provider record

Status: ACCEPTED (user, 2026-11-18)

## Context

Provider identity lives in the VSAM master and in DB2 (AP-12); PEMAPP2 rewrites PEMAPP1's master (AP-06).

## Decision

provider-service is the only writer of status, screen flag, risk tier and due date; one `provider` table holds every PRVRECRD and DB2 PROVIDER field; others use events or its API (D-013, D-014).

## Consequences

No dual system of record; other services keep read models built from events.
