# ADR-006: FHIR scope

Status: ACCEPTED (user, 2026-11-18)

## Context

The legacy directory is a 100-byte flat file for MCOs; the target adds a public directory.

## Decision

directory-service exposes read-only FHIR R4 Practitioner, Organization and PractitionerRole (specialty = taxonomy) under `/fhir`, built only from fields the legacy master holds (Q-008); public, no token.

## Consequences

No addresses, phones or networks until the business sources them.
