# Stage 5 Walkthrough: ATT-8498 (PO Rejection Redesign)

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8498`  
**Requirement Mapping**: `REQ-UI-2783` (`REQ-UI-2783.1`..`REQ-UI-2783.3`)  
**Test Spec Mapping**: `TST-UI-2783` (`TST-UI-2783.1`..`TST-UI-2783.4`)  
**Author**: AI Agent 1 (`--as agent1`) | **Date**: 2026-11-17

---

## 1. Executive Summary & Changes Implemented
Relocated `ZoneDisplayOptions` configuration out of **Experteneinstellungen** and into both PO-requested target UX locations on `feature/ATT-8498` (rebased onto `sprint/2026-11-17_02_Spät` @ `b1329131`):
1. **Expert Settings Removal (`REQ-UI-2783.1`)**: Reverted `ZoneDisplayOptions` UI/draft/Save/Reset wiring in `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`, restored `CockpitTypographySection.kt` to **367 LOC** (`< 400`, `REQ-UI-277.3`), deleted `CockpitZoneDisplaySubsection.kt` & `AdvancedTuningZoneDisplayTest.kt` (eliminating `file.readLines()` on `src/main`), and removed `ATT-8498` screenshot capture from `AdvancedTuningSectionsScreenshotTest.kt`.
2. **Shared Controls & Atomic DataStore (`REQ-UI-2783.2`, `.3`)**: Extended `ZoneDisplayOptionsControls.kt` with `resolveZoneProfileTarget`, `defaultZoneProfileTarget`, `ZoneDisplaySwitchRow`, `ZoneDisplayProfileSegmentedSelector` (`SingleChoiceSegmentedButtonRow`, `icon = {}`, `PaddingValues(6.dp, 0.dp)`, `labelSmall`, `maxLines = 1, softWrap = false, TextOverflow.Ellipsis`), `ZoneDisplayConfigSection`, and Light/Dark `@Preview`s; added atomic `SettingsDataStore.saveAllZoneDisplayOptions(...)` while preserving `ZonesSettingsActivity` (`HrvDfaA1SettingsContractTest`).
3. **Sensor Tile Long-Press (`EditSensorFieldViewModel` + `EditSensorFieldDialog`, `REQ-UI-2783.2`)**: Hydrated `ZoneDisplayOptionsDraft` from `SettingsDataStore.snapshotFlow` preserving draft across async `loadInitialState()` (`_uiState.update`), added `refreshZoneDisplayDraftIfClean()` / `discardZoneDisplayChanges()` across Cancel/reopen of cached ViewModels, rendered `ZoneDisplayConfigSection` in Group 3 solely for `(HR, RUN)`, `(HR, BIKE)`, `(POWER, BIKE)`, and `(HRV_DFA_A1, *)`, and persisted only on `saveChanges()`.
4. **Tracking Tabs Config (`TrackingTabGridContent` + `SensorGridScreen`, `REQ-UI-2783.3`)**: Passed `activityType` to `SensorGridScreen` and mounted `TrackingTabZoneDisplaySection` in `ScreenMode.CONFIGURATION` with immediate `SettingsDataStore` persistence and Light/Dark `@Preview`s.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-2783.1` | `TST-UI-2783.1` | Compose UI + Contracts: `EditSensorFieldZoneDisplayTest`, `ui.settings.tuning.*`, `HrvDfaA1SettingsContractTest` | **PASSED** (Post-heal) | `Verified` |
| `REQ-UI-2783.2` | `TST-UI-2783.2` | Unit + DataStore + Compose UI: `EditSensorFieldZoneDisplayTest` (**6/6**, incl. async race & cancel-reopen) | **PASSED** (Post-heal) | `Verified` |
| `REQ-UI-2783.3` | `TST-UI-2783.3` | Compose UI + DataStore: `TrackingTabConfigZoneDisplayTest` (**3/3**, `CONFIGURATION` profile & sync) | **PASSED** (Post-heal) | `Verified` |
| `REQ-UI-2783.2`, `.3` | `TST-UI-2783.4` | i18n (`ZoneDisplayLocalizationTest` **3/3**, `TranslationParityTest` **10/10**) + Rule 8.5 PNG | **PASSED** (Post-heal) | `Verified` |

---

## 3. Automated Test, Governance & Visual Evidence (Post-Heal & Post-Rebase on `b1329131`)
* **Targeted & Contract Gradle Suite** (`python3 tools/parallel_sprint_lock.py ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingTabConfigZoneDisplayTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.SensorGrid*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest" --tests "com.atrainingtracker.trainingtracker.localization.ZoneDisplayLocalizationTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest"`):
  * **BUILD SUCCESSFUL** — **274 / 274 tests PASSED** (0 failures, 0 errors, 0 skipped across 48 suites): `EditSensorField*` 7 suites (**57/57**, incl. `EditSensorFieldZoneDisplayTest` **6/6**), `SensorGrid*` + `TrackingTabConfigZoneDisplayTest` 10 suites (**57/57**), `ui.settings.tuning.*` 26 suites (**130/130**), `HrvDfaA1SettingsContractTest` (**12/12**), `ZoneDisplayLocalizationTest` (**3/3**), `TranslationParityTest` (**10/10**), `ThemeColorCentralizationAuditTest` (**5/5**).
* **Living Documentation & Governance**: `python3 tools/verify_requirement_governance.py --sync-indexes`, `python3 tools/verify_requirement_governance.py`, and `--text-file docs/engineering/walkthroughs/ATT-8498_walkthrough.md` exited **`0`** (100% parity, 4 new `@Composable`s reachable, 4-field Chesterton's Fence audit PASS).
* **Visual Proof (`Rule 8.5`)**: Inspected `docs/engineering/walkthroughs/img/ATT-8498_zone_display_tile_and_tabs.png` via `read_file`: German (`de-rDE`) Light & Dark cards show single-line `Lauf HF | Rad HF | Rad Leistung | HRV (DFA α1)` segmented buttons, 4/5 toggles (`Zonen-Balken` on HRV), and full-width `Auf alle anwenden` with zero clipping or wrapping.

---

## 4. System Invariants & Hygiene
* `CockpitTypographySection.kt` = **367 LOC** (`< 400`); zero `ZoneDisplayOptions` UI/state in `AdvancedTuning*` or `CockpitTypographySection`.
* Zero `src/main` file reads in `EditSensorFieldZoneDisplayTest.kt` or `TrackingTabConfigZoneDisplayTest.kt` (`REQ-PRO-049.1`); `ANT-Android-SDKs` untouched at `0e8cea714a231fb9565170d4ed01eafa868f9798`.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-2783` (originally Expert Settings), refining `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4`, `REQ-PRO-049.1`.
* **Historical Origin & Commit Trace**: `ATT-1390` (`e3966ebf`) added `ZoneDisplayOptions` in `ZonesSettingsActivity`; `ATT-8498` (`81af2506`) placed them in `CockpitTypographySection` (Ceremony 2 PO rejected `2026-11-17T15:59`).
* **Root Reason for Existing Formulation**: `81af2506` co-located zone toggles with Expert Settings typography tuning; PO directed tile long-press & tracking tabs config instead.
* **Preservation of Core Invariants**: Preserves `< 400` LOC `CockpitTypographySection`, `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, zero `src/main` reads, and zone thresholds.
