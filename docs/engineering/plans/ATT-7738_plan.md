# Stage 3: Implementation Plan - ATT-7738: Bike Radar Expert Settings Segmented Button Labels

**Ticket**: [ATT-7738](https://rainerblind.atlassian.net/browse/ATT-7738)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-TRK-063.7`, `REQ-TRK-073.4`, `REQ-CON-031.5`  
**Test Mapping**: `TST-TRK-063.7`, `TST-TRK-073.4`  
**Branch**: `feat/ATT-7738`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Problem Description & Background
During physical Sprint 2026-46.5 testing of `RadarSettingsSection` on Pixel 10 in German, segmented buttons in `Audio-Warnmodus` and `Warnschwelle für Überholabstand` were ellipsized due to verbose string resource labels. Furthermore, the `Rücklicht-Steuerung` heading was duplicated back-to-back, and the close-pass summary text was rendered bold like a title instead of `bodySmall`. This plan details construction steps to resolve these visual defects.

---

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-TRK-063.7` (Audio alert settings), `REQ-TRK-073.4` (Close-pass threshold), `REQ-CON-031.5` (Tail light settings UI)
* **Test Mappings**: `TST-TRK-063.7`, `TST-TRK-073.4` (`RadarSettingsSectionTest.kt`, `TranslationParityTest.kt`)

---

## 3. System Invariants & Preserved Behavior
1. **100% 9-Language Localization Parity**: `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt` must maintain exact string key and format parity.
2. **DataStore Invariance**: No changes to preference keys, enum values, default settings, or threshold floating-point values.
3. **Design Guidelines Compliance**: Adherence to `docs/design_guidelines.md` §1.4 (`TuningSegmentedButtonRow`) and §5.13 (concise localized labels).
4. **Code Lock**: Verification of `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>` prior to editing code.

---

## 4. Proposed Architectural & UI Changes

### Component 1: `app/src/main/res/values*/strings_radar.xml` (all 9 locales)
- Shorten `radar_alert_mode_*` labels (`Alle`, `Annäherung`, `Nur schnell`, `Stumm` in DE; `All`, `Approach`, `Fast Only`, `Muted` in EN).
- Add dynamic helper text strings for each alert mode (`radar_alert_mode_all_desc`, `radar_alert_mode_approach_fast_desc`, `radar_alert_mode_fast_only_desc`, `radar_alert_mode_muted_desc`).
- Shorten `radar_close_pass_threshold_*` distance labels to remove inline commentary (`1,0 m (3,3 ft)`, `1,5 m (5,0 ft)`, `2,0 m (6,6 ft)` in DE; `1.0 m (3.3 ft)` in EN/JA).
- Disambiguate `radar_light_control_mode_title` in German (`Steuerungsmodus`) and Dutch (`Bedieningsmodus`).

### Component 2: `RadarSettingsSection.kt`
- Under `Audio-Warnmodus`, pass dynamic `helperText` explaining the selected alert mode.
- In Section 4 (Close-Pass Threshold), remove the standalone `Text(titleMedium)` heading and pass `title = stringResource(R.string.radar_close_pass_threshold_title)` and `helperText = stringResource(R.string.radar_close_pass_threshold_summary)` to `TuningSegmentedButtonRow`.

### Component 3: `TuningFormControls.kt`
- Set `contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)` on `SegmentedButton` inside `TuningSegmentedButtonRow` for extra text breathing room.

### UI Consistency (Rule 23)
- **Reference Component**: `RadarSettingsSection.kt` and `TuningFormControls.kt`.
- **Reused Components**: `TuningSegmentedButtonRow`.
- **Theme Tokens**: `MaterialTheme.typography.bodySmall`, `MaterialTheme.colorScheme.onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
- Run `python3 tools/jira_util.py check-gate <IMPL_PLAN_KEY>`.

### Step 2: String Resources Update (9 Locales)
- Update `strings_radar.xml` in `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

### Step 3: Composable Updates
- Update `RadarSettingsSection.kt` and `TuningFormControls.kt`.

### Step 4: Unit Test Authoring & Clean-Room Verification
- Update `RadarSettingsSectionTest.kt` with string length budget and disambiguation tests.
- Run targeted tests:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.radar.RadarSettingsSectionTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningFormControlsTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`
- Verify requirement governance: `python3 tools/verify_requirement_governance.py`.
