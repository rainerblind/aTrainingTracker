# Stage 2: Requirement & Test Specification - ATT-4774: Reclaim Vertical Space in WorkoutDetailTabbedContent

**Ticket**: [ATT-4774](https://rainerblind.atlassian.net/browse/ATT-4774)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*[Epic] Landscape Mode*)  
**Target Release**: `v2.44.0` | **Active Sprint**: `Sprint 2026-43.10` | **Branch**: `feature/ATT-4774`  
**Requirement Mapping**: `REQ-UI-433` (refining `REQ-UI-408`, `REQ-UI-431`, `REQ-UI-225`)  
**Test Spec ID**: `TST-UI-398` (`TST-UI-398.1` .. `TST-UI-398.5`)  
**Author**: AI Agent 1 (Implementer, `subagent_flash`) | **Date**: 2026-10-23  

---

## 1. Requirement Specification (`REQ-UI-433`)

### 1.1 Problem Statement & Rationale
In `WorkoutDetailTabbedContent` (especially in landscape orientation with ~411dp vertical height), pinned top chrome currently consumes ~55% of the viewport height. The 4-row `WorkoutHeader`, `PrimaryScrollableTabRow`, and dedicated 36dp `GlobalTelemetryZoomToolbar` row do not scroll away. Furthermore, HR/Power charts use a 72/28 split with double right padding (`endPaddingPx = 25.dp`), while sibling charts stay at 100% width, breaking X-axis tick and vertical scrubbing cursor alignment. Reclaiming this vertical space and aligning horizontal plot boundaries provides athletes with expansive, synchronized analytics views in landscape mode.

### 1.2 Functional & Architectural Requirements
1. **Scroll-Away Collapsing Top Header (`REQ-UI-433.1`)**: In `WorkoutDetailTabbedContent`, downward vertical scrolling in any tab SHALL smoothly collapse top chrome off-screen via `CollapsingAppBarNestedScrollConnection` (collapsing `WorkoutHeader` in portrait, and both `WorkoutHeader` and `PrimaryScrollableTabRow` in landscape mode). Upward scrolling SHALL smoothly reveal the header again (quick return).
2. **Single-Line Compact Landscape WorkoutHeader (`REQ-UI-433.2`)**: In landscape mode (`Configuration.ORIENTATION_LANDSCAPE`), `WorkoutHeader` SHALL collapse its 4 vertical rows into a single compact horizontal `Row(40.dp)`: `[Sport Icon] Title [Cluster Pill] • SportType • Date & Time [Actions]`.
3. **Zero-Extra-Row Inline / Floating Zoom Controls (`REQ-UI-433.3`)**: In `MapDetailLayout`, zoom/pan controls (`-`, `+`, `👆`) SHALL NOT reserve a dedicated 36dp vertical row, rendering as a compact overlay or inlined with the first chart header row.
4. **Stacked Multi-Chart 72% Plot Width & Scrubber Alignment (`REQ-UI-433.4`)**: In landscape mode, all stacked charts (`ElevationProfile`, `Speed/Pace`, `Heart Rate`, `Power`, `W'bal`) SHALL share the identical horizontal plot width (~72% from `startPadding = 50.dp` to plot right edge) and eliminate double right padding (`endPaddingPx = 0.dp` on the 72% curve pane) so X-axis ticks and vertical scrubbing cursors align 1:1 across all charts.
5. **Preserved Invariants (`REQ-UI-433.5`)**: 100% portrait layout fidelity, multi-chart scrub synchronization, gesture isolation, and clean-room unit test pass rate.

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Refines and amends `REQ-UI-408` (*Multi-Tab Workout Details Container*), `REQ-UI-431` (*Landscape Marginal Zone Distribution*), and `REQ-UI-225` (*Global Telemetry Zoom Toolbar*).
* **Historical Origin & Commit Trace**: Commit `ea5fa705` (`ATT-3920`), commit `dcb8f15f` (`ATT-2145`), and commit `1357ce0b` (`ATT-1876` / `ATT-3453`).
* **Root Reason for Existing Formulation**: Pinned chrome guaranteed visibility on portrait screens (~800dp height); `ATT-2145` docked marginal zone bars on HR/Power but left sibling charts at 100% width and zoom controls in a dedicated row, causing 55% vertical occlusion on landscape viewports (~411dp).
* **Preservation of Core Invariants**: 100% portrait fidelity, multi-chart scrubbing synchronization (`startPadding = 50.dp`), gesture isolation, and 100% clean-room test pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Scroll-Away Header)**: *Given* `WorkoutDetailTabbedContent`, *When* scrolling down inside a tab, *Then* top header chrome smoothly collapses off-screen, maximizing the vertical viewing area.
* **Criterion 2 (Landscape Single-Line Header)**: *Given* landscape orientation, *When* `WorkoutHeader` renders, *Then* sport icon, title, cluster pill, sport type, date/time, and action buttons render in a single compact row.
* **Criterion 3 (Zero-Row Zoom Controls)**: *Given* `MapDetailLayout`, *When* rendered, *Then* zoom controls consume zero dedicated row height.
* **Criterion 4 (1:1 X-Axis & Scrubber Alignment)**: *Given* stacked elevation and telemetry charts in landscape mode, *When* rendered, *Then* all charts share the 72% right boundary and scrubbing cursors align 1:1 vertically.

---

## 2. Test Specification (`TST-UI-398.1` .. `TST-UI-398.5`)

### Test Cases
* `TST-UI-398.1` (**Collapsing Header NestedScroll Contract Test**): Verify `WorkoutDetailTabbedContent` integrates `CollapsingAppBarNestedScrollConnection`; verify downward scroll collapses header off-screen and upward scroll restores it; verify landscape collapses both header and tab row.
* `TST-UI-398.2` (**Landscape Single-Line Compact WorkoutHeader Layout Test**): Verify under `ORIENTATION_LANDSCAPE` `WorkoutHeader` renders in a single horizontal row ($\le 44\text{dp}$); verify title, cluster pill, sport name, date/time, actions present; verify portrait multi-row layout preserved.
* `TST-UI-398.3` (**Zero-Extra-Row Inline / Floating Zoom Controls Contract Test**): Verify `MapDetailLayout` does not allocate a dedicated full-width 36dp vertical row for `GlobalTelemetryZoomToolbar`; verify controls are positioned as a compact overlay or inlined.
* `TST-UI-398.4` (**Landscape Stacked Charts 72% Plot Width & Double-Padding Elimination Test**): Verify `ElevationProfile`, `Speed/Pace`, and `W'bal` charts align right plot boundary to 72% in landscape mode; verify `endPaddingPx = 0.dp` on 72% pane in `TelemetryMetricGraph`; verify 1:1 vertical scrubber alignment.
* `TST-UI-398.5` (**Clean-Room Regression Execution**): Run unit test suite across modified components (`WorkoutDetailTabbedContentTest`, `WorkoutHeaderLandscapeTest`, `MapDetailLayoutTest`, `TelemetryMetricGraphLandscapeContractTest`, `ThemeColorCentralizationAuditTest`) asserting 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target Component Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-UI-398.1` | Contract / UI | `WorkoutDetailTabbedContent.kt` | `REQ-UI-433.1` | Planned |
| `TST-UI-398.2` | Layout / UI | `WorkoutHeader.kt` | `REQ-UI-433.2` | Planned |
| `TST-UI-398.3` | Layout / UI | `MapDetailLayout.kt` | `REQ-UI-433.3` | Planned |
| `TST-UI-398.4` | Math / Contract | `TelemetryMetricGraph.kt`, `ElevationProfile.kt` | `REQ-UI-433.4` | Planned |
| `TST-UI-398.5` | Regression | Full Aftermath & Theme Audit Suite | `REQ-UI-433.5` | Planned |
