# Implementation Plan: FastScrollbar for All Collection Views (ATT-866)

* **Parent Ticket**: [ATT-866](https://rainerblind.atlassian.net/browse/ATT-866) (*[Feature] Scrollbar for all*)
* **Active Sub-Task**: [ATT-886](https://rainerblind.atlassian.net/browse/ATT-886) (*[Subtask] [Impl-Plan] Scrollbar for all*)
* **Target Version**: `V4.9.36`
* **Requirement**: [`REQ-UI-140`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L272)
* **Test Specification**: [`TST-UI-093`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L307)
* **Branch**: `feature/ATT-866`

---

## 1. Overview & Architecture

Following user alignment, we adopt **Approach 1 (`FastScrollableBox`)** to maximize code sharing and eliminate boilerplate across all collection views. 

Instead of manually duplicating `Box(modifier = Modifier.fillMaxSize())` and `FastScrollbar(modifier = Modifier.align(Alignment.CenterEnd)...)` across every screen, we introduce a shared container composable `FastScrollableBox` in [`FastScrollbar.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbar.kt). This shared container encapsulates the Box overlay and flush right-edge alignment while accepting screen-specific `topPadding` and `bottomPadding` insets.

---

## 2. Proposed Changes by Component

### 2.1 Shared Component ([`FastScrollbar.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/FastScrollbar.kt))
* **New Composable**: `FastScrollableBox`:
  ```kotlin
  /**
   * A shared layout container that overlays [FastScrollbar] flush on the right edge of a scrollable composable.
   *
   * @param state The [LazyListState] governing the contained list.
   * @param modifier Modifier applied to the outer Box container.
   * @param topPadding Top inset padding applied to the scrollbar (e.g. for collapsing headers).
   * @param bottomPadding Bottom inset padding applied to the scrollbar (e.g. for system navigation bar or FABs).
   * @param thumbColor Color of the scrollbar thumb pill.
   * @param trackColor Color of the background scrollbar track.
   * @param content Composable lambda containing the [LazyColumn] or other content.
   */
  @Composable
  fun FastScrollableBox(
      state: LazyListState,
      modifier: Modifier = Modifier,
      topPadding: Dp = 0.dp,
      bottomPadding: Dp = 0.dp,
      thumbColor: Color = MaterialTheme.colorScheme.primary,
      trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
      content: @Composable BoxScope.() -> Unit
  ) {
      Box(modifier = modifier.fillMaxSize()) {
          content()
          FastScrollbar(
              state = state,
              modifier = Modifier
                  .align(Alignment.CenterEnd)
                  .padding(top = topPadding, bottom = bottomPadding),
              thumbColor = thumbColor,
              trackColor = trackColor
          )
      }
  }
  ```

---

### 2.2 Periods View ([`PeriodList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodList.kt))
* **Change**: Wrap non-empty list with `FastScrollableBox`:
  ```kotlin
  FastScrollableBox(
      state = scrollState,
      modifier = modifier,
      bottomPadding = bottomPadding
  ) {
      LazyColumn(state = scrollState, ...) { ... }
  }
  ```

---

### 2.3 Routes View ([`RouteList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt))
* **Change**: Wrap list with `FastScrollableBox`:
  ```kotlin
  val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
  FastScrollableBox(
      state = scrollState,
      topPadding = topPadding,
      bottomPadding = bottomPadding
  ) {
      LazyColumn(state = scrollState, ...) { ... }
  }
  ```

---

### 2.4 Favorite Tracks / Clusters View ([`WorkoutClustersList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersList.kt))
* **Change**:
  * In `WorkoutClustersClusteredList`:
    ```kotlin
    val topPadding = headerHeightDp + currentAppBarOffsetDp
    FastScrollableBox(
        state = scrollState,
        topPadding = topPadding,
        bottomPadding = 80.dp // Space for FAB
    ) {
        LazyColumn(state = scrollState, ...) { ... }
    }
    ```
  * In `UnclusteredWorkoutsList`:
    Apply identical `FastScrollableBox` wrapping with `80.dp` bottom padding.

---

### 2.5 Segments View ([`SegmentList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentList.kt))
* **Change**: Wrap list with `FastScrollableBox`:
  ```kotlin
  val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
  FastScrollableBox(
      state = scrollState,
      topPadding = topPadding,
      bottomPadding = bottomPadding
  ) {
      LazyColumn(state = scrollState, ...) { ... }
  }
  ```

---

### 2.6 Equipment View ([`EquipmentTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt))
* **Change**: Expose `scrollState: LazyListState = rememberLazyListState()` in `EquipmentList` and wrap with `FastScrollableBox`:
  ```kotlin
  val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
  FastScrollableBox(
      state = scrollState,
      topPadding = topPadding,
      bottomPadding = bottomPadding
  ) {
      LazyColumn(state = scrollState, ...) { ... }
  }
  ```

---

### 2.7 Sport Types View ([`SportTypesTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt))
* **Change**: Allocate `val scrollState = rememberLazyListState()` and wrap with `FastScrollableBox`:
  ```kotlin
  val topPadding = with(density) { (appBarMaxHeightPx.toFloat() + connection.appBarOffset).toDp() }
  FastScrollableBox(
      state = scrollState,
      topPadding = topPadding,
      bottomPadding = bottomPadding
  ) {
      LazyColumn(state = scrollState, ...) { ... }
  }
  ```

---

### 2.8 Connected Devices View ([`DeviceListScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt))
* **Change**: Wrap `LazyColumn` in `DeviceListContent` with `FastScrollableBox`:
  ```kotlin
  val topPadding = if (isSearching) 0.dp else topPadding
  FastScrollableBox(
      state = scrollState,
      topPadding = topPadding,
      bottomPadding = navigationBarBottom
  ) {
      LazyColumn(state = scrollState, ...) { ... }
  }
  ```

---

### 2.9 Workout List ([`WorkoutList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt))
* **Refactor for Consistency**: Migrate `WorkoutList.kt` to also use `FastScrollableBox`, unifying all 8 scrollable lists in the codebase under the same clean abstraction.

---

## 3. System Invariants Checklist

1. **Auto-Hiding Invariant**: When lists have fewer items than the viewport, `FastScrollbar` automatically returns early and renders nothing.
2. **Touch Isolation Invariant**: The thumb's 48dp hit detection (`28.dp` wide at `Alignment.TopEnd` containing `8.dp` visual pill) leaves 95%+ of the right track area transparent to touches, guaranteeing that right-aligned controls (e.g. 3-dots menus, pairing switches, edit icons) receive tap events without dead zones or interception.
3. **Collapsing App Bar Invariant**: The scrollbar top padding connects reactively with `appBarOffsetPx`, preventing the thumb from clipping into collapsing toolbars.
4. **Zero Regressions**: No database DAOs, ViewModel logic, or data models are modified.

---

## 4. Verification Plan

### 4.1 Automated Clean-Room Regression Tests
* Command: `./gradlew testDebugUnitTest`
* Target: 0 regressions, all test suites passing.

### 4.2 On-Device Verification (Google Pixel 10 Hardware / `66020DLCR002FL`)
* **Periods Screen**: Verify fast scrollbar appears when period archive overflows; drag smoothly; verify card click actions.
* **Routes Screen**: Verify fast scrollbar appears with routes; drag up/down; verify route item toggle/menu clicks.
* **Favorite Tracks (Clusters)**: Verify fast scrollbar in clustered and unclustered tabs; verify FAB 80dp padding prevents overlap; verify cluster clicks.
* **Segments Screen**: Verify fast scrollbar appears; verify segment card inspection.
* **Equipment Screen**: Verify fast scrollbar across Bike and Shoe tabs; verify config & stats actions.
* **Sport Types Screen**: Verify fast scrollbar; verify sport type edit button.
* **Device Screen**: Verify fast scrollbar; verify pairing switch clickability.
* **Auto-Hiding Check**: Verify screens with 0 or few items display no scrollbar.
