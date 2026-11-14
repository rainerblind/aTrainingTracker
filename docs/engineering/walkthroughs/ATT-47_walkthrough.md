# Stage 5: Walkthrough & Verification - ATT-47: Indoor GPS suppression & external sensor / IMU rest-variance coupling for stationary coordinate pinning

**Ticket**: [ATT-47](https://rainerblind.atlassian.net/browse/ATT-47)  
**Sub-task**: Pending (`[Test]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.6`  
**Requirement Mapping**: `REQ-TRK-077` (`REQ-TRK-077.1`..`REQ-TRK-077.4`)  
**Test Mapping**: `TST-TRK-077` (`TST-TRK-077.1`..`TST-TRK-077.5`)  
**Branch**: `feat/ATT-47`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-14  

---

## 1. Executive Summary & Verification Overview

Implemented multi-source stationary coordinate pinning coupling in `SpeedAndLocationDevice.java`:
1. **Indoor GPS Suppression (`REQ-TRK-077.1`)**: Maintained `mIsIndoorMode` state (`setIndoorMode(boolean)`). When active, clamps speed to $0.0\text{ m/s}$, pins coordinates to initial fix, and zeroes `delta_distance`, preventing indoor spiderweb polyline drift and false GPS distance accumulation while paired trainer sensors record unimpeded.
2. **External Speed Sensor Standstill Coupling (`REQ-TRK-077.2`)**: Queries `mMySensorManager` for active connected external speed sensors (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, `RUN_SPEED`, `BIKE_POWER`). When updated within $\le 5\text{ s}$ reporting $\le 0.1\text{ m/s}$, blocks unpinning against multipath jumps $> \text{escapeRadius}$. Releases immediately upon physical motion ($> 0.1\text{ m/s}$) or stale data ($> 5\text{ s}$).
3. **Accelerometer Rest-Variance Gating (`REQ-TRK-077.3`)**: Registered `Sensor.TYPE_ACCELEROMETER` at `SENSOR_DELAY_UI` with clean lifecycle teardown. Computes rolling variance $\sigma^2$ of norm $||a|| = \sqrt{a_x^2 + a_y^2 + a_z^2}$ over 1.5–2.0 s. Holds stationary pinning when resting at noise floor ($\sigma^2 \le 0.05\text{ (m/s}^2)^2$); releases within $\le 1.0\text{ s}$ upon physical motion.
4. **Mock Replay & Neutral Fallback (`REQ-TRK-077.4`)**: Preserves 100% equivalence with baseline Doppler gating when external sensors or accelerometer hardware are absent.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-077.1` | `TST-TRK-077.1` | Unit (`SpeedAndLocationDeviceIndoorAndRestCouplingTest`) | **PASSED** | `Verified` |
| `REQ-TRK-077.2` | `TST-TRK-077.2` | Unit (`SpeedAndLocationDeviceIndoorAndRestCouplingTest`) | **PASSED** | `Verified` |
| `REQ-TRK-077.3` | `TST-TRK-077.3` | Unit (`SpeedAndLocationDeviceIndoorAndRestCouplingTest`) | **PASSED** | `Verified` |
| `REQ-TRK-077.4` | `TST-TRK-077.4` | Unit (`SpeedAndLocationDeviceIndoorAndRestCouplingTest` + `SpeedAndLocationDeviceStationaryJitterTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-077.5` | Tier 1 Regression (`--tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*"`) | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Regression Scope (`./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice*"`)
* Scope: `SpeedAndLocationDeviceIndoorAndRestCouplingTest`, `SpeedAndLocationDeviceStationaryJitterTest`, `SpeedAndLocationDeviceDisplacementTest`, `SpeedAndLocationDeviceGoogleFusedTest`, `SpeedAndLocationDevice_ATTFusedTest`, `SpeedAndLocationDeviceResilienceTest`, `SpeedAndLocationDeviceDecayTest`.
* Total tests: All passed with 0 failures, 0 errors, 100% success rate.

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure background sensor fusion and driver telemetry logic; zero APK UI layout changes. Verified via targeted unit tests and lifecycle teardown verification.

### Visual Consistency (Rule 23)
* No UI changes. Pure telemetry driver and sensor manager logic.

---

## 5. Invariant & Governance Verification

1. **Zero Standalone GPS Regressions**: All baseline Doppler tests in `SpeedAndLocationDeviceStationaryJitterTest` passed identically.
2. **Immediate Movement Unpinning**: Standstill lock disengages within $\le 1.0\text{ s}$ upon external motion ($> 0.1\text{ m/s}$) or IMU variance ($\sigma^2 > 0.05$).
3. **No 3D Accelerometer Integration**: Raw accelerometer is strictly utilized for scalar norm variance computation; no 2D/3D integration.
4. **Thread Safety & Lifecycle Hygiene**: Sensor listener cleanly unregistered in `shutDown()` and `LocationUnavailable()`.
5. **Living Documentation Synchronized**: Status in `docs/requirements.md` and `docs/tests.md` updated to `Verified`.

---

## 6. Stage 6 Cold Carbon Review — Bounce 1 Remediation Summary

### Remediation of Finding 1 (Production Wiring & Reachability Defect)
* **Root Cause**: `SpeedAndLocationDevice.setIndoorMode(boolean)` was implemented and tested in unit tests, but had zero production callers in `app/src/main/`.
* **Resolution**:
  1. **`DeviceManager.java`**: Added thread-safe `setIndoorMode(boolean)` and `isIndoorMode()`. Propagates mode to all active `SpeedAndLocationDevice` instances (`mSpeedAndLocationDevice_GPS`, `mSpeedAndLocationDevice_GoogleFused`, `mSpeedAndLocationDevice_ATTFused`) and caches `mIsIndoorMode` so dynamically/lazily created devices immediately inherit indoor mode via `applyIndoorMode()`.
  2. **`BANALService.java`**: Added `setIndoorMode(boolean)`, `isIndoorMode()`, `setStaticIndoorMode(boolean)`, `isStaticIndoorMode()`, and `updateIndoorModeForSportType(long)`. Forwarded from `BANALServiceComm` binder and automatically triggered on `setUserSelectedSportTypeId(sportTypeId)`.
  3. **`SportTypesRepository.kt`**: Added `isIndoorSportType(sportId: Long)` and `@JvmStatic isIndoorName(name: String?)` matching indoor keywords ("indoor", "trainer", "rolle", "laufband", "treadmill", "spinning", "ergometer", "stationary").
  4. **`TrackerService.java`**: Wired indoor mode propagation on workout start (`onStartCommand` checking `EXTRA_INDOOR` or sport type), on sport type change (`onUserSelectedSportTypeChanged`), on BANAL service connection (`mBanalConnection`), and when creating new tracked workouts (`createNewWorkout` persisting `WorkoutSummaries.TRAINER = 1`).
  5. **`WorkoutRepository.kt`**: Updated `createNewTrackedWorkout` and `updateWorkoutSport` with `@JvmOverloads` accepting `isTrainer: Boolean`.
  6. **Targeted Unit Test**: Authored `DeviceManagerIndoorModeTest.kt` verifying `DeviceManager.setIndoorMode(true)` propagation to `SpeedAndLocationDevice_GPS`, GPS coordinate and distance suppression during indoor fixes, dynamic inheritance upon lazy device creation, and sport keyword matching.

### Remediation of Finding 2 (Living Documentation & Governance Parity)
* **Root Cause**: Heading `### TST-TRK-077` in `docs/tests/tst_tracking_cockpit.md` omitted the status marker `[Verified]`, causing the master index in `docs/tests.md` to report 1 test as `Planned / Open`.
* **Resolution**: Added `[Verified]` to `### TST-TRK-077` in `docs/tests/tst_tracking_cockpit.md`. Ran `python3 tools/verify_requirement_governance.py --sync-indexes`, establishing 100% parity (`119 | 119 | 0 | 0`).
