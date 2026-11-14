# Stage 2: Requirement & Test Specification - ATT-7738: Bike Radar Expert Settings Segmented Button Labels

**Ticket**: [ATT-7738](https://rainerblind.atlassian.net/browse/ATT-7738)  
**Sub-task**: Pending (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-1286](https://rainerblind.atlassian.net/browse/ATT-1286) (*Cycling Rearview Radar*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.6`  
**Requirement Mapping**: `REQ-TRK-063.7`, `REQ-TRK-073.4`, `REQ-CON-031.5`  
**Test Spec ID**: `TST-TRK-063.7`, `TST-TRK-073.4`  
**Branch**: `feat/ATT-7738`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-14  

---

## 1. Requirement Specification (REQ-TRK-063.7 & REQ-TRK-073.4 Refinement)

### 1.1 Problem Statement & Rationale
In `RadarSettingsSection.kt` (Bike Radar Expert Settings), segmented button labels for `Audio-Warnmodus` and `Warnschwelle für Überholabstand` ellipsize on standard phone widths (360–412dp) due to verbose string phrasing. Additionally, the heading `Rücklicht-Steuerung` appears twice back-to-back in German/Dutch, and the summary text under `Warnschwelle für Überholabstand` renders bold like a title instead of `bodySmall`.

### 1.2 Attributes
- **Priority**: Medium
- **Type**: Functional / UI
- **Verification Method**: Unit Test / Visual Inspection

### 1.3 Functional & Architectural Requirements
1. `strings_radar.xml` across all 9 locales SHALL provide concise labels for `radar_alert_mode_*` (`Alle`, `Annäherung`, `Nur schnell`, `Stumm` in DE; `All`, `Approach`, `Fast Only`, `Muted` in EN).
2. `strings_radar.xml` across all 9 locales SHALL provide concise distance labels for `radar_close_pass_threshold_*` (`1,0 m (3,3 ft)`, `1,5 m (5,0 ft)`, `2,0 m (6,6 ft)` in DE; `1.0 m (3.3 ft)` in EN/JA).
3. `strings_radar.xml` in German and Dutch SHALL disambiguate `radar_light_control_mode_title` (`Steuerungsmodus` in DE; `Bedieningsmodus` in NL) to prevent duplicate back-to-back headings.
4. `RadarSettingsSection.kt` SHALL pass `radar_close_pass_threshold_title` as `title` and `radar_close_pass_threshold_summary` as `helperText` to `TuningSegmentedButtonRow`, removing redundant standalone title.
5. `RadarSettingsSection.kt` SHALL display dynamic `helperText` under `Audio-Warnmodus` describing the active mode.
6. `TuningFormControls.kt` SHALL use `PaddingValues(horizontal = 4.dp, vertical = 0.dp)` for `SegmentedButton` content padding in `TuningSegmentedButtonRow`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Non-Ellipsized Audio Mode Labels)**:
  * *Given* `RadarSettingsSection` rendered in German on a 360dp screen,
  * *When* `Audio-Warnmodus` is displayed,
  * *Then* all 4 button labels (`Alle`, `Annäherung`, `Nur schnell`, `Stumm`) fit on a single line without ellipsis.
* **Criterion 2 (Non-Ellipsized Close-Pass Threshold Labels & Helper Text)**:
  * *Given* `RadarSettingsSection` rendered,
  * *When* `Warnschwelle für Überholabstand` is displayed,
  * *Then* all 3 button labels fit without ellipsis, and the helper text is rendered in `bodySmall`.
* **Criterion 3 (Heading Disambiguation)**:
  * *Given* `RadarSettingsSection` rendered in German or Dutch,
  * *When* `Rücklicht-Steuerung` is displayed,
  * *Then* the section heading and the control title are distinct (`Rücklicht-Steuerung` followed by `Steuerungsmodus`).

### 1.5 System Invariants
- 100% 9-language translation parity maintained.
- Zero changes to DataStore preference keys, enum values, or float thresholds.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-063.7`, `REQ-TRK-073.4`, `REQ-CON-031.5`
* **Historical Origin & Commit Trace**: Added in ATT-7536 (Sprint 2026-46.5).
* **Root Reason for Existing Formulation**: Introduced M3 `TuningSegmentedButtonRow` controls, but initial strings included full descriptive sentences directly in button labels.
* **Preservation of Core Invariants**: User configuration options and functionality remain unchanged while polishing visual layout and typography.

---

## 2. Test Specification

### Test Case 1: `testRadarSettingsStrings_alertModeLabelsFitBudget` (`TST-TRK-063.7a`)
* **Scope**: Unit Test (`RadarSettingsSectionTest.kt`)
* **Assertions**:
  - `radar_alert_mode_*` string character lengths in German $\le 12$ characters each.

### Test Case 2: `testRadarSettingsStrings_closePassLabelsFitBudget` (`TST-TRK-073.4a`)
* **Scope**: Unit Test (`RadarSettingsSectionTest.kt`)
* **Assertions**:
  - `radar_close_pass_threshold_*` strings $\le 16$ characters across all locales.

### Test Case 3: `testRadarSettingsStrings_deAndNlLightTitlesDisambiguated` (`TST-CON-031.5e`)
* **Scope**: Unit Test (`RadarSettingsSectionTest.kt`)
* **Assertions**:
  - `radar_light_settings_title != radar_light_control_mode_title` in DE and NL.
