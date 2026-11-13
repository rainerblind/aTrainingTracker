# Stage 1 Analysis: ATT-7519 - ZonesSettingsActivity (HRV DFA α1): Unified 3-Zone Colored Dual-Thumb Slider & Layout Fixes

**Ticket**: [ATT-7519](https://rainerblind.atlassian.net/browse/ATT-7519)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `Sprint 2026-46.5`  
**Branch**: `feature/ATT-7519` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-13  

---

## 1. Problem Statement & Motivation
On-device review of the *Trainings Zonen -> HRV (DFA α1)* tab on Pixel 10 revealed three visual and localization defects (verified via attached screenshots `hrv_zones_top.png` and `hrv_zones_bottom.png`):
1. **Unwanted Text Wrapping**:
   * Header reset button wraps awkwardly across 3 lines (`Auf / Werkseinstellung / en zurücksetzen`) because title, `TextButton`, and `IconButton` are cramped onto a single row.
   * `HrvZoneBandCard` range badges break into 2 lines (`0.50 ≤ α1 ≤ \n 0.75`, `α1 < \n 0.50`) because `zoneLabel` lacks `weight(1f)` and `rangeDescription` lacks `maxLines = 1, softWrap = false`.
2. **Hardcoded English Strings**:
   * Guidance descriptions in `HrvZoneBandCard` and helper texts in `HrvThresholdControlRow` are hardcoded in English instead of using localized resources.
3. **Confusing Dual Sliders with Different Scales**:
   * T1 (`0.60..1.20`) and T2 (`0.30..0.80`) use separate sliders with mismatched domains, placing the thumb for `T1 = 0.75` visually to the left of `T2 = 0.50`, confusing athletes.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **RCA 1 (Mismatched Slider Domains)**: `T1` at 0.75 evaluates to $(0.75-0.60)/(1.20-0.60) = 25\%$ track width, while `T2` at 0.50 evaluates to $(0.50-0.30)/(0.80-0.30) = 40\%$ track width. This creates an optical illusion where lower threshold $T_2$ sits further right than higher threshold $T_1$.
* **RCA 2 (Cramped Header & Unconstrained Badges)**: Placing a 23-char title and 35-char button in one horizontal `Row` forces mid-word wrapping. In `HrvZoneBandCard`, unweighted titles push badge widths below minimum text bounds.
* **Independent Codebase Verification**:
  * Confirmed hardcoded English strings at lines 1163, 1171, 1179, 1209, 1230 in `ZonesSettingsActivity.kt`.
  * Verified `RangeSlider` with custom 3-zone colored track can render a single shared scale (`0.30..1.20`) with $T_2$ and $T_1$ thumbs enforcing $T_1 \ge T_2 + 0.05$.
  * Verified `HrvThresholdControlRow` is tested via reflection in `HrvDfaA1SettingsContractTest.kt` and must be retained as a public composable.

---

## 3. User Scope Grounding & Complexity Assessment (ATT-1250)
* **In-Scope Goals (3 SP - Complex)**:
  1. Implement a unified 3-zone colored dual-thumb `RangeSlider` (`0.30..1.20`) with track segments styled with `TTColor.Zone4` ($< T_2$), `TTColor.Zone3` ($T_2..T_1$), and `TTColor.Zone2` ($> T_1$).
  2. Retain fine-tuning `-` / `+` (0.01 step) steppers for $T_1$ and $T_2$, keeping `HrvThresholdControlRow` public and backward-compatible.
  3. Relocate `reset_to_defaults` cleanly alongside `title_edit_zones` with `maxLines = 1, softWrap = false` to eliminate the 3-line wrap.
  4. Fix `HrvZoneBandCard` layout by adding `Modifier.weight(1f)` to `zoneLabel` and `maxLines = 1, softWrap = false` to badge text.
  5. Add 5 localized string resources (`hrv_zone_aerobic_base_desc`, `hrv_zone_threshold_transition_desc`, `hrv_zone_anaerobic_desc`, `hrv_t1_aerobic_threshold_desc`, `hrv_t2_anaerobic_threshold_desc`) across all 9 locales.
* **Out-of-Scope Non-Goals**:
  * No changes to DataStore keys (`tuning_dfa_a1_threshold_*`, `hrv_dfa_a1_zone_*`) or defaults ($0.75 / 0.50$).
  * No modification to other tabs (`HR_RUN`, `HR_BIKE`, `PWR_BIKE`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
* **Original Requirement ID & Target**: Refines `REQ-TRK-061.4` and `REQ-UI-526` (`ZonesSettingsActivity.kt` HRV tab).
* **Historical Origin & Commit Trace**: `ATT-7317` (`fce6777b`) introduced the 4th tab with two separate sliders as a quick MVP; `ATT-7524` added persistent zone display toggles; `ATT-7521` unified typography to `α1`.
* **Root Reason for Existing Formulation**: Individual sliders were adopted because standard single-value sliders were already used in `TuningFormControls.kt`; dual-thumb coordination was postponed.
* **Preservation of Core Invariants**: Invariant $T_1 \ge T_2 + 0.05f$ is strictly preserved during slider dragging and stepper clicks. DataStore keys and defaults are unchanged.

---

## 5. Change-Request Impact Analysis (SUP.10)
* **Affected Requirements**: Refines `REQ-TRK-061.4`, `REQ-UI-526`.
* **Affected Architecture Elements**: `ZonesSettingsActivity.kt`, `app/src/main/res/values*/strings_hrv.xml`.
* **Verification Impact & Test Surface**: `HrvDfaA1SettingsContractTest.kt`, `TranslationParityTest.kt`, `verify_requirement_governance.py`.
* **Risk Assessment & Invariants**: Low-to-Medium risk. Self-contained UI and resource refactoring. Zero database or protocol risk.

---

## 6. Living Documentation Plan
* Refine `REQ-TRK-061.4` in `docs/requirements/req_tracking_cockpit.md` and `TST-TRK-068.4` in `docs/tests/tst_tracking_cockpit.md` to document the unified 3-zone dual-thumb slider and localized string parity.
