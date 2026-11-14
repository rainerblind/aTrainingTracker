# Stage 5: Walkthrough & Verification - ATT-7514: SmartBrakeLightDetector: Auto-bypass leak across sequential workouts and stuck brake light on pause in RADAR_ONLY_LIGHT_OFF

**Ticket**: [ATT-7514](https://rainerblind.atlassian.net/browse/ATT-7514)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.6`  
**Requirement Mapping**: `REQ-CON-035` (`REQ-CON-035.5`, `REQ-CON-035.7`)  
**Test Mapping**: `TST-CON-035` (`TST-CON-035.5`, `TST-CON-035.7`, `TST-CON-035.8`)  
**Branch**: `feat/ATT-7514`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

This walkthrough validates the architectural fixes for **ATT-7514** addressing two edge-case regressions identified during Stage 6 Cold Carbon Review:
1. **Sequential Workout Auto-Bypass Retention (`REQ-CON-035.5`)**:
   - Introduced `SmartBrakeLightDetector.resetMotionState()` to reset transient speed, acceleration, and deceleration accumulators upon workout finish while preserving hardware `isAutoBypassed` and user `isEnabled` settings.
   - `RadarLightController.onWorkoutStopped()` now invokes `resetMotionState()`, preventing redundant 50 Hz Android `SensorEventListener` registration across consecutive cycling workouts with connected Garmin Varia RearVue 820 hardware.
2. **Symmetric Pause Restoration from OFF Mode (`REQ-CON-035.7`)**:
   - Removed `preBrakeMode != RadarLightMode.OFF` condition in `RadarLightController.onWorkoutPaused()`.
   - When a cyclist riding with tail light in `OFF` decelerates to a stop triggering auto-pause, `RadarLightController` captures `preBrakeMode` inside `synchronized(this)` and restores `setLightMode(RadarLightMode.OFF)` outside the lock to eliminate deadlock risk and prevent high-intensity brake illumination from stranding ON while paused.
3. **Unit Test Isolation Preserved**:
   - `SmartBrakeLightDetector.reset()` refactored to delegate to `resetMotionState()` and reset `isEnabled = true` and `isAutoBypassed = false`, guaranteeing complete test isolation across unit test suites.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Scope & Test Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-035.5` | `TST-CON-035.5` | Sequential workout auto-bypass persistence (`RadarLightControllerTest.kt`) & motion state reset (`SmartBrakeLightDetectorTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.7` | `TST-CON-035.7` | Symmetric pause restoration from `preBrakeMode == OFF` outside lock (`RadarLightControllerTest.kt`) | **PASSED** | `Verified` |
| `REQ-CON-035.8` | `TST-CON-035.8` | Targeted radar unit test suite (`com.atrainingtracker.banalservice.devices.radar.*`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PRO-001` | Living requirement governance audit (`verify_requirement_governance.py`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Targeted Unit & Regression Test Suite
* **Command**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.radar.*" --max-workers=2`
* **Result**: `BUILD SUCCESSFUL` (All 93 radar unit tests passed: 30 `RadarLightControllerTest`, 12 `SmartBrakeLightDetectorTest`, 12 `RadarTrafficSimulatorTest`, 12 `RadarSnoozeControllerTest`, 8 `SmartRadarLightEngineTest`, 7 `RadarWindowAggregatorTest`, 5 `SimulatedRadarDeviceTest`, 4 `RadarStateRepositoryTest`, 3 `SolarElevationCalculatorTest`; 0 failures, 0 skipped, 0 regressions).

### Translation Parity & Resource Compilation
* **Command**: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --max-workers=2`
* **Result**: `BUILD SUCCESSFUL` (100% translation parity across 9 languages).

### Living Requirement Governance Audit
* **Command**: `python3 tools/verify_requirement_governance.py`
* **Result**: `PASS` (100% parity across 9 domain shards, 0 unmounted islands, zero architecture/system violations).

---

## 4. Invariant & Governance Verification

1. **Battery Conservation**: 50 Hz IMU sensors remain active strictly during active cycling workouts without onboard hardware brake sensors.
2. **Lock Invariant**: Peripheral light mode dispatch in `onWorkoutPaused()` executes strictly outside `synchronized(this)`.
3. **StVZO Anti-Glare Invariant**: StVZO steady clamping and night anti-glare protection remain strictly invariant.
4. **Zero SQLite Migrations**: Changes are purely in-memory lifecycle state adjustments requiring zero database alterations.
