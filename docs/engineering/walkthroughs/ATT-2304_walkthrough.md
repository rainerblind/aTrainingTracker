# Stage 5: Walkthrough & Verification - ATT-2304: Restore Immediate Collapsing Header Expansion When Scrolling Down in Workout Details

**Ticket**: [ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304)  
**Sub-task**: [ATT-2348](https://rainerblind.atlassian.net/browse/ATT-2348) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Review Workouts*)  
**Target Release**: `V4.9.40`  
**Active Sprint**: `2026-40.15`  
**Requirement Mapping**: `REQ-UI-266`  
**Test Mapping**: `TST-UI-225`  
**Branch**: `feature/ATT-2304`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-04  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the full verification and release qualification for [ATT-2304](https://rainerblind.atlassian.net/browse/ATT-2304). The collapsing header interaction in Workout Details (and all collapsible screens across the application) has been restored to standard Material Design Quick Return behavior:
1. **Root-Cause Resolution**: In commit `91fb65c1` (ATT-2178), downward gesture consumption was moved from `onPreScroll` to `onPostScroll`. Because child scrollable containers (`LazyColumn`) consumed downward gestures before reaching their top boundary, `onPostScroll` never received downward scroll deltas while the list was scrolled down. This completely blocked the collapsing header from expanding when scrolling down.
2. **Immediate Quick Return Dispatch**: Restored downward scroll consumption in `onPreScroll` within `CollapsingAppBarNestedScrollConnection.kt`:
   - When downward gestures occur (`available.y > 0`) while `rawOffset < 0f`, `onPreScroll` immediately consumes the delta and expands the app bar towards `0f`.
   - Once the app bar is fully expanded (`rawOffset == 0f`), `onPreScroll` returns `Offset.Zero`, allowing subsequent or remaining downward scroll deltas to pass cleanly to child containers.
3. **Sub-Pixel Precision & Float Accumulation**: Preserved sub-pixel precision accumulation in `rawOffset` to eliminate integer truncation micro-stutters during expansion and collapse.
4. **Backward Compatibility**: Added `quickReturn: Boolean = true` parameter, ensuring 100% API compatibility across all 10 call sites in the codebase.
5. **Clean-Room Regression**: Full clean-room test execution passed with 100% success across the entire unit test suite (32 tasks, 0 failures in 4m 34s).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-266` | `TST-UI-225.1` | Unit Test (`CollapsingAppBarNestedScrollConnectionTest`): immediate downward consumption in `onPreScroll` while collapsed | **PASSED** | `Verified` |
| `REQ-UI-266` | `TST-UI-225.2` | Unit Test (`CollapsingAppBarNestedScrollConnectionTest`): clamping at `0f` and unconsumed excess delta handoff to child container | **PASSED** | `Verified` |
| `REQ-UI-266` | `TST-UI-225.3` | Unit Test (`CollapsingAppBarNestedScrollConnectionTest`): upward collapse in `onPreScroll` and non-quick return mode compatibility | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-UI-225.4` | Clean-Room Full Suite Regression (`./gradlew testDebugUnitTest`) | **PASSED** (100% pass rate) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
BUILD SUCCESSFUL in 4m 34s
32 actionable tasks: 12 executed, 20 up-to-date
All unit tests completed with 0 failures, 0 skipped
```

### Targeted Nested Scroll Tests (`CollapsingAppBarNestedScrollConnectionTest`)
```text
CollapsingAppBarNestedScrollConnectionTest:
- testDownwardScroll_consumesInPreScroll_andExpandsImmediately: PASSED (Quick Return - REQ-UI-266)
- testDownwardScroll_clampsAtZero_andDefersExcessToChild: PASSED (clamp at 0f, handoff excess to child)
- testDownwardScroll_whenQuickReturnDisabled_defersToPostScroll: PASSED (compatibility mode)
- testUpwardScroll_consumesInPreScroll: PASSED (smooth collapse)
- testUpwardScroll_clampsAtMaxHeight: PASSED (clamp at -maxHeight)
- testFloatAccumulation_handlesSubPixelOffsets: PASSED (sub-pixel float precision)
```

---

## 4. Hardware / Physical Verification (Pixel 10)

1. **Build Validation**:
   - Executed `./gradlew assembleDebug` with 100% success.
2. **Behavioral Inspection**:
   - In Workout Details (`WorkoutDetailsScreen`), downward gestures immediately expand the header tabs regardless of list scroll position.
   - When the header reaches full height, continuing the downward gesture smoothly scrolls the child `LazyColumn`.
   - Upward gestures collapse the header tabs smoothly until hidden, keeping the sticky action bar pinned.

---

## 5. Invariant & Governance Verification

1. **Non-Breaking API Contract**: Default parameter `quickReturn: Boolean = true` maintains compatibility across all call sites (`KnownLocationsScreen`, `SensorsOverviewScreen`, `WorkoutDetailsScreen`, `RoutesScreen`, `SegmentsScreen`, etc.).
2. **Living Documentation Synchronized**: Status in `docs/requirements.md` (`REQ-UI-266`) and `docs/tests.md` (`TST-UI-225`) updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-2348` transitioned to `Erledigt` via `freigabe`.
4. **Parent Ticket Final Review**: Parent ticket `ATT-2304` moved to `Final Review (Human)` and assigned to `human` per Rule 1 and Rule 14.
5. **Continuous Sprint Branch Integration (Strategy A)**: Merged `feature/ATT-2304` into `sprint/2026-40.15` via `--no-ff` and pruned the local feature branch.
