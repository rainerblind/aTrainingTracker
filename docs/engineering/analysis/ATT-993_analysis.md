# Forensic Analysis: Compact Workout Summary: Remove Edit Workout from Context Menu (ATT-993)

## 1. Executive Summary & Problem Context
* **Ticket Key**: `ATT-993`
* **Summary**: `[Verbesserung] Compact workout summary: Remove edit workout from context menu`
* **Target Version**: `V4.9.36`
* **Target Branch**: `feature/ATT-993`
* **Stage Sub-Task**: `ATT-994` (`[SWE.1] System & Software Requirements Analysis`)

### Problem Description & User Directives
1. **Context**:
   In the Aftermath section (`WorkoutTabsScreen`, `WorkoutSummariesListFragment`, and `WorkoutClustersFragment`), workouts can be viewed in either expanded mode (`WorkoutSummary`) or high-density compact mode (`WorkoutSummaryCompact`).
2. **Current State**:
   In `WorkoutSummaryCompact.kt`, a long-press triggers a context menu (`DropdownMenu`) containing:
   * "Edit workout" (`@string/edit_workout`) with icon `ic_table_edit`.
   * "Mark as finished" (`@string/mark_as_finished`) with `Icons.Default.Check` (for unfinished sessions when idle).
   * "Delete" (`@string/delete`) with `Icons.Default.Delete` (for non-active sessions).
3. **User Directives**:
   * **Remove "Edit workout" from Compact Context Menu**:
     The context menu in `WorkoutSummaryCompact` must no longer display the "Edit workout" option.
   * **Context Menu Scoping**:
     The compact context menu is reserved strictly for lifecycle operations:
     1. **"Mark as finished"** (for old crashed/unfinished sessions when idle).
     2. **"Delete"** (for non-actively tracked sessions).
   * **Clean Invariant Preservation**:
     * Expanded view (`WorkoutSummary` / `WorkoutHeader`) retains its existing edit capabilities intact.
     * Active workout safety guard (`TrainingApplication.isActivelyTracked`) continues to prevent deletion and marking finished for actively recorded sessions.
     * When an actively tracked workout is viewed in compact mode, because neither "Delete" nor "Mark as finished" is available, and "Edit workout" is removed, `hasContextMenu` evaluates to `false`, disabling long-press entirely.

---

## 2. Forensic Architectural Breakdown

### 2.1 State Matrix: Compact View Context Menu
| Workout State | `canDelete` | `canMarkFinished` | `onEditWorkout` (Before) | `hasContextMenu` (Before) | `hasContextMenu` (After ATT-993) | Available Actions (After ATT-993) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Old Unfinished, Idle** | `true` | `true` | Present | `true` | `true` | "Mark as finished", "Delete" |
| **Finished, Idle** | `true` | `false` | Present | `true` | `true` | "Delete" |
| **Actively Tracked (Live)** | `false` | `false` | Present | `true` (Edit only) | `false` | None (Long-press disabled) |

### 2.2 Component Interaction Flow
```mermaid
flowchart TD
    A[User Long-Presses Compact Card] --> B{Evaluate hasContextMenu}
    B -->|canDelete || canMarkFinished| C[Display Compact Context Menu]
    B -->|Neither| D[Ignore Long-Press]
    
    C --> E{Available Items}
    E -->|canMarkFinished == true| F[DropdownMenuItem: Mark as finished]
    E -->|canDelete == true| G[DropdownMenuItem: Delete]
    
    style F fill:#e1f5fe,stroke:#0288d1
    style G fill:#ffebee,stroke:#d32f2f
```

---

## 3. Implementation Surfaces Identified

| Layer | Component File | Planned Modifications |
| :--- | :--- | :--- |
| **UI Compact** | `WorkoutSummaryCompact.kt` | • Remove `onEditWorkout: (() -> Unit)? = null` parameter.<br>• Remove `DropdownMenuItem` for `R.string.edit_workout`.<br>• Update `hasContextMenu = canDelete || canMarkFinished`. |
| **UI List** | `WorkoutList.kt` | • Remove `onEditWorkout` argument when instantiating `WorkoutSummaryCompact`.<br>• Preserve `onEditWorkout` argument for full `WorkoutSummary`. |
| **Automated Tests** | `WorkoutDeletionErgonomicsTest.kt` | • Update compact view context menu tests to reflect removal of `onEditWorkout`.<br>• Verify that actively tracked workouts in compact view have `hasContextMenu == false`. |
| **Automated Tests** | `WorkoutSummaryCompactMenuTest.kt` | • Create dedicated unit test suite validating compact context menu items under all lifecycle states (idle unfinished, idle finished, active session). |
| **Traceability** | `docs/requirements.md` | Add requirement `REQ-UI-147` ("Compact Workout Summary Context Menu Streamlining"). |
| **Traceability** | `docs/tests.md` | Add test specification `TST-UI-100` ("Compact Workout Summary Context Menu Streamlining Verification"). |

---

## 4. Verification & Testing Strategy
1. **Unit Tests (`WorkoutSummaryCompactMenuTest.kt` & `WorkoutDeletionErgonomicsTest.kt`)**:
   * Verify compact context menu omits "Edit workout" under all conditions.
   * Verify compact context menu renders "Mark as finished" and "Delete" for idle unfinished workouts.
   * Verify compact context menu renders only "Delete" for idle completed workouts.
   * Verify compact context menu does not trigger (`hasContextMenu == false`) for actively tracked workouts.
2. **Regression Verification**:
   * Run clean-room `./gradlew testDebugUnitTest` across the full test suite (0 regressions).
3. **Traceability**:
   * Register `REQ-UI-147` and `TST-UI-100` in documentation.

---

## 5. Stage Gate Audit Criteria
- [x] Scope strictly bounded to removing edit action from compact view context menu.
- [x] Full view (`WorkoutSummary`) editing ergonomics preserved intact.
- [x] Active tracking safety invariants maintained.
- [x] Test and regression strategy established.
- [x] Ready for Stage 1 sign-off.
