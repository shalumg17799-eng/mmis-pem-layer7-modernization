# ADR-005: Legacy-format adapters for future parity

Status: ACCEPTED (user, 2026-11-18)

## Context

Phase 9 compares legacy job outputs (from the Hercules container) with the new system's.

## Decision

Each service that replaces a file-based job keeps a fixed-width reader or writer (`adapter/legacy/`, using `libs/fixed-width`): APPINPUT, APPERROR, SCRNOUT, REVALQ, SANCIN, DIROUT, TMSISOUT and the 130-byte master export.

## Consequences

Byte-level comparison is possible; layouts come from the copybooks (130 bytes, D-007).
