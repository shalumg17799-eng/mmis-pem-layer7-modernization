# MAM-63 red report (directory-service, public directory search)

No `red-report.md` existed for MAM-63 before this correction (the folder held only `red-blobs.txt` and `red-run.txt`, the output of the original red run), so this file records the
corrections made after the red step. The state before each correction is kept in git's object store as the blob ids named below (`git cat-file blob <id>`).

## Test correction under Q-097 (code review 2026-11-19, additive)

Cause: review item SHOULD-FIX 7. **MAM-63 AC-3** is "search by name finds the composed name" (name contains, any case) on the public search `GET /api/v1/directory/providers?name=`.
`JdbcDirectoryStore.published(...)` (main code, lines 62-65 of the current file; the review quoted 197-198) builds the pattern as `"%" + name.trim() + "%"` for `display_name ILIKE ?`
without escaping, unlike `JdbcFhirDirectory.like()` which escapes `\`, `%` and `_`. So `name=%` and `name=_` match every ACTIVE provider, a visitor's text is read as SQL LIKE syntax, and
the recorded AC-3 scenario (`HOSPICE`, `rivera`, `ZZZZ`) could never see it. Rule: the searched name is plain text; `%`, `_` and `\` in it are literal characters. `%` matches no name that
does not hold a `%`, and a name that holds a literal `%` or `_` is found by that character. The user's standing rule **Q-097 C** applies: additive, no assertion weakened, removed or
relaxed, documented here and re-recorded; the code-reviewer confirms.

**This correction is additive.** Every assertion of every recorded step and scenario is unchanged. What changed:

1. **New test class** `api/DirectoryNameSearchLiteralTest` (7 tests, all `[MAM-63 AC-3]`; MockMvc against the real application with Testcontainers Postgres).
2. **Two new Cucumber scenarios** in `MAM-63.feature`, both tagged `@AC-3`, using only existing steps (no change to `DirectorySearchSteps.java`).

| File | Previous blob | New blob | Line endings | Change |
|---|---|---|---|---|
| `src/test/java/gov/medicaid/pem/directory/api/DirectoryNameSearchLiteralTest.java` | (new file) | `86d997f374e5ac6f2c4fe1d607d779039de950d8` | CRLF | new, 7 tests |
| `src/test/resources/features/MAM-63.feature` | `aa7e4267a8d8746789c5c4e3cc41de5d8f1f2fa8` | `a2538ab13e39538c6298eb038b921e4629907c4f` | LF (as before) | 2 scenarios added after the AC-3 scenario (19 lines added, none removed) |

(all paths under `modern/services/directory-service/`; `DirectorySearchSteps.java` is byte-identical to the previous recording.)

`MAM-63.feature` (LF)
```diff
@@ -27,6 +27,25 @@
     When anyone searches the directory with "name=ZZZZ"
     Then the search answers 200 with the NPIs "", page 0, size 20 and total 0
 
+  @AC-3
+  Scenario: A percent sign or an underscore in the searched name is a literal character, not a wildcard
+    When anyone searches the directory with "name=%"
+    Then the search answers 200 with the NPIs "", page 0, size 20 and total 0
+    When anyone searches the directory with "name=_"
+    Then the search answers 200 with the NPIs "", page 0, size 20 and total 0
+
+  @AC-3
+  Scenario: A name that holds a literal percent sign or underscore is found by that character only
+    Given the directory holds:
+      | npi        | medicaidId | type | tier | status | name              | taxonomy   |
+      | 1003000111 | M00000111  | IN   | L    | A      | MARIA RIVERA      | 207Q00000X |
+      | 1003000777 | M00000777  | GR   |      | A      | PLAN 100% CARE    | 251E00000X |
+      | 1003000888 | M00000888  | GR   |      | A      | NORTH_SIDE CLINIC | 251E00000X |
+    When anyone searches the directory with "name=%"
+    Then the search answers 200 with the NPIs "1003000777", page 0, size 20 and total 1
+    When anyone searches the directory with "name=_"
+    Then the search answers 200 with the NPIs "1003000888", page 0, size 20 and total 1
+
   @AC-4
   Scenario: Filter by provider type
```

### The new tests

Data (`DirectoryNameSearchLiteralTest`): ACTIVE `1003000111` MARIA RIVERA (IN) and `1003000333` SUNSET HOSPICE INC (FA), neither holds a `%`, `_` or `\`. The literal tests add synthetic
ACTIVE names: `1003000777` "PLAN 100% CARE", `1003000888` "NORTH_SIDE CLINIC", `1003000999` "ACME\WEST CLINIC".

| Test | Count | What it asserts |
|---|---|---|
| `wildcardTextMatchesNothing` | 4 | `name=%`, `name=_`, `name=R_VERA` and `name=RIV%` answer 200 with no item and total 0: the wildcard text is literal and no seeded name holds it. |
| `percentFindsOnlyTheNameWithAPercent` | 1 | With the literal names added, `name=%` returns only `1003000777`, total 1 (not every ACTIVE entry). |
| `underscoreFindsOnlyTheNameWithAnUnderscore` | 1 | `name=_` returns only `1003000888`, total 1. |
| `backslashFindsOnlyTheNameWithABackslash` | 1 | `name=\` returns only `1003000999`, total 1 (the escape character itself is literal). Beyond the two characters of the review, because it is the same defect and `JdbcFhirDirectory.like()` escapes it too. |
| Cucumber scenario "...literal character, not a wildcard" | 1 | the first two cases above, on the Background data (`name=%`, `name=_` give total 0). |
| Cucumber scenario "...found by that character only" | 1 | `name=%` gives only `1003000777`, `name=_` only `1003000888`. |

A first version of the class also had `name=\` in the "matches nothing" list. It passed today by accident (the unescaped pattern `%\%` means "a literal percent sign"), so it was not red;
it was removed before recording and replaced by `backslashFindsOnlyTheNameWithABackslash`, which fails today.

### Red run (2026-11-19, `--no-daemon`, under the Gradle lock): all 9 fail, for the expected reason (an assertion failure, no compile or setup error)

Run 1: `:services:directory-service:spotlessApply :services:directory-service:test --tests <the new classes, DirectoryExtractJobTest, CucumberSuiteTest>`: 72 tests, 25 failed, of which 6
of this class and the 2 scenarios below. Run 2 (after replacing the `\` case): `--tests DirectoryNameSearchLiteralTest`: **7 tests, 7 failed**.

| Test | Failure today |
|---|---|
| `wildcardTextMatchesNothing` `%` | `Expecting empty but was: ["1003000111", "1003000333"]`: `%%%` matches every name. |
| `wildcardTextMatchesNothing` `_` | `Expecting empty but was: ["1003000111", "1003000333"]`: `%_%` matches every non-empty name. |
| `wildcardTextMatchesNothing` `R_VERA` | `Expecting empty but was: ["1003000111"]`: `_` matches the `I` of RIVERA. |
| `wildcardTextMatchesNothing` `RIV%` | `Expecting empty but was: ["1003000111"]`: `%` matches `ERA`. |
| `percentFindsOnlyTheNameWithAPercent` | contains `["1003000111", "1003000333", "1003000777", "1003000888"]`, expected exactly `["1003000777"]`. |
| `underscoreFindsOnlyTheNameWithAnUnderscore` | same four NPIs, expected exactly `["1003000888"]`. |
| `backslashFindsOnlyTheNameWithABackslash` | `Expecting actual: []` to contain `["1003000999"]`: the backslash is read as an escape of the next `%`, so the name is not found. |
| Cucumber, wildcard scenario | `Expecting actual: ["1003000111", "1003000333"]` to contain exactly `[]` (MAM-63.feature:33, the `name=%` step; the raw `%` reaches the endpoint as `%`, not double-encoded). |
| Cucumber, literal scenario | `Expecting actual: ["1003000111", "1003000777", "1003000888"]` to contain exactly `["1003000777"]` (MAM-63.feature:45). |

**Hashes:** re-recorded with `test_protection.py record MAM-63` with exactly the 2 files of the previous `MAM-63.tests.sha256` plus `DirectoryNameSearchLiteralTest.java` (3 files); `check`
prints `OK: 3 test files of MAM-63 unchanged; no unrecorded test files`. The previous `MAM-63.feature` stays in git's object store under the previous blob id above.
