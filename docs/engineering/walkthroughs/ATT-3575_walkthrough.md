# Stage 5 Walkthrough: Complete Canonical Formatter, Speed Conversion, and Dead TuningSlider Cleanup

## Ticket Reference
- **Ticket ID**: ATT-3575
- **Subtask ID**: ATT-3749
- **Branch**: `feature/ATT-3575`
- **Requirement ID**: `REQ-UI-382`
- **Test Specification ID**: `TST-UI-345`

---

## 1. Executive Summary
In compliance with `REQ-UI-382`, Rule 36 (Single Source of Truth & DRY), and Rule 29 (Full 9-Language Localization Parity), all remaining hardcoded distance/speed unit conversions, raw metric string templates, and deprecated dead controls have been completely eliminated across the 6 production files identified in the problem domain:

1. `ClimbDetector.kt`: Formats distance via `DistanceFormatter().format_with_units(dist)` and resolves the climb title via `(context ?: TrainingApplication.getAppContext())?.getString(R.string.climb_title) ?: "Climb"` across all 9 languages.
2. `LapSplitCalculator.kt`: Delegates `defaultPaceFormatter` to `PaceFormatter().format_with_units(paceSecPerMeter)` (with `0.3 m/s` threshold guard) and `defaultSpeedFormatter` to `SpeedFormatter().format_with_units(speedMps)`.
3. `AdvancedTuningAccordion.kt`: Subtitle route selection radius formats dynamically via `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`.
4. `NavigationSection.kt`: Route selection radius slider value text and default text format dynamically via `DistanceFormatter().format_with_units(...)`.
5. `SensorsGpsFilterSection.kt`: Slope minimum speed secondary speed formats dynamically via `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())`.
6. `ClusterTuningContent.kt`: The dead `@Deprecated fun TuningSlider(...)` composable has been excised completely.

Comprehensive contract tests in `MetricFormatterContractTest.kt`, `SpeedConversionContractTest.kt`, and `ClusterTuningContractTest.kt` statically enforce zero occurrences of hardcoded formatting formulas and absence of dead sliders.

---

## 2. Changes Implemented

### 2.1 Production Source Refactorings
- **`ClimbDetector.kt`**:
  - Replaced manual metric division `dist / 1000.0` and hardcoded `"Anstieg"` with `DistanceFormatter` and `R.string.climb_title`.
  - Added optional `context: Context? = null` parameter for explicit context injection in tests or callers.
- **`LapSplitCalculator.kt`**:
  - Replaced magic constant `3.6` and raw `"%d:%02d /km"` / `"%.1f km/h"` string templates with `PaceFormatter` and `SpeedFormatter`.
  - Preserved the `0.3 m/s` minimum speed guard to ensure stopped/zero-pace samples cleanly return `"--"`.
- **`AdvancedTuningAccordion.kt`**:
  - `formatNavigationSubtitle`: Migrated `String.format(Locale.getDefault(), "%.1f km", radiusKm)` to `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`.
- **`NavigationSection.kt`**:
  - Migrated `valueText` and `defaultText` from hardcoded `"%.1f km"` / `"1.0 km"` to `DistanceFormatter().format_with_units(...)`.
- **`SensorsGpsFilterSection.kt`**:
  - Migrated `valueText` from inline `slopeMinSpeed * 3.6f` / `"km/h"` to `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())`.
- **`ClusterTuningContent.kt`**:
  - Removed all 53 lines of `@Deprecated fun TuningSlider(...)` along with unused `alpha` imports.

### 2.2 Test Suite Updates
- **`ClimbDetectorTest.kt`**:
  - Added deterministic `@Before`/`@After` setup for `TrainingApplication.setUnitForTesting(MyUnits.METRIC)`.
  - Updated climb name verification to assert against canonical `DistanceFormatter().format_with_units(2000.0)`.
- **`MetricFormatterContractTest.kt`**:
  - Added `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt` to inspected files.
  - Asserted zero occurrences of `"%.1f km"`, `"%.2f km"`, or `"Anstieg"`, and verified explicit reference to `DistanceFormatter`.
- **`SpeedConversionContractTest.kt`**:
  - Added `testLapSplitCalculatorAndSensorsGpsFilterUseCanonicalFormattersWithoutMagicMultiplier`, asserting zero occurrences of `* 3.6` and `"%.1f km/h"` outside comments and verifying explicit reference to `SpeedFormatter`.
- **`ClusterTuningContractTest.kt`**:
  - Updated `clusterTuningContent_deadTuningSliderIsCompletelyRemoved` asserting that `fun TuningSlider(` and `@Deprecated` for `TuningSlider` are absent.
  - Added `clusterTuning_noOrphanedTuningSliderReferencesInUiPackage`, recursively verifying zero orphaned references to `TuningSlider(` in the entire `ui/` directory.

---

## 3. Verification & Quality Assurance

### 3.1 Targeted Contract & Unit Tests
- `ClimbDetectorTest`: Passed 100%.
- `LapSplitCalculatorTest`: Passed 100%.
- `ClusterTuningContractTest`: Passed 100%.
- `MetricFormatterContractTest`: Passed 100%.
- `SpeedConversionContractTest`: Passed 100%.

### 3.2 Full Clean-Room Regression Test Suite
- Executed `./gradlew testDebugUnitTest` ensuring zero regressions across all 3001+ unit tests.

---

## 4. Living Documentation Status
- `REQ-UI-382`: Verified.
- `TST-UI-345`: Verified.
