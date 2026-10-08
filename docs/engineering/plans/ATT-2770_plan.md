# Stage 3: Implementation Plan - ATT-2770: Accelerate Test Suite Execution via Parallel Test Forks, Gradle Caching, and Worker Recycling

**Ticket**: [ATT-2770](https://atrainingtracker.atlassian.net/browse/ATT-2770)  
**Sub-task**: [ATT-2788](https://atrainingtracker.atlassian.net/browse/ATT-2788) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-232](https://atrainingtracker.atlassian.net/browse/ATT-232) (*Process & Engineering Workflow*)  
**Target Release**: `Unscheduled` (In-Sprint `2026-41.4`)  
**Active Sprint**: `2026-41.4`  
**Requirement Mapping**: `REQ-PRO-025` (*Automated Test Execution Acceleration, Worker Parallelism, and Resource-Safe Caching*)  
**Test Mapping**: `TST-PRO-018` (*Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking*)  
**Branch**: `improvement/ATT-2770`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-08  

---

## 1. Problem Description & Background

The automated test suite in `aTrainingTracker` has expanded into an extensive ASPICE quality firewall comprising **436 test classes** and **over 2,130 individual unit tests**.

During autonomous in-sprint development (ASPICE Stage 4 construction and Stage 5 clean-room verification), running the full regression test suite via `./gradlew testDebugUnitTest` takes **7 to 9 minutes** on an 8-core CPU workstation.

Root causes identified in Stage 1 forensic investigation:
1. **CPU Underutilization**: `testOptions.unitTests.all` is absent in `app/build.gradle`. AGP defaults `maxParallelForks` to `1`, leaving 7 of 8 CPU cores idle while 436 test classes execute sequentially.
2. **Monolithic Worker JVM & MockK Classloader Accumulation**: `forkEvery` defaults to `0` (never fork a new JVM). Over 150 test suites use MockK and dynamic ByteBuddy bytecode generation. Running all classes in a single JVM causes classloader leakage, heap fragmentation, and extended stop-the-world Full GC pauses toward the end of the test run.
3. **Disabled Gradle Parallelism & Caching**: In `gradle.properties`, `# org.gradle.parallel=true` is commented out, and `org.gradle.caching=true` is missing.
4. **Redundant Localization Disk I/O**: Over 40 localization test classes repeatedly read and parse XML strings from disk from scratch across all 9 locales.

This plan details the atomic steps to accelerate test execution to under 3.5 minutes while bounding memory consumption to host-safe limits ($\le 3.1\text{ GB}$ aggregate).

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-PRO-025` (*Automated Test Execution Acceleration, Worker Parallelism, and Resource-Safe Caching*)
* **Test Mapping**: `TST-PRO-018` (*Parallel Unit Test Forking, Worker Recycling, and Performance Benchmarking*):
  - `TST-PRO-018.1`: Parallel Worker Forking & Multi-Core Utilization Verification
  - `TST-PRO-018.2`: Defensive Memory Allocation Capping Verification (`minHeapSize=256m`, `maxHeapSize=768m`)
  - `TST-PRO-018.3`: Shared Localization DOM Cache Thread-Safety & Immutability Verification (`LocalizationTestCacheTest.kt`)
  - `TST-PRO-018.4`: Worker Recycling & MockK Heap Stability Verification (`forkEvery=80`)
  - `TST-PRO-018.5`: Full Clean-Room Regression & Performance Benchmark Execution (`testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Process-Level Test Isolation**: Gradle's `maxParallelForks` spawns separate operating system JVM processes (`GradleWorkerMain`). Static fields, global mocks (`mockkStatic`), and thread-locals remain completely isolated between parallel worker processes.
2. **Database Isolation**: In-memory SQLite and Room instances are worker-local and tests within each worker execute sequentially.
3. **Zero Test Flakiness**: All 436 test classes MUST pass cleanly with 100% determinism.
4. **Host Memory Safety**: Aggregate test worker heap allocation is strictly capped at $\le 3.072\text{ GB}$ ($4 \times 768\text{ MB}$), preserving over 75% of physical memory for host operations, the Gradle Daemon, and the IDE.
5. **No Production Bytecode Alteration**: All modifications are strictly restricted to build configuration (`gradle.properties`, `app/build.gradle`) and test-only helper infrastructure (`app/src/test/...`).
6. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
7. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: Gradle Build Configuration (`gradle.properties`)
- Uncomment `org.gradle.parallel=true`: Enables parallel execution of independent, decoupled Gradle tasks.
- Add `org.gradle.caching=true`: Enables Gradle's local build cache for deterministic task output reuse across compilation and pre-processing steps.

### Component 2: Android Unit Test Options (`app/build.gradle`)
- Configure `android.testOptions.unitTests.all`:
  - `maxParallelForks`: Dynamic hardware-adaptive calculation:
    `Math.max(1, Runtime.getRuntime().availableProcessors() / 2)` (yields 4 workers on an 8-core CPU).
  - `forkEvery = 80`: Recycles worker JVM after executing 80 test classes, flushing Metaspace and ByteBuddy classloaders.
  - `minHeapSize = "256m"` / `maxHeapSize = "768m"`: Bounds heap growth per worker.
  - `jvmArgs`: Configure `-XX:+TieredCompilation`, `-XX:TieredStopAtLevel=1`, `-Dfile.encoding=UTF-8`.
  - `testLogging`: Log failed test events with full exception formatting for debugging clarity.

### Component 3: Shared In-Memory Localization DOM Cache (`LocalizationTestCache.kt`)
- Location: `app/src/test/java/com/atrainingtracker/testing/LocalizationTestCache.kt`
- Thread-safe singleton backed by `ConcurrentHashMap<String, Map<String, String>>`.
- Loads `strings.xml` per locale via `computeIfAbsent`.
- Returns immutable maps via `Collections.unmodifiableMap()`.
- Provides raw text caching for regex-based tests.
- Accompanied by unit test suite `LocalizationTestCacheTest.kt` verifying thread safety, immutability, and 9-locale parity.

### UI Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. (Build configuration and test infrastructure optimization only).

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2788`
* Verification: Ensure exit code is 0 before modifying source code.

### Step 2: Configure Gradle Daemon & Local Build Cache
* File: `gradle.properties`
* Change: Uncomment `org.gradle.parallel=true` and append `org.gradle.caching=true`.
* Verification: Run `./gradlew --status` and verify daemon properties.

### Step 3: Configure Multi-Worker Test Options in AGP
* File: `app/build.gradle`
* Change: Add `testOptions.unitTests.all` configuration inside `android { ... }`:
  ```groovy
  testOptions {
      unitTests.all {
          maxParallelForks = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)
          forkEvery = 80
          minHeapSize = "256m"
          maxHeapSize = "768m"
          jvmArgs "-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1", "-Dfile.encoding=UTF-8"
          testLogging {
              events "failed"
              exceptionFormat "full"
          }
      }
  }
  ```
* Verification: Run `./gradlew help` to verify Gradle script syntax and configuration evaluation.

### Step 4: Implement `LocalizationTestCache`
* File: `app/src/test/java/com/atrainingtracker/testing/LocalizationTestCache.kt`
* Change: Create thread-safe in-memory cache helper supporting all 9 locales:
  - `values`, `values-de`, `values-es`, `values-fr`, `values-it`, `values-ja`, `values-nl`, `values-pl`, `values-pt`.
  - Methods: `getStrings(localeDir, filename)`, `getRawContent(localeDir, filename)`, `clear()`.

### Step 5: Implement `LocalizationTestCacheTest`
* File: `app/src/test/java/com/atrainingtracker/testing/LocalizationTestCacheTest.kt`
* Change: Author unit tests verifying:
  - `testCache_returnsCompleteAndValidStringsMap`: verifies required strings present across all 9 locales.
  - `testCache_returnsImmutableMap`: verifies `map.put` or `clear` throws `UnsupportedOperationException`.
  - `testCache_concurrentAccessIsThreadSafe`: spawns 16 threads concurrently requesting strings.
  - `testCache_idempotentSubsequentCallsDoNotReReadDisk`: confirms cache reuse.
* Targeted Test Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.testing.LocalizationTestCacheTest"`

### Step 6: Refactor Sample Localization Test to Validate Cache Integration
* File: `app/src/test/java/com/atrainingtracker/trainingtracker/ui/tracking/controltracking/BatteryOptimizationBannerLocalizationTest.kt`
* Change: Utilize `LocalizationTestCache.getStrings(locale)` / `getRawContent(locale)`.
* Targeted Test Command:
  `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.ui.tracking.controltracking.BatteryOptimizationBannerLocalizationTest"`

---

## 6. Verification & Rollback Plan

### 6.1 Verification
1. **Targeted Unit Test Verification**:
   - Run `LocalizationTestCacheTest` to confirm cache thread-safety and correctness.
2. **Process & Memory Inspection**:
   - Run `./gradlew testDebugUnitTest` and monitor `ps aux | grep GradleWorkerMain` to verify:
     - Exactly 4 concurrent worker processes running on 8 cores.
     - Presence of `-Xms256m` and `-Xmx768m`.
     - Worker PIDs recycling every 80 test classes.
3. **Clean-Room Benchmark**:
   - Run `./gradlew clean testDebugUnitTest`.
   - Verify 100% pass rate across all 436 test classes (>2,130 tests).
   - Verify execution time drop from ~7–9 minutes to $\le 3.5\text{ minutes}$ ($\ge 40\%$ speedup).

### 6.2 Rollback Plan
* If unexpected test concurrency conflicts or memory anomalies arise:
  - The changes are strictly isolated to branch `improvement/ATT-2770`.
  - `git revert` or resetting to sprint base commit `5428d766` restores default single-threaded behavior instantly with zero impact on `develop` or production releases.
