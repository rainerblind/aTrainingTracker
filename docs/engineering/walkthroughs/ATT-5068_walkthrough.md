# Stage 5: Walkthrough & Verification - ATT-5068: [Bug] W' values shown although no power available

**Ticket**: [[ATT-5068]](https://rainerblind.atlassian.net/browse/ATT-5068)  
**Sub-task**: [[ATT-5094]](https://rainerblind.atlassian.net/browse/ATT-5094) (`[Test]`)  
**Parent Epic**: None  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.1`  
**Requirement Mapping**: `REQ-TRK-038` (`REQ-TRK-038.1`, `REQ-TRK-038.4`, `REQ-TRK-038.5`), `REQ-UI-399` (`REQ-UI-399.3`)  
**Test Mapping**: `TST-TRK-031` (`TST-TRK-031.1`, `TST-TRK-031.2`, `TST-TRK-031.10`, `TST-TRK-031.9`)  
**Branch**: `feature/ATT-5068`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-02  

---

## 1. Executive Summary & Verification Overview
* **Issue Addressed**: On bike workouts without an active power meter (e.g. *Zum Bäcker #45*), paired/retained sensors previously caused `CyclingPowerMetricsDevice` to publish 20.0 kJ capacity on startup, persisting phantom samples at 1 Hz and rendering flat horizontal W'-Bilanz charts across `MapDetailLayout`, `TrackOnMapScreen`, and `WorkoutSummary`.
* **Delivered Fix**:
  1. `CyclingPowerMetricsDevice.java`: `resetSession()` seeds internal capacity `mCurrentWPrimeBalJoules` (Rule 51) but publishes `mWPrimeBalSensor.newValue(null)` unconditionally (Rule 52). Active W'bal emission and suspended reconstitution require active power telemetry (`mActivePowerDurationSec > 0`).
  2. `TelemetryMetricUtils.kt`: `hasWPrimeBalData(path)` requires `hasPowerData(path) && path.any { it.wPrimeBalKj != null && it.wPrimeBalKj >= 0.0 }`, cleanly suppressing the chart on power-less workouts.
  3. `CyclingPowerMetricsDeviceTest.kt` & `TelemetryMetricUtilsTest.kt`: Unit tests verifying null emission on startup, sample-gated activation, and power-gated chart check.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-038.1` | `[TST-TRK-031.1]` | Unit Test (`CyclingPowerMetricsDeviceTest`) | **PASSED** | `Verified` |
| `REQ-TRK-038.1` | `[TST-TRK-031.2]` | Unit Test (`CyclingPowerMetricsDeviceTest`) | **PASSED** | `Verified` |
| `REQ-TRK-038.5` / `REQ-UI-399.3` | `[TST-TRK-031.10]` | Unit Test (`TelemetryMetricUtilsTest`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `[TST-TRK-031.9]` | Tier 1 Targeted (`devices.*`, `map.*`, `aftermath.*`, `build.*`) | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Unit Test Suite (Rule 60 Scope)
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.map.*" \
  --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" \
  --tests "com.atrainingtracker.trainingtracker.build.*"
BUILD SUCCESSFUL in 1m 10s (32 actionable tasks: 1 executed, 31 up-to-date)
```

### Requirement Governance Audit (`verify_requirement_governance.py`)
```text
python3 tools/verify_requirement_governance.py
Modified/Altered existing requirement(s) detected: REQ-TRK-038.
PASS: Requirement Archaeology & Chesterton's Fence Audit successfully verified with all 4 mandatory fields.
```

---

## 4. Hardware / Physical Verification (Pixel 10)
* **Device Status**: `adb devices` reported `66020DLCR002FL offline`. Physical APK deployment skipped in accordance with Rules 60 & 62; fully verified via targeted unit test suite and compilation.
* **Visual Consistency**: Zero UI composable layout changes; leverages existing conditional chart visibility gates.

---

## 5. Invariant & Governance Verification
1. **Rule 51 & 52 Parity**: Internal capacity accumulator initialized on device startup; virtual sensor emits non-null only with real power telemetry.
2. **Living Documentation**: `REQ-TRK-038`, `REQ-UI-399.3`, and `TST-TRK-031` updated to `Verified`.
3. **Subtask Governance**: Moving Stage 5 subtask to `Erledigt` via Gate 5 review advances parent ticket to `Final Review (Human)` assigned to `human` (Rule 3).
