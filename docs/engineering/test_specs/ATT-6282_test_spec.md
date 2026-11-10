# Stage 2: Requirement & Test Specification - ATT-6282: DisplaySettingsDialog Subtitles

**Ticket**: [ATT-6282](https://rainerblind.atlassian.net/browse/ATT-6282)  
**Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `sprint/2026-46.1` | **Branch**: `feature/ATT-6282`  
**Requirement Mapping**: `REQ-UI-503` (`docs/requirements/req_ui_design_system.md`)  
**Test Spec ID**: `TST-UI-503` (`docs/tests/tst_ui_design_system.md`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-11-10  

---

## 1. Requirement Specification (REQ-UI-503)

### 1.1 Problem Statement & Rationale
In `DisplaySettingsDialog.kt`, the top three display option switches (`forcePortrait`, `keepScreenOn`, and `noUnlocking`) lack explanatory subtitles, and titles begin with a lowercase letter in multiple locales (e.g. EN: *"keep screen on"*). All four top toggles SHALL render capitalized titles and informative subtitles across all 9 supported locales.

### 1.2 Functional & Architectural Requirements
* **REQ-UI-503.1** (*Descriptive Subtitle Wiring*): Pass localized subtitle descriptions to `DisplayOptionToggle` for `forcePortrait`, `keepScreenOn`, and `noUnlocking` explaining vertical orientation locking, active tracking wake-lock retention, and lock-screen overlay access.
* **REQ-UI-503.2** (*Title Capitalization*): Capitalize `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle` across all locales.
* **REQ-UI-503.3** (*9-Language Localization Parity*): Summary strings SHALL be defined across all 9 locales with zero format specifiers and zero decorative emojis (`ATT-5400`).

### 1.3 9-Language Localization Specification Table
| Locale | `forcePortraitSummary` | `prefsKeepScreenOnSummary` | `prefsNoUnlockingSummary` | Titles (Cap) |
| :--- | :--- | :--- | :--- | :--- |
| `EN` | Lock the display to vertical portrait mode and prevent rotation to landscape | Prevent the screen from turning off automatically during active tracking | Show the cockpit above the lock screen without requiring PIN or pattern unlock | Keep screen on / Keep screen unlocked |
| `DE` | Sperrt die Anzeige im Hochformat und verhindert automatisches Drehen ins Querformat | Verhindert, dass sich das Display während einer Aufzeichnung automatisch abschaltet | Zeigt das Cockpit über dem Sperrbildschirm an, sodass beim Aufwecken kein Entsperren nötig ist | Display an lassen / Display entsperrt lassen |
| `ES` | Bloquea la pantalla en modo vertical e impide el giro automático a horizontal | Evita que la pantalla se apague automáticamente durante el seguimiento activo | Muestra el panel sobre la pantalla de bloqueo sin necesidad de introducir PIN o patrón | Mantener pantalla encendida / Mantener pantalla desbloqueada |
| `FR` | Verrouille l\'affichage en mode portrait et empêche la rotation automatique en paysage | Empêche l\'écran de s\'éteindre automatiquement pendant l\'enregistrement actif | Affiche le cockpit sur l\'écran de verrouillage sans nécessiter de déverrouillage | Garder l\'écran allumé / Garder l\'écran déverrouillé |
| `IT` | Blocca la visualizzazione in verticale e impedisce la rotazione automatica in orizzontale | Impedisce lo spegnimento automatico dello schermo durante la registrazione attiva | Mostra il cockpit sopra la schermata di blocco senza richiedere PIN o sequenza | Tieni schermo acceso / Tieni schermo sbloccato |
| `JA` | 画面を縦向きに固定し、横向きへの自動回転を防止します | アクティブな記録中に画面が自動的に消灯するのを防ぎます | ロック画面の上にコックピットを表示し、PINやパターンの入力を不要にします | 画面を常時点灯 / ロック解除を維持 |
| `NL` | Vergrendelt het scherm in staande modus en voorkomt automatisch draaien naar liggend | Voorkomt dat het scherm automatisch uitschakelt tijdens actieve registratie | Toont de cockpit boven het vergrendelscherm zonder pincode of patroon | Scherm aanhouden / Scherm ontgrendeld houden |
| `PL` | Blokuje ekran w orientacji pionowej i zapobiega automatycznemu obracaniu do poziomu | Zapobiega automatycznemu wyłączaniu ekranu podczas aktywnego rejestrowania | Wyświetla kokpit nad ekranem blokady bez konieczności wprowadzania kodu PIN lub wzoru | Utrzymuj włączony ekran / Utrzymuj odblokowany ekran |
| `PT` | Bloqueia o ecrã no modo vertical e impede a rotação automática para horizontal | Impede que o ecrã se desligue automaticamente durante a gravação ativa | Mostra o cockpit sobre o ecrã de bloqueio sem necessidade de PIN ou padrão | Manter ecrã ligado / Manter desbloqueado |

### 1.4 Acceptance Criteria (Given-When-Then) & Invariants
* *Given* `DisplaySettingsDialog`, *When* inspecting the top four toggles, *Then* each renders a capitalized title and descriptive subtitle across all 9 locales.
* *Invariants*: `DEFAULT_DISPLAY_OPTIONS` key set, SharedPreferences persistence, boolean getters, and 9-locale parity are 100% preserved.

---

## 2. Test Specification (TST-UI-503)

### Test Case 1: Display Option Subtitle Contract & Capitalization (`TST-UI-503.1`)
* **Scope**: Automated Unit Contract Test (`DisplaySettingsTest.kt`)
* **Assertion**: Verify across all 9 locales that `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` exist, have non-blank content, zero format specifiers, and zero emojis (`ATT-5400`). Verify `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle` start capitalized.

### Test Case 2: 9-Language Localization Parity (`TST-UI-503.2`)
* **Scope**: Resource Parity Test (`TranslationParityTest.kt`)
* **Assertion**: Verify all 3 summary string keys are defined in all 9 supported locales with 100% parity.

### Test Case 3: Tier 1 Targeted Regression Suite (`TST-UI-503.3`)
* **Scope**: Targeted Unit Test Suite
* **Command**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
* **Assertion**: 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-503.1` | Contract | `DisplaySettingsTest.kt` | `REQ-UI-503.1`, `REQ-UI-503.2` | Specified |
| `TST-UI-503.2` | Localization | `strings.xml` (all 9 locales) | `REQ-UI-503.3` | Specified |
| `TST-UI-503.3` | Regression | `ui.settings.display.*`, `build.*` | `REQ-PRO-001` | Specified |
