# Walkthrough - ATT-1401: [Feature] Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location

## 1. Executive Summary
Under **ATT-1401** (part of Epic **ATT-1396**), the application introduces seamless 1-tap drill-down navigation from favorite start locations (*Lieblingsorte*) into the workout history filtered by starting geofence coordinates.
Previously, favorite location cards in `KnownLocationsScreen` displayed recorded start counts (e.g. "45 Starts") as static, non-interactive text, making the card a navigational dead end. Athletes could not see or filter historical sessions originating from their favorite hubs ("Zuhause", "Büro", "Ferienhaus").
With this release:
* In `KnownLocationsScreen`, the starts count indicator is elevated into an accessible, interactive touch target ($\ge 48\times 48\text{dp}$) with tonal styling, location pin icon, and forward chevron.
* Tapping the starts badge executes 1-tap navigation to the Workouts list (`NavRoutes.WORKOUTS`), pre-filtered to sessions starting within the geofence radius of that location.
* `WorkoutFilterCriteria` is extended with four spatial properties (`startLocationName`, `startLocationLat`, `startLocationLng`, `startLocationRadiusM`), geodetic distance evaluation via `WorkoutClusterEngine.distanceBetween(p1, p2)`, active filter counter derivation, and lossless JSON serialization.
* `ActiveFilterChipsRow` displays an active `📍 {Location Name}` chip directly above the workout list with a single-tap remove action that clears the spatial filter and restores all workouts while preserving all other active criteria.
* Core invariants are strictly preserved: single-tap on the card body continues opening `EditKnownLocationDialog`, long-press strictly preserves the universal delete-only context menu (`REQ-UI-061`), and zero SQLite database schema migrations were required.
* 100% localization parity is maintained across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

---

## 2. Changes Implemented

### A. 100% 9-Language Localization Parity (`res/values*/strings.xml`)
Added 2 localized string tokens across all 9 supported application locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, and `values-pt/`):
* `filter_start_location`: `"Start Location"` / `"Startort"`
* `known_locations_view_workouts`: `"View Workouts"` / `"Workouts anzeigen"`

### B. Spatial Filter Criteria in Domain Model (`WorkoutFilterCriteria.kt`)
* Extended `@Immutable data class WorkoutFilterCriteria`:
  ```kotlin
  val startLocationName: String? = null,
  val startLocationLat: Double? = null,
  val startLocationLng: Double? = null,
  val startLocationRadiusM: Double? = null
  ```
* Updated `activeFilterCount`: increments by 1 when `startLocationLat != null && startLocationLng != null`.
* Implemented geodetic geofence predicate evaluation in `matches(workout: WorkoutData)`:
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
* Implemented lossless JSON persistence in `toJson()` and `fromJson()` with backward-compatible fallback for legacy JSON payloads.

### C. Active Filter Chips Presentation & Removal (`ActiveFilterChipsRow.kt`, `WorkoutTabsScreen.kt`)
* Added `onRemoveStartLocation: () -> Unit = {}` parameter to `ActiveFilterChipsRow`.
* Rendered active `RemovableFilterChip` displaying `"📍 ${criteria.startLocationName ?: stringResource(R.string.filter_start_location)}"`.
* Wired `onRemoveStartLocation` in `WorkoutTabsScreen.kt` to clear all 4 spatial fields while preserving all other active filters (query, temporal ranges, sport, equipment).

### D. Lieblingsorte Management UI & Interactive Touch Target (`KnownLocationsScreen.kt`)
* Added `onShowWorkouts: (KnownLocationItem) -> Unit` callback chain across `KnownLocationsScreen`, `KnownLocationsListContent`, and `KnownLocationCard`.
* Transformed static starts count `Text` in `KnownLocationCard` into an interactive `Surface` with tonal styling, location pin icon, start count, and forward chevron indicator:
  ```kotlin
  Surface(
      onClick = onShowWorkouts,
      shape = RoundedCornerShape(12.dp),
      color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
      contentColor = MaterialTheme.colorScheme.primary,
      modifier = Modifier
          .defaultMinSize(minHeight = 48.dp)
          .testTag("location_starts_badge_${item.id}")
  ) { ... }
  ```
* Preserved single-tap on card body (`onClick = onEdit`) and long-press (`onLongClick = { showContextMenu = true }`) without gesture collision.
* Updated Compose previews with default empty lambdas.

### E. App Navigation Host Wiring (`ATrainingTrackerApp.kt`)
* In `NavRoutes.START_LOCATIONS`, injected `summariesViewModel: WorkoutSummariesViewModel = viewModel(activity)`.
* Wired `onShowWorkouts` to construct `WorkoutFilterCriteria(startLocationName = item.name, startLocationLat = item.latLng.latitude, startLocationLng = item.latLng.longitude, startLocationRadiusM = item.radius.toDouble().takeIf { it > 0.0 } ?: 200.0)`, update `summariesViewModel.setFilterCriteria(criteria)`, and navigate to `NavRoutes.WORKOUTS`.

---

## 3. Verification & Evidence

### A. Targeted Unit Tests
* **`TranslationParityTest`** (`com.atrainingtracker.trainingtracker.localization.TranslationParityTest`):
  * Verified 100% translation parity across all 9 locales with 0 missing strings. Passed in 2s.
* **`WorkoutFilterCriteriaSpatialTest`** (7 passing tests):
  * `testExactLocationMatch`: Verified workout starting at exact coordinates matches.
  * `testWithinRadiusMatch`: Verified workout ~111m away within 200m radius matches.
  * `testOutsideRadiusRejection`: Verified workout ~890m away outside 200m radius is rejected.
  * `testNullStartCoordinateRejection`: Verified workout with null `startLatLng` is rejected.
  * `testCustomRadiusOverride`: Verified 1000m radius matches workout ~890m away.
  * `testActiveFilterCountIncludesSpatialDimension`: Verified count increments correctly.
  * `testJsonSerializationParity`: Verified lossless serialization and deserialization.
  * `testJsonDeserializationLegacyWithoutSpatial`: Verified backward compatibility with legacy JSON.
* **`ActiveFilterChipsRowLocationTest`** (4 passing tests):
  * `testStartLocationFilterActive_identifiesChipPresence`: Verified chip label formatting.
  * `testStartLocationFilterWithoutName_fallsBackToGenericLabel`: Verified fallback label when unnamed.
  * `testStartLocationFilterInactive_omitsChip`: Verified chip omission when filter inactive.
  * `testRemoveStartLocation_clearsSpatialFieldsPreservingOthers`: Verified removal preserves all other active criteria.
* **`KnownLocationsScreenDrillDownTest`** (3 passing tests):
  * `testConstructWorkoutFilterCriteriaFromKnownLocation`: Verified criteria construction from `KnownLocationItem`.
  * `testFallbackRadiusWhenZeroOrNegative`: Verified default 200m fallback when radius is zero.
  * `testDrillDownCallbackInvocation`: Verified independent invocation of drill-down, edit, and delete callbacks.

### B. Clean-Room Full Suite Regression
* Executed `./gradlew testDebugUnitTest` across all modules:
  * **Result**: **BUILD SUCCESSFUL** in 3m 20s.
  * **Metrics**: **908 tests executed, 0 failures, 0 errors, 0 skipped (100% pass rate)**.

---

## 4. Traceability

| Artifact | Identifier | Status |
| :--- | :--- | :--- |
| **Requirement** | `REQ-UI-185` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location*) | **Verified** |
| **Test Specification** | `TST-UI-138` (*Lieblingsorte: Drill-Down Filter to View Workouts by Starting Location Verification*) | **Verified** |
| **Stage 1 (Analysis)** | `ATT-1580` (`[Analysis]`) | **Erledigt** (Gate 1 Passed) |
| **Stage 2 (Specification)** | `ATT-1581` (`[Test-Spec]`) | **Erledigt** (Gate 2 Passed) |
| **Stage 3 (Implementation Plan)** | `ATT-1582` (`[Impl-Plan]`) | **Erledigt** (Gate 3 Passed) |
| **Stage 4 (Construction)** | `ATT-1583` (`[Implementation]`) | **Erledigt** (Gate 4 Passed) |
| **Stage 5 (Verification)** | `ATT-1584` (`[Test]`) | **In Überprüfung** (Audit Ready) |
| **Parent Feature** | `ATT-1401` | **Final Review (Human)** (Pending handover) |
