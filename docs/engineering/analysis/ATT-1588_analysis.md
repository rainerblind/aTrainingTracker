# Stage 1: Problem Domain & Root Cause Analysis - ATT-1588: Distinct Visual Boundaries, Contours & Elevation for Bottom Popups & Live Segment

**Ticket**: [ATT-1588](https://atrainingtracker.atlassian.net/browse/ATT-1588)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-29  

---

## 1. Problem Statement & User Impact

Endurance athletes monitoring training in the live tracking cockpit (`SensorGridScreen.kt`) or reviewing routes/filters across bottom popup sheets frequently encounter poor visual separation between the bottom sheet and the underlying screen canvas.

### Current Limitations:
1. **Visual Bleed & Ambiguity in Live Segment Sheet**:
   - In `SensorGridScreen.kt`, the `BottomSheetScaffold` hosting the Live Segment overlay uses default `surface` background color identical to the sensor tiles and map canvas.
   - `BottomSheetScaffold` specifies no custom `sheetShape`, while nested `MapDetailLayout.kt` explicitly renders a `Surface` with `shape = RectangleShape`, clipping square corners.
   - Zero shadow elevation (`sheetShadowElevation = 0.dp`) and no top border or outline stroke are rendered. Consequently, the Live Segment sheet content visually melts into the sensor tiles, appearing as unstructured floating text rather than a distinct, draggable overlay.
   - In `SensorGridScreen.kt`, `sheetDragHandle = null` suppresses the scaffold drag handle, relying on `MapDetailLayout` which positions a faint drag handle with inconsistent top spacing.
2. **Modal Sheet Boundary Inconsistencies**:
   - While `AppModalBottomSheet.kt` and `FilterBottomSheetScaffold.kt` implement edge-to-edge system insets (REQ-UI-148), they lack an authentic top boundary stroke (`outlineVariant` / 1.dp) and standard curvature. When opened in dark mode against dark content, the perimeter of the sheet is visually soft or indistinguishable from dark dialog scrims.
3. **User Design Constraints**:
   - The athlete explicitly mandates that visual separation must **NOT** be achieved through arbitrary color shifts or multi-color status bars. Structural separation and clarity must be achieved purely through form, contour lines, elevation drop shadows, and clear interactive drag affordances.

---

## 2. Chesterton's Fence & Requirement Archaeology

### 1. Requirement & Ticket Lineage
* **Predecessor Tickets**:
  - `ATT-939` / `REQ-UI-148`: Standardized edge-to-edge window insets (`navigationBarsPadding()`, `imePadding()`) and established `AppModalBottomSheet` and `MinimumDragHandle`.
  - `ATT-900`: Modernized legacy Android dialogs into Compose modal bottom sheets.
* **Historical Reason for Existing Formulation**:
  - In `REQ-UI-148`, the primary focus was solving keyboard overlap, gesture bar collision, and dead whitespace on 3-button navigation devices.
  - In `SensorGridScreen`, `BottomSheetScaffold` was wired to allow the map to render underneath the sensor grid, with minimal sheet styling to avoid interfering with telemetry rendering.
* **Root Reason for Gap**:
  - Default Material 3 `BottomSheetScaffold` has 0.dp shadow elevation and relies on tonal elevation color shifts. In dark mode, tonal elevation shifts are subtle and blend into dark surfaces. Without explicit top curvature clipping, a 1.dp contour border, and 8.dp shadow elevation, the sheet has no perceived physical boundary.

### 2. Core Invariant Preservation
* **Invariants Preserved**:
  - **Zero Color Shifts / No Multi-Color Bars**: Adhere strictly to user preference; maintain semantic `MaterialTheme.colorScheme.surface` and `surfaceContainer`.
  - **Edge-to-Edge Window Insets (`REQ-UI-148`)**: `navigationBarsPadding()` and `imePadding()` must remain intact across all sheets.
  - **Map Underlay Behavior**: The map in `SensorGridScreen` and `MapScreenWithTrack` must continue to draw under the sheet for seamless panning.
  - **Swipe & Drag Semantics**: Bottom sheet dragging, fling gestures, and peek heights must function smoothly with zero gesture conflicts.

---

## 3. Forensic Scope & Affected Components

1. **`BottomSheetDesign.kt` (New Core Design Primitive in `ui.components.core`)**:
   - Standardize bottom sheet design tokens:
     - `SheetCornerRadius = 20.dp`
     - `SheetShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`
     - `SheetShadowElevation = 8.dp`
     - `SheetTonalElevation = 2.dp`
     - `BorderWidth = 1.dp`
     - `BorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)`
   - Provide reusable `Modifier.sheetContour()` extension for applying top curvature and border outline stroke.

2. **`MinimumDragHandle.kt`**:
   - Enhance the drag handle pill to 36dp x 4dp with `color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)`.
   - Standardize vertical padding (`top = 12.dp, bottom = 6.dp`).

3. **`SensorGridScreen.kt` (Live Segment BottomSheetScaffold)**:
   - Configure `sheetShape = BottomSheetDesign.SheetShape`.
   - Configure `sheetShadowElevation = BottomSheetDesign.SheetShadowElevation` (8.dp).
   - Configure `sheetTonalElevation = BottomSheetDesign.SheetTonalElevation` (2.dp).
   - Apply `sheetContour` to the sheet content container so the top edge has a crisp 1.dp `outlineVariant` contour line and rounded clipping.

4. **`MapDetailLayout.kt` & `LIveSegmentSheet.kt`**:
   - Remove `shape = RectangleShape` when running in sheet mode (`!useStatusBarsPadding`), inheriting the top rounded shape.
   - Ensure header background conforms to top curvature without square corner bleeding.

5. **`AppModalBottomSheet.kt` & `FilterBottomSheetScaffold.kt`**:
   - Unify `shape = BottomSheetDesign.SheetShape`.
   - Apply subtle top contour border `BorderStroke(1.dp, outlineVariant.copy(alpha = 0.6f))` and 2.dp tonal elevation.
   - Standardize `MinimumDragHandle` affordance.

6. **Scaffold Map Sheets (`MapScreenWithTrack.kt`, `PeriodMapScreen.kt`, `WorkoutClusterHeatmapScreen.kt`)**:
   - Configure `sheetShape = BottomSheetDesign.SheetShape` and `sheetShadowElevation = 8.dp` for unified elevation and boundary across all persistent bottom sheets.

---

## 4. Scope Bounding & Out-of-Scope Items

* **In-Scope**:
  - Defining `BottomSheetDesign` constants and modifier.
  - Adding 8.dp drop shadow elevation and 20.dp rounded top curvature to `BottomSheetScaffold` instances.
  - Adding 1.dp `outlineVariant` contour line along top perimeter of bottom sheets.
  - Enhancing `MinimumDragHandle` visibility and proportions.
  - Eliminating square corner overrides in `MapDetailLayout` and sheet containers.
* **Out-of-Scope**:
  - Modifying sensor tile layout, telemetry values, or graph rendering.
  - Modifying live segment detection algorithm or Strava segment matching.
  - Modifying filter criteria evaluation logic or database persistence.
  - Introducing contrasting background colors or multi-color headers (strictly excluded per user design directive).
