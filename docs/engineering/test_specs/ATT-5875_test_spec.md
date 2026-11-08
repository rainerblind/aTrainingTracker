# Stage 2: Requirement & Test Specification - ATT-5875: Display & Expert Settings Polish

**Ticket**: [ATT-5875](https://atrainingtracker.atlassian.net/browse/ATT-5875)  
**Parent Epic**: [ATT-5909](https://atrainingtracker.atlassian.net/browse/ATT-5909) (*Localization & Formatting Parity (i18n)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-45.12` | **Branch**: `feature/ATT-5875`  
**Requirement Mapping**: `REQ-UI-472` (*Display & Expert Settings Terminology, Subtitle Localization & Warning Deduplication*)  
**Test Spec ID**: `TST-UI-472` | **Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-08  

---

## 1. Requirement Specification (REQ-UI-472)

### 1.1 Problem Statement & Rationale
Visual inspection and user walkthroughs of Display and Expert Settings revealed five defects: (1) internal developer jargon `'Control Tracking'` exposed in user-facing theme descriptions; (2) developer keyword `Aftermath` exposed in German category headers; (3) redundant header duplication between TopAppBar (`Experten-Einstellungen`) and advisory card (`Experteneinstellungen`); (4) uninflected adjective `Aktiv` causing broken German declension in sensor search summary; (5) unlocalized summary fragments (`Flat:`, `Steep:`, `Alt:`, `Speed:`) and inappropriate kilometer conversion (`0,200km`, `0,400km`) in accordion subtitles.

### 1.2 Functional & Architectural Requirements
1. **Display Settings Tab Terminology (`REQ-UI-472.1`)**: In `cockpit_theme_description`, the system SHALL reference the localized tab title (`'Start'` in DE/EN/NL/PL, `'Inicio'` in ES, `'Démarrer'` in FR, `'Avvia'` in IT, `'スタート'` in JA, `'Iniciar'` in PT) and SHALL NOT expose `'Control Tracking'`.
2. **German Category Terminology Parity (`REQ-UI-472.2`)**: In German `res/values-de/strings.xml`, `tuning_cat_aftermath` SHALL be localized as `Nachbereitung & Profil-Analytik`.
3. **Advisory Banner Heading Deduplication (`REQ-UI-472.3`)**: `advanced_tuning_warning_title` SHALL be deduplicated from repeating the screen title, displaying concise advisory label: German `Hinweis`, English `Notice`, Spanish `Aviso`, French `Remarque`, Italian `Avviso`, Japanese `注意`, Dutch `Let op`, Polish `Uwaga`, Portuguese `Aviso`.
4. **Sensor Search Declension Grammar (`REQ-UI-472.4`)**: In German `res/values-de/strings.xml`, `tuning_sensor_search_active` SHALL be inflected as `Aktive` ("Aktive Autosuche").
5. **Accordion Subtitle Localization & Metric Meter Formatting (`REQ-UI-472.5`)**: `formatBatterySaverSubtitle()` and `formatSensorsGpsSubtitle()` SHALL load localized labels (`Flach`/`Steil`, `Höhe`/`Geschw.`) when `Context` is provided. `formatClusterTuningSubtitle()` SHALL format metric tolerances $\le 1000\text{ m}$ as integer meters (`"${tolerance.roundToInt()} m"` instead of `0,xxxkm`) and bind localized labels (`Ende`/`Apex`/`Dist.`/`Höhe` in German).

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Display Settings Start Tab Reference)**:
  * *Given* German locale active,
  * *When* viewing `cockpit_theme_description`,
  * *Then* the text references `'Start'` and does not contain `'Control Tracking'`.
* **Criterion 2 (German Aftermath Category Localization)**:
  * *Given* German locale active,
  * *When* viewing Expert Settings categories,
  * *Then* the aftermath section header displays `Nachbereitung & Profil-Analytik`.
* **Criterion 3 (Warning Card Header Deduplication)**:
  * *Given* Expert Settings screen,
  * *When* viewing the top advisory warning card,
  * *Then* the title displays `Hinweis` (DE) / `Notice` (EN) without duplicating the TopAppBar header.
* **Criterion 4 (Sensor Search Declension)**:
  * *Given* German locale active,
  * *When* viewing sensor search accordion summary,
  * *Then* the text displays `Aktive Autosuche`.
* **Criterion 5 (Accordion Subtitle Localization & Meter Formatting)**:
  * *Given* AMOLED battery saver, sensors, and cluster tuning accordion summaries,
  * *When* rendered with context,
  * *Then* summaries display localized labels and format metric tolerances $\le 1000\text{ m}$ as integer meters (`200 m`, `400 m`).

### 1.4 System Invariants & Chesterton's Fence
* 100% 9-language translation parity maintained across all resource files.
* Zero regressions in settings persistence and reset mechanisms.
* 100% clean-room test pass rate.

---

## 2. Test Specification (TST-UI-472)

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-472.1]` | Contract | `TranslationParityTest`: 9-locale parity for tuning subtitle labels; no `Control Tracking` in `cockpit_theme_description`; German `Hinweis`, `Nachbereitung & Profil-Analytik`, `Aktive` | `REQ-UI-472.1`..`4` | Specified |
| `[TST-UI-472.2]` | Unit | `AdvancedTuningAccordionTest`: `formatBatterySaverSubtitle()` renders localized `Flach`/`Steil`; `formatSensorsGpsSubtitle()` renders `Höhe`/`Geschw.` | `REQ-UI-472.5` | Specified |
| `[TST-UI-472.3]` | Unit | `AdvancedTuningAccordionTest`: `formatClusterTuningSubtitle()` formats metric tolerances $\le 1000\text{ m}$ as integer meters (`200 m`, `400 m`) and localized labels | `REQ-UI-472.5` | Specified |
| `[TST-UI-472.4]` | Contract | `AdvancedTuningVisualContractTest`: `TuningWarningNotice` continues binding `advanced_tuning_warning_title` | `REQ-UI-472.3` | Specified |
| `[TST-UI-472.5]` | Regression | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*"` | `REQ-UI-472.1`..`5` | Specified |
