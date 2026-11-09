# Stage 1 Analysis: ATT-6282 - DisplaySettingsDialog: Add descriptive subtitles for top three display toggles

**Ticket**: [ATT-6282](https://rainerblind.atlassian.net/browse/ATT-6282)  
**Sub-task**: [ATT-6342](https://rainerblind.atlassian.net/browse/ATT-6342) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1`  
**Branch**: `feature/ATT-6282`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Statement & Motivation

In `DisplaySettingsDialog.kt` (lines 78–106), the top section renders four display switches: `forcePortrait`, `keepScreenOn`, `noUnlocking`, and `volumeKeysSwitchCockpitTabs`. While the fourth toggle provides a descriptive `subtitle` (`prefs_volume_keys_switch_cockpit_tabs_summary`), the top three toggles completely omit a subtitle explanation. Furthermore, legacy title strings for `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle` start with lowercase letters across multiple locales (e.g. EN: *"keep screen on"*, *"keep screen unlocked"*). Users lack clear context on what these toggles do (e.g., orientation locking, wake-lock behavior during tracking, and lock screen bypass).

---

## 2. Root Cause Analysis & Independent Hypothesis Verification

* **Root Cause**: The top three toggles were migrated from legacy Android XML preference screens where summaries were absent. When `volumeKeysSwitchCockpitTabs` was added later (`ATT-3549`), `subtitle` support was added to `DisplayOptionToggle` but not backported to the existing three switches.
* **Independent Peer Review & Verification**:
  * Confirmed in `DisplaySettingsDialog.kt` (lines 78–98): `forcePortrait`, `keepScreenOn`, and `noUnlocking` instantiate `DisplayOptionToggle` without `subtitle`.
  * Audited `DisplayOptionToggle` (lines 260–292): composable natively supports `subtitle: String? = null` and renders it with `bodySmall` and `onSurfaceVariant`.
  * Audited all 9 `res/values*/strings.xml` files: `forcePortrait`, `prefsKeepScreenOnTitle`, and `prefsNoUnlockingTitle` exist; titles start with lowercase in EN, ES, FR, IT, NL, PL, PT.
  * Side-Effect Audit (Rule 68): Audited `app/src/test/`; zero tests assert on exact lowercase title strings or string literals.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Author 3 new localized summary strings across all 9 locales: `forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`.
  * Capitalize `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle` across all 9 locales.
  * Wire `subtitle = stringResource(...)` for the top three `DisplayOptionToggle` calls in `DisplaySettingsDialog.kt`.
  * Add automated contract test coverage in `DisplaySettingsCleanupTest.kt` verifying all 4 toggles bind subtitles.
  * Update living docs: `docs/requirements/req_ui_design_system.md` (`REQ-UI-503`) and `docs/tests/tst_ui_design_system.md` (`TST-UI-503`).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No modification to preference storage keys (`TrainingApplication.DEFAULT_DISPLAY_OPTIONS`), SharedPreferences persistence, or boolean getters.
  * No layout restructuring of battery saver, brightness, or tuning navigation sections.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: Amends `REQ-UI-149.1` (`DisplaySettingsDialog.kt` modernization). Targets `DisplaySettingsDialog.kt` and `res/values*/strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-1016` (`3aa0845a`, initial dialog migration) and `ATT-3549` (`f9d7842e`, added `volumeKeysSwitchCockpitTabs` with subtitle).
* **Root Reason for Existing Formulation**: Expedient direct port of legacy XML preference strings without authoring explanatory summaries.
* **Preservation of Core Invariants**: `TrainingApplication.getDisplayOptions()` key set, preference listeners, and UI hierarchy remain 100% unaltered.

---

## 5. Architectural Strategy & High-Level Solution

* **Resource Layer**: Add `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` across all 9 supported locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`). Capitalize title strings.
* **UI Layer**: In `DisplaySettingsDialog.kt`, pass `subtitle = stringResource(...)` for the 3 toggles.
* **Test Layer**: In `DisplaySettingsCleanupTest.kt`, add `testDisplayOptionToggles_bindSubtitlesAcrossAllFourOptions()` and run `TranslationParityTest`.

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. SharedPreferences key string set `DEFAULT_DISPLAY_OPTIONS` and getters remain completely unchanged.
  2. 100% localization parity across all 9 locales with zero format specifiers and zero emojis (`ATT-5400`).
  3. Parent ticket Human Decision Gate strictly enforced.
* **Risk Rating**: **LOW** — Purely UI subtitle wiring and localized string additions with zero business logic changes.
