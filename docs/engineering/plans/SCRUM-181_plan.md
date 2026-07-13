# Implementation Plan: Auto-Dismiss Tuning UI (SCRUM-181)

## 1. Problem Statement
The current UI requires the user to manually go back from the tuning screen after a recalculation. Automatically returning to the list view provides better feedback that the task is done and immediate access to the new results.

## 2. Requirement Traceability
| Requirement ID | Component | Description | Test ID |
|:---|:---|:---|:---|
| **REQ-SET-014** | UI | Auto-Dismiss Tuning UI after recalculation. | TST-SET-006 |

## 3. Impact Analysis
* **UI/UX**: Improves the flow of the route tuning feature.
* **Architecture**: Adds an event-driven mechanism to the `FrequentPathsViewModel`.

## 4. Proposed Changes
### ViewModel
* **`FrequentPathsViewModel.kt`**:
    * Add `val recalculationFinished = MutableSharedFlow<Unit>()`.
    * Emit to this flow at the end of `recalculateClusters()`.

### Fragment
* **`FrequentPathsFragment.kt`**:
    * Use a `LaunchedEffect` to observe `recalculationFinished`.
    * Set `isTuning = false` when an event is received.

## 5. Verification Criteria (TST-SET-006)
1. Open "Regular Tracks".
2. Enter "Tuning" mode.
3. Adjust a parameter and tap "Recalculate All Clusters".
4. Verify that once the progress indicator disappears, the Tuning screen closes and the List view is displayed.
