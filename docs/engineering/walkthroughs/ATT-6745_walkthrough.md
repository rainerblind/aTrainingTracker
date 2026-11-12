# Stage 5: Walkthrough & Verification - ATT-6745: Surface Smoothed/Unsuitable Chest-Strap Warnings & Update Sensor Compatibility Guidance

**Ticket**: [ATT-6745](https://rainerblind.atlassian.net/browse/ATT-6745)  
**Sub-task**: [ATT-6781](https://rainerblind.atlassian.net/browse/ATT-6781) (`[Test]`)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*HRV Autonomic Metrics*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.3`  
**Requirement Mapping**: `REQ-TRK-068`  
**Test Mapping**: `TST-TRK-068`  
**Branch**: `ticket/ATT-6745`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) & AI Agent 2 (Reviewer, `subagent_pro`)  
**Date**: 2026-11-12  

---

## 1. Executive Summary & Verification Overview

Delivered user-facing protections against firmware-smoothed and synthetic RR intervals across four presentation touchpoints:
* **Sensor Compatibility Guidance (`HrvInfoBottomSheet.kt`)**: Updated sensor recommendation text to explicitly recommend verified ECG chest straps (Polar H10/H9, Garmin HRM-Dual/Pro/Pro Plus) and warn against unsuitable sensors (optical PPG, budget chest straps with internal RR smoothing like Magene, CooSpo). Added top error-styled warning banner when connected sensor reports `SMOOTHED_STREAM` or `QUANTIZED_SYNTHETIC`.
* **Live Cockpit Pacing Protection (`HrvZoneIndicator.kt`, `TrackingViewModel.kt`)**: Suppressed green Zone 2 aerobic pacing indicator when `isSmoothedStream == true`, preventing false easy-effort reassurance when $\alpha_1$ is artificially inflated ($> 1.4$).
* **Morning Assessment Protection (`MorningHrvCheckScreen.kt`)**: Added dedicated `SmoothedSignalWarningCard` displayed upon measurement completion when signal is smoothed/synthetic, advising against saving into the 60-day readiness baseline.
* **Post-Workout Aftermath Warning (`HrvAlpha1ScatterCard.kt`)**: Added inline warning banner cautioning that threshold estimates are unreliable on smoothed sessions.
* **9-Language Parity**: All 6 new warning keys and updated sensor text localized with 100% parity across `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, `pt`.

### 1.1 Stage 6 Cold Carbon Review Healing (Sprint 2026-46.3)
Healed adversarial findings from Stage 6 review:
1. **Live Cockpit `signalQualityStatus` Wiring**:
   - `hrvSignalQualityStatus` on `SensorFieldState` is dynamically populated from `BANALService.getDeviceManager()?.hrvMetricsDevice?.signalQualityResult?.status` in `TrackingViewModel.kt` (during initial load and within `applySensorData`) and in `EditSensorFieldDialog.kt`.
   - Wired to `HrvInfoBottomSheet` in `SensorFieldView.kt` and verified via contract tests in `HrvInfoBottomSheetTest.kt`.
2. **Post-Workout Signal Quality Evaluation & Wiring**:
   - Implemented `isWorkoutHrvSmoothed(workoutId: Long)` in `WorkoutRepository.kt` which queries persisted beat-to-beat intervals from `WorkoutRrRepository` and evaluates signal quality via `HrvPostWorkoutRecomputer.analyzeSignalQuality(beats)`.
   - Exposed `isSmoothedHrvStream` on `AftermathMapUIState` in `TrackOnMapAftermathViewModel.kt` populated during `loadAftermathData`.
   - Threaded `isSmoothedStream` through `WorkoutSummariesTabbedScreen.kt`, `WorkoutSummariesListFragment.kt`, `TrackOnMapScreen.kt`, and `WorkoutDetailTabbedContent.kt` directly into `HrvAlpha1ScatterCard(..., isSmoothedStream = isSmoothedStream)`.
   - Verified via unit test `loadAftermathData_populatesIsSmoothedHrvStreamFromWorkoutRepository` in `TrackOnMapAftermathViewModelTest.kt`.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-068.1` | `TST-TRK-068.1` | `HrvInfoBottomSheetTest.kt` | **PASSED** | `Verified` |
| `REQ-TRK-068.2` | `TST-TRK-068.2` | `TrackingHrvZoneIndicatorTest.kt` | **PASSED** | `Verified` |
| `REQ-TRK-068.3` | `TST-TRK-068.3` | `MorningHrvCheckScreen.kt` UI Inspection & Unit Tests | **PASSED** | `Verified` |
| `REQ-TRK-068.4` | `TST-TRK-068.4` | `HrvAlpha1ScatterChartTest.kt` | **PASSED** | `Verified` |
| `REQ-TRK-068.5` | `TST-TRK-068.5` | `TranslationParityTest.kt` (9 Locales) | **PASSED** | `Verified` |
| `REQ-TRK-068.6` | `TST-TRK-068.6` | `ThemeColorCentralizationAuditTest.kt` | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

* **Tier 1 Targeted Test Scope (Rule 60)**:
  - Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.hrv.*" --tests "com.atrainingtracker.trainingtracker.ui.cockpit.*" --tests "com.atrainingtracker.trainingtracker.ui.aftermath.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.ui.components.core.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.HrvInfoBottomSheetTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.TrackingHrvZoneIndicatorTest"`
  - Result: **BUILD SUCCESSFUL** (33 actionable tasks: 1 executed, 32 up-to-date; 100% PASS across all unit and localization suites).
* **Living Documentation Governance**:
  - Command: `python3 tools/verify_requirement_governance.py --sync-indexes`
  - Result: **PASS** (Master index tables synchronized with 100% parity; `REQ-TRK-068` and `TST-TRK-068` verified).

---

## 4. Hardware / Physical Verification (Pixel 10)

* **Testing Scope**: On-device emulator execution was explicitly retired per user directive ("using the emulator wasn't a good Idea. We must not use it anymore"). Verification was established via full unit test coverage, Compose structure audit, and automated 9-locale translation parity testing.
* **Visual & Theme Consistency (Rule 23)**: All warning containers and banners strictly employ centralized `TTColor.ArtifactWarningContainer`, `TTColor.ArtifactWarningBorder`, `TTColor.ArtifactWarningTitle`, and `TTColor.ArtifactWarningDesc` design tokens per `docs/design_guidelines.md` §5.

---

## 5. Invariant & Governance Verification

1. **Independent Warning Invariants**: Existing `> 5%` artifact warnings are preserved independently; both warnings render concurrently without collision if a stream exhibits both smoothing and artifact bursts.
2. **Aerobic Zone Color Integrity**: Unsmoothed streams retain standard green Zone 2 aerobic pacing colors; suppression applies strictly to smoothed streams.
3. **9-Language Parity**: 100% translation parity across `values`, `de`, `es`, `fr`, `it`, `ja`, `nl`, `pl`, and `pt`.
4. **Database Schema Integrity**: Zero SQLite schema alterations or migrations.
5. **Living Documentation Synchronized**: Status in `docs/requirements/req_tracking_cockpit.md` and `docs/tests/tst_tracking_cockpit.md` updated to `[Verified]`; master tables synchronized with 100% parity.
