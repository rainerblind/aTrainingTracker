# Stage 3: Implementation Plan - ATT-5189: CopyTrackingTabDialog Unselected Default

**Ticket**: [[ATT-5189]](https://rainerblind.atlassian.net/browse/ATT-5189)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-439` (`REQ-UI-439.2`)  
**Test Mapping**: `TST-UI-439` (`TST-UI-439.2a`, `TST-UI-439.2b`)  
**Branch**: `feature/ATT-5189`  
**Author**: AI Agent 1 (pro / Implementer)  
**Date**: 2026-11-03  

---

## 1. Problem Description & Background
When opening `CopyTrackingTabDialog` (ATT-5078), `selectedTypes` initializes to `setOf(sourceActivityType)`. Preselecting the active profile enables the Copy action immediately and risks accidental tab duplication within the same sport type. The goal is to start with all activity types unselected (`emptySet()`) and keep the Copy action button disabled until at least one target activity profile is explicitly selected.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-439.2` (*Unselected Default & Disabled Copy Action*) $\rightarrow$ `TST-UI-439.2a` (`TrackingTabConfigHeaderContractTest.kt`)
* **Requirement**: `REQ-PRO-001` (*Non-Regression Verification*) $\rightarrow$ `TST-UI-439.2b` (Clean-room tracking tabs test suite)

---

## 3. System Invariants & Preserved Behavior
1. **User Intent Gating**: Copy action button remains disabled (`saveEnabled = false`) while `selectedTypes.isEmpty()`, activating only when $\ge 1$ target is selected.
2. **Caller Compatibility**: Composable signature of `CopyTrackingTabDialog` and `CopyTrackingTabContent` preserves `sourceActivityType: ActivityType` parameter, ensuring zero breaking changes for `TrackingTabsScreen.kt`.
3. **Transactional Deep-Copy Invariant**: SQLite transactional cloning logic in `TrackingViewsDatabaseManager` and append semantics (`LAYOUT_NR = max + 1`) remain unchanged.
4. **UI Consistency**: Reuses existing `AppBottomSheetContent`, `BottomSheetDesign`, `AppDialogActions.SaveCancel`, typography, and semantic theme tokens without custom one-off styling.

---

## 4. Proposed Architectural Changes
* **UI Layer (`CopyTrackingTabDialog.kt`)**:
  In `CopyTrackingTabContent`, replace:
  `var selectedTypes by remember { mutableStateOf(setOf(sourceActivityType)) }`
  with:
  `var selectedTypes by remember { mutableStateOf(emptySet<ActivityType>()) }`
  Existing `saveEnabled = selectedTypes.isNotEmpty()` automatically enforces button disabling.
* **Test Layer (`TrackingTabConfigHeaderContractTest.kt`)**:
  Add assertion in `testCopyTrackingTabDialog_existsAndDeclaresActivityTypeMultiSelect` verifying `selectedTypes` starts as `emptySet<ActivityType>()` and `saveEnabled = selectedTypes.isNotEmpty()`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: Update State Initialization (`CopyTrackingTabDialog.kt`)**:
   Change `selectedTypes` initialization from `setOf(sourceActivityType)` to `emptySet()`.
2. **Step 2: Update Contract Assertions (`TrackingTabConfigHeaderContractTest.kt`)**:
   Add contract verification confirming `emptySet<ActivityType>()` default and `saveEnabled = selectedTypes.isNotEmpty()`.
3. **Step 3: Unit Testing & Verification**:
   Execute targeted unit tests:
   `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*"`
4. **Step 4: Governance Verification**:
   Run `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit/contract tests, AAPT2 compilation check, and Stage 5 walkthrough.
* **Rollback**: Isolated git branch `feature/ATT-5189`; changes can be cleanly reverted prior to squashing.
