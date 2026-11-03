# Stage 3: Implementation Plan - ATT-4926: Reset Cockpit Telemetry and Map Breadcrumbs

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

## 1. Problem Description & Background
When a workout is completed or stopped and the user navigates back to Cockpit/Map tabs in `TrackingMode.READY` or resumes the app, residual values from the previous workout remain visible: map breadcrumbs (`_currentTrack`), elevation profiles (`_currentPathPoints`), and non-zero accumulators. Furthermore, an unbalanced `bindReferenceCount` leak in `MainActivityWithNavigation.kt` prevents `BANALService` from stopping when idle, causing `ClockDevice` and location sensors to continue incrementing workout timers and distances during idle background periods.

---

## 2. Traceability & Requirements Mapping
* **`REQ-TRK-049.1` / `TST-TRK-049.1`**: Map breadcrumbs and elevation points cleared in `TrainingApplication.java` (`stopTracking()`, `trackingStopped()`, `onTrackingAborted()`) and `BANALServiceRepository.kt` on `TrackingMode.READY`.
* **`REQ-TRK-049.2` / `TST-TRK-049.2`**: Service binding lifecycle normalized in `MainActivityWithNavigation.kt` and `BANALServiceRepository.kt`; ensures clean unbind and `stopService()` on idle timeout when not tracking.
* **`REQ-TRK-049.3` / `TST-TRK-049.3`**: `MyAccumulatorSensor.increment()` gates accumulation to active tracking (`TrainingApplication.isTracking() && !isPaused() && !isAutoPaused()`) when `mRespectPause` is true.
* **`REQ-TRK-049.4` / `TST-TRK-049.4`**: `MyAccumulatorSensor.reset()` notifies listeners with `mZeroValue`, and `InstantaneousFilter.reset()` clears cached stale values.

---

## 3. System Invariants & Preserved Behavior
1. **Active Tracking Invariant**: In-progress workout metrics, auto-pause resumption, and manual lap splitting remain completely unaltered.
2. **Pre-Start Live Telemetry (`REQ-FIL-013`)**: Instantaneous rolling filters (HR, cadence, live power) continue updating in `TrackingMode.READY`.
3. **Idle Battery & Resource Protection**: No running background services or timer ticks while idle in `TrackingMode.READY`.
4. **Code Lock (Rule 4)**: Production source code remains locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes
* **Application & Repository Lifecycle (`TrainingApplication.java`, `BANALServiceRepository.kt`)**:
  * Add `BANALServiceRepository.getInstance(this).clearBreadcrumbs()` in `TrainingApplication` stop/abort callbacks.
  * In `BANALServiceRepository.trackingModeReceiver`, invoke `clearBreadcrumbs()` whenever entering `TrackingMode.READY`.
* **Service Binding Normalization (`MainActivityWithNavigation.kt`, `BANALServiceRepository.kt`)**:
  * Provide `forceUnbindFromBANALService()` or balance activity lifecycle binds so `disconnectFromBANALService()` drops connections and terminates `BANALService` during idle.
* **Telemetry Gating & Filter Reset (`MyAccumulatorSensor.java`, `InstantaneousFilter.java`)**:
  * In `MyAccumulatorSensor.increment()`, enforce `TrainingApplication.isTracking()` before accumulating when `mRespectPause` is true.
  * In `MyAccumulatorSensor.reset()`, dispatch `mZeroValue` to registered listeners.
  * In `InstantaneousFilter.java`, override `reset()` to clear cached values.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Cockpit and Map composables (`SensorGridScreen.kt`, `TrackingMapScreen.kt`).
* **UI Theme Tokens**: Preserved existing tokens; no new styles or visual components introduced.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`TrainingApplication.java` & `BANALServiceRepository.kt`)**: Wire `clearBreadcrumbs()` to workout termination and `TrackingMode.READY` transition.
2. **Step 2 (`MainActivityWithNavigation.kt` & `BANALServiceRepository.kt`)**: Normalize binding lifecycle and unbind on idle disconnect.
3. **Step 3 (`MyAccumulatorSensor.java`)**: Gate `increment()` on `isTracking()` and notify listeners in `reset()`.
4. **Step 4 (`InstantaneousFilter.java`)**: Implement `reset()` to clear cached telemetry.
5. **Step 5 (Unit Tests)**: Author unit tests in `BANALServiceRepositoryTest`, `MyAccumulatorSensorTrackingGateTest`, and `InstantaneousFilterTest`.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.repositories.BANALServiceRepositoryTest" --tests "com.atrainingtracker.banalservice.sensor.MyAccumulatorSensorTrackingGateTest" --tests "com.atrainingtracker.banalservice.filters.InstantaneousFilterTest"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Full git branch isolation on `feature/ATT-4926`.
