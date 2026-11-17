# Stage 3: Implementation Plan - ATT-8469: Morning Check Wellness Mode Gating

**Ticket**: [ATT-8469](https://rainerblind.atlassian.net/browse/ATT-8469) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8469`  
**Requirement Mapping**: `REQ-UI-525` (`REQ-UI-525.1`, `REQ-UI-525.4`)  
**Test Mapping**: `TST-UI-525` (`TST-UI-525.4`, `TST-UI-525.5`, `TST-UI-525.6`, `TST-UI-525.7`)  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Problem Description & Background
Athletes tracking subjective sleep and wellness without a heart rate sensor (`enableMorningSleepAndWellness == true`) are currently unable to launch the Morning Check from the Start screen (`ControlTrackingScreen`) because `ControlTrackingViewModel.isMorningHrvCheckVisible` requires `showMorningCheckOnStartScreen && hasPairedHeartRateDevices`. Furthermore, the cutoff slider in `MorningCheckSection.kt` is only rendered when `showMorningCheckOnStartScreen == true`.
This plan updates the reactive visibility predicate in `ControlTrackingViewModel` and expands the cutoff slider visibility condition in `MorningCheckSection`, while keeping existing HRV strap flows, daily completion auto-hide, and 9-locale translation parity completely intact.

## 2. Traceability & Requirements Mapping
* **Requirements**:
  - `REQ-UI-525.4`: CTA button visibility on Start screen for pure wellness mode (`((showMorningCheckOnStartScreen && hasPairedHeartRateDevices) || enableMorningSleepAndWellness)`).
  - `REQ-UI-525.1`: Cutoff hour slider available when `showMorningCheckOnStartScreen || enableMorningSleepAndWellness`.
* **Test Mapping**:
  - `TST-UI-525.4`: `ControlTrackingViewModelTest` (visibility with/without HRM, completion auto-hide).
  - `TST-UI-525.5`: `TranslationParityTest` (100% parity for `hrv_morning_check_start_button`).
  - `TST-UI-525.6`: `MorningCheckCutoffScreenshotTest` (slider visible in pure wellness mode).
  - `TST-UI-525.7`: Tier 1 regression suite (`ControlTrackingViewModelTest`, `MorningCheckCutoffScreenshotTest`, `RepositoryUiCleanlinessContractTest`).

## 3. System Invariants & Preserved Behavior
1. **Zero Premature Code Edits**: Strict Stage 3 Code Lock observed (zero Kotlin/XML changes prior to Gate 3 sign-off).
2. **Layering & Repository Decoupling**: Zero `*DatabaseManager` in `ui/` (`RepositoryUiCleanlinessContractTest` & `ArchitectureConformanceContractTest` compliant).
3. **Daily Auto-Hide Contract**: Saving a standalone wellness entry via `MorningHrvCheckViewModel.saveStandaloneWellness()` writes to `TABLE_HRV_READINESS`, setting `todayMorningCheckCompleted == true` and hiding the CTA on return to Start screen.
4. **9-Language String Parity**: `R.string.hrv_morning_check_start_button` ("Morgendlicher Check") is already mode-agnostic and 100% translated across all 9 locales.
5. **Cutoff Ceiling**: Slider range `6..11` (`DEFAULT = 8`, `MIN = 6`, `MAX = 11`) preserved.

## 4. Proposed Architectural Changes
* **Component 1 (`ControlTrackingViewModel.kt`)**: Update `isMorningHrvCheckVisible` combine predicate from `tuning.showMorningCheckOnStartScreen && hasHrm` to `((tuning.showMorningCheckOnStartScreen && hasHrm) || tuning.enableMorningSleepAndWellness)`.
* **Component 2 (`MorningCheckSection.kt`)**: Change `if (showMorningCheckOnStartScreen)` to `if (showMorningCheckOnStartScreen || enableMorningSleepAndWellness)`.
* **UI Consistency (`docs/design_guidelines.md`)**:
  - `MorningCheckSection.kt` maintains standard `TuningSliderItem` within `ExpertCategoryCard` (`TuningFormControls.kt`).
  - Start screen CTA button in `TrackingTabsScreen.kt` retains standard `OutlinedButton` with `RoundedCornerShape(8.dp)` and `R.string.hrv_morning_check_start_button`.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low Risk, 1 SP -> Flash executes Stage 4)**.
* **Step 1 (`ControlTrackingViewModel.kt`)**: Update `isMorningHrvCheckVisible` predicate to support `tuning.enableMorningSleepAndWellness`, updating KDoc (traces to `REQ-UI-525.4`).
* **Step 2 (`MorningCheckSection.kt`)**: Update cutoff slider guard to `if (showMorningCheckOnStartScreen || enableMorningSleepAndWellness)` (traces to `REQ-UI-525.1`).
* **Step 3 (`ControlTrackingViewModelTest.kt`)**: Add unit test methods (`// TST-UI-525`) asserting CTA button visibility when `enableMorningSleepAndWellness == true` without HRM and auto-hiding when `hasCompletedMorningCheck` is true.
* **Step 4 (`MorningCheckCutoffScreenshotTest.kt`, Rule 8.5)**: Add screenshot test case rendering `MorningCheckSection` with `enableMorningSleepAndWellness = true && showMorningCheckOnStartScreen = false`, generating visual PNG evidence and inspecting via `read_file`.
* **Tier 1 Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.MorningCheckCutoffScreenshotTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.PhysiologyModelsVisibilityContractTest" --tests "com.atrainingtracker.trainingtracker.ui.RepositoryUiCleanlinessContractTest"
  ```

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit + screenshot test pass, clean-room verification in Stage 5.
* **Rollback**: Isolated git branch `feature/ATT-8469` allows instantaneous clean revert without affecting `develop`.
