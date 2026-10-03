# Stage 3: Implementation Plan - ATT-1401: [Feature] Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location

**Ticket**: [ATT-1401](https://atrainingtracker.atlassian.net/browse/ATT-1401)  
**Sub-task**: [ATT-1582](https://atrainingtracker.atlassian.net/browse/ATT-1582) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*)  
**Test Mapping**: `TST-UI-138` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location Verification*)  
**Branch**: `feature/ATT-1401`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In `aTrainingTracker`, "Lieblingsorte" (Known Start Locations) serve as high-precision spatial anchors for barometric altimeter calibration and workout auto-naming. In the management screen (`KnownLocationsScreen.kt`), each location card prominently showcases the reference altitude alongside a recorded usage hit count badge (e.g., "45 Starts").

### Current Deficiency
Despite indicating how many times an athlete has initiated a workout from a specific Lieblingsort, the management card currently represents a **navigational dead end**:
1. **No Journal Drill-Down**: Tapping the location card opens the edit dialog (`EditKnownLocationDialog`), while long-pressing opens the delete context menu. There is no affordance for an athlete to inspect *which* historical workouts originated at that specific location.
2. **Missing Spatial Filter Dimension**: The workout list filtering engine (`WorkoutFilterCriteria.kt`) supports free-text search, calendar temporal intervals (year, month, timestamp range), sport subtypes, gear assignments, and duration/distance intervals, but lacks any spatial/geofence filtering dimension based on workout starting coordinates.
3. **Disconnected Value Pillar**: One of the core pillars of Epic `ATT-1396` (*Interactive Training Journaling*) requires enabling 1-tap filtering from any Lieblingsort directly into the workout history. Without this capability, athletes cannot easily review their historical performance from their primary starting hubs (e.g., "Zuhause", "Büro", "Ferienhaus").

By extending `WorkoutFilterCriteria` with spatial geofence matching (`startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`), wiring `ActiveFilterChipsRow` to display and dismiss the active location chip, and transforming the starts badge in `KnownLocationCard` into an accessible, interactive touch target triggering drill-down navigation, this feature seamlessly connects favorite locations to the training journal with **zero SQLite schema migrations**.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*)
* **Test Mapping**: `TST-UI-138` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location Verification*)
  * `TST-UI-138.1`: Spatial Filter Criteria & Predicate Matching Unit Tests (`WorkoutFilterCriteriaSpatialTest.kt`)
  * `TST-UI-138.2`: Active Filter Chip Presentation & Removal Composable Tests (`ActiveFilterChipsRowLocationTest.kt`)
  * `TST-UI-138.3`: Lieblingsorte Card Interaction & Drill-Down Unit Tests (`KnownLocationsScreenDrillDownTest.kt`)
  * `TST-UI-138.4`: 9-Language Localization & Format Specifier Audit (`TranslationParityTest.kt`)
  * `TST-UI-138.5`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Schema Migrations**: `WorkoutSummaries.db` and `StartLocation2Altitude.db` SQLite schemas remain 100% unchanged. Filtering operates dynamically in-memory against `workout.startLatLng` using geodetic distance calculations.
2. **Preservation of Existing Filter Dimensions**: All existing filter dimensions in `WorkoutFilterCriteria` (`query`, `year`, `month`, `startDateS`, `endDateS`, `sportTypeId`, `equipmentId`, `isCommute`, `isTrainer`, `hasGpsTrack`, `minDistanceMeters`, `maxDistanceMeters`, `minDurationSec`, `maxDurationSec`) remain completely intact.
3. **Card Interaction Invariants**:
   - Single-tap on card body (`onClick = onEdit`) must continue opening `EditKnownLocationDialog`.
   - Long-press (`onLongClick`) must strictly preserve the universal delete-only context menu (`REQ-UI-061`).
   - The starts badge is an independent interactive touch target with minimum $48\times 48\text{dp}$ touch bounding box that triggers `onShowWorkouts(item)`.
4. **Pure JVM Test Environment Safety**: Geodetic distance calculations must use `WorkoutClusterEngine.distanceBetween(p1, p2)`, which features an automatic pure Java Haversine fallback when `android.location.Location.distanceBetween` throws `RuntimeException` during JVM unit tests.
5. **Lossless JSON Persistence**: `toJson()` and `fromJson()` must serialize and deserialize the spatial fields without data loss or exceptions when fields are absent in legacy stored JSON payloads.
6. **100% 9-Language Localization Parity**: All newly introduced string tokens (`filter_start_location`, `known_locations_view_workouts`) are localized across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.
7. **Subtask Direct Completion**: Subtask `ATT-1582` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
8. **Parent Human Gate Invariance**: Terminal completion of parent `ATT-1401` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        KnownLocationsScreen                            │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ KnownLocationCard: "Zuhause"                                     │  │
│  │ [Ascent: 520 m]  [📍 45 Starts ->] (Interactive Surface Badge)   │  │
│  └──────────────────────────────────┬───────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────┘
                                      │ onShowWorkouts(locationItem)
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                    ATrainingTrackerApp (Nav Host)                      │
│                                                                        │
│  1. Construct criteria:                                                │
│     WorkoutFilterCriteria(                                             │
│         startLocationName = item.name,                                 │
│         startLocationLat = item.latLng.latitude,                       │
│         startLocationLng = item.latLng.longitude,                      │
│         startLocationRadiusM = item.radius.toDouble()                  │
│     )                                                                  │
│  2. summariesViewModel.setFilterCriteria(criteria)                     │
│  3. navController.navigate(NavRoutes.WORKOUTS)                         │
└─────────────────────────────────────┬──────────────────────────────────┘
                                      │ updates filterStateFlow
                                      ▼
┌────────────────────────────────────────────────────────────────────────┐
│                     WorkoutSummariesTabbedScreen                       │
│                                                                        │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ ActiveFilterChipsRow: [📍 Zuhause (x)]                           │  │
│  └──────────────────────────────────────────────────────────────────┘  │
│  ┌──────────────────────────────────────────────────────────────────┐  │
│  │ Filtered Workout List (matches workout.startLatLng within 200m)  │  │
│  └──────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────┘
```

### Component 1: Spatial Filter Criteria Extension (`WorkoutFilterCriteria.kt`)
* Add four nullable spatial fields to `WorkoutFilterCriteria`:
  ```kotlin
  val startLocationName: String? = null,
  val startLocationLat: Double? = null,
  val startLocationLng: Double? = null,
  val startLocationRadiusM: Double? = null
  ```
* In `activeFilterCount`:
  ```kotlin
  if (startLocationLat != null && startLocationLng != null) count++
  ```
* In `matches(workout: WorkoutData): Boolean`:
  ```kotlin
  if (startLocationLat != null && startLocationLng != null) {
      val start = workout.startLatLng ?: return false
      val radius = startLocationRadiusM ?: 200.0
      val dist = WorkoutClusterEngine.distanceBetween(start, LatLng(startLocationLat, startLocationLng))
      if (dist > radius) {
          return false
      }
  }
  ```
* In `toJson()`:
  ```kotlin
  startLocationName?.let { json.put("startLocationName", it) }
  startLocationLat?.let { json.put("startLocationLat", it) }
  startLocationLng?.let { json.put("startLocationLng", it) }
  startLocationRadiusM?.let { json.put("startLocationRadiusM", it) }
  ```
* In `fromJson()`:
  ```kotlin
  startLocationName = if (json.has("startLocationName")) json.optString("startLocationName") else null,
  startLocationLat = if (json.has("startLocationLat")) json.optDouble("startLocationLat") else null,
  startLocationLng = if (json.has("startLocationLng")) json.optDouble("startLocationLng") else null,
  startLocationRadiusM = if (json.has("startLocationRadiusM")) json.optDouble("startLocationRadiusM") else null
  ```

### Component 2: Active Filter Chips Presentation (`ActiveFilterChipsRow.kt`, `WorkoutTabsScreen.kt`)
* In `ActiveFilterChipsRow.kt`:
  * Add parameter: `onRemoveStartLocation: () -> Unit = {}`.
  * Render removable chip when start location filter is active:
    ```kotlin
    if (criteria.startLocationLat != null && criteria.startLocationLng != null || !criteria.startLocationName.isNullOrBlank()) {
        item("startLocation") {
            val label = if (!criteria.startLocationName.isNullOrBlank()) {
                "📍 ${criteria.startLocationName}"
            } else {
                "📍 ${stringResource(R.string.filter_start_location)}"
            }
            RemovableFilterChip(
                label = label,
                onRemove = onRemoveStartLocation
            )
        }
    }
    ```
* In `WorkoutTabsScreen.kt`:
  * Pass `onRemoveStartLocation`:
    ```kotlin
    onRemoveStartLocation = {
        onUpdateFilterCriteria {
            it.copy(
                startLocationName = null,
                startLocationLat = null,
                startLocationLng = null,
                startLocationRadiusM = null
            )
        }
    }
    ```

### Component 3: Lieblingsorte Management UI (`KnownLocationsScreen.kt`)
* In `KnownLocationsScreen`:
  * Add parameter: `onShowWorkouts: (KnownLocationItem) -> Unit = {}`.
* In `KnownLocationsListContent`:
  * Pass `onShowWorkouts: (KnownLocationItem) -> Unit = {}`.
* In `KnownLocationCard`:
  * Add parameter: `onShowWorkouts: () -> Unit = {}`.
  * Transform static starts text into an accessible, interactive touch target (`Surface`) with minimum $48\times 48\text{dp}$ touch bounding box, tonal styling, location icon, and forward chevron:
    ```kotlin
    Surface(
        onClick = onShowWorkouts,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("location_starts_badge_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Place,
                contentDescription = stringResource(R.string.known_locations_view_workouts),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = pluralStringResource(R.plurals.known_locations_starts, item.hitCount, item.hitCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
    ```

### Component 4: App Navigation Host Wiring (`ATrainingTrackerApp.kt`)
* In `composable(NavRoutes.START_LOCATIONS)`:
  * Obtain `summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)`.
  * Wire `onShowWorkouts`:
    ```kotlin
    onShowWorkouts = { locationItem ->
        val criteria = WorkoutFilterCriteria(
            startLocationName = locationItem.name,
            startLocationLat = locationItem.latLng.latitude,
            startLocationLng = locationItem.latLng.longitude,
            startLocationRadiusM = locationItem.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
        )
        summariesViewModel.setFilterCriteria(criteria)
        navController.navigate(NavRoutes.WORKOUTS)
    }
    ```

### Component 5: 100% 9-Language Localization Parity (`strings.xml`)
* Define string resources across all 9 supported locales:
  * `filter_start_location`:
    - `values`: "Start Location"
    - `values-de`: "Startort"
    - `values-es`: "Lugar de inicio"
    - `values-fr`: "Lieu de départ"
    - `values-it`: "Luogo di partenza"
    - `values-ja`: "開始地点"
    - `values-nl`: "Startlocatie"
    - `values-pl`: "Miejsce startu"
    - `values-pt`: "Local de início"
  * `known_locations_view_workouts`:
    - `values`: "View Workouts"
    - `values-de`: "Workouts anzeigen"
    - `values-es`: "Ver entrenamientos"
    - `values-fr`: "Voir les entraînements"
    - `values-it`: "Visualizza allenamenti"
    - `values-ja`: "ワークアウトを表示"
    - `values-nl`: "Workouts bekijken"
    - `values-pl`: "Pokaż treningi"
    - `values-pt`: "Ver treinos"

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language String Resources Definition
* Files:
  * `app/src/main/res/values/strings.xml`
  * `app/src/main/res/values-de/strings.xml`
  * `app/src/main/res/values-es/strings.xml`
  * `app/src/main/res/values-fr/strings.xml`
  * `app/src/main/res/values-it/strings.xml`
  * `app/src/main/res/values-ja/strings.xml`
  * `app/src/main/res/values-nl/strings.xml`
  * `app/src/main/res/values-pl/strings.xml`
  * `app/src/main/res/values-pt/strings.xml`
* Action: Add `filter_start_location` and `known_locations_view_workouts` across all 9 resource files.
* Test command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

### Step 2: Spatial Filter Criteria Extension in Domain Model
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteria.kt`
* Action:
  * Add properties `startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`.
  * Update `activeFilterCount` to increment when coordinates are present.
  * Implement geodetic geofence evaluation in `matches(workout: WorkoutData)`.
  * Implement lossless JSON serialization and deserialization in `toJson()` and `fromJson()`.

### Step 3: Active Filter Chips Row Integration
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRow.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutTabsScreen.kt`
* Action:
  * Add `onRemoveStartLocation` callback to `ActiveFilterChipsRow`.
  * Render `RemovableFilterChip` with `"📍 ${criteria.startLocationName ?: ...}"`.
  * Wire callback in `WorkoutTabsScreen.kt` to clear all 4 spatial fields on removal.

### Step 4: KnownLocationsScreen Card Interactive Touch Target
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* Action:
  * Add `onShowWorkouts: (KnownLocationItem) -> Unit = {}` to `KnownLocationsScreen` and `KnownLocationsListContent`.
  * Add `onShowWorkouts: () -> Unit = {}` to `KnownLocationCard`.
  * Wrap starts count in an interactive `Surface` with location icon, starts count, and forward indicator.
  * Update preview composables to supply default empty lambdas.

### Step 5: Navigation Host Integration in ATrainingTrackerApp
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/ATrainingTrackerApp.kt`
* Action:
  * In `NavRoutes.START_LOCATIONS`, obtain `summariesViewModel` and wire `onShowWorkouts` to construct `WorkoutFilterCriteria`, inject into `summariesViewModel.setFilterCriteria(criteria)`, and navigate to `NavRoutes.WORKOUTS`.

### Step 6: Targeted Unit Tests Implementation & Execution
* Target test files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterCriteriaSpatialTest.kt`:
    - Exact match, within radius match, outside radius rejection, null `startLatLng` rejection, active filter counter, and JSON serialization.
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/ActiveFilterChipsRowLocationTest.kt`:
    - Composable chip label and dismiss callback invocation.
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenDrillDownTest.kt`:
    - Card starts badge interaction, card body edit click, and long-press context menu preservation.
* Execution commands:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutFilterCriteriaSpatialTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.ActiveFilterChipsRowLocationTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.KnownLocationsScreenDrillDownTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run all targeted unit test suites during construction.
  - Run clean-room full suite regression `./gradlew testDebugUnitTest` (890+ unit tests) in Stage 5.
* **Rollback Plan**:
  - All changes reside on dedicated branch `feature/ATT-1401`.
  - In the event of an unresolvable defect or regression, the branch can be cleanly reset or deleted without impacting `sprint/2026-40.3`.
