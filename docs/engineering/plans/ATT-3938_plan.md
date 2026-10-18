# Stage 3 Implementation Plan: `ATT-3938` — Collapse / minimize export status

## 1. Architectural Overview & SWE.2 Layering
- **Domain / UI Model Layer (`ExportStatusGroupData.kt`)**:
  - Add computed classification properties to `ExportStatusGroupData`:
    - `val isFullySucceeded: Boolean get() = hasContent && !succeededLine.isNullOrBlank() && waitingLine.isNullOrBlank() && runningLine.isNullOrBlank() && failedLine.isNullOrBlank()`
    - `val needsAttentionOrInProgress: Boolean get() = hasContent && (!waitingLine.isNullOrBlank() || !runningLine.isNullOrBlank() || !failedLine.isNullOrBlank())`
  - Add pure helper functions in `ExportStatusGroupData.kt`:
    - `fun formatCollapsedSucceededSummary(groups: List<ExportStatusGroupData>): String?`
    - `fun filterAttentionGroups(groups: List<ExportStatusGroupData>): List<ExportStatusGroupData>`
- **Compose Presentation Layer (`ExportStatusGroup.kt`)**:
  - Update `ExportStatus(exportStatuses: List<ExportStatusGroupData>, modifier: Modifier = Modifier)`:
    - Maintain `var isExpanded by remember { mutableStateOf(false) }`.
    - Compute `val succeededSummary = remember(activeExports) { formatCollapsedSucceededSummary(activeExports) }` and `val attentionGroups = remember(activeExports) { filterAttentionGroups(activeExports) }`.
    - Render a clickable compact header `Row` displaying:
      - `Text(text = stringResource(R.string.export_status), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)`
      - When `!isExpanded && succeededSummary != null`: `Text(text = succeededSummary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))` (else `Spacer(modifier = Modifier.weight(1f))`).
      - Trailing `Icon(imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)`.
    - When `!isExpanded` and `attentionGroups.isNotEmpty()`:
      - Render each group in `attentionGroups` via `ExportStatusGroup(data = statusGroup, showSucceededLine = false)` so active uploads and failures are immediately visible and clickable to open `ExportDetailsDialog`.
    - Inside `AnimatedVisibility(visible = isExpanded)`:
      - Render all `activeExports` via `ExportStatusGroup(data = statusGroup, showSucceededLine = true)`.
  - Update `ExportStatusGroup(data: ExportStatusGroupData, modifier: Modifier = Modifier, showSucceededLine: Boolean = true)` (preserving backward-compatible default `showSucceededLine = true`) so that `StatusLine(text = data.succeededLine)` is rendered when `showSucceededLine` is `true`.

## 2. Atomic Step-by-Step Implementation Breakdown
1. **Step 1 — Extend `ExportStatusGroupData.kt` (`REQ-UI-407.1`)**:
   - Add `isFullySucceeded`, `needsAttentionOrInProgress`, `formatCollapsedSucceededSummary(...)`, and `filterAttentionGroups(...)` with comprehensive KDoc.
2. **Step 2 — Update `ExportStatusGroup.kt` (`REQ-UI-407.2`, `REQ-UI-407.3`)**:
   - Implement smart auto-collapse (`isExpanded = false` by default), clickable header row with succeeded summary and expand/collapse chevron, inline attention group display when collapsed, and `AnimatedVisibility` full expansion.
   - Add multi-state `@Preview` functions (`PreviewExportStatusAllSucceededCollapsed`, `PreviewExportStatusMixedAttentionCollapsed`).
3. **Step 3 — Unit & Contract Tests (`TST-UI-370`)**:
   - Create `app/src/test/java/com/atrainingtracker/trainingtracker/ui/components/export/ExportStatusCollapseTest.kt` verifying all `REQ-UI-407` clauses and run targeted tests + `ModalBottomSheetDialogsIntegrityTest`.

## 3. Preserved Invariants & Verification Commands
- `ExportDetailsDialog` public composable signature (`ModalBottomSheetDialogsIntegrityTest`) preserved.
- Zero raw hex colors outside `Color.kt` (`ThemeColorCentralizationAuditTest`).
- Targeted Stage 4 test command:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.components.export.ExportStatusCollapseTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.ModalBottomSheetDialogsIntegrityTest"
  ```
