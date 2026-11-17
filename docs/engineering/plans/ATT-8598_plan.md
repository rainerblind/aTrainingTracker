# Stage 3: Implementation Plan - ATT-8598: Pure Wellness Mode on MorningHrvCheckScreen

**Ticket**: [ATT-8598](https://rainerblind.atlassian.net/browse/ATT-8598) | **Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-17_02_Spät` | **Branch**: `feature/ATT-8598`  
**Requirement Mapping**: `REQ-HRV-043` (`REQ-HRV-043.1`..`REQ-HRV-043.4`) | **Test Mapping**: `TST-HRV-043` (`TST-HRV-043.1`..`TST-HRV-043.5`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-17  

---

## 1. Problem Description & Background
When `showMorningCheckOnStartScreen = false` and `enableMorningSleepAndWellness = true`, tapping the Start-screen *"Morning Check"* button (`ATT-8469`) opens `MorningHrvCheckScreen`, which unconditionally displays HRV protocol/posture/duration/pacing cards, HRV TopAppBar title/subtitle/info icon, and the *Start Measurement* CTA above `MorningWellnessInputCard`. Stage 4 implements `REQ-HRV-043` so pure wellness mode hides all HRV measurement chrome while keeping HRV-enabled, Athlete Profile fallback, and active-measurement paths intact.

## 2. Traceability & Requirements Mapping
- `REQ-HRV-043.1` <-> `TST-HRV-043.1`: `MorningHrvCheckViewModel.kt` -> `MorningHrvCheckViewModelTest.kt` (`showMorningCheckOnStartScreen` reactive flow + `saveStandaloneWellness()`).
- `REQ-HRV-043.2` <-> `TST-HRV-043.2`: `MorningHrvCheckScreen.kt` -> `MorningHrvCheckScreenTest.kt` (pure wellness TopAppBar, hidden HRV cards/info/Start, promoted primary `Save Morning Wellness`, empty-state hint).
- `REQ-HRV-043.3` <-> `TST-HRV-043.3`: `MorningHrvCheckScreen.kt` -> `MorningHrvCheckScreenTest.kt` (enabled HRV mode, `!enableMorningSleepAndWellness` fallback, `phase != READY` protection).
- `REQ-HRV-043.4` <-> `TST-HRV-043.4` & `TST-HRV-043.5`: 9-locale `strings_hrv.xml` + Light/Dark/AMOLED `@Preview`s -> `TranslationParityTest.kt`, `ThemeColorCentralizationAuditTest.kt`, PNG `read_file`.

## 3. System Invariants & Preserved Behavior
1. **Gate 3 Code Lock**: Zero edits to `app/` or `strings_hrv.xml` until Gate 3 passes.
2. **Layering & Submodule Hygiene**: Zero `*DatabaseManager` in `ui/`; `ANT-Android-SDKs` untouched at `0e8cea71`; `ControlTrackingViewModel` & `MorningCheckSection` untouched.
3. **Persistence & Lifecycle**: Standalone wellness saves `rmssdMs = null`, `isPrimaryForDay = true`; `keepScreenOn` active in `STABILIZING`/`MEASURING`; `AthleteProfileScreen` fallback preserved.

## 4. Proposed Architectural Changes
- **Component 1 (`MorningHrvCheckViewModel.kt`)**: Add `val showMorningCheckOnStartScreen: Boolean = TuningPreferencesDefaults.DEFAULT_SHOW_MORNING_CHECK_ON_START_SCREEN` to `MorningHrvUiState`, updated in `tuningDataStore.tuningConfigFlow.collect`; extend `setWellnessTogglesForTesting` with optional `showMorningCheckOnStartScreen` default param.
- **Component 2 (`MorningHrvCheckScreen.kt`)**: Extract stateless `MorningHrvCheckContent(uiState, onNavigateBack, ...)`; compute `isPureWellnessMode = !uiState.showMorningCheckOnStartScreen && uiState.enableMorningSleepAndWellness && uiState.phase == MorningHrvPhase.READY`. Gate TopAppBar title/subtitle/info action, HRV cards (`WakingProtocolChecklistCard`, `PostureAndDurationCard`, `MeasurementPacingCard`), `ActionButtonsSection` primary `Button`, and empty-state hint inside `MorningWellnessInputCard`.
- **Component 3 (`app/src/main/res/values*/strings_hrv.xml`)**: Add `morning_hrv_wellness_subtitle` and `morning_hrv_wellness_empty_hint` across all 9 locales (zero emojis).
- **UI Consistency (Rule 23 — `docs/design_guidelines.md`)**:
  - *Reference screen / component*: `MorningHrvCheckScreen.kt` existing Material 3 `TopAppBar`, `Card(RoundedCornerShape(12.dp))`, and `Button`/`OutlinedButton` (`heightIn(min = 52.dp)`).
  - *Theme tokens*: `MaterialTheme.colorScheme.surface` / `onSurface` / `onSurfaceVariant`, `TTColor.ReadinessOptimal` for saved state, `16.dp` spacing scale (`§5.2`).
  - *New one-off styles*: None. Light, Dark, and AMOLED `@Preview` functions added for `MorningHrvCheckContent` (`§4`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (9-Locale Strings — `REQ-HRV-043.4`)**: Add `morning_hrv_wellness_subtitle` and `morning_hrv_wellness_empty_hint` to all 9 `app/src/main/res/values*/strings_hrv.xml` files.
2. **Step 2 (`MorningHrvCheckViewModel.kt` — `REQ-HRV-043.1`)**: Expose `showMorningCheckOnStartScreen` in `MorningHrvUiState` from `tuningConfigFlow` and update `setWellnessTogglesForTesting`.
3. **Step 3 (`MorningHrvCheckScreen.kt` — `REQ-HRV-043.2`, `REQ-HRV-043.3`, `REQ-HRV-043.4`)**: Extract `MorningHrvCheckContent`, implement `isPureWellnessMode` gating + fallbacks + empty-state hint + promoted primary `Save Morning Wellness` `Button` (green saved state), and add Light/Dark/AMOLED `@Preview`s.
4. **Step 4 (Unit & Compose UI Tests — `TST-HRV-043.1..4`)**: Add ViewModel test in `MorningHrvCheckViewModelTest.kt`; add `MorningHrvCheckScreenTest.kt` verifying pure wellness, empty-state, HRV+Wellness, Profile fallback, active measurement, and Light/Dark PNG capture (`docs/engineering/walkthroughs/img/ATT-8598_morning_wellness_mode.png`).
5. **Step 5 (Gradle Execution & Visual Audit — `TST-HRV-043.5`)**:
   - Symlink `local.properties` (and root Gradle config if needed, never symlinking `ANT-Android-SDKs`) into worktree, then run under lock:
     `python3 /home/rainer/AndroidStudioProjects/aTrainingTracker/tools/parallel_sprint_lock.py -- ./gradlew :app:testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.hrv.MorningHrvCheckScreenTest" --tests "com.atrainingtracker.trainingtracker.build.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.ThemeColorCentralizationAuditTest"`
   - Inspect captured Light/Dark PNG via `read_file` (`Rule 8.5`).

## 6. Verification & Rollback Plan
- **Verification**: 100% pass rate on Tier 1 suite (`TST-HRV-043.1..5`) + `Rule 8.5` PNG inspection + Stage 5 clean-room verification & emulator UI smoke check.
- **Rollback**: Isolated `feature/ATT-8598` branch enables instant clean revert without impacting `develop` or `sprint/2026-11-17_02_Spät`.
