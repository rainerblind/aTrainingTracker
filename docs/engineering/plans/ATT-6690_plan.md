# Stage 3: Implementation Plan - ATT-6690: EditSensorFieldDialog: Replace 7-chip FlowRow for Sparkline Time Horizon with stepped Slider

**Ticket**: [ATT-6690](https://rainerblind.atlassian.net/browse/ATT-6690) | **Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) | **Target Release**: `V5.0.0`  
**Active Sprint**: `sprint/2026-46.2` | **Branch**: `feature/ATT-6690` | **Req/Test**: `REQ-TRK-059.3` / `TST-TRK-059.4`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`) | **Date**: 2026-11-11  

---

## 1. Problem Description & Architecture Rationale
`EditSensorFieldDialog.kt` (lines 206–238) displays 7 discrete duration options as `FilterChip`s inside a `FlowRow`. On mobile screens, trailing chips wrap awkwardly onto a second row, violating `docs/design_guidelines.md` §1.4. We replace the `FlowRow` with a Material 3 stepped `Slider` (7 discrete positions: `60s`, `120s`, `300s`, `600s`, `1200s`, `3600s`, `0s`), featuring:
1. Header `Row`: Title `sensor_field_graph_horizon` on the left, active localized duration on the right in `primary`.
2. Stepped `Slider`: `valueRange = 0f..6f`, `steps = 5`, snapping cleanly to index positions.
3. Range bounds `Row`: Start label (`1 min`) and end label (`Full workout`) in `outline` style.

---

## 2. System Invariants & Preserved Behavior
1. **Discrete Value Parity**: The exact same 7 duration values (`[60, 120, 300, 600, 1200, 3600, 0]`) are maintained.
2. **Persistence Schema**: Value persists into SQLite column `GraphHorizonS` via `TrackingViewsRepository.updateSensorFieldConfig()`; zero DB schema change.
3. **Recomposition Hygiene**: Rule 66 respected; zero snapshot state writes during composition.
4. **9-Language Parity**: All 7 duration string resources (`graph_horizon_1m`..`graph_horizon_full`) are reused with 100% parity.

---

## 3. UI Consistency (Design Guidelines)
* **Guideline Compliance**: Strictly implements `docs/design_guidelines.md` §1.4 for scalar sets $\ge 5$ items.
* **Reused Tokens**: `MaterialTheme.typography.labelLarge`, `MaterialTheme.typography.labelSmall`, `MaterialTheme.colorScheme.primary`, `MaterialTheme.colorScheme.onSurfaceVariant`, `MaterialTheme.colorScheme.outline`.
* **Layout Sizing**: Uses standard `Slider` with full width, matching `TuningSliderItem` aesthetics.

---

## 4. Step-by-Step Implementation Sequence

### Step 1: Stepped Slider & Active Value Header in `EditSensorFieldDialog.kt` (`REQ-TRK-059.3`)
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialog.kt`
* **Changes**:
  - Replace `FlowRow` + `FilterChip` block with:
    1. Header `Row(fillMaxWidth, Arrangement.SpaceBetween)` with `Text(sensor_field_graph_horizon)` and `Text(activeHorizonLabel, color = primary)`.
    2. Material 3 `Slider` with `value = selectedIndex.toFloat()`, `valueRange = 0f..6f`, `steps = 5`, calling `viewModel.onGraphHorizonChanged(horizons[newIndex].first)` on snap.
    3. Range bounds `Row(fillMaxWidth, Arrangement.SpaceBetween)` with min (`graph_horizon_1m`) and max (`graph_horizon_full`) labels.
  - Define top-level helper functions for duration-to-index and index-to-duration conversion to facilitate unit testing.

### Step 2: Unit & Contract Tests in `EditSensorFieldDialogSparklineTest.kt` (`TST-TRK-059.4a`, `TST-TRK-059.4b`)
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/editsensorfield/EditSensorFieldDialogSparklineTest.kt`
* **Changes**:
  - Add unit test for bidirectional slider index mapping (`0..6` $\leftrightarrow$ `[60, 120, 300, 600, 1200, 3600, 0]`).
  - Verify `onGraphHorizonChanged` across all 7 steps and persistence via `saveChanges()`.

### Step 3: Living Documentation Verification (`REQ-TRK-059.3`, `TST-TRK-059.4`)
* **Files**: `docs/requirements/req_tracking_cockpit.md`, `docs/tests/tst_tracking_cockpit.md`
* **Changes**: Ensure `REQ-TRK-059.3` and `TST-TRK-059.4` reflect the stepped slider control and status is `[Verified]`.

### Step 4: Targeted Verification & Governance Checks
* **Commands**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*" --tests "com.atrainingtracker.trainingtracker.localization.TranslationParityTest" --tests "com.atrainingtracker.trainingtracker.build.*"`
  * `python3 tools/verify_requirement_governance.py`
