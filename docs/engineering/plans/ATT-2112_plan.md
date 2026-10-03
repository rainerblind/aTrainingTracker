# Stage 3: Implementation Plan - ATT-2112: Ensure Description and Extrema Cards Render in Detailed Workout View

**Ticket**: [ATT-2112](https://rainerblind.atlassian.net/browse/ATT-2112)  
**Sub-task**: [ATT-2117](https://rainerblind.atlassian.net/browse/ATT-2117) (`[Impl-Plan]`)  
**Parent Epic**: [ATT-111](https://rainerblind.atlassian.net/browse/ATT-111) (*Compact Post-Workout Visual Analytics & Graphs*)  
**Target Release**: `V4.9.38`  
**Active Sprint**: `Sprint 2026-40.13`  
**Requirement Mapping**: `REQ-UI-245`  
**Test Mapping**: `TST-UI-204`  
**Branch**: `feature/ATT-2112`  
**Author**: AI Agent 1 (Implementer)  
**Date**: 2026-10-03  

---

## 1. Problem Description & Background

In Sprint 2026-40.12 (ATT-2030 / `REQ-UI-240`), independent preference controls were established for Workout Summary List vs. Detailed Workout view. In the workout summary list (`WorkoutSummary.kt`), `WorkoutDescription` and `WorkoutExtrema` are positioned immediately beneath `WorkoutHeader` at the top of the card. However, in the full detail inspection view (`TrackOnMapScreen.kt` / `MapDetailLayout.kt`), `WorkoutDescription` and `WorkoutExtrema` were relegated to the very end of `analyticsContent`, beneath up to 840dp of telemetry graphs and 580dp of zone/split cards (total >1,420dp scroll depth).

This caused workout notes and extrema to be practically invisible on real devices, giving athletes the false impression that the settings toggles had failed to display the cards. This plan establishes an upper metadata slot positioned immediately below the map/divider and above the graphs in `MapDetailLayout.kt`, and updates `TrackOnMapScreen.kt` to route `WorkoutDescription` and `WorkoutExtrema` to this upper slot while retaining zones, laps, and Strava in `analyticsContent`.

---

## 2. Traceability & Requirements Mapping

* **Requirement**: `REQ-UI-245` (*Aftermath/Details: Upper Metadata Slot Architecture & Reactive Detail Section Rendering in MapDetailLayout*)
* **Test Mapping**: `TST-UI-204` (*Upper Metadata Slot & WorkoutDetailPreferences Reactive Rendering Verification*)
  - `TST-UI-204.1`: Structural Contract Test in `MapDetailLayoutMetadataSlotContractTest.kt`
  - `TST-UI-204.2`: Routing Contract Test in `TrackOnMapScreenMetadataRoutingContractTest.kt`
  - `TST-UI-204.3`: 9-Language Localization Audit via `TranslationParityTest.kt`
  - `TST-UI-204.4`: Clean-room test suite execution (`./gradlew testDebugUnitTest`)

---

## 3. System Invariants & Preserved Behavior

1. **Zero Layout Regressions on Existing Callers**: Callers of `MapDetailLayout` that do not pass `metadataContent` (e.g. `RouteOnMapScreen.kt`, `SegmentOnMapScreen.kt`) default `metadataContent = null` and retain 100% layout and visual equivalence.
2. **Gesture & Scrubbing Transparency**: The upper metadata slot lives inside `lowerColumn` within the vertically scrollable viewport. It does NOT intercept scrubbing gestures on the charts or zoom gestures on the map.
3. **Reactive Preference Synchronization**: Visibility responds immediately to DataStore preference changes via `MyPreferenceManager.workoutDetailPreferencesFlow`.
4. **Empty-State Protection**: If no notes exist, `WorkoutDescription` renders nothing; if no extrema exist, `WorkoutExtrema` renders nothing.
5. **Subtask Self-Sufficiency**: Subtasks transition directly to `Erledigt` upon passing Gate audit via `freigabe`.
6. **Parent Human Gate Invariance**: Terminal completion of parent tickets remains reserved for the human user in `Final Review (Human)`.

---

## 4. Proposed Architectural Changes

### Component 1: `MapDetailLayout.kt` (Slotted Metadata Architecture)
* **Parameter Declaration**: Add optional slotted composable parameter:
  ```kotlin
  metadataContent: (@Composable ColumnScope.() -> Unit)? = null
  ```
* **Scrollable Content Detection**: Update `hasScrollableContent`:
  ```kotlin
  val hasScrollableContent = metadataContent != null || analyticsContent != null || hasTelemetryGraphs
  ```
* **Upper Slot Placement in `lowerColumn`**: Render `metadataContent` at the very beginning of `lowerColumn`, wrapped in a `Surface` container:
  ```kotlin
  metadataContent?.let { mContent ->
      Surface(
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier.fillMaxWidth()
      ) {
          Column(modifier = Modifier.fillMaxWidth()) {
              mContent()
          }
      }
  }
  ```

### Component 2: `TrackOnMapScreen.kt` (Component Routing)
* **Parameter Declaration**: Add optional parameter `metadataContent: (@Composable ColumnScope.() -> Unit)? = null` to `TrackOnMapScreen`.
* **Metadata Routing**: Pass `metadataContent` to `MapDetailLayout`:
  ```kotlin
  metadataContent = {
      if (metadataContent != null) {
          metadataContent()
      } else {
          if (activeDetailPrefs.showDescription) {
              WorkoutDescription(
                  data = workoutData.descriptionData,
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 4.dp)
              )
          }
          if (activeDetailPrefs.showExtrema && workoutData.extremaData.dataRows.isNotEmpty()) {
              WorkoutExtrema(
                  data = workoutData.extremaData,
                  modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 4.dp)
              )
          }
      }
  }
  ```
* **Deduplication in `analyticsContent`**: Remove `WorkoutDescription` and `WorkoutExtrema` from `analyticsContent`, keeping `HeartRateZoneDistributionCard`, `PowerZoneDistributionCard`, `LapSplitVisualizerCard`, and `StravaActivitySection`.

---

## 5. Step-by-Step Implementation Sequence (Stage 4 Construction)

### Step 1: Pre-Implementation Gate Check
* Command: `python3 tools/jira_util.py check-gate ATT-2117`
* Verification: Ensure exit code 0 (`GATE_PASSED`) before any source code edits.

### Step 2: Implement Upper Metadata Slot in `MapDetailLayout.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayout.kt`
* Changes:
  1. Add `metadataContent: (@Composable ColumnScope.() -> Unit)? = null` parameter.
  2. Update `hasScrollableContent` to include `metadataContent != null`.
  3. Render `metadataContent` at the start of `lowerColumn`.

### Step 3: Implement Metadata Routing in `TrackOnMapScreen.kt`
* File: `app/src/main/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreen.kt`
* Changes:
  1. Add `metadataContent: (@Composable ColumnScope.() -> Unit)? = null` parameter to `TrackOnMapScreen`.
  2. Forward `metadataContent` slot to `MapDetailLayout` with `WorkoutDescription` and `WorkoutExtrema`.
  3. Remove `WorkoutDescription` and `WorkoutExtrema` from `analyticsContent` to avoid duplicate rendering.

### Step 4: Add Structural and Routing Contract Unit Tests
* Files:
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/map/MapDetailLayoutMetadataSlotContractTest.kt`
  - `app/src/test/java/com/atrainingtracker/trainingtracker/ui/aftermath/TrackOnMapScreenMetadataRoutingContractTest.kt`
* Verification Command:
  `./gradlew testDebugUnitTest --tests "*MetadataSlotContractTest*" --tests "*MetadataRoutingContractTest*"`

### Step 5: Localization & Regression Checks
* Run translation parity: `./gradlew testDebugUnitTest --tests "*TranslationParityTest*"`
* Run full regression suite: `./gradlew testDebugUnitTest`

---

## 6. Verification & Rollback Plan

* **Verification**:
  - JVM structural and routing contract tests verifying method signatures, parameters, slot placement, and preference gating.
  - Full `./gradlew testDebugUnitTest` verifying 100% pass rate.
* **Rollback Plan**:
  - `feature/ATT-2112` is isolated off `sprint/2026-40.13`. Reverting git commits or discarding the branch returns the codebase cleanly to the baseline without collateral damage.
