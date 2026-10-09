# Stage 4 Implementation: ATT-2943 - Remove duplicate upper active route banner from Route Selector dialog

**Ticket**: [ATT-2943](https://atrainingtracker.atlassian.net/browse/ATT-2943)  
**Sub-task**: [ATT-2997](https://atrainingtracker.atlassian.net/browse/ATT-2997) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2943`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Summary of Code Changes

1. **`RouteSelectorSheet.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)**:
   - Removed the duplicate upper `ActiveRouteBanner` presentation and its following spacer from `RouteSelectorContent` (`AnimatedVisibility(visible = uiState.activeRoute != null)`).
   - Removed unused `AnimatedVisibility` import.
   - Enhanced `RouteCard` signature to accept optional `onClearRoute: (() -> Unit)? = null`.
   - Updated `RouteCard` when `isActive == true` to compose an `IconButton` with `Icons.Default.Close` (`contentDescription = stringResource(id = R.string.route_action_clear)`), allowing 1-tap route cancellation directly on the active route item in the list.
   - Wired `onClearRoute = { viewModel.stopRoute() }` into `RouteCard` from `RouteSelectorContent`.
   - Preserved `ActiveRouteBanner` function definition to maintain API backward compatibility and contract stability.

2. **`RouteSelectorSheetTest.kt` (`com.atrainingtracker.trainingtracker.ui.routes`)**:
   - Added `testRouteSelectorContent_doesNotRenderUpperActiveRouteBanner` asserting that `RouteSelectorContent` does not compose `ActiveRouteBanner(` while verifying that the `ActiveRouteBanner` composable remains exposed.
   - Added `testRouteCard_supportsDirectCancellationAction` asserting `RouteCard` accepts `onClearRoute`, renders `Icons.Default.Close` with `route_action_clear`, and that `RouteSelectorContent` invokes `viewModel.stopRoute()`.

---

## 2. Targeted Verification Results

Executed targeted unit test command:
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModelTest"
```

* **`RouteSelectorSheetTest`**: 8/8 tests PASSED (including both new `TST-UI-285` tests).
* **`RouteSelectorViewModelTest`**: 16/16 tests PASSED.
* Overall: BUILD SUCCESSFUL in 19s (0 failures, 100% pass rate).
