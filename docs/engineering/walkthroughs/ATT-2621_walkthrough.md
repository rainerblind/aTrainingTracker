# Stage 5 Walkthrough: ATT-2621 - Background energy / battery optimization permission not prompted when revoked

**Ticket**: [ATT-2621](https://atrainingtracker.atlassian.net/browse/ATT-2621)  
**Sub-task**: [ATT-2714](https://atrainingtracker.atlassian.net/browse/ATT-2714) (`[Test]`)  
**Parent Epic**: [ATT-166](https://atrainingtracker.atlassian.net/browse/ATT-166) (*Filtering to improve sensor values (especially locations)*)  
**Target Release**: `V4.9.39`  
**Active Sprint**: `2026-41.3`  
**Branch**: `feature/ATT-2621`  
**Author**: Antigravity  
**Date**: 2026-10-08  

---

## 1. Executive Summary
ATT-2621 resolves a critical gap where background energy / battery optimization exemption was not proactively prompted or was shadowed on `ControlTrackingScreen.kt` when revoked or denied. In previous releases, battery optimization checks were chained strictly behind background location permission and only executed upon tapping Start, leaving athletes unaware that tracking might be killed by Android battery management while idle, and bypassing battery checks entirely if background location was skipped.

Under `REQ-PRI-005`:
1. **Proactive Warning Banner (`BatteryOptimizationWarningBanner.kt`)**: Renders a non-blocking M3 warning card directly below `LocationCalibrationBadge` whenever `isIgnoringBatteryOptimizations == false`. Displays clear explanations, an action button that triggers `launchBatteryOptimizationIntent`, and a dismiss option.
2. **Unified Start Button Indicator**: `ControlTrackingButton` warning badge now reflects both missing location and missing battery optimization: `!hasLocationPermission || !isIgnoringBatteryOptimizations`.
3. **Decoupled JIT Cascade**: Skipping or denying background location ("Not now") no longer swallows the battery optimization check; it seamlessly transitions to `RationaleStep.BATTERY_OPTIMIZATION`.
4. **Lifecycle Synchronization (`ON_RESUME`)**: Reactively re-evaluates `checkIsIgnoringBatteryOptimizations()` on `ON_RESUME`, automatically dismissing the banner and starting tracking if returning from settings with exemption granted.
5. **Google Play Policy Compliance**: System settings intent is only launched upon direct user tap (Rule 21).
6. **100% 9-Language Localization Parity**: All strings translated across EN, DE, ES, FR, IT, JA, NL, PL, PT.
7. **Clean-Room Verification**: 100% test pass rate across the full regression test suite with zero failures.

---

## 2. Changes Summary

| Component | Target File | Key Changes |
|:---|:---|:---|
| **Warning Banner** | `BatteryOptimizationWarningBanner.kt` | Created M3 warning card with `RoundedCornerShape(12.dp)`, alert icon (`ic_battery_full`), dismissibility, and direct action trigger. |
| **Control Tracking Screen** | `ControlTrackingScreen.kt` | Added reactive `isIgnoringBatteryOptimizations`, `isBatteryBannerDismissed`, and `pendingStartAfterBatteryExemption` states; re-checked on `ON_RESUME`; embedded banner; decoupled rationale cascade. |
| **Localization** | `strings.xml` (all 9 locales) | Added `battery_optimization_warning_banner_title`, `battery_optimization_warning_banner_desc`, `battery_optimization_action_fix`, and `battery_optimization_dismiss_banner` across `values/`, `values-de/`, `values-es/`, `values-fr/`, `values-it/`, `values-ja/`, `values-nl/`, `values-pl/`, `values-pt/`. |
| **Contract & Parity Tests** | `*Battery*Test.kt` | Added `BatteryOptimizationBannerLocalizationTest`, `BatteryOptimizationWarningBannerTest`, and `ControlTrackingScreenBatteryTest`. |
| **Living Documentation** | `requirements.md`, `tests.md` | Promoted `REQ-PRI-005` and `TST-PRI-004` to `Verified`. |

---

## 3. Test & Verification Results

### 3.1 Targeted Tests
```bash
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.*Battery*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingScreenBatteryTest"
```
- Result: **BUILD SUCCESSFUL** in 1m 10s.
- 100% of targeted contract, state machine, and localization tests passed.

### 3.2 Full Regression Suite
```bash
./gradlew testDebugUnitTest
```
- Result: **BUILD SUCCESSFUL** in 9m 20s.
- Total tasks: 32 actionable, 12 executed, 20 up-to-date.
- Zero failures, zero regressions across entire project test suite.

---

## 4. UI Consistency & Invariants
- **UI Consistency (Rule 23)**: Matches `LocationCalibrationBadge.kt` and `PermissionRationaleSheet.kt` using standard M3 shapes (`12.dp`), spacing (`8.dp`, `12.dp`), typography (`titleSmall`, `bodySmall`), and theme-aware colors (`errorContainer` / `onErrorContainer`).
- **Google Play Policy Compliance**: Zero auto-launching of intents without user interaction.
- **Infinite Loop Prevention**: Dismissing rationale via "Not now" resets step to `NONE` and starts tracking gracefully.
