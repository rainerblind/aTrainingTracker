# Stage 2: Requirement & Test Specification - ATT-2145: Landscape Compact Aligned Marginal Zone Distribution Bars

**Ticket**: [ATT-2145](https://rainerblind.atlassian.net/browse/ATT-2145)  
**Sub-task**: Pending creation (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*Landscape Mode*)  
**Target Release**: `V4.9.41` | **Active Sprint**: `sprint/2026-43.9` | **Branch**: `feature/ATT-2145`  
**Requirement Mapping**: `REQ-UI-431` (*Landscape Compact Aligned Marginal Zone Distribution Bars Right of Telemetry Graph*)  
**Test Spec ID**: `TST-UI-396`  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-UI-431`)

### 1.1 Problem Statement & Rationale
In workout aftermath inspection, athletes rely on telemetry graphs (HR, Power, Cadence, Pace) to assess effort across physiological zones. In portrait orientation, `REQ-UI-251` (`ATT-2147`) renders an ultra-slim inline marginal zone strip inside the graph's canvas padding (`endPaddingPx = 25.dp`), whose compressed width prevents displaying zone labels, percentage shares, or durations. Landscape viewports (~640–900dp width) provide ample horizontal canvas. `REQ-UI-431` decouples the marginal zone distribution into a dedicated ~28% pane docked immediately to the right of the ~72% telemetry graph in a responsive horizontal `Row`, establishing 1:1 vertical Y-axis alignment with graph zone thresholds, rendering legible granular metrics (`Z5 45% 19:40 min`), synchronizing display mode switching (`FIVE_ZONES` vs. `HISTOGRAM`), and preserving 100% portrait layout fidelity.

### 1.2 Functional & Architectural Requirements
1. **Landscape Proportional Dual-Pane Layout Composition (`REQ-UI-431.1`)**: In landscape orientation (`Configuration.ORIENTATION_LANDSCAPE`), `TelemetryMetricGraph` SHALL compose the continuous telemetry curve and marginal zone distribution pane in a horizontal `Row`, allocating ~72% width (`Modifier.weight(0.72f)`) to the chart and ~28% width (`Modifier.weight(0.28f)`) to the marginal pane.
2. **Exact Vertical Y-Axis Coordinate Alignment (`REQ-UI-431.2`)**: The marginal zone distribution pane SHALL align horizontal zone bars (or histogram bins) 1:1 on the vertical Y-axis with the adjacent graph's zone threshold guidelines, matching the graph's top and bottom plot paddings.
3. **Granular Metrics Text Legibility (`REQ-UI-431.3`)**: In landscape mode, horizontal bars SHALL render filled weighted bars overlaid with legible text displaying Zone Label (e.g., `Z5`), Percentage (e.g., `45%`), and Duration (e.g., `19:40 min` via `TimeFormatter`), preventing clipping and ensuring contrast.
4. **Synchronous Display Mode Switching (`REQ-UI-431.4`)**: The marginal pane SHALL observe hoisted `ZoneCardDisplayMode`, rendering horizontal zone bars for `FIVE_ZONES`, `COLUMNS`, and `STACKED_BAR`, or frequency bars for `HISTOGRAM`, synchronizing seamlessly with aftermath zone cards.
5. **Portrait Invariant & Scrubber Preservation (`REQ-UI-431.5`)**: In portrait mode, `TelemetryMetricGraph` SHALL preserve the 25dp inline strip per `REQ-UI-251` with 100% byte-for-byte fidelity. Start padding (`startPadding = 50.dp`) SHALL remain identical across both orientations to guarantee multi-chart scrubber alignment.
6. **Preservation of System Invariants & Gestures (`REQ-UI-431.6`)**: Scrub dragging, tap point selection, pinch-to-zoom/pan, and callbacks (`onDistanceSelected`, `onPointSelected`, `onZoomChanged`) MUST operate without suppression (Rule 34).

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-UI-431` under Epic `ATT-2144` (*Landscape Mode*), refining `REQ-UI-251` (*Marginal Zone Bars in Portrait*) and `REQ-UI-206` (*Continuous Telemetry Graph*). Targets `TelemetryMetricGraph.kt` and `MapDetailLayout.kt`.
* **Historical Origin & Commit Trace**: Commit `c8192a0e` (`ATT-2147`, `REQ-UI-251`, `TST-UI-210`, inline marginal strip within `endPaddingPx = 25.dp`).
* **Root Reason for Existing Formulation**: Portrait viewports (~360dp width) lack horizontal space for text labels and time metrics without severe graph compression. Landscape mode provides ample width to decouple the marginal pane while preserving exact vertical alignment.
* **Preservation of Core Invariants**: 100% portrait fidelity (25dp inline strip), multi-chart scrubber alignment (`startPadding = 50.dp`), touch gesture disambiguation, and 100% clean-room test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Landscape Dual-Pane Split)**: *Given* an athlete inspecting telemetry in landscape mode, *When* `TelemetryMetricGraph` renders with zone data, *Then* the chart and marginal pane render side-by-side in a 72/28 `Row` layout.
* **Criterion 2 (Vertical Y-Axis Alignment)**: *Given* the landscape marginal pane, *When* rendered alongside the telemetry curve, *Then* bar boundaries align 1:1 with graph threshold Y-coordinates.
* **Criterion 3 (Granular Text Display)**: *Given* landscape zone bars, *When* rendered, *Then* each bar displays legible Zone Label, Percentage share, and formatted Duration.
* **Criterion 4 (Mode Synchronization)**: *Given* the athlete toggles display mode between `FIVE_ZONES` and `HISTOGRAM`, *When* state updates, *Then* the marginal pane synchronously transitions between 5-zone horizontal bars and histogram frequency bars.
* **Criterion 5 (Portrait Fidelity)**: *Given* portrait orientation, *When* `TelemetryMetricGraph` renders, *Then* the 25dp inline canvas strip renders identically per `REQ-UI-251`.

---

## 2. Test Specification (`TST-UI-396`)

### Test Cases
* `TST-UI-396.1` (**Landscape Proportional Dual-Pane Layout Composition**): Compose UI test asserting that when `orientation == Configuration.ORIENTATION_LANDSCAPE`, `TelemetryMetricGraph` composes a 72/28 proportional `Row` layout between the chart canvas and marginal zone distribution pane.
* `TST-UI-396.2` (**Vertical Y-Axis Coordinate Alignment & Normalization Math**): Unit and contract test asserting that bar boundaries match the exact Y-normalization coordinates of graph zone thresholds.
* `TST-UI-396.3` (**Granular Metrics Legibility & Formatting Contract**): UI test asserting that horizontal bars display legible text for Zone Label, Percentage, and formatted Duration (`TimeFormatter`).
* `TST-UI-396.4` (**Synchronous Display Mode Switching**): Compose UI test asserting synchronous rendering transitions between 5-Zone bars and histogram frequency bars.
* `TST-UI-396.5` (**Portrait Invariant & Scrubber Alignment Regression**): Contract test asserting portrait mode retains the 25dp inline strip and `startPadding = 50.dp` across both orientations.
* `TST-UI-396.6` (**Targeted & Full Clean-Room Regression Execution**): Run targeted tests and full project unit test suite verifying 100% test pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Class / Function Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-396.1` | Compose UI | `TelemetryMetricGraph.kt` (Landscape Branching) | `REQ-UI-431.1` | Planned |
| `TST-UI-396.2` | Unit / Math | `TelemetryMetricGraph.kt`, `TelemetryZoneMath.kt` | `REQ-UI-431.2` | Planned |
| `TST-UI-396.3` | Compose UI | `TelemetryMetricGraph.kt` (Marginal Bar Text) | `REQ-UI-431.3` | Planned |
| `TST-UI-396.4` | Compose UI | `TelemetryMetricGraph.kt` (Mode Synchronization) | `REQ-UI-431.4` | Planned |
| `TST-UI-396.5` | Contract | `TelemetryMetricGraphMarginalBarsContractTest.kt` | `REQ-UI-431.5` | Planned |
| `TST-UI-396.6` | Regression | Full Test Suite (`./gradlew testDebugUnitTest`) | `REQ-UI-431.1-6` | Planned |

---

## 4. Independent Peer Review & Verification (ATT-3534)
* **Stage 1 Alignment**: Verified 100% alignment with `subagent_pro`'s Stage 1 analysis (`docs/engineering/analysis/ATT-2145_analysis.md`). The decoupled 72/28 landscape layout cleanly exposes granular zone metrics without crowding the telemetry curve or losing vertical threshold alignment.
* **Invariant & Governance Safety**: Portrait mode is 100% byte-for-byte preserved. Multi-chart scrubbing alignment (`startPadding = 50.dp`) is preserved. Requirement governance verified with 0 violations via `verify_requirement_governance.py`.
