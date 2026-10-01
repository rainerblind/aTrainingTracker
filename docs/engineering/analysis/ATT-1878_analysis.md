# Stage 1: Problem Domain & Root Cause Analysis - ATT-1878: Calibrate Zoom Level on KnownLocationCard Map Thumbnail to Show Neighborhood Context

**Ticket**: [ATT-1878](https://rainerblind.atlassian.net/browse/ATT-1878)  
**Sub-task**: [ATT-1912](https://rainerblind.atlassian.net/browse/ATT-1912) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & User Impact

On the saved favorite start locations screen (`KnownLocationsScreen.kt`), each `KnownLocationCard` displays an 80dp square map preview thumbnail (`KnownLocationThumbnailMap`) on the right side of the card. In its initial implementation under `REQ-UI-217` (ATT-1816), the camera was hardcoded to a fixed zoom level of `14.5f`.

On-device physical testing on the Pixel 10 revealed two critical UX problems:
1. **Lack of Neighborhood Context**: At zoom `14.5f`, the visible span across an 80dp thumbnail on a 420 dpi display is only $\sim 360\text{ meters}$. Only 1–2 houses or a single street intersection are visible, leaving athletes unable to orient themselves geographically or recognize the surrounding neighborhood, city district, lake, or park.
2. **Geofence Boundary Clipping**: For typical start locations with geofence radii of 200m or 300m, the circular boundary diameter ($400\text{ m} - 600\text{ m}$) exceeds the visible ground width of the 80dp thumbnail ($360\text{ m}$), causing the circle to clip against the edges of the card and bleed outside the viewport.

Athletes require the map thumbnail to be zoomed out sufficiently so that the circular geofence boundary is fully contained with comfortable margins, and the surrounding neighborhood context (arterial roads, district features, terrain) is immediately recognizable at a glance.

---

## 2. Forensic Investigation & Root Cause

### 2.1 Code Archeology
Inspection of `KnownLocationsScreen.kt` (lines 391–400):
```kotlin
val cameraPositionState = rememberCameraPositionState {
    position = CameraPosition.fromLatLngZoom(item.latLng, 14.5f)
}
var isMapLoaded by remember { mutableStateOf(false) }

LaunchedEffect(item.latLng, isMapLoaded) {
    if (isMapLoaded) {
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(item.latLng, 14.5f))
    }
}
```

### 2.2 Mathematical Root Cause
In Web Mercator projection (used by Google Maps), the ground resolution in meters per display point at zoom $Z$ and latitude $\phi$ is:
$$\text{meters/dp} = \frac{156543.03392 \times \cos(\phi)}{2^Z}$$

For an 80dp thumbnail at Central European latitudes ($\phi \approx 48^\circ$, $\cos(48^\circ) \approx 0.669$):
$$\text{visibleSpanMeters} = 80 \times \frac{156543.03392 \times 0.66913}{2^Z} \approx \frac{8,380,000}{2^Z}$$

- At $Z = 14.5$: $2^{14.5} \approx 23,170 \implies \text{visibleSpan} \approx 361\text{ meters}$.
  - A geofence of $R = 200\text{ m}$ has diameter $400\text{ m} > 361\text{ m}$ (110% of thumbnail width $\implies$ clipped).
  - A geofence of $R = 300\text{ m}$ has diameter $600\text{ m} > 361\text{ m}$ (166% of thumbnail width $\implies$ clipped).
- At $Z = 12.5$: $2^{12.5} \approx 5,793 \implies \text{visibleSpan} \approx 1,446\text{ meters}$.
  - A geofence of $R = 200\text{ m}$ has diameter $400\text{ m}$ ($\approx 28\%$ of thumbnail width $\implies$ perfectly centered with $1\text{ km}$ of surrounding neighborhood).
- At $Z = 11.5$: $2^{11.5} \approx 2,896 \implies \text{visibleSpan} \approx 2,893\text{ meters}$.
  - A geofence of $R = 500\text{ m}$ has diameter $1000\text{ m}$ ($\approx 35\%$ of thumbnail width).

---

## 3. Chesterton's Fence Audit

1. **Origin**: `REQ-UI-217` introduced `KnownLocationThumbnailMap` in Sprint 2026-40.7 (`ATT-1816`) to provide visual spatial previews.
2. **Why 14.5f was selected**: `14.5f` was borrowed from full-screen map views (`MapScreenWithTrack`, `MapDetailLayout`), where the screen is $\ge 400\text{ dp}$ wide ($\text{visibleSpan} \approx 1.8\text{ km}$). When scaled down to an 80dp container without adjusting zoom, the visible area shrunk five-fold.
3. **Core Invariants to Preserve**:
   - `KnownLocationThumbnailMap` dimensions: `80.dp`, `RoundedCornerShape(12.dp)`, `surfaceContainerHigh`.
   - Lite mode configuration: `GoogleMapOptions().liteMode(true)` with all gestures disabled.
   - Marker: `createHeartPinMarker` centered at `item.latLng`.
   - Circle: `Circle` centered at `item.latLng` with radius `item.radius`.
   - Offline inspection mode: `LocalInspectionMode.current` drawing schematic canvas circle and place icon.
   - Click navigation: `onShowOnMap()` on thumbnail tap.
   - Location card invariants: single-tap edit, long-press delete context menu (`REQ-UI-061`), starts and routes badge rows (`REQ-UI-195`, `REQ-UI-207`).

---

## 4. Proposed Solution & Architecture

### 4.1 Pure Calculation Helper (`KnownLocationZoomMath.kt`)
To guarantee 100% testability and prevent UI clutter, encapsulate zoom calculation in an isolated pure object:
- `DEFAULT_THUMBNAIL_ZOOM = 12.0f`: Baseline regional zoom level.
- `MIN_ZOOM = 10.5f`: Maximum zoom-out floor (prevents zooming out past ~6 km for large 1000m geofences).
- `MAX_ZOOM = 12.5f`: Maximum zoom-in ceiling (guarantees at least 1.45 km of surrounding neighborhood context even for small 50m geofences).
- `TARGET_GEOFENCE_DIAMETER_RATIO = 0.35f`: Desired ratio of geofence diameter to thumbnail width (35%).
- `fun calculateThumbnailZoom(radiusMeters: Int, latitude: Double = 0.0): Float`:
  Calculates the optimal zoom level so that the geofence occupies approximately 35% of the thumbnail, clamped strictly within `[MIN_ZOOM, MAX_ZOOM]`.

### 4.2 Dynamic Integration in `KnownLocationThumbnailMap`
- In `KnownLocationsScreen.kt`, compute `val targetZoom = remember(item.radius, item.latLng.latitude) { KnownLocationZoomMath.calculateThumbnailZoom(item.radius, item.latLng.latitude) }`.
- Pass `targetZoom` to `CameraPosition.fromLatLngZoom(item.latLng, targetZoom)` and update `LaunchedEffect(item.latLng, targetZoom, isMapLoaded)`.

---

## 5. Scope & Risks

- **Risk Level**: Minimal. No database changes, no API breaking changes, pure UI presentation calibration.
- **Affected Files**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationZoomMath.kt` (New)
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationsScreen.kt` (Modified)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationZoomMathTest.kt` (New)
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/knownlocations/KnownLocationCardLayoutTest.kt` (Updated)
