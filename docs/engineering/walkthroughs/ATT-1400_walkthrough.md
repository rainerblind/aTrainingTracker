# Walkthrough - ATT-1400: Lieblingsorte: Display Start & Destination Locations in Workout Details and Map

## 1. Executive Summary
Under **ATT-1400** (part of Epic **ATT-1396**), workout summaries and map route previews were enriched to prominently display recognized favorite start and destination locations (*Lieblingsorte*).
Previously, favorite locations operated strictly as invisible background references for altimeter calibration and geofencing. With this release, workouts gain immediate geographic identity:
* In the workout detail header, recognized favorite locations are rendered in an elegant, dedicated metadata row beneath the timestamp.
* For round-trip/loop workouts starting and finishing at the same location, the UI seamlessly coalesces the endpoints into a single unified indicator: `📍 Start & Ziel: {name}`.
* For point-to-point workouts between two distinct favorite locations, both endpoints are shown: `📍 Start: {start}   🏁 Ziel: {end}`.
* On the route preview map, the start and finish pins display marker titles enriched with the recognized favorite location name (e.g. `Start: Zuhause`, `Stop: Büro`).
* Retroactive compatibility is achieved without SQLite schema migrations by dynamically resolving endpoint coordinates against the known locations database on-the-fly.
* 100% localization parity is preserved across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Changes Implemented

### A. 100% 9-Language Localization Parity (`res/values*/strings.xml`)
Added 3 localized string keys with exact `%s` formatting specifiers across all 9 language directories (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/`):
* `workout_start_location`: `"Start: %s"` / `"Start: %s"`
* `workout_destination_location`: `"Ziel: %s"` / `"Destination: %s"`
* `workout_start_and_destination`: `"Start & Ziel: %s"` / `"Start & Destination: %s"`

### B. Presentation Data Models (`WorkoutHeaderData.kt`, `WorkoutData.kt`)
* Extended `WorkoutHeaderData` with `val startLocationName: String? = null` and `val endLocationName: String? = null`.
* Added `startLocationName: String? = null` and `endLocationName: String? = null` to `WorkoutData`.
* Mapped `startLocationName` and `endLocationName` from `WorkoutData` to `WorkoutHeaderData` in `WorkoutData.headerData`.

### C. Auto-Naming Helper (`WorkoutAutoNamingHelper.kt`)
* Made `WorkoutAutoNamingHelper.getDisplayName(context, location)` public `@JvmStatic` to provide consistent, localized display name formatting (handling default "Home"/"Work" translations) across both auto-naming and workout details.

### D. Data Mapper (`WorkoutDataMapper.kt`)
* Injected `knownLocationsDatabaseManager: KnownLocationsDatabaseManager? = null` into `WorkoutDataMapper` constructor (with graceful fallback for JVM unit testing).
* Implemented `resolveLocationName(latLng: LatLng?): String?` which queries `knownLocationsDatabaseManager.getMyLocation(latLng)` and maps it through `WorkoutAutoNamingHelper.getDisplayName`.
* Resolved `startLocationName` from `startLatLng` and `endLocationName` from `endLatLng` in both single-record mapping (`fromCursor`) and batched mapping (`fromCursorBatched`).

### E. Jetpack Compose UI (`WorkoutHeader.kt`, `WorkoutHeaderPreviewProvider.kt`)
* Added Row C (`Spacer(height = 2.dp)` + `IconTextRow`) to `WorkoutHeader.kt`, rendered conditionally when `headerData.startLocationName != null || headerData.endLocationName != null`.
* Display logic:
  * **Round-trip** (`startLocationName != null && startLocationName == endLocationName`): renders `Icons.Default.LocationOn` with text `stringResource(R.string.workout_start_and_destination, startName)`.
  * **Point-to-point** (`startLocationName != null && endLocationName != null`): renders `stringResource(R.string.workout_start_location, startName)` and `stringResource(R.string.workout_destination_location, endName)` separated by `   `.
  * **Single endpoint** (start only or destination only): renders the recognized endpoint.
* Layout robustness: Each location element applies `Modifier.weight(1f, fill = false)`, `maxLines = 1`, and `overflow = TextOverflow.Ellipsis` to guarantee clean truncation on small screens without clipping or overlapping.
* Updated `IconTextRow` composable and preview providers to support custom spacing and location row states.

### F. Map Preview Markers (`WorkoutRepository.kt`, `PathPreviewMap.kt`, `WorkoutSummary.kt`)
* Updated `WorkoutRepository.getWorkoutMarkers(workoutData)`:
  * Start marker title incorporates `workoutData.startLocationName` when present: `"${application.getString(R.string.Start)}: $startLocationName"`.
  * Stop marker title incorporates `workoutData.endLocationName` when present: `"${application.getString(R.string.Stop)}: $endLocationName"`.
  * Defaults cleanly to `application.getString(R.string.Start)` and `application.getString(R.string.Stop)` when null.
* Extended `PathPreviewMap` composable with `startTitle: String` and `endTitle: String` parameters.
* Passed marker titles from `WorkoutSummary.kt` into `PathPreviewMap`.

---

## 3. Verification & Evidence

### A. Targeted Unit Tests
* **`TranslationParityTest`**:
  * 100% parity across all 9 application locales with matching format specifiers and 0 missing keys. Passed in 5s.
* **`WorkoutDataMapperLocationTest`** (5 passing tests):
  * `testFromCursor_withStartAndEndLocation_resolvesNames`: Validates point-to-point resolution.
  * `testFromCursor_roundTrip_resolvesSameLocation`: Validates round-trip resolution.
  * `testFromCursor_outsideGeofence_resolvesNull`: Validates coordinates outside known radius return null.
  * `testFromCursor_nullCoordinates_resolvesNull`: Validates null coordinate safety.
  * `testFromCursor_nullDatabaseManager_resolvesNullGracefully`: Validates graceful fallback when database manager is null.
* **`WorkoutHeaderLocationTest`** (5 passing tests):
  * `testWorkoutHeader_bothLocationsNull_doesNotRenderLocationRow`: Validates complete omission when no favorite locations recognized.
  * `testWorkoutHeader_roundTrip_rendersUnifiedString`: Validates `Start & Ziel: {name}` text formatting.
  * `testWorkoutHeader_pointToPoint_rendersBothLocations`: Validates `Start: {start}   Ziel: {end}` formatting.
  * `testWorkoutHeader_onlyStartLocation_rendersStart`: Validates single start endpoint.
  * `testWorkoutHeader_onlyEndLocation_rendersDestination`: Validates single destination endpoint.
* **`WorkoutRepositoryMarkerTest`** (3 passing tests):
  * `testWorkoutMarkers_withLocations_formatsLocationTitles`: Validates marker titles with location names.
  * `testWorkoutMarkers_withoutLocations_defaultsToStandardTitles`: Validates standard "Start" / "Stop" titles.
  * `testWorkoutMarkers_withoutCoordinates_omitsMarkers`: Validates null coordinate safety.

### B. Clean-Room Full Suite Regression
* Executed full unit test regression: `./gradlew testDebugUnitTest`.
* **Result**: **BUILD SUCCESSFUL**, 0 failures, 0 regressions across entire test suite.

---

## 4. Traceability
* **Requirement**: `REQ-UI-184` (Status: `Verified`)
* **Test Specification**: `TST-UI-137` (Status: `Verified`)
* **Jira Subtasks**:
  * Analysis: `ATT-1574` (Erledigt)
  * Specification: `ATT-1575` (Erledigt)
  * Design: `ATT-1576` (Erledigt)
  * Implementation: `ATT-1577` (Erledigt)
  * Verification: `ATT-1578` (In Bearbeitung -> In Review)
