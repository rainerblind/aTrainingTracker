# Stage 2 Requirement & Test Specification: ATT-1644

**Ticket**: [ATT-1644](https://atrainingtracker.atlassian.net/browse/ATT-1644)  
**Sub-task**: [ATT-1665](https://atrainingtracker.atlassian.net/browse/ATT-1665) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1644`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Requirement Specification (REQ-UI-196)

### REQ-UI-196: LiveSegment & Bottom Sheets: Refined Subtle Drag Handle & Harmonized Spacing
The system SHALL refine the visual weight, dimensions, color tokens, and container padding of `MinimumDragHandle` across the LiveSegment popup and all bottom sheet scaffolds:

1. **Refined Design Tokens (`BottomSheetDesign.kt`)**:
   - `DragHandleWidth`: Standardized at `32.dp` (reduced from `36.dp`).
   - `DragHandleHeight`: Standardized at `3.dp` (reduced from `4.dp`).
2. **Subtle Drag Handle Affordance (`MinimumDragHandle.kt`)**:
   - The pill container color SHALL use `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)` in place of heavy `onSurfaceVariant(0.5f)`, providing a gentle, muted grey that harmonizes with both Light and Dark themes.
   - Container padding SHALL be standardized to `top = 8.dp, bottom = 4.dp` (reduced from `12.dp` / `6.dp`).
   - `MinimumDragHandle` SHALL support configurable parameters with defaults:
     ```kotlin
     @Composable
     fun MinimumDragHandle(
         modifier: Modifier = Modifier,
         width: Dp = BottomSheetDesign.DragHandleWidth,
         height: Dp = BottomSheetDesign.DragHandleHeight,
         color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
         topPadding: Dp = 8.dp,
         bottomPadding: Dp = 4.dp
     )
     ```
3. **Harmonized Spacing in LiveSegment Popup**:
   - Distance between the sheet's top rounded contour and the handle pill SHALL be `8.dp`.
   - Combined vertical distance between the handle pill and the segment title row (`SegmentHeader`) SHALL be `8.dp` (`4.dp` handle bottom + `4.dp` header top).
4. **Preservation of Invariants**:
   - Edge-to-edge window insets (`REQ-UI-148`) and top contour styling (`REQ-UI-189`) remain 100% intact.
   - Drag mechanics, touch responsiveness, and sheet expand/collapse interactions remain identical.

---

## 2. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

1. **Original Requirement ID & Target**: `REQ-UI-189` (*UI/Sheets: Standardized Boundaries, Contours, Drop Shadow Elevation & Drag Affordances*).
2. **Historical Origin & Commit Trace**: Commit `69c84e1b` (ATT-1588).
3. **Root Reason for Existing Formulation**: `REQ-UI-189` introduced a prominent 36dp x 4dp handle with `onSurfaceVariant(0.5f)` to ensure discoverability.
4. **Refinement Reason**: On-device user testing revealed the handle dominated the cockpit UI visually. Replacing high-contrast `onSurfaceVariant` with muted `outlineVariant(0.6f)` and refining dimensions to 32dp x 3dp preserves affordance while satisfying user aesthetic requirements.
5. **Preservation of Core Invariants**: Universal sheet contracts, accessibility bounds, and touch mechanics are completely preserved.

---

## 3. Acceptance Criteria (Given-When-Then)

* **AC-1 (Refined Dimensions)**:
  - *Given* `BottomSheetDesign.DragHandleWidth` and `BottomSheetDesign.DragHandleHeight`,
  - *When* inspected,
  - *Then* `DragHandleWidth` SHALL equal `32.dp` and `DragHandleHeight` SHALL equal `3.dp`.
* **AC-2 (Subtle Color & Harmonized Padding)**:
  - *Given* `MinimumDragHandle` rendered in the LiveSegment popup or modal bottom sheets,
  - *When* rendered in Light or Dark mode,
  - *Then* the pill color SHALL be `outlineVariant.copy(alpha = 0.6f)` and outer padding SHALL be `top = 8.dp, bottom = 4.dp`.
* **AC-3 (Non-Regression of Interactivity)**:
  - *Given* an active live segment popup in `SensorGridScreen`,
  - *When* dragged upward or tapped,
  - *Then* the bottom sheet SHALL expand smoothly to reveal map details without clipping.

---

## 4. Test Case Specification (TST-UI-150)

### TST-UI-150: MinimumDragHandle Dimensions and Customization Unit Test
- **Target Component**: [MinimumDragHandle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/MinimumDragHandle.kt), [BottomSheetDesign.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt)
- **Test File**: [MinimumDragHandleTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/MinimumDragHandleTest.kt)
- **Scenarios**:
  1. `testMinimumDragHandle_dimensionsConformToDesignTokens`: Assert `BottomSheetDesign.DragHandleWidth` is 32dp and `BottomSheetDesign.DragHandleHeight` is 3dp.
  2. `testMinimumDragHandle_functionExistsAndIsPublic`: Validate reflection signature and public accessibility.
  3. `testCoreComponentsIntegrity`: Ensure `CoreComponentsIntegrityTest` continues to pass.
