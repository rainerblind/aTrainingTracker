# Stage 3: Implementation Plan - ATT-1400: [Feature] Lieblingsorte: Display Start & Destination Locations in Workout Details and Map

**Ticket**: [ATT-1400](https://atrainingtracker.atlassian.net/browse/ATT-1400)  
**Sub-task**: [ATT-1576](https://atrainingtracker.atlassian.net/browse/ATT-1576) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `sprint/2026-40.3`  
**Requirement Mapping**: `REQ-UI-184` (*Lieblingsorte: Display Start & Destination Locations in Workout Details and Map*)  
**Test Mapping**: `TST-UI-137` (*Lieblingsorte: Display Start & Destination Locations in Workout Details and Map Verification*)  
**Branch**: `feature/ATT-1400`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In `aTrainingTracker`, "Lieblingsorte" (favorite locations stored in `StartLocation2Altitude.db`) provide ground-truth coordinates and geofence radii for barometric altimeter calibration and workout auto-naming. However, once a workout is completed and saved, the athlete cannot immediately see which favorite location their workout started at or arrived at when inspecting the workout card header in the workout summary list or the route map preview. Start and stop pins on the route map currently display only generic "Start" and "Stop" labels without spatial context.

Furthermore, historical workouts recorded before a favorite location was defined or named do not reflect those locations in their summary views. Rather than altering the underlying SQLite database schema (`WorkoutSummaries.db`), resolving start and destination favorite locations dynamically at runtime in `WorkoutDataMapper.kt` provides retroactive spatial context across all historical workouts without requiring database migrations or data re-recording.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-184` (*Lieblingsorte: Display Start & Destination Locations in Workout Details and Map*)
* **Test Mapping**: `TST-UI-137` (*Lieblingsorte: Display Start & Destination Locations in Workout Details and Map Verification*)
  * `TST-UI-137.1`: Dynamic Location Resolution Unit Tests (`WorkoutDataMapperLocationTest.kt`)
  * `TST-UI-137.2`: WorkoutHeader Composable Rendering Unit Tests (`WorkoutHeaderLocationTest.kt`)
  * `TST-UI-137.3`: Route Map Pin Marker Annotations Unit Tests (`WorkoutRepositoryMarkerTest.kt`)
  * `TST-UI-137.4`: 9-Language Localization & Format Specifier Audit (`TranslationParityTest.kt`)
  * `TST-UI-137.5`: Clean-Room Full Suite Regression Execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Database Migrations**: `WorkoutSummaries.db` and `StartLocation2Altitude.db` SQLite schemas remain 100% unchanged. Location names are resolved dynamically on-demand from existing start and stop coordinates.
2. **Retroactive Enrichment**: Workouts recorded in the past automatically reflect newly created or updated favorite locations upon being mapped by `WorkoutDataMapper`.
3. **Null Safety in Mocked Unit Tests**: In pure JVM test environments where Android SQLite singletons throw runtime exceptions if unmocked, `WorkoutDataMapper` wraps `KnownLocationsDatabaseManager.getInstance(context)` in a safe fallback or accepts an injected manager.
4. **Header Layout Integrity**: `WorkoutHeader` touch targets, long-press handlers, overflow menu triggers, and existing metadata rows (title, cluster chip, sport/gear, date/time) remain unaffected.
5. **100% 9-Language Localization Parity**: All newly introduced string tokens are localized across all 9 supported locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries and matching `%s` specifiers.
6. **Subtask Direct Completion**: Subtask `ATT-1576` transitions directly to `Erledigt` upon passing Gate 3 audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent `ATT-1400` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Jetpack Compose UI                              │
│                                                                        │
│  ┌──────────────────────────────┐    ┌──────────────────────────────┐  │
│  │       WorkoutHeader          │    │       PathPreviewMap         │  │
│  │                              │    │                              │  │
│  │  📍 Start: Zuhause           │    │  [Start: Zuhause] Pin        │  │
│  │  🏁 Ziel: Büro               │    │  [Stop: Büro] Pin            │  │
│  │  (or 📍 Start & Ziel: ...)   │    │                              │  │
│  └──────────────┬───────────────┘    └──────────────▲───────────────┘  │
└─────────────────┼───────────────────────────────────┼──────────────────┘
                  │ binds WorkoutHeaderData           │ reads markers
┌─────────────────▼───────────────────────────────────┴──────────────────┐
│                         Presentation / Domain                          │
│                                                                        │
│  WorkoutHeaderData (startLocationName: String?, endLocationName: ...)  │
│  WorkoutData (startLocationName: String?, endLocationName: ...)        │
└─────────────────▲──────────────────────────────────────────────────────┘
                  │ maps Cursor + resolved names
┌─────────────────┴──────────────────────────────────────────────────────┐
│                  WorkoutDataMapper & Resolution                        │
│                                                                        │
│  startLatLng ──► KnownLocationsDatabaseManager.getMyLocation(latLng)   │
│                  └──► WorkoutAutoNamingHelper.getDisplayName(context)  │
│  endLatLng   ──► KnownLocationsDatabaseManager.getMyLocation(latLng)   │
│                  └──► WorkoutAutoNamingHelper.getDisplayName(context)  │
└────────────────────────────────────────────────────────────────────────┘
```

### Component 1: Domain & Presentation Models (`WorkoutData.kt`, `WorkoutHeaderData.kt`)
* In `WorkoutHeaderData.kt`, add:
  ```kotlin
  val startLocationName: String? = null,
  val endLocationName: String? = null
  ```
* In `WorkoutData.kt`, add:
  ```kotlin
  val startLocationName: String? = null,
  val endLocationName: String? = null,
  ```
  And in `val headerData: WorkoutHeaderData get()`, pass `startLocationName` and `endLocationName`.

### Component 2: Dynamic Geofence Resolution (`WorkoutDataMapper.kt`, `WorkoutAutoNamingHelper.kt`)
* In `WorkoutAutoNamingHelper.kt`, make `getDisplayName(context: Context, location: MyLocation?): String?` accessible (@JvmStatic public/internal) so both auto-naming and summary mapping share the identical display name formatting logic (including coordinate fallback if unnamed).
* In `WorkoutDataMapper.kt`:
  * Add optional dependency parameter with safe default:
    ```kotlin
    class WorkoutDataMapper(
        private val context: Context,
        private val workoutSummariesDatabaseManager: WorkoutSummariesDatabaseManager,
        private val sportTypeDatabaseManager: SportTypeDatabaseManager,
        private val equipmentDbHelper: EquipmentDbHelper,
        private val stravaUploadDbHelper: StravaUploadDbHelper,
        private val knownLocationsDatabaseManager: KnownLocationsDatabaseManager? = try {
            KnownLocationsDatabaseManager.getInstance(context)
        } catch (_: Throwable) {
            null
        }
    )
    ```
  * Resolve start and end location names from `startLatLng` and `endLatLng`:
    ```kotlin
    val startLocationName = resolveLocationName(startLatLng)
    val endLocationName = resolveLocationName(endLatLng)
    ```
    Where `resolveLocationName(latLng: LatLng?): String?`:
    ```kotlin
    private fun resolveLocationName(latLng: LatLng?): String? {
        if (latLng == null) return null
        val mgr = knownLocationsDatabaseManager ?: return null
        val myLoc = mgr.getMyLocation(latLng) ?: return null
        return WorkoutAutoNamingHelper.getDisplayName(context, myLoc)
    }
    ```
  * Populate `startLocationName` and `endLocationName` in returned `WorkoutData`.

### Component 3: Workout Header Presentation (`WorkoutHeader.kt`)
* Directly below the Date & Time row (Row B), introduce Row C for location context:
  * When `startLocationName != null && startLocationName == endLocationName`:
    Render single round-trip indicator:
    `IconTextRow(iconRes = R.drawable.my_locations, text = stringResource(R.string.workout_start_and_destination, startLoc))`
  * When both are non-null and distinct:
    Render both:
    `IconTextRow(iconRes = R.drawable.my_locations, text = stringResource(R.string.workout_start_location, startLoc))` and
    `IconTextRow(iconRes = R.drawable.my_locations, text = stringResource(R.string.workout_destination_location, endLoc))`
  * When only one is non-null:
    Render only the recognized endpoint indicator.
  * When both are null:
    Row C is completely omitted.
  * Truncation: Enforce `maxLines = 1` and `TextOverflow.Ellipsis`.

### Component 4: Route Map Pin Annotations (`WorkoutRepository.kt`, `PathPreviewMap.kt`)
* In `WorkoutRepository.getWorkoutMarkers(workoutData: WorkoutData)`:
  * Start marker title:
    ```kotlin
    val title = if (!workoutData.startLocationName.isNullOrBlank()) {
        "${application.getString(R.string.Start)}: ${workoutData.startLocationName}"
    } else {
        application.getString(R.string.Start)
    }
    ```
  * Stop marker title:
    ```kotlin
    val title = if (!workoutData.endLocationName.isNullOrBlank()) {
        "${application.getString(R.string.Stop)}: ${workoutData.endLocationName}"
    } else {
        application.getString(R.string.Stop)
    }
    ```
* In `PathPreviewMap.kt`, accept optional `startTitle: String? = null` and `endTitle: String? = null` and attach them to the start/end `Marker` states.

### Component 5: 9-Language Localization Parity (`strings.xml`)
Define string tokens across all 9 supported locales:
* `workout_start_location`: "Start: %s"
* `workout_destination_location`: "Ziel: %s" (DE) / "Destination: %s" (EN)
* `workout_start_and_destination`: "Start & Ziel: %s" (DE) / "Start & Destination: %s" (EN)
Locales: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.

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
* Verify parity:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

### Step 2: Domain & Presentation Models Extension
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderData.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt`
* Action: Add nullable `startLocationName` and `endLocationName` properties and update `headerData` property getter.

### Step 3: Auto-Naming Helper Visibility & Dynamic Resolution in WorkoutDataMapper
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutAutoNamingHelper.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt`
* Action:
  * Make `WorkoutAutoNamingHelper.getDisplayName` public `@JvmStatic`.
  * Add safe `knownLocationsDatabaseManager` dependency to `WorkoutDataMapper`.
  * Implement `resolveLocationName(latLng: LatLng?): String?`.
  * Pass resolved names to `WorkoutData` instance in `fromCursor`.

### Step 4: WorkoutHeader Composable Location Presentation
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeader.kt`
* Action:
  * Render Row C for location indications handling round-trip, point-to-point, and single-endpoint conditions.
  * Update preview providers with sample location names.

### Step 5: Route Map Pin Annotations
* Files:
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/PathPreviewMap.kt`
* Action:
  * Annotate start and finish `LocationMarker` titles in `WorkoutRepository.getWorkoutMarkers`.
  * Support `startTitle` and `endTitle` in `PathPreviewMap`.

### Step 6: Targeted Unit Tests
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapperLocationTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/workoutheader/WorkoutHeaderLocationTest.kt`
  * `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepositoryMarkerTest.kt`
* Commands:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutDataMapperLocationTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.workoutheader.WorkoutHeaderLocationTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.WorkoutRepositoryMarkerTest"`
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**: Execute all targeted unit tests during construction, followed by full regression suite `./gradlew testDebugUnitTest` (880+ tests) in Stage 5.
* **Rollback**: Work is isolated to git branch `feature/ATT-1400`. If any blocking architectural regression occurs, changes can be cleanly discarded via `git reset --hard` or branch checkout without affecting `sprint/2026-40.3`.
