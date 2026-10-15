# Stage 1 Analysis: ATT-1290 - Live Normalized Power (NP) and Intensity Factor (IF) calculations

**Ticket**: [ATT-1290](https://rainerblind.atlassian.net/browse/ATT-1290)  
**Sub-task**: [ATT-3635](https://rainerblind.atlassian.net/browse/ATT-3635) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1290`  
**Requirement Mapping**: `REQ-TRK-020` (complementing `REQ-TRK-019`, `REQ-UI-370`, `REQ-UI-373`, `REQ-UI-374`, and `REQ-FIL-012`)  
**Test Mapping**: `TST-TRK-012`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

In cycling telemetry, simple arithmetic average power grossly underestimates physiological strain and metabolic cost on variable or rolling terrain, during interval workouts, and in pack riding. A stochastic ride with repeated hard surges (e.g., alternating between 400 W for 15 seconds and 0 W coasting) produces the same average power (200 W) as a steady-state recovery spin, yet the physiological impact—glycogen depletion, cardiovascular load, and neuromuscular strain—is exponentially higher.

To quantify true physiological demands, Dr. Andrew Coggan developed the **Normalized Power (NP)** algorithm and the dimensionless **Intensity Factor (IF)**:
1. **Normalized Power (NP)**: Models physiological response through a 4th-power weighting function reflecting the non-linear relationship between exercise intensity and physiological stress (e.g. glycogenolysis and lactate accumulation).
2. **Intensity Factor (IF)**: Normalizes the workout's Normalized Power relative to the athlete's Functional Threshold Power:
   $$\text{IF} = \frac{\text{NP}}{\text{FTP}}$$
   This gives the athlete an immediate, real-time indicator of relative pacing intensity (e.g., endurance pacing at 0.70–0.75 vs. threshold/sweetspot pacing at 0.85–0.95).

Currently, `aTrainingTracker` records raw power and computes instant power-to-weight ratio (`POWER_TO_WEIGHT`, ATT-1289), but does not compute live Normalized Power or Intensity Factor during active workouts. Athletes have no immediate physiological stress pacing metric on their bike cockpit.

---

## 2. Mathematical Formalization & Algorithmic Design

### 2.1 The Coggan Normalized Power (NP) Algorithm
The live NP calculation is executed continuously at 1 Hz across active tracking:

1. **1-Second Power Sampling**:
   Every 1 second of non-paused recording, obtain the current smoothed power sample $P(t) \ge 0.0\text{ W}$.
   *Coasting Policy*: If the rider is coasting ($P = 0\text{ W}$), this is a genuine physiological recovery event and MUST be included in the calculation.
   *Dropout Policy*: If the sensor is lost or disconnected (null), the tick is skipped or treated as unconfigured.

2. **Rolling 30-Second Average Window ($P_{30s}$)**:
   Maintain a FIFO queue (circular buffer) of length 30:
   $$W = [P(t-29), P(t-28), \dots, P(t)]$$
   When the queue is filled ($|W| = 30$):
   $$P_{30s}(t) = \frac{1}{30} \sum_{i=0}^{29} P(t-i)$$

3. **4th Power Weighting**:
   Raise the 30-second rolling average to the 4th power:
   $$y(t) = \left(P_{30s}(t)\right)^4$$

4. **Cumulative Moving Average of 4th Powers**:
   Accumulate the sum $S_4$ and sample counter $N$ across all valid rolling seconds since workout inception:
   $$S_4 = \sum_{k=1}^N y(t_k), \quad \bar{Y} = \frac{S_4}{N}$$

5. **4th Root Extraction**:
   The instantaneous ride Normalized Power is the 4th root of the cumulative average of 4th powers:
   $$\text{NP} = \sqrt[4]{\bar{Y}} = \left(\frac{1}{N} \sum_{k=1}^N \left(P_{30s}(t_k)\right)^4\right)^{1/4}$$

### 2.2 Boundary & Warm-Up Conditions
- **$t < 30\text{ seconds}$ (Warm-up window)**:
  Before 30 seconds of active recording have accumulated, the 30s rolling average is incomplete. In accordance with Coggan's specification, NP is undefined during this initialization window and the tile displays `"-- W"`.
- **Zero Power**:
  If all 30 samples are $0\text{ W}$, $P_{30s} = 0$, $y(t) = 0$, $\text{NP} = 0\text{ W}$.
- **Numerical Overflow Guard**:
  Because $P^4$ for $1000\text{ W}$ equals $10^{12}$, standard 64-bit IEEE 754 floating-point `double` ($1.7 \times 10^{308}$) comfortably accommodates up to billions of hours of tracking without precision loss or overflow.

### 2.3 Intensity Factor (IF) Algorithm
Given current workout $\text{NP}$ and the athlete's configured $\text{FTP} \in [50, 600]\text{ W}$ from `SettingsDataStore`:
$$\text{IF} = \frac{\text{NP}}{\text{FTP}}$$
- **Unconfigured FTP Guard**:
  If $\text{FTP} \le 0$ or unset (`null`), IF cannot be determined mathematically; the tile renders `"--"` and tapping it prompts the athlete to configure their FTP in Zone Settings.
- **Precision & Formatting**:
  Formatted to 2 decimal places (`0.00`, e.g., `0.85` or `1.02`) via dedicated `IntensityFactorFormatter`.

---

## 3. Scope Bounding & Delineation (ATT-1250)

### In-Scope (Strict Target):
1. **Sensor Definitions**:
   - `SensorType.NORMALIZED_POWER`: units `W`, formatted with `IntegerFormatter` (e.g. `245 W`).
   - `SensorType.INTENSITY_FACTOR`: dimensionless decimal, formatted with new `IntensityFactorFormatter` (`0.00`, e.g. `0.82`).
   - Icon mapping: `R.drawable.ic_power`.
2. **Computed Engine Integration**:
   - Extend `CyclingPowerMetricsDevice.java` to calculate NP and IF in its existing 1Hz scheduled loop.
   - Maintain 30-sample rolling FIFO buffer and cumulative 4th power sums.
   - Thread-safe synchronization and shutdown.
   - Injected `FtpProvider` interface for deterministic, unit-testable FTP retrieval without static mocking.
3. **Sensor Filters & Presets**:
   - `SensorFilterDefaults.kt`: Configure `NORMALIZED_POWER` and `INTENSITY_FACTOR` with default `FilterType.INSTANTANEOUS` (since they are already smoothed calculated metrics).
4. **Activity Types**:
   - Add `NORMALIZED_POWER` and `INTENSITY_FACTOR` to `ActivityType.BIKE_POWER`.
5. **Cockpit UI & Fallback**:
   - Allow placing NP and IF in tracking cockpit grid.
   - In `EditSensorFieldDialog.kt`, when `INTENSITY_FACTOR` is selected and FTP is unconfigured, display guidance button to configure FTP in Zone Settings.
6. **9-Language Localization**:
   - Full translation parity for `normalized_power`, `normalized_power_short`, `intensity_factor`, `intensity_factor_short` across EN, DE, ES, FR, IT, JA, NL, PL, and PT.

### Out-of-Scope (Deferred to Future Tickets):
- Post-workout retroactive calculation of TSS (Training Stress Score) and Work in kJ (explicitly tracked in next ticket `ATT-1291`).
- Lap-specific Normalized Power (Lap NP) and Lap IF (can be added as secondary sensor types in future iterations).
- Heart rate based intensity metrics (hrIF / hrTSS).

---

## 4. Chesterton's Fence & Invariant Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   Net-new requirement `REQ-TRK-020`. Complements `REQ-TRK-019` (*Power-to-weight ratio*), `REQ-UI-370` (*7-Zone Coggan Cycling Power Model*), `REQ-UI-373` (*Automated Training Zone Calculation*), and `REQ-UI-374` (*Contextual Zone Settings Navigation*). Targets `CyclingPowerMetricsDevice.java`, `SensorType.java`, `IntensityFactorFormatter.java`, `EditSensorFieldDialog.kt`, and `strings.xml`.
2. **Historical Origin & Commit Trace**:
   Cycling power support introduced originally in `BANALService`. Sprint 2026-42.4 established the 7-Zone Coggan model (`ATT-3184`), automatic zone calculation from FTP (`ATT-3188`), contextual sensor pairing navigation (`ATT-3189`), and real-time power-to-weight ratio (`ATT-1289`, commit `5eee662f`).
3. **Root Reason for Existing Formulation**:
   Previous architecture only tracked instantaneous power and smoothed power over 3s/10s/30s. NP requires continuous cumulative state across the entire ride session.
4. **Preservation of Core Invariants**:
   - *Pause Gating Invariant*: Neither the 30s rolling buffer nor the cumulative 4th power sums advance when `TrainingApplication.isRecordingSuspended()` is true.
   - *DataStore Preference Integrity*: FTP is cleanly read from existing `SettingsDataStore.KEY_CYCLING_FTP` without schema mutation.
   - *Database Schema Invariant*: `WorkoutSamples` columns are generated via `makeColumns(SensorType.values())`. Adding enum entries preserves SQLite table structure.
   - *100% Test Pass Rate*: Pure unit tests for the 30s buffer, 4th power summation, mathematical edge cases, and locale formatting must pass with zero regression.

---

## 5. Architectural & Implementation Strategy

```mermaid
flowchart TD
    subgraph BANALService ["BANALService Engine (1 Hz Periodic Tick)"]
        CPS[Cycling Power Sensor] -->|1Hz Power Sample| CPMD[CyclingPowerMetricsDevice]
        PAUSE{isRecordingSuspended?|
        PAUSE -->|Yes| SKIP[Skip Tick / Freeze State]
        PAUSE -->|No| PUSH[Push to 30s FIFO Queue]
    end

    subgraph NPCalc ["Normalized Power Engine"]
        PUSH --> BUF_CHECK{Queue size == 30?}
        BUF_CHECK -->|No < 30s| UNDEF[NP = null, IF = null]
        BUF_CHECK -->|Yes >= 30s| AVG30[Compute 30s Rolling Mean P_30s]
        AVG30 --> POW4[Compute P_30s ^ 4]
        POW4 --> ACC[Sum S_4 += y_t, N++]
        ACC --> ROOT4[NP = S_4 / N ^ 0.25]
    end

    subgraph IFCalc ["Intensity Factor Engine"]
        ROOT4 --> FTP_CHECK{FTP Configured > 0?}
        FTP_CHECK -->|No| IF_NULL[IF = null]
        FTP_CHECK -->|Yes| IF_VAL[IF = NP / FTP]
    end

    subgraph Sensors ["MySensor Dispatches"]
        ROOT4 -->|newValue NP| NPS[mNormalizedPowerSensor]
        IF_VAL -->|newValue IF| IFS[mIntensityFactorSensor]
        UNDEF -->|newValue null| NPS
        UNDEF -->|newValue null| IFS
        IF_NULL -->|newValue null| IFS
    end

    subgraph UI ["Cockpit & Settings"]
        NPS -->|IntegerFormatter| TILE_NP["Cockpit Tile: '245 W'"]
        IFS -->|IntensityFactorFormatter| TILE_IF["Cockpit Tile: '0.85'"]
        UNDEF --> TILE_DASH["Cockpit Tile: '--'"]
    end
```

### Key Components to Implement / Modify:
1. `IntensityFactorFormatter.java`:
   - Extends `MyFormatter<Number>`.
   - Returns `"--"` when null, NaN, Infinite, or negative.
   - Formats finite values with 2 decimals (`0.00`, locale-aware).
2. `SensorType.java`:
   - Add `NORMALIZED_POWER` (units `W`, `IntegerFormatter`).
   - Add `INTENSITY_FACTOR` (dimensionless, `IntensityFactorFormatter`).
3. `CyclingPowerMetricsDevice.java`:
   - Introduce `mPowerToWeightSensor`, `mNormalizedPowerSensor`, `mIntensityFactorSensor`.
   - Add `FtpProvider` interface (`Integer getFtp(Context context)`).
   - Maintain `ArrayDeque<Double> mPowerWindow30s` and `double mSum4thPower`, `long mCount4thPower`.
   - In `calculateMetrics()`, compute NP and IF when active.
4. `ActivityType.java`:
   - Add `NORMALIZED_POWER` and `INTENSITY_FACTOR` to `BIKE_POWER`.
5. `SensorFilterDefaults.kt`:
   - Register default `FilterType.INSTANTANEOUS` for `NORMALIZED_POWER` and `INTENSITY_FACTOR`.
6. `EditSensorFieldDialog.kt`:
   - When `INTENSITY_FACTOR` is selected, display FTP configuration status and guidance button if FTP is unconfigured.
7. `strings.xml` (All 9 locales):
   - Add translations for NP and IF.

---

## 6. Verification Criteria & Deliverables Plan
- **Stage 1**: Analysis deliverable, Gate 1 audit.
- **Stage 2**: Requirement `REQ-TRK-020` and Test Spec `TST-TRK-012`, Gate 2 audit.
- **Stage 3**: Implementation Plan `docs/engineering/plans/ATT-1290_plan.md`, Gate 3 audit.
- **Stage 4**: Construction & unit testing (`NormalizedPowerMathTest.kt`, `IntensityFactorFormatterTest.kt`, `CyclingPowerMetricsDeviceTest.kt`), Gate 4 audit.
- **Stage 5**: Walkthrough `docs/engineering/walkthroughs/ATT-1290_walkthrough.md`, clean-room regression `./gradlew testDebugUnitTest`, integration into `sprint/2026-42.4`.
