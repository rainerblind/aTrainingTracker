# Stage 1 Analysis: ATT-4368 — [Bug] Hide W'bal row in Workout Summary when workout has no recorded power data

**Ticket**: [ATT-4368](https://rainerblind.atlassian.net/browse/ATT-4368)  
**Sub-task**: [ATT-4412](https://rainerblind.atlassian.net/browse/ATT-4412) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.2`  
**Branch**: `feature/ATT-4368`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

During the Sprint 2026-43.1 Joint Review (`ATT-4292`), physical device verification on a Google Pixel 10 (see ticket attachment `att_workout_summary_wbal.png`) revealed a visual and physiological defect in the Workout Summary aftermath presentation:
* **Current Behavior**: On non-power workouts (e.g., walking, running, swimming, or outdoor cycling without a paired power meter), the `WorkoutSummary` overview card and `WorkoutDetails` telemetry section still display a phantom `W'bal` (Anaerobic Battery) row:
  ```text
  W'bal: Min 20.0 kJ (100%)
  ```
* **Expected Behavior**: The `W'bal` row represents Dr. Philip Skiba's anaerobic work capacity expenditure and reconstitution ($W'$ balance), which is strictly derived from cycling power output relative to Critical Power ($CP$). Workouts without recorded power data MUST NOT display the `W'bal` summary row. Only workouts with active, recorded power data (`avgPower != null || maxPower != null`) and configured $W'$ capacity should resolve and display the Anaerobic Battery section in `WorkoutDetails`.

**Motivation**:
Displaying a 100% full anaerobic battery gauge for a 5 km foot walk or trail run confuses athletes, clutters the workout summary card with meaningless physiological telemetry, and violates core domain modeling standards (`docs/coding_standards.md` Section 1.8, Rules 51 & 52). Eliminating phantom virtual sensor emissions and gating summary presentation ensures clean, context-accurate workout cards for every sport type.

---

## 2. Root Cause Analysis (Forensic Investigation)

A comprehensive forensic investigation across `CyclingPowerMetricsDevice.java`, `TrackerService.java`, `WorkoutDataMapper.kt`, and `WorkoutDetails.kt` identified a two-layer failure mechanism:

### 2.1 Layer 1: Virtual Sensor Device Driver (`CyclingPowerMetricsDevice.java`)
1. **Unconditional Sensor Emission on Device Instantiation (Rule 51 vs. Rule 52 Conflict)**:
   - In ticket `ATT-4283` (commit `a4e878f5`), Constructor State Seeding Parity (Rule 51 / `REQ-TRK-038.1`) was implemented to ensure that a cycling workout with a paired power meter displays 100% full capacity from second zero before the first power packet arrives.
   - To achieve this, the constructor calls `resetSession()`, which contains:
     ```java
     Integer cp = resolveEffectiveCpWatts();
     float wPrimeKj = mWPrimeKjProvider.getWPrimeKj(mContext);
     if (cp != null && cp > 0 && wPrimeKj > 0.0f) {
         mCurrentWPrimeBalJoules = wPrimeKj * 1000.0;
         if (mWPrimeBalSensor != null) {
             mWPrimeBalSensor.newValue((double) wPrimeKj);
         }
     }
     ```
   - However, `resolveEffectiveCpWatts()` provides a hardcoded fallback (`DEFAULT_FALLBACK_CP_WATTS = 200`) when neither $CP$ nor $FTP$ is configured in settings, and `mWPrimeKjProvider.getWPrimeKj()` defaults to `20.0f`. Consequently, `cp > 0 && wPrimeKj > 0.0f` is unconditionally true.
   - `CyclingPowerMetricsDevice` is instantiated by `BANALService` as a core computed device for all activities.
   - In `resetSession()`, `mWPrimeBalSensor.newValue((double) wPrimeKj)` was invoked **without checking whether a `SensorType.POWER` sensor is actually registered in `MySensorManager`**.
2. **Suspended Emission During Pre-Tracking & Auto-Pause**:
   - In `CyclingPowerMetricsDevice.calculateMetrics()`, when `isSuspended == true` (which is active before tracking starts and during pauses):
     ```java
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
     ```
   - This block also lacked a sensor presence check, continuing to publish `20.0 kJ` every 1 second while recording was suspended, even when no power meter was present.
3. **Database Extrema Contamination in `TrackerService`**:
   - In `TrackerService.java`, `SensorType.W_PRIME_BAL` is included in `SENSORS_TO_TRACK`.
   - In the 1 Hz sensor polling loop (`TrackerService.java` lines 2092–2130), `banalService.getBestSensorData(SensorType.W_PRIME_BAL)` retrieves the seeded `20.0 kJ` value.
   - `LiveWorkoutSession.addSample(SensorType.W_PRIME_BAL, 20.0, currentPos)` is called, establishing running stats with `min = 20.0`, `max = 20.0`, `avg = 20.0`.
   - `mWorkoutRepository.updateExtremaValue(mWorkoutID, SensorType.W_PRIME_BAL, ExtremaType.MIN, stats.min, stats.minPos)` writes a row into the SQLite `workout_extrema` table:
     ```sql
     INSERT INTO workout_extrema (workout_id, sensor_type, extrema_type, value)
     VALUES (5023, 'W_PRIME_BAL', 'MIN', 20.0);
     ```
   - Hence, every recorded workout—even a 5-minute walk without sensors—persists a phantom `W_PRIME_BAL` minimum extrema in the local database.

### 2.2 Layer 2: Summary Mapper & UI Presentation Defense (`WorkoutDataMapper.kt` & `WorkoutDetails.kt`)
1. **Unconditional Extrema Resolution in `WorkoutDataMapper.kt`**:
   - In `WorkoutDataMapper.kt`, `resolveWPrimeBalSummary` is defined as:
     ```kotlin
     internal fun resolveWPrimeBalSummary(
         rawMinWPrimeBalKj: Double?,
         effectiveWPrimeKj: Double = SettingsDataStoreJavaHelper.getEffectiveWPrimeKj(context).toDouble()
     ): Triple<Double?, Double?, Boolean> {
         val validMinKj = rawMinWPrimeBalKj?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0.0 && it < Double.MAX_VALUE }
             ?: return Triple(null, null, false)
         val pct = if (effectiveWPrimeKj > 0.0) {
             PowerPhysiologyCalculator.calculateWPrimeBalancePct(wPrimeBal = validMinKj, wPrimeTotal = effectiveWPrimeKj)
         } else null
         val isEmptyTank = pct != null && pct < PowerPhysiologyCalculator.EMPTY_TANK_WARNING_THRESHOLD_PCT
         return Triple(validMinKj, pct, isEmptyTank)
     }
     ```
   - `resolveWPrimeBalSummary` evaluates solely whether `rawMinWPrimeBalKj` is a valid positive number. It does **not** check whether the workout actually contains recorded power data.
   - In contrast, `resolvePowerTrainingSummary()` explicitly checks:
     ```kotlin
     if (validNp == null && validAvgPower == null && validMaxPower == null &&
         validIf == null && validTss == null && validWorkKj == null &&
         validAvgPtw == null && validMaxPtw == null) {
         return PowerTrainingSummary()
     }
     ```
   - Because `rawMinWPrimeBal` was present in SQLite (`20.0 kJ`), `resolveWPrimeBalSummary()` returned `Triple(20.0, 100.0, false)`, which was mapped to `WorkoutData.minWPrimeBalKj = 20.0` and `minWPrimeBalPct = 100.0` across both `fromCursor` and `fromSnapshot` (Rule 20).
2. **Presentation in `WorkoutDetails.kt`**:
   - `WorkoutDetails.kt` (lines 152–154) renders the Anaerobic Battery section whenever:
     ```kotlin
     if (enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null) {
         add { AnaerobicBatteryRow(data = data) }
     }
     ```
   - Because `data.minWPrimeBalKj` and `data.minWPrimeBalPct` were non-null, the UI faithfully rendered the row, displaying `Min 20.0 kJ (100%)` for non-power activities.
   - Furthermore, even after fixing `CyclingPowerMetricsDevice` for future workouts, historical workouts stored in the user's database still contain the legacy phantom `W_PRIME_BAL` extrema row. Therefore, gating `WorkoutDataMapper.resolveWPrimeBalSummary()` on the presence of recorded power telemetry (`avgPower != null || maxPower != null`) is strictly required to protect historical records.

---

## 3. User Scope Grounding (ATT-1250)

To avoid scope creep and maintain strict ASPICE compliance, changes are bounded strictly to the reported defect:

* **In-Scope Goals**:
  1. **Sensor-Presence Gating in Driver (`CyclingPowerMetricsDevice.java` — Rule 52)**:
     - In `resetSession()` and suspended `calculateMetrics()`, check `mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null` before calling `mWPrimeBalSensor.newValue(...)`.
     - Retain Rule 51 constructor capacity seeding parity for the internal accumulator (`mCurrentWPrimeBalJoules = wPrimeKj * 1000.0`) so that when a power meter connects, initial capacity is ready immediately at 100%.
  2. **Presentation Defense & Historical Parity (`WorkoutDataMapper.kt` — Rule 52 / Rule 20)**:
     - In `WorkoutDataMapper.kt`, gate `resolveWPrimeBalSummary()` on the presence of recorded power telemetry (`hasPowerData = (avgPowerWatts != null || maxPowerWatts != null)`).
     - If `!hasPowerData`, return `Triple(null, null, false)` so `minWPrimeBalKj` and `minWPrimeBalPct` evaluate to `null` on `WorkoutData` and `WorkoutDetailsData`.
     - Apply this check symmetrically in both single-row `fromCursor()` and batch `fromSnapshot()` mapping pathways (Rule 20).
  3. **UI Display Integrity (`WorkoutDetails.kt`)**:
     - Verify that `WorkoutDetails.kt` renders `AnaerobicBatteryRow` only when power data exists and both `minWPrimeBalKj` and `minWPrimeBalPct` are non-null.
     - Ensure `WorkoutDetails.kt` strictly preserves its modularity constraint (< 500 lines; currently 494 lines).
  4. **Targeted Unit Test Verification**:
     - Update `CyclingPowerMetricsDeviceTest.kt` to verify that when no `SensorType.POWER` sensor exists in `MySensorManager`, `wPrimeBalSensor` publishes `null` on constructor, `resetSession()`, and suspended `calculateMetrics()`.
     - Update `WorkoutDataMappingSymmetryTest.kt` to verify that when power extrema are absent, `minWPrimeBalKj` and `minWPrimeBalPct` remain `null` even if a legacy `rawMinWPrimeBal` row exists in SQLite.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Do NOT modify Skiba W' balance differential equation or reconstitution math in `PowerPhysiologyCalculator.kt`.
  - Do NOT modify Normalized Power (NP), Intensity Factor (IF), or TSS calculations when power is active.
  - Do NOT alter SQLite schema or drop `workout_extrema` table columns in `WorkoutSummariesDatabaseManager`.
  - Do NOT alter live cockpit layout definitions or tab navigation in `TrackingViewsDatabaseManager` or `TrackingTabsViewModel`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:  
  Refines `REQ-TRK-038.1` (*Constructor State Seeding Parity (Rule 51)*, introduced in `ATT-4283`).  
  Targets:
  - `CyclingPowerMetricsDevice.java`
  - `CyclingPowerMetricsDeviceTest.kt`
  - `WorkoutDataMapper.kt`
  - `WorkoutDataMappingSymmetryTest.kt`
  - `WorkoutDetails.kt`

* **Historical Origin & Commit Trace**:  
  Introduced in Sprint 2026-43.1 via commit `a4e878f5` (`fix(ATT-4283): Seed initial W'bal capacity on startup and align W/kg & W'bal behavior when stationary or paused`).

* **Root Reason for Existing Formulation**:  
  In `ATT-4283`, `CyclingPowerMetricsDevice` was updated because unit tests had been masking an uninitialized `mCurrentWPrimeBalJoules = null` state on device startup. When instantiated in production for a power ride, the live cockpit battery gauge rendered empty (`--%` / `-- kJ`) until the first power packet arrived. `REQ-TRK-038.1` mandated calling `resetSession()` and publishing `mWPrimeBalSensor.newValue((double) wPrimeKj)` immediately on device creation so that the cockpit gauge renders full 100% capacity from second zero.  
  However, the author did not anticipate that `BANALService` instantiates `CyclingPowerMetricsDevice` across all sports, including running and walking where no power meter is paired. Because fallback CP (200W) and fallback W' (20.0 kJ) are always non-zero, `mWPrimeBalSensor.newValue(...)` was published unconditionally, injecting phantom `20.0 kJ` extrema into `TrackerService`'s database for every workout.

* **Preservation of Core Invariants**:  
  - **Rule 51 Internal Seeding Parity**: The internal accumulator `mCurrentWPrimeBalJoules` remains fully initialized to `wPrimeKj * 1000.0` in the constructor and `resetSession()`. If a power meter is paired, the initial capacity is ready immediately.
  - **Rule 52 Sensor-Presence Gating**: Virtual metric sensors (`mWPrimeBalSensor`) MUST NOT publish non-null values to `MySensor` unless the underlying physical/source sensor (`SensorType.POWER`) is registered and available in `MySensorManager`.
  - **Rule 20 DTO Symmetry**: `WorkoutDataMapper.fromCursor` and `fromSnapshot` continue to map identically.
  - **Legacy Database Protection**: Historical non-power workouts with stale `W_PRIME_BAL` extrema are safely filtered at the mapper layer, preventing phantom UI rows without requiring destructive SQLite migration scripts.

---

## 5. Architectural Strategy & High-Level Solution

```mermaid
flowchart TD
    subgraph Layer 1: Hardware & Device Driver
        A[CyclingPowerMetricsDevice Instantiation / resetSession] --> B{mMySensorManager has SensorType.POWER?}
        B -- Yes --> C[mWPrimeBalSensor.newValue wPrimeKj]
        B -- No --> D[mWPrimeBalSensor.newValue null]
        C --> E[TrackerService records EXTREMA in SQLite]
        D --> F[No EXTREMA recorded in SQLite]
    end

    subgraph Layer 2: Aftermath Mapper & UI
        G[WorkoutDataMapper.fromCursor / fromSnapshot] --> H{avgPower != null || maxPower != null?}
        H -- Yes --> I[resolveWPrimeBalSummary returns valid Min Kj & Pct]
        H -- No --> J[resolveWPrimeBalSummary returns null, null, false]
        I --> K[WorkoutDetails renders AnaerobicBatteryRow]
        J --> L[WorkoutDetails hides AnaerobicBatteryRow]
    end
```

### 5.1 Component Changes
1. **`CyclingPowerMetricsDevice.java`**:
   - In `resetSession()`:
     ```java
     boolean hasPowerSensor = (mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null);
     if (cp != null && cp > 0 && wPrimeKj > 0.0f) {
         mCurrentWPrimeBalJoules = wPrimeKj * 1000.0;
         if (mWPrimeBalSensor != null) {
             mWPrimeBalSensor.newValue(hasPowerSensor ? (double) wPrimeKj : null);
         }
     } else {
         mCurrentWPrimeBalJoules = null;
         if (mWPrimeBalSensor != null) {
             mWPrimeBalSensor.newValue(null);
         }
     }
     ```
   - In `calculateMetrics()` when `isSuspended`:
     ```java
     boolean hasPowerSensor = (mMySensorManager != null && mMySensorManager.getSensor(SensorType.POWER) != null);
     if (hasPowerSensor && mCurrentWPrimeBalJoules != null) {
         // Reconstitute during pause and publish new value
     }
     ```
2. **`WorkoutDataMapper.kt`**:
   - Update `resolveWPrimeBalSummary` signature and contract:
     ```kotlin
     internal fun resolveWPrimeBalSummary(
         rawMinWPrimeBalKj: Double?,
         effectiveWPrimeKj: Double = SettingsDataStoreJavaHelper.getEffectiveWPrimeKj(context).toDouble(),
         hasPowerData: Boolean = false
     ): Triple<Double?, Double?, Boolean> {
         if (!hasPowerData) return Triple(null, null, false)
         val validMinKj = rawMinWPrimeBalKj?.takeIf { !it.isNaN() && !it.isInfinite() && it >= 0.0 && it < Double.MAX_VALUE }
             ?: return Triple(null, null, false)
         val pct = if (effectiveWPrimeKj > 0.0) {
             PowerPhysiologyCalculator.calculateWPrimeBalancePct(wPrimeBal = validMinKj, wPrimeTotal = effectiveWPrimeKj)
         } else null
         val isEmptyTank = pct != null && pct < PowerPhysiologyCalculator.EMPTY_TANK_WARNING_THRESHOLD_PCT
         return Triple(validMinKj, pct, isEmptyTank)
     }
     ```
   - In `fromCursor(cursor: Cursor)`:
     ```kotlin
     val avgPowerWattsVal = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.POWER, ExtremaType.AVG)
     val maxPowerWattsVal = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.POWER, ExtremaType.MAX)
     val hasPowerData = (avgPowerWattsVal != null && avgPowerWattsVal > 0.0) || (maxPowerWattsVal != null && maxPowerWattsVal > 0.0)
     val rawMinWPrimeBal = workoutSummariesDatabaseManager.getExtremaValue(workoutId, SensorType.W_PRIME_BAL, ExtremaType.MIN)
     val (minWPrimeBalKj, minWPrimeBalPct, isEmptyTankWarning) = resolveWPrimeBalSummary(rawMinWPrimeBal, hasPowerData = hasPowerData)
     ```
   - In `fromSnapshot(snapshot, batch)`:
     ```kotlin
     val avgPowerWattsBatch = getBatchVal(SensorType.POWER, ExtremaType.AVG)
     val maxPowerWattsBatch = getBatchVal(SensorType.POWER, ExtremaType.MAX)
     val hasPowerData = (avgPowerWattsBatch != null && avgPowerWattsBatch > 0.0) || (maxPowerWattsBatch != null && maxPowerWattsBatch > 0.0)
     val rawMinWPrimeBal = getBatchVal(SensorType.W_PRIME_BAL, ExtremaType.MIN)
     val (minWPrimeBalKj, minWPrimeBalPct, isEmptyTankWarning) = resolveWPrimeBalSummary(rawMinWPrimeBal, hasPowerData = hasPowerData)
     ```
3. **`WorkoutDetails.kt`**:
   - `buildVisibleWorkoutDetailSections()` already checks:
     `if (enableCriticalPowerModel && showCriticalPowerInWorkoutSummary && data.minWPrimeBalKj != null && data.minWPrimeBalPct != null)`.
   - When `data.minWPrimeBalKj` is `null`, `AnaerobicBatteryRow` is not added to the section list and no horizontal divider is rendered.
   - Line count will be maintained strictly below the 500-line modularity threshold.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero Regression in Power Rides**: Workouts with a paired power meter must continue to compute and display $W'_{\text{bal}}$ telemetry in live cockpit tiles, aftermath summaries, and route map graphs without alteration.
  2. **Rule 51 Constructor State Seeding Parity**: Internal capacity accumulator state (`mCurrentWPrimeBalJoules`) must remain seeded in constructor and `resetSession()` so that when a power meter connects, initial capacity is ready immediately at 100%.
  3. **Rule 52 Sensor-Presence Gating**: Virtual metric devices must NOT publish non-null values to `MySensor` unless the underlying physical/source sensor (`SensorType.POWER`) is active/registered, and `WorkoutDataMapper` must gate derived summary rows on recorded source telemetry.
  4. **Rule 20 DTO Mapping Symmetry**: Both `fromCursor` and `fromSnapshot` must yield identical results for identical cursor rows.
  5. **Modularity Budget**: `WorkoutDetails.kt` must remain strictly `< 500` lines.
  6. **Clean-Room Test Pass Rate**: Full test suite (`./gradlew testDebugUnitTest`) must maintain a 100% pass rate.

* **Risk Rating**: **LOW**
  - **Technical Justification**:
    - Purely defensive conditional gating in `CyclingPowerMetricsDevice` and `WorkoutDataMapper`.
    - No changes to database schema, migrations, or SQL tables.
    - No changes to Skiba physiological algorithms or Coggan power metrics.
    - Zero risk to activities with power meters; completely eliminates phantom rows on non-power activities.
