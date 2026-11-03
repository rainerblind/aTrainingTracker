# Stage 2: Requirement & Test Specification - ATT-4930: Periods Maps: Re-Introduce Vertex Budget After Lowering Simplification Tolerance

**Ticket**: [ATT-4930](https://rainerblind.atlassian.net/browse/ATT-4930)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-019.2` (*Dual Vertex Budget Enforcement*)  
**Test Mapping**: `TST-PER-034` (`TST-PER-034.1`..`TST-PER-034.5`)  
**Branch**: `feature/ATT-4930`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Overview & Verification Strategy
Sprint Review 2026-44.1 (`ATT-4833`) lowered Douglas-Peucker simplification tolerance in `PeriodMapUtils.kt` to 5–8m (Week/Month/Year) to ensure repeated passes align on identical pixels. However, the original `REQ-PER-019.2` vertex budget guarantee ("bound aggregate vertices <= 2,000 pts") was dropped, causing memory pressure and potential `LazyColumn` scroll jank on full-year periods with 200–500 workouts (5–10x vertex inflation). `ATT-4930` re-introduces explicit dual vertex budgets: `MAX_CARD_MINI_MAP_VERTICES = 2_000` for card previews and `MAX_DETAILED_MAP_VERTICES = 8_000` for detailed interactive maps (`PeriodMapScreen`). An adaptive budget decimation algorithm (`simplifyPathsToBudget`) preserves base tolerances (5–8m) and shared-corridor pixel overlap under normal loads, escalating tolerance only when aggregate vertices exceed the container ceiling.

---

## 2. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Component | Scope & Target |
| :--- | :--- | :--- | :--- |
| `REQ-PER-019.2` | `TST-PER-034.1` | `PeriodMapUtilsTest` / `PeriodCardMapStateTest` | Synthetic 300-workout Year period bounded to $\le 2,000$ pts on card previews (`MAX_CARD_MINI_MAP_VERTICES`). |
| `REQ-PER-019.2` | `TST-PER-034.2` | `PeriodMapUtilsTest` / `PeriodsViewModelTest` | Detailed interactive maps bound aggregate paths to $\le 8,000$ pts (`MAX_DETAILED_MAP_VERTICES`). |
| `REQ-PER-019.2` / `REQ-PER-024.3` | `TST-PER-034.3` | `PeriodMapUtilsTest` | Low/moderate volume periods retain base tolerances (5–8m) with 100% shared-corridor pixel overlap. |
| `REQ-PER-019.2` | `TST-PER-034.4` | `PeriodMapUtilsTest` | Extreme point-density paths converge within finite decimation passes without exceptions or empty paths. |
| `REQ-PRO-001` | `TST-PER-034.5` | Targeted Test Suite | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*"` 100% pass rate. |

---

## 3. Concrete Test Scenarios (Given-When-Then)

### TST-PER-034.1: Card Mini-Map Hard Vertex Budget Enforcement
* **Given** a synthetic Year period with 300 workouts (each containing 200 GPS vertices, totaling 60,000 raw points),
* **When** `prepareCardMapPreviewSync()` computes `PeriodCardMapState`,
* **Then** the aggregate vertex count across all decoded and simplified paths is $\le 2,000$ points (`MAX_CARD_MINI_MAP_VERTICES`).

### TST-PER-034.2: Detailed Map Hard Vertex Budget Enforcement
* **Given** a synthetic Year period with 300 workouts loaded in `PeriodsViewModel`,
* **When** `simplifyPathsToBudget()` executes with `MAX_DETAILED_MAP_VERTICES`,
* **Then** total vertices across all member heatmap paths are bounded to $\le 8,000$ points without UI thread blocking.

### TST-PER-034.3: Base Tolerance Preservation Under Moderate Volume
* **Given** a period where total simplified vertices at base tolerance (5.0m Day/Week, 6.0m Month, 8.0m Year) are below budget,
* **When** `simplifyPathsToBudget()` runs,
* **Then** base tolerances are preserved identically without coarsening, preserving 100% shared-corridor pixel alignment.

---

### Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-PER-019.2` (*Douglas-Peucker Adaptive Decimation & Point Budgeting*). Targets `PeriodMapUtils.kt`, `PeriodData.kt`, `PeriodSummaryCard.kt`, `PeriodsViewModel.kt`.
* **Historical Origin & Commit Trace**: Commit `da70647f` (`ATT-4477`, established <= 2,000 pts bound with 15/35/75m tolerances), commit `2bea16b7` (`ATT-4833`, lowered tolerances to 5-8m for corridor alignment, removing point budget), and `ATT-4930`.
* **Root Reason for Existing Formulation**: ATT-4833 tightened tolerances to 5–8m to ensure repeated passes align on identical pixels for additive heatmap stacking, but omitted an aggregate ceiling, leading to heap pressure on Year cards with 200–500 workouts.
* **Preservation of Core Invariants**: Shared-corridor alignment on typical workloads, 60 fps `LazyColumn` scrolling, thread safety on `Dispatchers.Default`, and memory safety without OOMs remain 100% preserved.

---

## 4. Invariants & Governance
* **Zero Main-Thread Computation**: All polyline decoding and decimation execute off-thread on `Dispatchers.Default`.
* **Corridor Overlap Preservation**: Tolerance escalation only triggers when vertex budget is exceeded.
* **Target Release Parity**: `V4.9.41` verified as active/unreleased in Jira.
