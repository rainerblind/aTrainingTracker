# Stage 1 Analysis: ATT-4925 - Speed Source Gating for Start in Auto-Paused & Sensor Removal

**Ticket**: [ATT-4925](https://rainerblind.atlassian.net/browse/ATT-4925)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4925`  
**Author**: AI Agent 1 (Software Architect - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & User Motivation
In Sprint 2026-44.1, [ATT-4840] added *Start in Auto-Paused*. Code review revealed two failure modes when no speed source is available:
1. **Startup Defect**: `AutoPauseManager.shouldStartInAutoPause(...)` treated `null` speed as stationary (`0.0 m/s`) without checking whether a speed source exists. Workouts without any speed source (e.g. indoor heart-rate-only workouts) start in `AUTO_PAUSED`.
2. **Mid-Workout Deadlock**: In `evaluateTick`, when `currentState == AUTO_PAUSED`, losing or unpairing all speed sources (`!hasSpeedSource`) branches to `// If already AUTO_PAUSED, remain AUTO_PAUSED`. Because auto-resume requires speed readings $\ge$ threshold, the workout remains auto-paused indefinitely and records nothing.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
1. **Startup Call Sites**:
   - `AutoPauseManager.shouldStartInAutoPause(config, sportType, currentSpeedMps)` lacked a `hasSpeedSource` parameter.
   - `TrainingApplication.startTracking()` and `TrackerService.onServiceConnected()` passed only `(config, sportType, speedMps)`, evaluating `true` even when `speedData == null` and no speed provider is registered.
2. **Engine Fallthrough**:
   - `AutoPauseManager.kt:387-405`: `if (isSpeedCorrupt || !hasSpeedSource)` checked `currentState == TRACKING || PAUSE_DEBOUNCE_PENDING` to transition to `SENSOR_LOST`, but left `AUTO_PAUSED` unchanged with `triggerResume = false`.
   - Distinguishing `!hasSpeedSource` (all speed sources absent) from `isSpeedCorrupt` / tunnel dropout (`hasSpeedSource == true`) is required to safely transition `AUTO_PAUSED` out to `SENSOR_LOST` / `TRACKING` while preserving tunnel protections (`REQ-TRK-037`).

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**:
  - Extend `AutoPauseManager.shouldStartInAutoPause` with `hasSpeedSource: Boolean` (`false` if `!hasSpeedSource`).
  - Wire `hasSpeedSource` in `TrainingApplication.startTracking()` (via `BANALServiceRepository.activeSensors`) and `TrackerService.onServiceConnected()` (via `speedData != null`).
  - Update `AutoPauseManager.evaluateTick`: when `!hasSpeedSource` and `currentState == AUTO_PAUSED`, transition to `SENSOR_LOST` with `triggerResume = true`.
  - Preserve `hasSpeedSource == true` tunnel dropout (`REQ-TRK-037`) and `isSpeedCorrupt` pause protections.
  - Unit tests covering `shouldStartInAutoPause` with/without speed source, mid-workout removal from `AUTO_PAUSED`, and indoor HR-only startup in `TrainingApplicationTrackingTest`.
- **Out-of-Scope**:
  - Modifying manual pause lock precedence (`mManualPauseLock`).
  - Modifying resume debounce countdown or anti-chatter hysteresis calculations.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-TRK-044` (*Optional Automatic Stationary Auto-Pause at Workout Start*), amending `REQ-TRK-044.3`, `REQ-TRK-044.4`, and refining `REQ-TRK-037.2`. Targets `AutoPauseManager.kt`, `TrainingApplication.java`, `TrackerService.java`, and `BANALServiceRepository.kt`.
- **Historical Origin & Commit Trace**: Commit `e64ff057` (`ATT-4840`, Sprint `2026-44.1`) added `shouldStartInAutoPause` allowing `null` speed to mean stationary without verifying speed sensor presence. Commit `125fd7e0` (`ATT-4094`) and `9599f5f2` (`ATT-3984`) introduced `hasSpeedSource` and `SENSOR_LOST` distinctions.
- **Root Reason for Existing Formulation**: ATT-4840 assumed workouts with auto-pause enabled always had GPS or wheel speed active.
- **Preservation of Core Invariants**: `hasSpeedSource == true` standstill evaluation, GPS satellite acquisition pending fix, manual pause lock sovereignty, and tunnel dropout protection are 100% preserved.

---

## 5. Architectural Strategy & Technical Design
1. **Startup Predicate Contract**:
   - `AutoPauseManager.shouldStartInAutoPause(config, sportType, currentSpeedMps, hasSpeedSource)`:
     - Returns `false` immediately if `!hasSpeedSource`.
     - Returns `true` if `hasSpeedSource && (currentSpeedMps == null || speed < thresholdMps)`.
2. **Service Synchronization**:
   - `TrainingApplication.startTracking()`: check `banalRepo.activeSensors.value.contains(SensorType.SPEED_mps)`.
   - `TrackerService.onServiceConnected()`: check `speedData != null`.
3. **Mid-Workout Speed Source Removal**:
   - In `AutoPauseManager.evaluateTick`: if `!hasSpeedSource` and `currentState == AUTO_PAUSED`, transition to `SENSOR_LOST` with `triggerResume = true`, `soundCue = NONE`, `auditLogEvent = "SENSOR_LOST_SPEED_SOURCE_REMOVED"`.

---

## 6. System Invariants & Risk Assessment
- **Core Invariants**:
  1. No workout without a speed source ever starts in `AUTO_PAUSED`.
  2. Outdoor workouts waiting for initial GPS fix (`hasSpeedSource == true`, `speed == null`) still start in `AUTO_PAUSED`.
  3. Workouts never stay trapped in `AUTO_PAUSED` after speed sensors disconnect or power off.
- **Risk Rating**: **LOW** — Pure state machine condition refinement and call-site parameter wiring; no database schema or UI layout changes.
