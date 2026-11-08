# Stage 3: Implementation Plan - ATT-5875: Display & Expert Settings Polish

**Ticket**: [ATT-5875](https://atrainingtracker.atlassian.net/browse/ATT-5875) | **Target Release**: `V5.0.0` | **Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5875`  
**Requirement Mapping**: `REQ-UI-472` | **Test Mapping**: `TST-UI-472` | **Author**: AI Agent 1 (Implementer, `subagent_pro`)  

---

## 1. Problem Description & Background
User walkthrough and visual inspection revealed five terminology, localization, and formatting defects:
1. Internal developer string `'Control Tracking'` exposed in `cockpit_theme_description` across all locales.
2. German category header `tuning_cat_aftermath` uses unlocalized developer jargon `Aftermath & Profil-Analytik`.
3. Warning banner in `TuningWarningNotice` repeats TopAppBar header `Experteneinstellungen`.
4. Sensor search summary in German displays uninflected adjective `Aktiv Autosuche` instead of `Aktive Autosuche`.
5. Accordion subtitles in `AdvancedTuningAccordion.kt` display hardcoded English tokens (`Flat:`, `Steep:`, `Alt:`, `Speed:`, `End:`, `Apex:`, `Dist:`) and format small metric tolerances ($\le 1000\text{ m}$) as fractional kilometers (`0,200km`, `0,400km`).

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-472` (*Display & Expert Settings Terminology, Subtitle Localization & Warning Deduplication*)
  * `REQ-UI-472.1`: Replace `'Control Tracking'` with localized tab title (`'Start'`, `'Inicio'`, etc.) in `cockpit_theme_description`.
  * `REQ-UI-472.2`: Localize `tuning_cat_aftermath` as `Nachbereitung & Profil-Analytik` in German.
  * `REQ-UI-472.3`: Deduplicate warning card header `advanced_tuning_warning_title` to concise advisory title (`Hinweis` / `Notice`).
  * `REQ-UI-472.4`: Inflect German sensor search adjective `tuning_sensor_search_active` to `Aktive` ("Aktive Autosuche").
  * `REQ-UI-472.5`: Localize accordion subtitles in `TuningSubtitleFormatter` and format metric tolerances $\le 1000\text{ m}$ as integer meters (`"${val.roundToInt()} m"`).
* **Test Mapping**: `TST-UI-472` (`TST-UI-472.1` through `TST-UI-472.5`).

---

## 3. System Invariants & Preserved Behavior
1. **Backward Compatibility**: `TuningSubtitleFormatter` keeps optional `context: Context? = null` with English fallbacks so existing unit tests without context continue to pass.
2. **Contract Preservation**: `TuningWarningNotice` continues binding `R.string.advanced_tuning_warning_title` per `AdvancedTuningVisualContractTest`.
3. **9-Language Parity**: All new and modified string keys verified via `TranslationParityTest`.
4. **UI Consistency**: Advisory card preserves M3 `surfaceVariant.copy(alpha = 0.5f)` styling and `Icons.Default.Tune` icon from `ATT-5819`.

---

## 4. Proposed Architectural Changes
* **Component 1: String Resources (`res/values*/strings.xml`)**: Refine `cockpit_theme_description`, `tuning_cat_aftermath`, `advanced_tuning_warning_title`, and `tuning_sensor_search_active`. Add short label keys `tuning_battery_saver_flat_short`, `tuning_battery_saver_steep_short`, `tuning_sensors_alt_short`, `tuning_sensors_speed_short`, `tuning_cluster_end_short`, `tuning_cluster_apex_short`, `tuning_cluster_dist_short`, and `tuning_cluster_alt_short` across all 9 locales.
* **Component 2: Subtitle Formatter (`AdvancedTuningAccordion.kt`)**: Add optional `context: Context? = null` to `formatBatterySaverSubtitle()` and `formatSensorsGpsSubtitle()`; format metric tolerances $\le 1000\text{ m}$ as integer meters (`"${val.roundToInt()} m"`) with localized labels in `formatClusterTuningSubtitle()`.
* **Component 3: Composable Call Sites (`AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`)**: Pass `context = context` to `formatBatterySaverSubtitle()` and `formatSensorsGpsSubtitle()`.
* **UI Consistency**: Reuses existing `TuningAccordionSection` and `TuningWarningNotice` (`ui/settings/tuning/`). Preserves M3 `surfaceVariant` and typography (`titleSmall`, `bodySmall`). No new one-off styles.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Step 1: Subtitle Formatter Updates**: Update `TuningSubtitleFormatter` in `AdvancedTuningAccordion.kt` with localized labels and integer meter metric formatting for $\le 1000\text{ m}$.
* **Step 2: Composable Threading**: Pass `context` to `formatBatterySaverSubtitle()` and `formatSensorsGpsSubtitle()` in `AdvancedTuningScreen.kt` and `AdvancedTuningDialog.kt`.
* **Step 3: Unit & Contract Tests**: Update `AdvancedTuningAccordionTest.kt` for localized subtitles and meter formatting; create `DisplayAndExpertSettingsLocalizationContractTest.kt` to enforce terminology, declension, and contract bounds.
* **Step 4: Verification & Governance**: Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.localization.*"` and `python3 tools/verify_requirement_governance.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted unit tests (`ui.settings.tuning.*`, `localization.*`), requirement governance check, and visual inspection.
* **Rollback**: Clean git revert of commit on `feature/ATT-5875`.
