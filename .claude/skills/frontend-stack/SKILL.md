---
name: frontend-stack
description: Standards for the PEM web UI. Covers React + TypeScript structure, USWDS components, Keycloak login, typed API clients from OpenAPI, accessibility (WCAG 2.1 AA) and Playwright testing. Use for wireframes in Phase 3 and UI work in Phase 7.
---

# PEM web UI

## 1. One app, two areas

- `modern/web-ui/`: one React app with two role-based areas.
  - **Portal** (`/portal/*`, role PROVIDER): apply for enrolment, view application status,
    view own provider record, receive notices.
  - **Console** (`/console/*`, roles ANALYST, SUPERVISOR, ADMIN): search providers by NPI,
    name or Medicaid ID (replaces CICS PINQ); provider detail (status, screening results
    per source, risk tier, revalidation date, sanctions, audit history); work queues
    (pending approval, failed screening, revalidation due); approve or deny; record a
    sanction; trigger batch jobs (ADMIN).
- The exact screen list comes from `modern/docs/ux/screen-inventory.md` (Phase 3).

## 2. Stack

- React + TypeScript (strict) + Vite. React Router. TanStack Query for server state.
- USWDS via `@trussworks/react-uswds`; check the current version in Phase 5 and record
  it. Do not mix in another component library.
- Login with Keycloak OIDC (authorization code + PKCE) using `oidc-client-ts`. The token
  goes to the gateway at `http://localhost:8080`.
- API clients are generated from `modern/contracts/openapi/*.yaml` with
  `openapi-typescript` plus a small fetch wrapper. Never hand-write API types.
- Unit/component tests: Vitest + Testing Library. End to end: Playwright + `@axe-core/playwright`.

## 3. Structure

```
web-ui/src/
  app/          routing, layout, auth guard
  features/portal/<feature>/   pages, components, hooks
  features/console/<feature>/
  shared/       ui building blocks, api client, formatting
  theme/        USWDS settings, tokens
web-ui/tests/   component tests
web-ui/e2e/     Playwright journeys
```

## 4. Wireframes (Phase 3)

- Low-fidelity, static HTML using USWDS classes, one file per screen in
  `modern/docs/ux/wireframes/<area>-<screen>.html`, plus an `index.html` that links them.
- Each wireframe lists at the bottom: the route, the roles, the API calls it needs, and
  the story or rule it serves.
- If the user connects Figma later, the same screens can be pushed there; this is not
  required.

## 5. Accessibility (WCAG 2.1 AA, mandatory)

- Every input has a visible label. Errors are shown next to the field and in a summary at
  the top, linked to the fields.
- Every action can be done with the keyboard, focus is always visible, and the tab
  order follows the visual order.
- Colour contrast ≥ 4.5:1 for text. Status is never shown by colour alone.
- Tables have headers. Pages have one `h1` and landmarks.
- Every Playwright journey runs an axe scan; any violation fails the test.

## 6. Data display rules

- Show legacy codes with their meaning, e.g. "Moderate (M)". Format dates as
  `MM/DD/YYYY` for US users; send ISO dates to the API.
- NPI is always shown in full. Never show data from another provider to a PROVIDER user;
  the tests must check this.
