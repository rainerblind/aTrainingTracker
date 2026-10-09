# Stage 3 Implementation Plan: ATT-2943 - Remove duplicate upper active route banner from Route Selector dialog

**Ticket**: [ATT-2943](https://atrainingtracker.atlassian.net/browse/ATT-2943)  
**Sub-task**: [ATT-2996](https://atrainingtracker.atlassian.net/browse/ATT-2996) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2943`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-09  

---

## 1. Architectural Design & SWE.2 Boundaries

### 1.1 UI Component Architecture (`RouteSelectorSheet.kt`)
* **Layer**: UI / Jetpack Compose Presentation (`com.atrainingtracker.trainingtracker.ui.routes`).
* **Root Problem**: In `RouteSelectorContent`, an `AnimatedVisibility` block unconditionally mounts `ActiveRouteBanner` when `uiState.activeRoute != null`. In the `LazyColumn` directly beneath, `RouteCard` also displays the active route with an `"ACTIVE"` badge chip and `secondaryContainer` styling. This duplicates the active route item, crowds vertical sheet real estate, and fragments route management between two distinct card presentations.
* **Architectural Refinements**:
  1. **Banner Elimination**:
     - Remove `AnimatedVisibility(visible = uiState.activeRoute != null)` (and its following `Spacer(modifier = Modifier.height(8.dp))`) from `RouteSelectorContent`.
     - The scrollable `LazyColumn` becomes the sole presentation surface for all candidate and active routes.
  2. **Direct In-List Route Cancellation**:
     - Extend `RouteCard` with parameter `onClearRoute: (() -> Unit)? = null`.
     - When `isActive == true`, compose an `IconButton` with `Icons.Default.Close` (`contentDescription = stringResource(id = R.string.route_action_clear)`) adjacent to the `"ACTIVE"` badge chip.
     - In `RouteSelectorContent`, when composing items in `LazyColumn`:
       ```kotlin
       RouteCard(
           route = route,
           isActive = route.summary.id == uiState.activeRoute?.summary?.id,
           onClick = {
               viewModel.selectRoute(route.summary.id)
               onRouteSelected(route.summary.id)
           },
           onClearRoute = {
               viewModel.stopRoute()
           }
       )
       ```
     - Tapping the clear button halts active navigation immediately via `viewModel.stopRoute()`.
  3. **Contract & Composable Preservation**:
     - Retain `ActiveRouteBanner` function definition in `RouteSelectorSheet.kt` to preserve public composable API signatures and architectural contract compatibility (`RouteSelectorSheetTest.kt` line 64: `assertTrue(content.contains("fun ActiveRouteBanner("))`).

---

## 2. UI Consistency (Governance Rule 23)

* **Reference Screen**: `RouteSelectorSheet.kt` (M3 Modal Bottom Sheet).
* **Reused Components**:
  - `RouteCard` (existing in `RouteSelectorSheet.kt`).
  - Material 3 `IconButton` and `Icon` (`androidx.compose.material3.IconButton`, `androidx.compose.material3.Icon`).
  - Standard Material Vector Icon `Icons.Default.Close`.
  - Reused string resource `R.string.route_action_clear` (already localized in all 9 supported locales).
* **Spacing Scale**:
  - `4.dp` horizontal gap between `"ACTIVE"` badge chip and clear icon button.
  - Sizing: `24.dp` icon size with standard compact touch target.
  - Adheres strictly to Section 5.2 spacing scale (`4.dp`, `8.dp`, `12.dp`, `16.dp`).
* **Shapes & Color Tokens**:
  - `RouteCard` container color: `MaterialTheme.colorScheme.secondaryContainer` (for active route), `MaterialTheme.colorScheme.surfaceVariant` (for inactive routes).
  - Badge shape: `RoundedCornerShape(4.dp)` with `MaterialTheme.colorScheme.primary` container and `MaterialTheme.colorScheme.onPrimary` content color.
  - Clear icon tint: `MaterialTheme.colorScheme.onSecondaryContainer` or `MaterialTheme.colorScheme.error` for intuitive dismiss cue.
* **Justification for Custom Elements**: None; 100% compliant with standard M3 tokens and existing design patterns.

---

## 3. Atomic Implementation Steps

### Step 1: Update `RouteSelectorSheet.kt`
* Remove upper `AnimatedVisibility(visible = uiState.activeRoute != null)` and its spacer from `RouteSelectorContent`.
* Add `onClearRoute: (() -> Unit)? = null` parameter to `RouteCard`.
* Inside `RouteCard`, when `isActive == true`, render the clear `IconButton` beside the `"ACTIVE"` badge.
* Pass `onClearRoute = { viewModel.stopRoute() }` into `RouteCard` in `RouteSelectorContent`.
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheet.kt`.

### Step 2: Update Contract Tests in `RouteSelectorSheetTest.kt`
* Add test `testRouteSelectorContent_doesNotRenderUpperActiveRouteBanner` verifying `RouteSelectorContent` does not compose `ActiveRouteBanner(`.
* Add test `testRouteCard_supportsDirectCancellationAction` verifying parameter `onClearRoute` and close action presence.
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/routes/RouteSelectorSheetTest.kt`.

### Step 3: Run Targeted Unit Tests
* Run:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorSheetTest" --tests "com.atrainingtracker.trainingtracker.ui.routes.RouteSelectorViewModelTest"
  ```

### Step 4: Run Clean-Room Full Regression Suite
* Run `./gradlew testDebugUnitTest` verifying 100% pass rate.

---

## 4. Invariants & Risk Mitigation

* **Single Source of Truth**: The active route appears exactly once in the dialog inside the list.
* **Zero Feature Regression**: Athletes can still cancel an active route directly in 1 tap from the dialog.
* **API Stability**: Public composables and helper signatures are preserved.
* **Theme & Localization Integrity**: Uses existing localized strings and theme tokens across Light, Dark, and AMOLED modes.
