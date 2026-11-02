# Stage 3: Implementation Plan - ATT-4925: Speed Source Gating for Start in Auto-Paused & Sensor Removal

**Ticket**: [ATT-4925](https://rainerblind.atlassian.net/browse/ATT-4925)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-044` (`REQ-TRK-044.3`, `REQ-TRK-044.4`)  
**Test Mapping**: `TST-TRK-044` (`TST-TRK-044.1`, `TST-TRK-044.2`, `TST-TRK-044.3`)  
**Branch**: `feature/ATT-4925`  
**Author**: AI Agent 1 (Software Architect - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Architectural Design & Layer Boundaries (SWE.2)

```
[BANALService / BANALServiceRepository]
         │ (SPEED_mps registered? hasSpeedSource)
         ▼
[AutoPauseManager.shouldStartInAutoPause(..., hasSpeedSource)]
         │
         ├──► [TrainingApplication.startTracking()] -> TRACKING if !hasSpeedSource; PAUSED if hasSpeedSource
         └──► [TrackerService.onServiceConnected()] -> syncs AUTO_PAUSED only if hasSpeedSource == true
         │
[AutoPauseManager.evaluateTick(..., hasSpeedSource)]
         ▼ (mid-workout speed source removal: !hasSpeedSource)
[AUTO_PAUSED -> SENSOR_LOST (triggerResume = true)] -> sends REQUEST_RESUME_FROM_PAUSED
```

### Layer Responsibilities
- **Domain Engine (`AutoPauseManager.kt`)**: Pure predicate `shouldStartInAutoPause(..., hasSpeedSource)` returns `false` when `!hasSpeedSource`. In `evaluateTick`, transition `AUTO_PAUSED` to `SENSOR_LOST` with `triggerResume = true` when `!hasSpeedSource`.
- **Repository (`BANALServiceRepository.kt`)**: Expose helper `hasSpeedSource: Boolean` evaluating `activeSensors.contains(SensorType.SPEED_mps)`.
- **Application (`TrainingApplication.java`)**: In `startTracking()`, query `banalRepo.activeSensors` to gate `shouldStartInAutoPause`.
- **Service (`TrackerService.java`)**: In `onServiceConnected()`, gate `shouldStartInAutoPause` with `(speedData != null)`.

---

## 2. Traceability & Requirements Mapping
* **`REQ-TRK-044.3` / `TST-TRK-044.1`**: `AutoPauseManager.shouldStartInAutoPause` accepts `hasSpeedSource: Boolean`. If `false`, returns `false`.
* **`REQ-TRK-044.4` / `TST-TRK-044.2`**: `TrainingApplication.startTracking()` and `TrackerService.onServiceConnected()` pass `hasSpeedSource`. Workouts without speed sources initialize in `TRACKING`.
* **`REQ-TRK-044.4` / `TST-TRK-044.3`**: Mid-workout speed source loss in `AUTO_PAUSED` (`!hasSpeedSource`) transitions to `SENSOR_LOST` with `triggerResume = true`. Tunnel dropout (`REQ-TRK-037`) and corrupt speed retain `AUTO_PAUSED` when `hasSpeedSource == true`.

---

## 3. System Invariants & Preserved Behavior
1. **Satellite Fix Pending**: Workouts with GPS enabled awaiting initial fix (`hasSpeedSource == true`, `speed == null`) still start in `AUTO_PAUSED`.
2. **Tunnel & Corrupt Packet Defense**: Stale packets during motion (`REQ-TRK-037`) or NaN glitches while `hasSpeedSource == true` remain paused.
3. **Manual Pause Sovereignty**: Explicit user pause lock retains absolute precedence over auto-resume.
4. **Code Lock (Rule 4)**: Production source code in `app/src/...` remains untouched until Gate 3 passes.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`AutoPauseManager.kt`)**: Update `shouldStartInAutoPause` with `hasSpeedSource: Boolean`. In `evaluateTick`, add `!hasSpeedSource && currentState == AUTO_PAUSED -> SENSOR_LOST` with `triggerResume = true`.
2. **Step 2 (`BANALServiceRepository.kt`)**: Expose `hasSpeedSource: Boolean` backed by `_activeSensors`.
3. **Step 3 (`TrainingApplication.java` & `TrackerService.java`)**: Wire `hasSpeedSource` into `shouldStartInAutoPause` calls at workout start.
4. **Step 4 (`AutoPauseManagerTest.kt`)**: Update `TST-TRK-044_1` with `hasSpeedSource` cases and author `TST-TRK-044_3` for mid-workout removal from `AUTO_PAUSED`.
5. **Step 5 (`TrainingApplicationTrackingTest.kt`)**: Author `TST-TRK-044_2` tests for HR-only startup in `TRACKING` and GPS startup in `AUTO_PAUSED`.
6. **Step 6 (Verification)**: Run targeted unit tests and `verify_requirement_governance.py`.

---

## 5. Verification & Rollback Plan
* **Targeted Verification Scope**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.autopause.AutoPauseManagerTest" --tests "com.atrainingtracker.trainingtracker.TrainingApplicationTrackingTest" --tests "com.atrainingtracker.trainingtracker.tracker.TrackerServiceWorkoutStartTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Revert changes on `feature/ATT-4925` via git restore.
