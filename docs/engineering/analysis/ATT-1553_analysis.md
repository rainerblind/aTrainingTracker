# Stage 1 Analysis: ATT-1553 - [Bug] [Map] [Dark Mode] Initial bright/white map flash when loading list views with maps

**Ticket**: [ATT-1553](https://atrainingtracker.atlassian.net/browse/ATT-1553)  
**Sub-task**: [ATT-1554](https://atrainingtracker.atlassian.net/browse/ATT-1554) (`[Analysis]`)  
**Parent Epic**: [ATT-1157](https://atrainingtracker.atlassian.net/browse/ATT-1157) (*Optimize dark mode*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.3`  
**Branch**: `feature/ATT-1553`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-28  

---

## 1. Problem Statement & Motivation

During on-device testing on physical hardware (Google Pixel 10) in Sprint 2026-40.2, list views containing map previews (e.g. `PeriodSummaryCard` in period summary lists, `PathPreviewMap` in route lists, and cluster preview cards in `WorkoutClusterComponents`) occasionally exhibit a brief, jarring bright/white flash of an unstyled map at the very moment a list item card mounts or scrolls into the viewport. Once the map finishes loading (50–300ms later), it renders properly with the dark AMOLED map style.

This transient glare compromises visual comfort in dark mode and contradicts the core objective of Epic ATT-1157 (AMOLED Dark Mode optimization).

---

## 2. Root Cause Analysis (Forensic Investigation)

### A. The Native Surface vs. Compose Modifier Rendering Gap
In ATT-1479, universal dynamic map property resolution (`DarkMapStyle.resolveMapProperties`) was implemented, and `Modifier.background(if (isDark) Color(0xFF121212) else Color.White)` was added to `GoogleMap` composables and their parent containers.

However, forensic analysis reveals why this background modifier is insufficient to prevent the flash:
1. **AndroidView / Native Surface Ownership**: The Jetpack Compose `GoogleMap` composable wraps the native Google Maps SDK `MapView` (or `TextureMapView`) via `AndroidView`.
2. **Layering of Background Modifiers**: In Compose, the `Modifier.background(Color(0xFF121212))` applied to `GoogleMap` is drawn on the Compose layout canvas *behind* the native view hierarchy.
3. **Native View Initialization**: When the native `MapView` is attached to the window, the Google Maps C++/OpenGL rendering engine immediately draws its default surface. The default base vector tiles and background of Google Maps are bright cream/white.
4. **Asynchronous Style Dispatch**: The custom JSON map styling (`res/raw/map_style_dark.json`) provided via `MapProperties.mapStyleOptions` is parsed, dispatched to the Maps SDK render thread, and applied to the vector tile pipeline asynchronously.
5. **The Glitch Window**: Between the instant the native `MapView` surface draws its initial frame and the moment custom styling and tiles are fully rendered (signaled by `onMapLoaded`), the native surface renders *in front* of the Compose background, exposing the unstyled light map to the athlete for 50–300ms.

### B. Why List Views Are Particularly Vulnerable
In single-instance screens (e.g., Cockpit or Dialogs), the map mounts once and remains in memory. In contrast, list views (`LazyColumn` in Period lists, Route lists, Cluster lists):
* Rapidly mount, unmount, and recycle map cards as the athlete scrolls.
* Every newly visible card instantiates or re-attaches a `GoogleMap` instance.
* Each card lifecycle transition triggers the asynchronous Maps SDK initialization window, producing recurring white flashes during scrolling.
* Currently, none of the list card map implementations (`PeriodSummaryCard.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`) have any visual mask or overlay rendered *on top* of the `GoogleMap` while `isMapLoaded == false`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Eliminate the initial bright/white map flash when loading or scrolling list views containing maps in dark mode.
  2. Implement an active **Anti-Flash Surface Mask / Overlay** (`#121212`) placed *on top* of `GoogleMap` instances that remains opaque until `onMapLoaded` fires.
  3. Ensure a reusable, elegant component or composable helper (e.g. `DarkMapAntiFlashOverlay` in `DarkMapStyle.kt`) that can be applied across all preview map composables (`PeriodSummaryCard`, `PathPreviewMap`, `WorkoutClusterComponents`, `ManualClusterScreen`, `LapEditBottomSheet`, `EditKnownLocationDialog`).
  4. Ensure smooth crossfade or transition when `isMapLoaded` becomes true.
  5. Preserve all existing light mode behaviors (`MapType.TERRAIN`, null style options) and 100% unit test suite pass rate.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Replacing Google Maps SDK with static pre-rendered raster bitmaps or static map snapshot APIs (too complex, breaks interactive pinch-to-zoom in preview dialogs and requires backend API keys).
  * Changing route polyline colors or cluster detection algorithms.
  * Modifying unrelated list card layouts.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**: `REQ-MAP-021` (`Dark Mode Map Styling for Live Route Tracking, Navigation, Cockpit, and Secondary Map Views`), targeting `DarkMapStyle.kt`, `PeriodSummaryCard.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`.
* **Historical Origin & Commit Trace**: Commit `ceaf4070` (ATT-1479, 2026-09-28) and `5d8a0c23` (ATT-1266, 2026-09-12).
* **Root Reason for Existing Formulation**: ATT-1479 introduced `DarkMapStyle.resolveMapProperties` and double-layer background protection behind the map. It assumed the background modifier on `GoogleMap` would mask loading. Testing on physical hardware revealed that native `MapView` renders unstyled base tiles in front of the Compose background before style dispatch completes.
* **Preservation of Core Invariants**: Refining `REQ-MAP-021` to mandate an **Active Anti-Flash Surface Mask** placed on top of `GoogleMap` until `onMapLoaded` preserves all existing guarantees:
  * Light mode baseline (`MapType.TERRAIN`) remains completely intact.
  * Singleton caching (<10 KB RAM, <3ms parse) in `DarkMapStyle` remains intact.
  * Zero impact on tracking performance or thread dispatchers.

---

## 5. Architectural Strategy & High-Level Solution

### A. Reusable Composable: `DarkMapAntiFlashOverlay` in `DarkMapStyle.kt`
Provide a lightweight, reusable overlay composable:
```kotlin
@Composable
fun DarkMapAntiFlashOverlay(
    isMapLoaded: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF121212)
) {
    if (isDark && !isMapLoaded) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(backgroundColor)
        )
    }
}
```

### B. Map Container Layering in List Previews
In each list map card (`PeriodSummaryCard.kt`, `PathPreviewMap.kt`, `WorkoutClusterComponents.kt`), wrap `GoogleMap` in a `Box` and place `DarkMapAntiFlashOverlay` on top:
```kotlin
Box(modifier = modifier.background(if (isDark) Color(0xFF121212) else Color.White)) {
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = mapProperties,
        onMapLoaded = { isMapLoaded = true },
        ...
    ) { ... }

    // Active anti-flash surface mask obscures native light tiles until style and tiles finish rendering
    DarkMapAntiFlashOverlay(
        isMapLoaded = isMapLoaded,
        isDark = isDark
    )
}
```

### C. Benefits
1. **Zero Flash Guarantee**: Because the overlay is rendered on top of the native `MapView` in the Compose render tree, any unstyled light tiles painted by the native Maps SDK are completely obscured.
2. **Seamless Reveal**: As soon as `onMapLoaded` fires, the overlay is dismissed, revealing the fully rendered dark map with polylines and markers.
3. **Performance Efficiency**: When `isDark == false`, the overlay never renders, ensuring zero overhead in light mode.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. `onMapLoaded` callback MUST be preserved and triggered reliably across both Lite Mode and Normal Mode GoogleMap instances.
  2. Light mode baseline (`MapType.TERRAIN`) MUST NOT display an unwanted dark overlay.
  3. Memory and CPU overhead MUST remain negligible during rapid `LazyColumn` scrolling.
  4. 100% pass rate across all existing unit and Compose tests.

* **Risk Rating**: **LOW**
  * The overlay is purely additive at the UI presentation layer, requires zero schema changes, and introduces no background thread dependencies.
