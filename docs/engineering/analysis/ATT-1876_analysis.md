# Stage 1: Problem Domain & Root Cause Analysis - ATT-1876: Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs

**Ticket**: [ATT-1876](https://rainerblind.atlassian.net/browse/ATT-1876)  
**Sub-task**: [ATT-1917](https://rainerblind.atlassian.net/browse/ATT-1917) (`[Analysis]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Statement & User Impact

In Sprint 2026-40.6, ticket `ATT-1814` (`REQ-UI-215`) successfully hoisted horizontal zoom state in `MapDetailLayout.kt`, synchronizing the horizontal window across all four stacked continuous charts:
1. Elevation Profile (`ElevationProfile.kt`)
2. Speed/Pace Graph (`TelemetryMetricGraph.kt`)
3. Heart Rate Graph (`TelemetryMetricGraph.kt`)
4. Cycling Power Graph (`TelemetryMetricGraph.kt`)

However, the interactive zoom controls (`+`, `-`, Pan/Scrub mode toggle, and Reset pill) remained physically embedded inside `ElevationProfile.kt` (lines 718–812) underneath the local section heading *"Höhenprofil"*.

This architecture manifests two critical usability defects on physical devices:
1. **Loss of Controls During Deep Inspection**: The lower container of `MapDetailLayout` is vertically scrollable (`Modifier.verticalScroll(...)`). When an athlete scrolls down to inspect the Speed/Pace curve, Heart Rate zones, Cycling Power spikes, or lap interval splits, the zoom buttons scroll completely out of view. In order to zoom back out, pan, or reset the window to 1.0x, the athlete is forced to scroll all the way back up to the top of the container, interrupting the analysis workflow.
2. **Semantic Mismatch & False Component Coupling**: Placing global viewport controls inside one specific child chart (the Elevation Profile) falsely conveys that zoom only affects elevation data. In reality, zoom scales all four stacked graphs in lockstep. Coupling global controls to a child chart violates component encapsulation and creates awkward visual padding constraints inside `ElevationProfile.kt`.

Athletes need a dedicated, persistent, sticky global zoom toolbar positioned directly between the Map viewport (or `SplitPaneDivider`) and the scrollable telemetry graph container so that zoom and pan controls remain stationary and accessible at all times, regardless of vertical scroll depth.

---

## 2. Forensic Investigation & Root Cause

### 2.1 Code Archaeology: Embedded Zoom Controls in `ElevationProfile.kt`
Lines 718–812 of `ElevationProfile.kt` render the zoom controls directly inside the profile's root `Box`:
```kotlin
if (showZoomControls && cachedData.totalDist > 10.0) {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 50.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(onClick = { /* apply zoom in */ }) { ... }
        IconButton(onClick = { /* apply zoom out */ }) { ... }
        IconButton(onClick = { isPanMode = !isPanMode }) { ... }
        if (currentZoomScale > 1.01f) {
            Surface(onClick = { updateZoom(1.0f, 0.0) }) { ... }
        }
    }
}
```
Because `ElevationProfile` resides inside `lowerColumn` in `MapDetailLayout.kt` (lines 201–302):
```kotlin
val lowerColumn: @Composable (Modifier) -> Unit = { colModifier ->
    Column(modifier = colModifier) {
        if (showElevationProfile) {
            ...
            ElevationProfile(...)
            ...
            TelemetryMetricGraph(SPEED)
            TelemetryMetricGraph(HR)
            TelemetryMetricGraph(POWER)
        }
    }
}
```
And `lowerColumn` is attached to:
```kotlin
lowerColumn(
    Modifier
        .weight(1f - splitFraction)
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
)
```
Any vertical scroll event applied to the lower column causes `ElevationProfile`—and its embedded zoom row—to scroll upwards offscreen.

### 2.2 Layout Geometry & Vertical Space Inefficiencies
To prevent the embedded zoom row from colliding with the canvas and `ScrubbingTelemetryBadge`, `ElevationProfile.kt` previously had to allocate significant top padding:
- `val topPadding = if (showZoomControls) 72.dp else 16.dp`
- `val totalCanvasHeight = if (showZoomControls) cachedData.adaptiveHeight + 48.dp else cachedData.adaptiveHeight`
This extra 48–56 dp of padding pushed the elevation curve down and reduced the usable vertical area for chart plotting. Extracting the controls out of `ElevationProfile` allows the profile to reclaim this vertical space.

---

## 3. Chesterton's Fence Archaeology & Requirement Trace

| Requirement | Originating Ticket | Historical Intent | Invariant to Preserve |
|---|---|---|---|
| `REQ-UI-192` | `ATT-527` | Interactive Elevation Profile Zoom, Pan, Centroid Scaling Math (`ElevationProfileZoomMath.kt`). | Mathematical calculations (`applyZoomAtCentroid`, `applyPan`, `clampStartDistance`, `calculateVisibleDistance`) remain 100% untouched. |
| `REQ-UI-197` | `ATT-1647`, `ATT-1736`, `ATT-1737` | Contextual zoom controls visibility (`showZoomControls = false` in previews and LiveSegment sheet). | Preview items and ambient sheets continue to suppress zoom controls. |
| `REQ-UI-201` | `ATT-1391` | Synchronized multi-metric scrubbing badge (`ScrubbingTelemetryBadge`). | Telemetry badge continues displaying synchronized HR, Power, Speed, and Altitude during scrubbing without regression. |
| `REQ-UI-215` | `ATT-1814` | Global horizontal zoom synchronization across all stacked telemetry graphs. | `MapDetailLayout` continues hoisting `profileZoomScale` and `profileStartDist` and feeding all 4 charts. |
| `REQ-UI-223` | `ATT-1890` | Resizable split-pane viewport with `SplitPaneDivider`. | Draggable splitter continues partitioning height between mapBox and lowerColumn; divider touch height remains 24.dp. |

### Core Invariants to Preserve
1. **Mathematical Invariant**: `ElevationProfileZoomMath` is the single source of truth for zoom and panning mathematics.
2. **Gesture Invariant**: Direct canvas gestures (pinch-to-zoom on graph surface) remain functional when `showZoomControls == true`.
3. **Synchronization Invariant**: Tapping Zoom In, Zoom Out, or Reset on the new toolbar immediately updates `profileZoomScale` and `profileStartDist`, reflecting simultaneously across Elevation, Speed/Pace, Heart Rate, and Power graphs.
4. **Resilient SplitPane Math**: When calculating `availableHeightPx` in `MapDetailLayout`, the toolbar's fixed height must be subtracted alongside `DIVIDER_TOUCH_HEIGHT` to maintain exact fraction scaling.
5. **Localization Parity**: All button content descriptions and tooltip strings must be translated across all 9 application languages (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

---

## 4. Proposed Solution & Architecture

### 4.1 Standalone Composable: `GlobalTelemetryZoomToolbar`
Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/GlobalTelemetryZoomToolbar.kt`:
- **Dimensions & Styling**:
  - Compact horizontal strip with height `36.dp` (touch targets padded to accessible dimensions).
  - Background: `MaterialTheme.colorScheme.surface` or subtle `surfaceContainerLow` with bottom divider line or elevation.
  - Horizontal arrangement: Centered or start-aligned with 16.dp horizontal padding, matching graph margins (`start = 50.dp, end = 25.dp` alignment or sleek balanced centering).
- **Controls**:
  - **Zoom In (`+`) Button**: Increases zoom by `1.5x` using `applyZoomAtCentroid`. Disabled when `zoomScale >= 10.0f - 0.01f`.
  - **Zoom Out (`-`) Button**: Decreases zoom by `1.5x` using `applyZoomAtCentroid`. Disabled when `zoomScale <= 1.01f`.
  - **Pan / Scrub Mode Toggle**: Toggles `isPanMode` between continuous scrubbing and horizontal panning.
  - **Reset Pill (`1.0x` / `Reset`)**: Rendered when `zoomScale > 1.01f`. Displays current zoom factor (`"%.1fx"`) and reset icon (`Icons.Default.RestartAlt`), resetting to `zoomScale = 1.0f, startDist = 0.0`.
- **State Hoisting**:
  - `zoomScale: Float`
  - `startDist: Double`
  - `totalSpan: Double`
  - `onZoomChanged: (zoomScale: Float, startDist: Double) -> Unit`
  - `isPanMode: Boolean`
  - `onPanModeToggle: () -> Unit`
  - `modifier: Modifier = Modifier`

### 4.2 Placement in `MapDetailLayout.kt`
In `MapDetailLayout.kt`, place `GlobalTelemetryZoomToolbar` directly inside the resizable viewport Column:
```kotlin
Column(modifier = Modifier.fillMaxSize()) {
    mapBox(...)
    SplitPaneDivider(...)
    
    if (showZoomControls && activeScrubPath != null && activeScrubPath.isNotEmpty()) {
        GlobalTelemetryZoomToolbar(
            zoomScale = profileZoomScale,
            startDist = profileStartDist,
            totalSpan = totalSpan,
            onZoomChanged = { z, s ->
                profileZoomScale = z
                profileStartDist = s
            },
            isPanMode = isPanMode,
            onPanModeToggle = { isPanMode = !isPanMode },
            modifier = Modifier.fillMaxWidth()
        )
    }

    lowerColumn(
        Modifier
            .weight(1f - splitFraction)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    )
}
```
Because `GlobalTelemetryZoomToolbar` sits **outside** `Modifier.verticalScroll(...)`, it remains persistently pinned and sticky directly under the map/divider at all times!

### 4.3 Decoupling & Vertical Space Recovery in `ElevationProfile.kt`
- Remove the embedded zoom buttons row from `ElevationProfile.kt` (lines 718–812).
- Hoist `isPanMode` into `MapDetailLayout` and pass it down to `ElevationProfile` (or keep internal if not provided).
- Maintain the Info/Legend icon button at `Alignment.TopEnd`.
- Adjust `topPadding` and `ScrubbingTelemetryBadge` positioning so that canvas plotting area is maximized.

---

## 5. User Scope Grounding (`ATT-1250`)

### In-Scope:
- Create `GlobalTelemetryZoomToolbar.kt` in `ui/components/core/` with Zoom In, Zoom Out, Pan/Scrub toggle, and Reset pill.
- Position `GlobalTelemetryZoomToolbar` persistently between `SplitPaneDivider` and `lowerColumn` in `MapDetailLayout.kt`.
- Remove embedded zoom buttons from `ElevationProfile.kt`.
- Update `SplitPaneMath` height accounting if necessary to maintain exact fraction scaling.
- Add localized strings across 9 application locales.
- Create unit tests for `GlobalTelemetryZoomToolbar` and update `MapDetailLayoutTest.kt`.

### Out-of-Scope:
- Modifying gesture conflict resolution or vertical scroll intercept behavior (reserved for `ATT-1872`).
- Modifying map rendering, tracks layer, or snapshot generation.
- Changing telemetry parsing or database schemas.

---

## 6. Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Split pane fraction drift due to toolbar height | Minor visual layout jump | Include toolbar height in `calculateAvailableHeight` when toolbar is visible. |
| Inadvertent regression of pinch-to-zoom on chart canvas | Medium UX regression | Keep canvas pointer input gesture handlers intact in `ElevationProfile.kt` and `TelemetryMetricGraph.kt`. |
| Localization key omission | Build failure or untranslated UI | Add strings to all 9 `strings.xml` resource files and verify with `./gradlew assembleDebug`. |
