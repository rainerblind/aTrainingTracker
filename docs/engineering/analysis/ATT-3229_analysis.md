# Stage 1 Analysis: ATT-3229 - Format filtered cycling power as rounded integer and ensure proper rounding for cadence and heart rate

**Ticket**: [ATT-3229](https://atrainingtracker.atlassian.net/browse/ATT-3229)  
**Sub-task**: [ATT-3300](https://atrainingtracker.atlassian.net/browse/ATT-3300) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.10`  
**Branch**: `feature/ATT-3229`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-11  

---

## 1. Problem Statement & Motivation

In the athletic tracking cockpit, live workout views, and telemetry summaries, raw instantaneous cycling power received from ANT+ and BLE sensors is integer-based (e.g., 250 W). However, raw 1-second power is notoriously noisy due to pedal stroke dead-spots; consequently, athletic defaults (`REQ-UI-167`, `REQ-UI-198`) configure power with smoothing filters (e.g., 3-second moving average, 10s power, exponential smoothing, or session average).

When filters process sensor inputs, the resulting filtered values are calculated as floating-point `Double` instances (e.g., 250.37 W). Currently:
1. `SensorType.POWER` is configured with `new DefaultNumberFormatter()` in `SensorType.java:80`. `DefaultNumberFormatter` configures `DecimalFormat.setMaximumFractionDigits(1)`, formatting filtered power with a decimal fraction (e.g., `250.4` or `250,4`).
2. In cycling telemetry standards across major athletic platforms (Garmin Edge, Wahoo ELEMNT, Zwift, Strava, GoldenCheetah), cycling power is universally formatted as a whole integer in Watts. Displaying decimals on power tiles wastes horizontal tile space, causes visual clutter, and induces rapid digit vibration.
3. `IntegerFormatter.java:28` (used for `HR`, `SLOPE`, `CALORIES`, `PHONE_BATTERY`, etc.) and `CadenceFormatter.java:28` (used for `CADENCE`) currently perform truncation via `value.intValue()`. As a result, fractional values truncate downward (e.g., 89.9 rpm becomes 89 instead of 90, and 154.7 bpm becomes 154 instead of 155), rather than mathematically rounding to the nearest integer.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 SensorType Formatter Binding
In `com.atrainingtracker.banalservice.sensor.SensorType.java`:
```java
POWER(R.string.power, R.string.power_short, R.string.units_power, SensorValueType.DOUBLE, new DefaultNumberFormatter(), true),
CADENCE(R.string.cadence, R.string.cadence_short, R.string.units_rpm, SensorValueType.DOUBLE, new CadenceFormatter(), true),
```
`POWER` was assigned `DefaultNumberFormatter` when initial sensor types were created. `DefaultNumberFormatter` calls:
```java
DecimalFormat format = new DecimalFormat();
format.setMaximumFractionDigits(1);
return format.format(value);
```
Every 1Hz telemetry update formatted through `MyFilter.getFilteredData()` calls `mSensorType.getMyFormatter().format(filteredValue)`. Because `mSensorType` is `POWER`, it invokes `DefaultNumberFormatter`, forcing 1 fraction digit.

### 2.2 Truncation in IntegerFormatter and CadenceFormatter
In `IntegerFormatter.java`:
```java
@Override
public String format(Number value) {
    if (value == null) {
        return "--";
    } else {
        return value.intValue() + "";
    }
}
```
In `CadenceFormatter.java`:
```java
@Override
public String format(Number value) {
    if (value == null) {
        return "--";
    } else {
        return Integer.toString(value.intValue());
    }
}
```
Both classes cast floating-point `Number` types (`Double`, `Float`) via `intValue()`, which discards any fractional part by truncation toward zero. If an athlete's 3s average cadence is `89.9`, displaying `89` introduces a persistent 1 rpm downward error. Similarly, for heart rate, `154.8` bpm truncates to `154`.

### 2.3 Redundancy Between CadenceFormatter and IntegerFormatter
`CadenceFormatter` is identical to `IntegerFormatter`, differing only in the string conversion method (`Integer.toString(...)` vs string concatenation). By enhancing `IntegerFormatter` with mathematical rounding (`Math.round(value.doubleValue())`) and delegating `CadenceFormatter` to `IntegerFormatter`, code duplication is eradicated while guaranteeing consistent rounding behavior across all integer telemetry metrics.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Upgrade `IntegerFormatter.java` to perform mathematical rounding:
     - Check `value == null` or `NaN`/`Infinite` -> return `"--"`.
     - Compute `Long.toString(Math.round(value.doubleValue()))`.
  2. Refactor `CadenceFormatter.java` to delegate to `IntegerFormatter` or reuse `IntegerFormatter` directly, ensuring mathematical rounding for cadence.
  3. In `SensorType.java`, assign `new IntegerFormatter()` to `SensorType.POWER` and `SensorType.CADENCE`.
  4. Author comprehensive unit tests in `IntegerFormatterTest.kt` verifying:
     - Whole integer values (`250.0` -> `"250"`).
     - Proper half-up rounding (`245.2` -> `"245"`, `245.5` -> `"246"`, `245.8` -> `"246"`).
     - Cadence fraction rounding (`89.8` -> `"90"`).
     - Heart rate fraction rounding (`154.6` -> `"155"`).
     - Boundary values (`0`, `0.0`, negative numbers if applicable).
     - Edge cases: `null`, `Double.NaN`, `Double.POSITIVE_INFINITY`, `Double.NEGATIVE_INFINITY` -> `"--"`.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. Modifying floating-point formatters for speed (`SpeedFormatter`), altitude (`AltitudeFormatter`), distance (`DistanceFormatter`), or pace (`PaceFormatter`).
  2. Modifying underlying filter mathematical pipelines (`BANALService`, `ExponentialSmoothingFilter`, `MovingAverageTimeFilter`).
  3. Altering database schema or workout sample persistence (samples remain stored as precise raw/filtered doubles in SQLite).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - `REQ-UI-167` (*Intuitive Smoothing Presets, Simplified Filter Configuration & Smart Athletic Defaults*), which established 3-second moving average as the default filter for `SensorType.POWER`.
  - `REQ-CON-015` (*Smartphone Battery Level and Estimated Remaining Duration Telemetry Sensors*), which references `IntegerFormatter` for `PHONE_BATTERY`.
* **Historical Origin & Commit Trace**:
  - `IntegerFormatter.java` and `DefaultNumberFormatter.java` date back to initial codebase creation (`commit 5df80cf4` / baseline ANT+ integration).
  - `CadenceFormatter.java` was created separately in early revisions with an unused commented-out `String.format("%.0f", value)`.
* **Root Reason for Existing Formulation**:
  - Initially, raw sensors provided discrete integer readings directly from ANT+ packets (e.g., ANT+ power page transmits 16-bit instantaneous power in Watts). Truncation via `value.intValue()` was sufficient because raw readings were already integer values.
  - When smoothing filters were introduced in `ATT-1276` and `ATT-1624`, power output became a floating-point `Double`. Because `SensorType.POWER` was left on `DefaultNumberFormatter`, it exposed the decimal fraction.
* **Preservation of Core Invariants**:
  - In `IntegerFormatter`, rounding integer inputs (e.g. `250L` or `250`) via `Math.round(250.0)` returns `250L`, producing `"250"`, exactly preserving all existing integer behaviors.
  - `null` inputs continue to return `"--"`.
  - Upgrading to `Math.round()` strictly improves precision for floating-point inputs without altering any existing contracts.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Enhanced `IntegerFormatter`
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

### 5.2 Streamlined `CadenceFormatter`
```java
public class CadenceFormatter implements MyFormatter<Number> {
    private final IntegerFormatter mIntegerFormatter = new IntegerFormatter();

    @Override
    public String format(Number value) {
        return mIntegerFormatter.format(value);
    }
}
```

### 5.3 `SensorType.java` Update
```java
CADENCE(R.string.cadence, R.string.cadence_short, R.string.units_rpm, SensorValueType.DOUBLE, new IntegerFormatter(), true),
...
POWER(R.string.power, R.string.power_short, R.string.units_power, SensorValueType.DOUBLE, new IntegerFormatter(), true),
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `null` telemetry continues to format as `"--"` across all tiles, sheets, and notification widgets.
  2. Database storage and internal calculations retain full double-precision floating point fidelity; formatting is strictly presentation-tier.
  3. No changes to database schemas or external export formats (TCX/FIT/GPX).
  4. 100% clean-room unit test suite pass rate.
* **Risk Rating**: **LOW**
  - Rationale: Self-contained presentation formatting fix in pure Java/Kotlin formatters with no architectural dependencies or threading side-effects.
