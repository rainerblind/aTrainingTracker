# Stage 1 Analysis: ATT-5088 - Record vehicle pass events during rides and display post-workout traffic statistics and map hotspots

**Ticket**: [ATT-5088](https://rainerblind.atlassian.net/browse/ATT-5088)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-5088`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation

During outdoor rides with cycling rearview radar (Garmin Varia, Magene L508, Bryton Gardia, Trek CarBack), athletes receive real-time alerts of overtaking vehicles. However, until now, after the workout is finalized, athletes have had no access to post-ride traffic analytics (popularized by platforms like *MyBikeTraffic*):
1. **Lack of Post-Workout Traffic Statistics**: Riders cannot see how many vehicles overtook them, what the traffic density was (vehicles/km or vehicles/hour), or what the peak closing and absolute vehicle speeds were along their route.
2. **Missing Spatial Hotspot Visualization**: The aftermath map (`TrackOnMapScreen.kt`) does not show where overtaking passes occurred or where high-speed threats clustered.
3. **No Radar Data in FIT Exports**: Exporting workouts to Garmin `.fit` files currently omits native FIT radar threat events (`Event.RADAR_THREAT_ALERT`) and MyBikeTraffic-compatible developer fields on `RecordMesg`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Independent Peer Review & Verification**:
  * **Hypothesis 1 (Zero-Migration 1 Hz Sample Persistence & Extrema) — Verified**:
    Inspection of `WorkoutSamplesDatabaseManager.makeColumns()` (lines 870–890) confirms dynamic column creation from `sensorType.name()`. Because `RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, and `RADAR_VEHICLE_COUNT` are members of `SensorType`, they are automatically persisted in `WorkoutSamples.db`. Furthermore, `TABLE_EXTREMA_VALUES` (`WorkoutSummariesDatabaseManager.java:1240–1260`) stores sensor extrema as arbitrary strings (`SENSOR_TYPE`), so session traffic totals (`MAX`/`END` of `RADAR_VEHICLE_COUNT`, `MAX` of `RADAR_APPROACH_SPEED_mps`, traffic density) require **zero SQLite schema migrations** (`DB_VERSION` unchanged). `CSVFileWriter.java:70–100` dynamically queries all table columns and automatically exports radar metrics to CSV with zero changes.
  * **Hypothesis 2 (Workout Details Traffic Summary Card) — Verified**:
    Inspection of `WorkoutDetailTabbedContent.kt` confirms modular section dispatching (`DETAILS`, `DESCRIPTION`, `EXTREMA`, `ZONES`). A dedicated `RadarTrafficSummaryCard` can be seamlessly embedded in `WorkoutDetailSectionType.DETAILS` when radar data is present, displaying total vehicles passed, traffic density (vehicles/km, vehicles/h), max approach speed, max absolute vehicle speed, and high-threat passes.
  * **Hypothesis 3 (Compose Aftermath Map Traffic Hotspots) — Verified**:
    `TrackOnMapAftermathViewModel.kt` and `WorkoutRepository.getWorkoutMarkers()` generate `LocationMarker` objects for `TrackOnMapScreen.kt`. By identifying sample intervals where $\Delta \text{RADAR\_VEHICLE\_COUNT} > 0$, vehicle pass events with GPS coordinates, approach speeds, and threat levels can be rendered as discrete map markers along the polyline.
  * **Hypothesis 4 (FIT EventMesg & Developer Fields) — Verified**:
    Inspection of `fit-21.141.0-sources.jar` confirms that `EventMesg` supports native radar fields: `setRadarThreatCount()`, `setRadarThreatLevelMax(RadarThreatLevelType)`, `setRadarThreatAvgApproachSpeed()`, and `setRadarThreatMaxApproachSpeed()`. Furthermore, FIT V2.0 supports `DeveloperDataIdMesg` and `FieldDescriptionMesg` to define MyBikeTraffic developer fields on `RecordMesg` (`radar_current`, `radar_total`, `passing_speed`, `passing_speed_abs`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. *Sample Streaming & Persistence*: Ensure `WorkoutSampleRecord` in `WorkoutExportHeader.kt` and `WorkoutRepository.streamSamples()` expose radar metrics (`radarThreatCount`, `radarClosestDistanceM`, `radarApproachSpeedMps`, `radarVehicleCount`).
  2. *Extrema & Session Totals*: Compute traffic summary extrema in `WorkoutRepository` / `TrackerService`: total vehicles passed, max approach speed, max absolute vehicle speed ($v_{\text{rider}} + v_{\text{approach}}$), and traffic density.
  3. *Aftermath Traffic Summary Card*: Build `RadarTrafficSummaryCard.kt` in `ui/aftermath/` and wire it into `WorkoutDetailTabbedContent.kt`, localized across all 9 languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).
  4. *Aftermath Map Traffic Markers*: Expose vehicle pass markers in `TrackOnMapAftermathViewModel` / `WorkoutRepository` so `TrackOnMapScreen.kt` displays vehicle pass locations and high-threat hotspots along the GPS polyline.
  5. *Garmin FIT Radar Export*: Extend `FitFileWriter.java` to emit native `EventMesg` (`Event.RADAR_THREAT_ALERT`) on pass transitions and define `RecordMesg` developer fields (`radar_total`, `passing_speed`, etc.) compatible with *MyBikeTraffic*.
  6. *Automated Unit Tests*: Cover traffic metrics derivation, summary card rendering contracts, map marker extraction, FIT message emission, and 9-language translation parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No SQLite database schema migrations (maintain strict `DB_VERSION` invariance).
  * No cloud upload API modifications (Strava description radar summary is handled separately in `ATT-5122`).
  * No live Cockpit edge overlay modifications (`ATT-5085`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-RAD-005` (Workout Traffic Persistence & Statistics), `REQ-RAD-006` (Aftermath Traffic Hotspot Map), `REQ-EXP-042` (FIT Radar Threat Events & Developer Fields).
* **Historical Origin & Commit Trace**:
  * `dfbe39791ab8` (ATT-5083: Radar foundation, scalar SensorTypes).
  * `601fb789` (ATT-5087: Built-in Radar Traffic Simulator).
  * `a1240c5f` (ATT-528: `WorkoutClusterEngine`, `LocationMarker` spatial rendering).
  * `4249a405` (ATT-3543: FIT Protocol V2.0 encoder, `FitFileWriter`).
* **Root Reason for Existing Formulation**:
  * Prior to ATT-1286, `aTrainingTracker` had no cycling radar support. Once `ATT-5083` introduced real-time threat acquisition, capturing these events longitudinally for post-workout review was the next architectural step.
* **Preservation of Core Invariants**:
  * Existing SQLite schemas (`WorkoutSamples.db`, `WorkoutSummaries.db`) remain 100% unchanged.
  * Workouts recorded without radar continue to render standard details and export valid FIT/CSV files with zero overhead.
  * FIT file encoding strictly adheres to FIT Protocol V2.0.

---

## 5. Architectural Strategy & High-Level Solution

1. **`WorkoutExportHeader.kt` & `WorkoutRepository.kt`**:
   - Add radar fields to `WorkoutSampleRecord`: `radarThreatCount: Int?`, `radarClosestDistanceM: Double?`, `radarApproachSpeedMps: Double?`, `radarVehicleCount: Int?`.
   - Update `WorkoutRepository.streamSamples()` to query and populate these fields from the sample table cursor.
2. **Traffic Summary Model & Extrema**:
   - Create `RadarTrafficSummary(totalVehicles: Int, vehiclesPerKm: Double, vehiclesPerHour: Double, maxApproachSpeedMps: Double, maxVehicleSpeedMps: Double, highThreatPassCount: Int)`.
   - Provide helper `WorkoutRepository.getRadarTrafficSummary(workoutId: Long)` reading from `TABLE_EXTREMA_VALUES` or samples cursor.
3. **`RadarTrafficSummaryCard.kt` (`ui/aftermath/`)**:
   - Clean Jetpack Compose card rendering Key Metric tiles with radar badge, total vehicles, density (veh/km), max closing speed, and high-threat passes.
   - Embed conditionally in `WorkoutDetailTabbedContent.kt` under `DETAILS` section.
4. **Map Pass Markers in `WorkoutRepository.getWorkoutMarkers()`**:
   - When radar samples exist with GPS coordinates and $\Delta \text{RADAR\_VEHICLE\_COUNT} > 0$, generate `LocationMarker(pos, R.drawable.bike_radar, title)` indicating vehicle speed and threat level.
5. **Garmin FIT Export in `FitFileWriter.java`**:
   - Track previous vehicle count during sample streaming. When a pass occurs ($\Delta \text{count} > 0$), emit an `EventMesg` with `Event.RADAR_THREAT_ALERT`, `EventType.MARKER`, `radarThreatCount`, `radarThreatLevelMax`, and `radarThreatMaxApproachSpeed`.
   - Define FIT Developer Fields on `RecordMesg` (`radar_current`, `radar_total`, `passing_speed`, `passing_speed_abs`).
6. **9-Language Localization**:
   - Localize all card labels and marker strings across all 9 languages in `res/values*/strings.xml`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero SQLite schema migrations (`WorkoutSamples.db` version 1, `WorkoutSummaries.db` version unchanged).
  2. Zero regressions in CSV and TCX exporters.
  3. Workouts without radar render exactly as before with zero overhead or empty cards.
  4. 100% 9-language localization parity.
* **Risk Rating**: **MEDIUM**
  - Spans aftermath UI, map marker generation, and FIT export encoding, but cleanly decoupled with modular fallback paths and strict unit test coverage.
