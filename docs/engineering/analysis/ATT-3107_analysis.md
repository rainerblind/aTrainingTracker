# Stage 1 Analysis: ATT-3107 - Display elevation profile with adequate vertical height when map is disabled on tracking screen

**Ticket**: [ATT-3107](https://atrainingtracker.atlassian.net/browse/ATT-3107)  
**Sub-task**: [ATT-3148](https://atrainingtracker.atlassian.net/browse/ATT-3148) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.8`  
**Branch**: `feature/ATT-3107`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Problem Statement & Motivation

When an athlete enables the elevation profile on the live tracking screen (`SensorGridScreen` in `ScreenMode.TRACKING`) with the map disabled (`showMap == false` and `showElevationProfile == true`), the elevation profile is rendered as an unusable thin slit with virtually zero vertical height (~15–20dp), displaying only the horizontal distance axis and clipping the actual elevation curve and gradient shading.

The athlete expects that when the map is toggled off, the elevation profile takes advantage of the screen real estate vacated by the map (or is guaranteed adequate vertical height $\ge 120\text{dp}$ with a scrollable sensor grid), allowing clear visualization of upcoming gradients and elevation pacing during the ride.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `SensorGridScreen.kt` (lines 480–565) and `ElevationProfile.kt` (lines 410–415) revealed three compounding layout issues:

### 2.1 Unconstrained Sensor Grid Growth
In `SensorGridScreen.kt` (lines 485–491):
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Top
)
```
The sensor grid `Column` does not declare any vertical weight constraint (such as `Modifier.weight(1f, fill = false)`). In Compose `Column` layout, unweighted children measure first and consume their full desired height. For an athlete with 4 to 6 rows of sensor metrics, the sensor grid occupies nearly 100% of the display viewport height.

### 2.2 Discarded Weight on Hidden Map
In `SensorGridScreen.kt` (lines 523–528):
```kotlin
if (state.mapState.showMap) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f) // Fills remaining space
    ) { ... }
}
```
The map container declared `Modifier.weight(1f)`, taking all remaining vertical space when the map was enabled. However, when `showMap` is `false`, the `if (state.mapState.showMap)` block emits nothing, and the `1f` weight allocation disappears entirely from the parent `Column`.

### 2.3 Hardcoded Canvas Height in `ElevationProfile`
In `ElevationProfile.kt` (lines 411–415):
```kotlin
Box(modifier = modifier.fillMaxWidth()) {
    val baseCanvasModifier = Modifier
        .fillMaxWidth()
        .height(totalCanvasHeight)
```
Even if a caller passes `Modifier.fillMaxSize()` or `Modifier.weight(1f)`, `baseCanvasModifier` hardcoded `.height(totalCanvasHeight)` unconditionally, preventing the `Canvas` from expanding to fill available parent height. Furthermore, when unconstrained, `ElevationProfile` with fixed height is pushed against the bottom screen boundary by the unconstrained sensor grid, clipping the canvas.

---

## 3. Chesterton's Fence Archaeology & Invariant Preservation

1. **`ElevationProfileLayoutTest.kt` Invariants**:
   - `ElevationProfileLayoutTest` verifies that `val totalCanvasHeight = if (showZoomControls) cachedData.adaptiveHeight + 28.dp else cachedData.adaptiveHeight` is preserved.
   - List previews (`WorkoutSummary.kt`, `RouteItem.kt`, `SegmentItem.kt`) rely on `ElevationProfile` calculating its natural height when unconstrained.
   - *Risk Mitigation*: Unconditionally altering `baseCanvasModifier` to `.fillMaxHeight()` risks infinite/unbounded height measurement issues in unconstrained parents like `LazyColumn` or wrap-content containers.
   - *Resolution*: Decouple canvas sizing using an explicit parameter: `fillParentHeight: Boolean = false`.
     - When `fillParentHeight == false` (default across all existing callers), the canvas continues using `.height(totalCanvasHeight)`.
     - When `fillParentHeight == true` (explicitly requested in weighted tracking containers), the canvas uses `Modifier.defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`, cleanly expanding into the weighted space without side effects.

2. **Sparse / Single-Point PathData Safety**:
   - When `pathPoints.size < 2`, no meaningful elevation graph or gradient polygon can be drawn.
   - In `SensorGridScreen.kt`, guard elevation profile rendering with `if (state.showElevationProfile && state.pathPoints.size >= 2)`. This prevents displaying an empty, stretched canvas artifact when GPS tracking has just started or no track points exist yet.

3. **Map + Elevation Profile Coexistence Invariant**:
   - When both map and elevation profile are enabled (`showMap == true` and `showElevationProfile == true`), the map retains its primary role as the flexible weighted element (`weight(1f)`), and the elevation profile below it renders at its standard adaptive height (`fillParentHeight = false`).

4. **Sensor Grid Wrap-Content Behavior**:
   - For minimalist sensor setups (e.g. 1 or 2 rows), the sensor grid should NOT stretch to fill 50% of the screen. Using `Modifier.weight(1f, fill = false)` ensures it only takes its natural wrapped height when small, while capping growth when many rows are configured so bottom cockpit elements are never crowded out.

5. **Navigation Hints Overlay (REQ-UI-335 / ATT-3100)**:
   - When `showMap == false`, `NavigationHintsOverlay` floats at `Alignment.TopCenter` of the root Box. This remains completely intact.

---

## 4. User Scope Grounding (ATT-1250)

In accordance with ATT-1250:
- Scope is strictly bounded to vertical layout weight management in `SensorGridScreen.kt` and adaptive canvas height sizing in `ElevationProfile.kt`.
- No state management, sensor logic, or database changes are required.
- Sensor grid scrollability, map isolation barriers, and preview modes remain fully intact.

---

## 5. Architectural Solution (SWE.2)

### 5.1 Flexible Weight on Sensor Grid (`SensorGridScreen.kt`)
Apply `Modifier.weight(1f, fill = false)` to the sensor grid `Column`:
```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .weight(1f, fill = false)
        .verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = if (effectiveSpacing > 0.dp) Arrangement.spacedBy(effectiveSpacing) else Arrangement.Top
) { ... }
```

### 5.2 Dynamic Weight & Explicit Expansion for Elevation Profile (`SensorGridScreen.kt`)
Guard with `state.pathPoints.size >= 2`, apply `Modifier.weight(1f)` when `!state.mapState.showMap`, and pass `fillParentHeight = !state.mapState.showMap`:
```kotlin
if (state.showElevationProfile && state.pathPoints.size >= 2) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!state.mapState.showMap) Modifier.weight(1f) else Modifier
            )
            .padding(top = 8.dp)
    ) {
        ElevationProfile(
            pathPoints = state.pathPoints,
            currentDistance = state.pathPoints.lastOrNull()?.distance,
            fillParentHeight = !state.mapState.showMap,
            modifier = if (!state.mapState.showMap) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
        )
    }
}
```

### 5.3 Decoupled Canvas Height in `ElevationProfile.kt`
Add parameter `fillParentHeight: Boolean = false` to `ElevationProfile` overloads:
```kotlin
val baseCanvasModifier = if (fillParentHeight) {
    Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = totalCanvasHeight)
        .fillMaxHeight()
} else {
    Modifier
        .fillMaxWidth()
        .height(totalCanvasHeight)
}
```
- Existing callers (list items, workout summaries, map detail sheets) default to `fillParentHeight = false` and retain exact byte-for-byte behavior.
- Weighted tracking view passes `fillParentHeight = true`, allowing the canvas to scale dynamically to $\ge 120\text{dp}$.

---

## 6. Verification & Test Strategy (SWE.3 / SWE.4)

1. **Contract Tests (`ElevationProfileLayoutTest.kt` & `SensorGridScreenLayoutTest.kt`)**:
   - Verify `SensorGridScreen.kt` applies `weight(1f, fill = false)` to the sensor grid `Column`.
   - Verify `SensorGridScreen.kt` conditionally applies `Modifier.weight(1f)` to `ElevationProfile` when `!state.mapState.showMap`.
   - Verify `SensorGridScreen.kt` passes `fillParentHeight = !state.mapState.showMap`.
   - Verify `ElevationProfile.kt` defaults `fillParentHeight: Boolean = false`.
   - Verify `ElevationProfile.kt` attaches `defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()` when `fillParentHeight == true`.
2. **Clean-Room Regression**:
   - Run `./gradlew testDebugUnitTest` verifying 100% test pass rate with zero regressions.
