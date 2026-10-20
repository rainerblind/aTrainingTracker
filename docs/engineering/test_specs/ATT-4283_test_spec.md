# Stage 2: Requirement & Test Specification - ATT-4283: [Bug] Seed initial W'bal capacity on CyclingPowerMetricsDevice startup and align W/kg & W'bal behavior when stationary or paused

**Ticket**: [ATT-4283](https://rainerblind.atlassian.net/browse/ATT-4283)  
**Sub-task**: [ATT-4351](https://rainerblind.atlassian.net/browse/ATT-4351) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-TRK-038` (*Constructor State Seeding Parity (Rule 51), Stationary/Paused W/kg Telemetry Alignment, and Transient Power Dropout Resilience in CyclingPowerMetricsDevice*)  
**Test Spec ID**: `TST-TRK-031`  
**Branch**: `feature/ATT-4283`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-TRK-038)

### 1.1 Problem Statement & Rationale
During live on-device testing and the Sprint Review of `ATT-1296` on a physical Google Pixel 10 (Sprint Review 2026-42.10, `ATT-4100`), three interrelated telemetry anomalies were observed in the cycling telemetry pipeline involving `CyclingPowerMetricsDevice.java`, `WPrimeBatteryMath.kt`, and `TrackingViewModel.kt`:

1. **Uninitialized $W'$ Anaerobic Capacity at Service Startup**:  
   When `CyclingPowerMetricsDevice` was instantiated by `BANALService` at service startup, its internal battery tracker `mCurrentWPrimeBalJoules` initialized to `null`. Unlike `resetSession()` (which was called in unit tests), the constructors did not invoke `resetSession()` or seed `mCurrentWPrimeBalJoules = wPrimeKj * 1000.0`. Consequently, `mWPrimeBalSensor` emitted `null` on startup, causing the live cockpit tile to display `-- kJ` and `--%` with an empty gauge track until the athlete generated their first active non-paused power sample.

2. **Telemetry Expiration of W/kg During Auto-Pause**:  
   When a workout enters Auto-Pause (`isSuspended == true`, e.g. when an athlete stops at a traffic signal or intersection), `CyclingPowerMetricsDevice.calculateMetrics()` returned early before updating `mPowerToWeightSensor`. Even though the physical power meter remained paired and transmitting `0 W` (causing `Leistung (3 sec Ø)` to display `0 W` and cadence `Freq.` to show `0 /min`), `mPowerToWeightSensor` was completely starved of updates. After 3 seconds, `TimedMovingAverageFilter` timed out and expired to `null`, causing `W/kg (3 sec Ø)` to render `-- W/kg` alongside `0 W`.

3. **Premature Gauge Staleness on Transient Power Sample Dropouts**:  
   If `power` temporarily dropped to `null` during an active session (e.g. a 1-second BLE packet loss or coasting without pedal rotations on event-driven sensors), `CyclingPowerMetricsDevice.calculateMetrics()` immediately published `mWPrimeBalSensor.newValue(null)`. In `TrackingViewModel`, receiving `null` immediately bypassed the 3-second stale tolerance (`WPrimeBatteryMath.STALE_TIMEOUT_MS = 3000L`), setting `batteryFraction = null` and causing the battery gauge and numeric balance to violently flash to `-- kJ` / `--%` on a single missed sample.

**Rationale**:  
This specification formulates `REQ-TRK-038` to restore visual and behavioral harmony across the live cockpit telemetry grid:
- Deliver immediate visual confidence upon opening the cockpit: a 100% full green battery gauge and configured $W'$ capacity ($wPrimeKj$) from second zero (Rule 51).
- Ensure consistent stationary and paused behavior: when `Leistung (3 sec Ø)` displays `0 W`, `W/kg (3 sec Ø)` displays `0.0 W/kg` (or `0,0 W/kg`) rather than disappearing to `-- W/kg`.
- Harden the telemetry pipeline against transient sensor dropouts: preserve $W'_{\text{bal}}$ and allow `TrackingViewModel`'s 3-second stale timer (`STALE_TIMEOUT_MS`) to govern staleness gracefully without visual flickering.

### 1.2 Functional & Architectural Requirements
The system SHALL adhere to the following numbered requirement clauses:

1. **Constructor State Seeding Parity (`REQ-TRK-038.1` — Rule 51)**:
   * `CyclingPowerMetricsDevice` constructors SHALL invoke `resetSession()` (or seed baseline capacity identically) upon instantiation.
   * When `resolveEffectiveCpWatts() > 0 && mWPrimeKjProvider.getWPrimeKj(mContext) > 0.0f`, `mCurrentWPrimeBalJoules` SHALL be initialized to `wPrimeKj * 1000.0` and `mWPrimeBalSensor.newValue((double) wPrimeKj)` SHALL be published immediately on device creation, ensuring the cockpit battery gauge renders full 100% capacity from second zero before any power packet arrives.
   * When $CP \le 0$ or $W' \le 0.0\text{f}$, `mCurrentWPrimeBalJoules` SHALL be initialized to `null` and `mWPrimeBalSensor.newValue(null)` emitted.

2. **Stationary & Paused W/kg Alignment (`REQ-TRK-038.2`)**:
   * In `calculateMetrics()`, when `isSuspended == true` (Auto-Pause or Manual Pause), the engine SHALL retrieve instantaneous power (falling back to raw sensor data) and athlete weight `athleteWeightKg`.
   * When `athleteWeightKg > 0.0f`: if `power != null && power >= 0.0`, the system SHALL publish `mPowerToWeightSensor.newValue(power / athleteWeightKg)`; if `power == null` and a power sensor is present, it SHALL publish `0.0`.
   * When `athleteWeightKg <= 0.0f` (unconfigured), `mPowerToWeightSensor.newValue(null)` SHALL be published.
   * Continuously updating `mPowerToWeightSensor` during pause prevents `TimedMovingAverageFilter` (3-second window) from starving and purging all samples, ensuring `W/kg (3 sec Ø)` displays `0.0 W/kg` (or `0,0 W/kg`) alongside `0 W` instead of expiring to `-- W/kg`.

3. **Transient Dropout Resilience for W'bal & Cumulative Metrics (`REQ-TRK-038.3`)**:
   * In `calculateMetrics()`, when `power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0` during an active non-suspended session, the engine SHALL NOT publish `null` to `mWPrimeBalSensor`, `mNormalizedPowerSensor`, `mIntensityFactorSensor`, or `mTssSensor`.
   * The engine SHALL preserve the existing session values and return early before integrating mechanical work or pushing into `mPowerWindow30s`.
   * During transient RF/BLE packet dropouts ($\le 3\text{ seconds}$), $W'_{\text{bal}}$ and cumulative metrics remain preserved, allowing `TrackingViewModel`'s `wPrimeStaleJob` (`STALE_TIMEOUT_MS = 3000L`) to govern UI staleness gracefully without visual flickering or instantaneous gauge collapse.

4. **Cumulative Metric Pause Invariant (`REQ-TRK-038.4`)**:
   * While `isSuspended == true`, Normalized Power (NP), Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) SHALL remain strictly gated and SHALL NOT accumulate, integrate work, or advance FIFO samples.
   * $W'_{\text{bal}}$ SHALL continue exponential reconstitution at $P = 0.0\text{ W}$ during pause when `mCurrentWPrimeBalJoules != null`.

5. **9-Language Localization & Format Parity (`REQ-TRK-038.5`)**:
   * Existing telemetry strings (`power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `w_prime_battery_pct`, `w_prime_warning_low_battery`) across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) SHALL remain 100% defined and uncorrupted, with zero new string resources required.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-TRK-019.3` (*Decoupled 1Hz Computed Metrics Engine*) and `REQ-UI-416.1` / `REQ-UI-416.2` (*Real-time W' Balance Expenditure and Recovery Tracking*). Targets `CyclingPowerMetricsDevice.java` and `CyclingPowerMetricsDeviceTest.kt`.
* **Historical Origin & Commit Trace**: `REQ-TRK-019` introduced in Sprint 2026-42.4 (`ATT-1289`, commit `559376e2`). `REQ-UI-416` introduced in Sprint 2026-42.10 (`ATT-1296`, commit `889ebd18`). Follow-up defect logged during Sprint Review 2026-42.10 (`ATT-4100`, commit `c8939a3f`) upon physical Google Pixel 10 cockpit inspection.
* **Root Reason for Existing Formulation**: In `ATT-1289`, pause gating placed `if (isSuspended) return;` at the beginning of `calculateMetrics()` to prevent metric accumulation while tracking was paused. However, hardware power meters continue pushing `0 W` when stopped in Auto-Pause; starving `mPowerToWeightSensor` caused its 3-second filter to expire to `-- W/kg`, clashing with `0 W`. In `ATT-1296`, `resetSession()` seeded initial $W'$ capacity, but constructors never called it, leaving initial capacity unseeded until the first active power sample. In addition, an invalid-power check in `calculateMetrics()` immediately published `mWPrimeBalSensor.newValue(null)` on missing samples, bypassing `TrackingViewModel`'s 3-second stale tolerance (`STALE_TIMEOUT_MS`).
* **Preservation of Core Invariants**: Normalized Power, Mechanical Work, IF, and TSS must never accumulate during pauses; $W'_{\text{bal}}$ baseline capacity remains strictly equal to configured $W'$ capacity; Skiba differential equations and exponential reconstitution math remain untouched; 3000ms stale timeout remains authoritative for UI staleness; 100% clean-room test suite pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Constructor Capacity Seeding Parity - Rule 51)**:
  * *Given* `CyclingPowerMetricsDevice` instantiated with effective $CP = 280\text{ W}$ and $W' = 20.0\text{ kJ}$,
  * *When* the constructor completes without calling `calculateMetrics()` or `resetSession()`,
  * *Then* `wPrimeBalSensor.sensorData.value` immediately emits `20.0` (Double, delta 0.001) and internal balance is seeded with `20000.0` Joules.
* **Criterion 2 (Unconfigured Capacity Startup)**:
  * *Given* `CyclingPowerMetricsDevice` instantiated with $W' = 0.0\text{f}$ or $CP \le 0$,
  * *When* the constructor completes,
  * *Then* `wPrimeBalSensor.sensorData.value` remains `null`.
* **Criterion 3 (Stationary Auto-Pause W/kg Alignment)**:
  * *Given* an active ride entering Auto-Pause (`isSuspended == true`) with athlete weight $75.0\text{ kg}$ and power sensor transmitting $0\text{ W}$,
  * *When* `calculateMetrics()` executes,
  * *Then* `powerToWeightSensor` publishes `0.0` W/kg, preventing `TimedMovingAverageFilter` from purging all samples and expiring to `null` (`"-- W/kg"`).
* **Criterion 4 (Paused Coasting / Moving W/kg Alignment)**:
  * *Given* an active ride paused (`isSuspended == true`) with athlete weight $75.0\text{ kg}$ and power sensor transmitting $150\text{ W}$,
  * *When* `calculateMetrics()` executes,
  * *Then* `powerToWeightSensor` publishes `2.0` W/kg, while Normalized Power, Mechanical Work, and TSS do NOT accumulate.
* **Criterion 5 (Transient 1-Second Power Dropout Resilience)**:
  * *Given* an active ride where power temporarily drops to `null` for 1 second (e.g. transient BLE packet loss),
  * *When* `calculateMetrics()` executes,
  * *Then* `wPrimeBalSensor`, `normalizedPowerSensor`, `intensityFactorSensor`, and `tssSensor` retain their current session values and do NOT publish `null`.
* **Criterion 6 (Pause Cumulative Metric Gating)**:
  * *Given* `isSuspended == true`,
  * *When* `calculateMetrics()` executes,
  * *Then* Normalized Power, Mechanical Work, and TSS do NOT accumulate, while $W'_{\text{bal}}$ continues exponential reconstitution at $0\text{ W}$.
* **Criterion 7 (Unconfigured Athlete Weight Fallback)**:
  * *Given* an athlete with unconfigured weight ($0.0\text{ kg}$),
  * *When* `calculateMetrics()` executes in active or suspended states,
  * *Then* `powerToWeightSensor` publishes `null`.

### 1.5 System Invariants
1. **Rule 51 Parity**: Constructor state initialization must produce identical baseline capacity and sensor emissions as calling `resetSession()`.
2. **Pause Accumulation Invariant**: Coggan Normalized Power, Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) MUST NOT accumulate, advance FIFO samples, or integrate work while paused or auto-paused.
3. **Mathematical Precision**: Skiba differential expenditure and exponential reconstitution formulas in `PowerPhysiologyCalculator.kt` remain strictly unaltered.
4. **Stale Timeout Single Source of Truth**: UI staleness remains strictly governed by `TrackingViewModel`'s 3-second ticker (`STALE_TIMEOUT_MS = 3000L`).
5. **Localization Parity**: 100% translation parity across all 9 canonical app locales, with zero new string resources required.
6. **Clean-Room Test Suite Pass**: 100% pass rate under `./gradlew testDebugUnitTest`.

---

## 2. Test Specification (TST-TRK-031)

### Test Case 1: `testStartup_constructorSeedsInitialWPrimeCapacity_publishesFullCapacityImmediately` (`[TST-TRK-031.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: Mock `Context`, `MySensorManager`, configured `CpProvider` returning 280W, `WPrimeKjProvider` returning 20.0f.
* **Action**:
  1. Instantiate `device = CyclingPowerMetricsDevice(...)`.
  2. Inspect `device.wPrimeBalSensor.sensorData.value` immediately upon instantiation without calling `calculateMetrics()` or `resetSession()`.
* **Expected Result**:
  1. `device.wPrimeBalSensor.sensorData.value` is non-null and equals `20.0` (Double, delta 0.001).
  2. Instantiate second device with `wPrimeKj = 0.0f`: `wPrimeBalSensor.sensorData.value` is `null`.

### Test Case 2: `testStartup_constructorWithFtpFallback_seedsInitialWPrimeCapacity` (`[TST-TRK-031.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: Mock dependencies with `cpProvider` returning null, `ftpProvider` returning 250W, and `wPrimeKjProvider` returning 15.0f.
* **Action**:
  1. Instantiate `device = CyclingPowerMetricsDevice(...)`.
  2. Inspect `device.wPrimeBalSensor.sensorData.value` immediately upon instantiation.
* **Expected Result**:
  1. `device.wPrimeBalSensor.sensorData.value` is non-null and equals `15.0` (Double, delta 0.001).

### Test Case 3: `testCalculateMetrics_recordingSuspended_stationaryZeroPower_updatesPowerToWeightSensor` (`[TST-TRK-031.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: `isSuspended = true`, athlete weight `75.0f`, power sensor transmitting `0.0W`.
* **Action**:
  1. Call `device.calculateMetrics()`.
* **Expected Result**:
  1. `device.powerToWeightSensor.sensorData.value` equals `0.0` (Double, delta 0.001).
  2. `device.normalizedPowerSensor.sensorData.value` is `null` (not accumulated).
  3. `device.workKjSensor.sensorData.value` is `null` (not accumulated).
  4. `device.tssSensor.sensorData.value` is `null` (not accumulated).
  5. `device.wPrimeBalSensor.sensorData.value` reflects exponential reconstitution at 0.0W.

### Test Case 4: `testCalculateMetrics_recordingSuspended_coastingOrMovingPower_updatesPowerToWeightSensor` (`[TST-TRK-031.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: `isSuspended = true`, athlete weight `75.0f`, power sensor transmitting `150.0W`.
* **Action**:
  1. Call `device.calculateMetrics()`.
* **Expected Result**:
  1. `device.powerToWeightSensor.sensorData.value` equals `2.0` (Double, delta 0.001).
  2. `device.normalizedPowerSensor.sensorData.value` is `null`.
  3. `device.workKjSensor.sensorData.value` is `null`.

### Test Case 5: `testCalculateMetrics_transientInvalidPowerDrop_preservesWPrimeBalanceAndCumulativeSensors` (`[TST-TRK-031.5]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: Active session (`isSuspended = false`), CP = 250W, FTP = 250W, W' = 20.0 kJ, athlete weight `75.0f`.
* **Action**:
  1. Tick 1: `power = 300.0W`. Call `calculateMetrics()`. Assert W'bal depleted below 20.0 kJ, NP is valid, Work is valid.
  2. Capture `wPrimeBefore = device.wPrimeBalSensor.sensorData.value`, `npBefore = device.normalizedPowerSensor.sensorData.value`, `workBefore = device.workKjSensor.sensorData.value`.
  3. Tick 2: `power = null` (transient 1-second packet drop). Call `calculateMetrics()`.
* **Expected Result**:
  1. `device.wPrimeBalSensor.sensorData.value` equals `wPrimeBefore` (is NOT wiped to `null`).
  2. `device.normalizedPowerSensor.sensorData.value` equals `npBefore` (is NOT wiped to `null`).
  3. `device.workKjSensor.sensorData.value` equals `workBefore` (is NOT wiped to `null`).
  4. Mechanical work does not increment during the missing tick.

### Test Case 6: `testCalculateMetrics_unconfiguredAthleteWeight_publishesNullPowerToWeightEvenWhenSuspended` (`[TST-TRK-031.6]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: Athlete weight `0.0f` (unconfigured), `power = 200.0W`.
* **Action**:
  1. Test active state (`isSuspended = false`): call `calculateMetrics()`.
  2. Test suspended state (`isSuspended = true`): call `calculateMetrics()`.
* **Expected Result**:
  1. In both active and suspended states, `device.powerToWeightSensor.sensorData.value` is strictly `null`.

### Test Case 7: 9-Language Localization & Specifier Audit (`[TST-TRK-031.7]`)
* **Scope**: Localization Parity & Resource Audit
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/localization/TranslationParityTest.kt`
* **Goal**: Verify string presence, non-blank translations, and matching format specifiers across all 9 canonical app locales:
  * EN (`values/strings.xml`)
  * DE (`values-de/strings.xml`)
  * ES (`values-es/strings.xml`)
  * FR (`values-fr/strings.xml`)
  * IT (`values-it/strings.xml`)
  * JA (`values-ja/strings.xml`)
  * NL (`values-nl/strings.xml`)
  * PL (`values-pl/strings.xml`)
  * PT (`values-pt/strings.xml`)
* **Audit Scope**:
  * `power_to_weight` ("Power-to-weight ratio" / "Leistungsgewicht" / etc.)
  * `power_to_weight_short` ("W/kg")
  * `units_power_to_weight` ("W/kg", non-translatable)
  * `w_prime_battery_pct` (`%1$d%%`)
  * `w_prime_warning_low_battery` ("W\' Battery Low (&lt;20%)" / "W\'-Akku schwach (&lt;20%)" / etc.)
* **Expected Result**: 100% parity, zero missing keys, zero specifier mismatches, and confirm zero new string resources are required for this ticket.

### Test Case 8: Clean-Room Regression Suite Execution (`[TST-TRK-031.8]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with zero regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement Mapping | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-031.1]` | Unit | `CyclingPowerMetricsDevice` constructor (CP & W' seeding) | `REQ-TRK-038.1` | Specified |
| `[TST-TRK-031.2]` | Unit | `CyclingPowerMetricsDevice` constructor (FTP fallback seeding) | `REQ-TRK-038.1` | Specified |
| `[TST-TRK-031.3]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics()` (stationary Auto-Pause 0W) | `REQ-TRK-038.2`, `REQ-TRK-038.4` | Specified |
| `[TST-TRK-031.4]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics()` (paused moving power) | `REQ-TRK-038.2`, `REQ-TRK-038.4` | Specified |
| `[TST-TRK-031.5]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics()` (transient power dropout) | `REQ-TRK-038.3` | Specified |
| `[TST-TRK-031.6]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics()` (unconfigured weight) | `REQ-TRK-038.2` | Specified |
| `[TST-TRK-031.7]` | Localization | `TranslationParityTest` (telemetry string tokens x 9 locales) | `REQ-TRK-038.5`, `REQ-UI-106` | Specified |
| `[TST-TRK-031.8]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
