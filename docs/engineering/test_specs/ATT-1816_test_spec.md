# Stage 2: Requirement & Test Specification - ATT-1816: [Lieblingsorte] Add map preview thumbnail on right of KnownLocationCard and standardize heading typography

**Ticket**: [ATT-1816](https://rainerblind.atlassian.net/browse/ATT-1816)  
**Sub-task**: [ATT-1865](https://rainerblind.atlassian.net/browse/ATT-1865) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-217` (*Lieblingsorte: Map Preview Thumbnail and Standardized Title Typography on KnownLocationCard*)  
**Test Spec ID**: `TST-UI-171`  
**Branch**: `feature/ATT-1816`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-217)

### 1.1 Problem Statement & Rationale

In `aTrainingTracker`, the favorite locations (*Lieblingsorte*) feature allows athletes to designate and monitor recurrent workout hubs (e.g. Home, Office, Trailheads) with reference elevation and linked route clusters. In the current management screen (`KnownLocationsScreen.kt`), each location is rendered using `KnownLocationCard`:
1. **Lack of Spatial Orientation**: Cards only present textual elevation and start/route count badges. Unlike route cluster cards (`ClusterItem` in `WorkoutClusterComponents.kt`), no visual map thumbnail is provided. Athletes cannot quickly identify the geographical setting of a location without opening an edit sheet or navigating away. Furthermore, `onShowOnMap` is passed down to `KnownLocationCard` but left unwired to any visual trigger.
2. **Inconsistent Heading Typography**: The location name in `KnownLocationCard` uses `MaterialTheme.typography.titleMedium`, whereas the standard heading across other list cards (`WorkoutClusterIdentityRow`, `RouteSummaryHeader`) is `MaterialTheme.typography.titleLarge` with `FontWeight.Bold`. This leaves favorite location cards looking visually smaller and inconsistent.

The objective of `REQ-UI-217` is to standardize the card heading to `titleLarge` and integrate an 80dp square map preview thumbnail on the right side of `KnownLocationCard` using Google Maps lite mode, with tap-to-show-on-map interaction, heart pin marker, geofence circle, dark mode anti-flash overlay, and preview/inspection mode fallback.

---

### 1.2 Functional & Architectural Requirements

1. **Standardized Heading Typography (`KnownLocationsScreen.kt`)**:
   - The card's location name (`item.name`) SHALL use `MaterialTheme.typography.titleLarge` with `FontWeight.Bold` and `TextOverflow.Ellipsis`.
   - `MaterialTheme.typography.titleMedium` SHALL NO longer be applied to the location title.

2. **Card Content Layout Progression**:
   - Below the standardized location name header, the card body SHALL be organized into a two-column horizontal `Row` (`modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically`):
     - **Left Column** (`modifier = Modifier.weight(1f)`): Houses the Altitude Metric Row (`ic_ascent` + formatted elevation) followed by the Dedicated Badges Row (`FlowRow` containing Starts-Badge and, if applicable, Strecken-Badge per `REQ-UI-195` and `REQ-UI-207`).
     - **Spacer**: `Spacer(modifier = Modifier.width(12.dp))`.
     - **Right Column**: Houses the Map Preview Thumbnail (`KnownLocationThumbnailMap`).

3. **Spatial Map Preview Thumbnail (`KnownLocationThumbnailMap`)**:
   - **Container**: Rendered inside a `Surface` with dimensions `Modifier.size(80.dp)`, corner radius `RoundedCornerShape(12.dp)`, background `MaterialTheme.colorScheme.surfaceContainerHigh`, and test tag `location_map_preview_${item.id}`.
   - **Lite Mode Execution**: The map SHALL execute in Google Maps lite mode (`GoogleMapOptions().liteMode(true)`) to prevent OpenGL context exhaustion and frame drops in long lists.
   - **Gestures Disabled**: Map gestures and UI controls SHALL be disabled (`zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false, tiltGesturesEnabled = false, rotationGesturesEnabled = false, myLocationButtonEnabled = false, compassEnabled = false, mapToolbarEnabled = false`).
   - **Camera & Styling**: The camera SHALL be centered at `item.latLng` at zoom level `14.5f`. The map SHALL resolve light/dark themes via `DarkMapStyle.resolveMapProperties(isDark, context)` and apply `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)`.
   - **Overlays**:
     - A `Marker` at `item.latLng` using `createHeartPinMarker(context, MaterialTheme.colorScheme.primary, Color.White)`.
     - A `Circle` at `item.latLng` with radius `item.radius.toDouble()`, fill color `primary.copy(alpha = 0.2f)`, and outline stroke `primary.copy(alpha = 0.6f)` with width `2f`.
   - **Click Handling**: Tapping the map thumbnail SHALL invoke `onShowOnMap()`, triggering navigation to the full map centered on this location.
   - **Preview & Test Inspection Safety**: When `LocalInspectionMode.current == true`, the component SHALL render a lightweight Canvas schematic with a circular geofence boundary and pin icon, avoiding Google Play Services runtime dependencies in unit tests and Compose previews.

4. **Preservation of Core Invariants**:
   - Single-tap on the card body outside the thumbnail/badges SHALL continue opening the edit dialog (`onEdit`).
   - Long-press on the card body SHALL continue triggering the universal top-left Delete context menu (`REQ-UI-061`).
   - Tapping the Starts badge SHALL continue navigating to filtered workouts (`onShowWorkouts`).
   - Tapping the Routes badge SHALL continue navigating to filtered routes (`onShowRoutes`).
   - 100% 9-language localization parity (`REQ-UI-106`) SHALL remain preserved.

---

### 1.3 Acceptance Criteria (Given-When-Then)

* **Criterion 1 (Title Typography)**:
  * *Given* an athlete viewing the "Lieblingsorte" management screen (`KnownLocationsScreen`),
  * *When* inspecting any `KnownLocationCard`,
  * *Then* the location name SHALL render in `MaterialTheme.typography.titleLarge` with `FontWeight.Bold`.

* **Criterion 2 (Map Preview Thumbnail Rendering)**:
  * *Given* an athlete viewing `KnownLocationCard`,
  * *When* inspecting the right side of the card,
  * *Then* a square 80dp rounded map thumbnail (`location_map_preview_${item.id}`) SHALL be displayed, rendering a heart pin marker and geofence circle centered at the location coordinates.

* **Criterion 3 (Map Thumbnail Interaction)**:
  * *Given* an athlete viewing `KnownLocationCard`,
  * *When* tapping the map preview thumbnail,
  * *Then* the app SHALL invoke `onShowOnMap` to display the location on the full map.

* **Criterion 4 (Card Body Edit & Context Menu Preservation)**:
  * *Given* an athlete viewing `KnownLocationCard`,
  * *When* tapping the card body,
  * *Then* the Edit Location dialog SHALL open (`onEdit`).
  * *When* long-pressing the card body,
  * *Then* the universal Delete context menu SHALL appear (`REQ-UI-061`).

* **Criterion 5 (Badge Drill-Down Preservation)**:
  * *Given* an athlete viewing `KnownLocationCard`,
  * *When* tapping the Starts badge or Routes badge,
  * *Then* the app SHALL navigate to the filtered workouts or routes list respectively.

---

### 1.4 System Invariants

1. **Lite Mode Performance Invariant**: GoogleMap instances inside card lists must always utilize `liteMode(true)` to prevent memory exhaustion and OpenGL churn in Android `LazyColumn`.
2. **Offline & Test Robustness Invariant**: `LocalInspectionMode.current` must guard native map components to allow Robolectric and Compose Previews to render without throwing Play Services exceptions.
3. **Purity of Context Menu**: The universal context menu (`REQ-UI-061`) remains strictly Delete-only and top-left anchored.

---

## 2. Test Specification (TST-UI-171)

### Test Case 1: Typography Standardization Contract Tests (`TST-UI-171.1`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source loaded.
* **Action**: Parse `KnownLocationCard` composable implementation.
* **Expected Result**:
  * `KnownLocationCard` specifies `style = MaterialTheme.typography.titleLarge` on `item.name`.
  * `KnownLocationCard` specifies `fontWeight = FontWeight.Bold` on `item.name`.
  * `item.name` does NOT reference `titleMedium`.

### Test Case 2: Map Preview Thumbnail Layout & Properties Contract Tests (`TST-UI-171.2`)
* **Scope**: Unit & Source Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source loaded.
* **Action**: Verify map thumbnail layout and properties.
* **Expected Result**:
  * Contains test tag `location_map_preview_${item.id}`.
  * Container has `size(80.dp)` and `shape = RoundedCornerShape(12.dp)`.
  * Uses `GoogleMapOptions().liteMode(true)`.
  * Wires `onShowOnMap` to click action.
  * Inspects `LocalInspectionMode.current` for offline/test fallback.

### Test Case 3: Layout Structure & Badges Row Preservation (`TST-UI-171.3`)
* **Scope**: Unit & Layout Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Preconditions**: `KnownLocationsScreen.kt` source loaded.
* **Action**: Verify altitude metric, dedicated badges row, and ghost badge tokens are preserved.
* **Expected Result**:
  * `// Prominent Altitude Metric` and `// Dedicated Badges Row (Starts and Routes, REQ-UI-195)` preserved.
  * Altitude metric precedes badges row in left column.
  * Ghost badge styling (`surfaceVariant.copy(alpha = 0.35f)`, `outlineVariant.copy(alpha = 0.25f)`, `labelMedium`) 100% preserved.

### Test Case 4: Card Context Menu & Drill-Down Integrity (`TST-UI-171.4`)
* **Scope**: Unit & Regression Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreenTest.kt`, `KnownLocationsScreenDrillDownTest.kt`
* **Preconditions**: Sample `KnownLocationItem` instances.
* **Action**: Execute existing context menu and drill-down assertion tests.
* **Expected Result**:
  * Context menu remains Delete-only and anchored to TopStart (`REQ-UI-061`).
  * No MoreVert button, no secondary menu actions.
  * Starts and routes badge navigation callbacks fire accurately.

### Test Case 5: 9-Language Localization Audit (`TST-UI-171.5`)
* **Scope**: Localization Parity Test
* **Goal**: Verify string presence across all 9 locales:
  * EN, DE, ES, FR, IT, JA, NL, PL, PT
* **Expected Result**: 100% parity, zero missing entries, zero format specifier mismatches.

### Test Case 6: Clean-Room Full Suite Regression Execution (`TST-UI-171.6`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: Verify 100% pass rate across the full test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Method / Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-171.1` | Unit | `testLocationCardStandardizedTitleLargeTypography` | `REQ-UI-217` (item 1) | Specified |
| `TST-UI-171.2` | Unit | `testMapPreviewThumbnailLayoutAndLiteMode` | `REQ-UI-217` (items 2, 3) | Specified |
| `TST-UI-171.3` | Unit | `testAltitudeAndBadgesRowPreservation` | `REQ-UI-217`, `REQ-UI-195`, `REQ-UI-207` | Specified |
| `TST-UI-171.4` | Regression | `KnownLocationsScreenTest.testKnownLocationCardContextMenuStructure` | `REQ-UI-217`, `REQ-UI-061` | Specified |
| `TST-UI-171.5` | Localization | `KnownLocationsScreenTest.testDeleteStringResourcesAcrossAll9Locales` | `REQ-UI-217`, `REQ-UI-106` | Specified |
| `TST-UI-171.6` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
