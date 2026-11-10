# Stage 3: Implementation Plan - ATT-6282: DisplaySettingsDialog Subtitles

**Ticket**: [ATT-6282](https://rainerblind.atlassian.net/browse/ATT-6282)  
**Sub-task**: [ATT-6347](https://rainerblind.atlassian.net/browse/ATT-6347) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.1`  
**Requirement Mapping**: `REQ-UI-503`  
**Test Mapping**: `TST-UI-503`  
**Branch**: `feature/ATT-6282`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-10  

---

## 1. Problem Description & Background

In `DisplaySettingsDialog.kt`, the top three display toggles (`forcePortrait`, `keepScreenOn`, `noUnlocking`) lack explanatory subtitles, and titles begin with a lowercase letter in multiple locales. We will author 3 descriptive summary strings across all 9 locales, capitalize titles, and pass `subtitle = stringResource(...)` to each `DisplayOptionToggle`, matching the existing fourth toggle (`volumeKeysSwitchCockpitTabs`).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-503` (*DisplaySettingsDialog: Descriptive Subtitles and Capitalized Titles for Top Display Toggles*)
* **Test Mapping**: `TST-UI-503` (*DisplaySettingsDialog Display Option Subtitles Verification Suite*)

---

## 3. System Invariants & Preserved Behavior

1. **Preference Contract Invariants**: `DEFAULT_DISPLAY_OPTIONS` key set, SharedPreferences persistence, and boolean getters (`forcePortrait()`, `keepScreenOn()`, `NoUnlocking()`, `volumeKeysSwitchCockpitTabs()`) remain 100% unaltered.
2. **Localization Invariants**: 100% translation parity across all 9 locales with zero format specifiers and zero decorative emojis (`ATT-5400`).
3. **Merge Conflict Prevention**: New summary strings are placed directly adjacent to corresponding titles in `strings.xml` rather than at EOF.
4. **Parent Human Gate Invariance**: Final completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

* **Resource Layer**: Update `res/values*/strings.xml` across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`):
  * Capitalize `prefsKeepScreenOnTitle` and `prefsNoUnlockingTitle`.
  * Add `forcePortraitSummary` directly adjacent to `forcePortrait`.
  * Add `prefsKeepScreenOnSummary` directly adjacent to `prefsKeepScreenOnTitle`.
  * Add `prefsNoUnlockingSummary` directly adjacent to `prefsNoUnlockingTitle`.
* **UI Layer**: In `DisplaySettingsDialog.kt` (lines 78–98), pass `subtitle = stringResource(...)` to the top three `DisplayOptionToggle` calls.
* **UI Consistency (Rule 23)**: Reuses `DisplayOptionToggle` existing layout with `bodySmall` subtitle typography in `MaterialTheme.colorScheme.onSurfaceVariant`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Resource Additions & Title Capitalization (`REQ-UI-503.2`, `REQ-UI-503.3`)
* **Files**: `app/src/main/res/values*/strings.xml` (all 9 locales).
* **Changes**: Capitalize titles and insert adjacent summary strings according to the `REQ-UI-503` specification table.

### Step 2: DisplaySettingsDialog Subtitle Wiring (`REQ-UI-503.1`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsDialog.kt`.
* **Changes**: Add `subtitle` parameter to `forcePortrait`, `keepScreenOn`, and `noUnlocking` `DisplayOptionToggle` calls.

### Step 3: Contract & Unit Test Verification (`TST-UI-503.1`, `TST-UI-503.2`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsTest.kt`.
* **Changes**: Add `testDisplayOptionSubtitlesAndCapitalizationParity()` verifying all 3 summaries exist across 9 locales without format specifiers or emojis, and titles are capitalized.
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted unit test execution and `TranslationParityTest` in Stage 4; Tier 1 clean-room regression in Stage 5.
* **Rollback**: Branch isolation allows full git revert without affecting `sprint/2026-46.1`.
