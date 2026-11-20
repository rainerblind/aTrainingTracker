# Stage 3: Implementation Plan - ATT-8422: EditDeviceDialog (Rad-Sensor) Calibration UI Cleanup & WheelCircumferenceHistoryChart Embedding

**Ticket**: `ATT-8422` (`2 SP`, `Verbesserung`, `Priority: Medium`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8422` | **Author**: AI Agent 1 (`Pro` / Neon) | **Date**: 2026-11-20  
**Requirement Mapping**: `REQ-SEN-033` (`REQ-SEN-033.1`–`.3`) | **Test Mapping**: `TST-SEN-033` (`TST-SEN-033.1`–`.3`)

---

## 1. Architecture, Threading & LoC Budget (`SWE.2` / `SWE.3`)
* **Layer Boundaries**: Preserve strict MVVM + Repository separation (`REQ-PRO-050`):
  * UI (`WheelCircumferenceSection.kt`, `EditDeviceDialog.kt`, `WheelCircumferenceHistoryChart.kt`) -> ViewModel (`EditDeviceViewModel.kt`, injectable `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`) -> Repositories (`EquipmentRepository.kt`, `DeviceDataRepository.kt`) -> SQLite (`EquipmentDbHelper.java`, `DevicesDatabaseManager.java` — **0 edits to either `*DbHelper`/`*DatabaseManager`!**).
  * Zero SQLite schema bumps (`Equipment.db` v5 & `Devices.db` unchanged), zero `TrackerService`/`BANALService`/`MyBTLEDevice` edits (`REQ-PRO-114` N/A), zero `BANALServiceRepository.kt` edits (`ATT-8421` boundary respected).
* **Touched Production Files (Downward LoC Ratchet on `EditDeviceDialog.kt`; All Other Files `<< 1,500` LoC)**:
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`: **`1,843 -> ~1,750 / 2,004` LoC** (net **~-93 LoC reduction** by extracting `WheelCircumferenceSection.kt`; `> 250` LoC below ratcheted ceiling).
  * **NEW** `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/WheelCircumferenceSection.kt`: `~165 / 1,500` LoC (cohesive wheel-circumference selector + auto-calibration hint + localized baseline + embedded `WheelCircumferenceHistoryChart`, directly testable via Robolectric `createComposeRule()`).
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModel.kt`: `373 -> ~420 / 1,500` LoC.
  * `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt`: `719 -> ~738 / 1,500` LoC.
  * `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`: `264 -> ~310 / 1,500` LoC.
  * `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelCircumferenceHistoryChart.kt`: `287 -> ~318 / 1,500` LoC.
  * 9-locale XML resources (`values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`): `strings_devices.xml` & `strings_equipment.xml`.
* **Protected Monoliths (`0` LoC Touched)**: `TrackerService.java`, `WorkoutSummariesDatabaseManager.java`, `WorkoutSamplesDatabaseManager.java`, `WorkoutRepository.kt`, `DeviceManager.java`, `DevicesDatabaseManager.java`, `EquipmentDbHelper.java`.

---

## 2. UI Consistency (`docs/design_guidelines.md` §1 & §2)
* **Control Selection (`§1.3`)**: Retains Material 3 `ExposedDropdownMenuBox` + `OutlinedTextField` for the large tire-size preset list (`wheelSizeNames` / `wheelSizeValues`, `> 4` options) — no `FilterChip` or raw `Switch` anti-patterns.
* **Typography, Hierarchy & Spacing**: Section header uses `MaterialTheme.typography.titleSmall` (`FontWeight.Bold`, `onSurfaceVariant`) matching sibling sections in `EditDeviceDialog`; GNSS auto-calibration explanation uses `bodySmall` (`onSurfaceVariant`) directly below the selector; localized baseline value uses `labelSmall` (`onSurfaceVariant`); `Arrangement.spacedBy(8.dp)` between compact controls and `12.dp` spacing before the embedded `WheelCircumferenceHistoryChart` card (`RoundedCornerShape(12.dp)`, `surfaceVariant.copy(alpha = 0.5f)`).
* **No Duplicate Controls & Single-Line Action Safety**: Removes the redundant `OutlinedButton` (`devices_reset_tire_pressure_baseline`) from `WheelCircumferenceSection` so only the single unified baseline reset `Button` inside `WheelCircumferenceHistoryChart` (`equipment_tpms_reset_baseline`) appears.
* **Light/Dark Contrast & Chart Legibility (`§2`, `Rule 8.5`)**: All colors strictly use Material 3 semantic tokens (`primary` for Front, `secondary` for Rear, `tertiary` for Unspecified when non-empty, `outlineVariant` grid lines, `surface` / `onSurfaceVariant` empty-state card). Visual verification captured via Light & Dark `@Preview`-style Robolectric PNG (`docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`).

---

## 3. System Invariants & Preserved Contracts
1. **`RUN_SPEED` Calibration Preserved**: `RunCalibrationFactorSelector` and `CorrectCalibrationDialog` remain 100% intact for `DeviceType.RUN_SPEED` footpods.
2. **`EditEquipmentDialog` Chart Parity**: `WheelCircumferenceHistoryChart` defaults (`emptyStateText = stringResource(R.string.equipment_tpms_no_history)`) and conditional `UNSPECIFIED` legend (`if (unspecifiedRecords.isNotEmpty())`) leave `EditEquipmentDialog` visually and behaviorally unchanged for Front/Rear bike charts.
3. **Draft/Persisted DB Sync & Threading**: All `EquipmentRepository` SQLite reads/writes in `EditDeviceViewModel` execute strictly on injectable `ioDispatcher` (`Dispatchers.IO`); tapping Reset Baseline after editing draft circumference syncs `Devices.db` (`CALIBRATION_FACTOR` + `BASELINE_CIRCUMFERENCE`) and `WheelCircumferenceHistory` (`SOURCE_MANUAL_RESET`) atomically.
4. **Governance & Test Hygiene**: Gate 3 Code Lock (`0` edits under `app/` in Stage 3); 9-locale `TranslationParityTest` PASS; explicit hyphenated `// REQ-SEN-033.x / TST-SEN-033.x` tags in all new tests; `0` `src/main` reads (`REQ-PRO-049.1`); `0` new reflection-only UI tests (`UiReflectionOnlyTestAuditContractTest`).

---

## 4. Atomic Stage 4 Implementation Sequence

### Step 0: Gate 3 Code Lock Verification (`Rule 4`)
* Run `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` (exit `0`) before modifying any file under `app/src/`.

### Step 1: Repository History Query & Synchronized Baseline Reset (`REQ-SEN-033.2` -> `TST-SEN-033.2`)
* **Target 1**: `app/src/main/java/com/atrainingtracker/trainingtracker/repositories/EquipmentRepository.kt`
  * Add `open fun getCircumferenceHistoryForDevice(deviceId: Long): List<WheelCircumferenceRecord>`: resolves linked bike IDs via `equipmentDbHelper.getLinkedEquipmentIdsFromDeviceId(deviceId)`; for each bike resolves `assignedPos = equipmentDbHelper.getWheelPosition(eqId, deviceId) ?: WheelPosition.UNSPECIFIED`, fetches `getCircumferenceHistory(eqId)`, filters to `it.wheelPosition == assignedPos` when `assignedPos == FRONT || assignedPos == REAR` (or includes all bike records when `assignedPos == UNSPECIFIED`), merges, and sorts strictly by `timestampMs ASC` (returns `emptyList()` when unlinked or `deviceId <= 0`).
  * Add `open fun recordManualBaselineResetForDevice(deviceId: Long, circumferenceMm: Int, timestampMs: Long = System.currentTimeMillis()): List<Long>`: for each linked bike (when `deviceId > 0 && circumferenceMm > 0`), resolves `assignedPos` and appends `addCircumferenceHistory(eqId, timestampMs, assignedPos, circumferenceMm, WheelCircumferenceRecord.SOURCE_MANUAL_RESET)`.
* **Target 2**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/devicedata/DeviceDataRepository.kt` (Gate 2 Tip #1)
  * Update `suspend fun resetTirePressureBaseline(deviceId: Long, circumferenceMm: Int? = null)`: when `circumferenceMm != null && circumferenceMm > 0`, persist both `CALIBRATION_FACTOR` and `BASELINE_CIRCUMFERENCE` (`circumferenceMm / 1000.0`) to `DevicesDbHelper.DEVICES` via `devicesDatabaseManager.database.update(...)` and dispatch `sendCalibrationChangedBroadcast(deviceId, circumferenceMeters)`; otherwise delegate to `devicesDatabaseManager.resetBaselineCircumference(deviceId)`; then call `refreshDeviceFromDb(deviceId)`.

### Step 2: Async History State & Synced Baseline Reset in `EditDeviceViewModel` (`REQ-SEN-033.2` -> `TST-SEN-033.2`)
* **Target**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceViewModel.kt`
  * Add injectable secondary/internal constructor or default parameters (`equipmentRepository: EquipmentRepository = EquipmentRepository.getInstance(application)`, `ioDispatcher: CoroutineDispatcher = Dispatchers.IO`) while preserving the standard `(application: Application)` public constructor for `AndroidViewModelFactory`.
  * Expose `wheelCircumferenceHistory: StateFlow<List<WheelCircumferenceRecord>>`.
  * In `loadInitialDeviceData(deviceId)`: when wheel calibration applies (`isWheelSpeedSensor(snapshot.deviceType)` or wheel-capable `BIKE_POWER`), launch on `ioDispatcher` to load `equipmentRepository.getCircumferenceHistoryForDevice(deviceId)` into `_wheelCircumferenceHistory`.
  * In `resetTirePressureBaseline()`: resolve active `circumferenceMm` (`deviceSnapshot.value?.wheelCircumference`), update `_deviceSnapshot` baseline optimistically, launch on `ioDispatcher` to invoke `devicesRepository.resetTirePressureBaseline(id, circumferenceMm)`, append `equipmentRepository.recordManualBaselineResetForDevice(id, circumferenceMm)` when `circumferenceMm != null && circumferenceMm > 0`, and reload `_wheelCircumferenceHistory`.

### Step 3: 9-Locale String Resources (`REQ-SEN-033.1`, `.3`, `REQ-LOC-001`)
* **Targets**: `res/values{,-de,-es,-fr,-it,-ja,-nl,-pl,-pt}/strings_devices.xml` and `strings_equipment.xml`.
  * Add across all 9 locales (zero emojis): `devices_wheel_circumference_auto_hint`, `devices_wheel_baseline_format` (`%1$d`), `devices_wheel_history_empty` (`strings_devices.xml`), and `equipment_tpms_unspecified_legend` (`strings_equipment.xml`).
  * Remove obsolete bike-only keys across all 9 locales (`strings_devices.xml`): `devices_correct_calibration_factor_title_bike`, `devices_correct_calibration_explanation_bike`, `devices_editDeviceBikeEditCalibrationFactorBasedOnDistance`, `devices_reset_tire_pressure_baseline`. Preserve all `RUN_SPEED` keys untouched.

### Step 4: `WheelCircumferenceHistoryChart` Enhancement & `WheelCircumferenceSection.kt` Extraction (`REQ-SEN-033.1`, `.3` -> `TST-SEN-033.1`, `.3`)
* **Target 1**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/equipment/WheelCircumferenceHistoryChart.kt`
  * Add optional parameter `emptyStateText: String = stringResource(R.string.equipment_tpms_no_history)` (100% source-compatible with `EditEquipmentDialog`).
  * Partition `unspecifiedRecords = historyRecords.filter { it.wheelPosition == WheelPosition.UNSPECIFIED }`; show `LegendItem(tertiary, stringResource(R.string.equipment_tpms_unspecified_legend))` and draw `drawSeries(unspecifiedRecords, tertiaryColor)` strictly when `unspecifiedRecords.isNotEmpty()`.
* **Target 2**: Create `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/WheelCircumferenceSection.kt` and wire into `EditDeviceDialog.kt` (`CalibrationSection` at `:542-560`), removing `WheelCircumferenceSelector` (`:564-661`) from `EditDeviceDialog.kt` (shrinking `EditDeviceDialog.kt` below `1,760` LoC) while keeping `RunCalibrationFactorSelector` + `CorrectCalibrationDialog` intact for `RUN_SPEED`.
  * `WheelCircumferenceSection` renders: title (`devices_wheel_circumference`), full-width `ExposedDropdownMenuBox` tire-size preset + mm `OutlinedTextField` (**zero** `Icons.Default.Calculate` button / `CorrectCalibrationDialog`), localized `devices_wheel_circumference_auto_hint`, localized `devices_wheel_baseline_format` when `baselineMm > 0`, and embedded `WheelCircumferenceHistoryChart(historyRecords, onResetBaseline, emptyStateText = stringResource(R.string.devices_wheel_history_empty))`.

### Step 5: Behavioral Robolectric Compose Tests, Rule 8.5 PNG Capture & Locked Incremental Gradle (`TST-SEN-033.1`–`.3`)
* **Test Files** (every new test tagged with explicit hyphenated `// REQ-SEN-033.x / TST-SEN-033.x` comments; `0` `src/main` reads; `0` reflection-only UI tests):
  1. **NEW** `app/src/test/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceWheelCircumferenceTest.kt`: Robolectric `createComposeRule()` suite verifying `TST-SEN-033.1` (no bike calculator, preset dropdown selection, auto-hint, localized baseline, single reset button, `RUN_SPEED` calculator preserved), `TST-SEN-033.2` (`EquipmentRepository.getCircumferenceHistoryForDevice` position filtering, multi-bike `timestampMs ASC` sort, unlinked empty fallback, `recordManualBaselineResetForDevice`, and `EditDeviceViewModel` async `ioDispatcher` draft+baseline sync), and `TST-SEN-033.3` (`UNSPECIFIED` curve & legend, empty state, and **Rule 8.5 Light/Dark PNG capture** to `docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`).
* **Locked Incremental Verification Command (`Rule 7.5` — NEVER `--rerun-tasks`, NEVER `-PstampGitHash=true`)**:
  ```bash
  python3 tools/parallel_sprint_lock.py --timeout 1800 -- ./gradlew :app:testDebugUnitTest --rerun \
    --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceWheelCircumferenceTest" \
    --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceEquipmentWheelPositionTest" \
    --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogWheelGuidanceTest" \
    --tests "com.atrainingtracker.trainingtracker.ui.equipment.WheelCircumferenceHistoryChartTest" \
    --tests "com.atrainingtracker.trainingtracker.database.WheelCircumferenceHistoryDbTest" \
    --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" \
    --tests "*ArchitectureConformanceContractTest" \
    --tests "*DeadCodeEliminationContractTest" \
    --tests "*UiReflectionOnlyTestAuditContractTest" \
    --tests "*SourceTextReadingAuditContractTest" \
    --tests "*SystemLiveTrackingRequirementsContractTest" \
    :app:assembleDebug
  ```
* Visually inspect `docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png` via `read_file` to verify layout, Light/Dark contrast, chart rendering, and zero clipping (`Rule 8.5`).

### Step 6: Living Docs Promotion to `[Implemented]` & Governance Gate
* Promote `REQ-SEN-033` & `TST-SEN-033` to `[Implemented]` with exact test citations, bump versions + Change History rows, run `--sync-indexes`, run all 4 Python safety linters, and verify BOTH `verify_requirement_governance.py --base-ref refs/heads/sprint/2026-11-20_01_Früh` and `--check-verified-evidence` exit `0`.

---

## 5. Requirement-to-Step & Test Traceability Matrix

| Requirement | Plan Step(s) | Target File(s) | Test Case | Test Suite |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-SEN-033.1` | Steps 3, 4 | `WheelCircumferenceSection.kt`, `EditDeviceDialog.kt`, `strings_devices.xml` (9 locales) | `TST-SEN-033.1` | `EditDeviceWheelCircumferenceTest` |
| `REQ-SEN-033.2` | Steps 1, 2 | `EquipmentRepository.kt`, `DeviceDataRepository.kt`, `EditDeviceViewModel.kt` | `TST-SEN-033.2` | `EditDeviceWheelCircumferenceTest`, `WheelCircumferenceHistoryDbTest` |
| `REQ-SEN-033.3` | Steps 3, 4, 5 | `WheelCircumferenceHistoryChart.kt`, `WheelCircumferenceSection.kt`, 9-locale XMLs | `TST-SEN-033.3` | `EditDeviceWheelCircumferenceTest`, `WheelCircumferenceHistoryChartTest`, `TranslationParityTest`, Governance Contracts |
