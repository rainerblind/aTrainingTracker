# Stage 5: Walkthrough & Verification - ATT-3980: [Highest] Resumed workout writes all samples to a table named "null"

**Ticket**: [ATT-3980](https://rainerblind.atlassian.net/browse/ATT-3980)  
**Sub-task**: [ATT-4050](https://rainerblind.atlassian.net/browse/ATT-4050) (`[Test]`)  
**Parent Epic**: [ATT-171](https://rainerblind.atlassian.net/browse/ATT-171) (*General Improvements and Bugs*)  
**Target Release**: `(Unreleased — Assigned at Final Acceptance per Rule 14)`  
**Active Sprint**: `2026-42.9` (`id = 377`)  
**Requirement Mapping**: `REQ-TRK-026`  
**Test Mapping**: `TST-TRK-018`  
**Branch**: `feature/ATT-3980`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-18  

---

## 1. Executive Summary & Verification Overview

This walkthrough documents the end-to-end implementation and clean-room verification of **ATT-3980**, resolving a critical data-loss defect where resuming a workout (`RESUME_BY_USER` or `RESUME_SERVICE_RECREATION`) caused `TrackerService` to write all subsequent 1 Hz samples to a non-existent SQLite table named `"\"null\""` instead of the resumed workout's actual sample table (`"\"<BASE_FILE_NAME>\""`), with SQLite `no such table: null` exceptions silently swallowed inside `handleMissingColumns()`.

### Summary of Verified Changes
1. **Database Boundary Hardening (`WorkoutSamplesDatabaseManager.java`)**:
   - Hardened `getTableName(@NonNull String workoutName)` to reject `null`, empty/blank, or literal `"null"` (case-insensitive) input by throwing an `IllegalArgumentException`.
   - Added defensive validation in `existsTable(String baseFileName)` returning `false` on `null`, blank, or `"null"` input without throwing.
2. **Service Resume & Asynchronous `BANALService` Binding Synchronization (`TrackerService.java`)**:
   - Added package-private helper `resolveSamplesTableNameSafely(String baseFileName)` at the `TrackerService` boundary.
   - Updated `applyResumedState(@NonNull ResumedWorkoutState state)` to immediately compute `mSamplesTableName = resolveSamplesTableNameSafely(mBaseFileName)`, complete `mTableInitializationFuture.complete(null)`, and invoke `notifyTrackingStarted(mWorkoutID)` when `mWorkoutID > 0` (with a null-safe `getBaseContext() == null` guard in `notifyTrackingStarted` for pure JUnit tests).
   - Updated `onStartCommand()` to initialize `mTableInitializationFuture` idempotently (`if (mTableInitializationFuture == null || mTableInitializationFuture.isDone())`), only compute `mSamplesTableName` when `mBaseFileName` is already populated, and cancel `mTableInitializationFuture` on FGS abort or in `onDestroy()`.
   - Reordered `mBanalConnection.onServiceConnected()` so `recreateValuesWhenResuming()` executes **before** `mBanalService.createNewLap()` and `onSearchingFinished()`, guaranteeing `mWorkoutID`, `mLapNr`, `mBaseFileName`, and `mSamplesTableName` are restored before lap increments or active tracking transitions.
   - Updated `mAltitudeCorrectionReceiver` to capture `mWorkoutID` and `mSamplesTableName` inside `mTableInitializationFuture.thenAcceptAsync(...)`.
   - Updated `sampleAndWriteToDb()` and `handleMissingColumns()` (`boolean` return) to self-heal missing sample tables when `mBaseFileName` is valid and skip `db.setTransactionSuccessful()` on unrecoverable `SQLiteException` without crashing the foreground `TrackerService`.
3. **Unit & Regression Test Coverage (`TrackerServiceResumeTest.kt`)**:
   - Expanded `TrackerServiceResumeTest.kt` to 7 comprehensive unit tests covering `TST-TRK-018` Vectors 1–4.

---

## 2. Requirement & Test Verification Matrix

| Requirement | Test Spec | Verification Method | Result | Status in Living Docs |
| :--- | :--- | :--- | :--- | :--- |
| `REQ-TRK-026` (`REQ-TRK-026.1`) | `TST-TRK-018` (Vector 1) | Automated Unit Test (`testApplyResumedState_updatesSamplesTableNameAndCompletesFuture`) | **PASSED** | `Verified` |
| `REQ-TRK-026` (`REQ-TRK-026.2`, `REQ-TRK-026.3`) | `TST-TRK-018` (Vectors 2 & 3) | Automated Unit Tests (`testDeferredResumeServiceRecreation_bindsBanalServiceAsync_writesSamplesToWorkoutTable`, `testDeferredResumeByUser_restoresStateBeforeCreateNewLap`) | **PASSED** | `Verified` |
| `REQ-TRK-026` (`REQ-TRK-026.2`, `REQ-TRK-026.4`) | `TST-TRK-018` (Vector 4) | Automated Unit Test (`testGetTableName_rejectsNullBlankAndLiteralNull_andHandleMissingColumnsSelfHealsOrRejects`) | **PASSED** | `Verified` |
| `REQ-PRO-001` | `TST-TRK-018` (Clean-Room Suite) | Full Clean-Room `./gradlew testDebugUnitTest` | **PASSED** (100%) | `Verified` |

---

## 3. Automated Test Evidence

### Clean-Room Regression Suite (`./gradlew testDebugUnitTest`)
```text
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac
> Task :app:compileDebugUnitTestKotlin
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 30s
32 actionable tasks: 12 executed, 20 up-to-date
```

### Targeted Unit & Integration Tests (`TrackerServiceResumeTest` & `FreshInstallLocationInitTest`)
```text
./gradlew testDebugUnitTest --tests "com.atrainingtracker.trainingtracker.tracker.*" --tests "com.atrainingtracker.banalservice.devices.FreshInstallLocationInitTest"

> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 22s
32 actionable tasks: 2 executed, 30 up-to-date
```
All 7 test methods in `TrackerServiceResumeTest` (`testApplyResumedState_populatesAccumulatorsAndForwardsToBanalService`, `testApplyResumedState_handlesNullOrNegativeValuesGracefully`, `testApplyResumedState_handlesNullBanalServiceWithoutCrashing`, `testApplyResumedState_updatesSamplesTableNameAndCompletesFuture`, `testDeferredResumeServiceRecreation_bindsBanalServiceAsync_writesSamplesToWorkoutTable`, `testDeferredResumeByUser_restoresStateBeforeCreateNewLap`, and `testGetTableName_rejectsNullBlankAndLiteralNull_andHandleMissingColumnsSelfHealsOrRejects`) and all 76 tests in the targeted packages passed with 0 failures.

---

## 4. Hardware / Physical Verification (Pixel 10)

Pure backend service lifecycle and SQLite database persistence fix (`TrackerService.java` and `WorkoutSamplesDatabaseManager.java`); verified deterministically via `TrackerServiceResumeTest` simulating asynchronous `onStartCommand()` -> `mBanalConnection.onServiceConnected()` -> `sampleAndWriteToDb()` lifecycle sequencing and the full clean-room regression suite. On-device deployment (`./gradlew installDebug`) is performed at the start of Ceremony 2 (Sprint Review) per Rule 10.

### Visual Consistency (Rule 23 — mandatory if UI is added or changed)
No UI changes. Zero Compose screens, XML layouts, or string resources were added or modified.

---

## 5. Invariant & Governance Verification

1. **Zero Production Regressions**: Clean-room unit test suite (`./gradlew testDebugUnitTest`) executed with 100% pass rate.
2. **Living Documentation Synchronized**: Status of `REQ-TRK-026` in `docs/requirements.md` and `TST-TRK-018` in `docs/tests.md` updated to `Verified`.
3. **Subtask Completion**: Stage 5 subtask `ATT-4050` transitioned to `Erledigt` via `freigabe`.
4. **Sprint Branch Integration & Parent Ticket Final Review**: `feature/ATT-3980` merged into `sprint/2026-42.9` (`--no-ff`), `feature/ATT-3980` deleted, and `ATT-3980` transitioned to `Final Review (Human)`.
