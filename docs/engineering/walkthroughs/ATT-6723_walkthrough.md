# ATT-6723 Walkthrough: Wire CardioPowerDynamicsCard, Decimated Simulation & Y-Axis Bounds

**Ticket**: [ATT-6723](https://rainerblind.atlassian.net/browse/ATT-6723)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Sprint**: `2026-46.2` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: [`REQ-REV-010`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_aftermath_reviews.md) | **Test Mapping**: [`TST-REV-011`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_reviews.md)  
**Branch**: `feature/ATT-6723`  

---

## 1. Executive Summary

`ATT-6723` resolves 3 aftermath regressions originating from `ATT-3905` across presentation, physiology, and telemetry graphing layers:
- **CardioPowerDynamicsCard Production Wiring (`WorkoutDetailTabbedContent.kt`)**: Mounted `CardioPowerDynamicsCard` under `WorkoutDetailSectionType.DETAILS` whenever `enableCardioPowerDynamics == true && workoutData.cardioPowerDynamics != null`, eliminating the zero-caller island component regression (`Rule 81`) and surfacing the `ⓘ` Info button (`PowerHrDynamicsInfoDialog`), RMSE, $HR_0$ baseline comparison, and fatigued window breakdown.
- **Preference Gating Decoupling (`WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`)**: Decoupled full details gating from the compact summary toggle by passing `showCardioPowerInWorkoutSummary = true` to `WorkoutDetails(...)`. Unchecking *"Show in Workout Summary"* now strictly scopes suppression to list cards (`WorkoutSummary.kt`).
- **Timestamp-Aware Simulation (`PowerHrDynamicsCalculator.kt`)**: Added `simulateTrajectory(powerSamples, timestampsSec, tau, hr0, k)` computing step-wise $\Delta t_i = (timestampsSec[i] - timestampsSec[i-1]).\text{coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$. Restores mathematically accurate exponential kinetics across decimated multi-hour trackpoints ($\Delta t \in [3\text{s}, 10\text{s}+]$).
- **Dynamic Y-Axis Domain Bounds (`TelemetryMetricGraph.kt`)**: Passed `timestampsSec = pathPoints.map { it.timeSec }` and included non-null `freshHrTrajectory` values in `(dataMin, dataMax)` for `HEART_RATE` when the overlay is active, preventing canvas boundary clipping on surges and descents.

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-010.2` | `TST-REV-011.7` | `WorkoutDetailTabbedContent.kt`, `CardioPowerDynamicsCard.kt` | Mount card in full details with standard margins | **Verified** |
| `REQ-REV-010.3` | `TST-REV-011.5` | `PowerHrDynamicsCalculator.kt` | Step-wise $\Delta t$ trajectory simulation across decimation | **Verified** |
| `REQ-REV-010.3` | `TST-REV-011.6` | `TelemetryMetricGraph.kt` | Dynamic Y-axis bound inclusion prevents canvas clipping | **Verified** |
| `REQ-REV-010.4` | `TST-REV-011.7` | `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt` | Decouple full details gating from summary toggle | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope (Rule 60)**:
  - Scope: `Tier 1 targeted: physiology.PowerHrDynamicsCalculatorTest, ui.map.TelemetryMetricGraphOverlayTest, ui.components.workoutdetails.*, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*`
  - Commands executed:
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculatorTest"` (Passed)
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculatorTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphOverlayTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.*"` (Passed)
    - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"` (Passed)
    - `python3 tools/verify_requirement_governance.py --text-file docs/engineering/test_specs/ATT-6723_test_spec.md` (Passed, 0 Composable islands, 0 encapsulation violations)
  - Result: **100% PASS** (zero regressions, zero failures).
- **Device Policy**:
  - Android emulator permanently retired per explicit PO directive (`Rule 79`). Physical device evaluation reserved for Ceremony 2 Sprint Review on real Pixel 10 hardware.
