# Stage 3: Implementation Plan - ATT-2137: Strava Activity Data Not Displayed in Workout Details

**Ticket**: [ATT-2137](https://rainerblind.atlassian.net/browse/ATT-2137)  
**Sub-task**: [ATT-2151](https://rainerblind.atlassian.net/browse/ATT-2151) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Aftermath: Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Branch**: `feature/ATT-2137`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Architectural Design & SWE.2 Structure

To satisfy `REQ-UI-248` and guarantee structural sequence parity with `WorkoutSummary.kt`, `TrackOnMapScreen.kt` will be updated to route `StravaActivitySection` into the upper `metadataContent` slot rather than the bottom of `analyticsContent`.

```
                  TrackOnMapScreen
                         │
                         ├──► metadataContent (slotted above map)
                         │       ├── WorkoutDescription (if showDescription)
                         │       ├── WorkoutExtrema (if showExtrema)
                         │       └── StravaActivitySection (if showStrava && !stravaData.isNullOrBlank())
                         │
                         ├──► mapBox (BoxWithConstraints - Resizable Viewport)
                         │
                         └──► lowerColumn (scrollable lower split)
                                 ├── ElevationProfile (if showElevationProfile)
                                 ├── TelemetryMetricGraphs (Speed, HR, Power, Cadence)
                                 └── analyticsContent (slotted below graphs)
                                         ├── HeartRateZoneDistributionCard (if showZones)
                                         ├── PowerZoneDistributionCard (if showZones)
                                         └── LapSplitVisualizerCard (if showLaps)
```

---

## 2. Atomic Implementation Steps

### Step 1: Update `TrackOnMapScreen.kt` Default Slot Lambdas
* In `metadataContent` default lambda:
  * Append `StravaActivitySection` after `WorkoutExtrema`:
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
* In `analyticsContent` default lambda:
  * Remove lines 316–323 (`StravaActivitySection`), leaving only zone distributions and lap split cards.

### Step 2: Create Architectural Contract Test
* Author `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/TrackOnMapScreenMetadataRoutingContractTest.kt`:
  * Assert `metadataContent` lambda invokes `StravaActivitySection` after `WorkoutExtrema`.
  * Assert `analyticsContent` lambda does NOT invoke `StravaActivitySection`.
  * Assert invocation is guarded by `activeDetailPrefs.showStrava` and `!workoutData.stravaActivityData.isNullOrBlank()`.

### Step 3: Run Targeted Unit Tests & Full Clean-Room Regression
* Run targeted tests:
  ```bash
  ./gradlew testDebugUnitTest --tests "*TrackOnMapScreenMetadataRoutingContractTest*" --tests "*MapDetailLayoutMetadataSlotContractTest*"
  ```
* Run full suite:
  ```bash
  ./gradlew testDebugUnitTest
  ```

---

## 3. Invariants & Risk Mitigation

1. **Backwards Compatibility**: Callers providing explicit `metadataContent` or `analyticsContent` are unaffected because custom slots override the defaults.
2. **Empty State Cleanliness**: If `stravaActivityData` is null/empty or `showStrava` is false, nothing is rendered, preventing blank gaps or phantom surfaces above the map.
3. **9-Language Localization**: `StravaActivitySection` internally consumes existing localized strings; no new string resources are required.
