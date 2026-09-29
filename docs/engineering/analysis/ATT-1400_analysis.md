# Stage 1 Analysis: ATT-1400 - Lieblingsorte: Display Start & Destination Locations in Workout Details and Map

**Ticket**: [ATT-1400](https://atrainingtracker.atlassian.net/browse/ATT-1400)  
**Sub-task**: [ATT-1574](https://atrainingtracker.atlassian.net/browse/ATT-1574) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Branch**: `feature/ATT-1400`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & Motivation

In `aTrainingTracker`, "Lieblingsorte" (favorite start locations) have historically functioned primarily as silent background reference geofences for barometric altitude calibration and automated workout title generation (`WorkoutAutoNamingHelper.kt`, ATT-1398).

### Current Deficiency
Once a workout is saved and viewed in the Aftermath journal (workout list, summary cards, and full-screen map views):
1. **Disconnected Geographic Metadata**: While the workout title may reflect start and destination names (if auto-naming was triggered without a custom cluster override), the explicit start location and destination location are not structured or displayed anywhere in the `WorkoutHeader` / `WorkoutSummary`.
2. **Anonymous Map Endpoints**: On the Google Map in `TrackOnMapScreen` / `MapDetailLayout` and `PathPreviewMap`, start and stop pins render generic label strings (`"Start"`, `"Stop"` / `"Halt"`) without mentioning the recognized Lieblingsort (e.g. "Start: Zuhause", "Ziel: Büro").
3. **Loss of Journaling Context**: Workouts lack immediate spatial storytelling. An athlete inspecting past activities or looking at a route preview cannot see at a glance where the session originated or concluded unless they deduce it from map coordinates.

---

## 2. Root Cause Analysis (Forensic Investigation & Gap Analysis)

### Call-Site & Architecture Investigation

1. **Presentation Model (`WorkoutHeaderData.kt`, `WorkoutData.kt`)**:
   - `WorkoutHeaderData` encapsulates header-level fields: `workoutName`, `formattedDate`, `formattedTime`, `bSportType`, `sportName`, `equipmentName`, `commute`, `trainer`, `clusterId`, `clusterName`, and `finished`.
   - `WorkoutData` contains raw spatial coordinates `startLatLng: LatLng?` and `endLatLng: LatLng?`, which are populated from the summary extrema table (`SensorType.LATITUDE, ExtremaType.START / ExtremaType.END`).
   - *Gap*: Neither `WorkoutHeaderData` nor `WorkoutData` stores or exposes `startLocationName: String?` or `endLocationName: String?`.

2. **Data Mapping Layer (`WorkoutDataMapper.kt`)**:
   - `WorkoutDataMapper` maps SQLite cursor rows and extrema values to `WorkoutData`.
   - It computes `startLatLng` and `endLatLng`, but does not query `KnownLocationsDatabaseManager.getMyLocation(pos)` to resolve favorite location identity for the start or finish endpoints.
   - *Gap*: `WorkoutDataMapper` lacks a dynamic, retroactive resolver for start and destination Lieblingsorte.

3. **Workout Header UI (`WorkoutHeader.kt`)**:
   - `WorkoutHeader.kt` organizes metadata in a vertical column:
     - Top row: Sport icon and workout title.
     - Cluster badge / unclustered row.
     - Sport-specific info (sport name, equipment, commute/trainer tag).
     - Date and start time row (`ic_date_start`, `ic_time_start`).
   - *Gap*: No location metadata row or location chips exist to display recognized start and destination locations.

4. **Map Marker & Layer Rendering (`WorkoutRepository.kt`, `MapLayers.kt`, `PathPreviewMap.kt`)**:
   - In `WorkoutRepository.getWorkoutMarkers(workoutData)`:
     ```kotlin
     workoutData.startLatLng?.let {
         markerList.add(LocationMarker(it, R.drawable.control_start, application.getString(R.string.Start)))
     }
     workoutData.endLatLng?.let {
         markerList.add(LocationMarker(it, R.drawable.control_stop, application.getString(R.string.Stop)))
     }
     ```
   - In `PathPreviewMap.kt`: Start and end markers render with static icons and no title/snippet annotations.
   - *Gap*: Marker titles are static generic strings and do not incorporate recognized favorite location names.

---

## 3. User Scope Grounding (ATT-1250)

| User Role / Context | Action | Expected Outcome |
| :--- | :--- | :--- |
| **Athlete viewing Workout List / Summary** | Opens workout list or taps workout summary card. | Header displays recognized start and destination locations (e.g. 📍 Start: Zuhause, 🏁 Ziel: Büro, or 📍 Start & Ziel: Zuhause for round trips). |
| **Athlete viewing Map Details (`TrackOnMapScreen`)** | Taps map preview to expand full-screen track. | Start and destination markers display recognized favorite location names in their info windows or titles (e.g. "Start: Zuhause"). |
| **Athlete with Unrecognized Workout Endpoints** | Records a workout in an unknown area with no Lieblingsorte. | Start and destination location fields cleanly evaluate to `null`. Header does not render empty chips; map markers fall back cleanly to default "Start" and "Stop". |
| **Athlete editing a Lieblingsort Name** | Edits a favorite location name (e.g. "Home" -> "Zuhause"). | Existing and past workouts starting/ending at that location automatically reflect the new name upon reload, with zero database migrations required. |

---

## 4. Chesterton's Fence & Requirement Archaeology

### Existing Artifacts & Rules
1. **`WorkoutAutoNamingHelper.kt` (ATT-1398)**:
   - Contains established, tested location name extraction logic:
     `WorkoutAutoNamingHelper.getDisplayName(context, location: MyLocation?): String?`
   - Handles fallback to reverse-geocoded coordinates or null.
2. **`KnownLocationsDatabaseManager.java`**:
   - Provides thread-safe, geofence-based spatial matching via `getMyLocation(@NonNull LatLng latLng)`. Returns closest matching `MyLocation` within radius $r$.
3. **Preservation of Core Invariants**:
   - **Database Schema Invariant**: No changes to SQLite table schemas (`WorkoutSummaries.db`, `StartLocation2Altitude.db`). Dynamic resolution preserves complete backwards and forwards compatibility.
   - **Performance Invariant**: `getMyLocation` is a fast bounding-box/radius check executed on background IO dispatcher in `WorkoutDataMapper` / `WorkoutRepository`.
   - **100% Localization Parity**: All new labels (`workout_start_location`, `workout_destination_location`, `workout_start_and_destination`) must be localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 5. Proposed Solution Architecture & Interfaces

```
                                    +--------------------------------+
                                    |  KnownLocationsDatabaseManager |
                                    +--------------------------------+
                                                   |
                     getMyLocation(startLatLng)    |   getMyLocation(endLatLng)
                                                   v
+-----------------------+           +--------------------------------+
|  WorkoutDataMapper    | --------> |    WorkoutAutoNamingHelper     |
+-----------------------+           +--------------------------------+
           |                                       |
           | resolves startLocationName,           | getDisplayName()
           |          endLocationName              v
           v
+--------------------------------------------------------------------+
| WorkoutData & WorkoutHeaderData                                    |
| - startLocationName: String?                                       |
| - endLocationName: String?                                         |
+--------------------------------------------------------------------+
           |                                       |
           v                                       v
+-----------------------+           +--------------------------------+
| WorkoutHeader.kt      |           | TrackOnMapScreen / Markers     |
| 📍 Start: Zuhause     |           | 🟢 Marker: "Start: Zuhause"    |
| 🏁 Ziel: Büro         |           | 🔴 Marker: "Stop: Büro"        |
+-----------------------+           +--------------------------------+
```

### Proposed Domain & Presentation Modifications
1. **`WorkoutData.kt` & `WorkoutHeaderData.kt`**:
   Add optional properties with default `null`:
   - `startLocationName: String? = null`
   - `endLocationName: String? = null`
2. **`WorkoutDataMapper.kt`**:
   Resolve start and end location names using `KnownLocationsDatabaseManager.getInstance(context).getMyLocation(pos)` and `WorkoutAutoNamingHelper.getDisplayName(context, loc)`.
   Safely guard with try/catch to maintain testability without stubs.
3. **`WorkoutHeader.kt`**:
   Render location metadata below Row B (date/time) or as a dedicated chip row:
   - Point-to-Point: "📍 Start: {startLocationName}   🏁 Ziel: {endLocationName}"
   - Loop / Round Trip: "📍 Start & Ziel: {startLocationName}"
   - Single Known Endpoint: "📍 Start: {startLocationName}" or "🏁 Ziel: {endLocationName}"
4. **`WorkoutRepository.kt` & `PathPreviewMap.kt`**:
   Format `LocationMarker` titles with location names when present:
   - `"${application.getString(R.string.Start)}: $startLocationName"`
   - `"${application.getString(R.string.Stop)}: $endLocationName"`

---

## 6. Invariants & Guardrails

- **Zero Schema Migrations**: All resolutions happen dynamically at the data mapping boundary. No DB migrations required.
- **Null-Safety & Graceful Fallback**: If either endpoint is outside a known location geofence, it remains null; UI renders without errors or empty placeholders.
- **Clean Decoupling**: No dependencies on background tracking services or sensor threads.
- **100% Localization Parity**: All 9 supported locales must maintain 100% string parity verified via `TranslationParityTest`.

---

## 7. Risk Analysis & Mitigation

| Risk | Impact | Likelihood | Mitigation |
| :--- | :--- | :--- | :--- |
| **Unit Test Stub Exceptions**: `KnownLocationsDatabaseManager` instantiation throwing in mock Android tests. | Build/test failure | Low | Inject nullable `KnownLocationsDatabaseManager? = null` in `WorkoutDataMapper` constructor with lazy/try-catch fallback to `getInstance(context)`. |
| **UI Header Overflow**: Long location names causing layout wrapping or truncation on small screens. | Visual clutter | Low | Apply `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and responsive `FlowRow` or separate lines for start and destination. |
| **Overlapping Geofences**: Start or end coordinate matches multiple favorite locations. | Ambiguous name | Low | `KnownLocationsDatabaseManager.getMyLocation` already enforces distance minimization, selecting the nearest candidate. |

---

## 8. Gate 1 Readiness & Recommendations

- **Audit Readiness**: Problem domain is thoroughly understood, gaps forensic analysis complete, Chesterton's Fence archaeology observed, and solution architecture defined.
- **Recommendation**: Transition `ATT-1574` to `in_review` and execute Gate 1 audit via `tools/review_agent.py audit ATT-1574`.
