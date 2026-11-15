# Stage 1 Analysis: ATT-7905 - Java Core Migration: Migrate pure signal filters to Kotlin

**Ticket**: [ATT-7905](https://rainerblind.atlassian.net/browse/ATT-7905)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Statement & Motivation
Under ADR-001 (`REQ-ARC-005`, ATT-7566), 11 signal filter classes in `com.atrainingtracker.banalservice.filters` (1,049 LOC) remain in legacy Java. Calling them from Kotlin induces platform types (`T!`), risking nullability hazards during high-frequency telemetry ingestion. Migrating these pure algorithms to idiomatic Kotlin restores compile-time null safety, type invariance, and eliminates boilerplate.

---

## 2. Root Cause Analysis & Independent Hypothesis Verification
* **Independent Peer Review & Verification**:
  * *Ticket Hypothesis*: Description lists `AlphaFilter`, `MovingAverageFilter`, `LinearRegressionFilter`, `MedianFilter`, `RateOfChangeFilter`.
  * *Codebase Reality*: Neither `AlphaFilter` nor `LinearRegressionFilter`/`MedianFilter`/`RateOfChangeFilter` exist in `banalservice/filters/`. Exponential smoothing is handled by `ExponentialSmoothingFilter.java` ($y_k = \alpha x_k + (1-\alpha)y_{k-1}$).
  * *Full Inventory (11 classes, 1,049 LOC)*: `MyFilter`, `AverageFilter`, `ExponentialSmoothingFilter`, `MovingAverageFilter`, `NumberedMovingAverageFilter`, `TimedMovingAverageFilter`, `MaxValueFilter`, `InstantaneousFilter`, `FilterData`, `FilteredSensorData`, `FilterManager`.
  * *PO Decision & Retro ATT-7749*: Only Formatters (`ATT-7903`), Signalfilters (`ATT-7905`), and Exporters (`ATT-7906`) migrate; others remain `Keep`. `JavaCoreMigrationStrategyContractTest.kt` line 169 enforces `assertEquals(149, javaFiles.size)`, which must be loosened to `<= 149`.

---

## 3. User Scope Grounding (ATT-1250)
* **In-Scope**:
  * Convert all 11 Java classes in `com/atrainingtracker/banalservice/filters/` to idiomatic Kotlin (`.kt`).
  * Remove unused imports (`DevicesDatabaseManager`, `TrackingViewsDatabaseManager`) in `FilterManager`.
  * Update `JavaCoreMigrationStrategyContractTest.kt` assertion (`assertEquals(149, ...)` $\to$ `<= 149`).
  * Update `docs/architecture/adr_001_java_core_services_migration.md` to document the PO scope decision.
* **Out-of-Scope**:
  * Algorithmic redesign or adding new filter types (Kalman, Median, Rate of Change).
  * Refactoring `FilterManager` to Coroutine `Flow` (preserved as listener-based for SWE.5 seam stability).

---

## 4. Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: `REQ-ARC-005.1` & `REQ-ARC-005.6` (`docs/requirements/req_process_stability.md`).
* **Historical Origin & Commit Trace**: Added in Sprint `2026-46.6` via `ATT-7566` to formalize ADR-001.
* **Root Reason for Existing Formulation**: Pinned exact 149 count to prevent uncoordinated Java drift.
* **Preservation of Core Invariants**: Loosening assertion to `<= 149` permits authorized downward migration while continuing to prohibit unauthorized Java class additions.

---

## 5. Change Impact Analysis (SUP.10)
* **Affected Requirements**: `REQ-ARC-005` (updated contract tolerance `<= 149`) and `REQ-FIL-014` (pure Kotlin filter equivalence).
* **Affected Architecture Elements**: `banalservice.filters.*` (11 classes), `docs/architecture/adr_001_java_core_services_migration.md`, `JavaCoreMigrationStrategyContractTest.kt`.
* **Verification Impact & Test Surface**: All 6 `banalservice.filters.*` test suites, `JavaCoreMigrationStrategyContractTest`, adjacent `BANALService` tests.
* **Risk Assessment & Invariants**: Low risk. Thread synchronization (`@Synchronized`), post-trim emptiness check (Rule 50), and exact floating-point smoothing behavior are strictly preserved.

---

## 6. Architectural Strategy & High-Level Solution
* Direct 1:1 translation of 11 classes to idiomatic Kotlin with explicit `@Synchronized` on mutable state methods.
* Preserve generic signatures (`MyFilter<T>`, `InstantaneousFilter<T>`, `FilteredSensorData<T>`) for seamless Java/Kotlin interop.
* Update `ADR-001` and `JavaCoreMigrationStrategyContractTest.kt` in tandem to maintain green contract tests.

---

## 7. System Invariants & Risk Assessment
* **Core Invariants**: (1) Bitwise/epsilon numerical parity on all smoothing algorithms; (2) Rule 50 post-trim emptiness guard; (3) 100% pass rate on filter unit tests without modifying test logic.
* **Risk Rating**: **LOW** — Well-isolated mathematical leaf package with comprehensive unit test coverage.
