# Implementation Plan: ATT-448 Precise Altitude Learning

This plan addresses the "not plausible" auto-learned altitudes by ensuring that the system uses explicit start and end altitude samples instead of the session minimum.

## User Review Required

> [!IMPORTANT]
> This change introduces new persisted metadata (`START` and `END` altitudes) in the `ExtremumValues` table for the `ALTITUDE` sensor. Old workouts will not have this data until they are re-imported or manually edited, but the system will gracefully fall back to the existing logic for them.

## Proposed Changes

### 1. Sensor Data Capture (Service Layer)

#### [MODIFY] [LiveWorkoutSession.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)
- Add `private Double startAltitude = null` and `private Double lastAltitude = null`.
- Update `addSample` to capture the first altitude sample as `startAltitude` and every sample as `lastAltitude` (when type is `ALTITUDE`).
- Add getters for these fields.
- Update `applyAltitudeCorrection` to shift these values when a barometric correction occurs.

#### [MODIFY] [TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)
- In `finalizeLiveSession`, save the `START` and `END` altitudes to the `ExtremumValues` table using `summariesManager.updateExtremaValue`.

### 2. Data Model & Mapping (UI/Data Layer)

#### [MODIFY] [WorkoutData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt)
- Add `val startAltitude: Double?` and `val endAltitude: Double?` to the `WorkoutData` data class.

#### [MODIFY] [WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt)
- Update `fromCursor` (both standard and batch) to fetch the `START` and `END` altitudes from the `ExtremumValues` table or batch metadata.

### 3. Learning Logic (Repository Layer)

#### [MODIFY] [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)
- Update `updateExtremaValue` to support `ExtremaType.START` and `ExtremaType.END` for memory updates.
- In `reloadWorkoutData` and `saveWorkout`, call `learnLocation` using `freshWorkoutData.startAltitude` and `freshWorkoutData.endAltitude` instead of `minAltitude`.
- Add a fallback to `minAltitude` if the explicit fields are null (for legacy data).

### 4. Database Integrity (Core Layer)

#### [MODIFY] [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)
- Fix `addNewLocation` to include `KnownLocationsDbHelper.EXTREMA_TYPE` and `KnownLocationsDbHelper.HIT_COUNT` (set to 1) in the `ContentValues`.
- Fix `learnLocation` to correctly pass the `ExtremaType` string to `addNewLocation` (will require adding a parameter to `addNewLocation`).

## Verification Plan

### Automated Tests
- N/A (Logic involves complex Service/DB interactions best verified via manual system tests).

### Manual Verification
- **Test ID**: TST-DAT-002 (ATT-482)
- **Procedure**: 
  1. Start a workout at a known location (e.g., Home) with a specific altitude.
  2. Descend to a lower altitude.
  3. Return to another known location (e.g., Office).
  4. Stop the workout.
  5. Inspect the `KnownLocations.db` using Database Inspector.
- **Expected Result**: 
  - The 'Home' entry in `KnownLocations.db` should have an altitude matching the start sample, not the session minimum.
  - The 'Office' entry should have an altitude matching the end sample.
