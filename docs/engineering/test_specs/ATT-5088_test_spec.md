# Stage 2: Requirement & Test Specification - ATT-5088: Record vehicle pass events during rides and display post-workout traffic statistics and map hotspots

**Ticket**: [ATT-5088](https://rainerblind.atlassian.net/browse/ATT-5088)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar: BLE & ANT+ Acquisition, Live Edge Overlay, Audio/Haptic Alerts & Traffic Analytics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Requirements**: `REQ-RAD-005`, `REQ-RAD-006`, `REQ-EXP-042`  
**Test Spec IDs**: `TST-RAD-005`, `TST-RAD-006`, `TST-EXP-042`  
**Branch**: `feature/ATT-5088`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-11  

---

## 1. Requirement Specification

### 1.1 Scope & System Rationale
Following cycling activities with rearview radar, athletes need post-workout traffic analytics: total overtaking vehicles, traffic density (veh/km, veh/h), peak closing/absolute vehicle speeds, spatial pass hotspots along the route, and FIT export compatibility with *MyBikeTraffic*.

### 1.2 Functional & Architectural Requirements
1. **Workout Traffic Persistence & Summary Card (`REQ-RAD-005`)**:
   - `WorkoutSampleRecord` and `WorkoutRepository.streamSamples()` SHALL stream latched 1 Hz radar metrics (`radarThreatCount`, `radarClosestDistanceM`, `radarApproachSpeedMps`, `radarVehicleCount`) with zero SQLite schema migrations (`DB_VERSION` unchanged).
   - The system SHALL compute session traffic extrema (`RadarTrafficSummary`): total vehicles passed, traffic density (veh/km, veh/h), max approach speed, max absolute vehicle speed ($v_{\text{rider}} + v_{\text{approach}}$), and high-threat passes.
   - `RadarTrafficSummaryCard.kt` SHALL render conditionally in `WorkoutDetailTabbedContent.kt` (`DETAILS`) when radar data is present, with 100% 9-language localization parity (`values*/strings.xml`). Non-radar workouts SHALL remain 100% unaffected.
2. **Aftermath Map Traffic Hotspots (`REQ-RAD-006`)**:
   - `WorkoutRepository.getWorkoutMarkers()` and `TrackOnMapAftermathViewModel` SHALL identify GPS coordinates where $\Delta \text{RADAR\_VEHICLE\_COUNT} > 0$ and emit discrete vehicle pass `LocationMarker`s along the polyline.
   - High-threat (fast approaching) passes SHALL be visually highlighted on `TrackOnMapScreen.kt`.
3. **Garmin FIT Radar Export (`REQ-EXP-042`)**:
   - `FitFileWriter.java` SHALL emit native FIT `EventMesg` (`Event.RADAR_THREAT_ALERT`, `EventType.MARKER`) upon vehicle pass events with threat count, max threat level, and approach speed.
   - `FitFileWriter.java` SHALL define FIT Protocol V2.0 Developer Fields on `RecordMesg` (`radar_current`, `radar_total`, `passing_speed`, `passing_speed_abs`) compatible with *MyBikeTraffic*.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Persistence & Details Card)**:
  * *Given* a workout recorded with active radar telemetry,
  * *When* the workout is viewed in Workout Details,
  * *Then* `RadarTrafficSummaryCard` renders total vehicles, density, and peak speeds in the active locale.
* **Criterion 2 (Map Pass Hotspots)**:
  * *Given* a workout with recorded vehicle passes and valid GPS,
  * *When* viewed in `TrackOnMapScreen.kt`,
  * *Then* pass events are marked at authentic track coordinates.
* **Criterion 3 (Garmin FIT Export)**:
  * *Given* a workout with radar passes exported to `.fit`,
  * *When* parsed with Garmin FIT SDK,
  * *Then* native `RADAR_THREAT_ALERT` events and `RecordMesg` developer fields are verified.

---

## 2. Test Specification

### Test Case 1: Workout Traffic Summary & Persistence (`TST-RAD-005`)
* **Scope**: Unit & Contract Test (`RadarTrafficSummaryTest.kt`)
* **Action**: Parse mock samples with vehicle counts and approach speeds; assert `RadarTrafficSummary` derivation (totals, density, absolute speed); assert 9-language translation parity for all radar summary card string keys.
* **Expected Result**: Exact mathematical aggregation; zero missing translation keys.

### Test Case 2: Aftermath Map Traffic Hotspots (`TST-RAD-006`)
* **Scope**: Unit Test (`TrackOnMapAftermathViewModelTest.kt` / `WorkoutRepositoryTest.kt`)
* **Action**: Feed sample stream with incrementing vehicle counts; query `getWorkoutMarkers()`; assert pass markers generated at correct GPS points with speed/threat tags; assert empty markers when no radar data exists.
* **Expected Result**: Accurate marker placement; zero extraneous markers for non-radar rides.

### Test Case 3: Garmin FIT Radar Export (`TST-EXP-042`)
* **Scope**: Integration Test (`FitFileWriterRadarTest.kt`)
* **Action**: Export workout with radar samples through `FitFileWriter`; decode output with `FitDecoder` / Garmin FIT SDK; verify `EventMesg` (`RADAR_THREAT_ALERT`) and `RecordMesg` developer fields (`radar_total`, `passing_speed`).
* **Expected Result**: Valid FIT V2.0 encoding matching MyBikeTraffic schema.

---

## 3. Traceability Matrix

| Requirement ID | Description | Test Case ID | Target Verification |
| :--- | :--- | :--- | :--- |
| `REQ-RAD-005` | 1 Hz radar persistence, extrema & summary card | `TST-RAD-005` | `RadarTrafficSummaryTest.kt`, `TranslationParityTest` |
| `REQ-RAD-006` | Aftermath map pass hotspot markers | `TST-RAD-006` | `TrackOnMapAftermathViewModelTest.kt` |
| `REQ-EXP-042` | FIT native events & developer fields | `TST-EXP-042` | `FitFileWriterRadarTest.kt` |
