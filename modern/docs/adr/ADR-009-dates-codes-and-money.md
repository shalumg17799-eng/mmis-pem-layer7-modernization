# ADR-009: Dates, codes and money

Status: ACCEPTED (user, 2026-11-18)

## Context

The legacy uses 2-digit years and Julian dates (AP-04, AP-07) and 1-character codes.

## Decision

java.time with 4-digit years everywhere (D-004); ISO dates in APIs, MM/DD/YYYY in the UI; legacy codes kept as stored values and shown with their meaning; YYDDD only inside the legacy T-MSIS extract (D-015); BigDecimal for any amount (owner percentage).

## Consequences

No century bugs; legacy files still match byte for byte.
