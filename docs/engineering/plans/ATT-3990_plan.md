# Stage 3: Implementation Plan: ATT-3990

**Ticket**: [ATT-3990](https://atrainingtracker.atlassian.net/browse/ATT-3990)  
**Subtask**: `ATT-4163` (`[Plan]`)  
**Parent Epic**: None  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Mountain Climb Preservation*)  
**Test Case Mapping**: `TST-CON-008.6` (*High-Altitude Climb Negative Test & Zero DB Open Mutation*), `TST-CON-008.7` (*Database Open Thread Leak Elimination*)  
**Branch**: `feature/ATT-3990`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Architectural Design (SWE.2)

### 1.1 Architecture & Component Boundaries
The remediation targets the SQLite database helper lifecycle in `WorkoutSummariesDatabaseManager.java`:
1. **`WorkoutSummariesDbHelper.onOpen()` Excision**:
   - Completely remove the call to `Executors.newSingleThreadExecutor()` and `sanitizeCorruptedAltitudeWorkouts()`.
   - `onOpen(@NonNull SQLiteDatabase db)` will strictly call `super.onOpen(db)`.
   - Guarantees zero side effects, zero thread spawning, zero background executor leakage, and zero data modification when opening the database.
2. **`WorkoutSummariesDatabaseManager.sanitizeCorruptedAltitudeWorkouts()` Decoupling**:
   - Deprecate `sanitizeCorruptedAltitudeWorkouts()` (`@Deprecated`) with explicit documentation explaining that automated background execution is prohibited under `REQ-CON-017` / `ATT-3990`.
   - Verify that zero production callers invoke this method.
3. **Unit Test Suite Expansion (`WorkoutSummariesDatabaseManagerSanitizationTest.kt`)**:
   - Add negative test `testDatabaseOpen_preservesLegitimateHighAltitudeClimbsWithoutModification()` asserting that a genuine mountain climb (600m to 4200m) is never altered during database open.
   - Add thread leak elimination test `testOnOpen_doesNotSpawnUnmanagedExecutorOrLeakThreads()` asserting that repeated calls to `onOpen` spawn zero threads.

---

## 2. Step-by-Step Implementation Steps

### Step 1: Remove Thread Leak and Sanitizer Call from `WorkoutSummariesDbHelper.onOpen()`
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
- **Location**: Lines 1689–1700
- **Action**:
  - Replace the entire body of `onOpen` with:
    ```java
    @Override
    public void onOpen(@NonNull SQLiteDatabase db) {
        super.onOpen(db);
    }
    ```
  - Remove unused import `java.util.concurrent.Executors` if no other usages remain in the class.

### Step 2: Deprecate `sanitizeCorruptedAltitudeWorkouts()` and Document Non-Destructive Invariant
- **Target File**: `app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java`
- **Location**: Line 1175
- **Action**:
  - Add `@Deprecated` annotation and comprehensive JavaDoc:
    ```java
    /**
     * @deprecated Automated background mutation of historical workout altitude data is prohibited (REQ-CON-017, ATT-3990).
     * Database opening MUST remain side-effect free and preserve legitimate mountain climbs.
     */
    ```

### Step 3: Implement Unit Tests in `WorkoutSummariesDatabaseManagerSanitizationTest.kt`
- **Target File**: `app/src/test/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManagerSanitizationTest.kt`
- **Actions**:
  1. Add `testOnOpen_doesNotSpawnUnmanagedExecutorOrLeakThreads()` (`TST-CON-008.7`):
     - Instantiate `WorkoutSummariesDbHelper` with mock context.
     - Call `onOpen(mockDb)` 25 times.
     - Assert zero exceptions and verify that no background tasks or threads are leaked.
  2. Add `testDatabaseOpen_preservesLegitimateHighAltitudeClimbsWithoutModification()` (`TST-CON-008.6`):
     - Set up mock cursor and database with a workout having `min = 600.0 m` and `max = 4200.0 m`.
     - Profile: `[600.0, 1200.0, 1800.0, 2400.0, 3000.0, 3600.0, 4200.0]`.
     - Invoke `onOpen(mockDb)`.
     - Verify zero invocations of `mockDb.update(...)` or `mockDb.execSQL(...)`.
     - Confirm workout data is 100% untouched.

### Step 4: Full Clean-Room Regression Verification
- **Command**: `./gradlew testDebugUnitTest`
- **Action**: Verify all unit test suites in the app pass with 100% success rate (0 failures, 0 regressions).

---

## 3. System Invariants & Safety Measures

1. **Passive Database Open Contract**:
   - SQLite `onOpen()` is invoked whenever a connection is opened. It must never perform long-running, mutating, or multi-threaded background operations.
2. **Zero False Positives**:
   - By eliminating the automated background invocation, the possibility of false-positive data loss on high-speed descents or genuine climbs is eliminated.
3. **Preservation of Live Altimeter Safeguards**:
   - `AltitudeFromPressureDevice.java`, `TrackingTabsViewModel.kt`, and `TrackerService.java` remain untouched. All live tracking safeguards (geofence gating, oscillation prevention, and climb rate limits) remain active.

---

## 4. Gate 3 Readiness Verification
- Detailed atomic steps mapped directly to target files.
- Concrete negative test and thread-safety tests specified.
- Architectural compliance with ASPICE SWE.2 and SWE.3.
