# Stage 2: Requirement & Test Specification - ATT-6723: Wire CardioPowerDynamicsCard, Decimated Simulation & Y-Axis Bounds

**Ticket**: [ATT-6723](https://rainerblind.atlassian.net/browse/ATT-6723)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6723`  
**Requirement Mapping**: `REQ-REV-010` (refines `REQ-REV-010.2`, `REQ-REV-010.3`, `REQ-REV-010.4`) | **Test Spec ID**: `TST-REV-011`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-REV-010: Cardio-Power Details & Overlay)

### 1.1 Problem Statement & User Scope Grounding (ATT-1250)
`ATT-3905` delivered `CardioPowerDynamicsCard.kt` and the "Fresh HR Model" graph overlay. However, the card lacked a caller in `WorkoutDetailTabbedContent.kt`, summary-card gating (`showCardioPowerInWorkoutSummary`) accidentally hid full details, graph ODE simulation assumed 1-Hz streaming ($dt = 1.0$) causing 3×–10× phase lag on decimated trackpoints, and Y-axis bounds excluded model extremes leading to canvas boundary clipping. Scope is bounded strictly to resolving these 4 regressions without modifying database schemas or trend calculations.

### 1.2 Functional & Architectural Requirements
1. **Full Details Card Mounting (`WorkoutDetailTabbedContent.kt`)**: In `RenderWorkoutDetailCardSections`, `CardioPowerDynamicsCard` SHALL be invoked under `WorkoutDetailSectionType.DETAILS` whenever `enableCardioPowerDynamics == true` and `workoutData.cardioPowerDynamics != null`, independent of `showCardioPowerInWorkoutSummary`.
2. **Preference Isolation**: `showCardioPowerInWorkoutSummary` SHALL gate compact workout list cards (`WorkoutSummary.kt`) strictly; unchecking it SHALL NOT hide Cardio-Power Dynamics inside full Workout Details (`WorkoutDetailTabbedContent.kt`).
3. **Timestamp-Aware Simulation (`PowerHrDynamicsCalculator.kt`)**: `PowerHrDynamicsCalculator` SHALL expose an overload `simulateTrajectory(powerSamples: List<Double?>, timeSecSamples: List<Long>, tau: Double, hr0: Double, k: Double): List<Double?>` evaluating step-wise $\Delta t_i = (timeSecSamples[i] - timeSecSamples[i-1]).\text{coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$.
4. **Y-Axis Domain Expansion (`TelemetryMetricGraph.kt`)**: In `TelemetryMetricGraph.kt`, when `canRenderFreshModelOverlay && effectiveShowFreshModel`, the `(dataMin, dataMax)` calculation for `HEART_RATE` SHALL include all non-null values of `freshHrTrajectory` to prevent canvas clipping.

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Full Details Card Mounting)**: *Given* a workout with valid `cardioPowerDynamics` and `enableCardioPowerDynamics == true`, *When* viewing Workout Details, *Then* `CardioPowerDynamicsCard` is displayed with Info icon opening `PowerHrDynamicsInfoDialog`, even when `showCardioPowerInWorkoutSummary == false`.
* **AC 2 (Timestamp-Aware Simulation)**: *Given* decimated track points with spacing $\Delta t > 1\text{ s}$, *When* fresh model overlay simulates trajectory, *Then* filter response evaluates true time elapsed per step, matching physiological kinetics without decimation-induced phase lag.
* **AC 3 (Y-Axis Domain Inclusion)**: *Given* active "Fresh HR Model" overlay with simulated values exceeding or dipping below actual HR extremes, *When* rendered, *Then* the Y-axis bounds expand to contain the model curve without flattening against canvas edges.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: `REQ-REV-010` (`REQ-REV-010.2`, `REQ-REV-010.3`, `REQ-REV-010.4`), targeting `WorkoutDetailTabbedContent.kt`, `PowerHrDynamicsCalculator.kt`, and `TelemetryMetricGraph.kt`.
2. *Historical Origin & Commit Trace*: Introduced in `ATT-3905` (commit `1b33fcba`).
3. *Root Reason for Existing Formulation*: `CardioPowerDynamicsCard` was created but uncalled in `WorkoutDetailTabbedContent.kt`, and `showCardioPowerInWorkoutSummary` was improperly passed into full details. Trajectory simulation used scalar `dt = 1.0` assuming 1-Hz streaming without accounting for map decimation, and Y-axis limits evaluated measured HR only.
4. *Preservation of Core Invariants*: §5.10 compact row compliance in `WorkoutDetails.kt`, summary gating on `WorkoutSummary.kt`, mathematical ODE stability via $\Delta t \in [1\text{s}, 60\text{s}]$ coercion, and 100% 9-language translation parity are strictly preserved.

---

## 3. Test Specification (TST-REV-011: Cardio-Power Visual Display & Overlay Verification)

### Test Case 1: Decimated Step $\Delta t$ Trajectory Simulation (`TST-REV-011.5`)
* **Scope**: `PowerHrDynamicsCalculatorTest.kt`. Verify simulation with step intervals ($dt \in [1, 60]\text{ s}$) matches analytical step-response without phase lag, and verify clamp behavior for $\Delta t \le 0$ and $\Delta t > 60\text{ s}$.

### Test Case 2: Y-Axis Fresh Model Overlay Bounds Inclusion (`TST-REV-011.6`)
* **Scope**: `TelemetryMetricGraphOverlayTest.kt`. Verify that `(dataMin, dataMax)` for `HEART_RATE` expands to encompass simulated curve values when overlay is active, preventing boundary clipping.

### Test Case 3: Workout Details Card Mounting & Preference Decoupling (`TST-REV-011.7`)
* **Scope**: `WorkoutDetailsCardioPowerRowTest.kt`. Verify `CardioPowerDynamicsCard` displays in full Workout Details when `enableCardioPowerDynamics == true` even if `showCardioPowerInWorkoutSummary == false`.

---

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-011.5]` | Unit | `PowerHrDynamicsCalculatorTest`: True $\Delta t$ step response across decimation | `REQ-REV-010.3` | Specified |
| `[TST-REV-011.6]` | Unit / Canvas | `TelemetryMetricGraphOverlayTest`: Dynamic Y-axis bound inclusion prevents clipping | `REQ-REV-010.3` | Specified |
| `[TST-REV-011.7]` | Compose / UI | `WorkoutDetailsCardioPowerRowTest`: Card mounting in full details decoupled from summary toggle | `REQ-REV-010.2`, `REQ-REV-010.4` | Specified |
