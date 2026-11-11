# Stage 3: Implementation Plan - ATT-6715: DFA a1 Thresholds UI, Artifact Warning Icon & HR ZoneDisplayOptions Parity

**Ticket**: [ATT-6715](https://rainerblind.atlassian.net/browse/ATT-6715)  
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2`  
**Requirement Mapping**: `REQ-TRK-061` (`REQ-TRK-061.1`, `REQ-TRK-061.2`, `REQ-TRK-061.3`, `REQ-TRK-061.4`, `REQ-TRK-061.5`)  
**Test Mapping**: `TST-TRK-061` (`TST-TRK-061.1`, `TST-TRK-061.2`, `TST-TRK-061.3`, `TST-TRK-061.4`, `TST-TRK-061.5`, `TST-TRK-061.6`)  
**Branch**: `feature/ATT-6715` | **Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Background
Sprint `2026-46.1` follow-up of ATT-3542 revealed five UI, formatting, and configuration gaps: uncalled threshold setters in `TuningPreferencesDataStore.kt` without settings UI, hardcoded 0.75/0.50 threshold text in `HrvInfoBottomSheet.kt` lacking `@Preview` composables, ambiguity between rolling-window warm-up and artifact-gated suppression on cockpit tiles, unconditional exposure of HRV metrics on non-HR profiles in `ActivityType.java`, and non-integer formatting of `HRV_RMSSD` alongside missing HR `ZoneDisplayOptions` parity on `HRV_DFA_A1` tiles.

---

## 2. Traceability & Requirements Mapping
* `REQ-TRK-061.1` (Activity profile gating based on HR capability) $\rightarrow$ `TST-TRK-061.1` (`ActivityTypeTest`)
* `REQ-TRK-061.2` (RMSSD integer formatting & artifact warning icon) $\rightarrow$ `TST-TRK-061.2` (`SensorTypeHrvTest`, `SensorFieldViewTest`)
* `REQ-TRK-061.3` (HR `ZoneDisplayOptions` & gauge parity on DFA $\alpha_1$) $\rightarrow$ `TST-TRK-061.3` (`TrackingViewModelTest`)
* `REQ-TRK-061.4` (Threshold settings UI & $T_1 \ge T_2 + 0.05f$ invariant) $\rightarrow$ `TST-TRK-061.4` (`TuningPreferencesDataStoreDfaA1Test`)
* `REQ-TRK-061.5` (Dynamic info sheet & Light/Dark/AMOLED `@Preview`) $\rightarrow$ `TST-TRK-061.5` (`HrvInfoBottomSheetTest`, `TranslationParityTest`)
* `REQ-PRO-001` (Tier 1 regression suite) $\rightarrow$ `TST-TRK-061.6` (Clean-room build & test pass)

---

## 3. System Invariants & Preserved Behavior
1. **Fallback & Range Bounds**: Population defaults ($0.75 / 0.50$) preserved; range bounds $[0.20, 1.50]$ and ordering invariant $T_1 \ge T_2 + 0.05f$ strictly enforced.
2. **Visual & Theme Parity**: Adhere to `design_guidelines.md` §3.1 & §5 (scale direction hints, `MaterialTheme.colorScheme`, `TTColor.Zone*`); zero nested `verticalScroll` in `AppModalBottomSheet` (Rule 72).
3. **Hardware Independence**: Non-HR profiles hide HRV metrics unless an HR sensor is connected/paired.

---

## 4. Proposed Architectural Changes

### UI Consistency (`design_guidelines.md` §3.1, §5)
* **Reference Component**: `PhysiologyModelsSection.kt` / `WPrimeBatteryGaugeBar.kt` / `HrvInfoBottomSheet.kt`.
* **Reused Components**: `TuningSliderItem`, `SubordinateTuningToggleItem`, `AppModalBottomSheet`, `SensorFieldView`.
* **Theme Tokens**: `MaterialTheme.colorScheme.primary/error`, `TTColor.Zone2`, `TTColor.Zone3`, `TTColor.Zone4`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Activity Profile Gating (`ActivityType.java`)
* Files: `ActivityType.java`.
* Action: In `getSensorTypeArray()`, check whether `sensors` contains `SensorType.HR` or `context != null && DevicesDatabaseManager.getInstance(context).haveHeartRateDevice()`. Gate appending of `HRV_DFA_A1`, `HRV_RMSSD`, `RR_INTERVAL`, `HRV_ARTIFACT_RATE`, and `HRV_SDNN`.

### Step 2: RMSSD Integer Formatter (`SensorType.java`)
* Files: `SensorType.java`.
* Action: Update `SensorType.HRV_RMSSD` to use `new IntegerFormatter()` (emitting `0 ms` instead of `0.0 ms`).

### Step 3: Threshold Controls, Invariant & Settings UI (`TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`)
* Files: `TuningPreferencesDataStore.kt`, `PhysiologyModelsSection.kt`, `AdvancedTuningScreen.kt`, `AdvancedTuningDialog.kt`.
* Action: Enforce $T_1 \ge T_2 + 0.05f$ in `setDfaA1ThresholdT1`, `setDfaA1ThresholdT2`, and `saveTuningConfig`. In `PhysiologyModelsSection.kt`, add sliders for $T_1$ and $T_2$ with scale-direction hints. Wire into `AdvancedTuningScreen` and `AdvancedTuningDialog`.

### Step 4: Dynamic HrvInfoBottomSheet & Previews (`HrvInfoBottomSheet.kt`, `strings_hrv.xml`)
* Files: `HrvInfoBottomSheet.kt`, `app/src/main/res/values*/strings_hrv.xml`.
* Action: Accept `dfaA1ThresholdT1` and `dfaA1ThresholdT2` in `HrvInfoBottomSheet` and format zone descriptions dynamically. Add Light, Dark, and AMOLED `@Preview` composables wrapped in `ATrainingTrackerTheme`. Maintain 9-language translation parity.

### Step 5: Artifact Warning Indicator & HR ZoneDisplayOptions Parity (`SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`)
* Files: `SensorFieldState.kt`, `SensorFieldView.kt`, `TrackingViewModel.kt`.
* Action: Add `isArtifactGated: Boolean = false` to `SensorFieldState`. In `TrackingViewModel.kt`, map `HRV_DFA_A1` to HR zone type in `getZoneType`, compute `ZoneGaugeState` from T1/T2, and flag `isArtifactGated = true` when metric is null and artifact rate $>5.0\%$. In `SensorFieldView.kt`, render a subtle warning icon next to `--.--`.

### Step 6: Targeted Unit & Localization Tests
* Files: `SensorTypeHrvTest.kt`, `TuningPreferencesDataStoreDfaA1Test.kt`, `HrvInfoBottomSheetTest.kt`, `TrackingViewModelTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.sensor.*" --tests "com.atrainingtracker.trainingtracker.ui.tracking.*" --tests "com.atrainingtracker.trainingtracker.settings.*" --tests "com.atrainingtracker.trainingtracker.localization.*" --tests "com.atrainingtracker.trainingtracker.build.*"`

---

## 6. Verification & Rollback Plan
* **Verification**: Unit tests, `@Preview` inspections, and `TranslationParityTest` verifying dynamic thresholds and profile gating.
* **Rollback**: Isolated branch `feature/ATT-6715` allows clean reset without touching sprint branch.
