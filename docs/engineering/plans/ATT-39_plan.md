# Implementation Plan - ATT-39: Automated Altitude Reference Discovery

Maintain a background-updated database of known locations and their average altitudes to automate barometric sensor initialization.

## User Review Required

> [!IMPORTANT]
> - **Background Automation**: The app will now automatically learn the altitude of your frequent start and end points. You no longer need to manually manage the "Known Locations" database.
> - **Precision Refinement**: Every time you start or end a workout, the app will update the corresponding location's altitude using a weighted average. Over time, this provides a highly stable reference value, filtering out GPS jitters.
> - **Database Upgrade**: The `StartLocation2Altitude.db` will be upgraded to Version 4 to support this background learning.

## Proposed Changes

### 1. Database Layer: Weighted Learning Logic
Fulfills REQ-DAT-007 | Test: TST-DAT-001

#### [MODIFY] [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)
- **Schema Evolution**: Upgrade `KnownLocationsDbHelper` to **Version 4**.
- **New Column**: Add `HIT_COUNT` (Integer, default 0).
- **Refine `MyLocation`**: Change `altitude` from `int` to `double` for precision during averaging.
- **Implement `learnLocation(LatLng pos, double altitude, ExtremaType type)`**:
    - **Search**: Find an existing location within `DEFAULT_RADIUS` (200m).
    - **Update**: If found, calculate the new weighted mean altitude: `(oldAlt * hitCount + newAlt) / (hitCount + 1)` and increment `hitCount`.
    - **Insert**: If not found, create a new entry named "Auto-learned [Type]" (e.g. Start/End) with `hitCount = 1`.

### 2. Repository Layer: Background Hook
Fulfills REQ-DAT-007 | Test: TST-DAT-001

#### [MODIFY] [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)
- **Integration**: In `reloadWorkoutData`, after a workout is marked as `finished`:
    - Fetch the calculated Start and End altitudes from the summary table.
    - Call `KnownLocationsDatabaseManager.learnLocation()` for both the `startLatLng` and `endLatLng`.
- **Rationale**: This ensures that every successfully completed session contributes its spatial knowledge to the global reference database.

## Verification Plan

### Manual Verification (TST-DAT-001)
1. Record or import a workout at a known location.
2. Verify in Database Inspector that an entry in `StartLocation2Altitude.db` is created/updated with `hitCount = 1`.
3. Record another workout at the same location with a slightly different altitude.
4. **Verify** that the database altitude is the mean of the two recordings and `hitCount = 2`.
