# Stage 3: Implementation Plan - ATT-8823: EditDeviceDialog Private @Preview Audit

**Ticket**: [ATT-8823](https://rainerblind.atlassian.net/browse/ATT-8823)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8823`  
**Requirement Mapping**: `REQ-ARC-003` (`REQ-ARC-003.5`)  
**Test Mapping**: `TST-ARC-003` (`TST-ARC-003.5`)  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19  

---

## 1. Problem Description & Background

`DeadCodeEliminationContractTest.testComposePreviewsVisibilityAudit` fails on four `internal fun RadarLightControlSectionOffline*Preview` composables in `EditDeviceDialog.kt` (live lines `1437`, `1454`, `1471`, `1488`), violating `REQ-ARC-003.5`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-ARC-003` (`REQ-ARC-003.5` *Compose UI Preview Visibility Scoping*, `[Verified]`)
* **Test Mapping**: `TST-ARC-003` (`TST-ARC-003.5` *Top-Level Compose Preview Private Visibility Audit*, `[Verified]`), plus `TST-PRO-129.2`, `REQ-PRO-133`, `REQ-CON-047`
* **SWE.2 / SWE.3 Living Docs**: Unchanged (`0` new subsystems, services, DB schemas, or detailed-design state machines).

---

## 3. System Invariants & Preserved Behavior

1. **Zero LoC Growth**: `EditDeviceDialog.kt` stays at `1,843 <= 2,004` LoC (`0` net LoC delta under `ArchitectureConformanceContractTest.MAX_MONOLITH_FILE_LOC_BASELINE`).
2. **Zero Runtime / UI Behavior Change**: `@Preview` annotations, `@Composable` signatures, and `RadarLightControlSectionContent` remain untouched (`0` external callers).
3. **Zero Localization / Source-Text Impact**: `values*/strings*.xml` and `app/src/test/` source-text rules remain untouched.
4. **Parent Human Gate Invariance**: Parent ticket ends at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `EditDeviceDialog.kt` Preview Visibility Scoping
* **Target File**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`
* **Change**: Narrow visibility from `internal fun` to `private fun` on lines `1437`, `1454`, `1471`, and `1488`.

### UI Consistency (Rule 23)
No UI changes (visibility modifier narrowing only on existing `@Preview` functions).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Lock Check (Rule 4)
* Command: `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`GATE_PASSED` required before editing `app/src/`).

### Step 2: Narrow 4 `@Preview` Functions to `private fun`
* File: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`
* Exact edits (`internal fun` -> `private fun`, `0` net LoC delta):
  1. Line `1437`: `private fun RadarLightControlSectionOfflineMultiModeLightPreview()`
  2. Line `1454`: `private fun RadarLightControlSectionOfflineMultiModeDarkPreview()`
  3. Line `1471`: `private fun RadarLightControlSectionOfflineStvzoLightPreview()`
  4. Line `1488`: `private fun RadarLightControlSectionOfflineStvzoDarkPreview()`

### Step 3: Rule 7.5 Incremental `--rerun` Verification
* Command:
  ```bash
  ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.trainingtracker.build.DeadCodeEliminationContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" \
    --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest" \
    --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogRadarLightTest"
  ```

---

## 6. Verification & Rollback Plan

* **Verification**: Confirm `DeadCodeEliminationContractTest` (`5/5`), `ArchitectureConformanceContractTest`, `SourceTextReadingAuditContractTest`, and `EditDeviceDialogRadarLightTest` pass 100%, with `git diff` strictly scoped to the 4 visibility keywords.
* **Rollback**: Isolated on `feature/ATT-8823`; `sprint/2026-11-19_02_Spät` remains unaffected until Stage 6 `PASS`.
