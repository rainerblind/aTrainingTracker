# Stage 3: Implementation Plan - ATT-7761: Decoupled Multi-Color Dynamic Zone Sparkline Rendering

**Ticket**: [ATT-7761](https://rainerblind.atlassian.net/browse/ATT-7761) | **Sub-task**: `[Impl-Plan]` (Pending `submit-stage`)
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)
**Target Release**: `V5.0.0` | **Sprint**: `2026-47.1` | **Branch**: `feature/ATT-7761`
**Requirement Mapping**: `REQ-TRK-082` (`REQ-TRK-082.1`..`REQ-TRK-082.4`)
**Test Mapping**: `TST-TRK-082` (`TST-TRK-082.1`..`TST-TRK-082.4`)
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-16

---

## 1. Problem Description & Background
`resolveSparklineLineColor` (`SensorSparklineView.kt`) still accepts `showTextColor` (coupling `sensorType == null` fallback to text zone settings), callers pass `showTextColor`, and `"DYNAMIC_ZONE"` resolves only to a single instantaneous `fieldState.zoneColor`. This plan removes `showTextColor` from sparkline color resolution and adds threshold-mapped `Brush.verticalGradient` multi-color curve, fill, and terminal dot rendering for HR, Power (excluding `POWER_TO_WEIGHT`), and HRV DFA α1.

## 2. Traceability & Requirements Mapping
* **Requirements**: `REQ-TRK-082.1` (Complete `showTextColor` decoupling), `REQ-TRK-082.2` (`Brush.verticalGradient` & terminal dot for HR/Power/DFA α1), `REQ-TRK-082.3` (Dark/AMOLED `Zone7` contrast & edge fallbacks), `REQ-TRK-082.4` (Rule 8.5 PNG screenshot).
* **Test Mapping**: `TST-TRK-082.1`..`TST-TRK-082.4` in `docs/tests/tst_tracking_cockpit.md` (`// TST-TRK-082` tags in unit/ViewModel/screenshot tests).

## 3. System Invariants & Preserved Behavior
1. **Zero Ring-Buffer / Schema Churn**: `List<Float>` sparkline buffers and SQLite v22 schema remain unchanged.
2. **Gradient Stop Validity**: `Brush.verticalGradient` stops are clamped and monotonically non-decreasing in `[0f, 1f]`; `samples.size == 1`, `range == 0f`, `isPlaceholder == true`, and null thresholds safely use solid/dashed fallback.
3. **`POWER_TO_WEIGHT` & DFA α1 Guards**: `POWER_TO_WEIGHT` (W/kg) never receives raw Watt cutoffs; DFA α1 suppresses zones on `SMOOTHED_STREAM` / `QUANTIZED_SYNTHETIC`.
4. **UI Consistency (`docs/design_guidelines.md`)**: No new toggles/chips added; existing `SingleChoiceSegmentedButtonRow` (`icon = {}`) untouched; dark/AMOLED `TTColor.Zone7` (`#000000`) substitutes `colorScheme.onSurface`.

## 4. Proposed Architectural Changes
* **Component 1 (`SensorFieldState.kt` & `TrackingViewModel.kt`)**: Add immutable `SparklineZoneThresholds(cutoffs: List<Float>, zoneColors: List<Color>, invertedPolarity: Boolean = false)` + `sparklineZoneThresholds: SparklineZoneThresholds? = null` on `SensorFieldState`. Project `HR_RUN`/`HR_BIKE` (4 cutoffs -> `Zone1..5`), `PWR_BIKE` (6 cutoffs -> `Zone1..7`, excl. `POWER_TO_WEIGHT`), and `HRV_DFA_A1` (`t2, t1` -> `Zone4, Zone3, Zone2`, `alpha1 > t1` green top .. `alpha1 < t2` red bottom) when `showGraph && graphColor == "DYNAMIC_ZONE"`.
* **Component 2 (`SensorSparklineView.kt`)**: Remove `showTextColor` from `resolveSparklineLineColor`. Add pure helpers `resolveSparklineZoneColorForValue(...)` and `buildDynamicZoneVerticalGradientStops(...)` with tight epsilon threshold stops (`0f` = top/`maxVal`, `1f` = bottom/`minVal`) and dark/AMOLED `Zone7` contrast substitution. Cache stops/terminal color in `SensorSparklineView(..., zoneThresholds)` via `remember`.
* **Component 3 (`SensorFieldView.kt` & `EditSensorFieldDialog.kt`)**: Drop `showTextColor` from `resolveSparklineLineColor` calls; pass `fieldState.sparklineZoneThresholds` when `DYNAMIC_ZONE`; supply representative preview thresholds in `EditSensorFieldDialog` when `DYNAMIC_ZONE` is selected.

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
* **Risk Classification**: **STANDARD (Medium Risk, 2 SP -> Flash executes Stage 4)**.
* **Step 1 (`SensorFieldState.kt`)**: Add `SparklineZoneThresholds` data class and nullable `sparklineZoneThresholds` field (default `null`).
* **Step 2 (`SensorSparklineView.kt`)**: Remove `showTextColor` from `resolveSparklineLineColor`; implement `resolveSparklineZoneColorForValue`, `buildDynamicZoneVerticalGradientStops` (with dark `Zone7` contrast & tight stops), and `remember`-cached multi-color `Brush.verticalGradient` + terminal dot + edge fallbacks in `SensorSparklineView`.
* **Step 3 (`TrackingViewModel.kt`)**: Implement `resolveSparklineZoneThresholds(...)` (HR 4-cutoff, Power 6-cutoff excl. `POWER_TO_WEIGHT`, DFA α1 `t1`/`t2` + stream-quality suppression) and project onto `SensorFieldState`.
* **Step 4 (`SensorFieldView.kt`, `EditSensorFieldDialog.kt`, Callers)**: Update `resolveSparklineLineColor` call sites (removing `showTextColor`, incl. `EditSensorFieldDialogScreenshotTest.kt`) and wire `zoneThresholds` into live & preview `SensorSparklineView`.
* **Step 5 (Unit & ViewModel Tests with `// TST-TRK-082`)**: Extend `SensorSparklineViewTest.kt` (`TST-TRK-082.1`, `TST-TRK-082.2`) and create `TrackingViewModelDynamicZoneSparklineTest.kt` (`TST-TRK-082.3`).
* **Step 6 (Rule 8.5 Compose PNG Screenshot)**: Add `SensorFieldViewDynamicZoneScreenshotTest.kt` (`// TST-TRK-082`) capturing `docs/engineering/walkthroughs/img/ATT-7761_dynamic_zone_sparkline.png` (Light & Dark/AMOLED HR, Power incl. Zone 7, DFA α1, fixed color + `showTextColor`, placeholder/flat) and inspect via `read_file`.
* **Tier 1 Test Command**:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorSparklineViewTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelDynamicZoneSparklineTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.tracking.TrackingViewModelSparklineTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldZoneDisplayTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.EditSensorFieldDialogScreenshotTest" --tests "com.atrainingtracker.trainingtracker.ui.tracking.SensorFieldViewDynamicZoneScreenshotTest"
  ```

## 6. Verification & Rollback Plan
* **Verification**: Run Tier 1 Gradle unit + Robolectric screenshot tests (`100%` pass), `python3 tools/verify_requirement_governance.py`, and Rule 8.5 `read_file` PNG inspection.
* **Rollback**: Isolated on `feature/ATT-7761` before Stage 5 squash-merge onto `sprint/2026-47.1`.
