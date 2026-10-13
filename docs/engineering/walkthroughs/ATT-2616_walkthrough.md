# Stage 5 Walkthrough: ATT-2616 - When we don't receive a location update after some time, the speed must go towards zero

**Ticket**: [ATT-2616](https://atrainingtracker.atlassian.net/browse/ATT-2616)  
**Sub-task**: [ATT-2759](https://atrainingtracker.atlassian.net/browse/ATT-2759) (`[Test]`)  
**Parent Epic**: [ATT-281](https://atrainingtracker.atlassian.net/browse/ATT-281) (*Data Sovereignty & Sensor Management*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2616`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary
ATT-2616 resolves an architectural sensor defect in `SpeedAndLocationDevice.java`, where speed and pace remained frozen indefinitely at their last known values whenever an athlete came to a halt (e.g. traffic light, rest stop) or entered a GPS shadow (tunnel, underpass, dense foliage). This occurred because speed and pace were previously computed exclusively inside `onNewLocation(Location location)`, which ceases to be invoked by Android's `LocationManager` when no location changes occur.

Under `REQ-TRK-013` and `TST-TRK-005`:
1. **GPS Inactivity Watchdog ($T_{\text{timeout}} = 5.0\text{ s}$)**: A lightweight `Handler`-based watchdog tracks elapsed realtime since the last valid location fix. When location updates are silent for $\ge 5000\text{ ms}$, the decay sequence is triggered.
2. **Deterministic Exponential Decay ($1.0\text{ s}$ interval)**: Every 1.0 second during inactivity, speed is halved ($mSpeed = mSpeed \times 0.5$). When speed drops below $0.1\text{ m/s}$ (or reaches 0.0), it is clamped strictly to $0.0\text{ m/s}$ and published to `mSpeedSensor`.
3. **Pace Nullification & Zero-Division Safety**: Whenever speed drops below $0.1\text{ m/s}$ or is clamped to $0.0\text{ m/s}$, `mPaceSensor.newValue(null)` is emitted. This renders as clean dashes (`--:--`) in cockpit and wearable displays, preventing any `Double.POSITIVE_INFINITY` or NaN arithmetic issues.
4. **Immediate Recovery on Fresh Fix**: As soon as a valid `Location` fix is received, any pending decay callbacks are cancelled, the watchdog is reset, and active speed smoothing resumes immediately.
5. **Zero Phantom Distance Invariant**: Distance accumulator sensors (`mDistanceSensor`, `mLapDistanceSensor`) are strictly isolated from the decay runnable and accumulate zero distance during decay ticks.
6. **Lifecycle Safety**: `shutDown()` cleanly unregisters all pending callbacks on `mDecayHandler`, preventing memory or handler leaks.
7. **Clean-Room Regression Verification**: 100% pass rate achieved across unit tests in `SpeedAndLocationDeviceDecayTest.kt` and the full clean-room repository unit test suite.

---

## 2. Changes Summary

| Component | Target File | Key Changes |
|:---|:---|:---|
| **Location & Speed Device** | `SpeedAndLocationDevice.java` | Added `mLastLocationTimestampMs`, `INACTIVITY_TIMEOUT_MS` (5000ms), `DECAY_INTERVAL_MS` (1000ms), `MIN_SPEED_THRESHOLD_MPS` (0.1 m/s), `mDecayHandler`, `mDecayRunnable`, and `mIsDecaying`. Implemented watchdog scheduling in `onNewLocation()`, progressive decay logic, pace nullification guard, and teardown cleanup in `shutDown()`. |
| **Sensor Unit Tests** | `SpeedAndLocationDeviceDecayTest.kt` | Added 8 comprehensive unit tests covering: continuous fix stability, 5s timeout & progressive halving, threshold clamping to 0.0 m/s, pace nullification & division-by-zero protection, immediate recovery on fresh fix, zero phantom distance accumulation, clean teardown cancellation, and test looper idle execution. |
| **Living Documentation** | `requirements.md`, `tests.md` | Promoted `REQ-TRK-013` and `TST-TRK-005` to `Verified`. |

---

## 3. Test & Verification Results

### 3.1 Targeted Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.SpeedAndLocationDeviceDecayTest"
```
- Result: **BUILD SUCCESSFUL** in 23s.
- 100% of targeted unit tests in `SpeedAndLocationDeviceDecayTest` passed.

### 3.2 Full Clean-Room Regression Suite
```bash
./gradlew testDebugUnitTest
```
- Result: Executed against `feature/ATT-2616` verifying 100% pass rate with zero regressions across all modules.

---

## 4. Invariants & Non-Functional Requirements
- **Distance Accumulation Neutrality**: Decay ticks never invoke `mDistanceSensor.increment()` or `mLapDistanceSensor.increment()`.
- **Broadcast Isolation**: Watchdog ticks do not broadcast `NEW_LOCATION_INTENT` or mutate latitude, longitude, bearing, or altitude.
- **Null Safety**: No NPEs or division-by-zero arithmetic errors when speed decays or resets.
- **Thread & Lifecycle Safety**: Safe execution on main Looper handler, completely cancelled upon device shutdown.
