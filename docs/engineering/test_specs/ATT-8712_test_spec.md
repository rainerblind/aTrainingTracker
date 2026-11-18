# Stage 2: Requirement & Test Specification - ATT-8712

**DOC-ID**: `DOC-TST-ATT-8712` | **Ticket**: [ATT-8712](https://rainerblind.atlassian.net/browse/ATT-8712)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8712` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-TRK-087` (refining `REQ-TRK-061.4`, `REQ-UI-2783.3`; aligning with `REQ-TRK-046.4`) | **Test Spec ID**: `TST-TRK-087`  

---

## 1. Requirement Specification (`REQ-TRK-087` `[Planned]`)

### 1.1 Attributes & Rationale
- **Priority**: Low | **Type**: Functional | **Verification Method**: Unit
- **Implementation**: `ZonesSettingsActivity.kt` (`HrvDfaA1SettingsContent` only)
- **Rationale**: `ATT-8498` placed `ZoneDisplayOptions` (including `R.string.zone_display_gauge` for `HRV_DFA_A1`) inside `EditSensorFieldDialog` and `SensorGridScreen` via `ZoneDisplayConfigSection` (`SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`). `HrvDfaA1SettingsContent` (tab 3) still renders a redundant `ZoneDisplayToggleRow(R.string.zone_display_gauge)` inconsistent with tabs 0–2 (`REQ-TRK-046.4`).

### 1.2 Functional & Architectural Requirements
1. **HRV Tab Display Options Parity (`REQ-TRK-087.1`)**:
   - `HrvDfaA1SettingsContent` (`ZonesSettingsActivity.kt`) SHALL remove the `HorizontalDivider` and `ZoneDisplayToggleRow(R.string.zone_display_gauge, ...)` below `ZoneDisplayOptionsToggleList(..., showGaugeToggle = false)`, rendering strictly the 4 standard toggles (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`) in 1:1 parity with `SettingsScreenContent` (tabs 0–2) and `REQ-TRK-046.4`.
   - `HrvDfaA1SettingsContent` SHALL set `previewFieldState.showBar = true` unconditionally (matching `SettingsScreenContent`), keeping the 3-segment live `SensorFieldView` gauge preview visible while editing thresholds.
2. **Tile/Tab Config Preservation & Behavioral Compose Verification (`REQ-TRK-087.2`)**:
   - `ZoneDisplayConfigSection` (`ZoneDisplayOptionsControls.kt`, used by `EditSensorFieldDialog` and `SensorGridScreen`) SHALL preserve `showGaugeToggle = (selectedProfile == ZoneProfileTarget.HRV_DFA_A1)` and `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`.
   - `Localization Parity`: **N/A** — zero UI strings added, modified, or removed; `R.string.zone_display_gauge` stays active in `ZoneDisplayOptionsControls.kt:230` across all 9 locales (`values`..`values-pt`).

### 1.3 Acceptance Criteria (Given-When-Then) & Invariants
- *Given* `ZoneSettingsScreen` on tab 3 (`HRV (DFA α1)`), *When* `HrvDfaA1SettingsContent` renders, *Then* the 4 `ZoneDisplayOptionsToggleList` switches (`zone_display_background`, `zone_display_left_bar`, `zone_display_right_bar`, `zone_display_text_color`) are displayed, `R.string.zone_display_gauge` (*Zonen-Balken*) does NOT exist, and `SensorFieldView` preview renders with `showBar = true` even when `displayOptions.showGauge == false`.
- *Given* `EditSensorFieldDialog` or `SensorGridScreen` (`ScreenMode.CONFIGURATION`) for `HRV_DFA_A1`, *When* rendered, *Then* `R.string.zone_display_gauge` remains displayed and persists to `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE`.
- **Invariants**: 4-toggle `ZoneDisplayOptionsToggleList` across all 4 tabs; `previewFieldState.showBar = true`; `ZoneDisplayConfigSection` & `HRV_DFA_A1_ZONE_SHOW_GAUGE` untouched; `ATT-8105` slider gesture isolation & $T_1 \ge T_2 + 0.05f$ intact; `REQ-PRO-049.1` zero `src/main` reads in new/modified Compose UI tests.

### 1.4 Chesterton's Fence Audit (`REQ-PRO-022`)
- **Original Requirement ID & Target**: `REQ-TRK-087` refining `REQ-TRK-061.4` (`req_tracking_cockpit.md`) & `REQ-UI-2783.3` (`req_ui_design_system.md`), reconciling with `REQ-TRK-046.4` (`ZonesSettingsActivity.kt`).
- **Historical Origin & Commit Trace**: `ATT-4841`/`ATT-4927` (`REQ-TRK-046.4`) removed global `showGauge` from `ZonesSettingsActivity`; `ATT-7742` (`5e30863e`) added `zone_display_gauge` to `HrvDfaA1SettingsContent`; `ATT-8498` (`d7dec447`) moved `ZoneDisplayOptions` into `EditSensorFieldDialog` & `SensorGridScreen`, leaving `ZonesSettingsActivity` lines solely for `HrvDfaA1SettingsContractTest`.
- **Root Reason for Existing Formulation**: Interim HRV toggle (`ATT-7742`) prior to `ATT-8498` tile/tab placement.
- **Preservation of Core Invariants**: 4-toggle card + live `SensorFieldView` preview (`showBar = true`), `EditSensorFieldDialog`/`SensorGridScreen` `showGauge`, `ATT-8105` gesture isolation, $T_1 \ge T_2 + 0.05f$, and 9-locale parity stay intact.

---

## 2. Test Specification (`TST-TRK-087` `[Planned]`)
- **`TST-TRK-087.1` (Behavioral Compose UI Test via `createComposeRule()`, zero `src/main` reads per `REQ-PRO-049.1`)**:
  - Render `HrvDfaA1SettingsContent` (and/or `ZoneSettingsScreen` on `page == 3`) in Robolectric with `displayOptions = ZoneDisplayOptions(showGauge = false)`. Assert `zone_display_title` and the 4 standard `ZoneDisplayOptionsToggleList` toggles are displayed, `R.string.zone_display_gauge` `assertDoesNotExist()`, and the live `SensorFieldView` preview renders the 3-segment gauge bar (`showBar = true`). Inspect Light/Dark `Rule 8.5` PNG via `read_file`.
- **`TST-TRK-087.2` (Contract & Tile/Tab Config Regression)**:
  - Update `HrvDfaA1SettingsContractTest.testHrvSettings_gaugeToggleAndPreviewBarContracts` to assert absence of `R.string.zone_display_gauge` / `displayOptions.showGauge` and `showBar = true` (preserving `app/source-reading-baseline.txt` debt ceiling `105`), and run `EditSensorFieldZoneDisplayTest` + `TrackingTabConfigZoneDisplayTest` (9-locale strings unchanged: **N/A**).

---

## 3. Traceability Matrix

| Test Case | Scope | Target Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-TRK-087.1` | Compose UI (`createComposeRule()`) + `Rule 8.5` | `HrvDfaA1SettingsContent` (no `zone_display_gauge`, `showBar = true`) | `REQ-TRK-087.1`, `REQ-TRK-087.2`, `REQ-TRK-046.4` | Planned |
| `TST-TRK-087.2` | Contract & Tile/Tab Regression | `HrvDfaA1SettingsContractTest`, `EditSensorFieldZoneDisplayTest`, `TrackingTabConfigZoneDisplayTest` | `REQ-TRK-087.2`, `REQ-TRK-061.4`, `REQ-UI-2783.3` | Planned |
