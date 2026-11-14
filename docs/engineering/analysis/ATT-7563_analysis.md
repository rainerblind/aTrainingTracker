# Stage 1 Analysis: ATT-7563

**Ticket:** ATT-7563 (`[Verbesserung] Raise Kover coverage floor and add stricter bounds for critical packages`)  
**Parent Epic:** ATT-235 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Pro) | **Status:** In Progress (Stage 1)

---

## 1. Problem Statement & ASPICE F6 Context
- During previous sprints, the project expanded from ~2,000 to nearly 6,000 unit tests.
- CI/CD Kover verification floor in `app/build.gradle` remained fixed at legacy 35% line / 30% branch.
- ASPICE F6 audit mandates coverage ratchet with test suite maturity and dedicated subsystem bounds.
- Global-only bounds allow critical subsystems to regress undetected if other modules inflate coverage.

## 2. Codebase Baseline & Chesterton's Fence
- **Current `app/build.gradle`:** Single rule enforcing `minBound(35)` and branch `minValue = 30`.
- **Chesterton's Fence:** Originally set low during early Java-to-Kotlin UI migration. Today, test suite has ~45%+ line coverage globally. Ratcheting global to 40% line / 35% branch and establishing dedicated bounds for tracking, filtering, and sensor subsystems protects safety-critical code without flakiness.
- **Test Precondition:** 7 tests fail in `WorkoutSectionOrderPersistenceTest` and `WorkoutSectionReorderContractTest` due to hardcoded `13` instead of `14` after ATT-7756 (HRV). Restoring dynamic count resolves failures for `koverVerifyDebug`.

## Change-Request Impact Analysis
- **Affected Requirements:** Introduces new living process requirement `REQ-PRO-089` and preserves ASPICE SWE.4/SWE.6 standards.
- **Affected Architecture Elements:** `app/build.gradle` Kover plugin configuration, `WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSectionReorderContractTest.kt`, `docs/tests.md`, and `docs/coding_standards.md`.
- **Verification Impact & Test Surface:** Adds `TST-PRO-089`, executes `./gradlew koverVerifyDebug` across all 5,981+ unit tests, 0 APK runtime impact.
- **Risk Assessment & Invariants:** Subsystem bounds calibrated with 5-10% safety buffer; all unit tests must pass before Kover verification.
