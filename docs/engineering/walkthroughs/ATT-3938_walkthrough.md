# Stage 5 Walkthrough & Verification Report: `ATT-3938` — Collapse / minimize export status

## 1. Summary of Verified Changes
1. **Smart Classification & Summary Helpers (`ExportStatusGroupData.kt`, `REQ-UI-407.1`)**:
   - Added computed properties `isFullySucceeded` and `needsAttentionOrInProgress` on `ExportStatusGroupData`.
   - Added pure functions `formatCollapsedSucceededSummary(groups)` (returning `"✅ File, Dropbox, Communities"` when one or more groups succeeded completely, or `null` otherwise) and `filterAttentionGroups(groups)`.
2. **Smart Auto-Collapse & Inline Expansion (`ExportStatusGroup.kt`, `REQ-UI-407.2`, `REQ-UI-407.3`)**:
   - `ExportStatus` defaults to `isExpanded = false` and renders a single clickable header row (`Export status  ✅ File, Dropbox, Communities  [▾]`).
   - When all active export groups succeeded (`attentionGroups.isEmpty()`), `ExportStatus` consumes only **1 compact line**.
   - When one or more export groups are waiting, running, or failed (`attentionGroups.isNotEmpty()`), those attention groups are automatically displayed immediately below the header (`showSucceededLine = false`) and open `ExportDetailsDialog` on tap.
   - Tapping the header row toggles `isExpanded` via `AnimatedVisibility`, displaying all active `ExportStatusGroup` items in full (`showSucceededLine = true`).

## 2. Verification & Regression Results (`TST-UI-370`)
- **Targeted Unit & Contract Tests**:
  - `ExportStatusCollapseTest` (`classificationProperties_identifyFullySucceededAndAttentionGroups`, `formatCollapsedSucceededSummary_and_filterAttentionGroups_handleAllCases`, `exportStatusGroupKt_containsSmartCollapseAndAnimatedVisibilityContract`): **PASSED**
  - `ModalBottomSheetDialogsIntegrityTest`: **PASSED**
- **Full Clean-Room Unit Test Suite (`./gradlew testDebugUnitTest`)**:
  - Executed across the full project test suite (100% pass rate, zero regressions).
  - Living documentation (`REQ-UI-407` in `docs/requirements.md` and `TST-UI-370` in `docs/tests.md`) updated to `Verified`.
