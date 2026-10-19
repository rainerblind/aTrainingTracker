# Stage 2: Requirement & Test Specification: ATT-3990

**Ticket**: [ATT-3990](https://atrainingtracker.atlassian.net/browse/ATT-3990)  
**Subtask**: `ATT-4162` (`[Test-Spec]`)  
**Parent Epic**: None  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Mountain Climb Preservation*)  
**Test Case Mapping**: `TST-CON-008.6` (*High-Altitude Climb Negative Test & Zero DB Open Mutation*), `TST-CON-008.7` (*Database Open Thread Leak Elimination*)  
**Branch**: `feature/ATT-3990`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. System Requirements & Functional Specifications

### 1.1 Technical Constraints (MUST / MUST NOT)
1. **Zero Database Open Side-Effects**:
   - `WorkoutSummariesDbHelper.onOpen()` MUST NOT execute asynchronous data mutators, background sanitizers, or heuristics.
   - `WorkoutSummariesDbHelper.onOpen()` MUST NOT create, spawn, or leak background threads or `ExecutorService` instances.
2. **Immutability of Legitimate Altitude Records**:
   - The database layer MUST NOT modify, truncate, or clamp legitimate altitude profiles during database open or general read operations.
   - Workouts with genuine high-altitude climbs (including ascents exceeding 2000.0 m, 3000.0 m, or 4000.0 m) MUST remain bit-for-bit identical in both summary streams (`ALTITUDE_STREAM`) and raw sample tables (`WorkoutSamples`).
3. **Automated Silent Modification Prohibition**:
   - The system MUST NOT execute automated, silent background rewrites of historical workout data without explicit user initiation and non-destructive backups.

### 1.2 Functional Behavior (SHALL)
1. **Passive Database Open**:
   - When `WorkoutSummariesDbHelper.onOpen(SQLiteDatabase db)` is called by the Android SQLite framework, it SHALL strictly delegate to `super.onOpen(db)` and complete synchronously without side-effects.
2. **Thread Safety & Resource Hygiene**:
   - All database opening procedures SHALL maintain zero thread allocation overhead, preventing thread exhaustion or unbounded executor leakage.
3. **Preservation of Live Altimeter Safeguards**:
   - Live barometric calibration safeguards established under ATT-1810 (geofence gating, feedback oscillation immunity, and $|dh/dt| \le 30\text{ m/s}$ physical climb limits) SHALL remain 100% operational.

---

## 2. Acceptance Criteria (Given-When-Then)

### Acceptance Criterion 1: Legitimate Mountain Ascent Data Preservation (Negative Test)
- **Given** a stored workout with a genuine alpine climb from base elevation to a high summit (e.g. elevation points: `[600.0, 1200.0, 1800.0, 2400.0, 3000.0, 3600.0, 4200.0]`, where `max_altitude = 4200.0 m` and `min_altitude = 600.0 m`),
- **When** the database is opened via `WorkoutSummariesDatabaseManager`,
- **Then** `ALTITUDE_STREAM` SHALL retain all points unchanged (`[600.0, 1200.0, 1800.0, 2400.0, 3000.0, 3600.0, 4200.0]`), `TABLE_EXTREMA_VALUES` MAX altitude SHALL remain `4200.0 m`, and zero `UPDATE` queries SHALL be executed against `WorkoutSamples`.

### Acceptance Criterion 2: Thread Leak Elimination on Database Open
- **Given** `WorkoutSummariesDbHelper` initialized in `WorkoutSummariesDatabaseManager`,
- **When** `onOpen(SQLiteDatabase db)` is invoked repeatedly (e.g. 50 times across app activities and queries),
- **Then** zero instances of `Executors.newSingleThreadExecutor()` SHALL be allocated, and zero background threads SHALL remain active or leaked.

### Acceptance Criterion 3: Zero Automated Mutation Guarantee
- **Given** any existing workout database opened during app startup or migration,
- **When** database initialization completes,
- **Then** zero workout records SHALL have been modified, deleted, or clamped by automated background heuristics.

---

## 3. Chesterton's Fence & Requirement Archaeology (`REQ-CON-017`)

1. **Original Requirement ID & Target**:
   - `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration, Delta Shift Dispatch, Physical Sanity Limiting & Historical Workout Data Sanitization*), targeting `AltitudeFromPressureDevice.java` and `WorkoutSummariesDatabaseManager.java`.
2. **Historical Origin & Commit Trace**:
   - Commit `53502c7a` (`ATT-1353`), Sprint `2026-40.5` (`ATT-1617`), and Sprint `2026-42.10` (`ATT-3990`).
3. **Root Reason for Existing Formulation**:
   - `sanitizeCorruptedAltitudeWorkouts()` was originally implemented to clean up artificial 7300 m runaway barometric spikes created by a legacy calibration oscillation bug (`ATT-1810`).
   - However, placing the heuristic inside `WorkoutSummariesDbHelper.onOpen()` caused:
     - Severe data loss: any mountain climb from $<1000\text{ m}$ to $>4000\text{ m}$ had all points above $2000\text{ m}$ permanently flattened to valley level.
     - Unbounded thread leakage: a new `SingleThreadExecutor` was created on every database open.
4. **Preservation of Core Invariants**:
   - Live altimeter calibration safeguards, geofence calibration dispatch gating, and $|dh/dt| \le 30\text{ m/s}$ physical climb limits are fully preserved.
   - Eliminating the automatic silent heuristic in `onOpen()` eliminates false-positive data loss and restores full data integrity to all mountain activities.

---

## 4. Test Specifications

### 4.1 TST-CON-008.6: High-Altitude Climb Negative Test & Zero DB Open Mutation
- **Test File**: `com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerSanitizationTest.kt`
- **Method**: `testDatabaseOpen_preservesLegitimateHighAltitudeClimbsWithoutModification()`
- **Procedure**:
  1. Set up a mock database containing a legitimate workout summary (ID 42) with:
     - Profile: `[600.0, 1200.0, 1800.0, 2400.0, 3000.0, 3600.0, 4200.0]`.
     - `TABLE_EXTREMA_VALUES`: MIN = 600.0 m, MAX = 4200.0 m.
  2. Invoke `onOpen(mockDb)` on `WorkoutSummariesDbHelper`.
  3. Verify that `mockDb.update(...)` is called **0 times** for `WorkoutSummaries.TABLE` and **0 times** for `TABLE_EXTREMA_VALUES`.
  4. Verify that `mockDb.execSQL(...)` is called **0 times** for `WorkoutSamples`.
  5. Assert that the elevation stream and extrema records remain exactly identical.

### 4.2 TST-CON-008.7: Database Open Thread Leak Elimination Test
- **Test File**: `com.atrainingtracker.trainingtracker.database.WorkoutSummariesDatabaseManagerSanitizationTest.kt`
- **Method**: `testOnOpen_doesNotSpawnUnmanagedExecutorOrLeakThreads()`
- **Procedure**:
  1. Inspect `WorkoutSummariesDbHelper.onOpen()` implementation.
  2. Invoke `onOpen(mockDb)` 25 times consecutively.
  3. Verify that zero asynchronous threads are queued or running on background executors.
  4. Confirm `onOpen()` completes purely synchronously with zero thread spawning.

---

## 5. Bidirectional Traceability Matrix

| Requirement ID | Technical Constraint | Functional Behavior | Test Case ID | Test Target | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `REQ-CON-017` | Zero DB Open Side-Effects | Passive Database Open | `TST-CON-008.6` | `WorkoutSummariesDatabaseManagerSanitizationTest.kt` | Specified |
| `REQ-CON-017` | Immutability of Mountain Climbs | Climb Data Preservation | `TST-CON-008.6` | `WorkoutSummariesDatabaseManagerSanitizationTest.kt` | Specified |
| `REQ-CON-017` | Thread Leak Elimination | Zero Thread Allocation | `TST-CON-008.7` | `WorkoutSummariesDatabaseManagerSanitizationTest.kt` | Specified |
| `REQ-CON-017` | Automated Silent Modification Prohibition | No Silent Rewrites | `TST-CON-008.6` | `WorkoutSummariesDatabaseManager.java` | Specified |
