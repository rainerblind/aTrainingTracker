# Stage 3 Implementation Plan: ATT-2962 - Modernize in-ride fork decision card with direction grouping, clean white surfaces, and removed tap hint

**Ticket**: [ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962)  
**Sub-task**: [ATT-3031](https://atrainingtracker.atlassian.net/browse/ATT-3031) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2962`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Executive Summary & Architecture

This implementation plan defines the atomic construction steps for modernizing `ForkDecisionCard.kt` ([ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962)) in accordance with `REQ-UI-330` and `TST-UI-290`.

The plan refactors the visual design and layout:
1. **Clean Surfaces & Borders**:
   - Container card adopts `MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)` with a 1dp `outlineVariant` border.
   - Inner direction group cards adopt `MaterialTheme.colorScheme.surface` with a 1dp `outlineVariant.copy(alpha = 0.6f)` border and `RoundedCornerShape(12.dp)`.
2. **Directional Grouping**:
   - `decisionState.branches.groupBy { it.direction }`.
   - For each group: left-aligned direction column (arrow icon + localized label), subtle vertical divider, right-aligned stacked route candidates separated by 0.5dp `HorizontalDivider`.
3. **Redundant Hint Removal**:
   - Remove `fork_select_hint` ("Tippen zum Auswählen" / "Tap to select") completely from route rows.
4. **Interactive Callbacks**:
   - Clicking any candidate row invokes `onRouteSelected(branch.routeId)`.
   - Clicking close invokes `onDismiss()`.
5. **Previews & Modularity**:
   - Provide `@Preview` composables for light and dark modes.
   - Maintain modularity under 300 lines.

---

## 2. Atomic Implementation Steps

### Step 1: Composable Refactoring in `ForkDecisionCard.kt`
* Outer Card:
  - `containerColor = MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)`
  - `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`
* Direction Grouping:
  - `val groupedBranches = decisionState.branches.groupBy { it.direction }`
  - For each `(direction, branchList)`:
    - Render `ForkDirectionGroup(direction = direction, branches = branchList, onRouteSelected = onRouteSelected)`
* `ForkDirectionGroup`:
  - `Card` or `Surface` with `shape = RoundedCornerShape(12.dp)`, `color = MaterialTheme.colorScheme.surface`, `border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))`.
  - Row layout containing:
    - Left column (width 54dp, centered): direction icon (24dp) and label in `labelSmall` bold primary color.
    - Vertical divider (`width = 0.5.dp`, `outlineVariant.copy(alpha = 0.5f)`).
    - Right column (`Modifier.weight(1f)`): `branchList.forEachIndexed { index, branch -> ... }` with `HorizontalDivider` between items.
* `ForkRouteRow`:
  - Clickable row with padding:
    - `routeName` in `bodyMedium` bold, `maxLines = 1`, `TextOverflow.Ellipsis`.
    - Distance (`%.1f km`) and elevation (`+XX m`) in `bodySmall` onSurfaceVariant.
    - Hint text omitted completely.
* Add `@Preview` composables (`ForkDecisionCardPreview_Light`, `ForkDecisionCardPreview_Dark`).

### Step 2: Contract Tests in `ForkDecisionCardTest.kt`
* Add tests:
  - Direction grouping logic assertion.
  - Verification that string resources exist for 9 languages.
  - Layout verification tests.
* Execute targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.ForkDecisionCardTest"`.

### Step 3: Clean-Room Full Suite Regression
* Execute `./gradlew testDebugUnitTest` verifying 100% test pass rate across all suites.

---

## 3. Invariants & Governance

1. **Chesterton's Fence Preservation**:
   - `onRouteSelected(branch.routeId)` and `onDismiss()` callbacks strictly preserved.
   - `ForkDecisionState` and `ForkBranchOption` data models untouched.
   - Animated visibility transitions (`fadeIn + expandVertically`) preserved.
2. **Localization & Accessibility**:
   - 100% string resource parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
3. **Clean-Room Test Pass Rate**:
   - 100% unit test pass rate.

---

## 4. Gate 3 Readiness Checklist

- [x] All requirements mapped to atomic implementation steps.
- [x] Composable structure adheres to Material 3 tokens.
- [x] Hint text removal validated against AC.
- [x] 100% clean-room test plan in place.
