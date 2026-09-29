# Stage 3: Implementation Plan - ATT-1594: Kompaktes Strecken-Badge auf Lieblingsort-Karten analog zum Starts-Badge

**Ticket**: [ATT-1594](https://atrainingtracker.atlassian.net/browse/ATT-1594)  
**Parent Epic**: [ATT-1396](https://atrainingtracker.atlassian.net/browse/ATT-1396) (*Lieblingsorte: Visible Value, Auto-Naming & Tracking Integration*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Requirement Mapping**: `REQ-UI-188` (*Lieblingsorte: Compact Interactive Routes Badge on KnownLocationCard*)  
**Test Mapping**: `TST-UI-142` (`TST-UI-142.1`, `TST-UI-142.2`, `TST-UI-142.3`, `TST-UI-142.4`)  
**Branch**: `feature/ATT-1594`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Description & Background

In `ATT-1402`, `KnownLocationCard` was updated to display a section header (`"Lieblingsstrecken ab hier (N):"`) and a `FlowRow` of individual `SuggestionChip` items for every route cluster whose starting coordinates fall within that location's geofence. While effective for initial prototyping, this layout causes severe vertical card bloat when a location has multiple routes, cluttering the card list and disrupting visual harmony.

This implementation plan specifies the atomic steps to replace the multi-line individual cluster chips list with a single, compact interactive routes badge/button placed alongside the Starts-Badge (`"N Strecken >"`), maintaining accessible touch targets ($48\text{dp}$ min height) and triggering 1-tap navigation to the *Lieblingsstrecken* tab with the spatial filter pre-applied.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-188` (*Lieblingsorte: Compact Interactive Routes Badge on KnownLocationCard*)
* **Test Mapping**: `TST-UI-142`
  - `TST-UI-142.1`: Component unit tests for badge presence, pluralization, zero-state omission, and callbacks (`KnownLocationCardRoutesBadgeTest.kt`).
  - `TST-UI-142.2`: Navigation and spatial filter preset integration in `ATrainingTrackerApp.kt`.
  - `TST-UI-142.3`: 9-language localization audit via `TranslationParityTest.kt`.
  - `TST-UI-142.4`: Clean-room full regression test suite (`./gradlew testDebugUnitTest`).

---

## 3. System Invariants & Preserved Behavior

1. **Card Interaction Invariants**:
   - Single-tap on card body (`onClick = onEdit`) must continue to launch `EditKnownLocationDialog`.
   - Long-press on card body (`onLongClick`) must strictly preserve the universal delete-only context menu (`REQ-UI-061`).
   - Starts count badge (`onShowWorkouts`) must continue to navigate to the filtered workouts list without interference.
2. **Accessible Ergonomics**:
   - The new Routes-Badge `Surface` must enforce `defaultMinSize(minHeight = 48.dp)` to guarantee touch accessibility.
3. **Responsive Wrapping**:
   - The metrics and badges row must use `FlowRow` or a flexible horizontal layout to prevent horizontal overflow or clipping on compact displays and large font scaling settings.
4. **Zero-Routes Cleanliness**:
   - When a location has no linked route clusters (`linkedClusters.isEmpty()`), the routes badge must be completely omitted.
5. **Localization Parity**:
   - New plurals and strings must achieve 100% parity across all 9 application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT) with zero missing entries.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: Localization Layer (`strings.xml` in 9 locales)
* Define plural resource `known_locations_routes`:
  - `one`: `%1$d Route` / `%1$d Strecke` / `%1$d ruta` / `%1$d itinéraire` / `%1$d percorso` / `%1$d件のルート` / `%1$d route` / `%1$d trasa` / `%1$d percurso`
  - `other`: `%1$d Routes` / `%1$d Strecken` / `%1$d rutas` / `%1$d itinéraires` / `%1$d percorsi` / `%1$d件のルート` / `%1$d routes` / `%1$d tras` / `%1$d percursos`
  - Polish also requires `few`: `%1$d trasy`
* Define accessible content description string `known_locations_view_routes`:
  - E.g. EN: `"View favorite tracks starting here"`, DE: `"Lieblingsstrecken ab hier anzeigen"`.

### Component 2: Presentation Layer (`KnownLocationsScreen.kt`)
* In `KnownLocationCard` and `KnownLocationsScreen`:
  - Add parameter `onShowRoutes: (KnownLocationItem) -> Unit = {}`.
  - Remove `FlowRow` of `SuggestionChip` items and header text.
  - Wrap the altitude metric, Starts-Badge, and Routes-Badge in `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically)`.
  - Render Routes-Badge when `linkedClusters.isNotEmpty()`:
    ```kotlin
    Surface(
        onClick = onShowRoutes,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .testTag("location_routes_badge_${item.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Route,
                contentDescription = stringResource(R.string.known_locations_view_routes),
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = pluralStringResource(R.plurals.known_locations_routes, linkedClusters.size, linkedClusters.size),
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

### Component 3: Navigation Integration Layer (`ATrainingTrackerApp.kt`)
* In `composable(NavRoutes.START_LOCATIONS)`:
  - Obtain `val clustersViewModel: WorkoutClustersViewModel = viewModel(activity)`.
  - Pass `onShowRoutes = { locationItem -> ... }` to `KnownLocationsScreen`:
    ```kotlin
    onShowRoutes = { locationItem ->
        val criteria = ClusterFilterCriteria(
            startLocationName = locationItem.name,
            startLocationLat = locationItem.latLng.latitude,
            startLocationLng = locationItem.latLng.longitude,
            startLocationRadiusM = locationItem.radius.toDouble().takeIf { it > 0.0 } ?: 200.0
        )
        clustersViewModel.setFilterCriteria(criteria)
        navController.navigate(NavRoutes.LOCATIONS)
    }
    ```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: 9-Language Localization
* Add `known_locations_routes` plural and `known_locations_view_routes` string to all 9 `strings.xml` files.
* Run: `./gradlew testDebugUnitTest --tests "*TranslationParity*"`

### Step 2: Update `KnownLocationsScreen.kt`
* Add `onShowRoutes` callback to `KnownLocationsScreen` and `KnownLocationCard`.
* Replace individual chip list with compact Routes-Badge in `FlowRow` layout.
* Update `@Preview` composables.

### Step 3: Wire Navigation in `ATrainingTrackerApp.kt`
* Inject/retrieve `WorkoutClustersViewModel` in `NavRoutes.START_LOCATIONS`.
* Wire `onShowRoutes` to construct `ClusterFilterCriteria`, set it on ViewModel, and navigate to `NavRoutes.LOCATIONS`.

### Step 4: Author Component Unit Tests & Full Regression
* Author `KnownLocationCardRoutesBadgeTest.kt`.
* Run targeted tests: `./gradlew testDebugUnitTest --tests "*KnownLocation*"`
* Run full suite: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - `TranslationParityTest.kt` for 100% localization parity.
  - Component tests verifying badge visibility, count pluralization, zero-state omission, and callback firing.
  - Full clean-room test suite execution.
* **Rollback Strategy**:
  - Changes are isolated on `feature/ATT-1594`. If issues arise, git branch reset leaves `sprint/2026-40.4` undisturbed.
