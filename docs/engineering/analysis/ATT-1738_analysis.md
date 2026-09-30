# Stage 1 Analysis: ATT-1738 - Suppress VAM Noise During GPS Wander and Poor Indoor Accuracy

**Ticket**: [ATT-1738](https://atrainingtracker.atlassian.net/browse/ATT-1738)  
**Sub-task**: [ATT-1744](https://atrainingtracker.atlassian.net/browse/ATT-1744) (`[Analysis]`)  
**Parent Epic**: [ATT-235](https://atrainingtracker.atlassian.net/browse/ATT-235) (*Sensors & Telemetry Engine*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1738`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Problem Statement & Motivation

During on-device testing and the Sprint Review of `2026-40.5` (ATT-1621), the user verified the new 15-second least-squares linear regression VAM metric indoors and in a basement. While the algorithm performed well under outdoor open-sky conditions, testing in a basement with noisy GPS reception revealed that VAM still exhibited severe, erratic noise swings:
- Athletes standing or walking slowly in indoor/basement environments observe VAM jumping between $+800\text{ m/h}$ and $+2400\text{ m/h}$ (or deep negative rates), despite zero actual altitude change.
- The user observed that the stationary suppression logic implemented in ATT-1621 did not engage effectively under indoor GPS wander conditions.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic trace of `VerticalSpeedAndSlopeDevice.java` (`calculateMetrics()` and `calculateVam()`) under indoor/basement GNSS conditions revealed two distinct breakdown mechanisms:

### 2.1 The "Phantom Speed" Multipath Bypass
In `VerticalSpeedAndSlopeDevice.java` (lines 157–177):
```java
public static int calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed) {
    double slopeMps = calculateLinearRegressionSlope(history, count, headIndex);
    double rawVam = slopeMps * 3600.0;

    double verticalSpeed;
    if (Math.abs(speedMps) < minSpeed) {
        // Stationary athlete: suppress ambient jitter (< 150 m/h)
        if (Math.abs(rawVam) < 150.0) {
            verticalSpeed = 0.0;
        } else {
            verticalSpeed = rawVam;
        }
    } else {
        // Moving athlete: apply flat terrain noise deadband (< 35 m/h)
        if (Math.abs(rawVam) < 35.0) {
            verticalSpeed = 0.0;
        } else {
            verticalSpeed = rawVam;
        }
    }
...
```
1. In open outdoor conditions, when stopped, GPS speed reliably drops below `minSpeed = 0.5 m/s` (or `0.0 m/s`), properly engaging the stationary deadband of $150\text{ m/h}$.
2. In basements or indoors, GPS multipath reflections and satellite geometry degradation (high PDOP) induce continuous horizontal position wandering. The GPS engine computes instantaneous speed from Doppler shift or position diffs that routinely oscillates between $0.6\text{ m/s}$ and $1.5\text{ m/s}$ ($2.2\text{–}5.4\text{ km/h}$).
3. Because $0.6\text{–}1.5\text{ m/s} \ge 0.5\text{ m/s}$, the device classifies the athlete as **moving horizontally**.
4. Consequently, it drops the stationary deadband ($150\text{ m/h}$) and applies only the flat terrain deadband ($35\text{ m/h}$).

### 2.2 Amplified GPS Altitude Noise Under Poor Fix Accuracy
1. Indoors, GPS horizontal accuracy degrades from typical outdoor values ($3\text{–}8\text{ m}$) to $20\text{–}50\text{ m}$ (or worse). Vertical GPS accuracy is physically 2 to 3 times worse than horizontal accuracy, resulting in altitude uncertainty of $\pm 40\text{–}100\text{ m}$.
2. Over a 15-second window, multipath position wandering produces random altitude drift ramps of $5\text{–}10\text{ m}$.
3. Linear regression computes:
   $$\text{slope} \approx \frac{7\text{ m}}{15\text{ s}} \approx 0.467\text{ m/s} \implies \text{rawVam} \approx 0.467 \times 3600 = 1680\text{ m/h}$$
4. Since $1680\text{ m/h} \gg 35\text{ m/h}$, `calculateVam()` outputs $1680\text{ m/h}$ as a genuine climbing rate, directly corrupting real-time cockpit tiles.

### 2.3 Absence of Accuracy Gating in Telemetry Pipeline
While `SpeedAndLocationDevice.java` monitors `location.getAccuracy()` against `accuracyThreshold` (default $50\text{ m}$) before accepting coordinates, `VerticalSpeedAndSlopeDevice.java` has **zero** visibility into GPS accuracy. It consumes `SensorType.ALTITUDE` and `SensorType.SPEED_mps` without inspecting whether the current location fix is degraded by multipath or poor sky visibility.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. **Location Accuracy Gating**: Suppress vertical speed calculation (output `0 m/h`) whenever GPS horizontal accuracy is worse than a defined threshold ($20.0\text{ m}$).
  2. **GPS Drift Damping for Low Speeds**: For low speeds ($0.5\text{–}1.2\text{ m/s}$), enforce an elevated drift deadband ($150\text{ m/h}$) or gradient plausibility check to eliminate phantom climbing rates during indoor wander.
  3. **Preserve Overload Signature**: Maintain backward compatibility for existing callers/unit tests of `calculateVam()` while introducing the accuracy-aware overload.
  4. **Targeted Unit Tests**: Author comprehensive unit tests in `VerticalSpeedAndSlopeDeviceTest.kt` verifying indoor wander suppression and accuracy gating.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No background accelerometer hardware listeners (avoids battery drain and excessive CPU wakeups).
  2. No changes to `AltitudeFromPressureDevice` barometric pressure calibration formulas.
  3. No changes to `SensorType.ASCENT` and `SensorType.DESCENT` accumulator integration (which rely on `cAltitudeSuperFilter`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-FIL-012` (*Stabilized VAM (Vertical Speed) Metric & Stationary Suppression Architecture*), implemented in `VerticalSpeedAndSlopeDevice.java`.
* **Historical Origin & Commit Trace**: Added in Sprint `2026-40.5` under ticket `ATT-1621`.
* **Root Reason for Existing Formulation**: `REQ-FIL-012` addressed outdoor stationary stopping and linear regression smoothing. It assumed `speed_mps < 0.5 m/s` was sufficient to detect stillness, but did not account for indoor GNSS multipath wander where false horizontal speed $> 0.5\text{ m/s}$ co-occurs with degraded GPS accuracy ($> 20\text{ m}$).
* **Preservation of Core Invariants**: 15-second sliding window linear regression, 1-second update cadence, integer m/h units, accumulator integration, and slope calculation invariants are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Telemetry Accuracy Filter Registration
In `VerticalSpeedAndSlopeDevice.java`, register a 1-second instantaneous filter for `SensorType.ACCURACY`:
```java
private static final FilterData cAccuracyFilter = new FilterData(null, SensorType.ACCURACY, FilterType.INSTANTANEOUS, 1);
```
Subscribe to `cAccuracyFilter` in `calculateMetrics()` via `BANALService.getFilteredSensorData(cAccuracyFilter)`.

### 5.2 Accuracy Gating Threshold (`VAM_MAX_GPS_ACCURACY_METERS = 20.0`)
Define constant:
```java
public static final double VAM_MAX_GPS_ACCURACY_METERS = 20.0;
```
If GPS accuracy is available and degraded (`accuracy > 20.0 m`), suppress `verticalSpeed = 0`. Multipath altitude noise during poor satellite reception will not produce phantom VAM.

### 5.3 Low-Speed Drift Damping
For speeds below $1.2\text{ m/s}$ (typical walking / GPS drift threshold):
- Require $|rawVam| \ge 150.0\text{ m/h}$ to emit a vertical rate, treating anything below $150\text{ m/h}$ as indoor wander.
- For moving speeds $\ge 1.2\text{ m/s}$ with accuracy $\le 20.0\text{ m}$, retain the sensitive $35.0\text{ m/h}$ flat-road deadband.

### 5.4 Method Overload Contract
```java
public static int calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed) {
    return calculateVam(history, count, headIndex, speedMps, minSpeed, 0.0);
}

public static int calculateVam(double[] history, int count, int headIndex, double speedMps, double minSpeed, double accuracyMeters)
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Integer m/h broadcast on `SensorType.VERTICAL_SPEED` every 1 second.
  2. Slope calculation and ascent/descent accumulator sensors remain 100% unchanged.
  3. Physical athletic boundary clamping $[-3000, 3000]\text{ m/h}$ remains in effect.
* **Risk Rating**: **LOW** (Mathematical filter refinement cleanly localized within `VerticalSpeedAndSlopeDevice.java`).
