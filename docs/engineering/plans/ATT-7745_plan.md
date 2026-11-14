# Stage 3: Implementation Plan - ATT-7745: Auto-name workout, trigger Warmup/Cooldown lap splits, and stop recording on Guided HRV Test completion

**Ticket**: [ATT-7745](https://rainerblind.atlassian.net/browse/ATT-7745)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV: Real-Time Autonomic Metrics, Aerobic Threshold Guidance & Readiness Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6` | **Branch**: `feature/ATT-7745`  
**Requirement Mapping**: `REQ-TRK-072.8` | **Test Mapping**: `TST-TRK-072.10`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
Guided DFA α1 Threshold Tests currently record as a single unsegmented lap without phase boundaries between Warmup, Active Test, and Cooldown; recording continues indefinitely post-test until manually stopped; and workout titles default to raw timestamps or get overwritten by cluster/location auto-naming in `TrackerService.finalizeLiveSession()`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-TRK-072.8a`** (`TST-TRK-072.10a`): Dispatch `REQUEST_NEW_LAP` broadcast on `WARMUP -> TESTING` transition and entering `COOLDOWN` (producing 3 canonical laps: Warmup, Active Test, Cooldown).
* **`REQ-TRK-072.8b`** (`TST-TRK-072.10c`, `TST-TRK-072.10d`): Persist localized `WORKOUT_NAME` in `tagActiveWorkoutWithProtocol()` and suppress cluster/location name overwrites in `TrackerService.finalizeLiveSession()`.
* **`REQ-TRK-072.8c`** (`TST-TRK-072.10b`): Dispatch `REQUEST_STOP_TRACKING` broadcast when cooldown timer expires (`COOLDOWN -> FINISHED`) or `finishTest()` is invoked while tracking is active.
* **`REQ-TRK-072.8d`** (`TST-TRK-072.10e`): 9-language localization parity for `hrv_workout_name_step` and `hrv_workout_name_ramp`.

---

## 3. System Invariants & Preserved Behavior
1. **Manual Tracking Sovereignty**: Manual stop and lap triggers remain fully operational without interference.
2. **Pause/Resume Neutrality**: `ATT-896` lap neutrality across pause/resume intervals remains completely intact.
3. **Free Workout Clustering**: Standard cluster and known-location naming for regular workouts continue unaltered.
4. **9-Language Parity**: 100% localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. Proposed Architectural Changes
* **`HrvRampTestViewModel.kt`**:
  - Add `sendNewLapBroadcast()` and `sendStopTrackingBroadcast()` guarded by `TrainingApplication.isTracking()`.
  - In `processTick()`, trigger `sendNewLapBroadcast()` on `WARMUP -> TESTING` countdown expiry.
  - In `transitionToCooldown()`, trigger `sendNewLapBroadcast()`.
  - In `processTick()`, trigger `sendStopTrackingBroadcast()` on `COOLDOWN` expiry (`stepRemainingSec <= 0`).
  - In `finishTest()`, trigger `sendStopTrackingBroadcast()`.
  - In `tagActiveWorkoutWithProtocol()`, persist localized `WorkoutSummaries.WORKOUT_NAME` (`hrv_workout_name_step` / `hrv_workout_name_ramp`).
* **`WorkoutRepository.kt`**: Expose `getWorkoutMethod(workoutId: Long): String?` querying `WorkoutSummaries.METHOD`.
* **`TrackerService.java`**: In `finalizeLiveSession()`, check `isGuidedHrvTest = "GUIDED_STEP".equals(method) || "GUIDED_RAMP".equals(method)`; if true, skip cluster and known-location `updateWorkoutName()` overwrites.
* **Localization**: Define `hrv_workout_name_step` and `hrv_workout_name_ramp` across all 9 `strings_hrv.xml` files.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (Localization)**: Add `hrv_workout_name_step` and `hrv_workout_name_ramp` to all 9 `res/values*/strings_hrv.xml` files.
2. **Step 2 (Repository Accessor)**: Add `fun getWorkoutMethod(workoutId: Long): String?` in `WorkoutRepository.kt`.
3. **Step 3 (ViewModel Automation)**: Implement lap broadcasts, stop tracking broadcast, and workout name persistence in `HrvRampTestViewModel.kt`.
4. **Step 4 (TrackerService Auto-Naming Guard)**: Guard cluster & location auto-naming in `TrackerService.finalizeLiveSession()` against overwriting guided HRV workouts.
5. **Step 5 (Unit Tests)**: Add automated unit tests in `HrvRampTestViewModelTest.kt` asserting broadcasts and workout tagging, and verify localization parity in `TranslationParityTest.kt`.
6. **Step 6 (Living Documentation & Governance)**: Update `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md` with `REQ-TRK-072.8` / `TST-TRK-072.10`, run `verify_requirement_governance.py --sync-indexes`.

---

## 6. Verification Plan
* **Targeted Unit Tests**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" \
    --tests "com.atrainingtracker.trainingtracker.tracker.*" \
    --tests "*TranslationParityTest*" --max-workers=2
  ```
* **Living Documentation Governance**: `python3 tools/verify_requirement_governance.py` (100% parity).
