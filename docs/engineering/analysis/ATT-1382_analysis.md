# Architectural & Problem Analysis - ATT-1382: Improve Lieblingsorte UI

## 1. Executive Summary & Problem Statement

* **Issue Key**: `ATT-1382` / `ATT-1384`
* **Parent Issue**: `ATT-1382` (*[Verbesserung] Improve Lieblingsorte UI*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Associated Requirements**: `REQ-UI-165` (Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI), `REQ-UI-166` (Lieblingsorte UI/UX Harmonization & Standard Tabbed Layout)
* **Associated Verification**: `TST-UI-117` / `TST-UI-118`

### Problem Statement
In `ATT-919`, the core database management, reverse geocoding auto-naming, reactive repository, and dual-perspective (List/Map) Jetpack Compose screen were successfully introduced. However, visual and structural inspection revealed that the new screen deviated from the established UI/UX design language of `aTrainingTracker` and had several ergonomic and aesthetic shortcomings:

1. **Non-Standard Tabbed Layout**: The screen utilized a generic white `TopAppBar` and non-standard `TabRow` without `HorizontalPager` swipe transitions, contrasting sharply with the collapsing `primaryContainer` (dark blue) header and `surfaceContainerHighest` tab bars found on all other core screens (`WorkoutTabsScreen`, `RouteTabbedScreen`, `SegmentsTabsScreen`, `WorkoutClustersTabsScreen`).
2. **Suboptimal Camera Fallback Location**: When opening the Map perspective or recentering without active GPS, the camera arbitrarily focused on the first item in SQLite (or a hardcoded Munich fallback) rather than prioritizing the user's primary, most frequently used start location (the location with the highest `hitCount`).
3. **Navigation Drawer Hierarchy Disconnect**: In the drawer's `drawer__maps` section, `drawer_start_locations` was placed above `Lieblingsstrecken` (`drawer_my_locations`), disrupting the logical flow of map destinations.
4. **Mismatched Navigation Drawer Logos**:
   - `drawer_my_locations` (*"Lieblingsstrecken"*) used `R.drawable.my_locations` (a location pin with an embedded heart), which semantically represents a favorite *location* rather than a route.
   - `drawer_start_locations` used a generic plain pin (`R.drawable.ic_place`).
   - The heart-pin icon naturally belongs to **Lieblingsorte**, while **Lieblingsstrecken** requires a dedicated route-based icon featuring a favorite indicator.
5. **Aesthetic Map Marker Dissonance**: The map rendered standard Google Maps red teardrop markers, creating an unstyled and jarring contrast against the application's Material 3 theme.
6. **Card Design Disparity**: Location cards in the list view were built with plain `Card` elements and muted grey backgrounds rather than adopting `MappableListItem` (`ElevatedCard`, 16dp rounded corners, 2dp elevation) used across all other list screens.
7. **German Nomenclature Parity**: The German label was technical (*"Startorte"*), whereas the user-facing domain nomenclature across the app is **"Lieblingsorte"** (pairing with *"Lieblingsstrecken"*).

---

## 2. Target UI/UX Architecture & Specifications

### 2.1 Standard Tabbed Layout (`KnownLocationsScreen.kt`)
To achieve complete visual harmony with `RouteTabbedScreen` and `WorkoutClustersTabsScreen`:
* **Header Bar**:
  - Encapsulated in `Surface(color = MaterialTheme.colorScheme.primaryContainer)` with `statusBarsPadding()`.
  - Title row (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`):
    - Title: `Text(text = stringResource(R.string.known_locations_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)`.
    - Counter badge integrated cleanly in the title area.
    - Standard action icons (e.g. Search toggle, sort/filter actions) with `tint = MaterialTheme.colorScheme.onPrimaryContainer`.
* **Standard Tab Row**:
  - `PrimaryTabRow(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, divider = {})`.
  - Two primary perspectives: **"Liste"** (`R.string.known_locations_tab_list`) and **"Karte"** (`R.string.known_locations_tab_map`).
* **Fluid Page Swiping**:
  - Synchronized via `HorizontalPager(state = pagerState)` enabling natural swipe gestures between the List and Map perspectives.

### 2.2 Intelligent Fallback Map Centering
* When entering the Map perspective:
  - If a specific location is clicked from the list, the camera smoothly animates to that location.
  - If no location is explicitly selected and current GPS is unavailable, the fallback camera target is resolved dynamically as the location with the **highest visit count**:
    $$\text{fallbackTarget} = \underset{l \in \text{locations}}{\operatorname{argmax}}(\text{hitCount}(l))$$
  - If the database is empty, it safely falls back to standard default coordinates (e.g. Munich `48.13715, 11.57612`).

### 2.3 Navigation Drawer Alignment & Icon Reallocation
* **Drawer Order**:
  In `AppNavigationDrawer.kt` under `drawer__maps`:
  1. `R.id.drawer_map` (*Map* / *Karte*)
  2. `R.id.drawer_segments` (*Segments* / *Segmente*)
  3. `R.id.drawer_routes` (*Routes* / *Routen*)
  4. `R.id.drawer_my_locations` (*Lieblingsstrecken*)
  5. `R.id.drawer_start_locations` (*Lieblingsorte*) -> positioned directly below Lieblingsstrecken.
* **Logo Realignment**:
  - `R.id.drawer_start_locations` (Lieblingsorte): Assigns `R.drawable.my_locations` (location pin with embedded heart).
  - `R.id.drawer_my_locations` (Lieblingsstrecken): Introduces `R.drawable.ic_favorite_route` (route trajectory with heart indicator overlay).

### 2.4 Custom Theme-Colored Map Markers
* Deprecates default red Google Maps markers.
* Generates custom `BitmapDescriptor` using the application's theme colors:
  - Pin body rendered in `primary` / `primaryContainer` theme color.
  - Heart glyph rendered in crisp contrasting white.
  - Cohesive 200m circular geofence overlays rendered with semi-transparent primary fill (`0x332196F3`) and stroke (`0x882196F3`).

### 2.5 List Item Card Redesign (`MappableListItem`)
* Replaces ad-hoc `Card` implementation with the app-standard `MappableListItem`:
  - `ElevatedCard` with `RoundedCornerShape(16.dp)` and `defaultElevation = 2.dp`.
  - **Identity Row**: Heart/Pin icon, bold location name, prominently styled altitude with unit.
  - **Metadata & Badge Row**:
    - Provenance badge (`INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`).
    - Visit counter badge (`X Starts`).
    - Formatted 5-decimal coordinates.
  - **Interactions**:
    - Tap: Focus and center on map.
    - Overflow / Context Menu: Edit, Show on Map, Delete.

### 2.6 Localization Parity
* German (`values-de/strings.xml`):
  - `drawer_start_locations`: `"Lieblingsorte"`
  - `known_locations_title`: `"Lieblingsorte"`
  - `known_locations_empty_title`: `"Keine Lieblingsorte"`
  - `known_locations_empty_subtitle`: `"Gespeicherte Startorte und Referenzhöhen werden hier angezeigt."`
* English (`values/strings.xml`):
  - `drawer_start_locations`: `"Favorite Locations"`
  - `known_locations_title`: `"Favorite Locations"`
* Maintained across all remaining 7 locales (ES, FR, IT, JA, NL, PL, PT).

---

## 3. Call Site Audit & Affected Components

| Component / File | Purpose of Modification |
|:---|:---|
| [AppNavigationDrawer.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawer.kt) | Move `drawer_start_locations` below `drawer_my_locations`; update icon to `R.drawable.my_locations`; update `drawer_my_locations` icon to `R.drawable.ic_favorite_route`. |
| [res/drawable/ic_favorite_route.xml](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/drawable/ic_favorite_route.xml) | New vector drawable depicting route path with heart symbol overlay. |
| [KnownLocationsScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt) | Refactor header to `primaryContainer` surface; implement `PrimaryTabRow` + `HorizontalPager`; adopt `MappableListItem`; apply custom theme-colored heart markers; center map on location with highest `hitCount`. |
| [KnownLocationsViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsViewModel.kt) | Provide helper for primary/highest-hitCount location resolution. |
| [strings.xml (all 9 locales)](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/res/values-de/strings.xml) | Update German title to "Lieblingsorte" and English to "Favorite Locations" with full 9-language translation parity. |
| [AppNavigationDrawerTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/navigation/AppNavigationDrawerTest.kt) | Update drawer item ordering and icon assertions. |
| [KnownLocationsScreenTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt) | Update composable test assertions for `PrimaryTabRow`, `HorizontalPager`, and `MappableListItem`. |

---

## 4. Preserved System Invariants

1. **Database Schema & Storage Invariant (`REQ-DAT-014`)**: SQLite schema V5, SI meter storage, and single-thread dispatcher confinement (`KnownLocationsDB-Thread`) MUST NOT be altered.
2. **Auto-Locking & User Sovereignty Invariant (`REQ-UI-165`)**: Manual altitude edits MUST continue to automatically lock the location (`is_locked = 1`) and set `source = MANUAL_USER`.
3. **Start Count Accumulation Invariant (`REQ-DAT-007`)**: Repeat workout starts within a 200m geofence MUST continue to increment `hitCount` while preserving reference altitude.
4. **Drawer Navigation Routes (`NavRoutes.kt`)**: Route IDs and navigation controller contracts MUST NOT be broken.

---

## 5. Risk Assessment & Recommendation

* **Risk Rating**: **`LOW`**
  - All modifications are confined to presentation components (Jetpack Compose UI layout, vector drawables, Material 3 theme styling, and drawer ordering).
  - No database schema migrations or core tracking sensors are affected.
* **Recommendation**: **`RECOMMEND PASS`**
