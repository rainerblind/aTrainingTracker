# Stage 3: Implementation Plan - ATT-1298: Live MMP Curve & Rolling/Peak Metrics

**Ticket**: [ATT-1298](https://rainerblind.atlassian.net/browse/ATT-1298) | **Sub-task**: [ATT-6387](https://rainerblind.atlassian.net/browse/ATT-6387) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-1298`  
**Requirement Mapping**: `REQ-TRK-058` (`REQ-TRK-058.1`–`5`) | **Test Mapping**: `TST-TRK-058` (`TST-TRK-058.1`–`5`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Architecture Overview

Athletes need real-time Mean Maximal Power (MMP) duration benchmarks (5s, 1m, 5m, 20m) and a dynamic vector MMP curve chart in the cockpit. We implement $O(1)$ ring-buffer accumulators in `CyclingPowerMetricsDevice`, 8 new `SensorType` metrics with 9-language parity, and a Compose `LiveMmpCurveChart` with zero recomposition churn.

---

## 2. Traceability & Requirements Mapping

* `REQ-TRK-058.1` (O(1) Accumulators & Warmup) $\to$ `TST-TRK-058.1` (`SlidingWindowPowerAccumulatorTest`)
* `REQ-TRK-058.2` (Rolling & Peak Telemetry) $\to$ `TST-TRK-058.2` (`CyclingPowerMetricsDeviceMmpTest`)
* `REQ-TRK-058.3` (SensorType & 9-Locale Parity) $\to$ `TST-TRK-058.3` (`SensorTypeMmpTest`, `TranslationParityTest`)
* `REQ-TRK-058.4` (Live MMP Curve Composable) $\to$ `TST-TRK-058.4` (`LiveMmpCurveChartTest`)
* `REQ-TRK-058.5` (Pause & Reset Invariants) $\to$ `TST-TRK-058.2` (`CyclingPowerMetricsDeviceMmpTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 40 & Rule 52**: Rolling metrics compute over available warmup samples ($1 \le k < W$) without `null` delay; sensors publish `null` until valid power telemetry arrives.
2. **Rule 51 Reset Parity**: `resetSession()` cleanly resets all accumulators and peak registers.
3. **Rule 66 Composition Safety**: No `MutableState` snapshot writes inside `LiveMmpCurveChart` composition body.
4. **Pause Isolation**: `isSuspended == true` freezes accumulators without zero-watt sample contamination.

---

## 4. Proposed Architectural Changes

* **Domain / Telemetry Layer**:
  - `SlidingWindowPowerAccumulator.kt`: Circular ring buffer of size $W$, $O(1)$ sample push and running sum.
  - `CyclingPowerMetricsDevice.java`: Add 4 accumulators (5s, 60s, 300s, 1200s), 8 new `MySensor` fields, 1Hz updates.
  - `SensorType.java`: Add 8 enum entries under `SensorCategory.POWER_AND_PEDALING` with `ic_power`.
* **Presentation Layer (`LiveMmpCurveChart.kt`)**:
  - Logarithmic duration X-axis ($1\text{s} \dots 7200\text{s}$) with callout pins for 5s, 1m, 5m, 20m.
  - **UI Consistency**: Reuses `MaterialTheme.colorScheme` / `TTColor.chartLine`, spacing `12.dp`/`16.dp`, shapes `MaterialTheme.shapes.medium`. No custom one-off themes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: O(1) Circular Sliding-Window Accumulator (`SlidingWindowPowerAccumulator.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/physiology/SlidingWindowPowerAccumulator.kt`
* Add class with circular array, $O(1)$ `addSample(value)`, `getAverage()`, `getPeak()`, `reset()`.

### Step 2: SensorType Registration & 9-Locale String Parity
* Files: `SensorType.java`, `app/src/main/res/values*/strings_cockpit.xml` (all 9 locales)
* Add `POWER_5S`, `POWER_1M`, `POWER_5M`, `POWER_20M`, `POWER_PEAK_5S`, `POWER_PEAK_1M`, `POWER_PEAK_5M`, `POWER_PEAK_20M`.

### Step 3: CyclingPowerMetricsDevice Telemetry Wiring & Pause Gating
* Files: `app/src/main/java/com/atrainingtracker/banalservice/devices/CyclingPowerMetricsDevice.java`
* Wire accumulators, expose sensors, update at 1Hz, handle `isSuspended` freeze and `resetSession()` cleanup.

### Step 4: Compose Live MMP Curve Component (`LiveMmpCurveChart.kt`)
* Files: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/LiveMmpCurveChart.kt`
* Implement vector chart with log-scale axis, benchmark pins, and pure Canvas drawing.

### Step 5: Unit Tests, Verification & Architecture Sync
* Files: `SlidingWindowPowerAccumulatorTest.kt`, `CyclingPowerMetricsDeviceMmpTest.kt`, `SensorTypeMmpTest.kt`, `LiveMmpCurveChartTest.kt`, `docs/architecture.md`
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest"`
