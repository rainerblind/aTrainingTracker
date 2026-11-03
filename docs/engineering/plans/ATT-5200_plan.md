# Stage 3: Implementation Plan - ATT-5200: Profile & Zones (Zonendarstellung): rename zone_display_background from "Heller Hintergrund" / "Light background" to "Eingefärbter Hintergrund" / "Tinted background" across all 9 locales

**Ticket**: [ATT-5200](https://rainerblind.atlassian.net/browse/ATT-5200)  
**Sub-task**: Pending (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-45.2`  
**Requirement Mapping**: `REQ-UI-172`  
**Test Mapping**: `TST-UI-124` (`TST-UI-124.1`–`TST-UI-124.3`)  
**Branch**: `feature/ATT-5200`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-03

---

## 1. Problem Description & Background

In `ZonesSettingsActivity.kt`, the first toggle under "Zonendarstellung" (`showBackground`) is labeled via `R.string.zone_display_background`. Its original wording ("Light background" / "Heller Hintergrund") is misleading in Dark Mode and on AMOLED screens. This plan specifies renaming the string resource across all 9 supported locales to accurately convey zone color tinting ("Tinted background" / "Eingefärbter Hintergrund") while strictly preserving string keys, preference storage, and system invariants.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-172` (*Configurable Zone Visualization Options and Theme-Aware Live Preview*)
  * Accurate terminology across all 9 locales reflecting 12% alpha zone color tinting.
  * Preserved string key `R.string.zone_display_background`, `showBackground: Boolean`, and DataStore preferences.
* **Test Mapping**: `TST-UI-124` in `ZoneDisplayLocalizationTest.kt` and `TranslationParityTest.kt`.

---

## 3. System Invariants & Preserved Behavior

1. **Zero Unintended Regressions**: Existing feature suites and DataStore preference reads continue to function without changes.
2. **Rule 4 Code Lock**: Zero production edits before Gate 3 passes `python3 tools/jira_util.py check-gate`.
3. **Identifier Invariance**: String resource key `zone_display_background` remains unchanged.
4. **Subtask & Human Gate Invariants**: Subtasks close via `freigabe`; parent ticket completes at `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: String Resources (`app/src/main/res/values*/strings.xml`)
Update `zone_display_background` value in all 9 locales:
* `values`: `"Tinted background"`
* `values-de`: `"Eingefärbter Hintergrund"`
* `values-es`: `"Fondo tintado"`
* `values-fr`: `"Arrière-plan teinté"`
* `values-it`: `"Sfondo tinto"`
* `values-ja`: `"色付き背景"`
* `values-nl`: `"Getinte achtergrond"`
* `values-pl`: `"Zabarwione tło"`
* `values-pt`: `"Fundo matizado"`

### Component 2: Localization Test Suite (`ZoneDisplayLocalizationTest.kt`)
* Add test assertion verifying that `zone_display_background` matches the exact expected translation in each locale.

### UI Consistency (Rule 23)
* **Reference screen**: `ZonesSettingsActivity.kt` ("Zonendarstellung" section).
* **Reused components**: `ZoneDisplayToggleRow`.
* **Theme tokens**: Standard typography and colors from Material 3.
* **New one-off styles & justification**: None (strictly text refinement).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update 9 strings.xml Files
* Files: `app/src/main/res/values*/strings.xml`
* Changes: Update `zone_display_background` string values.

### Step 2: Update Localization Contract Test & UI Comments
* Files:
  * `app/src/test/java/com/atrainingtracker/trainingtracker/localization/ZoneDisplayLocalizationTest.kt`
  * `app/src/main/java/com/atrainingtracker/trainingtracker/activities/ZonesSettingsActivity.kt` (update comment to `// Toggle: Tinted background`)
* Changes: Assert exact string translations per locale.

### Step 3: Targeted Unit Tests
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.localization.ZoneDisplayLocalizationTest" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --max-workers=2`

---

## 6. Verification & Rollback Plan

* **Verification**: Targeted localization test suite, `TranslationParityTest`, and `verify_requirement_governance.py`.
* **Rollback**: Clean git branch revert via `git checkout sprint/2026-45.2`.
