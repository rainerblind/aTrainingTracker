# Stage 1 Analysis: ATT-7738 - Bike Radar Expert Settings: Fix Ellipsized Segmented Button Labels

## 1. Problem Description & Operational Context
During physical Sprint 2026-46.5 review of ATT-7536 on Pixel 10 in German, several UI layout and typography issues were identified in `RadarSettingsSection.kt` (*Experten-Einstellungen -> Bike Radar*):
1. **Audio-Warnmodus**: 4-button `TuningSegmentedButtonRow` ellipsizes 3 of 4 button labels (`Alle Warn...`, `Annäheru...`, `Nur schne...`, `Stumm`).
2. **Warnschwelle für Überholabstand**: 3-button `TuningSegmentedButtonRow` ellipsizes all 3 button labels (`1,0 m (3,3 ft - ...`, `1,5 m (5,0 ft - ...`, `2,0 m (6,6 ft - ...`).
3. **Minor Polish**:
   - `Rücklicht-Steuerung` section heading appears twice back-to-back.
   - The summary helper text under `Warnschwelle für Überholabstand` is rendered bold like a title instead of `bodySmall`.

---

## 2. Forensic Root Cause Analysis (RCA)
1. **Excessive Label Character Length in Segmented Buttons**:
   - In `Audio-Warnmodus`, 4 buttons on a 360–412dp screen have ~75–85dp width each. German labels like `"Annäherung & Schnell"` (20 chars) and `"Nur schnelle Fahrzeuge"` (22 chars) exceed available single-line width, forcing truncation via `TextOverflow.Ellipsis`.
   - In `Warnschwelle für Überholabstand`, 3 buttons have ~100–110dp width each. Appending verbose regional notes like `"(3,3 ft - UK/US)"` (23 chars) and `"(5,0 ft - Innerorts / StVO)"` (34 chars) overflows single-line capacity.
2. **Duplicated Heading in German & Dutch**:
   - In `values-de/strings_radar.xml`, both `radar_light_settings_title` and `radar_light_control_mode_title` are identically translated as `"Rücklicht-Steuerung"`. In `values-nl`, both are `"Achterlichtbediening"`.
3. **Misplaced Summary as Control Title**:
   - In `RadarSettingsSection.kt:463-474`, a standalone `Text(titleMedium)` renders `"Warnschwelle für Überholabstand"`, and then `TuningSegmentedButtonRow` passes `radar_close_pass_threshold_summary` as its `title` parameter, causing the helper text to render in `bodyMedium` with `FontWeight.SemiBold`.

---

## 3. Independent Peer Review & Verification
- Inspected `radar_settings_1.png` and `radar_settings_3.png` via `read_file`: Confirmed truncation of buttons and duplicate/bold typography.
- Inspected `TuningFormControls.kt`: `TuningSegmentedButtonRow` provides `helperText: String? = null` styled with `bodySmall` and `onSurfaceVariant`.
- Inspected `strings_radar.xml` across all 9 language folders to verify translation parity and line budgeting.

---

## 4. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Shorten segmented button labels for `RadarAudioAlertMode` and close-pass threshold across all 9 languages so they fit comfortably on 360–412dp screens without ellipsizing.
  * Pass close-pass summary as `helperText` rather than `title` in `TuningSegmentedButtonRow`, removing redundant title.
  * Disambiguate `radar_light_control_mode_title` in German (`"Steuerungsmodus"`) and Dutch (`"Bedieningsmodus"`).
  * Reduce `SegmentedButton` content padding from 8dp to 4dp in `TuningFormControls.kt` for compact segmented rows.
* **Out-of-Scope Non-Goals**:
  * No modifications to underlying DataStore preference keys, audio playback logic, or threshold values.

---

## 5. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-TRK-063.6` (Audio alert settings), `REQ-TRK-073.4` (Close-pass threshold), `REQ-CON-031.5` (Tail light settings UI).
* **Historical Origin & Commit Trace**: Added in ATT-7536 (Sprint 2026-46.5).
* **Root Reason for Existing Formulation**: Introduced `TuningSegmentedButtonRow` for Expert Settings, but verbose regional descriptions were embedded directly in button labels.
* **Preservation of Core Invariants**: User choices, DataStore serialization, and audio/threshold values remain strictly unchanged.

---

## 6. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-TRK-063.6`, `REQ-TRK-073.4`, `REQ-CON-031.5` in `docs/requirements/req_tracking_cockpit.md`.
* **Affected Architecture Elements**: `RadarSettingsSection.kt`, `TuningFormControls.kt`, `strings_radar.xml` across 9 locales.
* **Verification Impact & Test Surface**: `RadarSettingsSectionTest.kt`, `TuningFormControlsTest.kt`, visual rendering inspection.
* **Risk Assessment & Invariants**: Low risk (cosmetic/text layout polish, 1 SP). Zero impact on tracking, audio synthesis, or database schema.

---

## 7. Architectural Strategy & High-Level Solution
* Update strings in all 9 locales:
  - `radar_alert_mode_*`: Shorten labels (`Alle`, `Annäherung`, `Nur schnell`, `Stumm`).
  - `radar_close_pass_threshold_*`: Shorten labels to distance (`1,0 m (3,3 ft)`, `1,5 m (5,0 ft)`, `2,0 m (6,6 ft)`).
  - DE/NL light control mode title disambiguation.
* In `RadarSettingsSection.kt`:
  - Pass `radar_close_pass_threshold_title` as `title` and `radar_close_pass_threshold_summary` as `helperText` to `TuningSegmentedButtonRow`.
  - Add dynamic `helperText` for active `RadarAudioAlertMode`.
* In `TuningFormControls.kt`: Set `contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)`.

---

## 8. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% 9-language translation parity maintained across all modified strings.
  2. Zero changes to preference keys or numerical threshold values.
  3. Single-line button label contract preserved.
* **Risk Rating**: **LOW** (1 SP layout and string polish).
