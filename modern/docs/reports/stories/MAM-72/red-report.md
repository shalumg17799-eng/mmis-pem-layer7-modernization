# MAM-72 red report

The journey test `e2e/journeys/j2-rejected.spec.ts` was written first from the ACs and passed on its first run against the
live stack: every behaviour it exercises was already built by the stories it depends on (MAM-64, 66, 70, 46, 54, 58/59, 50).
Non-vacuity check: run without a reseed it fails at its seed-state precondition ("run ./pem.sh seed first", line 63); its
assertions are concrete (exact error counts, E104 only, one error row, REJECTED notice, provider 404).
