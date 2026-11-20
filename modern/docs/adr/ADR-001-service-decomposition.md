# ADR-001: Service decomposition

Status: ACCEPTED (user, 2026-11-18)

## Context

The legacy estate is two applications sharing one VSAM master (PEMAPP1, PEMAPP2); AWS's decomposition found no domains.

## Decision

Nine business services plus api-gateway and web-ui, as in the overview (the skill's default list, unchanged). Each owns one database and replaces named legacy programs.

## Consequences

Clear ownership and independent deployment; more moving parts, so contract tests and events are mandatory.
