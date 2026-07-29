# Implementation Plan - ATT-38: Atomic Altitude Correction

Ensure that altitude corrections derived from known start locations are applied consistently across all data structures, including raw samples, summary extrema, and encoded elevation streams.

## User Review Required

> [!IMPORTANT]
> - **Total Consistency**: This change ensures that if your barometric altitude is corrected a few minutes into a workout, your **Elevation Profile graph** and **Min/Max Altitude summary** will be automatically adjusted to match the corrected values.
> - **Accurate Analytics**: It prevents inconsistencies where the "Raw Logs" show one altitude range while the "Workout Summary" shows another.

## Proposed Changes

### 1. Database Layer: Summary Synchronization
Fulfills REQ-FIL-004 | Test: TST-FIL-003

#### [MODIFY] [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- **Extrema Shift**: Add logic to shift existing Altitude records in `ExtremumValues` table by the offset.
- **Stream Reconstruction**: Add logic to decode the `altitudeStream`, add the offset to all points, re-encode, and save back to the summary.

### 2. Live Session: Real-Time Adjustment
Fulfills REQ-FIL-004 | Test: TST-FIL-003

#### [MODIFY] [LiveWorkoutSession.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)
- **State Shift**: Add `applyAltitudeCorrection(double offset)` method:
    - Adjust `min`, `max`, and `sum` in the Altitude `RunningStats`.
    - Shift all values in the `sampledAltitudes` list.
    - Update `lastAltE2` to ensure the next delta-encoded point is correct.

### 3. Orchestration: Unified Intent Handler
Fulfills REQ-FIL-004 | Test: TST-FIL-003

#### [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- Refactor `mAltitudeCorrectionReceiver`:
    - Call the new DB sync methods (Samples + Extrema + Streams).
    - Call the LiveSession sync method.
    - Broadcast `WORKOUT_UPDATED_INTENT` to notify the UI and Repository.

## Verification Plan

### Manual Verification (TST-FIL-003)
1. Start a workout. Record data for 30 seconds.
2. Trigger an altitude correction (e.g. by nearing a known location or via debugger).
3. Finish the workout.
4. **Verify** in Workout History that the Elevation Profile graph is a continuous line (no vertical jump at the correction point).
5. **Verify** that Min/Max altitudes in the summary table are correct.
