# Stage 1 Analysis: ATT-3961 - No speed and distance in cockpit and not tracked

**Ticket**: [ATT-3961](https://rainerblind.atlassian.net/browse/ATT-3961)  
**Sub-task**: [ATT-3978](https://rainerblind.atlassian.net/browse/ATT-3978) (`[Analysis]`)  
**Parent Epic**: [ATT-1340](https://rainerblind.atlassian.net/browse/ATT-1340) (*[Tracking/Cockpit] Real-Time Tracking & Cockpit UI*)  
**Target Release**: `TBD (Set on Completion per Rule 14)`  
**Active Sprint**: `Sprint 2026-42.8`  
**Branch**: `feature/ATT-3961`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Problem Statement & Motivation

During an outdoor commute on `2026-10-18` (`2026-10-18_090642`), the top sensor bar showed **Timer (`T`)**, **GPS Location (`G`)**, and **Altitude (`A`)** as active (`T------AG--`), and the live map rendered the full `2.90 km` blue GPS track (`LATITUDE` / `LONGITUDE`). However:
* The **Distance (`D`)** and **Speed (`S`)** icons in the top sensor bar remained inactive (greyed out).
* The cockpit displayed `-- km/h` for speed and `-- km` for distance.
* The live elevation profile remained stuck at `0 m` horizontal distance.
* The exported TCX/CSV had empty primary `DISTANCE_m`, `DISTANCE_m_LAP`, `SPEED_mps`, and `PACE_spm` columns, even though the device-specific `DISTANCE_m (Google Fused)` (`2,896.48 m`) and `SPEED_mps (Google Fused)` columns were recorded every second.

---

## 2. Root Cause Analysis (Forensic Investigation)

1. **`DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` Commented Out in `MySensorManager.SPEED_PRIORITY` ([`MySensorManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L42-L71))**:
   - In [`MySensorManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L59-L71), `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY` both include `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` right after `DeviceType.SPEED_AND_LOCATION_GPS`:
     ```java
     protected static final DeviceType[] ALTITUDE_PRIORITY = new DeviceType[]{
             DeviceType.ALTITUDE_FROM_PRESSURE,
             DeviceType.SPEED_AND_LOCATION_GPS,
             DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
             DeviceType.SPEED_AND_LOCATION_NETWORK
     };
     protected static final DeviceType[] LOCATION_PRIORITY = new DeviceType[]{
             DeviceType.SPEED_AND_LOCATION_GPS,
             DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
             DeviceType.SPEED_AND_LOCATION_NETWORK
     };
     ```
   - However, in `SPEED_PRIORITY` (lines 42–51, which governs `SensorType.SPEED_mps`, `SensorType.PACE_spm`, `SensorType.DISTANCE_m`, and `SensorType.DISTANCE_m_LAP`), `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` was commented out:
     ```java
     protected static final DeviceType[] SPEED_PRIORITY = new DeviceType[]{
             DeviceType.RUN_SPEED,
             DeviceType.BIKE_SPEED,
             DeviceType.BIKE_SPEED_AND_CADENCE,
             DeviceType.BIKE_POWER,
             // DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
             DeviceType.SPEED_AND_LOCATION_GPS,
             DeviceType.SPEED_AND_LOCATION_NETWORK
     };
     ```
   - When `SpeedAndLocationDevice_GoogleFused` registers its sensors via `MySensorManager.registerSensor(mySensor)`:
     1. `getBestSensor(sensorType)` initially returns `null`, so `addSensor(new ProxySensor(this, sensorType, mySensor))` adds the proxy sensor.
     2. Immediately afterward on line 185, `setBestSensorForProxySensor(sensorType)` calls `getBestSensor(sensorType)`.
     3. Because `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` is absent from `SPEED_PRIORITY`, `getBestSensor(sensorType)` returns `null` for `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP`.
     4. Line 271 (`else { removeSensor(sensorType); }`) immediately deletes the `ProxySensor` for `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP`!
     5. Consequently, whenever `SpeedAndLocationDevice_GoogleFused` is the active location provider (or delivers fixes while raw `GPS_PROVIDER` is unavailable or silent), the app tracks `LATITUDE`, `LONGITUDE`, `BEARING`, `ACCURACY`, and `ALTITUDE` from Google Fused, but has zero `ProxySensor` for `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, or `DISTANCE_m_LAP`.

2. **Secondary Trigger (Addressed in Companion Ticket `ATT-3971`)**:
   - Raw `LocationManager.GPS_PROVIDER` was silent during that commute because an earlier `tools/replay_workout.py` session had left `gps` registered as an ADB `[mock]` test provider (`ATT-3971` fixes `tools/replay_workout.py` to release test providers immediately on Pause/Stop/Completion).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Enable `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` in `MySensorManager.SPEED_PRIORITY` immediately after `DeviceType.SPEED_AND_LOCATION_GPS` (matching the exact priority order in `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY`).
  * Add comprehensive unit tests for `MySensorManager` verifying:
    - When `SPEED_AND_LOCATION_GOOGLE_FUSED` registers `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, `DISTANCE_m_LAP`, `LATITUDE`, `LONGITUDE`, and `ALTITUDE`, `MySensorManager` retains active `ProxySensor`s for all of them and sets `'D'`, `'S'`, `'A'`, `'G'` in `SENSORS` (`mSensorsSensor`).
    - When `SPEED_AND_LOCATION_GPS` or a dedicated wheel/footpod sensor (`BIKE_SPEED`, `RUN_SPEED`, `BIKE_POWER`, `BIKE_SPEED_AND_CADENCE`) is also registered, `getBestSensor` prioritizes the higher-priority device over `SPEED_AND_LOCATION_GOOGLE_FUSED`, and seamlessly falls back to `SPEED_AND_LOCATION_GOOGLE_FUSED` over `SPEED_AND_LOCATION_NETWORK`.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * `tools/replay_workout.py` ADB mock provider lifecycle changes (tracked in dedicated ticket `ATT-3971`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirement `REQ-TRK-022` in `docs/requirements.md` targeting `MySensorManager.java` (`SPEED_PRIORITY` alignment with `LOCATION_PRIORITY` and `ALTITUDE_PRIORITY`). No existing requirements in `docs/requirements.md` are modified or weakened.
* **Historical Origin & Commit Trace**: Legacy `MySensorManager.java` where `// DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` was commented out above `SPEED_AND_LOCATION_GPS` when testing raw GPS priority, rather than being placed below `SPEED_AND_LOCATION_GPS` as was done in `ALTITUDE_PRIORITY` and `LOCATION_PRIORITY`.
* **Root Reason for Existing Formulation**: The original author wanted raw `SPEED_AND_LOCATION_GPS` to take priority over `SPEED_AND_LOCATION_GOOGLE_FUSED` when both were active, and commented out `SPEED_AND_LOCATION_GOOGLE_FUSED` (which was originally listed *above* `SPEED_AND_LOCATION_GPS` on line 48) instead of moving it *below* `SPEED_AND_LOCATION_GPS` like lines 62–63 and 68–69.
* **Preservation of Core Invariants**: Placing `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` immediately *after* `DeviceType.SPEED_AND_LOCATION_GPS` in `SPEED_PRIORITY` preserves `RUN_SPEED > BIKE_SPEED > BIKE_SPEED_AND_CADENCE > BIKE_POWER > SPEED_AND_LOCATION_GPS` priority while ensuring `SPEED_AND_LOCATION_GOOGLE_FUSED` provides speed and distance whenever higher-priority sensors are absent.

---

## 5. Architectural Strategy & High-Level Solution

1. **Update `MySensorManager.SPEED_PRIORITY` ([`MySensorManager.java`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/sensor/MySensorManager.java#L42-L51))**:
   ```java
   protected static final DeviceType[] SPEED_PRIORITY
           = new DeviceType[]{
           DeviceType.RUN_SPEED,
           DeviceType.BIKE_SPEED,
           DeviceType.BIKE_SPEED_AND_CADENCE,
           DeviceType.BIKE_POWER,
           DeviceType.SPEED_AND_LOCATION_GPS,
           DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED,
           DeviceType.SPEED_AND_LOCATION_NETWORK
   };
   ```
2. **Unit Testing (`MySensorManagerPriorityTest.kt`)**:
   - Verify `SPEED_PRIORITY`, `ALTITUDE_PRIORITY`, and `LOCATION_PRIORITY` ordering invariants (`SPEED_AND_LOCATION_GPS` -> `SPEED_AND_LOCATION_GOOGLE_FUSED` -> `SPEED_AND_LOCATION_NETWORK`).
   - Verify `MySensorManager.registerSensor(...)` retains non-null `ProxySensor` instances for `SPEED_mps`, `PACE_spm`, `DISTANCE_m`, and `DISTANCE_m_LAP` when registered by a `DeviceType.SPEED_AND_LOCATION_GOOGLE_FUSED` device, updates `SensorType.SENSORS` (`GCdata`) with `'D'` and `'S'`, and dynamically upgrades/downgrades the `ProxySensor` source when higher-priority (`SPEED_AND_LOCATION_GPS`, `BIKE_SPEED`) sensors register or unregister.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests.
  2. Strict priority ordering (`RUN_SPEED > BIKE_SPEED > BIKE_SPEED_AND_CADENCE > BIKE_POWER > SPEED_AND_LOCATION_GPS > SPEED_AND_LOCATION_GOOGLE_FUSED > SPEED_AND_LOCATION_NETWORK`).
  3. Parent ticket Human Decision Gate remains strictly enforced.
* **Risk Rating**: **LOW** — Single-array priority alignment in `MySensorManager.java` matching `LOCATION_PRIORITY` and `ALTITUDE_PRIORITY`.
