# ATT-3905 Walkthrough: Cardio-Power Dynamics Details Row, Summary Card & Fresh Model HR Graph Overlay

**Ticket**: [ATT-3905](https://rainerblind.atlassian.net/browse/ATT-3905)  
**Parent Epic**: [ATT-3903](https://rainerblind.atlassian.net/browse/ATT-3903) (*Cardio-Power Analytics*)  
**Sprint**: `2026-46.1` | **Target Release**: `V5.0.0` | **Status**: Verified  
**Requirement Mapping**: [`REQ-REV-010`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements/req_aftermath_reviews.md) | **Test Mapping**: [`TST-REV-011`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests/tst_aftermath_reviews.md)  
**Branch**: `feature/ATT-3905`

---

## 1. Executive Summary

`ATT-3905` visually operationalizes the 1st-order dynamical Power-HR identification engine introduced in `ATT-3904` across workout aftermath UI surfaces:
- **Data Model Hydration (`WorkoutDataMapper.kt`)**: Hydrated `CardioPowerDynamicsRecord?` into `WorkoutData` and `WorkoutDetailsData` via single cursor retrieval and batch database query (`getCardioPowerDynamicsForWorkouts`).
- **Canonical Details Row (`WorkoutDetails.kt`)**: Implemented single-line `CardioPowerDynamicsRow` strictly following `design_guidelines.md` §5.10 (`AltitudeRow` pattern: domain icon + 3 equal-weight `MetricItem`s for $\text{HR@200W}$, $\tau$, and $1/K$). Wired gating behind `enableCardioPowerDynamics` and `showCardioPowerInWorkoutSummary`.
- **Dedicated Summary Card (`CardioPowerDynamicsCard.kt`)**: Created modular summary card displaying fresh vs. fatigued window comparisons, cardiac drift badge ($\Delta\text{HR@200W}$), and header info action triggering `PowerHrDynamicsInfoDialog`.
- **Canvas Telemetry Overlay (`TelemetryMetricGraph.kt`, `MapDetailLayout.kt`)**: Implemented "Fresh HR Model" toggle chip, dashed cyan predicted trajectory overlay (`TTColor.Zone2`), shaded cardiac drift decoupling gap (`TTColor.Zone4`), and calibration window tinting (min 8–55) strictly respecting `Rule 64` plot alignment.
- **Localization & Contract Tests**: Delivered 100% 9-locale parity in `res/values*/strings_cardio_power.xml` (zero emojis) and comprehensive unit/contract test suites (`WorkoutDataMapperCardioPowerTest`, `WorkoutDetailsCardioPowerRowTest`, `TelemetryMetricGraphOverlayTest`).

---

## 2. Traceability Matrix

| Requirement | Test Specification | Production Component | Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-REV-010.1` | `TST-REV-011.1` | `WorkoutDataMapper.kt`, `WorkoutData.kt`, `WorkoutDetailsData.kt` | Hydration of `CardioPowerDynamicsRecord` into UI state | **Verified** |
| `REQ-REV-010.2` | `TST-REV-011.2` | `WorkoutDetails.kt` | Canonical single-line grouped metric row adhering to §5.10 | **Verified** |
| `REQ-REV-010.3` | `TST-REV-011.3` | `CardioPowerDynamicsCard.kt` | Dedicated summary card with drift badge & info dialog trigger | **Verified** |
| `REQ-REV-010.4` | `TST-REV-011.4` | `TelemetryMetricGraph.kt`, `MapDetailLayout.kt` | Canvas Fresh HR Model overlay, drift shading, calibration window tint | **Verified** |
| `REQ-REV-010.5` | `TST-REV-011.5` | `strings_cardio_power.xml`, `TranslationParityTest.kt` | 100% 9-locale translation parity (zero inline emojis) | **Verified** |

---

## 3. Verification & Test Execution

- **Executed Test Scope (Rule 60)**:
  - Scope: `Tier 1 targeted: physiology.*, ui.aftermath.*, ui.components.workoutdetails.*, ui.map.TelemetryMetricGraphOverlayTest, localization.TranslationParityTest, ui.theme.ThemeColorCentralizationAuditTest, ui.components.core.*, build.*`
  - Command: `python3 tools/parallel_sprint_lock.py --timeout 1800 ./gradlew testDebugUnitTest --max-workers=2 --tests "com.atrainingtracker.trainingtracker.physiology.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.ui.components.workoutdetails.*" --tests "com.atrainingtracker.trainingtracker.ui.map.TelemetryMetricGraphOverlayTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.build.*"`
  - Result: **100% PASS** (715+ tests completed, 0 failures, 0 errors, 0 regressions).
- **Physical Device Execution**:
  - Checked `adb devices` (no physical test hardware connected; no emulator booted per Rule 79). UI layout conformance verified via Compose previews, Robolectric/JUnit contract tests, and `ThemeColorCentralizationAuditTest`.
