# Stage 1 Analysis: ATT-8678 - Reuse Unified RadarLightQuickSwitch Tile Grid in EditDeviceDialog

**Ticket**: [ATT-8678](https://rainerblind.atlassian.net/browse/ATT-8678) (`2 SP`)  
**Sub-task**: Assigned upon Stage 1 submission (`[Analysis]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-11-18_01_Früh` (`49deebfb`)  
**Branch**: `feature/ATT-8678` | **Author**: AI Agent 1 (`agent1`) | **Date**: 2026-11-18

---

## 1. Problem Statement & Motivation
* `RadarLightQuickSwitchSheet.kt` renders a modern Material 3 2-column compact tile grid (`REQ-CON-047.1`–`.3`) using semantic vector icons (`resolveLightVisuals`), `TTColor.RadarLightContainer` badges, `Automatik` active-mode subtitle, and StVZO notice banner.
* `EditDeviceDialog.kt` (`RadarLightControlSectionContent`, `L1376–1594`) still renders legacy `SingleChoiceSegmentedButtonRow` (StVZO) and `10.dp` colored `CircleShape` dot + `RadioButton` rows (non-StVZO), creating visual divergence and blocking shared layout/ordering updates (`ATT-8674`).

---

## 2. Gap Analysis & Independent Peer Review (ATT-3534)
* **Independent Peer Review & Verification**:
  * **Line drift**: Ticket cited `EditDeviceDialog.kt:1113–1298`; on `49deebfb` (`2004` LoC), `shouldShowRadarLightControlSection` sits at `L1317–1326`, `RadarLightControlSection` at `L1336–1368`, `RadarLightControlSectionContent` at `L1376–1594`, and 4 pre-existing `internal` `@Preview`s at `L1596–1662` (tracked under backlog bug `ATT-8823`). Extracting the grid reduces `EditDeviceDialog.kt` LoC cleanly.
  * **Package boundary & `IF-SWE` check**: `EditDeviceDialog.kt` (`banalservice.ui.devices.editdevice`) already imports shared Compose helpers from `trainingtracker.ui.components.core.*` and `trainingtracker.ui.theme.*`. `ArchitectureConformanceContractTest` and `InterfaceSignatureDriftContractTest` (`IF-SWE-01..08`) allow shared stateless UI composables under `trainingtracker.ui.components.radar` (zero DB/Service/ViewModel coupling).
  * **Offline `Automatik` subtitle nuance**: `RadarLightQuickSwitchSheet.kt:169–173` always renders `radar_light_active_mode_format` when `isAutoMode`, whereas `EditDeviceDialog.kt:1483,1527` gates it on `isAutoMode && !isOffline` because offline `effectiveMode` is `preferredMode` (not live hardware state). Parameterizing `showActiveModeSubtitle: Boolean = !isOffline` preserves offline accuracy.
  * **Persistence & `workoutAutoSwitch` / BLE vs ANT+**: `TuningPreferencesDataStore` (`radarLightAutoMode`, `radarLightDefaultMode` by `.name`) and `RadarLightController` govern both `BTLERadarDevice` and `ANTBikeLightDevice` identically; `RadarLightMode.OFF` uses `R.string.radar_light_mode_off` (`Off (Radar Only)`) and `Automatik` uses `R.string.radar_light_control_mode_smart` (`Smart Auto`).
  * **Broken legacy test expectation**: `EditDeviceDialogRadarLightTest.kt:178–181` asserts strict vertical monotonicity (`bounds[i].top < bounds[i + 1].top`), which fails on a 2-column grid where pairs share `.top`; Stage 4 must update it to row-major 2-column assertions (`RadarLightQuickSwitchSheetTest:329–338`).
  * **Contract guards**: Preserve `RadarLightQuickSwitchSheet` / `RadarLightQuickSwitchSheetContent` & `RadarLightButton` reflection signatures (`RadarLightQuickSwitchSheetTest:256`, `RadarLightButtonContractTest:57`), zero new `internal`/`public` `@Preview`s (`DeadCodeEliminationContractTest:215`), and zero `src/main` reads (`REQ-PRO-049.1`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`2 SP`)**: Extract stateless shared `RadarLightModeGrid` + `resolveLightVisuals` into `com.atrainingtracker.trainingtracker.ui.components.radar`; invoke it from both `RadarLightQuickSwitchSheetContent` (keeping Audio Snooze local) and `RadarLightControlSectionContent` (keeping section title + `RadarLightOfflineHint` + `RadarLightController` direct wiring); update/add Light, Dark, and StVZO Compose UI tests.
* **Out-of-Scope**: Swapping `OFF`/`Automatik` order (`ATT-8674`), fixing pre-existing `ATT-8823` `@Preview`s in `EditDeviceDialog.kt`, modifying `EditDeviceViewModel`, or changing `RadarLightMode`/`RadarLightController` persistence.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-CON-047` (`REQ-CON-047.1`–`.3`, `docs/requirements/req_tracking_cockpit_part2.md:1020–1061`, `3013` lines) & `REQ-CON-048` (`L2655–2691`), `TST-CON-047` / `TST-CON-048` (`docs/tests/tst_tracking_cockpit.md:2441,3251`, `3454` lines). Targets `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `EditDeviceDialog.kt`.
* **Historical Origin & Commit Trace**: `ATT-8456` introduced the 2-column tile grid in `RadarLightQuickSwitchSheet.kt` (`REQ-CON-047`); `ATT-8450` enabled offline `EditDeviceDialog` light configuration (`REQ-CON-048`) on the legacy segmented/radio UI (`f55c9fee` sharded docs).
* **Root Reason for Existing Formulation**: `ATT-8456` and `ATT-8450` evolved in parallel on separate UI widgets before consolidating the selector grid.
* **Preservation of Core Invariants**: `shouldShowRadarLightControlSection` (`RVR315` / unsupported hiding), `EditDeviceViewModel` draft isolation (`EditDeviceViewModelTest:241`), offline `RadarLightOfflineHint`, `RadarLightMode_AUTO` / `RadarLightMode_<MODE>` / `RadarLightStvzoNotice` tags, 9-locale `strings_radar.xml` parity (`TranslationParityTest`), and `REQ-CON-047.3` ascending order.

---

## Change Impact Analysis
* **Affected Requirements**: Refine `REQ-CON-047` (`REQ-CON-047.2`/`.3`) & `REQ-CON-048` in `docs/requirements/req_tracking_cockpit_part2.md` (`3013` < `4000` lines); update `TST-CON-047` & `TST-CON-048` in `docs/tests/tst_tracking_cockpit.md` (`3454` < `4000` lines).
* **Affected Architecture Elements**: New shared stateless `RadarLightModeGrid.kt` (`ui/components/radar/`); `RadarLightQuickSwitchSheet.kt`; `RadarLightButton.kt` (`resolveLightVisuals` delegation/import); `EditDeviceDialog.kt` (`RadarLightControlSectionContent` simplified, LoC reduced).
* **Verification Impact & Test Surface**: `EditDeviceDialogRadarLightTest` (update row-major 2-col bounds + add StVZO/Dark/Light parity), `RadarLightQuickSwitchSheetTest`, `RadarLightButtonContractTest`, `EditDeviceViewModelTest`. Zero new `strings_radar.xml` keys (`TranslationParityTest` unchanged); zero `src/main` reads (`REQ-PRO-049.1`); Stage 5 emulator check on `emulator-5554`.
* **Risk Assessment & Invariants**: **MEDIUM** (`2 SP`). Pure stateless UI consolidation with zero DB/protocol/ViewModel state changes, but spans two shared Compose surfaces (`EditDeviceDialog` + `RadarLightQuickSwitchSheet`) and alters `EditDeviceDialog` layout geometry tested by `EditDeviceDialogRadarLightTest`.

---

## 6. Architectural Strategy & System Invariants
1. **Shared Stateless `RadarLightModeGrid` (`ui/components/radar/RadarLightModeGrid.kt`)**: Host `resolveLightVisuals(mode)`, StVZO notice banner (`testTag("RadarLightStvzoNotice")`), `Automatik` tile (`testTag("RadarLightMode_AUTO")`, optional `activeAutoSubtitle` gated by `showActiveModeSubtitle`), and 2-column even/odd chunked `RadarLightModeTile`s (`testTag("RadarLightMode_${mode.name}")`, `Role.RadioButton`, Light/Dark M3 tokens).
2. **Dual Consumers**: `RadarLightQuickSwitchSheetContent` invokes `RadarLightModeGrid(..., showActiveModeSubtitle = true)` above Audio Snooze; `RadarLightControlSectionContent` invokes `RadarLightModeGrid(..., showActiveModeSubtitle = !isOffline)` below its section header and `RadarLightOfflineHint`, wired directly to `RadarLightController.setAutoMode()` / `setManualLightMode(mode)`.
3. **Hygiene**: Keep new `@Preview`s `private` (`DeadCodeEliminationContractTest`), remove orphaned `EditDeviceDialog.kt` legacy rows/imports only if unused elsewhere in file, and retain `resolveLightVisuals` accessibility for `RadarLightButton`.
