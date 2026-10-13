# Stage 3: Implementation Plan - ATT-3229: Format filtered cycling power as rounded integer and ensure proper rounding for cadence and heart rate

**Ticket**: [ATT-3229](https://atrainingtracker.atlassian.net/browse/ATT-3229)  
**Sub-task**: [ATT-3302](https://atrainingtracker.atlassian.net/browse/ATT-3302) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Requirement Mapping**: `REQ-UI-354` (*Whole Integer Formatting for Cycling Power and Mathematical Rounding for Integer Telemetry Metrics*)  
**Test Mapping**: `TST-UI-314` (*Whole Integer Cycling Power Formatting, IntegerFormatter Mathematical Rounding, and Cadence Consolidation Verification*)  
**Branch**: `feature/ATT-3229`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Description & Background

In the athletic tracking cockpit, live workout views, and telemetry summaries, cycling power is universally formatted as a whole integer in Watts across sports telemetry standards. Displaying decimals on power tiles introduces visual clutter, wastes horizontal digit space, and causes jarring number fluctuations. However, because `SensorType.POWER` was configured with `new DefaultNumberFormatter()` (which sets `DecimalFormat.setMaximumFractionDigits(1)`), filtered power metrics (e.g., 3-second moving average, exponential smoothing, or session average) display with one decimal place (e.g., `250.4` or `250,4`).

Furthermore, `IntegerFormatter.java` (used for `HR`, `SLOPE`, `CALORIES`, `PHONE_BATTERY`) and `CadenceFormatter.java` (used for `CADENCE`) currently perform truncation via `value.intValue()`. As a result, fractional values truncate downward (e.g., `89.9` rpm becomes `89` instead of `90`, and `154.7` bpm becomes `154` instead of `155`), introducing systematic downward error.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-354` (*Whole Integer Formatting for Cycling Power and Mathematical Rounding for Integer Telemetry Metrics*)
* **Test Mapping**: `TST-UI-314` (*Whole Integer Cycling Power Formatting, IntegerFormatter Mathematical Rounding, and Cadence Consolidation Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Precision Invariance**: Raw sensor readings and filtered calculations in memory and SQLite storage (`WorkoutSamples.db`) retain full double-precision floating point fidelity; formatting is strictly presentation-tier.
2. **Defensive Edge Case Handling**: `null`, `Double.NaN`, `Double.POSITIVE_INFINITY`, and `Double.NEGATIVE_INFINITY` format consistently as `"--"`.
3. **Non-Integer Metric Isolation**: Formatting for speed (`SpeedFormatter`), altitude (`AltitudeFormatter`), distance (`DistanceFormatter`), and pace (`PaceFormatter`) remains completely unaltered.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
5. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `IntegerFormatter.java` (Presentation Tier)
- Replace downward truncation `value.intValue() + ""` with mathematical half-up rounding:
  ```java
  public class IntegerFormatter implements MyFormatter<Number> {
      @Override
      public String format(Number value) {
          if (value == null) {
              return "--";
          }
          double d = value.doubleValue();
          if (Double.isNaN(d) || Double.isInfinite(d)) {
              return "--";
          }
          return Long.toString(Math.round(d));
      }
  }
  ```

### Component 2: `CadenceFormatter.java` (Presentation Tier)
- Delegate to `IntegerFormatter` to eliminate duplication while ensuring mathematical half-up rounding for all cadence consumers:
  ```java
  public class CadenceFormatter implements MyFormatter<Number> {
      private final IntegerFormatter mIntegerFormatter = new IntegerFormatter();

      @Override
      public String format(Number value) {
          return mIntegerFormatter.format(value);
      }
  }
  ```

### Component 3: `SensorType.java` (Configuration Tier)
- Update `POWER` and `CADENCE` bindings:
  ```java
  CADENCE(R.string.cadence, R.string.cadence_short, R.string.units_rpm, SensorValueType.DOUBLE, new IntegerFormatter(), true),
  ...
  POWER(R.string.power, R.string.power_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true),
  ```

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
* **Reference screen / component**: Tracking cockpit metric tiles (`MetricItem.kt`, `SensorGridTile.kt`), `WorkoutDetails.kt`.
* **Reused components**: Standard `MyFormatter` interface and existing formatters.
* **Theme tokens**: Formatting respects existing typography and layout bounds; whole integers prevent text truncation/overflow on compact tiles.
* **New one-off styles & justification**: No UI changes. Presentation formatting string enhancement only.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Upgrade `IntegerFormatter.java`
* Target File: `app/src/main/java/com/atrainingtracker/banalservice/sensor/formater/IntegerFormatter.java`
* Changes:
  - Add defensive check for `NaN` and `Infinite` returning `"--"`.
  - Replace `value.intValue() + ""` with `Long.toString(Math.round(value.doubleValue()))`.

### Step 2: Streamline `CadenceFormatter.java`
* Target File: `app/src/main/java/com/atrainingtracker/banalservice/sensor/formater/CadenceFormatter.java`
* Changes:
  - Delegate `format(Number value)` to `IntegerFormatter`.

### Step 3: Update `SensorType.java`
* Target File: `app/src/main/java/com/atrainingtracker/banalservice/sensor/SensorType.java`
* Changes:
  - Rebind `SensorType.POWER` from `new DefaultNumberFormatter()` to `new IntegerFormatter()`.
  - Rebind `SensorType.CADENCE` from `new CadenceFormatter()` to `new IntegerFormatter()`.

### Step 4: Unit Test Suite Construction
* Target Files:
  - `app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/IntegerFormatterTest.kt`
  - `app/src/test/java/com/atrainingtracker/banalservice/sensor/formater/CadenceFormatterTest.kt`
  - `app/src/test/java/com/atrainingtracker/banalservice/sensor/SensorTypeFormatterTest.kt`
* Targeted Test Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  1. Targeted test execution: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*"` (must pass 100%).
  2. Full clean-room regression: `./gradlew testDebugUnitTest` in Stage 5.
* **Rollback**: Clean Git branch isolation (`feature/ATT-3229`). Reverting the commit restores legacy formatting without database migrations or schema impacts.
