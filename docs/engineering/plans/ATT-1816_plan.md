# Stage 3: Implementation Plan - ATT-1816: [Lieblingsorte] Add map preview thumbnail on right of KnownLocationCard and standardize heading typography

**Ticket**: [ATT-1816](https://rainerblind.atlassian.net/browse/ATT-1816)  
**Sub-task**: [ATT-1866](https://rainerblind.atlassian.net/browse/ATT-1866) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Requirement Mapping**: `REQ-UI-217` (*Lieblingsorte: Map Preview Thumbnail and Standardized Title Typography on KnownLocationCard*)  
**Test Mapping**: `TST-UI-171`  
**Branch**: `feature/ATT-1816`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Description & Background

In `KnownLocationsScreen.kt`, athlete favorite start hubs (*Lieblingsorte*) are currently rendered using `KnownLocationCard`. Forensic analysis identified two primary user experience issues:
1. **Lack of Inline Spatial Preview**: Cards display only text elevation and visit count badges, lacking visual spatial context. Athletes must tap through to other screens to recognize the location's geographical surroundings. Additionally, the existing `onShowOnMap` parameter in `KnownLocationCard` was unreferenced by any visual touch target.
2. **Inconsistent Typography**: Location titles currently use `MaterialTheme.typography.titleMedium` (16sp), whereas standard card headers across the application (such as `WorkoutClusterIdentityRow` in `WorkoutClusterComponents.kt` and `RouteSummaryHeader`) use `MaterialTheme.typography.titleLarge` (22sp) with `FontWeight.Bold`.

This implementation plan outlines the construction steps to standardize heading typography to `titleLarge` and integrate an 80dp rounded map preview thumbnail on the right side of `KnownLocationCard` using Google Maps lite mode, with dark/light theming, anti-flash overlay, heart pin marker, geofence circle, tap-to-show-on-map navigation, and inspection-mode safety for automated test runners.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-217` (*Lieblingsorte: Map Preview Thumbnail and Standardized Title Typography on KnownLocationCard*)
* **Test Mapping**: `TST-UI-171` (*Lieblingsorte Map Preview Thumbnail & Standardized Typography on KnownLocationCard Verification*)
* **Governing Requirements**:
  - `REQ-UI-195`: *Lieblingsorte: Dedicated Row and Compact Sizing for Starts and Strecken Badges on KnownLocationCard* (heading typography updated to `titleLarge`; left column structure preserved).
  - `REQ-UI-207`: *Lieblingsorte: Subtle Low-Contrast Ghost Badges for Starts and Strecken on KnownLocationCard* (ghost badge container colors and tokens preserved).
  - `REQ-UI-061`: *Universal Top-Left Long-Press Delete Context Menu* (strictly preserved).
  - `REQ-UI-106`: *100% 9-Language Localization Parity* (preserved).

---

## 3. System Invariants & Preserved Behavior

1. **Lite Mode Performance Invariant**: All GoogleMap composables within card lists must strictly utilize `GoogleMapOptions().liteMode(true)` to prevent memory exhaustion and OpenGL context thrashing inside `LazyColumn`.
2. **Test & Offline Robustness Invariant**: When `LocalInspectionMode.current == true`, the map thumbnail renders an offline Canvas schematic representation with a circular geofence boundary and pin icon, guaranteeing deterministic execution on Robolectric and Compose Previews without Google Play Services dependencies.
3. **Card Interaction Decoupling**:
   - Single-tap on the card body triggers `onEdit`.
   - Long-press on the card body triggers the universal Delete context menu (`REQ-UI-061`).
   - Single-tap on the map thumbnail triggers `onShowOnMap`.
   - Single-tap on Starts badge triggers `onShowWorkouts`.
   - Single-tap on Routes badge triggers `onShowRoutes`.
4. **Subtask & Gate Governance**: Subtasks transition directly to `Erledigt` upon passing review audit via `freigabe`. Parent ticket `ATT-1816` must be transitioned to `Final Review (Human)` assigned to `rainer`, preserving the human gate mandate.

---

## 4. Proposed Architectural Changes

### Component 1: `KnownLocationsScreen.kt` (`KnownLocationThumbnailMap` & `KnownLocationCard`)
- **`KnownLocationThumbnailMap`**:
  - Dedicated composable encapsulated in an 80dp square `Surface` (`shape = RoundedCornerShape(12.dp)`, `surfaceContainerHigh`).
  - Contains test tag `location_map_preview_${item.id}`.
  - Lite-mode `GoogleMap` centered at `item.latLng` with zoom level `14.5f`.
  - Overlays: `createHeartPinMarker` at `item.latLng` and `Circle` with radius `item.radius.toDouble()`.
  - Light/Dark map styling via `DarkMapStyle.resolveMapProperties(isDark, context)` and `DarkMapAntiFlashOverlay(isMapLoaded = isMapLoaded, isDark = isDark)`.
  - Inspection mode check: returns Canvas circle + place icon when `LocalInspectionMode.current` is true.
  - Tapping the map thumbnail invokes `onShowOnMap()`.
- **`KnownLocationCard` Layout Update**:
  - Heading updated to `MaterialTheme.typography.titleLarge` with `FontWeight.Bold` and `TextOverflow.Ellipsis`.
  - Content below heading arranged into a horizontal `Row` (`verticalAlignment = Alignment.CenterVertically`):
    - Left Column (`Modifier.weight(1f)`): Altitude Metric Row (`ic_ascent` + formatted altitude) and Dedicated Badges Row (`FlowRow` housing Starts and Routes ghost badges).
    - Spacer: `Spacer(modifier = Modifier.width(12.dp))`.
    - Right Column: `KnownLocationThumbnailMap(item = item, onShowOnMap = onShowOnMap)`.

### Component 2: `KnownLocationCardLayoutTest.kt`
- Add contract tests for `titleLarge` typography standardization and map preview thumbnail structure.
- Verify preservation of altitude isolation, badge dimensions, and ghost badge tokens.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Implement `KnownLocationThumbnailMap` & Refactor `KnownLocationCard`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt`
* **Changes**:
  1. Add required imports: `GoogleMapOptions`, `GoogleMap`, `Marker`, `MarkerState`, `Circle`, `rememberCameraPositionState`, `CameraPosition`, `CameraUpdateFactory`, `MapUiSettings`, `DarkMapStyle`, `DarkMapAntiFlashOverlay`, `createHeartPinMarker`, `LocalInspectionMode`, `Canvas`, `Offset`.
  2. Implement `KnownLocationThumbnailMap` composable.
  3. Update `KnownLocationCard` heading to `titleLarge, FontWeight.Bold`.
  4. Refactor card body into two-column layout with metrics on the left (`weight(1f)`) and `KnownLocationThumbnailMap` on the right.

### Step 2: Update Card Layout Contract Tests
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Changes**:
  1. Add `testLocationCardStandardizedTitleLargeTypography()` asserting `titleLarge`, `FontWeight.Bold`, and absence of `titleMedium`.
  2. Add `testMapPreviewThumbnailLayoutAndLiteMode()` asserting `location_map_preview_`, `80.dp`, `RoundedCornerShape(12.dp)`, `liteMode(true)`, `onShowOnMap` wiring, and `LocalInspectionMode` fallback.
  3. Ensure all existing tests pass without regressions.

### Step 3: Run Targeted Unit Tests
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.knownlocations.*"`
* **Expected Result**: 100% pass across all tests in `ui.knownlocations`.

---

## 6. Verification & Rollback Plan

* **Verification**: Run targeted tests during construction, followed by the complete clean-room test suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback Plan**: In the event of an irrecoverable issue, git branch isolation on `feature/ATT-1816` allows clean reset to `sprint/2026-40.7` without impacting production or sprint branches.
