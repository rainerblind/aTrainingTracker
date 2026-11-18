# Stage 1 Analysis: ATT-8712 - Remove 'Zonen-Balken' Switch from HRV (DFA α1) in ZonesSettingsActivity

**DOC-ID**: `DOC-ANA-ATT-8712` | **Ticket**: [ATT-8712](https://rainerblind.atlassian.net/browse/ATT-8712)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8712` | **Author**: `--as agent1`

---

## 1. Problem Statement & Motivation
In `ATT-8498` (`d7dec447`), `ZoneDisplayOptions`—including `R.string.zone_display_gauge` (*Zonen-Balken*) for `HRV_DFA_A1`—were added to `EditSensorFieldDialog` and `SensorGridScreen` via `ZoneDisplayConfigSection`. However, `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent`, tab 3) still renders an extra `ZoneDisplayToggleRow(R.string.zone_display_gauge)` switch below `ZoneDisplayOptionsToggleList`, creating an inconsistent duplicate UI relative to tabs 0–2 (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`).

## 2. Independent Peer Review & Verification
Audited `ZonesSettingsActivity.kt` (post-`ATT-8105` `0843e81d`), `EditSensorFieldDialog.kt`, `ZoneDisplayOptionsControls.kt`, `SettingsDataStore.kt`, `TrackingViewModel.kt`, `SensorFieldView.kt`, and test suites:
* **`ZonesSettingsActivity.kt` Tab Comparison**:
  * **Tabs 0–2 (`SettingsScreenContent`, L584–614, L693)**: Render the 4-toggle `ZoneDisplayOptionsToggleList(showGaugeToggle = false)` (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`) paired with a live `SensorFieldView` preview (`showBar = true`). No `zone_display_gauge` switch exists (removed per `REQ-TRK-046.4`).
  * **Tab 3 (`HrvDfaA1SettingsContent`, L1270–1312, L1389)**: Renders `ZoneDisplayOptionsToggleList(..., showGaugeToggle = false)` (`L1288–1300`), then appends `HorizontalDivider` (`L1301`) + `ZoneDisplayToggleRow(R.string.zone_display_gauge, displayOptions.showGauge)` (`L1303–1310`) and binds `previewFieldState.showBar = displayOptions.showGauge` (`L1389`).
* **Tile & Tab Config Verification (`EditSensorFieldDialog`, `SensorGridScreen`)**:
  * `ZoneDisplayConfigSection` (`ZoneDisplayOptionsControls.kt:312–394`) passes `showGaugeToggle = (selectedProfile == ZoneProfileTarget.HRV_DFA_A1)` (`L369`), used by `EditSensorFieldDialog.kt:537` and `SensorGridScreen.kt:1926`, persisting via `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE` (`L209, L1226, L1236, L1264`).
  * `EditSensorFieldZoneDisplayTest.kt:175–180` and `TrackingTabConfigZoneDisplayTest.kt:151–159` already verify this `showGauge` toggle and persistence.
  * In live tracking (`TrackingViewModel.kt:615–627, L869`; `SensorFieldView.kt:426`), `HRV_DFA_A1` gauge bars are gated by per-tile `config.showBar` (`isBarSupported(HRV_DFA_A1, *) == true`), making the `ZonesSettingsActivity` global toggle redundant and misleading.
* **Test Surface Impact**: `HrvDfaA1SettingsContractTest.kt:291–311` currently asserts `R.string.zone_display_gauge`, `displayOptions.showGauge`, and `showBar = displayOptions.showGauge` inside `ZonesSettingsActivity.kt` and must be updated alongside a behavioral Compose UI check (`REQ-PRO-049.1` compliant).

## 3. User Scope Grounding (ATT-1250)
* **In-Scope (`1 SP`)**: Remove `HorizontalDivider` + `ZoneDisplayToggleRow(R.string.zone_display_gauge)` (`ZonesSettingsActivity.kt:1301–1310`) from `HrvDfaA1SettingsContent`; set `previewFieldState.showBar = true` (`L1389`, matching `L693`); update `HrvDfaA1SettingsContractTest.kt` + add Compose UI verification; verify `EditSensorFieldDialog` `showGauge` tests pass.
* **Out-of-Scope**: Keep the 4-toggle `ZoneDisplayOptionsToggleList` card + live preview in all 4 tabs of `ZonesSettingsActivity.kt` (`REQ-UI-2783.3`, `REQ-UI-172`) so threshold editing retains live visual feedback; keep `ZoneDisplayConfigSection` (`ZoneDisplayOptionsControls.kt:369`) and `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE` untouched.

## 4. Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: `REQ-TRK-061.4` (`docs/requirements/req_tracking_cockpit.md:3883`) and `REQ-UI-2783.3` (`docs/requirements/req_ui_design_system.md:17548`), reconciling with `REQ-TRK-046.4` (`req_tracking_cockpit.md:2654`).
* **Historical Origin & Commit Trace**: `ATT-4841`/`ATT-4927` (`REQ-TRK-046.4`) deprecated the global `showGauge` toggle in `ZonesSettingsActivity`; `ATT-7742` (`5e30863e`) re-added `zone_display_gauge` to `HrvDfaA1SettingsContent`; `ATT-8498` (`d7dec447`) moved `ZoneDisplayOptions` (with `HRV_DFA_A1` `showGauge`) into `EditSensorFieldDialog` & `SensorGridScreen` while keeping `ZonesSettingsActivity` lines solely to avoid breaking `HrvDfaA1SettingsContractTest`.
* **Root Reason for Existing Formulation**: Interim HRV preview toggle (`ATT-7742`) before `ATT-8498` placed `ZoneDisplayOptions` in `EditSensorFieldDialog` & `SensorGridScreen`.
* **Preservation of Core Invariants**: Preserve 4-toggle `ZoneDisplayOptionsToggleList` + live `SensorFieldView` preview across all 4 `ZonesSettingsActivity` tabs, `EditSensorFieldDialog`/`SensorGridScreen` `showGauge` toggle & `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`, `ATT-8105` slider gesture isolation, `T1 >= T2 + 0.05f`, and 9-locale `strings*.xml` parity (`zone_display_gauge` stays active in `ZoneDisplayOptionsControls.kt:230`).

## Change Impact Analysis
* **Affected Requirements**: Refines `REQ-TRK-061.4` (`docs/requirements/req_tracking_cockpit.md`) and `REQ-UI-2783.3` (`docs/requirements/req_ui_design_system.md`), aligning HRV tab with `REQ-TRK-046.4`; updates `TST-TRK-061.4` (`docs/tests/tst_tracking_cockpit.md`).
* **Affected Architecture Elements**: `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent` only); `EditSensorFieldDialog`, `ZoneDisplayOptionsControls`, `SettingsDataStore`, `TrackingViewModel` unchanged.
* **Verification Impact & Test Surface**: Update `HrvDfaA1SettingsContractTest.kt` (`testHrvSettings_gaugeToggleAndPreviewBarContracts`), add Robolectric Compose UI test verifying `zone_display_gauge` absence in `HrvDfaA1SettingsContent` without `src/main` reads (`REQ-PRO-049.1`), and re-run `EditSensorFieldZoneDisplayTest` & `TrackingTabConfigZoneDisplayTest`.
* **Risk Assessment & Invariants**: **LOW** — isolated 11-line removal in `HrvDfaA1SettingsContent`; zero DataStore schema/key or threshold logic changes.

## 5. High-Level Solution
Remove `L1301–1310` (`HorizontalDivider` + `ZoneDisplayToggleRow(R.string.zone_display_gauge)`) and change `showBar = displayOptions.showGauge` (`L1389`) to `showBar = true` in `ZonesSettingsActivity.kt`, bringing `HrvDfaA1SettingsContent` to 1:1 parity with `SettingsScreenContent` (`L602–612, L693`).
