# Stage 1 Analysis: ATT-8469 - Morgendlicher Check Start-Screen-Button & Zeit-Schieber bei reinem Wellness-Modus

**Ticket**: [ATT-8469](https://rainerblind.atlassian.net/browse/ATT-8469)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-11-16_03_Nacht`  
**Branch**: `feature/ATT-8469`  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`)  
**Date**: 2026-11-17  

---

## 1. Problem Statement & Motivation
During PO review of ATT-8117 on Pixel 10, a functional disconnect between *Morgenschlaf & Wohlbefinden* (`enableMorningSleepAndWellness`) and *Morgen-HRV-Check auf Startbildschirm* (`showMorningCheckOnStartScreen`) was uncovered:
1. `ControlTrackingViewModel.isMorningHrvCheckVisible` strictly requires `showMorningCheckOnStartScreen && hasPairedHeartRateDevices`. Athletes who track subjective sleep & wellness without a heart rate strap never see the Start screen CTA button.
2. `MorningCheckSection.kt` renders the cutoff hour slider (`morningCheckCutoffHour`) only if `showMorningCheckOnStartScreen == true`, omitting the slider when only *Morgenschlaf & Wohlbefinden* is enabled.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
- **Mechanism**:
  - `ControlTrackingViewModel.kt:184-199`: `isMorningHrvCheckVisible` gates visibility on `mode == TrackingMode.READY && tuning.showMorningCheckOnStartScreen && hasHrm && !completedToday && hour < tuning.morningCheckCutoffHour`.
  - `MorningCheckSection.kt:113-131`: Cutoff slider is wrapped in `if (showMorningCheckOnStartScreen)`.
- **Independent Peer Review & Verification**:
  - Code inspection confirms `TrackingTabsScreen.kt:1018` button text is `R.string.hrv_morning_check_start_button` ("Morgendlicher Check" / "Morning Check"), which is already mode-agnostic across all 9 locales.
  - Inspection of `MorningHrvCheckScreen.kt:794-823` and `MorningHrvCheckViewModel.kt:467-510` confirms standalone wellness saving (`saveStandaloneWellness()`) correctly writes `HrvReadinessEntry` with `isPrimaryForDay = true` to `TABLE_HRV_READINESS`.
  - `hasCompletedMorningCheck()` in `HrvReadinessDatabaseManager.kt:446` detects the entry, preserving the auto-hide contract once saved.

---

## 3. User Scope Grounding (ATT-1250)
- **In-Scope Goals**:
  1. Update `ControlTrackingViewModel.isMorningHrvCheckVisible` to show the CTA button if `TRACKING_MODE == READY`, `!todayMorningCheckCompleted`, `hour < cutoff`, AND `((showMorningCheckOnStartScreen && hasPairedHeartRateDevices) || enableMorningSleepAndWellness)`.
  2. Update `MorningCheckSection.kt` to display `TuningSliderItem` (cutoff hour) when `showMorningCheckOnStartScreen || enableMorningSleepAndWellness`.
  3. Expand `ControlTrackingViewModelTest` and `MorningCheckCutoffScreenshotTest` / tuning tests to verify reactive visibility for pure wellness mode without HRM.
- **Out-of-Scope Non-Goals (Scope Bounding)**:
  - Zero database schema migrations or changes to `HrvReadinessDbHelper` / `DevicesDbHelper`.
  - Zero changes to HRV math calculation corridors, artifact gating, or post-workout metrics.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit (REQ-PRO-022)
- **Original Requirement ID & Target**: `REQ-UI-525` (`REQ-UI-525.1`, `REQ-UI-525.4`) in `docs/requirements/req_ui_design_system.md`.
- **Historical Origin & Commit Trace**: Introduced in `ATT-7532` (`647ef674d06`), amended in `ATT-7740` (`6267d2c0`), relocated to `MorningCheckSection.kt` in `ATT-8117` (`f628f0a6`).
- **Root Reason for Existing Formulation**: Morning Check was originally conceived solely as an ECG/HRV strap measurement. `ATT-7034` subsequently introduced subjective sleep/wellness tracking, but Start screen visibility and cutoff controls remained tethered to HRM presence.
- **Preservation of Core Invariants**: Retains `TRACKING_MODE == READY`, `!todayMorningCheckCompleted`, `hour < morningCheckCutoffHour`, and cutoff range `6..11` (default `8`).

---

## 5. Change-Request Impact Analysis (SUP.10)
- **Affected Requirements**: `REQ-UI-525.1`, `REQ-UI-525.4`, `TST-UI-525.4`, `TST-UI-525.6`.
- **Affected Architecture Elements**: `ControlTrackingViewModel.kt`, `MorningCheckSection.kt`.
- **Verification Impact & Test Surface**: `ControlTrackingViewModelTest.kt`, `MorningCheckCutoffScreenshotTest.kt`, `PhysiologyModelsVisibilityContractTest.kt`.
- **Risk Assessment & Invariants**: LOW risk. Localized Compose UI gating and StateFlow predicate adjustment; zero database or background service impact.

---

## 6. Architectural Strategy & High-Level Solution
1. **ControlTrackingViewModel**: Update `isMorningHrvCheckVisible` predicate to `((tuning.showMorningCheckOnStartScreen && hasHrm) || tuning.enableMorningSleepAndWellness)`.
2. **MorningCheckSection**: Expand slider visibility check to `if (showMorningCheckOnStartScreen || enableMorningSleepAndWellness)`.
3. **Clean Architecture Guard**: Zero direct `*DatabaseManager` in UI layer (`RepositoryUiCleanlinessContractTest` verified).

---

## 7. System Invariants & Risk Assessment
- **Core Invariants**:
  1. Buttons auto-hide upon completion (`todayMorningCheckCompleted == true`) or cutoff hour.
  2. HRV chest-strap workflow is 100% unaffected when HRM is present.
  3. All 9 locales retain 100% translation parity.
- **Risk Rating**: **LOW** (Scope: 1 SP localized StateFlow predicate & Compose UI visibility refinement).
