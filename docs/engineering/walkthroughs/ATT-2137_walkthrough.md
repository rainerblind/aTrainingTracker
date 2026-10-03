# Stage 5: Verification & Walkthrough - ATT-2137: Strava Activity Data Not Displayed in Workout Details

**Ticket**: [ATT-2137](https://rainerblind.atlassian.net/browse/ATT-2137)  
**Sub-task**: [ATT-2153](https://rainerblind.atlassian.net/browse/ATT-2153) (`[Test]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2137`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Overview & Problem Solved

In the workout details view (`TrackOnMapScreen` / `MapDetailLayout`), Strava activity data (PR badges, best efforts, segment achievements) was previously placed at the very bottom of `analyticsContent` in `lowerColumn`. This buried Strava results under all elevation charts, scrubbing controls, speed/cadence/power/HR telemetry graphs, zone cards, and lap split cards, violating the canonical sequence order established in `WorkoutSummary.kt`.

Under `REQ-UI-248` / `ATT-2137`:
* `StravaActivitySection` was relocated into the upper `metadataContent` slot of `MapDetailLayout`, positioned immediately following `WorkoutExtrema` and statically above the map container.
* `StravaActivitySection` was cleanly excised from `analyticsContent`, completely eliminating duplicate rendering below the charts.
* Architectural contract test `TrackOnMapScreenMetadataRoutingContractTest` was implemented and verified.

---

## 2. Key Code Changes

### [TrackOnMapScreen.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt)
* Relocated `StravaActivitySection` into `metadataContent` immediately following `WorkoutExtrema`:
  ```kotlin
  if (activeDetailPrefs.showStrava && !workoutData.stravaActivityData.isNullOrBlank()) {
      StravaActivitySection(
          rawActivityJson = workoutData.stravaActivityData,
          modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 4.dp)
      )
  }
  ```
* Removed `StravaActivitySection` from `analyticsContent`.

### [TrackOnMapScreenMetadataRoutingContractTest.kt](file:///home/rainer/AndroidStudioProjects/aTrainingTracker/app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapScreenMetadataRoutingContractTest.kt)
* Added architectural contract tests:
  - `testTrackOnMapScreen_routesStravaActivitySectionInMetadataContentAfterExtrema`
  - `testTrackOnMapScreen_omitsStravaActivitySectionFromAnalyticsContent`

---

## 3. Verification Evidence

### 3.1 Targeted Contract Test Run
```bash
./gradlew testDebugUnitTest --tests "*TrackOnMapScreenMetadataRoutingContractTest*" --tests "*MapDetailLayoutMetadataSlotContractTest*"
```
* **Result**: `BUILD SUCCESSFUL in 19s` (32 actionable tasks, 100% pass rate).

### 3.2 Full Clean-Room Suite Regression
```bash
./gradlew testDebugUnitTest
```
* **Result**: Passed with zero regressions.

---

## 4. Physical Device Checklist for Pixel 10
* [x] Open a workout with Strava results and verify that the Strava card appears directly above the map under the extrema card.
* [x] Toggle "Strava" off in Expert Settings Section 5 ("In Details" column) and confirm the card disappears.
* [x] Scroll through the lower graphs and zone cards to verify that Strava is not duplicated at the bottom.
