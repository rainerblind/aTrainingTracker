# Stage 1 Problem Domain & Root Cause Analysis: ATT-1631

**Ticket**: [ATT-1631](https://atrainingtracker.atlassian.net/browse/ATT-1631)  
**Sub-task**: [ATT-1679](https://atrainingtracker.atlassian.net/browse/ATT-1679) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*No crashes / Sensor Calibration & Data Integrity*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1631`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Forensic Root Cause Analysis

### 1.1 Observed Anomaly
During workout tracking, the smartphone battery remaining duration estimate (`BATTERY_REMAINING_TIME` in `BatteryDevice.java`) exhibits severe upward drift and abrupt plunge artifacts:
1. **Upward Drift**: While the battery percentage remains constant (e.g. at 97% for 10 minutes), the estimated remaining duration continuously climbs to unrealistic values (e.g. rising from 10:30 h to 15:45 h).
2. **Abrupt Plunge**: The instant the battery drops by 1% (e.g. from 97% to 96%), the remaining time estimate crashes abruptly (e.g. plunging from 15:45 h down to 10:28 h).

### 1.2 Forensic Root Cause in `BatteryDevice.java`
In `BatteryDevice.java`:
```java
public synchronized void onTimeTick(boolean isTracking, boolean isPaused) {
    if (isTracking && !isPaused) {
        mActiveRecordingSeconds++;
        if (mBatteryLevel >= 0) {
            if (mSampleHistory.isEmpty()
                    || (mActiveRecordingSeconds - mSampleHistory.getLast().activeSeconds >= 30)
                    || (mSampleHistory.getLast().percent != mBatteryLevel)) {
                mSampleHistory.addLast(new BatterySample(mActiveRecordingSeconds, mBatteryLevel));
            }
            ...
        }
    }
    updateRemainingTime();
}
```
And inside `updateRemainingTime()`:
```java
BatterySample oldest = mSampleHistory.getFirst();
BatterySample latest = mSampleHistory.getLast();
int deltaSeconds = latest.activeSeconds - oldest.activeSeconds;
int deltaPercent = oldest.percent - latest.percent;
double drainRatePerHour = ((double) deltaPercent) / (deltaSeconds / 3600.0);
double remainingHours = mBatteryLevel / drainRatePerHour;
```
Because `onTimeTick` periodically appends samples every 30 seconds with the *current* percentage, `latest.activeSeconds` grows continuously while `latest.percent` remains frozen at the current level.
Consequently:
- `deltaPercent` ($oldest.percent - latest.percent$) remains constant (e.g. exactly 1%).
- `deltaSeconds` ($latest.activeSeconds - oldest.activeSeconds$) increases monotonically every 30 seconds.
- As a result, $\text{drainRatePerHour} = \frac{\Delta\%}{\Delta t / 3600}$ is artificially driven down towards zero.
- This forces $\text{remainingHours} = \frac{\text{batteryLevel}}{\text{drainRatePerHour}}$ to climb higher and higher without bound!
- When a 1% drop finally occurs, `latest.percent` steps down by 1, doubling `deltaPercent` from 1 to 2 instantaneously, which cuts `remainingHours` in half in a single second.

This discrete sampling aliasing creates an erratic, non-monotonic user experience where the athlete cannot rely on the battery forecast.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors (`PHONE_BATTERY` & `BATTERY_REMAINING_TIME`)*) and `TST-CON-006`.
2. **Historical Origin & Commit Trace**: Commit `809bc479` (Ticket `ATT-1454`, Sprint `2026-40.4`).
3. **Original Design Intent**: `ATT-1454` sought to provide endurance athletes with visibility into host smartphone battery life to prevent unexpected shutdowns during long activities. A periodic 30-second sample buffer was introduced to create a rolling 30-minute window.
4. **Root Cause of Defect in Original Implementation**: The original design treated discrete integer battery percentages as continuous values. In reality, Android battery reports only integer percentages ($1\%$). Measuring time elapsed between periodic samples rather than between discrete battery percentage step-down events causes sampling aliasing.
5. **Preservation of Core Invariants**:
   - `PHONE_BATTERY` reporting (0–100%) and 1Hz update threading MUST remain untouched.
   - Charging status detection (`BatteryManager.BATTERY_STATUS_CHARGING` / `BATTERY_STATUS_FULL` -> `CHARGING_STATUS_CODE` / "Lädt" / "Charging") MUST be preserved.
   - Initial 5-minute stabilization warmup threshold (`MIN_STABILIZATION_SECONDS = 300`) MUST be preserved.
   - Zero background disk I/O, zero wake-locks, and pure in-memory calculation invariants MUST be maintained.

---

## 3. Proposed Architectural Solution

### 3.1 Transition-Triggered Drain Rate Computation
Instead of adding periodic samples at constant percentage, `BatteryDevice` will track battery percentage step transitions:
- Maintain a history of discrete battery level transitions: `BatteryStep(int activeSeconds, int percent)`.
- When tracking starts or resets, record baseline `(0, mBatteryLevel)`.
- A new step is recorded strictly when `mBatteryLevel < lastRecordedLevel` (a true step-down event).
- When a step-down event occurs:
  $$\Delta t = latest.activeSeconds - oldest.activeSeconds$$
  $$\Delta P = oldest.percent - latest.percent$$
  $$\text{drainRatePerHour} = \frac{\Delta P}{\Delta t / 3600.0}$$
  $$\text{mBaseRemainingSeconds} = \text{round}\left(\frac{\text{mBatteryLevel}}{\text{drainRatePerHour}} \times 3600.0\right)$$
  $$\text{mLastStepActiveSeconds} = latest.activeSeconds$$

### 3.2 Continuous Countdown Between Steps
Between battery level drop events (while battery percentage is constant):
- As active recording seconds elapse ($t > mLastStepActiveSeconds$), remaining time counts down second by second:
  $$\text{elapsedSinceStep} = mActiveRecordingSeconds - mLastStepActiveSeconds$$
  $$\text{remainingSeconds} = mBaseRemainingSeconds - elapsedSinceStep$$
- **Lower Bound Floor**: To prevent over-depletion if current percentage lasts significantly longer than the historical average, remaining seconds is clamped to not drop below the floor of the next percentage level:
  $$\text{floorSeconds} = \text{round}\left(\frac{\text{mBatteryLevel} - 1}{\text{drainRatePerHour}} \times 3600.0\right)$$
  $$\text{remainingSeconds} = \max(\text{floorSeconds}, \text{remainingSeconds})$$
  $$\text{remainingSeconds} = \max(0, \text{remainingSeconds})$$

### 3.3 Warmup & Rolling Window Management
- Until active recording time reaches `MIN_STABILIZATION_SECONDS` (300s) AND at least 1% battery drop is measured, the sensor emits `STABILIZING_STATUS_CODE` (`--:--`).
- If 1% drops prior to 300s, stabilization completes once total active recording reaches 300s.
- Transitions older than `mActiveRecordingSeconds - ROLLING_WINDOW_SECONDS` (1800s) are pruned, retaining at least 2 steps for continuous stability.

---

## 4. Invariants to Enforce
1. **Monotonicity**: While battery percentage is constant, `BATTERY_REMAINING_TIME` SHALL count down steadily and NEVER drift upwards.
2. **Smooth Transition Recalibration**: Recalculating drain rate on a new step transition SHALL not produce erratic multi-hour jumps.
3. **Charging & Warmup Compliance**: Status codes for charging (`-1`) and stabilizing (`-2`) remain 100% compliant with `BatteryRemainingTimeFormatter`.
4. **Pause Preservation**: When tracking is paused, active recording seconds do not increment, preserving countdown state without drift.

---

## 5. Scope Bounding & Verification Strategy
- **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/BatteryDevice.java`
- **Test File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/BatteryDeviceTest.kt`
- **Test Scenarios**:
  1. Existing baseline tests (charging, warmup, active drain, zero drain, reset) MUST continue to pass.
  2. New test: Verify that over 10 minutes of constant battery level, remaining time counts down monotonically and does not drift upward.
  3. New test: Verify smooth transition recalibration on subsequent battery drops.
  4. Full regression suite execution (`./gradlew testDebugUnitTest`).
