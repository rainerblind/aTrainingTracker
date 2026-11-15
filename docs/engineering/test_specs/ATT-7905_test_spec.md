# Stage 2: Requirement & Test Specification - ATT-7905: Java Core Migration: Migrate pure signal filters to Kotlin

**Ticket**: [ATT-7905](https://rainerblind.atlassian.net/browse/ATT-7905)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7`  
**Requirement Mapping**: `REQ-ARC-006` (*Pure Signal Filters Kotlin Migration & ADR-001 Contract Alignment*)  
**Test Spec ID**: `TST-ARC-006`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Requirement Specification (REQ-ARC-006)

### 1.1 Problem Statement & Rationale
Under ADR-001 (`REQ-ARC-005`, `ATT-7566`), 11 signal filter classes in `com.atrainingtracker.banalservice.filters` (1,049 LOC) remain in legacy Java, producing platform types (`T!`) across Kotlin callers. Migrating these pure algorithms to Kotlin eliminates nullability hazards while preserving numerical equivalence and thread synchronization invariants. Per PO decision in `ATT-7749`, migration is bounded to leaf formatters, signal filters, and exporters; ADR-001 and `JavaCoreMigrationStrategyContractTest` must align with `<=` 149 Java classes.

### 1.2 Attributes
- **Priority**: Medium | **Type**: Non-Functional | **Verification Method**: Unit / Review

### 1.3 Functional & Architectural Requirements
1. **1:1 Kotlin Conversion (`REQ-ARC-006.1`)**: Migrate all 11 Java classes in `com.atrainingtracker.banalservice.filters/` (`MyFilter`, `AverageFilter`, `ExponentialSmoothingFilter`, `MovingAverageFilter`, `NumberedMovingAverageFilter`, `TimedMovingAverageFilter`, `MaxValueFilter`, `InstantaneousFilter`, `FilterData`, `FilteredSensorData`, `FilterManager`) to Kotlin (`.kt`), preserving generics, binary compatibility, and `@Synchronized` monitor locks.
2. **Numerical & Rule 50 Parity (`REQ-ARC-006.2`)**: Preserve bitwise/epsilon numerical parity and Rule 50 post-trim emptiness check returning `null` (not `NaN`) in `TimedMovingAverageFilter`.
3. **PO Scope Boundary & Contract Alignment (`REQ-ARC-006.3`)**: Document PO bounded scope in ADR-001; loosen `JavaCoreMigrationStrategyContractTest.kt` line 169 to `<= 149`.
4. **Kover Subsystem Floor (`REQ-ARC-006.4`)**: Satisfy $\ge 70\%$ Line and $\ge 50\%$ Branch coverage for `banalservice.filters`.

### 1.4 Acceptance Criteria (Given-When-Then)
* **AC-1 (Kotlin 1:1 Parity)**: *Given* 11 filter Java classes, *When* converted to `.kt`, *Then* 0 `.java` files remain in `banalservice/filters` and public APIs remain source/binary compatible.
* **AC-2 (Thread Safety)**: *Given* concurrent telemetry updates, *When* filters compute or reset, *Then* `@Synchronized` locks prevent data corruption.
* **AC-3 (Rule 50)**: *Given* all expired samples in `TimedMovingAverageFilter`, *When* `getFilteredValue()` is queried, *Then* `null` is returned (never `NaN`).
* **AC-4 (ADR Contract)**: *Given* `<= 149` Java classes in codebase, *When* `JavaCoreMigrationStrategyContractTest` executes, *Then* 100% assertions pass.
* **AC-5 (Kover Floor)**: *Given* filter test suite, *When* `./gradlew koverVerifyDebug` runs, *Then* coverage $\ge 70\%$ Line / $\ge 50\%$ Branch.

---

## 2. Test Specification (TST-ARC-006)

### TST-ARC-006.1: Filter Behavioral & Numerical Equivalence Test Suite
* **Scope**: Unit Tests | **Target**: `app/src/test/java/com/atrainingtracker/banalservice/filters/`
* **Preconditions**: Filter classes migrated to `.kt`.
* **Action**: Run `FilterInitializationAndBoundsTest`, `FilterManagerAccumulatorResetTest`, `FilterManagerRebindTest`, `FilterNullSafetyTest`, `FilterResetAndPreStartTest`, `SensorFilterDefaultsTest`.
* **Expected Result**: 100% pass rate, identical numerical outputs.

### TST-ARC-006.2: Concurrency & Thread Synchronization Audit
* **Scope**: Static & Bytecode Audit | **Target**: Migrated `.kt` filter sources
* **Action**: Verify `@Synchronized` on mutating methods (`newValue`, `reset`, `getFilteredValue`, `createFilter`).
* **Expected Result**: Monitor safety preserved across background threads.

### TST-ARC-006.3: Rule 50 Post-Trim NaN Elimination Test
* **Scope**: Unit Test | **Target**: `TimedMovingAverageFilterTest`
* **Action**: Add samples, let timestamp expire, trim, verify `getFilteredValue() == null`.
* **Expected Result**: Returns `null` without `Double.NaN`.

### TST-ARC-006.4: Java Core Migration Strategy Contract Test
* **Scope**: Architecture Contract Test | **Target**: `JavaCoreMigrationStrategyContractTest.kt`
* **Action**: Run `./gradlew testDebugUnitTest --tests com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest`.
* **Expected Result**: Passes with `<= 149` count check and updated ADR-001 text.

### TST-ARC-006.5: Filter Subsystem Kover Verification
* **Scope**: Verification Gate | **Action**: Run `./gradlew koverVerifyDebug`.
* **Expected Result**: Line Coverage $\ge 70\%$, Branch Coverage $\ge 50\%$.

---

## 3. Traceability Matrix

| Test Case | Scope | Method Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-006.1` | Unit | `Filter*.kt` test suites | `REQ-ARC-006.1`, `REQ-ARC-006.2` | Specified |
| `TST-ARC-006.2` | Static / Review | Mutating methods in filter classes | `REQ-ARC-006.1` | Specified |
| `TST-ARC-006.3` | Unit | `TimedMovingAverageFilter.getFilteredValue()` | `REQ-ARC-006.2` | Specified |
| `TST-ARC-006.4` | Architecture | `JavaCoreMigrationStrategyContractTest` | `REQ-ARC-006.3` | Specified |
| `TST-ARC-006.5` | Coverage | Kover verification on `banalservice.filters` | `REQ-ARC-006.4` | Specified |
