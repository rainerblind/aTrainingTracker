# Stage 2: Requirement & Test Specification - ATT-4926: Reset Cockpit Telemetry and Map Breadcrumbs

**Ticket**: [ATT-4926](https://rainerblind.atlassian.net/browse/ATT-4926)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-TRK-049` (`REQ-TRK-049.1`..`REQ-TRK-049.4`)  
**Test Mapping**: `TST-TRK-049` (`TST-TRK-049.1`..`TST-TRK-049.4`)  
**Branch**: `feature/ATT-4926`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
When an athlete completes or stops a workout and re-enters the app in `TrackingMode.READY`, the UI previously retained the previous workout's map polyline (`_currentTrack`), elevation points (`_currentPathPoints`), and accumulator values (`TIME_TOTAL`, `DISTANCE_m`, etc.). Furthermore, leaked service bindings kept `BANALService` and `ClockDevice` running, incrementing timers while idle. This specification defines immediate clearing of breadcrumbs on workout stop/abort, binding lifecycle termination for idle services, gating accumulator sensor increments strictly to active tracking, and immediate zero-value dispatch through sensor listeners and filter resets.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-049.1` | `TST-TRK-049.1` | `BANALServiceRepositoryTest` | Clear `_currentTrack` and `_currentPathPoints` on `stopTracking()`, `trackingStopped()`, `onTrackingAborted()`, and on receiving `TrackingMode.READY`. |
| `REQ-TRK-049.2` | `TST-TRK-049.2` | `BANALServiceBindingLifecycleTest` | Lifecycle balancing in `MainActivityWithNavigation` and forced unbind in `disconnectFromBANALService()` preventing service leaks. |
| `REQ-TRK-049.3` | `TST-TRK-049.3` | `MyAccumulatorSensorTrackingGateTest` | In `MyAccumulatorSensor.increment()`, enforce `TrainingApplication.isTracking() && !isPaused() && !isAutoPaused()` when `mRespectPause` is true. |
| `REQ-TRK-049.4` | `TST-TRK-049.4` | `AccumulatorResetNotificationTest` | `MyAccumulatorSensor.reset()` notifies listeners with `mZeroValue`, and `InstantaneousFilter.reset()` clears cached stale values. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-TRK-049.1: Breadcrumb & Path Point Cleansing on Session End
* **Given** an active workout with populated `_currentTrack` polyline and `_currentPathPoints`,
* **When** the workout is stopped or aborted, or when the repository transitions to `TrackingMode.READY`,
* **Then** `_currentTrack` and `_currentPathPoints` are cleared to empty lists immediately.

### TST-TRK-049.2: Idle Service Disconnect & Unbind Normalization
* **Given** `MainActivityWithNavigation` repeatedly calling `bindToBANALService()` on resume,
* **When** `disconnectFromBANALService()` is invoked on idle timeout or workout finish,
* **Then** `bindReferenceCount` drops cleanly, `unbindService` executes, and `BANALService` stops without lingering timers.

### TST-TRK-049.3: Idle Accumulator Increment Suppression
* **Given** the app in `TrackingMode.READY` with `BANALService` running,
* **When** `ClockDevice` or location fixes trigger `MyAccumulatorSensor.increment()`,
* **Then** timers and distance accumulators do NOT increment while `!TrainingApplication.isTracking()`.

### TST-TRK-049.4: Reset Notification & Filter Cache Invalidation
* **Given** accumulator sensors with non-zero historical totals and cached `InstantaneousFilter` values,
* **When** `RESET_ACCUMULATORS_INTENT` and `FilterManager.resetAllFilters()` are triggered on workout completion,
* **Then** `mZeroValue` is dispatched to registered sensor listeners, and `InstantaneousFilter` invalidates cached values.

---

## 4. Invariants & Non-Functional Verification
1. **Pre-Start Live Telemetry (REQ-FIL-013)**: Instantaneous sensors (HR, cadence, live power) continue to display rolling values in `TrackingMode.READY`.
2. **Active Tracking Invariant**: Distance, ascent/descent, and timer calculations during active tracking and auto-pause are completely unaffected.
3. **Clean-Room Test Pass Rate**: 100% pass across targeted unit tests and full Tier 1 regression suite.
