# Stage 2: Requirement & Test Specification - ATT-4667: Add @JvmOverloads to WorkoutClustersViewModel constructor to fix NoSuchMethodException crash

**Ticket**: [ATT-4667](https://rainerblind.atlassian.net/browse/ATT-4667)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4667`  
**Requirement Mapping**: `REQ-ARC-004` (*Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene*)  
**Test Spec ID**: `TST-ARC-004`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-ARC-004`)

### 1.1 Problem Statement & Rationale
Navigating to the Favorite Tracks (Clusters) or Known Locations screen triggers a fatal runtime `NoSuchMethodException` because `WorkoutClustersViewModel(application: Application, ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` lacks `@JvmOverloads constructor`. AndroidX's `AndroidViewModelFactory` reflectively looks for `WorkoutClustersViewModel(Application)`, which Kotlin does not emit in Java bytecode without `@JvmOverloads`. Annotating the constructor and codifying reflection contract tests guarantees runtime stability.

### 1.2 Functional & Architectural Requirements
1. **Bytecode Constructor Generation (`REQ-ARC-004.1`)**: `WorkoutClustersViewModel.kt` primary constructor SHALL be annotated with `@JvmOverloads constructor(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)` to emit a 1-argument `(Application)` constructor in Java bytecode.
2. **Reflection Contract Enforcement (`REQ-ARC-004.2`)**: An automated contract test suite (`AndroidViewModelReflectionContractTest.kt`) SHALL verify that all 14 `AndroidViewModel` subclasses instantiated via default framework factory (`AndroidViewModelFactory` / `viewModel(activity)`) expose an accessible `(Application.class)` constructor.
3. **Coding Standards Parity (`REQ-ARC-004.3`)**: `docs/coding_standards.md` §3.5 SHALL document the mandatory `@JvmOverloads constructor` rule for reflective framework classes declaring default parameters.
4. **Invariant Preservation (`REQ-ARC-004.4`)**: Configurable coroutine dispatcher injection for background equipment mapping (`REQ-DAT-033.3`), existing cluster caching, route linking, and StateFlow contracts MUST remain unaltered.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-ARC-004` under Epic `ATT-431` (*Clean Code*), extending `REQ-ARC-003`. Targets `WorkoutClustersViewModel.kt`, `AndroidViewModelReflectionContractTest.kt`, and `docs/coding_standards.md` §3.5.
* **Historical Origin & Commit Trace**: Introduced in `ATT-4003` (commit `6073cf43`, Sprint `2026-43.6`), adding `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` to offload sport equipment mapping (`REQ-DAT-033.3`).
* **Root Reason for Existing Formulation**: `ioDispatcher` was introduced with a default argument without `@JvmOverloads`, causing Kotlin compiler to omit the single-arg JVM constructor required by framework reflection.
* **Preservation of Core Invariants**: Retains `Dispatchers.IO` default injection, cluster map caching, route linking, and 100% test suite pass rate.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Reflection Instantiation)**: *Given* `WorkoutClustersViewModel`, *When* `clazz.getConstructor(Application.class)` is invoked, *Then* the 1-argument constructor resolves and instantiates without `NoSuchMethodException`.
* **Criterion 2 (Contract Test Coverage)**: *Given* `AndroidViewModelReflectionContractTest`, *When* executed, *Then* all default-instantiated `AndroidViewModel` subclasses satisfy the `(Application.class)` reflection contract.
* **Criterion 3 (Clean-Room Test Suite)**: *Given* the full test suite, *When* `./gradlew testDebugUnitTest` runs, *Then* 100% tests pass with zero regressions.

---

## 2. Test Specification (`TST-ARC-004`)

### Test Cases
* `TST-ARC-004.1` (**WorkoutClustersViewModel Reflection Constructor**): Verify via Java reflection that `WorkoutClustersViewModel` declares an accessible `(Application)` constructor in `AndroidViewModelReflectionContractTest.kt`.
* `TST-ARC-004.2` (**All Default AndroidViewModels Reflection Contract**): Verify that all 14 default-instantiated `AndroidViewModel` subclasses expose an accessible `(Application)` constructor in `AndroidViewModelReflectionContractTest.kt`.
* `TST-ARC-004.3` (**Coding Standards Verification**): Verify `docs/coding_standards.md` §3.5 codifies mandatory `@JvmOverloads constructor` for reflective classes.
* `TST-ARC-004.4` (**Clean-Room Full Regression Suite**): Verify `./gradlew testDebugUnitTest` achieves a 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-004.1` | Unit / Contract | `WorkoutClustersViewModel.kt`, `AndroidViewModelReflectionContractTest.kt` | `REQ-ARC-004.1` | Proposed |
| `TST-ARC-004.2` | Unit / Contract | `AndroidViewModelReflectionContractTest.kt` | `REQ-ARC-004.2` | Proposed |
| `TST-ARC-004.3` | Governance | `docs/coding_standards.md` §3.5 | `REQ-ARC-004.3` | Proposed |
| `REQ-PRO-001` / `TST-ARC-004.4` | Regression | Full test suite (`./gradlew testDebugUnitTest`) | `REQ-ARC-004.4` | Proposed |
