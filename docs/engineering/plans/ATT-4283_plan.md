# Stage 3: Implementation Plan - ATT-4283: [Bug] Seed initial W'bal capacity on CyclingPowerMetricsDevice startup and align W/kg & W'bal behavior when stationary or paused

**Ticket**: [ATT-4283](https://rainerblind.atlassian.net/browse/ATT-4283)  
**Sub-task**: [ATT-4352](https://rainerblind.atlassian.net/browse/ATT-4352) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-TRK-038` (`REQ-TRK-038.1`..`REQ-TRK-038.5`)  
**Test Mapping**: `TST-TRK-031` (`TST-TRK-031.1`..`TST-TRK-031.8`)  
**Branch**: `feature/ATT-4283`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

During live physical testing and Sprint Review 2026-42.10 on a Google Pixel 10 (see `docs/attachments/w_prime_cockpit.png`), three interrelated telemetry anomalies were identified in the cycling telemetry engine (`CyclingPowerMetricsDevice.java`, `WPrimeBatteryMath.kt`, `TrackingViewModel.kt`):

1. **Uninitialized $W'$ Anaerobic Capacity at Service Startup**:  
   When `CyclingPowerMetricsDevice` was instantiated by `BANALService` at service startup, its internal battery tracker `mCurrentWPrimeBalJoules` initialized to `null`. Unlike `resetSession()` (which was called in unit tests), the constructors did not invoke `resetSession()` or seed `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0`. Consequently, `mWPrimeBalSensor` emitted `null` on startup, causing the live cockpit tile to display `-- kJ` and `--%` with an empty gauge track until the athlete generated their first active non-paused power sample.

2. **Telemetry Expiration of W/kg During Auto-Pause**:  
   When a workout enters Auto-Pause (`isSuspended == true`, e.g. when an athlete stops at a traffic signal or intersection), `CyclingPowerMetricsDevice.calculateMetrics()` returned early before updating `mPowerToWeightSensor`. Even though the physical power meter remained paired and transmitting `0 W` (causing `Leistung (3 sec Ø)` to display `0 W` and cadence `Freq.` to show `0 /min`), `mPowerToWeightSensor` was completely starved of updates. After 3 seconds, `TimedMovingAverageFilter` timed out and expired to `null`, causing `W/kg (3 sec Ø)` to render `-- W/kg` alongside `0 W`.

3. **Premature Gauge Staleness on Transient Power Sample Dropouts**:  
   If `power` temporarily dropped to `null` during an active session (e.g. a 1-second BLE packet loss or coasting without pedal rotations on event-driven sensors), `CyclingPowerMetricsDevice.calculateMetrics()` immediately published `mWPrimeBalSensor.newValue(null)`. In `TrackingViewModel`, receiving `null` immediately bypassed the 3-second stale tolerance (`WPrimeBatteryMath.STALE_TIMEOUT_MS = 3000L`), setting `batteryFraction = null` and causing the battery gauge and numeric balance to violently flash to `-- kJ` / `--%` on a single missed sample.

The goal of this implementation plan is to define the exact, order-dependent code construction steps in `CyclingPowerMetricsDevice.java` and `CyclingPowerMetricsDeviceTest.kt` to resolve all three issues while preserving all system invariants (pause accumulation gating, mathematical formulas, and zero regressions).

---

## 2. Traceability & Requirements Mapping

This implementation plan establishes 1:1 bidirectional traceability between requirements (`REQ-TRK-038`), test cases (`TST-TRK-031`), and atomic construction steps:

| Requirement Clause | Description | Target Test Case | Target Implementation Step |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-038.1` | **Constructor State Seeding Parity (Rule 51)**: Invoke `resetSession()` upon device creation to seed $W'_{\text{bal}}$ capacity (`wPrimeKj * 1000.0` Joules) and immediately publish full capacity via `mWPrimeBalSensor.newValue((double) wPrimeKj)` from second zero when $CP > 0$ and $W' > 0$. | `TST-TRK-031.1`, `TST-TRK-031.2` | Step 1 |
| `REQ-TRK-038.2` | **Stationary & Paused W/kg Alignment**: Decouple power query and W/kg emission from pause gating. When `isSuspended == true`, compute and emit `mPowerToWeightSensor.newValue(...)` (publishing `0.0` or `power / weight`), feeding `TimedMovingAverageFilter` and preventing `-- W/kg` expiration. | `TST-TRK-031.3`, `TST-TRK-031.4`, `TST-TRK-031.6` | Step 2 |
| `REQ-TRK-038.3` | **Transient Dropout Resilience for W'bal & Cumulative Metrics**: In `calculateMetrics()`, do not wipe sensors to `null` on missing/invalid power (`power == null || power < 0`). Return early while preserving session metrics so `TrackingViewModel`'s 3-second `wPrimeStaleJob` (`STALE_TIMEOUT_MS = 3000L`) governs staleness gracefully. | `TST-TRK-031.5` | Step 3 |
| `REQ-TRK-038.4` | **Cumulative Metric Pause Gating Invariant**: Normalized Power (NP), Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) remain strictly paused and do NOT integrate or accumulate during `isSuspended == true`. $W'_{\text{bal}}$ reconstitutes exponentially at 0W. | `TST-TRK-031.3`, `TST-TRK-031.4` | Step 2 |
| `REQ-TRK-038.5` | **9-Language Localization & Format Parity**: Ensure 100% translation coverage, positional specifier integrity (`%1$d%%`), and zero missing resources across all 9 canonical app locales without introducing redundant strings. | `TST-TRK-031.7` | Step 5 |

---

## 3. System Invariants & Preserved Behavior

1. **Constructor State Seeding Parity with `resetSession()` (Rule 51 / Coding Standards Sec 1.8)**:  
   Instantiating `CyclingPowerMetricsDevice` directly in `BANALService` MUST produce the identical baseline state as invoking `resetSession()`. Calling `resetSession()` inside the primary constructor guarantees that `mCurrentWPrimeBalJoules` is seeded with `wPrimeKj * 1000.0` and `mWPrimeBalSensor` emits `(double) wPrimeKj` immediately.
2. **Cumulative Metrics Pause Gating Invariant (REQ-TRK-019 / REQ-TRK-021)**:  
   Normalized Power (NP), Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) MUST NEVER accumulate, integrate work, or advance FIFO samples while `isRecordingSuspended()` is `true`.
3. **Mathematical Precision & Skiba Formula Stability**:  
   Formulas in `PowerPhysiologyCalculator.kt` (`stepWPrimeBalanceJoules`, `calculatePowerToWeight`, `calculateNormalizedPower`, `calculateIntensityFactor`, `calculateTss`) and `WPrimeBatteryMath.kt` remain strictly unmodified.
4. **Stale Timeout Single Source of Truth**:  
   UI staleness remains strictly governed by `TrackingViewModel`'s 3-second ticker (`WPrimeBatteryMath.STALE_TIMEOUT_MS = 3000L`). `CyclingPowerMetricsDevice` shall not preemptively push `null` on 1-second transient packet drops.
5. **Human Decision Gate on Parent Tickets (Rule 3)**:  
   Sub-task `ATT-4352` advances autonomously to `Erledigt` via transition `freigabe` upon passing Gate 3 review, synchronously advancing `ATT-4283` to `Implementation`. The parent ticket `ATT-4283` terminal state remains `Final Review (Human)`.
6. **Zero Regressions & Clean-Room Test Suite Pass (Rule 4 / Rule 44)**:  
   All existing unit tests and regression suites (`./gradlew testDebugUnitTest`) must pass cleanly (100% green). No intermediate git commits are created in Stage 3.

---

## 4. Proposed Architectural Changes

```
┌────────────────────────────────────────────────────────────────────────┐
│                        BANALService Lifecycle                          │
│                                                                        │
│   CyclingPowerMetricsDevice(...) Constructor                           │
│     ├── super(context, mySensorManager, CYCLING_POWER_METRICS)         │
│     ├── Provider assignments (RecordingState, Weight, FTP, CP, W')     │
│     ├── registerSensors() [initializes MySensor instances]             │
│     ├── resetSession() [RULE 51 CONSTRUCTOR SEEDING PARITY]            │
│     │     └── mWPrimeBalSensor.newValue((double) wPrimeKj)  ◄──────────┤ Baseline 100% Full
│     └── schedule 1Hz calculateMetrics()                                │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ 1 Hz Tick
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│               calculateMetrics() Execution Pipeline                    │
│                                                                        │
│ 1. Query Power:                                                        │
│    power = BANALService.getFilteredSensorData(cPowerFilter)            │
│            ?: rawSensor.getValue()                                     │
│                                                                        │
│ 2. Check Suspension: isSuspended = isRecordingSuspended()              │
│    ├── IF isSuspended:                                                 │
│    │     ├── Update Power-to-Weight (REQ-TRK-038.2):                   │
│    │     │     wPerKg = (athleteWeightKg > 0)                          │
│    │     │              ? (power >= 0 ? power / weight : 0.0)          │
│    │     │              : null                                         │
│    │     │     mPowerToWeightSensor.newValue(wPerKg) ◄─────────────────┤ Prevents W/kg timeout!
│    │     ├── Step W'bal at 0.0 W (Exponential Reconstitution):         │
│    │     │     mCurrentWPrimeBalJoules = stepWPrimeBal(..., 0.0, ...)  │
│    │     │     mWPrimeBalSensor.newValue(mCurrentWPrimeBalJoules/1000) │
│    │     └── RETURN (Strict Gate: No NP, Work, or TSS accumulation)    │
│    └── IF NOT isSuspended:                                             │
│          ├── Resumption from long pause (> 30s): clear mPowerWindow30s │
│          ├── Update Power-to-Weight:                                   │
│          │     wPerKg = (power >= 0 && weight > 0) ? power/weight:null │
│          │     mPowerToWeightSensor.newValue(wPerKg)                   │
│          ├── Power Validation:                                         │
│          │     IF power == null || NaN || Inf || power < 0.0:          │
│          │       RETURN (Preserve existing NP, Work, TSS, W'bal!) ◄────┤ Transient Dropout Resilience!
│          ├── Active Calculations:                                      │
│          │     Work += power * 1.0; FIFO window add; running NP;       │
│          │     IF & TSS; Step W'bal with live power                    │
└────────────────────────────────────────────────────────────────────────┘
```

### Component 1: `CyclingPowerMetricsDevice.java` (Virtual Sensor Telemetry Engine)
* **Layer**: Background Service Telemetry Provider (`com.atrainingtracker.banalservice.devices`)
* **Interface / Class**: `CyclingPowerMetricsDevice` extending `VirtualDevice`
* **Modifications**:
  1. **Primary Constructor (`lines 155–178`)**:
     - Insert `resetSession();` immediately following `registerSensors();` and before `mScheduler.scheduleWithFixedDelay(...)`.
     - *Effect*: When `BANALService` creates the device, `mCurrentWPrimeBalJoules` is seeded with `wPrimeKj * 1000.0` Joules and `mWPrimeBalSensor.newValue((double) wPrimeKj)` is dispatched immediately, eliminating the startup null state (`-- kJ` / `--%`).
  2. **`calculateMetrics()` Suspension Branch (`lines 286–312`)**:
     - Query `power` (from `cPowerFilter` or raw fallback) *before* evaluating suspension branch.
     - Within `if (isSuspended)`, compute `wPerKg`:
       - If `athleteWeightKg > 0.0f`: if `power != null && power >= 0.0`, `wPerKg = PowerPhysiologyCalculator.calculatePowerToWeight(power, athleteWeightKg)`; else if `power == null && mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null`, `wPerKg = 0.0`.
       - If `athleteWeightKg <= 0.0f`: `wPerKg = null`.
       - Emit `mPowerToWeightSensor.newValue(wPerKg)`.
     - Continue stepping $W'_{\text{bal}}$ at 0.0 W and emit `mWPrimeBalSensor.newValue(...)`.
     - Keep `return;` to ensure NP, Work, and TSS do not accumulate while paused.
  3. **`calculateMetrics()` Invalid Power Validation Branch (`lines 354–368`)**:
     - In the check `if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0)`:
     - Remove destructive calls:
       - `// REMOVE: if (mNormalizedPowerSensor != null) mNormalizedPowerSensor.newValue(null);`
       - `// REMOVE: if (mIntensityFactorSensor != null) mIntensityFactorSensor.newValue(null);`
       - `// REMOVE: if (mTssSensor != null) mTssSensor.newValue(null);`
       - `// REMOVE: if (mWPrimeBalSensor != null) mWPrimeBalSensor.newValue(null);`
     - Retain `return;` to avoid integrating work or updating FIFO rolling averages.
     - *Effect*: Transient 1-second packet loss preserves the existing balance and cumulative metrics, allowing `TrackingViewModel`'s 3-second `wPrimeStaleJob` (`STALE_TIMEOUT_MS = 3000L`) to handle staleness cleanly without visual flickering.

### Component 2: `CyclingPowerMetricsDeviceTest.kt` (Unit Test Suite)
* **Layer**: Unit Test Harness (`app/src/test/java/com/atrainingtracker/banalservice/devices`)
* **Modifications**:
  1. Add `testStartup_constructorSeedsInitialWPrimeCapacity_publishesFullCapacityImmediately()` (`[TST-TRK-031.1]`).
  2. Add `testStartup_constructorWithFtpFallback_seedsInitialWPrimeCapacity()` (`[TST-TRK-031.2]`).
  3. Update existing `testCalculateMetrics_recordingSuspended_skipsCalculation()` and add `testCalculateMetrics_recordingSuspended_stationaryZeroPower_updatesPowerToWeightSensor()` (`[TST-TRK-031.3]`) asserting `powerToWeightSensor` updates with `0.0` while NP, Work, and TSS remain null.
  4. Add `testCalculateMetrics_recordingSuspended_coastingOrMovingPower_updatesPowerToWeightSensor()` (`[TST-TRK-031.4]`).
  5. Add `testCalculateMetrics_transientInvalidPowerDrop_preservesWPrimeBalanceAndCumulativeSensors()` (`[TST-TRK-031.5]`).
  6. Add `testCalculateMetrics_unconfiguredAthleteWeight_publishesNullPowerToWeightEvenWhenSuspended()` (`[TST-TRK-031.6]`).

### UI Consistency (Rule 23 — Mandatory Section)
* **Scope**: No new UI components, layouts, or composables are created. The changes are strictly within the background service telemetry calculation layer (`CyclingPowerMetricsDevice.java`), which feeds existing UI components.
* **Reference screen / component**:
  - Live Tracking Cockpit: `SensorGridScreen.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.grid`)
  - Cockpit Telemetry Tiles: `SensorGridTile.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.grid.tiles`)
  - Battery Gauge Tile: `WPrimeBatteryGaugeBar.kt` (`com.atrainingtracker.trainingtracker.ui.tracking.components`)
* **Reused components**: Existing `WPrimeBatteryGaugeBar`, `SensorGridTile`, and `SensorCard` composables consume the improved telemetry stream directly without modification.
* **Theme tokens**: All visual rendering continues to use canonical theme tokens (`TTColor.Zone1Active` / green for 100% capacity, `TTColor.BatteryLow` / amber, `MaterialTheme.colorScheme.errorContainer` for critical fatigue).
* **New one-off styles & justification**: None. Visual consistency is fully maintained.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Constructor Baseline Capacity Seeding (Rule 51 Parity)
* **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* **Changes**:
  - In the primary constructor (lines 155–178), call `resetSession();` immediately following `registerSensors();` (line 175) and before `mScheduler.scheduleWithFixedDelay(...)` (line 177).
  - *Verification Target*: `CyclingPowerMetricsDeviceTest.testStartup_constructorSeedsInitialWPrimeCapacity_publishesFullCapacityImmediately` (`TST-TRK-031.1`) and `testStartup_constructorWithFtpFallback_seedsInitialWPrimeCapacity` (`TST-TRK-031.2`).

### Step 2: Stationary & Paused W/kg Alignment and Pause Gating
* **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* **Changes**:
  - Move the query for `power` (lines 324–342) above the `if (isSuspended)` block.
  - Inside `if (isSuspended)`:
    - Retrieve `athleteWeightKg = mWeightProvider.getAthleteWeightKg(mContext);`.
    - Compute `wPerKg`:
      ```java
      Double wPerKg = null;
      if (athleteWeightKg > 0.0f) {
          if (power != null && power >= 0.0) {
              wPerKg = PowerPhysiologyCalculator.calculatePowerToWeight(power, athleteWeightKg);
          } else if (power == null && mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null) {
              wPerKg = 0.0;
          }
      }
      if (mPowerToWeightSensor != null) {
          mPowerToWeightSensor.newValue(wPerKg);
      }
      ```
    - Preserve the existing $W'_{\text{bal}}$ exponential reconstitution logic at 0.0 W.
    - Preserve `return;` at line 311 to prevent NP, Work, and TSS accumulation during pause.
  - In the active (non-suspended) path:
    - Compute and emit `wPerKg`:
      ```java
      Double wPerKg = (power != null && power >= 0.0 && athleteWeightKg > 0.0f)
              ? PowerPhysiologyCalculator.calculatePowerToWeight(power, athleteWeightKg)
              : null;
      if (mPowerToWeightSensor != null) {
          mPowerToWeightSensor.newValue(wPerKg);
      }
      ```
  - *Verification Target*: `testCalculateMetrics_recordingSuspended_stationaryZeroPower_updatesPowerToWeightSensor` (`TST-TRK-031.3`), `testCalculateMetrics_recordingSuspended_coastingOrMovingPower_updatesPowerToWeightSensor` (`TST-TRK-031.4`), and `testCalculateMetrics_unconfiguredAthleteWeight_publishesNullPowerToWeightEvenWhenSuspended` (`TST-TRK-031.6`).

### Step 3: Transient Power Dropout Resilience in `calculateMetrics()`
* **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* **Changes**:
  - In lines 354–368:
    ```java
    // 2. Power Validation for NP, IF, Mechanical Work, TSS & W'bal
    if (power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0) {
        // Do NOT wipe sensors to null on transient 1-second dropouts.
        // Let TrackingViewModel's 3-second stale timer govern UI staleness gracefully.
        return;
    }
    ```
  - Delete `mNormalizedPowerSensor.newValue(null);`, `mIntensityFactorSensor.newValue(null);`, `mTssSensor.newValue(null);`, and `mWPrimeBalSensor.newValue(null);`.
  - *Verification Target*: `testCalculateMetrics_transientInvalidPowerDrop_preservesWPrimeBalanceAndCumulativeSensors` (`TST-TRK-031.5`).

### Step 4: Unit Test Suite Expansion
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Changes**:
  - Implement test cases `TST-TRK-031.1` through `TST-TRK-031.6`.
  - Update `testCalculateMetrics_recordingSuspended_skipsCalculation` to reflect the refined behavior: `powerToWeightSensor` emits `4.0` (or `0.0`), while NP, Work, and TSS remain null.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDeviceTest"
  ```

### Step 5: Localization & Specifier Parity Verification
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```
* **Goal**: Validate that all existing power, W/kg, and W' battery string resources across all 9 locales pass translation parity (`TST-TRK-031.7`).

### Step 6: Targeted Module Test Suite Execution
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.physiology.*"
  ```
* **Goal**: Confirm fast feedback (~5–15 seconds) and complete pass across all related sensor device and physiological calculation unit tests before Stage 5 clean-room regression.

---

## 6. Verification & Rollback Plan

### Verification Plan
1. **Targeted Unit Verification (Stage 4)**:  
   Execute `CyclingPowerMetricsDeviceTest` and `PowerPhysiologyCalculatorTest`:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.CyclingPowerMetricsDeviceTest"
   ```
2. **Localization Audit (Stage 4)**:  
   Execute `TranslationParityTest`:
   ```bash
   ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
   ```
3. **Clean-Room Regression (Stage 5)**:  
   Execute the full test suite (`TST-TRK-031.8`):
   ```bash
   ./gradlew testDebugUnitTest
   ```
4. **Physical On-Device Verification (Stage 5 Walkthrough)**:  
   Deploy debug build to physical Google Pixel 10 via `./gradlew installDebug`. Confirm:
   - Cockpit W'bal tile displays full green battery bar and configured kJ from second zero.
   - Stopping in Auto-Pause preserves `0.0 W/kg` alongside `0 W`.
   - Simulating sensor disconnection triggers graceful 3-second dimming rather than instantaneous gauge collapse.

### Rollback Plan
- All changes are strictly confined to the isolated git branch `feature/ATT-4283`.
- If unresolvable regressions or design defects emerge, the branch can be cleanly reset to `origin/sprint/2026-43.1` via:
  ```bash
  git checkout sprint/2026-43.1
  git branch -D feature/ATT-4283
  ```
- Because no database schema migrations, persistent shared preferences format changes, or external API contracts are modified, rolling back introduces zero risk of database corruption or data loss.
