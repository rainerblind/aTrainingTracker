# Stage 3: Implementation Plan - ATT-2186: Add Origin Source Attribute (Tracked, TCX, GPX, FIT) to Workouts

**Ticket**: [[ATT-2186]](https://atrainingtracker.atlassian.net/browse/ATT-2186)  
**Sub-task**: [[ATT-2190]](https://atrainingtracker.atlassian.net/browse/ATT-2190) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-281]](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Migration*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-40.14`  
**Requirement Mapping**: `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*)  
**Test Mapping**: `TST-DAT-012` (*Workout Origin Source Provenance Verification*)  
**Branch**: `feature/ATT-2186`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In `aTrainingTracker`, workout sessions originate from multiple distinct ingestion channels:
1. Live sensor recording on the mobile device via `TrackerService`.
2. External file imports via `LegacyImportEngine` (TCX and GPX activity files).
3. Upcoming Garmin FIT SDK binary imports (`FitFileImporter` / ATT-1828).

Currently, `WorkoutSummaries.TABLE` stores extensive session statistics and classifications (sport, equipment, commute, trainer, race), but **lacks an explicit provenance attribute recording how the session entered the database**. Once imported or recorded, athletes cannot tell whether a workout was recorded live on their phone or imported from an external source, nor can downstream components (such as export filters or cloud synchronizers) differentiate between live recordings and external imports.

Adding a dedicated `WorkoutSource` provenance attribute across the SQLite schema (v24), ingestion engines, domain models, and UI header provides complete data transparency, traceability, and platform identity without cluttering the UI for live tracked sessions.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-DAT-017` (*Workout Origin Source Provenance Attribute*)
  - Domain Model `WorkoutSource` (`TRACKED`, `TCX`, `GPX`, `FIT`) with robust string parser.
  - Database schema evolution to `DB_VERSION = 24` with `source text DEFAULT 'TRACKED'`.
  - Safe `onUpgrade(23, 24)` column addition preserving all historical records.
  - Provenance tagging in `TrackerService.createNewWorkout()` (`TRACKED`).
  - Provenance tagging in `LegacyImportEngine.importTcxStream` (`TCX`) and `importGpxWorkoutStream` (`GPX`).
  - Domain mapping in `WorkoutData`, `WorkoutHeaderData`, `WorkoutDataMapper`, and `WorkoutRepository`.
  - Subtle M3 tonal origin badge in `WorkoutHeader.kt` for non-tracked sessions (`source != TRACKED`).
  - 100% 9-language localization parity across all supported application locales.
* **Test Mapping**: `TST-DAT-012` (*Workout Origin Source Provenance Verification*)
  - `TST-DAT-012.1`: `WorkoutSummariesDatabaseSourceMigrationTest.kt`
  - `TST-DAT-012.2`: `WorkoutDataSourceMappingTest.kt`
  - `TST-DAT-012.3`: `WorkoutIngestionSourceTaggingTest.kt`
  - `TST-DAT-012.4`: `WorkoutHeaderSourceBadgeVisualContractTest.kt`
  - `TST-DAT-012.5`: `TranslationParityTest.kt`
  - `TST-DAT-012.6`: Clean-room regression suite (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Data Loss on Migration (v23 -> v24)**: Historical workout records, extrema rows, spatial bounds, and cluster mappings are 100% preserved. Unset legacy records evaluate safely to `WorkoutSource.TRACKED`.
2. **Immutable Provenance Across Edits**: Editing workout properties in `EditWorkoutScreen` (such as sport, equipment, description, commute, race) preserves the immutable origin source in SQLite and memory.
3. **Single-Source UI Consistency**: `WorkoutHeader` remains the single source of truth for header rendering across list cards (`WorkoutSummary.kt`) and detail views (`TrackOnMapScreen.kt`).
4. **Visual Cleanliness for Live Sessions**: Live sessions (`source == WorkoutSource.TRACKED`) do NOT display an import badge, keeping primary tracking cards uncluttered.
5. **Thread Safety & Dispatcher Affinity**: Database operations remain isolated to designated single-thread dispatchers (`Dispatchers.IO`, `mDbExecutor`).
6. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-2186` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Domain Model (`WorkoutSource.kt`)
* Package: `com.atrainingtracker.trainingtracker.database`
* Enum values: `TRACKED`, `TCX`, `GPX`, `FIT`.
* Method: `fromString(value: String?): WorkoutSource`
  - Safely matches enum name case-insensitively.
  - Gracefully falls back to `WorkoutSource.TRACKED` for null, empty, blank, or unrecognized strings.

### Component 2: SQLite Schema & Upgrade (`WorkoutSummariesDatabaseManager.java`)
* Increment schema version: `DB_VERSION = 24`.
* Constant: `WorkoutSummaries.SOURCE = "source"`.
* Table schema: Add `+ WorkoutSummaries.SOURCE + " text DEFAULT 'TRACKED',"` to `CREATE_TABLE`.
* Schema migration: In `onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)`:
  ```java
  if (oldVersion < 24) {
      Log.i(TAG, "upgrading to DB version 24 (Adding source column)");
      addColumnIfNotExists(db, WorkoutSummaries.TABLE, WorkoutSummaries.SOURCE, "text", "'TRACKED'");
  }
  ```
* Persistence: In `updateWorkoutData(WorkoutData workoutData)`:
  ```java
  if (workoutData.getSource() != null) {
      values.put(WorkoutSummaries.SOURCE, workoutData.getSource().name());
  }
  ```

### Component 3: Ingestion Pipeline Provenance Tagging
* `TrackerService.java`: In `createNewWorkout()`, put `WorkoutSummaries.SOURCE = WorkoutSource.TRACKED.name()`.
* `LegacyImportEngine.kt`:
  - In `importTcxStream()`, put `WorkoutSummaries.SOURCE = WorkoutSource.TCX.name`.
  - In `importGpxWorkoutStream()`, put `WorkoutSummaries.SOURCE = WorkoutSource.GPX.name`.

### Component 4: Domain Mapping & In-Memory State
* `WorkoutData.kt`: Add `val source: WorkoutSource = WorkoutSource.TRACKED`.
* `WorkoutHeaderData.kt`: Add `val source: WorkoutSource = WorkoutSource.TRACKED`.
* `WorkoutData.headerData`: Pass `source = source`.
* `WorkoutDataMapper.kt`: Extract column `WorkoutSummaries.SOURCE` via `takeIf { it >= 0 }?.let { WorkoutSource.fromString(cursor.getString(it)) } ?: WorkoutSource.TRACKED`.
* `WorkoutRepository.kt`: In `saveWorkout()`, ensure in-memory copy retains `source = current.source` (or `userEditedWorkout.source`).

### Component 5: Presentation & Localization
* `WorkoutHeader.kt`: In Row A (sport metadata row), after equipment and race badge:
  ```kotlin
  if (data.source != WorkoutSource.TRACKED) {
      val sourceLabel = when (data.source) {
          WorkoutSource.TCX -> stringResource(R.string.workout_source_tcx)
          WorkoutSource.GPX -> stringResource(R.string.workout_source_gpx)
          WorkoutSource.FIT -> stringResource(R.string.workout_source_fit)
          WorkoutSource.TRACKED -> ""
      }
      Surface(
          shape = RoundedCornerShape(4.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          contentColor = MaterialTheme.colorScheme.onSurfaceVariant
      ) {
          Text(
              text = sourceLabel,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
      }
  }
  ```
* Localization across all 9 `strings.xml` files:
  - `workout_source_tracked`: Tracked / Aufgezeichnet / etc.
  - `workout_source_tcx`: TCX
  - `workout_source_gpx`: GPX
  - `workout_source_fit`: FIT

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Domain Enum Model (`WorkoutSource.kt`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSource.kt`
* **Changes**: Create enum with values `TRACKED`, `TCX`, `GPX`, `FIT` and robust `fromString(value: String?)` companion method.
* **Test**: Author unit tests in `WorkoutSourceTest.kt` verifying parsing and fallback.

### Step 2: Database Schema & Migration (`WorkoutSummariesDatabaseManager.java`)
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
* **Changes**:
  - Add constant `WorkoutSummaries.SOURCE = "source"`.
  - Bump `DB_VERSION` from 23 to 24.
  - Update `CREATE_TABLE` string with `+ WorkoutSummaries.SOURCE + " text DEFAULT 'TRACKED',"`.
  - Update `onUpgrade()` with `oldVersion < 24` branch invoking `addColumnIfNotExists`.
  - Update `updateWorkoutData()` to persist `workoutData.getSource().name()`.
* **Test**: Author `WorkoutSummariesDatabaseSourceMigrationTest.kt` verifying migration from v23 to v24 and default value preservation.

### Step 3: Domain Models & Mapper Integration
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderData.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
* **Changes**:
  - Expose `source: WorkoutSource` in `WorkoutData` and `WorkoutHeaderData`.
  - Pass `source` in `WorkoutData.headerData` getter.
  - Extract `source` column safely in `WorkoutDataMapper.fromCursor()`.
  - Preserve `source` in `WorkoutRepository.saveWorkout()`.
* **Test**: Author `WorkoutDataSourceMappingTest.kt` verifying cursor extraction and headerData propagation.

### Step 4: Ingestion Pipeline Provenance Tagging
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/migration/LegacyImportEngine.kt`
* **Changes**:
  - `TrackerService.createNewWorkout()`: put `WorkoutSummaries.SOURCE = WorkoutSource.TRACKED.name()`.
  - `LegacyImportEngine.importTcxStream()`: put `WorkoutSummaries.SOURCE = WorkoutSource.TCX.name`.
  - `LegacyImportEngine.importGpxWorkoutStream()`: put `WorkoutSummaries.SOURCE = WorkoutSource.GPX.name`.
* **Test**: Author `WorkoutIngestionSourceTaggingTest.kt` verifying TCX/GPX stream imports write correct source string to SQLite.

### Step 5: 9-Language Localization Parity
* **Target Files**:
  - `app/src/main/res/values/strings.xml` (EN)
  - `app/src/main/res/values-de/strings.xml` (DE)
  - `app/src/main/res/values-es/strings.xml` (ES)
  - `app/src/main/res/values-fr/strings.xml` (FR)
  - `app/src/main/res/values-it/strings.xml` (IT)
  - `app/src/main/res/values-ja/strings.xml` (JA)
  - `app/src/main/res/values-nl/strings.xml` (NL)
  - `app/src/main/res/values-pl/strings.xml` (PL)
  - `app/src/main/res/values-pt/strings.xml` (PT)
* **Changes**: Define keys `workout_source_tracked`, `workout_source_tcx`, `workout_source_gpx`, `workout_source_fit`.
* **Test**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`.

### Step 6: UI Header Badge Presentation
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`
* **Changes**: In Row A, render subtle M3 surface badge with `labelSmall` text for non-tracked workouts (`source != TRACKED`).
* **Test**: Author `WorkoutHeaderSourceBadgeVisualContractTest.kt` verifying badge visibility contract.

### Step 7: Clean-Room Regression Verification
* **Command**: `./gradlew testDebugUnitTest`
* **Target**: 100% test pass rate across the full test suite with 0 regressions.

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit tests during each step, culminating in clean-room `./gradlew testDebugUnitTest` execution.
* **Rollback Safety**: All changes are isolated on `feature/ATT-2186` branched from `sprint/2026-40.14`. In the event of an unresolvable defect or regression, the branch can be cleanly reset with zero impact on `develop` or the sprint integration branch.
