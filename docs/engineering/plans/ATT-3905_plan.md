# Stage 3: Implementation Plan - ATT-3905: Cardio-Power Dynamics Card & Fresh Model HR Overlay

**Ticket**: [ATT-3905](https://rainerblind.atlassian.net/browse/ATT-3905)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3905`  
**Requirement Mapping**: `REQ-REV-010` | **Test Mapping**: `TST-REV-011`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-10  

---

## 1. Problem Description & Background

`ATT-3904` delivered the 1st-order Output-Error identification engine and DB v29 persistence. We now implement post-workout UI visualization:
1. Canonical grouped summary row in `WorkoutDetails.kt` following `design_guidelines.md` §5.10 (`AltitudeRow` pattern).
2. Dedicated summary card (`CardioPowerDynamicsCard.kt`) with header `?` info action opening `PowerHrDynamicsInfoDialog`.
3. "Fresh HR Model" overlay in `TelemetryMetricGraph.kt` plotting simulated fresh HR (cyan dashed curve), shaded cardiac drift gap, and calibration window tinting, respecting `Rule 64` horizontal plot width alignment.

---

## 2. System Invariants & Preserved Behavior

1. **Design Guidelines §5.10 Grouped Metric Row Compliance**: `CardioPowerDynamicsRow` MUST use a single horizontal `Row` containing at most 2–3 equal-weight `MetricItem`s under the domain header icon. Multi-row stacking and `FlowRow` pill clouds are strictly prohibited.
2. **Stacked Multi-Chart Plot Alignment (`Rule 64`)**: `TelemetryMetricGraph.kt` horizontal plot boundaries (start `50.dp`, landscape `0.72f` right edge) remain synchronized with sibling telemetry and elevation charts.
3. **Progressive Disclosure & Gating**: Visual elements render only when `enableCardioPowerDynamics == true`, `cardioPowerDynamics != null`, and (in summary cards) `showCardioPowerInWorkoutSummary == true`.
4. **9-Locale Parity**: All new UI strings reside in `res/values*/strings_cardio_power.xml` with zero emojis.

---

## 3. Proposed Architectural Changes (SWE.2)

* **Data Model & Mapper (`WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt`)**: Add and hydrate `cardioPowerDynamics: CardioPowerDynamicsRecord?`.
* **Summary Row (`WorkoutDetails.kt`)**: Implement `CardioPowerDynamicsRow` using `WorkoutDomainRow` with `ic_cardio_load` icon and 3 `MetricItem`s ($\text{HR@200W}$, $\tau$, $1/K$).
* **Summary Card (`CardioPowerDynamicsCard.kt`)**: Implement compact card displaying Start $\rightarrow$ End parameters, drift badge, fit quality ($R^2$, RMSE), and Info button.
* **Canvas Telemetry Overlay (`TelemetryMetricGraph.kt`)**: Add `showFreshModelOverlay` toggle and draw cyan dashed curve (`TTColor.Zone2`), shaded drift polygon, and calibration window tint.

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Data Model Hydration & Domain Mapping (`REQ-REV-010.1`, `TST-REV-011.1`)
* **Files**: `WorkoutData.kt`, `WorkoutDetailsData.kt`, `WorkoutDataMapper.kt`, `WorkoutDataMapperTest.kt`.
* **Details**: Add `cardioPowerDynamics` property to data classes; hydrate from `WorkoutSummariesDatabaseManager.getCardioPowerDynamics(workoutId)` in `WorkoutDataMapper`.

### Step 2: Canonical Details Row & §5.10 Grouped Layout (`REQ-REV-010.2`, `TST-REV-011.2`)
* **Files**: `WorkoutDetails.kt`, `WorkoutDetailsData.kt`, `WorkoutDetailsCardioPowerRowTest.kt`.
* **Details**: Implement `CardioPowerDynamicsRow` with `AltitudeRow` layout pattern (domain icon + 3 `MetricItem`s). Wire gating in `WorkoutSummary.kt` and `WorkoutDetailTabbedContent.kt`.

### Step 3: Cardio-Power Dynamics Card (`REQ-REV-010.2`, `TST-REV-011.2`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/CardioPowerDynamicsCard.kt`.
* **Details**: Create dedicated card with header Info icon opening `PowerHrDynamicsInfoDialog`, drift metrics, and parameter comparison.

### Step 4: Fresh Model Graph Overlay & Shading (`REQ-REV-010.3`, `TST-REV-011.3`)
* **Files**: `TelemetryMetricGraph.kt`, `MapDetailLayout.kt`, `TelemetryMetricGraphOverlayTest.kt`.
* **Details**: Add "Fresh HR Model" toggle chip, draw cyan dashed curve (`TTColor.Zone2`), shade cardiac drift area where $\text{HR}_{\text{actual}} > \text{HR}_{\text{fresh}}$, and tint fresh calibration window.

### Step 5: Localization & Targeted Regression Suite (`REQ-REV-010.5`, `TST-REV-011.5`)
* **Files**: `res/values*/strings_cardio_power.xml`, `TranslationParityTest.kt`.
* **Details**: Add UI strings across all 9 locales with 100% parity. Execute targeted test suite:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
