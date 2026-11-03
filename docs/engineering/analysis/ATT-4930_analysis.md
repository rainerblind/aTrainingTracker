# Stage 1 Analysis: ATT-4930 - Periods Maps: Re-Introduce Vertex Budget After Lowering Simplification Tolerance

**Ticket**: [ATT-4930](https://rainerblind.atlassian.net/browse/ATT-4930)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Branch**: `feature/ATT-4930`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Statement & Motivation
In sprint review 2026-44.1 of `ATT-4833` (Periods polish), Douglas-Peucker simplification tolerance in `PeriodMapUtils.kt` was lowered from 15 / 35 / 75m to 5 / 6 / 8m (Week / Month / Year) to ensure repeated passes align on identical pixels. However, the original `REQ-PER-019.2` vertex budget guarantee ("bound aggregate vertices <= 2,000 pts") was removed without replacement:
* For full-year periods with 200–500 workouts, aggregate vertex count expands 5–10x (tens of thousands of vertices), held concurrently across multiple cached `PeriodSummaryCard`s in `LazyColumn`.
* This causes excessive memory pressure, garbage collection churn, and potential scrolling jank (risking regressions against OOM fix `ATT-4863`).

---

## 2. Forensic Investigation & Codebase Findings
* **Fixed Tolerance Without Point Bounding**: `getPeriodSimplificationToleranceMeters(periodType)` returns static 5.0m (Day/Week), 6.0m (Month), and 8.0m (Year). Neither `prepareCardMapPreviewSync()` nor `PeriodsViewModel` counts aggregate points or enforces a ceiling.
* **Card Mini-Map vs. Detailed Map Asymmetry**: Mini-maps need an aggressive budget (`MAX_CARD_MINI_MAP_VERTICES = 2_000`) for smooth `LazyColumn` scrolling, whereas interactive detailed maps (`InteractivePeriodMap.kt`) can accommodate higher fidelity (`MAX_DETAILED_MAP_VERTICES = 8_000`) while still avoiding OOM/ANRs.
* **Shared-Corridor Preservation**: `ATT-4932` (single-pass coverage masks) and `ATT-4929` (corridor normalization) depend on shared road segments overlapping. Coarse fixed tolerances (e.g. 75m) distort geometry, whereas dynamic budget-driven scaling or raster snapping preserves shared alignments.

---

## 3. User Scope Grounding & Acceptance Criteria
* **In-Scope**:
  1. Re-introduce explicit vertex budgets: `MAX_CARD_MINI_MAP_VERTICES = 2_000` for card previews; `MAX_DETAILED_MAP_VERTICES = 8_000` for detailed interactive maps.
  2. Implement an adaptive two-tier simplification / budget enforcement algorithm (dynamic tolerance escalation or two-pass Douglas-Peucker decimation) that bounds aggregate points while preserving shared corridor overlap.
  3. Restore and update `REQ-PER-019.2` KDoc and formal requirement contract in `req_aftermath_periods.md`.
  4. Unit test verification with synthetic high-volume periods (e.g. 300 workouts) ensuring strict budget compliance.
* **Out-of-Scope**:
  * Altering single-pass mask rasterization (`ATT-4932`) or logarithmic normalization (`ATT-4929`).
  * Modifying Workout Clusters heatmap (`ATT-4931`).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement**: `REQ-PER-019.2` (*Douglas-Peucker Adaptive Decimation & Point Budgeting*).
* **Historical Origin**: `ATT-4477` (Full-period card map preview) originally bounded vertices to $\le 2,000\text{ pts}$ using progressive $15/35/75\text{m}$ tolerances. `ATT-4833` lowered tolerances to $5\text{--}8\text{m}$ for heatmap visual continuity, inadvertently abandoning the upper bound.
* **Resolution**: Re-establish the invariant with a smart adaptive decimation pipeline: start at optimal high-fidelity tolerances ($5\text{--}8\text{m}$), and dynamically escalate tolerance only when total vertices exceed the container budget.

---

## 5. Architectural Strategy & Technical Design (SWE.2)
* **Constants**: Define `MAX_CARD_MINI_MAP_VERTICES = 2_000` and `MAX_DETAILED_MAP_VERTICES = 8_000` in `PeriodData.kt`.
* **Budget-Aware Simplifier**: In `PeriodMapUtils.kt`, create `simplifyPathsToBudget(paths: List<List<LatLng>>, maxVertices: Int, baseTolerance: Double): List<List<LatLng>>`:
  * Pass 1: Simplify paths using `baseTolerance` ($5\text{--}8\text{m}$).
  * If $\sum \text{size} \le \text{maxVertices}$, return immediately.
  * Pass 2: If vertices exceed budget, compute ratio $R = \sum \text{size} / \text{maxVertices}$ and re-simplify with escalated tolerance $\tau = \text{baseTolerance} \times \sqrt{R}$ (clamped to sensible maximums).
* **Integration**: Wire into `prepareCardMapPreviewSync` (`MAX_CARD_MINI_MAP_VERTICES`) and `PeriodsViewModel` / `InteractivePeriodMap` (`MAX_DETAILED_MAP_VERTICES`).

---

## 6. System Invariants & Risk Assessment
* **Invariants**: All decoding and decimation remain off-thread on `Dispatchers.Default`; no database schema alterations; zero UI-thread jank.
* **Risk Rating**: **LOW** — Pure algorithmic refinement in background map pipeline with comprehensive unit test coverage.
