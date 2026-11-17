# Stage 2: Requirement & Test Specification - ATT-8498 (PO Rejection Redesign)

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Sub-task**: `[Req & Test Spec]` | **Epic**: `ATT-5908`  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8498`  
**Requirement Mapping**: `REQ-UI-2783` (`REQ-UI-2783.1`..`REQ-UI-2783.3`) | **Test Spec ID**: `TST-UI-2783` (`TST-UI-2783.1`..`TST-UI-2783.4`)  
**Invariants Preserved**: `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4`, `REQ-PRO-049.1`

---

## 1. Requirement Specification (`REQ-UI-2783`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. **Removal from Expert Settings (`REQ-UI-2783.1`)**:
   - `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, and `CockpitTypographySection.kt` SHALL NOT expose `ZoneDisplayOptions` UI, `ZoneDisplayOptionsDraft` state/Save/Reset wiring, or zone overrides in `PreviewCockpitTile`.
   - `CockpitZoneDisplaySubsection.kt` and `AdvancedTuningZoneDisplayTest.kt` SHALL be deleted (eliminating the `src/main` `readLines()` violation under `REQ-PRO-049.1`), and `testCaptureCockpitZoneDisplayScreenshot_LightAndDark` removed from `AdvancedTuningSectionsScreenshotTest.kt`. `CockpitTypographySection.kt` MUST restore its pre-`81af2506` inline `ExposedDropdownMenuBox` selectors (`< 400` LOC per `REQ-UI-277.3`).
2. **Sensor Tile Long-Press (`EditSensorFieldDialog` / `EditSensorFieldViewModel`, `REQ-UI-2783.2`)**:
   - `(selectedSensorType, sportType)` SHALL map 1:1 with `TrackingViewModel.resolveDisplayOptions` (`(HR, RUN) -> HR_RUN`, `(HR, BIKE) -> HR_BIKE`, `(POWER, BIKE) -> PWR_BIKE`, `(HRV_DFA_A1, *) -> HRV_DFA_A1`).
   - For those 4 zone-capable pairs, Group 3 SHALL render `ZoneDisplayOptionsToggleList` (`showGaugeToggle = target == HRV_DFA_A1`) + `zone_display_apply_all` without nested `verticalScroll`, persisting ONLY on **Save** (`saveChanges()`) and discarding on **Cancel**. Non-zone or unmapped metrics MUST hide the section.
3. **Tracking Tabs Config & Shared Invariants (`REQ-UI-2783.3`)**:
   - `SensorGridScreen` in `ScreenMode.CONFIGURATION` SHALL provide profile-scoped `ZoneDisplayOptions` controls (`SingleChoiceSegmentedButtonRow` across all 4 `ZoneProfileTarget`s pre-selected from `ActivityType`, `ZoneDisplayOptionsToggleList`, and `zone_display_apply_all`), persisting immediately to `SettingsDataStore`.
   - Shared `ZoneDisplayOptionsControls` (`ui/settings/`) SHALL preserve `ZonesSettingsActivity` (`HrvDfaA1SettingsContractTest`), zone thresholds, and 9-locale parity (`values`..`values-pt`) across `zone_display_*` and `tab_*` keys (zero new untranslated string keys needed).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* Expert Settings (**Cockpit & Kacheln**), *When* expanded, *Then* zero `ZoneDisplayOptions` controls appear and `CockpitTypographySection.kt < 400` LOC.
* *Given* a zone-capable tile opened via long-press (`EditSensorFieldDialog`), *When* **Cancel** is tapped, changes discard; *When* **Save** is tapped, changes persist in `SettingsDataStore` (thresholds untouched); *Given* a non-zone tile, the section is hidden.
* *Given* `TrackingTabsScreen` in `ScreenMode.CONFIGURATION`, *When* switching `ZoneProfileTarget` via `SingleChoiceSegmentedButtonRow` and toggling `ZoneDisplayOptions` or `zone_display_apply_all` (preserving `HRV.showGauge`), *Then* `SettingsDataStore` updates immediately.
* *Given* 9 locale `strings.xml` files and Light/Dark themes (`Rule 8.5`), *When* tests execute, *Then* `TranslationParityTest`, `ZoneDisplayLocalizationTest`, `SourceTextReadingAuditContractTest` (zero `src/main` reads), and `ATT-8498_zone_display_tile_and_tabs.png` pass.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-2783` (originally Expert Settings), refining `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4`, `REQ-PRO-049.1`.
* **Historical Origin & Commit Trace**: `ATT-1390` (`e3966ebf`) added `ZoneDisplayOptions` in `ZonesSettingsActivity`; `ATT-8498` (`81af2506`) placed them in `CockpitTypographySection`, rejected in Ceremony 2 (`2026-11-17T15:59`).
* **Root Reason for Existing Formulation**: `81af2506` co-located zone toggles with typography tuning; PO clarified tile long-press & tracking tabs config are the expected entry points.
* **Preservation of Core Invariants**: `CockpitTypographySection < 400` LOC, `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, zero `src/main` test reads, and zone thresholds remain intact.

---

## 2. Test Specification (`TST-UI-2783`)

1. **`TST-UI-2783.1` (Expert Settings Removal & Contract Guards)**: Verify zero Zone Display UI in `AdvancedTuningScreen`/`Dialog`/`CockpitTypographySection` plus green `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, and `SourceTextReadingAuditContractTest`.
2. **`TST-UI-2783.2` (Tile Long-Press — `EditSensorFieldZoneDisplayTest.kt`)**: Verify `(SensorType, BSportType)` mapping, hidden state for non-zone sensors, `HRV.showGauge` gating & `applyToAll` preservation, and **Save** vs. **Cancel** persistence (thresholds untouched).
3. **`TST-UI-2783.3` (Tracking Tabs Config — `TrackingTabConfigZoneDisplayTest.kt`)**: Verify `SensorGridScreen` `CONFIGURATION` mode `SingleChoiceSegmentedButtonRow` pre-selection from `ActivityType`, toggle persistence to `SettingsDataStore`, and `zone_display_apply_all`.
4. **`TST-UI-2783.4` (9-Locale Parity & Rule 8.5 Visuals)**: Verify `ZoneDisplayLocalizationTest`, `TranslationParityTest`, and Light/Dark PNG `docs/engineering/walkthroughs/img/ATT-8498_zone_display_tile_and_tabs.png` (zero `src/main` reads).

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-2783.1]` | Contract / UI | Removal from `AdvancedTuning*`/`CockpitTypographySection`; 5 contract suites | `REQ-UI-2783.1`, `REQ-UI-2783.3` | Planned |
| `[TST-UI-2783.2]` | Unit / UI | `EditSensorFieldZoneDisplayTest`: mapping, Save/Cancel, `applyToAll` | `REQ-UI-2783.2` | Planned |
| `[TST-UI-2783.3]` | Compose UI | `TrackingTabConfigZoneDisplayTest`: `CONFIGURATION` profile & DataStore sync | `REQ-UI-2783.3` | Planned |
| `[TST-UI-2783.4]` | i18n / Visual | 9-locale parity + Light/Dark `ATT-8498_zone_display_tile_and_tabs.png` | `REQ-UI-2783.2`, `REQ-UI-2783.3` | Planned |
