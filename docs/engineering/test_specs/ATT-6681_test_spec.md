# Stage 2: Requirement & Test Specification - ATT-6681: DisplaySettingsDialog: Condense verbose 3-line display option subtitles into concise single-line descriptions

**Ticket**: [ATT-6681](https://rainerblind.atlassian.net/browse/ATT-6681)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-515` (*Concise Display Settings Toggle Subtitles and Cockpit Theme Description*)  
**Test Spec ID**: `TST-UI-515` | **Branch**: `feature/ATT-6681`  
**Author**: AI Agent 1 (Implementer - Flash) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (REQ-UI-515)

### 1.1 Problem Statement & Rationale
In `ATT-6282`, descriptive subtitles were added to the display option toggles and cockpit theme in `DisplaySettingsDialog`. On standard mobile viewport widths ($\le 412\text{dp}$), long multi-line copy (2–3 lines per switch with orphaned words) creates visual clutter and pushes the battery-saver brightness sliders off-screen into a scroll. The system SHALL condense `forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`, and `cockpit_theme_description` across all 9 locales into crisp, single-line descriptions ($\le 55$ characters for toggles, $\le 75$ characters for cockpit theme).

### 1.2 Functional & Architectural Requirements
1. **Concise Toggle Subtitle Length Bound (`REQ-UI-515.1`)**: `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` SHALL NOT exceed 55 characters in any of the 9 supported locales (`en`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`).
2. **Cockpit Theme Description Length Bound (`REQ-UI-515.2`)**: `cockpit_theme_description` SHALL NOT exceed 75 characters in any of the 9 supported locales, eliminating 3-line overflow and orphaned words.
3. **9-Language Semantic Parity (`REQ-UI-515.3`)**: All 9 localized variants SHALL preserve the functional meaning of each setting without formatting specifiers (`%s`, `%d`), XML-escaped newline entities (`&#10;`), or decorative emojis (`ATT-5400`).
4. **Contract Test Validation (`REQ-UI-515.4`)**: `DisplaySettingsTest.kt` SHALL validate that toggle subtitles do not exceed 55 characters and cockpit theme description does not exceed 75 characters across all 9 locales, while matching the canonical German and English copy.

### 1.3 Acceptance Criteria (Given-When-Then)
* *Given* `DisplaySettingsDialog` rendered on a standard phone viewport (1080px / 360–412dp),
* *When* inspecting the subtitles for `forcePortrait`, `keepScreenOn`, and `noUnlocking`,
* *Then* each subtitle renders on a single line without wrapping or orphaned words, and its length is $\le 55$ characters across all 9 locales.
* *Given* the `cockpit_theme_description` text,
* *When* rendered across all 9 locales,
* *Then* its length is $\le 75$ characters and fits within 2 compact lines without orphaned 3rd-line words.
* *Given* `DisplaySettingsTest.kt`,
* *When* executed,
* *Then* length bound assertions and exact copy matches for German and English pass with 100%.

### 1.4 System Invariants & Requirement Archaeology (Chesterton's Fence)
* **Original Requirement ID & Target**: Amends `REQ-UI-503` (*Display Settings Toggle Subtitles & Capitalization Parity*, `ATT-6282`). Targets `values*/strings.xml`, `DisplaySettingsDialog.kt`, and `DisplaySettingsTest.kt`.
* **Historical Origin & Commit Trace**: `ATT-6282` (`6753b825`, Sprint 2026-45.6) introduced explanatory subtitles to the display toggles.
* **Root Reason for Existing Formulation**: Subtitles were drafted as full explanatory sentences to maximize clarity on fresh installations, but 2–3 line height caused excessive vertical elongation on standard mobile viewport widths ($\le 412\text{dp}$).
* **Preservation of Core Invariants**: Full functional clarity is maintained; preference keys (`forcePortrait`, `keepScreenOn`, `noUnlocking`), zero format specifiers, zero emojis, and sentence capitalization invariants are strictly preserved.

---

## 2. Test Specification (TST-UI-515)

### Test Case 1: Toggle Subtitles Character Bound Test (`TST-UI-515.1`)
* **Scope**: JVM Unit Test (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsTest.kt`)
* **Assertion**: Verify `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` are present, non-blank, and $\le 55$ characters in length across all 9 locales.

### Test Case 2: Cockpit Theme Description Character Bound Test (`TST-UI-515.2`)
* **Scope**: JVM Unit Test (`DisplaySettingsTest.kt`)
* **Assertion**: Verify `cockpit_theme_description` is present, non-blank, and $\le 75$ characters in length across all 9 locales.

### Test Case 3: Exact Copy Match Test for German & English (`TST-UI-515.3`)
* **Scope**: JVM Unit Test (`DisplaySettingsTest.kt`)
* **Assertion**:
  - German (`de`):
    - `forcePortraitSummary` == `"Anzeige dauerhaft im Hochformat fixieren"`
    - `prefsKeepScreenOnSummary` == `"Kein Standby während einer Aufzeichnung"`
    - `prefsNoUnlockingSummary` == `"Cockpit direkt über dem Sperrbildschirm anzeigen"`
  - English (`values/`):
    - `forcePortraitSummary` == `"Lock display to portrait mode"`
    - `prefsKeepScreenOnSummary` == `"Prevent standby during active tracking"`
    - `prefsNoUnlockingSummary` == `"Show cockpit directly above the lock screen"`

### Test Case 4: Hygiene & Specifier Constraints (`TST-UI-515.4`)
* **Scope**: JVM Unit Test (`DisplaySettingsTest.kt`)
* **Assertion**: Assert zero format specifiers (`%s`, `%d`) and zero emojis across all 9 locales for all 4 string resources.

### Test Case 5: Tier 1 Targeted Unit Test Regression Suite (`TST-UI-515.5`)
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsTest"`
* **Assertion**: 100% pass rate.

---

## 3. 9-Language Localization Reference Table

| Key | DE | EN | ES | FR | IT | JA | NL | PL | PT |
|:---|:---|:---|:---|:---|:---|:---|:---|:---|:---|
| `forcePortraitSummary` | Anzeige dauerhaft im Hochformat fixieren (41) | Lock display to portrait mode (30) | Fijar pantalla en modo vertical (32) | Bloquer l\'affichage en mode portrait (36) | Blocca lo schermo in verticale (30) | 画面を縦向きに固定します (12) | Scherm vergrendelen in staande modus (36) | Blokuj ekran w orientacji pionowej (34) | Bloquear o ecrã no modo vertical (32) |
| `prefsKeepScreenOnSummary` | Kein Standby während einer Aufzeichnung (40) | Prevent standby during active tracking (38) | Evitar suspensión durante la grabación (39) | Empêcher la mise en veille pendant l\'enregistrement (51) | Evita lo standby durante la registrazione (42) | 記録中の自動スリープを防止します (16) | Geen stand-by tijdens actieve opname (36) | Zapobiegaj uśpieniu podczas rejestrowania (41) | Evitar suspensão durante a gravação (35) |
| `prefsNoUnlockingSummary` | Cockpit direkt über dem Sperrbildschirm anzeigen (49) | Show cockpit directly above the lock screen (43) | Mostrar panel sobre la pantalla de bloqueo (42) | Afficher le cockpit sur l\'écran de verrouillage (47) | Mostra il cockpit sopra la schermata di blocco (46) | ロック画面の上にコックピットを表示 (17) | Cockpit direct boven vergrendelscherm tonen (43) | Pokaż kokpit bezpośrednio na ekranie blokady (44) | Mostrar o cockpit sobre o ecrã de bloqueio (42) |
| `cockpit_theme_description` | Gilt nur für Telemetrie- und Sensoransichten des aktiven Workouts. (66) | Applies only to telemetry and sensor views during active workouts. (65) | Solo se aplica a vistas de telemetría y sensores del entrenamiento. (67) | S\'applique uniquement aux données et capteurs du workout actif. (63) | Si applica solo a viste di telemetria e sensori del workout attivo. (67) | アクティブなワークアウトのテレメトリ画面にのみ適用されます。 (29) | Geldt alleen voor telemetrie en sensors van actieve workouts. (60) | Dotyczy tylko widoków telemetrycznych i sensorów treningu. (57) | Aplica-se apenas às vistas de telemetria e sensores do treino. (62) |

---

## 4. Traceability Matrix

| Test Case | Scope | Target Component | Requirement | Status |
|:---|:---|:---|:---|:---|
| `TST-UI-515.1` | Length Bound ($\le 55$ chars) | `DisplaySettingsTest.kt` | `REQ-UI-515.1` | **Specified** |
| `TST-UI-515.2` | Length Bound ($\le 75$ chars) | `DisplaySettingsTest.kt` | `REQ-UI-515.2` | **Specified** |
| `TST-UI-515.3` | Exact Copy Matching | `DisplaySettingsTest.kt` | `REQ-UI-515.3` | **Specified** |
| `TST-UI-515.4` | Specifier & Emoji Hygiene | `DisplaySettingsTest.kt` | `REQ-UI-515.3` | **Specified** |
| `TST-UI-515.5` | Unit Test Execution | `DisplaySettingsTest.kt` | `REQ-UI-515.4` | **Specified** |
