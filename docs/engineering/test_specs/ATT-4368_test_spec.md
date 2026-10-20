# Stage 2: Requirement & Test Specification - ATT-4368: [Bug] Hide W'bal row in Workout Summary when workout has no recorded power data

**Ticket**: [ATT-4368](https://rainerblind.atlassian.net/browse/ATT-4368)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Requirement Mapping**: `REQ-TRK-038` (*Constructor State Seeding Parity (Rule 51), Sensor-Presence Gating (Rule 52), Stationary/Paused W/kg Telemetry Alignment, and Workout Summary Presentation Defense*)  
**Test Spec ID**: `TST-TRK-031`  
**Branch**: `feature/ATT-4368`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Requirement Specification (REQ-TRK-038)

### 1.1 Problem Statement & Rationale
During Sprint 2026-43.1 Joint Review (ticket ATT-4292), physical device verification on a Google Pixel 10 (attached `att_workout_summary_wbal.png`) revealed that workouts recorded without power telemetry (such as walks, runs, swims, or cycling without a paired power meter) display a phantom Anaerobic Battery row in the Workout Summary card and `WorkoutDetails` section:
```text
W'bal: Min 20.0 kJ (100%)
```

Dr. Philip Skiba's Anaerobic Work Capacity balance ($W'_{\text{bal}}$) is strictly a cycling power metric derived from active power output relative to Critical Power ($CP$). Displaying full anaerobic battery capacity for a foot walk or non-power activity confuses athletes, clutters the overview card, and violates domain modeling standards (`docs/coding_standards.md` Section 1.8, Rules 51 & 52).

A forensic root cause analysis identified two contributing flaws:
1. `CyclingPowerMetricsDevice.java` driver unconditionally published `mWPrimeBalSensor.newValue((double) wPrimeKj)` inside `resetSession()` (called from constructor for Rule 51 parity) and during suspended `calculateMetrics()`, without verifying whether an active `SensorType.POWER` sensor was registered in `MySensorManager`. As a result, `TrackerService` recorded phantom `W_PRIME_BAL` minimum extrema (`20.0 kJ`) in SQLite for every recorded activity.
2. `WorkoutDataMapper.kt` mapped `resolveWPrimeBalSummary()` whenever `rawMinWPrimeBal` was present in SQLite, without checking whether the workout actually contained recorded power data (`avgPower != null || maxPower != null`).

To resolve this defect completely and protect both future tracking and historical user databases, the system must enforce sensor-presence gating in `CyclingPowerMetricsDevice` (Rule 52) while preserving Rule 51 internal accumulator seeding, and `WorkoutDataMapper` must defensively gate $W'_{\text{bal}}$ summary mapping on the presence of recorded power data across both single and batch mapping pathways (Rule 20).

### 1.2 Functional & Architectural Requirements

1. **Constructor State Seeding Parity & Sensor-Presence Gating (`REQ-TRK-038.1` — Rule 51 & Rule 52)**:
   - `CyclingPowerMetricsDevice` constructors SHALL invoke `resetSession()` (or seed baseline capacity identically) upon instantiation, ensuring internal capacity state parity (Rule 51).
   - When `resolveEffectiveCpWatts() > 0 && mWPrimeKjProvider.getWPrimeKj(mContext) > 0.0f`, the internal accumulator `mCurrentWPrimeBalJoules` SHALL be initialized to `wPrimeKj * 1000.0`.
   - `CyclingPowerMetricsDevice` SHALL evaluate `mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null`. If and only if an active power sensor is present, `mWPrimeBalSensor.newValue((double) wPrimeKj)` SHALL be published immediately on device creation (Rule 51). If no power sensor is present, `mWPrimeBalSensor.newValue(null)` SHALL be published (Rule 52), preventing `TrackerService` from recording phantom `W_PRIME_BAL` extrema into SQLite.
   - When $CP \le 0$ or $W' \le 0.0\text{f}$, `mCurrentWPrimeBalJoules` SHALL be initialized to `null` and `mWPrimeBalSensor.newValue(null)` emitted.

2. **Stationary & Paused W/kg Alignment (`REQ-TRK-038.2`)**:
   - In `calculateMetrics()`, when `isSuspended == true` (Auto-Pause or Manual Pause), the engine SHALL retrieve instantaneous power (falling back to raw sensor data) and athlete weight `athleteWeightKg`.
   - When `athleteWeightKg > 0.0f`: if `power != null && power >= 0.0`, the system SHALL publish `mPowerToWeightSensor.newValue(power / athleteWeightKg)`; if `power == null` and a power sensor is present, it SHALL publish `0.0`.
   - When `athleteWeightKg <= 0.0f` (unconfigured), `mPowerToWeightSensor.newValue(null)` SHALL be published.
   - Continuously updating `mPowerToWeightSensor` during pause prevents `TimedMovingAverageFilter` (3-second window) from starving and purging all samples, ensuring `W/kg (3 sec Ø)` displays `0.0 W/kg` (or `0,0 W/kg`) alongside `0 W` instead of expiring to `-- W/kg`.

3. **Transient Dropout Resilience for W'bal & Cumulative Metrics (`REQ-TRK-038.3`)**:
   - In `calculateMetrics()`, when `power == null || Double.isNaN(power) || Double.isInfinite(power) || power < 0.0` during an active non-suspended session, the engine SHALL NOT publish `null` to `mWPrimeBalSensor`, `mNormalizedPowerSensor`, `mIntensityFactorSensor`, or `mTssSensor`.
   - The engine SHALL preserve the existing session values and return early before integrating mechanical work or pushing into `mPowerWindow30s`.
   - During transient RF/BLE packet dropouts ($\le 3\text{ seconds}$), $W'_{\text{bal}}$ and cumulative metrics remain preserved, allowing `TrackingViewModel`'s `wPrimeStaleJob` (`STALE_TIMEOUT_MS = 3000L`) to govern UI staleness gracefully without visual flickering or instantaneous gauge collapse.

4. **Cumulative Metric Pause Invariant & Suspended W'bal Reconstitution Gating (`REQ-TRK-038.4` — Rule 52)**:
   - While `isSuspended == true`, Normalized Power (NP), Mechanical Work (kJ), Intensity Factor (IF), and Training Stress Score (TSS) SHALL remain strictly gated and SHALL NOT accumulate, integrate work, or advance FIFO samples.
   - $W'_{\text{bal}}$ exponential reconstitution at $P = 0.0\text{ W}$ during pause SHALL execute and publish to `mWPrimeBalSensor` if and only if `mCurrentWPrimeBalJoules != null` AND `mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null`.

5. **Aftermath Presentation Defense & DTO Mapping Symmetry (`REQ-TRK-038.5` — Rule 20 & Rule 52)**:
   - In `WorkoutDataMapper.kt`, `resolveWPrimeBalSummary()` SHALL accept `hasPowerData: Boolean` and evaluate `if (!hasPowerData) return Triple(null, null, false)`.
   - `hasPowerData` SHALL be evaluated as `(avgPowerWatts != null && avgPowerWatts > 0.0) || (maxPowerWatts != null && maxPowerWatts > 0.0)`.
   - Both single-row `fromCursor()` and batch `fromSnapshot()` mapping pathways SHALL resolve `hasPowerData` identically, enforcing Rule 20 DTO symmetry.
   - In `WorkoutDetails.kt`, `AnaerobicBatteryRow` SHALL be rendered if and only if `data.minWPrimeBalKj != null && data.minWPrimeBalPct != null`, ensuring non-power workouts omit the row and preceding divider completely, while preserving the modularity budget of `WorkoutDetails.kt` strictly below 500 lines.

6. **9-Language Localization & Format Parity (`REQ-TRK-038.6`)**:
   - Existing telemetry strings (`power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `w_prime_battery_pct`, `w_prime_warning_low_battery`, `workout_w_prime_min_kj_label`, `workout_w_prime_min_pct_label`) across all 9 application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) SHALL remain 100% defined and uncorrupted, with zero new string resources required.

### 1.3 Acceptance Criteria (Given-When-Then)

- **Criterion 1 (Sensor-Presence Gating in Driver — Non-Power Workout)**:
  - *Given* `CyclingPowerMetricsDevice` instantiated when NO `SensorType.POWER` is registered in `MySensorManager`,
  - *When* the constructor completes and `calculateMetrics()` runs (whether suspended or active),
  - *Then* `wPrimeBalSensor.sensorData.value` remains `null`, preventing `TrackerService` from persisting phantom `W_PRIME_BAL` extrema.

- **Criterion 2 (Seeding Parity with Power Sensor Present)**:
  - *Given* `CyclingPowerMetricsDevice` instantiated when `SensorType.POWER` IS registered in `MySensorManager`, with effective $CP = 280\text{ W}$ and $W' = 20.0\text{ kJ}$,
  - *When* the constructor completes,
  - *Then* `wPrimeBalSensor.sensorData.value` immediately emits `20.0` (kJ) and the internal balance is seeded with `20000.0` Joules (Rule 51).

- **Criterion 3 (Historical Legacy Database Protection in Mapper)**:
  - *Given* a workout without recorded power data (`avgPower == null && maxPower == null`), even if legacy `W_PRIME_BAL` extrema exists in SQLite,
  - *When* mapped via `WorkoutDataMapper.fromCursor` or `WorkoutDataMapper.fromSnapshot`,
  - *Then* `minWPrimeBalKj` and `minWPrimeBalPct` evaluate to `null` identically across both pathways (Rule 20).

- **Criterion 4 (Workout Details Summary Row Suppression)**:
  - *Given* a workout without recorded power data rendered in `WorkoutDetails`,
  - *When* sections are constructed,
  - *Then* `AnaerobicBatteryRow` is omitted and no duplicate or stray dividers are rendered.

- **Criterion 5 (Power Workout Display Fidelity)**:
  - *Given* a workout with recorded power data (`avgPower > 0.0`),
  - *When* mapped and rendered in `WorkoutDetails`,
  - *Then* `AnaerobicBatteryRow` is rendered normally displaying `Min X kJ (Y%)`.

### 1.4 System Invariants

- **Rule 51 Internal Seeding Parity**: Internal capacity accumulator state (`mCurrentWPrimeBalJoules`) must remain seeded in constructor and `resetSession()` so that when a power meter connects, initial capacity is ready immediately at 100%.
- **Rule 52 Sensor-Presence Gating**: Virtual metric devices must NOT publish non-null values to `MySensor` unless the underlying physical/source sensor (`SensorType.POWER`) is active/registered, and `WorkoutDataMapper` must gate derived summary rows on recorded source telemetry.
- **Rule 20 DTO Mapping Symmetry**: Both `fromCursor` and `fromSnapshot` must yield identical results for identical cursor rows.
- **Modularity Budget**: `WorkoutDetails.kt` must remain strictly `< 500` lines (currently 494 lines).
- **Human Gate Sovereignty**: Moving parent ticket `ATT-4368` to `Erledigt` is reserved strictly for the human user (`Core Rule 3`).
- **Clean-Room Test Pass Rate**: Full test suite (`./gradlew testDebugUnitTest`) must maintain a 100% pass rate.

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   Refines `REQ-TRK-038.1` (*Constructor State Seeding Parity (Rule 51)*, introduced in `ATT-4283`). Targets `CyclingPowerMetricsDevice.java`, `CyclingPowerMetricsDeviceTest.kt`, `WorkoutDataMapper.kt`, `WorkoutDataMappingSymmetryTest.kt`, and `WorkoutDetails.kt`.

2. **Historical Origin & Commit Trace**:
   Introduced in Sprint 2026-43.1 via commit `a4e878f5` (`fix(ATT-4283): Seed initial W'bal capacity on startup and align W/kg & W'bal behavior when stationary or paused`). Refined in Sprint 2026-43.2 (`ATT-4368`) after physical device verification revealed phantom W'bal rows on non-power workouts.

3. **Root Reason for Existing Formulation**:
   In `ATT-4283`, `CyclingPowerMetricsDevice` was updated because unit tests had been masking an uninitialized `mCurrentWPrimeBalJoules = null` state on device startup. When instantiated in production for a power ride, the live cockpit battery gauge rendered empty (`--%` / `-- kJ`) until the first power packet arrived. `REQ-TRK-038.1` mandated calling `resetSession()` and publishing `mWPrimeBalSensor.newValue((double) wPrimeKj)` immediately on device creation so that the cockpit gauge renders full 100% capacity from second zero. However, `BANALService` instantiates `CyclingPowerMetricsDevice` across all sports, and fallback CP (200W) and fallback W' (20.0 kJ) are always non-zero, causing `mWPrimeBalSensor.newValue(...)` to be published unconditionally without checking for a power meter, which injected phantom `20.0 kJ` extrema into `TrackerService`'s database for every workout.

4. **Preservation of Core Invariants**:
   Rule 51 internal seeding parity preserved (internal accumulator `mCurrentWPrimeBalJoules` is seeded so initial capacity is ready immediately upon power meter connection); Rule 52 sensor-presence gating enforced (virtual sensor `mWPrimeBalSensor` only publishes non-null when `SensorType.POWER` is registered); Rule 20 DTO mapping symmetry preserved across `fromCursor` and `fromSnapshot`; legacy non-power database records protected at mapper layer; `WorkoutDetails.kt` line budget strictly `< 500` lines; and clean-room full test suite pass rate remains 100%.

---

## 2. Test Specification (TST-TRK-031)

### Test Case 1: `testStartup_noPowerSensor_doesNotPublishWPrimeBalSensor` (`[TST-TRK-031.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: `sensorManager.getSensor(SensorType.POWER)` returns `null`.
* **Action**:
  1. Instantiate `CyclingPowerMetricsDevice` with effective CP = 250W and W' = 20.0 kJ.
  2. Inspect `wPrimeBalSensor.sensorData.value`.
  3. Invoke `calculateMetrics()` with `isSuspended = true`.
  4. Inspect `wPrimeBalSensor.sensorData.value`.
* **Expected Result**:
  - `wPrimeBalSensor.sensorData.value` is strictly `null` upon instantiation and remains `null` during suspended calculations (Rule 52).

### Test Case 2: `testStartup_constructorWithPowerSensor_seedsInitialWPrimeCapacity` (`[TST-TRK-031.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: `sensorManager.getSensor(SensorType.POWER)` returns active `powerSensor`.
* **Action**:
  1. Instantiate `CyclingPowerMetricsDevice` with CP = 280W (and FTP fallback 250W) and W' = 20.0 kJ.
  2. Inspect `wPrimeBalSensor.sensorData.value`.
  3. Instantiate with W' = 0.0f.
* **Expected Result**:
  - Case 1: `wPrimeBalSensor.sensorData.value` immediately emits `20.0` (Double, delta 0.001) without requiring `calculateMetrics()` or `resetSession()` (Rule 51).
  - Case 2: `wPrimeBalSensor.sensorData.value` is `null`.

### Test Case 3: `testCalculateMetrics_recordingSuspended_stationaryZeroPower_updatesPowerToWeightSensor` (`[TST-TRK-031.3]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: `isSuspended = true`, `athleteWeightKg = 75.0f`.
* **Action**:
  1. Set `power = 0.0W`, invoke `calculateMetrics()`.
  2. Set `power = 150.0W`, invoke `calculateMetrics()`.
* **Expected Result**:
  - Case 1: `powerToWeightSensor.sensorData.value == 0.0`.
  - Case 2: `powerToWeightSensor.sensorData.value == 2.0`.
  - Cumulative metrics (`normalizedPowerSensor`, `workKjSensor`, `tssSensor`) remain `null`.

### Test Case 4: `testCalculateMetrics_transientInvalidPowerDrop_preservesWPrimeBalanceAndCumulativeSensors` (`[TST-TRK-031.4]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: Active ride with CP = 250W, W' = 20.0 kJ, power = 300W on tick 1.
* **Action**:
  1. Simulate 1-second transient dropout (`power = null`) on tick 2.
  2. Invoke `calculateMetrics()`.
* **Expected Result**:
  - `wPrimeBalSensor`, `normalizedPowerSensor`, `intensityFactorSensor`, and `tssSensor` retain their values from tick 1 (not wiped to `null`).

### Test Case 5: `testCalculateMetrics_unconfiguredAthleteWeight_publishesNullPowerToWeightEvenWhenSuspended` (`[TST-TRK-031.5]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDeviceTest.kt`
* **Preconditions**: Athlete weight = 0.0f.
* **Action**:
  - Invoke `calculateMetrics()` in active and suspended states.
* **Expected Result**:
  - `powerToWeightSensor.sensorData.value` is strictly `null`.

### Test Case 6: `testWorkoutDataMapper_noPowerData_suppressesWPrimeBalSummarySymmetrically` (`[TST-TRK-031.6]`)
* **Scope**: Architecture / Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMappingSymmetryTest.kt`
* **Preconditions**: Cursor and snapshot containing legacy SQLite extrema `rawMinWPrimeBal = 20.0 kJ` but `avgPower = null` and `maxPower = null`.
* **Action**:
  1. Map via `WorkoutDataMapper.fromCursor(cursor)`.
  2. Map via `WorkoutDataMapper.fromSnapshot(snapshot, batch)`.
* **Expected Result**:
  - Both mapping pathways yield `minWPrimeBalKj == null`, `minWPrimeBalPct == null`, and `isEmptyTankWarning == false` (Rule 20 & Rule 52).
  - When `avgPower = 200.0 W` and `rawMinWPrimeBal = 15.0 kJ`, both map `minWPrimeBalKj == 15.0 kJ` and `minWPrimeBalPct == 75.0%`.

### Test Case 7: `testWorkoutDetails_noPowerData_omitsAnaerobicBatteryRowAndDivider` (`[TST-TRK-031.7]`)
* **Scope**: UI Layout / Modularity Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsLayoutTest.kt`
* **Preconditions**: `WorkoutDetailsData` constructed with `minWPrimeBalKj = null` (non-power workout).
* **Action**:
  1. Assemble visible sections in `WorkoutDetails.kt`.
  2. Audit line count of `WorkoutDetails.kt`.
* **Expected Result**:
  - `AnaerobicBatteryRow` is not added to visible sections.
  - `WorkoutDetails.kt` line count is strictly `< 500` lines.

### Test Case 8: 9-Language Localization & Specifier Audit (`[TST-TRK-031.8]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify string presence and specifiers across all 9 canonical locales (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`) for tokens `power_to_weight`, `power_to_weight_short`, `units_power_to_weight`, `w_prime_battery_pct`, `w_prime_warning_low_battery`, `workout_w_prime_min_kj_label`, `workout_w_prime_min_pct_label`.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 9: Clean-Room Full Suite Regression Execution (`[TST-TRK-031.9]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the complete unit test suite.
* **Expected Result**: Zero regressions, 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-TRK-031.1]` | Unit | `CyclingPowerMetricsDevice.resetSession` / `calculateMetrics` (no power sensor) | `REQ-TRK-038.1`, `REQ-TRK-038.4` (Rule 52) | Specified |
| `[TST-TRK-031.2]` | Unit | `CyclingPowerMetricsDevice.<init>` (power sensor present) | `REQ-TRK-038.1` (Rule 51) | Specified |
| `[TST-TRK-031.3]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics` (suspended) | `REQ-TRK-038.2`, `REQ-TRK-038.4` | Specified |
| `[TST-TRK-031.4]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics` (dropout) | `REQ-TRK-038.3` | Specified |
| `[TST-TRK-031.5]` | Unit | `CyclingPowerMetricsDevice.calculateMetrics` (unconfigured weight) | `REQ-TRK-038.2` | Specified |
| `[TST-TRK-031.6]` | Contract | `WorkoutDataMapper.resolveWPrimeBalSummary` / `fromCursor` / `fromSnapshot` | `REQ-TRK-038.5` (Rule 20 & Rule 52) | Specified |
| `[TST-TRK-031.7]` | UI / Contract | `WorkoutDetails.kt` (presentation gating & < 500 lines) | `REQ-TRK-038.5` | Specified |
| `[TST-TRK-031.8]` | Localization | `TranslationParityTest` | `REQ-TRK-038.6`, `REQ-UI-106` | Specified |
| `[TST-TRK-031.9]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
