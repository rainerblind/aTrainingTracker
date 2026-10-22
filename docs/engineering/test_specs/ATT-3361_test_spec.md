# Stage 2: Requirement & Test Specification - ATT-3361: Replace hardcoded distance and altitude strings with canonical formatters

**Ticket**: [ATT-3361](https://rainerblind.atlassian.net/browse/ATT-3361)  
**Sub-task**: [ATT-3526](https://rainerblind.atlassian.net/browse/ATT-3526) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-364` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards (Rule 36)*), complementing `REQ-UI-015`, `REQ-UI-016`, `REQ-UI-330`, `REQ-MAP-027`, `REQ-MAP-028`, and `REQ-MAP-029`  
**Test Spec ID**: `TST-UI-327`  
**Branch**: `feature/ATT-3361`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-364)

### 1.1 Problem Statement & Rationale

`aTrainingTracker` features centralized, unit-aware metric formatters located in `com.atrainingtracker.banalservice.sensor.formater` (specifically `DistanceFormatter` and `AltitudeFormatter`), exposed in Jetpack Compose via `LocalMetricFormatter.current`.

However, across several UI screens, developers hand-rolled inline string templates with hardcoded metric units:
1. `ForkDecisionCard.kt:260`: `String.format(Locale.US, "%.1f km", distKm)` and `"+${branch.totalElevationMeters.roundToInt()} m"`
2. `ReturnNavigationState.kt:43`: `String.format(Locale.US, "%.1f km", km)` and `"+$climb m"`
3. `RouteSelectionButton.kt:137`: `String.format(Locale.getDefault(), "%.1f km", distKm)` and `"+%.0f m"`
4. `TurnPromptBanner.kt:142`: `"${distanceRemaining} m"`
5. `LapSplitVisualizer.kt:277`: `String.format(Locale.US, "%.2f km", distanceM / 1000.0)`
6. `LiveClimbSheet.kt:118 & 146`: `"${climb.elevationGainMeters.roundToInt()} m"` and `"${liveClimb.remainingElevationGain.roundToInt()} m"`
7. `ClimbOnMapScreen.kt:279` & `RouteOnMapScreen.kt:555`: fallback literals with raw `" m"` and `" km"`

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
1. **Severe Imperial Unit Regression**: Athletes using Imperial units (miles and feet) are presented with hardcoded metric "km" and "m" in active navigation prompts, fork decision alerts, take-me-home HUD, and climb sheets.
2. **Logic Duplication**: Distance and elevation display thresholds are duplicated across disjoint UI components with varying rounding rules and format specifiers.

### 1.2 Functional & Architectural Requirements

1. **Fork Decision Navigation Card (`ForkDecisionCard.kt`)**:
   - In `ForkDecisionCard.kt`, branch distance SHALL be formatted via `LocalMetricFormatter.current.distance.format_with_units(branch.totalDistanceMeters)`.
   - Branch elevation SHALL be formatted via `"+${LocalMetricFormatter.current.altitude.format_with_units(branch.totalElevationMeters)}"`.
   - Hardcoded `String.format(Locale.US, "%.1f km", distKm)` and `"+${branch.totalElevationMeters.roundToInt()} m"` templates SHALL be completely excised.

2. **Return Navigation & Arrival State (`ReturnNavigationState.kt`)**:
   - In `ReturnNavigationState.kt`, property `formattedRemainingDistance` SHALL return `DistanceFormatter().format_with_units(remainingDistanceMeters)`.
   - Property `formattedRemainingClimb` SHALL return `"+${AltitudeFormatter().format_with_units(remainingElevationGainMeters)}"`.
   - Hardcoded `String.format(Locale.US, "%.1f km", km)` and `"+$climb m"` templates SHALL be completely excised.

3. **Route Selection Controls (`RouteSelectionButton.kt` & `RouteSelectorSheet.kt`)**:
   - In `RouteSelectionButton.kt`, active route metrics subtitle SHALL format distance via `distanceFormatter.format_with_units(activeRoute.summary.distance)` and elevation via `"+${altitudeFormatter.format_with_units(activeRoute.summary.elevationGain)}"`.
   - In `RouteSelectorSheet.kt`, `formatDistanceAndElevation` helper SHALL format distance and elevation via `DistanceFormatter().format_with_units(distanceMeters)` and `"+${AltitudeFormatter().format_with_units(elevationMeters)}"`.
   - Hardcoded `String.format(Locale.getDefault(), "%.1f km", distKm)` and `"+%.0f m"` templates SHALL be excised.

4. **Turn-by-Turn Navigation Prompt Banner (`TurnPromptBanner.kt`)**:
   - In `TurnPromptBanner.kt`, remaining distance before turn cue execution SHALL be formatted via `distanceFormatter.format_with_units(distanceRemaining)`.
   - Hardcoded `"${distanceRemaining} m"` template SHALL be excised.

5. **Lap Split Visualizer (`LapSplitVisualizer.kt`)**:
   - In `LapSplitVisualizer.kt`, `formatSplitDistance` SHALL return `DistanceFormatter().format_with_units(distanceM)`.
   - Hardcoded `String.format(Locale.US, "%.2f km", ...)` and `String.format(Locale.US, "%.0f m", ...)` templates SHALL be excised.

6. **Live Climb Sheet & Map Screens (`LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt`)**:
   - In `LiveClimbSheet.kt`, remaining elevation gain, finish states, and climb route counter SHALL be formatted via `altitudeFormatter.format_with_units(...)`.
   - In `ClimbOnMapScreen.kt` and `RouteOnMapScreen.kt`, hardcoded fallback strings `"${climb.elevationGainMeters.roundToInt()} m"` and `"${(startDistMeters / 1000.0).roundToInt()} km"` SHALL be excised.

7. **Formatter Robustness & Null Safety (`AltitudeFormatter.java`, `DistanceFormatter.java`, `TrainingApplication.java`)**:
   - `AltitudeFormatter.format_with_units` and `TrainingApplication.getUnit()` SHALL safeguard against null `ApplicationContext` and null `SharedPreferences` in pure unit test environments without throwing `NullPointerException`.

8. **Preservation of System Invariants**:
   - Turn prompt icons and cues (`REQ-UI-360`, `REQ-UI-361`), route selection dialogs, climb categorization badges, and 100% full-suite test pass rate MUST NOT be regressed.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-364`). Complements `REQ-UI-015` (*Unified Metric component system*), `REQ-UI-016` (*Centralized metric formatting context*), `REQ-UI-330` (*Turn-by-turn Navigation Prompts Overlay / Fork Decision Card*), `REQ-MAP-027` (*ClimbPro Live Climb Bottom Sheet*), `REQ-MAP-028` (*Turn-by-turn navigation banner*), and `REQ-MAP-029` (*Return Navigation and Dynamic Arrival Metrics*).
2. **Historical Origin & Commit Trace**: Early Compose migration (Sprints 2026-40.16 to 2026-41.8) introduced Compose cards where distance and elevation strings were hand-rolled with raw `"km"` and `"m"` templates.
3. **Root Reason for Existing Formulation**: Quick prototyping in metric locales where developers verified metric formatting, inadvertently bypassing `DistanceFormatter` and `AltitudeFormatter` and breaking Imperial unit support.
4. **Preservation of Core Invariants**: Metric and Imperial unit conversion formulas in `DistanceFormatter` and `AltitudeFormatter`, visual styling of cards, and 100% clean-room test pass rate strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Imperial Navigation Cues)**:
  * *Given* an athlete with Imperial units selected in settings,
  * *When* viewing `TurnPromptBanner`, `ForkDecisionCard`, `RouteSelectionButton`, or `ReturnNavigationHud`,
  * *Then* all distance values are formatted with `ft` or `mile`, and elevation values with `ft`, with zero occurrences of raw `km` or `m`.
* **Criterion 2 (Metric Navigation Cues)**:
  * *Given* an athlete with Metric units selected in settings,
  * *When* viewing these navigation cards,
  * *Then* distance and elevation values are formatted with canonical `m` and `km` with standard threshold precision.
* **Criterion 3 (Live Climb Telemetry)**:
  * *Given* `LiveClimbSheet` during an active or finished climb,
  * *When* viewing remaining distance and elevation gain,
  * *Then* metrics route through `DistanceFormatter` and `AltitudeFormatter` without raw inline unit templates.
* **Criterion 4 (Pure JVM Formatter Null Safety)**:
  * *Given* pure JVM unit tests invoking `DistanceFormatter` and `AltitudeFormatter` without an Android application context,
  * *When* formatting distances and altitudes,
  * *Then* the formatters return valid formatted strings without throwing `NullPointerException`.

---

## 4. Test Specification (TST-UI-327)

### 4.1 Unit & Contract Tests (`MetricFormatterContractTest.kt`)
1. **Fork Decision Card Formatting**:
   - Test that `ForkDecisionCard` distance and elevation text formats correctly for Metric (e.g. `38.40 km`, `+420 m`) and Imperial (e.g. `23.86 mile`, `+1378 ft`).
2. **Return Navigation State Formatting**:
   - Test that `ReturnNavigationState.formattedRemainingDistance` and `formattedRemainingClimb` format via canonical formatters across Metric and Imperial modes.
3. **Route Selection Button & Sheet**:
   - Test that `RouteSelectionButton` and `RouteSelectorSheet` format distance and elevation via canonical formatters.
4. **Turn Prompt Banner**:
   - Test that `TurnPromptBanner` formats remaining distance via canonical formatters.
5. **Lap Split Visualizer**:
   - Test that `LapSplitVisualizer` formats split distances via `DistanceFormatter`.
6. **Live Climb Sheet**:
   - Test that `LiveClimbSheet` uses `AltitudeFormatter` for elevation without hardcoded `" m"` strings.

### 4.2 Formatter Headless JVM Tests (`DistanceFormatterTest.kt`, `AltitudeFormatterTest.kt`)
1. **Headless Null Safety**:
   - Test `DistanceFormatter.format_with_units()` and `AltitudeFormatter.format_with_units()` with `TrainingApplication.getAppContext() == null` and null `SharedPreferences` to assert no NPE is thrown.

### 4.3 Clean-Room Full Suite Regression Run
- Execute `./gradlew testDebugUnitTest` asserting 100% test pass rate with zero regressions.

---

## 5. Traceability Matrix

| Requirement Clause | Test ID | Target Component |
| :--- | :--- | :--- |
| `REQ-UI-364.1` (Fork Decision Card) | `TST-UI-327.1` | `ForkDecisionCard.kt`, `ForkDecisionCardTest.kt` |
| `REQ-UI-364.2` (Return Navigation State) | `TST-UI-327.1` | `ReturnNavigationState.kt`, `ReturnNavigationHudContractTest.kt` |
| `REQ-UI-364.3` (Route Selection) | `TST-UI-327.1` | `RouteSelectionButton.kt`, `RouteSelectorSheet.kt` |
| `REQ-UI-364.4` (Turn Prompt Banner) | `TST-UI-327.1` | `TurnPromptBanner.kt` |
| `REQ-UI-364.5` (Lap Split Visualizer) | `TST-UI-327.1` | `LapSplitVisualizer.kt`, `LapSplitVisualizerTest.kt` |
| `REQ-UI-364.6` (Live Climb Sheet & Maps) | `TST-UI-327.1` | `LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt` |
| `REQ-UI-364.7` (Formatter Null Safety) | `TST-UI-327.2` | `DistanceFormatter.java`, `AltitudeFormatter.java`, `TrainingApplication.java` |
| System Invariants | `TST-UI-327.3` | Clean-room regression `./gradlew testDebugUnitTest` |
