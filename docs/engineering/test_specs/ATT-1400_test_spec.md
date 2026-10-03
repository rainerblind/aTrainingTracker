# Stage 2: Requirement & Test Specification - ATT-1400: [Feature] Lieblingsorte: Display Start & Destination Locations in Workout Details and Map

**Ticket**: [ATT-1400](https://atrainingtracker.atlassian.net/browse/ATT-1400)  
**Sub-task**: [ATT-1575](https://atrainingtracker.atlassian.net/browse/ATT-1575) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-184` (*Lieblingsorte: Display Start & Destination Locations in Workout Details and Map*)  
**Test Spec ID**: `TST-UI-137`  
**Branch**: `feature/ATT-1400`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-184)

### 1.1 Problem Statement & Rationale
In `aTrainingTracker`, "Lieblingsorte" (favorite locations) provide ground-truth coordinates and geofences for barometric altimeter calibration and workout auto-naming. However, once a workout is completed and saved, the athlete cannot immediately see which favorite location their workout started at or arrived at when inspecting the workout card header in the workout summary list or the route map preview. Start and stop pins on the route map currently show only generic "Start" and "Stop" labels without spatial context. Furthermore, historical workouts recorded before a favorite location was named or edited do not reflect those locations in their summary views. Resolving start and destination favorite locations dynamically at runtime provides retroactive spatial context across all historical workouts without requiring complex database schema migrations.

### 1.2 Functional & Architectural Requirements
The system SHALL resolve, encapsulate, and visually present recognized favorite start and destination locations (*Lieblingsorte*) within workout summary headers and route map markers:

1. **Domain & Presentation Metadata Extension (`WorkoutHeaderData.kt`, `WorkoutData.kt`)**:
   - `WorkoutData` and `WorkoutHeaderData` SHALL provide nullable properties: `startLocationName: String? = null` and `endLocationName: String? = null`.
   - `WorkoutData.headerData` SHALL map `startLocationName` and `endLocationName` from `WorkoutData` directly into `WorkoutHeaderData`.

2. **Dynamic Endpoint Geofence Resolution (`WorkoutDataMapper.kt`)**:
   - In `WorkoutDataMapper.kt`, the system SHALL resolve `startLocationName` and `endLocationName` from `startLatLng` and `endLatLng` using `KnownLocationsDatabaseManager.getMyLocation(latLng)` and `WorkoutAutoNamingHelper.getDisplayName(context, location)`.
   - The resolution SHALL be retroactive across all existing and past workouts without requiring SQLite database schema migrations.
   - If an endpoint coordinate is outside all known geofences, or if coordinates are null, the corresponding location name SHALL evaluate to `null`.
   - In pure JVM unit tests where Android context mocks or unmocked SQLite singletons may be present, `WorkoutDataMapper` SHALL allow optional injection or graceful fallback for `KnownLocationsDatabaseManager`.

3. **Workout Header Presentation (`WorkoutHeader.kt`)**:
   - In `WorkoutHeader.kt`, when either `startLocationName` or `endLocationName` is non-null, the header SHALL render a dedicated location indicator row below the date/time row.
   - **Loop / Round-Trip Case**: When `startLocationName != null && startLocationName == endLocationName`, the header SHALL display: `📍 Start & Ziel: {startLocationName}` (`@string/workout_start_and_destination`).
   - **Point-to-Point Case**: When both are present and distinct, the header SHALL display: `📍 Start: {startLocationName}   🏁 Ziel: {endLocationName}` (`@string/workout_start_location`, `@string/workout_destination_location`).
   - **Single Endpoint Case**: When only one endpoint is recognized, the header SHALL display the recognized endpoint (`📍 Start: {startLocationName}` or `🏁 Ziel: {endLocationName}`).
   - Text truncation: Each location name SHALL enforce `maxLines = 1` and `overflow = TextOverflow.Ellipsis`.

4. **Route Map Pin Annotations (`WorkoutRepository.kt`, `PathPreviewMap.kt`)**:
   - In `WorkoutRepository.getWorkoutMarkers()`, start and finish `LocationMarker` titles SHALL incorporate the recognized favorite location name when present (e.g. `"${application.getString(R.string.Start)}: $startLocationName"`).
   - In `PathPreviewMap.kt`, start and end markers SHALL receive and render `startTitle` and `endTitle` in their marker states.

5. **100% Localization Parity Across 9 Locales**:
   - String resources `workout_start_location`, `workout_destination_location`, and `workout_start_and_destination` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations and matching `%s` specifiers.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Point-to-Point Favorite Locations in Header)**:
  * *Given* a workout recorded starting at known location "Zuhause" and ending at "Büro",
  * *When* the athlete views the workout summary card or header,
  * *Then* the header SHALL display "Start: Zuhause" and "Ziel: Büro".
* **Criterion 2 (Round-Trip / Loop Favorite Location in Header)**:
  * *Given* a workout recorded starting and ending at "Zuhause",
  * *When* viewing the workout header,
  * *Then* the header SHALL display "Start & Ziel: Zuhause".
* **Criterion 3 (Route Map Pin Annotations)**:
  * *Given* a workout starting at "Zuhause",
  * *When* inspecting the start marker on the route map preview or detail map,
  * *Then* the marker title SHALL display "Start: Zuhause".
* **Criterion 4 (Unrecognized Coordinates Fallback)**:
  * *Given* a workout recorded outside all known geofences,
  * *When* viewing the workout header and route map,
  * *Then* no empty location row SHALL be rendered in the header and map markers SHALL fall back cleanly to default "Start" and "Stop".
* **Criterion 5 (Retroactive Resolution Invariant)**:
  * *Given* past workouts recorded prior to creating a new favorite location,
  * *When* the athlete creates a favorite location covering the start coordinates of those past workouts,
  * *Then* the past workouts SHALL immediately reflect the new location name upon reload without requiring database migration or data re-recording.

### 1.4 System Invariants
* SQLite schemas (`WorkoutSummaries.db`, `StartLocation2Altitude.db`) remain 100% unchanged.
* `WorkoutHeader` click, long-press, and overflow menu behaviors remain completely intact.
* Dynamic resolution executes efficiently in `WorkoutDataMapper` using existing spatial indexing.
* 9-language localization parity maintained across all supported locales.

---

## 2. Test Specification (TST-UI-137)

### Test Case 1: Dynamic Location Resolution (`TST-UI-137.1`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/workoutsummaries/WorkoutDataMapperLocationTest.kt`
* **Preconditions**: Mocked `KnownLocationsDatabaseManager` providing known locations: "Zuhause" at (48.137, 11.576, r=200m) and "Büro" at (48.140, 11.580, r=200m).
* **Actions & Assertions**:
  1. Map workout with start at (48.137, 11.576) and stop at (48.140, 11.580) -> asserts `startLocationName == "Zuhause"`, `endLocationName == "Büro"`.
  2. Map workout with start and stop both at (48.137, 11.576) -> asserts `startLocationName == "Zuhause"`, `endLocationName == "Zuhause"`.
  3. Map workout with start at (48.137, 11.576) and stop at (49.000, 12.000) (unrecognized) -> asserts `startLocationName == "Zuhause"`, `endLocationName == null`.
  4. Map workout with both coordinates null or outside geofences -> asserts `startLocationName == null`, `endLocationName == null`.
  5. Verify `WorkoutData.headerData` maps `startLocationName` and `endLocationName` directly to `WorkoutHeaderData`.

### Test Case 2: WorkoutHeader Composable Location Presentation (`TST-UI-137.2`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/workoutsummaries/WorkoutHeaderLocationTest.kt`
* **Preconditions**: `WorkoutHeaderData` instances representing various location combinations.
* **Actions & Assertions**:
  1. Round-trip (`startLocationName == endLocationName == "Zuhause"`): asserts header renders text matching `@string/workout_start_and_destination` ("Start & Ziel: Zuhause").
  2. Point-to-point (`startLocationName == "Zuhause"`, `endLocationName == "Büro"`): asserts header renders both "Start: Zuhause" and "Ziel: Büro".
  3. Single endpoint: asserts header renders only the non-null endpoint.
  4. Neither endpoint: asserts location indicator row is completely omitted.

### Test Case 3: Route Map Marker Pin Annotations (`TST-UI-137.3`)
* **Scope**: Repository / Map Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/repository/WorkoutRepositoryMarkerTest.kt`
* **Preconditions**: Workout with known start location "Zuhause" and end location "Büro".
* **Actions & Assertions**:
  1. Verify `WorkoutRepository.getWorkoutMarkers()` emits start marker with title containing "Start: Zuhause".
  2. Verify finish marker contains "Stop: Büro".
  3. Verify that when locations are null, titles default to standard localized "Start" and "Stop".

### Test Case 4: 9-Language Localization & Format Specifier Audit (`TST-UI-137.4`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Goal**: Verify string presence and matching `%s` tokens across all 9 locales:
  * `workout_start_location`: "Start: %s"
  * `workout_destination_location`: "Ziel: %s" / "Destination: %s"
  * `workout_start_and_destination`: "Start & Ziel: %s" / "Start & Destination: %s"
  * Locales: EN, DE, ES, FR, IT, JA, NL, PL, PT.
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 5: Clean-Room Full Suite Regression Execution (`TST-UI-137.5`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite (880+ tests) with 0 regressions.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-137.1` | Unit | `WorkoutDataMapper.toWorkoutData` | `REQ-UI-184` | Specified |
| `TST-UI-137.2` | Unit / UI | `WorkoutHeader` composable | `REQ-UI-184` | Specified |
| `TST-UI-137.3` | Repository / Map | `WorkoutRepository.getWorkoutMarkers`, `PathPreviewMap` | `REQ-UI-184` | Specified |
| `TST-UI-137.4` | Localization | `TranslationParityTest` | `REQ-UI-184`, `REQ-UI-106` | Specified |
| `TST-UI-137.5` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
