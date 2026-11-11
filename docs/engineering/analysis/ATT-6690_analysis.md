# Stage 1 Analysis: ATT-6690 - EditSensorFieldDialog: Replace 7-chip FlowRow for Sparkline Time Horizon with stepped Slider

**Ticket**: [ATT-6690](https://rainerblind.atlassian.net/browse/ATT-6690)  
**Sub-task**: `[Analysis]`  
**Parent Epic**: [ATT-5908](https://rainerblind.atlassian.net/browse/ATT-5908) (*Cockpit & Live-Tracking UX*)  
**Target Release**: `V5.0.0`  
**Active Sprint**: `2026-46.2`  
**Branch**: `feature/ATT-6690`  
**Author**: AI Agent 1 (Implementer, `subagent_pro`)  
**Date**: 2026-11-11  

---

## 1. Problem Statement & Motivation
In `EditSensorFieldDialog.kt` (lines 206–238), the Sparkline Time Horizon (*Zeitfenster*) selector renders 7 discrete duration choices (`1m`, `2m`, `5m`, `10m`, `20m`, `1h`, `Full Workout`) as 7 `FilterChip`s inside a `FlowRow`. On standard portrait phone viewports, the trailing chip wraps onto an isolated second row, violating `docs/design_guidelines.md` §1.4 (*Discrete Scalar Ranges & Durations, $\ge 5$ Numeric Values*), which mandates a stepped `Slider` with snap steps and a live value indicator rather than multi-chip wrapping rows.

---

## 2. Root Cause Analysis & Independent Peer Review (4-Augen-Prinzip)
* **Gap Analysis**:
  1. *Violation of Design Guidelines §1.4*: Using `FilterChip` inside `FlowRow` for scalar sets with $\ge 5$ values produces ragged line wraps, awkward button heights, and wasted vertical dialog space.
  2. *Header Staticness*: The current section header displays only static text `"Zeitfenster"` (`sensor_field_graph_horizon`) without indicating the currently selected duration.
* **Independent Peer Review & Verification**:
  - *Call-Site Audit*: The selector exists exclusively in `EditSensorFieldDialog.kt` (lines 206–238) inside `Column(Modifier.verticalScroll(scrollState))`.
  - *Discrete Value Set*: The 7 discrete duration seconds are: `[60, 120, 300, 600, 1200, 3600, 0]`.
  - *Slider Mapping*: A stepped `Slider` with `valueRange = 0f..6f` and `steps = 5` maps index $i \in 0..6$ directly to the 7 duration options. Snapping to discrete steps (`Math.round(value).toInt().coerceIn(0, 6)`) avoids invalid intermediate values.
  - *Header Value Indicator*: A single-line header `Row(fillMaxWidth, SpaceBetween)` displaying the label on the left and the active localized duration (e.g., `"5 min"` or `"Gesamtes Training"`) on the right in `MaterialTheme.colorScheme.primary` provides instant readability.

---

## 3. User Scope Grounding (ATT-1250) & Complexity Split Guardrail (REQ-PRO-029)
* **In-Scope Goals**:
  - Replace `FlowRow` + 7 `FilterChip`s in `EditSensorFieldDialog.kt` with a stepped `Slider` (7 positions, 5 intermediate steps).
  - Add active localized horizon string indicator in the section header row.
  - Add min (`1 min`) and max (`Full workout`) range hints below the slider.
  - Maintain 100% backward compatibility with `EditSensorFieldViewModel.onGraphHorizonChanged`.
  - Add contract/unit tests verifying index-to-horizon mapping and header string resolution.
* **Out-of-Scope Non-Goals**: Modifying `EditSensorFieldViewModel` state logic, database schemas, or `LiveSparklineBuffer` ingestion.
* **Complexity Estimation**: 1 SP (Trivial UI control replacement in a single dialog; zero business logic changes).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines `REQ-TRK-059` (`REQ-TRK-059.3` / `REQ-TRK-059.4` *Edit Dialog Presentation & Time Horizon Configuration*) in `docs/requirements/req_tracking_cockpit.md`.
* **Historical Origin & Commit Trace**: `ATT-1294` introduced `FilterChip` in `FlowRow` during initial live sparkline configuration prototyping.
* **Root Reason for Existing Formulation**: Quick initial implementation of 7 selectable options using standard chip components before design guideline audit §1.4 was formalized.
* **Preservation of Core Invariants**: Exactly the same 7 discrete duration horizons (`60s`, `120s`, `300s`, `600s`, `1200s`, `3600s`, `0s`) remain selectable; persistence in `TrackingViewsDatabaseManager` column `GraphHorizonS` is 100% unchanged; all 9-locale strings remain in use.

---

## 5. Architectural Strategy & High-Level Solution
* **Component Refactoring**: In `EditSensorFieldDialog.kt`, replace `FlowRow` with:
  1. Header `Row(fillMaxWidth, Arrangement.SpaceBetween)` with label and active duration value text.
  2. Stepped `Slider(value = selectedIndex.toFloat(), onValueChange = { ... }, valueRange = 0f..6f, steps = 5)`.
  3. Range annotation `Row(fillMaxWidth, Arrangement.SpaceBetween)` with `"1 min"` (start) and `"Full workout"` (end).

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**: Zero regression in `EditSensorFieldViewModel`; 100% 9-language translation parity; zero Compose snapshot state writes during composition (Rule 66).
* **Risk Rating**: **LOW** (Pure UI composable replacement in a single dialog).
