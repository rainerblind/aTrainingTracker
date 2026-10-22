# Stage 3: Implementation Plan - ATT-4667: Add @JvmOverloads to WorkoutClustersViewModel constructor to fix NoSuchMethodException crash

**Ticket**: [ATT-4667](https://rainerblind.atlassian.net/browse/ATT-4667)  
**Sub-task**: [ATT-4684](https://rainerblind.atlassian.net/browse/ATT-4684) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `V4.9.40` | **Active Sprint**: `2026-43.7` | **Branch**: `feature/ATT-4667`  
**Requirement Mapping**: `REQ-ARC-004` (`REQ-ARC-004.1`–`4`)  
**Test Mapping**: `TST-ARC-004` (`TST-ARC-004.1`–`4`)  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
Navigating to the Favorite Tracks (Clusters) or Known Locations screen crashes at runtime with `NoSuchMethodException: WorkoutClustersViewModel.<init>(Application)` because `WorkoutClustersViewModel(application, ioDispatcher = Dispatchers.IO)` lacks `@JvmOverloads constructor`. AndroidX's `AndroidViewModelFactory` reflectively looks up a 1-arg `(Application.class)` constructor, which Kotlin omits in JVM bytecode without `@JvmOverloads`. Annotating the constructor and establishing an automated reflection contract test prevents regressions across all reflective ViewModels.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-004` (*Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene*)
* **Test Mapping**: `TST-ARC-004` (*Android Framework Reflection Constructor Compatibility & Lifecycle ViewModel Factory Hygiene Verification Suite*)

---

## 3. System Invariants & Preserved Behavior
1. **Zero Runtime Regressions**: Existing clustering algorithms, sport discovery, route linking, and StateFlow contracts remain unaltered.
2. **Dispatcher Injection Parity**: `ioDispatcher: CoroutineDispatcher = Dispatchers.IO` default argument remains intact for non-blocking mapping (`REQ-DAT-033.3`) and coroutine test injection.
3. **Framework Reflection Compatibility**: Generates bytecode matching `ViewModelProvider.AndroidViewModelFactory` expectations across all Android API versions.
4. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes
* **Presentation Layer (`WorkoutClustersViewModel.kt`)**:
  * Annotate primary constructor with `@JvmOverloads constructor(application: Application, private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO)`.
* **Architectural Contract Suite (`AndroidViewModelReflectionContractTest.kt`)**:
  * Create test suite in `com.atrainingtracker.trainingtracker.build` asserting that all 14 `AndroidViewModel` subclasses instantiated via default framework factory expose a public 1-arg `(Application.class)` constructor, and verifying `WorkoutClustersViewModel` reflectively instantiates.
* **Coding Standards (`docs/coding_standards.md` §3.5)**:
  * Maintain §3.5 standard requiring `@JvmOverloads constructor` for reflective Android framework classes declaring default parameters.
* **UI Consistency**: No UI layout or styling changes (lifecycle constructor and architectural reflection guard).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Code Lock Verification (Rule 4)
* Verify exit code 0 on `python3 tools/jira_util.py check-gate <Impl-Plan-Subtask-Key>`.

### Step 2: Annotate WorkoutClustersViewModel Constructor
* **File**: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/clusters/WorkoutClustersViewModel.kt` (L86–89)
* **Change**: Add `@JvmOverloads constructor` to the class declaration.

### Step 3: Author AndroidViewModel Reflection Contract Test Suite
* **File**: `app/src/test/java/com/atrainingtracker/trainingtracker/build/AndroidViewModelReflectionContractTest.kt`
* **Change**: Implement `TST-ARC-004.1` (direct `WorkoutClustersViewModel` 1-arg constructor & instantiation test) and `TST-ARC-004.2` (reflection contract across all 14 default-instantiated `AndroidViewModel` subclasses).

### Step 4: Verify Coding Standards Parity
* **File**: `docs/coding_standards.md` §3.5
* **Change**: Confirm §3.5 accurately documents `@JvmOverloads constructor` invariant for reflective Android framework classes (`AndroidViewModel`, `ViewModel`, `Fragment`).

### Step 5: Incremental Test Execution & Validation
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.clusters.*" --tests "com.atrainingtracker.trainingtracker.build.*"
  python3 tools/verify_requirement_governance.py
  ```

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests (`AndroidViewModelReflectionContractTest`, `WorkoutClustersViewModelTest`), `verify_requirement_governance.py`, followed by clean-room full suite in Stage 5.
* **Rollback**: Branch isolation allows full revert via `git checkout -- . && git clean -fd`.
