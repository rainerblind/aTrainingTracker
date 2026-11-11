# Stage 1 Analysis: ATT-6723 - Wire CardioPowerDynamicsCard, Timestamp-Aware Fresh Model Overlay & Y-Axis Inclusion

**Ticket**: [ATT-6723](https://rainerblind.atlassian.net/browse/ATT-6723)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6723` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
* In `ATT-3905` (commit `1b33fcba`), `CardioPowerDynamicsCard.kt` was authored as an aftermath summary card with header `ⓘ` Info action (`PowerHrDynamicsInfoDialog`), RMSE, $HR_0$ baseline comparison, and fatigued window breakdown. However, it was not wired into `WorkoutDetailTabbedContent.kt`, leaving zero production callers in `app/src/main/`.
* `WorkoutDetailTabbedContent.kt` passed `showCardioPowerInWorkoutSummary = tuningConfig.showCardioPowerInWorkoutSummary` to `WorkoutDetails.kt`. When an athlete toggled off *"Show in Workout Summary"*, it inadvertently hid Cardio-Power Dynamics inside full Workout Details rather than scoping suppression strictly to compact workout list cards (`WorkoutSummary.kt`).
* In `TelemetryMetricGraph.kt`, the *"Fresh HR Model"* overlay simulated trajectory using hardcoded `dt = 1.0`. Across multi-hour rides, decimated `pathPoints` have timestamps spaced by 3–10+ seconds, causing the 1st-order simulated filter response to lag 3×–10× slower than real cardiorespiratory kinetics.
* When the overlay is enabled, `TelemetryMetricGraph.kt` computed Y-axis `(dataMin, dataMax)` bounds solely from actual HR measurements. If the simulated fresh model dipped lower on descents or surged higher, the dashed curve clamped flat against canvas boundaries.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Call Site Audit**:
  * `CardioPowerDynamicsCard.kt` (lines 61–223): Verified to have 0 production callers across `app/src/main/`.
  * `WorkoutDetailTabbedContent.kt` (lines 804–805): `WorkoutDetails` received `showCardioPowerInWorkoutSummary = tuningConfig.showCardioPowerInWorkoutSummary`. Unchecking this preference hid `CardioPowerDynamicsRow` in full details, and `CardioPowerDynamicsCard` was omitted entirely.
  * `PowerHrDynamicsCalculator.kt` (lines 290–313): `simulateTrajectory` accepted scalar `dt: Double = 1.0` and calculated $\alpha = \exp(-dt / \tau)$ once at the start.
  * `TelemetryMetricGraph.kt` (lines 155–165): Invoked `simulateTrajectory` with `dt = 1.0` without supplying timestamps.
  * `TelemetryMetricGraph.kt` (lines 223–235): `(dataMin, dataMax)` for `HEART_RATE` only evaluated `validValues.minOrNull()` and `validValues.maxOrNull()`.
* **Independent Peer Review & Verification**:
  * *Decimation Kinetic Audit*: On a 100 km ride with 5-second sampling, simulating with $dt = 1.0$ makes a $\tau = 30\text{ s}$ system behave as $\tau = 150\text{ s}$. Stepping by $(t_i - t_{i-1})\text{.coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$ restores mathematically true exponential response across decimation.
  * *Boundary Clamping Audit*: Live inspection of `valueToY` in `TelemetryMetricGraph.kt:484` confirms it applies `.coerceIn(0.0, 1.0)`. Clamping occurs whenever fresh model values fall outside $(dataMin, dataMax)$. Evaluating `effectiveMin = minOf(min, freshMin)` and `effectiveMax = maxOf(max, freshMax)` prevents boundary flattening while preserving chart zoom scaling.
  * *Preference Separation*: `showCardioPowerInWorkoutSummary` is configured under "Show in Workout Summary" in `PhysiologyModelsSection.kt`. It must only gate `WorkoutSummary.kt`, while full details (`WorkoutDetailTabbedContent.kt`) displays Cardio-Power whenever `enableCardioPowerDynamics == true && workoutData.cardioPowerDynamics != null`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Render `CardioPowerDynamicsCard` in `WorkoutDetailTabbedContent.kt` under `WorkoutDetailSectionType.DETAILS` when `enableCardioPowerDynamics == true && workoutData.cardioPowerDynamics != null`.
  2. In `WorkoutDetailTabbedContent.kt`, decouple full details from `showCardioPowerInWorkoutSummary` (pass `showCardioPowerInWorkoutSummary = true` to `WorkoutDetails`).
  3. Add timestamp-aware overload `simulateTrajectory(powerSamples, timestampsSec, tau, hr0, k)` in `PowerHrDynamicsCalculator.kt` computing step-wise $\Delta t = (t_i - t_{i-1})\text{.coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$.
  4. In `TelemetryMetricGraph.kt`, invoke timestamp-aware simulation and include non-null `freshHrTrajectory` values in `(dataMin, dataMax)` when `canRenderFreshModelOverlay && effectiveShowFreshModel`.
  5. Unit tests verifying wiring, visibility decoupling, kinetic calculation with varying timestamps, and Y-axis domain expansion.
* **Out-of-Scope Non-Goals**:
  1. Modifying SQLite tables or schema (`WorkoutSummariesDatabaseManager`).
  2. Modifying `CardioPowerTrendsCard` or Athlete Profile trends (governed by `ATT-3906` / `ATT-6724`).
  3. Changing `CardioPowerDynamicsRow.kt` §5.10 compact presentation inside `WorkoutDetails.kt`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-REV-010` (*Cardio-Power Dynamics Details Row & Fresh Model HR Graph Overlay*), refining `REQ-REV-010.3` and `REQ-REV-010.4`. Targets `WorkoutDetailTabbedContent.kt`, `PowerHrDynamicsCalculator.kt`, and `TelemetryMetricGraph.kt`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-3905` (commit `1b33fcba`).
* **Root Reason for Existing Formulation**: In `ATT-3905`, `CardioPowerDynamicsCard` was created but accidentally omitted from `WorkoutDetailTabbedContent.kt`. `simulateTrajectory` was ported from synthetic 1-Hz tests without accounting for decimation in `pathPoints`.
* **Preservation of Core Invariants**:
  1. §5.10 compact row compliance in `WorkoutDetails.kt` preserved.
  2. `showCardioPowerInWorkoutSummary` gating on `WorkoutSummary.kt` preserved.
  3. Mathematical fidelity of 1st-order Output-Error equation preserved.
  4. 100% 9-language translation parity in `strings_cardio_power.xml` preserved.

---

## 5. Architectural Strategy & High-Level Solution
* **Mount `CardioPowerDynamicsCard` (`WorkoutDetailTabbedContent.kt`)**: In `RenderWorkoutDetailCardSections`, under `WorkoutDetailSectionType.DETAILS`, invoke `CardioPowerDynamicsCard(record = workoutData.cardioPowerDynamics, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))` when `tuningConfig.enableCardioPowerDynamics && workoutData.cardioPowerDynamics != null`.
* **Decouple Detail Gating**: Set `showCardioPowerInWorkoutSummary = true` for `WorkoutDetails` inside `WorkoutDetailTabbedContent.kt`.
* **Timestamp-Aware Simulation (`PowerHrDynamicsCalculator.kt`)**: Implement `simulateTrajectory(powerSamples: List<Double?>, timestampsSec: List<Long>, tau: Double, hr0: Double, k: Double): List<Double?>` stepping with $\Delta t_i = (timestampsSec[i] - timestampsSec[i-1])\text{.coerceIn}(1\text{L}, 60\text{L})\text{.toDouble}()$.
* **Y-Axis Domain Inclusion (`TelemetryMetricGraph.kt`)**: Pass `timestampsSec = pathPoints.map { it.timeSec }` to `simulateTrajectory`. When `canRenderFreshModelOverlay && effectiveShowFreshModel`, expand `HEART_RATE` `dataMin`/`dataMax` with non-null `freshHrTrajectory` values.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Disabling `showCardioPowerInWorkoutSummary` suppresses display on list cards (`WorkoutSummary.kt`) while leaving full Workout Details unaffected.
  2. Coercing $\Delta t \in [1\text{s}, 60\text{s}]$ guarantees numerical stability without infinite decay across long pauses or division-by-zero on zero-second intervals.
  3. Toggling overlay dynamically expands/contracts Y-axis scale smoothly without clipping or state leakage.
* **Risk Rating**: **LOW** — Isolated UI rendering and pure numerical ODE simulation fixes with zero database schema, background service, or IPC impact (2 SP).
