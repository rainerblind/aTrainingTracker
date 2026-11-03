# Stage 1 Analysis: ATT-4843 - [Bug] Analyse Georgenfelder Hochmoor

**Ticket**: [ATT-4843](https://rainerblind.atlassian.net/browse/ATT-4843)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `2026-44.1` | **Branch**: `feature/ATT-4843`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
In workout 434 (*Georgenfelder Hochmoor*, `2026-10-25_131818`), `distanceTotal_m` accumulated 13,377.31 m (avg speed 2.26 m/s) on a ~4.4–5.4 km hike with an indoor pause of ~2.7h, diverging drastically from the elevation profile X-axis and GPS track distance (~4.37 km).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Investigation on Device DBs (`/tmp/ws_debug.db`, `/tmp/workout_samples_debug.db`)**:
  * **Primary GPS vs. Fused Discrepancy**: In `WorkoutSamples.db`, primary `DISTANCE_m` (GPS) ends at **4,370.02 m** (geodesic track: 5,449.61 m; simplified polyline: 4,237.01 m). In `Laps.db`, Lap 1 distance is **4,370.02 m**. In `WorkoutSummaries.db`, `distanceStream` ends at **4,367.96 m**.
  * **Indoor Jump**: At sample 4208 (15:54:58) during the indoor break, `Google Fused` jumped from `(50.73199, 13.74682)` to `(50.70461, 13.78871)` (4,245.6 m in 1s) and returned over 4s, adding **8,492.9 m** (+8.5 km) to `DISTANCE_m (Google Fused)` (3,384.7 m $\rightarrow$ 11,877.6 m $\rightarrow$ 13,377.31 m). Accuracy was 46.7 m (under 50 m threshold).
  * **Missing Distance Plausibility Filter**: In `SpeedAndLocationDevice.java:296`, `mDistanceSensor.increment(delta_distance)` unconditionally accumulates `delta_distance`. While line 321 checks `calcSpeed <= 70.0 m/s` for instantaneous speed, distance increments have zero plausibility clamping.
  * **Multi-Sensor Summary Overwrite**: In `TrackerService.java:2129-2169`, `sampleAndWriteToDb()` iterates `getAllSensorData()`. The `switch (sensorType)` for `DISTANCE_m` does not check `sensorData.getDeviceName() == null`. Because `Google Fused` iterates after `ProxySensor`, it overwrote `mDistanceTotal_m` and `WorkoutSummaries.DISTANCE_TOTAL_m` on every tick.
  * **Elevation Profile Discrepancy**: The ElevationProfile chart reads `distanceStream` (or primary `DISTANCE_m`), ending at ~4.37 km, while `WorkoutSummaries` card displays 13.38 km. Post-break distance (`13377.31 - 2927.74 = 10449.57 m = ~10.5 km`) and imperial unsimplified fused track (16.7 km = 10.38 mi) explain the ~10.5 km observation.
* **Independent Peer Review & Verification**:
  * Confirmed in `TrackerService.java:2200` that `distanceStream` and `Laps.db` correctly query `getBestSensorData(DISTANCE_m)` (ProxySensor / GPS), while line 2163 blindly overwrote summary distance with the last device in `getAllSensorData()`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Add physical speed plausibility check (`calcSpeed <= 70.0 m/s`) in `SpeedAndLocationDevice.java` to reject impossible teleportation/multipath distance increments.
  * Guard summary updates in `TrackerService.java:2152-2169` so only canonical/primary sensors (`sensorData.getDeviceName() == null`) update `summaryValues` (`DISTANCE_TOTAL_m`, `TIME_TOTAL_s`, etc.).
  * Add targeted unit regression tests in `SpeedAndLocationDeviceTest` and `TrackerServiceTest`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Implementing full Kalman fusion / ZUPT / RTS smoothing (separate tickets under Epic ATT-166).
  * Altering Douglas-Peucker simplification, ElevationProfile rendering, or retrospective DB migration.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-008` (Align session averages with core summary metrics) & `REQ-TRK-014` (Successive Geodesic Location Delta Fallback for Missing GPS Bearing and Speed).
* **Historical Origin & Commit Trace**: `fc28443c` (`ATT-3961`, enabled Google Fused speed/dist); `ATT-2467` (`REQ-TRK-014`, introduced 70 m/s plausibility clamp for speed).
* **Root Reason for Existing Formulation**: `REQ-TRK-014` clamped synthetic speed to 70 m/s for mock GPS testing but omitted distance increments. `REQ-TRK-008` aligned summary averages with session metrics but `getAllSensorData()` lacked `deviceName == null` filtering.
* **Preservation of Core Invariants**: Multi-sensor recording into `WorkoutSamples.db` remains intact. Canonical proxy sensor controls summary metrics. Plausibility limit (70 m/s = 252 km/h) rejects teleportations without impacting athlete speeds.

---

## 5. Architectural Strategy & High-Level Solution
* `SpeedAndLocationDevice.java:onNewLocation`: gate `mDistanceSensor.increment` and `mLapDistanceSensor.increment` by `calcSpeed <= 70.0 m/s` (when `dt_ms > 0 && dt_ms < 30000`).
* `TrackerService.java:sampleAndWriteToDb`: restrict `summaryValues.put` inside `switch (sensorType)` to canonical sensor entries (`sensorData.getDeviceName() == null`).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Secondary device telemetry columns in `WorkoutSamples.db` must continue recording.
  2. Single-thread database serialization on `mDbExecutor` preserved.
  3. Zero regressions in existing location and tracking tests.
* **Risk Rating**: **LOW** (Targeted guardrails in accumulator increments and summary mapping; no schema changes).
