# Stage 1 Analysis: ATT-8498 - ZoneDisplayOptions in Cockpit & Kacheln

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8498`  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Problem Statement & Motivation
`ZoneDisplayOptions` (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`, `showGauge`) can currently only be edited inside `ZonesSettingsActivity.kt` per zone tab (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`, `HRV_DFA_A1`). Athletes tuning tile typography, borders, and corner radius under **Cockpit & Kacheln** (`CockpitTypographySection.kt` in `AdvancedTuningScreen` / `AdvancedTuningDialog`) cannot configure or preview zone highlighting alongside tile geometry in one place.

---

## 2. Forensic Analysis & Verification

### Independent Peer Review & Verification
Audited the ticket's proposed architecture against the live codebase and uncovered **4 critical design constraints** not noted in the ticket:
1. **`TuningDesignGuidelinesContractTest.kt` (`L52–85`, `REQ-UI-262.5`)**: Prohibits `FilterChip(`, raw `Switch(`, and `ElevatedCard(` in `ui/settings/tuning/categories/`. Therefore, the ticket's suggested `FilterChip` profile row MUST use `TuningSegmentedButtonRow` (`TuningFormControls.kt:304`, `maxLines = 1, softWrap = false`), toggles inside `categories/` MUST use `TuningToggleItem` (`L116`, not raw `Switch` from `ZoneDisplayToggleRow` in `ZonesSettingsActivity.kt:783`), and subsections MUST use `HorizontalDivider` instead of nested `Card`/`ElevatedCard`.
2. **`CockpitTypographySection.kt` `< 400` LOC Invariant (`REQ-UI-277.3`)**: `CockpitTypographySection.kt` is already `367` LOC. Extracting shared `ZoneDisplayOptionsControls` (outside `categories/`, e.g. `ui/settings/`) + a compact `CockpitZoneDisplaySubsection` is mandatory to keep `CockpitTypographySection.kt < 400` LOC.
3. **`HrvDfaA1SettingsContractTest.kt` (`L297–300`)**: Scans `ZonesSettingsActivity.kt` source text for `R.string.zone_display_gauge` and `displayOptions.showGauge`. Refactoring `ZonesSettingsActivity.kt` (`L570–611`, `L1270–1329`) to share toggle controls must retain those literal references (e.g., passing `showGauge = displayOptions.showGauge` with `stringResource(R.string.zone_display_gauge)`).
4. **Combined Preview Fidelity (`SensorFieldView.kt:319–552`)**: `PreviewCockpitTile` (`CockpitTypographySection.kt:320`) currently renders a bare `Card > Column`. To match `SensorFieldView`, it must support `Row(Modifier.height(IntrinsicSize.Min))` with `6.dp` left/right bars (`TTColor.Zone2` / `R.color.zone_2`), `12%` background composite tint, `showTextColor`, and HRV `ZoneGaugeBar` when `HRV_DFA_A1` + `showGauge == true`.

### Side-Effect & Cross-Caller Impact Analysis
- **State Parity (`AdvancedTuningScreen.kt` vs. `AdvancedTuningDialog.kt`)**: Both entry points (`AdvancedTuningScreen.kt` `L93–295` and deprecated `AdvancedTuningDialog.kt` `L84–243, 457–470`) independently hold Compose state, `Save`, `Cancel`, and `Reset to Defaults` (`TuningResetDefaultsButton` / TopAppBar `RestartAlt`). Both must hydrate `hrRun`/`hrBike`/`pwrBike`/`hrvDisplayOptions` once from `SettingsDataStore.snapshotFlow` (`isZoneDisplayInitialized` guard), mutate local state only until Save, persist via `saveZoneDisplayOptions` + `saveHrvZoneDisplayOptions` on Save, discard on Cancel, and reset only `ZoneDisplayOptions()` (never zone thresholds `hrRunZoneMax`, etc.!) on Reset. Extracting a shared `ZoneDisplayOptionsDraft` state holder avoids duplicating boilerplate across both hosts.
- **Live Cockpit & Zones Sync (`TrackingViewModel.kt:360–368`)**: `TrackingViewModel` observes `SettingsDataStore.snapshotFlow`; saving from `AdvancedTuningScreen`/`Dialog` updates live cockpit tiles and `ZonesSettingsActivity` automatically.
- **9-Locale Localization**: `zone_display_*`, `tab_hr_*`, `tab_pwr_bike`, `tab_hrv_dfa_a1` exist in all 9 locales (`ZoneDisplayLocalizationTest`). New `zone_display_apply_all` (`"Apply to all"` / `"Auf alle anwenden"`) must be added to all 9 `strings.xml` files.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**: Extract reusable Zone Display toggle controls (`ZoneDisplayOptionsControls.kt`), integrate profile selector (`TuningSegmentedButtonRow`) + toggles (`TuningToggleItem`) + `"Apply to all"` (`zone_display_apply_all`, preserving `hrv.showGauge`) + combined `PreviewCockpitTile` zone preview in `CockpitTypographySection.kt` (`< 400` LOC), wire Save/Cancel/Reset in `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`, localize `zone_display_apply_all` across 9 locales, add unit/contract/screenshot tests. **Complexity**: `2 SP` (**MEDIUM** risk).
- **Out-of-Scope**: Modifying zone thresholds, `SettingsDataStore` schema/keys, or `SensorFieldView` runtime logic.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-UI-172` (*Zone Display Options*), `REQ-UI-262.5` (*Design Guidelines Contract*), `REQ-UI-277.3` (*< 400 LOC & `PreviewCockpitTile`*), `REQ-UI-376` (*HRV `showGauge`*), `REQ-TRK-061.4` (`HrvDfaA1SettingsContractTest`).
- **Historical Origin & Commit Trace**: `ATT-1390` (`e3966ebf`) added `ZoneDisplayOptions` in `ZonesSettingsActivity.kt`; `ATT-2457` added `PreviewCockpitTile` + `< 400` LOC rule; `ATT-7539` added `TuningDesignGuidelinesContractTest`; `ATT-7742` added `HrvDfaA1SettingsContractTest`.
- **Root Reason for Existing Formulation**: Zone visualization lived exclusively in `ZonesSettingsActivity` until Cockpit tile customization expanded under `CockpitTypographySection`.
- **Preservation of Core Invariants**: `CockpitTypographySection.kt < 400` LOC, `TuningDesignGuidelinesContractTest` (zero `FilterChip`/`Switch`/`ElevatedCard` in `categories/`), `HrvDfaA1SettingsContractTest`, `CockpitPreviewTileContractTest`, and `ZoneDisplayOptions` defaults/thresholds remain 100% preserved.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Net-new `REQ-UI-2783` (integrating `ZoneDisplayOptions` + combined preview into Cockpit & Tiles), refining `REQ-UI-172` and `REQ-UI-277`.
- **Affected Architecture Elements**: `ZoneDisplayOptionsControls.kt` (new shared UI), `ZonesSettingsActivity.kt`, `CockpitTypographySection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `res/values*/strings.xml` (9 locales).
- **Verification Impact & Test Surface**: `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, `ZoneDisplayLocalizationTest`, `TranslationParityTest`, `AdvancedTuningScreenTest`, plus new Compose UI + Save/Cancel/Reset + Rule 8.5 screenshot tests.
- **Risk Assessment & Invariants**: **MEDIUM** risk (`2 SP`). Guards against `TuningDesignGuidelinesContractTest` (`FilterChip`/`Switch`), `CockpitTypographySection < 400` LOC, `HrvDfaA1SettingsContractTest`, and threshold preservation on Reset. `ANT-Android-SDKs` (`0e8cea71`) untouched.
