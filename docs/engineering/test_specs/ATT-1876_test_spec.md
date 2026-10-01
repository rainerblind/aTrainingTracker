# Stage 2: Requirement & Test Specification - ATT-1876: Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs

**Ticket**: [ATT-1876](https://rainerblind.atlassian.net/browse/ATT-1876)  
**Sub-task**: [ATT-1918](https://rainerblind.atlassian.net/browse/ATT-1918) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-225` (*Aftermath/Graphs: Persistent Sticky Global Zoom Toolbar Between Map and Telemetry Graphs*)  
**Test Spec ID**: `TST-UI-179`  
**Branch**: `feature/ATT-1876`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Requirement Specification (`REQ-UI-225`)

### 1.1 Problem Statement & Rationale
On the detailed workout inspection screen (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`), horizontal zoom was synchronized globally across all stacked charts (Elevation, Speed/Pace, Heart Rate, Power) in `REQ-UI-215` (`ATT-1814`). However, the interactive zoom controls (`+`, `-`, Pan/Scrub mode toggle, and Reset pill) remained physically embedded inside `ElevationProfile.kt` underneath the local section header *"Höhenprofil"*. 

When an athlete scrolls down the lower container to examine telemetry curves (Speed/Pace, Heart Rate, Power) or lap splits, the embedded zoom controls scroll completely out of view. Furthermore, placing global controls inside a child chart is semantically misleading. Decoupling the zoom controls into a dedicated composable `GlobalTelemetryZoomToolbar` positioned persistently between the map viewport and the scrollable lower container ensures controls remain stationary, accessible, and clearly global at all times, while recovering valuable vertical plotting space inside `ElevationProfile.kt`.

### 1.2 Functional & Architectural Requirements
The system SHALL decouple horizontal zoom controls from child graph components and provide a dedicated, persistent sticky zoom toolbar positioned directly between the map viewport and the scrollable telemetry graph container on detailed workout inspection screens (`MapDetailLayout.kt` / `TrackOnMapScreen.kt`) (ATT-1876):

1. **Reusable GlobalTelemetryZoomToolbar Component (`GlobalTelemetryZoomToolbar.kt`)**:
   - The system SHALL implement a reusable composable `GlobalTelemetryZoomToolbar` in package `com.atrainingtracker.trainingtracker.ui.components.core` accepting:
     - `zoomScale: Float`
     - `startDist: Double`
     - `totalSpan: Double`
     - `onZoomChanged: (zoomScale: Float, startDist: Double) -> Unit`
     - `isPanMode: Boolean = false`
     - `onPanModeToggle: (() -> Unit)? = null`
     - `modifier: Modifier = Modifier`
   - The toolbar SHALL render as a compact horizontal strip with standardized height `36.dp` (`TOOLBAR_HEIGHT = 36.dp`) and surface container styling (`MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)` or surface with subtle bottom border).
   - The toolbar SHALL provide accessible zoom controls:
     - **Zoom Out (`-`) Button**: Scales down by factor `1.5f` anchored at center via `ElevationProfileZoomMath.applyZoomAtCentroid`; disabled when `zoomScale <= 1.01f`.
     - **Zoom In (`+`) Button**: Scales up by factor `1.5f` anchored at center via `ElevationProfileZoomMath.applyZoomAtCentroid`; disabled when `zoomScale >= 10.0f - 0.01f`.
     - **Pan / Scrub Mode Toggle Button**: Toggles `isPanMode` between continuous route scrubbing and horizontal pan navigation.
     - **Current Zoom & Reset Pill**: Displayed when `zoomScale > 1.01f`, rendering current magnification (e.g. `2.5x`) and reset icon (`Icons.Default.RestartAlt`); single-tap restores full `1.0x` extent (`zoomScale = 1.0f, startDist = 0.0`).
   - All toolbar controls SHALL include accessible content descriptions with 100% 9-language localization parity (`zoom_in`, `zoom_out`, `zoom_reset`, `zoom_pan_mode`, `zoom_scrub_mode`).

2. **MapDetailLayout Persistent Sticky Placement**:
   - In `MapDetailLayout.kt`, when `showZoomControls == true` and `activeScrubPath != null && activeScrubPath.isNotEmpty()`, `GlobalTelemetryZoomToolbar` SHALL be placed directly below `SplitPaneDivider` and above `lowerColumn(...)`.
   - Because the toolbar resides outside `Modifier.verticalScroll(...)`, it SHALL remain stationary and pinned directly below the map/divider at all times, never scrolling offscreen when inspecting lower telemetry graphs or lap splits.
   - Dynamic available height calculations (`SplitPaneMath.calculateAvailableHeight`) SHALL account for `TOOLBAR_HEIGHT` alongside `DIVIDER_TOUCH_HEIGHT` when the toolbar is active, ensuring exact viewport fraction calculations.

3. **Decoupling & Vertical Space Recovery in `ElevationProfile.kt`**:
   - The embedded zoom controls row in `ElevationProfile.kt` (lines 718–812) SHALL be completely removed, eliminating redundant duplicate buttons.
   - `ElevationProfile` SHALL accept `isPanMode: Boolean = false`.
   - Canvas top padding in `ElevationProfile` SHALL be reduced from `72.dp` to `44.dp` when `showZoomControls == true`, reclaiming vertical screen real estate for profile curve visualization while preserving collision clearance for `ScrubbingTelemetryBadge` (`top = 4.dp`).

4. **Preservation of Core Invariants**:
   - Direct canvas pinch-to-zoom and pan gestures on `ElevationProfile` and `TelemetryMetricGraph` remain 100% functional per `REQ-UI-192` and `REQ-UI-215`.
   - Synchronized scrubbing cursor parity across all stacked graphs, Google Maps gestures, snapshot sharing, and 9-language localization parity MUST NOT be broken.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Persistent Sticky Placement)**:
  - *Given* an athlete viewing a workout in `MapDetailLayout`,
  - *When* scrolling down the lower section to inspect Speed/Pace, Heart Rate, Power, or lap splits,
  - *Then* `GlobalTelemetryZoomToolbar` SHALL remain stationary and pinned directly below the map and divider, never scrolling offscreen.
* **Criterion 2 (Decoupled from Höhenprofil)**:
  - *Given* the 'Höhenprofil' section header in the scrollable content,
  - *When* rendered,
  - *Then* it SHALL no longer contain the embedded zoom buttons, which reside exclusively in the global toolbar.
* **Criterion 3 (Global Synchronization)**:
  - *Given* an athlete tapping Zoom In, Zoom Out, or Reset on `GlobalTelemetryZoomToolbar`,
  - *When* triggered,
  - *Then* all visible stacked graphs (Elevation Profile, Speed/Pace, Heart Rate, Power) SHALL zoom, pan, or reset in lockstep synchronously.
* **Criterion 4 (Pan/Scrub Mode Toggle)**:
  - *Given* an athlete tapping the Pan/Scrub mode toggle on `GlobalTelemetryZoomToolbar`,
  - *When* switched to Pan mode,
  - *Then* dragging on the elevation profile SHALL pan the visible window instead of moving the scrubbing cursor.
* **Criterion 5 (Boundary State Protection)**:
  - *Given* `zoomScale <= 1.01f`,
  - *Then* Zoom Out SHALL be disabled and the Reset pill SHALL NOT be rendered.
  - *Given* `zoomScale >= 9.99f`,
  - *Then* Zoom In SHALL be disabled.

### 1.4 System Invariants
1. `ElevationProfileZoomMath` is the sole authority for mathematical window clamping and centroid scaling.
2. Synchronized scrubbing cursor alignment across all stacked charts.
3. Resilient split-pane fraction calculations via `SplitPaneMath`.
4. 100% 9-language localization parity (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`).

---

## 2. Test Specification (`TST-UI-179`)

### Test Case 1: `GlobalTelemetryZoomToolbarTest` (`[TST-UI-179.1]`)
* **Scope**: Pure Composable & Logic Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/GlobalTelemetryZoomToolbarTest.kt`
* **Test Procedures**:
  - `testToolbarHeight_is36dp()`: Verifies `GlobalTelemetryZoomToolbar.TOOLBAR_HEIGHT == 36.dp`.
  - `testZoomIn_computesTargetZoomAndInvokesCallback()`: Verifies that tapping Zoom In invokes `onZoomChanged` with `zoomScale * 1.5f` anchored via `applyZoomAtCentroid`.
  - `testZoomOut_computesTargetZoomAndInvokesCallback()`: Verifies that tapping Zoom Out invokes `onZoomChanged` with `zoomScale / 1.5f` anchored via `applyZoomAtCentroid`.
  - `testZoomIn_disabledAtMaxZoom()`: Verifies that Zoom In is disabled when `zoomScale >= 10.0f - 0.01f`.
  - `testZoomOut_disabledAtMinZoom()`: Verifies that Zoom Out is disabled when `zoomScale <= 1.01f`.
  - `testResetPill_hiddenAt1x_visibleWhenZoomed()`: Verifies Reset pill is hidden at 1.0x and visible at >1.01x.
  - `testResetPill_resetsToFullExtent()`: Verifies tapping Reset pill invokes `onZoomChanged(1.0f, 0.0)`.
  - `testPanModeToggle_triggersCallback()`: Verifies tapping Pan/Scrub button invokes `onPanModeToggle`.

### Test Case 2: `MapDetailLayoutTest` Structural Contract Updates (`[TST-UI-179.2]`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`
* **Test Procedures**:
  - `testMapDetailLayout_rendersGlobalTelemetryZoomToolbarOutsideScroll()`: Verifies `GlobalTelemetryZoomToolbar` is rendered in `MapDetailLayout.kt` outside `Modifier.verticalScroll`.
  - `testMapDetailLayout_factorsToolbarHeightInSplitPaneMath()`: Verifies `availableHeightPx` accounts for `TOOLBAR_HEIGHT` when toolbar is active.
  - `testMapDetailLayout_wiresPanModeState()`: Verifies `isPanMode` state is hoisted in `MapDetailLayout` and passed to `GlobalTelemetryZoomToolbar` and `ElevationProfile`.

### Test Case 3: `ElevationProfileLayoutTest` Decoupling Contract (`[TST-UI-179.3]`)
* **Scope**: Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ElevationProfileLayoutTest.kt`
* **Test Procedures**:
  - `testElevationProfile_doesNotContainEmbeddedZoomButtonsRow()`: Verifies embedded zoom buttons row is removed from `ElevationProfile.kt`.
  - `testElevationProfile_acceptsIsPanModeParameter()`: Verifies `ElevationProfile` accepts `isPanMode: Boolean = false`.
  - `testElevationProfile_usesReducedTopPadding()`: Verifies canvas top padding is reduced to `44.dp` when `showZoomControls == true`.

### Test Case 4: 9-Language Localization Audit (`[TST-UI-179.4]`)
* **Scope**: Resource Localization Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/ZoomToolbarLocalizationTest.kt`
* **Test Procedures**:
  - `testZoomToolbarStrings_existInAllNineLocales()`: Verifies `zoom_in`, `zoom_out`, `zoom_reset`, `zoom_pan_mode`, `zoom_scrub_mode` are defined across all 9 localized resource files (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`).

### Test Case 5: Clean-Room Regression Suite (`[TST-UI-179.5]`)
* **Command**: `./gradlew testDebugUnitTest`
* **Goal**: 100% pass rate with zero regressions across all project modules.

---

## 3. Traceability Matrix

| Requirement Clause | Test Case | Method / Assertion | Status |
|---|---|---|---|
| `REQ-UI-225.1` (Toolbar composable & controls) | `[TST-UI-179.1]` | `GlobalTelemetryZoomToolbarTest.*` | Specified |
| `REQ-UI-225.2` (Persistent sticky placement & SplitPaneMath) | `[TST-UI-179.2]` | `MapDetailLayoutTest.*` | Specified |
| `REQ-UI-225.3` (Decoupling & topPadding reduction) | `[TST-UI-179.3]` | `ElevationProfileLayoutTest.*` | Specified |
| `REQ-UI-225.4` (9-Language Localization Parity) | `[TST-UI-179.4]` | `ZoomToolbarLocalizationTest.*` | Specified |
| Full ASPICE regression safety | `[TST-UI-179.5]` | `./gradlew testDebugUnitTest` | Specified |
