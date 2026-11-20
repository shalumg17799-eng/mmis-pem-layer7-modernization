```
Phase / item:      MAM-53 EBCDIC to ASCII converter for the Phase 9 parity run
Outcome:           BUILT (build-phase: tests in Part D), batch A6
What was done:     - Spec check: modern/specs/MAM-53.md (6 ACs); Jira not re-read in this batch
                   - modern/tools/data/ebcdic_to_ascii.py INPUT OUTPUT --codepage {037,1047} [--lrecl N]: one ASCII
                     byte per EBCDIC byte, no line terminators added; code page required (exit 2 with the list 037,
                     1047, no output); --lrecl checks the size is a multiple (exit 3 naming size and length); a byte that
                     does not convert to printable ASCII 0x20-0x7E stops the run with its offset and value (exit 4),
                     never a replacement character, and nothing is written; an output path equal to the input is refused
                     (exit 2) and the input's sha256 is checked unchanged after the run
                   - 037 = Python cp037; 1047 = cp037 with the eight positions where IBM 1047 differs (0x15/0x25 LF/NEL,
                     0x5F, 0xAD, 0xB0, 0xBA, 0xBB, 0xBD: brackets, caret, not sign, Y acute, diaeresis); an encode()
                     helper for the round-trip test
                   - AC status: AC-1..AC-6 built; smoke-checked by hand in the scratchpad (round trip of letters, digits,
                     space and the layout punctuation under both pages; missing and unsupported code page; --lrecl
                     mismatch; same input and output path; a 0x00 byte names offset 1 and writes no file). Note for AC-3:
                     a 037 file of ASCII text read as 1047 differs exactly at [ ] ^, which 1047 maps to characters outside
                     printable ASCII, so the tool stops with the offset rather than writing a different character
Files created/changed: modern/tools/data/ebcdic_to_ascii.py (new)
Checks run:        by-hand smoke run above (python 3, no Gradle)
Not verified:      automated tests (Part D, tools/checks/tests); a real mainframe file
Questions for you: none
```

## Lane 3 tests (2026-11-19)

Tests for a story that was built without them (user approval 2026-11-19, lane 3: tests for every story built without tests). Written after the tool, so there was no red phase; the Outcome line above is unchanged.

- File: modern/tools/checks/tests/test_ebcdic_to_ascii.py (38 test functions, 118 cases; 36 functions and 115 cases name an AC, 2 functions and 3 cases check the hand-written EBCDIC table the other tests use as their oracle)
- Per AC, functions / cases: AC-1 4 / 16, AC-2 4 / 14, AC-3 9 / 26, AC-4 3 / 10, AC-5 10 / 34, AC-6 6 / 15. Every test is named test_mam53_acN_* and starts its docstring with "[MAM-53 AC-n]"
- Command: modern/kag/.venv/Scripts/python.exe -m pytest -q modern/tools/checks/tests/test_ebcdic_to_ascii.py
- Result: 118 passed in 51 s (run in this session; no tool defect found). Run together with the MAM-44 tests: 261 passed in 103 s
- Test hashes: modern/docs/reports/stories/MAM-53.tests.sha256 (restore copy listed in MAM-53/red-blobs.txt); test_protection.py check MAM-53 gives OK
- How: the tool is run as a program (exit code, stderr, files) on synthetic bytes built under tmp_path. Expected EBCDIC bytes come from the IBM 037 and 1047 tables written out in the test (95 printable characters per page, cross-checked with Python's cp037 codec), not from the tool's own table. The known text, with its 037 and 1047 hex, is recorded in the test (AC-3). The eight positions where 1047 differs from 037 (15 25 5F AD B0 BA BB BD) are asserted per byte
- Sensitivity: the tests were also run against ten deliberately broken copies of the tool (kept in the scratchpad, not in the repo; MAM53_TOOL points the tests at a copy): newline appended, default code page, replacement character for a bad byte, no --lrecl check, same-path check removed, 1047 equal to 037, message without the page list, last instead of first offending offset, input rewritten, off-by-one size check. Each one failed the tests of the AC it breaks
- Observations, none an AC failure: (1) AC-3 "a different result" is a refusal, not other text: no byte is printable ASCII under both pages with different characters, so a 037 file with [ ] ^ converted as 1047 stops at the first of them (offset and value named, no output), and 5F AD BD gives ^[] under 1047 and a refusal under 037. The tests assert exactly that. (2) An output path in a missing folder, or an output path that is a directory, ends in a Python traceback (exit 1), not a message; the input stays unchanged. No AC covers it, so the tests only require a non-zero exit and an unchanged input hash. (3) An existing output file that is not the input is overwritten without a warning; no AC covers it
- Not covered: a real mainframe file (synthetic bytes only); a symbolic link as the second name of the input (needs a privilege on Windows; a hard link and a dir/../ spelling are tested); files too large to read into memory
