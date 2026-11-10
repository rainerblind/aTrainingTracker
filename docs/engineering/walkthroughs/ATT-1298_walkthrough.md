# Stage 5: Walkthrough & Verification - ATT-1298: Live MMP Curve & Rolling/Peak Metrics

**Ticket**: [ATT-1298](https://rainerblind.atlassian.net/browse/ATT-1298) | **Sub-task**: `[Test]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-1298`  
**Requirement Mapping**: `REQ-TRK-058` (`REQ-TRK-058.1`–`5`) | **Test Mapping**: `TST-TRK-058` (`TST-TRK-058.1`–`5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Executive Summary & Verification Overview

Implemented real-time Mean Maximal Power (MMP) duration accumulators (5s, 1m, 5m, 20m), 8 new `SensorType` metrics, and a Compose `LiveMmpCurveChart` vector chart for the live-tracking cockpit:
* **SlidingWindowPowerAccumulator**: High-efficiency $O(1)$ circular ring-buffer accumulator supporting the growing warm-up window ($1 \le k < W$, Rule 40 / `REQ-TRK-023`) and monotonic session peak tracking.
* **CyclingPowerMetricsDevice**: Integrated 4 duration accumulators, registered 8 new sensors under `SensorCategory.POWER_AND_PEDALING`, and enforced pause suspension freezing (`REQ-TRK-058.5`) and `resetSession()` parity (Rule 51).
* **9-Language String Parity**: Configured `strings_cockpit.xml` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 100% parity verified by `TranslationParityTest`.
* **LiveMmpCurveChart**: Composed vector curve chart on logarithmic time scale (1s to 2h) with benchmark markers and zero snapshot-state writes during composition (Rule 66).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-058.1` | `TST-TRK-058.1` | `SlidingWindowPowerAccumulatorTest` | **PASSED** | `Verified` |
| `REQ-TRK-058.2` | `TST-TRK-058.2` | `CyclingPowerMetricsDeviceMmpTest` | **PASSED** | `Verified` |
| `REQ-TRK-058.3` | `TST-TRK-058.3` | `SensorTypeMmpTest`, `TranslationParityTest` | **PASSED** | `Verified` |
| `REQ-TRK-058.4` | `TST-TRK-058.4` | `LiveMmpCurveChartTest` | **PASSED** | `Verified` |
| `REQ-TRK-058.5` | `TST-TRK-058.2` | `CyclingPowerMetricsDeviceMmpTest` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Test Scope (Runtime: ~2m 36s)
* **Executed Scope**:
  * `--tests "com.atrainingtracker.banalservice.devices.*"`
  * `--tests "com.atrainingtracker.banalservice.sensor.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.tracking.*"`
  * `--tests "com.atrainingtracker.trainingtracker.physiology.*"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  * `--tests "com.atrainingtracker.trainingtracker.ui.components.core.*"`
  * `--tests "com.atrainingtracker.trainingtracker.build.*"`
  * `--tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
* **Result**: `BUILD SUCCESSFUL` (100% PASS across 861 targeted unit and contract tests under `tools/parallel_sprint_lock.py`).
* **Device Inspection**: No physical device connected in `adb devices`; per Rule 79 (`ATT-5890`), emulator launch omitted.

---

## 4. Key Implementation Highlights

```kotlin
// app/src/main/java/com/atrainingtracker/trainingtracker/physiology/SlidingWindowPowerAccumulator.kt
class SlidingWindowPowerAccumulator(val windowSeconds: Int) {
    fun addSample(power: Double) { ... }
    fun getAverage(): Double? { if (internalCount == 0) return null; return runningSum / internalCount }
    fun getPeak(): Double? = peakAverage
    fun reset() { ... }
}
```

---

## 5. Invariants & Preserved Behavior
1. **Rule 40 & Rule 52**: Rolling metrics compute immediately from second 1; sensors emit `null` before first valid power sample.
2. **Rule 51 Reset Parity**: `resetSession()` cleanly resets all accumulators and peak registers.
3. **Rule 66 State Hygiene**: Zero `MutableState` snapshot writes during composition in `LiveMmpCurveChart`.
4. **Existing Power Telemetry**: `POWER`, `NP`, `IF`, `WORK_KJ`, `TSS`, and `W_PRIME_BAL` 100% regression-free.
