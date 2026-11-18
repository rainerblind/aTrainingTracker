# Stage 3: Implementation Plan - ATT-8678: Reuse Unified RadarLightModeGrid in EditDeviceDialog

**Ticket**: [ATT-8678](https://rainerblind.atlassian.net/browse/ATT-8678) (`2 SP`) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8678`  
**Requirement Mapping**: `REQ-CON-047` (`.2`, `.3`), `REQ-CON-048` (`.1`–`.4`) | **Test Mapping**: `TST-CON-047`, `TST-CON-048`  
**Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

---

## 1. Problem Description & Architectural Scope
* `RadarLightQuickSwitchSheet.kt` renders the unified Material 3 2-column compact tile grid (`REQ-CON-047.1`–`.3`) inline inside `RadarLightQuickSwitchSheetContent`, while `EditDeviceDialog.kt` (`RadarLightControlSectionContent`, `L1376–1594`) still renders legacy `SingleChoiceSegmentedButtonRow` (StVZO) and `10.dp` `CircleShape` dot + `RadioButton` rows (non-StVZO).
* Consolidate the light-mode selector into one shared, stateless Compose component `RadarLightModeGrid` + `resolveLightVisuals` under `com.atrainingtracker.trainingtracker.ui.components.radar` (`:app` UI component layer, zero DB/Service/ViewModel coupling) consumed by both `RadarLightQuickSwitchSheetContent` and `EditDeviceDialog` (`RadarLightControlSectionContent`), reducing `EditDeviceDialog.kt` LoC (`< 2004`) with zero duplicated selector UI.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-CON-047` (`REQ-CON-047.2`/`.3`), `REQ-CON-048` (`REQ-CON-048.1`–`.4`) in `docs/requirements/req_tracking_cockpit_part2.md`
* **Test Specs**: `TST-CON-047` (`TST-CON-047.1`–`.4`), `TST-CON-048` (`TST-CON-048.1`–`.4`) in `docs/tests/tst_tracking_cockpit.md`
* **SWE.2 / SWE.3 Impact**: Pure stateless UI extraction inside `:app`; no new subsystem, service, DB schema, or `IF-SWE-01..08` signature changes (`docs/architecture.md` & `detailed_design/` unchanged).

---

## 3. System Invariants & UI Consistency (`docs/design_guidelines.md`)
1. **Package Boundary & Signature Stability**: `banalservice.ui` (`EditDeviceDialog.kt`) already imports stateless Compose helpers from `trainingtracker.ui.components.*` and `ui.theme.*`. Placing `RadarLightModeGrid.kt` in `trainingtracker.ui.components.radar` satisfies `ArchitectureConformanceContractTest` and preserves `RadarLightQuickSwitchSheet*` (`RadarLightQuickSwitchSheetTest:256`) and `RadarLightButton` (`RadarLightButtonContractTest:57`) signatures.
2. **Offline vs. Online Subtitle & Controller Isolation**: `EditDeviceDialog` passes `showActiveModeSubtitle = !isOffline` (suppressing `radar_light_active_mode_format` offline where `effectiveMode == preferredMode`), retains `RadarLightOfflineHint`, `shouldShowRadarLightControlSection` (`RVR315`/unsupported exclusion), direct `RadarLightController.setAutoMode()`/`setManualLightMode(mode)` wiring (zero `EditDeviceViewModel` draft mutation), and Workout Auto-Switch / BLE+ANT+ `.name` persistence.
3. **UI Consistency (`docs/design_guidelines.md` §§4, 5.1, 5.2, 5.4, 5.5)**: Reuse existing M3 `Surface` tile tokens (`primaryContainer`/`onPrimaryContainer`/`primary` vs `surfaceContainerLow`/`onSurface`/`outline`), `TTColor.RadarLightContainer`, `TTColor.RadarLightBadgeAuto`, `TTColor.RadarDisconnectedAmber`, `8.dp`/`12.dp` spacing/corner scale, `resolveLightVisuals` vector icons, `Role.RadioButton` merged semantics, `RadarLightMode_AUTO`/`RadarLightMode_<MODE>`/`RadarLightStvzoNotice` tags, and existing 9-locale `strings_radar.xml` keys (`N/A - no UI strings`). All new `@Preview`s MUST be `private` (`DeadCodeEliminationContractTest`; pre-existing `ATT-8823` previews in `EditDeviceDialog.kt` untouched).

---

## 4. Atomic Implementation Steps (Stage 4 Construction)
### Step 1: Extract Shared `RadarLightModeGrid` & `resolveLightVisuals` (`REQ-CON-047.2/.3`)
* **Files**: New `app/src/main/java/com/atrainingtracker/trainingtracker/ui/components/radar/RadarLightModeGrid.kt`; update `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/trackingtabs/RadarLightButton.kt`.
* **Changes**: Move `resolveLightVisuals(RadarLightMode)` into `RadarLightModeGrid.kt` (public/internal to `:app`, imported by `RadarLightButton.kt` without changing `RadarLightButton` signature). Implement stateless `RadarLightModeGrid(currentMode, availableModes, isStvzo, isAutoMode, showActiveModeSubtitle = true, onModeSelected, onAutoSelected, modifier)` hosting the StVZO banner (`testTag("RadarLightStvzoNotice")`), `Automatik` tile (`testTag("RadarLightMode_AUTO")`, subtitle gated by `isAutoMode && showActiveModeSubtitle`), and even/odd 2-column chunked `RadarLightModeTile`s (`testTag("RadarLightMode_${mode.name}")`) in `REQ-CON-047.3` ascending order, plus `private` Light/Dark/StVZO `@Preview`s wrapped in `ATrainingTrackerTheme`.

### Step 2: Refactor `RadarLightQuickSwitchSheet.kt` (`REQ-CON-047.1/.2/.3`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/radar/RadarLightQuickSwitchSheet.kt`.
* **Changes**: Replace inline light-grid block (`L147–249`) and private `LightTileSpec`/`RadarQuickSwitchLightTile` (`L313–402`) inside `RadarLightQuickSwitchSheetContent` with `RadarLightModeGrid(..., showActiveModeSubtitle = true, onModeSelected = onModeSelected, onAutoSelected = onAutoSelected)`. Keep `AppModalBottomSheet`, Audio Snooze (`RadarQuickSwitchSnoozeTile`, `RadarSnooze_*`), and public composable signatures intact; remove unused imports.

### Step 3: Refactor `EditDeviceDialog.kt` (`REQ-CON-048.1`–`.4`)
* **Files**: `app/src/main/java/com/atrainingtracker/banalservice/ui/devices/editdevice/EditDeviceDialog.kt`.
* **Changes**: Inside `RadarLightControlSectionContent` (`L1376–1594`), replace legacy `if (isStvzo) { SingleChoiceSegmentedButtonRow } else { RadioButton dot rows }` (`L1426–1592`) with `RadarLightModeGrid(currentMode = effectiveMode, availableModes = availableModes, isStvzo = isStvzo, isAutoMode = isAutoMode, showActiveModeSubtitle = !isOffline, onModeSelected = onSelectMode, onAutoSelected = onSelectAuto)`. Retain section title, `RadarLightOfflineHint`, `shouldShowRadarLightControlSection`, and direct `RadarLightController` wiring; strip unused imports (`selectable`, etc.) while keeping `CircleShape` (used at `L141`) and adding zero new `internal`/`public` `@Preview`s (`ATT-8823`).

### Step 4: Behavioral UI, Contract Tests & `Rule 8.5` Visual Verification (`TST-CON-047`, `TST-CON-048`)
* **Files**: `EditDeviceDialogRadarLightTest.kt`, `RadarLightQuickSwitchSheetTest.kt`; PNG artifact `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png`.
* **Changes**: Update `EditDeviceDialogRadarLightTest.kt` (`L178–181`) to assert row-major 2-column grid geometry (`[AUTO, OFF]`, `[PELOTON, SOLID]`, `[NIGHT_FLASH, DAY_FLASH]` non-StVZO; full-width `AUTO` row 1 + `[OFF, SOLID]` row 2 + `RadarLightStvzoNotice` StVZO), online/offline `showActiveModeSubtitle = !isOffline`, and Light/Dark parity (`0` new `src/main` reads per `REQ-PRO-049.1`). Extend `RadarLightQuickSwitchSheetTest.kt` (`RadarLightStvzoNotice` + grid parity) and capture/inspect `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png` via `read_file`.
* **Verification**: `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceDialogRadarLightTest" --tests "com.atrainingtracker.banalservice.ui.devices.editdevice.EditDeviceViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.radar.RadarLightQuickSwitchSheetTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.trackingtabs.RadarLightButtonContractTest" --tests "com.atrainingtracker.banalservice.devices.radar.RadarLightControllerTest" --tests "com.atrainingtracker.trainingtracker.build.ArchitectureConformanceContractTest" --tests "com.atrainingtracker.trainingtracker.build.InterfaceSignatureDriftContractTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`

---

## 5. Verification & Rollback Plan
* **Pre-Edit Gate (`Rule 4.1`)**: Execute `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/jira_util.py check-gate <IMPL_PLAN_KEY>` (`GATE_PASSED`) before modifying any `app/` source file.
* **Rollback**: Isolated on `feature/ATT-8678`; revertible cleanly without affecting `sprint/2026-11-18_01_Früh` or `develop`.
