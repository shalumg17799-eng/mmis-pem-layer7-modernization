# ADR-004: Batch and scheduling

Status: ACCEPTED (user, 2026-11-18)

## Context

The legacy runs daily and monthly jobs with undefined `&DATE` symbolics (D-020) and silent failures (D-024).

## Decision

Spring Batch jobs for the intake, screening, revalidation sweep, sanction intake, directory extract and T-MSIS extract; cron in configuration; a manual trigger for ADMIN; the business date is a job parameter defaulting to the injected Clock; every run records its counts and failure reason.

## Consequences

Jobs can be re-run for a date; failures are visible in the console.
