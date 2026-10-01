# Stage 3: Implementation Plan - ATT-1738: Suppress VAM (Vertical Speed) Noise During GPS Wander and Poor Indoor Accuracy

**Ticket**: [ATT-1738](https://atrainingtracker.atlassian.net/browse/ATT-1738)  
**Sub-task**: [ATT-1746](https://atrainingtracker.atlassian.net/browse/ATT-1746) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*Sensors & Telemetry Engine*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Requirement Mapping**: `REQ-FIL-012` (*Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Architecture*)  
**Test Mapping**: `TST-FIL-004`  
**Branch**: `feature/ATT-1738`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Description & Background

In Sprint Review `2026-40.5`, on-device testing in an indoor basement setting revealed that vertical speed (VAM) output jumped erratically between $+800\text{ m/h}$ and $+2400\text{ m/h}$ despite the athlete standing still. 
GPS multipath reflection indoors causes false horizontal speeds ($0.6\text{–}1.5\text{ m/s}$) that bypass the stationary gating check (`speed < 0.5 m/s`). Combined with degraded GPS accuracy ($> 20\text{ m}$), random altitude wandering is amplified into massive climbing rates.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-FIL-012` (*Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Architecture*)
* **Test Mapping**: `TST-FIL-004` (*Stabilized VAM (Vertical Speed) Metric, Location Accuracy Gating & Indoor Drift Suppression Verification*)

---

## 3. System Invariants & Preserved Behavior

1. **Integer m/h Cadence**: `mVerticalSpeedSensor` broadcasts integer values in m/h at 1-second intervals.
2. **Backward-Compatible Overload**: Existing callers/tests invoking `calculateVam(history, count, headIndex, speedMps, minSpeed)` remain 100% functional via delegation to `calculateVam(..., accuracyMeters = 0.0)`.
3. **Sensor Independence**: `ASCENT`, `DESCENT`, and `SLOPE` sensors remain completely unaltered.
4. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` via `freigabe` upon passing Gate audit.
5. **Parent Human Gate Invariance**: Final completion of parent ticket `ATT-1738` remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `VerticalSpeedAndSlopeDevice.java`
* Register 1-second instantaneous accuracy filter:
  ```java
  private static final FilterData cAccuracyFilter = new FilterData(null, SensorType.ACCURACY, FilterType.INSTANTANEOUS, 1);
  ```
* Define thresholds:
  ```java
  public static final double VAM_MAX_GPS_ACCURACY_METERS = 20.0;
  public static final double VAM_LOW_SPEED_DRIFT_THRESHOLD_MPS = 1.2;
  ```
* Register filter in `BANALService.createFilter(cAccuracyFilter)`.
* Implement accuracy-aware `calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed, double accuracyMeters)`:
  - If `accuracyMeters > VAM_MAX_GPS_ACCURACY_METERS` (and not `NaN`), return `0`.
  - For speeds $< 0.5\text{ m/s}$ (stationary) or $< 1.2\text{ m/s}$ (low-speed wander/drift): enforce $150\text{ m/h}$ noise deadband.
  - For speeds $\ge 1.2\text{ m/s}$: apply $35\text{ m/h}$ flat-road deadband.
  - Retain $[-3000, 3000]\text{ m/h}$ physical athletic clamping.
* In `calculateMetrics()`: query `cAccuracyFilter` and pass `accuracyMeters` to `calculateVam`.

### Component 2: `VerticalSpeedAndSlopeDeviceTest.kt`
* Add targeted tests covering:
  - Degraded accuracy gating ($> 20.0\text{ m}$ suppressed to 0).
  - Low-speed indoor drift damping ($0.5 \le \text{speed} < 1.2\text{ m/s}$ with $|\text{raw\_vam}| < 150\text{ m/h}$ clamped to 0).
  - Low-speed genuine vertical climb preservation ($|\text{raw\_vam}| \ge 150\text{ m/h}$ preserved).
  - Backward compatibility of the 5-parameter overload.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update `VerticalSpeedAndSlopeDevice.java`
* Add `cAccuracyFilter`, `VAM_MAX_GPS_ACCURACY_METERS`, `VAM_LOW_SPEED_DRIFT_THRESHOLD_MPS`.
* Implement the new `calculateVam(..., accuracyMeters)` method and overload.
* Query `cAccuracyFilter` in `calculateMetrics()`.

### Step 2: Add Unit Tests in `VerticalSpeedAndSlopeDeviceTest.kt`
* Add unit tests for degraded accuracy, low-speed wander deadband, and backward compatibility.

### Step 3: Execute Targeted Unit Tests
* Command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.devices.VerticalSpeedAndSlopeDeviceTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during Stage 4 construction, followed by full test suite regression (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch `feature/ATT-1738` can be reverted cleanly without impacting `sprint/2026-40.6` or `develop`.
