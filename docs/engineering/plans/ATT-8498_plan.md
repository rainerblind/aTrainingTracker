# Stage 3: Implementation Plan - ATT-8498 (PO Rejection Redesign)

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) | **Release**: `V5.0.0`  
**Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8498` | **Author**: `--as agent1`  
**Requirements**: `REQ-UI-2783` (`REQ-UI-2783.1`..`.3`) | **Tests**: `TST-UI-2783` (`TST-UI-2783.1`..`.4`)

---

## 1. Traceability, UI Consistency & System Invariants
* **`REQ-UI-2783.1`** -> **`TST-UI-2783.1`**: Zero Zone Display UI/draft/Save/Reset in `AdvancedTuningScreen`, `AdvancedTuningDialog`, `CockpitTypographySection` (`< 400` LOC, `REQ-UI-277.3`); delete `CockpitZoneDisplaySubsection.kt` & `AdvancedTuningZoneDisplayTest.kt`; remove `ATT-8498` screenshot from `AdvancedTuningSectionsScreenshotTest.kt`.
* **`REQ-UI-2783.2`** -> **`TST-UI-2783.2`, `.4`**: `EditSensorFieldDialog` / `EditSensorFieldViewModel` maps `(selectedSensorType, sportType)` 1:1 with `TrackingViewModel.resolveDisplayOptions`, hydrating `ZoneDisplayOptionsDraft` from `SettingsDataStore` and persisting only on Save.
* **`REQ-UI-2783.3`** -> **`TST-UI-2783.3`, `.4`**: `SensorGridScreen` (`CONFIGURATION`) hosts `SingleChoiceSegmentedButtonRow` (`ZoneProfileTarget` pre-selected from `ActivityType`) + `ZoneDisplayOptionsToggleList` + `zone_display_apply_all` with immediate `SettingsDataStore` persistence; `ZonesSettingsActivity` (`HrvDfaA1SettingsContractTest`) & 9-locale strings preserved.
* **UI Consistency (`docs/design_guidelines.md §1, §5.6, §5.8`)**: Use `SingleChoiceSegmentedButtonRow` with `SegmentedButton(..., icon = {}, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp))` and `Text(..., style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)` so German (`de`) labels fit cleanly without clipping or wrapping; use `ZoneDisplayOptionsToggleList` inside `AppModalBottomSheet` (`scrollable = true`, zero nested `verticalScroll`); single-line `OutlinedButton` for `zone_display_apply_all`; Light/Dark `@Preview`s in `ATrainingTrackerTheme`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-2783` (originally `ATT-8498` Expert Settings), refining `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4`, `REQ-PRO-049.1`.
* **Historical Origin & Commit Trace**: `ATT-1390` (`e3966ebf`) added `ZoneDisplayOptions` in `ZonesSettingsActivity`; `ATT-8498` (`81af2506`) placed them in `CockpitTypographySection` (Ceremony 2 PO rejected `2026-11-17T15:59`).
* **Root Reason for Existing Formulation**: `81af2506` co-located zone toggles with Expert Settings typography tuning; PO directed tile long-press & tracking tabs config instead.
* **Preservation of Core Invariants**: Preserves `< 400` LOC `CockpitTypographySection`, `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, zero `src/main` reads, and zone thresholds.

---

## 2. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Remove ZoneDisplayOptions from Expert Settings (`REQ-UI-2783.1`, `TST-UI-2783.1`)
* **Files**: `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `CockpitTypographySection.kt`, `CockpitZoneDisplaySubsection.kt` (delete), `AdvancedTuningZoneDisplayTest.kt` (delete), `AdvancedTuningSectionsScreenshotTest.kt`.
* **Changes**: Revert `zoneDisplayDraft` state/persistence/Reset wiring in `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`; restore `CockpitTypographySection.kt` to pre-`81af2506` inline `ExposedDropdownMenuBox` & clean `PreviewCockpitTile` (`< 400` LOC, passing `CockpitPreviewTileContractTest` & `AdvancedTuningModularityTest`); delete `CockpitZoneDisplaySubsection.kt` & `AdvancedTuningZoneDisplayTest.kt` (clearing `ATT-8631` `src/main` violation); remove `testCaptureCockpitZoneDisplayScreenshot_LightAndDark`.

### Step 2: Shared Mapping Helper & Controls Reuse (`REQ-UI-2783.2`, `.3`)
* **Files**: `ui/settings/ZoneDisplayOptionsControls.kt`, `activities/ZonesSettingsActivity.kt` (unchanged).
* **Changes**: Add pure helpers `resolveZoneProfileTarget(sensorType: SensorType?, sportType: BSportType): ZoneProfileTarget?` (matching `TrackingViewModel.resolveDisplayOptions`: `(HR, RUN)->HR_RUN`, `(HR, BIKE)->HR_BIKE`, `(POWER, BIKE)->PWR_BIKE`, `(HRV_DFA_A1, *)->HRV_DFA_A1`) and `defaultZoneProfileTarget(activityType: ActivityType): ZoneProfileTarget`, plus shared `ZoneDisplayToggleRow` / profile card helper while preserving `ZonesSettingsActivity` (`HrvDfaA1SettingsContractTest`).

### Step 3: Tile Long-Press in `EditSensorFieldViewModel` & `EditSensorFieldDialog` (`REQ-UI-2783.2`, `TST-UI-2783.2`)
* **Files**: `EditSensorFieldViewModel.kt`, `EditSensorFieldDialog.kt`.
* **Changes**: Hydrate `zoneDisplayDraft` from `SettingsDataStore.snapshotFlow` in `EditSensorFieldViewModel` (plus `isZoneDisplayDirty` flag); expose `onZoneDisplayOptionsChanged` / `onApplyZoneDisplayToAll` updating draft state only; persist modified `ZoneDisplayOptions` via `SettingsDataStore` exclusively inside `saveChanges()` (discarded on Cancel). In `EditSensorFieldDialog` Group 3, when `resolveZoneProfileTarget(uiState.selectedSensorType, uiState.sportType) != null`, render profile-labeled `ZoneDisplayOptionsToggleList` (`showGaugeToggle = target == HRV_DFA_A1`) + `zone_display_apply_all` `OutlinedButton` (zero nested `verticalScroll`) + Light/Dark `@Preview`s.

### Step 4: Tracking Tabs Config in `TrackingTabGridContent` & `SensorGridScreen` (`REQ-UI-2783.3`, `TST-UI-2783.3`)
* **Files**: `TrackingTabGridContent.kt`, `SensorGridScreen.kt`.
* **Changes**: Pass `currentActivityType` (`ActivityType`) to `SensorGridScreen`; in `ScreenMode.CONFIGURATION`, render a collapsible/card Zone Display section below the WYSIWYG toggles using `SingleChoiceSegmentedButtonRow` (compact padding, `icon = {}`, `maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`), `ZoneDisplayOptionsToggleList` (`showGaugeToggle = selected == HRV_DFA_A1`), and `zone_display_apply_all`, persisting immediately to `SettingsDataStore` on tap, with Light/Dark `@Preview`s.

### Step 5: Tests, Visual Inspection & Verification (`TST-UI-2783.1`..`.4`)
* **Files**: `EditSensorFieldZoneDisplayTest.kt` (new), `TrackingTabConfigZoneDisplayTest.kt` (new), `ZoneDisplayLocalizationTest.kt`.
* **Changes**: Add ViewModel/Compose/DataStore tests & Light/Dark screenshot capture (`docs/engineering/walkthroughs/img/ATT-8498_zone_display_tile_and_tabs.png` inspected via `read_file`) with ZERO `src/main` file reads (`REQ-PRO-049`). Run under `python3 tools/parallel_sprint_lock.py`: `EditSensorField*Test`, `TrackingTab*Test`/`SensorGrid*Test`, `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, `ZoneDisplayLocalizationTest`, `TranslationParityTest`, `ThemeColorCentralizationAuditTest`, and `SourceTextReadingAuditContractTest`.
