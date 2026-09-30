# Stage 3: Implementation Plan - ATT-1739: [Aftermath/Zones] Redesign Zone Distribution (HR & Power) from horizontal stacked bar to vertical column chart

**Ticket**: [ATT-1739](https://atrainingtracker.atlassian.net/browse/ATT-1739)  
**Sub-task**: [ATT-1752](https://atrainingtracker.atlassian.net/browse/ATT-1752) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://atrainingtracker.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*), `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*)  
**Test Mapping**: `TST-UI-156`, `TST-UI-157`  
**Branch**: `feature/ATT-1739`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

During Sprint Review `2026-40.5`, the human user rejected the horizontal stacked bar presentation introduced in [HeartRateZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt) and [PowerZoneDistributionCard.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt):
> *"The zones (Zone 1, Zone 2, .., Zone 5) must be on the x-Axis; the time in the corresponding Zone must be on the y-Axis."*

This implementation plan defines the exact construction sequence to replace the single horizontal stacked bar with a 5-column vertical histogram, sharing a common component [ZoneDistributionColumnChart.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt), preserving all data layer logic, and validating via targeted unit tests and full-suite clean-room regression.

---

## 2. Traceability & Requirements Mapping

* **Requirement**:
  - `REQ-UI-202` (*Aftermath: Heart Rate 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*)
  - `REQ-UI-203` (*Aftermath: Cycling Power 5-Zone Distribution (Time-in-Zones) Vertical Column Chart Architecture*)
* **Test Mapping**:
  - `TST-UI-156` (*Aftermath Heart Rate 5-Zone Distribution Vertical Column Chart Verification*)
  - `TST-UI-157` (*Aftermath Cycling Power 5-Zone Distribution Vertical Column Chart Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites, database queries, and viewmodel data flow continue to pass cleanly.
2. **Data Layer Decoupling**: [ZoneDistributionCalculator.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionCalculator.kt), [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt), and [TrackOnMapAftermathViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapAftermathViewModel.kt) are untouched.
3. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.
5. **9-Language Localization Parity**: Existing string resources (`aftermath_hr_zones_title`, `aftermath_power_zones_title`) are strictly retained.

---

## 4. Proposed Architectural Changes

### Component 1: `ZoneDistributionColumnChart.kt` (New Reusable UI Component)
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt`
* **Responsibilities**:
  - Encapsulates the 5-column vertical histogram layout.
  - Scales column height relative to the maximum duration among zones: $\text{heightFraction}_i = t_i / \max_j(t_j)$.
  - Enforces a minimum visual height of `4.dp` for non-zero duration zones, and `0.dp` for zero-duration zones.
  - Renders duration (`formatZoneDuration`) and percentage (`X%`) above the column.
  - Renders filled vertical bar with rounded top corners (`RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)`), filled with `entry.color` (`TTColor.Zone1`..`Zone5`).
  - Renders a clean baseline divider (`HorizontalDivider`).
  - Renders zone label ("Z1" through "Z5") with color badge below the baseline.
* **Helper Object**:
  - `ZoneDistributionChartMath` containing pure functions `calculateHeightFraction(durationSec: Long, maxDurationSec: Long): Float` and `formatZoneDuration(seconds: Long): String` to allow pure unit testing.

### Component 2: `HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt` (Refactored)
* **Location**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/`
* **Changes**:
  - Replace the horizontal stacked `Row` and bottom legend `Row` with `ZoneDistributionColumnChart(distribution = distribution)`.
  - Maintain the Card container styling, header row with respective icon and localized title, and total active duration string.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Create `ZoneDistributionColumnChart.kt`
* **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionColumnChart.kt`
* **Actions**:
  - Implement pure math helper object `ZoneDistributionChartMath`:
    - `calculateHeightFraction(durationSec: Long, maxDurationSec: Long): Float`
    - `formatZoneDuration(seconds: Long): String`
  - Implement `@Composable fun ZoneDistributionColumnChart(distribution: ZoneDistributionData, modifier: Modifier = Modifier)`:
    - 5 evenly-spaced column slots for `distribution.entries` (Z1..Z5).
    - Top duration/percentage labels.
    - Proportionally-scaled vertical bar with rounded top corners and `defaultMinSize(minHeight = 4.dp)` when `durationSec > 0`.
    - Horizontal baseline divider.
    - Bottom "Z1".."Z5" labels with colored indicator dots.

### Step 2: Refactor `HeartRateZoneDistributionCard.kt` & `PowerZoneDistributionCard.kt`
* **Target Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/HeartRateZoneDistributionCard.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/PowerZoneDistributionCard.kt`
* **Actions**:
  - Remove redundant private `formatZoneDuration` and `ZoneLegendItem`.
  - Delegate chart rendering in `Column` body directly to `ZoneDistributionColumnChart(distribution = distribution)`.

### Step 3: Implement Unit Tests in `ZoneDistributionChartTest.kt`
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/zones/ZoneDistributionChartTest.kt`
* **Actions**:
  - Test `calculateHeightFraction`: max duration returns 1.0f, zero returns 0.0f, intermediate scaled linearly.
  - Test `formatZoneDuration`: 0s, 45s, 600s (10:00), 3665s (1:01:05).
  - Test 5-zone histogram structure and entry ordering.

### Step 4: Run Targeted Unit Tests
* **Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.zones.*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted test suite for `ui.aftermath.zones.*` passes.
  2. Localization parity tests for `HeartRateZoneLocalizationTest` and `PowerZoneLocalizationTest` pass.
  3. Clean-room full `./gradlew testDebugUnitTest` runs cleanly in Stage 5.
* **Rollback Plan**:
  - All changes isolated on `feature/ATT-1739`.
  - In case of critical regression, `git checkout sprint/2026-40.6` restores previous clean integration state.
