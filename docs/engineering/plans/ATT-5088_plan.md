# Stage 3: Implementation Plan - ATT-5088: Record vehicle pass events during rides and display post-workout traffic statistics and map hotspots

**Ticket**: [ATT-5088](https://rainerblind.atlassian.net/browse/ATT-5088)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-RAD-005`, `REQ-RAD-006`, `REQ-EXP-042`  
**Test Mapping**: `TST-RAD-005`, `TST-RAD-006`, `TST-EXP-042`  
**Branch**: `feature/ATT-5088`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Problem Description & Background

Athletes riding with cycling rearview radar (Garmin Varia, Magene L508, Bryton Gardia, Trek CarBack) receive real-time edge alerts, but post-workout traffic analytics (*MyBikeTraffic* features) are currently missing:
1. No post-workout session traffic metrics (total overtaking vehicles, passing density per km/h, peak closing and absolute speeds).
2. No spatial hotspot markers on the aftermath map (`TrackOnMapScreen.kt`) showing where vehicle passes and high-threat closing speeds clustered.
3. Garmin FIT exports omit native `EventMesg` (`Event.RADAR_THREAT_ALERT`) and MyBikeTraffic-compatible developer fields on `RecordMesg`.

---

## 2. Traceability & Requirements Mapping

* **`REQ-RAD-005` / `TST-RAD-005`**: Workout Traffic Persistence & Summary Card (`RadarTrafficSummary`, latched columns streaming, zero SQLite schema migration, `RadarTrafficSummaryCard.kt` in `WorkoutDetailTabbedContent.kt`).
* **`REQ-RAD-006` / `TST-RAD-006`**: Aftermath Map Traffic Hotspots (`WorkoutRepository.getWorkoutMarkers()` emitting discrete vehicle pass `LocationMarker`s along polyline with speed and threat metadata).
* **`REQ-EXP-042` / `TST-EXP-042`**: Garmin FIT Radar Export (`FitFileWriter.java` emitting native `EventMesg` `RADAR_THREAT_ALERT` and FIT V2.0 `RecordMesg` developer fields: `radar_current`, `radar_total`, `passing_speed`, `passing_speed_abs`).

---

## 3. System Invariants & Preserved Behavior

1. **Zero SQLite Schema Migrations**: `WorkoutSamples.db` version 1 and `WorkoutSummaries.db` version unchanged (`SensorType` names drive columns dynamically; `TABLE_EXTREMA_VALUES` indexes sensor names as strings).
2. **Backward-Compatible Exporters**: Workouts recorded without radar export identical FIT, CSV, and TCX streams without extra overhead or schema divergence.
3. **UI Null Safety**: Non-radar workouts omit `RadarTrafficSummaryCard` and pass markers cleanly with zero empty placeholders or layout shifts.
4. **Human Gate Governance**: Subtasks transition to `Erledigt` via agent `freigabe`; parent ticket transitions strictly to `Final Review (Human)` assigned to `rainer.blind`.

---

## 4. Proposed Architectural Changes

### Component 1: `WorkoutExportHeader.kt` & `WorkoutRepository.kt`
* Add radar fields to `WorkoutSampleRecord`: `radarThreatCount: Int?`, `radarClosestDistanceM: Double?`, `radarApproachSpeedMps: Double?`, `radarVehicleCount: Int?`.
* Extend `WorkoutRepository.streamSamples()` to read `RADAR_THREAT_COUNT`, `RADAR_CLOSEST_DISTANCE_m`, `RADAR_APPROACH_SPEED_mps`, `RADAR_VEHICLE_COUNT` columns if present.
* Add pass marker generation in `WorkoutRepository.getWorkoutMarkers(workoutData)`: detecting $\Delta \text{RADAR\_VEHICLE\_COUNT} > 0$ with GPS coordinates and emitting `LocationMarker(pos, R.drawable.bike_radar, ...)`.

### Component 2: Domain Model & `RadarTrafficSummaryCard.kt`
* Create `RadarTrafficSummary` model: `totalVehicles`, `vehiclesPerKm`, `vehiclesPerHour`, `maxApproachSpeedMps`, `maxVehicleSpeedMps`, `highThreatPassCount`.
* Add `WorkoutData.radarTrafficSummary: RadarTrafficSummary?` populated in `WorkoutDataMapper.kt` from `TABLE_EXTREMA_VALUES` or samples cursor.
* Create `ui/aftermath/RadarTrafficSummaryCard.kt` (`ElevatedCard`, `RoundedCornerShape(12.dp)`, `16.dp` padding, `MetricItem` tiles, `bike_radar` header icon) and wire conditionally into `WorkoutDetailTabbedContent.kt` (`DETAILS` section).

### Component 3: `FitFileWriter.java` (FIT SDK 21.141.0)
* Register `DeveloperDataIdMesg` and `FieldDescriptionMesg` (id 0..3: `radar_current`, `radar_total`, `passing_speed`, `passing_speed_abs`) for FIT Protocol V2.0.
* On vehicle count increment ($\Delta \text{count} > 0$), emit native `EventMesg` (`Event.RADAR_THREAT_ALERT`, `EventType.MARKER`, `radarThreatCount`, `radarThreatLevelMax`, `radarThreatMaxApproachSpeed`).
* Attach developer fields to `RecordMesg` when radar metrics are present.

### UI Consistency (Design Guidelines §5)
* Reference screen: `CardioPowerDynamicsCard.kt` / `GradeZoneDistributionCard.kt`.
* Shapes & Spacing: `RoundedCornerShape(12.dp)`, content padding `16.dp`, metric spacing `12.dp`, typography from `MaterialTheme.typography`, colors from `MaterialTheme.colorScheme` & `TTColor`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

| Step | Target Files | Verification / Tests |
| :--- | :--- | :--- |
| **1. Sample DTO & Streaming** | `WorkoutExportHeader.kt`, `WorkoutRepository.kt` | Unit tests asserting radar columns populated in `WorkoutSampleRecord` |
| **2. Traffic Summary & Mapper** | `RadarTrafficSummary.kt`, `WorkoutData.kt`, `WorkoutDataMapper.kt` | `RadarTrafficSummaryTest.kt` verifying totals, density, absolute speeds |
| **3. Summary Card UI** | `RadarTrafficSummaryCard.kt`, `WorkoutDetailTabbedContent.kt` | Robolectric / Compose preview tests |
| **4. Map Pass Markers** | `WorkoutRepository.kt`, `TrackOnMapAftermathViewModel.kt` | `TrackOnMapAftermathViewModelTest.kt` asserting pass markers along track |
| **5. FIT Export** | `FitFileWriter.java` | `FitFileWriterRadarTest.kt` decoding `EventMesg` & developer fields |
| **6. 9-Language Localization** | `app/src/main/res/values*/strings.xml` (all 9 locales) | `TranslationParityTest.kt` |

---

## 6. Verification & Rollback Plan

* **Automated Gates**:
  ```bash
  python3 tools/parallel_sprint_lock.py ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.exporter.writer.FitFileWriter*" --tests "com.atrainingtracker.banalservice.TranslationParityTest"
  ```
* **Rollback**: Branch isolation (`feature/ATT-5088`) enables atomic rollback via `git reset --hard origin/sprint/2026-46.2`.
