# Stage 3: Implementation Plan - ATT-3363: Centralize inline speed and unit conversions to use MyHelper

**Ticket**: [ATT-3363](https://rainerblind.atlassian.net/browse/ATT-3363)  
**Sub-task**: [ATT-3537](https://rainerblind.atlassian.net/browse/ATT-3537) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `Sprint 2026-42.3`  
**Active Sprint**: `Sprint 2026-42.3`  
**Requirement Mapping**: `REQ-UI-365`  
**Test Spec Mapping**: `TST-UI-328`  
**Branch**: `feature/ATT-3363`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Architectural Design & SWE.2 Specification

### 1.1 Architectural Motivation & Single Source of Truth (Rule 36)

`MyHelper.java` serves as the centralized utility for unit conversions across `aTrainingTracker`. Currently:
1. `MyHelper` provides `mps2userUnit(speed, myUnits)` which supports unit injection, but only provides `UserUnit2mps(speed)` which implicitly calls `TrainingApplication.getUnit()`.
2. `TelemetryMetricUtils.kt` manually implements speed conversion using hardcoded factors (`* 3.6` and `* 2.236936` in `extractMetricValue`, and `/ 3.6` and `/ 2.236936` in `formatValue`).
3. `EditSportTypeDialog.kt` contains hacky formatting (`"%.2f".format(...).replace(",", ".")`) and brittle input handling that fails on comma decimal inputs.
4. `SpeedFormatter.java` duplicates the unit conversion switch-statement (`* 3.6` and `* 2.23693629`) instead of calling `MyHelper`.

### 1.2 Target Architectural Structure

```
                  ┌─────────────────────────────────────┐
                  │            MyHelper.java            │
                  │  mps2userUnit(speed, unit)          │
                  │  UserUnit2mps(speed, unit)  [NEW]   │
                  │  mps2userUnit(speed)                │
                  │  UserUnit2mps(speed)                │
                  └──────────────┬──────────────────────┘
                                 │
         ┌───────────────────────┼────────────────────────┐
         │                       │                        │
         ▼                       ▼                        ▼
┌──────────────────┐    ┌──────────────────┐    ┌─────────────────────┐
│ SpeedFormatter   │    │TelemetryMetric   │    │ EditSportTypeDialog │
│ .java            │    │Utils.kt          │    │ .kt                 │
│ format(): calls  │    │ extractMetric()  │    │ formatSpeed():      │
│ mps2userUnit()   │    │ formatValue()    │    │ Locale.US "%.2f"    │
│                  │    │ DRY via MyHelper │    │ comma/dot parse     │
└──────────────────┘    └──────────────────┘    └─────────────────────┘
```

---

## 2. Invariants & Safety Measures

1. **Precision & Conversion Exactness**:
   - `case METRIC`: multiplication by `3.6` (from m/s to km/h) and division by `3.6` (from km/h to m/s).
   - `case IMPERIAL`: multiplication by `2.23693629` (from m/s to mph) and division by `2.23693629` (from mph to m/s).
   - Round-trip mathematical identity: `UserUnit2mps(mps2userUnit(v, unit), unit) == v` within $10^{-6}$ epsilon.
2. **Headless JVM Null-Safety**:
   - Parameterized overloads `mps2userUnit(speed, myUnits)` and `UserUnit2mps(speed, myUnits)` accept `@NonNull MyUnits` directly, completely avoiding any Android runtime or `ApplicationContext` dependencies during telemetry processing.
3. **Locale-Agnostic User Input in Sport Type Dialog**:
   - Support decimal comma (common in European numeric keyboards) and decimal dot simultaneously via `.replace(',', '.').toDoubleOrNull()`.
4. **Zero Regressions**:
   - Scrubbing telemetry charts, workout recording, speed thresholds in sport types, and the entire test suite must remain 100% green.

---

## 3. Bite-Sized Atomic Implementation Steps

### Step 1: Add Symmetric `UserUnit2mps(double, MyUnits)` to `MyHelper.java`
- In `app/src/main/java/com/atrainingtracker/trainingtracker/MyHelper.java`:
  - Add:
    ```java
    public static double UserUnit2mps(double speed, @NonNull MyUnits myUnits) {
        return switch (myUnits) {
            case METRIC -> speed / 3.6;
            case IMPERIAL -> speed / 2.23693629;
        };
    }
    ```
  - Refactor existing `UserUnit2mps(double speed)` to delegate:
    ```java
    public static double UserUnit2mps(double speed) {
        return UserUnit2mps(speed, TrainingApplication.getUnit());
    }
    ```

### Step 2: Refactor `SpeedFormatter.java` to Delegate to `MyHelper`
- In `app/src/main/java/com/atrainingtracker/banalservice/sensor/formater/SpeedFormatter.java`:
  - Replace switch statement in `format(Number speed_mps)` with:
    ```java
    double speed = MyHelper.mps2userUnit(speed_mps.doubleValue());
    ```

### Step 3: Refactor `TelemetryMetricUtils.kt` to Use `MyHelper`
- In `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtils.kt`:
  - In `extractMetricValue` for `TelemetryMetricType.SPEED`:
    ```kotlin
    point.speedMps?.takeIf { it > 0.0 }?.let { mps ->
        MyHelper.mps2userUnit(mps, unit)
    }
    ```
  - In `formatValue` for `TelemetryMetricType.SPEED`:
    ```kotlin
    val mps = MyHelper.UserUnit2mps(value, unit)
    speedFormatter.format_with_units(mps)
    ```
  - Remove all inline magic numbers (`3.6`, `2.236936`).

### Step 4: Refactor `EditSportTypeDialog.kt` Formatting & Input Parsing
- In `app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeDialog.kt`:
  - Replace `formatSpeed`:
    ```kotlin
    fun formatSpeed(speed: Double): String = String.format(Locale.US, "%.2f", MyHelper.mps2userUnit(speed))
    ```
  - In speed input text fields:
    ```kotlin
    onValueChange = { input ->
        val sanitized = input.replace(',', '.')
        if (input.isEmpty() || sanitized.toDoubleOrNull() != null) minSpeed = input
    }
    ```
  - In `onSave`:
    ```kotlin
    val finalMin = MyHelper.UserUnit2mps(minSpeed.replace(',', '.').toDoubleOrNull() ?: 0.0)
    val finalMax = MyHelper.UserUnit2mps(maxSpeed.replace(',', '.').toDoubleOrNull() ?: 0.0)
    ```

### Step 5: Create Unit & Verification Tests
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/MyHelperSpeedConversionTest.kt`:
  - Test metric and imperial forward and reverse conversions.
  - Test mathematical identity round-tripping.
  - Test zero and edge-case values.
  - Test delegation from zero-arg overload to injected-arg overload.
- Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TelemetryMetricUtilsSpeedTest.kt`:
  - Test speed extraction and formatting under metric and imperial settings.
  - Assert exact adherence to `MyHelper`.
- Create `app/src/test/java/com/atrainingtracker/banalservice/ui/sporttype/EditSportTypeSpeedParsingTest.kt`:
  - Test formatting with Locale.US and comma/dot resilience.

### Step 6: Full Clean-Room Regression Suite Execution
- Run `./gradlew testDebugUnitTest` and ensure 100% passing tests.

---

## 4. Gate 3 Readiness Verification
- All functional changes mapped to `REQ-UI-365` and `TST-UI-328`.
- Atomic steps are self-contained and independently verifiable.
- Invariants and non-functional requirements strictly maintained.
