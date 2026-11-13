# Stage 3 Implementation Plan - ATT-7519: Unified 3-Zone RangeSlider & Layout Fixes

## 1. Architecture & Component Decomposition (SWE.2)
- **Target Components**:
  - `ui/activities/ZonesSettingsActivity.kt`:
    - Header: Decouple `reset_to_defaults` from top header row into the Edit Zones section header, avoiding 3-line wrap; enforce `maxLines = 1, softWrap = false`.
    - `HrvZoneBandCard`: Apply `Modifier.weight(1f)` to `zoneLabel` and `maxLines = 1, softWrap = false` to range badge to eliminate 2-line wraps. Externalize guidance strings to localized resources.
    - Unified 3-Zone `RangeSlider`: Introduce dual-thumb `RangeSlider` spanning continuous domain `0.30f..1.20f` with custom track drawn via `Canvas` partitioned into semantic segments (`TTColor.Zone4` Red $< T_2$, `TTColor.Zone3` Orange $T_2..T_1$, `TTColor.Zone2` Green $> T_1$). Enforce $T_1 \ge T_2 + 0.05f$ during dragging.
    - Fine-Tuning Steppers: Retain `HrvThresholdControlRow` as a public composable with `-` / `+` stepper buttons (0.01 step precision) and localized helper texts (`hrv_t1_aerobic_threshold_desc`, `hrv_t2_anaerobic_threshold_desc`).
  - `app/src/main/res/values*/strings_hrv.xml` (all 9 locales: `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`): Add 5 string definitions (`hrv_zone_aerobic_base_desc`, `hrv_zone_threshold_transition_desc`, `hrv_zone_anaerobic_desc`, `hrv_t1_aerobic_threshold_desc`, `hrv_t2_anaerobic_threshold_desc`) with 100% translation parity (`REQ-TRK-061.4e`).
  - `trainingtracker/zones/HrvDfaA1SettingsContractTest.kt`: Update contract tests to verify `RangeSlider`, custom track coloring, layout anti-wrapping constraints, and zero hardcoded English literals in `ZonesSettingsActivity.kt`.
  - `localization/TranslationParityTest.kt`: Verify 100% 9-language translation parity for the 5 new keys (`TST-TRK-061.4d`).
- **Scope Boundaries (ATT-1250)**: DataStore keys (`tuning_dfa_a1_threshold_*`, `hrv_dfa_a1_zone_*`), SQLite schemas, and population defaults ($0.75 / 0.50$) remain 100% invariant.

## 2. Atomic Implementation Steps
1. **Step 1 - XML String Resources Parity across 9 Locales (`REQ-TRK-061.4e`)**:
   - Add the 5 localized strings to `strings_hrv.xml` across all 9 locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
   - Validate XML resource compilation via `./gradlew processDebugResources`.
2. **Step 2 - ZonesSettingsActivity Layout Anti-Wrapping Fixes (`REQ-TRK-061.4d`)**:
   - In `ZonesSettingsActivity.kt`: In `HrvZoneBandCard`, add `Modifier.weight(1f)` to `zoneLabel` and `maxLines = 1, softWrap = false` to range badge text.
   - Relocate `reset_to_defaults` button to the `title_edit_zones` row with `maxLines = 1, softWrap = false` to eliminate the 3-line header wrap.
3. **Step 3 - Unified 3-Zone Colored Dual-Thumb RangeSlider (`REQ-TRK-061.4a`, `REQ-TRK-061.4b`)**:
   - In `ZonesSettingsActivity.kt`, construct `HrvDfaA1RangeSlider` composable wrapping Material 3 `RangeSlider` (`0.30f..1.20f`) with custom track drawing `TTColor.Zone4` ($< T_2$), `TTColor.Zone3` ($T_2..T_1$), and `TTColor.Zone2` ($> T_1$).
   - Enforce invariant $T_1 \ge T_2 + 0.05f$ on value change with step rounding.
4. **Step 4 - Stepper Rows & String Resource Externalization (`REQ-TRK-061.4c`, `REQ-TRK-061.4e`)**:
   - Retain public `HrvThresholdControlRow` with stepper buttons and connect localized helper text strings.
   - Replace all 5 hardcoded English strings in `HrvDfaA1SettingsContent` with `stringResource(...)`.
5. **Step 5 - Contract Tests & Verification Suite (`TST-TRK-061.4a`–`e`)**:
   - Update `HrvDfaA1SettingsContractTest.kt` to assert `RangeSlider`, track colors, layout wrapping constraints, and absence of hardcoded English strings.
   - Run `TranslationParityTest`, `HrvDfaA1SettingsContractTest`, `TuningPreferencesDataStoreDfaA1Test`, and `verify_requirement_governance.py`.

## 3. Invariant Protection & System Properties
- **DataStore & Invariant Stability**: Defaults ($0.75 / 0.50$) and gap invariant $T_1 \ge T_2 + 0.05f$ strictly enforced in both slider and steppers.
- **API Stability**: Public composables `HrvDfaA1SettingsContent`, `HrvZoneBandCard`, `HrvThresholdControlRow` remain intact.
- **Rule 71 Compliance**: Semantic zone colors `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4` used on track and badges.

## 4. Verification & Testing Strategy
- Automated tests: `TranslationParityTest` (`TST-TRK-061.4d`), `HrvDfaA1SettingsContractTest` (`TST-TRK-061.4a`–`c`), targeted unit suite (`TST-TRK-061.4e`).
- Governance: `python3 tools/verify_requirement_governance.py`.

## 5. UI Consistency & Design Guidelines Compliance
- Eliminates visual scale inversion with single unified `0.30..1.20` track.
- Adheres to `design_guidelines.md`: Single-line action labels (`maxLines = 1`) on German locales, zero text clipping.

## 6. Living Documentation Updates
- Update `docs/requirements/req_tracking_cockpit.md` (`REQ-TRK-061.4`) and `docs/tests/tst_tracking_cockpit.md` (`TST-TRK-061.4`) to `[Verified]` in Stage 5.
