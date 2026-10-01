# Stage 1 Analysis: ATT-1817 - Standardize popup and bottom sheet surface background across LiveSegment, Routes, Segments, and Settings

**Ticket**: [ATT-1817](https://rainerblind.atlassian.net/browse/ATT-1817)  
**Sub-task**: [ATT-1873](https://rainerblind.atlassian.net/browse/ATT-1873) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.7`  
**Branch**: `feature/ATT-1817`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Problem Statement & Motivation

In `aTrainingTracker`, bottom sheets, popups, and modal dialogs provide essential contextual controls and secondary information (such as live Strava segment tracking in the cockpit, route and segment details in map views, settings sheets, and user confirmations).

User feedback and photographic evidence (`screenshot_livesegment_popup.png`, `screenshot_map_fragment_popup.png`, `screenshot_segments_popup.png`) demonstrate inconsistent background fills and contrasting colors across several key areas:
1. **LiveSegment Popup (`SensorGridScreen` / `LIveSegmentSheet`)**:
   - The top header card (`SegmentHeader` and live details) has a pure white surface (`#FFFFFF`), but the elevation container below has a contrasting light grey background (`#EDEDED`, RGB 237, 237, 237), creating an abrupt visual seam.
2. **Route Bottom Sheet in Map Fragment (`RouteOnMapScreen` / `MapScreenWithTrack`)**:
   - The top header card ('Way Back Home') is white, while the lower area beneath the map ('Höhenprofil' and elevation controls) has a contrasting light grey background (`#EDEDED`).
3. **Segment Bottom Sheet in Map Fragment (`SegmentOnMapScreen` / `MapScreenWithTrack`)**:
   - The top header card ('kraehenbach climb 1') is white, while the 'Höhenprofil' container below the map has a light grey background (`#EDEDED`).
4. **Settings Popups & Confirmation Dialogs**:
   - Popups and dialogs within Settings (`AppModalBottomSheet`, `AppBottomSheetContent`, `FilterBottomSheetScaffold`, and various `AlertDialog` instances) exhibit non-standard container backgrounds (`surfaceContainerLow` / `surfaceContainerHigh`) or non-zero tonal elevations that inject a light grey overlay into light-mode surfaces instead of presenting a clean, consistent `MaterialTheme.colorScheme.surface` fill.

The objective of ATT-1817 is to unify and standardize all popups, bottom sheets, and dialogs to use clean, consistent `MaterialTheme.colorScheme.surface` background tokens, eliminating artificial grey tonal tinting and ensuring a clean, DRY architectural approach.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Forensic Pixel & Color Metric Findings

Pixel sampling of the attached screenshots reveals the precise mechanics of the issue:
* In `screenshot_livesegment_popup.png`:
  - Y = 1700 to 2082 (Header): Color is pure white `(255, 255, 255)` (`#FFFFFF`).
  - Y = 2083 onward (Elevation profile container): Color abruptly shifts to `(237, 237, 237)` (`#EDEDED`).
* In `screenshot_map_fragment_popup.png` and `screenshot_segments_popup.png`:
  - The bottom sheet container background is `(237, 237, 237)` (`#EDEDED`), contrasting sharply with pure white elements and cockpit surfaces.

### 2.2 Mathematical Origin of the `(237, 237, 237)` Grey

Why did the container render as `(237, 237, 237)`?
In Jetpack Compose Material 3:
1. `BottomSheetDesign.kt` defined:
   ```kotlin
   val SheetTonalElevation: Dp = 2.dp
   ```
2. When a `Surface` or `BottomSheetScaffold` is composed with `tonalElevation = 2.dp`, Material 3 evaluates `surfaceColorAtElevation(color = surface, elevation = 2.dp)`.
3. The official Material 3 formula calculates the elevation alpha overlay as:
   $$\alpha = \frac{4.5 \times \ln(\text{elevation} + 1) + 2.0}{100}$$
   For $\text{elevation} = 2.0\text{dp}$:
   $$\alpha = \frac{4.5 \times \ln(3.0) + 2.0}{100} = \frac{4.5 \times 1.0986 + 2.0}{100} = 6.9438\% \approx 7\%$$
4. Over a pure white base surface ($255$), applying a $6.94\%$ overlay of dark `surfaceTint` produces:
   $$\text{Color} = 255 \times (1 - 0.0694) = 237.29 \rightarrow \mathbf{237} \ (\text{#EDEDED})$$
5. In `LiveSegmentSheet.kt`, the header container explicitly applied `Modifier.background(MaterialTheme.colorScheme.surface)`. Unlike `Surface`, `Modifier.background()` does NOT apply tonal elevation, rendering as pure white $255$ (`#FFFFFF`).
6. However, immediately below the header, `MapDetailLayout.kt` rendered the elevation profile inside:
   ```kotlin
   Surface(
       color = MaterialTheme.colorScheme.surface,
       modifier = Modifier.fillMaxWidth()
   ) { ... }
   ```
   This nested `Surface` inherited the parent `BottomSheetScaffold`'s `LocalAbsoluteTonalElevation` of `2.dp`, triggering the $6.94\%$ overlay and rendering `#EDEDED`!
7. This created the exact visual contrast seam right at the boundary between the header and the elevation profile.

### 2.3 Scaffold Container Color Deficiencies

Forensic inspection across all bottom sheets and scaffolds identified missing or uncoordinated container colors:
1. **`MapScreenWithTrack.kt`**:
   - `BottomSheetScaffold` did NOT specify `sheetContainerColor`. In Material 3, it defaulted to `BottomSheetDefaults.ContainerColor` (`surfaceContainerLow`) with `sheetTonalElevation = 2.dp`.
2. **`PeriodMapScreen.kt` & `WorkoutClusterHeatmapScreen.kt`**:
   - `BottomSheetScaffold` did NOT specify `sheetContainerColor`, defaulting to `surfaceContainerLow` with `sheetTonalElevation = 2.dp`.
3. **`AppModalBottomSheet.kt` & `FilterBottomSheetScaffold.kt`**:
   - `ModalBottomSheet` did NOT specify `containerColor`, defaulting to `surfaceContainerLow` with `tonalElevation = 2.dp`.
   - `AppBottomSheetContent` applied `tonalElevation = BottomSheetDesign.SheetTonalElevation` (2.dp).
4. **Settings & Confirmation Dialogs**:
   - `AlertDialog` in `StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, and `DevicesTabbedScreen.kt` omitted `containerColor` and `tonalElevation`, defaulting to `surfaceContainerHigh` and 6.dp tonal elevation.
   - In contrast, `DeleteConfirmationDialog.kt` and `DeleteOldWorkoutsDialog.kt` had already established the correct standard:
     ```kotlin
     containerColor = MaterialTheme.colorScheme.surface,
     tonalElevation = 0.dp
     ```

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Update `BottomSheetDesign.SheetTonalElevation` from `2.dp` to `0.dp` to eliminate the 6.94% grey tonal elevation tint across all bottom sheets and nested surfaces.
  2. Standardize `sheetContainerColor = MaterialTheme.colorScheme.surface` and `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` across all `BottomSheetScaffold` instances (`SensorGridScreen.kt`, `MapScreenWithTrack.kt`, `WorkoutClusterHeatmapScreen.kt`, `PeriodMapScreen.kt`).
  3. Standardize `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = BottomSheetDesign.SheetTonalElevation` across all modal bottom sheets (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`) and `AppBottomSheetContent`.
  4. Ensure `MapDetailLayout.kt` and `LIveSegmentSheet.kt` maintain seamless, continuous `MaterialTheme.colorScheme.surface` backgrounds across header, map, elevation profile, and analytics without visual seams.
  5. Standardize `AlertDialog` instances (`StravaSettingsDialog.kt`, `WorkoutClusterHeatmapScreen.kt`, `WorkoutClustersScreen.kt`, `DevicesTabbedScreen.kt`, `ImportBackupTabsScreen.kt`) to declare `containerColor = MaterialTheme.colorScheme.surface` and `tonalElevation = 0.dp`.
  6. Update visual contract tests (`BottomSheetDesignTest.kt`, `BottomSheetVisualContractTest.kt`) to enforce the zero tonal elevation and surface container color invariants.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No alterations to bottom sheet corner curvature (`SheetCornerRadius = 20.dp`, `SheetShape`).
  2. No alterations to bottom sheet contour border stroke (`BorderWidth = 1.dp`, `outlineVariant.copy(alpha = 0.6f)`).
  3. No changes to elevation drop shadow elevation (`SheetShadowElevation = 8.dp`).
  4. No changes to `MinimumDragHandle` dimensions ($32\text{dp} \times 3\text{dp}$) or padding ($8\text{dp}$ / $4\text{dp}$).
  5. No alterations to map snapshot capture, telemetry graphs, or sensor grid layout logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  - `REQ-UI-189`: *UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances* (`BottomSheetDesign.kt`, `SensorGridScreen.kt`, `AppModalBottomSheet.kt`).
  - `REQ-UI-196`: *LiveSegment & Bottom Sheets: Refined Subtle Drag Handle, Harmonized Popup Spacing & Unified Surface Background* (`SensorGridScreen.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`).
* **Historical Origin & Commit Trace**:
  - Commit `69c84e1b` (ATT-1588): Introduced `BottomSheetDesign.kt` with `SheetTonalElevation = 2.dp`.
  - Commit `e7ae455c` (ATT-1644) & commit `54f90713` (ATT-1735): Harmonized drag handle and attempted to fix LiveSegment popup contrast by applying `sheetContainerColor = MaterialTheme.colorScheme.surface` in `SensorGridScreen.kt` and `MapDetailLayout.kt`.
* **Root Reason for Existing Formulation**:
  - `SheetTonalElevation = 2.dp` was chosen under the assumption that subtle tonal elevation was desirable in Material 3. However, M3's mathematical tint formula applies a 6.94% overlay (`#EDEDED`) to white surfaces. Furthermore, while `SensorGridScreen` had `sheetContainerColor = surface`, other persistent scaffolds (`MapScreenWithTrack.kt`, etc.) and modal sheets omitted `containerColor`. Additionally, because `LiveSegmentSheet`'s header used `Modifier.background()` while the elevation container used `Surface()`, the 2.dp tonal elevation produced an unintended contrast split.
* **Preservation of Core Invariants**:
  - Setting `SheetTonalElevation = 0.dp` and explicitly specifying `containerColor = MaterialTheme.colorScheme.surface` across all scaffolds and sheets eliminates the contrast split while preserving all physical boundary delineations:
    - Top curvature: `SheetCornerRadius = 20.dp` (`SheetShape`).
    - Top boundary stroke: 1.dp `outlineVariant` at 60% opacity (`sheetContour`).
    - Drop shadow elevation: 8.dp (`SheetShadowElevation`).
    - Drag handle pill: 32dp x 3dp with subtle `outlineVariant` color.
    - Full-screen status bars edge-to-edge insets (`REQ-UI-148`).

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Central Design Token Refinement (`BottomSheetDesign.kt`)
```kotlin
object BottomSheetDesign {
    val SheetCornerRadius: Dp = 20.dp
    val SheetShape: Shape = RoundedCornerShape(topStart = SheetCornerRadius, topEnd = SheetCornerRadius)
    val SheetShadowElevation: Dp = 8.dp
    val SheetTonalElevation: Dp = 0.dp // Standardized to 0.dp to eliminate grey tinting
    val BorderWidth: Dp = 1.dp
    val DragHandleWidth: Dp = 32.dp
    val DragHandleHeight: Dp = 3.dp
}
```

### 5.2 Modal Bottom Sheet Harmonization (`AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`)
In `AppModalBottomSheet.kt`:
```kotlin
ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    shape = BottomSheetDesign.SheetShape,
    containerColor = MaterialTheme.colorScheme.surface, // Explicitly white/surface
    tonalElevation = BottomSheetDesign.SheetTonalElevation, // 0.dp
    dragHandle = { MinimumDragHandle() },
    modifier = modifier
)
```
In `AppBottomSheetContent`:
```kotlin
Surface(
    modifier = modifier.fillMaxWidth(),
    shape = BottomSheetDesign.SheetShape,
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = BottomSheetDesign.SheetTonalElevation, // 0.dp
    border = BorderStroke(BottomSheetDesign.BorderWidth, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
)
```
In `FilterBottomSheetScaffold.kt`:
```kotlin
ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    shape = BottomSheetDesign.SheetShape,
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = BottomSheetDesign.SheetTonalElevation,
    dragHandle = { MinimumDragHandle() },
    modifier = modifier
)
```

### 5.3 Persistent Scaffold Bottom Sheets
Explicitly declare `sheetContainerColor = MaterialTheme.colorScheme.surface` and `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` in:
- `MapScreenWithTrack.kt`
- `SensorGridScreen.kt`
- `PeriodMapScreen.kt`
- `WorkoutClusterHeatmapScreen.kt`

### 5.4 Map Detail Layout & Live Segment Uniformity
In `MapDetailLayout.kt`:
- Root `Column` in sheet mode applies `Modifier.background(MaterialTheme.colorScheme.surface)`.
- Header `Surface` and lower `Surface` containers (elevation profile and analytics) uniformly use `color = MaterialTheme.colorScheme.surface` with zero tonal elevation.
In `LIveSegmentSheet.kt`:
- Header and details seamlessly share `MaterialTheme.colorScheme.surface`.

### 5.5 AlertDialog Standardization
Ensure confirmation dialogs across settings and management screens use:
```kotlin
AlertDialog(
    containerColor = MaterialTheme.colorScheme.surface,
    tonalElevation = 0.dp,
    ...
)
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. *Physical Form Preservation*: 20dp top rounded corners, 1dp contour stroke, and 8dp drop shadow remain completely intact.
  2. *Edge-to-Edge System Insets*: Transparent system bars, navigation bar padding, and keyboard IME padding (`REQ-UI-148`) remain preserved without regressions.
  3. *Parent Ticket Governance*: Parent ticket `ATT-1817` advances to `Final Review (Human)` assigned to `rainer`; AI agents must not transition parent tickets to `Erledigt`.
* **Risk Rating**: **LOW**
  - Changes are purely declarative Jetpack Compose theme token and parameter bindings.
  - Zero changes to business logic, threading, data models, or navigation routing.
