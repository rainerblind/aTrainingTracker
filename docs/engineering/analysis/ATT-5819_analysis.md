# Stage 1 Analysis: ATT-5819 - Experten-Einstellungen: Warnhinweis (TuningWarningNotice) sachlicher und freundlicher formulieren

**Ticket**: [ATT-5819](https://rainerblind.atlassian.net/browse/ATT-5819)  
**Sub-task**: [ATT-5904](https://rainerblind.atlassian.net/browse/ATT-5904) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5819`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
The advisory banner at the top of Expert Settings (`TuningWarningNotice` in `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt`) currently presents an intimidating danger alert (`Icons.Default.Warning`, `errorContainer` red background, and alarming copy focusing solely on corrupted sensor heuristics).  
Because Expert Settings now manages beneficial visual features (Workout Cards, Cockpit Typography, Physiology Models), the tone is needlessly alarmist. In accordance with `docs/design_guidelines.md` (Section 5.4 *Semantic Alert & Warning Containers*), the text across all 9 locales must be rewritten to be friendly, factual, and constructive (highlighting that layout, model, and sensor parameters are customized here and can be restored to defaults anytime via the reset icon), while modernizing the card styling to an informative advisory notice.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: The banner text (`advanced_tuning_warning_desc` / `tuning_warning_notice`) was authored in Sprint 2026-40.8 (`ATT-1821`) when the dialog only exposed raw GPS thresholds, slope smoothing, and battery dimming.
* **Independent Peer Review & Verification**:
  * Code Inspection: `TuningFormControls.kt:158-189` binds `R.string.advanced_tuning_warning_title` and `R.string.advanced_tuning_warning_desc` inside an `errorContainer` card with `error` red warning icon.
  * Tone Mismatch: The current German copy ("Das Ändern dieser Parameter beeinflusst die Tracking-Heuristik...") reads like a failure warning rather than an empowering customization guide.
  * Harmonization: Rewriting the string to explain advanced layout, model, and sensor customization with standard reset reassurance across all 9 languages directly satisfies user intent.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Formulate friendly, constructive, and matter-of-fact copy for `tuning_warning_notice` / `advanced_tuning_warning_desc` across all 9 locales (EN, DE, ES, FR, IT, JA, NL, PL, PT).
  * Harmonize `TuningWarningNotice` in `TuningFormControls.kt` to bind the new friendly copy and use an appropriate advisory container (avoiding alarming error-red backgrounds).
  * Add unit and contract tests in `AdvancedTuningVisualContractTest.kt` verifying banner rendering, string bindings, and 9-language translation parity.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No removal of the warning notice or reset-to-defaults functionality.
  * No modifications to underlying DataStore preferences or accordion section structures.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-222` (item 5, *Preservation of Core Invariants*) in `docs/requirements/req_ui_design_system.md`. Targets `TuningFormControls.kt` and `strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-1821` (Sprint 2026-40.8, commit `e2303aa7`), `ATT-1957` (Sprint 2026-40.9), and `ATT-5819` (Sprint 2026-45.12).
* **Root Reason for Existing Formulation**: Defensive wording and red warning styling were introduced when experimental tuning parameters were first exposed.
* **Preservation of Core Invariants**: The advisory notice remains permanently visible at the top of Expert Settings; factory reset safety is reinforced.

---

## 5. Architectural Strategy & High-Level Solution
1. **Localization Parity**: Define friendly, factual copy for `advanced_tuning_warning_desc` and `tuning_warning_notice` across all 9 supported locales explaining layout, model, and sensor customization with reset icon reassurance.
2. **Component Modernization**: In `TuningFormControls.kt` (`TuningWarningNotice`), update the card styling to an advisory container (e.g. `surfaceVariant` or `secondaryContainer` with `Icons.Default.Info` / `Tune`) to match the constructive tone.
3. **Contract Verification**: Expand `AdvancedTuningVisualContractTest.kt` to verify `TuningWarningNotice` presence, string resource binding, and absence of alarmist error-red styling.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. 100% 9-language translation parity across all application locales.
  2. Factory reset action (`R.string.reset_to_defaults`) and advisory banner visibility remain strictly preserved.
  3. Zero regressions in DataStore preferences or accordion section expand/collapse mechanics.
* **Risk Rating**: **LOW** (Copy refinement and container theme harmonization with zero business logic changes).
