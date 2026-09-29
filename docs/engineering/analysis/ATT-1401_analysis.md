# Stage 1 Analysis: ATT-1401 - Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location

**Ticket**: [ATT-1401](https://atrainingtracker.atlassian.net/browse/ATT-1401)  
**Sub-task**: [ATT-1580](https://atrainingtracker.atlassian.net/browse/ATT-1580) (`[Analysis]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Branch**: `feature/ATT-1401`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & Motivation

In `aTrainingTracker`, "Lieblingsorte" (Known Start Locations) serve as high-precision spatial anchors for barometric altimeter calibration and workout auto-naming. In the management screen (`KnownLocationsScreen.kt`), each location card prominently showcases the reference altitude alongside a recorded usage hit count badge (e.g., "45 Starts").

### Current Deficiency
Despite indicating how many times an athlete has initiated a workout from a specific Lieblingsort, the management card currently represents a **navigational dead end**:
1. **No Journal Drill-Down**: Tapping the location card opens the edit dialog (`EditKnownLocationDialog`), while long-pressing opens the delete context menu. There is no affordance for an athlete to inspect *which* historical workouts originated at that specific location.
2. **Missing Spatial Filter Dimension**: The workout list filtering engine (`WorkoutFilterCriteria.kt`) supports free-text search, calendar temporal intervals (year, month, timestamp range), sport subtypes, gear assignments, and duration/distance intervals, but lacks any spatial/geofence filtering dimension based on workout starting coordinates.
3. **Disconnected Value Pillar**: One of the core pillars of Epic `ATT-1396` (*Interactive Training Journaling*) requires enabling 1-tap filtering from any Lieblingsort directly into the workout history. Without this capability, athletes cannot easily review their historical performance from their primary starting hubs (e.g., "Zuhause", "Büro", "Ferienhaus").

---

## 2. Root Cause Analysis (Forensic Investigation & Gap Analysis)

### Call-Site & Architecture Investigation

1. **Filtering Domain Model (`WorkoutFilterCriteria.kt`)**:
   - `WorkoutFilterCriteria` is an `@Immutable` data class managing all active filter dimensions.
   - It implements `matches(workout: WorkoutData): Boolean` to evaluate sessions against active predicates.
   - It serializes to/from JSON (`toJson()`, `fromJson()`) for preference persistence via DataStore (`MyPreferenceManager.kt`).
   - *Gap*: It does not contain fields for starting location geofence filtering (`startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`). When matching workouts, it does not evaluate `workout.startLatLng` against a spatial target.

2. **Workout Summaries ViewModel (`WorkoutSummariesViewModel.kt`)**:
   - Manages `_filterCriteria: MutableStateFlow<WorkoutFilterCriteria>`.
   - Combines `workoutList` and `_filterCriteria` via `filter { criteria.matches(it) }`.
   - Offers `setFilterCriteria(criteria)` and `clearFilterCriteria()`.
   - *Observation*: The filtering pipeline in `WorkoutSummariesViewModel` is completely reactive and decoupled. Any addition to `WorkoutFilterCriteria.matches()` automatically works across all workout lists.

3. **Active Filter Feedback (`ActiveFilterChipsRow.kt`)**:
   - Composable displaying removable chips for every active filter criterion (`query`, `year`, `month`, `dateRange`, `sport`, `equipment`, `commute`, `trainer`, `hasGps`, `distanceRange`, `durationRange`).
   - *Gap*: Lacks a chip representation for active start location filters (e.g. `📍 Zuhause`), preventing athletes from seeing and dismissing a spatial filter directly from the header.

4. **Lieblingsorte Management UI (`KnownLocationsScreen.kt`)**:
   - In `KnownLocationCard`, the starts count is rendered as static text:
     ```kotlin
     Text(
         text = pluralStringResource(R.plurals.known_locations_starts, item.hitCount, item.hitCount),
         style = MaterialTheme.typography.bodyMedium,
         fontWeight = FontWeight.Bold,
         color = MaterialTheme.colorScheme.primary
     )
     ```
   - Card click is hardcoded to `onEdit(item)`.
   - Long-press click opens the universal delete-only context menu.
   - *Gap*: No interaction or button exists to trigger drill-down navigation to filtered workouts.

5. **Top-Level Navigation (`ATrainingTrackerApp.kt`, `MainActivityWithNavigation.kt`)**:
   - `ATrainingTrackerApp.kt` hosts `KnownLocationsScreen` at `NavRoutes.START_LOCATIONS`.
   - `MainActivityWithNavigation.kt` demonstrates established navigation to filtered workouts via `navigateToFilteredWorkouts(stats)` and `startWorkoutSummaryListFromPeriod(...)` which inject criteria into `WorkoutSummariesViewModel` and navigate to `R.id.drawer_workouts` / `NavRoutes.WORKOUTS`.
   - *Gap*: `KnownLocationsScreen` does not receive or expose an `onShowWorkouts: (KnownLocationItem) -> Unit` callback, and `NavRoutes.START_LOCATIONS` has no navigation link to `NavRoutes.WORKOUTS`.

---

## 3. User Scope Grounding (ATT-1250)

| User Role / Context | Action | Expected Outcome |
| :--- | :--- | :--- |
| **Athlete on KnownLocationsScreen** | Taps on the interactive "Starts" chip/button on a favorite location card (e.g. "45 Starts"). | Navigates immediately to the Workouts list (`NavRoutes.WORKOUTS`) pre-filtered to all workouts that started within the geofence radius of that location. |
| **Athlete viewing Filtered Workouts** | Inspects workout list after drill-down navigation. | Only workouts with `startLatLng` within the Lieblingsort geofence radius are displayed. An active filter chip `📍 <Location Name>` is visible in `ActiveFilterChipsRow`. |
| **Athlete dismissing Start Location Filter** | Taps the `(x)` on the `📍 <Location Name>` filter chip. | The spatial filter is cleared and the workout list immediately restores the unfiltered (or remaining) workout sessions. |
| **Athlete on Location Card with 0 Starts** | Views a newly created favorite location with 0 recorded starts. | Tapping navigates to the workout list showing an empty state with clear indication that 0 workouts match the starting location geofence. |
| **Athlete editing Location Metadata** | Taps the body of the location card. | Preserves existing behavior: opens `EditKnownLocationDialog` for modifying altitude, name, or radius without conflict. |

---

## 4. Chesterton's Fence & Requirement Archaeology

### Existing Artifacts & Invariants
1. **`WorkoutFilterCriteria.kt` (ATT-1263 / REQ-UI-145)**:
   - Established the multi-dimensional immutable criteria model and high-speed predicate evaluation.
   - Must preserve all existing filter dimensions, short-circuit evaluation logic, JSON serialization resilience, and zero database schema changes.
2. **`WorkoutClusterEngine.distanceBetween(p1, p2)`**:
   - Provides safe, robust geodetic distance calculation (meters) with automatic fallback to Haversine formula during pure JVM unit test execution where `android.location.Location.distanceBetween` is unmocked.
3. **`KnownLocationsScreen.kt` (ATT-1523 / REQ-UI-165, REQ-UI-061)**:
   - Enforces universal delete-only long-press context menu and clean single-perspective card layout.
   - Primary card body click opens `EditKnownLocationDialog`. To preserve this established interaction invariant, the drill-down trigger must be clearly differentiated (e.g., an interactive tonal surface / button for the starts badge, or explicit trailing button `Icons.AutoMirrored.Filled.ArrowForward`).
4. **Preservation of Core Invariants**:
   - **Database Schema Invariant**: Zero changes to SQLite table schemas (`WorkoutSummaries.db`, `StartLocation2Altitude.db`). Dynamic spatial filtering against `startLatLng` requires no schema migrations.
   - **100% Localization Parity**: All new labels and accessibility content descriptions (`filter_start_location`, `view_matching_workouts`, `known_locations_view_workouts`) must be defined across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 5. Proposed Solution Architecture & Interfaces

```
+-------------------------------------------------------------------------+
|                        KnownLocationsScreen                             |
|  +-------------------------------------------------------------------+  |
|  | KnownLocationCard: "Zuhause"                                      |  |
|  | [Ascent: 520 m]  [📍 45 Starts ->] (Tonal clickable badge/button) |  |
|  +-------------------------------------------------------------------+  |
+-------------------------------------------------------------------------+
                                    |
                    onShowWorkouts(locationItem)
                                    v
+-------------------------------------------------------------------------+
|                         ATrainingTrackerApp                             |
|  1. constructs WorkoutFilterCriteria(                                   |
|       startLocationName = item.name,                                    |
|       startLocationLat = item.latitude,                                 |
|       startLocationLng = item.longitude,                                |
|       startLocationRadiusM = item.radius.toDouble()                     |
|     )                                                                   |
|  2. summariesViewModel.setFilterCriteria(criteria)                      |
|  3. navController.navigate(NavRoutes.WORKOUTS)                          |
+-------------------------------------------------------------------------+
                                    |
                                    v
+-------------------------------------------------------------------------+
|                    WorkoutSummariesTabbedScreen                         |
|  - ActiveFilterChipsRow: [📍 Zuhause (x)]                               |
|  - Filtered workout list matching startLatLng within geofence radius    |
+-------------------------------------------------------------------------+
```

### Key Technical Specifications

1. **`WorkoutFilterCriteria` Extension**:
   ```kotlin
   data class WorkoutFilterCriteria(
       ...
       val startLocationName: String? = null,
       val startLocationLat: Double? = null,
       val startLocationLng: Double? = null,
       val startLocationRadiusM: Double? = null
   )
   ```
   - In `activeFilterCount`: increment if `startLocationLat != null && startLocationLng != null`.
   - In `matches(workout: WorkoutData)`:
     ```kotlin
     if (startLocationLat != null && startLocationLng != null) {
         val start = workout.startLatLng ?: return false
         val radius = startLocationRadiusM ?: 200.0
         val dist = WorkoutClusterEngine.distanceBetween(start, LatLng(startLocationLat, startLocationLng))
         if (dist > radius) return false
     }
     ```
   - In `toJson()` and `fromJson()`: serialize/deserialize the 4 fields safely.

2. **`ActiveFilterChipsRow.kt` Integration**:
   - Add parameter `onRemoveStartLocation: () -> Unit = {}`.
   - If `criteria.startLocationName != null || (criteria.startLocationLat != null && criteria.startLocationLng != null)`:
     - Render `RemovableFilterChip(label = "📍 ${criteria.startLocationName ?: ...}", onRemove = onRemoveStartLocation)`.

3. **`KnownLocationsScreen.kt` Interaction**:
   - Expose `onShowWorkouts: (KnownLocationItem) -> Unit` in `KnownLocationsScreen` and `KnownLocationCard`.
   - Design an interactive, touch-friendly badge for the starts count (e.g. `FilterChip` / `Surface` with `primaryContainer` tonal background, location icon, and subtle forward chevron `ArrowForwardIos` / `ArrowForward`).
   - Tapping this badge invokes `onShowWorkouts(item)`.

4. **100% 9-Language Localization**:
   - Add localized tokens across all 9 languages (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
     - `filter_start_location`: "Startort" / "Start Location"
     - `known_locations_view_workouts`: "Workouts anzeigen" / "View Workouts"

---

## 6. Risk Rating & Mitigation

* **Technical Risk**: **LOW**
* **Justification**:
  - The feature is purely additive. It extends the immutable domain filter criteria without modifying any SQLite schemas, background services, or sensor processing pipelines.
  - Geodetic distance calculations reuse the existing, battle-tested `WorkoutClusterEngine.distanceBetween` method with built-in JVM fallback for clean unit testing.
  - The interaction model on `KnownLocationCard` preserves existing click (`onEdit`) and long-press (`onDelete`) behaviors while providing a distinct, explicit touch target for workout drill-down.

---

## 7. Gate 1 Recommendation
**RECOMMEND PASS** for proceeding to Stage 2 (Requirement & Test Specification).
