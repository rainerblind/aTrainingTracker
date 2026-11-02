# Stage 3: Implementation Plan - ATT-4930: Periods Maps Vertex Budget Enforcement

**Ticket**: [ATT-4930](https://rainerblind.atlassian.net/browse/ATT-4930)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-019.2` (Dual Vertex Budget Enforcement)  
**Test Mapping**: `TST-PER-034` (`TST-PER-034.1`..`TST-PER-034.5`)  
**Branch**: `feature/ATT-4930`  
**Author**: AI Agent 1 (Implementer - `subagent_pro`)  
**Date**: 2026-11-01  

---

## 1. Problem Description & Background
Sprint Review 2026-44.1 (`ATT-4833`) lowered Douglas-Peucker simplification tolerance in `PeriodMapUtils.kt` to 5–8m (Week/Month/Year) to ensure repeated passes align on identical pixels. However, the original `REQ-PER-019.2` vertex budget guarantee ("bound aggregate vertices <= 2,000 pts") was dropped, causing memory pressure and potential `LazyColumn` scroll jank on full-year periods with 200–500 workouts (5–10x vertex inflation). `ATT-4930` re-introduces explicit dual vertex budgets: `MAX_CARD_MINI_MAP_VERTICES = 2_000` for card previews and `MAX_DETAILED_MAP_VERTICES = 8_000` for detailed interactive maps (`PeriodMapScreen`). An adaptive budget decimation algorithm (`simplifyPathsToBudget`) preserves base tolerances (5–8m) and shared-corridor pixel overlap under normal loads, escalating tolerance only when aggregate vertices exceed the container ceiling.

---

## 2. Traceability & Requirements Mapping
* **`REQ-PER-019.2` / `TST-PER-034.1`**: Card mini-map hard budget enforcement ($\le 2,000$ points) via `MAX_CARD_MINI_MAP_VERTICES`.
* **`REQ-PER-019.2` / `TST-PER-034.2`**: Detailed map hard budget enforcement ($\le 8,000$ points) via `MAX_DETAILED_MAP_VERTICES`.
* **`REQ-PER-019.2` / `TST-PER-034.3`**: Baseline tolerance preservation ($5\text{--}8\text{m}$) and 100% shared corridor alignment under moderate volume.
* **`REQ-PER-019.2` / `TST-PER-034.4`**: Finite algorithmic convergence on extreme-density paths without empty lists or exceptions.
* **`REQ-PRO-001` / `TST-PER-034.5`**: Clean-room regression verification across `ui.aftermath.periodlist.*` test suite.

---

## 3. System Invariants & Preserved Behavior
1. **Thread Safety & Non-Blocking UI**: Polyline decoding and budget decimation execute exclusively on `Dispatchers.Default`.
2. **Corridor Alignment Integrity**: Periods under budget retain base 5–8m tolerances for `ATT-4932` (single-pass mask) and `ATT-4929` (normalization).
3. **Zero SQLite Migrations**: In-memory and cached view-model computation only.
4. **Code Lock (Rule 4)**: Production code modifications locked until Gate 3 audit passes.

---

## 4. Proposed Architectural Changes (SWE.2)
* **Constants Definition (`PeriodData.kt`)**:
  * Add `const val MAX_CARD_MINI_MAP_VERTICES = 2_000`
  * Add `const val MAX_DETAILED_MAP_VERTICES = 8_000`
* **Adaptive Decimation Engine (`PeriodMapUtils.kt`)**:
  * Implement `simplifyPathsToBudget(paths: List<List<LatLng>>, maxVertices: Int, baseTolerance: Double): List<List<LatLng>>`:
    1. Fast pass: Run `PolyUtil.simplify` with `baseTolerance`.
    2. Point count check: If $\sum \text{size} \le \text{maxVertices}$, return immediately.
    3. Escalation pass: If over budget, calculate $R = \sum \text{size} / \text{maxVertices}$ and re-simplify with $\tau = (\text{baseTolerance} \times \sqrt{R}).\text{coerceIn}(\text{baseTolerance}, 80.0)$.
* **Caller-Site Wiring**:
  * In `prepareCardMapPreviewSync()` (`PeriodMapUtils.kt`): apply `simplifyPathsToBudget` with `MAX_CARD_MINI_MAP_VERTICES`.
  * In `PeriodsViewModel.kt` (`prepareMapState`): apply `simplifyPathsToBudget` with `MAX_DETAILED_MAP_VERTICES` before setting `mapState`.
  * Restore and update KDoc for `REQ-PER-019.2`.

### UI Consistency (Rule 23)
* **Reference Component**: Existing Period Summary mini-maps and `InteractivePeriodMap.kt`.
* **Behavior**: Zero visual change under normal workloads; guaranteed fluid 60 fps `LazyColumn` scrolling under 300+ workout Year periods.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
1. **Step 1 (`PeriodData.kt`)**: Define `MAX_CARD_MINI_MAP_VERTICES` and `MAX_DETAILED_MAP_VERTICES`.
2. **Step 2 (`PeriodMapUtils.kt`)**: Implement `simplifyPathsToBudget` and update KDoc for `REQ-PER-019.2`.
3. **Step 3 (`PeriodMapUtils.kt` & `PeriodSummaryCard.kt`)**: Wire `simplifyPathsToBudget` into `prepareCardMapPreviewSync`.
4. **Step 4 (`PeriodsViewModel.kt`)**: Wire `simplifyPathsToBudget` into detailed map path preparation.
5. **Step 5 (Unit Tests)**: Author `TST-PER-034.1`..`TST-PER-034.4` tests in `PeriodMapVisualsTest.kt` / `PeriodCardMapStateTest.kt`.
6. **Step 6 (Verification)**: Run targeted test suite and governance checks.

---

## 6. Verification & Rollback Plan
* **Targeted Verification**:
  * `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"`
  * `python3 tools/verify_requirement_governance.py`
* **Rollback Plan**: Isolated git branch on `feature/ATT-4930`.
