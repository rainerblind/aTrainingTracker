# Stage 3 Implementation Plan: ATT-8712 - Remove 'Zonen-Balken' Switch from HRV Tab in ZonesSettingsActivity

**DOC-ID**: `DOC-PLN-ATT-8712` | **Ticket**: [ATT-8712](https://rainerblind.atlassian.net/browse/ATT-8712)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*) | **Target Release**: `V5.0.0`  
**Sprint**: `sprint/2026-11-18_01_Früh` | **Branch**: `feature/ATT-8712` | **Author**: `--as agent1`  
**Requirement Mapping**: `REQ-TRK-087` (`REQ-TRK-087.1`, `REQ-TRK-087.2`; refines `REQ-TRK-061.4`, `REQ-UI-2783.3`; aligns with `REQ-TRK-046.4`) | **Test Mapping**: `TST-TRK-087` (`TST-TRK-087.1`, `TST-TRK-087.2`)

---

## 1. Architectural Scope & SWE.2/SWE.3 Impact
* **UI Layer (`ZonesSettingsActivity.kt`)**: Remove only the redundant `Zonen-Balken` (`R.string.zone_display_gauge`) row in `HrvDfaA1SettingsContent` (tab 3) and hardcode `previewFieldState.showBar = true`, matching `SettingsScreenContent` (tabs 0–2).
* **SWE.2 / SWE.3 Docs**: `N/A` — no new subsystem, service, repository, SQLite schema, or BLE/ANT+ state contract (`docs/architecture.md` and `detailed_design/*` untouched).
* **UI Consistency (`docs/design_guidelines.md` §3.2, §4, §5.1, §5.4)**:
  * *Reference & Reused Components*: `SettingsScreenContent` (`ZonesSettingsActivity.kt:584–614, 693`), `ZoneDisplayOptionsToggleList(showGaugeToggle = false)`, `ZoneDisplayToggleRow`, `SensorFieldView`, `ATrainingTrackerTheme`.
  * *Theme Tokens & Strings*: Unchanged (`MaterialTheme.colorScheme.*`, `TTColor.Zone2/3/4`); zero new/modified strings (`R.string.zone_display_gauge` stays active in `ZoneDisplayOptionsControls.kt:230` across 9 locales).

## 2. System Invariants & Constraints
1. **4-Tab Parity**: All 4 tabs in `ZonesSettingsActivity.kt` keep the 4-toggle `ZoneDisplayOptionsToggleList` (`showBackground`, `showLeftBar`, `showRightBar`, `showTextColor`) + live `SensorFieldView` preview (`showBar = true`).
2. **Tile & Tab Config Preserved**: `ZoneDisplayConfigSection` (`ZoneDisplayOptionsControls.kt:369`, `EditSensorFieldDialog.kt:537`, `SensorGridScreen.kt:1926`) and `SettingsDataStore.HRV_DFA_A1_ZONE_SHOW_GAUGE` remain untouched.
3. **HRV Tab Invariants**: `ATT-8105` `HrvDfaA1RangeSlider` gesture isolation (`userScrollEnabled`) and $T_1 \ge T_2 + 0.05f$ gap remain intact.
4. **Test Governance (`REQ-PRO-049.1` & `REQ-PRO-049.3`)**: New Compose UI checks have zero `src/main` reads; updating `HrvDfaA1SettingsContractTest` preserves its existing `resolveActivityFile()` read so `app/source-reading-baseline.txt` (`#109`) remains exact at `baseline.size == 105` (`SourceTextReadingAuditContractTest`).

## 3. Step-by-Step Implementation Sequence (Stage 4)
### Step 1: Remove `zone_display_gauge` Switch & Bind `showBar = true` (`REQ-TRK-087.1`)
* **Gate Lock (`Rule 4.1`)**: Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_SUBTASK_KEY>` and confirm `GATE_PASSED` before editing `app/src/`.
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt`
* **Edits**: In `HrvDfaA1SettingsContent`: remove `HorizontalDivider(...)` + `ZoneDisplayToggleRow(label = stringResource(R.string.zone_display_gauge), checked = displayOptions.showGauge, ...)` (`L1301–1310`); change `showBar = displayOptions.showGauge` (`L1389`) to `showBar = true` (matching `SettingsScreenContent` `L693`).

### Step 2: Behavioral Compose UI Test, `Rule 8.5` PNG & Contract Update (`TST-TRK-087.1`, `TST-TRK-087.2`)
* **Files**:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1RangeSliderGestureTest.kt` (zero `src/main` reads, `@GraphicsMode(NATIVE)`): add `createComposeRule()` test rendering `HrvDfaA1SettingsContent` with `displayOptions.showGauge = false`, asserting `zone_display_title` + 4 standard toggles are displayed, `R.string.zone_display_gauge` (`assertDoesNotExist()`), and `zone_gauge_bar_container` (`ZoneGaugeBar`) is displayed (`showBar = true`); capture Light/Dark PNG to `docs/engineering/walkthroughs/img/ATT-8712_hrv_zone_display.png` and inspect via `read_file` (`Rule 8.5`).
  * `app/src/test/java/com/atrainingtracker/trainingtracker/zones/HrvDfaA1SettingsContractTest.kt` (`L291–311`): update `testHrvSettings_gaugeToggleAndPreviewBarContracts` to assert `!content.contains("R.string.zone_display_gauge")`, `!content.contains("displayOptions.showGauge")`, and `content.contains("showBar = true")` (preserving `resolveActivityFile()` so `baseline == 105` passes).
* **Tier-1 Command**:
  `./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1RangeSliderGestureTest" --tests "com.atrainingtracker.trainingtracker.zones.HrvDfaA1SettingsContractTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldZoneDisplayTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingTabConfigZoneDisplayTest" --tests "com.atrainingtracker.trainingtracker.build.SourceTextReadingAuditContractTest"`

## 4. Verification & Rollback Plan
* **Verification**: Tier-1 unit/Robolectric Compose + contract tests above; `Rule 8.5` PNG `read_file` inspection; `emulator-5554` UI check in Stage 5; `python3 tools/verify_requirement_governance.py` exit `0`.
* **Rollback**: Isolated to `feature/ATT-8712` (`sprint/2026-11-18_01_Früh` unaffected until Stage 6 `PASS`).
