# Stage 3: Implementation Plan - ATT-3450: Replace navigation cue dismiss duration chips with integer slider from 1s to 10s

**Ticket**: [ATT-3450](https://atrainingtracker.atlassian.net/browse/ATT-3450)  
**Sub-task**: [ATT-3459](https://atrainingtracker.atlassian.net/browse/ATT-3459) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `2026-42.2`  
**Requirement Mapping**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*)  
**Test Mapping**: `TST-UI-247`  
**Branch**: `feature/ATT-3450`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Problem Description & Background

In the Advanced Settings navigation accordion (`NavigationSection.kt`), auto-dismiss duration for turn cues is currently selected via a `FlowRow` of 6 `FilterChip` elements (`2s`, `3s`, `4s`, `5s`, `8s`, `10s`). On mobile device viewports, having 6 chips causes the final `10s` chip to wrap onto an isolated second row, creating unbalanced whitespace and violating `docs/design_guidelines.md` § 1.4 (*Discrete Scalar Ranges & Durations*).

This plan outlines the atomic, step-by-step refactoring to replace the `FilterChip` row with a `TuningSliderItem` supporting continuous 1s increments from 1s to 10s (default 4s), update DataStore boundary clamping, and verify UI layout contracts without impacting runtime navigation cue display logic in `TurnPromptBanner.kt`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*)
  - Clause 1: `TuningPreferencesDefaults` defining `MIN = 1`, `MAX = 10`, `DEFAULT = 4`, `STEP = 1`, and `OPTIONS = (1..10).toList()`; range clamping `[1, 10]`; legacy `0` fallback to `4`.
  - Clause 2: DataStore and UI state mapping reflects valid clamped value immediately upon initialization without rendering inconsistent state.
  - Clause 3: Composable `NavigationSection.kt` renders `TuningSliderItem` (1s..10s, steps = 8, 1s increments, default 4s) with zero `FilterChip` items.
  - Clause 5: 100% 9-language localization parity across all supported application locales.
* **Test Mapping**: `TST-UI-247`
  - `[TST-UI-247.1]`: Unit test verifying default constants and range bounds in `TuningPreferencesDataStoreDismissDurationTest.kt`.
  - `[TST-UI-247.2]`: Unit test verifying boundary clamping and legacy zero fallback in `TuningPreferencesDataStoreDismissDurationTest.kt`.
  - `[TST-UI-247.3]`: Localization parity verification in `TranslationParityTest.kt`.
  - `[TST-UI-247.4]`: Clean-room full test suite regression (`./gradlew testDebugUnitTest`).
  - `[TST-UI-247.5]`: UI visual and structural contract test in `AdvancedTuningVisualContractTest.kt`.

---

## 3. System Invariants & Preserved Behavior

1. **Preference Key Backward Compatibility**: Preference key `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` remains completely unchanged.
2. **Factory Reset Determinism**: Invoking `resetToDefaults()` restores duration to exactly 4s.
3. **Runtime HUD Timer Integrity**: `TurnPromptBanner.kt` consumes `dismissDurationSec: Int` without modifications to countdown/animation internals.
4. **Immediate UI Consistency**: If an invalid or legacy value is loaded, it is clamped to `[1, 10]` immediately upon flow emission so the UI slider never renders an out-of-bounds state.
5. **Zero Unintended Regressions**: All existing unit and visual contract tests pass cleanly.
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `TuningPreferencesDefaults` & `TuningPreferencesDataStore`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* Changes:
  - Add `const val MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`
  - Update `const val MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC = 10` (currently 10)
  - Add `const val STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`
  - Maintain `const val DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4`
  - Update `NAVIGATION_CUE_DISMISS_OPTIONS = (MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC..MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC).toList()`
  - Update DataStore flow mapping:
    ```kotlin
    val rawDismiss = prefs[KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC] ?: TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
    val validatedDismiss = if (rawDismiss == 0) {
        TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC
    } else {
        rawDismiss.coerceIn(
            TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
            TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
        )
    }
    ```
  - Update `updateNavigationCueDismissDurationSec(durationSec: Int)` to clamp input to `[MIN, MAX]`.

### Component 2: `NavigationSection.kt` Composable
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
* Changes:
  - Replace the `Column` holding `FlowRow` of `FilterChip` items (lines 100–148) with:
    ```kotlin
    TuningSliderItem(
        title = stringResource(R.string.tuning_nav_cue_dismiss_duration_title),
        valueText = "${navigationCueDismissDurationSec}s",
        helperText = stringResource(R.string.tuning_nav_cue_dismiss_duration_desc),
        defaultText = stringResource(
            R.string.tuning_default_format,
            "${TuningPreferencesDefaults.DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC}s"
        ),
        value = navigationCueDismissDurationSec.toFloat(),
        onValueChange = { snapped ->
            onDismissDurationChange(
                snapped.roundToInt().coerceIn(
                    TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC,
                    TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC
                )
            )
        },
        valueRange = TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat()..TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat(),
        steps = 8
    )
    ```
  - Remove unused imports (`FilterChip`, `FlowRow` if not used elsewhere in `NavigationSection.kt`).

### UI Consistency (Rule 23 — mandatory for UI changes)
* **Reference screen / component**: `TuningSliderItem` in `NavigationSection.kt` (lines 151–167, Turn Cue Countdown Distance slider) and `CockpitTypographySection.kt`.
* **Reused components**: `com.atrainingtracker.trainingtracker.ui.settings.tuning.components.TuningSliderItem`.
* **Theme tokens**: Standard typography (`MaterialTheme.typography.bodyMedium`, `labelSmall`), color tokens (`MaterialTheme.colorScheme.onSurface`, `primary`, `outline`), and spacing tokens (`spacedBy(4.dp)`).
* **Slider step math**: `valueRange = 1.0f..10.0f` with `steps = 8` intermediate steps yields exactly `(10 - 1) / (8 + 1) = 9 / 9 = 1.0f` intervals, snapping strictly to integer seconds `1, 2, 3, 4, 5, 6, 7, 8, 9, 10`.
* **New one-off styles & justification**: None. Perfectly reuses established `TuningSliderItem` pattern.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Update DataStore Defaults & Validation (`TuningPreferencesDataStore.kt`)
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStore.kt`
* **Changes**:
  - Define `MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1` and `STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`.
  - Update `NAVIGATION_CUE_DISMISS_OPTIONS` to `(1..10).toList()`.
  - Update flow mapping to clamp non-zero values into `[1, 10]` and map legacy `0` to default `4`.
  - Update `updateNavigationCueDismissDurationSec` mutator to clamp input values.

### Step 2: Update DataStore Unit Tests (`TuningPreferencesDataStoreDismissDurationTest.kt`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreDismissDurationTest.kt`
* **Changes**:
  - Verify `MIN = 1`, `MAX = 10`, `DEFAULT = 4`, `OPTIONS = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)`.
  - Verify boundary clamping for `< 1`, `0`, values in `1..10`, and `> 10`.

### Step 3: Replace `FilterChip` / `FlowRow` in `NavigationSection.kt`
* **Files**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/categories/NavigationSection.kt`
* **Changes**:
  - Replace `Column` containing `FlowRow` of `FilterChip` with `TuningSliderItem` configured for 1s..10s, steps = 8, default 4s.
  - Clean up unused imports.

### Step 4: Add UI Visual & Structural Contract Test (`[TST-UI-247.5]`)
* **Files**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`
* **Changes**:
  - Add test method `testNavigationSection_dismissDurationUsesSliderContract()` verifying:
    - Presence of `TuningSliderItem` with `tuning_nav_cue_dismiss_duration_title`.
    - Presence of `steps = 8`.
    - Live value format `"${navigationCueDismissDurationSec}s"`.
    - Total absence of `FilterChip` in navigation cue dismiss duration section.

### Step 5: Execute Targeted Unit Tests & Localization Check
* **Commands**:
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningPreferencesDataStoreDismissDurationTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningVisualContractTest"`
  - `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.TranslationParityTest"`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - Run targeted test suite during construction.
  - Run full clean-room regression suite (`./gradlew testDebugUnitTest`) during Stage 5.
  - Author comprehensive walkthrough in `docs/engineering/walkthroughs/ATT-3450_walkthrough.md`.
* **Rollback Plan**:
  - Work is strictly isolated on branch `feature/ATT-3450`.
  - In case of failure or regression, the branch can be cleanly reset or reverted without impacting `develop` or `sprint/2026-42.2`.
