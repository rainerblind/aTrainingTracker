# Stage 3: Implementation Plan - ATT-5820: Auto-Pause Slider Accordion

**Ticket**: [ATT-5820](https://rainerblind.atlassian.net/browse/ATT-5820)  
**Sub-task**: [ATT-5893](https://rainerblind.atlassian.net/browse/ATT-5893) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Requirement Mapping**: `REQ-SET-080` (`REQ-SET-080.6`)  
**Test Mapping**: `TST-SET-080` (`TST-SET-080.7`)  
**Branch**: `feature/ATT-5820`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Description & Scope
In `AutoPauseTabsScreen.kt`, three calibration sliders (`speedThreshold`, `debounceSec`, `resumeDebounceSec`) render openly per sport tab, cluttering the UI. Following Design Guidelines Section 3 (*Settings & Accordion Structure*), technical sliders SHALL be encapsulated inside an initially collapsed accordion (`TuningAccordionSection`) titled "Schwellenwerte & Verzögerung anpassen" (`tuning_auto_pause_custom_thresholds_title`) with active values in its header subtitle.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-SET-080.6` (Collapsible Calibration Accordion with Live Active Values Subtitle)
* **Test Mapping**: `TST-SET-080.7` (`TST-SET-080.7a` Contract, `TST-SET-080.7b` Formatter Unit, `TST-SET-080.7c` Parity, `TST-SET-080.7d` Tier 1 Regression)

---

## 3. System Invariants & Preserved Behavior
1. **Zero DataStore Ingress Regressions**: Preferences storage, keys, defaults, and ranges remain identical.
2. **Fixed Header Invariant**: Top app bar, global start-in-auto-pause card, and tabs stay visible without collapsing.
3. **9-Language Parity & Theme Centralization**: 100% translation parity with indexed positional specifiers (`%1$s`), zero raw hardcoded colors.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningSubtitleFormatter` (`AdvancedTuningAccordion.kt`)
* Introduce `formatAutoPauseSubtitle(speedThreshold, speedUnit, debounceSec, resumeDebounceSec, context): String` formatting speed and debounce delays into `tuning_auto_pause_subtitle_format`.

### Component 2: `AutoPauseTabsScreen.kt` (`AutoPauseSportTabContent`)
* Encapsulate the 3 `TuningSliderItem`s inside `TuningAccordionSection` with `icon = Icons.Default.Tune` when `enabled == true`.
* Add `var isSlidersExpanded by rememberSaveable { mutableStateOf(false) }` to maintain collapsed default state per tab.

### Component 3: Localization (`strings.xml` x 9)
* Add `tuning_auto_pause_custom_thresholds_title` and `tuning_auto_pause_subtitle_format` across EN, DE, ES, FR, IT, JA, NL, PL, PT.

### UI Consistency (Rule 23)
* **Reference Component**: `TuningAccordionSection` in `AdvancedTuningScreen.kt` / `AdvancedTuningDialog.kt`.
* **Reused Components**: `TuningAccordionSection`, `TuningSliderItem`, `TuningToggleItem`.
* **Theme Tokens**: `RoundedCornerShape(12.dp)`, `surfaceVariant` (alpha 0.35f), `outlineVariant` (alpha 0.5f).
* **New One-off Styles**: None (100% canonical M3 design guidelines reuse).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Strings & 9-Language Localization Parity
* Files: `app/src/main/res/values*/strings.xml` (all 9 locales)
* Changes: Add `tuning_auto_pause_custom_thresholds_title` and `tuning_auto_pause_subtitle_format`.

### Step 2: Formatter Implementation & Unit Test
* Files: `AdvancedTuningAccordion.kt`, `app/src/test/.../ui/settings/tuning/TuningSubtitleFormatterTest.kt`
* Changes: Implement `formatAutoPauseSubtitle` and verify metric/imperial formatting.

### Step 3: Accordion Encapsulation in `AutoPauseTabsScreen.kt`
* Files: `AutoPauseTabsScreen.kt`
* Changes: Wrap sliders in `TuningAccordionSection`, wire `rememberSaveable { mutableStateOf(false) }` and dynamic subtitle.

### Step 4: Contract Test Expansion & Verification
* Files: `AutoPauseTabsScreenContractTest.kt`
* Changes: Add `testAutoPauseSportTabContent_encapsulatesSlidersInsideDefaultCollapsedAccordion()`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.autopause.*" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted tests pass with 100% success; Code Lock checked via `check-gate`.
* **Rollback**: Clean git working tree revert on `feature/ATT-5820` if any regression occurs.
