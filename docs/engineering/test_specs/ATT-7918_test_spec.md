# Stage 2: Requirement & Test Specification - ATT-7918: Backfill Polar H10 stored HR and RR intervals on workout stop before automatic exports

**Ticket**: [ATT-7918](https://rainerblind.atlassian.net/browse/ATT-7918)  
**Parent Epic**: [ATT-5910](https://rainerblind.atlassian.net/browse/ATT-5910) (*Equipment, Sensors, Sport Types & Athlete Profile*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7` | **Branch**: `feature/ATT-7918`  
**Requirement Mapping**: `REQ-SET-105` | **Test Spec ID**: `TST-SET-107`  
**Risk Classification**: `HIGH-RISK` (Touches `TrackerService.endWorkout()`, multi-DB writes, and export gating)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-SET-105)

### 1.1 Problem Statement & Rationale
When an endurance athlete leaves their phone in a locker during swimming or experiences mid-workout BLE dropouts, `TrackerService.endWorkout()` must retrieve the internal flash session recorded by the Polar H10 before automated file serialization or cloud synchronization occurs. Without gating, `ExportManager` immediately serializes incomplete FIT/TCX files and initiates uploads lacking HR and HRV metrics.

### 1.2 Attributes
- **Priority**: High | **Type**: Functional | **Verification Method**: Unit, Integration

### 1.3 Functional & Architectural Requirements
1. `TrackerService.endWorkout()` SHALL evaluate live telemetry continuity in `WorkoutSamples.db` and `WorkoutRr.db`. If live 0x180D reception was 100% continuous (`count(HR) == totalActiveSeconds` and zero `ARTIFACT_GAP`), the system SHALL bypass PFTP sample transfer, issue `stopRecording()` + `removeExercise()`, and release downstream exports immediately (`REQ-SET-105.1`).
2. When gaps exist, `PolarH10OfflineController.stopAndBackfillWorkout()` SHALL pause `MyBTLEDevice` GATT operations, connect via PFTP, and retrieve stored RR intervals via `fetchExercise()` bounded by a 30-second timeout (`REQ-SET-105.2`).
3. Retrieved RR intervals SHALL be anchored to the exercise start timestamp (`setLocalTime`). Missing 1-Hz HR samples (`round(60000.0 / rrMs)`) SHALL be patched into empty seconds in `WorkoutSamples.db`, and RR intervals SHALL be inserted into `WorkoutRr.db` closing `ARTIFACT_GAP` ranges (`REQ-SET-105.3`).
4. Upon successful SQLite commit, `HrvPostWorkoutRecomputer.recomputeWorkoutBlocking()` SHALL recalculate rMSSD, SDNN, DFA a1, and update `WorkoutSummaries.db`. `removeExercise()` on the strap SHALL execute strictly after SQLite commit (`REQ-SET-105.4`).
5. `ExportManager.workoutFinished()` (FIT, TCX, Strava, Health Connect) SHALL be held in `TrackerService.endWorkout()` until backfill and summary recomputation complete or timeout expires (`REQ-SET-105.5`).
6. A foreground sync notification SHALL be displayed during download with 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT (`REQ-SET-105.6`).

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Pool Swim Backfill)**: *Given* a 30-min swim out of phone range, *When* workout ends, *Then* stored RR data is fetched, missing 1-Hz HR and RR rows are populated in SQLite, summaries are recomputed, and H10 exercise is cleared.
* **AC-2 (Export Gating)**: *Given* automatic exports enabled, *When* workout ends with Polar gaps, *Then* `ExportManager.workoutFinished()` is gated until backfill commits so exported files contain continuous HR/HRV.
* **AC-3 (Zero-Gap Fast Path)**: *Given* 100% continuous live 0x180D data, *When* workout ends, *Then* H10 exercise is stopped/cleared without redundant PFTP download.
* **AC-4 (30s Timeout Fallback)**: *Given* an unreachable/dead strap on workout stop, *When* 30s timeout expires, *Then* service finishes cleanly using available live data without hanging.

### 1.5 System Invariants
- Existing live GPS, cadence, power, and HR telemetry are never overwritten or corrupted.
- Unreachable sensor never hangs `TrackerService.endWorkout()` or crashes background services.
- Android 8–12 compatibility preserved via `@RequiresApi(33)` isolation.

---

## 2. Test Specification (TST-SET-107)

* **TST-SET-107.1 (Zero-Gap Fast Path)**: Unit test verifying PFTP transfer is bypassed when live telemetry has zero missing seconds and zero `ARTIFACT_GAP`.
* **TST-SET-107.2 (Stop & Fetch Backfill)**: Integration test verifying PFTP `fetchExercise()`, timestamp alignment, 1-Hz HR reconstruction, and idempotent gap-only SQLite insertion.
* **TST-SET-107.3 (Recomputation & Transactional Slot Cleanup)**: Test asserting `HrvPostWorkoutRecomputer` updates `WorkoutSummaries.db` and `removeExercise()` executes strictly after DB commit.
* **TST-SET-107.4 (Export Gating)**: Service lifecycle test asserting `ExportManager.workoutFinished()` is deferred until backfill completion.
* **TST-SET-107.5 (Bounded Timeout Fallback)**: Test asserting 30s timeout releases export gate cleanly when strap is disconnected.
* **TST-SET-107.6 (9-Locale Localization Audit)**: `TranslationParityTest` verifying sync notification strings across all 9 locales.
* **Tier 1 Verification Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.bluetooth_le.polar.*" --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-SET-107.1]` | Unit | `PolarH10OfflineController.shouldBackfill` | `REQ-SET-105.1` | Specified |
| `[TST-SET-107.2]` | Integration | `PolarH10OfflineController.stopAndBackfillWorkout` | `REQ-SET-105.2`, `REQ-SET-105.3` | Specified |
| `[TST-SET-107.3]` | Integration | `HrvPostWorkoutRecomputer.recomputeWorkoutBlocking` | `REQ-SET-105.4` | Specified |
| `[TST-SET-107.4]` | Integration | `TrackerService.endWorkout` export gating | `REQ-SET-105.5` | Specified |
| `[TST-SET-107.5]` | Unit | `PolarH10OfflineController` timeout fallback | `REQ-SET-105.2` | Specified |
| `[TST-SET-107.6]` | Localization | `TranslationParityTest` | `REQ-SET-105.6` | Specified |
