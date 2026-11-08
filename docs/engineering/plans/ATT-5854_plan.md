# Stage 3: Implementation Plan - ATT-5854: StravaSettingsDialog Polish & Sync Action Affordances

**Ticket**: [ATT-5854](https://atrainingtracker.atlassian.net/browse/ATT-5854) | **Sub-task**: `[Impl-Plan]`  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5854`  
**Requirement Mapping**: `REQ-UI-488` (`REQ-UI-488.1`..`2`) | **Test Mapping**: `TST-UI-488` (`TST-UI-488.1`..`4`)  
**Author**: AI Agent 1 (`subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Description & Background
In `StravaSettingsDialog.kt`, two UI polish defects exist: (1) modal bottom sheet rendered redundant top-right `✕` alongside bottom `Abbrechen` and `Speichern` buttons; and (2) manual sync cards for Equipment, Routes, and Segments rendered as static text boxes without trailing sync action icons (`Icons.Default.Refresh`) or active progress spinners (`CircularProgressIndicator`), failing to indicate interactive clickability.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-488` (*Strava Settings Dialog Close Button Elimination & Sync Card Action Icons*)
  - `REQ-UI-488.1`: Dynamic close button suppression when bottom `actions` bar is supplied.
  - `REQ-UI-488.2`: Trailing refresh icon when idle and 20.dp progress spinner when actively syncing across all 3 manual sync cards.
* **Test Mapping**: `TST-UI-488` (`TST-UI-488.1`..`4` in `docs/tests/tst_ui_design_system.md`).

---

## 3. System Invariants & Preserved Behavior
1. **Security & OAuth Invariants**: Strava OAuth 2.0 flow, token storage (`SP_STRAVA_TOKEN`), and data purge mechanics remain completely untouched.
2. **Worker & Concurrency Invariants**: Background synchronization triggers (`StravaEquipmentSynchronizeThread`, `routesRepo.syncRoutesFromStravaAsync()`, `segmentsRepo.syncSegmentsAsync()`), debounce checks (`if (!isSyncing)`), and SharedPreferences listeners remain strictly unaltered.
3. **Bottom Actions Invariant**: `AppDialogActions.SaveCancel` callback semantics and dismiss behavior are 100% preserved.

---

## 4. Proposed Architectural Changes
* `StravaSettingsDialog.kt`:
  - Verify `AppBottomSheetContent` supplies `actions` without explicit `showCloseButton=true`, inheriting automatic suppression from `ATT-5873` (`REQ-UI-488.1`).
  - Introduce `@Composable private fun StravaManualSyncCard(...)` (or refactor inline) using `Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically)`.
  - Left child: `Column(modifier = Modifier.weight(1f))` rendering title (`titleMedium`) and last update / syncing timestamp (`bodySmall`).
  - Right child: Trailing feedback container rendering `CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)` while `isSyncing == true`, or `Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)` while idle (`REQ-UI-488.2`).
* **UI Consistency (Rule 23 / Section 5)**: Standardizes on `OutlinedCard`, `MaterialTheme.colorScheme.primary`, and compact 20.dp progress indicator tokens matching app-wide design guidelines.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Refactor Manual Sync Cards in `StravaSettingsDialog.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialog.kt`
* Action: Update Equipment, Routes, and Segments cards to use `Row` with trailing `Icons.Default.Refresh` and `CircularProgressIndicator` based on reactive `isSyncing` states (`REQ-UI-488.2`).

### Step 2: Implement `StravaSettingsDialogContractTest.kt`
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/strava/StravaSettingsDialogContractTest.kt`
* Action: Assert: (1) `StravaSettingsDialog` does not pass `showCloseButton = true`; (2) card contents bind `Row`, `Icons.Default.Refresh`, and `CircularProgressIndicator` (`TST-UI-488.1`..`3`).

### Step 3: Execute Test Verification Suite
* Commands:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.strava.*"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.core.AppModalBottomSheetTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`
  - `python3 tools/verify_requirement_governance.py`
