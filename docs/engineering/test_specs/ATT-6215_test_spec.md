# Stage 2: Requirement & Test Specification - ATT-6215: Fix duplicate bikes/shoes & auto-reconcile duplicates

**Ticket**: [ATT-6215](https://rainerblind.atlassian.net/browse/ATT-6215)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-098` (*Equipment Lookup Normalization, Strava Import Integrity & Automatic Deduplication Reconciliation*)  
**Test Spec ID**: `TST-SET-098`  
**Branch**: `feature/ATT-6215`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-SET-098)

### 1.1 Problem Statement & Rationale
Case/whitespace sensitivity and Strava ZIP import name pollution create duplicate gear in `Equipment.db` (e.g. `'Endurance'`, `'endurance '`, `'endurance'`), fracturing workout odometer totals and unlinking sensors.

### 1.2 Functional & Architectural Requirements
1. `EquipmentDbHelper.getEquipmentId` & `addOrUpdateStravaGear` SHALL evaluate matching via `TRIM(Name) = TRIM(?) COLLATE NOCASE OR TRIM(StravaName) = TRIM(?) COLLATE NOCASE`.
2. `addOrUpdateStravaGear` and `StravaEquipmentSynchronizeThread` SHALL preserve local user-customized `Name`, updating only `StravaId`, `StravaName`, `FrameType`, and `Retired`.
3. `StravaBulkExportImporter` SHALL NOT write plaintext names into `StravaId`; `StravaId` MUST remain `null` for bulk-imported gear until verified by Strava API sync.
4. `EquipmentDbHelper` SHALL increment `DB_VERSION` (2 -> 3) and execute an automatic deduplication migration: merge duplicates by `SportType` and normalized name, re-link `Links.EQUIPMENT_ID` and `SportTypeEquipmentLink.equipment_id`, reassign `WorkoutSummaries.equipmentId`, and delete duplicate rows.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* bike `'Endurance'` (`_id=13`, `StravaId=null`),
  *When* Strava sync processes `'endurance '` with ID `'b16080279'`,
  *Then* `_id=13` is matched, `StravaId` is set to `'b16080279'`, local `Name` remains `'Endurance'`, and no duplicate row is created.
* *Given* Strava ZIP export with `bikes.csv`,
  *When* `StravaBulkExportImporter` runs,
  *Then* `StravaId` remains `null` and `StravaName` is set.
* *Given* duplicates `_id=13, 20, 22` referenced by workouts,
  *When* deduplication migration runs,
  *Then* all workouts repoint to `_id=13`, links consolidate, and rows 20 and 22 are deleted.

### 1.4 System Invariants
Zero workout data loss; total workout odometer and stats remain consistent; all DB updates run within atomic transactions.

---

## 2. Test Specification (TST-SET-098)

### Test Case 1: `testEquipmentLookup_caseInsensitiveAndTrimmed_matchesSameId` (`TST-SET-098.1`)
* **Scope**: Unit Test (`EquipmentDbHelperNormalizationTest.kt`)
* **Preconditions**: Equipment `'Endurance'` exists in `Equipment.db`.
* **Action**: Invoke `getEquipmentId` with `'endurance '`, `' ENDURANCE '`, `'Endurance'`.
* **Expected Result**: All invocations return the identical existing `_id`.

### Test Case 2: `testStravaBulkImport_gearIngestion_stravaIdIsNull` (`TST-SET-098.2`)
* **Scope**: Unit Test (`StravaBulkExportImporterGearTest.kt`)
* **Action**: Import mock `bikes.csv` and `shoes.csv`.
* **Expected Result**: Created equipment has `StravaId == null`, `StravaName == gearName`, and subsequent API sync matches cleanly.

### Test Case 3: `testStravaSync_remoteWhitespaceOrCasing_preservesLocalName` (`TST-SET-098.3`)
* **Scope**: Contract Test (`StravaEquipmentSyncTest.kt`)
* **Action**: Sync remote gear `'endurance '` with existing local bike `'Endurance'`.
* **Expected Result**: `StravaId` is linked; local `Name` remains `'Endurance'` (not overwritten).

### Test Case 4: `testRealSqlite_deduplicationMigration_mergesRowsAndCascadesWorkouts` (`TST-SET-098.4`)
* **Scope**: Real SQLite Migration Test (`EquipmentDeduplicationMigrationTest.kt` / `EquipmentRealSqliteMigrationTest.kt`)
* **Action**: Upgrade DB with duplicate rows (IDs 13, 20, 22), sensor links, and workout summaries.
* **Expected Result**: Workouts remapped to 13; duplicate sensor/sport links merged without collision; rows 20 and 22 removed.

### Test Case 5: Tier 1 Targeted Regression Suite (`TST-SET-098.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SET-098.1` | Unit | `EquipmentDbHelper.getEquipmentId` | `REQ-SET-098.1`, `REQ-SET-098.2` | Specified |
| `TST-SET-098.2` | Unit | `StravaBulkExportImporter` | `REQ-SET-098.3` | Specified |
| `TST-SET-098.3` | Contract | `StravaEquipmentSynchronizeThread` | `REQ-SET-098.1`, `REQ-SET-098.2` | Specified |
| `TST-SET-098.4` | SQLite | `EquipmentDbHelper.onUpgrade` / reconciliation | `REQ-SET-098.4` | Specified |
| `TST-SET-098.5` | Regression | Database, Migration & Strava packages | `REQ-PRO-001` | Specified |
