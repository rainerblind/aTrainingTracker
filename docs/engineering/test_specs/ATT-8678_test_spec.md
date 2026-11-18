# Stage 2: Requirement & Test Specification - ATT-8678

**DOC-ID**: `DOC-TST-ATT-8678` | **Ticket**: [ATT-8678](https://rainerblind.atlassian.net/browse/ATT-8678) | **Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286)  
**Target Release**: `V5.0.0` | **Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8678` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-CON-047` (refines `.2`/`.3`), `REQ-CON-048` | **Test Spec ID**: `TST-CON-047`, `TST-CON-048`

---

## 1. Requirement Specification (`REQ-CON-047`, `REQ-CON-048` `[Planned]`)
- **Priority**: High (`REQ-CON-048`) / Medium (`REQ-CON-047`) | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `RadarLightModeGrid.kt` (`com.atrainingtracker.trainingtracker.ui.components.radar`), `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `EditDeviceDialog.kt`
- **Localization Parity**: `N/A - no UI strings` (`values*/strings_radar.xml` untouched; existing 9-locale parity preserved).
1. **`REQ-CON-047.2/.3` (Shared Stateless `RadarLightModeGrid` & `resolveLightVisuals`)**:
   - Extract a single shared, stateless composable `RadarLightModeGrid` alongside `resolveLightVisuals` into `com.atrainingtracker.trainingtracker.ui.components.radar`.
   - BOTH `RadarLightQuickSwitchSheetContent` (keeping Audio Snooze local) and `EditDeviceDialog`'s `RadarLightControlSection` / `RadarLightControlSectionContent` SHALL invoke `RadarLightModeGrid` identically (`0` duplicated `10.dp` dot/`RadioButton`/`SingleChoiceSegmentedButtonRow` UI), rendering `RadarLightStvzoNotice`, `RadarLightMode_AUTO`, and 2-column even/odd chunked `RadarLightMode_<MODE>` tiles (`Role.RadioButton`, `TTColor.RadarLightContainer`, Light/Dark M3 tokens) in ascending order (`AUTO -> OFF -> PELOTON -> SOLID -> NIGHT_FLASH -> DAY_FLASH`; StVZO: `AUTO -> OFF -> SOLID`), while preserving `RadarLightQuickSwitchSheet*` and `RadarLightButton` (`resolveLightVisuals` delegation) reflection/IF-SWE signatures.
2. **`REQ-CON-048.1/.2/.3/.4` (`EditDeviceDialog` Offline/Online Parity, Wiring & Preview Hygiene)**:
   - `RadarLightControlSectionContent` SHALL pass `showActiveModeSubtitle = !isOffline` to `RadarLightModeGrid` (suppressing `radar_light_active_mode_format` offline where `effectiveMode == preferredMode`), retain section title, `RadarLightOfflineHint`, `shouldShowRadarLightControlSection` (`RVR315`/unsupported exclusion), direct `RadarLightController.setAutoMode()`/`setManualLightMode(mode)` wiring (without mutating `EditDeviceViewModel` draft state), and Workout Auto-Switch behavior, with zero new `internal`/`public` `@Preview`s (`ATT-8823` pre-existing noted).

### Requirement Archaeology & Chesterton's Fence Audit
- **Original Requirement ID & Target**: `REQ-CON-047` (`.2`/`.3`), `REQ-CON-048` in `docs/requirements/req_tracking_cockpit_part2.md`; targets `RadarLightModeGrid.kt`, `RadarLightQuickSwitchSheet.kt`, `RadarLightButton.kt`, `EditDeviceDialog.kt`.
- **Historical Origin & Commit Trace**: `ATT-8456` introduced the 2-column tile grid in `RadarLightQuickSwitchSheet.kt` (`REQ-CON-047`); `ATT-8450` enabled offline `EditDeviceDialog` config (`REQ-CON-048`) on legacy segmented/radio UI (`f55c9fee`).
- **Root Reason for Existing Formulation**: `ATT-8456` and `ATT-8450` evolved in parallel on separate UI widgets before consolidating onto `ui/components/radar/`.
- **Preservation of Core Invariants**: `shouldShowRadarLightControlSection` (`RVR315`/unsupported hiding), `EditDeviceViewModel` draft isolation, `RadarLightController` direct wiring & Workout Auto-Switch, `showActiveModeSubtitle = !isOffline`, `RadarLightQuickSwitchSheet*`/`RadarLightButton` signatures, ascending order, and 9-locale `strings_radar.xml` parity remain intact.

## 2. Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `RadarLightQuickSwitchSheetContent` or `RadarLightControlSectionContent` (`isOffline = false` or `true`), *When* rendered in Light/Dark & StVZO/non-StVZO, *Then* both invoke `RadarLightModeGrid` with row-major 2-col bounds (`[AUTO, OFF]`, `[PELOTON, SOLID]`, `[NIGHT_FLASH, DAY_FLASH]`; StVZO: `RadarLightStvzoNotice` + full-width `AUTO` + `[OFF, SOLID]`) and `showActiveModeSubtitle = !isOffline` (`EditDeviceDialog`) vs `true` (sheet).
- *Given* `EditDeviceDialog` offline `RearVue8` vs `RVR315`/unsupported, *When* tapping `SOLID`/`AUTO`, *Then* `RadarLightController.setManualLightMode(SOLID)`/`setAutoMode()` fires without mutating `EditDeviceViewModel` drafts; `RVR315` and connected `!isLightControlAvailable` remain hidden.
- **Invariants**: `0` duplicated mode-grid UI; `showActiveModeSubtitle = !isOffline`; Workout Auto-Switch preserved; `0` new `internal`/`public` `@Preview`s; `0` new `src/main` reads (`REQ-PRO-049.1`); `Localization Parity: N/A - no UI strings`.

## 3. Test Specification (`TST-CON-047`, `TST-CON-048` `[Planned]`)
- **`TST-CON-047.1` / `TST-CON-048.1` (`RadarLightQuickSwitchSheetTest`, `EditDeviceDialogRadarLightTest`, Robolectric `createComposeRule()`, `0` new `src/main` reads per `REQ-PRO-049.1`)**:
  - Update `EditDeviceDialogRadarLightTest` (`L178-181`) from strict vertical monotonicity to row-major 2-column grid geometry (`bAuto.top == bOff.top`, `bAuto.left < bOff.left`, `bPeloton.top > bAuto.top`, etc.), testing Light/Dark, StVZO/non-StVZO (`RadarLightStvzoNotice`), online/offline (`showActiveModeSubtitle = !isOffline`, `RadarLightOfflineHint`), `RVR315` hiding, and selection callbacks.
  - Assert `RadarLightQuickSwitchSheetTest` 2-col parity, zero viewport clipping, and `RadarLightStvzoNotice` / `RadarLightMode_*` tags.
- **`TST-CON-047.2` / `TST-CON-048.2` (Controller, ViewModel & Contract Guards)**:
  - Verify `RadarLightControllerTest` (hydration, StVZO reset, Workout Auto-Switch), `EditDeviceViewModelTest` (draft isolation), `RadarLightButtonContractTest`, `RadarLightQuickSwitchSheetTest:256`, `ArchitectureConformanceContractTest`, `InterfaceSignatureDriftContractTest` (`IF-SWE-01..08`), and `DeadCodeEliminationContractTest` (zero new `internal`/`public` `@Preview`s; noting pre-existing `ATT-8823`).
- **`TST-CON-047.4` / `TST-CON-048.3` (`Rule 8.5` Visual Verification)**:
  - Capture Light/Dark `@Preview` PNG `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png` covering `RadarLightQuickSwitchSheetContent` & `RadarLightControlSectionContent` (StVZO/non-StVZO, online/offline) and inspect via `read_file`.

## 4. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-CON-047.1` / `TST-CON-048.1` | Compose UI (`createComposeRule()`, `0` `src/main` reads) | Shared `RadarLightModeGrid` row-major 2-col bounds, Light/Dark, StVZO, online/offline `showActiveModeSubtitle` | `REQ-CON-047.2/.3`, `REQ-CON-048.1/.3` | Planned |
| `TST-CON-047.2` / `TST-CON-048.2` | Unit & Contract Guards | `RadarLightControllerTest`, `EditDeviceViewModelTest`, `RadarLightButtonContractTest`, `ArchitectureConformance*`, `InterfaceSignatureDrift*`, `DeadCodeElimination*` (`ATT-8823`) | `REQ-CON-047.2/.3/.4`, `REQ-CON-048.2/.4` | Planned |
| `TST-CON-047.4` / `TST-CON-048.3` | `Rule 8.5` Visual Audit | Light/Dark `@Preview` PNG `docs/engineering/walkthroughs/img/ATT-8678_radar_light_grid_preview.png` | `REQ-CON-047.2`, `REQ-CON-048.4` | Planned |
