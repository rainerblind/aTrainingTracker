# Stage 1 Analysis: ATT-4283 — [Bug] Seed initial W'bal capacity on CyclingPowerMetricsDevice startup and align W/kg & W'bal behavior when stationary or paused

**Ticket**: [ATT-4283](https://rainerblind.atlassian.net/browse/ATT-4283)  
**Sub-task**: [ATT-4350](https://rainerblind.atlassian.net/browse/ATT-4350) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4283`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During on-device testing and the Sprint Review of `ATT-1296` on a physical Google Pixel 10 (see attached artifact `docs/attachments/w_prime_cockpit.png`), three interrelated discrepancies were observed in the cycling telemetry pipeline involving `CyclingPowerMetricsDevice.java`, `WPrimeBatteryMath.kt`, and `TrackingViewModel.kt`:

1. **Uninitialized $W'$ Anaerobic Capacity at Service Startup**:  
   When `CyclingPowerMetricsDevice` is instantiated by `BANALService` at service startup, its internal battery tracker `mCurrentWPrimeBalJoules` initializes to `null`. Unlike `resetSession()` (which was called in unit tests), the constructors did not invoke `resetSession()` or seed `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0`. Consequently, `mWPrimeBalSensor` emitted `null` on startup, causing the live cockpit tile to display `-- kJ` and `--%` with an empty gauge track until the athlete generated their first active non-paused power sample.

2. **Telemetry Expiration of W/kg During Auto-Pause**:  
   When a workout enters Auto-Pause (`isSuspended == true`, e.g. when an athlete stops at a traffic signal or intersection), `CyclingPowerMetricsDevice.calculateMetrics()` returned early before updating `mPowerToWeightSensor`. Even though the physical power meter remained paired and transmitting `0 W` (causing `Leistung (3 sec Ø)` to display `0 W` and cadence `Freq.` to show `0 /min`), `mPowerToWeightSensor` was completely starved of updates. After 3 seconds, `TimedMovingAverageFilter` timed out and expired to `null`, causing `W/kg (3 sec Ø)` to render `-- W/kg` alongside `0 W`.

3. **Premature Gauge Staleness on Transient Power Sample Dropouts**:  
   If `power` temporarily dropped to `null` during an active session (e.g. a 1-second BLE packet loss or coasting without pedal rotations on event-driven sensors), `CyclingPowerMetricsDevice.calculateMetrics()` (line 365) immediately published `mWPrimeBalSensor.newValue(null)`. In `TrackingViewModel`, receiving `null` immediately bypassed the 3-second stale tolerance (`WPrimeBatteryMath.STALE_TIMEOUT_MS = 3000L`), setting `batteryFraction = null` and causing the battery gauge and numeric balance to violently flash to `-- kJ` / `--%` on a single missed sample.

**Motivation**:  
Restoring visual and behavioral harmony across the live cockpit telemetry grid:
- Deliver immediate visual confidence upon opening the cockpit: a 100% full green battery gauge and configured $W'$ capacity ($wPrimeKj$) from second zero.
- Ensure consistent stationary and paused behavior: when `Leistung (3 sec Ø)` displays `0 W`, `W/kg (3 sec Ø)` displays `0.0 W/kg` (or `0,0 W/kg`) rather than disappearing to `-- W/kg`.
- Harden the telemetry pipeline against transient sensor dropouts: preserve $W'_{\text{bal}}$ and allow `TrackingViewModel`'s 3-second stale timer (`STALE_TIMEOUT_MS`) to govern staleness gracefully without visual flickering.

---

## 2. Root Cause Analysis (Forensic Investigation)

A comprehensive forensic inspection of `CyclingPowerMetricsDevice.java`, `TimedMovingAverageFilter.java`, and `TrackingViewModel.kt` revealed three root causes:

### 2.1 Unseeded Baseline Capacity in Constructors (Rule 51 / REQ-UI-416)
- In `CyclingPowerMetricsDevice.java` (lines 155–178), the primary constructor initializes dependencies, creates the 3-second power filter `cPowerFilter`, registers sensors via `registerSensors()`, and schedules `calculateMetrics()` at 1 Hz.
- However, `resetSession()` (lines 239–276)—which contains the logic to resolve Critical Power and $W'$ capacity and seed `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0`—was **never called** from any constructor overload.
- In production, `BANALService` instantiates `CyclingPowerMetricsDevice` but only invokes `resetSession()` on specific session reset callbacks.
- In unit tests, tests either called `device.resetSession()` explicitly in `@Before` or passed active power samples ($P > CP$) to `calculateMetrics()`, which lazily initialized `mCurrentWPrimeBalJoules = wPrimeJoules` at line 431 on the first valid tick. This masked the bug where a freshly launched service left the W'bal sensor empty on device startup.

### 2.2 Premature Early Return on `isSuspended` in `calculateMetrics()` (REQ-TRK-019)
- Lines 286–312 of `CyclingPowerMetricsDevice.java`:
  ```java
  boolean isSuspended = mRecordingStateProvider.isRecordingSuspended();
  long now = mTimeProvider.currentTimeMillis();

  if (isSuspended) {
      if (!mWasSuspended) {
          mWasSuspended = true;
          mSuspendedStartMs = now;
      }
      if (mCurrentWPrimeBalJoules != null) {
          Integer cp = resolveEffectiveCpWatts();
          float wPrimeKj = mWPrimeKjProvider.getWPrimeKj(mContext);
          if (cp != null && cp > 0 && wPrimeKj > 0.0f) {
              mCurrentWPrimeBalJoules = PowerPhysiologyCalculator.INSTANCE.stepWPrimeBalanceJoules(
                      mCurrentWPrimeBalJoules, 0.0, cp.doubleValue(), wPrimeKj * 1000.0, 1.0);
              if (mWPrimeBalSensor != null) {
                  mWPrimeBalSensor.newValue(mCurrentWPrimeBalJoules / 1000.0);
              }
          }
      }
      if (DEBUG) Log.i(TAG, "calculateMetrics(): recording suspended -> skip");
      return;
  }
  ```
- Because of `return;` at line 311, execution never reached lines 324–351 where `power` is queried and `mPowerToWeightSensor.newValue(wPerKg)` is called.
- In `SensorFilterDefaults.kt`, `SensorType.POWER_TO_WEIGHT` is configured with a 3-second moving average (`TimedMovingAverageFilter`).
- When `mPowerToWeightSensor.newValue(...)` is not called for 3 seconds, `TimedMovingAverageFilter.trimValues()` purges all expired samples. `getFilteredValue()` then returns `null`, causing `W/kg (3 sec Ø)` to display `-- W/kg`.
- In contrast, the hardware sensor device (`ANTBikePowerDevice` or `BTLEBikePowerDevice`) continues pushing `0` to `SensorType.POWER` at 1 Hz when the bike is stopped. Thus, `POWER` maintains `0 W` in its filter while `POWER_TO_WEIGHT` expires to `-- W/kg`.

### 2.3 Destructive Overwriting of `mWPrimeBalSensor` with `null` on Missing Power Samples (REQ-UI-416.2)
- Lines 354–368 of `CyclingPowerMetricsDevice.java`:
  ```java
  if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
      if (mNormalizedPowerSensor != null) mNormalizedPowerSensor.newValue(null);
      if (mIntensityFactorSensor != null) mIntensityFactorSensor.newValue(null);
      if (mTssSensor != null) mTssSensor.newValue(null);
      if (mWPrimeBalSensor != null) mWPrimeBalSensor.newValue(null);
      return;
  }
  ```
- Git archaeology reveals this validation block originated in `ATT-1289` for `POWER_TO_WEIGHT` only. Subsequent commits (`ATT-1290`, `ATT-1291`, `ATT-3862`) simply appended other sensors to the null-publishing block.
- In `TrackingViewModel.kt`, `applySensorData()` passes `numericValue` to `WPrimeBatteryMath.calculateBatteryState()`. When `remainingKj == null`, `calculateBatteryState()` returns `isStale = true, batteryFraction = null, percentage = 0` and cancels `wPrimeStaleJob`.
- This immediately destroys the visual battery gauge upon a single missed packet, bypassing the 3000 ms graceful degradation window (`WPrimeBatteryMath.STALE_TIMEOUT_MS = 3000L`).
- Furthermore, in human exercise physiology, anaerobic battery energy does not vanish when an ANT+/BLE packet is dropped or when the athlete coasts without pedaling; it either holds steady or reconstitutes at 0 W (as implemented for post-workout series in `PowerPhysiologyCalculator.kt` line 388: *"During missing samples after workout start, recharge at 0W"*).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Constructor State Seeding Parity (Rule 51)**: Invoke `resetSession()` (or seed baseline capacity) inside `CyclingPowerMetricsDevice` constructors so `mCurrentWPrimeBalJoules` is seeded with `wPrimeKj * 1000.0` and `mWPrimeBalSensor.newValue((double) wPrimeKj)` is emitted upon device creation when `cp > 0 && wPrimeKj > 0`.
  2. **Stationary & Auto-Pause W/kg Alignment**: Ensure `mPowerToWeightSensor.newValue(...)` is updated during Auto-Pause (`isSuspended == true`). When the power sensor reports 0 W (or while stationary/paused), emit `wPerKg = 0.0 W/kg` so `W/kg (3 sec Ø)` aligns with `Leistung (3 sec Ø)` (`0 W`).
  3. **Transient Dropout Resilience for $W'_{\text{bal}}$**: Eliminate the destructive `mWPrimeBalSensor.newValue(null)` emission in the invalid-power check of `calculateMetrics()`. During transient single-tick dropouts, preserve the balance and allow `TrackingViewModel`'s `wPrimeStaleJob` (`STALE_TIMEOUT_MS = 3000L`) to govern staleness smoothly.
  4. **Cumulative Metrics Integrity**: Ensure session-cumulative metrics (NP, IF, TSS, Mechanical Work) do not emit `null` on transient 1-second power dropouts.
  5. **Unit Test Verification**: Update and expand `CyclingPowerMetricsDeviceTest.kt` to verify:
     - Immediate full capacity emission upon device instantiation.
     - Auto-pause W/kg emission matching 0 W.
     - Resilience of W'bal and cumulative metrics against 1-second power dropouts.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to Skiba differential equations or mathematical curves in `PowerPhysiologyCalculator.kt`.
  2. No modifications to `WPrimeBatteryGaugeBar.kt` UI layout, capsule dimensions, or animation specs.
  3. No modifications to SQLite database schemas or DTO mappers.
  4. No changes to hardware sensor drivers (`ANTBikePowerDevice` or `BTLEBikePowerDevice`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:  
  Refines `REQ-TRK-019.3` (*Decoupled 1Hz Computed Metrics Engine*) and `REQ-UI-416.1` / `REQ-UI-416.2` (*Real-time W' Balance Expenditure and Recovery Tracking*). Targets `CyclingPowerMetricsDevice.java` and `CyclingPowerMetricsDeviceTest.kt`.

* **Historical Origin & Commit Trace**:  
  - `REQ-TRK-019` was introduced in `ATT-1289` (commit `559376e2`, Sprint 2026-42.4).
  - `REQ-UI-416` was introduced in `ATT-1296` (commit `889ebd18`, Sprint 2026-42.10).
  - The test `testCalculateMetrics_recordingSuspended_skipsCalculation()` was written in `ATT-1289` with the initial assumption: *"Value should remain unassigned / null because recording is suspended"*.

* **Root Reason for Existing Formulation**:  
  In `ATT-1289`, pause gating was designed to prevent workout metrics from accumulating while tracking was suspended. The implementer placed `if (isSuspended) return;` at the very beginning of `calculateMetrics()`.  
  However, in a physical bike workout, `SensorType.POWER` is a smoothed sensor metric that continues displaying `0 W` when stopped in Auto-Pause. Leaving `mPowerToWeightSensor` un-updated causes its 3-second moving average filter to purge all samples and expire to `-- W/kg`, clashing with `0 W`.  
  Furthermore, `resetSession()` was assumed to be called at workout start, but `BANALService` never called it upon device construction, leaving initial capacity unseeded.  
  Finally, `mWPrimeBalSensor.newValue(null)` was placed in the invalid-power block, causing transient 1-second BLE packet drops to immediately wipe out the W' battery gauge to `-- kJ` and empty tank, bypassing the 3-second stale tolerance (`WPrimeBatteryMath.STALE_TIMEOUT_MS`).

* **Preservation of Core Invariants**:  
  - Coggan Normalized Power (NP), Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) MUST remain strictly gated by pause and must not accumulate while paused.
  - $W'_{\text{bal}}$ baseline capacity remains strictly equal to `wPrimeKj * 1000.0` Joules when `cp > 0 && wPrimeKj > 0`.
  - Stale timeout remains strictly 3000 ms (`WPrimeBatteryMath.STALE_TIMEOUT_MS`).
  - 100% clean-room test suite pass rate must be preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Constructor Baseline Seeding (Rule 51 Parity)
In `CyclingPowerMetricsDevice.java`:
- Invoke `resetSession()` at the end of the constructor chain (or extract a synchronized helper `seedInitialSessionState()`).
- When `resolveEffectiveCpWatts() > 0 && mWPrimeKjProvider.getWPrimeKj(mContext) > 0.0f`:
  - `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0`
  - `mWPrimeBalSensor.newValue((double) wPrimeKj)`
- This ensures that as soon as `CyclingPowerMetricsDevice` is created by `BANALService`, `mWPrimeBalSensor` publishes the full configured capacity, presenting a 100% green battery gauge from second zero.

### 5.2 Decoupled Pause Gating & Stationary W/kg Alignment
Restructure `calculateMetrics()` in `CyclingPowerMetricsDevice.java` to decouple instantaneous metric updates from cumulative metric integration:
1. **Query Power**: Retrieve 3-second filtered power `powerFiltered`, falling back to raw sensor data if available.
2. **Compute & Publish W/kg**:
   - Retrieve athlete weight `athleteWeightKg`.
   - If `isSuspended` is true:
     - If `athleteWeightKg > 0.0f`: if `power != null && power >= 0.0`, publish `power / athleteWeightKg`; if `power == null` and a power sensor is present, publish `0.0`.
   - If `isSuspended` is false:
     - Publish `(power != null && power >= 0.0 && athleteWeightKg > 0.0f) ? power / athleteWeightKg : null`.
3. **W' Balance Reconstitution During Pause**:
   - If `isSuspended` is true:
     - If `mCurrentWPrimeBalJoules != null`: step W' balance at 0.0 W and publish `mWPrimeBalSensor.newValue(mCurrentWPrimeBalJoules / 1000.0)`.
     - Update pause timestamp tracking (`mWasSuspended`, `mSuspendedStartMs`).
     - Return early to prevent NP / Work / TSS accumulation and FIFO ingestion.
4. **Resumption & FIFO Window Management**:
   - If resuming after `pauseDurationMs > 30000L`, clear `mPowerWindow30s`.
5. **Active Ride Power Validation & Cumulative Metric Gating**:
   - If `power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0`:
     - Do NOT emit `null` to `mWPrimeBalSensor`, `mNormalizedPowerSensor`, `mIntensityFactorSensor`, or `mTssSensor`.
     - During brief dropouts, preserve current session values and allow `TrackingViewModel`'s `wPrimeStaleJob` (`STALE_TIMEOUT_MS = 3000L`) to handle UI staleness.
     - Return early before integrating work or FIFO samples.
6. **Active Ride Calculation (NP, Work, TSS, W'bal)**:
   - Integrate mechanical work, push sample to `mPowerWindow30s`, calculate running NP via Welford's algorithm, calculate IF and TSS, and step W'bal with instantaneous `power`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression**: 100% pass rate across the full test suite (`./gradlew testDebugUnitTest`).
  2. **Pause Accumulation Invariant**: Coggan Normalized Power, Mechanical Work (kJ), and TSS must never accumulate or dilute while tracking is paused or auto-paused.
  3. **Constructor Parity (Rule 51)**: Device initialization must produce the identical baseline state as `resetSession()`.
  4. **Single Atomic Commit (Rule 44)**: No intermediate commits; changes committed as a single atomic unit at Stage 5.
  5. **Parent Ticket Human Gate (Rule 3)**: Parent ticket `ATT-4283` advances synchronously across stages and pauses at `Final Review (Human)`.

* **Risk Rating**: **LOW**  
  *Justification*: The fix is strictly contained within `CyclingPowerMetricsDevice.java` calculation ordering and constructor state seeding. No database schemas, Compose layouts, or BLE/ANT+ protocol drivers are altered. Existing mathematical formulas (`stepWPrimeBalanceJoules`, `calculatePowerToWeight`) remain untouched.
