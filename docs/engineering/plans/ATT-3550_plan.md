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

The objective of ATT-3550 is to guarantee complete cursor resource hygiene (preventing native file-descriptor and SQLite cursor window leaks) and crash immunity against `CursorIndexOutOfBoundsException` when queries return empty result sets.

### Core Architectural Patterns
1. **Deterministic Resource Management (`try-with-resources`)**:
   - Every local query invocation allocates its `Cursor` within a Java 7+ try-with-resources header:
     ```java
     try (Cursor cursor = db.query(...)) {
         if (cursor != null && cursor.moveToFirst()) {
             // Read columns safely
         }
     }
     ```
   - All legacy manual `cursor.close()` calls on local cursors are removed.
2. **Comprehensive Navigation Safety & Canonical Sentinel Mapping**:
   - `cursor.moveToFirst()`, `cursor.moveToLast()`, and `while (cursor.moveToNext())` are always checked as conditional guards.
   - When a result set is empty (`cursor == null || !cursor.moveToFirst()`), methods immediately return canonical, type-specific empty sentinels:
     - **Collections / Lists**: `Collections.emptyList()` or `new ArrayList<>()`
     - **Entities / Objects / Strings**: `null` (or `""` where specified by contract)
     - **Database Identifiers**: `-1L`
     - **Numeric counts / Metrics**: `0` / `0.0`
     - **Booleans**: `false`
3. **Provider / Factory Method Distinction**:
   - Methods whose explicit API contract is to return an open `Cursor` to an external consumer or `CursorAdapter` (e.g. `public Cursor getCursorForAllDevices()`, `public Cursor getDeviceCursor(long)`) are preserved as open cursor providers.
   - Internal helper methods that consume these providers wrap the returned cursor in `try (Cursor cursor = ...)` at the consumption call site.

---

## 2. Call-Site Inventory & Migration Scope

| File | Target Query Methods / Lines | Navigation Guard | Canonical Sentinel |
| :--- | :--- | :--- | :--- |
| **`ExportStatusDatabaseManager.java`** | `getExportStatus(long, FileFormat)` (L198)<br>`hasPendingExports()` (L269)<br>`getRecentExports()` (L300)<br>`isExported(long, FileFormat)` (L328) | `if (cursor != null && cursor.moveToFirst())`<br>`while (cursor.moveToNext())` | `null`<br>`false`<br>`Collections.emptyList()`<br>`false` |
| **`ExportManager.java`** | `processExport(...)` (L393) | `if (cursor != null && cursor.moveToFirst())` | `false` / early exit |
| **`BaseFileWriter.java`** | `writeWorkoutSummary(...)` (L71) | `if (cursor != null && cursor.moveToFirst())` | early return |
| **`GPXFileWriter.java`** | `writeLaps(...)` (L133) | `while (cursor.moveToNext())` | early return |
| **`DevicesDatabaseManager.java`** | `getDeviceName(long)` (L169)<br>`getDeviceAddress(long)` (L189)<br>`getDeviceType(long)` (L209)<br>`getProtocol(long)` (L230)<br>`getDeviceKey(long)` (L249)<br>`isDevicePaired(long)` (L305)<br>`getCalibrationFactor(long)` (L502)<br>`isPaired(long)` (L528)<br>`getBikePowerSensorFlags(long)` (L607)<br>`upgradeToV4/V5` migrations (L1092, L1143, L1159) | `if (cursor != null && cursor.moveToFirst())`<br>`while (cursor.moveToNext())` | `""`<br>`""`<br>`DeviceType.UNKNOWN` / `null`<br>`Protocol.UNKNOWN` / `null`<br>`""`<br>`false`<br>`1.0f` / default<br>`false`<br>`0`<br>migration loop completion |
| **`SportTypeDatabaseManager.java`** | `getName(long)` (L110)<br>`getShortName(long)` (L127)<br>`getDefaultSportTypeId()` (L217)<br>`getSportType(long)` (L243)<br>`getSpeedThreshold(long)` (L306)<br>`getAutoPause(long)` (L321)<br>`getHeartRateZones(long)` (L345)<br>`getPowerZones(long)` (L371)<br>`getSpeedZones(long)` (L395)<br>`getAllSportTypes()` (L468)<br>`getSportTypeByName(String)` (L507) | `if (cursor != null && cursor.moveToFirst())`<br>`while (cursor.moveToNext())` | `""`<br>`""`<br>`-1L`<br>`null`<br>`0.0`<br>`false`<br>`null`<br>`null`<br>`null`<br>`Collections.emptyList()`<br>`null` |
| **`TrackingViewsDatabaseManager.java`** | `getViewSize(long)` (L234)<br>`getSensorFieldCount(long)` (L348)<br>`getLayout(long)` (L418)<br>`getPageCount()` (L451)<br>`getPageName(int)` (L477)<br>`getPageLayout(int)` (L497)<br>`getAllViews()` (L572)<br>`upgradeViews()` (L969) | `if (cursor != null && cursor.moveToFirst())`<br>`while (cursor.moveToNext())` | `ViewSize.NORMAL`<br>`0`<br>`null`<br>`0`<br>`""`<br>`null`<br>`Collections.emptyList()`<br>migration loop completion |
| **`SegmentsDatabaseManager.java`** | `getSegmentById(long)` (L218) | `if (cursor != null && cursor.moveToFirst())` | `null` |
| **`WorkoutSamplesDatabaseManager.java`**| `getSampleCount(long)` (L172) | `if (cursor != null && cursor.moveToFirst())` | `0` |
| **`ActiveDevicesDbHelper.java`** | `getActiveDevice(long)` (L72)<br>`getAllActiveDevices()` (L105) | `if (cursor != null && cursor.moveToFirst())`<br>`while (cursor.moveToNext())` | `null`<br>`Collections.emptyList()` |
| **`EquipmentDbHelper.java`** | `migrateLegacyEquipment()` (L664) | `while (cursor.moveToNext())` | migration completion |
| **`PebbleDatabaseManager.java`** | `getView(long)` (L93)<br>`getRows(long)` (L220)<br>`getAllViews()` (L397) | `if (cursor != null && cursor.moveToFirst())`<br>`while (cursor.moveToNext())` | `null`<br>`Collections.emptyList()`<br>`Collections.emptyList()` |

---

## 3. Architecture Guard & Static Linter Design

### Whitelist & Provider Method Exclusion Strategy
To prevent false positives on legitimate factory/provider methods returning `Cursor` to external components (`CursorAdapter`s, ContentProviders):
1. **Whitelist**:
   - `getDeviceCursor(long deviceId)` in `DevicesDatabaseManager.java`
   - `getCursorForAllDevices()` in `DevicesDatabaseManager.java`
   - `getViewsCursor(SQLiteDatabase db, long viewId)` in `PebbleDatabaseManager.java`
   - `getCursor(SQLiteDatabase db, long deviceID, String column)` in `DevicesDatabaseManager.java`
2. **Automated Unit Test (`TryWithResourcesCursorAuditTest.kt`)**:
   - Scans all `.java` files in database, exporter, and helper packages.
   - Detects all raw `Cursor <var> =` occurrences.
   - Ignores method declarations returning `Cursor` or methods in the whitelist.
   - Enforces that all local query variables are enclosed in `try (...)`.
3. **CLI Linter (`tools/lint_cursor_safety.py`)**:
   - Standalone Python script validating zero unshielded cursor allocations.

---

## 4. Atomic Implementation Steps

### Step 1: Implement Static Architecture Guards
- Create `tools/lint_cursor_safety.py` with provider method whitelist.
- Create `app/src/test/java/com/atrainingtracker/architecture/TryWithResourcesCursorAuditTest.kt`.
- Run linter to verify detection of currently unmigrated call sites.

### Step 2: Migrate Export and Writer Classes
- Refactor `ExportStatusDatabaseManager.java`: Enclose queries in `try-with-resources` and guard `moveToFirst()`.
- Refactor `ExportManager.java`, `BaseFileWriter.java`, `GPXFileWriter.java`: Enclose queries in `try-with-resources` and guard `moveToFirst()` / `while (moveToNext())`.

### Step 3: Migrate Devices and Hardware Helper Classes
- Refactor `DevicesDatabaseManager.java`: Enclose all local queries in `try-with-resources`, remove manual `cursor.close()`, add bounds checks, preserving `getDeviceCursor` and `getCursorForAllDevices` as providers.
- Refactor `ActiveDevicesDbHelper.java`: Migrate queries to `try-with-resources`.

### Step 4: Migrate SportType and TrackingViews Managers
- Refactor `SportTypeDatabaseManager.java`: Wrap queries in `try-with-resources`, remove manual `close()`, add `moveToFirst()` guards returning type-specific sentinels.
- Refactor `TrackingViewsDatabaseManager.java`: Wrap queries in `try-with-resources`, remove manual `close()`, add `moveToFirst()` guards returning type-specific sentinels.

### Step 5: Migrate Segments, Samples, Equipment, and Pebble Managers
- Refactor `SegmentsDatabaseManager.java` (L218).
- Refactor `WorkoutSamplesDatabaseManager.java` (L172).
- Refactor `EquipmentDbHelper.java` (L664).
- Refactor `PebbleDatabaseManager.java` (L93, L111, L145, L162, L175, L220, L397).

### Step 6: Verify Static Guards and Unit Tests
- Run `python3 tools/lint_cursor_safety.py` asserting zero violations.
- Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.TryWithResourcesCursorAuditTest"`.
- Run database unit tests (e.g. `DevicesDatabaseTest`, `SportTypeDatabaseTest`, etc.).

### Step 7: Clean-Room Regression & Gate Progression
- Run full unit test suite `./gradlew testDebugUnitTest` verifying 100% pass rate.
- Author Stage 5 walkthrough and update living documentation to `Verified`.
