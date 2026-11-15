# Stage 1 Analysis: ATT-7918 - Backfill Polar H10 HR/RR on Workout Stop Before Exports

**Ticket**: [ATT-7918](https://rainerblind.atlassian.net/browse/ATT-7918) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7918`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
In ATT-7762, `PolarH10OfflineController` armed `SampleType.RR` flash recording at workout start. However, when an endurance athlete leaves their phone in a locker (swimming) or experiences mid-workout radio drops, `TrackerService.endWorkout()` currently shuts down without retrieving the strap's stored session. Consequently, `ExportManager` immediately serializes incomplete FIT/TCX files and triggers Strava/Health Connect sync before HR/HRV can be backfilled.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Current State**: `TrackerService.endWorkout()` invokes `ExportManager.workoutFinished()` immediately on `mDbExecutor` while `HrvPostWorkoutRecomputer` runs asynchronously in `ForkJoinPool`, creating a race condition where exports trigger on incomplete data.
* **Independent Peer Review & Verification**:
  * Verified: In `polar-ble-sdk:8.6.0`, `fetchExercise()` returns `PolarExerciseData` with raw RR intervals in `data.hrSamples` when `SampleType.RR` was armed.
  * Verified: Missing 1-Hz HR in `WorkoutSamples.db` can be reconstructed via `round(60000.0 / rrMs)` anchored to exercise start time (`setLocalTime`).
  * Verified: `WorkoutRrRepository.insertIntervalsBlocking` can patch missing beats and `ARTIFACT_GAP` ranges in `WorkoutRr.db`.
  * Verified: `removeExercise()` must be deferred until SQLite backfill transaction commits, preventing data loss on download interruptions.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Implement `PolarH10OfflineController.stopAndBackfillWorkout()` coordinating PFTP `fetchExercise()`, GATT arbitration, and transactional SQLite patching.
  * Zero-gap fast path: Skip slow PFTP transfer if live BLE 0x180D had zero dropouts (`count(HR) == totalSeconds` and no `ARTIFACT_GAP`).
  * Gated export pipeline: Defer `ExportManager.workoutFinished()` until backfill + `HrvPostWorkoutRecomputer` finishes (or bounded timeout).
  * Foreground sync notification during PFTP download.
* **Out-of-Scope Non-Goals**:
  * Modifying non-Polar BLE sensors or manual file import screens.
  * Altering FIT/TCX encoder schemas or Strava OAuth upload worker internals.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Requirement Status**: Net-new requirement only (`REQ-SET-105` mapped to `TST-SET-107`). No existing requirements modified.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Net-new `REQ-SET-105` (Tracking, Persistence & Settings shard).
* **Affected Architecture Elements**: `PolarH10OfflineController.kt`, `TrackerService.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRrDatabaseManager.kt`, `ExportManager.java`, `docs/architecture.md`.
* **Verification Impact & Test Surface**: `PolarH10OfflineBackfillTest`, `TrackerServiceEndWorkoutGatingTest`, clean-room unit suite, and governance audit.
* **Risk Assessment & Invariants**:
  * *Timeout Guard*: 30s bounded timeout ensures unreachable/dead strap never hangs `endWorkout()`.
  * *Idempotent Backfill*: Existing continuous live HR/RR rows are never overwritten or duplicated.

---

## 6. Architectural Strategy & High-Level Solution
1. **Zero-Gap Evaluation**: In `TrackerService.endWorkout()`, inspect `WorkoutSamples.db` for missing HR seconds. If continuous, call `stopRecording()` + `removeExercise()` immediately.
2. **Asynchronous Backfill Gate**: If gaps exist, display "Syncing Polar H10 HR/HRV...", pause `MyBTLEDevice` GATT, fetch RR samples via `fetchExercise()`, insert reconstructed 1-Hz HR into `WorkoutSamples.db` and RR beats into `WorkoutRr.db`.
3. **Downstream Execution**: Run `HrvPostWorkoutRecomputer.recomputeWorkoutBlocking()`, delete H10 exercise slot, then release `ExportManager.workoutFinished()`.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Export files (.fit, .tcx) and cloud uploads contain 100% complete HR/HRV when backfill succeeds.
  2. Unreachable sensor or fetch error gracefully falls back to partial live data without service crash.
  3. Android 8–12 backward compatibility maintained via API 33 isolation.
* **Risk Rating**: **HIGH** (Touches `TrackerService.endWorkout()` lifecycle, multi-database writes, and export synchronization).
