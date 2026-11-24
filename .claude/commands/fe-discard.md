---
description: Change loop, discard. Switch back to the branch modernization and delete the local branch cr/<CR-ID> (and its generated folder if it was never approved). Local only.
argument-hint: <CR-ID, e.g. CR-001>
---

# /fe-discard `$ARGUMENTS`

Read `.claude/skills/hitl-change-loop/SKILL.md` first. This throws away the local result of a change request. It touches **only** the local branch `cr/$ARGUMENTS` and, if the change was never approved, the untracked generated folder `modern/changes/$ARGUMENTS/`.
Never delete another branch, never touch `modernization`, `main` or any remote, never push, no Jira write.

## Steps
1. `$ARGUMENTS` must match `[A-Za-z0-9][A-Za-z0-9_.-]*`; otherwise stop. The branch name is exactly `cr/$ARGUMENTS`.
2. Look before deleting. Show the user: `git branch --list "cr/$ARGUMENTS"`, the commits that exist only on it (`git log --oneline modernization..cr/$ARGUMENTS`), the uncommitted changes if you are on it (`git status --short`), and whether `modern/changes/$ARGUMENTS/` exists and whether it is tracked on `modernization` (`git ls-files modern/changes/$ARGUMENTS`).
   If neither the branch nor the folder exists: say "nothing to discard" and stop.
3. Switch back: if the current branch is `cr/$ARGUMENTS`, run `git switch --discard-changes modernization` (uncommitted work on the branch is part of what is discarded; it was listed in step 2). Confirm `git branch --show-current` prints `modernization`.
4. Delete the branch: `git branch -D cr/$ARGUMENTS` (a local delete only). The commits stay reachable through `git reflog` for a while: tell the user the last commit hash in case they change their mind.
5. If `modern/changes/$ARGUMENTS/` is **untracked** on `modernization` (the analysis was never approved, so nothing of it is on any branch): list its files and delete that folder. If it is tracked, leave it. **Never delete `modern/changes/$ARGUMENTS.md`** (the user's change-request file) or `TEMPLATE.md`.
6. Report: the branch deleted (with its last commit hash), the folder deleted or kept, the current branch, and `git status --short` (it must show no change of tracked files).
