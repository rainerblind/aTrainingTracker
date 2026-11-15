# Stage 1 Analysis: ATT-7919 - Aftermath Retry Sync Banner for Asleep Polar H10

**Ticket**: [ATT-7919](https://rainerblind.atlassian.net/browse/ATT-7919) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7919`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
A Polar H10 powers down BLE 45s after losing skin contact. Swimmers often unclip straps in the shower before stopping workouts at lockers. In ATT-7918, stop-time backfill times out when the strap is asleep, saving the workout without HR/RR while the stored exercise remains stranded in strap memory. An Aftermath retry banner ensures zero data loss without blocking workout finalization.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: On backfill timeout or connection failure, `PolarH10OfflineController.stopAndBackfillWorkout()` disarms and returns `false`. `TrackerService` finalizes the workout and exports partial telemetry; no persistent record of the unretrieved session is retained.
* **Independent Peer Review & Verification**:
  * Verified: Arming in `PolarH10OfflineController` generates `exerciseId = "ATT_${System.currentTimeMillis()}"` and knows the target MAC/ID.
  * Verified: `WorkoutSummariesDatabaseManager` (DB_VERSION 30) can add pending sync columns (`polarDeviceId`, `polarExerciseId`, `polarExpectedStartMs`, `polarExpectedEndMs`, `polarSyncPending`) via idempotent `onUpgrade` and matching `onCreate`.
  * Verified: `DatabaseSchemaRegistry.kt` dynamically binds `WorkoutSummariesDbHelper.DB_VERSION`. Existing tests (`DatabaseMigrationIndexTest`, `WorkoutRpeFeelTest`) assert DB_VERSION and require updated constants.
  * Verified: Deferred backfill can reuse ATT-7918's gap-only `WorkoutSamples.db` + `WorkoutRr.db` backfiller, `HrvPostWorkoutRecomputer`, and transactional `removeExercise()`, followed by export invalidation and refresh.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Persist pending Polar H10 sync metadata in `WorkoutSummaries.db` on stop-time connection failure/timeout without blocking workout save.
  * Render a Material 3 retry card/banner in Aftermath (`TrackOnMapScreen`) with wake instructions, "Sync Now", and "Dismiss".
  * Execute deferred backfill on "Sync Now", delete strap memory slot, clear pending flag, recompute HRV summaries, and trigger re-export.
  * 9-language localization parity without decorative inline emojis.
* **Out-of-Scope Non-Goals**:
  * Continuous background BLE polling/scanning when the app is closed.
  * Non-Polar sensors or modifying external export formats (FIT/TCX).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Status**: Net-new requirement only (`REQ-SET-106` mapped to `TST-SET-108`). No existing requirements modified.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SET-106` (`req_settings_profiles.md`).
* **Affected Architecture Elements**: `PolarH10OfflineController.kt`, `WorkoutSummariesDatabaseManager.java` (DB_VERSION 30->31), `WorkoutData.kt`, `WorkoutDataMapper.kt`, `TrackOnMapScreen.kt`, `WorkoutRepository.kt`, strings resources.
* **Verification Impact & Test Surface**: `WorkoutSummariesDatabaseManagerMigrationV31Test`, `DatabaseMigrationIndexTest`, `WorkoutRpeFeelTest`, `PolarH10OfflineControllerTest`, unit test suite.
* **Risk Assessment & Invariants**:
  * *Non-Blocking Invariant*: Timeout or connection failure during tracking stop never hangs `TrackerService.endWorkout()`.
  * *Transactional Slot Cleanup*: `removeExercise()` invoked only after SQLite commits backfilled samples.
  * *Dismiss Safety*: Dismissing banner clears pending flag without deleting unretrieved strap exercises.

---

## 6. Architectural Strategy & High-Level Solution
1. **Schema Upgrade**: Bump `WorkoutSummariesDbHelper.DB_VERSION` to 31; add `polarDeviceId`, `polarExerciseId`, `polarExpectedStartMs`, `polarExpectedEndMs`, `polarSyncPending` with `addColumnIfNotExists`.
2. **Stop-Time Fallback**: If `stopAndBackfillWorkout` fails/times out, write pending metadata to `WorkoutSummaries.db` before returning `false`.
3. **Aftermath UI Banner**: When `workoutData.polarSyncPending == true`, display `PolarH10RetrySyncBanner` in `TrackOnMapScreen`. "Dismiss" marks resolved in DB; "Sync Now" launches `PolarH10OfflineController.syncPendingWorkout()`.
4. **Deferred Backfill & Re-Export**: Connect to strap, fetch matching exercise, run gap-only patch, recompute HRV, remove exercise from strap, mark pending resolved, and trigger `exportManager.exportWorkout()`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero workout finalization delays when strap is asleep.
  2. Complete backwards compatibility via API 33 isolation and schema migration.
  3. No duplicate samples; live GPS and sensor streams preserved intact.
* **Risk Rating**: **MEDIUM** (Modifies `WorkoutSummaries.db` schema and adds Aftermath UI/re-export actions; core real-time tracking is decoupled).
