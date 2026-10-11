# Stage 2: Requirement & Test Specification - ATT-3229: Format filtered cycling power as rounded integer and ensure proper rounding for cadence and heart rate

**Ticket**: [ATT-3229](https://atrainingtracker.atlassian.net/browse/ATT-3229)  
**Sub-task**: [ATT-3301](https://atrainingtracker.atlassian.net/browse/ATT-3301) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-354` (*Whole Integer Formatting for Cycling Power and Mathematical Rounding for Integer Telemetry Metrics*)  
**Test Spec ID**: `TST-UI-314`  
**Branch**: `feature/ATT-3229`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Requirement Specification (REQ-UI-354)

### 1.1 Problem Statement & Rationale
In cycling telemetry standards across major athletic platforms (Garmin Edge, Wahoo ELEMNT, Zwift, Strava, GoldenCheetah), cycling power is universally formatted as a whole integer in Watts. Displaying decimals on power tiles wastes horizontal tile space, causes visual clutter, and induces rapid digit vibration. Because `SensorType.POWER` was configured with `new DefaultNumberFormatter()` (which sets `DecimalFormat.setMaximumFractionDigits(1)`), filtered power metrics (e.g. 3-second moving average, 10s power, exponential smoothing, or session average) display with one decimal place (e.g., `250.4` or `250,4`).

Furthermore, `IntegerFormatter.java` (used for `HR`, `SLOPE`, `CALORIES`, `PHONE_BATTERY`) and `CadenceFormatter.java` (used for `CADENCE`) currently perform truncation via `value.intValue()`. As a result, fractional values truncate downward (e.g., `89.9` rpm becomes `89` instead of `90`, and `154.7` bpm becomes `154` instead of `155`), introducing systematic downward error.

### 1.2 Functional & Architectural Requirements
The system SHALL format cycling power as a whole rounded integer and enforce mathematical half-up rounding across all integer-based telemetry metrics (`REQ-UI-354`):
1. **Mathematical Rounding in `IntegerFormatter` (`REQ-UI-354.1`)**:
   - `IntegerFormatter.java` SHALL format incoming `Number` instances via mathematical half-up rounding: `Long.toString(Math.round(value.doubleValue()))`.
   - When `value == null`, or `Double.isNaN(value.doubleValue())`, or `Double.isInfinite(value.doubleValue())`, `IntegerFormatter` SHALL return `"--"`.
   - Downward truncation via `value.intValue()` SHALL be completely excised.
2. **Cadence Formatter Consolidation (`REQ-UI-354.2`)**:
   - `CadenceFormatter.java` SHALL delegate directly to `IntegerFormatter` or reuse its rounding logic, ensuring consistent rounding across all cadence views.
3. **SensorType Formatter Configuration (`REQ-UI-354.3`)**:
   - In `SensorType.java`, `SensorType.POWER` SHALL be configured with `new IntegerFormatter()`, replacing `DefaultNumberFormatter()`. Filtered and instantaneous power values formatted via `SensorType.POWER.getMyFormatter()` SHALL format as whole integers without decimal fractions (e.g., `250` instead of `250.4`).
   - In `SensorType.java`, `SensorType.CADENCE` SHALL be configured with `new IntegerFormatter()`.
4. **Preservation of System Invariants (`REQ-UI-354.4`)**:
   - Raw sensor readings and filtered calculations in memory and SQLite storage (`WorkoutSamples.db`) retain full double-precision floating point fidelity; formatting is strictly presentation-tier.
   - Non-integer metrics (`SPEED_mps`, `ALTITUDE`, `DISTANCE_m`, `PACE_spm`) remain unaffected.
   - 100% clean-room test pass rate across all suites.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Filtered Power Whole Integer Display)**:
  * *Given* an active workout tracking session or TCX replay with a cycling power meter,
  * *When* viewing a cockpit tile configured with filtered power (e.g. 3-second moving average, 5-second smoothing, or average power),
  * *Then* the displayed value is a rounded whole integer without decimal places (e.g., `250` instead of `250.4`).
* **Criterion 2 (Cadence Rounding)**:
  * *Given* filtered cadence telemetry of 89.8 rpm,
  * *When* displayed in a cadence tile or formatted via `SensorType.CADENCE.getMyFormatter()`,
  * *Then* the value rounds to `90` rpm instead of truncating to `89` rpm.
* **Criterion 3 (Heart Rate Rounding)**:
  * *Given* filtered heart rate telemetry of 154.6 bpm,
  * *When* displayed in an HR tile or formatted via `SensorType.HR.getMyFormatter()`,
  * *Then* the value rounds to `155` bpm instead of truncating to `154` bpm.
* **Criterion 4 (Null and Edge Case Fallbacks)**:
  * *Given* no sensor connected (`null` value) or invalid calculation (`NaN` / `Infinite`),
  * *When* formatted by `IntegerFormatter` or `CadenceFormatter`,
  * *Then* the formatter returns `"--"`.

### 1.4 System Invariants
1. Full double precision maintained in SQLite samples database.
2. `null` telemetry continues to format as `"--"` across all tiles, sheets, and notification widgets.
3. Thread safety on sensor dispatchers and Zero regressions across clean-room test suite.

---

## 2. Test Specification (TST-UI-314)

### Test Case 1: `IntegerFormatterTest` (`TST-UI-314.1`)
* **Scope**: Automated Unit Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/IntegerFormatterTest.kt`
* **Test Methods**:
  1. `format_null_returnsDashes`: Assert `formatter.format(null) == "--"`.
  2. `format_nanOrInfinite_returnsDashes`: Assert `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` return `"--"`.
  3. `format_integerNumbers_returnsExactString`: Assert `250`, `150L`, `0` return `"250"`, `"150"`, `"0"`.
  4. `format_positiveFractions_roundsHalfUp`:
     - `245.0` -> `"245"`
     - `245.2` -> `"245"`
     - `245.49` -> `"245"`
     - `245.5` -> `"246"`
     - `245.8` -> `"246"`
  5. `format_cadenceFractions_roundsCorrectly`:
     - `89.1` -> `"89"`
     - `89.5` -> `"90"`
     - `89.8` -> `"90"`
  6. `format_heartRateFractions_roundsCorrectly`:
     - `154.2` -> `"154"`
     - `154.6` -> `"155"`

### Test Case 2: `CadenceFormatterTest` (`TST-UI-314.2`)
* **Scope**: Automated Unit Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/CadenceFormatterTest.kt`
* **Test Methods**:
  1. `format_nullAndEdgeCases`: Assert `null`, `NaN` return `"--"`.
  2. `format_fractions_roundsHalfUp`: Assert `89.8` -> `"90"`, `89.2` -> `"89"`.

### Test Case 3: `SensorTypeFormatterTest` (`TST-UI-314.3`)
* **Scope**: Automated Unit Tests
* **Target File**: `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeFormatterTest.kt`
* **Test Methods**:
  1. `powerSensor_usesIntegerFormatter_andRoundsFilteredWattage`:
     - Assert `SensorType.POWER.myFormatter is IntegerFormatter`.
     - Assert `SensorType.POWER.myFormatter.format(250.37)` returns `"250"`.
     - Assert `SensorType.POWER.myFormatter.format(250.7)` returns `"251"`.
  2. `cadenceSensor_usesIntegerFormatter_andRounds`:
     - Assert `SensorType.CADENCE.myFormatter.format(89.8)` returns `"90"`.
  3. `heartRateSensor_usesIntegerFormatter_andRounds`:
     - Assert `SensorType.HR.myFormatter.format(154.6)` returns `"155"`.

### Test Case 4: Clean-Room Full Suite Regression Execution (`TST-UI-314.4`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-314.1` | Unit | `IntegerFormatter.format` | `REQ-UI-354.1` | Specified |
| `TST-UI-314.2` | Unit | `CadenceFormatter.format` | `REQ-UI-354.2` | Specified |
| `TST-UI-314.3` | Contract Unit | `SensorType.POWER.myFormatter`, `SensorType.CADENCE.myFormatter` | `REQ-UI-354.3` | Specified |
| `TST-UI-314.4` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-UI-354.4`, `REQ-PRO-001` | Specified |
