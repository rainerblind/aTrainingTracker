# Stage 2: Requirement & Test Specification - ATT-3176

## 1. Overview & Test Objectives
* **Issue**: [ATT-3176](https://atrainingtracker.atlassian.net/browse/ATT-3176): `[Verbesserung] Default Live Segment and Climb bottom sheets to fully expanded state during tracking`
* **Requirement**: `REQ-UI-343` (amending `REQ-UI-327`)
* **Verification Specification**: `TST-UI-303`
* **Sprint**: `2026-41.9`
* **Primary Objective**: Verify that when a Live Segment or Climb bottom sheet appears during active tracking (`shouldShowBottomSheet == true`), it initializes and transitions to the fully expanded state (`SheetValue.Expanded`), while preserving manual swipe down ergonomics and inactive tab hiding.

---

## 2. Traceability Matrix

| Requirement ID | Test Case ID | Test Class / Implementation | Method / Target |
| :--- | :--- | :--- | :--- |
| `REQ-UI-343.1` | `TST-UI-303.1` | `SensorGridScreenClimbSuppressionTest.kt` | `testSensorGridScreen_scaffoldState_initializesWithConditionalHiddenState` |
| `REQ-UI-343.2` | `TST-UI-303.2` | `SensorGridScreenClimbSuppressionTest.kt` | `testSensorGridScreen_launchedEffect_drivesSheetStateHideAndExpand` |
| `REQ-UI-343.3` | `TST-UI-303.3` | `SensorGridScreenClimbSuppressionTest.kt` | `testSensorGridScreen_suppressesPeekHeightAndSwipeGesturesWhenInactive` |
| `REQ-UI-343.4` | `TST-UI-303.4` | `./gradlew testDebugUnitTest` | Full clean-room regression suite |

---

## 3. Detailed Test Specification

### 3.1 Initial BottomSheet State Evaluation Contract (`TST-UI-303.1`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt`
* **Given**: `SensorGridScreen.kt` source code defining `initialSheetValue`.
* **When**: Inspected for initial state assignment.
* **Then**:
  - `initialSheetValue` evaluates to: `if (shouldShowBottomSheet) SheetValue.Expanded else SheetValue.Hidden`.
  - `scaffoldState` initializes `bottomSheetState` with `initialValue = initialSheetValue`.
  - Asserts absence of legacy `SheetValue.PartiallyExpanded` default.

### 3.2 Reactive Transition to Expanded State Contract (`TST-UI-303.2`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt`
* **Given**: `SensorGridScreen.kt` reactive driver `LaunchedEffect(shouldShowBottomSheet)`.
* **When**: `shouldShowBottomSheet` transitions from `false` to `true`.
* **Then**:
  - When `shouldShowBottomSheet == true` and current state is `Hidden`, calls `scaffoldState.bottomSheetState.expand()`.
  - When `shouldShowBottomSheet == false` and current state is not `Hidden`, calls `scaffoldState.bottomSheetState.hide()`.
  - Asserts absence of legacy `partialExpand()` call.

### 3.3 Preserved Dynamic Drag & Inactive Tab Suppression (`TST-UI-303.3`)
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/tracking/SensorGridScreenClimbSuppressionTest.kt`
* **Given**: `SensorGridScreen.kt` sheet layout configuration.
* **When**: Inactive tab or active tab is evaluated.
* **Then**:
  - `sheetSwipeEnabled` incorporates `isTabActive`, `screenMode == ScreenMode.TRACKING`, and `showLiveSegments || showLiveClimbs`.
  - When inactive, `sheetPeekHeight` is `0.dp`, container color is `Color.Transparent`, and shadow elevation is `0.dp`.

---

## 4. Acceptance Criteria & Given-When-Then Mapping

1. **Automatic Full Expansion on Climb/Segment Trigger**:
   - *Given* an active workout tracking session on a tab with live segments or live climbs enabled,
   - *When* the athlete enters or approaches an active live segment or route climb (`shouldShowBottomSheet` evaluates to `true`),
   - *Then* `scaffoldState.bottomSheetState` animates to `SheetValue.Expanded`, immediately displaying the full elevation profile canvas and HUD metrics without manual interaction.
2. **Manual Swipe Down Retention**:
   - *Given* the sheet in `SheetValue.Expanded`,
   - *When* the athlete swipes down on the sheet drag handle,
   - *Then* the sheet collapses smoothly to peek height (`SheetValue.PartiallyExpanded`), uncovering the sensor tiles.
3. **Inactive Tab & No-Segment Suppression**:
   - *Given* an active workout session with no active segment or climb (`shouldShowBottomSheet == false`),
   - *Then* `scaffoldState.bottomSheetState` remains in `SheetValue.Hidden` with `0.dp` peek height.

---

## 5. Clean-Room Regression Strategy
* Targeted unit test: `./gradlew testDebugUnitTest --tests com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGridScreenClimbSuppressionTest`.
* Clean-room full suite regression: `./gradlew testDebugUnitTest`.
* Verification mandate: 100% test pass rate with zero regressions.
