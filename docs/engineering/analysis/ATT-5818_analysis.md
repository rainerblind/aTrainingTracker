# Stage 1 Analysis: ATT-5818 - ClusterNamingDialog Read-Only Picker OutlinedTextField Tap Handling

**Ticket**: [ATT-5818](https://rainerblind.atlassian.net/browse/ATT-5818)  
**Sub-task**: [ATT-5915](https://rainerblind.atlassian.net/browse/ATT-5915) (`[Analysis]`)  
**Parent Epic**: [ATT-176](https://rainerblind.atlassian.net/browse/ATT-176) (*Auto Name / Route Clusters*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5818`  
**Author**: AI Agent 1 (`subagent_pro`)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`), athletes configure route assignment for recurring workouts. Currently, only tapping the trailing route icon (`my_locations`) opens the candidate selection dialog (`WorkoutClusterSelectionDialog`). Tapping directly into the text field body of "Zugeordnete Lieblingsstrecke" (`cluster_naming__selected_route_label`) does nothing because the tap is consumed before bubbling to outer modifiers. Expected: tapping anywhere on the text field body reliably opens `WorkoutClusterSelectionDialog`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Failure Mechanism**: In `ImportBackupTabsScreen.kt` (L971–992), the read-only route picker `OutlinedTextField` relies on `Modifier.fillMaxWidth().clickable { showSelectionDialog = true }`. In Jetpack Compose Material 3, an `enabled = true` `OutlinedTextField` (even with `readOnly = true`) attaches an internal pointer-input focus handler inside `BasicTextField` that consumes tap events before they bubble up to the outer `Modifier.clickable`. Only the child `trailingIcon` `IconButton` intercepts clicks because it has its own independent pointer input.
* **Independent Peer Review & Verification**:
  * Hypothesis from ATT-5762 retro verified against `ImportBackupTabsScreen.kt`: `Modifier.clickable` on L975 is completely unreachable for body taps.
  * Audit of other `readOnly` fields in `ImportBackupTabsScreen.kt`: other picker fields (L1112, L1145) use `ExposedDropdownMenuBox` with `Modifier.menuAnchor()`. `ClusterNamingDialog` uses a custom modal bottom sheet (`WorkoutClusterSelectionDialog`), which requires `MutableInteractionSource` observing `PressInteraction.Release` (Rule 77 / Design Guidelines §5.19).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  1. Add `MutableInteractionSource` to `OutlinedTextField` in `ClusterNamingDialog` and observe `PressInteraction.Release` inside a `LaunchedEffect` to trigger `showSelectionDialog = true`.
  2. Remove dead `Modifier.clickable` from `OutlinedTextField`.
  3. Add regression/contract test verifying `PressInteraction.Release` tap handling contract on the route selector field.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Modifying `WorkoutClusterSelectionDialog` or candidate scoring logic.
  - Modifying other text fields in `ClusterNamingDialog` (`workoutNameInput`, `name`).
* **Complexity Rating**: **1 SP (Trivial)**. Isolated UI interaction fix in a single composable.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Amends `REQ-UI-462` (refining `REQ-UI-462.2` / adding `REQ-UI-462.4`), adheres to Rule 77 (`docs/design_guidelines.md` §5.19).
* **Historical Origin & Commit Trace**: Commit `2e35eb81` (ATT-5674, Sprint 2026-45.10).
* **Root Reason for Existing Formulation**: ATT-5674 added escape hatches and placed `Modifier.clickable` on `OutlinedTextField`, unaware of `BasicTextField` pointer consumption on read-only fields.
* **Preservation of Core Invariants**: Trailing icon clear (`X`) and picker (`my_locations`) buttons remain fully functional; `readOnly = true` state preserved; zero schema, string, or navigation changes.

---

## 5. Architectural Strategy & High-Level Solution
* In `ClusterNamingDialog` (`ImportBackupTabsScreen.kt`):
  - Instantiate `val routePickerInteractionSource = remember { MutableInteractionSource() }`.
  - Collect `PressInteraction.Release` in `LaunchedEffect(routePickerInteractionSource)` to set `showSelectionDialog = true`.
  - Pass `interactionSource = routePickerInteractionSource` to `OutlinedTextField` and remove `.clickable { showSelectionDialog = true }`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in trailing icon actions (clear route selection vs. open picker).
  2. Text field remains strictly `readOnly = true` (no keyboard popup, no cursor).
  3. Backward compatibility for `ClusterNamingDialog` callers and queue processing.
* **Risk Rating**: **LOW** (Standard M3 interaction pattern adhering to Rule 77; zero side effects outside dialog).
