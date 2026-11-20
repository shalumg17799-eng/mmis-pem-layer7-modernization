# ADR-008: UI stack and design system

Status: ACCEPTED (user, 2026-11-18)

## Context

PINQ is a green-screen inquiry; the target needs a provider portal and an analyst console.

## Decision

One React + TypeScript (Vite) app with portal and console areas, USWDS via `@trussworks/react-uswds`, typed clients generated from OpenAPI, Vitest and Playwright with axe.

## Consequences

Accessible by default (WCAG 2.1 AA); no second component library.
