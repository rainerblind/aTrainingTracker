# Stage 2: Requirement & Test Specification - ATT-4925: Speed Source Gating for Start in Auto-Paused & Sensor Removal

**Ticket**: [ATT-4925](https://rainerblind.atlassian.net/browse/ATT-4925)  
**Parent Epic**: [ATT-754](https://rainerblind.atlassian.net/browse/ATT-754) (*Filtering*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-044` (*Optional Automatic Stationary Auto-Pause at Workout Start*)  
**Test Spec ID**: `TST-TRK-044`  
**Branch**: `feature/ATT-4925`  
**Author**: AI Agent 2 (`subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Requirement Specification (`REQ-TRK-044`)

### 1.1 Problem Statement & Rationale
When *Start in Auto-Paused* is active, `shouldStartInAutoPause` previously treated missing speed (`null`) as stationary (`0.0 m/s`) without checking if a speed source exists, trapping indoor workouts without speed sensors in `AUTO_PAUSED` forever. Additionally, if an auto-paused workout loses all speed sources (`!hasSpeedSource`), `evaluateTick` stayed in `AUTO_PAUSED` indefinitely without an auto-resume mechanism.

### 1.2 Functional & Architectural Requirements
- `REQ-TRK-044.3`: `AutoPauseManager.shouldStartInAutoPause(config, sportType, currentSpeedMps, hasSpeedSource)` SHALL evaluate whether startup conditions are satisfied: `hasSpeedSource == true`, `config.startInAutoPauseEnabled == true`, auto-pause is enabled for `sportType`, and `currentSpeedMps` is below the configured sport speed threshold (or null/0.0/corrupt). If `hasSpeedSource` is `false`, it SHALL return `false` immediately.
- `REQ-TRK-044.4`: When the user starts tracking with `startInAutoPauseEnabled == true` and `shouldStartInAutoPause` evaluates to `true`, tracking SHALL initialize directly in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED`. If `hasSpeedSource` is `false`, tracking SHALL initialize directly in `TrackingMode.TRACKING`.
- `REQ-TRK-044.4` (Mid-Workout Sensor Removal): In `evaluateTick`, when `currentState == AutoPauseState.AUTO_PAUSED` and all speed sources are removed/disconnected (`hasSpeedSource == false`), `AutoPauseManager` SHALL transition to `AutoPauseState.SENSOR_LOST` with `triggerResume = true`, restoring recording while preserving tunnel dropout protection (`REQ-TRK-037`) when `hasSpeedSource == true`.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. *Original Requirement ID & Target*: `REQ-TRK-044` (*Optional Automatic Stationary Auto-Pause at Workout Start*), refining `REQ-TRK-018` (`ATT-45`) and `REQ-TRK-037` (`ATT-4094`). Targets `AutoPauseManager.kt`, `TrainingApplication.java`, and `TrackerService.java`.
2. *Historical Origin & Commit Trace*: Ticket `ATT-4840`, Sprint `2026-44.1` (commit `e64ff057`) and `ATT-4925`, Sprint `2026-44.2`.
3. *Root Reason for Existing Formulation*: `ATT-4840` permitted `null` speed to trigger `AUTO_PAUSED` under the assumption that speed sensors were always present; however, for indoor workouts without speed sensors, this created an unrecoverable auto-pause deadlock.
4. *Preservation of Core Invariants*: Standstill auto-pause debounce, GPS satellite fix acquisition waiting (`hasSpeedSource == true`), manual pause lock sovereignty, and tunnel dropout protection (`REQ-TRK-037`) are 100% preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

- **AC-1 (Stationary Start with Speed Source)**: *Given* `startInAutoPauseEnabled` is `true`, bike auto-pause is enabled, `hasSpeedSource == true`, and speed is $0.0\text{ m/s}$ (or null GPS acquiring), *When* workout starts, *Then* state initializes in `TrackingMode.PAUSED` (`isAutoPaused = true`) and `AutoPauseState.AUTO_PAUSED`.
- **AC-2 (No Speed Source at Start)**: *Given* `startInAutoPauseEnabled` is `true`, but `hasSpeedSource == false` (e.g. indoor HR-only), *When* workout starts, *Then* `shouldStartInAutoPause` returns `false` and state initializes in `TrackingMode.TRACKING`.
- **AC-3 (Mid-Workout Speed Source Removal)**: *Given* workout is in `AutoPauseState.AUTO_PAUSED` and all speed sources disconnect (`hasSpeedSource == false`), *When* `evaluateTick` executes, *Then* state transitions to `AutoPauseState.SENSOR_LOST` with `triggerResume = true`.
- **AC-4 (Tunnel / Dropout Invariant)**: *Given* workout is in `AutoPauseState.AUTO_PAUSED` and speed source remains registered (`hasSpeedSource == true`) but packet is stale or speed corrupt, *When* `evaluateTick` executes, *Then* state remains `AUTO_PAUSED` awaiting valid resume speed.

---

## 4. Test Specification (`TST-TRK-044`) & Traceability Matrix

| Test ID | Scope | Target Method / Class | Requirement | Expected Result |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-044.1` | Unit | `AutoPauseManager.shouldStartInAutoPause` | `REQ-TRK-044.3` | Returns `false` when `hasSpeedSource=false`; returns `true` when `hasSpeedSource=true` & speed below threshold |
| `TST-TRK-044.2` | Unit | `TrainingApplication.startTracking`, `TrackerService` | `REQ-TRK-044.4` | HR-only / no speed source starts in `TRACKING`; speed source present starts in `AUTO_PAUSED` |
| `TST-TRK-044.3` | Unit | `AutoPauseManager.evaluateTick` | `REQ-TRK-044.4` | `AUTO_PAUSED` with `hasSpeedSource=false` transitions to `SENSOR_LOST` with `triggerResume=true` |
| `TST-TRK-044.4` | Unit | `AutoPauseViewModelTest` | `REQ-TRK-044.1` | Preference mutation and reactive StateFlow emission verified |
| `TST-TRK-044.5` | Unit | `TranslationParityTest` | `REQ-TRK-044.5` | 100% 9-language translation parity verified |

---

## 5. System Invariants

1. **GPS Satellite Fix Pending**: Workouts with GPS enabled (`hasSpeedSource == true`) awaiting first fix (`speed == null`) still start in `AUTO_PAUSED`.
2. **Tunnel / Dropout Defense**: Temporary signal loss during motion continues to follow `REQ-TRK-037` in-motion dropout classification.
3. **Manual Pause Sovereignty**: User manual pause lock (`mManualPauseLock`) strictly overrides auto-pause and auto-resume.
