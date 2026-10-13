# Stage 3: Implementation Plan - ATT-3176

## 1. Executive Summary & Architectural Invariants (SWE.2)
* **Issue**: [ATT-3176](https://atrainingtracker.atlassian.net/browse/ATT-3176): `[Verbesserung] Default Live Segment and Climb bottom sheets to fully expanded state during tracking`
* **Parent Epic**: [ATT-355](https://atrainingtracker.atlassian.net/browse/ATT-355): *Good and consistent UI*
* **Requirement**: `REQ-UI-343` (amending `REQ-UI-327`)
* **Test Specification**: `TST-UI-303`
* **Sprint**: `2026-41.9`

### Architectural Invariants
1. **Default Full Presentation**: When a Live Climb or Live Segment becomes active on an active tracking tab (`shouldShowBottomSheet == true`), the bottom sheet must initialize and animate directly to `SheetValue.Expanded`.
2. **Interactive Manual Swipe Down**: The athlete retains manual gesture control (`sheetSwipeEnabled = true`), enabling swiping down to peek height (`SheetValue.PartiallyExpanded`) or dismiss it.
3. **Strict Tab Isolation & Pre-composition Safety**: When `!shouldShowBottomSheet` (inactive tab, disabled climbs/segments, or non-tracking screen mode), the sheet state must hide (`SheetValue.Hidden`), peek height must be `0.dp`, container color `Transparent`, and elevation `0.dp`, preventing visual artifacts or drag contention in `HorizontalPager`.
4. **Scope Bounding (Rule 26 / ATT-1250)**: Confined strictly to `SensorGridScreen.kt` and `SensorGridScreenClimbSuppressionTest.kt`. Zero changes to sensor drivers, database schemas, or Android manifests.

---

## 2. Detailed Component Architecture

```
+-------------------------------------------------------------------------+
| SensorGridScreen (Tracking Tab)                                         |
|                                                                         |
|  isTabActive && (showLiveSegments || showLiveClimbs)                    |
|  && screenMode == ScreenMode.TRACKING                                   |
|       │                                                                 |
|       ▼                                                                 |
|  shouldShowBottomSheet: Boolean                                         |
|       │                                                                 |
|       ├──► initialSheetValue:                                           |
|       │      if (shouldShowBottomSheet) SheetValue.Expanded             |
|       │      else SheetValue.Hidden                                     |
|       │                                                                 |
|       └──► LaunchedEffect(shouldShowBottomSheet)                       |
|              ├── true  ──► scaffoldState.bottomSheetState.expand()     |
|              └── false ──► scaffoldState.bottomSheetState.hide()       |
|                                                                         |
|  BottomSheetScaffold:                                                   |
|       ├── sheetPeekHeight: if (shouldShow) PeekHeight + insets else 0dp |
|       ├── sheetSwipeEnabled: shouldShow                                 |
|       └── sheetContent: LiveSegmentSheet or LiveClimbSheet              |
+-------------------------------------------------------------------------+
```

---

## 3. Step-by-Step Construction Plan (Atomic Steps)

### Step 1: Update BottomSheet State Initialization in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Lines**: 230-237
* **Action**:
  Change:
  ```kotlin
  val initialSheetValue = if (shouldShowBottomSheet) SheetValue.PartiallyExpanded else SheetValue.Hidden
  ```
  To:
  ```kotlin
  val initialSheetValue = if (shouldShowBottomSheet) SheetValue.Expanded else SheetValue.Hidden
  ```

### Step 2: Update Reactive State Transition in `SensorGridScreen.kt`
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreen.kt`
* **Lines**: 240-250
* **Action**:
  Change:
  ```kotlin
  LaunchedEffect(shouldShowBottomSheet) {
      if (!shouldShowBottomSheet) {
          if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
              scaffoldState.bottomSheetState.hide()
          }
      } else {
          if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
              scaffoldState.bottomSheetState.partialExpand()
          }
      }
  }
  ```
  To:
  ```kotlin
  LaunchedEffect(shouldShowBottomSheet) {
      if (!shouldShowBottomSheet) {
          if (scaffoldState.bottomSheetState.currentValue != SheetValue.Hidden) {
              scaffoldState.bottomSheetState.hide()
          }
      } else {
          if (scaffoldState.bottomSheetState.currentValue == SheetValue.Hidden) {
              scaffoldState.bottomSheetState.expand()
          }
      }
  }
  ```

### Step 3: Update Architectural Contract Test in `SensorGridScreenClimbSuppressionTest.kt`
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt`
* **Actions**:
  1. In `testSensorGridScreen_scaffoldState_initializesWithConditionalHiddenState`:
     Update assertion to expect `"val initialSheetValue = if (shouldShowBottomSheet) SheetValue.Expanded else SheetValue.Hidden"`.
  2. In `testSensorGridScreen_launchedEffect_drivesSheetStateHideAndPartialExpand` (rename to `testSensorGridScreen_launchedEffect_drivesSheetStateHideAndExpand`):
     Update assertion to expect `"scaffoldState.bottomSheetState.expand()"`.

---

## 4. Test Strategy & Verification Gate Alignment

1. **Targeted Unit Test**:
   - Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest`.
   - Validate that all 7 test cases pass cleanly.
2. **Clean-Room Full Suite Regression**:
   - Run `./gradlew testDebugUnitTest`.
   - Verify 100% test pass rate across all 2,253+ unit tests with zero regressions.
3. **Requirement Governance Validation**:
   - Run `python3 tools/verify_requirement_governance.py --base-ref sprint/2026-41.9`.

---

## 5. Risk Assessment & Rollback Strategy

| Risk | Probability | Severity | Mitigation |
| :--- | :---: | :---: | :--- |
| Athlete prefers looking at sensor tiles over climb elevation profile | Low | Low | Bottom sheet retains full downward swipe gesture (`sheetSwipeEnabled = true`) allowing collapse to peek height at any time. |
| Cross-tab state bleeding in `HorizontalPager` | Very Low | High | `isTabActive` strictly enforces `SheetValue.Hidden` and `0.dp` peek height on inactive tabs. |
| Inadvertent regression | None | High | Bounded strictly to 2 lines of code in `SensorGridScreen.kt` and validated by 2,253+ automated tests. |
| **Rollback Plan** | In the unlikely event of an issue, reverting commit restores legacy `PartiallyExpanded` and `partialExpand()` in under 1 minute. |
