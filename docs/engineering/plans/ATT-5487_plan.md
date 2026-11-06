# Stage 3: Implementation Plan - ATT-5487: Workout Details Grade Distribution Card

**Ticket**: [ATT-5487](https://rainerblind.atlassian.net/browse/ATT-5487)  
**Sub-task**: [ATT-5512](https://rainerblind.atlassian.net/browse/ATT-5512) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-826](https://rainerblind.atlassian.net/browse/ATT-826) (*Laps*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.7` | **Branch**: `feature/ATT-5487`  
**Requirement Mapping**: `REQ-UI-453` | **Test Mapping**: `TST-UI-453`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-06  

---

## 1. Problem Description & Background
Workout Details' Zonen tab currently visualizes time in zones and fine-grained histograms only for Heart Rate (`HeartRateZoneDistributionCard`) and Power (`PowerZoneDistributionCard`). ATT-5487 adds a dedicated **`GradeZoneDistributionCard`** featuring 7 canonical slope zones matching `ElevationProfile.kt` (`TTColor.Zone1..Zone7`), fine-grained 1% grade binning with `ElevationSmoothingMath` Loess smoothing, and the canonical 3-way toggle (Balken | Säulen | Histogramm).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-453` (*Aftermath Workout Details: Grade / Slope Zone Distribution Card & 1% Telemetry Histogram*)
  * `REQ-UI-453.1` (7 Canonical Slope Zones & Color Parity), `REQ-UI-453.2` (Loess-Smoothed Grade Calculation Engine)
  * `REQ-UI-453.3` (`GradeZoneDistributionCard` UI Component), `REQ-UI-453.4` (Workout Details Integration & Tab Availability)
  * `REQ-UI-453.5` (9-Language Localization Parity)
* **Test Mapping**: `TST-UI-453` (`TST-UI-453.1` .. `TST-UI-453.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Dispatcher Affinity & Off-Main-Thread Execution**: All Loess elevation smoothing and grade binning execute asynchronously on `Dispatchers.IO`. Zero UI jank.
2. **Elevation Color & Zone Parity**: Strict adherence to `ElevationProfile.kt` (`getElevationGradeColor` / `GradeLegend`) thresholds (`< 2%` to `> 25%`) and `TTColor.Zone1..Zone7`.
3. **Database & Schema Invariance**: Zero SQLite table alterations or migrations; grade analytics computed dynamically from existing `PathPoint` streams.
4. **Zero Regression**: Existing HR and Power zone distribution cards, summaries, and test suites remain untouched.

---

## 4. Proposed Architectural Changes
* **Calculation Engine (`ZoneDistributionCalculator.kt` & `TelemetryHistogramCalculator.kt`)**:
  - `ZoneDistributionCalculator.calculateGradeDistribution(pathPoints, sigma)`: Runs `ElevationSmoothingMath.smoothAltitudes(pathPoints, sigma = 21.0m)`, computes segment slope via `calculateGrade`, clamps $\Delta t \le 5\text{s}$, maps to 7 zones, and generates 1% histogram bins.
* **UI Component (`ui/aftermath/zones/GradeZoneDistributionCard.kt`)**:
  - Card with `ic_grade`, localized title `aftermath_grade_zones_title`, and 3-way toggle (`SingleChoiceSegmentedButtonRow`) switching between `ZoneDistributionStackedBar`, `ZoneDistributionColumnChart`, and `TelemetryHistogramChart` (`unit = "%"`).
* **View & State Integration (`AftermathMapUIState.kt`, `TrackOnMapAftermathViewModel.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, `WorkoutSummary.kt`)**:
  - Expose `gradeZoneDistribution: ZoneDistributionData?` in `AftermathMapUIState`.
  - Wire into `TrackOnMapScreen` and `WorkoutDetailTabbedContent` under `WorkoutDetailSectionType.ZONES`.
  - Update `hasZonesData = hrZoneDistribution != null || powerZoneDistribution != null || gradeZoneDistribution != null`.
* **UI Consistency**:
  - Reference screen: `HeartRateZoneDistributionCard.kt` and `PowerZoneDistributionCard.kt`.
  - Reused components: `ZoneDistributionStackedBar`, `ZoneDistributionColumnChart`, `TelemetryHistogramChart`, `SingleChoiceSegmentedButtonRow`.
  - Theme tokens: `RoundedCornerShape(12.dp)`, `TTColor.Zone1..Zone7`, `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Grade Zone & Histogram Calculation Engine**:
   - In `ZoneDistributionCalculator.kt`, implement `calculateGradeDistribution(...)` with 7 canonical slope zones and 1% fine-grained histogram bins.
2. **Step 2: UI Card Implementation (`GradeZoneDistributionCard.kt`)**:
   - Implement `GradeZoneDistributionCard.kt` in `ui/aftermath/zones/` matching `HeartRateZoneDistributionCard.kt` layout and 3-way segmented button row.
3. **Step 3: Aftermath State & Navigation Wiring**:
   - Add `gradeZoneDistribution` to `AftermathMapUIState.kt`, `TrackOnMapAftermathViewModel.kt`, `WorkoutRepository.kt`, `TrackOnMapScreen.kt`, `WorkoutDetailTabbedContent.kt`, and `WorkoutSummary.kt`.
   - Update `hasZonesData` check in `TrackOnMapScreen.kt` to include `gradeZoneDistribution != null`.
4. **Step 4: Unit & Contract Tests**:
   - Create `GradeZoneDistributionCalculatorTest.kt` and `GradeZoneDistributionCardTest.kt`.
   - Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted package tests (`com.atrainingtracker.trainingtracker.ui.aftermath.zones.*`), `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, and `verify_requirement_governance.py`.
* **Rollback**: Branch isolation (`feature/ATT-5487`) allows full revert without affecting `develop` or sprint branch.
