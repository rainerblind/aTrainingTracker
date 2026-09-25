# Architectural Implementation Plan - ATT-1382: Improve Lieblingsorte UI

**Ticket**: [ATT-1382](https://rainerblind.atlassian.net/browse/ATT-1382)  
**Sub-task**: [ATT-1386](https://rainerblind.atlassian.net/browse/ATT-1386)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-39.2`  
**Requirement**: `REQ-UI-166` (*Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout & Custom Map Markers*), referencing `REQ-UI-165`  
**Test Spec ID**: `TST-UI-118`  
**Branch**: `feature/ATT-1382`  

---

## 1. Technical Architecture & Modifications

To achieve complete aesthetic and ergonomic parity with the established `aTrainingTracker` design system (`RouteTabbedScreen`, `WorkoutTabsScreen`, `SegmentsTabsScreen`), the presentation layer is systematically refactored across seven distinct technical components:

```mermaid
graph TD
    subgraph Navigation Layer
        ND[AppNavigationDrawer.kt] -->|Reorder & Drawables| DML[drawer_my_locations: ic_favorite_route]
        ND -->|Directly Below| DSL[drawer_start_locations: my_locations]
    end

    subgraph Presentation Layer: KnownLocationsScreen.kt
        HC[Surface: primaryContainer Header] --> TR[PrimaryTabRow: surfaceContainerHighest]
        TR --> HP[HorizontalPager: swipeable List & Map]
        HP --> LP[Page 0: List Perspective]
        HP --> MP[Page 1: Map Perspective]
        LP --> MLI[MappableListItem: ElevatedCard 16dp rounded]
        MP --> CMM[Custom Theme Heart Markers & 200m Geofences]
        MP --> FMC[Fallback Centering: argmax hitCount]
    end

    subgraph ViewModel & Domain Layer
        VM[KnownLocationsViewModel.kt] -->|resolveFallbackMapLocation| FMC
        VM -->|StateFlow| LP
        VM -->|StateFlow| MP
    end
```

### 1.1 Navigation Drawer Hierarchy & Vector Asset Creation
1. **New Vector Asset `app/src/main/res/drawable/ic_favorite_route.xml`**:
   - Create vector asset combining a route path trajectory with a heart glyph overlay.
   - Tint: `#000000` / default drawable tinting support; width/height 24dp.
2. **Drawer Configuration in `AppNavigationDrawer.kt`**:
   - Reorder items in `drawer__maps` so `drawer_my_locations` (*Lieblingsstrecken*) precedes `drawer_start_locations` (*Lieblingsorte*).
   - Assign `R.drawable.ic_favorite_route` to `drawer_my_locations`.
   - Assign `R.drawable.my_locations` (the location pin with embedded heart) to `drawer_start_locations`.
   - Preserve existing route resolution contracts: `NavRoutes.START_LOCATIONS` and `NavRoutes.MY_LOCATIONS`.

### 1.2 Standard Tabbed Header Surface & Tab Row
In `KnownLocationsScreen.kt`:
1. **Header Surface**:
   - Remove generic `TopAppBar`.
   - Enclose header in `Surface(color = MaterialTheme.colorScheme.primaryContainer)`.
   - Add status bar padding: `Column(modifier = Modifier.statusBarsPadding())`.
   - Title Row (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`):
     - Title: `Text(text = stringResource(R.string.known_locations_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)`.
     - Action icons (Search toggle, search input) styled with `tint = MaterialTheme.colorScheme.onPrimaryContainer`.
2. **PrimaryTabRow**:
   - Replace standard `TabRow` with `PrimaryTabRow(selectedTabIndex = pagerState.currentPage, containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, divider = {})`.
   - Tab 0: "Liste" (`R.string.known_locations_tab_list`) with `Icons.AutoMirrored.Filled.List`.
   - Tab 1: "Karte" (`R.string.known_locations_tab_map`) with `Icons.Default.Map`.
3. **Fluid Horizontal Pager**:
   - Host screen content in `HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize())` with `pageCount = 2`.
   - Page 0 renders `KnownLocationsListContent`.
   - Page 1 renders `KnownLocationsMapContent`.
   - Bidirectional synchronization: swiping pages updates `pagerState.currentPage`; clicking tabs triggers `coroutineScope.launch { pagerState.animateScrollToPage(index) }`.

### 1.3 Intelligent Fallback Map Centering on Max HitCount
In `KnownLocationsViewModel.kt`:
- Introduce a pure domain helper:
  ```kotlin
  fun getFallbackMapLocation(): KnownLocationItem? {
      val locations = uiState.value.locations
      return if (locations.isEmpty()) null else locations.maxByOrNull { it.hitCount }
  }
  ```
In `KnownLocationsScreen.kt`:
- When opening the map perspective or resetting camera without an explicitly clicked location and without GPS:
  - If `fallbackLocation != null`, center camera on `fallbackLocation.latLng` at zoom level 15f.
  - If no locations exist in SQLite, center on standard default coordinates (Munich `48.13715, 11.57612`).

### 1.4 Custom Theme-Colored Map Markers
In `KnownLocationsScreen.kt` / `MapUtils.kt`:
- Construct custom marker bitmap using `createSensorMarker(context, R.drawable.my_locations, pinColor = MaterialTheme.colorScheme.primary, iconColor = Color.White)` or vector-to-bitmap generator:
  - Pin body rendered in `primary` theme color.
  - Heart glyph rendered in crisp contrasting white.
- Render map markers via Google Maps Compose `Marker(state = MarkerState(position = location.latLng), icon = customMarkerBitmap, title = location.name, onClick = { ... })`.
- Maintain 200m circular geofence overlays with semi-transparent primary fill (`0x332196F3`) and stroke (`0x882196F3`).
- Completely eliminate unstyled default red Google Maps markers.

### 1.5 List Item Card Standardization (`MappableListItem`)
In `KnownLocationsScreen.kt`:
- Replace plain `Card` with `com.atrainingtracker.trainingtracker.ui.components.MappableListItem`:
  - `ElevatedCard` with `RoundedCornerShape(16.dp)` and `defaultElevation = 2.dp`.
  - **Identity Row**: Heart/Pin icon, bold location name (`titleMedium`), prominently styled altitude chip.
  - **Metadata & Badge Row**:
    - Provenance badge (`INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`).
    - Visit count badge ("X Starts").
    - Formatted 5-decimal coordinates.
  - **Interactions**:
    - Card tap: Navigate to Map tab and animate camera to location.
    - Context menu: Edit (opens `EditKnownLocationDialog`), Show on Map, Delete (opens `DeleteConfirmationDialog`).

### 1.6 9-Language Domain Localization Parity
Update string resources across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT):
- German (`values-de/strings.xml`):
  - `drawer_start_locations`: `"Lieblingsorte"`
  - `known_locations_title`: `"Lieblingsorte"`
  - `known_locations_empty_title`: `"Keine Lieblingsorte"`
  - `known_locations_empty_subtitle`: `"Gespeicherte Startorte und Referenzhöhen werden hier angezeigt."`
- English (`values/strings.xml`):
  - `drawer_start_locations`: `"Favorite Locations"`
  - `known_locations_title`: `"Favorite Locations"`
  - `known_locations_empty_title`: `"No Favorite Locations"`
- Complete parity across Spanish, French, Italian, Japanese, Dutch, Polish, and Portuguese.

---

## 2. Invariant Verification & Architectural Integrity

1. **Database Schema & Storage Invariant (`REQ-DAT-014`)**:
   - `StartLocation2Altitude.db` SQLite schema V5 MUST NOT be altered.
   - Altitude storage MUST strictly remain SI meters (`Double`).
   - Single-threaded dispatcher confinement (`KnownLocationsDB-Thread`) MUST NOT be altered.
2. **Auto-Locking & User Sovereignty Invariant (`REQ-UI-165`)**:
   - Manual altitude edits via `EditKnownLocationDialog` MUST continue to automatically set `is_locked = 1` and `source = MANUAL_USER`.
3. **Start Count Accumulation Invariant (`REQ-DAT-007`)**:
   - Workout starts within a 200m geofence MUST continue to increment `hitCount` while preserving reference altitude.
4. **Navigation Route Stability (`NavRoutes.kt`)**:
   - `NavRoutes.START_LOCATIONS` (`"known_locations"`) and `NavRoutes.MY_LOCATIONS` (`"my_locations"`) route IDs and contracts MUST NOT be broken.
5. **Separation of Concerns (SWE.2)**:
   - Presentation logic is strictly isolated in Jetpack Compose UI components.
   - Domain logic (e.g. `getFallbackMapLocation`) resides in `KnownLocationsViewModel`.
   - Data access remains encapsulated within `KnownLocationsRepository`.

---

## 3. Verification Coverage & Test Plan (`TST-UI-118`)

| Test Case | Target Class / File | Verification Method | Pass Criteria |
| :--- | :--- | :--- | :--- |
| **TST-UI-118.1** | `AppNavigationDrawerTest.kt` | Unit test inspecting `DrawerGroup(titleRes = R.string.drawer__maps)` | `drawer_my_locations` precedes `drawer_start_locations`; icons are `ic_favorite_route` and `my_locations`. |
| **TST-UI-118.2** | `KnownLocationsViewModelTest.kt` | Unit test calling `getFallbackMapLocation()` with varying `hitCount` values | Returns item with `argmax(hitCount)`; returns `null` when list is empty. |
| **TST-UI-118.3** | `KnownLocationsScreenTest.kt` | Composable / UI unit test inspecting card container | Cards render inside `MappableListItem` container with Name, Altitude, Source badge, Hit Count, Coordinates. |
| **TST-UI-118.4** | `KnownLocationsScreenTest.kt` | Composable / UI unit test inspecting tabs and pager | Composes `PrimaryTabRow` with container color `surfaceContainerHighest` and `HorizontalPager` with 2 pages. |
| **TST-UI-118.5** | `KnownLocationsScreenTest.kt` | Marker bitmap generation test | Custom marker bitmap is non-null and themed; red default marker is avoided. |
| **TST-UI-118.6** | `KnownLocationsScreenTest.kt` | 9-Language string resource audit | All 16 keys exist across all 9 locales; DE="Lieblingsorte", EN="Favorite Locations"; zero missing/blank values. |
| **TST-UI-118.7** | Full Repository | `./gradlew testDebugUnitTest` | 100% test success across all modules (0 failures, 0 regressions). |

---

## 4. Step-by-Step Implementation Sequence (Stage 4 Roadmap)

1. **Create Vector Drawable**:
   - Add `app/src/main/res/drawable/ic_favorite_route.xml` representing route with heart overlay.
2. **Update Navigation Drawer**:
   - In `AppNavigationDrawer.kt`, reorder `drawer_start_locations` below `drawer_my_locations` and update icon resources.
3. **Refactor `KnownLocationsScreen.kt`**:
   - Replace `TopAppBar` with `primaryContainer` `Surface` header.
   - Replace standard `TabRow` with `PrimaryTabRow(containerColor = surfaceContainerHighest, divider = {})`.
   - Implement `HorizontalPager(state = pagerState)`.
   - Wrap location items in `MappableListItem`.
   - Apply custom theme-colored heart markers on the map.
   - Implement fallback map camera target to $\operatorname{argmax}(\text{hitCount})$.
4. **Update `KnownLocationsViewModel.kt`**:
   - Add `getFallbackMapLocation()` helper.
5. **Update Localization Strings**:
   - Update strings across `values/strings.xml`, `values-de/strings.xml`, and remaining 7 locales.
6. **Update Test Suites**:
   - Add drawer ordering & icon assertions in `AppNavigationDrawerTest.kt`.
   - Add fallback location resolver tests in `KnownLocationsViewModelTest.kt`.
   - Update `KnownLocationsScreenTest.kt`.
7. **Clean-Room Verification & Device Installation**:
   - Run `./gradlew testDebugUnitTest`.
   - Build and verify APK.
   - Run Agent 2 Gate 4 audit and present for human approval.
