# Stage 3 Implementation Plan: ATT-7563

**Ticket:** ATT-7563 (`[Verbesserung] Raise Kover coverage floor and add stricter bounds for critical packages`)  
**Parent Epic:** ATT-235 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Pro) | **Status:** In Progress (Stage 3)

---

## 1. SWE.2 Architecture & Verification Surface
- **Kover Verification Architecture (`app/build.gradle`)**:
  - The Kover DSL in `kover.reports.verify` supports multiple named `rule(String) { ... }` blocks with independent `filters { includes { packages(...) } }`.
  - **Global Rule**: `rule("Global Coverage Floor")` enforcing `minBound(40)` (line) and branch `bound { coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH; minValue = 35 }`.
  - **Critical Tracking Subsystem**: `rule("Critical Tracking")` targeting packages `com.atrainingtracker.trainingtracker.tracker` with line $\ge 55\%$ and branch $\ge 45\%$.
  - **Filter Subsystem**: `rule("Critical Filters")` targeting packages `com.atrainingtracker.banalservice.filters` with line $\ge 70\%$ and branch $\ge 50\%$ (baseline 54.15%).
  - **Sensors & Protocols Subsystem**: `rule("Critical Sensors & Protocols")` targeting packages `com.atrainingtracker.banalservice.devices*` and `com.atrainingtracker.banalservice.sensor*` with line $\ge 60\%$ and branch $\ge 50\%$.
- **Test Hygiene Prerequisite**:
  - `koverVerifyDebug` executes all unit tests prior to coverage calculation.
  - Updating `WorkoutSectionOrderPersistenceTest.kt` and `WorkoutSectionReorderContractTest.kt` from hardcoded 13 to dynamic `WorkoutSectionType.DEFAULT_ORDER.size` (14) restores 100% test suite health.

---

## 2. Atomic Implementation Steps

| Step | Action & Target File | Description & Scope | Verification Command |
| :--- | :--- | :--- | :--- |
| **Step 1** | Test Suite Hygiene (`WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSectionReorderContractTest.kt`) | Replace hardcoded `13` assertions with `WorkoutSectionType.DEFAULT_ORDER.size` across 5 test methods affected by ATT-7756 (HRV addition). | `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.settings.WorkoutSectionOrderPersistenceTest" --tests "com.atrainingtracker.trainingtracker.ui.settings.tuning.WorkoutSectionReorderContractTest"` |
| **Step 2** | Ratchet Kover Rules (`app/build.gradle`) | Update `kover.reports.verify`: raise global rule to 40% line / 35% branch; add package-level rules for `tracker`, `filter`, and `devices`/`sensor`. | `./gradlew koverVerifyDebug` |
| **Step 3** | Documentation Update (`docs/tests.md`, `docs/coding_standards.md`) | Document updated global coverage floors and subsystem bounded requirements. | Check file diffs & governance verification |
| **Step 4** | Full Gate Verification | Execute full test suite and verify clean-room Kover verification gate. | `./gradlew koverVerifyDebug testDebugUnitTest --max-workers=2` |

---

## 3. Non-Negotiable Invariants & Rollback Safety
- **Zero Production APK Logic Modifications**: No changes to user-space classes or runtime behaviors.
- **100% Test Suite Pass Rate**: All 5,981+ unit tests pass without failure, timeout, or flakiness.
- **Fail-Fast Boundary Enforcement**: Any drop below designated subsystem floors immediately halts CI.
