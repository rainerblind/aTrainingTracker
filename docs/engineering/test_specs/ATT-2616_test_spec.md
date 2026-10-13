# Stage 2: Requirement & Test Specification - ATT-2616: [Fehler] Stop displaying pace and speed if no location is received

**Ticket**: [ATT-2616](https://atrainingtracker.atlassian.net/browse/ATT-2616)  
**Sub-task**: [ATT-2756](https://atrainingtracker.atlassian.net/browse/ATT-2756) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-277](https://atrainingtracker.atlassian.net/browse/ATT-277) (*Recording, Tracking & Cockpit Architecture*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2616`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (`REQ-TRK-013`)

### REQ-TRK-013: GPS Speed Inactivity Watchdog, Progressive Exponential Decay & Pace Display Guard

The GPS speed processing pipeline in `SpeedAndLocationDevice` SHALL monitor continuous location fix telemetry and proactively decay stale speed to zero while suppressing pace computation whenever location updates stall (ATT-2616):

1. **Watchdog Timeout Interval & Frequency**:
   - `SpeedAndLocationDevice` SHALL maintain a 5.0-second inactivity watchdog.
   - If no fresh location fix is received for 5.0 seconds while tracking, the watchdog timer SHALL initiate progressive decay ticks every 1.0 second.
   - The watchdog mechanism SHALL be active only when tracking mode is running and SHALL be cleanly paused/cancelled upon workout pause, stop, or sensor shutdown.

2. **Progressive Exponential Decay Function**:
   - At each 1.0-second decay tick after the 5.0-second threshold, the active instantaneous speed (`mSpeed`) and `mSpeedSensor` value SHALL be scaled by a decay factor of $0.5$ ($50\%$ per second):
     $$v_{k+1} = v_k \times 0.5$$
   - When the decayed speed falls below $0.1\text{ m/s}$ ($\approx 0.36\text{ km/h}$), the speed SHALL immediately clamp strictly to $0.0\text{ m/s}$ ($0.0\text{ km/h}$) and decay ticking SHALL terminate.

3. **Pace Nullification & Zero-Division Immunity**:
   - Pace ($s/\text{m}$) calculation in `mPaceSensor` SHALL be guarded against zero and near-zero speeds.
   - When speed $v \le 0.1\text{ m/s}$ (or $0.0\text{ m/s}$), `mPaceSensor.setValue()` SHALL be set strictly to `null`.
   - Under NO circumstances SHALL `Double.POSITIVE_INFINITY`, `Double.NaN`, or astronomical pace values be emitted to downstream listeners or stored in memory. Cockpit pace UI elements SHALL render the established empty state dashes (`--:--`).

4. **Distance & Track Point Invariance**:
   - Watchdog decay ticks SHALL NOT accumulate phantom distance (`mDistanceSensor` and `mLapDistanceSensor` SHALL NOT increment).
   - Watchdog decay ticks SHALL NOT synthesize artificial GPS track points, inject fake coordinates, or alter GPS accuracy metrics in the database or export streams.

5. **Immediate Telemetry Recovery**:
   - As soon as a fresh, valid GPS location fix is received, any active decay cycle or scheduled watchdog decay tasks SHALL be immediately cancelled.
   - Normal speed smoothing and pace computation SHALL resume seamlessly from the fresh location fix.

6. **Lifecycle & Concurrency Safety**:
   - The inactivity timer and decay scheduler SHALL operate safely on the device's handler/looper or scheduled executor.
   - Invoking `shutDown()`, `pause()`, or switching tracking modes SHALL cancel all pending watchdog tasks, preventing memory leaks, lingering runnable execution, or TOCTOU race conditions.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-TRK-013`) extending `REQ-TRK-001` (*Core Metric Calculations and Sensors*) and `REQ-TRK-002` (*Lap Lifecycle Management*).
2. **Historical Origin & Commit Trace**:
   - `SpeedAndLocationDevice.java` line 211:
     ```java
     mSpeed = (1 - SPEED_SMOOTH_FACTOR) * mSpeed + SPEED_SMOOTH_FACTOR * location.getSpeed();
     mSpeedSensor.setValue(mSpeed);
     if (mSpeed > 0) {
         mPaceSensor.setValue(1.0 / mSpeed);
     }
     ```
   - Speed was strictly updated inside `handleLocation(Location location)`.
3. **Root Reason for Existing Formulation**:
   - Android's `LocationListener` model is push-based (`onLocationChanged`). In normal operation outdoors, GPS fixes arrive at $\sim 1\text{ Hz}$.
   - The original architecture assumed locations would arrive continuously while moving. It did not anticipate modern Android battery management dropping fixes indoors/underground or sudden user stops without GPS hardware reporting $0\text{ m/s}$.
   - Consequently, `mSpeed` and `mPaceSensor` retained their last calculated values indefinitely.
4. **Preservation of Core Invariants**:
   - Exponential moving average smoothing (`SPEED_SMOOTH_FACTOR = 0.5`) during active fix reception is 100% preserved.
   - Raw GPS accuracy filtering (`MAX_ACCURACY_THRESHOLD = 20.0f`) is preserved.
   - Zero phantom distance accumulation is strictly guaranteed.
   - UI formatting conventions (`--:--` for null pace) are preserved without requiring new string resources.

---

### Acceptance Criteria (Given-When-Then)

* **Scenario 1: GPS Inactivity Watchdog Initiates Speed Decay**
  - **Given** an athlete running at $10.0\text{ m/s}$ ($36\text{ km/h}$),
  - **When** entering an underground tunnel where no GPS fixes are received for $5.0$ seconds,
  - **Then** `SpeedAndLocationDevice` SHALL initiate decay ticking every $1.0\text{ s}$, halving the speed successively ($5.0\text{ m/s}$, $2.5\text{ m/s}$, $1.25\text{ m/s}$, $0.625\text{ m/s}$), and clamping strictly to $0.0\text{ m/s}$ once $< 0.1\text{ m/s}$.

* **Scenario 2: Pace Nullification & Zero Division Protection**
  - **Given** an athlete whose GPS signal has ceased and decayed,
  - **When** speed decays to $0.0\text{ m/s}$ (or $< 0.1\text{ m/s}$),
  - **Then** `mPaceSensor.getValue()` SHALL be `null`, preventing division-by-zero, `Infinity`, or `NaN`, and the UI cockpit SHALL display `--:--`.

* **Scenario 3: Zero Phantom Distance Accumulation**
  - **Given** an active decay sequence in progress during GPS signal loss,
  - **When** decay ticks fire and decay speed to zero,
  - **Then** `mDistanceSensor.getValue()` and `mLapDistanceSensor.getValue()` SHALL remain strictly unchanged.

* **Scenario 4: Immediate Telemetry Recovery on Fix Reacquisition**
  - **Given** a device with speed decayed to $0.0\text{ m/s}$ and pace nullified,
  - **When** exiting the tunnel and receiving a fresh GPS fix with speed $5.0\text{ m/s}$,
  - **Then** the watchdog decay SHALL immediately terminate, and active speed and pace SHALL immediately recalculate based on the fresh fix.

* **Scenario 5: Clean Teardown and Shutdown Protection**
  - **Given** a device running with an active watchdog scheduled or decay sequence executing,
  - **When** `device.shutDown()` is invoked,
  - **Then** all watchdog timers and decay tasks SHALL be cancelled immediately with zero memory leaks and zero post-teardown executions.

---

## 2. Test Specification (`TST-TRK-005`)

### TST-TRK-005: GPS Speed Inactivity Watchdog, Progressive Decay & Recovery Verification

1. **Continuous Fix Speed Stability Unit Test (`SpeedAndLocationDeviceDecayTest.kt`)**:
   - Instantiate `SpeedAndLocationDevice`.
   - Feed continuous GPS fixes at 1.0-second intervals with speed $5.0\text{ m/s}$.
   - Verify active speed remains stable and watchdog decay does NOT trigger.

2. **Inactivity Timeout & Progressive Decay Test (`SpeedAndLocationDeviceDecayTest.kt`)**:
   - Feed a location fix with speed $10.0\text{ m/s}$.
   - Advance time by $5.0$ seconds without fixes.
   - Verify tick 1 (at 5.0s) reduces speed to $5.0\text{ m/s}$.
   - Verify tick 2 (at 6.0s) reduces speed to $2.5\text{ m/s}$.
   - Verify tick 3 (at 7.0s) reduces speed to $1.25\text{ m/s}$.
   - Verify tick 4 (at 8.0s) reduces speed to $0.625\text{ m/s}$.
   - Verify subsequent ticks continue until speed drops below $0.1\text{ m/s}$, clamping to $0.0\text{ m/s}$.

3. **Pace Sensor Nullification & Zero-Division Safety Test (`SpeedAndLocationDeviceDecayTest.kt`)**:
   - Verify that when speed decays to $0.0\text{ m/s}$, `mPaceSensor.getValue()` is strictly `null`.
   - Verify that no `Double.POSITIVE_INFINITY` or `Double.NaN` is ever produced.

4. **Distance Invariant Verification (`SpeedAndLocationDeviceDecayTest.kt`)**:
   - Verify that distance sensors (`mDistanceSensor`, `mLapDistanceSensor`) remain constant during decay ticks.

5. **Immediate Recovery on Fresh Location Fix Test (`SpeedAndLocationDeviceDecayTest.kt`)**:
   - After decaying to $0.0\text{ m/s}$, emit a fresh fix with speed $6.0\text{ m/s}$.
   - Verify speed immediately updates, decay cancels, and pace sensor emits valid non-null pace.

6. **Clean Teardown & Lifecycle Verification (`SpeedAndLocationDeviceDecayTest.kt`)**:
   - Trigger `device.shutDown()` during active decay.
   - Verify all watchdog callbacks are cancelled cleanly.

7. **Clean-Room Regression Suite**:
   - Execute full test suite (`./gradlew testDebugUnitTest`) verifying 100% pass rate.

---

## 3. ASPICE Compliance & Verification

- **Requirement Coverage**: `REQ-TRK-013` $\leftrightarrow$ `TST-TRK-005`
- **Living Documentation**: Synchronized in `docs/requirements.md` and `docs/tests.md`.
- **Governance Gate**: `tools/verify_requirement_governance.py` passed cleanly (exit code 0).
- **Localization Parity**: 100% (zero new UI strings required; existing standard null format `--:--` utilized).
