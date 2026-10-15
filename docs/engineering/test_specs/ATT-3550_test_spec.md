# Stage 2 Test Specification: ATT-3550 - Enforce Try-With-Resources and Cursor Bounds Checks Across Legacy Java Database Managers

**Ticket**: [ATT-3550](https://rainerblind.atlassian.net/browse/ATT-3550)  
**Sub-task**: [ATT-3666](https://rainerblind.atlassian.net/browse/ATT-3666) (`[Specification]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-3550`  
**Requirement Mapping**: `REQ-DAT-001` (*Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Across Legacy Database Managers*)  
**Test Mapping**: `TST-DAT-001`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Formal Requirement Specification (`REQ-DAT-001`)

### REQ-DAT-001: Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Across Legacy Database Managers

The system SHALL guarantee deterministic resource cleanup and zero cursor leaks by enclosing all internally-consumed `Cursor` instances in `try-with-resources` (`AutoCloseable`) blocks across all Java database managers, database helpers, and file exporters, AND SHALL guarantee crash immunity against `CursorIndexOutOfBoundsException` by validating cursor navigation before accessing column data (ATT-3550, parent epic ATT-431):

1. **Try-With-Resources Allocation (`REQ-DAT-001.1`)**:
   - All local queries (`db.query(...)`, `db.rawQuery(...)`) in `ExportStatusDatabaseManager`, `DevicesDatabaseManager`, `SportTypeDatabaseManager`, `TrackingViewsDatabaseManager`, `SegmentsDatabaseManager`, `WorkoutSamplesDatabaseManager`, `PebbleDatabaseManager`, `EquipmentDbHelper`, `ActiveDevicesDbHelper`, `ExportManager`, `BaseFileWriter`, and `GPXFileWriter` SHALL be wrapped in `try (Cursor cursor = ...)`.
   - Trailing manual `cursor.close()` calls on try-with-resources cursors SHALL be eliminated.
   - Public factory/provider methods returning an open `Cursor` to external components (e.g. `getCursorForAllDevices()`, `getDeviceCursor(long)`) SHALL NOT close the cursor internally, but callers consuming them within internal methods SHALL use try-with-resources.

2. **Checked Cursor Navigation & Empty Result Immunity (`REQ-DAT-001.2`)**:
   - All invocations of `cursor.moveToFirst()` or `cursor.moveToLast()` SHALL be conditionally guarded via `if (cursor != null && cursor.moveToFirst())` (or `cursor.moveToLast()`).
   - If the cursor contains 0 rows (returns `false`), the query method SHALL return the canonical sentinel or fallback value (`null`, `-1L`, `0`, `false`, `""`, or empty collection) without attempting to read column indices or throwing `CursorIndexOutOfBoundsException`.

3. **Static Architectural Guard & CI/CD Contract (`REQ-DAT-001.3`)**:
   - The codebase SHALL enforce try-with-resources and cursor safety via an automated JUnit architecture audit test (`TryWithResourcesCursorAuditTest.kt`) executed as part of `./gradlew testDebugUnitTest`, verifying that all local cursor allocations in Java database managers use try-with-resources.
   - Standalone CLI linter `tools/lint_cursor_safety.py` SHALL verify zero unshielded cursor allocations.

---

## 2. Test Specification (`TST-DAT-001`)

### TST-DAT-001: Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Verification Suite

1. **Checked Cursor Navigation Unit Tests (`TST-DAT-001.1`)**:
   - `testQueryOnEmptyTable_returnsDefaultWithoutException`:
     - Given an in-memory or empty SQLite database table,
     - When any migrated query method (e.g., in `ExportStatusDatabaseManager`, `DevicesDatabaseManager`, `SportTypeDatabaseManager`, `TrackingViewsDatabaseManager`) executes,
     - Then it returns the documented sentinel (`null`, `-1L`, `0`, `false`, etc.) without throwing `CursorIndexOutOfBoundsException`.
   - `testQueryOnPopulatedTable_parsesRowCorrectly`:
     - Given a table containing valid records,
     - When query methods execute,
     - Then rows are correctly read and entity objects or scalars are populated as expected.

2. **Static Architecture Audit Guard (`TryWithResourcesCursorAuditTest.kt`) (`TST-DAT-001.2`)**:
   - `testNoUnshieldedCursorAllocationsInJavaDatabaseManagers`:
     - Given all Java database managers, helpers, and file writers in `app/src/main`,
     - When inspecting all occurrences of `Cursor <var> =`,
     - Then assert that every local declaration is wrapped in a `try (` resource block (excluding provider methods that explicitly return `Cursor` to callers).

3. **CLI Linter Verification (`tools/lint_cursor_safety.py`) (`TST-DAT-001.3`)**:
   - Execute python script `tools/lint_cursor_safety.py`.
   - Assert exit code 0 and zero unshielded cursor allocations.

4. **Clean-Room Full Suite Regression Execution (`TST-DAT-001.4`)**:
   - Execute `./gradlew testDebugUnitTest`.
   - Assert 100% test pass rate with 0 failures and 0 skipped across all 2932+ tests.

---

## 3. Requirement Traceability Matrix

| Requirement | Test Spec Identifier | Verification Method | Pass Criteria |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-001.1` (Try-With-Resources Allocation) | `TST-DAT-001.2`, `TST-DAT-001.3` | `TryWithResourcesCursorAuditTest.kt` & `lint_cursor_safety.py` | 100% of local cursors use `try (...)` |
| `REQ-DAT-001.2` (Checked Cursor Navigation) | `TST-DAT-001.1` | Unit tests & existing DB test suites | Zero `CursorIndexOutOfBoundsException` on empty results |
| `REQ-DAT-001.3` (Static Architectural Guard) | `TST-DAT-001.2`, `TST-DAT-001.3` | JUnit architecture test & CLI lint script | Zero violations detected |
| Invariant Preservation | `TST-DAT-001.4` | `./gradlew testDebugUnitTest` | 100% pass rate across 2932+ tests |

---

## 4. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Empty Query Execution**
  - *Given* an empty `devices` or `export_status` table,
  - *When* querying for a device name or pending export,
  - *Then* `cursor.moveToFirst()` returns `false`, no column is accessed, and `null` or `false` is returned without throwing `CursorIndexOutOfBoundsException`.

- **Scenario 2: Query Execution with Exception**
  - *Given* a database query inside a `try (Cursor cursor = ...)` block,
  - *When* an unexpected runtime exception occurs during cursor iteration,
  - *Then* `cursor.close()` is automatically invoked by `AutoCloseable` during stack unwind, preventing cursor and file-descriptor leaks.

- **Scenario 3: Architecture Audit Execution**
  - *Given* the codebase under test,
  - *When* `TryWithResourcesCursorAuditTest.kt` and `tools/lint_cursor_safety.py` execute,
  - *Then* zero unshielded cursor allocations are found.
