# Stage 5: Walkthrough & Verification - ATT-8719: EditDeviceDialog Wheel Speed Sensor Mounting Position

**Ticket**: [ATT-8719](https://rainerblind.atlassian.net/browse/ATT-8719) (`2 SP`) | **Parent Epic**: [ATT-166](https://rainerblind.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-17_03_Nacht` | **Branch**: `feature/ATT-8719`  
**Requirement Mapping**: `REQ-SEN-031` (`REQ-SEN-031.1`–`.4`, refining `REQ-SEN-029`, `REQ-UI-149`) | **Test Mapping**: `TST-SEN-031`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-18

---

## 1. Executive Summary & Verification Overview
* Updated `EditDeviceDialog.kt` (`EquipmentSectionContent` / `EquipmentSheetListContent`), `EditDeviceViewModel.kt`, `DeviceData.kt`, `RawDeviceDataProvider.kt`, `DeviceDataRepository.kt`, `WheelPositionSelectionDialog.kt`, and 9-locale `strings_equipment.xml` so `DeviceType.BIKE_SPEED` and `DeviceType.BIKE_SPEED_AND_CADENCE` sensors display per-bike `WheelPosition` (`Front` / `Rear` / `Unspecified`), prompt via reused `WheelPositionSelectionDialog` with conflict/swap previews (`equipment_wheel_position_conflict_swap`), isolate edits in `EditDeviceViewModel` draft memory on `Cancel`, and commit links + positions atomically on `Save` via `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)`.
* Verified zero code drift in Stage 5 (`ATT-8030`), 100% 9-locale parity, `Rule 8.5` Light/Dark PNG visual inspection (`docs/engineering/walkthroughs/img/ATT-8719_wheel_position_edit_device.png`), Tier-1 targeted build & unit/UI/contract test execution (`173/173` passed across `41` classes, `0` failed, `0` errors, `0` skipped), and `verify_requirement_governance.py` (`exit 0`).

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-031.1` | `TST-SEN-031.1`, `.2` | Unit & Compose UI (`RawDeviceDataProvider`, `EditDeviceEquipmentWheelPositionTest`) | **PASSED** (`5/5` in class) | `Verified` |
| `REQ-SEN-031.2` | `TST-SEN-031.1`, `.2` | Draft isolation & conflict preview (`EditDeviceViewModelTest`, `EditDeviceEquipmentWheelPositionTest`) | **PASSED** (`7/7` + `5/5`) | `Verified` |
| `REQ-SEN-031.3` | `TST-SEN-031.1` | Atomic `Save` & `swapConflict = true` (`DeviceDataRepositoryEquipmentSyncTest`) | **PASSED** (`6/6` in class) | `Verified` |
| `REQ-SEN-031.4`, `REQ-PRO-049.1` | `TST-SEN-031.3`, `.4` | Non-wheel isolation, 9-locale `TranslationParityTest` (`10/10`), Light/Dark `@Preview` PNG | **PASSED** (`173/173` Tier-1) | `Verified` |

---

## 3. Automated Test Evidence
* **Tier 1 Scope** (executed independently on Stage 4 SHA `5dc94860` under `parallel_sprint_lock.py`, reused in Stage 5 with zero production/test code edits per `ATT-8030`):
  * `./gradlew :app:assembleDebug :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.*" --tests "com.atrainingtracker.banalservice.devices.EditDeviceDialog*" --tests "com.atrainingtracker.banalservice.ui.devices.devicedata.DeviceDataRepository*" --tests "com.atrainingtracker.trainingtracker.ui.equipment.*" --tests "com.atrainingtracker.trainingtracker.repositories.EquipmentRepository*" --tests "com.atrainingtracker.trainingtracker.database.Equipment*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"` (`BUILD SUCCESSFUL`, `167/167` passed across `39` classes, `0` failed, `0` errors, `0` skipped).
  * `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.DetailedDesignParityContractTest" --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest"` (`BUILD SUCCESSFUL`, `6/6` passed across `2` classes, `0` failed, `0` errors, `0` skipped).
  * **Combined Tier-1 Verified Total**: `173/173` passed (`41` classes), `0` failed, `0` errors, `0` skipped; zero `src/main` file reads in `EditDeviceEquipmentWheelPositionTest.kt` (`REQ-PRO-049.1`).

---

## 4. UI & Visual Verification (`Rule 8.5`)
* **Captured Light/Dark Preview Artifact**: [ATT-8719_wheel_position_edit_device.png](file:///home/rainer/.gemini/antigravity/worktrees/965103cc-f0a5-4db9-a505-8095708188d2/subagent-Lane-C-Worker-Flash--ATT-8719--self-56bf2b6a/docs/engineering/walkthroughs/img/ATT-8719_wheel_position_edit_device.png) (`154,166` bytes).
* Inspected directly via `read_file`: Light and Dark themes render `"Gravel Bike (Front Wheel), Road Bike (Rear Wheel)"` summary text, `"→ Cycling"` mapping, and bike rows (`Gravel Bike` / `Front Wheel` + `F` badge, `Road Bike` / `Rear Wheel` + `R` badge, `Winter Bike` unchecked) with clean contrast and zero clipping/overlap.
* Non-wheel sensors (`HRM`, `BIKE_POWER`, `BIKE_RADAR`, `BIKE_CADENCE`, `RUN_SPEED`) retain plain checkbox UI without `WheelPositionSelectionDialog`.

---

## 5. Invariant & Governance Verification
1. **Core Invariants Preserved**: Strict `SaveCancel` draft isolation (`Cancel` = zero `Equipment.db` mutations), `EquipmentRepository.setWheelSensorLink(..., swapConflict = true)` single-`FRONT`/single-`REAR` guardrail, `equipmentLinksChanged` sync, and `Equipment.db` v4 schema unchanged.
2. **Living Documentation Synchronized**: `REQ-SEN-031` and `TST-SEN-031` `[Verified]` in `docs/requirements/req_tracking_cockpit.md` (`v1.0.13`) and `docs/tests/tst_tracking_cockpit.md` (`v1.0.13`), with `docs/requirements.md` and `docs/tests.md` synced via `--sync-indexes` and `verify_requirement_governance.py` (`exit 0`).
3. **Human Decision Gate**: Parent `ATT-8719` awaits Gate 5 (`--as agent2`) and Stage 6 Cold Review before ending at `Final Review (Human)`.
