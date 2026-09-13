# Implementation Plan - ATT-644: Equipment Database Boundary Validation & Unassigned Equipment Query Optimization

## 1. Executive Summary & Problem Context
Ticket **ATT-644** addresses excessive Logcat error flooding and redundant SQLite database queries when rendering workout summaries (e.g. Workout List, Aftermath, Periods):
```text
ERROR: in getEquipmentFromId: no name for id: 0
ERROR: in getEquipmentFromId: no name for id: -1
ERROR: in getStravaIdFromId: no stravaId for id: -1
```

### Root Cause Analysis Summary (from ATT-646 - Erledigt)
1. In `WorkoutSummaries`, workouts without assigned equipment store `WorkoutSummaries.EQUIPMENT_ID` as SQL `NULL` (via `WorkoutSummariesDatabaseManager.putNull`) or legacy `-1L`.
2. Android SQLite Cursor contract (`cursor.getLong(columnIndex)`) returns `0L` when column value is SQL `NULL`.
3. Downstream callers (`WorkoutDataMapper`, `EquipmentDataProvider`, `StravaUploader`) unconditionally pass `0L` or `-1L` into `EquipmentDbHelper.getEquipmentNameFromId()` or `getStravaIdFromId()`.
4. `EquipmentDbHelper` has no precondition check for non-positive IDs (`<= 0`) and executes `SELECT * FROM Equipment WHERE _id = 0` (or `-1`).
5. Because SQLite auto-increment primary keys start at `1`, the cursor returns empty, and `EquipmentDbHelper` logs `Log.e(TAG, "ERROR: in getEquipmentFromId: no name for id: " + equipmentId)`.
6. This causes heavy Logcat noise and redundant database queries / lock contention on UI and background mapping threads for every unassigned workout item.

---

## 2. Requirement & Test Mapping
- **Requirement**: `REQ-STB-005` (*Equipment Database Boundary Validation & Unassigned Equipment Query Optimization*)
- **Verification Test**: `TST-STB-005` (*Equipment Database Boundary Validation & Error Log Shielding Verification*)
- **Jira Tickets**:
  - Parent: `ATT-644` ([Bug] EquipmentDbHelper.getEquipmentNameFromId)
  - Stage 1 RCA: `ATT-646` (Erledigt)
  - Stage 2 Plan: `ATT-647` (In Bearbeitung)
  - Stage 2 Test: `ATT-648` (Zu erledigen)
  - Epic: `ATT-68` (Improve WorkoutSummaries)

---

## 3. Architecture & Technical Design

### A. EquipmentDbHelper Precondition Guards (Defense-in-Depth)
`EquipmentDbHelper` must never issue database queries or log errors for non-positive primary keys:
1. `getEquipmentNameFromId(long equipmentId)`:
   - If `equipmentId <= 0`: Immediately return `null` without opening `readableDatabase`, querying SQLite, or logging an error.
   - If `equipmentId > 0`: Proceed with query; if no record found, retain existing `Log.e(TAG, "ERROR: in getEquipmentFromId: no name for id: " + equipmentId)` to warn about genuine data corruption.
2. `getStravaIdFromId(int equipmentId)`:
   - If `equipmentId <= 0`: Immediately return `null` without database access or error logging.
   - If `equipmentId > 0`: Proceed with query; log error only if a positive ID is missing.
   - Add overload `getStravaIdFromId(long equipmentId)` to avoid narrowing conversions by callers.
3. `getDeviceIdsForEquipment(long equipmentId)`:
   - If `equipmentId <= 0`: Immediately return `Collections.emptyList()` without database access.
4. `deleteEquipment(long id)` and `updateEquipment(long id, ...)`:
   - If `id <= 0`: Early return to prevent useless write transactions.

### B. Caller Short-Circuit Optimization (Eliminate Database Access Overhead)
Even though `EquipmentDbHelper` guards itself, caller layers should avoid calling the helper altogether when `equipmentId <= 0`:
1. `WorkoutDataMapper.kt`:
   - In `fromCursor(Cursor)`:
     ```kotlin
     val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
     val equipmentName = if (equipmentId > 0) equipmentDbHelper.getEquipmentNameFromId(equipmentId) else null
     ```
   - In `fromCursor(Cursor, BatchMetadata)`:
     ```kotlin
     val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
     val equipmentName = if (equipmentId > 0) equipmentDbHelper.getEquipmentNameFromId(equipmentId) else null
     ```
2. `EquipmentDataProvider.kt`:
   - In `getEquipmentData(Cursor)`:
     ```kotlin
     val equipmentId = cursor.getLong(cursor.getColumnIndexOrThrow(WorkoutSummaries.EQUIPMENT_ID))
     val equipmentName = if (equipmentId > 0) equipmentDbHelper.getEquipmentNameFromId(equipmentId) else null
     ```
3. `StravaUploader.kt`:
   - In `uploadUpdateActivity(...)`:
     ```kotlin
     val eqIndex = cursor.getColumnIndex(WorkoutSummariesDatabaseManager.WorkoutSummaries.EQUIPMENT_ID)
     val gearId: String? = if (!cursor.isNull(eqIndex)) {
         val eqId = cursor.getLong(eqIndex)
         if (eqId > 0) EquipmentDbHelper(mContext).getStravaIdFromId(eqId) else null
     } else null
     ```
4. `EquipmentAndSportTypeDiscoveryManager.kt`:
   - Filter `id > 0` before querying `getEquipmentNameFromId(id)`.
5. `SportTypeViewModel.kt`:
   - Filter `equipId > 0` before querying `getEquipmentNameFromId(equipId)`.

---

## 4. Implementation Steps & File Modifications

| Component | File Path | Scope of Modification |
| :--- | :--- | :--- |
| **Equipment DB Helper** | [`EquipmentDbHelper.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentDbHelper.java) | Add `<= 0` guards in `getEquipmentNameFromId`, `getStravaIdFromId`, and `getDeviceIdsForEquipment`. |
| **Workout Data Mapper** | [`WorkoutDataMapper.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt) | Short-circuit `equipmentName` resolution when `equipmentId <= 0` in both cursor mapping methods. |
| **Equipment Provider** | [`EquipmentDataProvider.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/EquipmentDataProvider.kt) | Short-circuit `equipmentName` resolution when `equipmentId <= 0`. |
| **Strava Uploader** | [`StravaUploader.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/exporter/uploader/StravaUploader.kt) | Guard against non-positive equipment IDs before querying `getStravaIdFromId`. |
| **Discovery Manager** | [`EquipmentAndSportTypeDiscoveryManager.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/EquipmentAndSportTypeDiscoveryManager.kt) | Filter positive IDs before name lookups. |
| **SportType VM** | [`SportTypeViewModel.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypeViewModel.kt) | Filter positive IDs before name lookups. |
| **Documentation** | [`docs/requirements.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md) | Add `REQ-STB-005` specification. |
| **Documentation** | [`docs/tests.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md) | Add `TST-STB-005` test specification. |
| **Unit Tests** | `app/src/test/.../EquipmentDbHelperResilienceTest.kt` | Unit tests for boundary IDs (`0`, `-1`, `-100`, positive), log suppression, and mapper short-circuiting. |

---

## 5. System Invariants & Regression Safety Checklist
- [x] **Valid Equipment Resolution Invariant**: Any workout with a valid assigned equipment ID (`equipmentId > 0`) MUST resolve the correct equipment name and Strava gear ID identically to before.
- [x] **Unassigned Equipment Invariant**: Any workout without equipment (SQL `NULL`, `0`, or `-1`) MUST resolve `equipmentName = null` and `gearId = null` without throwing exceptions, without executing SQLite queries, and without emitting `ERROR` logs in Logcat.
- [x] **Corrupt ID Diagnostic Invariant**: If an invalid positive ID (`equipmentId > 0` that does not exist in `Equipment.db`) is requested, the system MUST continue logging the diagnostic error so real database integrity issues remain visible.
- [x] **Zero Test Regressions**: All unit tests across the test suite (`./gradlew testDebugUnitTest`) must pass without failures.

---

## 6. Verification & Test Strategy
1. **Automated Unit Tests (`EquipmentDbHelperResilienceTest.kt`)**:
   - `testEquipmentName_zeroAndNegativeId_returnsNullWithoutQueryOrErrorLog`: Assert that calling `getEquipmentNameFromId(0L)` and `getEquipmentNameFromId(-1L)` returns `null` and does not query SQLite or call `Log.e`.
   - `testStravaId_zeroAndNegativeId_returnsNullWithoutQueryOrErrorLog`: Assert that calling `getStravaIdFromId(0)` and `getStravaIdFromId(-1)` returns `null` without SQLite access or `Log.e`.
   - `testDeviceIds_zeroAndNegativeId_returnsEmptyListWithoutQuery`: Assert that calling `getDeviceIdsForEquipment(0L)` returns an empty list.
   - `testEquipmentName_positiveId_queriesDatabaseAndReturnsName`: Assert valid positive ID queries DB and returns name.
   - `testEquipmentName_positiveIdNotFound_logsError`: Assert positive ID not found logs error as expected.
   - `testWorkoutDataMapper_unassignedEquipment_doesNotQueryEquipmentDb`: Verify mappers short-circuit lookups when `EQUIPMENT_ID` is `0` or `-1`.
   - `testEquipmentDataProvider_unassignedEquipment_resolvesNullName`: Verify provider returns `equipmentName = null` cleanly.
2. **Regression Suite**:
   - Run `./gradlew testDebugUnitTest` to ensure all existing tests pass with 0 regressions.
