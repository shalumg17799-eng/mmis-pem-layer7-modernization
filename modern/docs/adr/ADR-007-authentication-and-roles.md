# ADR-007: Authentication and roles

Status: ACCEPTED (user, 2026-11-18)

## Context

PINQ has no authorisation (D-023); the skill defines four user roles.

## Decision

Keycloak realm `pem`, OIDC with PKCE for the UI, resource servers with method-level checks. Roles PROVIDER, ANALYST, SUPERVISOR, ADMIN, plus SYSTEM for service accounts (client credentials); directory and FHIR are PUBLIC.

## Consequences

Every endpoint lists its roles in the contract (`x-roles`) and has a test per role.
