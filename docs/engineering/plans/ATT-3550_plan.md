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

To eliminate cursor resource leaks and `CursorIndexOutOfBoundsException` systematically across legacy Java database managers, ATT-3550 introduces a standardized architectural model combining centralized sentinel constants, functional cursor navigation helpers, explicit exception handling policies, convention-based provider detection, and automated blocking CI/CD guards.

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
        +String reason
    }
    DatabaseConstants <.. CursorUtils : uses
    CursorProvider <.. CursorUtils : interacts
```

### Key Architectural Pillars

1. **Centralized Sentinel Constants (`DatabaseConstants.java`)**:
   - Location: `app/src/main/java/com/atrainingtracker/database/DatabaseConstants.java`.
   - Defines canonical empty-state sentinels (`INVALID_ID = -1L`, `EMPTY_STRING = ""`, `EMPTY_COUNT = 0`, `EMPTY_METRIC = 0.0`) to avoid magic literals and prevent sentinel drift across managers.

2. **Functional Cursor Helper (`CursorUtils.java`)**:
   - Location: `app/src/main/java/com/atrainingtracker/util/CursorUtils.java`.
   - Null-safe navigation primitives:
     - `mapFirst(Cursor cursor, T defaultValue, CursorMapper<T> mapper)`: Safely checks `if (cursor != null && cursor.moveToFirst())`, extracts and returns `mapper.map(cursor)`, or returns `defaultValue` if empty, ensuring zero `CursorIndexOutOfBoundsException`.
     - `mapList(Cursor cursor, CursorMapper<T> mapper)`: Safely iterates `while (cursor.moveToNext())`, returning an unmodifiable or empty list.
   - Operates inside standard Java `try (Cursor cursor = ...)` blocks.

3. **Exception Handling & Telemetry Policy (`SQLException` / `SQLiteException`)**:
   - `CursorUtils` propagates `SQLException` to callers to avoid masking underlying database connectivity or schema corruption defects.
   - In database managers where the existing public API contract guarantees a fallback return value (rather than re-throwing), query blocks catch `SQLiteException` explicitly, log high-severity diagnostics with `Log.e(TAG, "Query execution failed on table ...", e)`, and return the canonical sentinel, guaranteeing observability in Crashlytics and Logcat.

4. **Provider Method Naming & Annotation Convention**:
   - Public factory/provider methods returning an open `Cursor` to external components (e.g. `CursorAdapter`) must be annotated with `@CursorProvider` or have `public Cursor` return type ending with `Cursor` (e.g. `public Cursor getDeviceCursor(long)`, `public Cursor getCursorForAllDevices()`).
   - Internal helper methods (`private`, `protected`, package-private) MUST NOT use the `*Cursor` suffix unless returning directly to an outer provider, preventing accidental audit circumvention.

5. **False Positive Mitigation (`@SuppressCursorAudit`)**:
   - Introduce `@SuppressCursorAudit(reason = "...")` annotation for rare non-standard cursor patterns (e.g. custom `MatrixCursor`, test mocks, or synthetic cursors) requiring manual lifecycle handling, preventing hacky workarounds while maintaining an auditable rationale.

6. **Blocking CI/CD Gate Integration**:
   - `TryWithResourcesCursorAuditTest.kt` is executed automatically as part of `./gradlew testDebugUnitTest`, acting as a mandatory blocking gate on local development and CI pipelines.

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
   - Strictly scans `.java` source files within database, helper, and exporter packages under `app/src/main/java`.
2. **Identification of Local Cursor Allocations**:
   - Matches variable declarations `Cursor <var> =`.
   - Asserts that every local declaration is wrapped in a `try (` resource block.
3. **Exclusion Precedence**:
   - Method is annotated with `@CursorProvider`.
   - Method is annotated with `@SuppressCursorAudit`.
   - Method has `public Cursor` signature and ends in `Cursor`.
4. **CI/CD Integration**:
   - `tools/lint_cursor_safety.py`: Command-line script exiting with code 1 upon any unshielded cursor allocation.
   - `TryWithResourcesCursorAuditTest.kt`: JUnit test executed on every `./gradlew testDebugUnitTest` invocation, acting as a non-bypassable blocking gate.

---

## 4. Atomic Implementation Steps

### Step 1: Centralized Architecture Infrastructure
- Create `app/src/main/java/com/atrainingtracker/database/DatabaseConstants.java`.
- Create `app/src/main/java/com/atrainingtracker/database/CursorProvider.java` and `SuppressCursorAudit.java`.
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
- Author `DatabaseEmptyCursorSentinelTest.kt` verifying that database managers return canonical sentinels on empty tables.
- Run `python3 tools/lint_cursor_safety.py`.
- Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest"` and `CursorUtilsTest`.

### Step 7: Clean-Room Regression & Gate Progression
- Run full suite `./gradlew testDebugUnitTest` (2932+ tests).
- Author Stage 5 walkthrough and update living documentation to `Verified`.
