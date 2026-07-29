# Walkthrough - ATT-39: Automated Altitude Reference Discovery

Successfully implemented a background learning engine that automatically discovers and refines altitude reference points based on workout history. This eliminates the need for manual user maintenance of the "Known Locations" database.

## Fulfilled Requirements

| ID | Description | Rationale |
|:---|:---|:---|
| **REQ-DAT-007** | The system SHALL automatically maintain a database of known locations and their average altitudes based on workout start and end points. | Provide high-precision reference values for barometric sensor initialization without manual user effort. |

## Changes Made

### 🚀 Automated Learning Engine

#### [KnownLocationsDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/KnownLocationsDatabaseManager.java)
- **Schema Evolution (v4)**: Added a `hitCount` column to the `StartLocation2Altitude` table to track the number of recordings for each location.
- **Weighted Averaging**: Implemented `learnLocation(LatLng pos, double altitude, ExtremaType type)` which uses a weighted moving average to refine altitude estimates: `(oldAlt * hitCount + newAlt) / (hitCount + 1)`.
- **Automatic Discovery**: The system now automatically creates new location entries (named "Auto-learned start/end") if no existing location is found within a 200m radius.

### 🏗️ Workflow Integration

#### [WorkoutRepository.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/WorkoutRepository.kt)
- **Background Hook**: Integrated the learning logic into the `reloadWorkoutData` flow. Every time a workout is finished (either via live tracking or import), its start and end locations are automatically processed and recorded in the reference database.

#### [AltitudeFromPressureDevice.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/banalservice/devices/AltitudeFromPressureDevice.java)
- **Precision Alignment**: Updated the altitude correction logic to use `double` precision, ensuring full compatibility with the new refined reference values.

## Verification Results

### Integration Verification (SWE.5)
- **Test ID**: TST-DAT-001 (Automated Altitude Discovery)
- **Result**: **PASS**. 
    - Verified that importing a workout creates a new entry in `StartLocation2Altitude.db` with `hitCount = 1`.
    - Verified that a second workout at the same location correctly updates the altitude to the mean of the two values and increments `hitCount` to 2.

> [!TIP]
> This "Zero-Touch" automation ensures that your training cockpit always has the most accurate barometric baseline, improving elevation gain precision every time you record a session.
