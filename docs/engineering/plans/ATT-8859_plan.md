# Stage 3: Implementation Plan - ATT-8859: WheelPositionSelectionDialog Front/Rear Only

**Ticket**: [ATT-8859](https://rainerblind.atlassian.net/browse/ATT-8859)  
**Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-19_02_Spät` | **Branch**: `feature/ATT-8859`  
**Requirement Mapping**: `REQ-SEN-032` (refining `REQ-SEN-029.3`, `REQ-SEN-031.2`)  
**Test Mapping**: `TST-SEN-032`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-19  

---

## 1. Problem Description & Background

`WheelPositionSelectionDialog.kt` (`L83-87`) currently displays `Front`, `Rear`, and `Unspecified`. Removing `WheelPosition.UNSPECIFIED` from the interactive dialog prevents ambiguous wheel-sensor links while keeping legacy `UNSPECIFIED` DB/enum fallback and `EditDeviceDialog` display intact.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-SEN-032` (`REQ-SEN-032.1`–`.3`, `[Planned]`; refines `REQ-SEN-029.3`, `REQ-SEN-031.2`)
* **Test Mapping**: `TST-SEN-032` (`TST-SEN-032.1`, `TST-SEN-032.2`, `[Planned]`)
* **SWE.2 / SWE.3 Living Docs**: Unchanged (`0` new subsystems, services, DB schemas, or detailed-design state machines).

---

## 3. System Invariants & Preserved Behavior

1. **Zero Enum/DB/String Regressions**: `WheelPosition.UNSPECIFIED`, `EquipmentDbHelper` v4 `DEFAULT 'UNSPECIFIED'`, `RawDeviceDataProvider` / `EditDeviceViewModel`, and 9-locale `R.string.equipment_wheel_position_unspecified` remain untouched (`0` edits to `EditDeviceDialog.kt`, `EquipmentSensorMatrixScreen.kt`, or `strings*.xml`).
2. **Legacy `UNSPECIFIED` Selection**: When `currentPosition == WheelPosition.UNSPECIFIED`, neither `Front` nor `Rear` radio is selected; `Unlink` (`isLinked == true`) & `Cancel` stay functional.
3. **Contract & Preview Hygiene**: All `@Preview` composables in `WheelPositionSelectionDialog.kt` use `private fun` (`REQ-ARC-003.5`); `0` `src/main/` test reads (`REQ-PRO-049.1`); Parent Human Gate enforced.

---

## 4. Proposed Architectural Changes

### Component 1: `WheelPositionSelectionDialog.kt`
* Narrow `options` (`L83-87`) strictly to `WheelPosition.FRONT` and `WheelPosition.REAR`; update KDoc (`L47`); add `private fun` Light/Dark `@Preview` composables (`ATrainingTrackerTheme`).

### Component 2: `WheelPositionSelectionDialogUiTest.kt` (New Robolectric `createComposeRule()` Test)
* Offline behavioral Compose UI assertions + **Rule 8.5** Light/Dark PNG capture at `docs/engineering/walkthroughs/img/ATT-8859_wheel_position_dialog.png`.

### UI Consistency (Rule 23)
* **Reference / Reused**: Existing `WheelPositionSelectionDialog` Material 3 `AlertDialog` (`RadioButton` rows, `12.dp` horizontal icon-to-text gap, `MaterialTheme.colorScheme.*`, `MaterialTheme.typography.*`) — removes only the 3rd radio row (`UNSPECIFIED`); no new styles or strings (`Localization Parity: N/A`).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Gate 3 Lock Check (Rule 4)
* `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` (`GATE_PASSED` required before editing `app/src/`).

### Step 2: Update `WheelPositionSelectionDialog.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelPositionSelectionDialog.kt`
* Remove `Triple(WheelPosition.UNSPECIFIED, R.string.equipment_wheel_position_unspecified, "wheel_position_unspecified")` (`L86`), update KDoc (`L47`), and add `private fun` Light & Dark `@Preview`s.

### Step 3: Author `WheelPositionSelectionDialogUiTest.kt` & Capture Rule 8.5 PNG
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelPositionSelectionDialogUiTest.kt`
* Verify strictly 2 options (`wheel_position_front`, `wheel_position_rear`), `wheel_position_unspecified` absent, `FRONT`/`REAR` callbacks, `currentPosition == WheelPosition.UNSPECIFIED` neither selected + `Unlink`/`Cancel` + conflict hints, and render/save Light+Dark PNG `docs/engineering/walkthroughs/img/ATT-8859_wheel_position_dialog.png`.

### Step 4: Rule 7.5 Incremental `--rerun` Verification (NO `--rerun-tasks`, NO `-PstampGitHash=true`)
```bash
python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
  --tests "*WheelPositionSelectionDialogUiTest" \
  --tests "*WheelPositionDialogsTest" \
  --tests "*EditDeviceEquipmentWheelPositionTest" \
  --tests "*ArchitectureConformanceContractTest" \
  --tests "*DeadCodeEliminationContractTest" \
  --tests "*SourceTextReadingAuditContractTest"
```

---

## 6. Verification & Rollback Plan

* **Verification**: Inspect `docs/engineering/walkthroughs/img/ATT-8859_wheel_position_dialog.png` via `read_file` (`Rule 8.5`), confirm `100%` pass across all targeted suites, and verify `verify_requirement_governance.py` exits `0`.
* **Rollback**: Isolated on `feature/ATT-8859`; `sprint/2026-11-19_02_Spät` remains untouched until Stage 6 `PASS`.
