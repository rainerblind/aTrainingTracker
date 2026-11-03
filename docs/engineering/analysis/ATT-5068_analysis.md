# Stage 1 Analysis: ATT-5068 - [Bug] W' values shown (and probably also calculated) although no power available

**Ticket**: [[ATT-5068]](https://rainerblind.atlassian.net/browse/ATT-5068)  
**Sub-task**: [[ATT-5089]](https://rainerblind.atlassian.net/browse/ATT-5089) (`[Analysis]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Branch**: `feature/ATT-5068`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Problem Statement & Motivation
* **Current Behavior**: On cycling workouts recorded without an active power meter (e.g. *Zum Bäcker #45*), the *W'-Bilanz (Anaerober Akku)* chart displays a flat horizontal line at full capacity (~20.0 kJ) across `MapDetailLayout`, `TrackOnMapScreen`, and `WorkoutSummary`. `W_PRIME_BAL` telemetry samples are actively generated and recorded despite no power data being available.
* **Expected Behavior**: Workouts without actual numeric power telemetry MUST NOT calculate, step, or record `W_PRIME_BAL` samples, and post-workout aftermath charts/views MUST suppress the W'-Bilanz chart when power telemetry is absent (`!hasPowerData(path)`).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA Call Sites**:
  1. `CyclingPowerMetricsDevice.java` (`resetSession()` L282 & `calculateMetrics()` L348): Only checked `mMySensorManager.getSensor(SensorType.POWER) != null`. When a power meter is paired in settings/profile, `getSensor()` is non-null even if offline/asleep. `resetSession()` published `mWPrimeBalSensor.newValue(wPrimeKj)` before receiving a single sample, and `isSuspended` stepped reconstitution at 0W.
  2. `TrackerService.java` (L2104-2115): Polled `getBestSensorData(W_PRIME_BAL)` at 1Hz, persisting 20.0 kJ rows into `WorkoutSamples.db` and extrema (`MIN`, `AVG`, `END`) into `WorkoutSummaries.db`.
  3. `TelemetryMetricUtils.kt` (L118-120 `hasWPrimeBalData`): Only checked `it.wPrimeBalKj != null && it.wPrimeBalKj >= 0.0` without requiring `hasPowerData(path)`.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Verified ticket hypothesis against live sprint codebase and historical commits (`a4e878f5` `ATT-4283`, `9fce3cd7` `ATT-4368`).
  * `WorkoutDataMapper.kt` (L123-126) already gates `resolveWPrimeBalSummary` on `hasPowerData` (hiding summary stats), but the UI charts in `WorkoutSummary.kt` (L393), `TrackOnMapScreen.kt` (L623), and `MapDetailLayout.kt` (L485) rely solely on `TelemetryMetricUtils.hasWPrimeBalData()`, causing the flat chart to render on both new and historical power-less workouts.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Gate `CyclingPowerMetricsDevice` W'bal publishing/stepping on actual received numeric power samples (`mActivePowerDurationSec > 0`).
  * In `resetSession()`, seed internal capacity `mCurrentWPrimeBalJoules` (Rule 51) but publish `mWPrimeBalSensor.newValue(null)`.
  * Update `TelemetryMetricUtils.hasWPrimeBalData(path)` to require `hasPowerData(path)`.
  * Update unit tests in `CyclingPowerMetricsDeviceTest.kt` and `TelemetryMetricUtilsTest.kt`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Altering W/kg stationary pause logic (`REQ-TRK-038.2`) or other power metrics (`NP`, `IF`, `TSS`).
  * Retroactive database migrations or SQLite schema changes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-038` (`REQ-TRK-038.1`, `REQ-TRK-038.4`), `REQ-UI-399` (`REQ-UI-399.3`).
* **Historical Origin & Commit Trace**: `ATT-4283` (Rule 51 constructor parity), `ATT-4368` (Rule 52 sensor-presence gating, commit `9fce3cd7`), `ATT-3862` (W'bal engine & charts).
* **Root Reason for Existing Formulation**: `ATT-4368` assumed checking `getSensor(SensorType.POWER) != null` was sufficient to distinguish power vs non-power workouts, overlooking paired/retained sensors with zero samples. `REQ-UI-399.3` assumed presence of `wPrimeBalKj` implied valid power data.
* **Preservation of Core Invariants**: Retains Rule 51 constructor state seeding while enforcing Rule 52: virtual sensors MUST NOT publish data without real source telemetry. Adding `hasPowerData(path)` to `hasWPrimeBalData(path)` safely cleanses both future recordings and legacy flat-line charts.

---

## 5. Architectural Strategy & High-Level Solution
* **`CyclingPowerMetricsDevice.java`**:
  * `resetSession()`: Seed `mCurrentWPrimeBalJoules` internally, but emit `mWPrimeBalSensor.newValue(null)`.
  * `calculateMetrics()`: During `isSuspended`, only step reconstitution and publish `mWPrimeBalSensor` if `mActivePowerDurationSec > 0`. On active tracking, publish `mWPrimeBalSensor` only after active power sample increments `mActivePowerDurationSec`.
* **`TelemetryMetricUtils.kt`**:
  * `hasWPrimeBalData(path)`: `hasPowerData(path) && path.any { it.wPrimeBalKj != null && it.wPrimeBalKj >= 0.0 }`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regressions in power-equipped workouts; thread safety on sensor single-thread executors; living doc traceability maintained.
* **Risk Rating**: **LOW**. Localized guard conditions in virtual sensor driver and telemetry utility; zero UI layout or DB migrations.
