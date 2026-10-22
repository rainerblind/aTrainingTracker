# Stage 3: Implementation Plan - ATT-4030: Improve exception handling hygiene across main sources

**Ticket**: [ATT-4030](https://rainerblind.atlassian.net/browse/ATT-4030)  
**Sub-task**: [ATT-4649](https://rainerblind.atlassian.net/browse/ATT-4649) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-431](https://rainerblind.atlassian.net/browse/ATT-431) (*Clean Code*)  
**Target Release**: `4.9.40` | **Active Sprint**: `2026-43.6` | **Branch**: `feature/ATT-4030`  
**Requirement Mapping**: `REQ-ARC-002`  
**Test Mapping**: `TST-ARC-002`  
**Author**: AI Agent 1 (Implementer) | **Date**: 2026-10-22  

---

## 1. Problem Description & Background
Independent code review identified exception handling anti-patterns across main sources: silent catches hiding malformed inputs, blanket `catch (Throwable)` suppressing fatal JVM errors (`OutOfMemoryError`), discarded thread interrupts on sleep, legacy `e.printStackTrace()`, and unsafe `!!` force-unwraps.

---

## 2. Traceability & Requirements Mapping
* **Requirement**: `REQ-ARC-002` (`REQ-ARC-002.1` – `REQ-ARC-002.5`)
  * `REQ-ARC-002.1`: Silent catch elimination and diagnostic logging across `FitFileWriter`, `StravaUploader`, `WorkoutClusterDatabaseManager`, `MigrationEngine`, `BackupManager`.
  * `REQ-ARC-002.2`: `catch (Throwable)` eradication and `Exception` scoping in `FitFileWriter`, `PeriodsRepository`, `MigrationEngine`.
  * `REQ-ARC-002.3`: Thread interrupt restoration in `StravaUploader.sleepStrategy`.
  * `REQ-ARC-002.4`: `MyBanalDebugHelper` `Log.e` conversion & `ForkNavigationRepository` safe null binding.
  * `REQ-ARC-002.5`: Automated unit and policy tests in `ExceptionHandlingHygieneTest.kt`.
* **Test Mapping**: `TST-ARC-002` (`TST-ARC-002.1` – `TST-ARC-002.6`).

---

## 3. System Invariants & Preserved Behavior
1. **Graceful Fallbacks**: Bad input data (dates, JSON, DB records) continues to return safe defaults (`null`, `emptyList()`, `emptyMap()`) without crashing user workflows.
2. **Cancellation Propagation**: Coroutine `CancellationException` and fatal JVM errors are never swallowed.
3. **Thread State Integrity**: Interrupted threads retain interrupted status for cooperative task cancellation.
4. **Zero Regressions**: 100% clean-room test pass rate across existing test suites.

---

## 4. Proposed Architectural Changes
* **Component 1 (Exporters & Uploaders)**:
  * `FitFileWriter.java`: Log warnings at L131, L331; replace `catch (Throwable)` with `catch (Exception)` at L457, L471.
  * `StravaUploader.kt`: Narrow catches in `parseTimeToEpochSeconds`; restore interrupt in `sleepStrategy`.
* **Component 2 (Repositories & Databases)**:
  * `PeriodsRepository.kt`: Replace `catch (Throwable)` at L114, L123, L825, L831 with `catch (e: Exception)` and logging.
  * `WorkoutClusterDatabaseManager.kt`: Narrow L190 to `JSONException` with warning log.
  * `ForkNavigationRepository.kt`: Replace `lastSearchPos!!` at L121, L122 with local safe val.
* **Component 3 (Migration & Debug Helpers)**:
  * `MigrationEngine.kt` / `BackupManager.kt`: Replace `Throwable` with `Exception` at L108; log stream close failures.
  * `MyBanalDebugHelper.java`: Replace `printStackTrace()` with `Log.e`.
* **UI Consistency**: No UI changes.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)
### Step 1: Exporter & Uploader Hardening
* Edit `FitFileWriter.java` (warning logs on parse errors; `Throwable` -> `Exception` in cleanup).
* Edit `StravaUploader.kt` (narrow parse catches; thread interrupt restoration in `sleepStrategy`).

### Step 2: Repository & Database Hygiene
* Edit `PeriodsRepository.kt` (`Throwable` -> `Exception` + diagnostic logs on DB queries).
* Edit `WorkoutClusterDatabaseManager.kt` (`JSONException` scoping on preview paths).
* Edit `ForkNavigationRepository.kt` (eliminate `!!` on `lastSearchPos`).

### Step 3: Migration & Debug Helper Cleanup
* Edit `MigrationEngine.kt` and `BackupManager.kt` (`Throwable` -> `Exception` + log warnings).
* Edit `MyBanalDebugHelper.java` (replace `printStackTrace()` with `Log.e`).

### Step 4: Targeted Unit & Policy Test Suite
* Create `app/src/test/java/com/atrainingtracker/trainingtracker/build/ExceptionHandlingHygieneTest.kt`.
* Command: `./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.build.ExceptionHandlingHygieneTest"`

---

## 6. Verification & Rollback Plan
* **Verification**: Targeted tests in Stage 4; full suite `./gradlew testDebugUnitTest` and governance script in Stage 5.
* **Rollback**: Atomic git revert on branch `feature/ATT-4030`.
