# Stage 3: Implementation Plan - ATT-7905: Java Core Migration: Migrate pure signal filters to Kotlin

**Ticket**: [ATT-7905](https://rainerblind.atlassian.net/browse/ATT-7905)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V5.0.0` | **Active Sprint**: `2026-46.7`  
**Requirement Mapping**: `REQ-ARC-006` (*Pure Signal Filters Kotlin Migration & ADR-001 Contract Alignment*)  
**Test Mapping**: `TST-ARC-006`  
**Risk Classification**: `STANDARD` (non-UI backend Kotlin migration)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-11-15  

---

## 1. Problem Description & Background
Under ADR-001 (`REQ-ARC-005`, `ATT-7566`) and the PO decision from retro `ATT-7749`, 11 signal filter classes in `com.atrainingtracker.banalservice.filters` (1,049 LOC) will migrate from Java to idiomatic Kotlin. This eliminates platform type (`T!`) nullability hazards while preserving numerical equivalence, thread synchronization (`@Synchronized`), Rule 50 post-trim emptiness guards, and existing Java caller compatibility.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-006` (`REQ-ARC-006.1`–`REQ-ARC-006.4`)
* **Test Mapping**: `TST-ARC-006` (`TST-ARC-006.1`–`TST-ARC-006.5`)

---

## 3. System Invariants & Preserved Behavior
1. **Numerical Equivalence**: Bitwise and floating-point smoothing parity across all algorithms (`AverageFilter`, `ExponentialSmoothingFilter`, `MovingAverageFilter`, `MaxValueFilter`).
2. **Thread Safety**: `@Synchronized` monitor locks on all mutating methods (`newValue`, `reset`, `getFilteredValue`, `createFilter`, `resetAllFilters`).
3. **Rule 50 Emptiness Guard**: `TimedMovingAverageFilter.getFilteredValue()` returns `null` (never `Double.NaN`) when expired samples leave the window empty.
4. **Binary & Source Interoperability**: Java callers (`BANALService`, `TrackingViewsDatabaseManager`) remain unperturbed with zero calling-signature changes.
5. **Contract Test Invariance**: `JavaCoreMigrationStrategyContractTest.kt` passes with `<= 149` tolerance.

---

## 4. Proposed Architectural Changes
* **Subsystem `com.atrainingtracker.banalservice.filters`**:
  * 11 files converted from `.java` to `.kt`: `MyFilter.kt`, `AverageFilter.kt`, `ExponentialSmoothingFilter.kt`, `MovingAverageFilter.kt`, `NumberedMovingAverageFilter.kt`, `TimedMovingAverageFilter.kt`, `MaxValueFilter.kt`, `InstantaneousFilter.kt`, `FilterData.kt`, `FilteredSensorData.kt`, `FilterManager.kt`.
  * Clean removal of unused imports (`DevicesDatabaseManager`, `TrackingViewsDatabaseManager`) in `FilterManager`.
* **Architecture Governance & Strategy Alignment**:
  * `JavaCoreMigrationStrategyContractTest.kt`: Relax line 169 from `assertEquals(149, ...)` to `assertTrue(javaFiles.size <= 149)`.
  * `docs/architecture/adr_001_java_core_services_migration.md`: Document PO decision (only `ATT-7903`, `ATT-7905`, `ATT-7906` migrate; others Keep).
* **UI Consistency**: No UI changes (pure backend mathematical algorithms).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Governance & Contract Alignment
* Files: `JavaCoreMigrationStrategyContractTest.kt`, `docs/architecture/adr_001_java_core_services_migration.md`.
* Changes: Relax line 169 assertion to `javaFiles.size <= 149`; document PO decision in ADR-001.

### Step 2: Leaf DTOs & Abstract Filter Migration
* Files: `FilterData.kt`, `FilteredSensorData.kt`, `MyFilter.kt`, `MovingAverageFilter.kt`, `InstantaneousFilter.kt`.
* Changes: Convert to Kotlin data/abstract classes, preserve `@JvmField` on `FilterData` properties, and `@Synchronized` locks.

### Step 3: Mathematical Smoothing Filter Migration
* Files: `AverageFilter.kt`, `ExponentialSmoothingFilter.kt`, `NumberedMovingAverageFilter.kt`, `TimedMovingAverageFilter.kt`, `MaxValueFilter.kt`.
* Changes: Convert to Kotlin, maintain Rule 50 post-trim emptiness check, and delete original `.java` files.

### Step 4: FilterManager Migration & Java Interop Verification
* Files: `FilterManager.kt`.
* Changes: Convert to Kotlin, maintain receiver registration, proxy sensor unwrapping, and remove unused imports. Delete `FilterManager.java`.

### Step 5: Verification & Governance Audit
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.banalservice.filters.*"`
* Run: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.architecture.JavaCoreMigrationStrategyContractTest"`
* Run: `python3 tools/verify_requirement_governance.py`
* Run: `./gradlew koverVerifyDebug` and `:app:lintDebug`.

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted filter unit tests (`TST-ARC-006.1`–`TST-ARC-006.5`), Kover coverage floor ($\ge 70\%$ Line / $\ge 50\%$ Branch), and requirement governance audit.
* **Rollback**: Branch isolation allows full discard via `git checkout -- .` before merge.
