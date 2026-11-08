# Stage 1 Analysis: ATT-5875 - Display & Expert Settings Terminology, Subtitle Formatting & Warning Deduplication

**Ticket**: [ATT-5875](https://atrainingtracker.atlassian.net/browse/ATT-5875)  
**Parent Epic**: [ATT-5909](https://atrainingtracker.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5875`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-08  

---

## 1. Problem Statement & Motivation
* **Internal Developer Jargon in German UI**:
  * In `Einstellungen -> Display` (`settings_04_display.png`), `cockpit_theme_description` states: `Das restliche Menü und 'Control Tracking' behalten das Standarddesign.` The tab is labeled `Start` in user-facing UI; exposing developer term `'Control Tracking'` violates terminology standards.
  * In `Experten-Einstellungen` (`round4_04_experten_bottom.png`), the bottom accordion section is titled `Aftermath & Profil-Analytik`. `Aftermath` is an internal code keyword that must be localized in German (`Nachbereitung & Profil-Analytik` or `Auswertung & Profil-Analytik`).
* **Untranslated Summary Fragments & Kilometer Formatting in Accordions**:
  * `AMOLED-Energiesparmodus` summary displays hardcoded English labels `Flat: 2,0%, Steep: 5,0%` (`settings_05_experten.png`).
  * `Sensoren, GPS & Filter` summary displays hardcoded English labels `Alt: 21s, Speed: 0,5 m/s`.
  * `Routen- & Workout-Clustering` summary displays English abbreviations `End: 0,200km · Apex: 0,400km · Dist: 20% · Alt: 0,400km`, incorrectly formatting short meter tolerances (`200 m`, `400 m`) as `0,200km` / `0,400km`.
  * `Sensorsuche & Verhalten` summary displays broken grammar: `3 Suchrunden, Aktiv Autosuche` instead of `Aktive Autosuche`.
* **Redundant Heading in Warning Card**: In `Experten-Einstellungen` (`settings_05_experten.png`), the TopAppBar title is `Experten-Einstellungen`, and the advisory banner immediately repeats `Experteneinstellungen`.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA**:
  1. *Resource String Leak*: `cockpit_theme_description` hardcoded `'Control Tracking'` across all 9 locales. `tuning_cat_aftermath` in `values-de/strings.xml` used English `Aftermath`.
  2. *Unlocalized Subtitle Formatters*: In `AdvancedTuningAccordion.kt`, `formatBatterySaverSubtitle()` and `formatSensorsGpsSubtitle()` construct strings using hardcoded English literals (`Flat:`, `Steep:`, `Alt:`, `Speed:`) without accepting a `Context`.
  3. *Inappropriate Kilometer Formatting*: `formatClusterTuningSubtitle()` multiplies metric tolerances by `0.001f` and formats with 3 decimals (`%.3fkm`), turning 200m/400m tolerances into `0,200km`/`0,400km`, with hardcoded English prefixes (`End:`, `Apex:`, `Dist:`, `Alt:`).
  4. *Adjective Declension*: In German, `tuning_sensor_search_active` was set to uninflected `Aktiv`, producing ungrammatical `Aktiv Autosuche` (feminine noun requires `Aktive`).
  5. *Redundant Card Heading*: `TuningWarningNotice` in `TuningFormControls.kt` binds `advanced_tuning_warning_title` ("Experteneinstellungen"), repeating the TopAppBar header.
* **Independent Peer Review & Verification (ATT-3534)**:
  * Inspected all 4 attachments (`settings_04`, `settings_05`, `round4_04`, `round4_05`) via `read_file` confirming defect locations.
  * Audited `ATT-5819` synergy: `TuningWarningNotice` recently switched to advisory `secondaryContainer`/`surfaceVariant` styling; changing `advanced_tuning_warning_title` to `Hinweis` (German) / `Notice` (English) cleanly deduplicates the header while preserving `AdvancedTuningVisualContractTest.kt` contract constraints.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Replace `'Control Tracking'` with localized tab title (`'Start'` / `'Inicio'` / etc.) across all 9 locales.
  * Localize `tuning_cat_aftermath` in German to `Nachbereitung & Profil-Analytik`.
  * Update `TuningSubtitleFormatter` to localize `Flat`/`Steep` (`Flach`/`Steil`), `Alt`/`Speed` (`Höhe`/`Geschw.`), and metric meter formatting (`200 m`, `400 m`).
  * Fix German declension for `tuning_sensor_search_active` to `Aktive`.
  * Update `advanced_tuning_warning_title` to concise `Hinweis` (German) / `Notice` (English) across 9 locales.
* **Out-of-Scope**: Altering clustering algorithms (`WorkoutClustersViewModel`), modifying battery saver thresholds, or redesigning settings navigation.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Net-new refinement `REQ-UI-472` (*Display & Expert Settings Terminology, Subtitle Localization & Warning Deduplication*), refining `REQ-UI-122` and `REQ-UI-222`.
* **Historical Origin & Commit Trace**: `ATT-2746` (REQ-UI-122 Display settings), `ATT-2940` (REQ-UI-222 Tuning accordion), and `ATT-5819` (REQ-UI-469 TuningWarningNotice styling).
* **Root Reason for Existing Formulation**: Early prototype implementations reused internal code identifiers and hardcoded English formatters; rapid additions prioritized function over localization parity.
* **Preservation of Core Invariants**: 100% 9-language translation parity maintained; zero regressions in Settings persistence and reset mechanisms.

---

## 5. Architectural Strategy & High-Level Solution
* **String Resources**: Update `res/values*/strings.xml` for `cockpit_theme_description`, `tuning_cat_aftermath`, `tuning_sensor_search_active`, and `advanced_tuning_warning_title`.
* **Subtitle Formatters (`AdvancedTuningAccordion.kt`)**:
  * Add optional `context: Context? = null` to `formatBatterySaverSubtitle()` and `formatSensorsGpsSubtitle()` to load localized short labels (`Flach`/`Steil`, `Höhe`/`Geschw.`) when provided, with safe fallback for existing unit tests.
  * In `formatClusterTuningSubtitle()`, format metric tolerances as clean integer meters (`"${tolerance.roundToInt()} m"`) instead of `0,xxxkm`, and bind localized labels (`Ende`/`Apex`/`Distanz`/`Höhe`).
* **Call Sites**: Pass `context` from `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: 100% clean-room test pass rate (`TranslationParityTest`, `AdvancedTuningAccordionTest`); zero layout shift or regression in preferences persistence.
* **Risk Rating**: **LOW** — Pure localization, string formatting, and resource refinement with zero database or business-logic risks.
