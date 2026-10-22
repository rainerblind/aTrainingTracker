# Stage 1: Problem Domain & Root Cause Analysis: ATT-3990

**Ticket**: [ATT-3990](https://atrainingtracker.atlassian.net/browse/ATT-3990)  
**Parent Epic**: None  
**Target Release**: `Next Minor Release` (V4.9.40)  
**Active Sprint**: `2026-42.10`  
**Requirement Mapping**: `REQ-CON-017` (*Barometric Altimeter Idempotent Calibration & Historical Altitude Sanitization*)  
**Branch**: `feature/ATT-3990`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-19  

---

## 1. Executive Summary & Problem Formulation

During the independent code review of `feature/ATT-3921` (October 2026), a critical data loss bug was discovered in `WorkoutSummariesDatabaseManager.java`:
1. **Unconditional Destruction of Legitimate High-Altitude Workouts**:
   On every database `onOpen()`, `WorkoutSummariesDatabaseManager.sanitizeCorruptedAltitudeWorkouts()` executes an asynchronous heuristic targeting any workout where `max_altitude > 4000.0 m` and `min_altitude < 1000.0 m`.
   When matched, the method:
   - Identifies the workout as "corrupted".
   - Rewrites the `ALTITUDE_STREAM` by replacing **every single point above 2000.0 m** with the valley baseline (`validBaseline` $\approx 500.0\text{ m}$).
   - Recomputes and updates `TABLE_EXTREMA_VALUES` (overwriting the genuine maximum from e.g. 4200 m down to $\le 2000\text{ m}$).
   - Executes raw SQL against the workout's `WorkoutSamples` database table:
     `UPDATE samplesTable SET ALTITUDE = validBaseline WHERE ALTITUDE > 2000.0`.
   As a result, legitimate mountain workouts (such as cycling or mountaineering ascents from a valley at 800 m to an alpine summit or mountain pass at 4100 m, e.g. Mauna Kea, Pikes Peak, Colorado 14ers, or Andean passes) permanently and irreversibly have their entire upper half truncated and flattened to a baseline level.
2. **Thread Leak per Database Open**:
   In `WorkoutSummariesDbHelper.onOpen()`, `Executors.newSingleThreadExecutor().execute(...)` creates an unmanaged, unbounded single-thread executor on every single database open event without ever calling `shutdown()`. This leaks threads on recurring database opens.

---

## 2. Forensic Reproduction & Root Cause Analysis

### 2.1 Code Inspection

In `WorkoutSummariesDatabaseManager.java:1690-1700`:
```java
@Override
public void onOpen(@NonNull SQLiteDatabase db) {
    super.onOpen(db);
    // Asynchronously sanitize any historical workouts corrupted with runaway altitude shifts (REQ-CON-017)
    Executors.newSingleThreadExecutor().execute(() -> {
        try {
            WorkoutSummariesDatabaseManager.getInstance(mContext).sanitizeCorruptedAltitudeWorkouts();
        } catch (Exception e) {
            Log.w(TAG, "Error during automatic altitude sanitization: " + e.getMessage());
        }
    });
}
```

In `WorkoutSummariesDatabaseManager.java:1175-1260`:
```java
// 1. Identify corrupted workout IDs
String query = "SELECT max_t." + WorkoutSummaries.WORKOUT_ID + " FROM " + WorkoutSummaries.TABLE_EXTREMA_VALUES + " max_t "
        + " JOIN " + WorkoutSummaries.TABLE_EXTREMA_VALUES + " min_t ON max_t." + WorkoutSummaries.WORKOUT_ID + " = min_t." + WorkoutSummaries.WORKOUT_ID
        + " WHERE max_t." + WorkoutSummaries.SENSOR_TYPE + " = 'ALTITUDE' AND max_t." + WorkoutSummaries.EXTREMA_TYPE + " = 'MAX' AND max_t." + WorkoutSummaries.VALUE + " > 4000.0 "
        + " AND min_t." + WorkoutSummaries.SENSOR_TYPE + " = 'ALTITUDE' AND min_t." + WorkoutSummaries.EXTREMA_TYPE + " = 'MIN' AND min_t." + WorkoutSummaries.VALUE + " < 1000.0";
...
for (double alt : alts) {
    double sanitized = (alt > 2000.0) ? validBaseline : alt;
    sanitizedAlts.add(sanitized);
    ...
}
...
samplesDb.execSQL("UPDATE " + samplesTable + " SET " + SensorType.ALTITUDE.name()
        + " = " + validBaseline + " WHERE " + SensorType.ALTITUDE.name() + " > 2000.0");
```

### 2.2 Proof of Failure Scenario
Consider an athlete recording a genuine mountain workout:
- Start location: Base valley at 600 m elevation (`min = 600.0 m < 1000.0 m`).
- Climb: Continuous ascent up to a mountain summit at 4200 m elevation (`max = 4200.0 m > 4000.0 m`).
- Profile: `[600, 1200, 1800, 2400, 3000, 3600, 4200]`.
1. The SQL query matches this workout: `max > 4000.0` and `min < 1000.0`.
2. `validBaseline` is computed as `600.0 m`.
3. Points above 2000 m (`2400, 3000, 3600, 4200`) are replaced with `600.0 m`.
4. Resulting profile: `[600, 1200, 1800, 600, 600, 600, 600]`.
5. Max altitude in extrema table is rewritten from 4200 m to 1800 m.
6. The raw SQLite samples table `WorkoutSamples` has all points $> 2000\text{ m}$ overwritten with `600.0 m`.
7. The genuine activity profile is destroyed permanently without user prompt or recourse.

---

## 3. Chesterton's Fence Archaeology (`REQ-CON-017` / `ATT-1810`)

1. **Why was this code added?**
   Under `ATT-1810` / `REQ-CON-017`, a bug in `AltitudeFromPressureDevice.java` caused barometric calibration offsets to oscillate. Before departing on a ride, ~30 oscillations accumulated $+1710\text{ m}$ to $+7300\text{ m}$ on the initial samples recorded at home (e.g. "Kurz zum Bäcker #14"). Once the athlete rode away from the geofence, the shifts stopped, creating a sharp descending staircase from 7300 m down to 507 m.
2. **Why is the existing mechanism flawed?**
   - The sanitizer was intended as a **one-time repair** for historical database records affected by the ATT-1810 bug.
   - Instead of running once under a migration guard or one-time flag, it was placed inside `WorkoutSummariesDbHelper.onOpen()`, running indiscriminately on every DB open forever.
   - The condition (`max > 4000 && min < 1000`) conflated genuine mountain climbs with the artificial 7300 m staircase.
   - The sanitization rule (`alt > 2000 -> baseline`) completely flattens any elevation above 2000 m.
3. **Preservation of Original Intent without Destructive Side Effects**:
   - The original ATT-1810 bug has been fixed in `AltitudeFromPressureDevice` and `TrackingTabsViewModel`. No new staircase workouts can be produced by tracking.
   - Automated, silent background modification of historical database records is fundamentally dangerous and violates core data integrity invariants.
   - Legitimate high-altitude mountain workouts must never be altered or flattened.

---

## 4. Remediation Design & Architectural Guardrails (Addressing Gate 1 Feedback)

In response to the Gate 1 audit findings and strict safety standards, the remediation design rejects automated, silent heuristic mutations entirely.

### 4.1 Prohibition of Automated, Silent Data Modification
- **No Silent Heuristic Repair**:
  - As highlighted by the external auditor, complex heuristic "auto-repairs" introduce unacceptable risk of false positives (e.g. high-speed descents in cycling, skiing, or paragliding).
  - Therefore, the app will **completely cease all automated, silent background modification** of user workout records.
- **Excise Sanitizer Call from `onOpen()`**:
  - Remove `sanitizeCorruptedAltitudeWorkouts()` entirely from `WorkoutSummariesDbHelper.onOpen()`.
  - Database open events will perform **zero** modifications to existing user data, satisfying Acceptance Criterion 1 with 100% mathematical certainty.

### 4.2 Complete Elimination of the Thread Leak
- **Remove Executor**:
  - Delete `Executors.newSingleThreadExecutor()` entirely from `WorkoutSummariesDbHelper.onOpen()`.
  - `WorkoutSummariesDbHelper.onOpen()` will strictly call `super.onOpen(db)` and nothing else.
  - Zero background threads will be created during database opening, satisfying Acceptance Criterion 2.

### 4.3 Policy on Historical Flattened Workouts & Data Recovery
- **Damage Assessment**:
  - For workouts that were unfortunately modified by the previous buggy sanitizer in past app releases: because the raw points were overwritten in-place in both `ALTITUDE_STREAM` and `WorkoutSamples` without backup columns, mathematical in-place recovery of the lost mountain data is impossible from SQLite alone.
- **Recovery Strategy**:
  - Recovery requires restoring from an existing uncorrupted `.attbackup` archive or re-importing the original activity GPX/TCX file.
  - This limitation will be documented in the release notes.

### 4.4 Non-Destructive User-in-the-Loop Integrity & Audit Trail (If Repair Invoked)
- If the sanitization routine `sanitizeCorruptedAltitudeWorkouts()` is retained as an explicit, user-initiated utility or developer tool:
  1. It MUST NOT run automatically on database open, fresh install, or app upgrade.
  2. It MUST only run with explicit user-in-the-loop confirmation.
  3. It MUST be non-destructive: prior to altering any record, an audit trail or backup snapshot of the original streams must be recorded, preventing irreversible data loss.

---

## 5. Scope Bounding & Non-Goals

- **In Scope**:
  - `WorkoutSummariesDatabaseManager.java`:
    - Completely remove `Executors.newSingleThreadExecutor()` and the `sanitizeCorruptedAltitudeWorkouts()` call from `WorkoutSummariesDbHelper.onOpen()`.
    - Mark `sanitizeCorruptedAltitudeWorkouts()` as deprecated / obsolete or decouple it so that no automatic callers invoke it.
  - Unit test verification in `WorkoutSummariesDatabaseManagerSanitizationTest.kt`:
    - **Negative Test (Core Invariant)**: Prove that opening the database or running queries on a genuine mountain ascent from 0m to 4200m (e.g. `[600, 1200, 1800, 2400, 3000, 3600, 4200]`) leaves the altitude profile 100% untouched (0 modifications, original values intact).
    - **Thread Leak Test**: Verify that opening the database repeatedly leaks 0 threads.
- **Out of Scope**:
  - Automatic background alteration of user workouts.
  - Modifying live barometric pressure recording in `AltitudeFromPressureDevice.java`.
  - Database schema table version bumps.

---

## 6. Living Documentation & Traceability Plan

- Update `REQ-CON-017` in `docs/requirements.md` to formally state:
  - Automated, silent background modification of historical altitude data is strictly PROHIBITED.
  - Database open hooks (`onOpen`) SHALL NOT trigger asynchronous mutations or spawn background threads.
  - Legitimate high-altitude workouts SHALL NEVER be truncated, clamped, or overwritten.
- Update `docs/tests.md` with:
  - `TST-CON-008.6`: Negative test verifying legitimate mountain ascent (0 to 4200m) is untouched.
  - `TST-CON-008.7`: Thread safety test verifying zero thread leaks on repeated database opens.
- Author Stage 2 Test Specification (`ATT-3990_test_spec.md`) and Implementation Plan (`ATT-3990_plan.md`).
