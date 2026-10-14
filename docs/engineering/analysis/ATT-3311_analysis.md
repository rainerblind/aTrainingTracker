# Stage 1 Analysis: ATT-3311 - [Bug] Restore visibility of sole elevation profile on tracking screen without map

**Ticket**: [ATT-3311](https://atrainingtracker.atlassian.net/browse/ATT-3311)  
**Sub-task**: [ATT-3590](https://atrainingtracker.atlassian.net/browse/ATT-3590) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `v1.0.0-rc1`  
**Active Sprint**: `2026-42.4`  
**Branch**: `bugfix/ATT-3311`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-14  

---

## 1. Problem Statement & Motivation

During Sprint Review `2026-41.10` (evaluating `ATT-3173` on Google Pixel 10), the reviewer reported:
> *"Unfortunately, the elevation profile is no longer shown at all."*

### Symptoms & Observed Behavior:
* **Expected**: On tracking views configured with the elevation profile enabled (both when the embedded map is disabled per `ATT-3173` / `REQ-UI-346` and when the map is enabled per `ATT-3343` / `REQ-UI-358`), the `ElevationProfile` composable renders cleanly with canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) directly below the cockpit sensor grid (or map), fully visible and unclamped.
* **Observed**: On physical test devices (e.g. Google Pixel 10 with 3-button navigation, as captured in `screencap_elevation_missing.png`), the elevation profile canvas is no longer visible at all. On tabs with the map enabled, a blank white strip of empty headroom appears while the chart path, baseline, and labels are buried; on tabs without the map, the profile is completely obscured or omitted.

---

## 2. Root Cause Analysis (Forensic Investigation)

A forensic deep dive across `SensorGridScreen.kt`, `ElevationProfile.kt`, `TrackingElevationProfileResolver.kt`, and the live on-device screenshot `screencap_elevation_missing.png` identified the dual technical root cause:

### 2.1 Primary Mechanism: Bottom Sheet Peek Bar Occlusion
1. **Dynamic Peek Height Allocation**:
   In `SensorGridScreen.kt` (lines 272, 282):
   ```kotlin
   val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
   ...
   sheetPeekHeight = if (shouldShowBottomSheet && screenMode == ScreenMode.TRACKING) 
       BottomSheetDesign.PeekHeightLiveSegment + navBarHeight else 0.dp
   ```
   `BottomSheetDesign.PeekHeightLiveSegment` is $56\text{dp}$. On devices with 3-button navigation (such as the Pixel 10 in the review screenshot), `navBarHeight` is $\approx 48\text{dp}$, producing an aggregate `sheetPeekHeight` of $\approx 104\text{dp}$ ($\approx 286\text{px}$ at $2.75\times$ screen density).

2. **Omission of Scaffold Bottom Inset Padding in Cockpit Layout**:
   In `SensorGridScreen.kt` (line 536):
   ```kotlin
   BoxWithConstraints(
       modifier = Modifier
           .fillMaxSize()
           .padding(top = paddingValues.calculateTopPadding()) // Only pad the top
   ) {
       Column(modifier = Modifier.fillMaxSize()) { ... }
   }
   ```
   The tracking cockpit layout explicitly omits `paddingValues.calculateBottomPadding()`. This was historically done so that a full-screen Google Map could render underneath the translucent/floating bottom sheet.

3. **Collision with Unweighted Canonical Adaptive Elevation Profile**:
   * Prior to `ATT-3173` (`REQ-UI-339` in `ATT-3107`), the elevation profile was assigned `Modifier.weight(1f)` and `fillParentHeight = true` when `!state.mapState.showMap`. It stretched across $\approx 50\%$ of the screen ($\approx 400\text{dp}$), which allowed its upper portion to remain visible above the $104\text{dp}$ sheet peek bar despite the distortion.
   * In `ATT-3173` (`REQ-UI-346`), the artificial `weight(1f)` and `fillParentHeight` were excised to enforce canonical adaptive height ($70\text{dp} \dots 200\text{dp}$, wrapping content).
   * Consequently, when an elevation profile with adaptive height $\approx 70\text{dp} \dots 106\text{dp}$ is placed at the bottom of the `Column`, the $104\text{dp}$ Bottom Sheet Peek Bar sits directly on top of it.
   * In `ElevationProfile.kt`, the elevation path and X-axis labels are drawn along the baseline (`y = height - (seg.altNorm * height)`). With `bottomPadding = 24.dp` and baseline near the bottom, the entire drawn path is located in the bottom $70\text{dp}$ of the canvas. The $104\text{dp}$ sheet peek bar completely covers the path, baseline, and labels.
   * Pixel analysis of `screencap_elevation_missing.png` confirmed this: the map ends at $y = 2108\text{px}$, leaving a $180\text{px}$ ($\approx 65\text{dp}$) blank white region ($y = 2109 \dots 2289\text{px}$) corresponding to the top headroom/padding of the `ElevationProfile` canvas, while the bottom sheet peek bar sits at $y = 2290 \dots 2424\text{px}$, entirely occluding the elevation chart path and baseline.

### 2.2 Secondary Mechanism: Map-less Tab Inset Handling
When `state.mapState.showMap == false`:
* The sensor grid has `.weight(1f, fill = false)`.
* When the sensor grid has multiple rows (or when the stationary Column extends to the bottom boundary), the unweighted `Surface` holding the `ElevationProfile` is positioned in the lower viewport where the bottom sheet peek bar overlays it.
* Because `BoxWithConstraints` ignores `paddingValues.calculateBottomPadding()`, neither the sensor grid nor the elevation profile accounts for the space consumed by the bottom sheet peek bar.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Ensure that on tracking tabs without a map (`!state.mapState.showMap`), the cockpit layout accounts for `paddingValues.calculateBottomPadding()` (or `sheetPeekHeight`) so that the elevation profile is anchored cleanly **above** the bottom sheet peek bar with its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) fully visible.
  2. Ensure that on tracking tabs with a map (`state.mapState.showMap`), whenever the elevation profile is rendered below the map, it applies bottom padding equal to `paddingValues.calculateBottomPadding()` so that the profile sits cleanly above the bottom sheet peek bar, while the map expands flexibly to fill the remaining space (`REQ-UI-358`).
  3. Ensure that when no bottom sheet is present (or when sheet peek height is $0\text{dp}$), zero unnecessary bottom padding or dead space is introduced.
  4. Preserve all existing layout invariants: sensor grid internal scrolling (`REQ-UI-358`), map recomposition isolation (`REQ-UI-326`), canonical adaptive height ($70\text{dp} \dots 200\text{dp}$, `REQ-UI-346`), and zero regressions across the unit test suite.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to `ElevationProfile` internal math, curve generation, or climb badge pill rendering (`REQ-UI-274`).
  2. No modifications to `LiveSegmentSheet` or `LiveClimbSheet` internal layout.
  3. No changes to database schema or tab configuration preferences.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-346` (*Canonical Adaptive Height & Weight Decoupling for Sole Elevation Profile on Tracking Screen*, Sprint 2026-41.9, ATT-3173) and aligns with `REQ-UI-358` (*Dynamic Cockpit Viewport Allocation, Asymmetric Map Weight Expansion & Bounded Sensor Grid Containment*, Sprint 2026-41.11, ATT-3343). Targets `SensorGridScreen.kt` and `SensorGridScreenLayoutTest.kt`.
* **Historical Origin & Commit Trace**: 
  - `3079c4b9` (Sprint 2026-40.8): Initial introduction of `ElevationProfile` on tracking screen.
  - `71ea8399` (Sprint 2026-41.8 / `ATT-3107` / `REQ-UI-339`): Introduced `Modifier.weight(1f)` on `ElevationProfile` when map is disabled.
  - `935a2328` (Sprint 2026-41.9 / `ATT-3173` / `REQ-UI-346`): Removed `Modifier.weight(1f)` to restore canonical adaptive height ($70\text{dp} \dots 200\text{dp}$).
  - `fca58372` (Sprint 2026-41.11 / `ATT-3343` / `REQ-UI-358`): Introduced `BoxWithConstraints` and asymmetric weight expansion for the map, but retained `.padding(top = paddingValues.calculateTopPadding()) // Only pad the top`.
* **Root Reason for Existing Formulation**: 
  The top-level `BoxWithConstraints` only padded the top because the map was intended to draw underneath the bottom sheet peek bar. However, when the elevation profile sits at the bottom of the stationary `Column` (either alone or under the map), it has a fixed/adaptive height ($70\text{dp} \dots 200\text{dp}$) and is not a background map canvas. Failing to apply bottom padding causes the bottom sheet peek bar to occlude the elevation profile.
* **Preservation of Core Invariants**: 
  By applying bottom padding specifically to the elevation profile container or dynamically to the non-map cockpit container (`padding(bottom = paddingValues.calculateBottomPadding())`), the map can continue to draw under the sheet when alone, while the elevation profile is guaranteed to remain 100% visible above the bottom sheet peek bar in all modes.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Architecture & Layout Modifications in `SensorGridScreen.kt`
1. **Dynamic Bottom Inset Management**:
   * For the stationary cockpit `Column`:
     When `!state.mapState.showMap`, the `Column` (or container) applies `padding(bottom = paddingValues.calculateBottomPadding())`. Because no map exists to draw under the bottom sheet, this cleanly lifts the sensor grid and elevation profile above the bottom sheet peek bar.
   * When `state.mapState.showMap`:
     The map expands with `.weight(1f, fill = true)`.
     The elevation profile `Surface` (rendered below the map) applies `padding(bottom = paddingValues.calculateBottomPadding())`. This ensures the elevation profile is placed strictly above the bottom sheet peek bar, while the map above it flexes to consume all remaining space between the sensor grid and the elevation profile per `REQ-UI-358`.
2. **Zero Dead Space Preservation**:
   * When `sheetPeekHeight == 0.dp` (e.g. no active segments/climbs or preview mode), `paddingValues.calculateBottomPadding()` is $0\text{dp}$, so no redundant spacing is introduced.
   * When the elevation profile is hidden or contains $< 2$ points, the map expands down to the screen boundary (or sensor grid wraps without bottom padding).

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. Zero regression in existing features and unit tests (`./gradlew testDebugUnitTest` must remain 100% passing).
  2. Canonical adaptive height formula ($70\text{dp} \dots 200\text{dp}$) remains strictly preserved without reintroducing artificial `weight(1f)` or `fillParentHeight = true`.
  3. Parent ticket Human Decision Gate remains strictly enforced (AI agent terminates at `Final Review (Human)`).
* **Risk Rating**: **LOW**
  - Scope is strictly bounded to Compose layout modifier insets in `SensorGridScreen.kt`.
  - No database migrations, background service threading, or network logic involved.
