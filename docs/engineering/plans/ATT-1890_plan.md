# Stage 3: Implementation Plan - ATT-1890: Interactive Draggable Splitter to Resize Map and Telemetry Viewports in MapDetailLayout

**Ticket**: [ATT-1890](https://rainerblind.atlassian.net/browse/ATT-1890)  
**Sub-task**: [ATT-1909](https://rainerblind.atlassian.net/browse/ATT-1909) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `2026-40.8`  
**Requirement Mapping**: `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*)  
**Test Mapping**: `TST-UI-177`  
**Branch**: `feature/ATT-1890`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-02  

---

## 1. Problem Description & Background

On the detailed post-workout inspection screen (`TrackOnMapScreen` / `MapDetailLayout`), the screen real estate is divided into a static ratio between Google Map at the top and telemetry graphs/analytics at the bottom. Athletes have distinct analysis workflows: route inspection requires an expansive map; telemetry graph analysis requires maximum vertical space. The fixed split limits both use cases.

This implementation plan defines the atomic engineering steps to implement a reusable, tactile draggable horizontal splitter (`SplitPaneDivider.kt`) with an isolated mathematical helper (`SplitPaneMath`), integrated into `MapDetailLayout.kt`. The splitter enables athletes to smoothly resize the map and graph viewports in real time, double-tap to reset to a balanced 50/50 split, and clamps safely to prevent viewport collapse.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-223` (*Aftermath/Map: Interactive Draggable Splitter and Dynamic Viewport Resizing in MapDetailLayout*)
* **Test Mapping**: `TST-UI-177`
  * `[TST-UI-177.1]`: Pure unit tests for `SplitPaneMath` in `SplitPaneMathTest.kt`
  * `[TST-UI-177.2]`: Visual and structural contract tests in `SplitPaneDividerVisualContractTest.kt` and `MapDetailLayoutTest.kt`
  * `[TST-UI-177.3]`: 9-language localization audit for `map_splitter_content_description`
  * `[TST-UI-177.4]`: Clean-room full suite regression (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Layout Regressions for Routes & Segments**: When `hasScrollableContent == false` (e.g. `RouteOnMapScreen`, `SegmentOnMapScreen`), the map retains full available height and lower container wraps content without displaying a divider.
2. **Gesture Isolation**: The Google Map container resides outside any scroll container; map gestures (pan, tilt, zoom, rotate) and graph horizontal scrubbing operate without interference.
3. **Multi-Chart Synchronized Scrubbing Parity**: Shared `selectedDistance` state remains synchronously wired across the map marker and all telemetry graphs.
4. **Snapshot Sharing Integrity**: Composite workout summary digest (`combineWorkoutAndShare`) captures both map and graphs correctly regardless of splitter position.
5. **Safe Boundary Clamping**: The map viewport cannot shrink below `120.dp` (`minMapHeight`), and the lower graph container cannot shrink below `160.dp` (`minLowerHeight`), preventing graphical glitches or OpenGL crashes.
6. **Subtask Self-Sufficiency**: Subtask `ATT-1909` transitions directly to `Erledigt` upon Gate 3 approval via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent ticket `ATT-1890` remains strictly reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes (SWE.2)

### Component 1: `SplitPaneDivider.kt` & `SplitPaneMath` (`ui.components.core`)
* **Package**: `com.atrainingtracker.trainingtracker.ui.components.core`
* **Pure Math Helper**:
  ```kotlin
  object SplitPaneMath {
      const val DEFAULT_SPLIT_FRACTION: Float = 0.50f
      val MIN_MAP_HEIGHT: Dp = 120.dp
      val MIN_LOWER_HEIGHT: Dp = 160.dp
      val DIVIDER_TOUCH_HEIGHT: Dp = 24.dp
      val GRIP_WIDTH: Dp = 32.dp
      val GRIP_HEIGHT: Dp = 4.dp

      fun calculateAvailableHeight(totalHeightPx: Float, dividerHeightPx: Float): Float =
          (totalHeightPx - dividerHeightPx).coerceAtLeast(1f)

      fun calculateMinFraction(minTopHeightPx: Float, availableHeightPx: Float): Float =
          (minTopHeightPx / availableHeightPx).coerceIn(0.05f, 0.95f)

      fun calculateMaxFraction(minBottomHeightPx: Float, availableHeightPx: Float, minFraction: Float): Float =
          (1f - (minBottomHeightPx / availableHeightPx)).coerceIn(minFraction, 0.95f)

      fun updateFraction(currentFraction: Float, deltaPx: Float, availableHeightPx: Float, minFraction: Float, maxFraction: Float): Float {
          val deltaFraction = deltaPx / availableHeightPx
          return (currentFraction + deltaFraction).coerceIn(minFraction, maxFraction)
      }
  }
  ```
* **Composable**:
  ```kotlin
  @Composable
  fun SplitPaneDivider(
      onDelta: (Float) -> Unit,
      onReset: () -> Unit,
      modifier: Modifier = Modifier
  )
  ```
  - Touch target height: `24.dp`.
  - Visual pill grip: `32.dp x 4.dp`, `RoundedCornerShape(2.dp)`, `MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)`.
  - Background: `MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)`.
  - Gesture Handling:
    - Vertical Drag: `Modifier.draggable(orientation = Orientation.Vertical, state = rememberDraggableState { onDelta(it) })`.
    - Double-Tap: `Modifier.pointerInput(Unit) { detectTapGestures(onDoubleTap = { onReset() }) }`.
  - Semantics: Accessibility `contentDescription = stringResource(R.string.map_splitter_content_description)`.

### Component 2: `MapDetailLayout.kt` Dynamic Integration
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* State management:
  ```kotlin
  var splitFraction by rememberSaveable { mutableFloatStateOf(SplitPaneMath.DEFAULT_SPLIT_FRACTION) }
  ```
* Resizable Container:
  When `showMap && hasScrollableContent`:
  ```kotlin
  BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
      val density = LocalDensity.current
      val totalHeightPx = constraints.maxHeight.toFloat()
      val dividerHeightPx = with(density) { SplitPaneMath.DIVIDER_TOUCH_HEIGHT.toPx() }
      val minMapHeightPx = with(density) { SplitPaneMath.MIN_MAP_HEIGHT.toPx() }
      val minLowerHeightPx = with(density) { SplitPaneMath.MIN_LOWER_HEIGHT.toPx() }

      val availableHeightPx = SplitPaneMath.calculateAvailableHeight(totalHeightPx, dividerHeightPx)
      val minFraction = SplitPaneMath.calculateMinFraction(minMapHeightPx, availableHeightPx)
      val maxFraction = SplitPaneMath.calculateMaxFraction(minLowerHeightPx, availableHeightPx, minFraction)

      Column(modifier = Modifier.fillMaxSize()) {
          // 2. Map Area
          Box(
              modifier = Modifier
                  .weight(splitFraction)
                  .heightIn(min = SplitPaneMath.MIN_MAP_HEIGHT)
                  .fillMaxWidth()
          ) {
              // Map Composable + Overlay + Share Button
          }

          // Interactive Draggable Splitter
          SplitPaneDivider(
              onDelta = { delta ->
                  splitFraction = SplitPaneMath.updateFraction(
                      splitFraction, delta, availableHeightPx, minFraction, maxFraction
                  )
              },
              onReset = {
                  splitFraction = SplitPaneMath.DEFAULT_SPLIT_FRACTION
              }
          )

          // 3. Lower Scrollable Charts & Analytics
          Column(
              modifier = Modifier
                  .weight(1f - splitFraction)
                  .fillMaxWidth()
                  .verticalScroll(rememberScrollState())
          ) {
              // Elevation Profile + Telemetry Graphs + Analytics
          }
      }
  }
  ```

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: String Resources & 9-Language Localization
* Add `map_splitter_content_description` across all 9 language resources:
  - `values/strings.xml` (EN)
  - `values-de/strings.xml` (DE)
  - `values-es/strings.xml` (ES)
  - `values-fr/strings.xml` (FR)
  - `values-it/strings.xml` (IT)
  - `values-ja/strings.xml` (JA)
  - `values-nl/strings.xml` (NL)
  - `values-pl/strings.xml` (PL)
  - `values-pt/strings.xml` (PT)

### Step 2: Implement `SplitPaneDivider.kt` & `SplitPaneMath`
* Create `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/core/SplitPaneDivider.kt`.
* Implement `SplitPaneMath` helper object and `SplitPaneDivider` composable.

### Step 3: Integrate into `MapDetailLayout.kt`
* Refactor `MapDetailLayout.kt` to use `BoxWithConstraints`, `splitFraction`, and `SplitPaneDivider` when `showMap && hasScrollableContent`.

### Step 4: Unit & Contract Tests
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/core/SplitPaneMathTest.kt`.
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/SplitPaneDividerVisualContractTest.kt`.
* Update `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutTest.kt`.

### Step 5: Verification
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "*SplitPane*" --tests "*MapDetailLayoutTest*"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted tests during construction, followed by clean-room suite (`./gradlew testDebugUnitTest`) in Stage 5.
* **Rollback**: Branch isolation (`feature/ATT-1890`) allows full revert without affecting `sprint/2026-40.8`.
