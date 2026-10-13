# Stage 1: Problem Domain & Root Cause Analysis - ATT-3325: Replace persistent option with 10s option for navigation cue dismiss duration

**Ticket**: [ATT-3325](https://rainerblind.atlassian.net/browse/ATT-3325)  
**Sub-task**: [ATT-3367](https://rainerblind.atlassian.net/browse/ATT-3367) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://rainerblind.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.1`  
**Active Sprint**: `Sprint 2026-42.1`  
**Requirement Mapping**: `REQ-UI-287` (amending Clauses 1, 2, 3)  
**Test Mapping**: `TST-UI-247` (amending Clauses 1, 2)  
**Branch**: `feature/ATT-3325`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & Value Proposition

In the Advanced Settings -> Navigation menu (`NavigationSection.kt`), the auto-dismiss duration for turn maneuver prompt banners (`TurnPromptBanner.kt`) is configurable via filter chips representing durations: 2s, 3s, 4s (default), 5s, 8s, and "Dauerhaft" (Persistent / 0s).

Allowing navigation cue banners to stay permanently on screen ("Dauerhaft") indefinitely occludes the cockpit sensor grid and the map viewport until the next turn instruction occurs, degrading the in-ride tracking experience. Furthermore, athletes who desire longer banner visibility currently have to jump from 8s directly to permanent, lacking an intermediate duration such as 10s.

Removing the "Dauerhaft" option and replacing it with a 10s option:
1. Simplifies the interaction model and ensures that turn banners always dismiss cleanly.
2. Prevents indefinite occlusion of cockpit metrics and map navigation overlays.
3. Provides a well-balanced duration progression (`2s, 3s, 4s, 5s, 8s, 10s`).
4. Eliminates special-casing for `sec == 0` across UI chips, section subtitles, and countdown timers.

---

## 2. Forensic Investigation & Current System Architecture

1. **Options Definition in `TuningPreferencesDefaults` (`TuningPreferencesDataStore.kt:127`)**:
   * Currently defined as:
     ```kotlin
     val NAVIGATION_CUE_DISMISS_OPTIONS = listOf(2, 3, 4, 5, 8, 0)
     ```
   * Changing this constant to `listOf(2, 3, 4, 5, 8, 10)` updates the allowed option set for all consumers.

2. **DataStore Read, Fallback, and Write Validation (`TuningPreferencesDataStore.kt:507-509, 640-644, 714-718`)**:
   * DataStore reads evaluate:
     ```kotlin
     navigationCueDismissDurationSec = (prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] ?: TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC).let {
         if (it in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS) it else TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
     }
     ```
   * Because validation checks `it in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS`, any legacy user profile with `0` ("Dauerhaft") stored will cleanly and automatically fall back to `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC` (4s) without requiring manual database migration or crashing!
   * Preference write mutator `updateNavigationCueDismissDurationSec(durationSec: Int)` likewise enforces `if (durationSec in NAVIGATION_CUE_DISMISS_OPTIONS) durationSec else DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC`.

3. **UI Chip Selector & Value Header in `NavigationSection.kt:116-120, 143-156`**:
   * In `NavigationSection.kt`, the value header currently has a special check:
     ```kotlin
     text = if (navigationCueDismissDurationSec == 0) {
         stringResource(R.string.tuning_nav_cue_dismiss_persistent)
     } else {
         "${navigationCueDismissDurationSec}s"
     }
     ```
   * The chips in `FlowRow` also special-case `sec == 0`:
     ```kotlin
     val chipText = if (sec == 0) {
         stringResource(R.string.tuning_nav_cue_dismiss_persistent)
     } else {
         "${sec}s"
     }
     ```
   * With options strictly positive integers (`2, 3, 4, 5, 8, 10`), both can be simplified to uniform `"${sec}s"` formatting.

4. **Subtitle Formatter in `AdvancedTuningAccordion.kt:108-120`**:
   * `TuningSubtitleFormatter.formatNavigationSubtitle` currently checks `if (dismissSec == 0) "Persistent" else "${dismissSec}s"`.
   * When updated, it evaluates `dismissSec in NAVIGATION_CUE_DISMISS_OPTIONS` and formats `"${effectiveDismiss}s, ${radiusStr}"`.

5. **Banner Countdown in `TurnPromptBanner.kt:73-76`**:
   * Currently:
     ```kotlin
     if (dismissDurationSec > 0 && (navigationState.isApproaching || navigationState.isOffRoute)) {
         delay(dismissDurationSec * 1000L)
         isVisible = false
     }
     ```
   * With `dismissDurationSec` always $\in \{2, 3, 4, 5, 8, 10\}$, all values are $> 0$. A duration of 10s simply delays 10,000 ms, smoothly auto-dismissing after 10 seconds.

6. **9-Language String Resources (`strings.xml`)**:
   * `tuning_nav_cue_dismiss_persistent` can be deprecated or safely removed across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.

---

## 3. Chesterton's Fence Archaeology (`REQ-PRO-022`)

1. **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*), amending Clauses 1, 2, 3.
2. **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`), and amended in Sprint 2026-42.1 (`ATT-3321` and `ATT-3322`).
3. **Root Reason for Existing Formulation**: In ATT-2632, the `0` ("Dauerhaft") option was included as a catch-all for athletes who might want instructions to never disappear. However, in practice, navigation banners permanently occlude the cockpit and map viewport until the next turn instruction, hiding metrics and distracting the athlete. The longest practical duration athletes requested for difficult navigations is 10 seconds, which gives ample reading time while guaranteeing clean auto-dismissal.
4. **Preservation of Core Invariants**:
   * Default dismiss duration remains `4`s (`DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC`).
   * Factory reset (`resetToDefaults()`) restores `4`s.
   * Legacy profile fallback: Stored `0` safely coerces to `4`s without user intervention.
   * Turn prompt auto-dismiss animation, cue triggering math, countdown distance (30m–300m), and 100% full-suite unit test pass rate remain strictly preserved.

---

## 4. Scope Bounding (`ATT-1250`)

* **In-Scope**:
  * Update `NAVIGATION_CUE_DISMISS_OPTIONS` to `listOf(2, 3, 4, 5, 8, 10)`.
  * Ensure DataStore fallback handles legacy `0` gracefully to default `4`s.
  * Clean up `NavigationSection.kt` and `AdvancedTuningAccordion.kt` to format chip labels and subtitles uniformly.
  * Update unit tests (`TuningConfigTest.kt`, `AdvancedTuningAccordionTest.kt`).
  * Remove `tuning_nav_cue_dismiss_persistent` across all 9 language directories and verify localization parity.
* **Out-of-Scope**:
  * Modifying turn cue triggering thresholds or GPS countdown logic (`TurnByTurnNavigationEngine.kt`).
  * Adding custom duration text inputs.

---

## 5. Risk Assessment & Mitigation

| Risk | Impact | Mitigation |
| :--- | :--- | :--- |
| Existing user profile with `0` stored enters invalid state | Low | DataStore read logic already checks `if (it in NAVIGATION_CUE_DISMISS_OPTIONS) it else DEFAULT...`, guaranteeing immediate fallback to 4s. |
| Test failures expecting `[2, 3, 4, 5, 8, 0]` | Low | Synchronously update `TuningConfigTest.kt` and `AdvancedTuningAccordionTest.kt`. |
| Localization divergence | Low | Remove `tuning_nav_cue_dismiss_persistent` simultaneously across all 9 `strings.xml` files and run `TranslationParityTest`. |
