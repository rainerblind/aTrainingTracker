# Stage 2: Requirement & Test Specification - ATT-8469: Morning Check Wellness Mode Gating

**Ticket**: [ATT-8469](https://rainerblind.atlassian.net/browse/ATT-8469)  
**Sub-task**: [[Req & Test Spec]](https://rainerblind.atlassian.net/browse/ATT-8469)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8469`  
**Requirement Mapping**: `REQ-UI-525` (`REQ-UI-525.1`, `REQ-UI-525.4`) | **Test Spec ID**: `TST-UI-525` (`TST-UI-525.4`, `TST-UI-525.5`, `TST-UI-525.6`)  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Requirement Specification (`REQ-UI-525.1`, `REQ-UI-525.4` Refinement)

### 1.1 Problem Statement & Rationale
Athletes tracking subjective sleep and wellness without a heart rate strap (`enableMorningSleepAndWellness == true`) currently cannot access the Morning Check CTA button on the Start screen (`ControlTrackingScreen`) because `isMorningHrvCheckVisible` strictly requires paired HRM hardware and `showMorningCheckOnStartScreen`. Additionally, the cutoff hour slider (`morningCheckCutoffHour`) in `MorningCheckSection.kt` is omitted when only wellness tracking is enabled.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Functional | **Verification Method**: Unit

### 1.3 Functional & Architectural Requirements
1. **Start-Screen CTA Visibility (`REQ-UI-525.4`)**: `ControlTrackingViewModel.isMorningHrvCheckVisible` SHALL evaluate to `true` when `TrackingMode == READY`, `!todayMorningCheckCompleted`, `timeProvider().hour < morningCheckCutoffHour`, AND `((showMorningCheckOnStartScreen && hasPairedHeartRateDevices) || enableMorningSleepAndWellness)`.
2. **Cutoff Slider Availability (`REQ-UI-525.1`)**: `MorningCheckSection.kt` SHALL render `TuningSliderItem` for `morningCheckCutoffHour` whenever `showMorningCheckOnStartScreen || enableMorningSleepAndWellness` is true.
3. **Single Daily Completion & Auto-Hide**: Saving standalone wellness via `saveStandaloneWellness()` writes an entry to `TABLE_HRV_READINESS`, immediately setting `todayMorningCheckCompleted = true` and auto-hiding the CTA button on return to Start screen.
4. **Clean Architecture & Layering**: Zero direct `*DatabaseManager` in UI layer (`RepositoryUiCleanlinessContractTest` verified).

### 1.4 Acceptance Criteria (Given-When-Then)
* *Given* `enableMorningSleepAndWellness == true` and zero paired HRM devices in `TrackingMode.READY` before cutoff hour, *When* `ControlTrackingScreen` renders, *Then* the "Morgendlicher Check" CTA button is visible.
* *Given* an athlete completes today's check (HRV or standalone wellness), *When* returning to `ControlTrackingScreen`, *Then* the CTA button is hidden.
* *Given* `enableMorningSleepAndWellness == true && !showMorningCheckOnStartScreen`, *When* viewing `MorningCheckSection`, *Then* the cutoff hour slider is visible.
* *Given* all 9 locales (`EN, DE, ES, FR, IT, JA, NL, PL, PT`), *When* reading `hrv_morning_check_start_button` and Morning Check tuning strings, *Then* 100% translation parity is maintained.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-525` (`REQ-UI-525.1`, `REQ-UI-525.4`) in `docs/requirements/req_ui_design_system.md`.
* **Historical Origin & Commit Trace**: Introduced in `ATT-7532` (`647ef674d06`), amended in `ATT-7740` (`6267d2c0`), relocated to `MorningCheckSection.kt` in `ATT-8117` (`f628f0a6`).
* **Root Reason for Existing Formulation**: Morning Check was originally tied strictly to chest-strap HRV measurements.
* **Preservation of Core Invariants**: Retains `READY` mode guard, cutoff ceiling `6..11` (default `8`), daily single-completion auto-hide, zero DB schema migration, and strict UI repository decoupling.

---

## 2. Test Specification (`TST-UI-525`)

1. **`TST-UI-525.4` (`ControlTrackingViewModelTest.kt`)**:
   - `isMorningHrvCheckVisible_whenWellnessModeWithoutHrm_emitsTrue()`: Assert CTA visible with `enableMorningSleepAndWellness = true`, no HRM paired, before cutoff.
   - `isMorningHrvCheckVisible_whenStandaloneWellnessSaved_autoHides()`: Assert CTA hides when `hasCompletedMorningCheck(today)` returns `true`.
2. **`TST-UI-525.6` (`MorningCheckCutoffScreenshotTest.kt`, Rule 8.5)**:
   - Render `MorningCheckSection` with `enableMorningSleepAndWellness = true && showMorningCheckOnStartScreen = false`; assert cutoff slider is visible.
3. **`TST-UI-525.5` (`TranslationParityTest.kt`)**:
   - Verify `hrv_morning_check_start_button` exists across all 9 locales with 100% parity.
4. **`TST-UI-525.7` (Targeted Regression Suite)**:
   - Run `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.MorningCheckCutoffScreenshotTest" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"`.

---

## 3. Traceability Matrix

| Test Case | Scope | Target / Assertion | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `[TST-UI-525.4]` | Unit | `ControlTrackingViewModelTest.kt`: pure wellness gating without HRM & completion auto-hide | `REQ-UI-525.4` | Planned |
| `[TST-UI-525.5]` | Localization | `TranslationParityTest.kt`: all 9 locales verified for `hrv_morning_check_start_button` | `REQ-UI-525.5` | Planned |
| `[TST-UI-525.6]` | Screenshot | `MorningCheckCutoffScreenshotTest.kt`: slider visible when pure wellness enabled | `REQ-UI-525.1` | Planned |
| `[TST-UI-525.7]` | Regression | `./gradlew testDebugUnitTest` on affected UI ViewModels and Cleanliness contracts | `REQ-PRO-001` | Planned |
