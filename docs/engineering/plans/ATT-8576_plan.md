# Stage 3: Implementation Plan - ATT-8576: att_fused Cold GNSS Jump Gating, Standstill Slope Clearing & Low-Speed Slope Damping

**Ticket**: `ATT-8576` (`2 SP`, Epic `ATT-166`) | **Branch**: `feature/ATT-8576` | **Date**: 2026-11-17  
**Requirement**: `REQ-FIL-023` (`REQ-FIL-023.1`..`REQ-FIL-023.3`) | **Test Mapping**: `TST-FIL-023` (`TST-FIL-023.1`..`TST-FIL-023.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **UI Consistency**: `N/A - no UI strings`

---

## 1. Architecture & Layering Design (SWE.2)
- **Odometry EKF (`banalservice.odometry.UnicycleOdometryEkf`)**: Add ZUPT (`applyZeroVelocityUpdate()`) clamping `x[2] = 0.0` & shrinking speed/cross-covariance (`p[2][2]`, `p[0][2]`, `p[1][2]`) and add Mahalanobis innovation gating (`GNSS_MAHALANOBIS_GATE`, `isStationary` guard) in `updateGnssPosition()` so cold convergence jumps cannot teleport `[E, N]` or leak velocity into `x[2]`, while preserving `REQ-FIL-009` moving tunnel-exit re-acquisition.
- **Odometry Driver (`banalservice.devices.SpeedAndLocationDevice_ATTFused`)**: Defer `refLat`/`refLon` anchoring until the first valid fix (`acc <= MAX_GNSS_ACCURACY_METERS = 20.0 m`) without calling `onNewLocation()` on unanchored degraded fixes; ingest speed/ZUPT before `ekf.updateGnssPosition()`; preserve `location.accuracy = max(acc, sqrt(ekf.positionVariance))` so `SpeedAndLocationDevice.onNewLocation()` (`REQ-TRK-056`) never shrinks `escapeRadius` on coarse fixes; reset `refLat/refLon` + `hasValidGnssAnchor` on `shutDown()`/`onAccumulatorsReset()`.
- **Altitude/Slope EKF (`banalservice.odometry.KinematicAltitudeSlopeEkf`)**: Add exponential standstill slope decay toward `0.0` (`STANDSTILL_SLOPE_DECAY_TAU_SEC`), low-speed (`< 1.2 m/s`) slope gain/process damping (`LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2`), and physical grade bounds (`±0.35`, `±35%`), while preserving `@Synchronized` safety, `dh = 0`, `Q_g = 0`, and `REQ-FIL-022` `baroBias`.
- **Altitude/Slope Driver (`banalservice.devices.AltitudeAndSlopeDevice_ATTFused`)**: Pass current speed to `ekf.updateBarometer()` and publish decayed/zeroed `0%` integer slope to `mSlopeSensor` during sustained standstill (`speedMps < minSpeed`) instead of freezing stale pre-stop slope (`ATT-8473`/`ATT-8474` coherence).
- **Threading / Schema / Detailed Design**: Single-thread/synchronized device & EKF state only; zero new threads, DB migrations, UI strings, or `TrackerService`/`BANALService`/`DeviceManager` state changes (`REQ-PRO-114` N/A).

## 2. Atomic Stage 4 Implementation Sequence & Traceability
| Step | Target File(s) | Action & Scope | REQ / TST Mapping |
| :--- | :--- | :--- | :--- |
| **Step 1** | `.../odometry/UnicycleOdometryEkf.kt` | Add `applyZeroVelocityUpdate(timestampMs)` and Mahalanobis/stationary innovation gating (`updateGnssPosition(..., isStationary: Boolean = false)`) protecting `[E, N]` and `x[2]` against cold stationary jumps while accepting moving `REQ-FIL-009` tunnel exits and `testGnssPositionUpdate` (`5 m` @ `3 m`). | `REQ-FIL-023.1`, `TST-FIL-023.1` |
| **Step 2** | `.../devices/SpeedAndLocationDevice_ATTFused.kt` | Track `hasValidGnssAnchor` (cleared in `shutDown()`/`onAccumulatorsReset()`). Defer `refLat`/`refLon` until `acc <= MAX_GNSS_ACCURACY_METERS`; drop unanchored degraded fixes before `onNewLocation()`; apply speed/ZUPT (`isAccelerometerAtRest()`/`isExternalSpeedSensorStationary()`/`speed <= 0.1`) before `updateGnssPosition()`; set `location.accuracy = max(acc, sqrt(ekf.positionVariance)).toFloat()`. | `REQ-FIL-023.1`, `TST-FIL-023.1` |
| **Step 3** | `.../odometry/KinematicAltitudeSlopeEkf.kt` | Add `MAX_PHYSICAL_SLOPE = 0.35`, `LOW_SPEED_SLOPE_THRESHOLD_MPS = 1.2`, and standstill slope decay (`predict()` decays `slope` to `0.0` during standstill while keeping `dh = 0`, `Q_g = 0`, `p22 += qBias*dt`); damp `k1`/`Q_g` smoothly for `speed < 1.2 m/s`; clamp `slope` to `[-MAX_PHYSICAL_SLOPE, MAX_PHYSICAL_SLOPE]`. | `REQ-FIL-023.2`, `REQ-FIL-023.3`, `TST-FIL-023.2`, `TST-FIL-023.3` |
| **Step 4** | `.../devices/AltitudeAndSlopeDevice_ATTFused.kt` | Pass `speed` to `ekf.updateBarometer(slewAlt, isStandstill = isStandstill, speedMps = speed)`; in `publishAltitudeAndMetrics(speedMps)`, publish `Math.round(ekf.slopePercent).toInt()` (`0%` once standstill decay clears slope, or `0` after sustained `speedMps < minSpeed`) instead of leaving pre-stop slope frozen. | `REQ-FIL-023.2`, `REQ-FIL-023.3`, `TST-FIL-023.2`, `TST-FIL-023.3` |
| **Step 5** | `SpeedAndLocationDevice_ATTFusedTest.kt`, `UnicycleOdometryEkfTest.kt`, `AltitudeAndSlopeDevice_ATTFusedTest.kt`, `KinematicAltitudeSlopeEkfTest.kt` | Add behavioral unit tests (`REQ-PRO-049.1`, zero `src/main` reads): (a) `25.1 m -> 16.0 m` (`25.2 m` jump, `0 m/s`) start spike suppression & accuracy preservation + tunnel exit; (b) `-21%` pre-stop slope clearing to `0%` on sustained stop with preserved `baroBias`; (c) `0.85 m/s` flat roll-away with `±0.35 m` 5-Hz baro noise (`ASCENT` & `SLOPE` protection) + `+8%` climb at `5.0 m/s`. | `TST-FIL-023.1`..`TST-FIL-023.3` |
| **Step 6** | Symlinks & Gradle Verification | Verify `ANT-Android-SDKs` untouched + symlinks, run targeted `./gradlew :app:testDebugUnitTest` via `parallel_sprint_lock.py`, and run `verify_requirement_governance.py`. | `TST-FIL-023.4` |

## 3. System Invariants & Constraints
1. **Base-Class & Dead-Reckoning Invariants**: `SpeedAndLocationDevice.java` (`REQ-TRK-048`, `REQ-TRK-056`, `REQ-TRK-074`, `REQ-TRK-077`) stays untouched; `REQ-FIL-009` tunnel-exit re-acquisition remains intact.
2. **Altitude/Slope & ERTS Invariants**: `REQ-FIL-020.1`/`.2` (DEM precedence, `2.0 m` hysteresis), `REQ-FIL-022` (3-state `baroBias` & 2-state `p00 = varBaro`), `@Synchronized` safety, and `ExtendedRts*Smoother.kt` remain untouched.
3. **Scope & Submodule Invariants**: `ANT-Android-SDKs` remains at `0e8cea714a231fb9565170d4ed01eafa868f9798`; `UI Consistency: N/A - no UI strings`.

## 4. Verification & Rollback Plan
- **Verification**: `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --tests "*.SpeedAndLocationDevice_ATTFusedTest" --tests "*.UnicycleOdometryEkfTest" --tests "*.AltitudeAndSlopeDevice_ATTFusedTest" --tests "*.KinematicAltitudeSlopeEkfTest" --tests "*.SpeedAndLocationDeviceStationaryJitterTest" --tests "*.OdometrySimulationTest"` and `python3 tools/verify_requirement_governance.py`.
- **Rollback**: Isolated to 4 `banalservice` Kotlin files and 4 unit test files; cleanly revertible via `git checkout`.

## 5. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: Net-new `REQ-FIL-023` refining `REQ-FIL-007`, `REQ-SEN-028.3`, `REQ-FIL-016.1`/`.2`, and `REQ-FIL-022`.
- **Historical Origin & Commit Trace**: `bc4a4109` (`ATT-131`), `75c1497e` (`ATT-6279`), `8b0eba41` (`ATT-8081`), `e9766625` (`ATT-8575`), and `196b3e67` (`ATT-6256` / `REQ-TRK-056`).
- **Root Reason for Existing Formulation**: Initial fixes were assumed `<= 20 m` and moving; `ATT-6279` froze `mSlopeSensor` below `minSpeed` and kept `ekf.slope` undamped above `0.3 m/s`.
- **Preservation of Core Invariants**: `REQ-TRK-056`, `REQ-FIL-009`, `REQ-FIL-020`, `REQ-FIL-022`, `@Synchronized` safety, and `ExtendedRts*Smoother` remain 100% preserved.
