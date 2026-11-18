---
name: cobol-to-java
description: Exact rules for translating COBOL behaviour and data into Java 21. Covers data types, REDEFINES, 88-levels, dates, file handling, control flow and fixed-width formats. Use whenever writing Java that replaces a COBOL program, or a test for it.
---

# COBOL to Java translation rules

## 1. Principle

Translate **verified behaviour**, not code structure. Never port paragraph-by-paragraph,
and never keep GO TO style flow. The verified rules plus the approved decisions in
`modern/docs/analysis/defect-register.md` are the specification. Where a decision says
FIX, build the fixed behaviour and add a test that states it.

## 2. Data types

| COBOL | Java (domain model) | Notes |
|---|---|---|
| `PIC X(n)` | `String` (trimmed of trailing spaces) | Padding exists only in legacy-format adapters |
| `PIC 9(n)` used as an identifier (NPI, IDs) | `String` with format validation | Never a number: leading zeros matter |
| `PIC 9(n)` used as a quantity | `int` / `long` | |
| `PIC 9(n)V9(m)`, `COMP-3` | `BigDecimal` with scale m | Never `double` or `float` |
| `PIC 9(8)` date CCYYMMDD | `LocalDate` | Parse in adapters only |
| YYDDD Julian | `LocalDate` internally | Format back only in the T-MSIS legacy extract (D-015) |
| 88-level values | `enum` with a `code()` method returning the legacy code | e.g. `EnrollmentStatus.PENDING.code() == "P"` |
| `REDEFINES` by type | `sealed interface` + one `record` per view | Discriminator = provider type code; an unknown code is a validation error, never a guess |
| `OCCURS` | `List<T>` | |
| Group item | `record` | |

## 3. Arithmetic and moves

- `COMPUTE … ROUNDED` → `BigDecimal.setScale(scale, RoundingMode.HALF_UP)`. Without
  ROUNDED → `RoundingMode.DOWN` (truncation). Write a test for each.
- Numeric MOVE into a smaller field truncates high-order digits. Alphanumeric MOVE
  truncates on the right or pads with spaces. Reproduce this **only** in legacy-format
  writers, never in domain logic. Validate lengths at the API instead.
- 2-digit-year arithmetic is fixed (D-004): always use `LocalDate` and full years.

## 4. Files, keys and status codes

| COBOL | Java |
|---|---|
| VSAM KSDS keyed READ | Repository `findById` → `Optional` |
| READ … INVALID KEY | `Optional.empty()` handled explicitly, or a typed `NotFoundException` |
| WRITE duplicate key (status 22) | Unique constraint → `DuplicateNpiException` → error `E103` |
| Sequential read loop until EOF | A stream, or a Spring Batch reader |
| REWRITE | Update inside a transaction, with optimistic locking (`@Version`) |
| FILE STATUS checks | Typed exceptions; never ignore them silently |
| Another app's file opened directly (AP-06) | A call to the owning service's API, or an event |

## 5. Control flow

- `PERFORM` paragraph → private method with a clear name.
- `GO TO` / `ALTER` (AP-01, AP-03) → straightforward control flow, or an explicit enum
  state machine if there are real states. Behaviour follows the defect decision, not the
  quirk.
- Dynamic `CALL` (AP-05) → publish a domain event; the receiving service decides.
- Dead code and never-set flags (AP-02, AP-08) → not ported (decisions D-010, D-011).

## 6. Batch jobs

- A JCL job becomes either event-driven processing or a Spring Batch job, as the
  architecture decides.
- JCL step conditions (`COND`, `IF RC`) become explicit job flow, tested.
- The business date is always an explicit job parameter (D-020). Never call
  `LocalDate.now()` inside business logic: inject a `java.time.Clock`.

## 7. Legacy-format adapters (needed for Phase 9 parity)

- Live in `modern/libs/fixed-width` (codec) and each service's `adapter.legacy` package.
- Field positions and lengths come from the record layouts in the graph
  (`Record`/`Field`), never typed by hand.
- Writers reproduce the exact bytes: padding, zero-fill, date formats, record length.
- Every adapter has a round-trip test (parse, then write, gives the same bytes) and a
  golden-file test once golden files exist.
