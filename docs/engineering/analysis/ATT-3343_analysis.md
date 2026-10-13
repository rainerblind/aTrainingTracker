# Stage 1 Analysis: ATT-3343 - Ensure tracking map fills all remaining vertical space above elevation profile

**Ticket**: [ATT-3343](https://atrainingtracker.atlassian.net/browse/ATT-3343)  
**Sub-task**: [ATT-3438](https://atrainingtracker.atlassian.net/browse/ATT-3438) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Branch**: `feature/ATT-3343`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

### Requirement Archaeology & Chesterton's Fence Audit

1. **Original Requirement ID & Target**:
   - Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107), and complements `REQ-UI-355` (*Active Route Full Elevation Profile with Map-Matched Position Marker, Categorized Climbs & Per-Tab Rolling Window Scoping During Tracking*, Sprint 2026-41.10 / Sprint 2026-42.1, ATT-3199 & ATT-3317).
   - Formulated as `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*).
   - Targets `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt` and `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`.
2. **Historical Origin & Commit Trace**:
   - Sprint 2026-41.8 (`ATT-3107`, commit `8d6ef4e1`): Introduced `Modifier.weight(1f, fill = false)` on the sensor grid Column to prevent sensor rows from crowding the elevation profile when the map is disabled.
   - Sprint 2026-41.9 (`ATT-3173`, commit `ad0c2020`): Excised `Modifier.weight(1f)` from the elevation profile `Surface` to eliminate half-screen stretching when the map is hidden, establishing canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
3. **Root Reason for Existing Formulation**:
   - In `ATT-3107`, `weight(1f, fill = false)` was placed on the sensor grid Column so it wraps content for 1–2 rows and scrolls for many rows without pushing bottom cockpit views off screen.
   - However, when the map is visible (`state.mapState.showMap == true`), the map is also assigned `Modifier.weight(1f)`.
   - In Jetpack Compose `Column`, weighted children divide the available remaining space proportionally according to their weights. Because both the sensor grid and the map declare weight `1f`, total weight is `2f` (50% quota each).
   - Because the sensor grid specifies `fill = false`, it only consumes its intrinsic content height (e.g. 1 row = ~80dp). Crucially, Compose does *not* reallocate the unused portion of the sensor grid's 50% quota to the other weighted child (the map).
   - The map remains hard-capped at its 50% allocation. The unweighted elevation profile renders directly below the map, leaving the remaining unused 50% space (~250–350dp) completely empty as dead white space at the bottom of the screen.
4. **Preservation of Core Invariants**:
   - **Invariant 1 (Map-Disabled Invariant)**: When `state.mapState.showMap == false`, the sensor grid retains `Modifier.weight(1f, fill = false)` so it wraps content for 1–2 rows and scrolls smoothly for 4–6 rows, and the elevation profile renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) directly below it (`REQ-UI-346`).
   - **Invariant 2 (Adaptive Height Invariant)**: The elevation profile retains its canonical adaptive height computed via `calculateElevationProfileHeight(range)` with non-weighted wrapping `Surface` (`padding(top = 8.dp)`).
   - **Invariant 3 (Sensor Grid Containment Invariant)**: When the map is visible, the sensor grid wraps content naturally when few rows exist, and is bounded to a maximum proportional constraint (`heightIn(max = availableHeight * 0.5f)`) with internal scrolling when many rows exist, preventing it from starving the map.
   - **Invariant 4 (Full Flexible Expansion Invariant)**: When the map is visible, the map Box is the sole flexible weight consumer (`Modifier.weight(1f, fill = true)`), expanding to consume 100% of all vertical space remaining between the sensor grid and the elevation profile (or bottom edge).
   - **Invariant 5 (System Stability)**: Recomposition isolation (`TrackingMapContainer`), ambient navigation overlays, live climb sheets, and 100% full-suite unit test pass rate MUST be strictly preserved.

---

## 1. Problem Statement & Motivation

During live workout tracking on devices with high-resolution screens (e.g. Google Pixel 10), athletes configuring a tracking tab with sensor tiles, a map, and an elevation profile (e.g. the standard "Karte" tab) observe severe visual layout anomalies:

1. **Dead White Space**:
   - When 1 or 2 rows of sensor tiles are configured, the map occupies only a narrow band (~40% of the display), leaving a massive blank white gap (30–40% of screen height) between the elevation profile and the bottom cockpit boundary.
2. **Suboptimal Map Visibility**:
   - Athletes following active routes or navigating unfamiliar terrain rely on maximum map viewport size to anticipate turns, forks, and upcoming climbs. Having nearly half the screen wasted as an empty white rectangle degrades situational awareness and ergonomics.

Eliminating this dead space by ensuring the map consumes 100% of all remaining vertical space restores an immersive edge-to-edge cockpit experience.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 The Compose Layout Geometry in `SensorGridScreen.kt`

In `SensorGridScreen.kt:523-615`:
```kotlin
Box(
    modifier = Modifier
        .fillMaxSize()
        .padding(top = paddingValues.calculateTopPadding())
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Child 1: Sensor Grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .weight(1f, fill = false),
            ...
        ) { ... }

        // Child 2: Map Box
        if (state.mapState.showMap) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                TrackingMapContainer(...)
            }
        }

        // Child 3: Elevation Profile Surface
        if (state.showElevationProfile && resolvedProfile.pathPoints.size >= 2) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                ElevationProfile(...)
            }
        }
    }
}
```

### 2.2 Mathematical Measurement Breakdown

Suppose the available viewport height is $H_{\text{total}} = 800\text{dp}$.
- **Step 1 (Unweighted Children Measurement)**:
  - Compose measures non-weighted children first: Child 3 (`ElevationProfile`) has no weight and measures at its canonical adaptive height, e.g., $H_{\text{elev}} = 120\text{dp} + 8\text{dp} = 128\text{dp}$.
- **Step 2 (Remaining Space Calculation)**:
  - Available space for weighted children:
    $$H_{\text{rem}} = H_{\text{total}} - H_{\text{elev}} = 800\text{dp} - 128\text{dp} = 672\text{dp}$$
- **Step 3 (Weight Distribution)**:
  - Child 1 (Sensor Grid) has weight `1f`.
  - Child 2 (Map Box) has weight `1f`.
  - Total weight sum = $1\text{f} + 1\text{f} = 2\text{f}$.
  - Quota allocated to Sensor Grid = $672\text{dp} \times \frac{1}{2} = 336\text{dp}$.
  - Quota allocated to Map Box = $672\text{dp} \times \frac{1}{2} = 336\text{dp}$.
- **Step 4 (Measurement & The Dead Space Trap)**:
  - Sensor Grid has `fill = false`. With 1 row of sensor tiles, its intrinsic content height is only $H_{\text{grid}} \approx 80\text{dp}$. Because `fill = false`, Compose measures the Sensor Grid at $80\text{dp}$.
  - **Crucial Compose behavior**: The unconsumed remainder ($336\text{dp} - 80\text{dp} = 256\text{dp}$) is **NOT** reallocated to Child 2! Child 2 was already assigned its fixed quota of $336\text{dp}$.
  - Child 2 (Map Box) has `fill = true` and measures at exactly $336\text{dp}$.
  - Total height utilized by Column:
    $$H_{\text{used}} = 80\text{dp} + 336\text{dp} + 128\text{dp} = 544\text{dp}$$
  - **Dead white space remaining**:
    $$H_{\text{gap}} = 800\text{dp} - 544\text{dp} = 256\text{dp}$$
  - Exactly 32% of the screen is rendered as dead white space!

---

## 3. Proposed Solution & Technical Architecture

### 3.1 Asymmetric Weight Allocation with Proportional Height Bounding

To eliminate symmetric weight competition while preserving all invariants:

1. **Top-Level `BoxWithConstraints`**:
   - In `SensorGridScreen.kt`, wrap the cockpit content in `BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding()))`.
   - Expose `availableHeight = maxHeight`.
2. **Conditional Sensor Grid Modifier**:
   - When the map is visible (`state.mapState.showMap == true`):
     - Remove `weight` entirely from the sensor grid Column!
     - Instead, constrain the sensor grid to wrap content naturally while bounding it to at most 50% of available height:
       ```kotlin
       Modifier
           .fillMaxWidth()
           .heightIn(max = availableHeight * 0.5f)
           .verticalScroll(rememberScrollState())
       ```
     - For 1–2 rows (~80–160dp), the sensor grid measures at its exact content height without stealing or reserving unused weight quota.
     - For 4–6 rows, the sensor grid is capped at 50% of screen height and scrolls internally, preventing sensor overflow from pushing the map or elevation profile off screen.
   - When the map is hidden (`state.mapState.showMap == false`):
     - Retain `Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).weight(1f, fill = false)` per `REQ-UI-346`.
3. **Sole Flexible Map Weight Consumer**:
   - The map Box is assigned:
     ```kotlin
     Modifier
         .fillMaxWidth()
         .weight(1f, fill = true)
     ```
   - Because the map is now the **only** weighted child in the Column when `showMap == true`, Compose allocates **100% of all remaining vertical space** to the map!
   - Mathematical verification:
     $$H_{\text{map}} = H_{\text{total}} - H_{\text{grid}} - H_{\text{elev}} = 800\text{dp} - 80\text{dp} - 128\text{dp} = 592\text{dp}$$
     $$H_{\text{used}} = 80\text{dp} + 592\text{dp} + 128\text{dp} = 800\text{dp}$$
     $$H_{\text{gap}} = 0\text{dp}$$
   - Zero dead space! The map expands smoothly and completely fills all space between the sensor grid and the elevation profile.

---

## 4. Scope Bounding (In-Scope vs. Out-of-Scope)

### In-Scope:
- Update `SensorGridScreen.kt` cockpit container with `BoxWithConstraints` and asymmetric weight allocation.
- Ensure sensor grid scrolls cleanly when row count is large and map is visible.
- Ensure map consumes 100% of remaining space when visible (with or without elevation profile).
- Retain exact existing behavior when map is hidden.
- Add comprehensive AST and layout contract tests in `SensorGridScreenLayoutTest.kt`.
- Run clean-room regression test suite (`./gradlew testDebugUnitTest`).

### Out-of-Scope:
- Modifying `ATrainingTrackerMap` camera behaviors or follow-me zoom parameters.
- Modifying `ElevationProfile` internal drawing or height calculations.
- Altering database schema or view configuration storage.

---

## 5. Verification Strategy & Test Plan

1. **Static AST & Contract Tests (`SensorGridScreenLayoutTest.kt`)**:
   - Verify `SensorGridScreen.kt` declares `BoxWithConstraints`.
   - Verify sensor grid modifier conditionally applies `heightIn(max = availableHeight * 0.5f)` when `showMap` is true.
   - Verify sensor grid modifier retains `.weight(1f, fill = false)` when `showMap` is false.
   - Verify map Box declares `Modifier.weight(1f, fill = true)`.
2. **Clean-Room Test Execution**:
   - Run `./gradlew testDebugUnitTest` and assert 100% pass rate.
