# Stage 3: Implementation Plan - ATT-8498: ZoneDisplayOptions in Cockpit & Kacheln

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8498`  
**Requirements**: `REQ-UI-2783` (`REQ-UI-2783.1`..`.3`) | **Tests**: `TST-UI-2783` (`TST-UI-2783.1`..`.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Traceability & System Invariants
* **`REQ-UI-2783.1`** -> **`TST-UI-2783.1`, `.2`, `.3`**: `TuningSegmentedButtonRow`, `TuningToggleItem`, `HorizontalDivider`, `zone_display_apply_all` (preserving `HRV.showGauge`), `CockpitTypographySection.kt < 400` LOC (`REQ-UI-277.3`), zero `FilterChip`/`Switch`/`ElevatedCard` in `categories/` (`REQ-UI-262.5`).
* **`REQ-UI-2783.2`** -> **`TST-UI-2783.3`**: Shared `ZoneDisplayOptionsControls` in `ui/settings/` preserving `HrvDfaA1SettingsContractTest` (`R.string.zone_display_gauge`, `displayOptions.showGauge`, `showBar = displayOptions.showGauge` in `ZonesSettingsActivity.kt`).
* **`REQ-UI-2783.3`** -> **`TST-UI-2783.1`, `.2`, `.4`**: Combined `PreviewCockpitTile` (`TTColor.Zone2`), Save/Cancel/Reset parity across `AdvancedTuningScreen` & `AdvancedTuningDialog` (thresholds untouched), 9-locale `zone_display_apply_all`, Light/Dark Rule 8.5 screenshots.

---

## 2. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Shared `ZoneDisplayOptionsControls.kt` & `ZonesSettingsActivity.kt` Reuse (`REQ-UI-2783.2`)
* **Files**: `ui/settings/ZoneDisplayOptionsControls.kt` (new), `activities/ZonesSettingsActivity.kt`
* **Changes**:
  - Define `ZoneProfileTarget` (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`, `HRV_DFA_A1`) and `ZoneDisplayOptionsDraft` (pure helpers `optionsFor`, `withUpdated`, `applyToAllFrom` copying `showBackground`/`showLeftBar`/`showRightBar`/`showTextColor` while preserving `hrv.showGauge`, `persistTo(dataStore)`).
  - Extract reusable `ZoneDisplayOptionsToggleList` accepting a `toggleRow` slot so `ZonesSettingsActivity.kt` (`L570–611`, `L1270–1329`) keeps `Card` + `ZoneDisplayToggleRow` and retains `R.string.zone_display_gauge` / `displayOptions.showGauge` / `showBar = displayOptions.showGauge` literals (`HrvDfaA1SettingsContractTest`).

### Step 2: `CockpitZoneDisplaySubsection.kt` & `CockpitTypographySection.kt` (`REQ-UI-2783.1`, `.3`)
* **Files**: `ui/settings/tuning/categories/CockpitZoneDisplaySubsection.kt` (new), `ui/settings/tuning/categories/CockpitTypographySection.kt`
* **Changes**:
  - In `CockpitZoneDisplaySubsection.kt` (strictly `TuningSegmentedButtonRow` + `TuningToggleItem` + `OutlinedButton(zone_display_apply_all)` + `HorizontalDivider`, zero `FilterChip`/`Switch`/`ElevatedCard` per `TuningDesignGuidelinesContractTest`): render profile selector (`tab_hr_run`, `tab_hr_bike`, `tab_pwr_bike`, `tab_hrv_dfa_a1`), 4/5 toggles (`zone_display_gauge` strictly for `HRV_DFA_A1`), and Apply-All button.
  - In `CockpitTypographySection.kt` (keep `< 400` LOC per `REQ-UI-277.3`): add defaulted `zoneDisplayDraft` / `onZoneDisplayDraftChange` parameters, embed `HorizontalDivider` + `CockpitZoneDisplaySubsection`, and enhance `PreviewCockpitTile` (kept inside `CockpitTypographySection.kt` per `CockpitPreviewTileContractTest`) so tile 1 renders active profile value (`148 bpm` / `245 W` / `0.82 α1`) with `TTColor.Zone2` 12% tint, `6.dp` left/right bars (`IntrinsicSize.Min`), `showTextColor`, and `ZoneGaugeBar` (`HRV_DFA_A1 && showGauge`).

### Step 3: Save / Cancel / Reset State Wiring in Both Tuning Hosts (`REQ-UI-2783.3`)
* **Files**: `ui/settings/tuning/AdvancedTuningScreen.kt`, `ui/settings/tuning/AdvancedTuningDialog.kt`
* **Changes**:
  - In both files, collect `SettingsDataStore.getInstance(context).snapshotFlow` with `isZoneDisplayInitialized` guard to hydrate `zoneDisplayDraft` once.
  - Pass `zoneDisplayDraft` + `onZoneDisplayDraftChange` to `CockpitTypographySection`.
  - On **Save**: call `zoneDisplayDraft.persistTo(settingsDataStore)` (`saveZoneDisplayOptions` + `saveHrvZoneDisplayOptions`). On **Cancel**: discard draft. On **Reset**: set `zoneDisplayDraft = ZoneDisplayOptionsDraft()` and persist `ZoneDisplayOptions()` via `persistTo(settingsDataStore)` (zone thresholds untouched).

### Step 4: 9-Locale Localization (`REQ-UI-2783.3`)
* **Files**: `app/src/main/res/values{,-de,-es,-fr,-it,-ja,-nl,-pl,-pt}/strings.xml`, `ZoneDisplayLocalizationTest.kt`
* **Changes**: Add `zone_display_apply_all` across all 9 locales (`"Apply to all"`, `"Auf alle anwenden"`, `"Aplicar a todos"`, `"Appliquer à tout"`, `"Applica a tutti"`, `"すべてに適用"`, `"Op alles toepassen"`, `"Zastosuj do wszystkich"`, `"Aplicar a todos"`); extend `ZoneDisplayLocalizationTest`.

### Step 5: Unit, Contract & Rule 8.5 Screenshot Verification (`TST-UI-2783.1`..`.4`)
* **Files**: `AdvancedTuningZoneDisplayTest.kt` (new), `AdvancedTuningSectionsScreenshotTest.kt`
* **Changes**:
  - Add Compose UI + DataStore Save/Cancel/Reset + `applyToAllFrom` tests in `AdvancedTuningZoneDisplayTest.kt`.
  - Add Light/Dark `CockpitTypographySection` Zone Display capture in `AdvancedTuningSectionsScreenshotTest.kt` and inspect via `read_file` (`Rule 8.5`).
  - Verify `wc -l CockpitTypographySection.kt` (`< 400`) and run targeted Gradle tests (`ui.settings.tuning.*`, `ZoneDisplayLocalizationTest`, `TranslationParityTest`, `HrvDfaA1SettingsContractTest`).
