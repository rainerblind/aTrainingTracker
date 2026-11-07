# Stage 1 Analysis: ATT-5820 - Auto-Pause (AutoPauseTabsScreen): Schwellenwert- und Verzögerungs-Slider standardmäßig einklappen

**Ticket**: [ATT-5820](https://rainerblind.atlassian.net/browse/ATT-5820)  
**Sub-task**: [ATT-5891](https://rainerblind.atlassian.net/browse/ATT-5891) (`[Analysis]`)  
**Parent Epic**: [ATT-355](https://rainerblind.atlassian.net/browse/ATT-355) (*Good and consistent UI*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-45.12`  
**Branch**: `feature/ATT-5820`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-11-07  

---

## 1. Problem Statement & Motivation
In `AutoPauseTabsScreen.kt`, each sport tab (Cycling, Running, Other Sports) immediately renders three technical calibration sliders (`speedThreshold`, `debounceSec`, `resumeDebounceSec`) openly inside the primary card whenever Auto-Pause is enabled.  
This clutters the UI and pushes other controls off-screen. In accordance with `docs/design_guidelines.md` (Section 3: *Settings & Accordion Structure*), primary screens must emphasize top-level switches, bundling granular calibration sliders into an initially collapsed accordion section ("Schwellenwerte & Verzögerung anpassen") displaying active values in its header subtitle.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Root Cause**: `AutoPauseSportTabContent` currently renders `TuningSliderItem` directly inside the main `Column` without encapsulation when `enabled == true`. No collapsible container or subtitle summary was introduced during initial screen construction (`ATT-4836` / `ATT-4924`).
* **Independent Peer Review & Verification**:
  * Hypothesis Verified: Codebase audit of `AutoPauseTabsScreen.kt:323-364` confirms that the three sliders are rendered unconditionally when `enabled == true`, without an accordion wrapper.
  * Visual Pattern Harmony: `AdvancedTuningAccordion.kt` provides `TuningAccordionSection` with Material 3 styling (`12.dp` rounded corners, `surfaceVariant` at 35% alpha, animated chevron rotation, and expand/shrink vertical transitions), perfectly aligned with Design Guidelines Section 3.
  * Subtitle Dynamic Summary: `TuningSubtitleFormatter` provides precedent for concise live subtitles (e.g. `formatNavigationSubtitle`). A dedicated formatter will display active speed and debounce values (e.g. `"3 km/h · Pause: 5 s · Fortsetzen: 2 s"`).

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Encapsulate the 3 calibration sliders inside a collapsible `TuningAccordionSection` in `AutoPauseTabsScreen.kt`.
  * Maintain default-collapsed state (`isExpanded = false`) per sport tab via `rememberSaveable`.
  * Render dynamic active values summary in the accordion subtitle across unit modes (metric/imperial).
  * Add localized strings for the accordion header and subtitle template with 100% parity across all 9 locales.
  * Update architectural contracts in `AutoPauseTabsScreenContractTest.kt` to verify accordion structure and default collapsed state.
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * No changes to auto-pause detection algorithms (`AutoPauseManager`), data storage schema, or default calibration values.
  * No alterations to the top fixed header or global Start-in-Auto-Pause toggle card.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-SET-080` (`REQ-SET-080.3` / `REQ-SET-080.6`) in `docs/requirements/req_settings_profiles.md`. Targets `AutoPauseTabsScreen.kt` and `strings.xml`.
* **Historical Origin & Commit Trace**: `ATT-4836` (commit `69e9d6d3`, Sprint 2026-44.1) and `ATT-4924` (Sprint 2026-44.2).
* **Root Reason for Existing Formulation**: Sliders were placed openly when the dedicated screen was first separated from Advanced Tuning. The UX layout was functional but lacked progressive disclosure.
* **Preservation of Core Invariants**: Instant DataStore persistence, slider step resolutions, per-sport reset-to-defaults, and reactive UI updates are 100% preserved.

---

## 5. Architectural Strategy & High-Level Solution
1. **Component Reuse**: Utilize `TuningAccordionSection` from `ui.settings.tuning` inside `AutoPauseSportTabContent` when `enabled == true`.
2. **State Management**: Introduce `var isSlidersExpanded by rememberSaveable { mutableStateOf(false) }` within each sport tab page.
3. **Subtitle Formatting**: Implement `TuningSubtitleFormatter.formatAutoPauseSubtitle(speedThreshold, speedUnit, debounceSec, resumeDebounceSec, context)` returning formatted active values.
4. **Localization**: Define `tuning_auto_pause_custom_thresholds_title` ("Schwellenwerte & Verzögerung anpassen" / "Customize thresholds & delays") and format strings across all 9 languages in `strings.xml`.
5. **Testing**: Expand `AutoPauseTabsScreenContractTest.kt` to assert `TuningAccordionSection` binding, slider encapsulation, and default collapsed state.

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in DataStore persistence (`TuningPreferencesDataStore`) and auto-pause engine (`AutoPauseManager`).
  2. Full compliance with Design Guidelines Section 3 (*Settings & Accordion Structure*) and AMOLED theme contrast.
  3. 100% string translation parity across all 9 supported application locales.
* **Risk Rating**: **LOW** (UI layout refinement and progressive disclosure using established `TuningAccordionSection` without data model or backend logic changes).
