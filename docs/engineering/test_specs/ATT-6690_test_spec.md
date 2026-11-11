# Stage 2: Requirement & Test Specification - ATT-6690: EditSensorFieldDialog: Replace 7-chip FlowRow for Sparkline Time Horizon with stepped Slider

**Ticket**: [ATT-6690](https://rainerblind.atlassian.net/browse/ATT-6690) | **Sub-task**: `[Req & Test Spec]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.2` | **Branch**: `feature/ATT-6690`  
**Requirement Mapping**: `REQ-TRK-059` (`REQ-TRK-059.3`) | **Test Spec ID**: `TST-TRK-059` (`TST-TRK-059.4`)  
**Author**: AI Agent 1 (`subagent_flash`) | **Date**: 2026-11-11  

---

## 1. Requirement Specification (`REQ-TRK-059.3`)

### 1.1 Functional Scope & Technical Invariants
* **Stepped Slider Time Horizon Selection (`REQ-TRK-059.3`, ATT-6690)**: In `EditSensorFieldDialog.kt`, the Sparkline Time Horizon selector SHALL replace the 7-chip `FlowRow` with a Material 3 stepped `Slider` (`valueRange = 0f..6f`, `steps = 5`) complying strictly with `docs/design_guidelines.md` §1.4.
* **Discrete Horizon Mapping**: The slider position index $i \in 0..6$ SHALL map deterministically to the 7 discrete durations: `0 -> 60s (1m)`, `1 -> 120s (2m)`, `2 -> 300s (5m)`, `3 -> 600s (10m)`, `4 -> 1200s (20m)`, `5 -> 3600s (1h)`, and `6 -> 0s (Full Workout)`.
* **Dynamic Header Value Indicator**: The section header `Row` SHALL display static label `sensor_field_graph_horizon` on the left and the active localized duration label (e.g., `"5 min"` or `"Gesamtes Training"`) on the right in `MaterialTheme.colorScheme.primary` with `labelLarge` typography.
* **Range Bounds Labels**: A compact label row below the slider track SHALL annotate the minimum (`1 min`) and maximum (`Full workout`) horizon bounds.
* **State & Persistence Invariance**: Dragging or tapping the slider SHALL dispatch `viewModel.onGraphHorizonChanged(durationSeconds)`. Database persistence to SQLite column `GraphHorizonS` in `ROWS_TABLE` remains 100% unchanged.

### 1.2 Given-When-Then Acceptance Criteria
* **AC-1 (Initial Horizon Alignment)**: *Given* opening `EditSensorFieldDialog` for a field configured with `showGraph == true` and 5m (`300s`) horizon, *When* the dialog renders, *Then* the slider thumb rests at step index `2` and the header displays localized `"5 min"`.
* **AC-2 (Stepped Adjustment & Active Indicator)**: *Given* an athlete adjusting the slider, *When* dragging from index `2` to index `0`, *Then* the header updates immediately to `"1 min"`, `onGraphHorizonChanged(60)` is invoked, and saving persists `60` into `ROWS_TABLE.GraphHorizonS`.
* **AC-3 (Full Workout Sentinel Position)**: *Given* an athlete sliding to the terminal right step (index `6`), *When* released, *Then* the header displays localized `"Full Workout"` (`graph_horizon_full`), `onGraphHorizonChanged(0)` is invoked, and saving persists `0` into `ROWS_TABLE.GraphHorizonS`.

### 1.3 System Invariants
Zero Compose snapshot state writes in composition (`Rule 66`); 100% 9-language translation parity; bounded memory; exact same 7 discrete duration values preserved.

### Requirement Archaeology & Chesterton's Fence Audit
1. *Original Requirement ID & Target*: Net-new requirement `REQ-TRK-059`, extending `REQ-TRK-046`, `REQ-UI-436`, and `REQ-UI-159`. Refined by `ATT-6710` (singleton repository persistence), `ATT-6695` (X-axis time labels), and `ATT-6690` (replacing 7-chip `FlowRow` with stepped `Slider` per Design Guidelines §1.4 in `EditSensorFieldDialog.kt`).
2. *Historical Origin & Commit Trace*: `ATT-4841` (DB v16), `ATT-4927` (DB v18), `ATT-1294` (FilterChip FlowRow in EditSensorFieldDialog), `ATT-6710` (repository persistence), `ATT-6695` (X-axis labels), `ATT-6690` (stepped Slider).
3. *Root Reason for Existing Formulation*: Prior sparkline implementations used `FlowRow` with `FilterChip`s as a fast initial prototype before Design Guidelines §1.4 formalized prohibiting multi-chip rows for scalar sets with $\ge 5$ discrete values.
4. *Preservation of Core Invariants*: Exactly the same 7 discrete duration horizons (`60s`, `120s`, `300s`, `600s`, `1200s`, `3600s`, `0s`) remain selectable; persistence to `GraphHorizonS` in SQLite remains 100% identical; bounded buffer memory ($\le 300$ samples / $\le 1\text{ KB}$ per tile) and 100% 9-language translation parity remain strictly preserved.

---

## 2. Test Specification & Traceability Matrix (`TST-TRK-059.4`)

### 2.1 Concrete Verification Procedures
* **`TST-TRK-059.4a` (Slider Index to Duration Mapping)**: In `EditSensorFieldDialogSparklineTest.kt`, verify helper mapping functions correctly translate duration seconds `[60, 120, 300, 600, 1200, 3600, 0]` to slider indices `0..6` and vice versa, defaulting safely on unknown durations.
* **`TST-TRK-059.4b` (ViewModel Horizon State & Persistence Contract Test)**: In `EditSensorFieldDialogSparklineTest.kt`, verify `onGraphHorizonChanged` updates `uiState.graphHorizonSeconds` across all 7 steps and saves correctly via `trackingViewsRepository.updateSensorFieldConfig()`.
* **`TST-TRK-059.4c` (9-Locale Parity)**: Run `TranslationParityTest` verifying all 7 duration string resources exist across all 9 supported locales.

### 2.2 Traceability Matrix

| Req ID | Test Case | Target Component | Verification Scope | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-059.3` | `TST-TRK-059.4a` | `EditSensorFieldDialogSparklineTest` | Stepped slider index-to-duration bidirectional mapping | Specified |
| `REQ-TRK-059.3` | `TST-TRK-059.4b` | `EditSensorFieldDialogSparklineTest` | ViewModel horizon state update and repository persistence | Specified |
| `REQ-UI-106` | `TST-TRK-059.4c` | `TranslationParityTest` | 100% 9-locale parity for horizon string resources | Specified |
| `REQ-PRO-001` | `TST-TRK-059.5` | Targeted Cockpit Suite | `--tests "com.atrainingtracker.trainingtracker.ui.tracking.editsensorfield.*"` | Specified |
