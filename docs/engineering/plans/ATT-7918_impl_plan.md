# Stage 3: Implementation Plan - ATT-7918: Backfill Polar H10 Stored HR/RR & Export Gating

**Ticket**: [ATT-7918](https://rainerblind.atlassian.net/browse/ATT-7918) | **Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7918`  
**Requirement Mapping**: `REQ-SET-105` | **Test Mapping**: `TST-SET-107`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  
**Risk Classification**: `HIGH-RISK` (Touches `TrackerService.endWorkout()`, multi-database transactions, BLE PFTP fetch, and export pipeline synchronization)  

---

## 1. Problem Description & Background
Once ATT-7762 arms `SampleType.RR` internal flash recording on the Polar H10 at workout start, stopping a workout currently terminates without retrieving stored intervals. This plan implements `PolarH10OfflineController.stopAndBackfillWorkout()` on workout stop, synchronizing stored RR data to close RF/pool-swimming gaps in `WorkoutSamples.db` and `WorkoutRr.db`, recomputing summary HRV extrema via `HrvPostWorkoutRecomputer`, transactional H10 slot deletion (`removeExercise`), and gating `ExportManager.workoutFinished()` so exported FIT/TCX files contain continuous HR/HRV data.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-105` (`REQ-SET-105.1` zero-gap fast path, `.2` async fetch & 30s timeout, `.3` gap-only SQLite patch, `.4` post-backfill recompute & slot cleanup, `.5` export gating, `.6` 9-locale notification)
* **Test Mapping**: `TST-SET-107` (`TST-SET-107.1` fast path, `.2` fetch & patch, `.3` recomputation & cleanup, `.4` export gating, `.5` 30s timeout fallback, `.6` 9-locale parity)

---

## 3. System Invariants & Preserved Behavior
1. **Live Data Integrity**: Existing continuous 1-Hz GPS, speed, power, and HR trackpoints in `WorkoutSamples.db` are never overwritten or corrupted.
2. **Non-Blocking / ANR Protection**: 30-second bounded timeout ensures unreachable/dead sensor never stalls `TrackerService.endWorkout()` or hangs app shutdown.
3. **Android 8–12 Compatibility**: Plain JVM tests and Android < 13 devices bypass Polar PFTP APIs cleanly via `@RequiresApi(33)` isolation.
4. **Transactional Slot Protection**: H10 flash memory exercise is deleted via `removeExercise()` strictly after SQLite backfill and summary recomputation commit.

---

## 4. Proposed Architectural Changes
* **PolarH10OfflineController**:
  * Implement `shouldBackfill()` evaluating whether `WorkoutSamples.db` or `WorkoutRr.db` has missing HR seconds / `ARTIFACT_GAP`.
  * Implement `stopAndBackfillWorkout(context, workoutId, baseFileName)` executing zero-gap fast path or GATT-arbitrated PFTP download (`fetchExercise`), gap-only SQLite patching, `HrvPostWorkoutRecomputer` recomputation, and transactional cleanup.
* **TrackerService & Export Gating**:
  * In `endWorkout()`, synchronously await `PolarH10OfflineController.stopAndBackfillWorkout()` on the background `mDbExecutor` before dispatching `ExportManager.workoutFinished(baseFileName)`.
* **UI & Localization**:
  * Foreground sync notification strings (`notification_polar_h10_syncing_title`, `notification_polar_h10_syncing_desc`) in `strings_devices.xml` across all 9 locales (zero emojis).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: 9-Locale Sync Notification Strings
* Files: `app/src/main/res/values*/strings_devices.xml` (all 9 locales).
* Changes: Add `notification_polar_h10_syncing_title` and `_desc` across EN, DE, ES, FR, IT, JA, NL, PL, PT.
### Step 2: Zero-Gap Evaluation & SQLite Patching Primitives
* Files: `WorkoutSamplesDatabaseManager.java`, `WorkoutRrRepository.kt`, `PolarH10OfflineController.kt`.
* Changes: Add query for missing HR ranges, anchor cumulative `rrMs` to start time, patch empty 1-Hz HR in samples DB, insert `WorkoutRrInterval` closing gaps.
### Step 3: PFTP Fetch, GATT Arbitration & Transactional Slot Cleanup
* Files: `PolarH10OfflineController.kt`.
* Changes: Implement 30s timeout-bounded `stopAndBackfillWorkout()`, fast path bypass, GATT pause/resume handoff, `fetchExercise()`, post-commit `removeExercise()`.
### Step 4: TrackerService Export Gating & Continuous SWE.2 Maintenance
* Files: `TrackerService.java`, `docs/architecture.md`.
* Changes: Sequence `stopAndBackfillWorkout()` and `HrvPostWorkoutRecomputer` prior to `ExportManager.workoutFinished()`; update SWE.2 sequence diagram.
### Step 5: Unit & Integration Verification
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `TST-SET-107.1`–`TST-SET-107.6`, `verify_requirement_governance.py`, and clean-room full suite in Stage 5.
* **Rollback**: Branch isolation allows full revert without affecting `sprint/2026-46.7`.
