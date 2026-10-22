# Stage 3: Implementation Plan - ATT-3361: Replace hardcoded distance and altitude strings with canonical formatters

**Ticket**: [ATT-3361](https://rainerblind.atlassian.net/browse/ATT-3361)  
**Sub-task**: [ATT-3527](https://rainerblind.atlassian.net/browse/ATT-3527) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-364` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards (Rule 36)*), complementing `REQ-UI-015`, `REQ-UI-016`, `REQ-UI-330`, `REQ-MAP-027`, `REQ-MAP-028`, and `REQ-MAP-029`  
**Test Mapping**: `TST-UI-327` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification*)  
**Branch**: `feature/ATT-3361`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Description & Background

`aTrainingTracker` has centralized, unit-aware metric formatters in package `com.atrainingtracker.banalservice.sensor.formater` (specifically `DistanceFormatter` and `AltitudeFormatter`), exposed in Jetpack Compose via `LocalMetricFormatter.current`.

However, during early Compose migration across several UI screens, developers hand-rolled inline string templates with hardcoded metric units:
1. `ForkDecisionCard.kt:260`: `String.format(Locale.US, "%.1f km", distKm)` and `"+${branch.totalElevationMeters.roundToInt()} m"`
2. `ReturnNavigationState.kt:43`: `String.format(Locale.US, "%.1f km", km)` and `"+$climb m"`
3. `RouteSelectionButton.kt:137`: `String.format(Locale.getDefault(), "%.1f km", distKm)` and `"+%.0f m"`
4. `TurnPromptBanner.kt:142`: `"${distanceRemaining} m"`
5. `LapSplitVisualizer.kt:277`: `String.format(Locale.US, "%.2f km", distanceM / 1000.0)`
6. `LiveClimbSheet.kt:118 & 146`: `"${climb.elevationGainMeters.roundToInt()} m"` and `"${liveClimb.remainingElevationGain.roundToInt()} m"`
7. `ClimbOnMapScreen.kt:279` & `RouteOnMapScreen.kt:555`: fallback literals with raw `" m"` and `" km"`

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
- **Imperial Unit Defect**: Athletes with Imperial units active (miles / feet) are shown hardcoded metric "km" and "m" in active navigation prompts, fork decision alerts, take-me-home HUD, and live climb telemetry.
- **Scattered Threshold Logic**: Distance and elevation display thresholds are duplicated across disjoint UI components with varying rounding rules and format specifiers.

ATT-3361 replaces all raw unit string templates across these components with canonical `DistanceFormatter` and `AltitudeFormatter` calls, ensuring 100% unit parity and DRY compliance.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-364` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards (Rule 36)*)
* **Test Mapping**: `TST-UI-327` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification*)
* **Living Documentation**:
  - `docs/requirements.md` (`REQ-UI-364` specified)
  - `docs/tests.md` (`TST-UI-327` specified)

---

## 3. System Invariants & Preserved Behavior

1. **Rule 36 (Single Source of Truth & DRY)**: All UI distance and elevation strings must flow through `DistanceFormatter` and `AltitudeFormatter`.
2. **Imperial & Metric Precision Invariant**:
   - Metric distance: `< 1000m` -> `"%d m"`, `< 100km` -> `"%.2f km"`, `< 1000km` -> `"%.1f km"`, `>= 1000km` -> `"%.0f km"`.
   - Imperial distance: `< 1000m` -> `"%.0f ft"`, `< 1609344m` -> `"%.2f mile"`, `>= 1609344m` -> `"%.0f mile"`.
   - Altitude / Elevation: Metric -> `"%.0f m"`, Imperial -> `"%.0f ft"`.
3. **Headless JVM Null Safety**: Formatters must never throw `NullPointerException` when run in headless unit test environments where `TrainingApplication.getAppContext()` or `SharedPreferences` is uninitialized.
4. **UI Layout & Navigation Invariant**: Visual hierarchy, padding, typography, card shapes, and audio prompts remain 100% unaltered.
5. **Clean-Room Full Suite Regression Invariant**: 100% test pass rate across all modules (`./gradlew testDebugUnitTest`).

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Formatter Headless Null Safety & Robustness
* **Files**:
  - `app/src/main/java/com/atrainingtracker/banalservice/sensor/formater/AltitudeFormatter.java`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/TrainingApplication.java`
* **Changes**:
  - In `TrainingApplication.java`: In `getUnit()`, return `MyUnits.METRIC` if `cSharedPreferences == null` (guarding against uninitialized unit tests).
  - In `AltitudeFormatter.java`: In `format_with_units()`, guard `TrainingApplication.getAppContext()`: if null, fallback unit string to `"ft"` when Imperial and `"m"` when Metric.

### Component 2: `ForkDecisionCard.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/ForkDecisionCard.kt`
* **Changes**:
  - Consume `LocalMetricFormatter.current` (or instantiated formatters).
  - Replace `String.format(Locale.US, "%.1f km", distKm)` with `formatters.distance.format_with_units(branch.totalDistanceMeters)`.
  - Replace `"+${branch.totalElevationMeters.roundToInt()} m"` with `"+${formatters.altitude.format_with_units(branch.totalElevationMeters)}"`.

### Component 3: `ReturnNavigationState.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/routes/ReturnNavigationState.kt`
* **Changes**:
  - `formattedRemainingDistance`: replace `String.format(Locale.US, "%.1f km", km)` with `DistanceFormatter().format_with_units(remainingDistanceMeters)`.
  - `formattedRemainingClimb`: replace `"+$climb m"` with `"+${AltitudeFormatter().format_with_units(remainingElevationGainMeters)}"`.

### Component 4: `RouteSelectionButton.kt` & `RouteSelectorSheet.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/RouteSelectionButton.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`
* **Changes**:
  - In `RouteSelectionButton.kt`: format `distStr` via `distanceFormatter.format_with_units(activeRoute.summary.distance)` and `eleStr` via `"+${altitudeFormatter.format_with_units(activeRoute.summary.elevationGain)}"`.
  - In `RouteSelectorSheet.kt`: In `formatDistanceAndElevation`, format via `DistanceFormatter().format_with_units(distanceMeters)` and `"+${AltitudeFormatter().format_with_units(elevationMeters)}"`.

### Component 5: `TurnPromptBanner.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/TurnPromptBanner.kt`
* **Changes**:
  - Replace `"${distanceRemaining} m"` with `distanceFormatter.format_with_units(distanceRemaining)`.

### Component 6: `LapSplitVisualizer.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitVisualizer.kt`
* **Changes**:
  - In `formatSplitDistance(distanceM: Double)`, replace manual format with `DistanceFormatter().format_with_units(distanceM)`.

### Component 7: `LiveClimbSheet.kt`, `ClimbOnMapScreen.kt` & `RouteOnMapScreen.kt`
* **Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/LiveClimbSheet.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/climbs/ClimbOnMapScreen.kt`
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteOnMapScreen.kt`
* **Changes**:
  - In `LiveClimbSheet.kt`: add `altitudeFormatter = remember { AltitudeFormatter() }`; replace `"${climb.elevationGainMeters.roundToInt()} m"` and `"${liveClimb.remainingElevationGain.roundToInt()} m"` with `altitudeFormatter.format_with_units(...)`. Remove dead fallback `?: "${liveClimb.distanceToSummit.roundToInt()} m"`.
  - In `ClimbOnMapScreen.kt` and `RouteOnMapScreen.kt`: remove redundant raw `" m"` and `" km"` fallback strings.

---

## 5. UI Consistency (Rule 23)

* **Closest Reference Screen**: `SensorGridScreen.kt` and `TrackOnMapScreen.kt`, which consume `LocalMetricFormatter.current` for all live and post-workout telemetry.
* **Reused Components**: Material 3 typography tokens (`bodySmall`, `titleLarge`, `bodyMedium`) and `route_metrics_format`.
* **Theme Tokens**: Standard `MaterialTheme.colorScheme.onSurfaceVariant`, `primary`, and `surface`.
* **Zero One-Off Styles**: No new custom colors, fonts, or shapes are introduced; changes purely replace raw unit string formatting with canonical formatter calls.

---

## 6. Atomic Implementation Steps

### Step 1: Null Safety & Robustness in Formatters
* Update `TrainingApplication.java` (`getUnit()`) and `AltitudeFormatter.java` (`format_with_units()`) to guard against uninitialized contexts in JVM tests.
* Verify existing unit tests in `banalservice/sensor/formater/`.

### Step 2: ReturnNavigationState & HUD Contract Updates
* Update `ReturnNavigationState.kt` to use `DistanceFormatter` and `AltitudeFormatter`.
* Update `ReturnNavigationHudContractTest.kt` to align expected distance string with canonical formatter output (`12.40 km` instead of `12.4 km`).

### Step 3: ForkDecisionCard & RouteSelectionButton / RouteSelectorSheet
* Update `ForkDecisionCard.kt`, `RouteSelectionButton.kt`, and `RouteSelectorSheet.kt`.
* Verify with `ForkDecisionCardTest.kt`.

### Step 4: TurnPromptBanner & LapSplitVisualizer
* Update `TurnPromptBanner.kt` and `LapSplitVisualizer.kt`.
* Verify with `LapSplitVisualizerTest.kt`.

### Step 5: LiveClimbSheet & Map Screens
* Update `LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, and `RouteOnMapScreen.kt`.
* Verify with `LiveClimbSheetLayoutTest.kt`.

### Step 6: Comprehensive Metric Formatter Contract Test
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/util/MetricFormatterContractTest.kt`.
* Test Metric and Imperial formatting across all updated components.

### Step 7: Clean-Room Full Suite Regression Run
* Execute `./gradlew testDebugUnitTest`.

---

## 7. Rollback & Contingency Strategy

All modifications are confined strictly to presentation-tier formatting logic without schema or data migrations. In the event of an unexpected regression, git revert cleanly restores previous state.
