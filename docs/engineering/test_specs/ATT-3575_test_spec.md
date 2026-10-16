# Stage 2: Requirement & Test Specification - ATT-3575

## Ticket Overview
- **Ticket ID**: ATT-3575
- **Subtask ID**: ATT-3746
- **Summary**: Complete Canonical Formatter, Speed Conversion, and Dead TuningSlider Cleanup in ClimbDetector, LapSplitCalculator, and Tuning Screens
- **Requirement ID**: `REQ-UI-382`
- **Test Specification ID**: `TST-UI-345`
- **Active Branch**: `feature/ATT-3575`

---

## 1. Formal Requirement Specification (`REQ-UI-382`)

### 1.1 Requirement Title
**Comprehensive Canonical Formatting, Unit Conversion, and Dead Control Elimination Architecture**

### 1.2 Description
The system SHALL eliminate all hardcoded distance/speed unit conversions, raw metric formatting strings, and obsolete deprecated tuning controls across all remaining production components in strict adherence to Rule 36 (Single Source of Truth & DRY) and Rule 29 (Full 9-Language Localization Parity) (ATT-3575):

1. **Climb Detector Localization & Canonical Distance Formatting (`REQ-UI-382.1`)**:
   - `ClimbDetector.kt` SHALL format climb distance via `DistanceFormatter().format_with_units(dist)` instead of manual metric division `dist / 1000.0`.
   - `ClimbDetector.kt` SHALL resolve the localized climb title via `TrainingApplication.getAppContext()?.getString(R.string.climb_title) ?: "Climb"` instead of hardcoding German `"Anstieg"`.
   - The climb name template SHALL format as `"$climbTitle $formattedDistance @ ${String.format(Locale.getDefault(), "%.1f%%", avgGrade)}"`.

2. **Lap Split Calculator Formatter Delegation (`REQ-UI-382.2`)**:
   - In `LapSplitCalculator.kt`, `defaultPaceFormatter(paceSecPerMeter: Double)` SHALL delegate directly to `PaceFormatter().format_with_units(paceSecPerMeter)`, eliminating raw `"%d:%02d /km"`.
   - In `LapSplitCalculator.kt`, `defaultSpeedFormatter(speedMps: Double)` SHALL delegate directly to `SpeedFormatter().format_with_units(speedMps)`, eliminating raw magic multiplier `3.6` and `"%.1f km/h"`.

3. **Tuning Accordion and Navigation Section Distance Formatting (`REQ-UI-382.3`)**:
   - In `AdvancedTuningAccordion.kt`, `formatNavigationSubtitle` SHALL format route selection radius using `DistanceFormatter().format_with_units((radiusKm * 1000f).toDouble())`, eliminating hardcoded `"%.1f km"`.
   - In `NavigationSection.kt`, route selection radius slider `valueText` and default text SHALL format using `DistanceFormatter().format_with_units((routeSelectionRadiusKm * 1000f).toDouble())` and `DistanceFormatter().format_with_units((TuningPreferencesDefaults.DEFAULT_ROUTE_SELECTION_RADIUS_KM * 1000f).toDouble())`.

4. **Sensors GPS Filter Section Canonical Speed Formatting (`REQ-UI-382.4`)**:
   - In `SensorsGpsFilterSection.kt`, the slope minimum speed slider `valueText` SHALL format secondary speed via `SpeedFormatter().format_with_units(slopeMinSpeed.toDouble())` within `String.format(Locale.getDefault(), "%.1f m/s (%s)", slopeMinSpeed, SpeedFormatter().format_with_units(slopeMinSpeed.toDouble()))`, eliminating inline `slopeMinSpeed * 3.6f` and `"km/h"`.

5. **Cluster Tuning Dead Slider Elimination (`REQ-UI-382.5`)**:
   - In `ClusterTuningContent.kt`, the dead `@Deprecated fun TuningSlider(...)` composable and all related imports SHALL be completely excised from the codebase.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Complements and closes technical debt from `REQ-UI-364` (ATT-3361), `REQ-UI-365` (ATT-3363), and `REQ-SET-077` (ATT-3365).
   - Formally tracked as `REQ-UI-382`.
2. **Historical Origin & Commit Trace**:
   - Prototyping commits for ClimbPro (ATT-1281) introduced hardcoded `"Anstieg"`.
   - Sprint 2026-42.3 standardized primary UI call sites but omitted helper and tuning modules.
3. **Root Reason for Existing Formulation**:
   - Initial feature spikes used ad-hoc string formatting; subsequent standardization passes did not scan peripheral files.
4. **Preservation of Core Invariants**:
   - Numerical calculations (climb scores, pace values, tuning sliders) remain identical.
   - 9-language localization parity is fully maintained (`R.string.climb_title` is already verified across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - 100% full clean-room unit test suite pass rate strictly preserved.

---

## 3. Formal Test Specification (`TST-UI-345`)

### 3.1 Contract Tests
1. **`MetricFormatterContractTest.kt`**:
   - Scan `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt`.
   - Assert zero occurrences of `"%.1f km"`, `"%.2f km"`, or `"Anstieg %.1f"`.
   - Assert explicit reference to `DistanceFormatter`.
2. **`SpeedConversionContractTest.kt`**:
   - Scan `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt`.
   - Assert zero occurrences of `* 3.6` (outside comments) or `"%.1f km/h"`.
   - Assert explicit reference to `SpeedFormatter` and `PaceFormatter`.
3. **`ClusterTuningContractTest.kt`**:
   - Assert `ClusterTuningContent.kt` does NOT declare `fun TuningSlider(`.
   - Assert zero occurrences of `@Deprecated` referencing `TuningSliderItem`.

### 3.2 Regression & Functional Unit Tests
1. **`ClimbDetectorTest.kt`**:
   - Verify `detectClimbs` produces valid climb names matching localized title and `DistanceFormatter().format_with_units(dist)`.
2. **`LapSplitCalculatorTest.kt`**:
   - Verify `calculateSplitData` accurately formats run pace (`min/km` or `min/mile`) and cycling speed (`km/h` or `mile/h`).
3. **Clean-Room Full Suite Regression Execution**:
   - Execute `./gradlew testDebugUnitTest` asserting 100% pass rate.

---

## 4. Acceptance Criteria (Given-When-Then)

- **Criterion 1: Zero Hardcoded Formatting Literals**:
  - *Given* `ClimbDetector.kt`, `LapSplitCalculator.kt`, `AdvancedTuningAccordion.kt`, `NavigationSection.kt`, and `SensorsGpsFilterSection.kt`,
  - *When* running `MetricFormatterContractTest` and `SpeedConversionContractTest`,
  - *Then* all contract assertions pass with zero hardcoded `"%.1f km"`, `"%.1f km/h"`, `"Anstieg"`, or `* 3.6` formulas.

- **Criterion 2: Dead TuningSlider Removed**:
  - *Given* `ClusterTuningContent.kt`,
  - *When* running `ClusterTuningContractTest`,
  - *Then* `fun TuningSlider` is completely absent.

- **Criterion 3: Regression Immunity**:
  - *Given* the entire unit test suite,
  - *When* running `./gradlew testDebugUnitTest`,
  - *Then* 100% of tests pass without errors.
