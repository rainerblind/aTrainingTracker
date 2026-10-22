# Stage 5 Walkthrough - ATT-3363: Centralize inline speed and unit conversions to use MyHelper

**Ticket**: [ATT-3363](https://rainerblind.atlassian.net/browse/ATT-3363)  
**Stage 5 Subtask**: [ATT-3546](https://rainerblind.atlassian.net/browse/ATT-3546) (`[Test]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-42.3`  
**Requirement Mapping**: `REQ-UI-365` (`Verified`)  
**Test Mapping**: `TST-UI-328` (`Verified`)  
**Branch**: `feature/ATT-3363`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Executive Summary & Implementation Details

Multiple components across the application previously bypassed `MyHelper.java` and duplicated inline conversion math (`mps * 3.6`, `value / 3.6`, `mps * 2.236936`, `value / 2.236936`), or hand-rolled localized decimal formatting with ad-hoc string replacements (`.replace(",", ".")`). This violated **Rule 36 (Single Source of Truth & DRY — Shared Code Over Duplication)**, introducing precision discrepancies and risking formatting bugs across regional locales.

ATT-3363 centralizes all speed unit conversions and formatting strictly through `MyHelper` and `SpeedFormatter`, provides context-independent overloaded conversion methods for headless JVM unit tests, preserves native decimal formatting across regions (`Locale.getDefault()`), and hardens numeric input handling against comma/dot variations in European keyboard layouts.

### Key Architectural Changes & Refactorings

1. **Context-Independent Parameterized Overloads in `MyHelper.java`**:
   - Added `UserUnit2mps(double speed, @NonNull MyUnits myUnits)` to allow pure-JVM conversion without Android Context dependencies.
   - Refactored `UserUnit2mps(double speed)` to delegate to `UserUnit2mps(speed, TrainingApplication.getUnit())`, preserving 100% backward compatibility for all existing callers.
   - Centralized conversion constants strictly within `MyHelper`: `3.6` for `METRIC` and `2.23693629` for `IMPERIAL`.

2. **DRY Consolidation in `SpeedFormatter.java`**:
   - Refactored `format(Number speed_mps)` to delegate unit conversion directly to `MyHelper.mps2userUnit(speed_mps.doubleValue())`, eliminating redundant switch-case conversion logic and duplicated magic constants.

3. **Telemetry Graph & Map Utilities (`TelemetryMetricUtils.kt`)**:
   - Replaced inline ternary math in `extractMetricValue` (`TelemetryMetricType.SPEED`) with `MyHelper.mps2userUnit(mps, unit)`.
   - Replaced inline division in `formatValue` (`TelemetryMetricType.SPEED`) with `MyHelper.UserUnit2mps(value, unit)`.
   - Completely excised magic constants `3.6` and `2.236936` from telemetry utilities.

4. **Locale Resilience & Input Sanitization in Sport Type Editor (`EditSportTypeDialog.kt`)**:
   - Replaced ad-hoc string replacement in `formatSpeed` with standard localized numeric formatting: `String.format(Locale.getDefault(), "%.2f", MyHelper.mps2userUnit(speed))`.
   - Hardened `minSpeed` and `maxSpeed` text fields in `onValueChange` and `onSave` by sanitizing commas to dots (`input.replace(',', '.')`), ensuring seamless support for European keyboard commas without rejection or parse exceptions.

5. **Contract & Regression Test Suite (`SpeedConversionContractTest.kt`)**:
   - Added comprehensive contract tests validating bidirectional conversion symmetry within $10^{-6}$ epsilon.
   - Verified delegation of `SpeedFormatter` to `MyHelper`.
   - Verified `TelemetryMetricUtils` speed extraction and formatting.
   - Verified locale-resilient formatting (testing both US and European German locales) and comma/dot decimal input parsing in `EditSportTypeDialog`.
   - Added a codebase-wide static file scanner asserting zero occurrences of hardcoded speed conversion constants outside `MyHelper.java`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-365` (*Centralized Speed Unit Conversion and Formatter Harmonization via MyHelper (Rule 36)*) $\to$ **Status: Verified**
* **Test Mapping**: `TST-UI-328` (*Centralized Speed Unit Conversion and Formatter Harmonization via MyHelper Verification*) $\to$ **Status: Verified**

---

## 3. Preserved Invariants & Invariant Verification

1. **Rule 36 (Single Source of Truth & DRY)**: Magic conversion constants (`3.6`, `2.23693629`) reside exclusively within `MyHelper.java`; all consumers route conversions through centralized helper methods.
2. **Mathematical Precision & Round-Trip Identity**: Bidirectional conversion `UserUnit2mps(mps2userUnit(v, unit), unit) == v` holds within $10^{-6}$ epsilon.
3. **Regional Locale Parity**: UI respects native device locale formatting (`Locale.getDefault()`), rendering commas in European locales and dots in US/UK locales, while safely sanitizing both decimal delimiters upon user entry.
4. **Headless JVM Test Execution**: Parameterized `MyUnits` overloads allow pure JVM unit tests without mocking Android `Context` or `SharedPreferences`.
5. **Clean-Room Test Regression Free**: 100% full unit test pass rate maintained with zero regressions across 2,673 tests.

---

## 4. Test Verification Results

### Unit & Contract Test Suites

| Test Suite | Description | Result |
| :--- | :--- | :--- |
| `SpeedConversionContractTest` | Verifies bidirectional symmetry, `SpeedFormatter` delegation, `TelemetryMetricUtils` extraction/formatting, `EditSportTypeDialog` US/German locale formatting & comma parsing, and static check for zero magic constants outside `MyHelper` | **PASS** (6/6 tests) |
| `TelemetryMetricUtilsTest` / Map tests | Verifies telemetry point extraction, formatting, and map interactions | **PASS** |

### Clean-Room Full Suite Regression Run
* Command: `./gradlew testDebugUnitTest`
* Result: **100% Tests Passed** (2,673 tests completed, 0 failures, 0 errors, 0 regressions).
