# Stage 2: Requirement & Test Specification - ATT-1401: [Feature] Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location

**Ticket**: [ATT-1401](https://atrainingtracker.atlassian.net/browse/ATT-1401)  
**Sub-task**: [ATT-1581](https://atrainingtracker.atlassian.net/browse/ATT-1581) (`[Test-Spec]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*)  
**Test Spec ID**: `TST-UI-138`  
**Branch**: `feature/ATT-1401`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Requirement Specification (REQ-UI-185)

### 1.1 Problem Statement & Rationale
In `aTrainingTracker`, "Lieblingsorte" (favorite start locations) serve as central hubs for athletic sessions. The management screen (`KnownLocationsScreen`) lists each favorite location along with a recorded start hit count (e.g. "45 Starts"). However, the card is currently a navigational dead end: tapping opens `EditKnownLocationDialog` and long-pressing opens the delete menu, but an athlete cannot view or drill down into the historical workouts that originated at that location. Furthermore, `WorkoutFilterCriteria` lacks a spatial geofence filter predicate to query workouts by starting coordinates. Extending `WorkoutFilterCriteria` with a geofence predicate and providing a dedicated drill-down affordance on the location card connects favorite locations to the training journal seamlessly without database migrations.

### 1.2 Functional & Architectural Requirements
The system SHALL provide spatial geofence filtering in `WorkoutFilterCriteria` and an interactive drill-down navigation flow from `KnownLocationsScreen` to the filtered workout list:

1. **Spatial Filter Criteria Extension (`WorkoutFilterCriteria.kt`)**:
   - `WorkoutFilterCriteria` SHALL define four nullable properties:
     `val startLocationName: String? = null`
     `val startLocationLat: Double? = null`
     `val startLocationLng: Double? = null`
     `val startLocationRadiusM: Double? = null`
   - **Predicate Evaluation (`matches`)**: When `startLocationLat != null && startLocationLng != null`, the system SHALL evaluate the workout's start coordinate (`workout.startLatLng`).
     - If `workout.startLatLng == null`, `matches` SHALL return `false`.
     - If geodetic distance between `workout.startLatLng` and `(startLocationLat, startLocationLng)` exceeds `startLocationRadiusM ?: 200.0`, `matches` SHALL return `false`.
     - Distance calculation SHALL utilize `WorkoutClusterEngine.distanceBetween(p1, p2)`, ensuring robust execution in both Android runtime and pure JVM test environments.
   - **Active Filter Counter**: `activeFilterCount` SHALL increment by 1 when `startLocationLat != null && startLocationLng != null`.
   - **JSON Serialization Parity**: `toJson()` and `fromJson()` SHALL serialize and deserialize `startLocationName`, `startLocationLat`, `startLocationLng`, and `startLocationRadiusM` losslessly for DataStore preference persistence.

2. **Active Filter Chip Presentation (`ActiveFilterChipsRow.kt`)**:
   - `ActiveFilterChipsRow` SHALL display an active filter chip when start location filtering is active (`criteria.startLocationName != null || (criteria.startLocationLat != null && criteria.startLocationLng != null)`).
   - The chip label SHALL format as `"📍 ${criteria.startLocationName ?: stringResource(R.string.filter_start_location)}"`.
   - Tapping the chip's dismiss action SHALL invoke `onRemoveStartLocation: () -> Unit`, clearing the spatial filter dimensions while preserving all other active criteria.

3. **Lieblingsorte Card Drill-Down Affordance (`KnownLocationsScreen.kt`)**:
   - `KnownLocationsScreen` and `KnownLocationCard` SHALL expose a navigation callback:
     `onShowWorkouts: (KnownLocationItem) -> Unit`.
   - In `KnownLocationCard`, the starts count indicator SHALL be rendered as an interactive, accessible touch target (tonal surface/chip with location icon and forward indicator) with minimum touch bounding box of $48\times 48\text{dp}$.
   - Tapping the starts badge SHALL trigger `onShowWorkouts(item)`.
   - Invariant: Single-tap on the card body (`onClick = onEdit`) SHALL continue to launch `EditKnownLocationDialog`, and long-press (`onLongClick`) SHALL strictly preserve the universal delete-only context menu (`REQ-UI-061`).

4. **App Navigation Wiring (`ATrainingTrackerApp.kt`)**:
   - In `ATrainingTrackerApp.kt`, within the `NavRoutes.START_LOCATIONS` composable route, the `onShowWorkouts` callback SHALL be wired to:
     1. Construct `WorkoutFilterCriteria` with `startLocationName = item.name`, `startLocationLat = item.latitude`, `startLocationLng = item.longitude`, and `startLocationRadiusM = item.radius.toDouble().takeIf { it > 0 } ?: 200.0`.
     2. Update `WorkoutSummariesViewModel.setFilterCriteria(criteria)`.
     3. Navigate to `NavRoutes.WORKOUTS`.

5. **100% Localization Parity Across 9 Locales**:
   - String resources `filter_start_location` and `known_locations_view_workouts` SHALL be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with 0 missing translations.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (1-Tap Drill-Down Navigation)**:
  * *Given* an athlete on `KnownLocationsScreen` viewing location "Zuhause",
  * *When* the athlete taps the starts badge on the card,
  * *Then* the app SHALL navigate to `NavRoutes.WORKOUTS` displaying only workouts that started within the geofence radius of "Zuhause".
* **Criterion 2 (Spatial Filtering Accuracy)**:
  * *Given* workouts starting at "Zuhause" (distance $\le 200\text{m}$) and workouts starting at "Büro" (distance $> 200\text{m}$),
  * *When* the "Zuhause" filter is active,
  * *Then* only the "Zuhause" workouts SHALL be visible in the workout list.
* **Criterion 3 (Active Filter Chip & Removal)**:
  * *Given* the workout list filtered by starting location "Zuhause",
  * *When* viewing the top of the workout list,
  * *Then* an active chip "📍 Zuhause" SHALL be displayed in `ActiveFilterChipsRow`, and tapping its remove icon SHALL clear the start location filter and restore all sessions.
* **Criterion 4 (Card Interaction Invariant)**:
  * *Given* an athlete on `KnownLocationsScreen`,
  * *When* tapping the card body outside the starts badge,
  * *Then* `EditKnownLocationDialog` SHALL open as before.
  * *When* long-pressing the card,
  * *Then* the universal delete-only context menu SHALL appear as before.

### 1.4 System Invariants
* SQLite schemas (`WorkoutSummaries.db`, `StartLocation2Altitude.db`) remain 100% unchanged.
* Existing filter dimensions (`query`, `year`, `month`, `sportTypeId`, `equipmentId`, `commute`, `trainer`, `hasGpsTrack`, distance/duration ranges) remain fully functional.
* Geodetic calculations utilize `WorkoutClusterEngine.distanceBetween` with built-in JVM fallback.
* 100% 9-language localization parity maintained across all supported locales.

---

## 2. Test Specification (TST-UI-138)

### Test Case 1: Spatial Filter Criteria Unit Tests (`TST-UI-138.1`)
* **Scope**: JVM Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaSpatialTest.kt`
* **Preconditions**: Sample `WorkoutData` instances with known start coordinates.
* **Actions & Assertions**:
  1. Test workout with `startLatLng` at (48.137, 11.576) matches filter with center (48.137, 11.576) and radius 200m -> asserts `true`.
  2. Test workout with `startLatLng` at (48.138, 11.576) (~111m away) matches radius 200m -> asserts `true`.
  3. Test workout with `startLatLng` at (48.145, 11.576) (~890m away) matches radius 200m -> asserts `false`.
  4. Test workout with `startLatLng = null` against spatial filter -> asserts `false`.
  5. Test `activeFilterCount` includes spatial filter dimension.
  6. Test JSON serialization and deserialization preserves all 4 spatial fields.

### Test Case 2: Active Filter Chips Row Unit Tests (`TST-UI-138.2`)
* **Scope**: Composable Unit Test / Roborazzi
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowLocationTest.kt`
* **Preconditions**: `WorkoutFilterCriteria` with `startLocationName = "Zuhause"`, `startLocationLat = 48.137`, `startLocationLng = 11.576`.
* **Actions & Assertions**:
  1. Verify active chip with label `"📍 Zuhause"` is composed.
  2. Verify tapping remove invokes `onRemoveStartLocation` callback.

### Test Case 3: KnownLocationsScreen Card Interaction Unit Tests (`TST-UI-138.3`)
* **Scope**: Composable Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenDrillDownTest.kt`
* **Preconditions**: Rendered `KnownLocationCard` with `hitCount = 45`.
* **Actions & Assertions**:
  1. Tapping the starts badge invokes `onShowWorkouts(item)`.
  2. Tapping the card body invokes `onEdit(item)`.
  3. Long-pressing the card opens the delete context menu.

### Test Case 4: 9-Language Localization Audit (`TST-UI-138.4`)
* **Scope**: Static Resource Audit
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/TranslationParityTest.kt`
* **Actions & Assertions**:
  1. Verify `filter_start_location` exists across all 9 locales.
  2. Verify `known_locations_view_workouts` exists across all 9 locales.

### Test Case 5: Clean-Room Full Suite Regression (`TST-UI-138.5`)
* **Scope**: Clean-room full suite regression
* **Command**: `./gradlew testDebugUnitTest`
* **Assertions**: 100% pass rate, 0 regressions.

---

## 3. Traceability Matrix

| Requirement ID | Requirement Description | Test Case ID | Test Case Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| **REQ-UI-185.1** | Spatial Filter Predicate & Serialization | `TST-UI-138.1` | Unit Test (`WorkoutFilterCriteriaSpatialTest`) | Specified |
| **REQ-UI-185.2** | Active Filter Chip Presentation & Removal | `TST-UI-138.2` | Composable Test (`ActiveFilterChipsRowLocationTest`) | Specified |
| **REQ-UI-185.3** | Lieblingsorte Card Drill-Down Affordance | `TST-UI-138.3` | Composable Test (`KnownLocationsScreenDrillDownTest`) | Specified |
| **REQ-UI-185.4** | Navigation Wiring in ATrainingTrackerApp | `TST-UI-138.3` | Integration / UI | Specified |
| **REQ-UI-185.5** | 100% 9-Language Localization Parity | `TST-UI-138.4` | Resource Test (`TranslationParityTest`) | Specified |
| **REQ-UI-185.6** | Full Suite Zero Regressions | `TST-UI-138.5` | Regression (`testDebugUnitTest`) | Specified |
