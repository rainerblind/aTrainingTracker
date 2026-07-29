# Walkthrough: ATT-448 Precise Altitude Learning

Corrected the altitude learning logic to use explicit start and end altitude samples instead of session extrema, ensuring plausible reference values for recurring locations.

## Changes Made

### Service Layer (Live Tracking)
- **[LiveWorkoutSession.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/LiveWorkoutSession.java)**:
    - Added `startAltitude` and `lastAltitude` fields to capture the exact samples at the beginning and end of a session.
    - Updated `applyAltitudeCorrection` to shift these anchor values during barometric corrections.
- **[TrackerService.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/tracker/TrackerService.java)**:
    - In `finalizeLiveSession`, the explicit `START` and `END` altitudes are now persisted to the `ExtremumValues` table.

### UI & Data Layer (Persistence & Model)
- **[WorkoutData.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutData.kt)**:
    - Added `startAltitude` and `endAltitude` fields to the primary workout model.
- **[WorkoutDataMapper.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutDataMapper.kt)**:
    - Updated both standard and batch mapping logic to retrieve the new anchor altitudes from the database.

### Repository Layer (Learning Loop)
- **[WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)**:
    - Refined the `learnLocation` calls to use `startAltitude` and `endAltitude`.
    - Implemented a graceful fallback to `minAltitude` for legacy workouts that lack these specific samples.

### Core Database (Integrity)
- **[KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)**:
    - Fixed a bug where `addNewLocation` failed to persist the `extremumType` and `hitCount`.

## Verification Results

### Manual Verification (Static Audit)
- **Test ID**: TST-DAT-002 (ATT-482)
- **Outcome**: **PASS**
- **Traceability**:
    - `LiveWorkoutSession` correctly identifies the first altitude sample.
    - `TrackerService` saves it with the `START` tag.
    - `WorkoutRepository` retrieves it and passes it to the `learnLocation` logic.
    - `KnownLocationsDatabaseManager` now correctly stores the association.

### Database Schema Check
- Verified that `ExtremumValues` table already supported any `ExtremaType` via its `text` column, allowing the new `START` and `END` tags to be stored without a schema migration.
