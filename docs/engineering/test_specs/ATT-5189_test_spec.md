# Stage 2: Requirement & Test Specification - ATT-5189: CopyTrackingTabDialog Unselected Default

**Ticket**: [[ATT-5189]](https://rainerblind.atlassian.net/browse/ATT-5189)  
**Parent Epic**: [[ATT-355]](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-439` (`REQ-UI-439.2`)  
**Test Mapping**: `TST-UI-439` (`TST-UI-439.2`)  
**Branch**: `feature/ATT-5189`  
**Author**: AI Agent 1 (flash / Implementer)  
**Date**: 2026-11-03  

---

## 1. Requirement Specification (REQ-UI-439)

### 1.1 Problem Statement & Rationale
When opening `CopyTrackingTabDialog`, the source activity profile is preselected by default (`selectedTypes = setOf(sourceActivityType)`). This immediately enables the Copy button and leads to unintended duplicate tabs within the active sport type if confirmed inadvertently. Athletes expect all sport profiles to start unselected so target selection is intentional.

### 1.2 Functional & Architectural Requirements
1. *Unselected Default Selection (`REQ-UI-439.2a`)*: In `CopyTrackingTabDialog.kt`, `selectedTypes` SHALL initialize to `emptySet<ActivityType>()`, ensuring zero sport profiles are preselected upon opening the dialog.
2. *Disabled Copy Action Gating (`REQ-UI-439.2b`)*: The Copy action button (`AppDialogActions.SaveCancel`) SHALL remain disabled (`saveEnabled = false`) while `selectedTypes.isEmpty()`, activating only when at least one profile is checked.
3. *Caller Signature Compatibility (`REQ-UI-439.2c`)*: `CopyTrackingTabDialog` composable signature SHALL preserve `sourceActivityType: ActivityType` to ensure full source and binary compatibility across existing call sites (`TrackingTabsScreen.kt`).

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Amends `REQ-UI-439.2` (*Target ActivityType Selection Dialog*), Parent Epic `ATT-355`. Targets `CopyTrackingTabDialog.kt`.
2. *Historical Origin & Commit Trace*: Sprint 2026-45.1 commit `82bf905d` (ATT-5078) introduced `CopyTrackingTabDialog` with placeholder default `setOf(sourceActivityType)`.
3. *Root Reason for Existing Formulation*: Default preselection was used during initial bottom sheet drafting without considering cross-sport copying ergonomics, leading to accidental tab duplication in the current sport.
4. *Preservation of Core Invariants*: Multi-selection of any `ActivityType` (including the source profile) remains fully supported; deep copy and append semantics (`REQ-UI-439.3`) and 9-language localization (`REQ-UI-439.4`) remain 100% intact.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Unselected Default on Open)**:
  * *Given* an athlete in tab configuration mode tapping the Copy Tab icon,
  * *When* `CopyTrackingTabDialog` opens,
  * *Then* all sport type checkboxes are unchecked by default, and the Copy action button is disabled.
* **Criterion 2 (Selection Enables Copy Button)**:
  * *Given* the dialog open with zero selections,
  * *When* the athlete checks one or more sport profiles,
  * *Then* the Copy action button becomes enabled.

---

## 2. Test Specification (TST-UI-439.2)

### Test Case 1: CopyTrackingTabDialog Unselected Contract (`TST-UI-439.2a`)
* **Scope**: Contract / Unit Test (`TrackingTabConfigHeaderContractTest.kt`)
* **Preconditions**: `CopyTrackingTabDialog.kt` file present in codebase.
* **Action**: Assert `CopyTrackingTabContent` declares `selectedTypes` initialization with `emptySet<ActivityType>()`.
* **Expected Result**: Assertion passes; `selectedTypes` is initialized to `emptySet()`.

### Test Case 2: Clean-Room Regression Suite (`TST-UI-439.2b`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.*"`
* **Expected Result**: 100% pass rate across tracking tabs test suite.

---

## 3. Traceability Matrix

| Test Case | Scope | Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-439.2a` | Contract | `CopyTrackingTabDialog.kt` | `REQ-UI-439.2` | Planned |
| `TST-UI-439.2b` | Regression | Tier 1 Tracking Tabs Unit Tests | `REQ-PRO-001` | Planned |
