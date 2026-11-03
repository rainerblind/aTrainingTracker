# Stage 1 Analysis: ATT-4774 - Reclaim Vertical Space in WorkoutDetailTabbedContent: Scroll-Away Collapsing Header, Single-Line Landscape WorkoutHeader, Inline Zoom Controls, and 72% X-Axis Plot Alignment

**Ticket**: [ATT-4774](https://rainerblind.atlassian.net/browse/ATT-4774)  
**Sub-task**: [ATT-4782](https://rainerblind.atlassian.net/browse/ATT-4782) (`[Analysis]`)  
**Parent Epic**: [ATT-2144](https://rainerblind.atlassian.net/browse/ATT-2144) (*[Epic] Landscape Mode*)  
**Target Release**: `v2.44.0`  
**Active Sprint**: `Sprint 2026-43.10`  
**Branch**: `feature/ATT-4774`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation
* In `WorkoutDetailTabbedContent` (especially in landscape orientation with ~411dp height), pinned top chrome consumes >50% vertical viewport height:
  1. `WorkoutHeader` stacks 4 vertical rows unconditionally above `HorizontalPager` without scroll-away behavior.
  2. `PrimaryScrollableTabRow` (48dp), dividers, and `GlobalTelemetryZoomToolbar` (36dp) consume dedicated rows.
  3. `TelemetryMetricGraph` (Heart Rate, Power) uses a 0.72f / 0.28f split with double right padding (`endPaddingPx = 25.dp`), while stacked sibling charts (`ElevationProfile`, `Speed/Pace`, `W'bal`) remain at 100% width, breaking X-axis tick and vertical scrubbing cursor alignment.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Forensic Root Cause Analysis**:
  * `WorkoutDetailTabbedContent.kt`: Top header and tab row are placed directly in an outer `Column` above `HorizontalPager` without a `NestedScrollConnection`, preventing child scrolls in Mode A/B from collapsing top chrome.
  * `WorkoutHeader.kt`: Layout hardcodes a 4-row vertical `Column` without branching on `Configuration.ORIENTATION_LANDSCAPE`.
  * `MapDetailLayout.kt`: Allocates a dedicated full-width `36.dp` row (`GlobalTelemetryZoomToolbarDefaults.TOOLBAR_HEIGHT`) directly in the upper Column.
  * `TelemetryMetricGraph.kt` & `ElevationProfile.kt`: `TelemetryMetricGraph` retains `endPaddingPx = 25.dp` inside the 72% box; sibling charts lack 72% plot width alignment in landscape mode.
* **Independent Peer Review & Verification**:
  * Audited `WorkoutDetailTabbedContent.kt` (lines 144–232): Confirmed zero nested scroll connection; header is completely static.
  * Audited `WorkoutHeader.kt` (lines 111–337): Confirmed 4 stacked vertical rows taking ~140dp in all orientations.
  * Audited `MapDetailLayout.kt` (lines 689, 731, 769): Confirmed dedicated 36dp toolbar row.
  * Audited `TelemetryMetricGraph.kt` (lines 396, 800) and `ElevationProfile.kt` (line 658): Confirmed double padding and width divergence.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope Goals**:
  * Wire collapsing `NestedScrollConnection` in `WorkoutDetailTabbedContent` (collapsing header in portrait; collapsing header + tab row in landscape on downward scroll, quick return on upward scroll).
  * Single-line compact `WorkoutHeader` in landscape mode: `[Icon] Title [Cluster] • SportType • Date & Time [Actions]`.
  * Inline / floating zoom controls in `MapDetailLayout` (reclaiming the dedicated 36dp row).
  * 1:1 horizontal X-axis and scrubber alignment across stacked charts in landscape (72% plot width, 0dp double padding).
* **Out-of-Scope Non-Goals (Scope Bounding)**:
  * Modifying tab persistence or `MyPreferenceManager` tab order settings (`ATT-3920`).
  * Modifying Mapbox/Google Maps camera projections or GPS tracking logic.
  * Altering physiology model calculations or zone definitions.

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-UI-408` (`WorkoutDetailTabbedContent.kt`), `REQ-UI-431` (`TelemetryMetricGraph.kt`), `REQ-UI-225` (`MapDetailLayout.kt`).
* **Historical Origin & Commit Trace**: Commit `ea5fa705` (`ATT-3920`), commit `dcb8f15f` (`ATT-2145`), and commit `1357ce0b` (`ATT-1876` / `ATT-3453`).
* **Root Reason for Existing Formulation**: Pinned headers guaranteed navigation discoverability in portrait viewports; `ATT-2145` docked marginal zone bars on HR/Power graphs but left sibling charts and toolbar rows untouched.
* **Preservation of Core Invariants**: 100% portrait layout fidelity (25dp inline strip), multi-chart vertical scrubbing synchronization, touch gesture isolation, and 100% clean-room test pass rate.

---

## 5. Architectural Strategy & High-Level Solution
| Component | Architectural Strategy |
| :--- | :--- |
| `WorkoutDetailTabbedContent.kt` | Attach `CollapsingAppBarNestedScrollConnection` with dynamic `appBarMaxHeight` (header in portrait; header + tabs in landscape) and offset translation. |
| `WorkoutHeader.kt` | Branch on `Configuration.ORIENTATION_LANDSCAPE` to render a single compact `Row(40.dp)`. |
| `MapDetailLayout.kt` | Replace dedicated 36dp toolbar row with compact right-aligned floating overlay (`Alignment.TopEnd`) or chart header inlining. |
| `TelemetryMetricGraph.kt` & `ElevationProfile.kt` | In landscape, enforce 72% plot width across all stacked charts and set `endPaddingPx = 0.dp` on the 72% curve pane. |

---

## 6. System Invariants & Risk Assessment
* **Core Invariants**:
  1. Zero regression in portrait aftermath inspection or existing unit tests (`WorkoutDetailTabsTest`, `TelemetryMetricGraphLandscapeContractTest`).
  2. Multi-chart horizontal scrub synchronization (`selectedDistance`) preserved.
  3. Parent ticket Human Decision Gate strictly enforced (`Final Review (Human)`).
* **Risk Rating**: **MEDIUM** (multi-component UI coordinate adjustments in landscape; bounded blast radius verified via contract unit tests and Compose previews).
