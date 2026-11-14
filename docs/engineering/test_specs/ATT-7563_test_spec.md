# Stage 2 Test Specification: ATT-7563

**Ticket:** ATT-7563 (`[Verbesserung] Raise Kover coverage floor and add stricter bounds for critical packages`)  
**Parent Epic:** ATT-235 | **Sprint:** 2026-46.6 | **Target Release:** V5.0.0 | **Story Points:** 2 SP  
**Role:** `agent1` (Subagent Flash) / Review: `agent2` (Subagent Pro) | **Status:** In Progress (Stage 2)

---

## 1. Traceability Matrix

| Requirement ID | Test Specification ID | Verification Method | Target Component | Status |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-PRO-089` | `TST-PRO-089` | Unit / Review | `app/build.gradle`, Kover verification | Proposed |

---

## 2. Requirement Specification (`REQ-PRO-089`)

### REQ-PRO-089: Kover Coverage Floor Ratcheting and Critical Subsystem Bounds [Proposed]
- **Tests**: `TST-PRO-089`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Implementation**: `app/build.gradle`, `docs/tests.md`, `docs/coding_standards.md`
- **Rationale**: Under ASPICE F6 verification criteria, the project's verification floor must ratchet upwards as the test suite matures and establish package-level guardrails for safety-critical components.
- **Description**:
  1. *Global Verification Floor*: `app/build.gradle` SHALL enforce a minimum of 40% line coverage (`minBound(40)`) and 35% branch coverage (`CoverageUnit.BRANCH`, `minValue = 35`).
  2. *Subsystem Verification Bounds*: `app/build.gradle` SHALL define bounded verification rules for:
     - `Tracking`: packages `com.atrainingtracker.trainingtracker.tracker` (line $\ge 55\%$, branch $\ge 45\%$).
     - `Filters`: packages `com.atrainingtracker.banalservice.filters` (line $\ge 70\%$, branch $\ge 50\%$).
     - `Sensors & Devices`: packages matching `com.atrainingtracker.banalservice.devices*` and `com.atrainingtracker.banalservice.sensor*` (line $\ge 60\%$, branch $\ge 50\%$).
  3. *Test Suite Execution Precondition*: All unit tests SHALL pass cleanly before coverage verification is evaluated.
  4. *Documentation Synchronization*: `docs/tests.md` and `docs/coding_standards.md` SHALL document the new bounds.

**Acceptance Criteria (Given-When-Then)**:
- *Given* the complete codebase and test suite,
- *When* `./gradlew koverVerifyDebug` is executed,
- *Then* all global and subsystem rules pass without violation.
- *Given* any critical package falling below its designated line or branch bound,
- *When* `./gradlew koverVerifyDebug` executes,
- *Then* the build fails with a specific bounded rule violation.

**Invariants**: Zero changes to production runtime logic; zero flaky or bypassed tests.

---

## 3. Test Specification (`TST-PRO-089`)

### TST-PRO-089: Kover Verification Gate Execution and Multi-Package Rule Audit [Proposed]
- **Verifies**: `REQ-PRO-089`
- **Priority**: High
- **Type**: Non-Functional
- **Verification Method**: Unit / Review
- **Component**: `app/build.gradle`, `WorkoutSectionOrderPersistenceTest.kt`, `WorkoutSectionReorderContractTest.kt`, `docs/tests.md`
- **Jira**: `ATT-7563`
- **Test Procedures**:
  1. *Test Suite Hygiene Verification*: Execute `./gradlew testDebugUnitTest` and assert all unit tests pass (100% pass rate).
  2. *Kover Global Rule Verification*: Execute `./gradlew koverVerifyDebug` and assert line coverage $\ge 40\%$ and branch coverage $\ge 35\%$.
  3. *Kover Subsystem Rule Verification*: Verify bounded rules for `tracker`, `filter`, and `devices`/`sensor` packages pass in `koverVerifyDebug`.
  4. *Negative Mutation Verification*: Artificially elevate bounds (e.g., line 99%) and assert build failure, verifying gate enforcement.
  5. *Documentation Parity*: Confirm `docs/tests.md` and `docs/coding_standards.md` reflect ratcheted figures.
