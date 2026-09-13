# Implementation Plan: Compact Workout Summary Context Menu Streamlining (ATT-993)

## 1. Executive Summary & Objective
The objective of **ATT-993** is to streamline the long-press context menu of the compact workout summary (`WorkoutSummaryCompact`):
1. **Remove "Edit workout" Action**:
   Eliminate the "Edit workout" (`@string/edit_workout`) dropdown menu item and remove the `onEditWorkout` callback parameter from `WorkoutSummaryCompact.kt`.
2. **Strict Lifecycle Scoping**:
   The compact context menu is reserved strictly for lifecycle management:
   - **"Als abgeschlossen markieren"** / **"Mark as finished"** (`@string/mark_as_finished`): available for idle unfinished sessions (`canMarkFinished == true`).
   - **"Löschen"** / **"Delete"** (`@string/delete`): available for non-actively tracked sessions (`canDelete == true`).
3. **Context Menu Trigger Invariant**:
   Long-press trigger evaluates `hasContextMenu = canDelete || canMarkFinished`.
   When a session is actively tracked (`TrainingApplication.isActivelyTracked(workoutId) == true`), both `canDelete` and `canMarkFinished` evaluate to `false`, causing `hasContextMenu` to evaluate to `false` and cleanly disabling the long-press gesture.
4. **Preserve Expanded View Editing**:
   The expanded workout summary card (`WorkoutSummary.kt` via `WorkoutHeader.kt`) retains full workout editing capabilities (`onEditWorkout`) intact.

---

## 2. Traceability & Mapped Entities

| Artifact | Identifier | Details |
| :--- | :--- | :--- |
| **Requirement** | `REQ-UI-147` | Compact Workout Summary Context Menu Streamlining (`docs/requirements.md`) |
| **Test Specification** | `TST-UI-100` | Compact Workout Summary Context Menu Streamlining Verification (`docs/tests.md`) |
| **Parent Ticket** | `ATT-993` | `[Verbesserung] Compact workout summary: Remove edit workout from context menu` |
| **Analysis Sub-task** | `ATT-994` | `[Analysis] Compact workout summary: Remove edit workout from context menu` (`Erledigt`) |
| **Test Sub-task** | `ATT-995` | `[Test-Spec] Compact workout summary: Remove edit workout from context menu` (`Erledigt`) |
| **Plan Sub-task** | `ATT-996` | `[Impl-Plan] Compact workout summary: Remove edit workout from context menu` (`In Bearbeitung`) |
| **Target Version** | `V4.9.36` | Target Release Version |
| **Target Branch** | `feature/ATT-993` | Git Working Branch off `develop` |

---

## 3. Detailed Software Design & File Changes

### 3.1 UI Compact Card ([WorkoutSummaryCompact.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompact.kt))
* Remove parameter `onEditWorkout: (() -> Unit)? = null`.
* Update `hasContextMenu`:
  ```kotlin
  val canDelete = !isActivelyTracked
  val canMarkFinished = !workoutData.headerData.finished && !isActivelyTracked && onMarkFinished != null
  val hasContextMenu = canDelete || canMarkFinished
  ```
* Remove the `if (onEditWorkout != null) { DropdownMenuItem(...) }` block from `DropdownMenu`.
* Keep `canMarkFinished` and `canDelete` menu items intact.

### 3.2 UI Workout List ([WorkoutList.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutList.kt))
* When instantiating `WorkoutSummaryCompact`, remove `onEditWorkout = { onEditWorkout(workoutData.id) }`.
* Retain `onEditWorkout` for `WorkoutSummary` in the expanded view branch.

### 3.3 Unit Tests
* **[WorkoutDeletionErgonomicsTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutDeletionErgonomicsTest.kt)**:
  Update `testCompactViewContextMenu_allowsEditEvenIfActivelyTrackedWithoutDelete` to reflect that compact view no longer offers edit, and actively tracked sessions have `hasContextMenu == false`.
* **[WorkoutSummaryCompactMenuTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummaryCompactMenuTest.kt)** (New):
  Create dedicated test suite verifying:
  1. Idle finished session: `canDelete == true`, `canMarkFinished == false`, `hasContextMenu == true`.
  2. Idle unfinished session: `canDelete == true`, `canMarkFinished == true`, `hasContextMenu == true`.
  3. Actively tracked session: `canDelete == false`, `canMarkFinished == false`, `hasContextMenu == false`.
  4. Expanded view editing preservation: `WorkoutHeader` / `WorkoutSummary` continue to support `onEditWorkout`.

---

## 4. Verification Plan

### Automated Tests
1. `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutSummaryCompactMenuTest"`
2. `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.workoutlist.WorkoutDeletionErgonomicsTest"`
3. `./gradlew testDebugUnitTest` (Clean-room full regression suite).

---

## 5. Stage Gate Audit Checklist
- [x] Clear design specification without breaking expanded view functionality.
- [x] Active tracking invariants strictly preserved.
- [x] Full automated test coverage planned.
- [x] Ready for Stage 3 sign-off.
