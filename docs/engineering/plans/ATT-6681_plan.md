# Stage 3: Implementation Plan - ATT-6681: DisplaySettingsDialog: Condense verbose 3-line display option subtitles into concise single-line descriptions

**Ticket**: [ATT-6681](https://rainerblind.atlassian.net/browse/ATT-6681)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Global UI & Design System*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-UI-515` (`REQ-UI-515.1`..`REQ-UI-515.4`)  
**Test Mapping**: `TST-UI-515` (`TST-UI-515.1`..`TST-UI-515.5`)  
**Branch**: `feature/ATT-6681`  
**Author**: AI Agent 1 (Implementer - Pro) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
In `ATT-6282`, descriptive subtitles were added to the display option toggles in `DisplaySettingsDialog`. On standard mobile viewports, verbose descriptions wrap to 2–3 lines per switch with orphaned words, causing visual clutter and vertical scrolling. This plan details the modification of string resources across all 9 locales and the expansion of `DisplaySettingsTest.kt` with character limits and exact copy assertions.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-UI-515` (*Concise Display Settings Toggle Subtitles and Cockpit Theme Description*)
* **Test Mapping**: `TST-UI-515` (*Character Bounds ($\le 55$ and $\le 75$), Exact Copy Match, and Hygiene Constraints*)

---

## 3. System Invariants & Preserved Behavior
1. **Preference Key Parity**: The keys `forcePortrait`, `keepScreenOn`, `noUnlocking`, `volumeKeysSwitchCockpitTabs` and their respective DataStore/SharedPreferences storage remain strictly unchanged.
2. **Hygiene Standards**: Zero format specifiers (`%s`, `%d`), zero escaped newlines, and zero decorative emojis (`ATT-5400`) across all 9 locales.
3. **UI Consistency**: Existing `DisplaySettingsDialog.kt` Composable layout, Material 3 tokens, and spacing remain unchanged.

---

## 4. Proposed Architectural Changes
* **Localization Resources (`app/src/main/res/values*/strings.xml`)**:
  - Update `forcePortraitSummary`, `prefsKeepScreenOnSummary`, `prefsNoUnlockingSummary`, and `cockpit_theme_description` across all 9 locales (`values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`) to crisp single-line copy.
* **Test Suite (`app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/display/DisplaySettingsTest.kt`)**:
  - Add character length assertions ($\le 55$ chars for toggle summaries, $\le 75$ chars for cockpit theme).
  - Update German and English exact copy assertions to match the new concise phrasing.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1: 9-Language Localization Update (`values*/strings.xml`)**
   - Replace long subtitles with concise translations from `ATT-6681_test_spec.md` table across all 9 locales.
2. **Step 2: Update DisplaySettingsTest (`DisplaySettingsTest.kt`)**
   - Assert `forcePortraitSummary`, `prefsKeepScreenOnSummary`, and `prefsNoUnlockingSummary` are $\le 55$ characters across all 9 locales.
   - Assert `cockpit_theme_description` is $\le 75$ characters across all 9 locales.
   - Assert exact German and English matches.
3. **Step 3: Verification Execution (`DisplaySettingsTest.kt` & `TranslationParityTest.kt`)**
   - Execute targeted unit tests under `/tmp/att_build_merge.lock` via `python3 tools/parallel_sprint_lock.py`.

---

## 6. Verification & Rollback Plan
* **Verification**: Execute `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.display.DisplaySettingsTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest"`.
* **Rollback**: Discard uncommitted changes on `feature/ATT-6681` via `git restore . && git clean -fd`.
