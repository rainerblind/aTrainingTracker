# Stage 2 Requirement & Test Specification: ATT-1735

**Ticket**: [ATT-1735](https://atrainingtracker.atlassian.net/browse/ATT-1735)  
**Sub-task**: [ATT-1766](https://atrainingtracker.atlassian.net/browse/ATT-1766) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.6`  
**Branch**: `feature/ATT-1735`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-01  

---

## 1. Requirement Specification (REQ-UI-196)

### REQ-UI-196: LiveSegment & Bottom Sheets: Refined Subtle Drag Handle, Harmonized Popup Spacing & Unified Surface Background
The system SHALL refine the visual weight, dimensions, color tokens, and container padding of `MinimumDragHandle` across the LiveSegment popup and all bottom sheet scaffolds, and unify container surface styling to eliminate contrast seams (ATT-1644, ATT-1735):

1. **Refined Design Tokens (`BottomSheetDesign.kt`)**:
   - `DragHandleWidth`: Standardized at `32.dp` (reduced from `36.dp`).
   - `DragHandleHeight`: Standardized at `3.dp` (reduced from `4.dp`).
2. **Subtle Drag Handle Affordance (`MinimumDragHandle.kt`)**:
   - The drag handle pill container color SHALL use `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)` in place of heavy `onSurfaceVariant(0.5f)`, providing a gentle, muted grey that harmonizes with both Light and Dark themes.
   - Container padding SHALL be standardized to `top = 8.dp, bottom = 4.dp` (reduced from `12.dp` / `6.dp`).
   - `MinimumDragHandle` SHALL support configurable parameters with defaults: `width`, `height`, `color`, `topPadding`, `bottomPadding`.
3. **Harmonized Spacing in LiveSegment Popup**:
   - Vertical spacing from the sheet's top rounded contour to the pill SHALL be `8.dp`.
   - Combined vertical distance from the pill to the segment title row (`SegmentHeader`) SHALL be `8.dp` (`4.dp` handle bottom + `4.dp` header top).
4. **Unified Container Surface Background (`SensorGridScreen.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`)**:
   - In `SensorGridScreen.kt`, `BottomSheetScaffold` SHALL explicitly define `sheetContainerColor = MaterialTheme.colorScheme.surface`, matching inner sheet content and eliminating visual color contrast seams.
   - The sheet container `Box` SHALL apply `.background(MaterialTheme.colorScheme.surface, shape = BottomSheetDesign.SheetShape)`.
   - In `MapDetailLayout.kt`, when operating in bottom sheet mode (`!useStatusBarsPadding`), the root `Column` SHALL apply `.background(MaterialTheme.colorScheme.surface)`, ensuring `MinimumDragHandle`, the slotted header, and the elevation profile share the identical unified surface.
   - In `LIveSegmentSheet.kt`, the header and metrics column SHALL seamlessly share `MaterialTheme.colorScheme.surface`, eliminating stark white patches against darker sheet containers.
5. **Preservation of Invariants**:
   - Edge-to-edge window insets (`REQ-UI-148`) and top contour styling (`REQ-UI-189`) remain 100% intact; touch responsiveness and drag interactions are preserved.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**:
  - `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*), targeting `BottomSheetDesign.kt`, `MinimumDragHandle.kt`, `MapDetailLayout.kt`, `LIveSegmentSheet.kt`, and `SensorGridScreen.kt`.
* **Historical Origin & Commit Trace**:
  - Commit `69c84e1b` (ATT-1588) and commit `e7ae455c` (ATT-1644).
* **Root Reason for Existing Formulation**:
  - `REQ-UI-189` introduced a prominent 36dp x 4dp handle with `onSurfaceVariant(0.5f)` to ensure discoverability. `ATT-1644` refined the drag handle dimensions (32dp x 3dp) and harmonized vertical spacing, but left the container color uncoordinated between `BottomSheetScaffold` (defaulting to `surfaceContainerLow`) and nested `Surface` components (using `surface`), resulting in a stark white header inside a darker container.
* **Preservation of Core Invariants**:
  - Full touch target accessibility, edge-to-edge window insets (`REQ-UI-148`), sheet contour border (`REQ-UI-189`), and full-screen detail view styling remain intact.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Drag Handle Subtle Affordance & Spacing)**:
  - *Given* an active live segment popup in `SensorGridScreen` or any bottom sheet using `MinimumDragHandle`,
  - *When* displayed in Light or Dark mode,
  - *Then* the drag handle pill SHALL render with refined dimensions ($32\text{dp} \times 3\text{dp}$), subtle `outlineVariant.copy(alpha = 0.6f)` color, and balanced padding ($8\text{dp}$ top, $4\text{dp}$ bottom) without commanding visual dominance over segment metrics.
* **AC-2 (Design Token Dimensions)**:
  - *Given* `BottomSheetDesign.DragHandleWidth` and `BottomSheetDesign.DragHandleHeight`,
  - *When* inspected, `DragHandleWidth` SHALL equal `32.dp` and `DragHandleHeight` SHALL equal `3.dp`.
* **AC-3 (Unified Container Surface Background)**:
  - *Given* an active live segment popup displayed in `SensorGridScreen` (`LIveSegmentSheet`),
  - *When* rendered in Light, Dark, or AMOLED theme,
  - *Then* the bottom sheet scaffold container, drag handle zone, segment header, live details, and elevation profile SHALL share a single unified `MaterialTheme.colorScheme.surface` background without stark contrasting color patches or visible background seams.

---

## 4. Test Case Specification (TST-UI-150)

### TST-UI-150: MinimumDragHandle Dimensions, Unified Popup Surface and Customization Unit Test
- **Target Components**: [SensorGridScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt), [MapDetailLayout.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt), [LIveSegmentSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/LIveSegmentSheet.kt), [BottomSheetDesign.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt)
- **Test Files**: `LiveSegmentSheetLayoutTest.kt`, `MinimumDragHandleTest.kt`, `CoreComponentsIntegrityTest.kt`
- **Scenarios**:
  1. `testLiveSegmentSheet_designTokensConformToRefinedSpecs`: Verify `DragHandleWidth == 32.dp` and `DragHandleHeight == 3.dp`.
  2. `testLiveSegmentSheet_composableExistsAndIsPublic`: Verify composable visibility and contract.
  3. `testLiveSegmentSheet_unifiedBackgroundContract`: Verify that `SensorGridScreen.kt` configures `sheetContainerColor = MaterialTheme.colorScheme.surface`, `MapDetailLayout.kt` uses `MaterialTheme.colorScheme.surface` background on bottom sheet mode, and `LIveSegmentSheet.kt` maintains unified surface background.
  4. `testLiveSegmentSheet_suppressesZoomControls`: Verify `showZoomControls = false` integration.
  5. Clean-room full regression execution: `./gradlew testDebugUnitTest`.
