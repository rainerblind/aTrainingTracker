# Stage 2: Requirement & Test Specification - ATT-5487: [Feature] Workout Details (Zonen Tab): Add Grade Distribution Card

**Ticket**: [ATT-5487](https://rainerblind.atlassian.net/browse/ATT-5487)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.7`  
**Requirement Mapping**: `REQ-UI-453` (*Aftermath Workout Details: Grade / Slope Zone Distribution Card & 1% Telemetry Histogram*)  
**Test Spec ID**: `TST-UI-453`  
**Branch**: `feature/ATT-5487`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-06  

---

## 1. Requirement Specification (REQ-UI-453)

### 1.1 Problem Statement & Rationale
Workout Details' Zonen tab currently visualizes time in zones and fine-grained histograms only for Heart Rate and Power telemetry. Altitude/slope data is currently restricted to the elevation profile and its marginal altitude histogram. REQ-UI-453 introduces `GradeZoneDistributionCard` providing 7 canonical slope zones matching `ElevationProfile.kt` (`TTColor.Zone1..Zone7`), fine-grained 1% grade binning with `ElevationSmoothingMath` Loess smoothing, and the canonical 3-way toggle (Stacked Bar | Columns | Histogram).

### 1.2 Functional & Architectural Requirements
1. **7 Canonical Slope Zones (`REQ-UI-453.1`)**: System SHALL map segments into 7 zones matching `getElevationGradeColor` / `GradeLegend`: `< 2%` (Z1, Green), `2–5%` (Z2, Yellow), `5–10%` (Z3, Orange), `10–15%` (Z4, Red), `15–20%` (Z5, Magenta), `20–25%` (Z6, Black), `> 25%` (Z7, White with outline).
2. **Loess-Smoothed Grade Engine (`REQ-UI-453.2`)**: `ZoneDistributionCalculator.calculateGradeDistribution(...)` SHALL smooth path elevation samples using `ElevationSmoothingMath.smoothAltitudes` ($\sigma = 21.0\text{m}$) before grade calculation, clamping $\Delta t \le 5\text{s}$.
3. **`GradeZoneDistributionCard` UI (`REQ-UI-453.3`)**: Composable in `ui/aftermath/zones/GradeZoneDistributionCard.kt` with icon `R.drawable.ic_grade`, localized title `aftermath_grade_zones_title`, and 3-way toggle (`SingleChoiceSegmentedButtonRow`) switching between Stacked Bar, Columns, and 1% Histogram.
4. **Workout Details Integration (`REQ-UI-453.4`)**: Wire `gradeZoneDistribution` into `WorkoutDetailTabbedContent.kt` and update `hasZonesData` in `TrackOnMapScreen.kt` to include `gradeZoneDistribution != null`. Run computation 100% on `Dispatchers.IO`.
5. **9-Language Localization (`REQ-UI-453.5`)**: Localize `aftermath_grade_zones_title` across all 9 supported locales.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Zonen Tab Presentation)**:
  * *Given* a workout with valid elevation and distance path points,
  * *When* the user navigates to the Zonen tab in Workout Details,
  * *Then* `GradeZoneDistributionCard` is rendered with 7 canonical slope zones and moving duration breakdown.
* **Criterion 2 (Tab Availability without HR/Power)**:
  * *Given* a GPS track without HR or power sensors,
  * *When* `TrackOnMapScreen` evaluates `hasZonesData`,
  * *Then* `hasZonesData` evaluates to `true` and the Zonen tab is displayed.
* **Criterion 3 (Interactive 3-Way Mode Switcher)**:
  * *Given* the `GradeZoneDistributionCard`,
  * *When* switching between Stacked Bar, Columns, and Histogram,
  * *Then* the display updates seamlessly with interactive touch scrubbing enabled on the 1% grade histogram.

### 1.4 System Invariants
* 100% off-main-thread calculation on `Dispatchers.IO`.
* Exact color and boundary alignment with `ElevationProfile.kt`.
* Zero regressions in existing HR/Power cards and zero DB migrations.

---

## 2. Test Specification (TST-UI-453)

### Test Case 1: `GradeZoneDistributionCalculatorTest` (`TST-UI-453.1`, `TST-UI-453.2`)
* **Scope**: Unit Test (`app/src/test/java/.../zones/GradeZoneDistributionCalculatorTest.kt`)
* **Action**: Pass synthetic and recorded GPS elevation profiles to `calculateGradeDistribution`.
* **Expected Result**: Exact 7-zone time mapping, correct 1% histogram bins, delta time clamping, and smooth altitude filter application.

### Test Case 2: `GradeZoneDistributionCardTest` (`TST-UI-453.3`)
* **Scope**: Compose Contract & State Test (`app/src/test/java/.../zones/GradeZoneDistributionCardTest.kt`)
* **Action**: Render card across `STACKED_BAR`, `COLUMNS`, and `HISTOGRAM` display modes.
* **Expected Result**: Proper component selection, header rendering with `ic_grade`, and mode switcher dispatch.

### Test Case 3: 9-Language Localization Parity (`TST-UI-453.4`)
* **Scope**: `TranslationParityTest`
* **Expected Result**: `aftermath_grade_zones_title` present across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing entries.

### Test Case 4: Targeted Unit & Governance Suite (`TST-UI-453.5`)
* **Commands**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"` & `python3 tools/verify_requirement_governance.py`
* **Expected Result**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-453.1` | Unit | `ZoneDistributionCalculator.calculateGradeDistribution` | `REQ-UI-453.1`, `REQ-UI-453.2` | Specified |
| `TST-UI-453.2` | Unit | `calculateGradeDistribution` (1% bins) | `REQ-UI-453.2` | Specified |
| `TST-UI-453.3` | Contract | `GradeZoneDistributionCard` | `REQ-UI-453.3`, `REQ-UI-453.4` | Specified |
| `TST-UI-453.4` | Localization | `TranslationParityTest` | `REQ-UI-453.5`, `REQ-UI-106` | Specified |
| `TST-UI-453.5` | Governance | `verify_requirement_governance.py` | `REQ-PRO-001` | Specified |
