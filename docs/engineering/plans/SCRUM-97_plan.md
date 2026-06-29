# Implementation Plan - SCRUM-97: Live Total Time Updates during Pause

Ensure that the total duration of a workout continues to update in the UI and database even when the session is paused.

## 1. Requirements Mapping
- **Requirement**: `REQ-TRK-009` (Total Time Persistence)
- **Requirement**: `REQ-UI-038` (Live Pause Updates)
- **Test ID**: `TST-REG-007` (Pause Time Sync)

## 2. Impact Analysis
- **Service**: `TrackerService.java`.
- **Database**: `WorkoutSummaries` table (`TIME_TOTAL_s` column).
- **Communication**: `WORKOUT_UPDATED_INTENT` broadcast.
- **Side Effects**: None. Writing a single integer to the database once per second during pause is computationally negligible.

## 3. Proposed Changes

### 3.1 Refine Sampling Logic (`TrackerService.java`)
- Modify `sampleAndWriteToDb()` to perform a partial update when paused:
    1. Always fetch `TIME_TOTAL` from `mBanalService`.
    2. If `TrainingApplication.isPaused()`:
        - Populate a dedicated `ContentValues` with only `TIME_TOTAL_s`.
        - Submit a surgical update to the `WorkoutSummaries` table via the `mDbExecutor`.
        - Broadcast `WORKOUT_UPDATED_INTENT` to trigger UI refresh.
        - Return (do not write to samples table or update other metrics).
    3. If NOT paused, continue with the full sampling logic as before.

## 4. Verification Plan
- **Build**: Ensure successful compilation.
- **Functional Audit**:
    1. Start a workout.
    2. Wait for 5 seconds.
    3. Pause the workout.
    4. Observe the workout card in the "Workouts" tab.
    5. Verify the "Total Time" (secondary value) continues to increment every second while "Active Time" is frozen.
    6. Resume and verify both increment correctly.
