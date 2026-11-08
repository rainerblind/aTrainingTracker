# Stage 2: Requirement & Test Specification - ATT-5854: StravaSettingsDialog Polish

**Ticket**: [ATT-5854](https://atrainingtracker.atlassian.net/browse/ATT-5854)  
**Parent Epic**: [ATT-597](https://atrainingtracker.atlassian.net/browse/ATT-597) (*Strava Support*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5854`  
**Requirement Mapping**: `REQ-UI-488` (*Strava Settings Dialog Close Button Elimination & Sync Card Action Icons*)  
**Test Spec ID**: `TST-UI-488` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-488)

### 1.1 Problem Statement & Rationale
During UI inspection of the Strava synchronization sheet (`StravaSettingsDialog.kt`, `sync_01_strava.png`), two defects were identified:
1. Redundant header close button: The top-right `✕` was displayed alongside bottom `Abbrechen` and `Speichern` buttons (resolved via `REQ-UI-473` close button inheritance).
2. Static manual sync cards: Outlined cards for Equipment, Routes, and Segments rendered simple vertical text columns without trailing sync icons or progress indicators, giving interactive triggers a static read-only appearance.

### 1.2 Functional & Architectural Requirements
1. **Close Button Elimination (`REQ-UI-488.1`)**: In `StravaSettingsDialog.kt`, `AppBottomSheetContent` SHALL automatically suppress the top-right `✕` close button because `actions` (`SaveCancel`) is provided, keeping the bottom action row as the sole dismissal/confirmation mechanism.
2. **Interactive Manual Sync Card Feedback (`REQ-UI-488.2`)**: The three manual sync `OutlinedCard`s (*Ausrüstung aktualisieren*, *Routen aktualisieren*, *Segmente aktualisieren*) SHALL format their content using a horizontal `Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp))`. The left child SHALL be a `Column(modifier = Modifier.weight(1f))` holding the title and formatted timestamp subtitle. The right child SHALL be a trailing feedback container rendering `CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)` while actively syncing (`isSyncing == true`), or `Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)` while idle.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Header Close Button Suppression)**:
  * *Given* `StravaSettingsDialog` open with bottom `Abbrechen` and `Speichern` buttons,
  * *When* inspecting the header row,
  * *Then* zero `✕` close buttons are rendered.
* **Criterion 2 (Idle Manual Sync Card Refresh Affordance)**:
  * *Given* an idle Strava sync card (*Equipment*, *Routes*, or *Segments*),
  * *When* rendered in `StravaSettingsDialog`,
  * *Then* it displays `Icons.Default.Refresh` as a trailing action icon on the right.
* **Criterion 3 (Active Sync Progress Feedback)**:
  * *Given* an active synchronization operation in progress,
  * *When* rendered in `StravaSettingsDialog`,
  * *Then* the corresponding card displays a 20.dp `CircularProgressIndicator` instead of the refresh icon.

### 1.4 System Invariants & Chesterton's Fence
* Strava OAuth 2.0 flow, token storage (`SP_STRAVA_TOKEN`), and data purge mechanics remain unaltered.
* Worker scheduling (`StravaEquipmentSyncWorker`, `StravaRoutesSyncWorker`, `StravaSegmentsSyncWorker`) and manual sync threads remain strictly intact.
* 100% 9-language translation parity maintained; 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-488)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-488.1]` | Contract | `StravaSettingsDialogContractTest`: `StravaSettingsDialog` supplies `actions` without `showCloseButton=true` override | `REQ-UI-488.1` | Specified |
| `[TST-UI-488.2]` | Contract | `StravaSettingsDialogContractTest`: Sync cards wrap text in `weight(1f)` Column and display trailing `Refresh` icon when idle | `REQ-UI-488.2` | Specified |
| `[TST-UI-488.3]` | Contract | `StravaSettingsDialogContractTest`: Sync cards display `CircularProgressIndicator` (size 20.dp) when `isSyncing == true` | `REQ-UI-488.2` | Specified |
| `[TST-UI-488.4]` | Regression | Targeted test suite (`com.atrainingtracker.trainingtracker.ui.settings.strava.*`, `com.atrainingtracker.trainingtracker.ui.components.*`, `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest`, `com.atrainingtracker.trainingtracker.build.*`) | `REQ-UI-488.1`..`2` | Specified |
