# Stage 1 Analysis: ATT-4926 - Reset Cockpit Telemetry and Map Breadcrumbs on Workout Completion and App Resume

**Ticket**: [ATT-4926](https://rainerblind.atlassian.net/browse/ATT-4926)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4926`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
When an athlete completes or stops a workout, returns to the app, or navigates to Cockpit/Map tabs in `TrackingMode.READY`, the UI displays residual values from the prior workout:
1. Map breadcrumbs (`_currentTrack`) and elevation path (`_currentPathPoints`) retain the full polyline of the completed workout.
2. Accumulator sensors (`TIME_TOTAL`, `TIME_ACTIVE`, `DISTANCE_m`, `ASCENT`, `DESCENT`, `CALORIES`, `LAP_NR`) show previous totals or continue accumulating during idle post-workout states.
This violates user expectations of a clean slate for upcoming sessions and risks battery drain and data pollution when background services remain active during idle periods.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
Four interlocking defects cause telemetry and path retention:
* **Cause A (Breadcrumb Retention)**: `TrainingApplication.java` invokes `BANALServiceRepository.clearBreadcrumbs()` only in `startTracking()`. `stopTracking()`, `trackingStopped()`, and `onTrackingAborted()` omit this call, leaving `_currentTrack` and `_currentPathPoints` populated until a new workout starts.
* **Cause B (Service Binding Leak & Phantom Execution)**: In `MainActivityWithNavigation.kt`, `onResume()` calls `bindToBANALService()` without matching unbinds in `onPause()`. In `disconnectFromBANALService()`, `unbindFromBANALService()` only decrements `bindReferenceCount` by 1. Because the count remains $\ge 1$, Android ignores `stopService()`, leaving `BANALService` and its 1-Hz `ClockDevice` timer running indefinitely during idle.
* **Cause C (Idle Accumulation)**: `MyAccumulatorSensor.increment()` only checks `if (mRespectPause && (isPaused() || isAutoPaused()))`. In `TrackingMode.READY`, `isPaused()` is false. When `BANALService` leaks in the background, `ClockDevice` and location sensors continue incrementing workout timers and distances while idle.
* **Cause D (Reset Propagation & Filter Cache)**: On stop, `RESET_ACCUMULATORS_INTENT` triggers `MyAccumulatorSensor.reset()`, but it fails to notify `mSensorListeners` (`sensorListener.newValue(mZeroValue)`). Furthermore, `InstantaneousFilter.java` inherits the no-op `MyFilter.reset()`, caching stale previous-workout values.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Clear map breadcrumb polyline and elevation path points in `TrainingApplication` stop/abort paths and `BANALServiceRepository.trackingModeReceiver` upon entering `TrackingMode.READY`.
  * Fix `bindReferenceCount` lifecycle in `MainActivityWithNavigation` and `BANALServiceRepository` so idle background timeouts cleanly disconnect and terminate `BANALService`.
  * Prevent `MyAccumulatorSensor` and `ClockDevice` from accumulating telemetry when not actively tracking (`!TrainingApplication.isTracking()`).
  * Notify sensor listeners on `MyAccumulatorSensor.reset()` and implement `InstantaneousFilter.reset()` to invalidate cached telemetry immediately on session reset.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering historical workout persistence or SQLite summary export logic (managed by `WorkoutSummariesDatabaseManager`).
  * Changing route pre-loading or navigation track overlay features (`req_maps_navigation.md`).
  * Modifying rolling smoothing filters (`REQ-FIL-013.1`) that provide pre-start live telemetry (e.g. cadence, heart rate, instantaneous power).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-049` (*Post-Workout State Cleansing & Idle Service Dormancy*), extending `REQ-TRK-001` (*Start/Stop Lifecycle*), `REQ-TRK-002` (*Lap Lifecycle*), and refining `REQ-FIL-013` (*Session Reset Isolation*).
* **Historical Origin & Commit Trace**: Commit `d937073c` and `ATT-3222` introduced filter reset protocol but omitted `InstantaneousFilter`. Commit `a6920388` introduced Compose tracking tabs where repeated `onResume()` binds bypassed the single-bind assumption.
* **Root Reason for Existing Formulation**: Breadcrumb clearing was originally deferred to `startTracking()` to preserve visual map context immediately after stopping; however, with modern multi-tab navigation, this causes stale paths and non-zero readouts when reopening the app.
* **Preservation of Core Invariants**: Active workout tracking, auto-pause behavior, and pre-start live rolling sensor readouts (HR, cadence, instantaneous power) are preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Breadcrumb Cleansing**: Invoke `BANALServiceRepository.clearBreadcrumbs()` in `TrainingApplication.stopTracking()`, `trackingStopped()`, `onTrackingAborted()`, and `BANALServiceRepository.trackingModeReceiver` on `TrackingMode.READY`.
* **Binding Lifecycle Normalization**:
  * Ensure `MainActivityWithNavigation` lifecycle balances `bindToBANALService()` or encapsulates unbind logic so `disconnectFromBANALService()` cleanly forces `unbindService` and halts `BANALService` when idle.
* **Accumulator Gating**:
  * In `MyAccumulatorSensor.increment()`, gate accumulation: if `mRespectPause` is true, accumulate only if `TrainingApplication.isTracking() && !isPaused() && !isAutoPaused()`.
  * In `MyAccumulatorSensor.reset()`, dispatch `mZeroValue` to registered `mSensorListeners`.
  * In `InstantaneousFilter.java`, override `reset()` to clear cached values on `FilterManager.resetAllFilters()`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. No data loss during active tracking or auto-pause transitions.
  2. Rolling live pre-start telemetry (HR, power, cadence) continues to display in `TrackingMode.READY` per `REQ-FIL-013`.
  3. Clean zeroed readouts (`0`, `0:00:00`, Lap 1) and empty map tracks upon entering `TrackingMode.READY` after workout completion.
* **Risk Rating**: **MEDIUM** — Touches core service lifecycle (`BANALService`), accumulator sensor pipeline, and Compose repository binding; mitigated by clean separation between session accumulators and rolling live filters, backed by comprehensive unit tests.
