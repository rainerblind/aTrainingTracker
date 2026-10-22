# Stage 3: Implementation Plan - ATT-4285: Keep RouteTabbedScreen in composition while EditRouteScreen bottom sheet is open to prevent tab bar scroll jump

**Ticket**: [[ATT-4285]](https://rainerblind.atlassian.net/browse/ATT-4285)  
**Sub-task**: [[ATT-4342]](https://rainerblind.atlassian.net/browse/ATT-4342) (`[Impl-Plan]`)  
**Parent Epic**: [[ATT-66]](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Requirement Mapping**: `REQ-UI-423` (`REQ-UI-423.1`, `REQ-UI-423.2`, `REQ-UI-423.3`, `REQ-UI-423.4`, `REQ-UI-423.5`)  
**Test Mapping**: `TST-UI-387` (`TST-UI-387.1`, `TST-UI-387.2`, `TST-UI-387.3`, `TST-UI-387.4`, `TST-UI-387.5`)  
**Branch**: `feature/ATT-4285`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Description & Background

In **aTrainingTracker**, the Routes hub (`RoutesScreen.kt`) provides comprehensive route asset management, sorting, filtering, and preview inspection across four activity tabs (All, Bike, Run, Other). Routes can be inspected on a full-screen interactive map (`RouteOnMapScreen`), or edited directly via an inline modal bottom sheet (`EditRouteScreen.kt` wrapping `AppModalBottomSheet`).

During on-device testing in the Sprint Review of `ATT-4049` (Multi-file GPX route selection and import), a noticeable visual regression was observed when editing a route on non-zero tabs (specifically on the rightmost tab, "Sonstige" / "Other" corresponding to `BSportType.UNKNOWN`, page index 3 in `pagerState`):
1. **Abrupt Tab Bar Jump Animation**: When the athlete taps the dedicated edit action button on a route card in the rightmost tab, `EditRouteScreen` opens. Upon saving or dismissing the bottom sheet, the `PrimaryScrollableTabRow` header visibly animates/jumps from the leftmost tab ("Alle" / "All") all the way across to the rightmost tab.
2. **Loss of Collapsing Header Offset**: Any vertical scroll offset accumulated in `CollapsingAppBarNestedScrollConnection` is lost; the header snaps back to fully expanded (`appBarOffset = 0`).
3. **Empty Background Flashing During Sheet Display**: While `EditRouteScreen` is open, the underlying screen is completely unmounted, rendering a blank `Box` container behind the bottom sheet scrim rather than the dimmed routes list.

Forensic analysis revealed that `RoutesScreen.kt` (lines 186–277) placed `EditRouteScreen` inside a mutually exclusive `else if (selectedRouteIdForEdit != null)` branch alongside `RouteOnMapScreen` and `RouteTabbedScreen`. Because `RouteTabbedScreen` was unmounted when editing started and remounted upon dismissal, `PrimaryScrollableTabRow` instantiated a brand-new internal horizontal `ScrollState(0)` at Tab 0 and subsequently animated (`animateScrollTo`) to `pagerState.currentPage = 3` on first layout.

The architectural solution is to adhere to ASPICE Governance Rule 47 and Design Guidelines Section 5.8 (*Modal Overlay Composition Retention*): keep `RouteTabbedScreen` unconditionally mounted in the non-details branch and compose `EditRouteScreen` conditionally as an overlay *alongside* `RouteTabbedScreen`.

---

## 2. Traceability & Requirements Mapping

* **Primary Requirement**: `REQ-UI-423` (*Modal Overlay Composition Retention for Route Tabbed Screen During Route Editing*)
  * **`REQ-UI-423.1` (Continuous Composition Retention of `RouteTabbedScreen`)**:
    In `RoutesScreen.kt`, when `selectedRouteIdForDetails == null`, `RouteTabbedScreen` SHALL remain unconditionally mounted in the composition tree regardless of whether `selectedRouteIdForEdit` is `null` or populated with an active route ID.
    *Test Mapping*: `TST-UI-387.1` (Contract test asserting `RouteTabbedScreen` is rendered unconditionally in the non-details branch).
  * **`REQ-UI-423.2` (Modal Overlay Composition for `EditRouteScreen`)**:
    In `RoutesScreen.kt`, `EditRouteScreen` SHALL be rendered conditionally alongside `RouteTabbedScreen` inside the non-details branch whenever `selectedRouteIdForEdit != null` and a matching route is found in `routes`. The mutually exclusive `else if (selectedRouteIdForEdit != null)` branch is strictly prohibited.
    *Test Mapping*: `TST-UI-387.1` (Contract test asserting overlay composition and strict absence of `else if (selectedRouteIdForEdit != null)`).
  * **`REQ-UI-423.3` (Tab Bar Scroll Offset & Header State Preservation)**:
    Dismissing `EditRouteScreen` (via save, cancel, back button navigation, or backdrop scrim click) SHALL clear `selectedRouteIdForEdit = null` without triggering unmounting, remounting, or scroll reset of `RouteTabbedScreen`. `PrimaryScrollableTabRow` horizontal `ScrollState`, `CollapsingAppBarNestedScrollConnection.appBarOffset`, and lazy list states shall remain intact with zero tab jump.
    *Test Mapping*: `TST-UI-387.2` (Contract test verifying clean dismissal paths), `TST-UI-387.5` (On-device manual walkthrough on Google Pixel 10).
  * **`REQ-UI-423.4` (Preservation of System & Navigation Invariants)**:
    Full-screen route detail inspection (`RouteOnMapScreen` when `selectedRouteIdForDetails != null`), route updates via `viewModel.updateRoute(it)`, and cancellation logic in `EditRouteScreen` SHALL remain 100% functional.
    *Test Mapping*: `TST-UI-387.2` (Contract test asserting `viewModel.updateRoute` on save), `TST-UI-387.4` (Clean-room unit test suite pass rate under `./gradlew testDebugUnitTest`).
  * **`REQ-UI-423.5` (9-Language Parity & Zero New String Mutations)**:
    Zero new string resources are required; existing route editing strings across all 9 canonical locales maintain 100% translation parity with zero missing keys.
    *Test Mapping*: `TST-UI-387.3` (9-locale string resource audit across `values*/strings.xml`).

---

## 3. System Invariants & Preserved Behavior

1. **Modal Overlay Composition Retention Invariant (Rule 47 / Design Guidelines Sec 5.8)**:
   Modal bottom sheets (`AppModalBottomSheet`) and dialogs must always be rendered as overlays alongside the host screen, never in mutually exclusive branches that unmount the underlying screen.
2. **State Continuity Invariant**:
   Non-saveable Compose state (`PrimaryScrollableTabRow` internal `ScrollState`, `CollapsingAppBarNestedScrollConnection.appBarOffset`, lazy list states `allSportsListState`, `bikeListState`, `runListState`, `otherListState`) must not be destroyed by opening/closing `EditRouteScreen`.
3. **Full-Screen Navigation Isolation**:
   When `selectedRouteIdForDetails != null`, `RouteOnMapScreen` continues to occupy the full viewport with its dedicated top bar, map interactions, and back navigation.
4. **Data Mutation & Dismissal Symmetry**:
   Saving routes invokes `viewModel.updateRoute(it)` and clears `selectedRouteIdForEdit = null`. Canceling or back navigation clears `selectedRouteIdForEdit = null` without saving.
5. **Zero Unintended Regressions**:
   Existing feature test suites (`RoutesViewModelTest`, `RoutesScreenContractTest`, full module tests) continue to pass with 100% success.
6. **Strict Code Lock in Stage 3**:
   Zero production code or test code in `app/src/...` is modified in Stage 3. Code modifications occur exclusively in Stage 4 after passing Gate 3 audit (`check-gate`).
7. **Single Atomic Commit per Ticket (Rule 44)**:
   Zero intermediate git commits during Stage 1–4; all deliverables, code, and tests are integrated in a single atomic commit at the end of Stage 5.
8. **Parent Human Gate Invariance (Rule 3)**:
   Sub-tasks transition directly to `Erledigt` via `freigabe` upon passing Gate review; the parent ticket `ATT-4285` advances synchronously across stages and reaches terminal `Final Review (Human)` assigned to `human`.

---

## 4. Proposed Architectural Changes

### Component 1: `RoutesScreen.kt` (UI Layer / Screen Container)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
* **Role**: Host screen coordinating route tab display, route map inspection, and route editing overlay.
* **Architectural Change**:
  Restructure the container `Box` (lines 186–277) to separate full-screen destination routing from modal sheet overlays:
  1. The `if (selectedRouteIdForDetails != null)` branch remains unchanged as the full-screen destination for `RouteOnMapScreen`.
  2. The `else` branch hosts `RouteTabbedScreen` unconditionally.
  3. Inside the `else` branch, directly following `RouteTabbedScreen`, conditionally compose `EditRouteScreen` when `selectedRouteIdForEdit != null` and the route exists in `routes`.
  4. Retain `BackHandler` inside the `selectedRouteIdForEdit != null` block to intercept back button presses and dismiss the bottom sheet by setting `selectedRouteIdForEdit = null`.

```kotlin
// Architectural Blueprint in RoutesScreen.kt:
Box(modifier = Modifier.padding(paddingValues)) {
    if (selectedRouteIdForDetails != null) {
        val selectedRoute = routes.find { it.summary.id == selectedRouteIdForDetails }
        if (selectedRoute != null) {
            val backgroundPaths = remember(selectedRoute, allSegments) {
                allSegments
                    .filter { it.summary.bSportType == selectedRoute.summary.bSportType }
                    .map { it.toMapSegment(showStartAndFinishText = false) }
            }
            val isActivelyNavigating = selectedRoute.summary.id == activeNavigatedRouteId
            RouteOnMapScreen(...)
            BackHandler { selectedRouteIdForDetails = null }
        }
    } else {
        // RouteTabbedScreen is continuously mounted in the composition tree:
        RouteTabbedScreen(
            routesWithPath = routes,
            pagerState = pagerState,
            allSportsListState = allSportsListState,
            bikeListState = bikeListState,
            runListState = runListState,
            otherListState = otherListState,
            onMapClick = { id -> selectedRouteIdForDetails = id },
            onHeaderClick = { id -> selectedRouteIdForEdit = id },
            onToggleSelection = { id, isSelected -> viewModel.toggleRouteSelection(id, isSelected) },
            onDeleteConfirmed = { id -> viewModel.deleteRoute(id) },
            onDuplicateAsLocal = { id -> ... },
            onImportClick = { gpxPickerLauncher.launch("*/*") },
            onSyncStravaClick = { viewModel.syncStravaRoutes() },
            isSyncing = isSyncingStrava,
            sortOrder = sortOrder,
            onSortOrderChange = { viewModel.setSortOrder(it) },
            scrollToTop = viewModel.shouldScrollToTop(sortOrder),
            isLocationAvailable = isLocationAvailable,
            filterCriteria = filterCriteria,
            tuningConfig = tuningConfig,
            onApplyFilterCriteria = { viewModel.setFilterCriteria(it) },
            onClearAllFilters = { viewModel.clearFilterCriteria() },
            onUpdateFilterCriteria = { viewModel.updateFilterCriteria(it) }
        )

        // Modal BottomSheet overlay rendered alongside RouteTabbedScreen:
        if (selectedRouteIdForEdit != null) {
            val routeToEdit = routes.find { it.summary.id == selectedRouteIdForEdit }
            if (routeToEdit != null) {
                EditRouteScreen(
                    routeSummary = routeToEdit.summary,
                    onSave = {
                        viewModel.updateRoute(it)
                        selectedRouteIdForEdit = null
                    },
                    onCancel = {
                        selectedRouteIdForEdit = null
                    }
                )

                BackHandler {
                    selectedRouteIdForEdit = null
                }
            }
        }
    }
}
```

### Component 2: `RoutesScreenContractTest.kt` (Architectural Contract Testing)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
* **Role**: Contract and structural verification test suite for `RoutesScreen`.
* **Architectural Change**:
  Add automated contract test methods enforcing `REQ-UI-423`:
  1. `testRoutesScreen_compositionRetention_routeTabbedScreenMountedWhenEditing` (`TST-UI-387.1`):
     - Asserts `RouteTabbedScreen` is rendered unconditionally when `selectedRouteIdForDetails == null`.
     - Asserts `EditRouteScreen` is composed as an overlay inside the non-details container branch.
     - Asserts `else if (selectedRouteIdForEdit != null)` is strictly absent from `RoutesScreen.kt`.
  2. `testRoutesScreen_editRouteScreen_overlayContractsAndDismissal` (`TST-UI-387.2`):
     - Asserts `EditRouteScreen` dismissal handlers (`onSave`, `onCancel`, `BackHandler`) all properly reset `selectedRouteIdForEdit = null`.
     - Asserts `viewModel.updateRoute(it)` is called within `onSave`.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
*Per `docs/design_guidelines.md` Section 5:*
* **Reference screen / component**:
  `RouteTabbedScreen.kt` and its internal `RouteFilterBottomSheet` (lines 120–139), which exemplifies the canonical overlay pattern by composing `AppModalBottomSheet` conditionally *alongside* the screen content without unmounting the underlying view or resetting scroll states.
* **Reused components**:
  `RouteTabbedScreen` (existing tabbed route browser), `EditRouteScreen` (existing route metadata editor wrapping `AppModalBottomSheet`), `BackHandler` (Jetpack Compose platform back navigation handler).
* **Theme tokens**:
  `MaterialTheme.colorScheme.surfaceContainerHighest`, `BottomSheetDefaults.ScrimColor`, default M3 shapes/tokens.
* **New one-off styles & justification**:
  **None**. No new visual styles, custom colors, or layout tokens are introduced. This change is strictly an architectural composition hierarchy refactoring that enforces Governance Rule 47 and Design Guidelines Section 5.8 (*Modal Overlay Composition Retention*).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Codify Architectural Contract Tests (`TST-UI-387.1`, `TST-UI-387.2`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreenContractTest.kt`
* **Changes**:
  Add contract assertions for:
  - Unconditional mounting of `RouteTabbedScreen` in the non-details branch.
  - Overlay composition of `EditRouteScreen` alongside `RouteTabbedScreen`.
  - Strict absence of mutually exclusive `else if (selectedRouteIdForEdit != null)` branching.
  - Verification of `onSave`, `onCancel`, and `BackHandler` state dismissal logic.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenContractTest"
  ```

### Step 2: Refactor `RoutesScreen.kt` Layout Hierarchy (`REQ-UI-423.1`, `REQ-UI-423.2`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RoutesScreen.kt`
* **Changes**:
  Restructure lines 186–277:
  - Eliminate `else if (selectedRouteIdForEdit != null)` branch.
  - Render `RouteTabbedScreen` unconditionally in the `else` block.
  - Render `if (selectedRouteIdForEdit != null) { ... EditRouteScreen(...) ... }` as an overlay within the `else` block.
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenContractTest"
  ```

### Step 3: Run Targeted Unit Test Suite for Routes Domain (`REQ-UI-423.4`, `TST-UI-387.4`)
* **Files**: All test files under `com.atrainingtracker.trainingtracker.ui.routes`
* **Changes**: None (verification step).
* **Targeted Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesScreenContractTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RoutesViewModelTest"
  ```

### Note on Strict Code Lock:
In accordance with ASPICE Stage 3 process governance and Rule 4, NO production or test code in `app/src/...` is modified during Stage 3. Code modifications are strictly deferred to Stage 4 after the implementation plan passes Gate 3 audit (`check-gate`).

---

## 6. Verification & Rollback Plan

### 6.1 Verification Plan
1. **Targeted Unit & Contract Tests (Stage 4)**:
   - Execute `RoutesScreenContractTest` to verify structural contracts and overlay mechanics.
   - Execute `RoutesViewModelTest` to verify route mutation and selection behaviors.
2. **Clean-Room Regression Suite (Stage 5)**:
   - Execute full clean-room test suite `./gradlew testDebugUnitTest` across all modules (confirming 100% pass rate, 0 failures, 0 regressions).
3. **On-Device Interactive Walkthrough (Stage 5 - Google Pixel 10)** (`TST-UI-387.5`):
   - Navigate to Routes screen.
   - Switch to the rightmost tab ("Sonstige" / "Other", tab index 3).
   - Scroll down the routes list so the header collapses partially.
   - Tap the edit icon on a route card to open `EditRouteScreen` (`AppModalBottomSheet`).
   - Confirm route list remains visible and dimmed behind the modal scrim.
   - Tap "Abbrechen" / Cancel or press system back button.
   - Confirm `EditRouteScreen` dismisses smoothly and `PrimaryScrollableTabRow` maintains its exact position on Tab 3 with **zero horizontal jump animation** from Tab 0.
   - Confirm collapsing app bar offset and list scroll position are fully preserved.

### 6.2 Rollback Plan
- The changes are strictly isolated to `feature/ATT-4285`.
- In the event of unforeseen complications, the branch can be cleanly reset (`git reset --hard`) or deleted (`git checkout sprint/... && git branch -D feature/ATT-4285`) without any impact on `develop` or the active sprint integration branch.
