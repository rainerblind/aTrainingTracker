# Stage 1 Analysis: ATT-5096 - [Feature] Render vehicle size (car vs. truck) and lateral lane-change offsets in live radar overlay and record close-pass (< 1.5 m) hotspots

**Ticket**: [ATT-5096](https://rainerblind.atlassian.net/browse/ATT-5096)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Branch**: `feature/ATT-5096`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

Following the implementation of Garmin BLE V2 high-resolution radar decoding (`ATT-5095` / `6a4e3204`), the system receives 2D spatial telemetry: lateral offset (`lateralOffsetMeters`), vehicle classification (`VehicleSizeCategory`), lane trajectory (`LaneState`), and passing clearance (`RADAR_PASSING_DISTANCE_m`). 

Currently, `RadarEdgeOverlay.kt` renders all targets as identical 1D circular 14.dp dots centered on a fixed 140 m vertical rail. Athletes cannot discern whether an approaching vehicle is a passenger car or a heavy commercial truck, nor whether the vehicle has shifted into the adjacent passing lane or remains on a direct collision course behind the cyclist. Furthermore, post-workout analytics (`TrackOnMapScreen.kt` and `RadarTrafficSummaryCard.kt`) display only pass counts and peak speeds without spatial close-pass hazard markers or passing clearance metrics.

This feature enhances both live tracking and aftermath analytics:
1. Live 2D edge overlay rendering with dynamic 175 m range scaling, distinct vehicle size markers (compact dot for light vehicles, standard dot for cars, elongated double-width badge for trucks/busses), and lateral lane-shift visualization.
2. Configurable close-pass safety threshold in `RadarSettingsSection.kt` (`default: 1.5 m`, with `1.0 m`, `1.5 m`, `2.0 m` presets).
3. Post-workout close-pass hazard map layer, expanded Traffic Summary Card, and FIT Protocol V2.0 developer field serialization.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification (4-Augen-Prinzip — ATT-3534)**:
  * *Audit of Hypothesis against Codebase*:
    1. `RadarEdgeOverlay.kt`: `RadarOverlayMath.MAX_RADAR_RANGE_METERS` is hardcoded to `140.0f`. `RadarVerticalRail` uses a fixed `22.dp` track width with target dots aligned at `Alignment.TopCenter` (`x = 0`). Targets must dynamically scale range to `175.0f` when V2 telemetry or distant targets are present, expand track width (e.g., `28.dp`–`32.dp`), and apply horizontal offsets based on `target.laneState` / `target.lateralOffsetMeters` and `radarTrafficOrientation` (RHT vs LHT).
    2. `RadarState.kt` & `BTLERadarDevice.java`: `VehicleSizeCategory` (`UNKNOWN_OR_LIGHT`, `CAR`, `TRUCK_OR_BUS`), `LaneState` (`UNKNOWN`, `IN_RIDER_LANE`, `CHANGING_LANE`, `PASSING_LANE`), and `lateralOffsetMeters` are already decoded by `BTLERadarDevice.java` into `RadarTarget`.
    3. `WorkoutExportHeader.kt` & `WorkoutRepository.kt`: `WorkoutSampleRecord` does not yet expose `radarPassingDistanceM: Double?` and `radarLargeVehicleCount: Int?`. `WorkoutRepository.streamSamples()` must populate these from `WorkoutSamples.db` columns, and `computeRadarTrafficSummaryFromSamples()` must aggregate minimum passing distance, close passes below threshold, and large vehicle count.
    4. `TrackOnMapScreen.kt` / `WorkoutRepository.getWorkoutMarkers()`: Currently places vehicle pass markers based only on vehicle count increments. Must evaluate `sample.radarPassingDistanceM` against the configured threshold to generate high-visibility Close-Pass Hazard Markers with tooltips showing clearance, vehicle type, and speed.
    5. `FitFileWriter.java`: Defines developer fields 0 (`radar_current`) and 1 (`radar_total`). Needs developer fields for `passing_distance` (uint16, scale 100, "m") and `large_vehicle_count` (uint16, scale 1, "vehicles").
    6. `TuningPreferencesDataStore.kt` & `RadarSettingsSection.kt`: Lacks `radarClosePassThresholdM` preference. Needs M3 preset selector (`1.0 m`, `1.5 m`, `2.0 m`).

---

## 3. User Scope Grounding (ATT-1250) & Side-Effect Audit

* **In-Scope Goals (ATT-5096 - 3 SP)**:
  * **Live Cockpit Overlay**: Dynamic 175 m rail scaling, vehicle size geometry (`SMALL` 10.dp dot, `MEDIUM` 14.dp circle, `LARGE` 20x12.dp pill badge), horizontal lateral offset mapping (respecting RHT/LHT traffic orientation).
  * **Close-Pass Settings**: `radarClosePassThresholdM` preference in `TuningPreferencesDataStore` with 9-language presets (`1.0 m` [3 ft], `1.5 m` [StVO Urban], `2.0 m` [StVO Rural]).
  * **Sample & Extrema Persistence**: Add `radarPassingDistanceM` and `radarLargeVehicleCount` to `WorkoutSampleRecord` with zero SQLite schema migrations; compute `minPassingDistanceM`, `closePassCount`, and `heavyVehicleCount` in `RadarTrafficSummary`.
  * **Aftermath Close-Pass Hazard Layer**: Dedicated close-pass warning markers along the GPS track on `TrackOnMapScreen.kt` via `WorkoutRepository.getWorkoutMarkers()`.
  * **Traffic Summary Card Enhancement**: Add passing clearance metrics and close-pass alert badge to `RadarTrafficSummaryCard.kt`.
  * **FIT Developer Fields**: Encode `passing_distance` and `large_vehicle_count` on FIT `RecordMesg`.
* **Out-of-Scope Non-Goals**:
  * No modification to BLE GATT connection state machines or V2 LESC bonding (completed in `ATT-5095` / `ATT-6706`).
  * No SQLite schema alterations (`DB_VERSION` unchanged).
  * No third-party API changes.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Net-new requirements `REQ-TRK-066` (*Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay*), `REQ-RAD-008` (*Post-Workout Close-Pass Hazard Map & Heavy-Vehicle Safety Analytics*), and `REQ-EXP-044` (*FIT Protocol V2.0 Passing Clearance & Large Vehicle Developer Fields*), extending `REQ-TRK-062` (Live Edge Overlay), `REQ-RAD-005` (Traffic Persistence), `REQ-RAD-006` (Map Hotspots), and `REQ-EXP-042` (FIT Radar Developer Fields).
* **Chesterton's Fence Invariant**: Legacy V1 radars (RTL515/RTL516/RVR315) and ANT+ units emit 1D scalar data (`UNKNOWN_OR_LIGHT`, `lateralOffsetMeters = null`). The overlay must gracefully preserve centered circular dot rendering for V1 devices without visual distortion or regression.

---

## 5. Architectural Strategy & High-Level Solution

```
                                  ┌─── BTLERadarDevice (V2 stream) / RadarTrafficSimulator ───┐
                                  │                                                           │
                                  ▼                                                           ▼
                     RadarState (RadarTarget)                                    RadarWindowAggregator
             (vehicleSizeCategory, laneState, lateral)                     (minPassingLateral, largeVehicles)
                                  │                                                           │
                                  ▼                                                           ▼
                      RadarEdgeOverlay.kt                                           WorkoutSamples.db
            - 175m dynamic rail scaling                                     (RADAR_PASSING_DISTANCE_m,
            - Small dot / Medium car / Large truck pill                     RADAR_LARGE_VEHICLE_COUNT)
            - Horizontal lane-shift (RHT / LHT)                                       │
                                                                                      ▼
                                                                           WorkoutRepository.streamSamples()
                                                                                      │
                                                    ┌─────────────────────────────────┴─────────────────────────────────┐
                                                    ▼                                                                   ▼
                                         TrackOnMapScreen.kt                                               RadarTrafficSummaryCard
                                     (Close-Pass Hazard Markers)                                     (Passing Clearance & Close-Pass Badge)
```

1. **Overlay Geometry (`RadarEdgeOverlay.kt`)**: Implement `RadarOverlayMath.resolveRailMaxRange()` (140m vs 175m), `resolveTargetShape()`, and `calculateHorizontalOffset()` based on `lateralOffsetMeters` or `laneState` adjusted for `radarTrafficOrientation`.
2. **Preference (`TuningPreferencesDataStore.kt`)**: Add `KEY_RADAR_CLOSE_PASS_THRESHOLD` with flow and update methods.
3. **Analytics Pipeline (`WorkoutRepository.kt`)**: Read passing distance and large vehicle count in `streamSamples()`, populate `RadarTrafficSummary`, and emit close-pass `LocationMarker`s in `getWorkoutMarkers()`.
4. **Export Serialization (`FitFileWriter.java`)**: Add developer fields 2 & 3.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *100% V1 Backward Compatibility*: Legacy radars with 1D scalar data must render identical centered markers without crashes or layout clipping.
  2. *Zero Database Migration*: Zero SQLite schema changes (`DB_VERSION` intact).
  3. *Touch Pass-Through*: The overlay rail must remain completely click-transparent to avoid interfering with map gestures or viewpager swipes (`REQ-TRK-062.3`).
  4. *Localization Parity*: All preset labels and tooltip strings localized across all 9 languages.
* **Risk Rating**: **MEDIUM** (Multi-component feature spanning Compose overlay math, map marker generation, settings DataStore, and FIT developer fields; risk bounded by existing V2 domain models and pure mathematical transformations).
