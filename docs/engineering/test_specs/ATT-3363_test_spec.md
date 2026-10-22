# Stage 2: Requirement & Test Specification - ATT-3363: Centralize inline speed and unit conversions to use MyHelper

**Ticket**: [ATT-3363](https://rainerblind.atlassian.net/browse/ATT-3363)  
**Sub-task**: [ATT-3536](https://rainerblind.atlassian.net/browse/ATT-3536) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-365` (*Centralized Speed Unit Conversion and Formatter Harmonization via MyHelper (Rule 36)*), complementing `REQ-UI-015`, `REQ-UI-016`, and `REQ-UI-364`  
**Test Spec ID**: `TST-UI-328`  
**Branch**: `feature/ATT-3363`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Requirement Specification (REQ-UI-365)

### 1.1 Problem Statement & Rationale

`MyHelper.java` is the repository's single authoritative utility for unit conversions:
1. `MyHelper.mps2userUnit(speed)` / `MyHelper.mps2userUnit(speed, myUnits)`
2. `MyHelper.UserUnit2mps(speed)`
3. `MyHelper.getSpeedUnitNameId()` / `MyHelper.getShortSpeedUnitNameId()`

However, multiple components throughout the codebase duplicate inline conversion math or implement ad-hoc workarounds:
1. `TelemetryMetricUtils.kt:130`: `if (unit == MyUnits.METRIC) mps * 3.6 else mps * 2.236936`
2. `TelemetryMetricUtils.kt:190`: `val mps = if (unit == MyUnits.METRIC) value / 3.6 else value / 2.236936`
3. `EditSportTypeDialog.kt:80`: Hand-rolled formatting with string replacement:
   `fun formatSpeed(speed: Double): String = "%.2f".format(MyHelper.mps2userUnit(speed)).replace(",", ".")`
4. `SpeedFormatter.java:34-43`: Duplicates switch-case arithmetic (`* 3.6` and `* 2.23693629`) instead of calling `MyHelper.mps2userUnit`.

This violates **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**:
- **Magic Constant Duplication**: Mathematical constants (`3.6`, `2.236936`, `2.23693629`) are scattered across Kotlin and Java classes with slight precision discrepancies.
- **Asymmetric API**: `MyHelper` contains `mps2userUnit(double, MyUnits)` but only an unparameterized `UserUnit2mps(double)` which depends on global state.
- **Locale Brittle Formatting**: Calling `.replace(",", ".")` in `EditSportTypeDialog.kt` to compensate for comma-delivering locales while rejecting comma input in `OutlinedTextField` creates an inconsistent UX.

### 1.2 Functional & Architectural Scope

1. **Symmetric Overloads in `MyHelper.java`**:
   - Provide `public static double UserUnit2mps(double speed, @NonNull MyUnits myUnits)`.
   - Ensure `UserUnit2mps(double speed)` delegates to `UserUnit2mps(speed, TrainingApplication.getUnit())`.
   - Maintain exact mathematical constants: METRIC factor $3.6$, IMPERIAL factor $2.23693629$.

2. **Harmonization in `TelemetryMetricUtils.kt`**:
   - Replace manual conversion in `extractMetricValue` (`TelemetryMetricType.SPEED`) with `MyHelper.mps2userUnit(mps, unit)`.
   - Replace manual conversion in `formatValue` (`TelemetryMetricType.SPEED`) with `MyHelper.UserUnit2mps(value, unit)`.
   - Excise magic numbers `3.6` and `2.236936`.

3. **Locale Resilience in `EditSportTypeDialog.kt`**:
   - Format speed using `String.format(Locale.US, "%.2f", MyHelper.mps2userUnit(speed))`.
   - Support both dot and comma decimal separators in text field input parsing via `it.replace(',', '.').toDoubleOrNull()`.

4. **DRY Consolidation in `SpeedFormatter.java`**:
   - In `SpeedFormatter.format(Number speed_mps)`, replace the duplicate switch statement with `double speed = MyHelper.mps2userUnit(speed_mps.doubleValue())`.

5. **Preservation of System Invariants**:
   - Telemetry graph scrubbing, pace calculation, elevation profiles, and 100% full-suite unit test pass rate MUST NOT be regressed.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: Net-new requirement (`REQ-UI-365`). Complements `REQ-UI-015` (*Unified Metric component system*), `REQ-UI-016` (*Centralized metric formatting context*), and `REQ-UI-364` (*Centralized metric distance and altitude formatting enforcement*).
2. **Historical Origin & Commit Trace**: `TelemetryMetricUtils.kt` was created in Sprint 2026-40.16 (`ATT-2031`) for interactive elevation and telemetry profile scrubbing. Conversion logic was duplicated inline to avoid cross-module references. `EditSportTypeDialog.kt` was written in Sprint 2026-41.4 (`ATT-2751`), where `.replace(",", ".")` was used to work around local formatting issues.
3. **Root Reason for Existing Formulation**: Rapid iterative UI prototyping where developers implemented localized math rather than reusing centralized utilities.
4. **Preservation of Core Invariants**: Mathematical precision, Imperial and Metric parity, and 100% clean-room test pass rate strictly preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Zero Magic Conversion Numbers in Telemetry)**:
  * *Given* speed telemetry points displayed on a workout map scrub graph,
  * *When* converting speed values between m/s and user display units in Metric or Imperial modes,
  * *Then* conversions execute via `MyHelper.mps2userUnit` and `MyHelper.UserUnit2mps` with zero magic constants in `TelemetryMetricUtils.kt`.
* **Criterion 2 (Consistent Locale Formatting & Input in Sport Type Editor)**:
  * *Given* the sport type editor dialog (`EditSportTypeDialog`) opened in any language locale,
  * *When* displaying or editing minimum and maximum speed thresholds,
  * *Then* speed values format properly without ad-hoc string character replacement, and user inputs accepting comma or dot decimal separators parse without error.
* **Criterion 3 (DRY SpeedFormatter Consolidation)**:
  * *Given* `SpeedFormatter.format(speed_mps)`,
  * *When* formatting speed values,
  * *Then* the conversion calculation routes through `MyHelper.mps2userUnit()`.
* **Criterion 4 (Pure JVM Formatter & Conversion Parity)**:
  * *Given* pure JVM unit tests verifying speed conversions,
  * *When* executing `MyHelper` and `TelemetryMetricUtils` unit tests under both `MyUnits.METRIC` and `MyUnits.IMPERIAL`,
  * *Then* conversions match expected mathematical values within $10^{-6}$ tolerance.

---

## 4. Test Specification (TST-UI-328)

### 4.1 Unit & Contract Test Cases

1. **`MyHelperSpeedConversionTest.kt`**:
   - `testMetricSpeedConversionSymmetry`: Given speeds in m/s (0.0, 1.0, 5.0, 10.0, 25.0), verify `UserUnit2mps(mps2userUnit(v, METRIC), METRIC) == v` within $10^{-9}$.
   - `testImperialSpeedConversionSymmetry`: Given speeds in m/s, verify `UserUnit2mps(mps2userUnit(v, IMPERIAL), IMPERIAL) == v` within $10^{-9}$.
   - `testMetricSpeedConversionFactor`: Verify `mps2userUnit(10.0, METRIC)` equals exactly `36.0 km/h`.
   - `testImperialSpeedConversionFactor`: Verify `mps2userUnit(10.0, IMPERIAL)` equals approximately `22.3693629 mph`.
   - `testDefaultOverloadDelegation`: Verify `mps2userUnit(v)` and `UserUnit2mps(v)` match the overloaded versions using `TrainingApplication.getUnit()`.

2. **`TelemetryMetricUtilsTest.kt`**:
   - Verify `extractMetricValue` for `SPEED` under `METRIC` returns `speedMps * 3.6`.
   - Verify `extractMetricValue` for `SPEED` under `IMPERIAL` returns `speedMps * 2.23693629`.
   - Verify `formatValue` for `SPEED` converts user units back to m/s via `MyHelper.UserUnit2mps` before passing to `SpeedFormatter`.
   - Verify zero occurrences of inline constants `3.6` or `2.236936` in `TelemetryMetricUtils.kt`.

3. **`EditSportTypeDialogTest.kt`**:
   - Verify speed formatting produces clean decimal strings without string replacement artifacts.
   - Verify comma and dot decimal inputs parse into identical numeric thresholds.

4. **`SpeedFormatterTest.kt`**:
   - Verify `SpeedFormatter.format` matches `MyHelper.mps2userUnit` across both units.

5. **Full Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` and assert 100% pass rate.
