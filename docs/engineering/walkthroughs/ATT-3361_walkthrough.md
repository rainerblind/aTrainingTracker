# Stage 5 Walkthrough - ATT-3361: Replace hardcoded distance and altitude strings with canonical formatters

**Ticket**: [ATT-3361](https://rainerblind.atlassian.net/browse/ATT-3361)  
**Stage 5 Subtask**: [ATT-3530](https://rainerblind.atlassian.net/browse/ATT-3530) (`[Test]`)  
**Parent Epic**: [ATT-1191](https://rainerblind.atlassian.net/browse/ATT-1191) (*Architecture & Refactoring*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-364` (`Verified`)  
**Test Mapping**: `TST-UI-327` (`Verified`)  
**Branch**: `feature/ATT-3361`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Implementation Details

During early Compose UI migrations, multiple navigation overlays, route selection cards, and HUD telemetry components introduced raw, hardcoded metric formatting templates (`"km"`, `"m"`). This violated **Rule 36 (Single Source of Truth & DRY)** and completely broke user preference support for athletes selecting Imperial units (`miles`, `feet`).

ATT-3361 centralizes all distance and elevation formatting across navigation cards and telemetry components using canonical formatters (`DistanceFormatter` and `AltitudeFormatter`) and `LocalMetricFormatter.current`, guaranteeing full Imperial unit parity and headless JVM test safety.

### Key Architectural Changes & Refactorings

1. **Headless JVM Null-Safety in Core Formatters**:
   - `TrainingApplication.java`: Added null-check in `getUnit()` when `cSharedPreferences` is null in pure JVM unit tests, falling back to `MyUnits.METRIC`. Introduced `TrainingApplication.setUnitForTesting(MyUnits unit)` to allow unit tests to cleanly toggle unit modes without mocking Android components or touching disk.
   - `AltitudeFormatter.java`: Updated `format_with_units(float altitudeMeters)` with an `appContext != null` guard. If `getAppContext()` is null in headless test environments, it falls back to canonical unit suffixes (`"ft"` if `getUnit() == MyUnits.IMPERIAL` else `"m"`), preventing `NullPointerException` across all headless JVM unit test runners.

2. **Return Navigation HUD (`ReturnNavigationState.kt`)**:
   - Replaced hardcoded `String.format(Locale.US, "%.1f km", km)` in `formattedRemainingDistance` with `DistanceFormatter().format_with_units(remainingDistanceMeters)`.
   - Replaced hardcoded `"+ m"` in `formattedRemainingClimb` with `"+${AltitudeFormatter().format_with_units(remainingElevationGainMeters)}"`.
   - Updated contract tests in `ReturnNavigationHudContractTest.kt` to assert against canonical formatter output.

3. **Fork Decision Navigation Card (`ForkDecisionCard.kt`)**:
   - Replaced hardcoded `String.format(Locale.US, "%.1f km", distKm)` with `LocalMetricFormatter.current.distance.format_with_units(branch.totalDistanceMeters)`.
   - Replaced hardcoded `"+${branch.totalElevationMeters.roundToInt()} m"` with `"+${LocalMetricFormatter.current.altitude.format_with_units(branch.totalElevationMeters)}"`.

4. **Route Selection Button & Sheet (`RouteSelectionButton.kt` & `RouteSelectorSheet.kt`)**:
   - In `RouteSelectionButton.kt`, replaced raw string formatting with `distanceFormatter.format_with_units(activeRoute.summary.distance)` and `"+${altitudeFormatter.format_with_units(activeRoute.summary.elevationGain)}"`.
   - In `RouteSelectorSheet.kt` (`formatDistanceAndElevation`), replaced hardcoded `String.format(Locale.getDefault(), "%.1f km", distKm)` and `"+%.0f m"` with `DistanceFormatter().format_with_units(distanceMeters)` and `"+${AltitudeFormatter().format_with_units(elevationMeters)}"`.

5. **Turn-by-Turn Navigation Prompt Banner (`TurnPromptBanner.kt`)**:
   - Replaced hardcoded `"${distanceRemaining} m"` template with `LocalMetricFormatter.current.distance.format_with_units(distanceRemaining)`.

6. **Lap Split Visualizer (`LapSplitVisualizer.kt`)**:
   - Refactored `formatSplitDistance(distanceM: Double)` to delegate directly to `DistanceFormatter().format_with_units(distanceM)`, excising custom piecewise branch logic and hardcoded templates (`"%.2f km"`, `"%.0f m"`).

7. **Live Climb Sheet & Map Screens (`LiveClimbSheet.kt`, `ClimbOnMapScreen.kt`, `RouteOnMapScreen.kt`)**:
   - In `LiveClimbSheet.kt`, replaced raw `"${gain.roundToInt()} m"` with `altitudeFormatter.format_with_units(gain)`. Replaced fallback `" m"` literals with empty string or canonical formatter output.
   - In `ClimbOnMapScreen.kt` and `RouteOnMapScreen.kt`, eliminated raw unit fallback literals (`" m"` and `" km"`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-364` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards (Rule 36)*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-UI-327` (*Centralized Metric Distance and Altitude Formatting Enforcement Across Navigation and Telemetry Cards Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Rule 36 (Single Source of Truth & DRY)**: Zero duplicate formatting routines for distance and elevation; all UI cards route through canonical `DistanceFormatter` and `AltitudeFormatter`.
2. **Imperial Unit Parity**: When `MyUnits.IMPERIAL` is configured, all distance values render in `ft` or `mile`, and elevation in `ft`, without rogue metric literals.
3. **Pure Compose Architecture**: Jetpack Compose UI state management and composable purity are strictly preserved.
4. **Headless JVM Test Execution**: Formatters execute safely in pure JVM unit tests without requiring Android framework stubs or throwing `NullPointerException`.
5. **Clean-Room Test Regression Free**: 100% full unit test pass rate maintained with zero regressions.

---

## 4. Test Verification Results

### Unit & Contract Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `MetricFormatterContractTest` | Verifies `ForkDecisionCard`, `ReturnNavigationState`, `RouteSelectionButton`, `RouteSelectorSheet`, `TurnPromptBanner`, `LapSplitVisualizer`, and `LiveClimbSheet` formatting under Metric and Imperial units, ensures headless null-safety, and verifies absence of hardcoded `"km"` and `" m"` templates in Imperial mode | **PASS** |
| `ReturnNavigationHudContractTest` | Verifies Return Navigation HUD remaining distance and elevation formatting using canonical formatters | **PASS** |
| `DistanceFormatterTest` | Verifies distance thresholding, rounding, and imperial conversion | **PASS** |
| `AltitudeFormatterTest` | Verifies altitude formatting and unit handling | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (0 failures, 0 errors, 0 regressions across all modules).
