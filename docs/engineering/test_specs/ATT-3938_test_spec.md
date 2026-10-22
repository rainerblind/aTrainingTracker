# Stage 2 Requirement & Test Specification: `ATT-3938` — Collapse / minimize export status

## 1. Formal Requirement Specification (`REQ-UI-407`)

### `REQ-UI-407`: Smart Auto-Collapse and Inline Expansion of Export Status in Workout Summary and Details
The system SHALL minimize vertical space consumed by `ExportStatus` (`ExportStatusGroup.kt`, `ExportStatusGroupData.kt`) in Workout Summary cards (`WorkoutSummary.kt`) and Workout Details (`TrackOnMapScreen.kt`) by defaulting to a smart collapsed state (`isExpanded = false`) that summarizes fully succeeded export groups on a single clickable header row while automatically surfacing any waiting, ongoing, or failed export groups inline, and expanding all groups via `AnimatedVisibility` when tapped (ATT-3938, complementing `REQ-UI-149` and `REQ-UI-285`):

1. **Classification & Partitioning in `ExportStatusGroupData` (`REQ-UI-407.1`)**:
   - `ExportStatusGroupData` (`ExportStatusGroupData.kt`) SHALL expose computed classification properties:
     - `val isFullySucceeded: Boolean get() = hasContent && !succeededLine.isNullOrBlank() && waitingLine.isNullOrBlank() && runningLine.isNullOrBlank() && failedLine.isNullOrBlank()`
     - `val needsAttentionOrInProgress: Boolean get() = hasContent && (!waitingLine.isNullOrBlank() || !runningLine.isNullOrBlank() || !failedLine.isNullOrBlank())`
   - `ExportStatusGroupData.kt` SHALL provide pure helper functions:
     - `fun formatCollapsedSucceededSummary(groups: List<ExportStatusGroupData>): String?` returning `"✅ ${succeededTitles.joinToString(", ")}"` when one or more groups satisfy `isFullySucceeded`, or `null` when none do.
     - `fun filterAttentionGroups(groups: List<ExportStatusGroupData>): List<ExportStatusGroupData>` returning `groups.filter { it.needsAttentionOrInProgress }`.
2. **Smart Auto-Collapse & Inline Expansion in `ExportStatus` (`REQ-UI-407.2`)**:
   - `ExportStatus` (`ExportStatusGroup.kt`) SHALL maintain `var isExpanded by remember { mutableStateOf(false) }`.
   - The `ExportStatus` header SHALL be a clickable `Row(modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically)` displaying:
     - `Text(text = stringResource(R.string.export_status), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)`
     - When `!isExpanded && succeededSummary != null`: `Text(text = succeededSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))` (or `Spacer(modifier = Modifier.weight(1f))` when `isExpanded || succeededSummary == null`).
     - A trailing expand/collapse chevron `Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)`.
   - **Minimized State (`!isExpanded`)**:
     - **Case 1 (All Active Export Groups Succeeded)**: Only the single compact header row (`Export status  ✅ File, Dropbox, Communities  [▾]`) is rendered; zero multi-line `ExportStatusGroup` blocks are shown below it.
     - **Case 2 (One or More Groups Waiting, Running, or Failed)**: Any `isFullySucceeded` groups are summarized in the compact header row, while each group in `attentionGroups` (`needsAttentionOrInProgress == true`) is automatically rendered below the header via `ExportStatusGroup(data = statusGroup, showSucceededLine = false)` (displaying its `groupTitle` and non-null `waitingLine`, `runningLine`, and/or `failedLine`) and opens `ExportDetailsDialog` on tap.
   - **Expanded State (`isExpanded`)**:
     - `AnimatedVisibility(visible = isExpanded)` renders all `activeExports` via `ExportStatusGroup(data = statusGroup, showSucceededLine = true)`, displaying all status lines (including `succeededLine`) and opening `ExportDetailsDialog` when any group is tapped.
3. **Preservation of `ExportDetailsDialog` & Public Signatures (`REQ-UI-407.3`)**:
   - `ExportStatus`, `ExportStatusGroup`, and `ExportDetailsDialog` SHALL preserve their public composable contracts (`ModalBottomSheetDialogsIntegrityTest`).

### Acceptance Criteria (Given-When-Then)
- **Scenario 1 (All Export Groups Succeeded — Collapsed 1-Line Summary)**:
  - *Given* `activeExports` containing `File` (`succeededLine = "✅ ..."`), `Dropbox` (`succeededLine = "✅ ..."`), and `Communities` (`succeededLine = "✅ ..."`),
  - *When* `formatCollapsedSucceededSummary(activeExports)` and `filterAttentionGroups(activeExports)` are evaluated,
  - *Then* `formatCollapsedSucceededSummary` returns `"✅ File, Dropbox, Communities"` and `filterAttentionGroups` returns `emptyList()`, rendering a single 1-line header when `isExpanded == false`.
- **Scenario 2 (Mixed Succeeded and Failed/Ongoing Groups — Smart Partial Collapse)**:
  - *Given* `activeExports` where `File` and `Dropbox` are `isFullySucceeded` while `Communities` has `failedLine = "❌ Uploading to Strava failed."`,
  - *When* evaluated in collapsed state (`isExpanded == false`),
  - *Then* `formatCollapsedSucceededSummary` returns `"✅ File, Dropbox"` for the header row, and `filterAttentionGroups` returns `[Communities]` so the failed `Communities` group is immediately displayed below the header and opens `ExportDetailsDialog` on tap.
- **Scenario 3 (Inline Expansion)**:
  - *Given* the user taps the `ExportStatus` header row (`isExpanded` toggles from `false` to `true`),
  - *When* rendered inside `AnimatedVisibility(visible = isExpanded)`,
  - *Then* all `activeExports` are rendered with `showSucceededLine = true`.

---

## 2. Test Specification (`TST-UI-370`)

### `TST-UI-370`: Smart Auto-Collapse & Inline Expansion of Export Status Unit and Contract Verification Suite
1. **Pure Domain Classification & Summary Formatting Unit Tests (`ExportStatusCollapseTest.kt`)**:
   - Verify `isFullySucceeded` and `needsAttentionOrInProgress` across all combinations of `hasContent`, `waitingLine`, `runningLine`, `succeededLine`, and `failedLine`.
   - Verify `formatCollapsedSucceededSummary` returns `"✅ File, Dropbox, Communities"` when all three succeeded, `"✅ File, Dropbox"` when `Communities` has a failure/ongoing job, and `null` when no group is fully succeeded.
   - Verify `filterAttentionGroups` returns `emptyList()` when all groups succeeded, and returns only groups with `waitingLine`, `runningLine`, or `failedLine` when present.
2. **UI Source Contract & Bottom Sheet Integrity Tests (`ExportStatusCollapseTest.kt`, `ModalBottomSheetDialogsIntegrityTest.kt`)**:
   - Verify `ExportStatusGroup.kt` uses `AnimatedVisibility`, `Icons.Default.ExpandMore`, `Icons.Default.ExpandLess`, `formatCollapsedSucceededSummary`, and `filterAttentionGroups`, and preserves `ExportDetailsDialog` (`ModalBottomSheetDialogsIntegrityTest`).

---

## 3. Bidirectional Traceability Matrix

| Requirement ID | Requirement Title | Test Case ID | Test Class / Method |
| :--- | :--- | :--- | :--- |
| `REQ-UI-407.1` | Classification & Partitioning in `ExportStatusGroupData` | `TST-UI-370` | `ExportStatusCollapseTest` |
| `REQ-UI-407.2` | Smart Auto-Collapse & Inline Expansion in `ExportStatus` | `TST-UI-370` | `ExportStatusCollapseTest` |
| `REQ-UI-407.3` | Preservation of `ExportDetailsDialog` & Public Signatures | `TST-UI-370` | `ModalBottomSheetDialogsIntegrityTest` |
