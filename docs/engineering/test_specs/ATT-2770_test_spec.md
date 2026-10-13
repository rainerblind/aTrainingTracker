# Stage 2: Requirement & Test Specification - ATT-2770: Accelerate Test Suite Execution via Parallel Test Forks, Gradle Caching, and Worker Recycling

**Ticket**: [ATT-2770](https://atrainingtracker.atlassian.net/browse/ATT-2770)  
**Sub-task**: [ATT-2787](https://atrainingtracker.atlassian.net/browse/ATT-2787) (`[Req & Test Spec]`)  
**Parent Epic**: [ATT-232](https://atrainingtracker.atlassian.net/browse/ATT-232) (*Process & Engineering Workflow*)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-PRO-025` (*Automated Test Execution Acceleration, Worker Parallelism, and Resource-Safe Caching*)  
**Test Spec ID**: `TST-PRO-018`  
**Branch**: `improvement/ATT-2770`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Requirement Specification (REQ-PRO-025)

### 1.1 Problem Statement & Rationale
The automated test suite in `aTrainingTracker` has expanded into an extensive ASPICE quality firewall comprising **436 test classes** and **over 2,130 individual unit tests**.

During autonomous in-sprint development (ASPICE Stage 4 construction and Stage 5 clean-room verification), running the full regression test suite via `./gradlew testDebugUnitTest` currently takes **7 to 9 minutes** on an 8-core CPU workstation.

This execution latency creates severe developer and agent feedback loop friction:
- **CPU Underutilization**: `maxParallelForks` is unconfigured and defaults to `1`, leaving 7 of 8 CPU cores idle while 436 test classes execute sequentially.
- **Disabled Gradle Parallelism & Caching**: In `gradle.properties`, `# org.gradle.parallel=true` is commented out, and `org.gradle.caching=true` is absent, preventing decoupled task parallelism and local task output reuse.
- **MockK Classloader & GC Bloat**: Over 150 test suites use `io.mockk:mockk` and dynamic ByteBuddy bytecode transformation. Running hundreds of MockK test classes inside a single, un-recycled JVM process causes classloader accumulation, heap fragmentation, and escalating Garbage Collection (GC) pauses toward the end of the run.
- **Redundant Disk I/O across Localization Tests**: Over 100 test classes independently read, parse, and allocate XML DOM trees for all 9 `strings.xml` resource files on every test class invocation.

Net-new requirement `REQ-PRO-025` formalizes the engineering performance standards, multi-core worker fork allocation, bounded heap limits, worker recycling intervals, and in-memory localization DOM caching to accelerate test execution while preserving test isolation and host stability.

### 1.2 Functional & Architectural Requirements
1. **Multi-Worker Parallelism (`app/build.gradle`)**:
   - The build system SHALL configure `testOptions.unitTests.all` with dynamic hardware-adaptive worker forking:
     $$\text{maxParallelForks} = \max\left(1, \left\lfloor \frac{\text{availableProcessors}}{2} \right\rfloor\right)$$
   - On an 8-core system, exactly 4 parallel test worker processes (`GradleWorkerMain`) SHALL execute test suites concurrently.
2. **Periodic Worker Recycling (`forkEvery`)**:
   - The build system SHALL configure `forkEvery = 80`. Each test worker process SHALL be terminated and replaced with a fresh JVM after executing 80 test classes.
   - This periodic recycling SHALL completely flush the JVM Metaspace and heap, permanently eliminating MockK/ByteBuddy classloader retention and long stop-the-world Full GC pauses.
3. **Defensive Heap Allocation Capping (`minHeapSize` / `maxHeapSize`)**:
   - To protect host stability and prevent out-of-memory (OOM) or swap thrashing on 8 GB / 16 GB developer laptops or CI runners, each worker fork SHALL be constrained to:
     - `minHeapSize = "256m"`
     - `maxHeapSize = "768m"`
   - Total concurrent heap allocation across all 4 worker forks SHALL be strictly capped at $\le 3.072\text{ GB}$, leaving over 75% of physical memory available for the host OS and Gradle daemon.
4. **Optimized JIT Compiler Flags (`jvmArgs`)**:
   - Test worker JVMs SHALL configure `-XX:+TieredCompilation`, `-XX:TieredStopAtLevel=1` (prioritizing fast C1 JIT compilation over heavyweight C2 optimizations, optimal for short-lived unit test forks), and `-Dfile.encoding=UTF-8`.
5. **Decoupled Task Execution & Build Caching (`gradle.properties`)**:
   - `gradle.properties` SHALL declare:
     - `org.gradle.parallel=true`: enabling parallel execution of independent project tasks.
     - `org.gradle.caching=true`: enabling Gradle's local build cache for deterministically cacheable task outputs.
6. **Thread-Safe In-Memory Localization DOM Cache (`LocalizationTestCache.kt`)**:
   - The test infrastructure SHALL provide a shared, thread-safe in-memory cache helper `LocalizationTestCache`:
     - Backed by `java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>`.
     - Loading each locale's `strings.xml` atomically via `computeIfAbsent`.
     - Returning unmodifiable/immutable string maps (`Collections.unmodifiableMap`) to guarantee zero cross-test mutation or pollution.
     - Naturally scoped to the worker JVM lifecycle and refreshed upon worker recycling (`forkEvery = 80`).

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement (`REQ-PRO-025`), establishing formal build and test infrastructure standards under Process Epic `ATT-232` (*Process & Engineering Workflow*).
* **Historical Origin & Commit Trace**:
  - `gradle.properties` historically had `# org.gradle.parallel=true` commented out due to early Gradle 2.x/3.x incubator instability. In Gradle 8.x/9.x and AGP 8.x/9.x, parallel task execution and the build cache are mature, production-grade features.
  - `testOptions` was never configured in `app/build.gradle`, defaulting to legacy single-worker execution.
* **Root Reason for Existing Formulation**: Single-worker sequential execution was the zero-configuration default. As the test suite expanded from ~50 classes to 436 classes, the cumulative execution time scaled linearly to 7–9 minutes.
* **Preservation of Core Invariants**:
  - Process-Level Test Isolation: Gradle's `maxParallelForks` creates separate operating system JVM processes (`GradleWorkerMain`), guaranteeing that static fields, global mocks (`mockkStatic`), and thread-locals remain 100% isolated between workers.
  - In-Memory Database Isolation: In-memory Room/SQLite instances are worker-local and tests within a worker execute sequentially.
  - Zero Flakiness: All 436 test classes MUST pass cleanly with 100% determinism.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Multi-Worker Parallelism)**:
  * *Given* an 8-core CPU workstation,
  * *When* `./gradlew testDebugUnitTest` is executed,
  * *Then* Gradle SHALL spawn 4 parallel test worker processes (`maxParallelForks = 4`).
* **Criterion 2 (Execution Duration Reduction)**:
  * *Given* the complete 436-class test suite,
  * *When* `./gradlew testDebugUnitTest` is executed,
  * *Then* total execution duration SHALL decrease by at least 40% compared to baseline single-worker sequential execution (targeting $\le 4\text{ minutes}$).
* **Criterion 3 (Deterministic Suite Integrity)**:
  * *Given* parallel multi-worker test execution,
  * *When* running the complete test suite,
  * *Then* 100% of test classes and test cases SHALL pass with zero race conditions, flakiness, or test cross-contamination.
* **Criterion 4 (Defensive Memory Capping)**:
  * *Given* 4 concurrent test workers running simultaneously,
  * *When* measuring worker JVM memory allocations,
  * *Then* each worker SHALL NOT exceed 768 MB heap, capping aggregate worker memory at $\le 3.1\text{ GB}$.
* **Criterion 5 (Thread-Safe Localization Cache)**:
  * *Given* concurrent access to `LocalizationTestCache`,
  * *When* multiple test classes request strings for the same locale,
  * *Then* `strings.xml` SHALL be parsed from disk at most once per worker JVM, and returned maps SHALL be strictly immutable.

---

## 2. Test Specification (TST-PRO-018)

### 2.1 Test Cases & Verification Procedures

#### TST-PRO-018.1: Parallel Worker Forking & Multi-Core Utilization Verification
- **Test Type**: Infrastructure / Build Verification.
- **Procedure**:
  1. Trigger `./gradlew testDebugUnitTest`.
  2. Inspect OS processes via `ps aux | grep GradleWorkerMain`.
  3. Verify that on an 8-core machine, Gradle spawns up to 4 concurrent `GradleWorkerMain` processes.
  4. Verify aggregate CPU utilization across all workers exceeds 300% (utilizing multiple CPU cores).
- **Expected Result**: 4 concurrent test worker processes execute test suites in parallel.

#### TST-PRO-018.2: Defensive Memory Allocation & Bounded Heap Capping Verification
- **Test Type**: Resource Safety / Memory Benchmark.
- **Procedure**:
  1. Inspect the command line arguments of spawned `GradleWorkerMain` processes via `ps -ef` or `/proc/<pid>/cmdline`.
  2. Verify presence of `-Xms256m` and `-Xmx768m`.
  3. Monitor resident set size (RSS) and heap usage throughout test execution.
- **Expected Result**: No worker exceeds 768 MB heap; aggregate memory remains well within safe host bounds without triggering swap or out-of-memory errors.

#### TST-PRO-018.3: Shared Localization DOM Cache Thread-Safety & Immutability Verification
- **Test Class**: `com.atrainingtracker.testing.LocalizationTestCacheTest`
- **Unit Test Cases**:
  - `testCache_returnsCompleteAndValidStringsMap`: Verifies all 9 locales parse correctly, containing required keys.
  - `testCache_returnsImmutableMap`: Verifies attempts to mutate the returned map (`put`, `remove`, `clear`) throw `UnsupportedOperationException`.
  - `testCache_concurrentAccessIsThreadSafe`: Concurrently accesses `LocalizationTestCache.getStrings(locale)` from 16 worker threads; verifies idempotent return values without data races or exceptions.
  - `testCache_idempotentSubsequentCallsDoNotReReadDisk`: Verifies subsequent invocations return the cached instance immediately.
- **Expected Result**: 100% pass rate with zero concurrency faults.

#### TST-PRO-018.4: Worker Recycling & MockK ByteBuddy Heap Stability Verification
- **Test Type**: Process Lifecycle & Leak Immunity.
- **Procedure**:
  1. Monitor process IDs of `GradleWorkerMain` during a full run of all 436 test classes.
  2. Verify that worker processes exit and new workers are spawned every 80 test classes (`forkEvery = 80`).
  3. Verify that test execution does not suffer from escalating GC pause degradation in later test batches.
- **Expected Result**: Clean worker recycling without process leaks or zombie processes.

#### TST-PRO-018.5: Full Clean-Room Regression & Performance Benchmark Execution
- **Test Type**: Clean-Room Performance & Correctness Benchmark.
- **Procedure**:
  1. Execute `./gradlew clean testDebugUnitTest`.
  2. Measure wall-clock execution duration.
  3. Verify 100% pass rate across all 436 test classes and >2,130 individual test cases.
  4. Compare execution duration against baseline (7–9 minutes).
- **Expected Result**: 100% test pass rate with total duration reduced by $\ge 40\%$.

---

## 3. Traceability & Invariants Matrix

| Requirement | Test Specification | Verification Procedure | Target Metric / Invariant |
| :--- | :--- | :--- | :--- |
| `REQ-PRO-025` | `TST-PRO-018.1` | Process Inspection (`ps aux \| grep GradleWorkerMain`) | Exactly 4 parallel worker JVMs on 8 cores |
| `REQ-PRO-025` | `TST-PRO-018.2` | JVM Parameter Audit (`ps -ef`) | `-Xms256m`, `-Xmx768m`, aggregate heap $\le 3.1\text{ GB}$ |
| `REQ-PRO-025` | `TST-PRO-018.3` | `LocalizationTestCacheTest.kt` | Thread-safe, immutable, 100% pass rate |
| `REQ-PRO-025` | `TST-PRO-018.4` | Process Recycling Trace | Fresh JVM spawned every 80 test classes |
| `REQ-PRO-025` | `TST-PRO-018.5` | `./gradlew testDebugUnitTest` Full Benchmark | 100% pass rate across all 436 classes, $\ge 40\%$ speedup |
