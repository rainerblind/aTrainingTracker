# Stage 5 Walkthrough: ATT-8498 - ZoneDisplayOptions in Cockpit & Kacheln

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8498`  
**Requirement Mapping**: `REQ-UI-2783` (`REQ-UI-2783.1`..`REQ-UI-2783.3`)  
**Test Spec Mapping**: `TST-UI-2783` (`TST-UI-2783.1`..`TST-UI-2783.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Executive Summary & Changes Implemented
Implemented profile-scoped `ZoneDisplayOptions` configuration and live combined zone preview directly inside **Cockpit & Kacheln** (`9044611040643dbfa6f9b50352ba70dd7d811f8b`):
1. **Shared Controls & `ZonesSettingsActivity` Reuse (`REQ-UI-2783.2`)**: Created `ZoneDisplayOptionsControls.kt` (`ZoneProfileTarget`, `ZoneDisplayOptionsDraft` with `applyToAllFrom` preserving `hrv.showGauge` and `persistTo`, and `ZoneDisplayOptionsToggleList` with host toggle slot). Refactored `ZonesSettingsActivity.kt` (`SettingsScreenContent` & `HrvDfaA1SettingsContent`) to reuse `ZoneDisplayOptionsToggleList` while retaining `R.string.zone_display_gauge` & `displayOptions.showGauge` (`HrvDfaA1SettingsContractTest`).
2. **Cockpit Integration & Combined Preview (`REQ-UI-2783.1`, `.3`)**: Added `CockpitZoneDisplaySubsection.kt` using strictly `TuningSegmentedButtonRow`, `TuningToggleItem`, `OutlinedButton` (`R.string.zone_display_apply_all`), and `HorizontalDivider` (zero `FilterChip`/`Switch`/`ElevatedCard`). Embedded it in `CockpitTypographySection.kt` (**334 LOC** `< 400` LOC invariant `REQ-UI-277.3`), and updated `PreviewCockpitTile` with `TTColor.Zone2` 12% background tint, `6.dp` left/right bars (`IntrinsicSize.Min`), `showTextColor`, and `ZoneGaugeBar` (`HRV_DFA_A1 && showGauge`).
3. **Dual-Host State Wiring & 9-Locale Parity (`REQ-UI-2783.3`)**: Hydrated `zoneDisplayDraft` once via `SettingsDataStore.snapshotFlow` (`isZoneDisplayInitialized`) in both `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt` (Save persists, Cancel discards, Reset resets only `ZoneDisplayOptions()` keeping thresholds). Added `zone_display_apply_all` across all 9 `values*/strings.xml` files.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-2783.1`, `.3` | `TST-UI-2783.1` | Compose UI: `AdvancedTuningZoneDisplayTest` (profile switch, toggles, `PreviewCockpitTile`) | **PASSED** (Stage 4 SHA `90446110`) | `Verified` |
| `REQ-UI-2783.1`, `.3` | `TST-UI-2783.2` | Unit/UI: `AdvancedTuningZoneDisplayTest` (apply-all, Screen & Dialog Save/Cancel/Reset) | **PASSED** (Stage 4 SHA `90446110`) | `Verified` |
| `REQ-UI-2783.1`, `.2` | `TST-UI-2783.3` | Contract: `< 400` LOC, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, modularity | **PASSED** (Stage 4 SHA `90446110`) | `Verified` |
| `REQ-UI-2783.3` | `TST-UI-2783.4` | Localization (`ZoneDisplayLocalizationTest` / `TranslationParityTest`) + Rule 8.5 screenshots | **PASSED** (Stage 4 SHA `90446110`)* | `Verified` |

---

## 3. Automated Test & Governance Evidence
* **Stage 4 Targeted Gradle Verification on Code SHA `90446110`** (`./gradlew :app:testDebugUnitTest --tests "*ZoneDisplay*" --tests "*CockpitPreviewTileContractTest*" --tests "*AdvancedTuningModularityTest*" --tests "*TuningDesignGuidelinesContractTest*" --tests "*HrvDfaA1SettingsContractTest*" --tests "*CockpitTypography*" --tests "*Localization*" --tests "*AdvancedTuningSectionsScreenshotTest*" --tests "*AdvancedTuningScreenTest*" :app:compileDebugAndroidTestKotlin --max-workers=2`): **BUILD SUCCESSFUL** — **191 / 191 tests passed, 0 skipped, 0 failed** (`AdvancedTuningZoneDisplayTest` 6/6, `AdvancedTuningSectionsScreenshotTest` 2/2, `TuningDesignGuidelinesContractTest` 5/5, `HrvDfaA1SettingsContractTest` 12/12, `CockpitPreviewTileContractTest` 1/1, `AdvancedTuningModularityTest` 2/2, `AdvancedTuningScreenTest` 6/6, `ZoneDisplayLocalizationTest` 3/3, `ZoneDisplayOptionsTest` 4/4, `SensorFieldZoneDisplayTest` 7/7, plus localization & typography suites).
* **Zero Redundant Re-Runs (`Rule 7.5`) & Tier-2 Lock Discipline**: Code SHA `90446110` is unchanged in Stage 5a; Gradle was intentionally not re-run while parent Tier-2 executes (`*`Gate 5 on `2026-11-17_01_Früh` will attach/inspect Light & Dark walkthrough PNGs and execute merged-tree Gradle verification).
* **Governance Sync & Audit**: `python3 tools/verify_requirement_governance.py --sync-indexes` and `python3 tools/verify_requirement_governance.py` -> **100% PASS**.

---

## 4. System Invariants & Hygiene
* `CockpitTypographySection.kt` = **334 LOC** (`< 400` LOC); zero `FilterChip`/`Switch`/`ElevatedCard` in `categories/`.
* Zero zone threshold mutations on Reset; 9-locale parity complete (`zone_display_apply_all`).
* `ANT-Android-SDKs` untouched (`0e8cea714a231fb9565170d4ed01eafa868f9798`).
