# Stage 3: Implementation Plan - ATT-7740: Morning HRV Cutoff Hour Default 08:00 & Max Bound 11:00

**Ticket**: [ATT-7740](https://rainerblind.atlassian.net/browse/ATT-7740) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-3539](https://rainerblind.atlassian.net/browse/ATT-3539) (*Heart Rate Variability (HRV)*)
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-7740`
**Requirement Mapping**: `REQ-UI-525` (`REQ-UI-525.1`)
**Test Mapping**: `TST-UI-525` (`TST-UI-525.1`, `TST-UI-525.4`, `TST-UI-525.6`, `TST-UI-525.7`)
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
Prior commit `6267d2c0` set `DEFAULT_MORNING_CHECK_CUTOFF_HOUR = 8` (`08:00`) and extended `MAX_MORNING_CHECK_CUTOFF_HOUR = 23` (`23:00`). Per PO revision (*"23:00 ist für eine morgendliche Routine viel zu spät. Die obere Grenze (MAX_MORNING_CHECK_CUTOFF_HOUR) muss bei 11:00 Uhr liegen (Default bleibt 08:00)."*), this plan lowers `MAX_MORNING_CHECK_CUTOFF_HOUR` to `11` (`11:00`) across `TuningPreferencesDataStore.kt` and `MorningCheckSection.kt` (`ATT-8117`), updates unit tests (`// TST-UI-525`), and verifies the `6..11` slider via `Rule 8.5` Compose PNG screenshot.

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-UI-525.1` (`DEFAULT = 8`, `MIN = 6`, `MAX = 11`, `.coerceIn(6, 11)`, `MorningCheckSection` `steps = 4`) in `docs/requirements/req_ui_design_system.md`.
* **Test Mapping**: `TST-UI-525.1` (`TuningConfigTest`), `TST-UI-525.4` (`ControlTrackingViewModelTest`), `TST-UI-525.6` (`MorningCheckCutoffScreenshotTest`), `TST-UI-525.7` (Tier 1 suite).

## 3. System Invariants & Preserved Behavior
1. **Default & Lower Bound Invariance**: `DEFAULT_MORNING_CHECK_CUTOFF_HOUR == 8` (`08:00`) and `MIN_MORNING_CHECK_CUTOFF_HOUR == 6` (`06:00`) remain unchanged.
2. **Legacy Persisted Value Coercion**: `TuningPreferencesDataStore.kt` (`1274-1276`, `1361-1373`, `1683-1686`) clamps any previously saved `> 11` value to `11` via `.coerceIn(6, 11)` with zero DB/DataStore migration.
3. **Reactive Start-Screen Gating & Localization**: `ControlTrackingViewModel.isMorningHrvCheckVisible` (`hour < morningCheckCutoffHour`) and all 9-locale `strings_hrv.xml` resources remain untouched.
4. **UI Consistency (`docs/design_guidelines.md`)**: `MorningCheckSection.kt` retains `TuningSliderItem` with `valueRange = 6f..11f` and `steps = 4` (`MAX - MIN - 1`) and `%02d:00` zero-padded formatting.

## 4. Proposed Architectural Changes
* **Component 1 (`TuningPreferencesDataStore.kt`)**: Update `TuningPreferencesDefaults.MAX_MORNING_CHECK_CUTOFF_HOUR` from `23` to `11` (`line 369`). Existing `.coerceIn(MIN, MAX)` calls automatically enforce `[6, 11]`.
* **Component 2 (`MorningCheckSection.kt`)**: Verify `valueRange = MIN..MAX` (`6f..11f`) and `steps = MAX - MIN - 1` (`4`, yielding 6 integer stops `06:00..11:00`) and `.coerceIn(6, 11)` work seamlessly with no hardcoded `23`.
* **Component 3 (`TuningConfigTest.kt`, `ControlTrackingViewModelTest.kt`, `AdvancedTuningAccordionTest.kt`)**: Update `TuningConfigTest` bounds assertions (`23 -> 11`) and clamping assertions (`23/25 -> 11`), verify `ControlTrackingViewModelTest` (`08:00` & `11:00` boundary), and embed explicit `// TST-UI-525` tags inside test methods (`ATT-8143`).

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Low Risk, 1 SP -> Flash executes Stage 4)**.
* **Step 1 (`TuningPreferencesDataStore.kt`)**: Change `MAX_MORNING_CHECK_CUTOFF_HOUR = 11` (`DEFAULT = 8`, `MIN = 6`).
* **Step 2 (`MorningCheckSection.kt` Audit)**: Verify `steps = MAX - MIN - 1` resolves to `4` (`6f..11f`) and `@Preview` (`morningCheckCutoffHour = 10`) stays within `6..11`.
* **Step 3 (Unit Tests with `// TST-UI-525`)**: Update `TuningConfigTest.kt` (`MAX_MORNING_CHECK_CUTOFF_HOUR == 11`, clamping `12/23/25 -> 11`, `4 -> 6`, default `8`) and add `// TST-UI-525` tags in `TuningConfigTest.kt`, `ControlTrackingViewModelTest.kt` (`08:00`/`11:00` gating), and `AdvancedTuningAccordionTest.kt` (`morningCheckCutoffHour = 8`).
* **Step 4 (Rule 8.5 Compose PNG Screenshot)**: Add `MorningCheckCutoffScreenshotTest.kt` (`// TST-UI-525`) rendering `MorningCheckSection` in Light & Dark themes (`6f..11f`, `steps = 4`, default `08:00`), saving `docs/engineering/walkthroughs/img/ATT-7740_morning_cutoff.png` and inspecting via `read_file`.
* **Tier 1 Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.TuningConfigTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.ControlTrackingViewModelTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.AdvancedTuningAccordionTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.TuningDesignGuidelinesContractTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.MorningCheckCutoffScreenshotTest"
  ```

## 6. Verification & Rollback Plan
* **Verification**: Tier 1 targeted unit + screenshot suite (`100%` pass), `python3 tools/verify_requirement_governance.py`, and Rule 8.5 `read_file` PNG inspection.
* **Rollback**: Isolated on `feature/ATT-7740` prior to Stage 5 squash-merge onto `sprint/2026-47.1`.
