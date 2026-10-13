# Stage 2: Requirement & Test Specification - ATT-3450: Replace navigation cue dismiss duration chips with integer slider from 1s to 10s

**Ticket**: [ATT-3450](https://atrainingtracker.atlassian.net/browse/ATT-3450)  
**Sub-task**: [ATT-3458](https://atrainingtracker.atlassian.net/browse/ATT-3458) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2564](https://atrainingtracker.atlassian.net/browse/ATT-2564) (*Navigation: Turn-by-Turn Guidance & Cockpit Prompts*)  
**Target Release**: `Sprint 2026-42.2`  
**Active Sprint**: `2026-42.2`  
**Requirement Mapping**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*)  
**Test Spec ID**: `TST-UI-247`  
**Branch**: `feature/ATT-3450`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-13  

---

## 1. Requirement Specification (Amending REQ-UI-287)

### 1.1 Problem Statement & Rationale
In Advanced Navigation Settings (`NavigationSection.kt`), the auto-dismiss duration for navigation cues is rendered using a `FlowRow` of `FilterChip` items (`2s`, `3s`, `4s`, `5s`, `8s`, `10s`). On mobile device viewports, having 6 chips causes the final `10s` chip to wrap onto an isolated second row, creating visual disharmony and violating `docs/design_guidelines.md` § 1.4 (*Discrete Scalar Ranges & Durations*).

Replacing the chips with an interactive `TuningSliderItem` allows smooth 1s adjustments between 1s and 10s (default 4s), eliminates line wrapping, and ensures visual consistency with adjacent settings sliders.

### Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirement ID & Target**: `REQ-UI-287` (*Navigation Cue Auto-Dismiss Duration, Countdown Distance & Expert Settings Configuration*, Clause 1 and Clause 2), targeting `TuningPreferencesDataStore.kt` and `NavigationSection.kt`.
* **Historical Origin & Commit Trace**: Introduced in Sprint 2026-41.2 (`ATT-2632`, commit `8e6bcfc5`); modified in Sprint 2026-42.1 (`ATT-3325`, commit `f417f308`) to replace legacy `0` ("Dauerhaft") with `10s`.
* **Root Reason for Existing Formulation**: ATT-3325 replaced `0` with `10s` using the existing `FilterChip` pattern. Expanding the set to 6 chips caused the `10s` chip to wrap onto a second row on mobile device widths.
* **Preservation of Core Invariants**: Default duration remains 4s. All existing persisted values (2s, 3s, 4s, 5s, 8s, 10s) remain valid within [1, 10]. Atomic factory reset restores 4s. Runtime auto-dismiss timer behavior in `TurnPromptBanner.kt` remains completely intact. 100% clean-room test pass rate.

### 1.2 Functional & Architectural Requirements
1. **Preference Defaults & Bounds (`TuningPreferencesDataStore.kt`)**:
   - The system SHALL define `MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`, `MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC = 10`, `STEP_NAVIGATION_CUE_DISMISS_DURATION_SEC = 1`, and `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC = 4` in `TuningPreferencesDefaults`.
   - `NAVIGATION_CUE_DISMISS_OPTIONS` SHALL evaluate to all integer values from 1 to 10 inclusive (`(1..10).toList()`).
2. **DataStore Range Clamping & Immediate UI Feedback**:
   - `TuningPreferencesDataStore` SHALL validate `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` using boundary clamping (`coerceIn(1, 10)`).
   - If a legacy profile stores `0` ("Dauerhaft"), the value SHALL automatically fall back to default `4`.
   - The DataStore and UI state mapping SHALL validate and reflect the clamped value immediately upon initialization or receipt of an out-of-bounds preference (e.g. legacy `0` or corrupted values), so the UI slider and live label immediately reflect a valid clamped value (`4s` for `< 1`, `10s` for `> 10`) without rendering out-of-bounds or inconsistent UI state.
3. **Composable UI Slider Integration (`NavigationSection.kt`)**:
   - `NavigationSection.kt` SHALL remove the `FlowRow` of `FilterChip` items and render a `TuningSliderItem` for "Navigation Cue Dismiss Duration".
   - The slider SHALL span `valueRange = 1.0f..10.0f` with `steps = 8`. (Note: In Jetpack Compose `Slider`, `steps` defines intermediate discrete anchor values between min and max; 8 steps across range `1.0f..10.0f` yield exactly 9 intervals of width 1.0f, ensuring deterministic snapping to integer values `1, 2, 3, 4, 5, 6, 7, 8, 9, 10`).
   - The live value label SHALL display `"${navigationCueDismissDurationSec}s"`.
   - The default indicator SHALL display `stringResource(R.string.tuning_default_format, "4s")`.
4. **Runtime HUD Invariant (`TurnPromptBanner.kt`)**:
   - Banners and navigation prompt cards SHALL continue to auto-dismiss when the configured duration (1 to 10 seconds) elapses without requiring code changes to banner animation internals.

### 1.3 Acceptance Criteria (Given-When-Then)
* **Criterion 1: Slider Rendered with Continuous 1s-10s Range**:
  * *Given* an athlete opens Advanced Settings and expands the "Navigation" accordion
  * *When* inspecting the "Anzeigedauer der Navigationshinweise" control
  * *Then* a `TuningSliderItem` is displayed spanning 1s to 10s with 1s steps, and no `FilterChip` items are rendered.
* **Criterion 2: Default Value Presentation**:
  * *Given* default settings or freshly reset preferences
  * *When* viewing the dismiss duration slider
  * *Then* the slider value indicator displays `4s` and the default label displays `Standard: 4s`.
* **Criterion 3: Granular Persistence Across 1s to 10s**:
  * *Given* an athlete drags the slider to any integer between 1s and 10s (e.g. `1s`, `6s`, `7s`, `9s`, or `10s`)
  * *When* saving or navigating back
  * *Then* the exact integer value is persisted to DataStore and restored upon next launch.
* **Criterion 4: Range Clamping for Out-of-Bounds Values & Immediate UI Feedback**:
  * *Given* an out-of-bounds integer in DataStore (e.g. `< 1` or `> 10`)
  * *When* preferences are parsed upon screen initialization
  * *Then* values `< 1` (including legacy `0`) fall back to default `4`, values `> 10` are clamped to `10`, and the UI slider immediately displays the clamped value.

### 1.4 System Invariants
1. Preference key `KEY_NAVIGATION_CUE_DISMISS_DURATION_SEC` remains unchanged, ensuring backward compatibility.
2. Factory reset (`resetToDefaults()`) restores duration to exactly 4s.
3. Live subtitle formatting via `TuningSubtitleFormatter.formatNavigationSubtitle` continues to format `"${sec}s, ${radius} km"`.
4. 100% clean-room test pass rate across unit tests and layout contracts.
5. 100% 9-language localization parity across all supported application locales.

---

## 2. Test Specification (TST-UI-247 Amended)

### Test Case 1: `testNavigationCueDismissDuration_sliderRangeAndDefaults` (`[TST-UI-247.1]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreDismissDurationTest.kt`
* **Preconditions**: `TuningPreferencesDefaults` and `TuningConfig` loaded.
* **Action**:
  1. Verify `MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC == 1`.
  2. Verify `MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC == 10`.
  3. Verify `DEFAULT_NAVIGATION_CUE_DISMISS_DURATION_SEC == 4`.
  4. Verify `NAVIGATION_CUE_DISMISS_OPTIONS == listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)`.
  5. Verify `TuningConfig().navigationCueDismissDurationSec == 4`.
* **Expected Result**: All assertions pass.

### Test Case 2: `testNavigationCueDismissDuration_clampingAndLegacyZero` (`[TST-UI-247.2]`)
* **Scope**: Unit Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/settings/TuningPreferencesDataStoreDismissDurationTest.kt`
* **Preconditions**: Test arbitrary inputs against DataStore validation logic.
* **Action**:
  1. Test value `0` (legacy fallback) -> coerces to `4`.
  2. Test negative values (`-5`) -> coerces to `4`.
  3. Test values `1..10` -> evaluates to exact input value.
  4. Test value `15` -> clamped to `10`.
* **Expected Result**: Exact boundary and default clamping verified.

### Test Case 3: 9-Language Localization & Parity Audit (`[TST-UI-247.3]`)
* **Scope**: Localization Parity Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/TranslationParityTest.kt`
* **Action**: Verify that string resources `tuning_nav_cue_dismiss_duration_title`, `tuning_nav_cue_dismiss_duration_desc`, and `tuning_default_format` are defined and consistent across:
  * `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`.
* **Expected Result**: 100% parity across all 9 locales with zero missing resources.

### Test Case 4: Clean-Room Full Suite Regression (`[TST-UI-247.4]`)
* **Scope**: Full Clean-Room Regression
* **Command**: `./gradlew testDebugUnitTest`
* **Expected Result**: 100% pass rate across all test targets.

### Test Case 5: Composable UI Structural & Visual Contract (`[TST-UI-247.5]`)
* **Scope**: UI Layout & Structural Contract Test
* **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/settings/tuning/AdvancedTuningVisualContractTest.kt`
* **Preconditions**: `NavigationSection.kt` source loaded.
* **Action**:
  1. Verify `NavigationSection.kt` invokes `TuningSliderItem` for navigation cue dismiss duration with `steps = 8`.
  2. Verify the slider's `valueRange` is bounded by `TuningPreferencesDefaults.MIN_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat()..TuningPreferencesDefaults.MAX_NAVIGATION_CUE_DISMISS_DURATION_SEC.toFloat()`.
  3. Verify that the live value text expression formatting evaluates to `"${navigationCueDismissDurationSec}s"`.
  4. Verify that `NavigationSection.kt` contains zero instances of `FilterChip` or `FlowRow` for dismiss duration selection.
* **Expected Result**: All structural UI contract assertions pass.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Method | Requirement Clause | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-247.1]` | Unit | `TuningPreferencesDefaults`, `TuningConfig` | `REQ-UI-287` Clause 1 | Specified |
| `[TST-UI-247.2]` | Unit | `TuningPreferencesDataStore` validation | `REQ-UI-287` Clause 1, 2 | Specified |
| `[TST-UI-247.3]` | Localization | `TranslationParityTest` | `REQ-UI-287` Clause 5 | Specified |
| `[TST-UI-247.4]` | Regression | Full unit test suite (`./gradlew testDebugUnitTest`) | `REQ-PRO-001` | Specified |
| `[TST-UI-247.5]` | UI Contract | `AdvancedTuningVisualContractTest.kt` / `NavigationSection.kt` | `REQ-UI-287` Clause 3 | Specified |
