# Implementation Plan: FastScrollbar for All Collection Views (ATT-866)

* **Parent Ticket**: [ATT-866](https://rainerblind.atlassian.net/browse/ATT-866) (*[Feature] Scrollbar for all*)
* **Active Sub-Task**: [ATT-886](https://rainerblind.atlassian.net/browse/ATT-886) (*[Subtask] [Impl-Plan] Scrollbar for all*)
* **Target Version**: `V4.9.36`
* **Requirement**: [`REQ-UI-140`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/requirements.md#L272)
* **Test Specification**: [`TST-UI-093`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/tests.md#L307)
* **Branch**: `feature/ATT-866`

---

## 1. Overview & Architecture

Following the verification of the edge-aligned, continuous drag-accumulating `FastScrollbar` in `WorkoutList` (under ATT-861), ATT-866 scales this fast-scroll capability across all 7 primary `LazyColumn` collection screens in the application:

1. **Periods (Zeiträume)**: [`PeriodList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodList.kt)
2. **Routes (Routen)**: [`RouteList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt)
3. **Favorite Tracks (Lieblingsstrecken)**: [`WorkoutClustersList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersList.kt) (`WorkoutClustersClusteredList` & `UnclusteredWorkoutsList`)
4. **Segments (Segmente)**: [`SegmentList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentList.kt)
5. **Equipment (Ausrüstung)**: [`EquipmentTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt)
6. **Sport Types (Sportarten)**: [`SportTypesTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt)
7. **Connected Devices (Geräte)**: [`DeviceListScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt)

---

## 2. Proposed Changes by Component

### 2.1 Periods View ([`PeriodList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodList.kt))
* **Change**: Wrap non-empty `LazyColumn` inside a `Box(modifier = modifier.fillMaxSize())`.
* **Add FastScrollbar**:
  ```kotlin
  FastScrollbar(
      state = scrollState,
      modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(bottom = bottomPadding)
  )
  ```
* **Preserved Behavior**: `EmptyStatePlaceholder` remains unaffected when periods are empty; bottom system bar padding is preserved.

---

### 2.2 Routes View ([`RouteList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteList.kt))
* **Change**: Wrap `LazyColumn` in a `Box(modifier = Modifier.fillMaxSize())`.
* **Add FastScrollbar**:
  ```kotlin
  val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
  FastScrollbar(
      state = scrollState,
      modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(top = topPadding, bottom = bottomPadding)
  )
  ```
* **Preserved Behavior**: Collapsing toolbar connection (`appBarOffsetPx`) dynamically offsets the top bounds of the scrollbar in sync with the header.

---

### 2.3 Favorite Tracks / Clusters View ([`WorkoutClustersList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersList.kt))
* **Change**:
  * In `WorkoutClustersClusteredList`: Wrap `LazyColumn` in a `Box(modifier = Modifier.fillMaxSize())`.
    ```kotlin
    val topPadding = headerHeightDp + currentAppBarOffsetDp
    FastScrollbar(
        state = scrollState,
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .padding(top = topPadding, bottom = 80.dp)
    )
    ```
  * In `UnclusteredWorkoutsList`: Apply identical `Box` wrapping and `FastScrollbar`.
* **Preserved Behavior**: Bottom padding of `80.dp` ensures the scrollbar does not clip behind the FloatingActionButton (FAB).

---

### 2.4 Segments View ([`SegmentList.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/segments/segmentlist/SegmentList.kt))
* **Change**: Wrap `LazyColumn` in a `Box(modifier = Modifier.fillMaxSize())`.
* **Add FastScrollbar**:
  ```kotlin
  val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
  FastScrollbar(
      state = scrollState,
      modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(top = topPadding, bottom = bottomPadding)
  )
  ```
* **Preserved Behavior**: Collapsing app bar offset and system bottom padding preserved; Strava badge and segment cards untouched.

---

### 2.5 Equipment View ([`EquipmentTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/EquipmentTabsScreen.kt))
* **Change**: In `EquipmentList`, add default parameter `scrollState: LazyListState = rememberLazyListState()`, wrap `LazyColumn` in a `Box(modifier = Modifier.fillMaxSize())`.
* **Add FastScrollbar**:
  ```kotlin
  val topPadding = with(density) { (headerHeightPx + appBarOffsetPx).toDp() }
  FastScrollbar(
      state = scrollState,
      modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(top = topPadding, bottom = bottomPadding)
  )
  ```
* **Preserved Behavior**: Bike and shoe card options menu, stats sheet, and delete workflows untouched.

---

### 2.6 Sport Types View ([`SportTypesTabsScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/sporttype/SportTypesTabsScreen.kt))
* **Change**: In `SportTypesTabsScreen`, allocate `val scrollState = rememberLazyListState()`, pass `state = scrollState` to `LazyColumn`, and wrap in `Box(modifier = Modifier.fillMaxSize())`.
* **Add FastScrollbar**:
  ```kotlin
  val topScrollbarPadding = with(density) { (appBarMaxHeightPx.toFloat() + connection.appBarOffset).toDp() }
  FastScrollbar(
      state = scrollState,
      modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(top = topScrollbarPadding, bottom = bottomPadding)
  )
  ```
* **Preserved Behavior**: Card clicks, edit dialogs, stats bottom sheet, and delete triggers remain 100% accessible.

---

### 2.7 Connected Devices View ([`DeviceListScreen.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicelist/DeviceListScreen.kt))
* **Change**: In `DeviceListContent`, wrap `LazyColumn` in a `Box(modifier = Modifier.fillMaxSize())`.
* **Add FastScrollbar**:
  ```kotlin
  val topScrollbarPadding = if (isSearching) 0.dp else topPadding
  FastScrollbar(
      state = scrollState,
      modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(top = topScrollbarPadding, bottom = navigationBarBottom)
  )
  ```
* **Preserved Behavior**: Pair toggle button, device inspection clicks, and long-click deletion remain 100% accessible.

---

## 3. System Invariants Checklist

1. **Auto-Hiding Invariant**: `FastScrollbar`'s internal guard (`totalItems <= visibleItems || totalItems == 0`) ensures that when collections are short or empty, the scrollbar returns early and renders nothing.
2. **Touch Isolation Invariant**: The thumb's 48dp hit detection (`28.dp` wide, `8.dp` visual pill) leaves 95%+ of the right track area transparent to touch, guaranteeing that right-aligned controls (e.g. 3-dots menus, pairing switches, edit icons) receive tap events without dead zones or interception.
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
