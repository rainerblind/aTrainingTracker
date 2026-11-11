# Stage 1 Analysis: ATT-6691 - WorkoutRepository & LegacyImportEngine: Backfill Climbs.db from existing historical workouts (climbs_workout_backfill_v1) and bulk/backup imports

**Ticket**: [ATT-6691](https://rainerblind.atlassian.net/browse/ATT-6691)  
**Parent Epic**: [ATT-2565](https://rainerblind.atlassian.net/browse/ATT-2565) (*Climbs: Detection, Live ClimbPro & Elevation Pacing*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6691`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
Prior to `V5.0.0`, `Climbs.db` did not exist; `ATT-2766` added post-workout climb extraction in `WorkoutRepository.reloadWorkoutData(workoutId)` when `isNewImportOrFinish == true`. However, per `docs/coding_standards.md` §2.4 (`Rule 80` — *Three-Way Parity: Live + Import + Historical Database Self-Healing*):
1. **Historical Workouts**: Athletes updating from legacy versions have historical outdoor workouts in `WorkoutSummaries.db` / `WorkoutSamples.db` whose qualifying climbs are absent from `Climbs.db`.
2. **Backup Restore & Schema Registry Gap**: Older `.attbackup` archives lack `Climbs.db`. Furthermore, `Climbs.db` is not registered in `DatabaseSchemaRegistry` or `DatabaseConnectionManager.getActiveDatabases()`, causing it to be skipped during backup snapshotting and WAL quiescence. Restoring an older `.attbackup` without `Climbs.db` leaves athletes with restored workouts but empty climbs without trigger for backfill.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * Hypotheses in ATT-6691 confirmed: `WorkoutRepository.extractAndPersistClimbsFromWorkout(workoutId)` exists (`WorkoutRepository.kt:3869`) and is called on fresh import/finish.
  * Audit of `LegacyImportEngine.kt:1331` confirms all import types (FIT, TCX, GPX, and Strava bulk export via `StravaBulkExportImporter.kt:256`) route through `persistParsedWorkout` and invoke `workoutRepo.reloadWorkoutData(workoutId)`.
  * Forensic audit of `DatabaseConnectionManager.kt:109` and `DatabaseSchemaRegistry.kt:29` reveals `Climbs.db` is missing from both registries, risking dirty snapshots and restore rejections.
  * In `MigrationEngine.performFullRestore()`, when restoring a legacy backup where `Climbs.db` is missing, `climbs_workout_backfill_v1` in restored preferences must be cleared/reset to trigger automatic historical backfill on next launch.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * One-time idempotent background backfill (`climbs_workout_backfill_v1` SharedPreferences guard) in `WorkoutRepository` iterating finished outdoor GPS workouts (`Sport IN ('BIKE', 'RUN', 'UNKNOWN')`, `trainer = 0` / null, non-empty `mapPolyline`) to extract climbs into `Climbs.db` with 50m spatial deduplication.
  * Register `Climbs.db` in `DatabaseSchemaRegistry` (`DATABASE_VERSION = 1`) and `DatabaseConnectionManager.getActiveDatabases()`.
  * Expose `getDatabase(): SQLiteDatabase` / `val database` on `ClimbsDatabaseManager`.
  * Audit and ensure `.attbackup` restore resets `climbs_workout_backfill_v1` when `Climbs.db` was absent from restored archive.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to `ClimbDetector` detection algorithms or categorization thresholds.
  * No UI modifications to `MapScreenWithTrack` or `GeneralMapLayer` (already completed in ATT-2766).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-MAP-064` (*Workout Climb Ingestion, Spatial Deduplication & Toggleable Map Climbs Layer*).
* **Historical Origin & Commit Trace**: `ATT-2766` (`HEAD~1`), `ATT-2565` (`c7438e78`).
* **Root Reason for Existing Formulation**: `ATT-2766` scoped climb ingestion to forward-looking finished/imported sessions (`isNewImportOrFinish`), intentionally deferring historical self-healing backfill to ATT-6691.
* **Preservation of Core Invariants**: 50m spatial deduplication threshold, transaction atomicity, low-priority IO execution, and single-commit sprint delivery remain invariant.

---

## 5. Architectural Strategy & High-Level Solution
1. **`WorkoutRepository.kt`**: Add `PREF_CLIMBS_WORKOUT_BACKFILL_V1 = "climbs_workout_backfill_v1"`. Implement `backfillHistoricalClimbs(): Int` and `triggerHistoricalClimbBackfillIfPending(): Job` on `Dispatchers.IO`. Invoke in `init {}`.
2. **`ClimbsDatabaseManager.kt`**: Expose `val database: SQLiteDatabase get() = dbHelper.writableDatabase`.
3. **`DatabaseSchemaRegistry.kt` & `DatabaseConnectionManager.kt`**: Register `Climbs.db` in schema registry and active databases list.
4. **`MigrationEngine.kt`**: In `performFullRestore`, if `Climbs.db` was not present in staged `databases/`, remove/reset `climbs_workout_backfill_v1` from shared preferences.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Idempotency: `climbs_workout_backfill_v1` ensures exactly one startup pass; subsequent launches are O(1) no-ops.
  2. Spatial deduplication: 50m start/summit distance check prevents duplicate climb rows.
  3. Non-blocking: Backfill executes asynchronously on `Dispatchers.IO` without degrading startup latency or UI rendering.
* **Risk Rating**: **LOW** (additive self-healing background pass with existing deduplicated insertion pipeline and zero schema alterations).
