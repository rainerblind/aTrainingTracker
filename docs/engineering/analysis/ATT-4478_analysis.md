# Stage 1 Analysis: ATT-4478 - Upgrade PeriodBarGraph with Rest Spacing, Sub-Hour Labels, X-Axis & Sport/Metric Modes

**Ticket**: [ATT-4478](https://rainerblind.atlassian.net/browse/ATT-4478)  
**Sub-task**: Pending (`[Analysis]`)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0: Self-Contained Persistence, Full-History Map Previews & Timeline Analytics*)  
**Target Release**: Sprint 2026-43.9 | **Active Sprint**: `2026-43.9`  
**Branch**: `feature/ATT-4478`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-23  

---

## 1. Problem Statement & Motivation

The `PeriodBarGraph` header in `PeriodsTabsScreen.kt:207-277` exhibits three analytical and UX limitations:
* **Collapsed Rest Periods**: `PeriodSummaries.db` only stores records for periods where `totalWorkouts > 0`. Directly plotting `periods.reversed()` collapses rest days/weeks out of the timeline, distorting temporal progression and hiding training consistency.
* **Missing Sub-Hour & X-Axis Labels**: Integer division (`totalDurationSec / 3600`) renders zero text when a period's duration is `< 60` minutes (e.g. 45-min run). Bars lack compact X-axis date/period identifiers (`12.10`, `W42`, `Oct`, `2026`).
* **Monolithic Duration-Only Bars**: Bars render as uniform primary-colored blocks without sport-type breakdown (`BIKE`, `RUN`, `OTHER`) and offer no metric switching (Duration, Distance, Ascent).

---

## 2. Root Cause Analysis & Independent Peer Review (ATT-3534)

* **Root Cause**: `PeriodBarGraph` was implemented as a minimal 70-line inline helper plotting raw `PeriodSummary` items without timeline gap interpolation, sub-hour formatting branches, or multi-metric evaluation.
* **Independent Verification & Feasibility**:
  * *Component Decoupling*: Extract `PeriodBarGraph` into dedicated `PeriodBarGraph.kt` under `ui.aftermath.periodlist`.
  * *Timeline Gap Filling*: Given chronological bounds of `periods`, generate continuous `PeriodBarItem` buckets for missing calendar units (days/weeks/months) up to a bounded window (e.g., 60 days / 52 weeks / 24 months), rendering subtle baseline ticks (height 2 dp) for rest days.
  * *Sub-Hour Formatting*: Duration `< 60s` $\rightarrow$ `<1m`; `< 1h` $\rightarrow$ `${m}m` (e.g. `45m`); `1h..10h` $\rightarrow$ `${h.m}h` or `${h}h`; `>10h` $\rightarrow$ `${h}h`.
  * *Sport-Stacked Proportions*: `SportStats` already contains `totalDurationSec`, `totalDistanceMeters`, `totalAscentMeters`, and `count` mapped by `BSportType` in `PeriodSummary.sportStats`. Bars can stack vertical segments for `BIKE`, `RUN`, and `OTHER` proportionately.
  * *Metric Toggle*: Three modes (`DURATION`, `DISTANCE`, `ASCENT`) toggled via compact Material 3 chips/segmented controls, backed by 100% localized strings (`R.string.filter_section_duration`, `filter_section_distance`, `stats_ascent`).

---

## 3. User Scope Grounding (ATT-1250)

* **In-Scope Goals**:
  * Extract and implement standalone `PeriodBarGraph.kt` with timeline gap interpolation.
  * Sub-hour duration and metric value formatting on top of active bars.
  * Compact X-axis date/period labels below bars.
  * Sport-stacked bar rendering (`BIKE`, `RUN`, `OTHER`) with accessible category tints.
  * Compact metric toggle (Duration / Distance / Ascent) with responsive bar scaling.
  * Bidirectional scroll and selection synchronization with `PeriodList`.
  * Unit tests covering timeline interpolation, metric scaling, label formatting, and click dispatch.
* **Out-of-Scope Non-Goals**:
  * Changes to `PeriodSummaries.db` schema or SQL queries (`ATT-4476`).
  * Changes to Map/Heatmap rendering (`ATT-4475`, `ATT-4480`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit

* **Original Requirements**: `REQ-PER-001`, `REQ-PER-007` in `docs/requirements/req_aftermath_periods.md`.
* **Historical Origin**: Sprint 2026-40.16 (`ATT-375` scroll sync, `ATT-440` layout spacing, `ATT-488` safety checks).
* **Preservation of Core Invariants**: Retains `LaunchedEffect` scroll synchronization with `LazyListState`, active bar selection highlighting, click-to-scroll navigation to `PeriodSummaryCard`, and zero-crash guarantees on empty/single lists.

---

## 5. Architectural Strategy & High-Level Solution

| Component | Responsibility |
| :--- | :--- |
| `PeriodBarGraph.kt` | Standalone composable with metric selector, timeline gap interpolation, stacked sport bars, and sub-hour labels. |
| `PeriodBarItem` | Internal model representing an active period or zero-volume rest bucket with computed metric heights. |
| `PeriodsTabsScreen.kt` | Hosts `PeriodBarGraph` in collapsing header column above `PeriodList`. |

---

## 6. System Invariants & Risk Assessment

* **System Invariants**: Zero database schema migrations; 9-language localization parity maintained without missing keys; strict non-crashing on empty lists; bidirectional scroll synchronization preserved.
* **Risk Rating**: **LOW** (Isolated UI presentation component in `PeriodsTabsScreen`; no background services, database writes, or sensor pipelines affected).
