# Walkthrough - ATT-38: Atomic Altitude Correction

Successfully implemented an atomic shift for all altitude-related data structures when a barometric correction is triggered mid-workout. This ensures 100% data consistency between raw logs, summary metrics, and visual graphs.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-FIL-004** | The system SHALL apply altitude corrections from known locations atomically across all data structures. | Ensure consistent and accurate vertical data across all session representations, preventing vertical jumps in graphs and discrepancies in summaries. |

## Changes Made

### 📐 Synchronized Data Shifting

#### [LiveWorkoutSession.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)
- **In-Memory Shift**: Implemented `applyAltitudeCorrection(double offset)` to immediately adjust the running statistics (Min, Max, Avg) and all previously sampled points in the active session. This ensures that the final summary generated at the end of the workout is already corrected.

#### [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- **Atomic Database Refinement**: Added `shiftAltitudeData(long workoutId, double offset)` to surgically update the `ExtremumValues` table and the encoded `altitudeStream` within a single transaction. This prevents visual artifacts (vertical jumps) in the elevation profile graphs after a correction.

#### [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- **Unified Correction Handler**: Refactored the `mAltitudeCorrectionReceiver` to coordinate the full shift sequence across the live session and the multiple database tables, ensuring total atomicity.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-FIL-003 (Atomic Altitude Correction)
- **Result**: **PASS**. 
    - Verified that triggering a correction mid-workout correctly shifts all previous sample points in the raw database.
    - Verified that the summary Min/Max altitude values are accurately adjusted.
    - Verified that the elevation profile graph remains a continuous, corrected line with no vertical discontinuities.

> [!TIP]
> This improvement eliminates the technical debt of "drifted" summaries, ensuring that your training data remains precise and visually professional regardless of when the barometric baseline is established.
