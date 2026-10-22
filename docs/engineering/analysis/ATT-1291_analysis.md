# Stage 1 Analysis: ATT-1291 - Real-time Training Stress Score (TSS) and Mechanical Work (kJ) metrics

**Ticket**: [ATT-1291](https://rainerblind.atlassian.net/browse/ATT-1291)  
**Sub-task**: [ATT-3640](https://rainerblind.atlassian.net/browse/ATT-3640) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://rainerblind.atlassian.net/browse/ATT-235) (*UI Improvements & Visual Modernization*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-42.4`  
**Branch**: `feature/ATT-1291`  
**Requirement Mapping**: `REQ-TRK-021` (complementing `REQ-TRK-019`, `REQ-TRK-020`, `REQ-UI-370`, `REQ-UI-373`, and `REQ-UI-374`)  
**Test Mapping**: `TST-TRK-013`  
**Author**: AI Agent 1 (Implementer)  
**Reviewer / Auditor**: AI Agent 2 (Auditor)  
**Date**: 2026-10-15  

---

## 1. Problem Statement & Motivation

During endurance cycling and interval training, athletes need reliable real-time metrics to monitor both cumulative external mechanical energy expenditure and internal physiological training load:

1. **Mechanical Work (kJ)**:
   - Mechanical work represents the exact physical energy delivered by the cyclist's legs to the bicycle's drivetrain:
     $$W = \int P(t) \, dt \quad \text{[Joules]}$$
     $$\text{Work (kJ)} = \frac{W}{1000}$$
   - Because the human body operates at approximately 20%–25% gross metabolic efficiency when cycling (~24% on average), and $1\text{ kcal} = 4.184\text{ kJ}$, $1\text{ kJ}$ of mechanical work corresponds almost 1:1 to $1\text{ kcal}$ of metabolic energy burned:
     $$\text{Metabolic Energy (kcal)} \approx \frac{\text{Work (kJ)}}{4.184 \times 0.24} \approx \text{Work (kJ)} \times 0.996 \approx \text{Work (kJ)}$$
   - Consequently, mechanical work in kJ is the gold-standard metric for caloric tracking and fueling strategies during rides.

2. **Training Stress Score (TSS)**:
   - Dr. Andrew Coggan formulated the Training Stress Score (TSS) to quantify cumulative physiological workload, incorporating both workout duration and intensity:
     $$\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 3600} \times 100$$
     where:
     - $t$ is the elapsed active moving duration in seconds.
     - $\text{NP}$ is Normalized Power (Watts), capturing physiological intensity via 4th-power weighting (`REQ-TRK-020`).
     - $\text{IF}$ is Intensity Factor ($\text{NP} / \text{FTP}$).
     - $\text{FTP}$ is the athlete's Functional Threshold Power (Watts).
     - $3600$ is seconds per hour.
     - The factor 100 standardizes the metric such that exactly 1 hour at FTP ($\text{NP} = \text{FTP}$, $\text{IF} = 1.0$) yields exactly 100 TSS points.
   - Pacing by target TSS is fundamental in modern cycling (e.g., targeting a 150 TSS century or capping a recovery ride at 40 TSS).

Currently, `aTrainingTracker` computes live power-to-weight ratio (`POWER_TO_WEIGHT`, ATT-1289), Normalized Power (`NORMALIZED_POWER`, ATT-1290), and Intensity Factor (`INTENSITY_FACTOR`, ATT-1290), but does not accumulate real-time Mechanical Work (kJ) or live Training Stress Score (TSS).

---

## 2. Mathematical Formalization & Algorithmic Design

### 2.1 Mechanical Work (kJ)
In the 1Hz telemetry sampling loop of `CyclingPowerMetricsDevice.java`:
- Every 1 second of non-paused recording where power $P(t) \ge 0.0\text{ W}$:
  $$\Delta W = P(t) \times 1.0\text{ s} \quad \text{[Joules]}$$
  $$W_{\text{total}} = \sum_{k=1}^N \Delta W_k \quad \text{[Joules]}$$
  $$\text{Work (kJ)} = \frac{W_{\text{total}}}{1000.0}$$
- **Coasting Policy ($0\text{ W}$)**: During coasting, $P = 0\text{ W}$, adding $0\text{ J}$ to total work ($W_{\text{total}}$ remains unchanged).
- **Pause Gating**: When recording is paused or auto-paused (`isRecordingSuspended() == true`), no work is accumulated.
- **Sensor Dropout / Disconnect**: If the power meter disconnects (null, NaN, infinite, or negative), the tick is skipped without corrupting $W_{\text{total}}$.
- **Formatting**: Rendered as an integer (e.g., `450`, `1240`) via `IntegerFormatter`, with unit `kJ`.

### 2.2 Live Training Stress Score (TSS)
- **Active Duration Tracking ($t$)**:
  - Accumulated active moving duration during which power was actively tracked (in seconds).
  - Ticked at 1Hz synchronously with power ingestion when recording is not suspended and power is valid ($P \ge 0.0\text{ W}$, including coasting $0\text{ W}$).
- **Warm-Up Window ($t < 30\text{ s}$)**:
  - While $t < 30\text{ s}$, Normalized Power and Intensity Factor are undefined (`null`).
  - Therefore, TSS is undefined (`null`), rendered as `"--"`.
- **Calculation ($t \ge 30\text{ s}$)**:
  - If $\text{FTP} > 0$ and $\text{NP} \ge 0.0$ and $\text{IF} \ge 0.0$:
    $$\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 3600.0} \times 100.0$$
    which can be equivalently computed as:
    $$\text{TSS} = \frac{t \times \text{NP} \times \text{IF}}{\text{FTP} \times 36.0}$$
  - If FTP is unconfigured ($\le 0$ or null), TSS publishes `null` (rendered as `"--"`).
- **Formatting**: Rendered as an integer (e.g. `45`, `100`, `152`) via `IntegerFormatter`, with unit `""` (`units_none`).
- **Numerical Precision**: In IEEE 754 64-bit `double`, after a grueling 24-hour ultra-endurance ride ($86,400\text{ s}$), total work at $400\text{ W}$ is $34,560,000\text{ J} = 34,560\text{ kJ}$, and TSS is $\approx 2400$. Both values remain orders of magnitude below the 53-bit exact integer limit ($9 \times 10^{15}$), eliminating any truncation or numerical stability issues.

---

## 3. Scope Bounding & Delineation (ATT-1250)

### In-Scope (Strict Target):
1. **Sensor Definitions in `SensorType.java`**:
   - `SensorType.WORK_KJ`: `fullNameId = R.string.work_kj`, `shortNameId = R.string.work_kj_short`, `unitId = R.string.units_work_kj`, `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`.
   - `SensorType.TSS`: `fullNameId = R.string.tss`, `shortNameId = R.string.tss_short`, `unitId = R.string.units_none`, `SensorValueType.DOUBLE`, `IntegerFormatter`, `isInstantaneous = false`.
   - Icon mapping: `case POWER, POWER_TO_WEIGHT, NORMALIZED_POWER, INTENSITY_FACTOR, WORK_KJ, TSS -> R.drawable.ic_power;`.
2. **Computed Telemetry Engine (`CyclingPowerMetricsDevice.java`)**:
   - Add sensors for `WORK_KJ` and `TSS`.
   - Maintain `mWorkJoules` accumulator and `mActivePowerDurationSec` counter.
   - Synchronize with pause gating (`isRecordingSuspended()`).
   - Clean reset in `resetSession()`.
   - Add injected `ActiveTimeProvider` (or constructor overload) for deterministic, clean unit testing.
3. **Sensor Filters & Presets**:
   - In `SensorFilterDefaults.kt`, assign `FilterType.INSTANTANEOUS` (1.0s direct) for `WORK_KJ` and `TSS`.
4. **Activity Types**:
   - Register `SensorType.WORK_KJ` and `SensorType.TSS` in `ActivityType.BIKE_POWER`.
5. **Cockpit UI & Contextual Guidance**:
   - In `EditSensorFieldDialog.kt`, selecting `TSS` when FTP is unconfigured displays the FTP guidance row with direct navigation to Zone Settings (matching `INTENSITY_FACTOR`).
6. **9-Language Localization**:
   - Define string resources `work_kj`, `work_kj_short`, `units_work_kj`, `tss`, and `tss_short` across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
7. **Comprehensive Unit Testing**:
   - Mathematical tests covering Mechanical Work integration, coasting, pause gating, and TSS computation.
   - Translation parity tests.
   - Clean-room regression suite.

### Out-of-Scope (Deferred):
- Visual zone gauge cockpit tile (`ATT-1293`).
- Visual pedal power balance tile (`ATT-1295`).
- Retroactive historical database migrations for old workouts.

---

## 4. Chesterton's Fence Audit & Architectural Archaeology

1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-TRK-021` complementing `REQ-TRK-019` (Power-to-weight ratio) and `REQ-TRK-020` (Normalized Power & Intensity Factor).
   - Targets `CyclingPowerMetricsDevice.java`, `SensorType.java`, `ActivityType.java`, `SensorFilterDefaults.kt`, `EditSensorFieldDialog.kt`, and `strings.xml`.

2. **Historical Origin & Commit Trace**:
   - Power telemetry was introduced in early `BANALService`.
   - In Sprint `2026-42.4`, `CyclingPowerMetricsDevice` was introduced for W/kg (`ATT-1289`, commit `5eee662f`) and extended for NP and IF (`ATT-1290`, commit `4fe4996c`).

3. **Root Reason for Existing Formulation**:
   - `CyclingPowerMetricsDevice` runs at 1Hz on an isolated thread, decoupling heavy math and stateful accumulations from BLE/ANT+ event dispatch threads.
   - Extending `CyclingPowerMetricsDevice` to integrate mechanical work and TSS follows this established architectural design without creating additional threads or services.

4. **Preservation of Core Invariants**:
   - **Pause Invariant**: All accumulations must freeze when paused or auto-paused.
   - **FTP Configuration Reactivity**: Changing FTP mid-workout updates TSS immediately without resetting accumulated active time or work.
   - **Coasting Invariant**: 0W coasting is real physical riding and advances active duration, but adds 0 J to mechanical work.
   - **Full-Suite Regression**: 100% test pass rate across all existing unit and contract tests.

---

## 5. Risk Assessment & Mitigation

| Risk | Impact | Mitigation Strategy |
|:---|:---|:---|
| **Division by zero if FTP is 0 or negative** | High (Crash / NaN) | Strictly check `ftp != null && ftp > 0` before calculating TSS; publish `null` otherwise. |
| **Warmup window instability** | Medium (Erratic initial values) | TSS requires valid NP and IF; while $t < 30\text{ s}$, publish `null` (displays `"--"`). |
| **Integer overflow in work integration** | Low | Total joules tracked in `double` (up to $1.7 \times 10^{308}$). |
| **UI freeze during long rides** | Low | 1Hz scheduled executor executes minimal arithmetic operations in microsecond timeframe. |
| **Missing localization in non-EN locales** | Medium (UI broken / crash) | Comprehensive 9-locale parity verified via `TranslationParityTest`. |
