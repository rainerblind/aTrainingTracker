# Stage 2: Requirement & Test Specification - ATT-1878: Calibrate Zoom Level on KnownLocationCard Map Thumbnail to Show Neighborhood Context

**Ticket**: [ATT-1878](https://rainerblind.atlassian.net/browse/ATT-1878)  
**Sub-task**: [ATT-1913](https://rainerblind.atlassian.net/browse/ATT-1913) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-224` (*Lieblingsorte: Calibrated Zoom Level and Dynamic Geofence Framing on KnownLocationCard Map Thumbnail*)  
**Test Spec ID**: `TST-UI-178`  
**Branch**: `feature/ATT-1878`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-224`)

### 1.1 Problem Statement & Rationale
On the saved favorite start locations screen (`KnownLocationsScreen.kt`), each `KnownLocationCard` displays an 80dp square map preview thumbnail (`KnownLocationThumbnailMap`). Under `REQ-UI-217` (ATT-1816), the camera was hardcoded to a fixed zoom level of `14.5f`. In the compact 80dp format, zoom `14.5f` yields a visible span of only $\sim 360\text{ meters}$, which clips circular geofences with typical radii of 200m–300m ($400\text{ m} - 600\text{ m}$ diameter) and completely eliminates surrounding neighborhood landmarks, road networks, and regional topography. Calibrating the zoom level dynamically via pure mathematical framing ensures the geofence is fully visible with comfortable margins and athletes can immediately orient themselves spatially.

### 1.2 Functional & Architectural Requirements
The system SHALL calibrate the camera zoom level on `KnownLocationCard` map preview thumbnails (`KnownLocationThumbnailMap` in `KnownLocationsScreen.kt`), ensuring the circular geofence boundary is fully contained without edge clipping and rich regional neighborhood context is displayed (ATT-1878):

1. **Mathematical Zoom Calibration (`KnownLocationZoomMath.kt`)**:
   - The system SHALL encapsulate thumbnail zoom calculations in a pure helper object `KnownLocationZoomMath` in package `com.atrainingtracker.trainingtracker.ui.knownlocations`:
     - `DEFAULT_THUMBNAIL_ZOOM = 12.0f`: Standard baseline zoom level for 80dp thumbnail previews.
     - `MIN_ZOOM = 10.5f`: Zoom-out lower bound protecting large geofences (up to 1000m) from over-zooming.
     - `MAX_ZOOM = 12.5f`: Zoom-in upper bound guaranteeing at least 1.45 km of surrounding neighborhood/district context even for small geofences (50m–200m).
     - `TARGET_GEOFENCE_DIAMETER_RATIO = 0.35f`: Target framing ratio where the circular geofence diameter occupies approximately 35% of the 80dp container width.
     - `fun calculateThumbnailZoom(radiusMeters: Int, latitude: Double = 0.0): Float`: Computes Web Mercator resolution based on latitude and radius, scaling zoom so that the geofence occupies the target ratio, clamped strictly within `[MIN_ZOOM, MAX_ZOOM]`.

2. **Thumbnail Camera Integration (`KnownLocationsScreen.kt`)**:
   - In `KnownLocationThumbnailMap`, the camera zoom SHALL be dynamically hoisted and calculated:
     ```kotlin
     val targetZoom = remember(item.radius, item.latLng.latitude) {
         KnownLocationZoomMath.calculateThumbnailZoom(item.radius, item.latLng.latitude)
     }
     ```
   - The camera position state and map movement SHALL consume `targetZoom`:
     `CameraPosition.fromLatLngZoom(item.latLng, targetZoom)` and `cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(item.latLng, targetZoom))`.
   - The hardcoded literal `14.5f` SHALL be completely eliminated from `KnownLocationsScreen.kt`.

3. **Preservation of Core Invariants**:
   - Google Maps lite mode (`liteMode(true)`), gesture isolation (all controls and gestures disabled), 80dp square bounds, 12dp rounded corners, dark/light theme styling, anti-flash overlay, heart pin marker, offline preview fallback (`LocalInspectionMode.current`), and card click navigation (`onShowOnMap()`, `onEdit()`, long-press delete) remain 100% strictly preserved.
   - Zero impact on 9-language localization parity (`REQ-UI-106`).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Broader Spatial Context & No Clipping for Standard Geofences)**:
  - *Given* an athlete viewing saved favorite locations on `KnownLocationsScreen`,
  - *When* viewing the 80dp map preview thumbnail on any `KnownLocationCard` with a typical geofence (radius 50m to 200m),
  - *Then* the camera zoom level SHALL be calibrated to `12.5f`, displaying at least 1.4 km of surrounding neighborhood terrain without clipping the geofence boundary.
* **Criterion 2 (Dynamic Scaling for Medium & Large Geofences)**:
  - *Given* a location with a medium or large geofence (e.g. radius 300m, 500m, or 1000m),
  - *When* the map thumbnail renders,
  - *Then* the camera zoom level SHALL dynamically adjust down to `12.2f` (300m), `11.5f` (500m), or `10.5f` (1000m), ensuring the entire geofence circle remains cleanly contained within the 80dp container with ample padding.
* **Criterion 3 (Safe Boundary Clamping)**:
  - *Given* any radius value (even edge cases like 10m or 5000m),
  - *When* `calculateThumbnailZoom` is evaluated,
  - *Then* the zoom level SHALL be clamped strictly between `10.5f` and `12.5f`.
* **Criterion 4 (Offline Inspection Mode Invariant)**:
  - *Given* `LocalInspectionMode.current` active in Compose Previews or unit tests,
  - *When* `KnownLocationThumbnailMap` renders,
  - *Then* the offline schematic canvas circle and pin SHALL continue to render without calling Google Play Services.

### 1.4 System Invariants
1. Google Maps lite mode performance and gesture lock.
2. Single-tap card edit (`onEdit`) and long-press delete context menu (`REQ-UI-061`).
3. Starts and routes badge rows (`REQ-UI-195`, `REQ-UI-207`).
4. Full clean-room test regression pass.

---

## 2. Test Specification (`TST-UI-178`)

### Test Case 1: `KnownLocationZoomMathTest` (`[TST-UI-178.1]`)
* **Scope**: Pure Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationZoomMathTest.kt`
* **Test Procedures**:
  - `testDefaultZoom_isTwelve()`: Verifies baseline default zoom is `12.0f`.
  - `testSmallRadii_clampedToMaxZoom()`: Verifies that radii $\le 200\text{ m}$ (50m, 100m, 150m, 200m) clamp to `12.5f`.
  - `testMediumRadii_scalesProportionally()`: Verifies that 300m evaluates to $\approx 12.2\text{f} - 12.3\text{f}$ and 500m evaluates to $\approx 11.5\text{f}$.
  - `testLargeRadii_clampedToMinZoom()`: Verifies that 1000m evaluates to $\approx 10.5\text{f}$ and $>1000\text{ m}$ clamps to `10.5f`.
  - `testLatitudeVariations_producesStableZoom()`: Verifies reasonable behavior at equator ($\phi = 0^\circ$), mid-latitudes ($\phi = 48^\circ$), and polar bounds.
  - `testDegenerateInputs_clampsGracefully()`: Verifies negative radius, zero radius, or extreme values clamp strictly within `[10.5f, 12.5f]`.

### Test Case 2: Visual & Structural Contract Tests (`[TST-UI-178.2]`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt`
* **Test Procedures**:
  - `testMapPreviewThumbnail_usesCalibratedZoomMath()`: Verifies that `KnownLocationsScreen.kt` references `KnownLocationZoomMath.calculateThumbnailZoom`.
  - `testMapPreviewThumbnail_eliminatesHardcoded14_5Literal()`: Verifies that the literal `14.5f` is completely removed from `KnownLocationsScreen.kt`.
  - `testMapPreviewThumbnail_preservesLiteModeAndInspectionFallback()`: Verifies that `GoogleMapOptions().liteMode(true)` and `LocalInspectionMode.current` remain intact.

### Test Case 3: Clean-Room Regression Suite (`[TST-UI-178.3]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate with zero regressions across all project modules.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-178.1]` | Unit | `KnownLocationZoomMathTest.kt` | `REQ-UI-224` (item 1) | Specified |
| `[TST-UI-178.2]` | Contract | `KnownLocationCardLayoutTest.kt` | `REQ-UI-224` (item 2, 3) | Specified |
| `[TST-UI-178.3]` | Regression | `./gradlew testDebugUnitTest` | `REQ-PRO-001` | Specified |
