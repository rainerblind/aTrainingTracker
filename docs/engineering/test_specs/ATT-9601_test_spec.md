# Stage 2 Requirement & Test Specification — ATT-9601

- **Ticket**: `ATT-9601` (`2 SP`, `Bug`, `tier-a`, Epic `ATT-166`) | **Target Release**: `V5.0.0` | **Risk Rating**: `HIGH`
- **Reserved IDs**: `REQ-FIL-041` (`[Planned]`), `TST-FIL-041` (`[Planned]`)
- **System Traceability (`SYS.2`/`SYS.3`)**: `Derived (SW-only)` — internal sensor-fusion speed publication and post-pause position-velocity decoupling refining `REQ-FIL-023.1`, `REQ-FIL-036.2`, and `REQ-TRK-056.2` without modifying hardware HALs, SQLite schemas, UI, or cloud APIs.
- **Living-Doc Shards**: `docs/requirements/req_tracking_cockpit_part3.md`, `docs/tests/tst_tracking_cockpit_part2.md`.
- **Localization Parity**: `N/A` — pure sensor-fusion bugfix with zero UI or `strings*.xml` edits.

## 1. Requirements Summary (`REQ-FIL-041` — Priority: `High`, Type: `Functional`, Verification: `Unit`)

| Sub-ID | Normative Contract (SHALL / MUST) |
|---|---|
| `REQ-FIL-041.1` | `SpeedAndLocationDevice` SHALL provide `protected synchronized void publishStationaryZeroSpeed()` that cancels pending watchdog decay, sets `mIsDecaying = false` and `mSpeed = 0.0`, emits `0.0` to `mSpeedSensor` and `null` to `mPaceSensor`, and leaves pinned coordinates (`mLatitudeSensor`/`mLongitudeSensor`/`mStationaryAnchor`), `mPrevLocation`, and distance accumulators (`mDistanceSensor`/`mLapDistanceSensor`) strictly unchanged. `SpeedAndLocationDevice_ATTFused.onLocationChanged()` SHALL invoke `publishStationaryZeroSpeed()` strictly outside `synchronized(ekf)` before returning early when a coarse/rejected GNSS fix (`!gnssAccepted`) coincides with `isStationary || fusedSpeed <= SPEED_ZERO_THRESHOLD` (including pre-anchor coarse stationary fixes). |
| `REQ-FIL-041.2` | `UnicycleOdometryEkf` SHALL track `lastAcceptedMovingGnssTimestampMs` and `postPauseDecoupleRemainingFixes` (reset in `resetGnssTrackingState()`). Executing `applyZeroVelocityUpdate()`, ingesting a stationary GNSS fix (`isStationary == true`), or observing a moving GNSS gap (`outageMs >= POST_PAUSE_DECOUPLE_GAP_MS` or `movingGapMs >= POST_PAUSE_DECOUPLE_GAP_MS`, `5_000L` ms) SHALL arm `postPauseDecoupleRemainingFixes = POST_PAUSE_DECOUPLE_FIXES` (`3`). While `postPauseDecoupleRemainingFixes > 0` on a moving GNSS update (`!isStationary`), `updateGnssPosition()` SHALL decouple position-to-velocity gain (`k[2][0] = k[2][1] = 0.0`) and zero velocity cross-covariances (`decoupleVelocityCovariance(pNew)`), decrementing strictly on accepted inlier fixes (`consecutiveMovingOutliers == 0`), without throttling `updateSpeed()` (Doppler or wheel). |
| `REQ-FIL-041.3` | `SpeedAndLocationDevice` SHALL expose `protected boolean trustsFusedLocationSpeed() { return false; }`, preserving `REQ-TRK-056.2` (`rawSpeed < 0.2 * calcSpeed` geometric fallback) 100% unchanged for `SpeedAndLocationDevice_GPS` and `SpeedAndLocationDevice_GoogleFused`. `SpeedAndLocationDevice_ATTFused` SHALL override `trustsFusedLocationSpeed()` to return `true` so `onNewLocation()` trusts positive EKF-fused `rawSpeed` across post-pause coordinate snaps instead of overriding with `calcSpeed = delta_distance / dt`, while preserving genuine fast starts (`0 -> 9.0 m/s`) and `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC. |

## 2. Traceability Matrix & Verification Plan (`TST-FIL-041`)

| Test ID | Target Test Class(es) | Requirement(s) | Key Verification Procedure |
|---|---|---|---|
| `TST-FIL-041.1` | `SpeedAndLocationDevice_ATTFusedTest.kt` | `REQ-FIL-041.1` | Move at `1.54 m/s`, enter indoor stop (`acc = 45.9 m > 20 m`, `PhoneCarriageMode.STATIONARY`); assert `mSpeedSensor` drops immediately to `0.0 m/s` and `mPaceSensor` to `null` on that exact tick (zero `10–14 s` lag), while coordinates and `mDistanceSensor` add `+0.0 m`; also verify pre-anchor coarse stationary fix publishes `0.0 m/s` cleanly. |
| `TST-FIL-041.2` | `UnicycleOdometryEkfTest.kt`, `SpeedAndLocationDevice_ATTFusedTest.kt` | `REQ-FIL-041.2`, `REQ-FIL-041.3` | Replay `159 s` indoor stop followed by outdoor re-acquisition (`row 228` `acc = 12.75 m`, `row 229` `+19.1 m` snap in `1 s`, `acc = 11.73 m`, Doppler `3.51 m/s`); assert `ekf.speedMps` and published `mSpeedSensor` stay within `<= 1.5 m/s` of `3.51 m/s` (never spiking to `10.85 m/s`), and verify 3-fix decoupling window clears back to coupled mode cleanly on fix 4. |
| `TST-FIL-041.3` | `SpeedAndLocationDevice_ATTFusedTest.kt`, `SpeedAndLocationDeviceStationaryJitterTest.kt`, `ArchitectureConformanceContractTest.kt` | `REQ-FIL-041.2`, `REQ-FIL-041.3` | Verify genuine fast start from standstill to `9.0 m/s` (Doppler `acc <= 10 m` and active wheel sensor) tracks `9.0 m/s` promptly without lag/clamping; verify `SpeedAndLocationDevice_GoogleFused` preserves `REQ-TRK-056.2` damped-speed fallback; verify `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC. |

## 3. Acceptance Criteria (Given-When-Then)

- **Given** `SpeedAndLocationDevice_ATTFused` is moving at `1.54 m/s` and enters an indoor stop (`acc = 45.9 m > 20 m`, `PhoneCarriageMode.STATIONARY`), **When** `onLocationChanged()` rejects the coarse fix while `isStationary == true`, **Then** `mSpeedSensor` immediately publishes `0.0 m/s` and `mPaceSensor` `null` on that tick while pinned coordinates and cumulative distance remain unchanged (`+0.0 m`).
- **Given** a workout resumes after a `159 s` indoor stop with a `+19.1 m` outdoor coordinate snap (`acc = 11.73 m`, Doppler `3.51 m/s`), **When** `SpeedAndLocationDevice_ATTFused` processes the first post-pause fixes, **Then** `SPEED_mps_att_fused` stays within `~1.5 m/s` of `3.51 m/s` and does NOT spike to `10.85 m/s`.
- **Given** a cyclist legitimately accelerates from standstill to `9.0 m/s` with valid outdoor GNSS (`acc <= 10 m`, Doppler `9.0 m/s`) or active wheel sensor, **When** `SpeedAndLocationDevice_ATTFused` processes the start, **Then** `SPEED_mps_att_fused` tracks `9.0 m/s` promptly without artificial clamping or lag.

## 4. Chesterton's Fence & System Invariants

- **Original Requirement ID & Target**: `REQ-TRK-056.2` (`196b3e67`, `ATT-6256`), `REQ-FIL-023.1` (`8b0eba41`, `ATT-8576`), `REQ-FIL-036.2` (`97f49453`, `ATT-9158`) in `SpeedAndLocationDevice.java`, `UnicycleOdometryEkf.kt`, `SpeedAndLocationDevice_ATTFused.kt`.
- **Preservation of Core Invariants**: `REQ-TRK-056.2` untouched for `GPS` & `GoogleFused` (`trustsFusedLocationSpeed() == false`); `MIN_DOPPLER_SPEED_MPS = 3.0`, `ZUPT_SPEED_VARIANCE = 2.0`, `MAX_GNSS_ACCURACY_METERS = 20.0`, `coldStartHelper`, `confirmedPostOutage`, and stationary pinning untouched; lock order `this -> ekf` strictly preserved; `SpeedAndLocationDevice_ATTFused.kt <= 1,500` LoC; `0` DB/UI/`strings*.xml` edits.
