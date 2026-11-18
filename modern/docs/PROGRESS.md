# PEM modernization progress

Updated by Claude at the end of every phase. Status values: NOT STARTED, IN PROGRESS,
AWAITING APPROVAL, APPROVED.

## Phases

| Phase | Status | Approved on | Notes |
|---|---|---|---|
| 0 Setup | AWAITING APPROVAL | | Not pushed to GitHub yet (awaiting user OK). `uv` not installed (needed in Phase 1). |
| 1 Knowledge graph | NOT STARTED | | |
| 2 Legacy analysis | NOT STARTED | | |
| 3 Architecture and UX | NOT STARTED | | |
| 4 Backlog | NOT STARTED | | |
| 5 Platform | NOT STARTED | | |
| 6 Services (one row per service below) | NOT STARTED | | |
| 7 UI (portal, console) | NOT STARTED | | |
| 8 E2E and hardening | NOT STARTED | | |
| 9 Parity (deferred) | NOT STARTED | | Legacy outputs will come from the local `mmis-hercules` container (see Decisions) |

## Services (Phase 6)

| Order | Service | Status | Approved on | Report |
|---|---|---|---|---|

## UI areas (Phase 7)

| Area | Status | Approved on | Report |
|---|---|---|---|
| portal | NOT STARTED | | |
| console | NOT STARTED | | |

## Decisions (newest first)

| Date | Decision | Made by | Where recorded |
|---|---|---|---|
| 2026-11-18 | Phase 9 legacy outputs are produced by running the legacy jobs in the local Docker container `mmis-hercules` (MVS TK5 on Hercules). Container state and the `*-LAB.jcl` stubs are checked when Phase 9 starts. | user | this file |
| 2026-11-18 | Git remote is `shalumg17799-eng/mmis-pem-layer7-modernization` (public, empty when checked). Baseline is on `main`, work on `modernization`. Repo-local `core.autocrlf=false` so legacy files keep their exact bytes. Nothing pushed until the user confirms. | user (remote), Claude (autocrlf) | this file |
| 2026-11-18 | The duplicate extract of the kit under `_archive/` was removed by the user; `_archive/pem-agent-kit.zip` stays as the backup. | user | this file |
| 2026-11-18 | Docker is used as a separate stack for this application. Other projects' containers on the machine are not reused or modified. | user | this file |

## Open questions

See `modern/docs/analysis/questions.md`. Count open: 0

## Connections configured (names only, never values)

| Connection | Configured in phase | How |
|---|---|---|
| Git remote `origin` (GitHub) | 0 | `git remote`, HTTPS, no credentials stored by Claude; not pushed yet |
