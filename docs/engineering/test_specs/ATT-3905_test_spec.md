# Stage 2: Requirement & Test Specification - ATT-3905: Cardio-Power Dynamics Row & Fresh Model HR Overlay

**Ticket**: [ATT-3905](https://rainerblind.atlassian.net/browse/ATT-3905)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Dynamical Power-to-Heart-Rate System Identification*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.1` | **Branch**: `feature/ATT-3905`  
**Requirement Mapping**: `REQ-REV-010` | **Test Spec ID**: `TST-REV-011`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-REV-010: Cardio-Power Visual Display & Overlay)

### 1.1 Problem Statement & Rationale
`ATT-3904` delivered the 1st-order Output-Error identification engine and DB v29 persistence. Athletes require post-workout visual presentation of aerobic economy ($\text{HR@200W}$, $\tau$, $1/K$) and cardiac drift. `WorkoutDetails.kt` and `TelemetryMetricGraph.kt` must display these metrics following design guidelines §5.10 (`AltitudeRow` pattern) with client-side trajectory simulation.

### 1.2 Functional & Architectural Requirements
1. **Hydration & Domain Mapping (`WorkoutDataMapper.kt`, `WorkoutDetailsData.kt`)**:
   - `WorkoutData` and `WorkoutDetailsData` SHALL hydrate `cardioPowerDynamics: CardioPowerDynamicsRecord?`.
2. **Canonical Single-Line Grouped Row (`WorkoutDetails.kt`)**:
   - `CardioPowerDynamicsRow` SHALL adhere strictly to design guidelines §5.10 (`AltitudeRow` pattern):
     a. *Left Domain Header Column*: Category label (`labelMedium`, `onSurfaceVariant`), `4.dp` spacer, `24.dp` icon (`ic_cardio_load`, `onSurfaceVariant`), and `width(20.dp)` spacer.
     b. *Single Horizontal Row of 3 MetricItems*: $\text{HR@200W}$ (Start $\rightarrow$ End bpm + drift), $\tau$ (Start $\rightarrow$ End s + $\Delta\tau$), and $1/K$ (W/bpm + $R^2$). Multi-row stacking and `FlowRow` pill clouds are strictly prohibited.
     c. *Separation*: Gated rows SHALL be separated by `HorizontalDivider(thickness = 0.5.dp)` with `padding(vertical = 4.dp)`.
3. **Fresh Model vs. Actual HR Graph Overlay (`TelemetryMetricGraph.kt`)**:
   - When active, the graph SHALL render a "Fresh HR Model" toggle chip.
   - When enabled, Canvas overlay SHALL draw predicted fresh HR curve $\text{HR}_{\text{fresh}}(t)$ as a dashed cyan curve (`TTColor.Zone2`), shade the drift gap where $\text{HR}_{\text{actual}} > \text{HR}_{\text{fresh}}$, and highlight the calibration window (min 8–55).
4. **Progressive Disclosure & Gating**:
   - Visual elements SHALL render only when `enableCardioPowerDynamics` is true, `cardioPowerDynamics != null`, and (in summary cards) `showCardioPowerInWorkoutSummary` is true. Workouts $< 20\text{ min}$ or lacking dynamics SHALL cleanly omit the row with zero visual distortion.
5. **Localization Parity**: All newly introduced UI strings SHALL reside in `res/values*/strings_cardio_power.xml` with 100% 9-locale parity (EN, DE, ES, FR, IT, JA, NL, PL, PT).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC 1 (Canonical Details Row)**: *Given* a workout with valid `cardioPowerDynamics` and `enableCardioPowerDynamics == true`, *When* viewing `WorkoutDetails`, *Then* `CardioPowerDynamicsRow` renders a single horizontal row of 3 `MetricItem`s with domain icon per §5.10.
* **AC 2 (Graph Overlay & Shading)**: *Given* `TelemetryMetricGraph` with paired Power+HR telemetry, *When* "Fresh HR Model" chip is toggled on, *Then* $\text{HR}_{\text{fresh}}(t)$ renders as a dashed cyan overlay with shaded cardiac drift gap.
* **AC 3 (Gating & Parity)**: *Given* `enableCardioPowerDynamics == false` or workout $< 20\text{ min}$, *When* rendered, *Then* the row and overlay are omitted with zero visual artifact, and `TranslationParityTest` passes across all 9 locales.

---

## 2. Test Specification (TST-REV-011: Cardio-Power Visual Display & Overlay Verification)

### Test Case 1: Data Hydration & Mapping Contract Test (`TST-REV-011.1`)
* **Scope**: `WorkoutDataMapperTest.kt`. Verify mapper hydrates `CardioPowerDynamicsRecord` into `WorkoutData` and `WorkoutDetailsData` from database cursor or repository cache.

### Test Case 2: Grouped Metric Row Layout & §5.10 Compliance Test (`TST-REV-011.2`)
* **Scope**: `WorkoutDetailsCardioPowerRowTest.kt`. Verify `CardioPowerDynamicsRow` conforms strictly to §5.10 (single `Row`, 3 `MetricItem`s, zero multi-row wrapping, proper divider padding, and clean null suppression).

### Test Case 3: Telemetry Graph Fresh Model Overlay Test (`TST-REV-011.3`)
* **Scope**: `TelemetryMetricGraphOverlayTest.kt`. Verify toggle chip state management, trajectory simulation sampling, dashed curve drawing parameters, and drift shading bounds.

### Test Case 4: Settings Gating & Visibility Test (`TST-REV-011.4`)
* **Scope**: `WorkoutDetailsVisibilityTest.kt`. Verify row visibility honors `enableCardioPowerDynamics`, `showCardioPowerInWorkoutSummary`, and data presence across workout summary cards and map details.

### Test Case 5: 9-Locale Localization Parity Test (`TST-REV-011.5`)
* **Scope**: `TranslationParityTest.kt` verifying all new cardio-power UI tokens in `strings_cardio_power.xml` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-REV-011.1]` | Unit | `WorkoutDataMapperTest`: Hydrate `CardioPowerDynamicsRecord` | `REQ-REV-010.1` | Specified |
| `[TST-REV-011.2]` | Compose / UI | `WorkoutDetailsCardioPowerRowTest`: §5.10 `AltitudeRow` layout compliance | `REQ-REV-010.2` | Specified |
| `[TST-REV-011.3]` | Canvas / UI | `TelemetryMetricGraphOverlayTest`: Fresh HR dashed curve & drift shading | `REQ-REV-010.3` | Specified |
| `[TST-REV-011.4]` | UI Contract | `WorkoutDetailsVisibilityTest`: Settings & duration data gating | `REQ-REV-010.4` | Specified |
| `[TST-REV-011.5]` | Localization | `TranslationParityTest`: 9-locale string parity in `strings_cardio_power.xml` | `REQ-REV-010.5`, `REQ-LOC-001` | Specified |
