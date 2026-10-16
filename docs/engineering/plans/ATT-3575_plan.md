# Stage 3: Implementation Plan - ATT-3575 (Revised)

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

### 1.2 Target File Modifications & Architectural Decisions
1. **`app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt`**:
   - Signature update: Add optional `context: Context? = null` parameter to `detectClimbs` to enable explicit context injection while retaining default fallback:
     ```kotlin
     val appContext = context ?: TrainingApplication.getAppContext()
     val climbTitle = appContext?.getString(R.string.climb_title) ?: "Climb"
     ```
   - No `try-catch` blocks: Clean null-safety navigation using standard Kotlin nullable types without hiding lifecycle bugs.
   - Format distance using canonical `DistanceFormatter().format_with_units(dist)`.
   - Authoritative format string: `val name = "$climbTitle $formattedDistance @ ${String.format(Locale.US, "%.1f%%", avgGrade)}"`.

2. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculator.kt`**:
   - Retain and document `MIN_VALID_SPEED = 0.3` (m/s) guard clause:
     - In `calculateSplitData`, speed <= 0.3 m/s renders `"--"` for running pace.
     - In `defaultPaceFormatter(paceSecPerMeter: Double)`: guard against invalid/stopped pace (`if (paceSecPerMeter <= 0.0 || (1.0 / paceSecPerMeter) <= MIN_VALID_SPEED) return "--"`) before delegating to `PaceFormatter().format_with_units(paceSecPerMeter)`.
     - In `defaultSpeedFormatter(speedMps: Double)`: delegate to `SpeedFormatter().format_with_units(speedMps)`, eliminating magic multiplier `3.6` and hardcoded `"%.1f km/h"`.

3. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt`**:
   - In `formatNavigationSubtitle`: Replace `String.format(Locale.getDefault(), "%.1f km", radiusKm)` with canonical `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`.

4. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`**:
   - Replace hardcoded `"%.1f km"` in `valueText` and `"1.0 km"` in `defaultText` with dynamic `DistanceFormatter().format_with_units(...)`.

5. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt`**:
   - Replace inline `slopeMinSpeed * 3.6f` and `"km/h"` with `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())` within `String.format(Locale.getDefault(), "%.1f m/s (%s)", slopeMinSpeed, speedFormatted)`.

6. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt`**:
   - Complete excision of `@Deprecated fun TuningSlider(...)` and its associated imports.

7. **Contract & Regression Tests**:
   - **`ClusterTuningContractTest.kt`**:
     - Remove declaration assertion for `TuningSlider`.
     - Add recursive source scanning test across `app/src/main/java/com/atrainingtracker/trainingtracker/ui/` asserting zero call sites or declarations of `TuningSlider(`.
   - **`MetricFormatterContractTest.kt`**:
     - Add `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt` to inspected files and assert absence of hardcoded templates (`"%.1f km"`, `"%.2f km"`, `"Anstieg"`) and presence of `DistanceFormatter`.
   - **`SpeedConversionContractTest.kt`**:
     - Add contract test asserting `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt` contain zero occurrences of `* 3.6` and `"%.1f km/h"`.
   - **`ClimbDetectorTest.kt`**:
     - Strengthen string assertion from weak `contains("2.0 km")` to exact equality:
       `assertEquals("Climb 2.00 km @ 6.0%", climb.name)`.

---

## 2. Atomic Step-by-Step Implementation Sequence

### Step 1: Clean Up Dead Code in `ClusterTuningContent.kt`
- Delete lines 202–254 of `ClusterTuningContent.kt`.
- Clean unused imports (`Slider`, `alpha`, `TTAlpha`).

### Step 2: Refactor `ClimbDetector.kt`
- Add optional `context: Context? = null` parameter to `detectClimbs`.
- Replace line 110:
  ```kotlin
  val appContext = context ?: TrainingApplication.getAppContext()
  val climbTitle = appContext?.getString(R.string.climb_title) ?: "Climb"
  val formattedDistance = DistanceFormatter().format_with_units(dist)
  val name = "$climbTitle $formattedDistance @ ${String.format(Locale.US, "%.1f%%", avgGrade)}"
  ```
- Import `com.atrainingtracker.R`, `com.atrainingtracker.banalservice.sensor.formater.DistanceFormatter`, and `com.atrainingtracker.trainingtracker.TrainingApplication`.

### Step 3: Refactor `LapSplitCalculator.kt`
- Update `defaultPaceFormatter` and `defaultSpeedFormatter`:
  ```kotlin
  private fun defaultPaceFormatter(paceSecPerMeter: Double): String {
      if (paceSecPerMeter <= 0.0 || (1.0 / paceSecPerMeter) <= MIN_VALID_SPEED) {
          return "--"
      }
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
  - In `clusterTuningContent_tuningSliderIsDeprecatedWithFullyQualifiedReplaceWith`: change to `clusterTuningContent_deadTuningSliderIsCompletelyRemoved` asserting `fun TuningSlider(` and `@Deprecated` for `TuningSlider` are absent.
  - Add `clusterTuningContent_noOrphanedTuningSliderReferencesInUiPackage`: recursively scan `ui/` directory and assert zero occurrences of `TuningSlider(`.
- In `MetricFormatterContractTest.kt`:
  - Add `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt` to scanned files.
- In `SpeedConversionContractTest.kt`:
  - Add test asserting `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt` contain zero occurrences of `* 3.6` and `"%.1f km/h"`.
- In `ClimbDetectorTest.kt`:
  - Update line 63 assertion to exact match: `assertEquals("Climb 2.00 km @ 6.0%", climb.name)`.

### Step 8: Clean-Room Verification
- Execute `./gradlew testDebugUnitTest` and ensure 100% clean-room pass rate across all 3001+ tests.

---

## 3. Preserved Invariants & Chesterton's Fence Safeguards
1. **Numerical Invariants**: Climb detection scores, bounding boxes, pace/speed calculations, and tuning slider bounds are strictly unchanged.
2. **Localization Parity**: 100% adherence to all 9 supported languages via `R.string.climb_title` and `TrainingApplication.getUnit()`.
3. **Headless JVM Safety**: Safe handling when `ApplicationContext` is null without silent catch swallowing.
4. **Guard Clause Consistency**: Explicit enforcement of `0.3 m/s` threshold across `LapSplitCalculator`.
5. **Architecture Contract Enforcement**: Zero hardcoded metric/speed conversion literals in all target files, validated by recursive contract tests.
