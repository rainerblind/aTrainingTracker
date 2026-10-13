# Stage 1 Analysis: ATT-3450 - Replace navigation cue dismiss duration chips with integer slider from 1s to 10s

**Ticket**: [ATT-3450](https://atrainingtracker.atlassian.net/browse/ATT-3450)  
**Sub-task**: [ATT-3457](https://atrainingtracker.atlassian.net/browse/ATT-3457) (`[Analysis]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `2026-42.2`  
**Branch**: `feature/ATT-3450`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Statement & Motivation

In Advanced Navigation Settings (`NavigationSection.kt`), the auto-dismiss duration for navigation cues currently renders using a `FlowRow` of `FilterChip` items (`2s`, `3s`, `4s`, `5s`, `8s`, `10s`). On standard Android mobile viewports (e.g. Google Pixel 10), horizontal space is insufficient to accommodate all 6 chips on a single row, forcing the final `10s` chip to wrap onto an isolated second row.

This layout wrapping violates the UI Design Guidelines ([`docs/design_guidelines.md`](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/docs/design_guidelines.md) § 1.4: *Discrete Scalar Ranges & Durations*), which specifically mandates:
> *"Do NOT use multi-chip rows (`FilterChip` in `FlowRow`) for large scalar sets. On mobile viewports, the trailing chip(s) frequently wrap onto an isolated second row, creating unbalanced whitespace and poor visual rhythm. Prefer an interactive slider with snap steps and a live value indicator."*

Replacing the `FilterChip` row with a discrete `TuningSliderItem` allows continuous 1s increments from 1s to 10s (default 4s), prevents wrapping, aligns visually with adjacent sliders (such as the Turn Cue Countdown Distance slider), and gives athletes full granular control over navigation prompt visibility.

---

## 2. Root Cause Analysis (Forensic Investigation)

### 2.1 Visual Defect Origin
During Sprint 2026-42.1 (ATT-3325), the legacy `0` option ("Dauerhaft") was replaced with `10s` to prevent indefinite cockpit occlusion. The option list was updated to `listOf(2, 3, 4, 5, 8, 10)` in `TuningPreferencesDefaults.kt`. Because 6 `FilterChip` elements with padding, text, and 8.dp horizontal spacing consume $\approx 360\text{dp}$, they exceed the available column width inside `AdvancedTuningDialog` and `AdvancedTuningScreen` after card margins and padding.

### 2.2 Architectural Analysis of Affected Call Sites
1. **Preferences DataStore Layer (`TuningPreferencesDataStore.kt`)**:
   - `TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4`.
   - `TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS = listOf(2, 3, 4, 5, 8, 10)` restricts saved values via strict set containment.
   - Validation logic:
     `prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] = if (config.navigationCueDismissDurationSec in TuningPreferencesDefaults.NAVIGATION_CUE_DISMISS_OPTIONS) ... else 4`.
   - Under the new slider requirement, validation must transition from discrete set membership to range boundary clamping:
     `val MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`
     `val MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC = 10`
     `config.navigationCueDismissDurationSec.coerceIn(1, 10)`.

2. **UI Layer (`NavigationSection.kt`)**:
   - Replace the `Column` holding `FlowRow` of `FilterChip` (lines 104–148) with `TuningSliderItem`.
   - Configure slider with:
     - `title`: `stringResource(R.string.tuning_nav_cue_dismiss_duration_title)`
     - `valueText`: `"${navigationCueDismissDurationSec}s"`
     - `helperText`: `stringResource(R.string.tuning_nav_cue_dismiss_duration_desc)`
     - `defaultText`: `stringResource(R.string.tuning_default_format, "4s")`
     - `value`: `navigationCueDismissDurationSec.toFloat()`
     - `onValueChange`: `{ onDismissDurationChange(it.roundToInt().coerceIn(1, 10)) }`
     - `valueRange`: `1.0f..10.0f`
     - `steps`: `8` (producing 9 discrete intervals with 1s steps: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10).

3. **Subtitle Formatter & Dialog Holders**:
   - `TuningSubtitleFormatter.formatNavigationSubtitle` already accepts `Int` seconds and formats `"${sec}s, ${radius} km"`, requiring no breaking contract changes.
   - `AdvancedTuningDialog.kt` and `AdvancedTuningScreen.kt` already store `navigationCueDismissDurationSec` as an integer state, binding seamlessly to `onDismissDurationChange: (Int) -> Unit`.

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  1. Define `MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`, `MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC = 10`, and `STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1` in `TuningPreferencesDefaults`.
  2. Update DataStore reading and writing validation in `TuningPreferencesDataStore.kt` to clamp values within `[1, 10]`.
  3. Replace the `FilterChip` / `FlowRow` block in `NavigationSection.kt` with `TuningSliderItem` (1s to 10s, 1s steps, default 4s).
  4. Update unit tests in `NavigationSectionTest.kt` and `TuningPreferencesDataStoreTest.kt` to verify slider interaction and DataStore range clamping.

* **Out-of-Scope Non-Goals (Scope Bounding)**:
  1. No changes to `TurnPromptBanner.kt` runtime countdown timer mechanics (it already consumes `dismissDurationSec: Int`).
  2. No changes to adjacent navigation settings (Route selection radius, Turn countdown distance, Off-route corridor).
  3. No changes to DataStore preference keys (`KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` remains identical).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**:
  - `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*, Clause 1 and Clause 2), targeting `TuningPreferencesDataStore.kt` and `NavigationSection.kt`.
* **Historical Origin & Commit Trace**:
  - Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`).
  - Modified in Sprint 2026-42.1 (`ATT-3325`, commit `f417f308`) to eliminate the "Dauerhaft" chip in favor of a 10s chip.
* **Root Reason for Existing Formulation**:
  - ATT-3325 replaced `0` with `10s` using the existing `FilterChip` pattern. However, expanding the set to 6 chips caused the `10s` chip to wrap onto a second row on standard device widths.
* **Preservation of Core Invariants**:
  - Default duration remains 4s.
  - All existing persisted values (2s, 3s, 4s, 5s, 8s, 10s) remain valid within `[1, 10]`.
  - Atomic factory reset restores 4s.
  - Runtime auto-dismiss timer behavior in `TurnPromptBanner.kt` remains completely intact.
  - 100% clean-room test pass rate.

---

## 5. Architectural Strategy & High-Level Solution

### 5.1 DataStore & Defaults (`TuningPreferencesDataStore.kt`)
```kotlin
object TuningPreferencesDefaults {
    // ...
    const val DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4
    const val MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1
    const val MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC = 10
    const val STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1
    val NAVIGATION_CUE_DISMISS_OPTIONS = (MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC..MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC).toList()
}
```
Validation during DataStore flow mapping:
```kotlin
navigationCueDismissDurationSec = (prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] 
    ?: TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC).let {
        if (it == 0) TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
        else it.coerceIn(TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC, TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC)
    }
```

### 5.2 Composable UI Replacement (`NavigationSection.kt`)
Replace `FlowRow` of `FilterChip` with:
```kotlin
TuningSliderItem(
    title = stringResource(R.string.tuning_nav_cue_dismiss_duration_title),
    valueText = "${navigationCueDismissDurationSec}s",
    helperText = stringResource(R.string.tuning_nav_cue_dismiss_duration_desc),
    defaultText = stringResource(R.string.tuning_default_format, "${TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC}s"),
    value = navigationCueDismissDurationSec.toFloat(),
    onValueChange = { snapped ->
        onDismissDurationChange(snapped.roundToInt().coerceIn(
            TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
            TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
        ))
    },
    valueRange = TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat()..TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat(),
    steps = 8
)
```

---

## 6. System Invariants & Risk Assessment

* **Core Invariants**:
  1. DataStore key `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` must be preserved for backward compatibility.
  2. Factory reset (`resetToDefaults()`) must restore exactly 4s.
  3. Legacy value `0` must continue to fall back safely to 4s.
  4. 100% clean-room test pass rate across unit tests and layout contracts.
  5. 100% 9-language localization parity.

* **Risk Rating**: **LOW**
  - *Justification*: Contained UI component substitution and DataStore range validation. Uses existing, proven `TuningSliderItem` component without modifying database schemas or service lifecycles.
