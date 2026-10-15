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
   - Public and protected factory/provider methods returning an open `Cursor` to external components (e.g. `getCursorForAllDevices()`, `getDeviceCursor(long)`, `getViewsCursor(...)`, `getCursor(...)`) SHALL NOT close the cursor internally, but callers consuming them within internal methods SHALL use try-with-resources.

2. **Comprehensive Navigation Safety & Canonical Sentinel Mapping (`REQ-DAT-001.2`)**:
   - All cursor navigation operations (`cursor.moveToFirst()`, `cursor.moveToLast()`, and iteration loops `while (cursor.moveToNext())`) SHALL be conditionally validated before reading column indices.
   - If a query yields 0 rows (empty result set), query methods SHALL return deterministic, type-specific canonical empty-state sentinels:
     - Collection and List types (`List<T>`, `Set<T>`): SHALL return empty collection (`Collections.emptyList()`, `Collections.emptySet()`, or empty mutable `new ArrayList<>()`).
     - Entity models, Objects, and Strings (`DeviceType`, `String`, custom POJO): SHALL return `null` (or empty string `""` where specified by method signature).
     - Database Identifiers (`long id`): SHALL return `-1L`.
     - Numeric counts and scalar metrics (`int`, `double`): SHALL return `0` / `0.0`.
     - Boolean state flags: SHALL return `false`.
   - Zero `CursorIndexOutOfBoundsException` or `NullPointerException` crashes SHALL occur on empty or closed cursor states.

3. **Static Architectural Guard & Whitelist-Based CI/CD Contract (`REQ-DAT-001.3`)**:
   - The codebase SHALL enforce try-with-resources and cursor safety via an automated JUnit architecture audit test (`TryWithResourcesCursorAuditTest.kt`) executed as part of `./gradlew testDebugUnitTest`, verifying that all local cursor allocations in Java database managers use try-with-resources.
   - The static audit test and standalone CLI linter `tools/lint_cursor_safety.py` SHALL utilize an explicit whitelist/method signature detection for provider methods returning `Cursor` (`public/protected Cursor get...`), preventing false positives on intentional cursor factory methods.

---

## 2. Test Specification (`TST-DAT-001`)

### TST-DAT-001: Deterministic Resource Management via Try-With-Resources and Bounds Checking on SQLite Cursors Verification Suite

1. **Checked Cursor Navigation Unit Tests (`TST-DAT-001.1`)**:
   - `testQueryOnEmptyTable_returnsTypeMappedSentinelWithoutException`:
     - Given an in-memory or empty SQLite database table,
     - When any migrated query method (e.g., in `ExportStatusDatabaseManager`, `DevicesDatabaseManager`, `SportTypeDatabaseManager`, `TrackingViewsDatabaseManager`) executes,
     - Then it returns the documented sentinel (`emptyList()`, `null`, `-1L`, `0`, `false`) without throwing `CursorIndexOutOfBoundsException`.
   - `testIterativeCursorLoops_safelyTerminateOnEmptyCursor`:
     - Given an empty cursor,
     - When `while (cursor.moveToNext())` executes,
     - Then the loop body is never entered and returns empty collection / 0 cleanly.
   - `testQueryOnPopulatedTable_parsesRowCorrectly`:
     - Given a table containing valid records,
     - When query methods execute,
     - Then rows are correctly read and entity objects or scalars are populated as expected.

2. **Static Architecture Audit Guard with Whitelist (`TryWithResourcesCursorAuditTest.kt`) (`TST-DAT-001.2`)**:
   - `testNoUnshieldedCursorAllocationsInJavaDatabaseManagers`:
     - Given all Java database managers, helpers, and file writers in `app/src/main`,
     - When inspecting all occurrences of `Cursor <var> =`,
     - Then assert that every local declaration is wrapped in a `try (` resource block, excluding whitelisted provider methods whose return type is `Cursor` (`getDeviceCursor`, `getCursorForAllDevices`, `getViewsCursor`, `getCursor`).

3. **CLI Linter Verification (`tools/lint_cursor_safety.py`) (`TST-DAT-001.3`)**:
   - Execute python script `tools/lint_cursor_safety.py`.
   - Assert exit code 0 and zero unshielded cursor allocations with provider method exclusion.

4. **Clean-Room Full Suite Regression Execution (`TST-DAT-001.4`)**:
   - Execute `./gradlew testDebugUnitTest`.
   - Assert 100% test pass rate with 0 failures and 0 skipped across all 2932+ tests.

---

## 3. Requirement Traceability Matrix

| Requirement | Test Spec Identifier | Verification Method | Pass Criteria |
| :--- | :--- | :--- | :--- |
| `REQ-DAT-001.1` (Try-With-Resources Allocation) | `TST-DAT-001.2`, `TST-DAT-001.3` | `TryWithResourcesCursorAuditTest.kt` & `lint_cursor_safety.py` | 100% of local cursors use `try (...)` |
| `REQ-DAT-001.2` (Checked Navigation & Type-Specific Sentinels) | `TST-DAT-001.1` | Unit tests & existing DB test suites | Zero `CursorIndexOutOfBoundsException` on empty results, deterministic sentinels |
| `REQ-DAT-001.3` (Static Architectural Guard & Whitelist) | `TST-DAT-001.2`, `TST-DAT-001.3` | JUnit architecture test & CLI lint script | Zero violations detected, zero false positives on provider methods |
| Invariant Preservation | `TST-DAT-001.4` | `./gradlew testDebugUnitTest` | 100% pass rate across 2932+ tests |

---

## 4. Acceptance Criteria (Given-When-Then)

- **Scenario 1: Empty Query Execution with Sentinel Mapping**
  - *Given* an empty `devices` or `export_status` table,
  - *When* querying for a device name, ID, or pending export list,
  - *Then* `cursor.moveToFirst()` returns `false`, no column is accessed, and type-specific sentinels (`null` for String, `-1L` for ID, `emptyList()` for List, `false` for boolean) are returned without throwing `CursorIndexOutOfBoundsException`.

- **Scenario 2: Query Execution with Exception**
  - *Given* a database query inside a `try (Cursor cursor = ...)` block,
  - *When* an unexpected runtime exception occurs during cursor iteration,
  - *Then* `cursor.close()` is automatically invoked by `AutoCloseable` during stack unwind, preventing cursor and file-descriptor leaks.

- **Scenario 3: Architecture Audit Execution with Provider Method Whitelist**
  - *Given* the codebase under test,
  - *When* `TryWithResourcesCursorAuditTest.kt` and `tools/lint_cursor_safety.py` execute,
  - *Then* zero unshielded cursor allocations are found and legitimate provider methods (`public Cursor get...`) pass without false alarms.
