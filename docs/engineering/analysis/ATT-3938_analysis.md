# Stage 1 Analysis: `ATT-3938` — Collapse / minimize export status

## 1. Problem Statement & Motivation
Currently, `ExportStatus` in [`ExportStatusGroup.kt`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusGroup.kt) renders:
1. A `HorizontalDivider` (`padding(horizontal = 16.dp, vertical = 8.dp)`).
2. A `titleMedium` section header (`R.string.export_status`, `"Export status"`).
3. For every active export target (`File`, `Dropbox`, `Google Drive`, `Communities`), a bold `groupTitle` plus 1–4 status lines (`waitingLine`, `runningLine`, `succeededLine`, `failedLine`).

When a workout is exported to multiple targets, this section consumes **7 to 10 lines of vertical space** on every workout summary card (`WorkoutSummary.kt`) and detail screen (`TrackOnMapScreen.kt`), even in the 99% common case where every export simply succeeded (`✅`).

## 2. Forensic Root Cause & Gap Analysis
1. **Data Model Classification (`ExportStatusGroupData.kt`)**:
   - `ExportStatusGroupData` holds `hasContent`, `groupTitle`, `waitingLine`, `runningLine`, `succeededLine`, `failedLine`, and `details: List<ExportDetail>`.
   - Currently, `ExportStatusGroupData` lacks classification properties to distinguish between:
     - **Fully Succeeded (`isFullySucceeded`)**: `hasContent && !succeededLine.isNullOrBlank() && waitingLine.isNullOrBlank() && runningLine.isNullOrBlank() && failedLine.isNullOrBlank()`
     - **Needs Attention / In Progress (`needsAttentionOrInProgress`)**: `hasContent && (!waitingLine.isNullOrBlank() || !runningLine.isNullOrBlank() || !failedLine.isNullOrBlank())`
2. **UI Component (`ExportStatusGroup.kt`)**:
   - `ExportStatus` unconditionally renders every `activeExports` item in full vertical layout without `isExpanded` state or smart collapsing of fully succeeded targets.
   - By adding `isExpanded` state (default `false`), a clickable compact header row summarizing succeeded groups (`✅ File, Dropbox, Communities` + trailing `ExpandMore`/`ExpandLess` chevron), automatic inline surfacing of any `needsAttentionOrInProgress` groups (displaying only their `groupTitle` and non-null `waitingLine`, `runningLine`, `failedLine` when collapsed), and `AnimatedVisibility` for the full expanded list of all active `ExportStatusGroup`s, routine workouts shrink to **1 compact line** while active/failed exports remain immediately visible and actionable.

## 3. Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. **Original Requirement ID & Target**:
   - Net-new requirement `REQ-UI-407`, building upon `REQ-UI-149` (`ExportDetailsDialog` bottom sheet contract) and `REQ-UI-285` (`showExportStatus` visibility preference).
   - Targets `ExportStatusGroupData.kt` and `ExportStatusGroup.kt`.
2. **Historical Origin & Commit Trace**:
   - `REQ-UI-149` (`TST-UI-102`, `ModalBottomSheetDialogsIntegrityTest`) established `ExportDetailsDialog` inside `ExportStatusGroup.kt` using `AppModalBottomSheet`.
   - `ATT-2310` (`812bb533`) added `FIT` format support to `ExportStatusDataProvider` and `ExportStatusRepository`.
3. **Root Reason for Existing Formulation**:
   - `ExportStatusGroup.kt` was originally a direct 1:1 port of the legacy XML `LinearLayout` that stacked all four status `TextView`s (`visibility="gone"` when null) under each export group title.
4. **Preservation of Core Invariants**:
   - `ExportDetailsDialog` and `ExportStatusGroup` public composable signatures remain 100% preserved (`ModalBottomSheetDialogsIntegrityTest` compliant).
   - Tapping any displayed group (either a `needsAttentionOrInProgress` group in collapsed mode, or any `ExportStatusGroup` in expanded mode) opens `ExportDetailsDialog` (`AppModalBottomSheet`).
   - Zero raw `Color(0x...)` literals outside `Color.kt` (`ThemeColorCentralizationAuditTest` compliant).

## 4. User Scope Grounding (`ATT-1250`)
- **In Scope**:
  - Adding `isFullySucceeded`, `needsAttentionOrInProgress`, and helper partition/summary logic to `ExportStatusGroupData.kt`.
  - Updating `ExportStatus` and `ExportStatusGroup` in `ExportStatusGroup.kt` to implement Smart Auto-Collapse (`isExpanded = false` by default) with inline `AnimatedVisibility` expansion:
    - Case 1 (All Succeeded): 1-line compact header `Export status: ✅ File, Dropbox, Communities [▾]`.
    - Case 2 (Waiting/Running/Failed present): Compact header summarizes any fully succeeded targets (`✅ File, Dropbox [▾]`), while `needsAttentionOrInProgress` targets are rendered immediately below the header showing `groupTitle` + `waitingLine`/`runningLine`/`failedLine` (omitting `succeededLine` until expanded) and opening `ExportDetailsDialog` on tap.
    - Expanded (`isExpanded = true`): Shows all active `ExportStatusGroup` items in full (including `succeededLine`), each opening `ExportDetailsDialog` on tap.
  - Multi-state `@Preview`s in `ExportStatusGroup.kt` and unit/contract tests (`TST-UI-370`).
- **Out of Scope**:
  - Modifying background export services (`ExportManager`, `DropboxUploader`, `StravaUploader`).
