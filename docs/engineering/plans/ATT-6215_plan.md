# Stage 3: Implementation Plan - ATT-6215: Fix duplicate bikes/shoes & auto-reconcile duplicates

**Ticket**: [ATT-6215](https://rainerblind.atlassian.net/browse/ATT-6215)  
**Sub-task**: [ATT-6261](https://rainerblind.atlassian.net/browse/ATT-6261) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile (Gear, ANT+/BLE & PMC)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.13`  
**Requirement Mapping**: `REQ-SET-098`  
**Test Mapping**: `TST-SET-098`  
**Branch**: `feature/ATT-6215`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-08  

---

## 1. Problem Description & Background
Casing, trailing whitespace, and Strava ZIP import name pollution create duplicate gear in `Equipment.db`, fracturing workout odometer totals and unlinking sensors.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-098` (*Equipment Lookup Normalization, Strava Import Integrity & Automatic Deduplication Reconciliation*)
* **Test Mapping**: `TST-SET-098` (*Equipment Lookup Normalization, Strava Ingestion & Deduplication Reconciliation Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Workout Data Loss**: All historical `WorkoutSummaries.equipmentId` links migrate to the surviving canonical equipment ID.
2. **Link Preservation**: Sensor pairings (`Links`) and sport associations (`SportTypeEquipmentLink`) migrate without foreign key / primary key collisions.
3. **Local Name Protection**: Remote Strava sync updates `StravaId`/`StravaName` but never overwrites customized local `Name`.
4. **Code Lock & Human Decision Gate**: Parent ticket advances to `Final Review (Human)`; production code edits locked until Gate 3 passes.

---

## 4. Proposed Architectural Changes

### Component 1: `EquipmentDbHelper.java` & `DatabaseSchemaRegistry.kt`
* `getEquipmentId`: Evaluate query `TRIM(Name) = TRIM(?) COLLATE NOCASE OR TRIM(StravaName) = TRIM(?) COLLATE NOCASE`.
* `addOrUpdateStravaGear`: Allow `@Nullable String stravaId`. Preserve existing `Name` on update.
* Schema v3: Increment `DB_VERSION = 3` and reference via `DatabaseSchemaRegistry.kt` (Rule 62).

### Component 2: `EquipmentReconciliationHelper.kt`
* Auto-merge duplicate rows sharing `SportType` and normalized name. Consolidate attributes (capitalized `Name`, valid `StravaId`, active status), migrate `Links`, `SportTypeEquipmentLink`, and `WorkoutSummaries.equipmentId`, and delete duplicate rows.

### Component 3: `StravaBulkExportImporter.kt` & `StravaEquipmentSynchronizeThread.java`
* `StravaBulkExportImporter`: Pass `null` as `stravaId` during gear import; populate `StravaName`.
* `StravaEquipmentSynchronizeThread`: Delegate to `addOrUpdateStravaGear` (DRY).

### UI Consistency (Rule 23)
* No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: `EquipmentDbHelper` Lookup Normalization & Name Preservation (`REQ-SET-098.1`, `REQ-SET-098.2`)
* Files: `EquipmentDbHelper.java`
* Update `getEquipmentId` and `addOrUpdateStravaGear` for trimmed case-insensitive lookup and `Name` preservation.

### Step 2: Prevent `StravaId` Pollution in Bulk Import & Sync (`REQ-SET-098.3`)
* Files: `StravaBulkExportImporter.kt`, `StravaEquipmentSynchronizeThread.java`
* Pass `null` for `stravaId` in `StravaBulkExportImporter`. Delegate sync thread to `addOrUpdateStravaGear`.

### Step 3: Deduplication Migration Engine & Schema v3 (`REQ-SET-098.4`)
* Files: `EquipmentDbHelper.java`, `EquipmentReconciliationHelper.kt`, `DatabaseSchemaRegistry.kt`
* Bump `DB_VERSION` to 3. Implement cascade deduplication across `Equipment.db`, `SportTypeLinks.db`, and `WorkoutSummaries.db`.

### Step 4: Unit, Migration & Contract Tests (`TST-SET-098.1`-`.4`)
* Files: `EquipmentDbHelperNormalizationTest.kt`, `StravaBulkExportImporterGearTest.kt`, `StravaEquipmentSyncTest.kt`, `EquipmentRealSqliteMigrationTest.kt`
* Implement comprehensive test cases asserting normalization, null StravaId on import, name protection, and duplicate reconciliation.

### Step 5: Verification Suite (`TST-SET-098.5`)
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.database.*" --tests "com.atrainingtracker.trainingtracker.migration.*" --tests "com.atrainingtracker.trainingtracker.onlinecommunities.strava.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute Tier 1 targeted tests in Stage 4, followed by clean-room validation in Stage 5.
* **Rollback**: Atomic branch revert (`git checkout sprint/2026-45.13`) leaves existing codebase completely unaffected.
