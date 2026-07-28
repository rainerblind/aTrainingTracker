# Implementation Plan - ATT-440: Robust Map Zoom & Null Safety

Address the issue where the Period Details map fails to zoom into the training area by fixing incorrect null-handling in the database layers and refining the camera's initialization logic.

## User Review Required

> [!IMPORTANT]
> - **Accurate Map Focus**: I identified a technical "blind spot" where missing map data was being incorrectly interpreted as the coordinate `(0,0)` (the ocean). I am fixing this so the app correctly recognizes missing data and instead uses your actual workout paths to focus the map.
> - **Immediate Visibility**: This ensures that as soon as you open a period's map, it will be perfectly centered and zoomed on your training area.

## Proposed Changes

### 1. Data Layer: Precision Null-Handling
Fulfills REQ-DAT-001 (Refinement)

#### [MODIFY] [PeriodSummariesDatabaseManager.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/PeriodSummariesDatabaseManager.kt)
- **Refactor `mapCursorToPeriod`**:
    - Use `cursor.isNull()` checks for all spatial columns (`minLat`, `maxLat`, `minLng`, `maxLng`).
    - Explicitly return sentinel values (`90.0`, `-90.0`, etc.) when data is null, ensuring the UI can distinguish between "Zero" and "Missing."

#### [MODIFY] [WorkoutSummariesDatabaseManager.java](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/database/WorkoutSummariesDatabaseManager.java)
- **Refactor `getDouble`**:
    - Add `cursor.isNull()` check. If null, return a literal `null` instead of the primitive `0.0`.
    - This fixes coordinate lookups across the entire application.

### 2. UI Layer: Reliable Camera Initialization
Fulfills REQ-MAP-004 (Refinement)

#### [MODIFY] [MapBehaviors.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapBehaviors.kt)
- **Refine `MapBoundsController`**:
    - Key the `hasFittedInitialBounds` state by both `zoomFocus` AND `initialBounds`.
    - **Rationale**: If the bounds are initially missing (null) but then arrive via the reactive enrichment pipeline, the camera must "try again" to fit the new valid bounds.

#### [MODIFY] [InteractivePeriodMap.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/periodlist/InteractivePeriodMap.kt)
- **Refine `periodBounds`**:
    - Add a safety check: `if (summary.minLat < 90.0 && summary.minLat != 0.0)`.
    - This prevents the "Ocean Trap" by ignoring invalid zero-coordinate bounds.

## Verification Plan

### Manual Verification
1. Open the 'Periods' screen.
2. Tap the map icon for a period recorded at a known location (e.g., your home area).
3. **Verify** that the map zooms into the correct area immediately upon opening.
4. **Verify** that the map does not show a world view or center on the Atlantic Ocean.
