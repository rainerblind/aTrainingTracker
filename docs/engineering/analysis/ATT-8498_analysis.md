# Stage 1 Analysis: ATT-8498 - Relocate ZoneDisplayOptions to Tile & Tab Config

**Ticket**: [ATT-8498](https://rainerblind.atlassian.net/browse/ATT-8498) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) | **Release**: `V5.0.0`  
**Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8498` | **Author**: `--as agent1`

---

## 1. Problem Statement & PO Rejection Context
Per Ceremony 2 PO Rejection (`2026-11-17T15:59`), commit `81af2506` implemented `ZoneDisplayOptions` inside **Experteneinstellungen** (`AdvancedTuningScreen`, `AdvancedTuningDialog`, `CockpitTypographySection`), which is unwanted and **MUST be removed**. Instead, `ZoneDisplayOptions` (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`, `showGauge` for HRV) MUST be configurable: (1) when **long-pressing a sensor tile (`Kachel`)** (`EditSensorFieldDialog`) and (2) within **tracking tabs configuration** (`ScreenMode.CONFIGURATION` in `TrackingTabsScreen` / `SensorGridScreen`).

---

## 2. Forensic Analysis & Verification

### Independent Peer Review & Verification
- **Audit of `81af2506` (Revert vs. Retain)**:
  - *Remove from Expert Settings*: Revert `zoneDisplayDraft` state/persistence/reset from `AdvancedTuningScreen.kt` & `AdvancedTuningDialog.kt`; remove `CockpitZoneDisplaySubsection` & zone preview changes from `CockpitTypographySection.kt` (restoring `CockpitDropdownSelector` at 367 LOC `< 400`, `REQ-UI-277.3`); delete `CockpitZoneDisplaySubsection.kt`; delete `AdvancedTuningZoneDisplayTest.kt` (which also resolves `ATT-8631` `SourceTextReadingAuditContractTest` failure from `L310–313` reading `src/main`); remove `testCaptureCockpitZoneDisplayScreenshot_LightAndDark` from `AdvancedTuningSectionsScreenshotTest.kt` (retaining `ATT-8117`).
  - *Retain Shared*: Keep `ZoneDisplayOptionsControls.kt` (`ZoneProfileTarget`, `ZoneDisplayOptionsDraft`, `ZoneDisplayOptionsToggleList` in `ui/settings/`), `ZonesSettingsActivity.kt` reuse (preserving `R.string.zone_display_gauge` & `displayOptions.showGauge` for `HrvDfaA1SettingsContractTest`), and 9-locale `zone_display_apply_all` + `ZoneDisplayLocalizationTest.kt`.
- **Target UX Location 1 — Long-Press Sensor Tile (`EditSensorFieldDialog.kt` / `EditSensorFieldViewModel.kt`)**:
  - `SensorFieldView.kt:308–314` triggers `onEdit()` on long-press in `TRACKING` (and tap in `CONFIGURATION`), opening `EditSensorFieldDialog` (`TrackingTabGridContent.kt:179–204`).
  - `TrackingViewModel.resolveDisplayOptions` (`L1138–1162`) maps strictly: `(HR, RUN) -> HR_RUN`, `(HR, BIKE) -> HR_BIKE`, `(POWER, BIKE) -> PWR_BIKE`, `(HRV_DFA_A1, *) -> HRV_DFA_A1` (`else -> default`). `EditSensorFieldDialog` Group 3 should render `ZoneDisplayOptionsToggleList` + `zone_display_apply_all` ONLY for those 4 zone-capable `(SensorType, BSportType)` pairs, persisting via `SettingsDataStore` on **Save** (`saveChanges()`) and discarding on **Cancel**.
- **Target UX Location 2 — Tracking Tabs Config (`TrackingTabsScreen.kt` / `SensorGridScreen.kt`)**:
  - Drawer `R.id.drawer_tracking_layouts` (`prefsConfigureDisplaysTitle`) -> `ActivityTypeSelectionDialog` -> `MainActivityWithNavigation.onActivityTypeSelected` (`L879`) opens `TrackingTabsScreen` in `ScreenMode.CONFIGURATION`, rendering `TrackingTabConfigHeader` + `SensorGridScreen` (`L603–911`).
  - Adding a collapsible/dedicated `ZoneDisplayOptions` section in `SensorGridScreen` `CONFIGURATION` mode (using `SingleChoiceSegmentedButtonRow` for `ZoneProfileTarget` pre-selected from `ActivityType`, `ZoneDisplayOptionsToggleList`, and `zone_display_apply_all`) persists immediately to `SettingsDataStore`, matching `SpatialCockpitToggleCard` semantics.

### Side-Effect & Cross-Caller Impact Analysis
- **Live Sync**: `TrackingViewModel.kt:349–370` observes `SettingsDataStore.snapshotFlow`; saves from tile/tab config propagate live to cockpit tiles and `ZonesSettingsActivity`.
- **Scroll Safety & Contracts**: `AppModalBottomSheet` has `scrollable = true` (`design_guidelines.md §5.8`); no nested `verticalScroll`. `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, `SourceTextReadingAuditContractTest` all remain green.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope**: Remove Zone Display UI/state from `AdvancedTuningScreen`/`Dialog`/`CockpitTypographySection`; expose shared `ZoneDisplayOptions` controls in `EditSensorFieldDialog` (Save/Cancel) and `SensorGridScreen` `CONFIGURATION` mode (immediate save); revise `REQ-UI-2783`/`TST-UI-2783` & tests (no `src/main` reads). **Complexity**: `2 SP` (**MEDIUM** risk).
- **Out-of-Scope**: Zone threshold editing, `SettingsDataStore` schema/keys, `SensorFieldView` rendering changes, or per-tile DB columns in `TrackingViews.db`.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-UI-2783` (retargeting `REQ-UI-172` Zone Display Options), `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-UI-376`, `REQ-TRK-061.4`, `REQ-PRO-049.1`.
- **Historical Origin & Commit Trace**: `ATT-1390` (`e3966ebf`) added `ZoneDisplayOptions` in `ZonesSettingsActivity`; `ATT-8498` (`81af2506`) placed it in `CockpitTypographySection`, rejected in Ceremony 2 (`2026-11-17`).
- **Root Reason for Existing Formulation**: `81af2506` assumed users wanted zone toggles inside Expert Settings alongside tile border/font tuning; PO clarified tile long-press & tracking tabs config are the natural UX entry points.
- **Preservation of Core Invariants**: `CockpitTypographySection` `< 400` LOC + `PreviewCockpitTile` contract, `ZonesSettingsActivity` (`HrvDfaA1SettingsContractTest`), zero `src/main` test reads (`REQ-PRO-049.1`), and zone threshold preservation.

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: Revise `REQ-UI-2783` (relocating from Expert Settings to `EditSensorFieldDialog` & `SensorGridScreen` config mode); preserve `REQ-UI-172`, `REQ-UI-262.5`, `REQ-UI-277.3`, `REQ-PRO-049.1`.
- **Affected Architecture Elements**: `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`, `CockpitTypographySection.kt`, `CockpitZoneDisplaySubsection.kt` (delete), `ZoneDisplayOptionsControls.kt`, `EditSensorFieldDialog.kt`, `EditSensorFieldViewModel.kt`, `SensorGridScreen.kt` (`TrackingTabGridContent.kt` / `TrackingTabsScreen.kt` wiring).
- **Verification Impact & Test Surface**: Remove `AdvancedTuningZoneDisplayTest.kt` & `ATT-8498` screenshot in `AdvancedTuningSectionsScreenshotTest.kt`; add `EditSensorFieldZoneDisplayTest.kt` + `TrackingTabConfigZoneDisplayTest.kt` (Compose/DataStore/contract, zero `src/main` reads); re-verify `CockpitPreviewTileContractTest`, `AdvancedTuningModularityTest`, `TuningDesignGuidelinesContractTest`, `HrvDfaA1SettingsContractTest`, `SourceTextReadingAuditContractTest`, `ZoneDisplayLocalizationTest`.
- **Risk Assessment & Invariants**: **MEDIUM** risk (`2 SP`). Guards against `CockpitTypographySection` breaking `CockpitPreviewTileContractTest` when restoring `CockpitDropdownSelector`, nested scroll inside `AppModalBottomSheet`, and `SourceTextReadingAuditContractTest`. `ANT-Android-SDKs` (`0e8cea71`) untouched.
