# Stage 5 Walkthrough: ATT-8469 - Morning Check Start-Screen-Button & Cutoff-Slider für reinen Wellness-Modus

**Ticket**: [ATT-8469](https://rainerblind.atlassian.net/browse/ATT-8469) | **Sub-task**: `[Test]` (Pending `submit-stage`)  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-11-16_03_Nacht` | **Branch**: `feature/ATT-8469`  
**Requirement Mapping**: `REQ-UI-525` (`REQ-UI-525.1`, `REQ-UI-525.4`)  
**Test Spec Mapping**: `TST-UI-525` (`TST-UI-525.4`, `TST-UI-525.5`, `TST-UI-525.6`, `TST-UI-525.7`)  
**Author**: AI Agent 1 (Implementer, `Model: "flash"`, `--as agent1`) | **Date**: 2026-11-17  

---

## 1. Executive Summary & Verification Walkthrough
1. **Start-Screen CTA Button Visibility (`REQ-UI-525.4`)**: Updated `ControlTrackingViewModel.isMorningHrvCheckVisible` combine predicate to include `((tuning.showMorningCheckOnStartScreen && hasHrm) || tuning.enableMorningSleepAndWellness)` under `READY` mode, `!todayMorningCheckCompleted`, and `hour < morningCheckCutoffHour`. Pure subjective sleep & wellness users without heart rate sensors can now launch the Morning Check directly from the Start screen.
2. **Cutoff Hour Slider Availability (`REQ-UI-525.1`)**: Expanded `MorningCheckSection.kt` slider visibility condition to `if (showMorningCheckOnStartScreen || enableMorningSleepAndWellness)`, ensuring the time cutoff slider is available and interactive when only *Morgenschlaf & Wohlbefinden* is enabled. Added `Pure Wellness` `@Preview`.
3. **Completion Auto-Hide & Agnostic Button Label**: Confirmed `TrackingTabsScreen` uses `R.string.hrv_morning_check_start_button` ("Morgendlicher Check" / "Morning Check") with 100% 9-language translation parity. Saving a standalone wellness entry via `MorningHrvCheckViewModel.saveStandaloneWellness()` writes to `TABLE_HRV_READINESS`, setting `todayMorningCheckCompleted = true` and auto-hiding the CTA button on return to Start screen.
4. **Architectural Cleanliness**: Confirmed zero direct `*DatabaseManager` in `ui/` (`RepositoryUiCleanlinessContractTest` & `ArchitectureConformanceContractTest` passed).

---

## 2. Requirement & Test Verification Matrix
| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-UI-525.4` | `TST-UI-525.4` | Unit: `ControlTrackingViewModelTest` | **PASSED** (14/14) | `Verified` |
| `REQ-UI-525.1` | `TST-UI-525.6` | Compose UI: `MorningCheckCutoffScreenshotTest` | **PASSED** (2/2) | `Verified` |
| `REQ-UI-525.5` | `TST-UI-525.5` | Localization: `TranslationParityTest` (all 9 locales) | **PASSED** (1/1) | `Verified` |
| `REQ-PRO-001` | `TST-UI-525.7` | Tier 1 Targeted Suite + Cleanliness Contracts | **PASSED** (415/415) | `Verified` |

---

## 3. Automated Test Evidence
* **Tier 1 Verification Command**:
  ```bash
  ./gradlew :app:testDebugUnitTest --tests "*ControlTracking*" --tests "*Morning*" --tests "*Tuning*" --tests "*ArchitectureConformanceContractTest*" --tests "*RepositoryUiCleanlinessContractTest*" --tests "*Localization*" :app:compileDebugAndroidTestKotlin assembleDebug --max-workers=2
  ```
* **Execution Results**:
  - `ControlTrackingViewModelTest`: 14/14 tests PASSED (including pure wellness visibility & completion auto-hide).
  - `MorningCheckCutoffScreenshotTest`: 2/2 tests PASSED (including cutoff slider visible in pure wellness mode).
  - `RepositoryUiCleanlinessContractTest` & `ArchitectureConformanceContractTest`: PASSED (zero leaky DB managers in UI layer).
  - `compileDebugAndroidTestKotlin` & `assembleDebug`: BUILD SUCCESSFUL.
  - Overall Test Suite: **415 tests passed, 0 failures, 0 errors, 0 skipped**.
  - Governance: `python3 tools/verify_requirement_governance.py` PASSED with 100% parity across all 14 checks.

---

## 4. UI Consistency & Verification
* **UI Controls**: `MorningCheckSection.kt` adheres to `docs/design_guidelines.md` §1 using `TuningSliderItem` inside the section column.
* **Button Style**: Start screen button in `TrackingTabsScreen.kt` maintains `OutlinedButton` with `Icons.Default.Favorite` and `R.string.hrv_morning_check_start_button` ("Morgendlicher Check", single-line, 9-locale parity).
* **Previews**: Added `MorningCheckSectionPureWellnessPreview` for Compose preview tooling.

---

## 5. System Invariants & Governance Verification
* Zero database migrations or schema alterations; `hour < morningCheckCutoffHour` bound `6..11` (default `8`) preserved.
* `ANT-Android-SDKs` submodule verified untouched at `0e8cea71`.
* `REQ-UI-525` and `TST-UI-525` promoted to `[Verified]`.
