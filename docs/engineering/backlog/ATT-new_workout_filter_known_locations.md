# [Verbesserung] [Workout-Filter] Auswahl von Lieblingsorten im Workout-Filter-Dialog

## Problem Statement & Value Proposition
Currently, filtering workouts by starting location (*Lieblingsort*) is only accessible via the 1-tap starts badge on individual cards within the **Lieblingsorte** screen (`KnownLocationsScreen`). 
When users are already viewing their workout history in the **Workouts tab** (`WorkoutTabsScreen`) and open the comprehensive filter dialog (`WorkoutFilterBottomSheet`), they cannot directly select a favorite location as a filter criterion alongside time periods, sports, equipment, and distance/duration constraints.

## User Story
* **As an**: athlete analyzing past workouts
* **I want**: a dedicated "Lieblingsorte" section at the very bottom of the Workout Filter popup dialog
* **So that**: I can directly filter my workout history by one of my favorite starting locations without having to leave the workout list and navigate through the Lieblingsorte screen.

## Scope & Technical Architecture
* **Component**: [WorkoutFilterBottomSheet.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutFilterBottomSheet.kt)
* **Data Flow**:
  * Pass `knownLocations: List<KnownLocationItem>` into `WorkoutTabsScreen` and `WorkoutFilterBottomSheet` via [WorkoutSummariesViewModel.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/workoutlist/WorkoutSummariesViewModel.kt) (`KnownLocationsRepository.getInstance(application).locationsFlow`).
  * Add a section at the very bottom of the filter sheet beneath Duration Interval:
    * Title: `@string/known_locations_title` ("Lieblingsorte" / "Favorite Locations").
    * Horizontal/wrapping `FlowRow` of `FilterChip` items for all available favorite locations.
    * Toggling a chip selects/deselects the favorite location, populating `startLocationName`, `startLocationLat`, `startLocationLng`, and `startLocationRadiusM`.
  * Update `onClearAll` and `onApply` in `WorkoutFilterBottomSheet` to reset or apply the spatial location fields.
* **Compatibility**:
  * Reuse existing `WorkoutFilterCriteria` spatial properties and `ActiveFilterChipsRow` display logic without schema migrations.

## Acceptance Criteria (Given-When-Then)
* **Criterion 1 (Display in Filter Dialog)**:
  * *Given* the athlete has one or more recorded favorite locations in `KnownLocationsDatabaseManager`
  * *When* the athlete taps the filter button on the Workouts tab to open `WorkoutFilterBottomSheet`
  * *Then* a "Lieblingsorte" section is visible at the very bottom with filter chips for each location.
* **Criterion 2 (Selection & Filtering)**:
  * *Given* the athlete opens `WorkoutFilterBottomSheet`
  * *When* the athlete selects a Lieblingsort chip (e.g. "Zuhause") and taps "Filter anwenden"
  * *Then* the workout list displays only workouts that started within that location's geofence radius, and the `ActiveFilterChipsRow` displays the `📍 Zuhause` chip.
* **Criterion 3 (Reset / Deselection)**:
  * *Given* a Lieblingsort filter chip is active in `WorkoutFilterBottomSheet`
  * *When* the athlete taps "Alle zurücksetzen" or deselects the chip
  * *Then* the spatial filter criteria are cleared.

## Parent Epic & Sprint Placement
* **Target Epic**: `ATT-1396` (Lieblingsorte & Lieblingsstrecken)
* **Target Sprint**: Next Sprint (slated for Sprint-Start Screening)
