# Test Specification & Requirement Synchronization - ATT-1382: Improve Lieblingsorte UI

## 1. Feature / Bug Overview & Test Scope

* **Issue Key**: `ATT-1382` / `ATT-1385`
* **Sub-tasks**: `ATT-1384` (Analysis), `ATT-1385` (Test Spec), `ATT-1386` (Plan), `ATT-1387` (Implementation)
* **Parent Issue**: `ATT-1382` (*[Verbesserung] Improve Lieblingsorte UI*)
* **Target Version**: `V4.9.38` (Sprint `2026-39.2`)
* **Related Requirements**: `REQ-UI-165` (Refined / Chesterton's Fence), `REQ-UI-166` (Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout, Sorting Options & Map Deletion Context Menu)
* **Related Tests**: `TST-UI-118` (Lieblingsorte UI/UX Harmonization Verification)

### Objective
Elevate the visual presentation, navigation hierarchy, and ergonomics of the *Lieblingsorte* (known start locations) screen and navigation drawer to achieve complete aesthetic parity with the established `aTrainingTracker` design system. This includes replacing the generic white `TopAppBar` with the dark blue `primaryContainer` header surface and `PrimaryTabRow` (`surfaceContainerHighest`) with swipeable `HorizontalPager`, centering the map camera on the start location with the highest visit count ($\operatorname{argmax}(\text{hitCount})$), reordering the drawer items to place *Lieblingsorte* below *Lieblingsstrecken*, reallocating drawer drawables (`my_locations` for locations, `ic_favorite_route` for favorite routes), rendering custom theme-colored heart markers on the map, standardizing list items using `MappableListItem` (`ElevatedCard`), and aligning German terminology to "Lieblingsorte" across all 9 supported locales.

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**: `REQ-UI-165` (*Known Start Locations Management, Interactive Map Geofences & Altitude Lock UI*) in `docs/requirements.md`, targeting `KnownLocationsScreen.kt` and `AppNavigationDrawer.kt`.
2. **Historical Origin & Commit Trace**: Introduced in ticket `ATT-919` via commits `48d0bdc8` and `b4ebca54` (dated 2026-09-24).
3. **Root Reason for Existing Formulation**: In `ATT-919`, the primary focus was baseline functionality (SQLite V5 persistence, DEM resolution, manual altitude auto-locking, and reverse geocoding auto-naming). It utilized a default white `TopAppBar` and standard `TabRow`, with generic red map markers and arbitrary initial camera centering, before being reviewed for complete design language parity.
4. **Preservation of Core Invariants**: Schema V5 persistence (`REQ-DAT-014`), single-thread SQLite confinement (`KnownLocationsDB-Thread`), automatic altitude write-protection on manual user edits (`is_locked = 1`, `source = MANUAL_USER`), repeat workout start hitCount increments (`REQ-DAT-007`), and navigation route contracts (`NavRoutes.START_LOCATIONS`) remain 100% strictly intact.

---

## 2. Harmonized Requirement Specification (`REQ-UI-166`)

### REQ-UI-166: Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout & Custom Map Markers
The system SHALL provide a harmonized, design-system-compliant Jetpack Compose interface and navigation integration for known start locations (*Lieblingsorte*), matching the look and feel of `aTrainingTracker` core screens (`RouteTabbedScreen`, `WorkoutTabsScreen`, `SegmentsTabsScreen`):

1. **Navigation Drawer Hierarchy & Icon Reallocation (`AppNavigationDrawer.kt`)**:
   - In the map destinations group (`drawer__maps`), the system SHALL position `drawer_start_locations` directly below `drawer_my_locations` (*Lieblingsstrecken*).
   - `drawer_start_locations` (*Lieblingsorte*) SHALL be assigned `R.drawable.my_locations` (location pin with embedded heart).
   - `drawer_my_locations` (*Lieblingsstrecken*) SHALL be assigned `R.drawable.ic_favorite_route` (route trajectory with heart overlay).
   - Both destinations SHALL continue to route correctly to their respective destinations (`NavRoutes.START_LOCATIONS` and `NavRoutes.MY_LOCATIONS`).

2. **Standard Tabbed Header Surface & Tab Row (`KnownLocationsScreen.kt`)**:
   - The screen header SHALL be enclosed in a `Surface` with `MaterialTheme.colorScheme.primaryContainer` and `Modifier.statusBarsPadding()`.
   - The title row (`LayoutConstants.HEADER_TITLE_ROW_HEIGHT`) SHALL display the screen title using `MaterialTheme.typography.titleLarge` colored `MaterialTheme.colorScheme.onPrimaryContainer`.
   - Action buttons (e.g. search toggle, search text field) SHALL use `tint = MaterialTheme.colorScheme.onPrimaryContainer`.
   - The tab row SHALL compose `PrimaryTabRow` with `containerColor = MaterialTheme.colorScheme.surfaceContainerHighest` and `divider = {}`.
   - Two primary tabs SHALL be defined: "Liste" (`R.string.known_locations_tab_list`) and "Karte" (`R.string.known_locations_tab_map`).

3. **Fluid Page Navigation & Swipe Gestures**:
   - The screen content SHALL be hosted in a `HorizontalPager(state = pagerState)` with page count 2, enabling natural horizontal swipe transitions synchronized bidirectionally with `PrimaryTabRow`.

4. **Intelligent Fallback Map Centering**:
   - When switching to or opening the Map perspective without an explicitly selected location and without active GPS, the camera SHALL automatically center on the start location with the highest visit count:
     $$\text{fallbackTarget} = \underset{l \in \text{locations}}{\operatorname{argmax}}(\text{hitCount}(l))$$
   - If no locations exist in SQLite, the camera SHALL center on default coordinates (Munich `48.13715, 11.57612`).

5. **Custom Theme-Colored Map Markers**:
   - The Google Map view SHALL render custom `BitmapDescriptor` markers styled with the application's theme colors:
     - Outer pin body tinted with `primary` theme color.
     - Contrasting white heart glyph (`R.drawable.my_locations` or vector pin).
   - Standard red Google Maps markers MUST NOT be displayed.
   - Circular 200m geofence overlays SHALL be rendered with semi-transparent fill (`0x332196F3`) and stroke (`0x882196F3`).

6. **List Item Card Standardization (`MappableListItem`)**:
   - Location cards in the list perspective SHALL strictly compose `com.atrainingtracker.trainingtracker.ui.components.MappableListItem` (`ElevatedCard`, 16dp rounded corners, 2dp elevation).
   - Each card SHALL display:
     - Name with pin/heart leading icon.
     - Reference altitude formatted with unit (metric: meters, imperial: feet).
     - Provenance badge (`INTERNET_DEM`, `MANUAL_USER`, `AUTO_LEARNED`, `GPS_FALLBACK`, `LEGACY_RAW`).
     - Visit count badge ("X Starts" / localized format).
     - Formatted 5-decimal coordinates.
     - Context overflow menu (Edit, Show on Map, Delete).

7. **9-Language Domain Nomenclature Parity**:
   - German strings (`values-de/strings.xml`) SHALL be updated from "Startorte" to "Lieblingsorte" (`drawer_start_locations`, `known_locations_title`, `known_locations_empty_title`).
   - English strings (`values/strings.xml`) SHALL be defined as "Favorite Locations".
   - Localization parity SHALL be 100% maintained across all 9 supported application locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).

#### Acceptance Criteria (Given-When-Then):
* **AC-1 (Drawer Ordering & Icons)**:
  - *Given* the athlete opens the navigation drawer,
  - *When* viewing the `drawer__maps` section,
  - *Then* `Lieblingsorte` SHALL appear immediately below `Lieblingsstrecken`, displaying `R.drawable.my_locations`, and `Lieblingsstrecken` SHALL display `R.drawable.ic_favorite_route`.
* **AC-2 (Header Surface & Tab Row Styling)**:
  - *Given* `KnownLocationsScreen` is rendered,
  - *When* inspecting the top bar and tab row,
  - *Then* the header surface SHALL have `primaryContainer` color with `statusBarsPadding()`, and the tab row SHALL be a `PrimaryTabRow` with `surfaceContainerHighest` container color and no divider.
* **AC-3 (Horizontal Pager Swiping)**:
  - *Given* the athlete is on the List tab,
  - *When* swiping left,
  - *Then* the screen SHALL smoothly animate to the Map tab, and `PrimaryTabRow`'s selected indicator SHALL synchronize to index 1.
* **AC-4 (Map Centering on Max HitCount)**:
  - *Given* multiple known locations with varying hit counts (e.g. Location A with hitCount=2, Location B with hitCount=15),
  - *When* opening the Map tab without selecting a specific location,
  - *Then* the camera position target SHALL resolve to Location B's coordinates.
* **AC-5 (Custom Theme-Colored Heart Markers)**:
  - *Given* known locations displayed on the map,
  - *When* rendered,
  - *Then* markers SHALL display custom theme-colored pins with embedded heart glyphs rather than default red markers.
* **AC-6 (MappableListItem Card Design & Insets)**:
  - *Given* the List tab with stored locations,
  - *When* rendered,
  - *Then* each item card SHALL be wrapped in `MappableListItem` with 16dp rounded corners, 2dp elevation, and the last item SHALL NOT be obscured by system navigation bars.
* **AC-7 (Multi-Dimension Sorting)**:
  - *Given* the List tab header,
  - *When* opening the sort dropdown,
  - *Then* 4 dimensions SHALL be offered in order: Starts (`RECORDINGS`), Distance (`DISTANCE_TO_USER`), Altitude (`ALTITUDE`), and Name (`NAME` last).
* **AC-8 (Map Long-Click Deletion Context Menu)**:
  - *Given* the Map tab,
  - *When* long-pressing a marker or geofence circle,
  - *Then* a context menu with Delete SHALL appear, prompting `DeleteConfirmationDialog` before removal.
* **AC-9 (Automatic Legacy Name Healing)**:
  - *Given* start locations with placeholder or coordinate-format names,
  - *When* the screen or viewmodel initializes,
  - *Then* `healLegacyNames()` SHALL asynchronously resolve geocoded addresses and update the database and UI while preserving custom user-edited names.
* **AC-10 (Localization Parity)**:
  - *Given* German language device configuration,
  - *When* viewing drawer, screen titles, and edit dialog,
  - *Then* "Lieblingsorte" and "Lieblingsort bearbeiten" SHALL be displayed across all relevant UI components.

#### System Invariants:
1. **Schema V5 & SQLite Concurrency**: `StartLocation2Altitude.db` schema V5 and single-thread dispatcher (`KnownLocationsDB-Thread`) MUST NOT be altered.
2. **Altitude Write Protection**: Manual altitude editing MUST automatically lock the record (`is_locked = 1`, `source = MANUAL_USER`).
3. **Hit Count Increment**: Workout starts matching an existing 200m geofence MUST increment `hitCount` without mutating locked altitude (`REQ-DAT-007`).
4. **Navigation Route Stability**: Navigation routes (`NavRoutes.START_LOCATIONS`, `NavRoutes.MY_LOCATIONS`) MUST NOT be broken.

---

## 3. Test Verification Procedures (`TST-UI-118`)

### TST-UI-118: Lieblingsorte UI/UX Harmonization, Standard Tabbed Layout, Sorting, Map Deletion & Legacy Name Healing Verification

| Test Step | Target Component | Action / Inputs | Expected Result | Pass Criteria |
| :--- | :--- | :--- | :--- | :--- |
| **TST-UI-118.1** | `AppNavigationDrawerTest.kt` | Query `drawer__maps` items configuration in `AppNavigationDrawer`. | `drawer_my_locations` precedes `drawer_start_locations`. Icon for `drawer_start_locations` is `R.drawable.my_locations`. Icon for `drawer_my_locations` is `R.drawable.ic_favorite_route`. | Drawer item ordering index and drawable resource IDs match specification exactly. |
| **TST-UI-118.2** | `KnownLocationsViewModelTest.kt` | Populate locations with `hitCount` values: [A: 3, B: 25, C: 8]. Invoke fallback target resolver. | Resolves to Location B (`hitCount = 25`). When list is empty, returns null / default Munich coordinates. | Fallback target coordinates strictly match `argmax(hitCount)`. |
| **TST-UI-118.3** | `KnownLocationsScreenTest.kt` | Verify composable card layout rendering for known locations. | Each location item renders inside `MappableListItem` container with Name, formatted Altitude, and Starts count. | All fields present and styled per design system; card uses `MappableListItem`. |
| **TST-UI-118.4** | `KnownLocationsScreenTest.kt` | Verify tab row and pager configuration. | `PrimaryTabRow` with container color `surfaceContainerHighest` and 2 text-only tabs ("Liste", "Karte"); `HorizontalPager` with pageCount 2. | Proper composable hierarchy and tab indices; map gestures unaffected. |
| **TST-UI-118.5** | `KnownLocationsViewModelTest.kt` / `KnownLocationsScreenTest.kt` | Verify 4-way sorting dropdown behavior. | Dimensions listed: Starts, Distance to User, Altitude, Name. Distance disabled if GPS location unavailable. | Sorting orders items correctly in UI. |
| **TST-UI-118.6** | `KnownLocationsScreenTest.kt` | Long-press marker on map perspective. | Anchored context menu appears with Delete; tapping presents `DeleteConfirmationDialog`. | Deletion triggers confirmation dialog before SQLite purge. |
| **TST-UI-118.7** | `KnownLocationsRepositoryTest.kt` / `LocationNameResolverTest.kt` | Initialize repository/viewmodel with placeholder names (e.g. `Startort (lat, lon)`). | `healLegacyNames()` resolves addresses and persists geocoded place names without mutating locked user names. | Legacy placeholder names successfully healed. |
| **TST-UI-118.8** | `MapUtilsTest.kt` / `KnownLocationsScreenTest.kt` | Generate marker bitmap descriptor using theme colors and heart drawable. | Returns non-null `BitmapDescriptor` generated from vector asset; default red marker is not used. | Non-null custom theme-colored marker bitmap descriptor. |
| **TST-UI-118.9** | `KnownLocationsScreenTest.kt` | 9-Language Localization Audit across EN, DE, ES, FR, IT, JA, NL, PL, PT. | DE contains "Lieblingsorte" and "Lieblingsort bearbeiten", EN contains "Favorite Locations"; all 9 locales populated with 0 missing or blank strings. | 100% localization parity across all 9 locales. |
| **TST-UI-118.10** | Full Repository | Execute full clean-room unit regression: `./gradlew testDebugUnitTest`. | All test suites pass with 0 failures and 0 errors. | BUILD SUCCESSFUL. |

---

## 4. ASPICE Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Status |
| :--- | :--- | :--- | :--- |
| `REQ-UI-165` (Refined) | `TST-UI-117` | Automated JUnit / MockK / Compose (`KnownLocationsViewModelTest.kt`, `KnownLocationsScreenTest.kt`) | Verified |
| `REQ-UI-166` (Harmonized) | `TST-UI-118` | Automated JUnit / MockK / Compose (`AppNavigationDrawerTest.kt`, `KnownLocationsViewModelTest.kt`, `KnownLocationsScreenTest.kt`) | Specified |
| `REQ-DAT-007` | `TST-DAT-009` | Automated JUnit (`KnownLocationsDatabaseManagerTest.kt`) | Verified |
| `REQ-DAT-014` | `TST-DAT-008` | Automated JUnit (`ElevationServiceTest.kt`) | Verified |

