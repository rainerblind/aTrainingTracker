# Stage 3 Implementation Plan - ATT-2616: [Fehler] Stop displaying pace and speed if no location is received

**Ticket**: [ATT-2616](https://atrainingtracker.atlassian.net/browse/ATT-2616)  
**Sub-task**: [ATT-2757](https://atrainingtracker.atlassian.net/browse/ATT-2757) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-277](https://atrainingtracker.atlassian.net/browse/ATT-277) (*Recording, Tracking & Cockpit Architecture*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.3`  
**Branch**: `feature/ATT-2616`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary & Problem Domain

### Problem Domain
In `SpeedAndLocationDevice.java`, speed (`mSpeed`) and pace (`mPaceSensor`) are updated strictly when `onNewLocation(Location location)` is invoked by the underlying location provider.
When an athlete stops (e.g. at traffic lights, rest stop) or enters GPS shadow (e.g. tunnels, dense urban canyons, dense tree cover), the Android `LocationManager` ceases emitting location fixes. Because no inactivity watchdog or decay mechanism exists:
1. `mSpeed` and `mSpeedSensor` remain frozen indefinitely at the last recorded speed (e.g. $25\text{ km/h}$).
2. `mPaceSensor` remains frozen at the last recorded pace.
3. In edge cases where `mSpeed == 0.0`, computing `1 / mSpeed` emits `Double.POSITIVE_INFINITY` to sensor listeners.
4. Downstream cockpit tiles and tracking summaries display misleading live metrics while the athlete is stationary.

### Proposed Architecture
Implement an inactivity watchdog and progressive exponential decay mechanism inside `SpeedAndLocationDevice.java`:
1. **Watchdog Timer**:
   - Maintain a 5.0-second inactivity timer (`INACTIVITY_TIMEOUT_MS = 5000`).
   - Reschedule or reset the watchdog upon every valid location fix with accuracy $\le \text{threshold}$.
2. **Progressive Decay**:
   - If no valid location arrives for 5.0 seconds, initiate decay ticks every 1.0 second (`DECAY_INTERVAL_MS = 1000`).
   - At each tick, halve the active speed: $mSpeed = mSpeed \times 0.5$.
   - When $mSpeed \le 0.1\text{ m/s}$ ($\approx 0.36\text{ km/h}$), clamp $mSpeed$ to strictly $0.0\text{ m/s}$ and terminate decay ticks.
   - Update `mSpeedSensor.newValue(mSpeed)`.
3. **Pace Nullification & Zero-Division Immunity**:
   - When $mSpeed > 0.1\text{ m/s}$, update `mPaceSensor.newValue(1.0 / mSpeed)`.
   - When $mSpeed \le 0.1\text{ m/s}$, set `mPaceSensor.newValue(null)`. Cockpit tiles cleanly display `--:--`.
4. **Distance & Track Point Immunity**:
   - Decay ticks do NOT update accumulators (`mDistanceSensor`, `mLapDistanceSensor`), do NOT update `mPrevLocation`, and do NOT broadcast `NEW_LOCATION_INTENT`.
5. **Immediate Recovery on New Fix**:
   - Fresh location fixes cancel pending decay tasks and restore normal smoothed speed and pace.
6. **Lifecycle Safety**:
   - Cancel all scheduled callbacks on `shutDown()` and `onAccumulatorsReset()`.

---

## 2. Technical Architecture & Component Mapping (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        SpeedAndLocationDevice                          │
│                                                                        │
│  Fields:                                                               │
│  - INACTIVITY_TIMEOUT_MS = 5000L                                       │
│  - DECAY_INTERVAL_MS = 1000L                                           │
│  - DECAY_FACTOR = 0.5                                                  │
│  - SPEED_ZERO_THRESHOLD = 0.1                                          │
│  - mWatchdogHandler: Handler                                           │
│  - mInactivityRunnable: Runnable                                       │
│  - mDecayRunnable: Runnable                                            │
│                                                                        │
│  Lifecycle / Handlers:                                                 │
│  + onNewLocation(Location location):                                   │
│      resetWatchdog() -> schedule inactivity after 5.0s                  │
│      mSpeed = (mSpeed + speed) / 2                                     │
│      mSpeedSensor.newValue(mSpeed)                                     │
│      mPaceSensor.newValue(mSpeed > 0.1 ? 1/mSpeed : null)              │
│                                                                        │
│  + onInactivityTimeout():                                              │
│      triggerDecayTick()                                                │
│                                                                        │
│  + triggerDecayTick():                                                 │
│      mSpeed = mSpeed * 0.5                                             │
│      if (mSpeed <= 0.1) { mSpeed = 0.0; stopDecay(); }                 │
│      else { scheduleNextDecayTick(1000ms); }                           │
│      mSpeedSensor.newValue(mSpeed)                                     │
│      mPaceSensor.newValue(mSpeed > 0.1 ? 1/mSpeed : null)              │
│                                                                        │
│  + shutDown() / onAccumulatorsReset():                                 │
│      cancelWatchdogAndDecay()                                          │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ notifies
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                             MySensor                                   │
│  - mSpeedSensor: publishes Double (m/s) -> Cockpit format (km/h)       │
│  - mPaceSensor: publishes Double (s/m) or null -> Cockpit ("--:--")    │
│  - mDistanceSensor: unchanged (0 phantom distance)                     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Step-by-Step Implementation Sequence

### Step 1: Add Inactivity Watchdog Constants & State to `SpeedAndLocationDevice.java`
- Declare:
  - `public static final long INACTIVITY_TIMEOUT_MS = 5000L;`
  - `public static final long DECAY_INTERVAL_MS = 1000L;`
  - `public static final double DECAY_FACTOR = 0.5;`
  - `public static final double SPEED_ZERO_THRESHOLD = 0.1;`
  - `protected Handler mWatchdogHandler;`
  - `private final Runnable mInactivityRunnable`
  - `private final Runnable mDecayRunnable`

### Step 2: Implement Watchdog & Progressive Decay Methods
- Implement `scheduleWatchdog()`, `cancelWatchdog()`, and `triggerDecayTick()`.
- Ensure `getWatchdogHandler()` lazily initializes from `mContext.getMainLooper()` (with null check for headless unit tests).
- Add `@VisibleForTesting public void setWatchdogHandler(Handler handler)`.
- Add `@VisibleForTesting public void triggerInactivityTimeoutForTesting()`.
- Add `@VisibleForTesting public void triggerDecayTickForTesting()`.

### Step 3: Wire `onNewLocation()`, `LocationUnavailable()`, `shutDown()`, and `onAccumulatorsReset()`
- In `onNewLocation(Location location)`:
  - Cancel any active decay.
  - Reset / reschedule inactivity watchdog for `INACTIVITY_TIMEOUT_MS`.
  - Update `mSpeed` and guard pace calculation:
    `mPaceSensor.newValue(mSpeed > SPEED_ZERO_THRESHOLD ? 1.0 / mSpeed : null)`.
- In `LocationUnavailable()`:
  - Cancel watchdog and immediately trigger decay if speed > 0.
- In `shutDown()` and `onAccumulatorsReset()`:
  - Cancel all pending watchdog and decay callbacks.
  - Reset speed to 0.0 and pace to null on reset.

### Step 4: Unit Test Suite Construction (`SpeedAndLocationDeviceDecayTest.kt`)
- Create unit test class covering:
  1. Continuous fix reception maintains stable speed and does not trigger decay.
  2. 5.0-second timeout initiates decay.
  3. Progressive halving: $10.0 \to 5.0 \to 2.5 \to 1.25 \to 0.625 \to 0.0\text{ m/s}$.
  4. Pace nullification when speed drops to or below $0.1\text{ m/s}$. Zero `Infinity` or `NaN`.
  5. Distance sensors (`mDistanceSensor`, `mLapDistanceSensor`) remain strictly unchanged.
  6. Reacquisition of GPS fix immediately recovers active speed and cancels decay.
  7. `shutDown()` cleans up handler callbacks without leaks.

---

## 4. Invariants, Non-Negotiables & UI Consistency (Rule 23)

### Non-Negotiable Invariants
1. **Zero Phantom Distance**: Decay ticks MUST NOT increment `mDistanceSensor` or `mLapDistanceSensor`.
2. **Zero Division & NaN Immunity**: `mPaceSensor` MUST never receive `Double.POSITIVE_INFINITY` or `Double.NaN`.
3. **Smooth Exponential Averaging**: Active fixes continue to use existing `mSpeed = (mSpeed + speed) / 2` formula.
4. **Thread Safety**: All handler executions and state transitions occur on the main looper or specified test looper.
5. **No Memory Leaks**: All pending runnables are purged in `shutDown()`.

### UI Consistency (Rule 23)
* **Status**: N/A - Non-UI ticket.
* ATT-2616 modifies internal sensor telemetry processing in `SpeedAndLocationDevice.java`.
* Cockpit tiles and UI elements already handle `null` pace (displaying `--:--`) and `0.0` speed.
* Zero new UI components, colors, themes, or layouts are added.

---

## 5. Test Strategy & Traceability Matrix (SWE.4 / SWE.5)

| Requirement | Test Identifier | Test Method / Verification Class | Target Pass |
|---|---|---|---|
| `REQ-TRK-013` (Timeout & Decay) | `TST-TRK-005` (Sec 1, 2) | `SpeedAndLocationDeviceDecayTest.testInactivityTimeout_triggersExponentialDecay` | PASS |
| `REQ-TRK-013` (Pace Nullification) | `TST-TRK-005` (Sec 3) | `SpeedAndLocationDeviceDecayTest.testDecayedSpeed_nullifiesPaceWithoutInfinity` | PASS |
| `REQ-TRK-013` (Distance Invariant) | `TST-TRK-005` (Sec 4) | `SpeedAndLocationDeviceDecayTest.testDecayTicks_doNotAccumulateDistance` | PASS |
| `REQ-TRK-013` (Fix Reacquisition) | `TST-TRK-005` (Sec 5) | `SpeedAndLocationDeviceDecayTest.testFreshLocationFix_immediatelyRecoversSpeed` | PASS |
| `REQ-TRK-013` (Teardown Safety) | `TST-TRK-005` (Sec 6) | `SpeedAndLocationDeviceDecayTest.testShutDown_cancelsAllWatchdogCallbacks` | PASS |
| Full Regression Suite | All Suites | `./gradlew testDebugUnitTest` | 100% PASS |

---

## 6. Rollback & Contingency Plan
* All changes are isolated within `SpeedAndLocationDevice.java` and new unit test `SpeedAndLocationDeviceDecayTest.kt`.
* In case of regression or unexpected behavior, reverting the feature commit on `feature/ATT-2616` cleanly restores previous behavior with zero database schema migrations or stateful storage implications.
