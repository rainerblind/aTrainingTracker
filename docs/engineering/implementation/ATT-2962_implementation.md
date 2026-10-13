# Stage 4 Implementation Report: ATT-2962 - Modernize in-ride fork decision card with direction grouping, clean white surfaces, and removed tap hint

**Ticket**: [ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962)  
**Sub-task**: [ATT-3032](https://atrainingtracker.atlassian.net/browse/ATT-3032) (`[Implementation]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `Sprint 2026-41.6`  
**Branch**: `feature/ATT-2962`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-10  

---

## 1. Implementation Summary

Stage 4 (Software Construction & Implementation) for [ATT-2962](https://atrainingtracker.atlassian.net/browse/ATT-2962) has been fully executed.

### 1.1 Key Modifications

1. **`ForkDecisionCard.kt` Modernization**:
   - Replaced container `surfaceVariant` with `MaterialTheme.colorScheme.surface.copy(alpha = overlayAlpha)` (crisp white in light mode) and added a subtle `BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))`.
   - Replaced the flat list of branch items with directional grouping (`decisionState.branches.groupBy { it.direction }`).
   - Implemented `ForkDirectionGroup`:
     - Direction column on the left (54dp width) with `Icons.AutoMirrored.Filled.ArrowBack/ArrowForward` or `Icons.Default.ArrowUpward` and bold primary heading label (`R.string.fork_direction_*`).
     - Subtle vertical divider separating the direction column from candidate routes.
     - Stacked candidate routes in the right column with subtle 0.5dp horizontal dividers between multiple routes.
   - Implemented `ForkRouteRow`:
     - Displays route name in `bodyMedium` bold with ellipsis.
     - Displays total distance in `km` and total elevation in `+XX m`.
     - Completely removed redundant hint text `fork_select_hint` ("Tippen zum Auswählen" / "Tap to select").
     - Retained `onRouteSelected(branch.routeId)` click interaction.
   - Replaced deprecated `Icons.Filled.CallSplit` with `Icons.AutoMirrored.Filled.CallSplit`.
   - Added Light and Dark mode `@Preview` composables.

2. **`ForkDecisionCardTest.kt` Enhancements**:
   - Added `forkDirectionGrouping_groupsBranchesByDirectionCorrectly` verifying branch grouping logic.
   - Added `forkAlertStrings_existAcrossAll9Languages` verifying 100% translation parity across EN, DE, ES, FR, IT, JA, NL, PL, PT.
   - Added `forkDecisionCardLayout_verifiesM3TokensAndStructure` asserting surface and border tokens, directional grouping, and hint text omission.

---

## 2. Test Execution

* Executed targeted tests via `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.routes.ForkDecisionCardTest"`:
  ```
  BUILD SUCCESSFUL in 6s
  ```
  All tests passed cleanly with 0 failures and 0 errors.

---

## 3. Invariants & Governance Verification

- `ForkDecisionState` and `ForkBranchOption` models unchanged.
- `onRouteSelected` and `onDismiss` callbacks fully preserved.
- Code modularity: `ForkDecisionCard.kt` is ~250 lines, well below the 400-line modularity threshold.
- Clean-room test suite ready for Gate 5.
