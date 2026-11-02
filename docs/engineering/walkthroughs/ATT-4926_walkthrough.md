# Stage 5: Walkthrough & Verification - ATT-4926: Reset Cockpit Telemetry and Map Breadcrumbs

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

## 1. Executive Summary & Verification Overview

ATT-4926 resolves residual telemetry and map breadcrumb retention upon workout completion and idle app resumption. In `TrainingApplication.java`, `BANALServiceRepository.clearBreadcrumbs()` is invoked during `stopTracking()`, `trackingStopped()`, and `onTrackingAborted()`. In `BANALServiceRepository.kt`, `trackingModeReceiver` clears `_currentTrack` and `_currentPathPoints` whenever entering `TrackingMode.READY`. To fix service binding leaks during idle, `forceUnbindFromBANALService()` was added to `BANALServiceRepository.kt` and integrated into `MainActivityWithNavigation.kt` (`disconnectFromBANALService()`), terminating `BANALService` and lingering timer ticks. In `MyAccumulatorSensor.java`, `increment()` is gated to active tracking (`TrainingApplication.isTracking() && !isPaused() && !isAutoPaused()`), and `reset()` dispatches `mZeroValue` to all registered listeners. In `InstantaneousFilter.java`, `reset()` overrides the base no-op to invalidate cached telemetry on session reset.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-049.1` | `TST-TRK-049.1` | `BANALServiceRepositoryTest` | **PASSED** | `Verified` |
| `REQ-TRK-049.2` | `TST-TRK-049.2` | `BANALServiceRepositoryTest` | **PASSED** | `Verified` |
| `REQ-TRK-049.3` | `TST-TRK-049.3` | `AccumulatorTest` | **PASSED** | `Verified` |
| `REQ-TRK-049.4` | `TST-TRK-049.4` | `AccumulatorTest` & `FilterResetAndPreStartTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.banalservice.sensor.*` (`AccumulatorTest`, `MySensorManagerPriorityTest`, `SensorTypeFormatterTest`)
  - `com.atrainingtracker.banalservice.filters.*` (`FilterResetAndPreStartTest`, `FilterNullSafetyTest`, `SensorFilterDefaultsTest`)
  - `com.atrainingtracker.trainingtracker.repositories.*` (`BANALServiceRepositoryTest`, `KnownLocationsRepositoryTest`)
  - `com.atrainingtracker.trainingtracker.build.*`
* **Execution**: `./gradlew testDebugUnitTest --tests "..."`
* **Result**: **BUILD SUCCESSFUL in 14s** (100% clean-room test pass).
* **ADB On-Device Verification**: `adb devices` reported no attached devices; headless Gradle execution confirmed zero regressions.
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> 0 errors, clean net-new detection (`REQ-TRK-049`).

---

## 4. Invariant & Governance Verification

1. **Pre-Start Live Telemetry (`REQ-FIL-013`)**: Instantaneous rolling filters (HR, cadence, live power) continue updating in `TrackingMode.READY`.
2. **Active Tracking Invariant**: In-progress workout metrics, auto-pause resumption, and manual lap splitting remain completely unaltered.
3. **Idle Battery & Resource Protection**: No running background services or timer ticks while idle in `TrackingMode.READY`.
4. **No Premature FixVersion**: Target Release `V4.9.41` maintained; `fixVersion` reserved for human Ceremony 2 sign-off.
