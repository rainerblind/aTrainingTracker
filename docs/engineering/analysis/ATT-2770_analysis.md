# Stage 1: Problem Domain & Root Cause Analysis - ATT-2770: Accelerate Test Suite Execution via Parallel Test Forks, Gradle Caching, and Worker Recycling

**Ticket**: [ATT-2770](https://atrainingtracker.atlassian.net/browse/ATT-2770)  
**Parent Epic**: [ATT-232](https://atrainingtracker.atlassian.net/browse/ATT-232) (*Process & Engineering Workflow*)  
**Requirement Mapping**: `REQ-PRO-025`  
**Sprint**: `2026-41.4`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  
**Status**: Revised (Addressing Gate 1 Audit Feedback)  

---

## 1. Problem Statement & User Impact

The automated test suite in `aTrainingTracker` has expanded into an extensive ASPICE quality firewall comprising **436 test classes** and **over 2,130 individual unit tests**.

During autonomous in-sprint development (ASPICE Stage 4 construction and Stage 5 clean-room verification), running the full regression test suite via `./gradlew testDebugUnitTest` currently takes **7 to 9 minutes** on an 8-core CPU workstation.

This execution latency creates severe developer and agent feedback loop friction:
- **Long Idle Waiting**: Each ticket in the autonomous pipeline must execute full regression verification. A 7–9 minute test run per ticket significantly slows down throughput.
- **CPU Underutilization**: While the host machine has 8 logical CPU cores and 30 GiB RAM (with >16 GiB free memory), 7 out of 8 CPU cores remain completely idle during test runs because Gradle defaults to a single worker process (`maxParallelForks = 1`).
- **Memory Bloat & GC Thrashing**: Over 150 test suites utilize `io.mockk:mockk`, which relies on dynamic ByteBuddy bytecode transformation. Running hundreds of MockK test suites inside a single, un-recycled JVM process causes classloader accumulation, heap fragmentation, and escalating Garbage Collection (GC) pauses toward the end of the test suite.

Accelerating test execution will dramatically shorten developer and agent feedback loops without compromising test determinism or isolation.

---

## 2. Forensic Archaeology & Root Cause Analysis

### 2.1 Missing `testOptions` in `app/build.gradle`
Examination of `app/build.gradle` reveals that the `testOptions` block is completely absent from the `android { ... }` configuration:
- In the Android Gradle Plugin (AGP), if `testOptions.unitTests.all` is not declared:
  - `maxParallelForks` defaults to `1` (strict sequential execution across all test classes).
  - `forkEvery` defaults to `0` (meaning "never fork a new JVM"; a single JVM executes all 436 test classes from start to finish).
- Consequently, all 436 test classes are executed one by one on a single OS thread.

### 2.2 Disabled Gradle Parallelism & Caching in `gradle.properties`
Inspection of `gradle.properties` shows:
```properties
# When configured, Gradle will run in incubating parallel mode.
# This option should only be used with decoupled projects. For more details, visit
# https://developer.android.com/r/tools/gradle-multi-project-decoupled-projects
# org.gradle.parallel=true
```
- `# org.gradle.parallel=true` is commented out.
- `org.gradle.caching=true` is completely missing.
- As a result, Gradle runs tasks sequentially and lacks local task artifact caching for unit test executions when inputs have not changed.

### 2.3 MockK Classloader Accumulation & GC Thrashing
- MockK generates dynamic mock classes at runtime via ByteBuddy. In a long-lived single JVM process, these generated classes remain in the Metaspace and their internal references accumulate in the heap.
- After ~200 test classes, heap utilization spikes, triggering frequent stop-the-world Full GC cycles.
- Periodic recycling of the test worker JVM (via Gradle's `forkEvery` setting) completely flushes the Metaspace and heap, eliminating memory leaks and GC overhead.

---

## 3. Chesterton's Fence & Invariant Analysis

Before enabling parallel test execution, we must evaluate the historical assumptions and potential side effects:

### 3.1 Test Isolation Across Separate JVM Processes
- **Mechanism**: Gradle's `maxParallelForks` does **not** run tests in multiple threads inside the same JVM; instead, it spawns separate, independent OS worker processes (`GradleWorkerMain`), each with its own JVM heap, classloader, and static state.
- **Invariant**: Because each worker process is an isolated JVM, static singletons, global mocks (`mockkStatic`), and thread-local variables in one worker cannot leak into or mutate state in another worker.
- **Within a single worker process**: Tests are assigned by class and run sequentially, preserving the exact same behavior as the current single-threaded execution.

### 3.2 In-Memory Databases & Temporary Files
- Unit tests that instantiate Room databases via `Room.inMemoryDatabaseBuilder()` or mock SQLite helpers use worker-local memory.
- Tests creating temporary files use unique temporary file paths (`File.createTempFile`) or distinct directories, preventing parallel file access collisions.

### 3.3 Defensive JVM Heap Sizing & Host Resource Protection
- **Auditor Concern**: A large heap configuration (e.g. 1.5 GB per fork) multiplied across 4 parallel forks could consume 6+ GB of RAM, creating severe out-of-memory (OOM) or swapping risks on standard 16 GB developer laptops or constrained CI runners.
- **Defensive Mitigation**:
  - We configure a tight, bounded heap allocation:
    - `minHeapSize = "256m"`
    - `maxHeapSize = "768m"`
  - Total maximum concurrent test worker heap consumption is strictly capped at:
    $$\text{Max Worker Memory} = 4 \times 768\text{ MB} = 3.072\text{ GB}$$
  - Paired with `forkEvery = 80`, each worker process executes 80 test classes and then exits cleanly. This guarantees that workers never accumulate memory, preventing heap fragmentation and leaving over 75% of host RAM available for the OS and Gradle daemon.

### 3.4 Shared In-Memory Localization DOM Cache Architecture
- **Problem**: Over 100 test classes independently read and parse all 9 `strings.xml` resource files from disk on every test class invocation, generating excessive redundant disk I/O and XML DOM tree allocations.
- **Design Specification (`LocalizationTestCache.kt`)**:
  - **Thread-Safety**: Backed by a `java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>`.
  - **Atomic Retrieval**: Uses `ConcurrentHashMap.computeIfAbsent(localeDir) { dir -> parseStringsXml(dir) }` ensuring idempotent single-pass parsing.
  - **Immutability**: Parsed key-value maps are wrapped in unmodifiable collections (`Collections.unmodifiableMap`), preventing any test from mutating cached definitions.
  - **Lifecycle Scope**: The cache resides in the worker JVM's heap. Because workers are recycled every 80 test classes (`forkEvery = 80`), the cache is naturally refreshed and bounded, guaranteeing zero cross-run memory leaks.

---

## 4. Scope Bounding & Proposed Solutions

### 4.1 Changes to `gradle.properties`
1. Enable parallel project task execution: `org.gradle.parallel=true`.
2. Enable Gradle local build cache: `org.gradle.caching=true`.

### 4.2 Changes to `app/build.gradle`
1. Introduce bounded `testOptions` block:
   ```groovy
   testOptions {
       unitTests.all {
           maxParallelForks = (Runtime.runtime.availableProcessors() / 2).coerceAtLeast(1)
           forkEvery = 80 // Periodically recycle worker JVM to prevent MockK classloader bloat
           minHeapSize = "256m"
           maxHeapSize = "768m"
           jvmArgs += [
               "-XX:+TieredCompilation",
               "-XX:TieredStopAtLevel=1", // Fast C1 JIT compilation optimal for short-lived unit test forks
               "-Dfile.encoding=UTF-8"
           ]
       }
   }
   ```

---

## 5. Acceptance Criteria (Given-When-Then)

- **AC-1 (Parallel Worker Forking)**:
  - *Given* an 8-core CPU workstation,
  - *When* `./gradlew testDebugUnitTest` is executed,
  - *Then* Gradle SHALL spawn multiple test worker processes (`maxParallelForks = 4`).

- **AC-2 (Execution Time Reduction)**:
  - *Given* the complete 436-class test suite,
  - *When* running `./gradlew testDebugUnitTest`,
  - *Then* execution duration SHALL decrease by at least 40% compared to baseline single-worker execution.

- **AC-3 (Test Suite Integrity & Determinism)**:
  - *Given* parallel test worker execution,
  - *When* running the complete test suite,
  - *Then* 100% of tests SHALL pass with zero flakiness, zero race conditions, and zero static state contamination across workers.

- **AC-4 (Gradle Cache Active)**:
  - *Given* an unchanged codebase after a successful test run,
  - *When* executing `./gradlew testDebugUnitTest`,
  - *Then* tasks SHALL resolve `UP-TO-DATE` or `FROM-CACHE`.

- **AC-5 (Defensive Host Memory Capping)**:
  - *Given* 4 concurrent test workers running simultaneously,
  - *When* measuring aggregate heap consumption,
  - *Then* total worker heap usage SHALL NOT exceed 3.5 GB, preserving system stability.

---

## 6. Living Requirement Mapping

This optimization will be formalized in Stage 2 as:
- **`REQ-PRO-025`**: *Automated Test Execution Acceleration, Worker Parallelism, and Resource-Safe Caching*.
- **`TST-PRO-018`**: *Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking*.

---

## 7. Traceability Matrix

| Artifact | Purpose | Status |
| :--- | :--- | :--- |
| `docs/engineering/analysis/ATT-2770_analysis.md` | Problem Domain & Root Cause Analysis | Completed (Revised) |
| `docs/engineering/test_specs/ATT-2770_test_spec.md` | Formal Test Specification & Verification Plan | Scheduled (Stage 2) |
| `docs/engineering/plans/ATT-2770_plan.md` | Atomic Implementation Plan | Scheduled (Stage 3) |
| `app/build.gradle` | `testOptions.unitTests.all` configuration | Scheduled (Stage 4) |
| `gradle.properties` | `org.gradle.parallel=true`, `org.gradle.caching=true` | Scheduled (Stage 4) |
| `docs/engineering/walkthroughs/ATT-2770_walkthrough.md` | Verification Walkthrough & Performance Benchmark | Scheduled (Stage 5) |
