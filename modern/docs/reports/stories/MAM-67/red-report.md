# MAM-67 red report (directory-service, directory extract as a Spring Batch job)

No `red-report.md` existed for MAM-67 before this correction (the folder held only `red-blobs.txt`), so this file records the corrections made after the red step. The state before each
correction is kept in git's object store as the blob ids named below (`git cat-file blob <id>`).

## Test correction under Q-097 (code review 2026-11-19, additive)

Cause: review item SHOULD-FIX 4. **MAM-67 AC-7** says a failed job gives a FAILED run "with the reason" (D-024), and `BatchRun.error` is "Why the run failed". The recorded tests only
require the error to be non-blank and to contain the database's message ("extract file rejected by the test database"). The failure listener of `DirectoryExtractJobConfig` stores
`e.getMessage()` of the first failure, so for a database error the answer, the stored `failure_reason` and the status GET carry Spring's whole wrapped text, including the SQL statement:

```
PreparedStatementCallback; uncategorized SQLException for SQL [UPDATE extract_run SET status = 'COMPLETED', row_count = ?, extract_file = ?, ended_at = ?, updated_at = ?,
version = version + 1 WHERE public_id = ?]; SQL state [P0001]; error code [0]; ERROR: extract file rejected by the test database
  Where: PL/pgSQL function reject_completed_run() line 1 at RAISE
```
(365 characters). That text goes to an ADMIN or SYSTEM caller and is stored. Rule: the error is a short reason with no SQL statement text. The user's standing rule **Q-097 C** applies:
additive, no assertion weakened, removed or relaxed, documented here and re-recorded; the code-reviewer confirms.

**This correction is additive.** The three new tests are added to `DirectoryExtractJobTest`; all 10 recorded tests of the class and their assertions are unchanged (the diff below has
added lines only). They use the existing real-trigger failure setup (`rejectCompletedRuns()`: the test database rejects the write that completes a run), no mock.

| File | Previous blob | New blob | Line endings | Change |
|---|---|---|---|---|
| `src/test/java/gov/medicaid/pem/directory/infrastructure/DirectoryExtractJobTest.java` | `3f7098a1219b6985af414eafc7bd9e86832b694c` | `443c4b0373e498cf0f20e06bbb27c32363c24310` | CRLF (kept) | 3 tests added after `failedRunReason`, 34 lines added, none removed |

(path under `modern/services/directory-service/`; the other 14 files of `MAM-67.tests.sha256` are byte-identical to the previous recording.)

`DirectoryExtractJobTest.java` (CRLF; diff made with `--strip-trailing-cr`, the line endings of the file were kept)
```diff
@@ -164,6 +164,40 @@
   }
 
   @Test
+  @DisplayName(
+      "[MAM-67 AC-7] the error text of a failed run is a reason only: no SQL statement text (code review 2026-11-19)")
+  void failedRunReasonHasNoSqlText() throws Exception {
+    rejectCompletedRuns();
+    JsonNode run = start("2026-03-31");
+    assertThat(run.path("error").asText().toLowerCase())
+        .doesNotContain("select", "insert", "update", "sql");
+  }
+
+  @Test
+  @DisplayName(
+      "[MAM-67 AC-7] the error text of a failed run is short: at most 200 characters (code review 2026-11-19)")
+  void failedRunReasonIsShort() throws Exception {
+    rejectCompletedRuns();
+    JsonNode run = start("2026-03-31");
+    assertThat(run.path("error").asText()).isNotBlank().hasSizeLessThanOrEqualTo(200);
+  }
+
+  @Test
+  @DisplayName(
+      "[MAM-67 AC-7] the reason stored with the failed run has no SQL statement text either (code review 2026-11-19)")
+  void storedFailureReasonHasNoSqlText() throws Exception {
+    rejectCompletedRuns();
+    String runId = start("2026-03-31").path("runId").asText();
+    String stored =
+        jdbc.queryForObject(
+            "SELECT failure_reason FROM extract_run WHERE public_id = ?::uuid",
+            String.class,
+            runId);
+    assertThat(stored).isNotBlank();
+    assertThat(stored.toLowerCase()).doesNotContain("select", "insert", "update", "sql");
+  }
+
   @Test
   @DisplayName("[MAM-67 AC-7] the file of a FAILED run is 404, so there is no partial file")
```

### The new tests (all `[MAM-67 AC-7]`)

| Test | What it asserts |
|---|---|
| `failedRunReasonHasNoSqlText` | The `error` of the 202 answer, lower-cased, holds none of `select`, `insert`, `update`, `sql`. |
| `failedRunReasonIsShort` | The `error` of the 202 answer is not blank and at most 200 characters (the column is VARCHAR(500); 200 is this test's reading of "short"). |
| `storedFailureReasonHasNoSqlText` | The `failure_reason` stored in `extract_run` for the failed run is not blank and holds none of the four fragments, so nothing with SQL is persisted either. |

The existing `failedRunReason` stays and still has to pass: the reason must still contain "extract file rejected by the test database". Note for the developer: the Postgres message also has
a second line, `Where: PL/pgSQL function reject_completed_run() line 1 at RAISE`, which contains `sql` and is not a "reason" either, so the first line of the root cause
(`ERROR: extract file rejected by the test database`) is the shape that satisfies all four checks.

### Red run (2026-11-19, `--no-daemon`, under the Gradle lock): all 3 fail, for the expected reason (an assertion failure on the error text)

Run: `:services:directory-service:spotlessApply :services:directory-service:test --tests <the new classes, DirectoryExtractJobTest, CucumberSuiteTest>`: 72 tests, 25 failed, of which these 3;
the 10 recorded tests of `DirectoryExtractJobTest` passed.

| Test | Failure today |
|---|---|
| `failedRunReasonHasNoSqlText` | the lower-cased text "preparedstatementcallback; uncategorized sqlexception for sql [update extract_run set status = 'completed' ..." contains `["update", "sql"]`. |
| `storedFailureReasonHasNoSqlText` | the stored `failure_reason` is the same text, found `["update", "sql"]`. |
| `failedRunReasonIsShort` | `Expecting size of "PreparedStatementCallback; uncategorized SQLException for SQL [UPDATE extract_run SET ..." to be less than or equal to 200 but was 365`. |

**Hashes:** re-recorded with `test_protection.py record MAM-67` with exactly the 15 files of the previous `MAM-67.tests.sha256` (no new file); `check` prints
`OK: 15 test files of MAM-67 unchanged; no unrecorded test files`. The previous `DirectoryExtractJobTest.java` stays in git's object store under the previous blob id above.
