# Stage 1 Analysis: ATT-1816 - [Lieblingsorte] Add map preview thumbnail on right of KnownLocationCard and standardize heading typography

**Ticket**: [ATT-1816](https://rainerblind.atlassian.net/browse/ATT-1816)  
**Sub-task**: [ATT-1864](https://rainerblind.atlassian.net/browse/ATT-1864) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1816`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

In `aTrainingTracker`, the favorite locations feature (*Lieblingsorte*) allows athletes to designate and monitor recurrent workout starting hubs (e.g., home, office, trailheads) with associated reference elevation, geofence radius, and linked route clusters (*Lieblingsstrecken*).

Currently, in `KnownLocationsScreen.kt`, each location is rendered using `KnownLocationCard`. Forensic analysis reveals two visual and functional design shortcomings:
1. **Lack of Spatial Preview (Map Thumbnail)**:
   - When browsing favorite locations in `KnownLocationsScreen`, the athlete is presented only with textual metadata (name, elevation, start count badge, route count badge).
   - In contrast, list items in related areas of the app (such as `ClusterItem` in `WorkoutClusterComponents.kt`) render a dedicated square map thumbnail on the right side of the card, offering instant spatial recognition of the route or location.
   - Without a map thumbnail, athletes with multiple nearby hubs or similarly named locations cannot quickly confirm spatial context without opening the edit bottom sheet or navigating away to the full map. Furthermore, `onShowOnMap: () -> Unit` is already passed down into `KnownLocationCard`, but was left completely unwired to any visual trigger.
2. **Inconsistent Heading Typography**:
   - The card heading in `KnownLocationCard` currently uses `MaterialTheme.typography.titleMedium` (`FontWeight.Bold`), whereas the standard card heading typography across the app's list items (such as `WorkoutClusterIdentityRow` in `WorkoutClusterComponents.kt` and `RouteSummaryHeader`) is `MaterialTheme.typography.titleLarge` (`FontWeight.Bold`).
   - This discrepancy results in `KnownLocationCard` appearing visually understated and inconsistent with the rest of the application's card hierarchy.

The goal of ATT-1816 is to:
1. Standardize the heading typography of `KnownLocationCard` to `MaterialTheme.typography.titleLarge` (`FontWeight.Bold`).
2. Add a compact, high-performance map preview thumbnail on the right side of `KnownLocationCard` (using Google Maps lite mode, centered at the location coordinates with geofence circle and heart pin marker, with tap-to-show-on-map interaction and preview/inspection-mode fallback).

---

## 2. Root Cause Analysis (Forensic Investigation)

### Forensic Inspection Findings:

1. **Card Hierarchy in `KnownLocationsScreen.kt`**:
   - Lines 334–353 of `KnownLocationsScreen.kt`:
     ```kotlin
     Column(
         modifier = Modifier
             .fillMaxWidth()
             .padding(horizontal = 16.dp, vertical = 12.dp),
         verticalArrangement = Arrangement.spacedBy(4.dp)
     ) {
         Row(
             modifier = Modifier.fillMaxWidth(),
             verticalAlignment = Alignment.CenterVertically
         ) {
             Text(
                 text = item.name,
                 style = MaterialTheme.typography.titleMedium,
                 fontWeight = FontWeight.Bold,
                 color = MaterialTheme.colorScheme.onSurface,
                 maxLines = 1,
                 overflow = TextOverflow.Ellipsis,
                 modifier = Modifier.fillMaxWidth()
             )
         }
         // Prominent Altitude Metric
         ...
         // Dedicated Badges Row (Starts and Routes, REQ-UI-195)
         ...
     }
     ```
   - Notice that `item.name` explicitly uses `titleMedium` (16sp), while standard list cards use `titleLarge` (22sp).
   - The entire card body is a single vertical `Column`, occupying full width with no horizontal division for visual media.

2. **Design Pattern Established in `WorkoutClusterComponents.kt` (`ClusterItem`)**:
   - In `ClusterItem` (lines 258–375), the card layout is split into a top header row and a bottom row:
     ```kotlin
     Row(
         modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
         verticalAlignment = Alignment.Bottom
     ) {
         // Left side: Metadata & Metrics (weight(1f))
         ...
         Spacer(modifier = Modifier.width(16.dp))
         // Right side: Small Square Map
         Surface(
             modifier = Modifier.size(100.dp),
             shape = RoundedCornerShape(12.dp),
             color = MaterialTheme.colorScheme.surfaceContainerHigh
         ) { ... }
     }
     ```
   - In `KnownLocationCard`, the left side contains the Altitude Metric Row (~20dp) and the Badges Row (~28-32dp), totaling ~55-70dp in height.
   - An 80dp square thumbnail (`size(80.dp)`, `shape = RoundedCornerShape(12.dp)`) provides an ideal, balanced aspect ratio that aligns cleanly with the left metadata stack without unnecessary vertical expansion.

3. **Map Rendering & Lite Mode Architecture**:
   - In a scrolling `LazyColumn` containing multiple cards, instantiating full interactive GoogleMap instances causes memory pressure and OpenGL view churn.
   - Following `PathPreviewMap.kt` and `WorkoutClusterComponents.kt`, the thumbnail must employ `GoogleMapOptions().liteMode(true)` and disable all gestures/controls (`zoomControlsEnabled = false`, `scrollGesturesEnabled = false`, `zoomGesturesEnabled = false`, `tiltGesturesEnabled = false`, `rotationGesturesEnabled = false`, `myLocationButtonEnabled = false`).
   - Tapping the map thumbnail directly triggers `onShowOnMap()`, allowing athletes to seamlessly jump to the full map with a single tap.
   - For fast Robolectric unit testing and Android Studio Compose Previews, `LocalInspectionMode.current` must be supported to render an instant schematic fallback without loading native Google Maps dependencies.

4. **Visual Language of Lieblingsorte on Maps**:
   - In `MapContentScope.kt` (lines 356–379) and `EditKnownLocationDialog.kt` (lines 440–478), a Known Location is visually defined by:
     - A heart pin marker (`createHeartPinMarker(context, primaryColor, Color.White)`).
     - A translucent geofence circle (`Circle(center = latLng, radius = radius, fillColor = primaryColor.copy(alpha = 0.2f), strokeColor = primaryColor.copy(alpha = 0.6f), strokeWidth = 2f)`).
     - Camera centered at `item.latLng` at zoom level 14.5f–15f.
     - Dark mode theme adaptation via `DarkMapStyle.resolveMapProperties(isDark, context)` and `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Standardize location name typography in `KnownLocationCard` from `MaterialTheme.typography.titleMedium` to `MaterialTheme.typography.titleLarge` with `FontWeight.Bold`.
  2. Implement an 80dp rounded map preview thumbnail on the right side of `KnownLocationCard`, placed in a horizontal `Row` alongside the altitude and badge metrics.
  3. Render the thumbnail using Google Maps lite mode (`GoogleMapOptions().liteMode(true)`), themed with dark/light map styling, anti-flash overlay, centered at `item.latLng` (zoom ~14.5f), with a heart pin marker and geofence circle.
  4. Wire single-tap on the map thumbnail to invoke `onShowOnMap()` (which triggers navigation to `NavRoutes.MAP` centered on this location).
  5. Add test tag `location_map_preview_${item.id}` for reliable automated testing.
  6. Support `LocalInspectionMode.current` for offline Compose Previews and Robolectric contract tests.
  7. Maintain 100% regression purity for existing interactions: single-tap card edit (`onEdit`), long-press delete context menu (`REQ-UI-061`), starts drill-down (`onShowWorkouts`), and routes drill-down (`onShowRoutes`).

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to `KnownLocationsViewModel` business logic, database queries, or geocoding services.
  2. No changes to the Edit Location dialog (`EditKnownLocationDialog.kt`).
  3. No changes to map rendering in the main map screen (`MapScreenWithTrack.kt`).
  4. No modification of the universal delete context menu structure (`REQ-UI-061`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - `REQ-UI-195`: *Lieblingsorte: Dedicated Row and Compact Sizing for Starts and Strecken Badges on KnownLocationCard* (`KnownLocationsScreen.kt`).
* **Historical Origin & Commit Trace**:
  - Introduced in sprint `2026-40.5` (`ATT-1643`) via commit `073df267`.
* **Root Reason for Existing Formulation**:
  - `REQ-UI-195` decoupled the altitude metric from the interactive badges and set `titleMedium` for the heading. At that time, `KnownLocationCard` was a plain single-column card without a map preview, and `titleMedium` was chosen conservatively.
* **Preservation of Core Invariants**:
  - Updating `titleMedium` to `titleLarge` aligns the card heading with standard list items across `aTrainingTracker` (`WorkoutClusterIdentityRow`, `RouteSummaryHeader`).
  - The vertical order of the left column (Altitude Metric preceding the Dedicated Badges Row) and badge styling (`REQ-UI-195`, `REQ-UI-207`) are strictly preserved.
  - Adding the right-hand map thumbnail fulfills the spatial preview requirement while preserving all existing touch targets, test tags, and context menu behaviors.

---

## 5. Architectural Strategy & High-Level Solution

### Component: `KnownLocationsScreen.kt` (`KnownLocationCard`)

1. **Title Typography Update**:
   - Update `Text` styling for `item.name`:
     ```kotlin
     Text(
         text = item.name,
         style = MaterialTheme.typography.titleLarge,
         fontWeight = FontWeight.Bold,
         color = MaterialTheme.colorScheme.onSurface,
         maxLines = 1,
         overflow = TextOverflow.Ellipsis,
         modifier = Modifier.fillMaxWidth()
     )
     ```

2. **Card Content Layout**:
   - Organize card content into:
     - Top: Title Row (`titleLarge, FontWeight.Bold`).
     - Spacer: `Spacer(modifier = Modifier.height(4.dp))`.
     - Bottom Row: `Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)`
       - Left Column (`Modifier.weight(1f)`):
         - Altitude Metric Row (`ic_ascent` + formatted altitude).
         - Badges Row (`FlowRow` containing Starts-Badge and Strecken-Badge).
       - Spacer: `Spacer(modifier = Modifier.width(12.dp))`.
       - Right Column: Map Preview Thumbnail (`KnownLocationThumbnailMap`).

3. **Map Preview Thumbnail Component (`KnownLocationThumbnailMap`)**:
   - Enclosed in `Surface(modifier = Modifier.size(80.dp).testTag("location_map_preview_${item.id}"), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh)`.
   - Inspection mode check (`LocalInspectionMode.current`): renders a clean visual canvas with circle and icon.
   - Non-inspection mode:
     - `GoogleMap` in `liteMode(true)` with `uiSettings` (all gestures disabled).
     - Camera centered at `item.latLng` with zoom `14.5f`.
     - `Marker` at `item.latLng` with `createHeartPinMarker(context, MaterialTheme.colorScheme.primary, Color.White)`.
     - `Circle` at `item.latLng` with radius `item.radius.toDouble()`.
     - `DarkMapAntiFlashOverlay` to eliminate dark-mode flash.
     - `onMapClick = onShowOnMap`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Zero Regression in Existing Navigation & Drill-Downs*: Single-tap card opens edit dialog; long-press opens universal delete context menu (`REQ-UI-061`); starts badge navigates to filtered workouts; routes badge navigates to filtered routes.
  2. *Performance Purity*: `GoogleMapOptions().liteMode(true)` prevents OpenGL context exhaustion in large location lists.
  3. *Parent Ticket Human Decision Gate*: Parent ticket `ATT-1816` must transition to `Final Review (Human)` assigned to `rainer`; never moved directly to `Erledigt`.
* **Risk Rating**: **LOW**
  - The changes are strictly localized to `KnownLocationsScreen.kt` layout composition and styling.
  - The map preview utilizes battle-tested patterns already in production in `PathPreviewMap.kt` and `WorkoutClusterComponents.kt`.
