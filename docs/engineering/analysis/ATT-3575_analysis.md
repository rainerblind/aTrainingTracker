# Stage 1: Problem Domain & Root Cause Analysis (RCA) - ATT-3575

## Ticket Overview
- **Ticket ID**: ATT-3575
- **Subtask ID**: ATT-3745
- **Summary**: Complete Canonical Formatter, Speed Conversion, and Dead TuningSlider Cleanup in ClimbDetector, LapSplitCalculator, and Tuning Screens
- **Type**: Improvement (Verbesserung)
- **Component**: UI / Formatting / Tuning Architecture
- **Sprint**: 2026-42.5
- **Active Branch**: `feature/ATT-3575`

---

## 1. Problem Domain & Forensic Root Cause Analysis

### 1.1 Technical Background & Context
In Sprint 2026-42.3, the project executed systematic architecture standardizations:
- **ATT-3361**: Centralized metric distance and altitude formatting across navigation and telemetry cards via `DistanceFormatter` and `AltitudeFormatter` (enforcing Rule 36 and Imperial unit parity).
- **ATT-3363**: Centralized speed and unit conversions via `MyHelper.mps2userUnit` and `UserUnit2mps` (eliminating magic numbers like `3.6` and `2.23693629`).
- **ATT-3365**: Standardized tuning controls into shared components (`TuningSliderItem`, `TuningToggleItem`, `TuningDropdownItem` in `TuningFormControls.kt`).

However, these tickets specifically migrated files referenced in their original scope descriptions. A comprehensive forensic audit across the codebase revealed **6 production files** that still violate **Rule 36 (Single Source of Truth & DRY)** and **Rule 29 (Full 9-Language Localization Parity)**:

1. **`app/src/main/java/com/atrainingtracker/trainingtracker/climbs/ClimbDetector.kt` (line 110)**:
   - *Current Code*: `val name = String.format(Locale.US, "Anstieg %.1f km @ %.1f%%", dist / 1000.0, avgGrade)`
   - *Violation*: Hardcodes the German word `"Anstieg"`, bypassing localized string resources (`R.string.climb_title`), and performs raw division (`dist / 1000.0`) with hardcoded `"km"`, violating Imperial unit settings.

2. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/splits/LapSplitCalculator.kt` (lines 139–151)**:
   - *Current Code*:
     ```kotlin
     private fun defaultPaceFormatter(paceSecPerMeter: Double): String {
         val totalSecPerKm = (paceSecPerMeter * 1000.0).roundToInt()
         val minutes = totalSecPerKm / 60
         val seconds = totalSecPerKm % 60
         return String.format(Locale.US, "%d:%02d /km", minutes, seconds)
     }
     private fun defaultSpeedFormatter(speedMps: Double): String {
         val speedKmh = speedMps * 3.6
         return String.format(Locale.US, "%.1f km/h", speedKmh)
     }
     ```
   - *Violation*: Manually multiplies by magic constant `3.6` and hardcodes metric-only string templates (`"%d:%02d /km"` and `"%.1f km/h"`), ignoring `PaceFormatter` and `SpeedFormatter`.

3. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningAccordion.kt` (line 121)**:
   - *Current Code*: `val radiusStr = String.format(Locale.getDefault(), "%.1f km", radiusKm)`
   - *Violation*: Hardcodes `"%.1f km"`, bypassing `DistanceFormatter().format_with_units(...)`.

4. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt` (lines 59, 61)**:
   - *Current Code*:
     - `valueText = String.format(Locale.getDefault(), "%.1f km", routeSelectionRadiusKm)`
     - `defaultText = stringResource(R.string.tuning_default_format, "1.0 km")`
   - *Violation*: Hardcodes `"%.1f km"` and `"1.0 km"`, bypassing canonical distance formatting.

5. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/SensorsGpsFilterSection.kt` (line 112)**:
   - *Current Code*: `valueText = String.format(Locale.getDefault(), "%.1f m/s (%.1f km/h)", slopeMinSpeed, slopeMinSpeed * 3.6f)`
   - *Violation*: Inline speed conversion `slopeMinSpeed * 3.6f` and hardcoded metric suffix `"km/h"`, bypassing `SpeedFormatter().format_with_units(...)`.

6. **`app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/ClusterTuningContent.kt` (lines 202–254)**:
   - *Current Code*: Retains 53 lines of dead code (`@Deprecated fun TuningSlider(...)`) with zero callers anywhere in the repository after ATT-3365.
   - *Violation*: Dead code accumulation violating Rule 36.

---

## 2. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-364` (Centralized Metric Distance and Altitude Formatting Enforcement), `REQ-UI-365` (Centralized Speed and Unit Conversion Architecture), and `REQ-SET-077` (Standardized Cluster Tuning UI Controls).
   - Targets `ClimbDetector.kt`, `LapSplitCalculator.kt`, `AdvancedTuningAccordion.kt`, `NavigationSection.kt`, `SensorsGpsFilterSection.kt`, and `ClusterTuningContent.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-42.3 commits introducing canonical formatters and `TuningFormControls`.
   - `ClimbDetector.kt`: `Anstieg` string was hardcoded during initial climb detection prototyping in ATT-1281.
   - `ClusterTuningContent.kt`: `TuningSlider` was deprecated in ATT-3365 when migrating to `TuningSliderItem`.
3. **Root Reason for Existing Formulation**:
   - Rapid feature prototyping and partial refactoring where formatters were introduced in core HUD screens first, leaving peripheral helpers and settings accordions untouched.
4. **Preservation of Core Invariants**:
   - Numerical calculation fidelity: Climb detection scores, lap split paces, and tuning preference float values must remain identical.
   - Localization parity: `R.string.climb_title` is already translated in all 9 supported languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
   - Automated contract test enforcement: All target files must be guarded by contract tests asserting zero raw literals.

---

## 3. Scope Bounding & Impact Analysis

### 3.1 In-Scope Objectives
- Refactor all 6 target production files to delegate strictly to canonical formatters and delete dead `TuningSlider`.
- Expand `MetricFormatterContractTest.kt` to inspect `ClimbDetector.kt`, `AdvancedTuningAccordion.kt`, and `NavigationSection.kt`.
- Expand `SpeedConversionContractTest.kt` to inspect `LapSplitCalculator.kt` and `SensorsGpsFilterSection.kt`.
- Update `ClusterTuningContractTest.kt` to assert `fun TuningSlider` is completely absent.
- Update `ClimbDetectorTest.kt` if string assertions require matching `DistanceFormatter` output.

### 3.2 Out-of-Scope Objectives
- Refactoring `DistanceFormatter` or `SpeedFormatter` internals.
- Modifying tuning preferences data store keys or schema.
- Touching any unmentioned tuning screens or sensor components.

---

## 4. Risk Assessment & Mitigation
- **Risk**: Headless JVM tests calling `DistanceFormatter()` or `PaceFormatter()` when Android `ApplicationContext` is null.
  - **Mitigation**: `DistanceFormatter` and `PaceFormatter` already include headless JVM null-safety guards (verified by `DistanceFormatterTest` and `AltitudeFormatterTest`). In `ClimbDetector`, fallback gracefully if context is null: `TrainingApplication.getAppContext()?.getString(R.string.climb_title) ?: "Climb"`.
- **Risk**: Existing test assertion breaks in `ClimbDetectorTest`.
  - **Mitigation**: Adapt `ClimbDetectorTest` assertion to verify against `DistanceFormatter().format_with_units(dist)`.

---

## 5. Verification Strategy & Gate 1 Criteria
- Gate 1 requires complete forensic investigation of all 6 target files, identification of root causes, Chesterton's Fence archaeology, and clear scope boundaries.
- Gate 1 audit will be executed via `tools/review_agent.py audit ATT-3745`.
