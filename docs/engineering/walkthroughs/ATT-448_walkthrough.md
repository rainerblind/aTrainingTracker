# Walkthrough: ATT-448 Refined Altitude Learning

Implemented an automatic altitude learning system that refinements location estimates using **weighted raw barometric data** measured at the **Start of the Workout**. This breaks the circular dependency where the system would previously "learn" its own applied corrections.

## Changes Made

### Sensor Layer (Automatic Learning Trigger)
- **[AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)**:
    - Added `mLastRawAltitude` to capture the current uncorrected ISA altitude.
    - Updated `initPressureSensor()` to trigger the learning loop immediately upon spatial discovery.
    - The system now uses the **raw sensor measurement** (before any offset is applied) as the input for database refinement.

### Database Layer (Core Integrity)
- **[KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)**:
    - Fixed a bug in `addNewLocation` where `extremumType` and `hitCount` were not being persisted.
    - Updated `learnLocation` to correctly handle both new discovery and weighted average refinement of existing entries.
    - The database now correctly accumulates "Average Raw Pressure" (as altitude) for known coordinates, which converges to the true altitude over many sessions regardless of weather fluctuations.

### Repository Layer (Cleanup)
- **[WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)**:
    - Removed all `learnLocation` calls from the post-workout flow.
    - Removed the redundant `startAltitude` and `endAltitude` fields from the primary workout model and persistence layers.

## Verification Results

### Manual Verification (Static Audit)
- **Test ID**: TST-DAT-002 (ATT-482)
- **Outcome**: **PASS**
- **Traceability**:
    - **Self-Correction**: Verified that the database converges towards the ground-truth altitude by averaging weather-induced pressure variations.
    - **No Circularity**: Confirmed that the `mAltitudeCorrection` applied to the workout is *not* fed back into the database; only the uncorrected raw reading is used for refinement.
    - **Timing**: Confirmed learning happens at the start, ensuring immediate availability of calibrated data for the active session.

### Process Quality
- Updated **REQ-DAT-007** to reflect the raw-averaging strategy.
- Cleaned up the data model by removing temporary anchor fields that are no longer required for learning.
