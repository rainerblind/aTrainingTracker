# Walkthrough - SCRUM-97: Live Total Time Updates during Pause

Implemented live temporal updates for the "Total Duration" of active workout sessions while in a paused state.

## 1. Requirements Fulfilled
- **REQ-TRK-009**: `TrackerService` now updates `TIME_TOTAL_s` in the database even during pause.
- **REQ-UI-038**: The user interface reflects these updates in real-time by observing the database change.

## 2. Verification Results
- **TST-REG-007**: **PASS**
    - Verified that during a pause, the "Total Time" field continues to increment every second in the database and is reflected in the UI (Workout List).
    - Verified that "Active Time" remains static during the pause.

## 3. Technical Changes
- **Service Logic**: Updated `TrackerService.sampleAndWriteToDb()` to perform a surgical database update on the `WorkoutSummaries` table when `isPaused()` is true.
- **Concurrency**: Offloaded the surgical DB write to the existing `mDbExecutor` thread pool to ensure main-thread performance is not affected.
- **Notification**: Maintained the existing broadcast mechanism (`WORKOUT_UPDATED_INTENT`) to trigger UI refreshes during the paused state.
