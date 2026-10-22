# Stage 2: Requirement & Test Specification - ATT-4030: Improve exception handling hygiene across main sources

**Ticket**: [ATT-4030](https://rainerblind.atlassian.net/browse/ATT-4030)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4030`  
**Requirement Mapping**: `REQ-ARC-002` (*Exception Handling Hygiene, Fatal Error Propagation, Diagnostic Logging & Thread Interrupt Preservation*)  
**Test Spec ID**: `TST-ARC-002`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Requirement Specification (`REQ-ARC-002`)

### 1.1 Problem Statement & Rationale
Audited production sources (`FitFileWriter`, `PeriodsRepository`, `StravaUploader`, `MyBanalDebugHelper`, `ForkNavigationRepository`, `WorkoutClusterDatabaseManager`, `MigrationEngine`) contained anti-patterns: silent empty catch blocks hiding malformed inputs, uncalibrated `catch (Throwable)` blocks suppressing fatal JVM faults (`OutOfMemoryError`) and coroutine cancellation, discarded thread interrupt flags on backoff sleep, legacy `e.printStackTrace()` calls, and unsafe force-unwraps (`!!`).

### 1.2 Functional & Architectural Requirements
1. **Silent/Empty Catch Elimination & Warning Logging (`REQ-ARC-002.1`)**: Empty and silent catch blocks SHALL be eliminated across `FitFileWriter.java`, `StravaUploader.kt`, `WorkoutClusterDatabaseManager.kt`, `MigrationEngine.kt`, and `BackupManager.kt`. Caught recoverable parse/IO exceptions SHALL emit diagnostic `Log.w` messages prior to applying safe fallback values.
2. **Fatal JVM Error Propagation & Exception Scoping (`REQ-ARC-002.2`)**: In `FitFileWriter.java`, `PeriodsRepository.kt`, and `MigrationEngine.kt`, operational `catch (Throwable)` blocks SHALL be replaced with `catch (Exception)`. Fatal JVM errors (`VirtualMachineError`, `OutOfMemoryError`, `LinkageError`) and coroutines cancellation signals MUST propagate naturally.
3. **Thread Interrupt Preservation (`REQ-ARC-002.3`)**: In `StravaUploader.kt` (`sleepStrategy`), when `InterruptedException` is caught during exponential backoff sleep, cooperative cancellation SHALL be preserved by restoring thread interrupt state via `Thread.currentThread().interrupt()`.
4. **Android Logging & Safe Null Handling (`REQ-ARC-002.4`)**: In `MyBanalDebugHelper.java`, `e.printStackTrace()` calls SHALL be replaced with `Log.e(TAG, "...", e)`. In `ForkNavigationRepository.kt`, unsafe force-unwraps (`!!`) on `lastSearchPos` SHALL be replaced with thread-safe local val bindings.
5. **Behavioral & Policy Verification (`REQ-ARC-002.5`)**: Verified through behavioral unit tests (`ExceptionHandlingHygieneTest.kt`) covering fallbacks, interrupt flags, and safe null checks, paired with structural policy guards.

### 1.3 Requirement Archaeology & Chesterton's Fence Audit
* **Original Requirement ID & Target**: Net-new requirement `REQ-ARC-002` under Epic `ATT-431` (*Clean Code*), extending `REQ-ARC-001` and `REQ-TST-001`. Targets `FitFileWriter.java`, `PeriodsRepository.kt`, `StravaUploader.kt`, `MyBanalDebugHelper.java`, `ForkNavigationRepository.kt`, `WorkoutClusterDatabaseManager.kt`, and `MigrationEngine.kt`.
* **Historical Origin & Commit Trace**: Independent whole-app code review (`0234bfc0`, `feature/ATT-3921`, Oct 2026). Defensive stopgaps accumulated over incremental feature development.
* **Root Reason for Existing Formulation**: Blanket catches and silent swallows were added to prevent background uploaders or export routines from aborting abruptly on unexpected inputs.
* **Preservation of Core Invariants**: Defensive return sentinels (`emptyList()`, `null`, default timestamps) remain intact to maintain 100% crash resilience without swallowing diagnostic error causes.

### 1.4 Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Date Parse Diagnostic Fallback)**: *Given* invalid date strings passed to `StravaUploader.parseTimeToEpochSeconds`, *When* parsed, *Then* `Log.w` is emitted, `null` is returned, and no exception escapes unhandled.
* **Criterion 2 (Malformed JSON Fallback)**: *Given* malformed JSON in `WorkoutClusterDatabaseManager.deserializePreviewPaths`, *When* deserialized, *Then* `Log.w` is emitted and `emptyList()` is returned safely.
* **Criterion 3 (Thread Interrupt Preservation)**: *Given* an interrupted thread during `StravaUploader` backoff sleep, *When* `InterruptedException` is caught, *Then* `Thread.currentThread().isInterrupted` remains `true`.
* **Criterion 4 (Safe Null Handling)**: *Given* `ForkNavigationRepository` when `lastSearchPos` is null, *When* queried, *Then* it executes without `NullPointerException` or unsafe `!!`.
* **Criterion 5 (Policy Audit)**: *Given* audited main production classes, *When* scanned, *Then* zero `e.printStackTrace()` and zero operational `catch (Throwable)` occurrences exist.

---

## 2. Test Specification (`TST-ARC-002`)

### Test Cases
* `TST-ARC-002.1` (**Silent/Empty Catch Elimination & Fallback Unit Tests**): `ExceptionHandlingHygieneTest.kt` verifies graceful `null` / `emptyList()` fallbacks and warning logs on malformed date and JSON inputs.
* `TST-ARC-002.2` (**Fatal Error Escalation & Exception Scoping Tests**): Verifies `catch (Throwable)` elimination in `PeriodsRepository`, `FitFileWriter`, and `MigrationEngine`, ensuring `OutOfMemoryError` propagates.
* `TST-ARC-002.3` (**Thread Interrupt Preservation Tests**): Verifies cooperative cancellation in `StravaUploader` by asserting thread interrupt status restoration.
* `TST-ARC-002.4` (**Logging & Safe Null Handling Tests**): Verifies `Log.e` output in `MyBanalDebugHelper` and null-safe branching in `ForkNavigationRepository`.
* `TST-ARC-002.5` (**Structural Policy Guard Audit**): Scans audited production source files asserting zero `e.printStackTrace()` and zero operational `catch (Throwable)`.
* `TST-ARC-002.6` (**Clean-Room Full Suite Regression Execution**): `./gradlew testDebugUnitTest` and `TranslationParityTest` achieve 100% pass rate.

---

## 3. Traceability Matrix

| Test Case | Scope | Target File / Class Under Test | Requirement | Status |
| :--- | :--- | :--- | :--- | :--- |
| `TST-ARC-002.1` | Unit | `StravaUploader.kt`, `WorkoutClusterDatabaseManager.kt`, `FitFileWriter.java` | `REQ-ARC-002.1` | In Progress (ATT-4030) |
| `TST-ARC-002.2` | Unit/Arch | `PeriodsRepository.kt`, `FitFileWriter.java`, `MigrationEngine.kt` | `REQ-ARC-002.2` | In Progress (ATT-4030) |
| `TST-ARC-002.3` | Unit | `StravaUploader.kt` | `REQ-ARC-002.3` | In Progress (ATT-4030) |
| `TST-ARC-002.4` | Unit | `MyBanalDebugHelper.java`, `ForkNavigationRepository.kt` | `REQ-ARC-002.4` | In Progress (ATT-4030) |
| `TST-ARC-002.5` | Contract | `ExceptionHandlingHygieneTest.kt` | `REQ-ARC-002.5` | In Progress (ATT-4030) |
| `TST-ARC-002.6` | Regression | `./gradlew testDebugUnitTest`, `TranslationParityTest.kt` | `REQ-ARC-002.5` | In Progress (ATT-4030) |
