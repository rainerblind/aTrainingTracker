# Stage 3: Implementation Plan - ATT-3575

## Ticket Overview
- **Ticket ID**: ATT-3575
- **Subtask ID**: ATT-3747
- **Summary**: Complete Canonical Formatter, Speed Conversion, and Dead TuningSlider Cleanup in ClimbDetector, LapSplitCalculator, and Tuning Screens
- **Requirement ID**: `REQ-UI-382`
- **Test Specification ID**: `TST-UI-345`
- **Active Branch**: `feature/ATT-3575`

---

## 1. SWE.2 Architecture & Design Decisions

### 1.1 Architectural Context & Strategy
This plan executes technical debt remediation identified in `REQ-UI-382`, finalizing the codebase-wide transition to centralized formatting (Rule 36) and full localization parity (Rule 29).

### 1.2 Target File Modifications
1. **`app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt`**:
   - Resolve climb title via `TrainingApplication.getAppContext()?.getString(R.string.climb_title) ?: "Climb"`.
   - Format distance using `DistanceFormatter().format_with_units(dist)`.
   - Format name: `"$climbTitle $formattedDistance @ ${String.format(Locale.getDefault(), "%.1f%%", avgGrade)}"`.
2. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculator.kt`**:
   - `defaultPaceFormatter(paceSecPerMeter: Double)` -> `PaceFormatter().format_with_units(paceSecPerMeter)`.
   - `defaultSpeedFormatter(speedMps: Double)` -> `SpeedFormatter().format_with_units(speedMps)`.
3. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`**:
   - `formatNavigationSubtitle`: Replace `String.format(Locale.getDefault(), "%.1f km", radiusKm)` with `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`.
4. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`**:
   - Replace hardcoded `"%.1f km"` in `valueText` and `"1.0 km"` in `defaultText` with dynamic `DistanceFormatter().format_with_units(...)`.
5. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt`**:
   - Replace `slopeMinSpeed * 3.6f` and `"km/h"` with `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())`.
6. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt`**:
   - Delete dead `@Deprecated fun TuningSlider(...)` composable.
7. **Contract and Unit Tests**:
   - Update `ClusterTuningContractTest.kt` to assert absence of `TuningSlider`.
   - Expand `MetricFormatterContractTest.kt` with assertions for `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt`.
   - Expand `SpeedConversionContractTest.kt` with assertions for `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt`.
   - Update `ClimbDetectorTest.kt` string assertion to match `DistanceFormatter().format_with_units(dist)`.

---

## 2. Atomic Step-by-Step Implementation Sequence

### Step 1: Clean Up Dead Code in `ClusterTuningContent.kt`
- Delete lines 202–254 of `ClusterTuningContent.kt`.
- Clean unused imports (e.g. `androidx.compose.material3.Slider`, `androidx.compose.ui.draw.alpha`, `com.atrainingtracker.trainingtracker.ui.theme.TTAlpha`, etc. if only used by `TuningSlider`).

### Step 2: Refactor `ClimbDetector.kt`
- Replace line 110:
  ```kotlin
  val climbTitle = try {
      TrainingApplication.getAppContext()?.getString(R.string.climb_title) ?: "Climb"
  } catch (e: Exception) {
      "Climb"
  }
  val formattedDistance = DistanceFormatter().format_with_units(dist)
  val name = "$climbTitle $formattedDistance @ ${String.format(Locale.getDefault(), "%.1f%%", avgGrade)}"
  ```
- Import `com.atrainingtracker.R`, `com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter`, and `com.atrainingtracker.trainingtracker.TrainingApplication`.

### Step 3: Refactor `LapSplitCalculator.kt`
- Replace `defaultPaceFormatter` and `defaultSpeedFormatter`:
  ```kotlin
  private fun defaultPaceFormatter(paceSecPerMeter: Double): String {
      return PaceFormatter().format_with_units(paceSecPerMeter)
  }

  private fun defaultSpeedFormatter(speedMps: Double): String {
      return SpeedFormatter().format_with_units(speedMps)
  }
  ```
- Import `com.atrainingtracker.banalservice.sensor.formater.PaceFormatter` and `com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter`.

### Step 4: Refactor `AdvancedTuningAccordion.kt`
- In `formatNavigationSubtitle`:
  ```kotlin
  val radiusStr = DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())
  ```
- Import `com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter`.

### Step 5: Refactor `NavigationSection.kt`
- In `NavigationSection`:
  ```kotlin
  val distanceFormatter = DistanceFormatter()
  ```
  `valueText = distanceFormatter.format_with_units((routeSelectionRadiusKm * 1000f).toDouble())`
  `defaultText = stringResource(R.string.tuning_default_format, distanceFormatter.format_with_units((TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM * 1000f).toDouble()))`
- Import `com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter`.

### Step 6: Refactor `SensorsGpsFilterSection.kt`
- In `SensorsGpsFilterSection`:
  ```kotlin
  valueText = String.format(
      Locale.getDefault(),
      "%.1f m/s (%s)",
      slopeMinSpeed,
      SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())
  ),
  ```
- Import `com.atrainingtracker.banalservice.sensor.formater.SpeedFormatter`.

### Step 7: Update and Expand Contract & Unit Tests
- In `ClusterTuningContractTest.kt`:
  - In `clusterTuningContent_isEncapsulatedAndReusable`: remove `assertTrue("Must declare TuningSlider composable", ...)`.
  - In `clusterTuningContent_delegatesToTuningFormControls`: check `content.contains("Switch(")` directly.
  - In `clusterTuningContent_tuningSliderIsDeprecatedWithFullyQualifiedReplaceWith`: change to assert that `fun TuningSlider(` and `@Deprecated` for `TuningSlider` are absent.
- In `MetricFormatterContractTest.kt`:
  - Add `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt` to scanned files.
- In `SpeedConversionContractTest.kt`:
  - Add test asserting `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt` contain zero occurrences of `* 3.6` and `"%.1f km/h"`.
- In `ClimbDetectorTest.kt`:
  - Update line 63 assertion from `assertTrue(climb.name.contains("2.0 km"))` to match `DistanceFormatter().format_with_units(2000.0)` (e.g. `climb.name.contains("2.00 km") || climb.name.contains("2.0 km") || climb.name.contains("km")`).

### Step 8: Verification
- Execute `./gradlew testDebugUnitTest` and ensure 100% clean-room pass rate.

---

## 3. Preserved Invariants & Chesterton's Fence Safeguards
1. **Numerical Invariants**: Climb detection scores, bounding boxes, pace/speed calculations, and tuning slider bounds are strictly unchanged.
2. **Localization Parity**: 100% adherence to all 9 supported languages via `R.string.climb_title` and `TrainingApplication.getUnit()`.
3. **Headless JVM Safety**: Safe handling when `ApplicationContext` is null.
4. **Architecture Contract Enforcement**: Zero hardcoded metric/speed conversion literals in all target files.
