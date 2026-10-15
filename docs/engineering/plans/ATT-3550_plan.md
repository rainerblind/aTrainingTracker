# Stage 3 Implementation Plan: ATT-3550 - Enforce Try-With-Resources and Cursor Bounds Checks Across Legacy Java Database Managers

**Ticket**: [ATT-3550](https://rainerblind.atlassian.net/browse/ATT-3550)  
**Sub-task**: [ATT-3667](https://rainerblind.atlassian.net/browse/ATT-3667) (`[Implementation Plan]`)  
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

## 1. Executive Summary & SWE.2 Architecture

To eliminate cursor resource leaks and `CursorIndexOutOfBoundsException` systematically across legacy Java database managers, ATT-3550 introduces a standardized architectural model combining centralized sentinel constants, functional cursor navigation helpers, hardened governance annotations, explicit exception propagation contracts, and automated blocking CI/CD guards.

```mermaid
classDiagram
    class DatabaseConstants {
        +INVALID_ID: long = -1L
        +EMPTY_STRING: String = ""
        +EMPTY_COUNT: int = 0
        +EMPTY_METRIC: double = 0.0
    }
    class CursorUtils {
        +mapFirst(cursor: Cursor?, defaultValue: T, mapper: CursorMapper~T~): T
        +mapList(cursor: Cursor?, mapper: CursorMapper~T~): List~T~
        +moveToFirstOrNull(cursor: Cursor?): Cursor?
    }
    class CursorProvider {
        <<annotation>>
        +String reason
    }
    class SuppressCursorAudit {
        <<annotation>>
        +String ticketId
        +String reason
    }
    DatabaseConstants <.. CursorUtils : uses
    CursorProvider <.. CursorUtils : interacts
    SuppressCursorAudit <.. CursorUtils : governs
```

### Key Architectural Pillars

1. **Centralized Sentinel Constants (`DatabaseConstants.java`)**:
   - Location: `app/src/main/java/com/atrainingtracker/database/DatabaseConstants.java`.
   - Defines canonical empty-state sentinels (`INVALID_ID = -1L`, `EMPTY_STRING = ""`, `EMPTY_COUNT = 0`, `EMPTY_METRIC = 0.0`) to eliminate magic literals and prevent sentinel drift across managers.

2. **Functional Cursor Helper (`CursorUtils.java`)**:
   - Location: `app/src/main/java/com/atrainingtracker/util/CursorUtils.java`.
   - Null-safe navigation primitives:
     - `mapFirst(Cursor cursor, T defaultValue, CursorMapper<T> mapper)`: Safely checks `if (cursor != null && cursor.moveToFirst())`, extracts and returns `mapper.map(cursor)`, or returns `defaultValue` if empty, ensuring zero `CursorIndexOutOfBoundsException`.
     - `mapList(Cursor cursor, CursorMapper<T> mapper)`: Safely iterates `while (cursor.moveToNext())`, returning an unmodifiable or empty list.
   - Operates inside standard Java `try (Cursor cursor = ...)` blocks.

3. **Exception Propagation & Telemetry Contract**:
   - `CursorUtils` **NEVER** swallows `SQLException`, `SQLiteException`, or `RuntimeException`. All exceptions thrown during mapping or cursor navigation are propagated cleanly to the caller.
   - The `try-with-resources` block guarantees that `cursor.close()` is invoked deterministically during stack unwind, even when exceptions are thrown mid-iteration.
   - In database managers with existing fallback API contracts, query blocks catch `SQLiteException` explicitly, log high-severity diagnostics with `Log.e(TAG, "Query execution failed on table ...", e)`, and return the canonical sentinel, guaranteeing observability in Crashlytics and Logcat.

4. **Hardened Annotation Governance & Transfer of Ownership Contract**:
   - **`@CursorProvider`**:
     - Strict contract: *Any method annotated with `@CursorProvider` transfers the responsibility of closing the `Cursor` to the caller. Callers consuming this method MUST close the cursor via `try-with-resources` or pass ownership to an Android framework lifecycle component (e.g. `CursorAdapter`, `Loader`).*
     - Location: `app/src/main/java/com/atrainingtracker/database/CursorProvider.java`.
   - **`@SuppressCursorAudit`**:
     - Hardened governance: Requires mandatory `ticketId` (validated by CI/linter to match Jira pattern `ATT-\\d+`) and descriptive `reason`:
       ```java
       public @interface SuppressCursorAudit {
           String ticketId();
           String reason();
       }
       ```
     - Prevents unreviewed "get-out-of-jail-free" bypassing of static checks.

5. **Robust AST & Token-Based Static Analysis (`lint_cursor_safety.py`)**:
   - Rather than simple variable regex matching, the static linter and JUnit architecture test inspect method bodies for any invocation of `.query(` or `.rawQuery(` on database objects.
   - It asserts that every query invocation is either:
     - Part of a `try (Cursor ... = db.query(...))` resource specification, OR
     - The direct return expression of a method annotated with `@CursorProvider`, OR
     - Explicitly annotated with a validated `@SuppressCursorAudit(ticketId="ATT-...", reason="...")`.
   - Any unshielded invocation outside these contracts fails the build immediately.

6. **Blocking CI/CD Gate Integration**:
   - `TryWithResourcesCursorAuditTest.kt` is executed automatically on `./gradlew testDebugUnitTest`, serving as a non-bypassable blocking gate on pull requests and commits.

---

## 2. Call-Site Inventory & Migration Scope

| File | Target Query Methods / Lines | Pattern & Helper Applied | Sentinel Value |
| :--- | :--- | :--- | :--- |
| **`ExportStatusDatabaseManager.java`** | `getExportStatus(long, FileFormat)` (L198)<br>`hasPendingExports()` (L269)<br>`getRecentExports()` (L300)<br>`isExported(long, FileFormat)` (L328) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` / `mapList` | `null`<br>`false`<br>`Collections.emptyList()`<br>`false` |
| **`ExportManager.java`** | `processExport(...)` (L393) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` | `false` / early exit |
| **`BaseFileWriter.java`** | `writeWorkoutSummary(...)` (L71) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` | early return |
| **`GPXFileWriter.java`** | `writeLaps(...)` (L133) | `try (Cursor cursor = ...)` + `while (cursor.moveToNext())` | early return |
| **`DevicesDatabaseManager.java`** | `getDeviceName(long)` (L169)<br>`getDeviceAddress(long)` (L189)<br>`getDeviceType(long)` (L209)<br>`getProtocol(long)` (L230)<br>`getDeviceKey(long)` (L249)<br>`isDevicePaired(long)` (L305)<br>`getCalibrationFactor(long)` (L502)<br>`isPaired(long)` (L528)<br>`getBikePowerSensorFlags(long)` (L607)<br>`upgradeToV4/V5` migrations (L1092, L1143, L1159) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` / `while (cursor.moveToNext())`<br>Annotate `getDeviceCursor` and `getCursorForAllDevices` with `@CursorProvider` | `DatabaseConstants.EMPTY_STRING`<br>`DatabaseConstants.EMPTY_STRING`<br>`null`<br>`null`<br>`DatabaseConstants.EMPTY_STRING`<br>`false`<br>`1.0f`<br>`false`<br>`DatabaseConstants.EMPTY_COUNT`<br>migration completion |
| **`SportTypeDatabaseManager.java`** | `getName(long)` (L110)<br>`getShortName(long)` (L127)<br>`getDefaultSportTypeId()` (L217)<br>`getSportType(long)` (L243)<br>`getSpeedThreshold(long)` (L306)<br>`getAutoPause(long)` (L321)<br>`getHeartRateZones(long)` (L345)<br>`getPowerZones(long)` (L371)<br>`getSpeedZones(long)` (L395)<br>`getAllSportTypes()` (L468)<br>`getSportTypeByName(String)` (L507) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` / `mapList` | `DatabaseConstants.EMPTY_STRING`<br>`DatabaseConstants.EMPTY_STRING`<br>`DatabaseConstants.INVALID_ID`<br>`null`<br>`DatabaseConstants.EMPTY_METRIC`<br>`false`<br>`null`<br>`null`<br>`null`<br>`Collections.emptyList()`<br>`null` |
| **`TrackingViewsDatabaseManager.java`** | `getViewSize(long)` (L234)<br>`getSensorFieldCount(long)` (L348)<br>`getLayout(long)` (L418)<br>`getPageCount()` (L451)<br>`getPageName(int)` (L477)<br>`getPageLayout(int)` (L497)<br>`getAllViews()` (L572)<br>`upgradeViews()` (L969) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` / `mapList` | `ViewSize.NORMAL`<br>`DatabaseConstants.EMPTY_COUNT`<br>`null`<br>`DatabaseConstants.EMPTY_COUNT`<br>`DatabaseConstants.EMPTY_STRING`<br>`null`<br>`Collections.emptyList()`<br>migration completion |
| **`SegmentsDatabaseManager.java`** | `getSegmentById(long)` (L218) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` | `null` |
| **`WorkoutSamplesDatabaseManager.java`**| `getSampleCount(long)` (L172) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` | `DatabaseConstants.EMPTY_COUNT` |
| **`ActiveDevicesDbHelper.java`** | `getActiveDevice(long)` (L72)<br>`getAllActiveDevices()` (L105) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` / `mapList` | `null`<br>`Collections.emptyList()` |
| **`EquipmentDbHelper.java`** | `migrateLegacyEquipment()` (L664) | `try (Cursor cursor = ...)` + `while (cursor.moveToNext())` | migration completion |
| **`PebbleDatabaseManager.java`** | `getView(long)` (L93)<br>`getRows(long)` (L220)<br>`getAllViews()` (L397) | `try (Cursor cursor = ...)` + `CursorUtils.mapFirst` / `mapList`<br>Annotate `getViewsCursor` with `@CursorProvider` | `null`<br>`Collections.emptyList()`<br>`Collections.emptyList()` |

---

## 3. Architecture Guard & Static Linter Design

### Detection & Exclusion Rules
1. **Scope Bounding**:
   - Strictly scans Java source files within database, helper, and exporter packages under `app/src/main/java`.
2. **Identification of Query Invocations**:
   - Detects all `.query(` and `.rawQuery(` calls.
   - Enforces that each call is either within a `try (...)` resource specification or in an authorized `@CursorProvider` method.
3. **Governance & Exclusion Precedence**:
   - Method is annotated with `@CursorProvider` (with documented Transfer of Ownership).
   - Method is annotated with `@SuppressCursorAudit` with non-empty `ticketId` matching `ATT-\\d+` and `reason`.
4. **CI/CD Integration**:
   - `tools/lint_cursor_safety.py`: Python CLI tool enforcing query safety across `app/src/main`.
   - `TryWithResourcesCursorAuditTest.kt`: JUnit test executed on every `./gradlew testDebugUnitTest` run.

---

## 4. Atomic Implementation Steps

### Step 1: Centralized Architecture Infrastructure
- Create `app/src/main/java/com/atrainingtracker/database/DatabaseConstants.java`.
- Create `app/src/main/java/com/atrainingtracker/database/CursorProvider.java` (with Transfer of Ownership Javadoc).
- Create `app/src/main/java/com/atrainingtracker/database/SuppressCursorAudit.java` (with mandatory `ticketId` and `reason`).
- Create `app/src/main/java/com/atrainingtracker/util/CursorUtils.java` (`mapFirst`, `mapList`, `moveToFirstOrNull`).
- Create unit test `CursorUtilsTest.kt` verifying mapping, exception propagation, and sentinel fallbacks.
- Create `tools/lint_cursor_safety.py` and `TryWithResourcesCursorAuditTest.kt`.

### Step 2: Migrate Exporter and Writer Classes
- Refactor `ExportStatusDatabaseManager.java`, `ExportManager.java`, `BaseFileWriter.java`, `GPXFileWriter.java`.
- Enclose queries in `try-with-resources` and use `CursorUtils`.

### Step 3: Migrate Hardware & Device Managers
- Refactor `DevicesDatabaseManager.java` and `ActiveDevicesDbHelper.java`.
- Annotate `getDeviceCursor` and `getCursorForAllDevices` with `@CursorProvider`.
- Enclose all local queries in `try-with-resources` + `CursorUtils`.

### Step 4: Migrate SportType and TrackingViews Managers
- Refactor `SportTypeDatabaseManager.java` and `TrackingViewsDatabaseManager.java`.
- Enclose all queries in `try-with-resources` + `CursorUtils`.

### Step 5: Migrate Segments, Samples, Equipment, and Pebble Managers
- Refactor `SegmentsDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `EquipmentDbHelper.java`, and `PebbleDatabaseManager.java`.
- Annotate `getViewsCursor` with `@CursorProvider`.

### Step 6: Runtime & Static Verification
- Author `DatabaseEmptyCursorSentinelTest.kt` verifying that database managers return canonical sentinels on empty tables and that `CursorUtils` propagates exceptions during iteration while closing cursors.
- Run `python3 tools/lint_cursor_safety.py`.
- Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest"` and `CursorUtilsTest`.

### Step 7: Clean-Room Regression & Gate Progression
- Run full suite `./gradlew testDebugUnitTest` (2932+ tests).
- Author Stage 5 walkthrough and update living documentation to `Verified`.
