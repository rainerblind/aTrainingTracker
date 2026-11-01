# Stage 5: Walkthrough & Verification - ATT-4930: Periods Maps: Re-Introduce Vertex Budget After Lowering Simplification Tolerance

**Ticket**: [ATT-4930](https://rainerblind.atlassian.net/browse/ATT-4930)  
**Parent Epic**: [ATT-4474](https://rainerblind.atlassian.net/browse/ATT-4474) (*Periods 2.0*)  
**Target Release**: `V4.9.41`  
**Active Sprint**: `2026-44.2`  
**Requirement Mapping**: `REQ-PER-019` (`REQ-PER-019.2`)  
**Test Mapping**: `TST-PER-034` (`TST-PER-034.1`..`TST-PER-034.5`)  
**Branch**: `feature/ATT-4930`  
**Author**: AI Agent 1 (Implementer - `subagent_flash`)  
**Date**: 2026-11-01  

---

## 1. Executive Summary & Verification Overview
Sprint Review 2026-44.1 of `ATT-4833` lowered Douglas-Peucker simplification tolerance in `PeriodMapUtils.kt` to 5–8m (Week/Month/Year) to ensure repeated passes align on identical pixels. However, the original `REQ-PER-019.2` vertex budget guarantee ("bound aggregate vertices <= 2,000 pts") was removed without replacement, leading to potential `LazyColumn` memory pressure and scroll jank on full-year periods with 200–500 workouts (5–10x point inflation). `ATT-4930` re-establishes explicit dual vertex budgets:
1. **Dual Vertex Budget Constants (`PeriodData.kt`)**: Added `MAX_CARD_MINI_MAP_VERTICES = 2_000` for period summary card previews and `MAX_DETAILED_MAP_VERTICES = 8_000` for detailed interactive map screens (`PeriodMapScreen`).
2. **Adaptive Decimation Engine (`PeriodMapUtils.kt`)**: Implemented `simplifyPathsToBudget` and `simplifyPathMapToBudget`. Pass 1 preserves base tolerances (5.0m Day/Week, 6.0m Month, 8.0m Year) and 100% shared-corridor pixel overlap when within budget. Pass 2 adaptively escalates tolerance ($\tau = \text{baseTolerance} \times \sqrt{R}$) only when aggregate vertices exceed the container budget, with clamped fallbacks.
3. **Card & Detail Map Integration**: Wired `simplifyPathsToBudget` into `prepareCardMapPreviewSync` and `simplifyPathMapToBudget` into `PeriodsViewModel.showPeriodMap()` on `Dispatchers.Default`. Restored `REQ-PER-019.2` KDoc.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Component | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PER-019.2` | `TST-PER-034.1` | `PeriodVertexBudgetTest.testTstPer034_1_CardMiniMapHardVertexBudget_300Workouts` | **PASSED** | `Verified` |
| `REQ-PER-019.2` | `TST-PER-034.2` | `PeriodVertexBudgetTest.testTstPer034_2_DetailedMapHardVertexBudget_300Workouts` | **PASSED** | `Verified` |
| `REQ-PER-019.2` / `REQ-PER-024.3` | `TST-PER-034.3` | `PeriodVertexBudgetTest.testTstPer034_3_BaseTolerancePreservationUnderModerateVolume` | **PASSED** | `Verified` |
| `REQ-PER-019.2` | `TST-PER-034.4` | `PeriodVertexBudgetTest.testTstPer034_4_AdaptiveDecimationConvergenceAndStability` | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-PER-034.5` | `PeriodVertexBudgetTest.testBudgetDefaultConstants` & `periodlist.*` suite | **PASSED** | `Verified` |

---

## 3. Automated Test Evidence

### Tier 1 Targeted Verification Suite (Rule 7 & Rule 60)
* **Explicit Test Scope**:
  - `com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*` (94 tests)
  - `com.atrainingtracker.trainingtracker.ui.map.*` (15 tests)
  - `com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest` (2 tests)
  - `com.atrainingtracker.trainingtracker.build.*` (13 tests)
* **Execution**: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.aftermath.periodlist.*" --tests "com.atrainingtracker.trainingtracker.ui.map.*" --tests "com.atrainingtracker.trainingtracker.ui.theme.ThemeColorCentralizationAuditTest" --tests "com.atrainingtracker.trainingtracker.build.*" --max-workers=2`
* **Result**: **BUILD SUCCESSFUL in 27s** (100% clean-room test pass rate, 0 failures).
* **ADB Device Verification**: `adb devices` checked; no attached hardware devices detected. Budget decimation math and bounding verified via targeted unit tests.
* **Requirement Governance**: `python3 tools/verify_requirement_governance.py` -> **PASSED** (code 0).

---

## 4. Invariant & Governance Verification
1. **Thread Safety & Non-Blocking UI**: Polyline decoding and budget decimation execute exclusively on `Dispatchers.Default`; zero main-thread GIS computation.
2. **Corridor Alignment Integrity**: Base 5–8m tolerances are preserved on pass 1 when within budget, preserving `ATT-4932` (single-pass mask) and `ATT-4929` (normalization) shared-corridor pixel overlap.
3. **Zero SQLite Migrations**: Pure in-memory and view-model background processing; no database schema alterations.
4. **Living Docs Updated**: `REQ-PER-019` and `TST-PER-034` marked as `Verified` in domain shards and master indexes.
5. **No Premature FixVersion**: Target Release `V4.9.41` maintained; `FixVersion` reserved for human Ceremony 2 sign-off.
