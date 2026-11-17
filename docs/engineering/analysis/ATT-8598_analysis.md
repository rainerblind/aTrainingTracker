# Stage 1 Analysis: ATT-8598 - Hide HRV Measurement Cards in Pure Wellness Mode

**Ticket**: [ATT-8598](https://rainerblind.atlassian.net/browse/ATT-8598)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8598`  
**Author**: AI Agent 1 (`Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
In Expert Settings (*Morning Checks & Wellness*), when an athlete disables `showMorningCheckOnStartScreen = false` and enables `enableMorningSleepAndWellness = true`, `ATT-8469` (`241df3a3`) surfaces the *"Morning Check"* button on the Start screen. Tapping it opens `MorningHrvCheckScreen`, which still renders the full HRV measurement UI (protocol checklist, posture/duration selectors, live HR pacing card, HRV TopAppBar title/subtitle/info sheet, and *Start Measurement* CTA) above `MorningWellnessInputCard`. In pure wellness mode, only wellness inputs and a primary *Save Morning Wellness* CTA should appear.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Mechanism**:
  - `MorningHrvCheckViewModel.kt:72-100, 145-157`: `MorningHrvUiState` collects `enableMorningSleepAndWellness` and its 4 sub-toggles from `tuningConfigFlow`, but omits `showMorningCheckOnStartScreen`.
  - `MorningHrvCheckScreen.kt:142-176, 208-235, 773-825`: `TopAppBar`, `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, and `ActionButtonsSection` (`morning_hrv_btn_start`) render unconditionally regardless of `showMorningCheckOnStartScreen`.
- **Independent Peer Review & Verification**:
  - Ticket hypothesis verified against live `sprint/2026-11-17_02_Spät` code. Correction: `ActionButtonsSection` (`L778-792`) still renders the filled `Start Measurement` button above `OutlinedButton` `Save Morning Wellness` (not missing).
  - Side-effect check: `AthleteProfileScreen.kt:336-352` navigates to `NavRoutes.MORNING_HRV` via *Measure Morning HRV*. If both `showMorningCheckOnStartScreen` and `enableMorningSleepAndWellness` are `false`, HRV measurement must remain visible as fallback so the screen is never blank. Also, if `phase != MorningHrvPhase.READY` (active sensor measurement/completed), HRV UI must stay visible.
  - Edge case: If pure wellness mode has all 4 wellness sub-toggles off (`MorningHrvCheckScreen.kt:973-1095`), `MorningWellnessInputCard` should show an informative empty-state hint instead of a blank card.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Expose `showMorningCheckOnStartScreen` in `MorningHrvUiState` (derived `isPureWellnessMode = !showMorningCheckOnStartScreen && enableMorningSleepAndWellness && phase == MorningHrvPhase.READY`).
  2. Hide `WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`, `HrvInfoBottomSheet` info icon, and *Start Measurement* CTA in pure wellness mode; adapt TopAppBar title/subtitle and promote *Save Morning Wellness* to primary filled `Button` (green *Saved* state on completion).
  3. Extract stateless `MorningHrvCheckContent` for Compose UI tests + Light/Dark/AMOLED `@Preview` coverage (pure wellness, full HRV+wellness, active measurement) and maintain 9-language localization parity.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Zero changes to `ControlTrackingViewModel`, `MorningCheckSection`, `HrvReadinessDbHelper` schema, or HRV DSP math (2 SP complexity confirmed; no ticket split needed).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-HRV-040` (`Standalone No-Strap Morning Wellness Logging`) & `REQ-PER-032` (`REQ-PER-032.1`, `REQ-PER-032.6`) in `docs/requirements/req_aftermath_periods.md`; `REQ-UI-525` (`ATT-8469`) in `docs/requirements/req_ui_design_system.md`.
- **Historical Origin & Commit Trace**: `REQ-PER-032` introduced in `ATT-3556` (`dc034c08`), localized/scoped in `ATT-6719` (`d275b588`); `REQ-HRV-040` added in `ATT-7034` (`a77ce176`); Start-screen decoupling added in `ATT-8469` (`241df3a3`).
- **Root Reason for Existing Formulation**: `MorningHrvCheckScreen` was built HRV-first, with `MorningWellnessInputCard` layered in during `ATT-7034` without checking `showMorningCheckOnStartScreen`.
- **Preservation of Core Invariants**: Retains full HRV+Wellness layout when `showMorningCheckOnStartScreen == true`, keeps `AthleteProfileScreen` fallback when `!enableMorningSleepAndWellness`, preserves `keepScreenOn` during `STABILIZING`/`MEASURING`, and keeps standalone wellness persistence (`rmssdMs = null`, `isPrimaryForDay = true`).

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: `REQ-HRV-040` (`TST-HRV-040`), `REQ-PER-032.1`, `REQ-PER-032.6` (`TST-PER-040`).
- **Affected Architecture Elements**: `MorningHrvCheckViewModel.kt`, `MorningHrvCheckScreen.kt`, `strings_hrv.xml` (9 locales).
- **Verification Impact & Test Surface**: `MorningHrvCheckViewModelTest.kt`, new Compose Robolectric UI/screenshot test for `MorningHrvCheckScreen` (Light/Dark), `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`.
- **Risk Assessment & Invariants**: LOW risk. UI gating + ViewModel flow field only; zero SQLite schema, `TrackerService`, or `ANT-Android-SDKs` impact.

---

## 6. Architectural Strategy & High-Level Solution
1. **ViewModel (`MorningHrvCheckViewModel`)**: Add `showMorningCheckOnStartScreen: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN` to `MorningHrvUiState`, updated from `tuningConfigFlow`, with optional test param in `setWellnessTogglesForTesting`.
2. **Screen (`MorningHrvCheckScreen`)**: Compute `isPureWellnessMode = !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`. In pure wellness mode: TopAppBar uses `morning_hrv_wellness_title` + wellness subtitle and hides the HRV info icon; body renders only error banner + `MorningWellnessInputCard`; `ActionButtonsSection` renders *Save Morning Wellness* as primary filled `Button`.
3. **Testability & Previews**: Extract `MorningHrvCheckContent` and add Light/Dark/AMOLED `@Preview`s per `docs/design_guidelines.md` §4.

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Enabled HRV check (`showMorningCheckOnStartScreen = true`) renders identical HRV + Wellness layout and active sensor measurement (`STABILIZING`/`MEASURING`/`COMPLETED`).
  2. Standalone wellness save sets `isWellnessSaved = true`, shows toast, and triggers Start-screen button auto-hide on return (`REQ-UI-525.4`).
  3. 100% 9-language translation parity and `MaterialTheme.colorScheme` / `TTColor` token usage (`Rule 8.5` Light/Dark verified).
- **Risk Rating**: **LOW** (Localized 2 SP UI & StateFlow refinement).
