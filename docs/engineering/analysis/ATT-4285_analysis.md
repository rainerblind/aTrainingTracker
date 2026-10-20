# Stage 1 Analysis: ATT-4285 - Keep RouteTabbedScreen in composition while EditRouteScreen bottom sheet is open to prevent tab bar scroll jump

**Ticket**: [[ATT-4285]](https://rainerblind.atlassian.net/browse/ATT-4285)  
**Sub-task**: [[ATT-4340]](https://rainerblind.atlassian.net/browse/ATT-4340) (`[Analysis]`)  
**Parent Epic**: [[ATT-66]](https://rainerblind.atlassian.net/browse/ATT-66) (*Improve Routes*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-43.1`  
**Branch**: `feature/ATT-4285`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-20  

---

## 1. Problem Statement & Motivation

In **aTrainingTracker**, the Routes hub (`RoutesScreen.kt`) provides comprehensive route asset management, sorting, filtering, and preview inspection across four activity tabs (All, Bike, Run, Other). Routes can be inspected on a full-screen interactive map (`RouteOnMapScreen`), or edited directly via an inline modal bottom sheet (`EditRouteScreen.kt` wrapping `AppModalBottomSheet`).

During on-device testing in the Sprint Review of `ATT-4049` (Multi-file GPX route selection and import), a noticeable visual regression was observed when editing a route on non-zero tabs (specifically on the rightmost tab, "Sonstige" / "Other" corresponding to `BSportType.UNKNOWN`, page index 3 in `pagerState`):
1. **Abrupt Tab Bar Jump Animation**: When the athlete taps the dedicated edit action button on a route card in the rightmost tab, `EditRouteScreen` opens. Upon saving or dismissing the bottom sheet, the `PrimaryScrollableTabRow` header visibly animates/jumps from the leftmost tab ("Alle" / "All") all the way across to the rightmost tab.
2. **Loss of Collapsing Header Offset**: Any vertical scroll offset accumulated in `CollapsingAppBarNestedScrollConnection` is lost; the header snaps back to fully expanded (`appBarOffset = 0`).
3. **Empty Background Flashing During Sheet Display**: While `EditRouteScreen` is open, the underlying screen is completely unmounted, rendering a blank `Box` container behind the bottom sheet scrim rather than the dimmed routes list.

The expected behavior is that opening and dismissing `EditRouteScreen` acts as a true modal overlay over `RouteTabbedScreen`. `RouteTabbedScreen` must remain mounted in the composition tree throughout the bottom sheet lifecycle, preserving tab scroll position, list scroll states, and collapsing header offsets with zero tab bar jump animation upon dismissal.

---

## 2. Root Cause Analysis (Forensic Investigation)

Forensic inspection of `RoutesScreen.kt` (lines 186–277) revealed that `EditRouteScreen` was wired into an exclusive conditional branch:

```kotlin
// RoutesScreen.kt:186-276 (Existing problematic branching):
Box(modifier = Modifier.padding(paddingValues)) {
    if (selectedRouteIdForDetails != null) {
        val selectedRoute = routes.find { it.summary.id == selectedRouteIdForDetails }
        if (selectedRoute != null) {
            ...
            RouteOnMapScreen(...)
            BackHandler { selectedRouteIdForDetails = null }
        }
    } else if (selectedRouteIdForEdit != null) {
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
    } else {
        RouteTabbedScreen(...)
    }
}
```

### 2.1 The Modal Sheet vs. Full Screen Destination Dichotomy
In Android Jetpack Compose:
- `RouteOnMapScreen` is a **full-screen destination**. Replacing `RouteTabbedScreen` with `RouteOnMapScreen` in the Compose hierarchy is natural because `RouteOnMapScreen` occupies the entire viewport, has its own app bar and top-level interactions, and acts as a dedicated inspection view.
- In contrast, `EditRouteScreen` is **NOT a full-screen view**. Inspecting `EditRouteScreen.kt` (lines 52–80) reveals that it renders inside `AppModalBottomSheet`:
  ```kotlin
  @Composable
  fun EditRouteScreen(
      routeSummary: RouteSummary,
      onSave: (RouteSummary) -> Unit,
      onCancel: () -> Unit
  ) {
      ...
      AppModalBottomSheet(
          title = stringResource(R.string.route_edit),
          icon = Icons.Default.Edit,
          onDismissRequest = onCancel,
          actions = { ... }
      ) {
          ...
      }
  }
  ```
- `AppModalBottomSheet` delegates to Material 3's `ModalBottomSheet`, which creates a top-level modal sheet overlay with a semi-transparent scrim (`scrimColor = BottomSheetDefaults.ScrimColor`). It is fundamentally designed to sit *on top of* the host screen content.

### 2.2 Mechanism of the Tab Bar Scroll Jump
Because `RoutesScreen.kt` used `else if (selectedRouteIdForEdit != null)` instead of composing `EditRouteScreen` alongside `RouteTabbedScreen`:
1. When `selectedRouteIdForEdit` is assigned a non-null route ID, Compose executes the `else if` branch and **completely removes `RouteTabbedScreen` from the composition tree**.
2. When a composable leaves the composition, all state managed by standard `remember { ... }` blocks (which are not saved via `rememberSaveable` or hoisted to a higher scope) is permanently discarded.
3. In `RouteTabbedScreen.kt` (lines 374–386), the tab header uses Material 3's `PrimaryScrollableTabRow`:
   ```kotlin
   PrimaryScrollableTabRow(
       selectedTabIndex = pagerState.currentPage,
       containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
       divider = {}
   ) {
       tabs.forEachIndexed { index, tab ->
           Tab(
               selected = pagerState.currentPage == index,
               onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
               text = { Text(text = tab.first) }
           )
       }
   }
   ```
4. `PrimaryScrollableTabRow` encapsulates an internal horizontal `ScrollState` initialized via `rememberScrollState()`. Its default initial scroll position is `0` (scrolled fully to the left, showing Tab 0 "Alle").
5. When `EditRouteScreen` is closed (via Save, Cancel, backdrop touch, or back button), `selectedRouteIdForEdit` is reset to `null`.
6. Compose re-enters the `else` branch and instantiates `RouteTabbedScreen` from scratch.
7. `PrimaryScrollableTabRow` initializes its internal `ScrollState` at `0`.
8. Next, during its first layout and subcomposition pass, `PrimaryScrollableTabRow` inspects `selectedTabIndex` (which evaluates to `pagerState.currentPage = 3` because `pagerState` was hoisted in `RoutesScreen`).
9. Detecting that the active tab is at index 3 while its internal `ScrollState` is currently at offset 0, `PrimaryScrollableTabRow` triggers an internal animated scroll (`animateScrollTo`) from 0 to the calculated tab offset.
10. The athlete sees the tab row visibly jump to Tab 0 and then animate across the screen to Tab 3!

### 2.3 Mechanism of the Collapsing Header Offset Reset
In `RouteTabbedScreen.kt` (lines 147–149):
```kotlin
val connection = remember(appBarMaxHeightPx) {
    CollapsingAppBarNestedScrollConnection(appBarMaxHeightPx)
}
```
`connection` holds `appBarOffset` in memory. When `RouteTabbedScreen` unmounts during route editing, this connection is destroyed. When remounted, `connection` is re-created with `appBarOffset = 0`, forcing the app bar to snap back to fully expanded even if the user was scrolled halfway down a long list of routes.

### 2.4 Reference Precedent: `RouteFilterBottomSheet`
In `RouteTabbedScreen.kt` (lines 120–129), `RouteFilterBottomSheet` is an `AppModalBottomSheet` that is composed conditionally *alongside* the screen content:
```kotlin
var showFilterBottomSheet by rememberSaveable { mutableStateOf(false) }

if (showFilterBottomSheet) {
    RouteFilterBottomSheet(
        criteria = filterCriteria,
        onApplyCriteria = onApplyFilterCriteria,
        onClearAll = onClearAllFilters,
        onDismissRequest = { showFilterBottomSheet = false }
    )
}
```
Because `RouteFilterBottomSheet` does not unmount `RouteTabbedScreen`, opening and closing filter criteria produces zero tab bar jumps, preserves scroll state, and properly dims the route list behind the bottom sheet scrim. `EditRouteScreen` should adhere to the exact same overlay composition pattern.

---

## 3. User Scope Grounding (ATT-1250)

In accordance with ASPICE Governance Rule 1 and Rule 47 (ATT-4285 / Design Guidelines Sec 5.8):
* **In-Scope Goals**:
  1. Restructure the container layout in `RoutesScreen.kt` so that `RouteTabbedScreen` remains continuously mounted in the composition when `selectedRouteIdForEdit != null`.
  2. Render `EditRouteScreen` conditionally as a modal overlay alongside `RouteTabbedScreen` within the non-details branch.
  3. Ensure dismiss, save, and `BackHandler` interactions for `EditRouteScreen` continue to cleanly clear `selectedRouteIdForEdit = null`.
  4. Expand architectural contract testing in `RoutesScreenContractTest.kt` to enforce that `RouteTabbedScreen` is retained in composition when `selectedRouteIdForEdit != null` and that `EditRouteScreen` is composed as an overlay rather than an exclusive branch.
  5. Verify that existing route editing functionality (`viewModel.updateRoute`, cancel, back navigation) and route detail inspection (`RouteOnMapScreen`) remain 100% operational.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No modifications to `EditRouteScreen.kt` internal fields, layout, validation, or string tokens.
  2. No alterations to `RouteTabbedScreen.kt` tab definitions, sort orders, or filter handling.
  3. No changes to `RouteOnMapScreen.kt` full-screen navigation semantics.
  4. No modifications to database schemas, SQLite queries, or repository methods.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)

* **Original Requirement ID & Target**:
  Net-new requirement `REQ-UI-423`, refining `REQ-UI-191` (*Standardized List Item Heading Click Interaction Semantics & Dedicated Edit Affordance*) and `REQ-UI-159` (*Single-Activity Architecture & Jetpack Compose Navigation Modernization*), and enforcing Governance Rule 47 / Design Guidelines Section 5.8 (*Modal Overlay Composition Retention*).
  Targets `RoutesScreen.kt` and `RoutesScreenContractTest.kt`.
* **Historical Origin & Commit Trace**:
  - `ef96c234` (Sprint 2026-40.7, ATT-1083): Migrated `MainActivityWithNavigation` to Jetpack Compose single-activity architecture (`REQ-UI-159`); extracted `RoutesScreen` with an initial `if / else if / else` branching model.
  - `fea001b7` (Sprint 2026-41.2, ATT-1541 / `REQ-UI-191`): Standardized list item heading clicks to inspection-first and added the dedicated edit icon button to `RouteSummaryHeader` which sets `selectedRouteIdForEdit = id`.
  - Sprint Review 2026-42.10 (`ATT-4049`): Testing route editing from the rightmost tab ("Sonstige", `BSportType.UNKNOWN`, page index 3) revealed the tab bar jumping from tab 0 to tab 3 upon dismissing `EditRouteScreen`.
* **Root Reason for Existing Formulation**:
  When `RoutesScreen.kt` was first authored during the single-activity Compose migration (ATT-1083), screen-level destination states (`selectedRouteIdForDetails` and `selectedRouteIdForEdit`) were naively modeled as mutually exclusive `if / else if / else` branches. The developer did not distinguish between a full-screen replacement destination (`RouteOnMapScreen`) and a modal bottom sheet overlay (`EditRouteScreen` wrapping `AppModalBottomSheet`), inadvertently unmounting `RouteTabbedScreen` whenever a route edit was triggered.
* **Preservation of Core Invariants**:
  - Full-screen route details inspection (`selectedRouteIdForDetails != null` -> `RouteOnMapScreen`) remains completely intact.
  - Route editing save (`viewModel.updateRoute`), cancel, and dismiss behaviors in `EditRouteScreen` remain 100% intact.
  - `PrimaryScrollableTabRow`, `CollapsingAppBarNestedScrollConnection`, and list scroll states (`allSportsListState`, `bikeListState`, `runListState`, `otherListState`) are preserved without unmounting.
  - All existing `RoutesScreenContractTest` assertions pass.
  - Clean-room test suite `./gradlew testDebugUnitTest` remains at 100% pass rate.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 Call Site & Component Audit
| Component / File | Role & Call Site | Required Changes |
| :--- | :--- | :--- |
| `RoutesScreen.kt` | Host screen managing route listing, detail inspection, and editing states. | Restructure `Box` content: keep `RouteTabbedScreen` mounted in the `else` branch, and compose `if (selectedRouteIdForEdit != null) EditRouteScreen(...)` as an overlay inside the same branch. |
| `RouteTabbedScreen.kt` | Tabbed route browser with collapsing header and `PrimaryScrollableTabRow`. | No changes required. Retains composition, tab scroll state, and scroll connection offsets automatically. |
| `EditRouteScreen.kt` | Route metadata editor wrapped in `AppModalBottomSheet`. | No changes required. Receives existing callbacks. |
| `RoutesScreenContractTest.kt` | Architectural contract test suite verifying `RoutesScreen` structure. | Add automated contract tests asserting that `RouteTabbedScreen` is retained in composition when `selectedRouteIdForEdit != null` and that `EditRouteScreen` is composed as an overlay. |

### 5.2 Architectural Refactoring in `RoutesScreen.kt`

```kotlin
// Proposed Solution in RoutesScreen.kt:
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
            RouteOnMapScreen(
                route = selectedRoute.toMapRoute(isActiveNavigation = isActivelyNavigating),
                routeSummary = selectedRoute.summary,
                backgroundPaths = backgroundPaths,
                allSegments = allSegments,
                modifier = Modifier,
                onToggleSelection = { isSelected ->
                    viewModel.toggleRouteSelection(
                        selectedRoute.summary.id,
                        isSelected
                    )
                }
            )

            BackHandler {
                selectedRouteIdForDetails = null
            }
        }
    } else {
        RouteTabbedScreen(
            routesWithPath = routes,
            pagerState = pagerState,
            allSportsListState = allSportsListState,
            bikeListState = bikeListState,
            runListState = runListState,
            otherListState = otherListState,
            onMapClick = { id ->
                selectedRouteIdForDetails = id
            },
            onHeaderClick = { id ->
                selectedRouteIdForEdit = id
            },
            onToggleSelection = { id, isSelected ->
                viewModel.toggleRouteSelection(id, isSelected)
            },
            onDeleteConfirmed = { id ->
                viewModel.deleteRoute(id)
            },
            onDuplicateAsLocal = { id ->
                viewModel.duplicateRouteAsLocal(id) { success ->
                    if (success) {
                        scope.launch {
                            snackbarHostState.showSnackbar(routeSavedAsLocalMsg)
                        }
                    }
                }
            },
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

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. **Zero State Unmounting**: `RouteTabbedScreen` must remain continuously in the composition tree when `selectedRouteIdForEdit != null`.
  2. **Navigation Parity**: Full-screen inspection navigation via `selectedRouteIdForDetails` remains completely unmodified.
  3. **Data Mutation Parity**: Route updating via `viewModel.updateRoute(it)` and cancellation logic remain 100% functional.
  4. **Clean-Room Test Suite Pass Rate**: `./gradlew testDebugUnitTest` must pass with 100% success (0 regressions).
  5. **Parent Ticket Human Decision Gate**: Parent ticket `ATT-4285` must advance synchronously through ASPICE gates without moving to `Erledigt` autonomously.

* **Risk Rating**: **LOW**
  - **Technical Justification**: The change is strictly a structural Compose layout refactoring in `RoutesScreen.kt` moving `EditRouteScreen` from an exclusive `else if` branch into an overlay alongside `RouteTabbedScreen` within the `else` branch. No data models, database tables, or business logic are modified.
