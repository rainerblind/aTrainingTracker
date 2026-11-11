# Stage 3: Implementation Plan - ATT-5096: Render vehicle size (car vs. truck) and lateral lane-change offsets in live radar overlay and record close-pass (< 1.5 m) hotspots

**Ticket**: [ATT-5096](https://rainerblind.atlassian.net/browse/ATT-5096)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TRK-073`  
**Test Mapping**: `TST-TRK-073`  
**Branch**: `feature/ATT-5096`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Garmin Varia BLE V2 high-resolution radar (`6a4e3204` from RearVue 820) provides 2D spatial telemetry: vehicle size classification (`VehicleSizeCategory`), lateral lane trajectories (`LaneState`), and passing clearance (`RADAR_PASSING_DISTANCE_m`). 

Currently, `RadarEdgeOverlay.kt` renders all approaching vehicles as identical 1D circular 14.dp dots on a fixed 140 m vertical track. Cyclists cannot distinguish between a passenger car and a heavy commercial truck, nor see if a vehicle has changed into the passing lane. In post-workout analytics, `TrackOnMapScreen.kt` and `RadarTrafficSummaryCard.kt` lack spatial close-pass hazard markers and passing clearance metrics.

This implementation plan delivers full 2D live overlay visualization, configurable close-pass safety thresholds, aftermath hazard mapping, and FIT Protocol V2.0 developer field persistence.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-073` (*Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay*)
* **Test Mapping**: `TST-TRK-073` (*Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay Test Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **V1 Legacy Radar Invariant**: RTL515, RTL516, RVR315, and ANT+ units emit 1D scalar data; the overlay must render standard centered 14.dp circular dots on a 140 m rail with zero visual distortion.
2. **Zero SQLite Migration**: Zero database schema alterations (`DB_VERSION` unchanged); 1 Hz latched scalar values use existing SQLite column projections.
3. **Rail Touch Pass-Through**: Overlay rail and targets remain 100% transparent to pointer inputs (`REQ-TRK-062.3`), preserving gestures on underlying maps and views.
4. **9-Language String Parity**: Complete localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.

---

## 4. Proposed Architectural Changes

### Component 1: Preferences & Settings (`TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`)
- Add `KEY_RADAR_CLOSE_PASS_THRESHOLD` to `TuningPreferencesDataStore` with default `1.5f` (meters).
- Provide flow `radarClosePassThresholdFlow` and mutation method `updateRadarClosePassThreshold(Float)`.
- Mount an M3 preset chip selector / slider (`1.0 m` [3 ft], `1.5 m` [StVO Urban], `2.0 m` [StVO Rural]) in `RadarSettingsSection.kt`.

### Component 2: Live Cockpit Edge Overlay (`RadarEdgeOverlay.kt`)
- `RadarOverlayMath`:
  - `resolveRailMaxRange()`: Dynamically scales rail to `175.0f` when V2 targets or ranges $> 140\text{ m}$ are detected.
  - `calculateHorizontalOffset()`: Maps `target.laneState` / `target.lateralOffsetMeters` to horizontal pixel offsets based on `radarTrafficOrientation` (RHT vs LHT).
- `RadarVerticalRail`:
  - Expands rail width from `22.dp` to `28.dp` during active 2D V2 tracking.
  - Renders distinct marker geometry:
    - `UNKNOWN_OR_LIGHT` (small): `10.dp` dot.
    - `CAR` (medium): `14.dp` circular dot.
    - `TRUCK_OR_BUS` (large): `20.dp` wide $\times$ `12.dp` high pill badge (`RoundedCornerShape(4.dp)`).

### Component 3: Data Access & Aftermath Analytics (`WorkoutExportHeader.kt`, `WorkoutRepository.kt`)
- Extend `WorkoutSampleRecord` with `radarPassingDistanceM: Double?` and `radarLargeVehicleCount: Int?`.
- Update `WorkoutRepository.streamSamples()` to project both fields from `WorkoutSamples.db`.
- Update `WorkoutRepository.computeRadarTrafficSummaryFromSamples()` to calculate `minPassingDistanceM`, `closePassCount`, and `heavyVehicleCount`.
- In `WorkoutRepository.getWorkoutMarkers()`: Add high-visibility Close-Pass Hazard Markers (`LocationMarker`) for passes with clearance $< \text{threshold}$, featuring distance, vehicle size, and speed in the tooltip.

### Component 4: Traffic Summary Card (`RadarTrafficSummaryCard.kt`)
- Add Row 3 for Passing Clearance & Vehicle Safety: Minimum Passing Clearance, Close-Pass Count (with warning badge if $> 0$), and Large Vehicle Count & Share.

### Component 5: FIT Exporter Developer Fields (`FitFileWriter.java`)
- Define FIT developer fields 2 (`passing_distance`, uint16, scale 100, "m") and 3 (`large_vehicle_count`, uint16, scale 1, "vehicles").
- Serialize developer fields into `RecordMesg` records for radar rides.

### UI Consistency (Rule 23)
* **Reference screens**: `RadarEdgeOverlay.kt` (Cockpit overlay rail), `RadarSettingsSection.kt` (ElevatedCard tuning), `TrackOnMapScreen.kt` (Aftermath map markers).
* **Reused components**: `FilterChip`, `ElevatedCard`, `LocationMarker`, `MetricItem`.
* **Theme tokens**: Standard M3 colors, `TTColor.RadarThreatAmber`, `TTColor.RadarThreatRed`, `12.dp` corner shapes.
* **New one-off styles**: None.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Preferences & Settings Wiring
* Files: `TuningPreferencesDataStore.kt`, `RadarSettingsSection.kt`
* Add `radarClosePassThreshold` preference, flow, and UI chip selector in `RadarSettingsSection.kt`.

### Step 2: Live Cockpit Edge Overlay 2D Geometry
* File: `RadarEdgeOverlay.kt`
* Implement 175m dynamic rail scaling, vehicle size geometry, and lateral lane-shift mapping respecting traffic orientation.

### Step 3: Domain Model & Sample Persistence
* Files: `WorkoutExportHeader.kt`, `WorkoutRepository.kt`
* Expose `radarPassingDistanceM` and `radarLargeVehicleCount` in `WorkoutSampleRecord` and `streamSamples()`.

### Step 4: Aftermath Close-Pass Hazard Layer & Summary Card
* Files: `WorkoutRepository.kt`, `RadarTrafficSummaryCard.kt`
* Emit close-pass `LocationMarker`s along the route and render passing clearance metrics & warning badges.

### Step 5: FIT Protocol V2.0 Developer Fields
* File: `FitFileWriter.java`
* Declare and encode `passing_distance` and `large_vehicle_count` developer fields on `RecordMesg`.

### Step 6: Unit & Integration Testing
* Files: `RadarEdgeOverlayTest.kt`, `TuningPreferencesDataStoreTest.kt`, `WorkoutRepositoryTest.kt`, `FitFileWriterRadarTest.kt`
* Test execution command:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest \
    --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarEdgeOverlayTest" \
    --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryTest" \
    --tests "com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriterRadarTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Execute targeted unit test suite under `parallel_sprint_lock.py`, verify 9-language translation parity, and run clean-room governance checks.
* **Rollback**: Branch isolation on `feature/ATT-5096` allows safe rollback.
