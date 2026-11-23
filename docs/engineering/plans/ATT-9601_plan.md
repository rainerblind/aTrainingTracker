# Stage 3 Implementation Plan — ATT-9601: Stationary Zero-Speed Publication & Post-Pause Velocity Spike Bounding

- **Ticket**: `ATT-9601` (`2 SP`, `Bug`, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Traceability**: `REQ-FIL-041` (`REQ-FIL-041.1`–`.3`) <-> `TST-FIL-041` (`TST-FIL-041.1`–`.3`) | `Derived (SW-only)` (refines `REQ-FIL-023.1`, `REQ-FIL-036.2`, `REQ-TRK-056.2`)
- **Database / UI / Monolith Impact**: `0` SQLite changes; `0` UI/`strings*.xml` edits (`Localization Parity: N/A`); `SpeedAndLocationDevice_ATTFused.kt` stays strictly `<= 1,498 / 1,500` LoC (`+2` net lines); `SpeedAndLocationDevice.java` `~860 / 1,500` LoC; `UnicycleOdometryEkf.kt` `~875 / 1,500` LoC; `0` edits to `TrackerService.java` or `WorkoutRepository.kt`.

## 1. SWE.2 Architectural Decomposition & Threading Model

- **Base-Class Hooks (`SpeedAndLocationDevice.java`, `841 -> ~860` LoC)**:
  - Add `protected synchronized void publishStationaryZeroSpeed()`: cancels pending watchdog decay (`cancelWatchdog()`), resets `mIsDecaying = false` and `mSpeed = 0.0`, and publishes `0.0` to `mSpeedSensor` and `null` to `mPaceSensor` without touching `mLatitudeSensor`, `mLongitudeSensor`, `mStationaryAnchor`, `mPrevLocation`, `mDistanceSensor`, or `mLapDistanceSensor`.
  - Add `protected boolean trustsFusedLocationSpeed() { return false; }` (mirroring `shouldReleaseColdStartStationaryPin()` & `recordsRawLocationDirectly()`), and guard the `REQ-TRK-056.2` damped-speed geometric fallback at lines `675`, `719`, and `738` with `(trustsFusedLocationSpeed() || calcSpeed <= 1.0 || rawSpeed >= 0.2 * calcSpeed)`. Preserves `GPS` and `GoogleFused` 100% unchanged while preventing `ATT_FUSED`'s fused speed (`3.51 m/s`) from being replaced by `calcSpeed` (`19.09 m/s`) across post-pause coordinate snaps.
- **Post-Pause & Post-Standstill EKF Velocity Decoupling (`UnicycleOdometryEkf.kt`, `850 -> ~875` LoC)**:
  - Add `POST_PAUSE_DECOUPLE_GAP_MS = 5_000L` and `POST_PAUSE_DECOUPLE_FIXES = 3`.
  - Track `lastAcceptedMovingGnssTimestampMs: Long = 0L` and `postPauseDecoupleRemainingFixes: Int = 0` (cleared in `resetGnssTrackingState()`).
  - Arm `postPauseDecoupleRemainingFixes = POST_PAUSE_DECOUPLE_FIXES` on: (a) `applyZeroVelocityUpdate()`, (b) any `updateGnssPosition(..., isStationary = true)` call, and (c) any moving `updateGnssPosition()` where `outageMs >= POST_PAUSE_DECOUPLE_GAP_MS` or `movingGapMs >= POST_PAUSE_DECOUPLE_GAP_MS` (guarded against backward timestamps).
  - While `postPauseDecoupleRemainingFixes > 0` on `!isStationary`, set `decoupleSpeedUpdate = true` (`k[2][0] = k[2][1] = 0.0`, `decoupleVelocityCovariance(pNew)`), preserving exact SPD covariance and zero interference with `updateSpeed()` (Doppler/wheel). Decrement `postPauseDecoupleRemainingFixes` and advance `lastAcceptedMovingGnssTimestampMs` strictly on accepted moving inliers (`!isStationary && consecutiveMovingOutliers == 0`).
- **Lock-Safe Integration (`SpeedAndLocationDevice_ATTFused.kt`, `1,495 -> 1,497 / 1,500` LoC)**:
  - Override `override fun trustsFusedLocationSpeed(): Boolean = true` (`+1` line).
  - Refactor pre-anchor coarse rejection (`L552-568`, `+0` net lines) so `if (isStationary) publishStationaryZeroSpeed()` executes strictly **outside** `synchronized(ekf)` (preserving strict lock order `this -> ekf` with zero AB-BA deadlock risk).
  - Invoke `publishStationaryZeroSpeed()` before the early return at `L626-628` (`+1` line) when `!gnssAccepted && (isStationary || fusedSpeed <= SPEED_ZERO_THRESHOLD)`.

## 2. Atomic Implementation Steps

| Step | Target File(s) | Atomic Change | Req / Test |
|---|---|---|---|
| **1** | `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice.java` | Add `publishStationaryZeroSpeed()` and `trustsFusedLocationSpeed()` (`default false`), guarding lines `675`, `719`, `738` damped-speed override without altering `GPS` / `GoogleFused`. | `REQ-FIL-041.1`, `.3` / `TST-FIL-041.1`, `.3` |
| **2** | `app/src/main/java/com/atrainingtracker/banalservice/odometry/UnicycleOdometryEkf.kt` | Add `POST_PAUSE_DECOUPLE_GAP_MS = 5_000L`, `POST_PAUSE_DECOUPLE_FIXES = 3`, `lastAcceptedMovingGnssTimestampMs`, and `postPauseDecoupleRemainingFixes` arm/decouple/decrement logic with backward-timestamp guard and `resetGnssTrackingState()` cleanup. | `REQ-FIL-041.2` / `TST-FIL-041.2` |
| **3** | `app/src/main/java/com/atrainingtracker/banalservice/devices/SpeedAndLocationDevice_ATTFused.kt` | Override `trustsFusedLocationSpeed() = true`, call `publishStationaryZeroSpeed()` outside `synchronized(ekf)` on pre-anchor coarse stationary rejection (`L554-559`) and post-anchor rejected stationary/zero-speed return (`L626-628`), keeping file strictly `<= 1,500` LoC (`1,497`). | `REQ-FIL-041.1`, `.3` / `TST-FIL-041.1`–`.3` |
| **4** | `UnicycleOdometryEkfTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt` | Add `TST-FIL-041.1`–`.3`: immediate indoor `acc = 45.9 m` zero-speed publication & `+0.0 m` distance; `159 s` bakery pause + `+19.1 m` outdoor snap (`3.51 m/s` Doppler, zero `10.85 m/s` spike) + 3-fix decoupling expiry; genuine fast start (`0 -> 9.0 m/s` Doppler & wheel); verify `SpeedAndLocationDeviceStationaryJitterTest`, `SpeedAndLocationDeviceDecayTest`, `ExtendedRtsOdometrySmootherTest`, and `ArchitectureConformanceContractTest`. | `TST-FIL-041.1`–`.3` |

## 3. UI Consistency, Invariants & Verification Command

- **UI Consistency (`docs/design_guidelines.md`)**: `N/A` — zero UI, layout, or `strings*.xml` edits.
- **Invariants**: `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC; lock order `this -> ekf` preserved; `REQ-TRK-056.2` untouched for `GPS` & `GoogleFused`; `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, `MAX_GNSS_ACCURACY_METERS = 20.0`, `coldStartHelper`, and `ExtendedRtsOdometrySmoother` preserved; `ANT-Android-SDKs` intact (`160000`).
- **Stage 4 Verification Command**: `./gradlew :app:testDebugUnitTest --rerun --tests "com.atrainingtracker.banalservice.odometry.UnicycleOdometryEkfTest" --tests "com.atrainingtracker.banalservice.odometry.AttFusedColdStartHelperTest" --tests "com.atrainingtracker.banalservice.odometry.ExtendedRtsOdometrySmootherTest" --tests "com.atrainingtracker.banalservice.odometry.OdometrySimulationTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDevice_ATTFusedTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceStationaryJitterTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceDecayTest" --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceIndoorAndRestCouplingTest" --tests "com.atrainingtracker.banalservice.replay.*" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest"`
