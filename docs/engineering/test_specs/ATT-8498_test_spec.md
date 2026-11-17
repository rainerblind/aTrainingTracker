# Stage 2: Requirement & Test Specification - ATT-8498: ZoneDisplayOptions in Cockpit & Kacheln

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Sub-task**: `[Req & Test Spec]` | **Epic**: `ATT-5908` (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8498`  
**Requirement Mapping**: `REQ-UI-2783` (`REQ-UI-2783.1`..`REQ-UI-2783.3`) | **Test Spec ID**: `TST-UI-2783` (`TST-UI-2783.1`..`TST-UI-2783.4`)  
**Invariants Preserved**: `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4` | **Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Requirement Specification (`REQ-UI-2783`)

### 1.1 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.2 Functional & Architectural Requirements
1. **Profile-Scoped ZoneDisplayOptions in Cockpit & Kacheln (`REQ-UI-2783.1`)**:
   - `CockpitTypographySection.kt` (MUST remain `< 400` LOC per `REQ-UI-277.3`) SHALL integrate a Zone Display subsection separated by `HorizontalDivider` (no `ElevatedCard`) with:
     - `TuningSegmentedButtonRow` (`maxLines = 1, softWrap = false`, zero `FilterChip` in `categories/` per `REQ-UI-262.5`) selecting among `HR_RUN`, `HR_BIKE`, `PWR_BIKE`, and `HRV_DFA_A1`.
     - Shared `ZoneDisplayOptions` toggles rendered via `TuningToggleItem` (zero raw `Switch(` in `categories/`): `zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`, and `zone_display_gauge` (strictly for `HRV_DFA_A1`).
     - `"Apply to all"` (`R.string.zone_display_apply_all`) copying `showBackground`, `showLeftBar`, `showRightBar`, and `showTextColor` across all 4 profiles while preserving `HRV_DFA_A1.showGauge`.
2. **Shared Controls & ZonesSettingsActivity Preservation (`REQ-UI-2783.2`)**:
   - Shared `ZoneDisplayOptionsControls` (outside `ui/settings/tuning/categories/`) SHALL serve `ZonesSettingsActivity` & Cockpit cleanly while preserving `R.string.zone_display_gauge`, `displayOptions.showGauge`, and `showBar = displayOptions.showGauge` (`HrvDfaA1SettingsContractTest`).
3. **Combined Preview, Host Parity & 9-Locale Completeness (`REQ-UI-2783.3`)**:
   - `PreviewCockpitTile` (`CockpitTypographySection.kt`) SHALL render active profile `ZoneDisplayOptions` with `TTColor.Zone2` (`R.color.zone_2`): 12% background tint, `6.dp` left/right bars, zone text color, and HRV gauge bar (`HRV_DFA_A1` + `showGauge == true`).
   - Both `AdvancedTuningScreen` and `AdvancedTuningDialog` SHALL hydrate once from `SettingsDataStore`, persist via `saveZoneDisplayOptions`/`saveHrvZoneDisplayOptions` on **Save**, discard on **Cancel**, and reset only `ZoneDisplayOptions()` (never zone thresholds) on **Reset to Defaults**.
   - `zone_display_apply_all` SHALL exist with accurate translations in all 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* **Cockpit & Kacheln** expanded in `AdvancedTuningScreen` or `AdvancedTuningDialog`, *When* an athlete switches profiles via `TuningSegmentedButtonRow` and toggles `ZoneDisplayOptions` via `TuningToggleItem`, *Then* `PreviewCockpitTile` immediately updates its `zone_2` tint, bars, text color, and HRV gauge alongside tile geometry/typography.
* *Given* `zone_display_apply_all` is tapped while `HRV.showGauge = true`, *When* applied, *Then* `showBackground`, `showLeftBar`, `showRightBar`, `showTextColor` match across all 4 profiles while `HRV.showGauge` stays `true`.
* *Given* unsaved `ZoneDisplayOptions` edits in `AdvancedTuningScreen` or `AdvancedTuningDialog`, *When* **Cancel** is tapped, changes discard; *When* **Reset** is tapped, only `ZoneDisplayOptions()` resets (zone thresholds preserved); *When* **Save** is tapped, all 4 profiles persist in `SettingsDataStore`.
* *Given* all 9 locale `strings.xml` files and Light/Dark themes (`Rule 8.5`), *When* localization, contract, and screenshot tests run, *Then* all contracts (`< 400` LOC, zero `FilterChip`/`Switch`/`ElevatedCard` in `categories/`, `HrvDfaA1SettingsContractTest`), 9-locale parity, and Light/Dark visuals pass.

### 1.4 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new `REQ-UI-2783`, refining `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, and `REQ-TRK-061.4`.
* **Historical Origin & Commit Trace**: `ATT-1390` (`e3966ebf`) added `ZoneDisplayOptions` in `ZonesSettingsActivity.kt`; `ATT-2457` added `PreviewCockpitTile` + `< 400` LOC limit; `ATT-7539` added `TuningDesignGuidelinesContractTest`; `ATT-7742` added `HrvDfaA1SettingsContractTest`.
* **Root Reason for Existing Formulation**: Zone visualization lived solely in `ZonesSettingsActivity.kt` before Cockpit tile customization matured.
* **Preservation of Core Invariants**: Preserves `< 400` LOC in `CockpitTypographySection.kt`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, `SettingsDataStore` thresholds, and 9-locale parity.

---

## 2. Test Specification (`TST-UI-2783`)

1. **`TST-UI-2783.1` (Profile Selector, Toggles & Combined Preview — `AdvancedTuningZoneDisplayTest.kt`)**:
   - Compose UI test verifying `TuningSegmentedButtonRow` switching (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`, `HRV_DFA_A1`), `TuningToggleItem` state updates (`zone_display_gauge` visible strictly for `HRV_DFA_A1`), and live `PreviewCockpitTile` `zone_2` rendering.
2. **`TST-UI-2783.2` (`zone_display_apply_all` & Save/Cancel/Reset Parity — `AdvancedTuningZoneDisplayTest.kt`)**:
   - Verify `applyToAll` copies `showBackground`, `showLeftBar`, `showRightBar`, `showTextColor` across all 4 profiles while preserving `HRV.showGauge`.
   - Verify Save, Cancel, and Reset-to-Defaults across both `AdvancedTuningScreen` and `AdvancedTuningDialog` via `SettingsDataStore` (thresholds untouched on Reset).
3. **`TST-UI-2783.3` (Contract & Modularity Guards)**:
   - Run `CockpitTypographySection.kt < 400` LOC check, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, and `AdvancedTuningScreenTest`.
4. **`TST-UI-2783.4` (9-Locale Parity & Rule 8.5 Light/Dark Screenshots)**:
   - Verify `zone_display_apply_all` across 9 locales (`ZoneDisplayLocalizationTest` / `TranslationParityTest`) and capture Light/Dark screenshots in `AdvancedTuningSectionsScreenshotTest.kt` inspected via `read_file`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-2783.1]` | Compose UI | Profile `TuningSegmentedButtonRow`, `TuningToggleItem`, `PreviewCockpitTile` (`zone_2`) | `REQ-UI-2783.1`, `REQ-UI-2783.3` | Planned |
| `[TST-UI-2783.2]` | Unit / UI | `zone_display_apply_all` (preserving `HRV.showGauge`), Save/Cancel/Reset parity | `REQ-UI-2783.1`, `REQ-UI-2783.3` | Planned |
| `[TST-UI-2783.3]` | Contract | `< 400` LOC, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, modularity | `REQ-UI-2783.1`, `REQ-UI-2783.2` | Planned |
| `[TST-UI-2783.4]` | i18n / Visual | 9-locale `zone_display_apply_all` parity + Light/Dark Rule 8.5 screenshots | `REQ-UI-2783.3`, `REQ-UI-262.5` | Planned |
