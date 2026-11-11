# Stage 3: Implementation Plan - ATT-6723: Wire CardioPowerDynamicsCard, Timestamp-Aware Fresh Model Overlay & Y-Axis Inclusion

**Ticket**: [ATT-6723](https://rainerblind.atlassian.net/browse/ATT-6723)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6723`  
**Requirement Mapping**: `REQ-REV-010` (refines `REQ-REV-010.2`, `REQ-REV-010.3`, `REQ-REV-010.4`) | **Test Mapping**: `TST-REV-011` (`TST-REV-011.5`, `TST-REV-011.6`, `TST-REV-011.7`)  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In `ATT-3905` (commit `1b33fcba`), `CardioPowerDynamicsCard.kt` was authored as an aftermath summary card with header `ⓘ` Info action (`PowerHrDynamicsInfoDialog`), RMSE, $HR_0$ baseline comparison, and fatigued window breakdown. However:
1. `CardioPowerDynamicsCard.kt` currently has **zero production callers** in `app/src/main/` (`Rule 81` / `ATT-6289`), leaving full Workout Details without access to the card and its Info dialog.
2. `WorkoutDetailTabbedContent.kt` passed `showCardioPowerInWorkoutSummary = tuningConfig.showCardioPowerInWorkoutSummary` to `WorkoutDetails.kt`. When an athlete toggles off *"Show in Workout Summary"*, it inadvertently hides Cardio-Power Dynamics inside full Workout Details rather than scoping suppression strictly to compact workout list cards (`WorkoutSummary.kt`).
3. `PowerHrDynamicsCalculator.simulateTrajectory` assumes a constant scalar $dt = 1.0$. On decimated `pathPoints` across multi-hour rides ($\Delta t \in [3\text{s}, 10\text{s}+]$), this produces an artificial $3\times\text{--}10\times$ phase lag in the simulated fresh model curve.
4. `TelemetryMetricGraph.kt` computes Y-axis `(dataMin, dataMax)` bounds for `HEART_RATE` solely from measured HR, causing the dashed fresh model curve to clamp flat against canvas top/bottom boundaries whenever simulated values exceed or dip below actual HR extremes.

---

## 2. System Invariants & Preserved Behavior
1. **Production Reachability (`Rule 81` — `ATT-6289`)**: `CardioPowerDynamicsCard` is mounted in `WorkoutDetailTabbedContent.kt` with standard card margins (`horizontal = 16.dp, vertical = 4.dp`).
2. **Preference Isolation**: `showCardioPowerInWorkoutSummary` only gates compact list cards (`WorkoutSummary.kt`); full details (`WorkoutDetailTabbedContent.kt`) displays Cardio-Power whenever `enableCardioPowerDynamics == true && workoutData.cardioPowerDynamics != null`.
3. **§5.10 Grouped Metric Row Compliance**: `CardioPowerDynamicsRow.kt` single-row presentation inside `WorkoutDetails.kt` preserved.
4. **Numerical Stability & ODE Invariants**: Stepping with $\Delta t_i = (timestampsSec[i] - timestampsSec[i-1]).\text{coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$ prevents zero-division and infinite decay across pause gaps.
5. **UI Consistency**: Reuses `CardioPowerDynamicsCard.kt` (`ElevatedCard(shape = RoundedCornerShape(12.dp))`) and `PowerHrDynamicsInfoDialog`. Follows standard card padding (`horizontal = 16.dp, vertical = 4.dp`) matching `WorkoutDescription` and `WorkoutExtrema`.

---

## 3. Proposed Architectural Changes (SWE.2)
* **Presentation Layer (`WorkoutDetailTabbedContent.kt`)**: In `RenderWorkoutDetailCardSections`, under `WorkoutDetailSectionType.DETAILS`, invoke `CardioPowerDynamicsCard(record = workoutData.cardioPowerDynamics, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))` when `tuningConfig.enableCardioPowerDynamics && workoutData.cardioPowerDynamics != null`. In `WorkoutDetails(...)`, pass `showCardioPowerInWorkoutSummary = true` (or rely on default `true`) to decouple full details from the list toggle.
* **Physiology Layer (`PowerHrDynamicsCalculator.kt`)**: Add timestamp-aware overload `simulateTrajectory(powerSamples: List<Double?>, timestampsSec: List<Long>, tau: Double, hr0: Double, k: Double): List<Double?>` computing step-wise $\alpha_i = \exp(-\Delta t_i / \tau)$ with $\Delta t_i = (timestampsSec[i] - timestampsSec[i-1]).\text{coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$. Also provide primitive overload `simulateTrajectory(power: DoubleArray, timestampsSec: LongArray, tau: Double, hr0: Double, k: Double): DoubleArray`.
* **Map / Telemetry Graph Layer (`TelemetryMetricGraph.kt`)**: Pass `timestampsSec = pathPoints.map { it.timeSec }` to `simulateTrajectory`. When `canRenderFreshModelOverlay && effectiveShowFreshModel`, include non-null `freshHrTrajectory` values when computing `effectiveMin`/`effectiveMax` for `HEART_RATE` `(dataMin, dataMax)`.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Timestamp-Aware Trajectory Simulation (`REQ-REV-010.3`, `TST-REV-011.5`)
* **Target Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/physiology/PowerHrDynamicsCalculator.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/physiology/PowerHrDynamicsCalculatorTest.kt`.
* **Details**: Implement timestamp-aware overloads in `PowerHrDynamicsCalculator.kt`. Add unit tests in `PowerHrDynamicsCalculatorTest.kt` verifying step response across decimated points, boundary clamps ($\le 0\text{s} \to 1\text{s}$, $> 60\text{s} \to 60\text{s}$), and single-point lists.

### Step 2: Telemetry Graph Decimated Simulation & Y-Axis Inclusion (`REQ-REV-010.3`, `TST-REV-011.6`)
* **Target Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraph.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricGraphOverlayTest.kt`.
* **Details**: Pass `pathPoints.map { it.timeSec }` to `simulateTrajectory`. When `canRenderFreshModelOverlay && effectiveShowFreshModel`, expand `HEART_RATE` `(dataMin, dataMax)` with `freshHrTrajectory.filterNotNull()`. Add unit tests verifying bounds expansion and overlay rendering.

### Step 3: Wire CardioPowerDynamicsCard into Workout Details & Decouple Gating (`REQ-REV-010.2`, `REQ-REV-010.4`, `TST-REV-011.7`)
* **Target Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContent.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutdetails/WorkoutDetailsCardioPowerRowTest.kt`, `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDetailTabbedContentTest.kt`.
* **Details**: In `RenderWorkoutDetailCardSections`, render `CardioPowerDynamicsCard` under `WorkoutDetailSectionType.DETAILS` when enabled and record present. Pass `showCardioPowerInWorkoutSummary = true` to `WorkoutDetails`. Add contract and layout unit tests asserting card presence and immunity to `showCardioPowerInWorkoutSummary == false`.

### Step 4: Verification & Governance Check
* **Details**: Execute targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.physiology.PowerHrDynamicsCalculatorTest" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphOverlayTest" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDetailTabbedContentTest"
  ```
  Run governance script: `python3 tools/verify_requirement_governance.py`.

---

## 5. UI Consistency Checklist
* **Reference Composable**: `CardioPowerDynamicsCard.kt` (`ElevatedCard(shape = RoundedCornerShape(12.dp))`), `PowerHrDynamicsInfoDialog`.
* **Card Margins**: `.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)` matching adjacent sections.
* **Palette**: Material 3 color scheme tokens with `TTColor.Zone2` (dashed cyan fresh curve) and `TTColor.Zone4` (cardiac drift shaded gap).
* **Typography**: M3 typography tokens (`titleMedium`, `labelMedium`, `bodyMedium`).
