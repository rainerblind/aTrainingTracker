# Stage 1: Problem Domain & Root Cause Analysis - ATT-3173

## 1. Executive Summary & Problem Domain
* **Issue**: [ATT-3173](https://atrainingtracker.atlassian.net/browse/ATT-3173): `[Bug] Sole elevation profile on tracking screen without map stretches to half screen instead of using canonical adaptive height`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Target Requirement**: `REQ-UI-346` (amending `REQ-UI-339` and aligning with `docs/design_guidelines.md` §2.3)
* **Target Test Spec**: `TST-UI-306`
* **Sprint**: `2026-41.9`

### Problem Description
During Sprint 2026-41.8 Joint Review of `ATT-3107` on Google Pixel 10, the user observed that when the elevation profile is displayed alone on a tracking tab (with the map toggled off), it consumes approximately half the display height, stretching vertically out of proportion.
The user highlighted the established reference implementation in `LiveSegmentSheet` / `MapDetailLayout` / `SegmentItem`, where the elevation profile maintains a clean, natural 'fixed' adaptive height calculated from the difference between the lowest and highest point on the track ($\text{range} = \text{maxAlt} - \text{minAlt}$, spanning 70dp to 200dp via `calculateElevationProfileHeight(range)`), wrapping its content without expanding.

---

## 2. Forensic Investigation & Technical Root Cause

### 2.1 The Reference Implementation in `LiveSegmentSheet` & `MapDetailLayout`
1. In `LiveSegmentSheet.kt`:
   ```kotlin
   MapDetailLayout(
       modifier = modifier.fillMaxWidth().wrapContentHeight(),
       showMap = false,
       ...
   )
   ```
2. In `MapDetailLayout.kt` (lines 324-345) and `SegmentItem.kt`:
   ```kotlin
   ElevationProfile(
       pathPoints = path,
       modifier = Modifier.fillMaxWidth() // No weight(1f), no fillMaxHeight()!
   )
   ```
3. Inside `ElevationProfile.kt`, `totalCanvasHeight = cachedData.adaptiveHeight` is calculated via `calculateElevationProfileHeight(range)`:
   - Minimum height: `70.dp` (for flat or low-relief terrain)
   - Maximum height: `200.dp` (at 1000m range)
   - Curved formula: `70 + (range / 1000)^0.6 * (200 - 70)`
   - The canvas renders with `Modifier.fillMaxWidth().height(totalCanvasHeight)`.

### 2.2 Forensic Analysis of Commit `8d6ef4e1` (ATT-3107 / `REQ-UI-339`)
In Sprint 2026-41.8 (`ATT-3107`), the goal was to prevent a large sensor grid from pushing the elevation profile off-screen. While adding `.weight(1f, fill = false)` to the sensor grid successfully solved scrolling and containment, commit `8d6ef4e1` additionally introduced:
```kotlin
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
```
**Mechanism of the Defect**:
1. When `!state.mapState.showMap`, `Surface` is assigned `Modifier.weight(1f)`.
2. Inside `Column`, assigning `weight(1f)` causes the surface to claim all remaining vertical space in the parent container.
3. `ElevationProfile` receives `fillParentHeight = true` and `Modifier.fillMaxSize()`, overriding its canonical `height(totalCanvasHeight)` and forcing `.defaultMinSize(minHeight = totalCanvasHeight).fillMaxHeight()`.
4. As a result, when an athlete configures 1–2 rows of sensor tiles, the sensor grid occupies ~120dp, while the elevation profile expands to consume 400–600dp (50%+ of modern phone displays). The graph appears massively distorted, vertically stretched, with excessive grid line separation.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

### 1. Original Requirement ID & Target
* **Requirement**: `REQ-UI-339` (*Dynamic Cockpit Weight Allocation & Flexible Vertical Expansion for Elevation Profile when Map is Disabled*, Sprint 2026-41.8, ATT-3107).
* **Target**: Amending `REQ-UI-339` with `REQ-UI-346` to conform to `docs/design_guidelines.md` §2.3.

### 2. Historical Origin & Commit Trace
* Commit `8d6ef4e1` (Sprint 2026-41.8, `ATT-3107`).
* Prior to `ATT-3107`, `TrackingMapContainer` was isolated with `weight(1f)`, but when `showMap == false`, no weight was assigned to either the sensor grid or the elevation profile. If the athlete had 6 sensor rows, the unconstrained sensor grid pushed the elevation profile into an unreadable slit.
* `ATT-3107` correctly bounded the sensor grid with `weight(1f, fill = false)`, but over-corrected by applying `weight(1f)` and `fillParentHeight = true` to `ElevationProfile`.

### 3. Root Reason for Existing Formulation
* The intent in `ATT-3107` was to ensure the elevation profile had enough space when the map was hidden. However, forcing `weight(1f)` and `fillParentHeight = true` violated the core design principle that elevation profile geometry should maintain consistent vertical aspect scaling based on physical elevation delta rather than screen aspect ratio.

### 4. Preservation of Core Invariants
* **Sensor Grid Scrollability & Containment**: The sensor grid must retain `weight(1f, fill = false)` so it never pushes bottom cockpit views off screen when many sensor fields are present.
* **Canonical Adaptive Height**: The elevation profile must strictly adhere to `calculateElevationProfileHeight(range)` (70dp–200dp) across all screens and sheets, matching `docs/design_guidelines.md` §2.3.
* **Map & Profile Coexistence**: When `showMap == true`, the map retains `weight(1f)` and the elevation profile renders below it at its standard adaptive height.
* **Clean-Room Test Pass Rate**: 100% unit test pass rate must be preserved.

---

## 4. Proposed Architectural Changes

### 4.1 Update `SensorGridScreen.kt`
Remove `Modifier.weight(1f)` and `fillParentHeight = !state.mapState.showMap` from the elevation profile block:
```kotlin
// 3. The Elevation Profile (Below the Map or anchored with canonical adaptive height when map is hidden, REQ-UI-346 / ATT-3173)
if (state.showElevationProfile && state.pathPoints.size >= 2) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        ElevationProfile(
            pathPoints = state.pathPoints,
            currentDistance = state.pathPoints.lastOrNull()?.distance,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
```
**Layout Dynamics**:
* The sensor grid Column retains `weight(1f, fill = false)`.
* If `state.pathPoints.size >= 2` and `showElevationProfile` is true:
  - `ElevationProfile` computes `totalCanvasHeight = cachedData.adaptiveHeight` (70dp–200dp).
  - `Surface` wraps its content height.
  - The sensor grid takes the remaining vertical space above it. If there are few tiles (1–2 rows), the tiles wrap naturally without forced stretching. If there are many tiles (4–6 rows), the sensor grid scrolls smoothly.

### 4.2 Align Unit Tests in `SensorGridScreenLayoutTest.kt`
* Update tests that assert `Modifier.weight(1f)` and `fillParentHeight = !state.mapState.showMap` on `ElevationProfile`.
* Replace with assertions verifying that `ElevationProfile` renders without `weight(1f)` and without `fillParentHeight = true`, adhering to canonical adaptive height.

---

## 5. Scope Bounding (`ATT-1250`)
* **In-Scope**:
  - `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`: Remove `weight(1f)` and `fillParentHeight` from ElevationProfile `Surface`.
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenLayoutTest.kt`: Update layout contract tests.
  - `docs/requirements.md` & `docs/tests.md`: Document `REQ-UI-346` and `TST-UI-306`.
* **Out-of-Scope**:
  - Modifying `calculateElevationProfileHeight(range)` or mathematical formulas in `ElevationProfile.kt`.
  - Modifying `LiveSegmentSheet.kt`, `MapDetailLayout.kt`, or `SegmentItem.kt`.
  - Modifying database schemas or tracking service logic.

---

## 6. Acceptance Criteria (Given-When-Then)
* **AC-1 (Sole Profile Adaptive Height)**:
  - *Given* an active workout tracking tab with `showElevationProfile = true` and `showMap = false`,
  - *When* `SensorGridScreen` is displayed in `ScreenMode.TRACKING`,
  - *Then* `ElevationProfile` renders at its canonical adaptive height ($70\text{dp} \dots 200\text{dp}$) determined by `calculateElevationProfileHeight(range)`, and does NOT stretch to fill 50%+ of the screen height.
* **AC-2 (Sensor Grid Flexibility & Containment)**:
  - *Given* a tracking tab with 1–2 sensor rows, `showElevationProfile = true`, and `showMap = false`,
  - *When* the screen renders,
  - *Then* the sensor grid wraps content height naturally without forced spacing, and the elevation profile is anchored neatly below.
* **AC-3 (Multi-Row Sensor Grid Scrollability)**:
  - *Given* a tracking tab with 6 sensor rows, `showElevationProfile = true`, and `showMap = false`,
  - *When* the screen renders,
  - *Then* the sensor grid is vertically scrollable and does not push the elevation profile off screen.
* **AC-4 (Map & Profile Coexistence)**:
  - *Given* a tracking tab with `showMap = true` and `showElevationProfile = true`,
  - *When* the screen renders,
  - *Then* the map consumes the primary weighted space (`weight(1f)`) and the elevation profile renders below it at its standard adaptive height.
