# Stage 2: Requirement & Test Specification - ATT-8422: EditDeviceDialog (Rad-Sensor) Calibration UI Cleanup & WheelCircumferenceHistoryChart Embedding

**Ticket**: `ATT-8422` (`2 SP`, `Verbesserung`, `Priority: Medium`) | **Parent Epic**: `ATT-166` | **Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-20_01_Früh` (`465`) | **Branch**: `feature/ATT-8422` (base `1795c82e`)  
**Requirement Mapping**: `REQ-SEN-033` (`REQ-SEN-033.1`–`.3`, refining `REQ-SEN-030`, `REQ-TRK-050`, `REQ-SEN-031`, `REQ-UI-149`, `REQ-LOC-001`)  
**Test Spec ID**: `TST-SEN-033` (`TST-SEN-033.1`–`.3`) | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-20

---

## 1. Requirement Specification (`REQ-SEN-033`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit
- **System Requirement(s)**: `SYSREQ-TRK-001`, `SYSREQ-TRK-010`
- **Implementation**: `EditDeviceDialog.kt`, `WheelCircumferenceSection.kt`, `EditDeviceViewModel.kt`, `EquipmentRepository.kt`, `WheelCircumferenceHistoryChart.kt`, `res/values*/strings_devices.xml`, `res/values*/strings_equipment.xml`

### 1.2 Functional & Architectural Requirements
1. **Bike-Sensor Known-Distance Calculator Removal, Initial Tire-Size Preset & Auto-Calibration Hint (`REQ-SEN-033.1`)**:
   - In `EditDeviceDialog` (`WheelCircumferenceSection` extracted from `EditDeviceDialog.kt` so `EditDeviceDialog.kt` shrinks below `1,843 / 2,004` LoC), for `DeviceType.BIKE_SPEED`, `DeviceType.BIKE_SPEED_AND_CADENCE`, and wheel-capable `DeviceType.BIKE_POWER`:
     (a) Remove the manual known-distance calculator `IconButton` (`Icons.Default.Calculate`) and `CorrectCalibrationDialog` invocation completely.
     (b) **Preserve `CorrectCalibrationDialog` and `RunCalibrationFactorSelector`** untouched for `DeviceType.RUN_SPEED` footpods.
     (c) Retain the tire-size preset dropdown (`ExposedDropdownMenuBox` with `wheelSizeNames` / `wheelSizeValues`, e.g. `700 x 28`) and numeric mm `OutlinedTextField` so newly added sensors start with plausible circumference values.
     (d) Display a concise localized explanation (`devices_wheel_circumference_auto_hint`) stating that this setting provides the initial tire size and that circumference is automatically calibrated via GNSS during rides.
     (e) Replace the hardcoded English `"Baseline: $baselineMm mm"` label with localized `devices_wheel_baseline_format` (`"Baseline: %1$d mm"`).
2. **Per-Sensor Circumference History Repository Query, Chronological Ordering, Unlinked Fallback & Async Baseline Sync (`REQ-SEN-033.2`)**:
   - `EquipmentRepository` **SHALL** expose:
     (a) `getCircumferenceHistoryForDevice(deviceId: Long): List<WheelCircumferenceRecord>`: queries linked bike IDs via `EquipmentDbHelper.getLinkedEquipmentIdsFromDeviceId(deviceId)`, resolves each link's assigned `WheelPosition`, filters each bike's `WheelCircumferenceHistory` to that sensor's linked `WheelPosition` (or includes all records for that bike when `assignedPosition == WheelPosition.UNSPECIFIED`), merges, and sorts chronologically by `timestampMs ASC` so Canvas path drawing never zigzags backward in time; returns `emptyList()` gracefully when `0` bikes are linked.
     (b) `recordManualBaselineResetForDevice(deviceId: Long, circumferenceMm: Int, timestampMs: Long = System.currentTimeMillis()): List<Long>`: appends `SOURCE_MANUAL_RESET` entries to `WheelCircumferenceHistory` for each linked `(equipmentId, assignedWheelPosition)` when `circumferenceMm > 0` (no-op returning `emptyList()` when `0` bikes are linked).
   - `EditDeviceViewModel` **SHALL** inject `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` (never blocking the main thread for `EquipmentRepository` SQLite reads/writes), expose `wheelCircumferenceHistory: StateFlow<List<WheelCircumferenceRecord>>`, load history asynchronously in `loadInitialDeviceData(deviceId)` for wheel-capable sensors, and in `resetTirePressureBaseline()` keep `Devices.db` and `EquipmentRepository` synchronized with the active draft/persisted circumference (persisting an edited initial circumference cleanly before/with resetting baseline in `Devices.db`, recording `SOURCE_MANUAL_RESET` in `EquipmentRepository`, and reloading `wheelCircumferenceHistory`). Zero direct `*DbHelper` imports in UI/ViewModel (`REQ-PRO-050`); zero SQLite schema bumps.
3. **Embedded `WheelCircumferenceHistoryChart` with `UNSPECIFIED` Fallback, Sensor Empty State, Unified Baseline Reset & 9-Locale Parity (`REQ-SEN-033.3`)**:
   - `WheelCircumferenceHistoryChart` **SHALL** support:
     (a) Rendering `WheelPosition.UNSPECIFIED` records with `MaterialTheme.colorScheme.tertiary` AND showing its localized legend (`equipment_tpms_unspecified_legend`) **strictly when `unspecifiedRecords.isNotEmpty()`** (keeping `EditEquipmentDialog` 100% visually unchanged when only `FRONT`/`REAR` records exist).
     (b) An optional `emptyStateText: String = stringResource(R.string.equipment_tpms_no_history)` parameter so `EditDeviceDialog` displays sensor-specific `devices_wheel_history_empty` while `EditEquipmentDialog` remains unchanged.
   - `WheelCircumferenceSection` in `EditDeviceDialog` **SHALL** embed `WheelCircumferenceHistoryChart` directly beneath the initial tire-size selector and consolidate onto its single unified baseline reset button (removing the redundant standalone `OutlinedButton` and obsolete bike-only strings across all 9 locales).

### 1.3 Acceptance Criteria (Given-When-Then)
* **AC-1 (`REQ-SEN-033.1`)**: *Given* a wheel-capable bike sensor (`BIKE_SPEED`, `BIKE_SPEED_AND_CADENCE`, or wheel-capable `BIKE_POWER`) opened in `EditDeviceDialog`, *When* `WheelCircumferenceSection` renders, *Then* zero calculator icon / `CorrectCalibrationDialog` trigger exists, the tire-size preset dropdown (`700 x 28` etc.) and mm text field remain interactive, the localized GNSS auto-calibration hint and localized baseline label render, and opening a `RUN_SPEED` footpod still renders `RunCalibrationFactorSelector` with its calculator button intact.
* **AC-2 (`REQ-SEN-033.2`)**: *Given* a wheel sensor linked to one or more bikes with `FRONT`/`REAR`/`UNSPECIFIED` records across timestamps (or an unlinked sensor with `0` bikes), *When* `EquipmentRepository.getCircumferenceHistoryForDevice()` and `EditDeviceViewModel` load history and invoke `resetTirePressureBaseline()` (including after editing the draft tire circumference), *Then* records are scoped per linked bike/position and strictly sorted by `timestampMs ASC` (empty list for unlinked sensors), SQLite I/O runs on `ioDispatcher`, and resetting baseline synchronizes `Devices.db` + appends `SOURCE_MANUAL_RESET` + refreshes `wheelCircumferenceHistory`.
* **AC-3 (`REQ-SEN-033.3`)**: *Given* populated (`FRONT`, `REAR`, and `UNSPECIFIED`) and empty histories rendered in `WheelCircumferenceSection` / `WheelCircumferenceHistoryChart` in both Light and Dark themes, *When* verified via Robolectric Compose UI tests and **Rule 8.5 PNG screenshot capture** (`docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`), *Then* exactly ONE baseline reset button renders, `UNSPECIFIED` curves & legend render cleanly without blank Canvas, empty state displays `devices_wheel_history_empty`, `EditEquipmentDialog` Front/Rear parity is preserved, and `TranslationParityTest` passes across all 9 locales (`values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### 1.4 9-Language Localization Parity Table (`REQ-LOC-001`)
* **Added keys (9 locales — `values` EN + `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`; zero emojis)**:

| String Key (File) | EN (`values`) | DE (`values-de`) | ES (`values-es`) | FR (`values-fr`) | IT (`values-it`) | JA (`values-ja`) | NL (`values-nl`) | PL (`values-pl`) | PT (`values-pt`) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `devices_wheel_circumference_auto_hint` (`strings_devices.xml`) | Starting tire size for new sensors — automatically calibrated via GNSS during rides. | Start-Reifengröße für neue Sensoren — wird während der Fahrt automatisch per GNSS kalibriert. | Tamaño inicial de neumático para sensores nuevos — calibrado automáticamente por GNSS durante las salidas. | Taille de pneu initiale pour les nouveaux capteurs — calibrée automatiquement par GNSS pendant les sorties. | Misura iniziale dello pneumatico per nuovi sensori — calibrata automaticamente via GNSS durante le uscite. | 新規センサーの初期タイヤサイズ — 走行中にGNSSで自動校正されます。 | Startbandenmaat voor nieuwe sensoren — wordt tijdens ritten automatisch via GNSS gekalibreerd. | Początkowy rozmiar opony dla nowych czujników — kalibrowany automatycznie przez GNSS podczas jazdy. | Tamanho inicial do pneu para novos sensores — calibrado automaticamente por GNSS durante os treinos. |
| `devices_wheel_baseline_format` (`strings_devices.xml`) | Baseline: %1$d mm | Basiswert: %1$d mm | Referencia: %1$d mm | Base : %1$d mm | Base: %1$d mm | 基準値: %1$d mm | Basiswaarde: %1$d mm | Wartość bazowa: %1$d mm | Base: %1$d mm |
| `devices_wheel_history_empty` (`strings_devices.xml`) | No calibration history yet. Complete a ride with GNSS and this wheel sensor to view circumference trends. | Noch kein Verlauf vorhanden. Absolviere eine Fahrt mit GNSS und diesem Radsensor, um den zeitlichen Verlauf zu sehen. | Aún no hay historial de calibración. Completa una salida con GNSS y este sensor de rueda para ver la evolución. | Aucun historique de calibration. Effectuez une sortie avec GNSS et ce capteur de roue pour voir l\'évolution. | Nessuna cronologia di calibrazione. Completa un\'uscita con GNSS e questo sensore ruota per vedere l\'andamento. | 校正履歴はまだありません。GNSSとこのホイールセンサーで走行すると推移が表示されます。 | Nog geen kalibratiegeschiedenis. Voltooi een rit met GNSS en deze wielsensor om het verloop te bekijken. | Brak historii kalibracji. Ukończ jazdę z GNSS i tym czujnikiem koła, aby zobaczyć przebieg w czasie. | Ainda sem histórico de calibração. Conclua um treino com GNSS e este sensor de roda para ver a evolução. |
| `equipment_tpms_unspecified_legend` (`strings_equipment.xml`) | Wheel sensor | Radsensor | Sensor de rueda | Capteur de roue | Sensore ruota | ホイールセンサー | Wielsensor | Czujnik koła | Sensor de roda |

* **Removed obsolete bike-only keys across ALL 9 locales (`strings_devices.xml`)**: `devices_correct_calibration_factor_title_bike`, `devices_correct_calibration_explanation_bike`, `devices_editDeviceBikeEditCalibrationFactorBasedOnDistance`, and `devices_reset_tire_pressure_baseline` (superseded by unified `equipment_tpms_reset_baseline`). Run-footpod keys (`devices_correct_calibration_factor_title_run`, `devices_correct_calibration_explanation_run`, `edit_calibration_factor`, `devices_measuredDistanceText`, `devices_correctDistanceText`) are strictly preserved.

### 1.5 Requirement Archaeology & Chesterton's Fence Audit (`REQ-PRO-022`)
1. *Original Requirement ID & Target*: Net-new `REQ-SEN-033`, refining `REQ-SEN-030` (`ATT-7753`), `REQ-TRK-050` (`ATT-4880`), `REQ-SEN-031` (`ATT-8719`), and `REQ-UI-149` across `EditDeviceDialog.kt`, `WheelCircumferenceSection.kt`, `EditDeviceViewModel.kt`, `EquipmentRepository.kt`, `WheelCircumferenceHistoryChart.kt`, and 9-locale string XMLs.
2. *Historical Origin & Commit Trace*: `e7cc0f39` (`ATT-4880`), `a5665f9a` (`ATT-7753`), `b08c779e` (`ATT-8719`), `f55c9fee` (`ATT-8206`).
3. *Root Reason for Existing Formulation*: Legacy known-distance calibration UI predated online GNSS auto-calibration (`ATT-131`); `ATT-7753` embedded `WheelCircumferenceHistoryChart` only in `EditEquipmentDialog`.
4. *Preservation of Core Invariants*: Initial tire-size presets & manual mm entry preserved; `RunCalibrationFactorSelector` + `CorrectCalibrationDialog` preserved for `RUN_SPEED`; `EditEquipmentDialog` chart behavior unchanged; `EditDeviceDialog.kt < 1,843 / 2,004` LoC; `0` direct `*DbHelper` imports in UI/ViewModel (`REQ-PRO-050`); `0` SQLite schema migrations; `0` new `src/main` reads (`REQ-PRO-049.1`); `0` new reflection-only UI tests (`UiReflectionOnlyTestAuditContractTest`).

---

## 2. Test Specification (`TST-SEN-033`)

* **`TST-SEN-033.1` (Bike-Sensor Calibration UI Cleanup & `RUN_SPEED` Preservation — `EditDeviceWheelCircumferenceTest.kt`)**:
  - Strictly offline Robolectric `createComposeRule()` behavioral UI test (zero `src/main` reads, zero reflection-only UI tests): render `WheelCircumferenceSection` for `BIKE_SPEED` / `BIKE_SPEED_AND_CADENCE` / wheel-capable `BIKE_POWER`, assert zero calculator button (`edit_calibration_factor` contentDescription absent), tire-size preset dropdown selection updates circumference callback, localized auto-calibration hint and localized baseline label (`devices_wheel_baseline_format`) render, and exactly ONE baseline reset button (`wheel_circumference_reset_baseline_button`) exists.
  - Render `RunCalibrationFactorSelector` for `RUN_SPEED` and assert calculator `IconButton` remains present and opens `CorrectCalibrationDialog`.
* **`TST-SEN-033.2` (Per-Sensor History Query, Chronological Sort, Unlinked Fallback & Async Baseline Sync — `EditDeviceWheelCircumferenceTest.kt`, `WheelCircumferenceHistoryDbTest.kt`)**:
  - Against real Robolectric SQLite `EquipmentDbHelper` + `EquipmentRepository`: verify `getCircumferenceHistoryForDevice(deviceId)` scopes records to each linked bike's assigned `WheelPosition` (excluding other sensor's opposite wheel on the same bike), merges across multiple linked bikes sorted strictly by `timestampMs ASC`, includes all records when link position is `UNSPECIFIED`, and returns `emptyList()` for unlinked sensors (`0` bikes).
  - Verify `recordManualBaselineResetForDevice()` persists `SOURCE_MANUAL_RESET` per linked `(bike, position)` with active circumference mm and returns `emptyList()` for unlinked sensors.
  - Verify `EditDeviceViewModel` loads `wheelCircumferenceHistory` on injected `ioDispatcher` and `resetTirePressureBaseline()` synchronizes edited draft circumference to `Devices.db` + `EquipmentRepository` and refreshes `wheelCircumferenceHistory`.
* **`TST-SEN-033.3` (`WheelCircumferenceHistoryChart` `UNSPECIFIED` & Empty State, Rule 8.5 PNG Visual Audit, 9-Locale Parity & Contracts — `EditDeviceWheelCircumferenceTest.kt`, `TranslationParityTest`, Contracts)**:
  - Render `WheelCircumferenceHistoryChart` / `WheelCircumferenceSection` with (a) `FRONT` + `REAR` records (verifying `equipment_tpms_unspecified_legend` is NOT shown), (b) `UNSPECIFIED` records (verifying `equipment_tpms_unspecified_legend` IS shown and non-empty chart renders), and (c) empty records (verifying sensor-specific `devices_wheel_history_empty` renders).
  - Capture Light & Dark Mode PNG (`docs/engineering/walkthroughs/img/ATT-8422_wheel_circumference_edit_device.png`) via `captureToImage()` (`Rule 8.5`) and inspect visually for layout polish, chart legibility, and zero clipping/duplication.
  - Run `TranslationParityTest` (all 9 locales), `WheelCircumferenceHistoryChartTest`, `EditDeviceEquipmentWheelPositionTest`, `ArchitectureConformanceContractTest` (`EditDeviceDialog.kt < 1,843`), `DeadCodeEliminationContractTest`, `UiReflectionOnlyTestAuditContractTest`, `SourceTextReadingAuditContractTest`, and `SystemLiveTrackingRequirementsContractTest`.

---

## 3. Traceability Matrix

| Test Case | Scope | Test Suite(s) | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-SEN-033.1` | Bike calculator removal, tire-size preset + auto-hint, localized baseline, `RUN_SPEED` preservation | `EditDeviceWheelCircumferenceTest` | `REQ-SEN-033.1` | Planned |
| `TST-SEN-033.2` | `EquipmentRepository` per-sensor sorted history, unlinked fallback & async `EditDeviceViewModel` baseline sync | `EditDeviceWheelCircumferenceTest`, `WheelCircumferenceHistoryDbTest` | `REQ-SEN-033.2` | Planned |
| `TST-SEN-033.3` | `UNSPECIFIED` series/legend, sensor empty state, Rule 8.5 Light/Dark PNG, 9-locale parity & contracts | `EditDeviceWheelCircumferenceTest`, `WheelCircumferenceHistoryChartTest`, `EditDeviceEquipmentWheelPositionTest`, `TranslationParityTest`, Governance Contracts | `REQ-SEN-033.3` | Planned |
