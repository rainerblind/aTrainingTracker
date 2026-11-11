# Stage 2: Requirement & Test Specification - ATT-5096: Render vehicle size and lateral lane-change offsets in live radar overlay and record close-pass hotspots

**Ticket**: [ATT-5096](https://rainerblind.atlassian.net/browse/ATT-5096)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `v2026.46`  
**Active Sprint**: `sprint/2026-46.2`  
**Requirement Mapping**: `REQ-TRK-073`  
**Test Mapping**: `TST-TRK-073`  
**Branch**: `feature/ATT-5096`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`)  
**Date**: 2026-11-11  

---

## 1. Traceability & Requirements Mapping

* **Requirement**: `REQ-TRK-073` (*Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay*)
* **Test Specification**: `TST-TRK-073` (*Live 2D Lane-Offset & Vehicle Sizing Radar Edge Overlay Test Suite*)

---

## 2. Requirements & Verification Matrix

| Requirement ID | Description | Test Case | Target Test Class |
| :--- | :--- | :--- | :--- |
| `REQ-TRK-073.1` | Dynamic 175m rail scaling for V2 streams / distant targets | `TST-TRK-073.1` | `RadarEdgeOverlayTest.kt` |
| `REQ-TRK-073.2` | Vehicle size geometry: light dot (10dp), car (14dp), truck pill (20x12dp) | `TST-TRK-073.1` | `RadarEdgeOverlayTest.kt` |
| `REQ-TRK-073.3` | Lateral lane offset mapping ($x \in [-6\text{dp}, +6\text{dp}]$) with RHT/LHT orientation | `TST-TRK-073.2` | `RadarEdgeOverlayTest.kt` |
| `REQ-TRK-073.4` | Configurable close-pass threshold (1.0m, 1.5m default, 2.0m presets) | `TST-TRK-073.3` | `TuningPreferencesDataStoreTest.kt` |
| `REQ-TRK-073.5` | Aftermath map close-pass hazard markers (< threshold) with clearance tooltip | `TST-TRK-073.4` | `WorkoutRepositoryTest.kt` |
| `REQ-TRK-073.6` | Traffic summary card clearance metrics, close-pass badge, & large-vehicle share | `TST-TRK-073.4` | `RadarTrafficSummaryTest.kt` |
| `REQ-TRK-073.7` | FIT Protocol V2.0 developer fields: `passing_distance` & `large_vehicle_count` | `TST-TRK-073.5` | `FitFileWriterRadarTest.kt` |
| `REQ-TRK-073.8` | 100% 9-language localization parity across EN, DE, ES, FR, IT, JA, NL, PL, PT | `TST-TRK-073.6` | `TranslationParityTest.kt` |

---

## 3. Detailed Acceptance Criteria & Verification Procedures

### TST-TRK-073.1: Rail Scaling & Vehicle Size Indicator Geometry
* **Given**: A `RadarState` with targets at ranges 40m, 120m, and 165m, with sizes `UNKNOWN_OR_LIGHT`, `CAR`, and `TRUCK_OR_BUS`.
* **When**: `RadarOverlayMath.resolveRailMaxRange()` and `RadarEdgeOverlay` execute.
* **Then**: Max range scales to 175.0m; light vehicle renders as 10.dp dot, car as 14.dp circle, truck as 20x12.dp pill badge.

### TST-TRK-073.2: Lateral Lane Offset & Traffic Orientation
* **Given**: A target with `lateralOffsetMeters = 2.0m` (`PASSING_LANE`).
* **When**: Evaluated under `RadarTrafficOrientation.RIGHT_HAND` vs. `LEFT_HAND`.
* **Then**: In RHT, marker shifts horizontally to the left (-6.dp from right edge rail); in LHT, marker shifts horizontally to the right.

### TST-TRK-073.3: Close-Pass Safety Threshold DataStore Preference
* **Given**: `TuningPreferencesDataStore` with default config.
* **When**: Read, updated to 2.0f, and re-read.
* **Then**: Initial value is 1.5f; update persists and emits 2.0f through `radarClosePassThresholdFlow`.

### TST-TRK-073.4: Post-Workout Close-Pass Hazard Map Markers & Summary Card
* **Given**: A recorded ride with vehicle passes at 1.10m and 1.90m clearance, with threshold = 1.50m.
* **When**: `WorkoutRepository.getWorkoutMarkers()` and `RadarTrafficSummaryCard` render.
* **Then**: The 1.10m pass generates a Close-Pass Hazard `LocationMarker` with custom hazard icon; summary card highlights 1 close pass.

### TST-TRK-073.5: FIT Developer Fields Encoding
* **Given**: A ride with recorded passing distance and large vehicle count.
* **When**: Exported via `FitFileWriter.java` and decoded via Garmin FIT SDK `FitDecoder`.
* **Then**: `RecordMesg` records contain developer fields `passing_distance` and `large_vehicle_count` matching recorded values.

---

## 4. Invariants & Governance

1. **V1 Radar Invariant**: Standard BLE V1 and ANT+ 1D telemetry render centered 14.dp circular dots on 140m rail with zero distortion.
2. **Zero SQLite Migration**: Zero changes to database schema or `DB_VERSION`.
3. **Rail Touch Pass-Through**: Overlay rail remains completely transparent to pointer inputs (`REQ-TRK-062.3`).
4. **9-Language Parity**: 12 new string keys added across all 9 localized `strings_radar.xml` files with zero missing entries.
