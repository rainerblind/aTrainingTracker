# Stage 3 Implementation Plan: ATT-1644

**Ticket**: [ATT-1644](https://atrainingtracker.atlassian.net/browse/ATT-1644)  
**Sub-task**: [ATT-1666](https://atrainingtracker.atlassian.net/browse/ATT-1666) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.5`  
**Branch**: `feature/ATT-1644`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-09-30  

---

## 1. Traceability & Scope Matrix
* **Requirements Traced**: `REQ-UI-196` (*LiveSegment & Bottom Sheets: Refined Subtle Drag Handle & Harmonized Popup Spacing*)
* **Tests Traced**: `TST-UI-150` (*MinimumDragHandle Dimensions and Customization Unit Test*)
* **Scope Definition**: Refine `BottomSheetDesign.kt` tokens, enhance `MinimumDragHandle.kt` with subtle M3 styling and harmonized padding, update `MinimumDragHandleTest.kt`.

---

## 2. Step-by-Step Atomic Implementation Tasks

### Task 1: Update Design Tokens in `BottomSheetDesign.kt`
- File: [BottomSheetDesign.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/BottomSheetDesign.kt)
- Modify `DragHandleWidth`:
  ```kotlin
  /** Standardized drag handle pill width (32dp). */
  val DragHandleWidth: Dp = 32.dp
  ```
- Modify `DragHandleHeight`:
  ```kotlin
  /** Standardized drag handle pill height (3dp). */
  val DragHandleHeight: Dp = 3.dp
  ```

### Task 2: Refine Composable in `MinimumDragHandle.kt`
- File: [MinimumDragHandle.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/MinimumDragHandle.kt)
- Refine method signature to provide configurable parameters with sleek M3 defaults:
  ```kotlin
  @Composable
  fun MinimumDragHandle(
      modifier: Modifier = Modifier,
      width: Dp = BottomSheetDesign.DragHandleWidth,
      height: Dp = BottomSheetDesign.DragHandleHeight,
      color: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
      topPadding: Dp = 8.dp,
      bottomPadding: Dp = 4.dp
  ) {
      Box(
          modifier = modifier
              .fillMaxWidth()
              .padding(top = topPadding, bottom = bottomPadding),
          contentAlignment = Alignment.Center
      ) {
          Surface(
              modifier = Modifier.size(width = width, height = height),
              color = color,
              shape = CircleShape
          ) {
              Box(Modifier.matchParentSize())
          }
      }
  }
  ```

### Task 3: Update Unit Tests in `MinimumDragHandleTest.kt`
- File: [MinimumDragHandleTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/MinimumDragHandleTest.kt)
- Update `testMinimumDragHandle_dimensionsConformToDesignTokens` to assert `32` and `3`.
- Verify `testMinimumDragHandle_functionExistsAndIsPublic` continues to validate public reflection contract.

### Task 4: Targeted Unit Verification
- Execute targeted unit test suite:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.*"
  ```
- Ensure 100% pass rate.

---

## 3. Invariant & Regression Guards
1. **Accessibility & Gestures**: The container `Box` retains its full width and padding envelope to catch vertical drag gestures reliably for `BottomSheetScaffold`.
2. **Backward Compatibility**: Existing call sites (`MapScreenWithTrack.kt`, `MapDetailLayout.kt`, `AppModalBottomSheet.kt`, `FilterBottomSheetScaffold.kt`) invoke `MinimumDragHandle()` with zero arguments, seamlessly picking up the refined styling.
3. **No Database or API Mutations**: Pure UI layer modification.
